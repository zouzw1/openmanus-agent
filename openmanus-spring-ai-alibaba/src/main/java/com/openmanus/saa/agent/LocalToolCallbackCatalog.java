package com.openmanus.saa.agent;

import com.openmanus.saa.tool.McpToolBridge;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.aop.support.AopUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.stereotype.Component;

@Component
public class LocalToolCallbackCatalog {

    private static final String TOOL_PACKAGE_PREFIX = "com.openmanus.saa.tool";

    private final Map<String, ToolCallback> callbacksByName;

    public LocalToolCallbackCatalog(ListableBeanFactory beanFactory) {
        Map<String, ToolCallback> callbacks = new LinkedHashMap<>();
        for (String beanName : beanFactory.getBeanNamesForType(Object.class, false, false)) {
            Class<?> beanType = beanFactory.getType(beanName, false);
            if (beanType == null || !beanType.getPackageName().startsWith(TOOL_PACKAGE_PREFIX)) {
                continue;
            }
            if (McpToolBridge.class.isAssignableFrom(beanType)) {
                continue;
            }

            Object bean = beanFactory.getBean(beanName);
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            if (targetClass == null || !targetClass.getPackageName().startsWith(TOOL_PACKAGE_PREFIX)) {
                continue;
            }
            if (McpToolBridge.class.isAssignableFrom(targetClass)) {
                continue;
            }
            ToolCallback[] discovered = MethodToolCallbackProvider.builder()
                    .toolObjects(bean)
                    .build()
                    .getToolCallbacks();
            Arrays.stream(discovered).forEach(callback -> callbacks.put(callback.getToolDefinition().name(), callback));
        }
        this.callbacksByName = Map.copyOf(callbacks);
    }

    public Set<String> listToolNames() {
        return callbacksByName.keySet();
    }

    public List<ToolCallback> getCallbacks(Set<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return List.of();
        }
        return toolNames.stream()
                .map(callbacksByName::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
