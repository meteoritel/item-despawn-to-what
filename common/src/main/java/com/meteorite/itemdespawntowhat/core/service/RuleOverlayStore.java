package com.meteorite.itemdespawntowhat.core.service;

import com.meteorite.itemdespawntowhat.core.load.RulePaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** 覆盖层文件工作的单一所有者；Future 终态仅在实际调用退出后发布。 */
final class RuleOverlayStore {
    private static final Logger LOGGER = LogManager.getLogger("IDTW.OverlayStore");
    private static final String VERSION_FILE = ".edit_version";
    private static final String PERSIST_REVISION_JOB = "persist-revision";
    private static final int MAX_BACKGROUND_JOBS = 8;
    private static final int READ_BUFFER_BYTES = 8192;
    private static final long STALL_NANOS = TimeUnit.SECONDS.toNanos(60);
    private static final long RETRY_NANOS = TimeUnit.SECONDS.toNanos(5);
    private static final long STOP_NANOS = TimeUnit.SECONDS.toNanos(30);
    private static final Object JOB_LOCK = new Object();
    private static final Set<Job<?>> JOBS = new HashSet<>();
    private static final ThreadLocal<Job<?>> CURRENT = new ThreadLocal<>();
    // 全进程保留单写入者；旧生命周期的实际作业退出前不允许启动新存档。
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(MAX_BACKGROUND_JOBS), action -> {
        Thread thread = new Thread(action, "IDTW.RuleSaveIO");
        thread.setDaemon(true);
        return thread;
    });

    private final Path root;
    private final Path versionFile;
    private int version;
    private int persistedVersion;
    private byte[] diskRevision;
    private boolean closed;
    private boolean recoveryRequired;
    private CompletableFuture<Void> persistence;
    private long retryAfter;

    RuleOverlayStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
        versionFile = this.root.resolve(VERSION_FILE);
        version = readVersion();
        persistedVersion = version;
    }

    synchronized int version() { return version; }

    synchronized int bumpVersion() {
        version = Math.incrementExact(version);
        // 重载已改变基底；下次读取重新建立覆盖层基线，不在服务端线程扫描目录。
        diskRevision = null;
        flushRevision();
        return version;
    }

    synchronized void acceptRevision(byte[] revision, boolean saved) {
        if (saved || diskRevision != null && !Arrays.equals(revision, diskRevision)) {
            version = Math.incrementExact(version);
        }
        diskRevision = revision == null ? null : revision.clone();
        if (CURRENT.get() == null) { flushRevision(); }
    }

    synchronized void flushRevision() {
        if (closed || version == persistedVersion || persistence != null && !persistence.isDone()
                || System.nanoTime() < retryAfter) { return; }
        persistence = submit(PERSIST_REVISION_JOB, () -> { persistLatestRevision(); return null; });
        // 拒绝和 I/O 错误都可观测；dirty 修订保留，后续 tick 再尝试。
        persistence.whenComplete((ignored, failure) -> {
            if (failure != null) {
                synchronized (this) { retryAfter = System.nanoTime() + RETRY_NANOS; }
                LOGGER.error("覆盖层修订号持久化失败，保留待重试值：{}", versionFile, failure);
            }
        });
    }

    void committed() {
        acceptRevision(null, true);
        try { persistLatestRevision(); }
        catch (IOException | RuntimeException failure) {
            synchronized (this) { retryAfter = System.nanoTime() + RETRY_NANOS; }
            LOGGER.error("规则已提交，但修订号持久化失败，后续重试：{}", versionFile, failure);
        }
    }

    private void persistLatestRevision() throws IOException {
        int target;
        synchronized (this) { target = version; }
        Files.createDirectories(root);
        Path temporary = Files.createTempFile(root, ".idtw-version-", ".tmp");
        try {
            Files.writeString(temporary, Integer.toString(target), StandardCharsets.UTF_8);
            try { Files.move(temporary, versionFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, versionFile, StandardCopyOption.REPLACE_EXISTING);
            }
            synchronized (this) { persistedVersion = target; }
        } finally { Files.deleteIfExists(temporary); }
    }

    <T> CompletableFuture<T> submit(String name, Callable<T> action) {
        synchronized (this) {
            if (closed || recoveryRequired) {
                return CompletableFuture.failedFuture(new RejectedExecutionException("覆盖层已关闭或需要恢复"));
            }
            Job<T> job = new Job<>(this, name, action);
            synchronized (JOB_LOCK) { JOBS.add(job); }
            try { WORKER.execute(job); }
            catch (RejectedExecutionException busy) { job.reject(busy); }
            return job.result;
        }
    }

    boolean stalled() {
        long now = System.nanoTime();
        boolean stalled = false;
        synchronized (JOB_LOCK) {
            for (Job<?> job : JOBS) {
                if (job.store == this && now - job.progressAt > STALL_NANOS) {
                    stalled = true;
                    if (!job.warned) {
                        job.warned = true;
                        LOGGER.error("覆盖层后台工作长时间无进展，目标保持隔离：root={} job={} running={}", root, job.name, job.running);
                    }
                }
            }
        }
        return stalled;
    }

    synchronized boolean unavailable() { return closed || recoveryRequired || stalled(); }

    synchronized void requireRecovery() {
        recoveryRequired = true;
        LOGGER.error("覆盖层回滚未完整成功，禁止继续保存或重载，请检查 .bak 并重启恢复：{}", root);
    }

    static void progress() {
        Job<?> job = CURRENT.get();
        if (job != null) { job.progressAt = System.nanoTime(); }
    }

    byte[] fingerprint(BooleanSupplier active) throws IOException {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        Path rules = root.resolve(RulePaths.OVERLAY_RULES_DIRECTORY);
        checkActive(active);
        if (!Files.isDirectory(rules)) { return digest.digest(); }
        try (var walk = Files.walk(rules)) {
            var files = walk.filter(path -> { checkActive(active); return Files.isRegularFile(path) && path.toString().endsWith(".json"); })
                    .sorted().toList();
            byte[] buffer = new byte[READ_BUFFER_BYTES];
            for (Path file : files) {
                checkActive(active);
                byte[] name = rules.relativize(file).toString().getBytes(StandardCharsets.UTF_8);
                long size = Files.size(file);
                if (size > Integer.MAX_VALUE) { throw new IOException("规则文件过大: " + file); }
                digest.update(ByteBuffer.allocate(8).putInt(name.length).putInt((int) size).array());
                digest.update(name);
                try (var input = Files.newInputStream(file)) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        checkActive(active);
                        digest.update(buffer, 0, read);
                    }
                }
            }
        }
        return digest.digest();
    }

    private static void checkActive(BooleanSupplier active) {
        if (!active.getAsBoolean()) { throw new CancellationException("覆盖层读取已取消"); }
        progress();
    }

    void close() {
        synchronized (this) { flushRevision(); closed = true; }
        long deadline = System.nanoTime() + STOP_NANOS;
        Set<Job<?>> queued;
        synchronized (JOB_LOCK) { queued = Set.copyOf(JOBS); }
        // 完成回调可以获取 store 锁，不能在 JOB_LOCK 内调用。
        for (Job<?> job : queued) {
            if (job.store == this && !job.name.equals(PERSIST_REVISION_JOB) && WORKER.remove(job)) {
                job.reject(new CancellationException("覆盖层已停服"));
            }
        }
        synchronized (JOB_LOCK) {
            while (JOBS.stream().anyMatch(job -> job.store == this)) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    LOGGER.error("停服等待覆盖层工作超时；旧工作退出前禁止下一存档启动：{}", root);
                    return;
                }
                try { TimeUnit.NANOSECONDS.timedWait(JOB_LOCK, remaining); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    LOGGER.warn("停服等待被中断；旧工作退出前仍禁止启动下一存档：{}", root);
                    return;
                }
            }
        }
    }

    static void requireIdle() {
        synchronized (JOB_LOCK) {
            if (!JOBS.isEmpty()) { throw new IllegalStateException("上一存档的覆盖层文件工作尚未退出，请等待后重试"); }
        }
    }

    private int readVersion() {
        try {
            if (Files.isRegularFile(versionFile)) { return Math.max(0, Integer.parseInt(Files.readString(versionFile).trim())); }
        } catch (IOException | NumberFormatException failure) {
            LOGGER.warn("无法读取覆盖层修订号，按零建立基线：{}", versionFile, failure);
        }
        return 0;
    }

    private static final class Job<T> implements Runnable {
        private final RuleOverlayStore store;
        private final String name;
        private final Callable<T> action;
        private final CompletableFuture<T> result = new CompletableFuture<>();
        private volatile long progressAt = System.nanoTime();
        private volatile boolean running;
        private boolean warned;

        private Job(RuleOverlayStore store, String name, Callable<T> action) {
            this.store = store; this.name = name; this.action = action;
        }

        @Override public void run() {
            running = true;
            progressAt = System.nanoTime();
            CURRENT.set(this);
            T value = null;
            Throwable failure = null;
            try { value = action.call(); }
            catch (Throwable thrown) { failure = thrown; }
            finally { CURRENT.remove(); }
            // 完成 Future 前先注销实际调用；调用方从不对这个 Future 执行取消或超时完成。
            synchronized (JOB_LOCK) { JOBS.remove(this); JOB_LOCK.notifyAll(); }
            if (failure == null) { result.complete(value); }
            else { result.completeExceptionally(failure); }
            if (failure instanceof VirtualMachineError fatal) { throw fatal; }
        }

        private void reject(Throwable failure) {
            synchronized (JOB_LOCK) { JOBS.remove(this); JOB_LOCK.notifyAll(); }
            result.completeExceptionally(failure);
        }
    }
}
