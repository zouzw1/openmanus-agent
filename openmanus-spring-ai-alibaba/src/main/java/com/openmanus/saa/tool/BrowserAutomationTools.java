package com.openmanus.saa.tool;

import com.openmanus.saa.service.browser.BrowserSessionService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class BrowserAutomationTools {

    private final BrowserSessionService browserSessionService;

    public BrowserAutomationTools(BrowserSessionService browserSessionService) {
        this.browserSessionService = browserSessionService;
    }

    @Tool(description = "Run browser automation actions. Supported actions: go_to_url, click, input, scroll, extract_text, current_state")
    public String browserAction(
            @ToolParam(description = "Browser action to execute: go_to_url, click, input, scroll, extract_text, or current_state.", required = true)
            String action,
            @ToolParam(description = "Target URL for navigation actions.", required = false)
            String url,
            @ToolParam(description = "CSS selector for click, input, or extract_text actions.", required = false)
            String selector,
            @ToolParam(description = "Text to type for input actions.", required = false)
            String text,
            @ToolParam(description = "Scroll amount in pixels for scroll actions.", required = false)
            Integer scrollAmount
    ) {
        return browserSessionService.execute(action, url, selector, text, scrollAmount);
    }
}
