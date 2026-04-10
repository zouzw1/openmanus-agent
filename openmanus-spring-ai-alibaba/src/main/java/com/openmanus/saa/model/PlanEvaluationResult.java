package com.openmanus.saa.model;

import java.util.List;

/**
 * Plan 评估结果。
 *
 * @param needsRevision 是否需要修订 plan
 * @param revisionSuggestion 修订建议（当 needsRevision=true 时填充）
 * @param missingElements 缺失要素列表（如 "缺少最终整合步骤"）
 */
public record PlanEvaluationResult(
    boolean needsRevision,
    String revisionSuggestion,
    List<String> missingElements
) {
    public static PlanEvaluationResult ok() {
        return new PlanEvaluationResult(false, null, List.of());
    }

    public static PlanEvaluationResult needsRevision(String suggestion, List<String> missing) {
        return new PlanEvaluationResult(true, suggestion, missing);
    }
}
