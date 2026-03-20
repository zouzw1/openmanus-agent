package com.openmanus.saa.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class PlanningTools {

    private final Map<String, List<String>> plans = new ConcurrentHashMap<>();

    @Tool(description = "Create or replace a simple execution plan")
    public String createPlan(String planId, List<String> steps) {
        plans.put(planId, new ArrayList<>(steps));
        return "Plan created: " + planId + ", steps=" + steps.size();
    }

    @Tool(description = "Get a previously created plan")
    public String getPlan(String planId) {
        List<String> steps = plans.get(planId);
        if (steps == null) {
            return "Plan not found: " + planId;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < steps.size(); i++) {
            builder.append(i + 1).append(". ").append(steps.get(i)).append("\n");
        }
        return builder.toString().trim();
    }
}
