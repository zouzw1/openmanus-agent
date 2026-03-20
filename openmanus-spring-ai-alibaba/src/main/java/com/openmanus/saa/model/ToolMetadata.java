package com.openmanus.saa.model;

import java.util.List;
import java.util.Map;

/**
 * 工具元数据模型，描述工具的结构化信息
 */
public class ToolMetadata {

    private final String name;
    private final String description;
    private final List<ParameterSchema> parameters;
    private final Map<String, Object> returns;

    public ToolMetadata(String name, String description, List<ParameterSchema> parameters, Map<String, Object> returns) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
        this.returns = returns;
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

    /**
     * 生成完整的 JSON Schema
     */
    public String toJsonSchema() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"name\": \"").append(name).append("\",\n");
        sb.append("  \"description\": \"").append(description).append("\",\n");
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
        sb.append("Tool: ").append(name).append("\n");
        sb.append("Description: ").append(description).append("\n\n");
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
}
