package com.openmanus.saa.service.session;

import com.openmanus.saa.model.session.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 会话压缩服务（Claw 风格）。
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

    // ================== 新 Session API ==================

    /**
     * 检测是否需要压缩（新 Session API）
     */
    public boolean shouldCompact(Session session, CompactionConfig config) {
        // 系统消息不计入压缩范围
        int compactableCount = (int) session.messages().stream()
            .filter(m -> m.role() != MessageRole.SYSTEM)
            .count();

        return compactableCount > config.preserveRecentMessages()
            && estimateSessionTokens(session) >= config.maxEstimatedTokens();
    }

    /**
     * 执行压缩（新 Session API）
     */
    public CompactionResult compactSession(Session session, CompactionConfig config) {
        if (!shouldCompact(session, config)) {
            return CompactionResult.unchanged(session);
        }

        log.info("Compacting session {} with {} messages",
            session.sessionId(), session.messages().size());

        // 1. 过滤掉系统消息，计算保留边界
        List<ConversationMessage> nonSystemMessages = session.messages().stream()
            .filter(m -> m.role() != MessageRole.SYSTEM)
            .toList();

        int keepFrom = nonSystemMessages.size() - config.preserveRecentMessages();

        // 2. 提取待压缩消息
        List<ConversationMessage> toCompress = nonSystemMessages.subList(0, keepFrom);

        if (toCompress.isEmpty()) {
            return CompactionResult.unchanged(session);
        }

        // 3. 生成摘要
        String summary = summarizeMessages(toCompress);

        // 4. 构建压缩后的会话
        Session compacted = buildCompactedSession(session, summary, keepFrom);

        log.info("Compacted {} messages, summary length: {}",
            toCompress.size(), summary.length());

        return new CompactionResult(summary, formatCompactSummary(summary), compacted, toCompress.size());
    }

    // ================== 向后兼容 SessionState API ==================

    /**
     * 检测是否需要压缩（向后兼容 SessionState API）
     * @deprecated 使用 {@link #shouldCompact(Session, CompactionConfig)} 代替
     */
    @Deprecated
    public boolean shouldCompact(SessionState session, CompactionConfig config) {
        int startIndex = session.getCompactedSummary().isPresent() ? 1 : 0;
        int compactableCount = session.getMessages().size() - startIndex;

        return compactableCount > config.preserveRecentMessages()
            && session.estimateTokens() >= config.maxEstimatedTokens();
    }

    /**
     * 执行压缩（向后兼容 SessionState API）
     * @deprecated 使用 {@link #compactSession(Session, CompactionConfig)} 代替
     */
    @Deprecated
    public CompactionResult compact(SessionState session, CompactionConfig config) {
        if (!shouldCompact(session, config)) {
            return CompactionResult.unchanged(convertToSession(session));
        }

        log.info("Compacting session {} with {} messages (SessionState API)",
            session.getSessionId(), session.getMessages().size());

        // 1. 计算保留边界
        int prefixLen = session.getCompactedSummary().isPresent() ? 1 : 0;
        int keepFrom = session.getMessages().size() - config.preserveRecentMessages();

        // 2. 提取待压缩消息
        List<ConversationMessage> toCompress =
            session.getMessages().subList(prefixLen, keepFrom);

        if (toCompress.isEmpty()) {
            return CompactionResult.unchanged(convertToSession(session));
        }

        // 3. 生成摘要
        String newSummary = summarizeMessages(toCompress);

        // 4. 合并历史摘要
        String mergedSummary = mergeSummaries(
            session.getCompactedSummary().orElse(null),
            newSummary
        );

        // 5. 构建压缩后的会话
        SessionState compacted = buildCompactedSessionState(session, mergedSummary, keepFrom);
        Session resultSession = convertToSession(compacted);

        log.info("Compacted {} messages, summary length: {}",
            toCompress.size(), mergedSummary.length());

        return new CompactionResult(mergedSummary, formatCompactSummary(mergedSummary), resultSession, toCompress.size());
    }

    /**
     * 将 SessionState 转换为 Session
     */
    private Session convertToSession(SessionState state) {
        return new Session(
            1,
            state.getSessionId(),
            state.getCreatedAt(),
            state.getUpdatedAt(),
            state.getLastAccessedAt(),
            new ArrayList<>(state.getMessages()),
            new HashMap<>(),
            new ArrayList<>(state.getExecutionLog()),
            TokenUsage.zero(),
            state.getLatestInferencePolicy(),
            state.getLatestResponseMode()
        );
    }

    /**
     * 构建压缩后的 SessionState（向后兼容）
     */
    private SessionState buildCompactedSessionState(
            SessionState original,
            String summary,
            int keepFrom) {

        SessionState compacted = new SessionState(original.getSessionId());

        // 添加摘要作为系统消息
        String continuationMessage = SUMMARY_PREAMBLE +
            formatCompactSummary(summary) + "\n\n" +
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
     * 提取摘要高亮信息
     */
    private List<String> extractSummaryHighlights(String summary) {
        return Arrays.stream(summary.split("\n"))
            .map(String::trim)
            .filter(line -> !line.isEmpty()
                && !line.startsWith("<")
                && !line.startsWith("- Key timeline"))
            .takeWhile(line -> !line.startsWith("- Key timeline"))
            .toList();
    }

    // ================== 公共方法 ==================

    /**
     * 格式化摘要输出
     */
    public String formatCompactSummary(String summary) {
        if (summary == null || summary.isBlank()) {
            return "";
        }

        // 移除 analysis 标签
        String cleaned = summary.replaceAll("<analysis>.*?</analysis>", "");

        // 格式化 summary 标签
        return cleaned
            .replace("<summary>", "Summary:")
            .replace("</summary>", "")
            .trim();
    }

    /**
     * 生成压缩续接消息
     */
    public String getCompactContinuationMessage(String summary, boolean suppressFollowUpQuestions, boolean recentMessagesPreserved) {
        StringBuilder sb = new StringBuilder();
        sb.append(SUMMARY_PREAMBLE);

        String formatted = formatCompactSummary(summary);
        sb.append(formatted).append("\n\n");

        if (recentMessagesPreserved) {
            sb.append(RECENT_NOTE).append("\n");
        }

        if (suppressFollowUpQuestions) {
            sb.append(RESUME_INSTRUCTION);
        }

        return sb.toString();
    }

    /**
     * 估算会话 token 数量
     */
    public int estimateSessionTokens(Session session) {
        return session.estimateTokens();
    }

    // ================== 私有辅助方法 ==================

    /**
     * 生成消息摘要（Claw 风格）
     */
    private String summarizeMessages(List<ConversationMessage> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("<summary>\n");
        sb.append("Conversation summary:\n");

        // 统计消息类型
        Map<String, Long> byRole = messages.stream()
            .collect(Collectors.groupingBy(m -> m.role().name().toLowerCase(), Collectors.counting()));
        sb.append("- Scope: ").append(messages.size()).append(" earlier messages compacted");
        sb.append(" (").append(formatRoleCounts(byRole)).append(").\n");

        // 提取工具名称
        Set<String> tools = extractToolNames(messages);
        if (!tools.isEmpty()) {
            sb.append("- Tools mentioned: ").append(String.join(", ", tools)).append(".\n");
        }

        // 最近用户请求
        List<String> recentRequests = extractRecentUserRequests(messages, 3);
        if (!recentRequests.isEmpty()) {
            sb.append("- Recent user requests:\n");
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

        // 提取关键文件
        Set<String> files = extractKeyFiles(messages);
        if (!files.isEmpty()) {
            sb.append("- Key files referenced: ").append(String.join(", ", files)).append(".\n");
        }

        // 当前工作
        String currentWork = inferCurrentWork(messages);
        if (!currentWork.isEmpty()) {
            sb.append("- Current work: ").append(currentWork).append("\n");
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
     * 构建压缩后的会话
     */
    private Session buildCompactedSession(Session original, String summary, int keepFrom) {
        // 生成续接消息作为系统消息
        String continuationMessage = getCompactContinuationMessage(summary, true, true);
        ConversationMessage systemMessage = ConversationMessage.system(continuationMessage);

        // 保留最近的消息（非系统消息）
        List<ConversationMessage> nonSystemMessages = original.messages().stream()
            .filter(m -> m.role() != MessageRole.SYSTEM)
            .toList();

        List<ConversationMessage> preservedMessages = nonSystemMessages
            .subList(keepFrom, nonSystemMessages.size());

        // 构建新消息列表：系统消息 + 保留的消息
        List<ConversationMessage> newMessages = new ArrayList<>();
        newMessages.add(systemMessage);
        newMessages.addAll(preservedMessages);

        return original.withMessages(newMessages);
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

    private String inferCurrentWork(List<ConversationMessage> messages) {
        // 查找最后一条用户消息作为当前工作
        return messages.stream()
            .filter(m -> m.role() == MessageRole.USER)
            .reduce((first, second) -> second)  // 取最后一个
            .map(m -> m.preview(100))
            .orElse("");
    }

    private String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s != null ? s : "";
        return s.substring(0, max) + "...";
    }

    private String formatRoleCounts(Map<String, Long> counts) {
        return counts.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining(", "));
    }
}