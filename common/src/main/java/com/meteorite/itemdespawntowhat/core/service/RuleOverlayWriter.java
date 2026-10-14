package com.meteorite.itemdespawntowhat.core.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import com.meteorite.itemdespawntowhat.core.api.RuleDecodeBudget;
import com.meteorite.itemdespawntowhat.core.api.RuleFields;
import com.meteorite.itemdespawntowhat.core.load.RulePaths;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleEdit;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleEditChangeSet;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSaveProgress;
import net.minecraft.resources.ResourceLocation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Stream;

/**
 * 覆盖层逐 id 写入：保留未编辑 JSON，整批预备、备份、提交；失败时回滚已经写入的文件、
 * 本轮写出的备份与临时文件，使目录回到保存前状态。
 * 单次保存只扫描并解析目录一次，后续阶段复用已读字节与元数据指纹，不重复读取全文。
 */
public final class RuleOverlayWriter {
    // 备份文件后缀：备份供管理员手工恢复，成功路径保留最新备份
    private static final String BACKUP_SUFFIX = ".bak";
    // 备份轮换暂存前缀：为新备份腾出位置时先把既有备份改名到这里
    private static final String ROTATED_BACKUP_PREFIX = ".idtw-bak-";
    // 暂存文件统一后缀
    private static final String TEMPORARY_SUFFIX = ".tmp";
    // 覆盖层文件被外部修改时的统一拒绝文案
    private static final String EXTERNALLY_MODIFIED_MESSAGE = "准备保存时文件已被外部修改: ";
    /** 写入结果；零文件或存在冲突均不能解释为保存成功。 */
    public record ApplyResult(int writtenFiles, List<String> conflicts, boolean rollbackFailed) {
        /**
         * 构造没有回滚失败的写入结果。
         * @param writtenFiles 写入文件数
         * @param conflicts 冲突说明
         */
        public ApplyResult(int writtenFiles, List<String> conflicts) { this(writtenFiles, conflicts, false); }
        public ApplyResult { conflicts = List.copyOf(conflicts); }
    }
    /** 文件原始形状与完整条目，非对象条目也必须原样保留。 */
    private static final class RuleFile {
        private final Path path;
        private final boolean array;
        private final byte[] original;
        private final List<JsonElement> entries = new ArrayList<>();
        // 读盘时刻的文件指纹；保存准备阶段靠它发现外部修改，无需重读全文
        private FileStamp stamp;
        // 本轮为该文件准备的备份路径，以及被腾开位置的既有备份（无既有备份时为空）
        private Path backup;
        private Path rotatedBackup;
        private int liveEntries;
        private int remainingIndexXor;
        private ResourceLocation inferredId;
        private RuleFile(Path path, boolean array, byte[] original) {
            this.path = path; this.array = array; this.original = original;
        }
        private JsonElement content() {
            if (!array && liveEntries == 1) { return entries.get(remainingIndexXor); }
            JsonArray result = new JsonArray();
            // Java null 是本批删除的槽位；JSON null 与其它未编辑成员必须原样保留。
            entries.stream().filter(Objects::nonNull).forEach(result::add);
            return result;
        }
    }
    /** 规则所在文件及条目下标。 */
    private record Location(RuleFile file, int index) {}
    /**
     * 覆盖层文件在读取时刻的元数据指纹。
     * 准备阶段用它与当前属性比对即可发现外部修改，避免为校验重复读取全文；
     * 属性读不到时视为已修改，不把不可判定当成未变更。
     */
    private record FileStamp(long size, long modifiedMillis) {
        // 取样失败或目标不再是普通文件时返回空，交由比对方判定为已修改
        private static FileStamp of(Path path) {
            try {
                BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
                if (!attributes.isRegularFile()) { return null; }
                return new FileStamp(attributes.size(), attributes.lastModifiedTime().toMillis());
            } catch (IOException unreadable) {
                return null;
            }
        }
        // 文件仍是普通文件且大小、修改时间都与取样时一致才算未变更
        private boolean matches(Path path) {
            FileStamp current = of(path);
            return current != null && current.size() == size && current.modifiedMillis() == modifiedMillis;
        }
    }
    private final Path root;
    private final String namespace;
    // 写入目录固定为 <overlayRoot>/rules；defaultNamespace 用于无 id 的单对象文件反推规则 id
    public RuleOverlayWriter(Path overlayRoot, String defaultNamespace) {
        root = overlayRoot.toAbsolutePath().normalize().resolve(RulePaths.OVERLAY_RULES_DIRECTORY);
        namespace = defaultNamespace;
    }

