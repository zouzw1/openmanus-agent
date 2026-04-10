package com.openmanus.saa.tool;

import com.openmanus.saa.agent.IdAccessPolicy;
import com.openmanus.saa.model.ToolMetadata;
import com.openmanus.saa.model.ToolMetadata.Category;
import com.openmanus.saa.model.ToolPermissionContext;
import com.openmanus.saa.service.ToolRegistryService;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 工具池服务
 *
 * <p>根据配置模式过滤工具，支持多种模式和安全控制。
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 获取简单模式的工具池
 * List<ToolMetadata> simpleTools = toolPoolService.assembleToolPool(ToolPoolConfig.simple());
 *
 * // 获取安全模式的工具池（排除危险操作）
 * List<ToolMetadata> safeTools = toolPoolService.assembleToolPool(ToolPoolConfig.safeOnly());
 *
 * // 自定义配置
 * ToolPoolConfig config = ToolPoolConfig.builder()
 *     .mode(ToolPoolConfig.Mode.FULL)
 *     .deniedTools(Set.of("runSandboxCommand"))
 *     .deniedPrefixes(Set.of("browser"))
 *     .deniedCategories(Set.of("SANDBOX"))
 *     .confirmDangerous(true)
 *     .build();
 * List<ToolMetadata> customTools = toolPoolService.assembleToolPool(config);
 * }</pre>
 */
@Service
public class ToolPoolService {

    private static final Logger log = LoggerFactory.getLogger(ToolPoolService.class);

    /**
     * 简单模式下允许的工具名称
     */
    private static final Set<String> SIMPLE_MODE_TOOLS = Set.of(
        "listWorkspaceFiles",
        "readWorkspaceFile",
        "writeWorkspaceFile",
        "runPowerShell"
    );

    private final ToolRegistryService registry;

    public ToolPoolService(ToolRegistryService registry) {
        this.registry = registry;
    }

    /**
     * 根据配置组装工具池
     *
     * @param config 工具池配置
     * @return 过滤后的工具列表
     */
    public List<ToolMetadata> assembleToolPool(ToolPoolConfig config) {
        ToolPoolConfig effectiveConfig = (config == null) ? ToolPoolConfig.full() : config;

        Collection<ToolMetadata> allTools = registry.getAllTools();

        return allTools.stream()
            .filter(tool -> filterByMode(tool, effectiveConfig))
            .filter(tool -> filterByEnabled(tool))
            .filter(tool -> filterByDangerous(tool, effectiveConfig))
            .filter(tool -> filterByCategories(tool, effectiveConfig))
            .filter(tool -> filterByPermission(tool, effectiveConfig))
            .collect(Collectors.toList());
    }

    /**
     * 根据 IdAccessPolicy 组装工具池
     *
     * @param policy 访问策略
     * @return 允许的工具列表
     */
    public List<ToolMetadata> assembleToolPool(IdAccessPolicy policy) {
        if (policy == null) {
            return new java.util.ArrayList<>(registry.getAllTools());
        }

        Set<String> allToolNames = registry.getAllTools().stream()
            .map(ToolMetadata::getName)
            .collect(Collectors.toSet());

        Set<String> allowedNames = policy.resolveAllowed(allToolNames);

        return registry.getAllTools().stream()
            .filter(tool -> allowedNames.contains(tool.getName()))
            .filter(tool -> filterByEnabled(tool))
            .collect(Collectors.toList());
    }

    /**
     * 根据权限上下文组装工具池
     *
     * @param context 权限上下文
     * @return 可用的工具列表
     */
    public List<ToolMetadata> assembleToolPool(ToolPermissionContext context) {
        if (context == null || context.isEmpty()) {
            return new java.util.ArrayList<>(registry.getAllTools());
        }

        return registry.getAllTools().stream()
            .filter(tool -> !context.blocks(tool))
            .filter(tool -> filterByEnabled(tool))
            .collect(Collectors.toList());
    }

    /**
     * 获取简单模式的工具池
     */
    public List<ToolMetadata> getSimpleTools() {
        return assembleToolPool(ToolPoolConfig.simple());
    }

    /**
     * 获取安全模式的工具池
     */
    public List<ToolMetadata> getSafeTools() {
        return assembleToolPool(ToolPoolConfig.safeOnly());
    }

    /**
     * 获取所有可用工具（基于 enable 配置）
     */
    public List<ToolMetadata> getEnabledTools() {
        return new java.util.ArrayList<>(registry.getEnabledTools());
    }

