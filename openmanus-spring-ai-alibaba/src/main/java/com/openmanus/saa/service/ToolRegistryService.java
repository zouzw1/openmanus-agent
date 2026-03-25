package com.openmanus.saa.service;

import com.openmanus.saa.model.ToolMetadata;
import com.openmanus.saa.model.ToolMetadata.ParameterSchema;
import com.openmanus.saa.config.BrowserProperties;
import com.openmanus.saa.config.OpenManusProperties;
import com.openmanus.saa.config.SandboxProperties;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.stereotype.Component;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ReflectionUtils;

/**
 * Tool metadata registry backed by the actual @Tool methods available in the application.
 */
@org.springframework.stereotype.Service
public class ToolRegistryService implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistryService.class);

    private final ApplicationContext applicationContext;
    private final OpenManusProperties openManusProperties;
    private final BrowserProperties browserProperties;
    private final SandboxProperties sandboxProperties;
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final Map<String, ToolMetadata> toolRegistry = new ConcurrentHashMap<>();

    public ToolRegistryService(
            ApplicationContext applicationContext,
            OpenManusProperties openManusProperties,
            BrowserProperties browserProperties,
            SandboxProperties sandboxProperties
    ) {
        this.applicationContext = applicationContext;
        this.openManusProperties = openManusProperties;
        this.browserProperties = browserProperties;
        this.sandboxProperties = sandboxProperties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        reloadDiscoveredTools();
    }

    public void registerTool(ToolMetadata metadata) {
        toolRegistry.put(metadata.getName(), metadata);
        log.info("Registered tool metadata: {} with {} parameters", metadata.getName(), metadata.getParameters().size());
    }

    public Optional<ToolMetadata> getTool(String toolName) {
        return Optional.ofNullable(toolRegistry.get(toolName));
    }

    public Collection<ToolMetadata> getAllTools() {
        return toolRegistry.values().stream()
                .sorted(Comparator.comparing(ToolMetadata::getName))
                .toList();
    }

    public Collection<ToolMetadata> getEnabledTools() {
        return getAllTools().stream()
                .filter(tool -> isToolEnabled(tool.getName()))
                .toList();
    }

    public boolean isToolEnabled(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }
        return switch (toolName.trim()) {
            case "runPowerShell" -> openManusProperties.isShellEnabled();
            case "runSandboxCommand" -> sandboxProperties.isEnabled();
            case "browserAction" -> browserProperties.isEnabled();
            default -> true;
        };
    }

    public String generateAllToolsJsonSchema() {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");

        List<ToolMetadata> tools = new ArrayList<>(getAllTools());
        for (int i = 0; i < tools.size(); i++) {
            ToolMetadata tool = tools.get(i);
            sb.append(tool.toJsonSchema());
            if (i < tools.size() - 1) {
                sb.append(",");
            }
        }

        sb.append("]");
        return sb.toString();
    }

    public String generateToolsPromptGuidance() {
        return generateToolsPromptGuidance(getAllTools().stream().map(ToolMetadata::getName).toList());
    }

    public String generateEnabledToolsPromptGuidance() {
        return generateToolsPromptGuidance(getEnabledTools().stream().map(ToolMetadata::getName).toList());
    }

    public String generateToolsPromptGuidance(Collection<String> allowedToolNames) {
        StringBuilder sb = new StringBuilder();
        sb.append("AVAILABLE LOCAL TOOLS AND THEIR PARAMETER SCHEMAS:\n\n");

        List<ToolMetadata> filteredTools = getAllTools().stream()
                .filter(tool -> allowedToolNames == null || allowedToolNames.isEmpty() || allowedToolNames.contains(tool.getName()))
                .toList();

        if (filteredTools.isEmpty()) {
            sb.append("- No local tools are enabled for this runtime.\n\n");
        }

        for (ToolMetadata tool : filteredTools) {
            sb.append("========================================\n");
            sb.append(tool.toPromptGuidance());
            sb.append("\n");
        }

        sb.append("\n========================================\n");
        sb.append("IMPORTANT: When using any tool, you MUST provide ALL required parameters.\n");
        sb.append("If a required parameter is missing, check the conversation history first.\n");
        sb.append("If not found in history, ask the user explicitly for that parameter.\n");
        sb.append("NEVER assume default values for required parameters!\n");

        return sb.toString();
    }

    public boolean validateParameters(String toolName, Map<String, Object> parameters) {
        ToolMetadata tool = toolRegistry.get(toolName);
        if (tool == null) {
            log.warn("Unknown tool: {}", toolName);
            return false;
        }

        for (ParameterSchema param : tool.getParameters()) {
            if (param.isRequired() && !parameters.containsKey(param.getName())) {
                log.warn("Missing required parameter '{}' for tool '{}'", param.getName(), toolName);
                return false;
            }
        }

        return true;
    }

    public List<String> getMissingRequiredParameters(String toolName, Map<String, Object> parameters) {
        List<String> missing = new ArrayList<>();
        ToolMetadata tool = toolRegistry.get(toolName);

        if (tool != null) {
            for (ParameterSchema param : tool.getParameters()) {
                if (param.isRequired() && !parameters.containsKey(param.getName())) {
                    missing.add(param.getName());
                }
            }
        }

        return missing;
    }

    private void reloadDiscoveredTools() {
        toolRegistry.clear();
        applicationContext.getBeansWithAnnotation(Component.class).values().stream()
                .filter(this::isToolBean)
                .map(AopUtils::getTargetClass)
                .distinct()
                .forEach(this::registerToolsFromClass);
        log.info("Discovered {} local tool definitions", toolRegistry.size());
    }

    private boolean isToolBean(Object bean) {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        return targetClass != null && hasToolMethods(targetClass);
    }

    private void registerToolsFromClass(Class<?> toolClass) {
        ReflectionUtils.doWithMethods(
                toolClass,
                method -> registerTool(buildMetadata(method)),
                method -> method.isAnnotationPresent(Tool.class) && !method.isSynthetic() && !method.isBridge()
        );
    }

    private boolean hasToolMethods(Class<?> toolClass) {
        final boolean[] found = {false};
        ReflectionUtils.doWithMethods(
                toolClass,
                method -> found[0] = true,
                method -> method.isAnnotationPresent(Tool.class) && !method.isSynthetic() && !method.isBridge()
        );
        return found[0];
    }

    private ToolMetadata buildMetadata(Method method) {
        Tool tool = method.getAnnotation(Tool.class);
        String toolName = resolveToolName(tool, method);
        String description = tool.description() == null || tool.description().isBlank()
                ? "No description provided."
                : tool.description().trim();

        return new ToolMetadata(
                toolName,
                description,
                buildParameterSchemas(method),
                Map.of(
                        "description", describeReturnType(method),
                        "javaType", method.getGenericReturnType().getTypeName()
                )
        );
    }

    private List<ParameterSchema> buildParameterSchemas(Method method) {
        Parameter[] parameters = method.getParameters();
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        List<ParameterSchema> schemas = new ArrayList<>(parameters.length);

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            ToolParam toolParam = parameter.getAnnotation(ToolParam.class);
            String name = parameterNames != null && i < parameterNames.length && parameterNames[i] != null
                    ? parameterNames[i]
                    : parameter.getName();
            String description = toolParam != null && toolParam.description() != null && !toolParam.description().isBlank()
                    ? toolParam.description().trim()
                    : "Parameter '" + name + "'.";
            boolean required = toolParam != null ? toolParam.required() : parameter.getType().isPrimitive();
            List<String> enumValues = resolveEnumValues(parameter.getType());

            schemas.add(new ParameterSchema(
                    name,
                    mapJavaType(parameter.getType()),
                    description,
                    required,
                    null,
                    enumValues
            ));
        }

        return schemas;
    }

    private String resolveToolName(Tool tool, Method method) {
        try {
            Method nameAccessor = tool.annotationType().getMethod("name");
            Object explicitName = nameAccessor.invoke(tool);
            if (explicitName instanceof String stringValue && !stringValue.isBlank()) {
                return stringValue.trim();
            }
        } catch (ReflectiveOperationException ignored) {
            // Some Tool annotation variants do not expose a "name" attribute.
        }
        return method.getName();
    }

    private List<String> resolveEnumValues(Class<?> type) {
        if (!type.isEnum()) {
            return null;
        }
        return Arrays.stream(type.getEnumConstants())
                .map(String::valueOf)
                .toList();
    }

    private String mapJavaType(Class<?> type) {
        if (type.isEnum()) {
            return "string";
        }
        if (type.isArray() || Collection.class.isAssignableFrom(type)) {
            return "array";
        }
        if (Map.class.isAssignableFrom(type)) {
            return "object";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        if (Number.class.isAssignableFrom(type)
                || type == byte.class
                || type == short.class
                || type == int.class
                || type == long.class
                || type == float.class
                || type == double.class) {
            return "number";
        }
        return "string";
    }

    private String describeReturnType(Method method) {
        Class<?> returnType = method.getReturnType();
        if (returnType == Void.TYPE) {
            return "No return value.";
        }
        if (Map.class.isAssignableFrom(returnType)) {
            return "Structured object result returned by the tool.";
        }
        if (Collection.class.isAssignableFrom(returnType) || returnType.isArray()) {
            return "List-like result returned by the tool.";
        }
        if (returnType == String.class) {
            return "Text result returned by the tool.";
        }
        return "Result returned by the tool as " + returnType.getSimpleName() + ".";
    }
}
