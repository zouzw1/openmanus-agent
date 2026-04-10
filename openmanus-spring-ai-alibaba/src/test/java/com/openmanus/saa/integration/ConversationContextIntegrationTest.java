package com.openmanus.saa.integration;

import com.openmanus.saa.model.session.Session;
import com.openmanus.saa.service.session.SessionMemoryService;
import com.openmanus.saa.service.session.storage.MemorySessionStorage;
import com.openmanus.saa.config.SessionConfig;
import com.openmanus.saa.service.session.SessionCompactor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 对话上下文感知系统的集成测试。
 */
class ConversationContextIntegrationTest {

    private SessionMemoryService sessionMemoryService;
    private Session session;

    @BeforeEach
    void setUp() {
        MemorySessionStorage storage = new MemorySessionStorage();
        SessionCompactor compactor = new SessionCompactor();
        SessionConfig config = new SessionConfig();
        sessionMemoryService = new SessionMemoryService(storage, compactor, config);
        session = sessionMemoryService.getOrCreate("test-integration-session");
    }

    @Test
    void testUserPreferencePersistence() {
        String sessionId = "test-pref-session";

        // 先创建 session
        sessionMemoryService.getOrCreate(sessionId);

        // 保存偏好
        sessionMemoryService.saveUserPreference(sessionId, "diet", "喜欢吃辣");

        // 验证偏好持久化
        Optional<Object> pref = sessionMemoryService.getUserPreference(sessionId, "diet");
        assertTrue(pref.isPresent());
        assertEquals("喜欢吃辣", pref.get());
    }

    @Test
    void testMultipleUserPreferences() {
        String sessionId = "test-multi-pref-session";

        // 先创建 session
        sessionMemoryService.getOrCreate(sessionId);

        // 保存多个偏好
        sessionMemoryService.saveUserPreference(sessionId, "diet", "喜欢吃辣");
        sessionMemoryService.saveUserPreference(sessionId, "budget", "中等");
        sessionMemoryService.saveUserPreference(sessionId, "travel_style", "深度游");

        // 验证所有偏好
        Map<String, Object> prefs = sessionMemoryService.getUserPreferences(sessionId);
        assertEquals(3, prefs.size());
        assertEquals("喜欢吃辣", prefs.get("diet"));
        assertEquals("中等", prefs.get("budget"));
        assertEquals("深度游", prefs.get("travel_style"));
    }
}