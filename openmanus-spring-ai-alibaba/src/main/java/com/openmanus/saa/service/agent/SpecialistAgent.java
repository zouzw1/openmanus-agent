package com.openmanus.saa.service.agent;

import com.openmanus.saa.agent.AgentDefinition;
import com.openmanus.saa.model.WorkflowStep;

public interface SpecialistAgent {

    String name();

    String description();

    AgentExecutionResult execute(AgentDefinition agentDefinition, String objective, String currentPlan, WorkflowStep step, String stepPrompt);
}
