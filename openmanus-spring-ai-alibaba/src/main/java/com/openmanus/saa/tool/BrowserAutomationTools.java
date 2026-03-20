package com.openmanus.saa.tool;

import com.openmanus.saa.service.browser.BrowserSessionService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class BrowserAutomationTools {

    private final BrowserSessionService browserSessionService;

    public BrowserAutomationTools(BrowserSessionService browserSessionService) {
        this.browserSessionService = browserSessionService;
    }

    @Tool(description = "Run browser automation actions. Supported actions: go_to_url, click, input, scroll, extract_text, current_state")
    public String browserAction(
            String action,
            String url,
            String selector,
            String text,
            Integer scrollAmount
    ) {
        return browserSessionService.execute(action, url, selector, text, scrollAmount);
    }
}
