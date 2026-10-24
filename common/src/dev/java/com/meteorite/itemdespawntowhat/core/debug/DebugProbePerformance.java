package com.meteorite.itemdespawntowhat.core.debug;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.Constants;
import com.meteorite.itemdespawntowhat.core.api.scheduler.ServerTaskKind;
import com.meteorite.itemdespawntowhat.core.runtime.ConversionRuntime;
import net.minecraft.server.MinecraftServer;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** 自动后端运行的单步性能记录；JMX 在独立线程采样，世界与调度信息只由服务端线程提交。 */
final class DebugProbePerformance {
    private static final int SAMPLE_INTERVAL_MS = 200;
    private static DebugProbePerformance active;
    private final long started = System.nanoTime();
    private final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    private final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    private final List<java.lang.management.GarbageCollectorMXBean> collectors = ManagementFactory.getGarbageCollectorMXBeans();
    private final com.sun.management.OperatingSystemMXBean os =
            (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    private final long serverThread = Thread.currentThread().threadId();
    private final ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor(action -> {
        Thread thread = new Thread(action, "IDTW-probe-sampler");
        thread.setDaemon(true);
        return thread;
    });
    private final List<ResourceSample> samples = new ArrayList<>();
    private final List<TickSample> ticks = new ArrayList<>();
    private final List<Phase> phases = new ArrayList<>();
    private final long initialGcCount = gcCount();
    private final long initialGcMillis = gcMillis();
    private String phase = "START";
    private long phaseStarted = started;
    private long phaseCpu;
    private long previousSample;
    private long previousProcessCpu;
    private long previousThreadCpu;
    private long samplingNanos;
    private long maxSamplingNanos;
    private long tickRecordingNanos;
    private String samplingError;
    private boolean closed;

    private record ResourceSample(double elapsed_ms, String phase, Double process_cpu_percent_one_core,
                                  Double server_thread_cpu_percent, long heap_used_bytes, long heap_committed_bytes,
                                  long heap_max_bytes, long non_heap_used_bytes, long gc_count, long gc_time_ms,
                                  double interval_ms) {}
    private record Phase(String name, double start_ms, double end_ms, double wall_ms, Double server_thread_cpu_ms) {}
    private record TickSample(double elapsed_ms, String phase, int server_tick, double vanilla_ms,
                              Double runtime_ms, long pending, long ready_delay_ticks, long drained,
                              long work_units, long[] kind_step_us) {}

    private DebugProbePerformance() {
        if (threads.isThreadCpuTimeSupported() && !threads.isThreadCpuTimeEnabled()) {
            threads.setThreadCpuTimeEnabled(true);
        }
        phaseCpu = threadCpu();
        sample();
        sampler.scheduleWithFixedDelay(() -> {
            try { sample(); }
            catch (RuntimeException failure) { synchronized (this) { samplingError = failure.toString(); } }
        }, SAMPLE_INTERVAL_MS, SAMPLE_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    static DebugProbePerformance start() {
        if (active != null) { throw new IllegalStateException("已有性能窗口未结束"); }
        active = new DebugProbePerformance();
        return active;
    }

    // 场景和世界探针共用阶段计时，未运行自动探针时没有采样或线程。
    static void phase(String next) {
        if (active != null) { active.changePhase(next); }
    }

    private synchronized void changePhase(String next) {
        if (phase.equals(next)) { return; }
        long now = System.nanoTime();
        long cpu = threadCpu();
        phases.add(new Phase(phase, millis(phaseStarted - started), millis(now - started),
                millis(now - phaseStarted), cpu < 0 || phaseCpu < 0 ? null : millis(cpu - phaseCpu)));
        phase = next;
        phaseStarted = now;
        phaseCpu = cpu;
    }

    // 短操作沿用同一时间轴，结束后恢复父阶段，重载成本不会混成不明的测量尖峰。
    static <T> T operation(String name, java.util.function.Supplier<T> action) {
        String previous = active == null ? null : active.phase;
        phase(name);
        try { return action.get(); }
        finally { if (previous != null) { phase(previous); } }
    }

    static void tick(MinecraftServer server, ConversionRuntime runtime) {
        if (active == null || runtime == null) { return; }
        long begin = System.nanoTime();
        long[] vanilla = server.getTickTimesNanos();
        long runtimeNanos = DebugSessionManager.runtimeTickNanos(server);
        var stats = runtime.schedulerStats();
        var budget = runtime.budgetSnapshot();
        long[] kinds = new long[ServerTaskKind.values().length];
        for (ServerTaskKind kind : ServerTaskKind.values()) {
            var stat = stats.perKind().get(kind);
            kinds[kind.ordinal()] = stat == null ? 0 : stat.lastMicros();
        }
        active.ticks.add(new TickSample(millis(begin - active.started), active.phase, server.getTickCount(),
                millis(vanilla[Math.floorMod(server.getTickCount(), vanilla.length)]),
                runtimeNanos < 0 ? null : millis(runtimeNanos), stats.pending(), stats.lastMaxReadyDelayTicks(),
                stats.lastDrainedTasks(), budget == null ? 0 : budget.usedWorkUnits(), kinds));
        active.tickRecordingNanos += System.nanoTime() - begin;
    }

    private synchronized void sample() {
        if (closed) { return; }
        long now = System.nanoTime();
        long process = os.getProcessCpuTime();
        long cpu = threadCpu();
        long interval = now - previousSample;
        var heap = memory.getHeapMemoryUsage();
        samples.add(new ResourceSample(millis(now - started), phase,
                percent(process, previousProcessCpu, interval), percent(cpu, previousThreadCpu, interval),
                heap.getUsed(), heap.getCommitted(), heap.getMax(), memory.getNonHeapMemoryUsage().getUsed(),
                gcCount() - initialGcCount, gcMillis() - initialGcMillis,
                previousSample == 0 ? 0 : millis(interval)));
        previousSample = now;
        previousProcessCpu = process;
        previousThreadCpu = cpu;
        long cost = System.nanoTime() - now;
        samplingNanos += cost;
        maxSamplingNanos = Math.max(maxSamplingNanos, cost);
    }

    private Double percent(long current, long previous, long interval) {
        return previousSample == 0 || current < 0 || previous < 0 || interval < TimeUnit.MILLISECONDS.toNanos(20) ? null
                : (current - previous) * 100D / interval;
    }

    // 先停止采样再构造 JSON，序列化与落盘成本留在窗口外。
    JsonObject finish() {
        sampler.shutdownNow();
        synchronized (this) {
            sample();
            changePhase("CLOSED");
            closed = true;
            active = null;
            return report();
        }
    }

    private JsonObject report() {
        Gson gson = new Gson();
        JsonObject data = new JsonObject();
        data.addProperty("format", Constants.ID_NAMESPACE + "-probe-performance-v1");
        data.addProperty("sample_interval_ms", SAMPLE_INTERVAL_MS);
        data.addProperty("sampling_error", samplingError);
        data.addProperty("sampling_total_ms", millis(samplingNanos));
        data.addProperty("sampling_max_ms", millis(maxSamplingNanos));
        data.addProperty("tick_recording_total_ms", millis(tickRecordingNanos));
        data.addProperty("duration_ms", millis(phaseStarted - started));
        data.add("phases", gson.toJsonTree(phases));
        data.add("resources", gson.toJsonTree(samples));
        data.add("ticks", gson.toJsonTree(ticks));
        data.add("kind_order", gson.toJsonTree(ServerTaskKind.values()));
        return data;
    }

    private long threadCpu() { return threads.isThreadCpuTimeSupported() ? threads.getThreadCpuTime(serverThread) : -1; }
    private static double millis(long nanos) { return nanos / 1_000_000D; }
    private long gcCount() {
        return collectors.stream().mapToLong(bean -> Math.max(0, bean.getCollectionCount())).sum();
    }
    private long gcMillis() {
        return collectors.stream().mapToLong(bean -> Math.max(0, bean.getCollectionTime())).sum();
    }
}
