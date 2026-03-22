package com.openmanus.saa.agent;

import java.util.List;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

public class TrackingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final List<String> invocations;
    private final List<String> invocationDetails;

    public TrackingToolCallback(ToolCallback delegate, List<String> invocations, List<String> invocationDetails) {
        this.delegate = delegate;
        this.invocations = invocations;
        this.invocationDetails = invocationDetails;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        track(toolInput);
        return delegate.call(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        track(toolInput);
        return delegate.call(toolInput, toolContext);
    }

    private void track(String toolInput) {
        String toolName = getToolDefinition().name();
        invocations.add(toolName);
        if (invocationDetails != null) {
            String normalizedInput = toolInput == null ? "" : toolInput.trim().replace("\r", "").replace("\n", "");
            invocationDetails.add(normalizedInput.isBlank() ? toolName : toolName + "|" + normalizedInput);
        }
    }
}
