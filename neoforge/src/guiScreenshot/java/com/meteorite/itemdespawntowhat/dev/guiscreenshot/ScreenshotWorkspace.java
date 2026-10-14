package com.meteorite.itemdespawntowhat.dev.guiscreenshot;

import com.meteorite.itemdespawntowhat.client.edit.EditorWorkspaceView;
import com.meteorite.itemdespawntowhat.client.net.EditorOpenRequest;
import com.meteorite.itemdespawntowhat.client.net.RuleEditClientState;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalog;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogEntry;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleCatalogType;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleIssue;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSaveStatus;
import com.meteorite.itemdespawntowhat.core.network.protocol.RuleSnapshot;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** 离线编辑工作区：固定快照和有限目录，不发送网络请求，不写入规则覆盖层。 */
final class ScreenshotWorkspace implements EditorWorkspaceView {
    private static final String SNAPSHOT_RESOURCE = "/gui-screenshot/editor-snapshot.json";
    private static final String SESSION_ID = "gui-screenshot-fixture";
    private static final int FIXTURE_REVISION = 1;
    private static final int REFERENCE_LIFESPAN_TICKS = 6000;
    private final RuleSnapshot snapshot = loadSnapshot();
    private final Map<RuleCatalogType, RuleCatalog> directories = new EnumMap<>(RuleCatalogType.class);
    private final Map<RuleCatalogType, RuleCatalog> pages = new EnumMap<>(RuleCatalogType.class);
    private RuleEditClientState state = RuleEditClientState.ACTIVE;
    private long operationSequence;
    private long resultSequence;

    ScreenshotWorkspace() {
        for (RuleCatalogType type : RuleCatalogType.values()) {
            List<RuleCatalogEntry> entries = switch (type) {
                case ITEM -> List.of(entry("minecraft:stone", "block.minecraft.stone"),
                        entry("minecraft:dirt", "block.minecraft.dirt"), entry("minecraft:diamond", "item.minecraft.diamond"));
                case BLOCK -> List.of(entry("minecraft:stone", "block.minecraft.stone"), entry("minecraft:dirt", "block.minecraft.dirt"));
                case ENTITY -> List.of(entry("minecraft:pig", "entity.minecraft.pig"), entry("minecraft:zombie", "entity.minecraft.zombie"));
                case FLUID -> List.of(entry("minecraft:water", "block.minecraft.water"), entry("minecraft:lava", "block.minecraft.lava"));
                default -> List.of();
            };
            directories.put(type, new RuleCatalog(type, FIXTURE_REVISION, entries, true));
        }
    }

    private static RuleCatalogEntry entry(String id, String translationKey) {
        return new RuleCatalogEntry(id, translationKey, "", id);
    }

    private static RuleSnapshot loadSnapshot() {
        try (InputStream input = ScreenshotWorkspace.class.getResourceAsStream(SNAPSHOT_RESOURCE)) {
            if (input == null) { throw new IllegalStateException("缺少截图 fixture: " + SNAPSHOT_RESOURCE); }
            return RuleSnapshot.parse(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getOrThrow();
        } catch (IOException exception) {
            throw new IllegalStateException("读取截图 fixture 失败", exception);
        }
    }

    /** {@inheritDoc} */
    @Override public RuleEditClientState state() { return state; }
    /** {@inheritDoc} */
    @Override public String sessionId() { return SESSION_ID; }
    /** {@inheritDoc} */
    @Override public int contextRevision() { return FIXTURE_REVISION; }
    /** {@inheritDoc} */
    @Override public @Nullable EditorOpenRequest openRequest() { return null; }
    /** {@inheritDoc} */
    @Override public RuleSnapshot snapshot() { return snapshot; }
    /** {@inheritDoc} */
    @Override public @Nullable RuleCatalog catalog(RuleCatalogType type) { return pages.get(type); }
    /** {@inheritDoc} */
    @Override public RuleCatalog directory(RuleCatalogType type) { return directories.get(type); }
    /** {@inheritDoc} */
    @Override public void refreshDirectory(RuleCatalogType type) { pages.put(type, directories.get(type)); }
    /** {@inheritDoc} */
    @Override public boolean directoryFailed(RuleCatalogType type) { return false; }
    /** {@inheritDoc} */
    @Override public void retryDirectory(RuleCatalogType type) { refreshDirectory(type); }
    /** {@inheritDoc} */
    @Override public void invalidateDirectories() { pages.clear(); }

    /** {@inheritDoc} */
    @Override public boolean requestCatalog(RuleCatalogType type, @Nullable String filter, int page, int pageSize) {
        if (page < 0 || pageSize < 1) { return false; }
        String query = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
        List<RuleCatalogEntry> entries = directories.get(type).entries().stream()
                .filter(entry -> entry.id().contains(query) || entry.label().contains(query)).toList();
        // 先用 long 计算偏移，页码过大时正常返回空页而非整数溢出。
        long offset = (long) page * pageSize;
        pages.put(type, new RuleCatalog(type, FIXTURE_REVISION, entries.stream().skip(offset).limit(pageSize).toList(),
                offset + pageSize >= entries.size()));
        return true;
    }

    /** {@inheritDoc} */
    @Override public int referenceLifespanTicks() { return REFERENCE_LIFESPAN_TICKS; }
    /** {@inheritDoc} <p>离线 fixture 拒绝提交，避免截图操作被误认为真实保存。</p> */
    @Override public boolean save(@Nullable String operationId, @Nullable String changeSetJson) { resultSequence++; return false; }
    /** {@inheritDoc} */
    @Override public String newOperationId() { return SESSION_ID + "-" + ++operationSequence; }
    /** {@inheritDoc} */
    @Override public void close(@Nullable String reasonCode) { state = RuleEditClientState.FREE; }
    /** {@inheritDoc} */
    @Override public long resultSeq() { return resultSequence; }
    /** {@inheritDoc} */
    @Override public @Nullable RuleSaveStatus lastStatus() { return resultSequence == 0 ? null : RuleSaveStatus.UNAVAILABLE; }
    /** {@inheritDoc} */
    @Override public @Nullable String lastMessageCode() { return resultSequence == 0 ? null : RuleSaveStatus.UNAVAILABLE.messageKey(); }
    /** {@inheritDoc} */
    @Override public List<String> lastMessageArgs() { return List.of(); }
    /** {@inheritDoc} */
    @Override public int lastResultVersion() { return snapshot.version(); }
    /** {@inheritDoc} */
    @Override public boolean lastWrittenToDisk() { return false; }
    /** {@inheritDoc} */
    @Override public boolean lastReloaded() { return false; }
    /** {@inheritDoc} */
    @Override public List<RuleIssue> lastIssues() { return List.of(); }
}
