package com.openmanus.saa.service.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.openmanus.saa.agent.AgentDefinition;
import com.openmanus.saa.agent.ResolvedAgentRuntime;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.stereotype.Component;

@Component
public class ReactAgentExecutionSupport {

    private final ChatClient chatClient;

    public ReactAgentExecutionSupport(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String execute(
            AgentDefinition agentDefinition,
            ResolvedAgentRuntime runtime,
            String systemPrompt,
            String instruction,
            String userMessage
    ) {
        ChatClient runtimeChatClient = chatClient.mutate()
                .defaultAdvisors(runtime.advisors())
                .build();

        ReactAgent agent = ReactAgent.builder()
                .name(agentDefinition.getId())
                .description(agentDefinition.getDescription())
                .chatClient(runtimeChatClient)
                .tools(runtime.toolCallbacks())
                .toolContext(runtime.toolContext())
                .systemPrompt(systemPrompt)
                .instruction(instruction)
                .build();

        try {
            AssistantMessage message = agent.call(userMessage);
            return message == null || message.getText() == null ? "" : message.getText();
        } catch (GraphRunnerException ex) {
            throw new IllegalStateException("ReactAgent execution failed for agent " + agentDefinition.getId(), ex);
        }
    }
}
