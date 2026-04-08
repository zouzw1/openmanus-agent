package com.openmanus.saa.service.intent;

import com.openmanus.saa.model.IntentResolution;
import com.openmanus.saa.model.IntentRouteMode;
import com.openmanus.saa.model.context.WorkflowState;
import com.openmanus.saa.model.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 识别用户是否要继续暂停的工作流。
 * 最高优先级，先于其他识别器检查。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SessionContextIntentRecognizer implements IntentRecognizer {

    private static final Logger log = LoggerFactory.getLogger(SessionContextIntentRecognizer.class);
    private static final String WORKFLOW_STATE_KEY = "workflowState";

    @Override
    public Optional<IntentResolution> recognize(String prompt, Session session) {
        if (session == null) {
            return Optional.empty();
        }

        WorkflowState state = session.getMemory(WORKFLOW_STATE_KEY, WorkflowState.class)
            .orElse(WorkflowState.none());

        // 检查是否有暂停的工作流
        if (state.isPaused()) {
            log.info("Detected paused workflow for session, routing to CONTINUE");
            return Optional.of(new IntentResolution(
                "continue_paused_workflow",
                1.0,
                IntentRouteMode.CONTINUE,
                null,
                Map.of("workflowState", state),
                null
            ));
        }

        return Optional.empty();
    }
}