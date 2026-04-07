package com.openmanus.saa.service.session.storage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.model.session.ContentBlock;
import com.openmanus.saa.model.session.ConversationMessage;
import com.openmanus.saa.model.session.MessageRole;
import com.openmanus.saa.model.session.SessionState;
import com.openmanus.saa.model.session.TextBlock;
import com.openmanus.saa.model.session.TokenUsage;
import com.openmanus.saa.service.session.entity.ConversationMessageEntity;
import com.openmanus.saa.service.session.entity.SessionStateEntity;
import com.openmanus.saa.service.session.mapper.ConversationMessageMapper;
import com.openmanus.saa.service.session.mapper.SessionStateMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * MyBatis Plus 会话存储实现
 *
 * <p>使用 MySQL 数据库持久化会话数据。
 * 需要配置 {@code openmanus.session.storage=mysql}。
 */
@Component
@ConditionalOnProperty(
    name = "openmanus.session.storage",
    havingValue = "mysql"
)
public class MybatisSessionStorage implements SessionStorage {

    private static final Logger log = LoggerFactory.getLogger(MybatisSessionStorage.class);

    private final SessionStateMapper sessionMapper;
    private final ConversationMessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    public MybatisSessionStorage(SessionStateMapper sessionMapper,
                                  ConversationMessageMapper messageMapper,
                                  ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public SessionState save(SessionState session) {
        SessionStateEntity entity = toEntity(session);
        sessionMapper.insertOrUpdate(entity);

        // 删除旧消息并保存新消息
        messageMapper.deleteBySessionId(session.getSessionId());
        for (int i = 0; i < session.getMessages().size(); i++) {
            ConversationMessage message = session.getMessages().get(i);
            ConversationMessageEntity messageEntity = toMessageEntity(session.getSessionId(), i, message);
            messageMapper.insert(messageEntity);
        }

        log.debug("Saved session: {}, messages: {}", session.getSessionId(), session.getMessages().size());
        return session;
    }

    @Override
    public Optional<SessionState> findById(String sessionId) {
        SessionStateEntity entity = sessionMapper.selectById(sessionId);
        if (entity == null) {
            return Optional.empty();
        }

        List<ConversationMessageEntity> messageEntities = messageMapper.findBySessionId(sessionId);
        return Optional.of(toSessionState(entity, messageEntities));
    }

    @Override
    public List<SessionState> findAll() {
        List<SessionStateEntity> entities = sessionMapper.selectList(null);
        return entities.stream()
            .map(entity -> {
                List<ConversationMessageEntity> messages = messageMapper.findBySessionId(entity.getSessionId());
                return toSessionState(entity, messages);
            })
            .collect(Collectors.toList());
    }

    @Override
    public void deleteById(String sessionId) {
        messageMapper.deleteBySessionId(sessionId);
        sessionMapper.deleteById(sessionId);
        log.info("Deleted session: {}", sessionId);
    }

    @Override
    public List<SessionState> findExpired(Instant threshold) {
        return sessionMapper.findExpired(threshold).stream()
            .map(entity -> {
                List<ConversationMessageEntity> messages = messageMapper.findBySessionId(entity.getSessionId());
                return toSessionState(entity, messages);
            })
            .collect(Collectors.toList());
    }

    @Override
    public long count() {
        return sessionMapper.selectCount(null);
    }

    private SessionStateEntity toEntity(SessionState session) {
        SessionStateEntity entity = new SessionStateEntity();
        entity.setSessionId(session.getSessionId());
        entity.setCreatedAt(session.getCreatedAt());
        entity.setUpdatedAt(session.getUpdatedAt());
        session.getCompactedSummary().ifPresent(entity::setCompactedSummary);
        if (session.getLatestInferencePolicy() != null) {
            try {
                entity.setLatestInferencePolicy(objectMapper.writeValueAsString(session.getLatestInferencePolicy()));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize inference policy", e);
            }
        }
        if (session.getLatestResponseMode() != null) {
            entity.setLatestResponseMode(session.getLatestResponseMode().name());
        }
        return entity;
    }

    private ConversationMessageEntity toMessageEntity(String sessionId, int seqNum, ConversationMessage message) {
        ConversationMessageEntity entity = new ConversationMessageEntity();
        entity.setSessionId(sessionId);
        entity.setSeqNum(seqNum);
        entity.setRole(message.role().name().toLowerCase());
        // Extract text content from blocks for backward compatibility
        String content = message.blocks().stream()
            .filter(b -> b instanceof TextBlock)
            .map(b -> ((TextBlock) b).text())
            .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
        entity.setContent(content);
        entity.setTimestamp(message.timestamp());

        try {
            if (message.blocks() != null && !message.blocks().isEmpty()) {
                entity.setBlocksJson(objectMapper.writeValueAsString(message.blocks()));
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize blocks for message", e);
        }

        return entity;
    }

    private SessionState toSessionState(SessionStateEntity entity, List<ConversationMessageEntity> messageEntities) {
        SessionState session = new SessionState(entity.getSessionId());

        // Note: SessionState constructor sets createdAt and updatedAt
        // We need to update them from entity
        session = new SessionState(entity.getSessionId());
        java.lang.reflect.Field createdAtField;
        java.lang.reflect.Field updatedAtField;
        try {
            createdAtField = SessionState.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(session, entity.getCreatedAt());

            updatedAtField = SessionState.class.getDeclaredField("updatedAt");
            updatedAtField.setAccessible(true);
            updatedAtField.set(session, entity.getUpdatedAt());
        } catch (Exception e) {
            log.warn("Failed to set session timestamps", e);
        }

        if (entity.getCompactedSummary() != null) {
            session.setCompactedSummary(entity.getCompactedSummary());
        }

        for (ConversationMessageEntity messageEntity : messageEntities) {
            ConversationMessage message = toConversationMessage(messageEntity);
            session.addMessage(message);
        }

        return session;
    }

    private ConversationMessage toConversationMessage(ConversationMessageEntity entity) {
        List<ContentBlock> blocks = null;
        if (entity.getBlocksJson() != null) {
            try {
                blocks = objectMapper.readValue(entity.getBlocksJson(), new TypeReference<List<ContentBlock>>() {});
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize blocks", e);
            }
        }

        if (blocks != null) {
            return new ConversationMessage(
                MessageRole.valueOf(entity.getRole().toUpperCase()),
                blocks,
                null,
                entity.getTimestamp());
        }
        // Fallback: create TextBlock from content
        return new ConversationMessage(
            MessageRole.valueOf(entity.getRole().toUpperCase()),
            entity.getContent() != null ? List.of(new TextBlock(entity.getContent())) : List.of(),
            null,
            entity.getTimestamp());
    }
}
