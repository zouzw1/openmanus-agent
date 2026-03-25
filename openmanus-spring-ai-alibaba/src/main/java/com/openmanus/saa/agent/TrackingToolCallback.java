package com.openmanus.saa.agent;

import java.util.List;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

public class TrackingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final List<String> invocations;
    private final List<String> invocationDetails;
    private final List<String> toolOutputs;

    public TrackingToolCallback(ToolCallback delegate, List<String> invocations, List<String> invocationDetails, List<String> toolOutputs) {
        this.delegate = delegate;
        this.invocations = invocations;
        this.invocationDetails = invocationDetails;
        this.toolOutputs = toolOutputs;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        track(toolInput);
        String output = delegate.call(toolInput);
        trackOutput(output);
        return output;
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        track(toolInput);
        String output = delegate.call(toolInput, toolContext);
        trackOutput(output);
        return output;
    }

    private void track(String toolInput) {
        String toolName = getToolDefinition().name();
        invocations.add(toolName);
        if (invocationDetails != null) {
            String normalizedInput = toolInput == null ? "" : toolInput.trim().replace("\r", "").replace("\n", "");
            invocationDetails.add(normalizedInput.isBlank() ? toolName : toolName + "|" + normalizedInput);
        }
    }

    private void trackOutput(String output) {
        if (toolOutputs == null) {
            return;
        }
        String toolName = getToolDefinition().name();
        String normalizedOutput = output == null ? "" : output.trim();
        toolOutputs.add(normalizedOutput.isBlank() ? toolName : toolName + "|" + normalizedOutput);
    }
}
