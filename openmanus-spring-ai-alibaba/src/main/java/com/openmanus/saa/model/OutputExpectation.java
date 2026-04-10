package com.openmanus.saa.model;

/**
 * 输出产物期望
 * 用于描述用户对输出形式的期望
 */
public record OutputExpectation(
        boolean needsFile,
        String userSpecifiedFormat,
        String reason
) {
    /**
     * 默认构造函数：不需要文件，文本输出
     */
    public OutputExpectation() {
        this(false, null, null);
    }

    /**
     * 简化构造函数
     */
    public OutputExpectation(boolean needsFile) {
        this(needsFile, null, null);
    }

    /**
     * 是否用户显式指定了格式
     */
    public boolean hasUserSpecifiedFormat() {
        return userSpecifiedFormat != null && !userSpecifiedFormat.isBlank();
    }

    /**
     * 获取有效格式（用户指定或默认文本）
     */
    public String getEffectiveFormat() {
        if (hasUserSpecifiedFormat()) {
            return userSpecifiedFormat.toLowerCase();
        }
        return needsFile ? "markdown" : "text";
    }

    /**
     * 不需要文件的默认实例
     */
    public static OutputExpectation textOutput() {
        return new OutputExpectation(false, null, "用户未要求文件载体，返回文本");
    }

    /**
     * 需要文件的实例（系统推断格式）
     */
    public static OutputExpectation fileOutput(String reason) {
        return new OutputExpectation(true, null, reason);
    }

    /**
     * 用户指定格式的实例
     */
    public static OutputExpectation userSpecified(String format, String reason) {
        return new OutputExpectation(true, format, reason);
    }
}