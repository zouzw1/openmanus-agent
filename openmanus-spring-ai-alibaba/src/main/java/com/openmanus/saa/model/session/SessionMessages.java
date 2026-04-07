package com.openmanus.saa.model.session;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * 会话消息管理器。
 * 管理结构化的消息列表，支持Tool调用和结果的追踪。
 */
public class SessionMessages {

    private static final Logger log = LoggerFactory.getLogger(SessionMessages.class);

    private final List<ConversationMessage> messages;
    private final int maxMessages;
    private final int maxChars;

    // 用于追踪未匹配的tool use
    private final List<PendingToolUse> pendingToolUses = new ArrayList<>();

    public SessionMessages() {
        this(50, 32000);
    }

    public SessionMessages(int maxMessages, int maxChars) {
        this.messages = new ArrayList<>();
        this.maxMessages = maxMessages;
        this.maxChars = maxChars;
    }

    /**
     * 添加用户消息。
     */
    public void addUserMessage(String content) {
        messages.add(ConversationMessage.userText(content));
        trimIfNeeded();
    }

    /**
     * 添加助手消息。
     */
    public void addAssistantMessage(String content) {
        messages.add(ConversationMessage.assistant(List.of(new TextBlock(content))));
        trimIfNeeded();
    }

    /**
     * 添加系统消息。
     */
    public void addSystemMessage(String content) {
        messages.add(ConversationMessage.system(content));
        trimIfNeeded();
    }

    /**
     * 记录Tool调用。
     * 注意：Tool调用通常嵌入在Assistant消息中，这里单独记录用于追踪。
     */
    public String recordToolUse(String toolName, String input) {
        String toolId = java.util.UUID.randomUUID().toString().substring(0, 8);
        pendingToolUses.add(new PendingToolUse(toolId, toolName, input));
        log.debug("Recorded tool use: {} with id {}", toolName, toolId);
        return toolId;
    }

    /**
     * 记录Tool结果。
     * 将匹配对应的Tool调用并创建ToolResult消息。
     */
    public void recordToolResult(String toolId, String toolName, String output, boolean isError) {
        // 查找匹配的pending tool use
        PendingToolUse matchedUse = pendingToolUses.stream()
                .filter(p -> p.toolId.equals(toolId) || p.toolName.equals(toolName))
                .findFirst()
                .orElse(null);

        if (matchedUse != null) {
            pendingToolUses.remove(matchedUse);
        }

        // 创建Tool结果消息
        ConversationMessage toolResultMsg = ConversationMessage.toolResult(toolId, toolName, output, isError);
        messages.add(toolResultMsg);
        trimIfNeeded();

        log.debug("Recorded tool result for {} (id: {}), error: {}", toolName, toolId, isError);
    }

    /**
     * 获取所有消息。
     */
    public List<ConversationMessage> getMessages() {
        return List.copyOf(messages);
    }

    /**
     * 获取消息数量。
     */
    public int size() {
        return messages.size();
    }

    /**
     * 估算字符数。
     */
    public int estimateChars() {
        return messages.stream()
                .mapToInt(m -> m.blocks().stream()
                    .mapToInt(b -> b.asText().length())
                    .sum())
                .sum();
    }

    /**
     * 转换为Spring AI Message列表。
     * 用于传递给LLM API调用。
     */
    public List<Message> toSpringAIMessages() {
        List<Message> result = new ArrayList<>();

        for (ConversationMessage msg : messages) {
            result.add(convertToSpringAIMessage(msg));
        }

        return result;
    }

    /**
     * 构建上下文摘要。
     * 用于日志和调试。
     */
    public String buildContextSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Session Messages (").append(messages.size()).append("):\n");

        for (int i = 0; i < messages.size(); i++) {
            ConversationMessage msg = messages.get(i);
            sb.append("  [").append(i + 1).append("] ")
                    .append(msg.role()).append(": ")
                    .append(msg.preview(100))
                    .append("\n");
        }

        return sb.toString();
    }

    /**
     * 清空消息。
     */
    public void clear() {
        messages.clear();
        pendingToolUses.clear();
    }

    /**
     * 检查是否为空。
     */
    public boolean isEmpty() {
        return messages.isEmpty();
    }

    // ================== 私有方法 ==================

    private Message convertToSpringAIMessage(ConversationMessage msg) {
        // 获取文本内容
        String content = msg.blocks().stream()
            .filter(b -> b instanceof TextBlock)
            .map(b -> ((TextBlock) b).text())
            .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);

        // 检查是否有ToolResult block - 使用文本格式嵌入
        if (msg.hasToolResult()) {
            // 提取所有tool results并格式化为文本
            StringBuilder sb = new StringBuilder();
            for (ContentBlock block : msg.blocks()) {
                if (block instanceof ToolResultBlock tr) {
                    sb.append("[Tool Result: ").append(tr.toolName()).append("]\n");
                    if (tr.isError()) {
                        sb.append("Error: ");
                    }
                    sb.append(tr.output()).append("\n");
                }
            }
            if (!sb.isEmpty()) {
                return new UserMessage(sb.toString());
            }
        }

        // 根据role创建对应的消息类型
        return switch (msg.role()) {
            case USER -> new UserMessage(content);
            case ASSISTANT -> new AssistantMessage(content);
            case SYSTEM -> new UserMessage("[System] " + content);
            case TOOL -> {
                // Tool结果作为格式化的UserMessage
                ToolResultBlock toolResult = msg.blocks().stream()
                        .filter(b -> b instanceof ToolResultBlock)
                        .map(b -> (ToolResultBlock) b)
                        .findFirst()
                        .orElse(null);
                if (toolResult != null) {
                    String formatted = formatToolResult(toolResult);
                    yield new UserMessage(formatted);
                }
                yield new UserMessage(content);
            }
        };
    }

    /**
     * 格式化Tool结果为文本。
     * 由于Spring AI的ToolResponseMessage API在不同版本间不兼容，
     * 我们使用文本格式嵌入tool结果，这样可以保持版本兼容性。
     */
    private String formatToolResult(ToolResultBlock tr) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Tool Result: ").append(tr.toolName());
        if (tr.toolUseId() != null && !tr.toolUseId().isBlank()) {
            sb.append(" (id: ").append(tr.toolUseId()).append(")");
        }
        sb.append("]\n");
        if (tr.isError()) {
            sb.append("⚠️ Error: ");
        }
        sb.append(tr.output());
        return sb.toString();
    }

    private void trimIfNeeded() {
        // 按消息数量裁剪
        while (messages.size() > maxMessages) {
            messages.remove(0);
            log.debug("Trimmed oldest message, current size: {}", messages.size());
        }

        // 按字符数裁剪
        int totalChars = estimateChars();
        if (totalChars > maxChars) {
            // 从最旧的消息开始移除，直到低于阈值
            while (messages.size() > 1 && estimateChars() > maxChars * 0.8) {
                messages.remove(0);
            }
            log.info("Trimmed messages to reduce context size, new size: {}, chars: {}",
                    messages.size(), estimateChars());
        }
    }

    /**
     * 待匹配的Tool调用。
     */
    private record PendingToolUse(String toolId, String toolName, String input) {}
}