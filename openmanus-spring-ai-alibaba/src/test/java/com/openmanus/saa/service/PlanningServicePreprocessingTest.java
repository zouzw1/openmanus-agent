package com.openmanus.saa.service;

import com.openmanus.saa.config.OpenManusProperties;
import com.openmanus.saa.service.mcp.McpPromptContextService;
import com.openmanus.saa.service.mcp.McpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class PlanningServicePreprocessingTest {

    @Mock
    private ChatClient chatClient;
    @Mock
    private OpenManusProperties properties;
    @Mock
    private ToolRegistryService toolRegistryService;
    @Mock
    private McpPromptContextService mcpPromptContextService;
    @Mock
    private McpService mcpService;
    @Mock
    private SkillsService skillsService;
    @Mock
    private SkillCapabilityService skillCapabilityService;

    private PlanningService planningService;

    @BeforeEach
    void setUp() {
        planningService = new PlanningService(
                chatClient, properties, toolRegistryService,
                mcpPromptContextService, mcpService,
                skillsService, skillCapabilityService
        );
    }

    @Test
    void shouldStripXmlCommentTags() {
        String input = "<!-- reasoning -->\n[\"step1\", \"step2\"]";
        String result = stripMarkdownCodeFence(input);
        assertThat(result).isEqualTo("[\"step1\", \"step2\"]");
    }

    @Test
    void shouldStripXmlThinkingTags() {
        String input = "<thinking>analysis</thinking>\n[\"step1\"]";
        String result = stripMarkdownCodeFence(input);
        assertThat(result).isEqualTo("[\"step1\"]");
    }

    @Test
    void shouldStripChineseThinkingTags() {
        String input = "<思考>分析</思考>\n[\"step1\", \"step2\"]";
        String result = stripMarkdownCodeFence(input);
        assertThat(result).isEqualTo("[\"step1\", \"step2\"]");
    }

    @Test
    void shouldExtractPureJsonArray() {
        String input = "Some text before\n[\"step1\", \"step2\"]\nSome text after";
        String result = stripMarkdownCodeFence(input);
        assertThat(result).isEqualTo("[\"step1\", \"step2\"]");
    }

    @Test
    void shouldStripMarkdownFences() {
        String input = "```json\n[\"step1\", \"step2\"]\n```";
        String result = stripMarkdownCodeFence(input);
        assertThat(result).isEqualTo("[\"step1\", \"step2\"]");
    }

    @Test
    void shouldHandleMixedContent() {
        String input = "<!-- comment -->\n<thinking>analysis</thinking>\n```json\n[\"step1\"]\n```\nSome text";
        String result = stripMarkdownCodeFence(input);
        assertThat(result).isEqualTo("[\"step1\"]");
    }

    @Test
    void shouldHandleNestedXmlTags() {
        String input = "<outer><inner>text</inner></outer>\n[\"step1\"]";
        String result = stripMarkdownCodeFence(input);
        assertThat(result).isEqualTo("[\"step1\"]");
    }

    @Test
    void shouldHandleNullInput() {
        String result = stripMarkdownCodeFence(null);
        assertThat(result).isEmpty();
    }

    @Test
    void shouldHandleEmptyInput() {
        String result = stripMarkdownCodeFence("");
        assertThat(result).isEmpty();
    }

    @Test
    void shouldPreserveJsonStructure() {
        String input = "[\n  {\"agent\": \"manus\", \"description\": \"step1\"},\n  {\"agent\": \"manus\", \"description\": \"step2\"}\n]";
        String result = stripMarkdownCodeFence(input);
        assertThat(result).contains("\"agent\": \"manus\"");
        assertThat(result).contains("\"description\": \"step1\"");
    }

    private String stripMarkdownCodeFence(String input) {
        return planningService.stripMarkdownCodeFence(input);
    }
}
