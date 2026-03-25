package com.openmanus.saa.service.summary;

public interface WorkflowSummaryFormatter {

    default boolean supports(WorkflowSummaryContext context) {
        return true;
    }

    String format(WorkflowSummaryContext context);
}
