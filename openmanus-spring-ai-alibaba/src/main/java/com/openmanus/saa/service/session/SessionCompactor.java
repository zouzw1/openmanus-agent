package com.openmanus.saa.service.session;

import com.openmanus.saa.model.session.CompactionConfig;
import com.openmanus.saa.model.session.CompactionResult;
import com.openmanus.saa.model.session.ContentBlock;
import com.openmanus.saa.model.session.ConversationMessage;
import com.openmanus.saa.model.session.MessageRole;
import com.openmanus.saa.model.session.SessionState;
import com.openmanus.saa.model.session.TextBlock;
import com.openmanus.saa.model.session.ToolResultBlock;
import com.openmanus.saa.model.session.ToolUseBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 会话压缩服务。
 * 自动压缩长对话，生成结构化摘要，保留关键信息。
 */
@Service
public class SessionCompactor {

    private static final Logger log = LoggerFactory.getLogger(SessionCompactor.class);

    private static final String SUMMARY_PREAMBLE =
        "This session is being continued from a previous conversation that ran out of context. " +
        "The summary below covers the earlier portion of the conversation.\n\n";
    private static final String RECENT_NOTE = "Recent messages are preserved verbatim.";
    private static final String RESUME_INSTRUCTION =
        "Continue the conversation from where it left off without asking the user any further questions.";

    // 文件路径匹配模式
    private static final Pattern FILE_PATTERN =
        Pattern.compile("[\\w/\\\\.-]+\\.(java|rs|ts|js|json|md|yaml|yml|xml|properties)");

    /**
     * 检测是否需要压缩
     */
    public boolean shouldCompact(SessionState session, CompactionConfig config) {
        int startIndex = session.getCompactedSummary().isPresent() ? 1 : 0;
        int compactableCount = session.getMessages().size() - startIndex;

        return compactableCount > config.getPreserveRecentMessages()
            && estimateTokens(session) >= config.getMaxEstimatedTokens();
    }

    /**
     * 执行压缩
     */
    public CompactionResult compact(SessionState session, CompactionConfig config) {
        if (!shouldCompact(session, config)) {
            return CompactionResult.unchanged(session);
        }

        log.info("Compacting session {} with {} messages",
            session.getSessionId(), session.getMessages().size());

        // 1. 计算保留边界
        int prefixLen = session.getCompactedSummary().isPresent() ? 1 : 0;
        int keepFrom = session.getMessages().size() - config.getPreserveRecentMessages();

        // 2. 提取待压缩消息
        List<ConversationMessage> toCompress =
            session.getMessages().subList(prefixLen, keepFrom);

        // 3. 生成摘要
        String newSummary = summarizeMessages(toCompress);

        // 4. 合并历史摘要
        String mergedSummary = mergeSummaries(
            session.getCompactedSummary().orElse(null),
            newSummary
        );

        // 5. 构建压缩后的会话
        SessionState compacted = buildCompactedSession(session, mergedSummary, keepFrom);

        log.info("Compacted {} messages, summary length: {}",
            toCompress.size(), mergedSummary.length());

        return CompactionResult.compacted(mergedSummary, compacted, toCompress.size());
    }

    /**
     * 生成消息摘要
     */
    private String summarizeMessages(List<ConversationMessage> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("<summary>\n");
        sb.append("Conversation summary:\n");

        // 统计消息类型
        Map<String, Long> byRole = messages.stream()
            .collect(Collectors.groupingBy(m -> m.role().name().toLowerCase(), Collectors.counting()));
        sb.append("- Scope: ").append(messages.size()).append(" messages compacted");
        sb.append(" (").append(formatRoleCounts(byRole)).append(").\n");

        // 提取工具名称
        Set<String> tools = extractToolNames(messages);
        if (!tools.isEmpty()) {
            sb.append("- Tools used: ").append(String.join(", ", tools)).append(".\n");
        }

        // 提取关键文件
        Set<String> files = extractKeyFiles(messages);
        if (!files.isEmpty()) {
            sb.append("- Key files: ").append(String.join(", ", files)).append(".\n");
        }

        // 最近用户请求
        List<String> recentRequests = extractRecentUserRequests(messages, 3);
        if (!recentRequests.isEmpty()) {
            sb.append("- Recent requests:\n");
            for (String req : recentRequests) {
                sb.append("  - ").append(truncate(req, 100)).append("\n");
            }
        }

        // 推断待处理工作
        List<String> pendingWork = inferPendingWork(messages);
        if (!pendingWork.isEmpty()) {
            sb.append("- Pending work:\n");
            for (String work : pendingWork) {
                sb.append("  - ").append(truncate(work, 100)).append("\n");
            }
        }

        // 关键时间线
        sb.append("- Key timeline:\n");
        for (ConversationMessage msg : messages) {
            sb.append("  - ").append(msg.role().name().toLowerCase()).append(": ");
            sb.append(msg.preview(80)).append("\n");
        }

        sb.append("</summary>");
        return sb.toString();
    }

