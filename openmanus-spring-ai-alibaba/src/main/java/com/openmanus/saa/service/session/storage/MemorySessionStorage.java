package com.openmanus.saa.service.session.storage;

import com.openmanus.saa.model.session.Session;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 内存会话存储实现
 *
 * <p>默认存储方式，数据保存在 JVM 内存中。
 * 重启后数据丢失，适用于开发测试或单实例部署。
 */
@Component
@ConditionalOnProperty(
    name = "openmanus.session.storage",
    havingValue = "memory",
    matchIfMissing = true
)
public class MemorySessionStorage implements SessionStorage {

    private static final Logger log = LoggerFactory.getLogger(MemorySessionStorage.class);

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    @Override
    public Session save(Session session) {
        sessions.put(session.sessionId(), session);
        log.debug("Saved session: {}, messages: {}",
            session.sessionId(), session.messages().size());
        return session;
    }

    @Override
    public Optional<Session> findById(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public List<Session> findAll() {
        return List.copyOf(sessions.values());
    }

    @Override
    public void deleteById(String sessionId) {
        Session removed = sessions.remove(sessionId);
        if (removed != null) {
            log.info("Deleted session: {}", sessionId);
        }
    }

    @Override
    public List<Session> findExpired(Instant threshold) {
        return sessions.values().stream()
            .filter(s -> s.updatedAt().isBefore(threshold))
            .toList();
    }

    @Override
    public long count() {
        return sessions.size();
    }

    @Override
    public boolean existsById(String sessionId) {
        return sessions.containsKey(sessionId);
    }
}
