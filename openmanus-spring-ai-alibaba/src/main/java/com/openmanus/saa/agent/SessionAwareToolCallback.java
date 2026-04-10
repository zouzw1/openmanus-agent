package com.openmanus.saa.agent;

import com.openmanus.saa.model.session.ConversationSession;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * ToolCallback包装器，自动将tool调用和结果记录到ConversationSession。
 *
 * <p>借鉴Claw的设计：Tool调用和结果作为结构化消息追加到session，
 * 让后续step可以看到完整的tool调用链。
 */
public class SessionAwareToolCallback implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(SessionAwareToolCallback.class);

    private final ToolCallback delegate;
    private final ConversationSession session;

    public SessionAwareToolCallback(ToolCallback delegate, ConversationSession session) {
        this.delegate = delegate;
        this.session = session;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        String toolId = generateToolId();
        String toolName = getToolDefinition().name();

        // 记录tool调用
        session.recordToolCall(toolName, toolInput);
        log.debug("SessionAwareToolCallback: recording tool call {} with id {}", toolName, toolId);

        String output;
        boolean isError = false;
        try {
            output = delegate.call(toolInput);
        } catch (Exception e) {
            output = "Error: " + e.getMessage();
            isError = true;
            log.warn("Tool {} execution failed: {}", toolName, e.getMessage());
        }

        // 记录tool结果
        session.recordToolResult(toolId, toolName, output, isError);
        log.debug("SessionAwareToolCallback: recorded tool result for {} (id: {}), error: {}", toolName, toolId, isError);

        return output;
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String toolId = generateToolId();
        String toolName = getToolDefinition().name();

        // 记录tool调用
        session.recordToolCall(toolName, toolInput);
        log.debug("SessionAwareToolCallback: recording tool call {} with id {}", toolName, toolId);

        String output;
        boolean isError = false;
        try {
            output = delegate.call(toolInput, toolContext);
        } catch (Exception e) {
            output = "Error: " + e.getMessage();
            isError = true;
            log.warn("Tool {} execution failed: {}", toolName, e.getMessage());
        }

        // 记录tool结果
        session.recordToolResult(toolId, toolName, output, isError);
        log.debug("SessionAwareToolCallback: recorded tool result for {} (id: {}), error: {}", toolName, toolId, isError);

        return output;
    }

    /**
     * 获取被包装的原始ToolCallback。
     */
    public ToolCallback getDelegate() {
        return delegate;
    }

    private String generateToolId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
