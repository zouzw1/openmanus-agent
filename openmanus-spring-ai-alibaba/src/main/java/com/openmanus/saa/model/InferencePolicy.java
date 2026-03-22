package com.openmanus.saa.model;

import java.util.List;

public record InferencePolicy(
        boolean inferenceAllowed,
        String inferenceScope,
        List<String> providedFacts,
        List<String> delegatedFields,
        List<String> mustConfirmFields,
        String rationale
) {
    public InferencePolicy {
        inferenceScope = inferenceScope == null || inferenceScope.isBlank() ? "none" : inferenceScope.trim();
        providedFacts = providedFacts == null ? List.of() : List.copyOf(providedFacts);
        delegatedFields = delegatedFields == null ? List.of() : List.copyOf(delegatedFields);
        mustConfirmFields = mustConfirmFields == null ? List.of() : List.copyOf(mustConfirmFields);
        rationale = rationale == null ? "" : rationale.trim();
    }

    public static InferencePolicy none() {
        return new InferencePolicy(false, "none", List.of(), List.of(), List.of(), "");
    }

    public boolean hasDelegatedFields() {
        return !delegatedFields.isEmpty();
    }

    public boolean hasMustConfirmFields() {
        return !mustConfirmFields.isEmpty();
    }
}
