package com.openmanus.saa.service.session;

import com.openmanus.saa.config.SessionConfig;
import com.openmanus.saa.model.context.WorkflowState;
import com.openmanus.saa.model.session.Session;
import com.openmanus.saa.service.session.storage.MemorySessionStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SessionMemoryServiceContextTest {

    private SessionMemoryService service;
    private Session session;

    @BeforeEach
    void setUp() {
        MemorySessionStorage storage = new MemorySessionStorage();
        SessionCompactor compactor = new SessionCompactor();
        SessionConfig config = new SessionConfig();
        service = new SessionMemoryService(storage, compactor, config);
        session = service.getOrCreate("test-session");
    }

    @Test
    void saveUserPreference_shouldPersist() {
        service.saveUserPreference("test-session", "diet", "喜欢吃辣");

        Optional<Object> pref = service.getUserPreference("test-session", "diet");
        assertTrue(pref.isPresent());
        assertEquals("喜欢吃辣", pref.get());
    }

    @Test
    void saveMultipleUserPreferences_shouldPersistAll() {
        service.saveUserPreference("test-session", "diet", "喜欢吃辣");
        service.saveUserPreference("test-session", "budget", "中等");

        Map<String, Object> prefs = service.getUserPreferences("test-session");
        assertEquals(2, prefs.size());
        assertEquals("喜欢吃辣", prefs.get("diet"));
        assertEquals("中等", prefs.get("budget"));
    }

    @Test
    void getUserPreference_nonExistent_shouldReturnEmpty() {
        Optional<Object> pref = service.getUserPreference("test-session", "nonexistent");
        assertFalse(pref.isPresent());
    }
}
