package com.openmanus.saa.prompt;

import com.openmanus.saa.agent.AgentDefinition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * System Prompt 构建器。
 *
 * <p>自动收集所有 {@link PromptSection} Bean，按顺序组装成完整的 System Prompt。
 *
 * <p>构建结构：
 * <pre>
 * [Static Sections (order < 300)]
 * __SYSTEM_PROMPT_DYNAMIC_BOUNDARY__
 * [Dynamic Sections (isDynamic = true)]
 * </pre>
 */
@Service
public class SystemPromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(SystemPromptBuilder.class);

    /**
     * 动态边界标记，用于区分静态内容和运行时动态内容
     */
    public static final String DYNAMIC_BOUNDARY = "__SYSTEM_PROMPT_DYNAMIC_BOUNDARY__";

    /**
     * 总字符数限制
     */
    private static final int MAX_TOTAL_CHARS = 12000;

    private final List<PromptSection> staticSections;
    private final List<PromptSection> dynamicSections;

    public SystemPromptBuilder(List<PromptSection> sections) {
        // 按 order 排序
        List<PromptSection> sorted = new ArrayList<>(sections != null ? sections : List.of());
        sorted.sort(Comparator.comparingInt(PromptSection::order));

        this.staticSections = sorted.stream()
            .filter(s -> !s.isDynamic())
            .toList();
        this.dynamicSections = sorted.stream()
            .filter(PromptSection::isDynamic)
            .toList();

        log.info("SystemPromptBuilder initialized with {} static sections and {} dynamic sections",
            staticSections.size(), dynamicSections.size());
    }

    /**
     * 构建 System Prompt。
     *
     * @param agent Agent 定义
     * @return 完整的 system prompt
     */
    public String build(AgentDefinition agent) {
        return build(agent, null, Map.of());
    }

    /**
     * 构建 System Prompt（带会话上下文）。
     *
     * @param agent Agent 定义
     * @param sessionId 会话 ID
     * @return 完整的 system prompt
     */
    public String build(AgentDefinition agent, String sessionId) {
        return build(agent, sessionId, Map.of());
    }

    /**
     * 构建 System Prompt（完整参数）。
     *
     * @param agent Agent 定义
     * @param sessionId 会话 ID
     * @param runtimeHints 运行时提示
     * @return 完整的 system prompt
     */
    public String build(AgentDefinition agent, String sessionId, Map<String, Object> runtimeHints) {
        PromptContext context = new PromptContext(agent, sessionId, runtimeHints);

        StringBuilder result = new StringBuilder();
        int remaining = MAX_TOTAL_CHARS;

        // 1. 静态 sections（顺序排列）
        for (PromptSection section : staticSections) {
            String rendered = renderSection(section, context, remaining);
            if (!rendered.isBlank()) {
                result.append(rendered).append("\n\n");
                remaining -= rendered.length();
            }
            if (remaining <= 0) {
                log.warn("Prompt truncated at section '{}' due to character limit", section.title());
                break;
            }
        }

        // 2. 动态边界
        result.append(DYNAMIC_BOUNDARY).append("\n\n");

        // 3. 动态 sections（运行时上下文）
        remaining = Math.max(remaining, 2000); // 保证动态内容至少有 2000 字符空间
        for (PromptSection section : dynamicSections) {
            String rendered = renderSection(section, context, remaining);
            if (!rendered.isBlank()) {
                result.append(rendered).append("\n\n");
                remaining -= rendered.length();
            }
            if (remaining <= 0) break;
        }

        return result.toString().trim();
    }

    /**
     * 渲染单个 Section
     */
    private String renderSection(PromptSection section, PromptContext context, int maxChars) {
        try {
            StringBuilder sb = new StringBuilder();

            String title = section.title();
            if (title != null && !title.isBlank()) {
                sb.append(title).append("\n");
            }

            String content = section.render(context);
            if (content == null) {
                content = "";
            }

            // 截断内容
            int titleLength = sb.length();
            int maxContentChars = maxChars - titleLength - 20; // 预留截断标记空间
            if (content.length() > maxContentChars && maxContentChars > 0) {
                content = content.substring(0, maxContentChars) + "\n[content truncated]";
            }

            sb.append(content);
            return sb.toString();
        } catch (Exception e) {
            log.warn("Failed to render section '{}': {}", section.title(), e.getMessage());
            return "";
        }
    }

    /**
     * 获取静态 sections 数量
     */
    public int staticSectionCount() {
        return staticSections.size();
    }

    /**
     * 获取动态 sections 数量
     */
    public int dynamicSectionCount() {
        return dynamicSections.size();
    }
}
