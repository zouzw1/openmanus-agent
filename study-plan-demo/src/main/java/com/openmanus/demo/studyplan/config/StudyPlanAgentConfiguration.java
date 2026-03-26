package com.openmanus.demo.studyplan.config;

import com.openmanus.saa.agent.AgentConfigSource;
import com.openmanus.saa.agent.AgentDefinition;
import com.openmanus.saa.agent.CapabilityAccessMode;
import com.openmanus.saa.agent.IdAccessPolicy;
import com.openmanus.saa.agent.McpAccessPolicy;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class StudyPlanAgentConfiguration {

    @Bean
    @Primary
    AgentConfigSource studyPlanAgentConfigSource() {
        AgentDefinition studyPlanner = new AgentDefinition(
                "study-planner",
                "Study Planner",
                true,
                "manus",
                "Specialized agent for building structured learning plans with custom local study tools.",
                true,
                100,
                """
                You are a study-planning agent.
                Prefer the dedicated learning-plan local tools before improvising your own structure.
                Build practical study plans with milestones, weekly rhythm, and actionable practice tasks.
                When the user asks for a file, write the final text into the workspace using the workspace tools.
                Keep outputs concrete and easy to execute.
                """.trim(),
                null,
                new IdAccessPolicy(
                        CapabilityAccessMode.ALLOW_LIST,
                        Set.of(
                                "recommendLearningGoals",
                                "buildWeeklyStudySchedule",
                                "generatePracticeChecklist",
                                "runPowerShell",
                                "callMcpTool",
                                "writeWorkspaceFile",
                                "readWorkspaceFile",
                                "listWorkspaceFiles"
                        )
                ),
                new McpAccessPolicy(CapabilityAccessMode.ALLOW_LIST, Set.of("demo-sse"), Set.of()),
                new IdAccessPolicy(CapabilityAccessMode.ALLOW_LIST, Set.of("skill:docx-format"))
        );
        return () -> List.of(studyPlanner);
    }
}