    /**
     * 根据模式过滤
     */
    private boolean filterByMode(ToolMetadata tool, ToolPoolConfig config) {
        if (config.getMode() == null || config.getMode() == ToolPoolConfig.Mode.FULL) {
            return true;
        }

        String toolName = tool.getName().toLowerCase(Locale.ROOT);
        Category category = tool.getCategory();

        return switch (config.getMode()) {
            case FULL -> true;
            case SIMPLE -> SIMPLE_MODE_TOOLS.contains(tool.getName())
                || (tool.getTags() != null && tool.getTags().contains("simple"));
            case MCP_ONLY -> category == Category.MCP;
            case NO_MCP -> category != Category.MCP;
            case SAFE_ONLY -> !tool.isDangerous();
        };
    }

    /**
     * 根据启用状态过滤
     */
    private boolean filterByEnabled(ToolMetadata tool) {
        return registry.isToolEnabled(tool.getName());
    }

    /**
     * 根据危险标记过滤
     */
    private boolean filterByDangerous(ToolMetadata tool, ToolPoolConfig config) {
        if (!config.isIncludeDangerous() && tool.isDangerous()) {
            return false;
        }
        return true;
    }

    /**
     * 根据分类过滤
     */
    private boolean filterByCategories(ToolMetadata tool, ToolPoolConfig config) {
        List<String> includeCategories = config.getIncludeCategories();
        List<String> excludeCategories = config.getExcludeCategories();

        // 排除分类检查
        if (!excludeCategories.isEmpty()) {
            String categoryName = tool.getCategory() != null
                ? tool.getCategory().name().toLowerCase(Locale.ROOT)
                : "other";
            if (excludeCategories.stream()
                .anyMatch(c -> c.equalsIgnoreCase(categoryName))) {
                return false;
            }
        }

        // 包含分类检查
        if (!includeCategories.isEmpty()) {
            String categoryName = tool.getCategory() != null
                ? tool.getCategory().name().toLowerCase(Locale.ROOT)
                : "other";
            if (!includeCategories.stream()
                .anyMatch(c -> c.equalsIgnoreCase(categoryName))) {
                return false;
            }
        }

        return true;
    }

    /**
     * 根据权限过滤
     */
    private boolean filterByPermission(ToolMetadata tool, ToolPoolConfig config) {
        String toolName = tool.getName().toLowerCase(Locale.ROOT);

        // 名称检查
        if (!config.getDeniedTools().isEmpty()
            && config.getDeniedTools().stream()
                .anyMatch(d -> d.equalsIgnoreCase(toolName))) {
            return false;
        }

        // 前缀检查
        if (!config.getDeniedPrefixes().isEmpty()
            && config.getDeniedPrefixes().stream()
                .anyMatch(prefix -> toolName.startsWith(prefix.toLowerCase(Locale.ROOT)))) {
            return false;
        }

        // 分类检查
        if (!config.getDeniedCategories().isEmpty()
            && tool.getCategory() != null
            && config.getDeniedCategories().stream()
                .anyMatch(c -> c.equalsIgnoreCase(tool.getCategory().name()))) {
            return false;
        }

        return true;
    }

    /**
     * 检查工具是否需要确认
     */
    public boolean requiresConfirmation(ToolMetadata tool) {
        return tool.isDangerous();
    }

    /**
     * 获取需要确认的工具列表
     */
    public List<ToolMetadata> getDangerousTools(ToolPoolConfig config) {
        return assembleToolPool(config).stream()
            .filter(ToolMetadata::isDangerous)
            .collect(Collectors.toList());
    }

    /**
     * 按分类分组工具
     */
    public List<ToolMetadata> getToolsByCategory(ToolPoolConfig config, Category category) {
        return assembleToolPool(config).stream()
            .filter(tool -> tool.getCategory() == category)
            .collect(Collectors.toList());
    }

    /**
     * 获取工具统计信息
     */
    public ToolPoolStats getStats(ToolPoolConfig config) {
        List<ToolMetadata> tools = assembleToolPool(config);

        int dangerous = (int) tools.stream().filter(ToolMetadata::isDangerous).count();
        int mcp = (int) tools.stream()
            .filter(t -> t.getCategory() == Category.MCP).count();
        int requiresConfirm = (int) tools.stream()
            .filter(ToolMetadata::isRequiresConfirm).count();

        return new ToolPoolStats(tools.size(), dangerous, mcp, requiresConfirm);
    }

    /**
     * 工具池统计信息
     */
    public record ToolPoolStats(
        int total,
        int dangerous,
        int mcp,
        int requiresConfirm
    ) {}
}
