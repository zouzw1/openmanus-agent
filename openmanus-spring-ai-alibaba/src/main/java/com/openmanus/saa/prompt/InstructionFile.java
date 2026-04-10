package com.openmanus.saa.prompt;

import java.nio.file.Path;

/**
 * 指令文件记录。
 * 表示一个发现的指令文件及其内容。
 */
public record InstructionFile(
    /**
     * 文件路径
     */
    Path path,

    /**
     * 文件内容（已截断到最大长度）
     */
    String content
) {

    /**
     * 获取文件名
     */
    public String fileName() {
        return path != null ? path.getFileName().toString() : "";
    }

    /**
     * 获取文件所在目录
     */
    public Path parent() {
        return path != null ? path.getParent() : null;
    }
}