    // 拒绝路径穿越、符号链接、重复编辑和文件占用；任何预备错误均不写入。
    public ApplyResult apply(RuleEditChangeSet changes, IssueCollector issues) {
        return apply(changes, issues, () -> true, () -> true);
    }

    /**
     * 在保存后台队列准备并提交整批规则；失效请求在提交检查点退出，已替换文件按原内容回滚。
     * @param changes 本批编辑
     * @param issues 本批问题
     * @param active 请求是否仍有效，不得访问世界
     * @param revisionMatches 提交前的全目录修订核对，不得访问世界
     * @return 整批写入结果
     */
    public ApplyResult apply(RuleEditChangeSet changes, IssueCollector issues,
                             java.util.function.BooleanSupplier active,
                             java.util.function.BooleanSupplier revisionMatches) {
        return apply(changes, issues, active, revisionMatches, () -> true, progress -> {});
    }

    /**
     * 带进度与原子提交确认的整批写入；取消与提交确认竞争，失败回滚由本调用线程独占执行。
     * @param changes 本批编辑
     * @param issues 本批问题
     * @param active 请求是否仍有效
     * @param revisionMatches 提交前目录修订核对
     * @param confirmCommit 原子确认提交；返回 false 时回滚整批
     * @param progress 接收不可变进度样本，不得访问世界
     * @return 写入结果及回滚失败标记
     */
    public ApplyResult apply(RuleEditChangeSet changes, IssueCollector issues,
                             java.util.function.BooleanSupplier active,
                             java.util.function.BooleanSupplier revisionMatches,
                             java.util.function.BooleanSupplier confirmCommit,
                             java.util.function.Consumer<RuleSaveProgress> progress) {
        Map<Path, RuleFile> files = new LinkedHashMap<>();
        Set<Path> dirty = new LinkedHashSet<>();
        List<String> conflicts = new ArrayList<>();
        try {
            requireActive(active);
            safePath(root);
            load(files, active);
            Map<ResourceLocation, List<Location>> locations = index(files.values());
            Set<ResourceLocation> seen = new HashSet<>();
            int processed = 0;
            for (RuleEdit change : changes.edits()) {
                requireActive(active);
                progress.accept(new RuleSaveProgress(RuleSaveProgress.Stage.PREPARING_FILES, processed++,
                        changes.edits().size(), change.id().toString(), true));
                if (!seen.add(change.id())) { throw new IOException("重复编辑规则: " + change.id()); }
                Path target = target(change.id());
                Location location = locate(locations, change.id());
                if (change.action() == RuleEdit.Action.DELETE) {
                    if (location != null) {
                        RuleFile file = location.file;
                        removeInferred(locations, file);
                        locations.remove(change.id());
                        // 保留稳定槽位，后续编辑不因数组删除而指向错误条目。
                        file.entries.set(location.index, null);
                        file.liveEntries--;
                        file.remainingIndexXor ^= location.index;
                        addInferred(locations, file);
                        dirty.add(location.file.path);
                    }
                    continue;
                }
                JsonObject body = Objects.requireNonNull(change.rule(), "upsert 缺少 rule").deepCopy();
                body.addProperty(RuleFields.ID, change.id().toString());
                if (location != null) {
                    removeInferred(locations, location.file);
                    location.file.entries.set(location.index, body);
                    locations.put(change.id(), new ArrayList<>(List.of(location)));
                    dirty.add(location.file.path);
                } else {
                    RuleFile existing = files.get(target);
                    // 合法空数组不承载规则，可复用其路径；其它现存内容（包括损坏 JSON）仍禁止覆盖。
                    boolean reusableEmptyArray = existing != null && existing.array && existing.entries.isEmpty();
                    if (!reusableEmptyArray && (Files.exists(target, LinkOption.NOFOLLOW_LINKS) || existing != null)) {
                        throw new IOException("新增规则目标文件已被占用: " + target);
                    }
                    RuleFile file = new RuleFile(target, false, existing == null ? null : existing.original);
                    // 复用空数组文件时沿用其读取指纹，后续校验仍以同一份快照为准
                    file.stamp = existing == null ? null : existing.stamp;
                    file.entries.add(body);
                    file.liveEntries = 1;
                    files.put(target, file);
                    locations.put(change.id(), new ArrayList<>(List.of(new Location(file, 0))));
                    dirty.add(target);
                }
            }
            requireActive(active);
            if (!revisionMatches.getAsBoolean()) { throw new IOException("准备保存时覆盖层修订已变化"); }
            commit(dirty.stream().map(files::get).toList(), active, confirmCommit, progress);
            return new ApplyResult(dirty.size(), List.of());
        } catch (IOException | RuntimeException failure) {
            if (active.getAsBoolean() || failure.getSuppressed().length > 0) {
                org.apache.logging.log4j.LogManager.getLogger().error("覆盖层整批保存失败", failure);
            } else {
                org.apache.logging.log4j.LogManager.getLogger().debug("覆盖层保存已取消并结束回滚", failure);
            }
            String message = "覆盖层整批保存失败: " + failure.getMessage();
            if (failure.getSuppressed().length > 0) { message += "；部分文件回滚失败，请用 .bak 恢复并检查日志"; }
            issues.error(message, root.toString(), null);
            conflicts.add(message);
            return new ApplyResult(0, conflicts, failure.getSuppressed().length > 0);
        }
    }

