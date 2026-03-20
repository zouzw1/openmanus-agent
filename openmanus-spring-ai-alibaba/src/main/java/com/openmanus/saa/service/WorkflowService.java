package com.openmanus.saa.service;

import com.openmanus.saa.config.OpenManusProperties;
import com.openmanus.saa.model.WorkflowExecutionResponse;
import com.openmanus.saa.model.WorkflowStep;
import com.openmanus.saa.service.agent.SpecialistAgent;
import com.openmanus.saa.model.session.SessionState;
import com.openmanus.saa.service.session.SessionMemoryService;
import com.openmanus.saa.tool.PlanningTools;
import com.openmanus.saa.util.ParameterMissingDetector;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class WorkflowService {

    private static final Pattern STEP_PATTERN = Pattern.compile("^\\[(.+?)\\]\\s*(.+)$");
    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);

    private final ChatClient chatClient;
    private final PlanningService planningService;
    private final PlanningTools planningTools;
    private final OpenManusProperties properties;
    private final Map<String, SpecialistAgent> agents;
    private final SessionMemoryService sessionMemoryService;

    public WorkflowService(
            ChatClient chatClient,
            PlanningService planningService,
            PlanningTools planningTools,
            OpenManusProperties properties,
            List<SpecialistAgent> agentExecutors,
            SessionMemoryService sessionMemoryService
    ) {
        this.chatClient = chatClient;
        this.planningService = planningService;
        this.planningTools = planningTools;
        this.properties = properties;
        this.sessionMemoryService = sessionMemoryService;
        this.agents = new LinkedHashMap<>();
        for (SpecialistAgent agent : agentExecutors) {
            this.agents.put(agent.name(), agent);
        }
    }

    public WorkflowExecutionResponse execute(String sessionId, String objective) {
        SessionState session = sessionMemoryService.getOrCreate(sessionId);
        session.addMessage("user", objective);
        List<WorkflowStep> steps = planningService.createWorkflowPlan(
                objective,
                availableAgentDescriptions()
        );

        String planId = "workflow-" + UUID.randomUUID();
        planningTools.createPlan(
                planId,
                steps.stream().map(step -> "[" + step.agent() + "] " + step.description()).toList()
        );

        List<String> executionLog = new ArrayList<>();
        int stepLimit = Math.min(steps.size(), properties.getMaxSteps());
        for (int i = 0; i < stepLimit; i++) {
            WorkflowStep step = steps.get(i);
            log.info("Executing step %s: %s".formatted(i + 1, step.description()));
            SpecialistAgent agent = selectAgent(step.agent());
            
            // 在当前 step 内进行重试，直到成功或确认需要用户澄清
            String result = null;
            int maxAttemptsInStep = 2; // 每个 step 最多尝试 2 次
            int attempt = 0;
            boolean needsUserClarification = false;
            
            while (attempt < maxAttemptsInStep && !needsUserClarification) {
                attempt++;
                log.debug("Step {} attempt {}/{}", i + 1, attempt, maxAttemptsInStep);
                
                // 传递会话历史，让 Agent 可以从上下文中获取信息
                result = agent.execute(objective, planningTools.getPlan(planId), step.description());
                log.info("Agent %s: %s".formatted(agent.name(), result));
                
                // 使用通用检测器判断结果类型
                ParameterMissingDetector.DetectionResult detection = ParameterMissingDetector.detect(result);
                
                if (detection == ParameterMissingDetector.DetectionResult.NEEDS_USER_CLARIFICATION ||
                    detection == ParameterMissingDetector.DetectionResult.MISSING_PARAMETERS) {
                    
                    log.warn("Step {} requires user clarification after attempt {} (detected: {})", 
                            i + 1, attempt, detection);
                    needsUserClarification = true;
                    
                    // 提取缺失的参数名（如果可能）
                    String missingParam = ParameterMissingDetector.extractMissingParameter(result);
                    if (missingParam != null) {
                        log.warn("Missing parameter: {}", missingParam);
                    }
                    
                    // 将需要澄清的信息添加到会话中，供下一轮使用
                    session.addMessage("system", "[NEEDS_CLARIFICATION] [Step " + (i + 1) + "] " + result);
                } else if (detection == ParameterMissingDetector.DetectionResult.SUCCESS) {
                    // 执行成功，退出重试循环
                    log.info("Step {} completed successfully on attempt {}", i + 1, attempt);
                    break;
                } else {
                    // 其他错误，也退出循环
                    log.warn("Step {} encountered other error: {}", i + 1, detection);
                    break;
                }
            }
            
            // 记录执行结果（无论是成功还是需要澄清）
            executionLog.add("Step " + (i + 1) + " [" + agent.name() + "]: " + result);
            session.addExecutionLog("Step " + (i + 1) + " [" + agent.name() + "] " + step.description() + " => " + result);
        }

        String summary = summarizeWorkflow(objective, planningTools.getPlan(planId), executionLog);
        session.addMessage("assistant", summary);
        return new WorkflowExecutionResponse(objective, steps, executionLog, summary);
    }

    private String summarizeWorkflow(String objective, String currentPlan, List<String> executionLog) {
        if (executionLog.isEmpty()) {
            return "No workflow steps were executed.";
        }
        return chatClient.prompt()
                .system("""
                        %s

                        You are responsible for summarizing workflow execution results.
                        Provide a concise final answer grounded in the execution log.
                        Mention important outcomes, unresolved gaps, and any limitations.
                        Do not invent actions or results that are not present in the log.
                        """.formatted(properties.getSystemPrompt()))
                .user("""
                        Objective:
                        %s

                        Workflow plan:
                        %s

                        Execution log:
                        %s
                        """.formatted(objective, currentPlan, String.join("\n\n", executionLog)))
                .call()
                .content();
    }

    private SpecialistAgent selectAgent(String agentName) {
        if ("data_analysis".equals(agentName) && !properties.isWorkflowUseDataAnalysisAgent()) {
            return agents.get("manus");
        }
        return agents.getOrDefault(agentName, agents.get("manus"));
    }

    private String availableAgentDescriptions() {
        StringBuilder builder = new StringBuilder();
        for (SpecialistAgent agent : agents.values()) {
            if ("data_analysis".equals(agent.name()) && !properties.isWorkflowUseDataAnalysisAgent()) {
                continue;
            }
            builder.append("- ")
                    .append(agent.name())
                    .append(": ")
                    .append(agent.description())
                    .append("\n");
        }
        return builder.toString().trim();
    }

    public WorkflowStep parseTaggedStep(String line) {
        Matcher matcher = STEP_PATTERN.matcher(line.trim());
        if (matcher.matches()) {
            return new WorkflowStep(matcher.group(1).trim(), matcher.group(2).trim());
        }
        return new WorkflowStep("manus", line.trim());
    }
}
