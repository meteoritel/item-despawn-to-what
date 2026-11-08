package com.meteorite.itemdespawntowhat.core.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

/** 自动格式升级的单文件提交；调用方保留线程所有权，普通编辑的整批事务另行管理。 */
public final class JsonFileMigration {
    private JsonFileMigration() { }

    /** 文件与读取基线不同，调用方必须重新读取，不能发布过期内存副本。 */
    public static final class SourceChangedException extends IOException {
        /** @param file 已改变的文件 */
        public SourceChangedException(Path file) { super("迁移期间文件已被外部修改: " + file); }
    }

    /** 文件原始字节及迁移后的完整字节；仅在完整验证成功后创建。 */
    public record Replacement(Path file, byte[] original, byte[] current) { }

    /**
     * 保存独立备份并原子替换；原子移动不支持时保留源文件交由调用方报告。
     * @param replacement 同一次读取与校验产生的替换内容
     * @param backupDirectory 位于规则扫描目录外的迁移备份目录
     * @return 成功保留的原始字节备份
     * @throws IOException 源文变化或存储失败时
     */
    public static Path commit(Replacement replacement, Path backupDirectory) throws IOException {
        Path file = replacement.file().toAbsolutePath().normalize();
        requireSafePath(file);
        requireSafePath(backupDirectory);
        verifySource(replacement);
        Files.createDirectories(backupDirectory);
        Path backup = Files.createTempFile(backupDirectory, file.getFileName() + "-", ".bak");
        Path temporary = null;
        try {
            Files.write(backup, replacement.original());
            temporary = Files.createTempFile(file.getParent(), ".migration-", ".tmp");
            Files.write(temporary, replacement.current());
            verifySource(replacement);
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return backup;
        } finally {
            if (temporary != null) { Files.deleteIfExists(temporary); }
        }
    }

    /**
     * 自动写回与普通写入遵守相同的符号链接限制，防止跨出实际配置目录。
     * @param path 目标文件或目录
     * @throws IOException 任一祖先为符号链接时
     */
    public static void requireSafePath(Path path) throws IOException {
        for (Path current = path.toAbsolutePath().normalize(); current != null; current = current.getParent()) {
            if (Files.isSymbolicLink(current)) { throw new IOException("迁移路径不允许符号链接: " + current); }
        }
    }

    private static void verifySource(Replacement replacement) throws IOException {
        Path file = replacement.file();
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || !Arrays.equals(replacement.original(), Files.readAllBytes(file))) {
            throw new SourceChangedException(file);
        }
    }
}
