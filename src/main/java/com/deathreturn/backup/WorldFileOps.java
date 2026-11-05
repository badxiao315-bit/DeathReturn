package com.deathreturn.backup;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

/**
 * 世界目录级别的文件操作工具：复制 / 删除，带进度回调。
 * session.lock 在所有操作中被跳过（它只用于运行期加锁，回档后由新服务器重新创建）。
 */
public final class WorldFileOps {

    /** 永远不复制/不删除的文件名（相对于世界目录任意层级）。 */
    private static final Set<String> SKIP_FILES = Set.of("session.lock");

    public interface ProgressListener {
        /**
         * @param copiedBytes  已处理字节数
         * @param totalBytes   总字节数（未知时为 -1）
         */
        void onProgress(long copiedBytes, long totalBytes);
    }

    private WorldFileOps() {}

    public static long sizeOf(Path root) throws IOException {
        if (!Files.exists(root)) return 0;
        final long[] total = {0};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!SKIP_FILES.contains(file.getFileName().toString())) total[0] += attrs.size();
                return FileVisitResult.CONTINUE;
            }
        });
        return total[0];
    }

    /** 统计会被复制/校验的文件总数（排除 session.lock）。 */
    public static long countFiles(Path root) throws IOException {
        if (!Files.exists(root)) return 0;
        final long[] count = {0};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!SKIP_FILES.contains(file.getFileName().toString())) count[0]++;
                return FileVisitResult.CONTINUE;
            }
        });
        return count[0];
    }

    public static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                // Windows 上可能因残留句柄短暂失败，重试一次
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                try {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                } catch (IOException e) {
                    throw exc != null ? exc : e;
                }
            }
        });
    }

    public static void copyTree(Path from, Path to, ProgressListener progress) throws IOException {
        Files.createDirectories(to);
        long total = sizeOf(from);
        final long[] copied = {0};
        final ProgressListener cb = progress != null ? progress : (a, b) -> {};
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path target = to.resolve(from.relativize(dir).toString());
                Files.createDirectories(target);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (SKIP_FILES.contains(file.getFileName().toString())) return FileVisitResult.CONTINUE;
                Path target = to.resolve(from.relativize(file).toString());
                Files.createDirectories(target.getParent());
                Files.copy(file, target);
                copied[0] += attrs.size();
                cb.onProgress(copied[0], total);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** 确保目标目录的内容完全等于源目录的内容（先删后拷）。 */
    public static void mirrorTree(Path src, Path target, ProgressListener progress) throws IOException {
        deleteTree(target);
        Files.createDirectories(target);
        copyTree(src, target, progress);
    }
}
