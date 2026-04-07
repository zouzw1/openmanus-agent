package com.openmanus.saa.service.intent;

import com.openmanus.saa.config.OpenManusProperties;
import com.openmanus.saa.model.IntentResolution;
import com.openmanus.saa.model.IntentRouteMode;
import com.openmanus.saa.model.session.MessageRole;
import com.openmanus.saa.model.session.Session;
import com.openmanus.saa.model.session.TextBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Intent recognizer for detecting multi-agent execution scenarios.
 * Analyzes prompts for patterns indicating parallelizable or decomposable tasks.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class MultiAgentIntentRecognizer implements IntentRecognizer {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentIntentRecognizer.class);

    // Keywords indicating parallel execution intent
    private static final List<String> PARALLEL_KEYWORDS = Arrays.asList(
            "同时", "并行", "分别", "都帮我", "一起",
            "simultaneously", "in parallel", "concurrently",
            "at the same time", "all at once"
    );

    // Keywords indicating multiple independent tasks
    private static final List<String> MULTI_TASK_KEYWORDS = Arrays.asList(
            "和", "以及", "还有", "另外", "并且",
            "and", "also", "as well as", "additionally", "plus"
    );

    // Patterns for task enumeration (e.g., "1. xxx 2. xxx" or "- xxx - xxx")
    private static final Pattern ENUMERATION_PATTERN = Pattern.compile(
            "(?:^|\\n)\\s*[-*•]\\s+.+(?:\\n\\s*[-*•]\\s+.+){2,}|"
                    + "(?:^|\\n)\\s*\\d+[.、)]\\s+.+(?:\\n\\s*\\d+[.、)]\\s+.+){2,}"
    );

    // Patterns indicating multiple distinct domains/topics
    private static final List<String> DOMAIN_INDICATORS = Arrays.asList(
            "景点", "酒店", "美食", "交通", "购物",
            "attraction", "hotel", "restaurant", "transportation", "shopping",
            "backend", "frontend", "database", "api", "ui", "ux"
    );

    private final OpenManusProperties properties;

    public MultiAgentIntentRecognizer(OpenManusProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<IntentResolution> recognize(String prompt, Session session) {
        if (!properties.getMultiAgent().isEnabled()) {
            return Optional.empty();
        }

        // Analyze prompt for multi-agent indicators
        MultiAgentScore score = analyzePrompt(prompt, session);

        if (score.isMultiAgent()) {
            log.info("Multi-agent mode detected with confidence {}: {}", score.confidence(), prompt.substring(0, Math.min(100, prompt.length())));
            return Optional.of(createMultiAgentResolution(score.confidence()));
        }

        return Optional.empty();
    }

    private MultiAgentScore analyzePrompt(String prompt, Session session) {
        double confidence = 0.0;
        String lowerPrompt = prompt.toLowerCase();

        // Check for parallel keywords
        for (String keyword : PARALLEL_KEYWORDS) {
            if (lowerPrompt.contains(keyword.toLowerCase())) {
                confidence += 0.25;
                log.debug("Found parallel keyword: {}", keyword);
            }
        }

        // Check for enumeration patterns
        if (ENUMERATION_PATTERN.matcher(prompt).find()) {
            confidence += 0.20;
            log.debug("Found enumeration pattern indicating multiple tasks");
        }

        // Count domain indicators
        long domainCount = DOMAIN_INDICATORS.stream()
                .filter(indicator -> lowerPrompt.contains(indicator.toLowerCase()))
                .count();
        if (domainCount >= 3) {
            confidence += 0.25;
            log.debug("Found {} domain indicators", domainCount);
        }

        // Check for multi-task keywords with sentence complexity
        String[] sentences = prompt.split("[。.!！?？]");
        if (sentences.length >= 2) {
            long multiTaskKeywordCount = MULTI_TASK_KEYWORDS.stream()
                    .filter(keyword -> lowerPrompt.contains(keyword.toLowerCase()))
                    .count();
            if (multiTaskKeywordCount >= 2) {
                confidence += 0.15;
                log.debug("Found {} multi-task keywords", multiTaskKeywordCount);
            }
        }

        // Check for complex requests with multiple objectives
        if (prompt.length() > 100 && domainCount >= 2) {
            confidence += 0.10;
        }

        // Session-aware context boost
        if (session != null && !session.messages().isEmpty()) {
            int recentTurns = (int) session.messages().stream()
                    .filter(m -> m.role() != MessageRole.SYSTEM)
                    .count();

            // Long conversations with complex tasks get a slight confidence boost
            if (recentTurns > 2 && confidence >= 0.25) {
                confidence += 0.05;
                log.debug("Session context boost: {} recent turns", recentTurns);
            }

            // Check for continuation patterns suggesting ongoing complex work
            String recentHistory = session.messages().stream()
                    .filter(m -> m.role() == MessageRole.ASSISTANT)
                    .map(m -> m.blocks().stream()
                        .filter(b -> b instanceof TextBlock)
                        .map(b -> ((TextBlock) b).text())
                        .reduce("", (a, b) -> b))
                    .reduce("", (a, b) -> b);

            if (recentHistory.toLowerCase().contains("next step") ||
                recentHistory.toLowerCase().contains("continuing") ||
                recentHistory.toLowerCase().contains("continue")) {
                confidence += 0.05;
                log.debug("Continuation pattern detected in session history");
            }
        }

        return new MultiAgentScore(Math.min(confidence, 1.0));
    }

    private IntentResolution createMultiAgentResolution(double confidence) {
        return new IntentResolution(
                "multi_agent_parallel",
                confidence,
                IntentRouteMode.MULTI_AGENT,
                null,
                java.util.Map.of(
                        "decompositionStrategy", "parallel",
                        "requiresCoordinator", "true"
                ),
                java.util.List.of("parallel_execution", "task_decomposition")
        );
    }

    private record MultiAgentScore(double confidence) {
        boolean isMultiAgent() {
            return confidence >= 0.4;
        }
    }
}
