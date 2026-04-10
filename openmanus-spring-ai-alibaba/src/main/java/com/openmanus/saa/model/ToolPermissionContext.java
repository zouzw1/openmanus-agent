package com.openmanus.saa.model;

import com.openmanus.saa.model.ToolMetadata.Category;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工具权限上下文（参考 Claw-Code 的 ToolPermissionContext）
 *
 * <p>提供统一的权限检查接口，支持按名称、前缀、分类三个维度拒绝访问。
 *
 * <h3>使用方式：</h3>
 * <pre>{@code
 * ToolPermissionContext ctx = ToolPermissionContext.builder()
 *     .denyTools(Set.of("runSandboxCommand", "browserAction"))
 *     .denyPrefixes(Set.of("browser"))
 *     .denyCategories(Set.of("SANDBOX"))
 *     .build();
 *
 * if (ctx.blocks(toolMetadata)) {
 *     // 工具不可用
 * }
 * }</pre>
 */
public class ToolPermissionContext {

    private final Set<String> deniedTools;
    private final Set<String> deniedPrefixes;
    private final Set<String> deniedCategories;

    private ToolPermissionContext(Set<String> deniedTools,
                                   Set<String> deniedPrefixes,
                                   Set<String> deniedCategories) {
        this.deniedTools = deniedTools;
        this.deniedPrefixes = deniedPrefixes;
        this.deniedCategories = deniedCategories;
    }

    /**
     * 检查工具是否被阻止访问
     *
     * @param tool 工具元数据
     * @return 是否阻止访问
     */
    public boolean blocks(ToolMetadata tool) {
        if (tool == null || tool.getName() == null) {
            return false;
        }

        String loweredName = tool.getName().toLowerCase(Locale.ROOT);

        // 1. 检查名称精确拒绝
        if (deniedTools.contains(loweredName)) {
            return true;
        }

        // 2. 检查前缀拒绝
        if (deniedPrefixes.stream().anyMatch(loweredName::startsWith)) {
            return true;
        }

        // 3. 检查分类拒绝
        if (tool.getCategory() != null
            && deniedCategories.contains(tool.getCategory().name().toLowerCase(Locale.ROOT))) {
            return true;
        }

        return false;
    }

    /**
     * 检查工具是否被阻止访问（使用工具名称）
     */
    public boolean blocks(String toolName) {
        if (toolName == null) {
            return false;
        }
        String loweredName = toolName.toLowerCase(Locale.ROOT);

        if (deniedTools.contains(loweredName)) {
            return true;
        }

        return deniedPrefixes.stream().anyMatch(loweredName::startsWith);
    }

    /**
     * 检查分类是否被拒绝
     */
    public boolean blocksCategory(Category category) {
        if (category == null) {
            return false;
        }
        return deniedCategories.contains(category.name().toLowerCase(Locale.ROOT));
    }

    public Set<String> getDeniedTools() {
        return deniedTools;
    }

    public Set<String> getDeniedPrefixes() {
        return deniedPrefixes;
    }

    public Set<String> getDeniedCategories() {
        return deniedCategories;
    }

    public boolean isEmpty() {
        return deniedTools.isEmpty()
            && deniedPrefixes.isEmpty()
            && deniedCategories.isEmpty();
    }

    // ==================== 工厂方法 ====================

    /**
     * 空的权限上下文（不阻止任何工具）
     */
    public static ToolPermissionContext empty() {
        return new ToolPermissionContext(Set.of(), Set.of(), Set.of());
    }

    /**
     * 从配置列表创建
     *
     * @param denyTools 拒绝的工具名称列表
     * @param denyPrefixes 拒绝的工具前缀列表
     * @param denyCategories 拒绝的分类列表
     */
    public static ToolPermissionContext fromConfig(
            List<String> denyTools,
            List<String> denyPrefixes,
            List<String> denyCategories) {
        return new ToolPermissionContext(
            toLowerSet(denyTools),
            toLowerSet(denyPrefixes),
            toLowerSet(denyCategories)
        );
    }

    /**
     * 从 IdAccessPolicy 转换
     */
    public static ToolPermissionContext fromIdAccessPolicy(
            com.openmanus.saa.agent.IdAccessPolicy policy) {
        if (policy == null) {
            return empty();
        }

        Set<String> denied = Set.of();
        Set<String> deniedPrefixes = Set.of();

        if (policy.getMode() == com.openmanus.saa.agent.CapabilityAccessMode.DENY_LIST) {
            denied = policy.getIds().stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
            deniedPrefixes = policy.getDenyPrefixes().stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        }

        return new ToolPermissionContext(denied, deniedPrefixes, Set.of());
    }

    private static Set<String> toLowerSet(List<String> list) {
        if (list == null || list.isEmpty()) {
            return Set.of();
        }
        return list.stream()
            .filter(s -> s != null && !s.isBlank())
            .map(s -> s.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Set<String> deniedTools = Set.of();
        private Set<String> deniedPrefixes = Set.of();
        private Set<String> deniedCategories = Set.of();

        public Builder deniedTools(Set<String> deniedTools) {
            this.deniedTools = deniedTools != null ? deniedTools : Set.of();
            return this;
        }

        public Builder deniedTools(String... tools) {
            this.deniedTools = Set.of(tools);
            return this;
        }

        public Builder deniedPrefixes(Set<String> deniedPrefixes) {
            this.deniedPrefixes = deniedPrefixes != null ? deniedPrefixes : Set.of();
            return this;
        }

        public Builder deniedPrefixes(String... prefixes) {
            this.deniedPrefixes = Set.of(prefixes);
            return this;
        }

        public Builder deniedCategories(Set<String> deniedCategories) {
            this.deniedCategories = deniedCategories != null ? deniedCategories : Set.of();
            return this;
        }

        public Builder deniedCategories(Category... categories) {
            this.deniedCategories = Set.of(
                java.util.Arrays.stream(categories)
                    .map(c -> c.name().toLowerCase(Locale.ROOT))
                    .toArray(String[]::new)
            );
            return this;
        }

        public ToolPermissionContext build() {
            return new ToolPermissionContext(deniedTools, deniedPrefixes, deniedCategories);
        }
    }

    @Override
    public String toString() {
        return "ToolPermissionContext{" +
            "deniedTools=" + deniedTools +
            ", deniedPrefixes=" + deniedPrefixes +
            ", deniedCategories=" + deniedCategories +
            '}';
    }
}
