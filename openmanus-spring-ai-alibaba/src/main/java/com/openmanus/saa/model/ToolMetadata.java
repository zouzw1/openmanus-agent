package com.openmanus.saa.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具元数据模型，描述工具的结构化信息
 */
public class ToolMetadata {

    private final String name;
    private final String description;
    private final List<ParameterSchema> parameters;
    private final Map<String, Object> returns;

    // 增强字段
    private final Category category;
    private final Set<String> tags;
    private final boolean dangerous;
    private final boolean requiresConfirm;
    private final String version;

    /**
     * 工具分类枚举
     */
    public enum Category {
        FILE,           // 文件操作
        SHELL,          // Shell 命令
        MCP,            // MCP 工具
        RAG,            // 知识检索
        PLANNING,       // 计划管理
        BROWSER,        // 浏览器自动化
        SANDBOX,        // 沙箱执行
        SKILL,          // 技能调用
        TRAVEL_POI,     // POI 搜索工具
        TRAVEL_ROUTE,   // 路线规划工具
        WEATHER,        // 天气工具（通用）
        TRAVEL_INFO,    // 旅游信息工具
        OTHER           // 其他
    }

    public ToolMetadata(String name, String description, List<ParameterSchema> parameters, Map<String, Object> returns) {
        this(name, description, parameters, returns, Category.OTHER, Set.of(), false, false, "1.0.0");
    }

