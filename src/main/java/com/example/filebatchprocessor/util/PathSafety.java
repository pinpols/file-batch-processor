package com.example.filebatchprocessor.util;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.util.StringUtils;

/**
 * 文件路径安全校验,防路径穿越(任意读/写)。
 *
 * <ul>
 *   <li>配置了 baseDir:候选路径必须规范化后仍落在 baseDir 之内,否则拒绝;
 *   <li>未配置 baseDir:退化为拒绝包含 {@code ..} 逃逸段的相对/绝对路径(最低限度防护)。
 * </ul>
 */
public final class PathSafety {

    private PathSafety() {}

    /**
     * @param baseDir 允许的基目录(可空/空白表示不强制限定)
     * @param candidate 待校验路径
     * @return 规范化后的绝对路径字符串
     * @throws IllegalArgumentException 越界或非法时
     */
    public static String confine(String baseDir, String candidate) {
        if (!StringUtils.hasText(candidate)) {
            throw new IllegalArgumentException("file path is required");
        }
        Path candidatePath = Paths.get(candidate).normalize();

        if (StringUtils.hasText(baseDir)) {
            Path base = Paths.get(baseDir).toAbsolutePath().normalize();
            Path resolved = (candidatePath.isAbsolute() ? candidatePath : base.resolve(candidatePath))
                    .normalize();
            if (!resolved.startsWith(base)) {
                throw new IllegalArgumentException(
                        "path escapes allowed base dir: " + candidate + " (base=" + baseDir + ")");
            }
            // 符号链接硬化:对已存在的路径解析真实路径(消解 symlink),再次校验仍在 base 内。
            // 不存在的路径(如待写出的输出文件)无法 toRealPath,保留上面的 normalize 校验即可。
            try {
                if (java.nio.file.Files.exists(resolved)) {
                    Path realResolved = resolved.toRealPath();
                    Path realBase = java.nio.file.Files.exists(base) ? base.toRealPath() : base;
                    if (!realResolved.startsWith(realBase)) {
                        throw new IllegalArgumentException(
                                "path escapes allowed base dir via symlink: " + candidate + " (base=" + baseDir + ")");
                    }
                    return realResolved.toString();
                }
            } catch (java.io.IOException e) {
                throw new IllegalArgumentException("failed to resolve real path: " + candidate, e);
            }
            return resolved.toString();
        }

        // 无 baseDir:至少拒绝 .. 逃逸
        if (candidate.contains("..") || candidatePath.toString().contains("..")) {
            throw new IllegalArgumentException("path traversal segment '..' is not allowed: " + candidate);
        }
        return candidatePath.toString();
    }
}