    // 对任意 id 执行同样的目录边界校验，不允许 . 或 .. 路径段。
    private Path target(ResourceLocation id) throws IOException {
        for (String segment : id.getPath().split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IOException("规则 id 含非法路径段: " + id);
            }
        }
        Path target = root.resolve(id.getNamespace()).resolve(id.getPath() + RulePaths.RULE_FILE_EXTENSION).normalize();
        if (!target.startsWith(root)) { throw new IOException("规则路径越过 rules 目录: " + id); }
        safePath(target);
        safePath(target.resolveSibling(target.getFileName() + BACKUP_SUFFIX));
        return target;
    }

    // 不跟随任何现存祖先链接，避免通过 rules 或备份链接越界写文件。
    private static void safePath(Path path) throws IOException {
        for (Path cursor = path; cursor != null; cursor = cursor.getParent()) {
            if (Files.isSymbolicLink(cursor)) { throw new IOException("不允许写入符号链接路径: " + cursor); }
        }
    }

    private void load(Map<Path, RuleFile> files, java.util.function.BooleanSupplier active) throws IOException {
        if (!Files.isDirectory(root)) { return; }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.filter(p -> p.toString().endsWith(RulePaths.RULE_FILE_EXTENSION)).sorted().toList()) {
                // 一次属性取样同时判定普通文件与元数据指纹：非普通文件与读不到属性都返回空，按不参与保存跳过
                FileStamp stamp = FileStamp.of(path);
                if (stamp == null) { continue; }
                requireActive(active);
                safePath(path);
                byte[] original = Files.readAllBytes(path);
                // 规则文本一律走统一的有界解码入口：非法 JSON 与嵌套超深同样只是拒载该文件
                RuleDecodeBudget.ParseResult parsed = RuleDecodeBudget.parse(new String(original, StandardCharsets.UTF_8));
                if (!parsed.ok()) {
                    // 无关坏文件不阻断保存，但必须留下来源，供管理员修复；原始字节不参与写回。
                    org.apache.logging.log4j.LogManager.getLogger().warn(
                            "保存扫描保留非法 JSON 文件: {}（{}）", path, parsed.failureReason());
                    continue;
                }
                JsonElement json = parsed.element();
                if (json == null || (!json.isJsonObject() && !json.isJsonArray())) { continue; }
                RuleFile file = new RuleFile(path, json.isJsonArray(), original);
                file.stamp = stamp;
                if (file.array) { json.getAsJsonArray().forEach(file.entries::add); }
                else { file.entries.add(json); }
                files.put(path, file);
            }
        }
    }

    private Map<ResourceLocation, List<Location>> index(Collection<RuleFile> files) {
        Map<ResourceLocation, List<Location>> locations = new HashMap<>();
        for (RuleFile file : files) {
            file.liveEntries = file.entries.size();
            for (int i = 0; i < file.entries.size(); i++) {
                file.remainingIndexXor ^= i;
                JsonElement element = file.entries.get(i);
                if (!element.isJsonObject()) { continue; }
                JsonObject body = element.getAsJsonObject();
                ResourceLocation candidate = null;
                try {
                    if (body.has(RuleFields.ID)) { candidate = ResourceLocation.tryParse(body.get(RuleFields.ID).getAsString()); }
                } catch (RuntimeException invalid) { /* 未编辑的坏条目原样保留。 */ }
                if (candidate != null) {
                    locations.computeIfAbsent(candidate, key -> new ArrayList<>()).add(new Location(file, i));
                }
            }
            addInferred(locations, file);
        }
        return locations;
    }

    private void addInferred(Map<ResourceLocation, List<Location>> locations, RuleFile file) {
        if (file.liveEntries != 1) { return; }
        JsonElement entry = file.entries.get(file.remainingIndexXor);
        if (!entry.isJsonObject() || entry.getAsJsonObject().has(RuleFields.ID)) { return; }
        ResourceLocation id = ResourceLocation.tryParse(namespace + ":"
                + RulePaths.stripExtension(root.relativize(file.path).toString().replace('\\', '/')));
        if (id != null) {
            file.inferredId = id;
            locations.computeIfAbsent(id, key -> new ArrayList<>()).add(new Location(file, file.remainingIndexXor));
        }
    }

    private static void removeInferred(Map<ResourceLocation, List<Location>> locations, RuleFile file) {
        if (file.inferredId == null) { return; }
        List<Location> entries = locations.get(file.inferredId);
        entries.remove(new Location(file, file.remainingIndexXor));
        if (entries.isEmpty()) { locations.remove(file.inferredId); }
        file.inferredId = null;
    }

    private static Location locate(Map<ResourceLocation, List<Location>> locations, ResourceLocation id) throws IOException {
        List<Location> found = locations.get(id);
        // 未编辑的重复 id 不阻断其它规则保存，只有命中歧义 id 时整批拒绝。
        if (found == null) { return null; }
        if (found.size() != 1) { throw new IOException("覆盖层同 id 多处定义，需先手动消歧: " + id); }
        return found.getFirst();
    }

    // 所有临时文件与备份准备成功后才替换；普通 I/O 失败时恢复整批原内容，并把备份放回原状、清理临时文件。
    private static void requireActive(java.util.function.BooleanSupplier active) throws IOException {
        if (!active.getAsBoolean()) { throw new IOException("保存请求已失效"); }
        RuleOverlayStore.progress();
    }

    private void commit(List<RuleFile> files, java.util.function.BooleanSupplier active,
                        java.util.function.BooleanSupplier confirmCommit,
                        java.util.function.Consumer<RuleSaveProgress> progress) throws IOException {
        Map<RuleFile, Path> temporary = new LinkedHashMap<>();
        List<RuleFile> committed = new ArrayList<>();
        try {
            for (RuleFile file : files) {
                requireActive(active);
                safePath(file.path);
                Files.createDirectories(file.path.getParent());
                // 删空的文件参与同一批备份与回滚，但不再生成空数组占用规则路径。
                if (file.liveEntries > 0) {
                    Path temp = Files.createTempFile(file.path.getParent(), ".idtw-", TEMPORARY_SUFFIX);
                    temporary.put(file, temp);
                    Files.writeString(temp, file.content().toString(), StandardCharsets.UTF_8);
                }
                // 准备阶段只核对元数据并写备份，全文逐字节比对留到替换前的那一次
                verifyMetadataUnchanged(file);
                prepareBackup(file);
            }
            for (RuleFile file : files) {
                requireActive(active);
                progress.accept(new RuleSaveProgress(RuleSaveProgress.Stage.WRITING, committed.size(), files.size(),
                        root.relativize(file.path).toString(), true));
                verifyContentUnchanged(file);
                if (file.liveEntries == 0) { Files.delete(file.path); }
                else { move(temporary.get(file), file.path); }
                committed.add(file);
            }
            requireActive(active);
            // 唯一提交确认点；主线程取消只做 CAS，绝不与本线程并发恢复文件。
            if (!confirmCommit.getAsBoolean()) { throw new IOException("保存已取消，回滚整批文件"); }
            // 提交确认之后失败已不可能，此刻才丢弃被逐出的既有备份
            for (RuleFile file : files) { discardRotatedBackup(file); }
        } catch (IOException | RuntimeException failure) {
            Collections.reverse(committed);
            int restored = 0;
            for (RuleFile file : committed) {
                progress.accept(new RuleSaveProgress(RuleSaveProgress.Stage.ROLLING_BACK, restored++, committed.size(),
                        root.relativize(file.path).toString(), false));
                try {
                    if (file.original == null) { Files.deleteIfExists(file.path); }
                    else {
                        Path restore = Files.createTempFile(file.path.getParent(), ".idtw-restore-", TEMPORARY_SUFFIX);
                        try { Files.write(restore, file.original); move(restore, file.path); }
                        finally { Files.deleteIfExists(restore); }
                    }
                } catch (IOException | RuntimeException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            }
            rollbackBackups(files, failure);
            throw failure;
        } finally {
            for (Path temp : temporary.values()) { deleteQuietly(temp); }
        }
    }

    // 准备阶段核对：只比对读取时刻的元数据指纹，不重读全文
    private static void verifyMetadataUnchanged(RuleFile file) throws IOException {
        boolean unchanged = file.original == null
                ? !Files.exists(file.path, LinkOption.NOFOLLOW_LINKS)
                : file.stamp != null && file.stamp.matches(file.path);
        if (!unchanged) { throw new IOException(EXTERNALLY_MODIFIED_MESSAGE + file.path); }
    }

    // 替换前核对：整次保存唯一一次全文比对，任何破坏性写入前都必须逐字节确认内容未变
    private static void verifyContentUnchanged(RuleFile file) throws IOException {
        boolean exists = Files.exists(file.path, LinkOption.NOFOLLOW_LINKS);
        if (file.original == null ? exists : !exists || !Arrays.equals(file.original, Files.readAllBytes(file.path))) {
            throw new IOException(EXTERNALLY_MODIFIED_MESSAGE + file.path);
        }
    }

    // 用内存中已读字节写出备份，不再回读源文件；已有旧备份先整体改名挪开，失败时能原样放回
    private static void prepareBackup(RuleFile file) throws IOException {
        if (file.original == null) { return; }
        Path backup = file.path.resolveSibling(file.path.getFileName() + BACKUP_SUFFIX);
        safePath(backup);
        file.backup = backup;
        if (Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
            Path rotated = Files.createTempFile(file.path.getParent(), ROTATED_BACKUP_PREFIX, TEMPORARY_SUFFIX);
            try {
                move(backup, rotated);
            } catch (IOException | RuntimeException rotateFailure) {
                // 改名失败时这一步不留暂存文件，目录与保存前一致
                deleteQuietly(rotated);
                throw rotateFailure;
            }
            file.rotatedBackup = rotated;
        }
        Files.write(backup, file.original);
    }

    // 失败时把本轮动过的备份恢复原状：既有备份放回原位即恢复旧内容，本轮新写的备份删除
    private static void rollbackBackups(List<RuleFile> files, Throwable failure) {
        for (RuleFile file : files) {
            try {
                if (file.rotatedBackup != null) {
                    move(file.rotatedBackup, file.backup);
                    file.rotatedBackup = null;
                } else if (file.backup != null) {
                    Files.deleteIfExists(file.backup);
                }
            } catch (IOException | RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
    }

    // 提交确认后丢弃被逐出的既有备份；清理失败只告警，不改变已经完成的提交结果
    private static void discardRotatedBackup(RuleFile file) {
        if (file.rotatedBackup == null) { return; }
        deleteQuietly(file.rotatedBackup);
        file.rotatedBackup = null;
    }

    // 删除暂存或备份文件：失败只告警，不把清理问题升级成保存失败
    private static void deleteQuietly(Path path) {
        try { Files.deleteIfExists(path); }
        catch (IOException cleanupFailure) {
            org.apache.logging.log4j.LogManager.getLogger().warn("暂存文件清理失败: {}", path, cleanupFailure);
        }
    }

    private static void move(Path from, Path to) throws IOException {
        try { Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException unsupported) { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING); }
    }
}