    public ToolMetadata(String name, String description, List<ParameterSchema> parameters,
                        Map<String, Object> returns, Category category, Set<String> tags,
                        boolean dangerous, boolean requiresConfirm, String version) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
        this.returns = returns;
        this.category = category != null ? category : Category.OTHER;
        this.tags = tags != null ? Set.copyOf(tags) : Set.of();
        this.dangerous = dangerous;
        this.requiresConfirm = requiresConfirm;
        this.version = version != null ? version : "1.0.0";
    }

    /**
     * 参数 Schema 定义
     */
    public static class ParameterSchema {
        private final String name;
        private final String type; // string, number, boolean, object, array
        private final String description;
        private final boolean required;
        private final Object defaultValue;
        private final List<String> enumValues; // 如果是枚举类型

        public ParameterSchema(String name, String type, String description, boolean required,
                              Object defaultValue, List<String> enumValues) {
            this.name = name;
            this.type = type;
            this.description = description;
            this.required = required;
            this.defaultValue = defaultValue;
            this.enumValues = enumValues;
        }

        public String getName() { return name; }
        public String getType() { return type; }
        public String getDescription() { return description; }
        public boolean isRequired() { return required; }
        public Object getDefaultValue() { return defaultValue; }
        public List<String> getEnumValues() { return enumValues; }

        /**
         * 生成 JSON Schema 格式的参数字段描述
         */
        public String toJsonSchemaField() {
            StringBuilder sb = new StringBuilder();
            sb.append("\"").append(name).append("\": {\n");
            sb.append("  \"type\": \"").append(type).append("\",\n");
            sb.append("  \"description\": \"").append(description).append("\"");
            if (!required) {
                sb.append(",\n  \"optional\": true");
            }
            if (defaultValue != null) {
                sb.append(",\n  \"default\": ").append(defaultValue);
            }
            if (enumValues != null && !enumValues.isEmpty()) {
                sb.append(",\n  \"enum\": ").append(enumValues);
            }
            sb.append("\n}");
            return sb.toString();
        }

        @Override
        public String toString() {
            return String.format("%s (%s)%s - %s",
                name, type, required ? " [required]" : " [optional]", description);
        }
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<ParameterSchema> getParameters() { return parameters; }
    public Map<String, Object> getReturns() { return returns; }
    public Category getCategory() { return category; }
    public Set<String> getTags() { return tags; }
    public boolean isDangerous() { return dangerous; }
    public boolean isRequiresConfirm() { return requiresConfirm; }
    public String getVersion() { return version; }

    /**
     * 检查是否属于指定分类
     */
    public boolean belongsTo(Category category) {
        return this.category == category;
    }

    /**
     * 检查是否包含指定标签
     */
    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    /**
     * 检查是否为安全工具（非危险操作）
     */
    public boolean isSafe() {
        return !dangerous;
    }

    /**
     * 生成完整的 JSON Schema
     */
    public String toJsonSchema() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"name\": \"").append(name).append("\",\n");
        sb.append("  \"description\": \"").append(description).append("\",\n");
        sb.append("  \"category\": \"").append(category.name()).append("\",\n");
        sb.append("  \"dangerous\": ").append(dangerous).append(",\n");
        sb.append("  \"parameters\": {\n");
        sb.append("    \"type\": \"object\",\n");
        sb.append("    \"properties\": {\n");

        for (int i = 0; i < parameters.size(); i++) {
            ParameterSchema param = parameters.get(i);
            sb.append("      ").append(param.toJsonSchemaField());
            if (i < parameters.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }

        sb.append("    }\n");

        // Required fields
        List<String> requiredFields = parameters.stream()
            .filter(ParameterSchema::isRequired)
            .map(ParameterSchema::getName)
            .toList();

        if (!requiredFields.isEmpty()) {
            sb.append("    ,\"required\": ").append(requiredFields.toString()).append("\n");
        }

        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * 生成用于 LLM Prompt 的参数抽取指导
     */
    public String toPromptGuidance() {
        StringBuilder sb = new StringBuilder();
        sb.append("Tool: ").append(name);
        if (dangerous) {
            sb.append(" [DANGEROUS]");
        }
        sb.append("\n");
        sb.append("Description: ").append(description).append("\n");
        sb.append("Category: ").append(category.name()).append("\n\n");
        sb.append("Required Parameters:\n");

        for (ParameterSchema param : parameters) {
            if (param.isRequired()) {
                sb.append("  - ").append(param.getName())
                  .append(" (").append(param.getType()).append("): ")
                  .append(param.getDescription()).append("\n");
            }
        }

        List<ParameterSchema> optionalParams = parameters.stream()
            .filter(p -> !p.isRequired())
            .toList();

        if (!optionalParams.isEmpty()) {
            sb.append("\nOptional Parameters:\n");
            for (ParameterSchema param : optionalParams) {
                sb.append("  - ").append(param.getName())
                  .append(" (").append(param.getType()).append("): ")
                  .append(param.getDescription());
                if (param.getDefaultValue() != null) {
                    sb.append(" [default: ").append(param.getDefaultValue()).append("]");
                }
                sb.append("\n");
            }
        }

        sb.append("\nExpected Output Format:\n");
        sb.append("  ").append(returns.getOrDefault("description", "Result of the operation")).append("\n");

        return sb.toString();
    }

    /**
     * 创建 Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String description;
        private List<ParameterSchema> parameters = List.of();
        private Map<String, Object> returns = Map.of();
        private Category category = Category.OTHER;
        private Set<String> tags = Set.of();
        private boolean dangerous = false;
        private boolean requiresConfirm = false;
        private String version = "1.0.0";

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder parameters(List<ParameterSchema> parameters) {
            this.parameters = parameters;
            return this;
        }

        public Builder returns(Map<String, Object> returns) {
            this.returns = returns;
            return this;
        }

        public Builder category(Category category) {
            this.category = category;
            return this;
        }

        public Builder tags(Set<String> tags) {
            this.tags = tags;
            return this;
        }

        public Builder dangerous(boolean dangerous) {
            this.dangerous = dangerous;
            return this;
        }

        public Builder requiresConfirm(boolean requiresConfirm) {
            this.requiresConfirm = requiresConfirm;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public ToolMetadata build() {
            return new ToolMetadata(name, description, parameters, returns,
                category, tags, dangerous, requiresConfirm, version);
        }
    }
}
