package com.openmanus.saa.tool;

import com.openmanus.saa.model.ToolMetadata.Category;
import java.util.List;
import java.util.Set;

/**
 * 工具池配置
 *
 * <p>定义工具池的过滤模式和权限控制。
 *
 * <h3>模式说明：</h3>
 * <ul>
 *   <li>FULL - 完整模式：所有工具</li>
 *   <li>SIMPLE - 简单模式：仅基础工具（文件、Shell 读操作）</li>
 *   <li>MCP_ONLY - 仅 MCP 工具</li>
 *   <li>NO_MCP - 排除 MCP 工具</li>
 *   <li>SAFE_ONLY - 仅安全工具（非危险操作）</li>
 * </ul>
 */
public class ToolPoolConfig {

    /**
     * 工具池模式
     */
    public enum Mode {
        /**
         * 完整模式：所有工具
         */
        FULL,

        /**
         * 简单模式：仅基础工具（文件读、Shell）
         */
        SIMPLE,

        /**
         * 仅 MCP 工具
         */
        MCP_ONLY,

        /**
         * 排除 MCP 工具
         */
        NO_MCP,

        /**
         * 仅安全工具
         */
        SAFE_ONLY
    }

    private Mode mode = Mode.FULL;
    private boolean includeMcp = true;
    private boolean includeDangerous = true;
    private List<String> includeCategories = List.of();
    private List<String> excludeCategories = List.of();
    private Set<String> deniedTools = Set.of();
    private Set<String> deniedPrefixes = Set.of();
    private Set<String> deniedCategories = Set.of();
    private boolean confirmDangerous = false;

    public ToolPoolConfig() {
    }

    private ToolPoolConfig(Mode mode, boolean includeMcp, boolean includeDangerous,
                          List<String> includeCategories, List<String> excludeCategories,
                          Set<String> deniedTools, Set<String> deniedPrefixes,
                          Set<String> deniedCategories, boolean confirmDangerous) {
        this.mode = mode;
        this.includeMcp = includeMcp;
        this.includeDangerous = includeDangerous;
        this.includeCategories = includeCategories;
        this.excludeCategories = excludeCategories;
        this.deniedTools = deniedTools;
        this.deniedPrefixes = deniedPrefixes;
        this.deniedCategories = deniedCategories;
        this.confirmDangerous = confirmDangerous;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public boolean isIncludeMcp() {
        return includeMcp;
    }

    public void setIncludeMcp(boolean includeMcp) {
        this.includeMcp = includeMcp;
    }

    public boolean isIncludeDangerous() {
        return includeDangerous;
    }

    public void setIncludeDangerous(boolean includeDangerous) {
        this.includeDangerous = includeDangerous;
    }

    public List<String> getIncludeCategories() {
        return includeCategories;
    }

    public void setIncludeCategories(List<String> includeCategories) {
        this.includeCategories = includeCategories;
    }

    public List<String> getExcludeCategories() {
        return excludeCategories;
    }

    public void setExcludeCategories(List<String> excludeCategories) {
        this.excludeCategories = excludeCategories;
    }

    public Set<String> getDeniedTools() {
        return deniedTools;
    }

    public void setDeniedTools(Set<String> deniedTools) {
        this.deniedTools = deniedTools;
    }

    public Set<String> getDeniedPrefixes() {
        return deniedPrefixes;
    }

    public void setDeniedPrefixes(Set<String> deniedPrefixes) {
        this.deniedPrefixes = deniedPrefixes;
    }

    public Set<String> getDeniedCategories() {
        return deniedCategories;
    }

    public void setDeniedCategories(Set<String> deniedCategories) {
        this.deniedCategories = deniedCategories;
    }

    public boolean isConfirmDangerous() {
        return confirmDangerous;
    }

    public void setConfirmDangerous(boolean confirmDangerous) {
        this.confirmDangerous = confirmDangerous;
    }

    // ==================== 工厂方法 ====================

    /**
     * 完整模式配置
     */
    public static ToolPoolConfig full() {
        return new ToolPoolConfigBuilder().mode(Mode.FULL).build();
    }

    /**
     * 简单模式配置
     */
    public static ToolPoolConfig simple() {
        return new ToolPoolConfigBuilder().mode(Mode.SIMPLE).build();
    }

    /**
     * 安全模式配置
     */
    public static ToolPoolConfig safeOnly() {
        return new ToolPoolConfigBuilder()
            .mode(Mode.SAFE_ONLY)
            .includeDangerous(false)
            .build();
    }

    // ==================== Builder ====================

    public static ToolPoolConfigBuilder builder() {
        return new ToolPoolConfigBuilder();
    }

    public static class ToolPoolConfigBuilder {
        private Mode mode = Mode.FULL;
        private boolean includeMcp = true;
        private boolean includeDangerous = true;
        private List<String> includeCategories = List.of();
        private List<String> excludeCategories = List.of();
        private Set<String> deniedTools = Set.of();
        private Set<String> deniedPrefixes = Set.of();
        private Set<String> deniedCategories = Set.of();
        private boolean confirmDangerous = false;

        public ToolPoolConfigBuilder mode(Mode mode) {
            this.mode = mode;
            return this;
        }

        public ToolPoolConfigBuilder includeMcp(boolean includeMcp) {
            this.includeMcp = includeMcp;
            return this;
        }

        public ToolPoolConfigBuilder includeDangerous(boolean includeDangerous) {
            this.includeDangerous = includeDangerous;
            return this;
        }

        public ToolPoolConfigBuilder includeCategories(List<String> includeCategories) {
            this.includeCategories = includeCategories;
            return this;
        }

        public ToolPoolConfigBuilder excludeCategories(List<String> excludeCategories) {
            this.excludeCategories = excludeCategories;
            return this;
        }

        public ToolPoolConfigBuilder deniedTools(Set<String> deniedTools) {
            this.deniedTools = deniedTools;
            return this;
        }

        public ToolPoolConfigBuilder deniedPrefixes(Set<String> deniedPrefixes) {
            this.deniedPrefixes = deniedPrefixes;
            return this;
        }

        public ToolPoolConfigBuilder deniedCategories(Set<String> deniedCategories) {
            this.deniedCategories = deniedCategories;
            return this;
        }

        public ToolPoolConfigBuilder confirmDangerous(boolean confirmDangerous) {
            this.confirmDangerous = confirmDangerous;
            return this;
        }

        public ToolPoolConfig build() {
            return new ToolPoolConfig(mode, includeMcp, includeDangerous,
                includeCategories, excludeCategories, deniedTools,
                deniedPrefixes, deniedCategories, confirmDangerous);
        }
    }

    @Override
    public String toString() {
        return "ToolPoolConfig{" +
            "mode=" + mode +
            ", includeMcp=" + includeMcp +
            ", includeDangerous=" + includeDangerous +
            ", includeCategories=" + includeCategories +
            ", excludeCategories=" + excludeCategories +
            ", deniedTools=" + deniedTools +
            ", deniedPrefixes=" + deniedPrefixes +
            ", deniedCategories=" + deniedCategories +
            ", confirmDangerous=" + confirmDangerous +
            '}';
    }
}
