package com.meteorite.itemdespawntowhat.core.load;

import com.meteorite.itemdespawntowhat.core.api.IssueCollector;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * config 覆盖层规则读取：扫描 config/itemdespawntowhat/rules/**&#47;*.json。
 * 覆盖层是唯一可写层，由 GUI 与命令写入；文件缺少 id 时以 <b>模组命名空间</b> + 文件相对路径推导默认 id。
 */
public final class OverlayRuleReader {

    private OverlayRuleReader() {
        throw new UnsupportedOperationException("Utility class");
    }

    // 读取覆盖层的全部规则原始条目，按文件绝对路径排序保证顺序稳定
    // overlayRoot 为覆盖层根目录（config/itemdespawntowhat），其下的 rules 子目录存放规则文件
    // defaultNamespace 为覆盖层文件缺省 id 使用的命名空间（通常为模组 id）

    public static List<RawRuleEntry> read(Path overlayRoot,
                                          String defaultNamespace,
                                          IssueCollector issues) {
        if (overlayRoot == null) {
            return List.of();
        }
        Path rulesDirectory = overlayRoot.resolve(RulePaths.OVERLAY_RULES_DIRECTORY);
        if (!Files.isDirectory(rulesDirectory)) {
            return List.of();
        }

        List<Path> files;
        try (Stream<Path> walk = Files.walk(rulesDirectory)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(RulePaths.RULE_FILE_EXTENSION))
                    .sorted(Comparator.comparing(path -> path.toAbsolutePath().toString()))
                    .toList();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("列举覆盖层规则文件失败，保留现有规则", e);
        }

        List<RawRuleEntry> entries = new ArrayList<>();
        for (Path file : files) {
            String relative = rulesDirectory.relativize(file).toString().replace('\\', '/');
            RuleOrigin origin = RuleOrigin.overlay(RulePaths.OVERLAY_RULES_DIRECTORY + "/" + relative);
            String text = readText(file, origin, issues);
            if (text == null) {
                continue;
            }
            entries.addAll(RuleFileParser.parse(text, origin, deriveId(relative, defaultNamespace), issues));
        }
        return entries;
    }

    // 读取文件文本；失败即该文件拒载并报 ERROR
    private static @Nullable String readText(Path file, RuleOrigin origin, IssueCollector issues) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            issues.error("读取覆盖层规则文件失败: " + e.getMessage(), origin.display(), "");
            return null;
        }
    }

    // 由覆盖层文件相对路径推导默认 id：<defaultNamespace>:<去扩展名的相对路径>
    private static @Nullable ResourceLocation deriveId(String relativePath, String defaultNamespace) {
        String relative = RulePaths.stripExtension(relativePath);
        if (relative.isEmpty()) {
            return null;
        }
        return ResourceLocation.tryParse(defaultNamespace + ":" + relative);
    }
}
