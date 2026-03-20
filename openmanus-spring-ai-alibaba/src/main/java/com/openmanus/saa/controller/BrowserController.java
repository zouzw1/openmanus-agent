package com.openmanus.saa.controller;

import com.openmanus.saa.model.browser.BrowserActionRequest;
import com.openmanus.saa.model.browser.BrowserStateResponse;
import com.openmanus.saa.service.browser.BrowserSessionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/browser")
public class BrowserController {

    private final BrowserSessionService browserSessionService;

    public BrowserController(BrowserSessionService browserSessionService) {
        this.browserSessionService = browserSessionService;
    }

    @PostMapping("/action")
    public String action(@Valid @RequestBody BrowserActionRequest request) {
        return browserSessionService.execute(
                request.action(),
                request.url(),
                request.selector(),
                request.text(),
                request.scrollAmount()
        );
    }

    @GetMapping("/state")
    public BrowserStateResponse state() {
        return browserSessionService.currentState();
    }
}
