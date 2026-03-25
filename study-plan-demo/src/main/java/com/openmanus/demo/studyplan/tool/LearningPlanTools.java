package com.openmanus.demo.studyplan.tool;

import java.util.Locale;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class LearningPlanTools {

    @Tool(description = "Recommend concrete learning goals and milestones for a study topic.")
    public String recommendLearningGoals(
            @ToolParam(description = "Primary topic to study, for example Java, Spring Boot, IELTS, or machine learning.", required = true)
            String topic,
            @ToolParam(description = "Learner level such as beginner, intermediate, or advanced.", required = true)
            String learnerLevel
    ) {
        String normalizedLevel = normalizeLevel(learnerLevel);
        return """
                Learning goals for %s (%s):
                1. Build conceptual clarity around the core foundations of %s.
                2. Complete a small but end-to-end practice outcome every week.
                3. Maintain a review loop with recap, exercises, and reflection.
                4. Finish the study cycle with one portfolio-style deliverable or assessment.

                Suggested milestone split:
                - Early stage: fundamentals and vocabulary
                - Middle stage: guided exercises and repetition
                - Final stage: independent application and review
                """.formatted(topic, normalizedLevel, topic).trim();
    }

    @Tool(description = "Build a week-by-week study schedule for a learner.")
    public String buildWeeklyStudySchedule(
            @ToolParam(description = "Primary topic to study, for example Java, Spring Boot, IELTS, or machine learning.", required = true)
            String topic,
            @ToolParam(description = "Learner level such as beginner, intermediate, or advanced.", required = true)
            String learnerLevel,
            @ToolParam(description = "Total study duration in weeks.", required = true)
            int durationWeeks,
            @ToolParam(description = "Available study hours per week.", required = true)
            int weeklyHours
    ) {
        int safeWeeks = Math.max(durationWeeks, 1);
        int safeHours = Math.max(weeklyHours, 1);
        int fundamentalsWeeks = Math.max(1, safeWeeks / 3);
        int practiceWeeks = Math.max(1, safeWeeks / 3);
        int consolidationWeeks = Math.max(1, safeWeeks - fundamentalsWeeks - practiceWeeks);

        return """
                Weekly study schedule for %s (%s):
                - Total duration: %d weeks
                - Weekly effort: %d hours

                Phase 1: Fundamentals (%d weeks)
                - Spend 60%% of time on guided study
                - Spend 40%% on short exercises and notes

                Phase 2: Practice (%d weeks)
                - Spend 50%% on focused drills
                - Spend 30%% on mini-projects or mock tasks
                - Spend 20%% on review and error correction

                Phase 3: Consolidation (%d weeks)
                - Complete one integrated outcome each week
                - Review weak points, refine notes, and simulate real use

                Recommended weekly rhythm:
                - 2 focused learning sessions
                - 1 practice session
                - 1 review or reflection session
                """.formatted(topic, normalizeLevel(learnerLevel), safeWeeks, safeHours, fundamentalsWeeks, practiceWeeks, consolidationWeeks).trim();
    }

    @Tool(description = "Generate a practical checklist for execution, review, and safety in a learning plan.")
    public String generatePracticeChecklist(
            @ToolParam(description = "Primary topic to study, for example Java, Spring Boot, IELTS, or machine learning.", required = true)
            String topic,
            @ToolParam(description = "Learner level such as beginner, intermediate, or advanced.", required = true)
            String learnerLevel
    ) {
        return """
                Practice checklist for %s (%s):
                - Define one measurable weekly outcome.
                - Keep study notes in a single place and update them after each session.
                - Reserve one session per week for recap and spaced repetition.
                - Track blockers and turn them into targeted practice tasks.
                - Validate progress with a quiz, mock task, code exercise, or explanation exercise.
                - Reduce overload by limiting each session to one core objective.
                - Adjust pace after two consecutive weeks of missed targets.
                """.formatted(topic, normalizeLevel(learnerLevel)).trim();
    }

    private String normalizeLevel(String learnerLevel) {
        if (learnerLevel == null || learnerLevel.isBlank()) {
            return "beginner";
        }
        String normalized = learnerLevel.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "advanced" -> "advanced";
            case "intermediate" -> "intermediate";
            default -> "beginner";
        };
    }
}
