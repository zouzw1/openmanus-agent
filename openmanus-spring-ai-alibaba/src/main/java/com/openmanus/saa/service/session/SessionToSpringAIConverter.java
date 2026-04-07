package com.openmanus.saa.service.session;

import com.openmanus.saa.model.session.*;
import org.springframework.ai.chat.messages.*;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class SessionToSpringAIConverter {

    public List<Message> convert(Session session) {
        return session.messages().stream()
            .map(this::convertMessage)
            .collect(Collectors.toList());
    }

    private Message convertMessage(ConversationMessage msg) {
        if (msg.role() == MessageRole.SYSTEM) {
            return new SystemMessage(convertBlocks(msg.blocks()));
        } else if (msg.role() == MessageRole.USER) {
            return new UserMessage(convertBlocks(msg.blocks()));
        } else if (msg.role() == MessageRole.ASSISTANT) {
            return new AssistantMessage(convertBlocks(msg.blocks()));
        } else {
            // TOOL role - convert to UserMessage (Spring AI compatibility)
            return convertToolMessage(msg);
        }
    }

    private String convertBlocks(List<ContentBlock> blocks) {
        return blocks.stream()
            .map(block -> {
                if (block instanceof TextBlock t) {
                    return t.text();
                } else if (block instanceof ToolUseBlock tu) {
                    return formatToolUse(tu);
                } else if (block instanceof ToolResultBlock tr) {
                    return formatToolResult(tr);
                }
                return "";
            })
            .collect(Collectors.joining("\n"));
    }

    private Message convertToolMessage(ConversationMessage msg) {
        String content = msg.blocks().stream()
            .map(block -> {
                if (block instanceof ToolResultBlock tr) {
                    return formatToolResult(tr);
                }
                return block.toSummary();
            })
            .collect(Collectors.joining("\n"));
        return new UserMessage(content);
    }

    private String formatToolUse(ToolUseBlock tu) {
        return String.format("[Tool Use: %s, Input: %s]", tu.name(), tu.input());
    }

    private String formatToolResult(ToolResultBlock tr) {
        return String.format("[Tool Result: %s%s, Output: %s]",
            tr.isError() ? "ERROR: " : "",
            tr.toolName(),
            tr.output());
    }
}