    /**
     * 合并历史摘要
     */
    private String mergeSummaries(String existing, String newSummary) {
        if (existing == null || existing.isBlank()) {
            return newSummary;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<summary>\n");
        sb.append("Conversation summary:\n");

        // 之前压缩的上下文
        sb.append("- Previously compacted:\n");
        extractSummaryHighlights(existing).forEach(line ->
            sb.append("  ").append(line).append("\n"));

        // 新压缩的上下文
        sb.append("- Newly compacted:\n");
        extractSummaryHighlights(newSummary).forEach(line ->
            sb.append("  ").append(line).append("\n"));

        sb.append("</summary>");
        return sb.toString();
    }

    /**
     * 构建压缩后的会话
     */
    private SessionState buildCompactedSession(
            SessionState original,
            String summary,
            int keepFrom) {

        SessionState compacted = new SessionState(original.getSessionId());

        // 添加摘要作为系统消息
        String continuationMessage = SUMMARY_PREAMBLE +
            formatSummary(summary) + "\n\n" +
            RECENT_NOTE + "\n" +
            RESUME_INSTRUCTION;

        compacted.addMessage(ConversationMessage.system(continuationMessage));
        compacted.setCompactedSummary(summary);

        // 保留最近消息
        List<ConversationMessage> preserved = original.getMessages()
            .subList(keepFrom, original.getMessages().size());
        preserved.forEach(compacted::addMessage);

        return compacted;
    }

    // ================== 辅助方法 ==================

    private int estimateTokens(SessionState session) {
        return session.estimateTokens();
    }

    private Set<String> extractToolNames(List<ConversationMessage> messages) {
        return messages.stream()
            .flatMap(m -> m.blocks().stream())
            .flatMap(b -> {
                if (b instanceof ToolUseBlock t) {
                    return java.util.stream.Stream.of(t.name());
                } else if (b instanceof ToolResultBlock t) {
                    return java.util.stream.Stream.of(t.toolName());
                }
                return java.util.stream.Stream.empty();
            })
            .collect(Collectors.toSet());
    }

    private Set<String> extractKeyFiles(List<ConversationMessage> messages) {
        return messages.stream()
            .flatMap(m -> m.blocks().stream())
            .flatMap(b -> {
                if (b instanceof TextBlock t) {
                    return FILE_PATTERN.matcher(t.text()).results().map(r -> r.group());
                } else if (b instanceof ToolUseBlock t) {
                    return FILE_PATTERN.matcher(t.input()).results().map(r -> r.group());
                } else if (b instanceof ToolResultBlock t) {
                    return FILE_PATTERN.matcher(t.output()).results().map(r -> r.group());
                }
                return java.util.stream.Stream.empty();
            })
            .limit(8)
            .collect(Collectors.toSet());
    }

    private List<String> extractRecentUserRequests(List<ConversationMessage> messages, int limit) {
        List<ConversationMessage> userMessages = messages.stream()
            .filter(m -> m.role() == MessageRole.USER)
            .toList();

        int start = Math.max(0, userMessages.size() - limit);
        return userMessages.subList(start, userMessages.size()).stream()
            .map(m -> truncate(m.blocks().stream()
                .filter(b -> b instanceof TextBlock)
                .map(b -> ((TextBlock) b).text())
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b), 160))
            .toList();
    }

    private List<String> inferPendingWork(List<ConversationMessage> messages) {
        return messages.stream()
            .filter(m -> m.role() == MessageRole.ASSISTANT)
            .map(m -> m.blocks().stream()
                .filter(b -> b instanceof TextBlock)
                .map(b -> ((TextBlock) b).text())
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b))
            .filter(c -> {
                String lower = c.toLowerCase();
                return lower.contains("next") || lower.contains("todo")
                    || lower.contains("pending") || lower.contains("follow up")
                    || lower.contains("remaining") || lower.contains("下一步");
            })
            .map(c -> truncate(c, 160))
            .limit(3)
            .toList();
    }

    private String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s != null ? s : "";
        return s.substring(0, max) + "…";
    }

    private String formatRoleCounts(Map<String, Long> counts) {
        return counts.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining(", "));
    }

    private List<String> extractSummaryHighlights(String summary) {
        return Arrays.stream(summary.split("\n"))
            .map(String::trim)
            .filter(line -> !line.isEmpty()
                && !line.startsWith("<")
                && !line.startsWith("- Key timeline"))
            .takeWhile(line -> !line.startsWith("- Key timeline"))
            .toList();
    }

    private String formatSummary(String summary) {
        // 移除 analysis 标签，格式化 summary 标签
        String cleaned = summary.replaceAll("<analysis>.*?</analysis>", "");
        return cleaned
            .replace("<summary>", "Summary:")
            .replace("</summary>", "")
            .trim();
    }
}
