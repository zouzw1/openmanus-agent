package com.openmanus.saa.prompt.section;

import com.openmanus.saa.agent.AgentDefinition;
import com.openmanus.saa.agent.AgentRagConfig;
import com.openmanus.saa.prompt.PromptContext;
import com.openmanus.saa.prompt.PromptSection;
import org.springframework.stereotype.Component;

/**
 * RAG 状态 Section。
 *
 * <p>渲染 Agent 的 RAG 配置信息。
 */
@Component
public class RagPromptSection implements PromptSection {

    @Override
    public int order() {
        return 200;
    }

    @Override
    public String title() {
        return "## RAG Status";
    }

    @Override
    public String render(PromptContext context) {
        AgentDefinition agent = context.agent();
        AgentRagConfig rag = agent.getRag();

        if (!rag.isEnabled()) {
            return "RAG is disabled for this agent.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("- Mode: ").append(rag.getMode()).append("\n");

        if (rag.getKnowledgeBaseIds().isEmpty()) {
            sb.append("- Knowledge bases: none preconfigured\n");
        } else {
            sb.append("- Knowledge bases: ")
                .append(String.join(", ", rag.getKnowledgeBaseIds()))
                .append("\n");
        }

        sb.append("- RAG tools exposed: ").append(rag.usesTools() ? "yes" : "no").append("\n");
        sb.append("- RAG advisor enabled: ").append(rag.usesAdvisor() ? "yes" : "no");

        return sb.toString();
    }
}
