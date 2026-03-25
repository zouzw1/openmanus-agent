package com.openmanus.saa.agent;

import com.openmanus.saa.tool.McpToolBridge;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.aop.support.AopUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.util.ReflectionUtils;
import org.springframework.stereotype.Component;

@Component
public class LocalToolCallbackCatalog {

    private final Map<String, ToolCallback> callbacksByName;

    public LocalToolCallbackCatalog(ListableBeanFactory beanFactory) {
        Map<String, ToolCallback> callbacks = new LinkedHashMap<>();
        for (String beanName : beanFactory.getBeanNamesForType(Object.class, false, false)) {
            Class<?> beanType = beanFactory.getType(beanName, false);
            if (beanType == null || !hasToolMethods(beanType)) {
                continue;
            }
            if (McpToolBridge.class.isAssignableFrom(beanType)) {
                continue;
            }

            Object bean = beanFactory.getBean(beanName);
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            if (targetClass == null || !hasToolMethods(targetClass)) {
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

    private boolean hasToolMethods(Class<?> beanType) {
        final boolean[] found = {false};
        ReflectionUtils.doWithMethods(
                beanType,
                method -> found[0] = true,
                method -> method.isAnnotationPresent(Tool.class) && !method.isSynthetic() && !method.isBridge()
        );
        return found[0];
    }
}
