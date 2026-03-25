package com.openmanus.saa.service.intent;

import com.openmanus.saa.model.session.SessionState;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

public final class SimpleIntentTreeNode implements IntentTreeNode {

    private final String id;
    private final BiPredicate<String, SessionState> matcher;
    private final BiFunction<String, SessionState, IntentDecision> decisionFunction;
    private final List<IntentTreeNode> children;

    private SimpleIntentTreeNode(
            String id,
            BiPredicate<String, SessionState> matcher,
            BiFunction<String, SessionState, IntentDecision> decisionFunction,
            List<IntentTreeNode> children
    ) {
        this.id = id;
        this.matcher = matcher;
        this.decisionFunction = decisionFunction;
        this.children = List.copyOf(children);
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean matches(String prompt, SessionState session) {
        return matcher.test(prompt, session);
    }

    @Override
    public IntentDecision decision(String prompt, SessionState session) {
        IntentDecision decision = decisionFunction.apply(prompt, session);
        return decision == null ? IntentDecision.empty() : decision;
    }

    @Override
    public List<IntentTreeNode> children() {
        return children;
    }

    public static final class Builder {
        private final String id;
        private BiPredicate<String, SessionState> matcher = (prompt, session) -> true;
        private BiFunction<String, SessionState, IntentDecision> decisionFunction = (prompt, session) -> IntentDecision.empty();
        private final List<IntentTreeNode> children = new ArrayList<>();

        private Builder(String id) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Intent tree node id must not be blank");
            }
            this.id = id.trim();
        }

        public Builder matcher(BiPredicate<String, SessionState> matcher) {
            this.matcher = matcher == null ? (prompt, session) -> true : matcher;
            return this;
        }

        public Builder decision(IntentDecision decision) {
            return decision((prompt, session) -> decision);
        }

        public Builder decision(BiFunction<String, SessionState, IntentDecision> decisionFunction) {
            this.decisionFunction = decisionFunction == null
                    ? (prompt, session) -> IntentDecision.empty()
                    : decisionFunction;
            return this;
        }

        public Builder child(IntentTreeNode child) {
            if (child != null) {
                this.children.add(child);
            }
            return this;
        }

        public Builder children(IntentTreeNode... children) {
            if (children == null) {
                return this;
            }
            for (IntentTreeNode child : children) {
                child(child);
            }
            return this;
        }

        public Builder children(List<? extends IntentTreeNode> children) {
            if (children == null) {
                return this;
            }
            for (IntentTreeNode child : children) {
                child(child);
            }
            return this;
        }

        public SimpleIntentTreeNode build() {
            return new SimpleIntentTreeNode(id, matcher, decisionFunction, children);
        }
    }
}
