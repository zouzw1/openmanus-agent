package com.openmanus.saa.service.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.openmanus.saa.config.BrowserProperties;
import com.openmanus.saa.model.browser.BrowserStateResponse;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

@Service
public class BrowserSessionService {

    private final BrowserProperties properties;
    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;

    public BrowserSessionService(BrowserProperties properties) {
        this.properties = properties;
    }

    public synchronized String execute(
            String action,
            String url,
            String selector,
            String text,
            Integer scrollAmount
    ) {
        if (!properties.isEnabled()) {
            return "Browser tool is disabled. Enable openmanus.browser.enabled to use it.";
        }
        ensureSession();
        return switch (action) {
            case "go_to_url" -> goToUrl(url);
            case "click" -> click(selector);
            case "input" -> input(selector, text);
            case "scroll" -> scroll(scrollAmount);
            case "extract_text" -> extractText(selector);
            case "current_state" -> currentState().toString();
            default -> "Unsupported browser action: " + action;
        };
    }

    public synchronized BrowserStateResponse currentState() {
        if (!properties.isEnabled()) {
            return new BrowserStateResponse(false, "", "", "");
        }
        ensureSession();
        String content = page.content();
        String preview = content.length() > 1000 ? content.substring(0, 1000) + "...<clipped>" : content;
        return new BrowserStateResponse(true, page.url(), page.title(), preview);
    }

    private String goToUrl(String url) {
        if (url == null || url.isBlank()) {
            return "URL is required.";
        }
        page.navigate(url);
        page.waitForTimeout(500);
        return "Navigated to " + page.url();
    }

    private String click(String selector) {
        if (selector == null || selector.isBlank()) {
            return "Selector is required.";
        }
        page.locator(selector).first().click();
        return "Clicked selector: " + selector;
    }

    private String input(String selector, String text) {
        if (selector == null || selector.isBlank()) {
            return "Selector is required.";
        }
        page.locator(selector).first().fill(text == null ? "" : text);
        return "Filled selector: " + selector;
    }

    private String scroll(Integer scrollAmount) {
        int amount = scrollAmount == null ? 800 : scrollAmount;
        page.evaluate("amount => window.scrollBy(0, amount)", amount);
        return "Scrolled by " + amount + " pixels";
    }

    private String extractText(String selector) {
        String extracted;
        if (selector == null || selector.isBlank()) {
            extracted = page.textContent("body");
        } else {
            extracted = page.locator(selector).first().textContent();
        }
        return extracted == null ? "" : extracted;
    }

    private void ensureSession() {
        if (page != null) {
            return;
        }
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserTypeOptionsFactory(properties).create());
        context = browser.newContext();
        context.setDefaultTimeout(properties.getTimeoutSeconds() * 1000L);
        page = context.newPage();
    }

    @PreDestroy
    public synchronized void shutdown() {
        if (page != null) {
            page.close();
            page = null;
        }
        if (context != null) {
            context.close();
            context = null;
        }
        if (browser != null) {
            browser.close();
            browser = null;
        }
        if (playwright != null) {
            playwright.close();
            playwright = null;
        }
    }

    private static final class BrowserTypeOptionsFactory {

        private final BrowserProperties properties;

        private BrowserTypeOptionsFactory(BrowserProperties properties) {
            this.properties = properties;
        }

        private BrowserType.LaunchOptions create() {
            BrowserType.LaunchOptions options = new BrowserType.LaunchOptions();
            options.setHeadless(properties.isHeadless());
            return options;
        }
    }
}
