package com.openmanus.saa.controller;

import com.openmanus.saa.model.session.SessionStateResponse;
import com.openmanus.saa.service.session.SessionMemoryService;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionMemoryService sessionMemoryService;

    public SessionController(SessionMemoryService sessionMemoryService) {
        this.sessionMemoryService = sessionMemoryService;
    }

    @GetMapping
    public List<SessionStateResponse> listSessions() {
        return sessionMemoryService.listStates();
    }

    @GetMapping("/{sessionId}")
    public SessionStateResponse getSession(@PathVariable String sessionId) {
        return sessionMemoryService.getState(sessionId);
    }

    @DeleteMapping("/{sessionId}")
    public boolean deleteSession(@PathVariable String sessionId) {
        return sessionMemoryService.delete(sessionId);
    }
}
