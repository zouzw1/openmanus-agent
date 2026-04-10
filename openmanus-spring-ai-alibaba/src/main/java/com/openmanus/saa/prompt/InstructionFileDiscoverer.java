package com.openmanus.saa.prompt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 指令文件发现服务。
 *
 * <p>从工作目录向上遍历，发现项目级指令文件：
 * <ul>
 *   <li>MANUS.md - 根目录项目指令</li>
 *   <li>.manus.md - 隐藏的项目指令</li>
 *   <li>.manus/instructions.md - 子目录指令</li>
 *   <li>.manus/local.md - 本地覆盖指令</li>
 * </ul>
 *
 * <p>发现的文件会按内容哈希去重，并按总长度限制截断。
 */
@Service
public class InstructionFileDiscoverer {

    private static final Logger log = LoggerFactory.getLogger(InstructionFileDiscoverer.class);

    private static final List<String> INSTRUCTION_FILE_NAMES = List.of(
        "MANUS.md",
        ".manus.md",
        ".manus/instructions.md",
        ".manus/local.md"
    );

    private static final int MAX_FILE_CHARS = 4000;
    private static final int MAX_TOTAL_CHARS = 12000;

    @Value("${openmanus.workspace:./workspace}")
    private Path defaultWorkspace;

    /**
     * 从工作目录向上遍历，发现指令文件。
     *
     * @param cwd 工作目录
     * @return 发现的文件列表（已去重和截断）
     */
    public List<InstructionFile> discover(Path cwd) {
        if (cwd == null) {
            cwd = defaultWorkspace;
        }

        List<InstructionFile> files = new ArrayList<>();

        Path cursor = cwd;
        while (cursor != null) {
            for (String filename : INSTRUCTION_FILE_NAMES) {
                Path file = cursor.resolve(filename);
                if (Files.exists(file) && Files.isRegularFile(file)) {
                    try {
                        String content = Files.readString(file, StandardCharsets.UTF_8);
                        if (!content.isBlank()) {
                            files.add(new InstructionFile(file, content));
                            log.debug("Found instruction file: {}", file);
                        }
                    } catch (IOException e) {
                        log.debug("Failed to read instruction file {}: {}", file, e.getMessage());
                    }
                }
            }
            cursor = cursor.getParent();
        }

        return deduplicateAndLimit(files);
    }

    /**
     * 从默认工作目录发现指令文件。
     */
    public List<InstructionFile> discover() {
        return discover(null);
    }

    /**
     * 去重（基于内容哈希）+ 长度限制。
     */
    private List<InstructionFile> deduplicateAndLimit(List<InstructionFile> files) {
        Set<String> seenHashes = new java.util.HashSet<>();
        List<InstructionFile> result = new ArrayList<>();
        int totalChars = 0;

        for (InstructionFile file : files) {
            String normalized = normalizeContent(file.content());
            String hash = hash(normalized);

            if (!seenHashes.contains(hash)) {
                seenHashes.add(hash);

                String truncated = truncate(file.content(), MAX_FILE_CHARS);
                int newTotal = totalChars + truncated.length();

                if (newTotal <= MAX_TOTAL_CHARS) {
                    result.add(new InstructionFile(file.path(), truncated));
                    totalChars = newTotal;
                    log.debug("Added instruction file: {}, chars: {}", file.path(), truncated.length());
                } else {
                    log.info("Skipping instruction file {} due to total size limit", file.path());
                }
            } else {
                log.debug("Skipping duplicate instruction file: {}", file.path());
            }
        }

        return result;
    }

    /**
     * 规范化内容：折叠空行、去除首尾空白
     */
    private String normalizeContent(String content) {
        StringBuilder result = new StringBuilder();
        boolean previousBlank = false;

        for (String line : content.split("\n", -1)) {
            boolean isBlank = line.trim().isEmpty();
            if (isBlank && previousBlank) {
                continue;  // 跳过连续的空白行
            }
            result.append(line.trim()).append("\n");
            previousBlank = isBlank;
        }

        return result.toString().trim();
    }

    /**
     * 计算内容的稳定哈希
     */
    private String hash(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 总是存在
            return content;
        }
    }

    /**
     * 截断内容到最大长度
     */
    private String truncate(String content, int maxChars) {
        String trimmed = content.trim();
        int length = trimmed.length();

        if (length <= maxChars) {
            return trimmed;
        }

        return trimmed.substring(0, maxChars) + "\n[content truncated]";
    }
}
