package com.openmanus.saa.service.session.storage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.model.InferencePolicy;
import com.openmanus.saa.model.ResponseMode;
import com.openmanus.saa.model.session.ContentBlock;
import com.openmanus.saa.model.session.ConversationMessage;
import com.openmanus.saa.model.session.MessageRole;
import com.openmanus.saa.model.session.Session;
import com.openmanus.saa.model.session.TextBlock;
import com.openmanus.saa.model.session.TokenUsage;
import com.openmanus.saa.service.session.entity.ConversationMessageEntity;
import com.openmanus.saa.service.session.entity.SessionStateEntity;
import com.openmanus.saa.service.session.mapper.ConversationMessageMapper;
import com.openmanus.saa.service.session.mapper.SessionStateMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
    public Session save(Session session) {
        SessionStateEntity entity = toEntity(session);
        sessionMapper.insertOrUpdate(entity);

        // 删除旧消息并保存新消息
        messageMapper.deleteBySessionId(session.sessionId());
        for (int i = 0; i < session.messages().size(); i++) {
            ConversationMessage message = session.messages().get(i);
            ConversationMessageEntity messageEntity = toMessageEntity(session.sessionId(), i, message);
            messageMapper.insert(messageEntity);
        }

        log.debug("Saved session: {}, messages: {}", session.sessionId(), session.messages().size());
        return session;
    }

    @Override
    public Optional<Session> findById(String sessionId) {
        SessionStateEntity entity = sessionMapper.selectById(sessionId);
        if (entity == null) {
            return Optional.empty();
        }

        List<ConversationMessageEntity> messageEntities = messageMapper.findBySessionId(sessionId);
        return Optional.of(toSession(entity, messageEntities));
    }

    @Override
    public List<Session> findAll() {
        List<SessionStateEntity> entities = sessionMapper.selectList(null);
        return entities.stream()
            .map(entity -> {
                List<ConversationMessageEntity> messages = messageMapper.findBySessionId(entity.getSessionId());
                return toSession(entity, messages);
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
    public List<Session> findExpired(Instant threshold) {
        return sessionMapper.findExpired(threshold).stream()
            .map(entity -> {
                List<ConversationMessageEntity> messages = messageMapper.findBySessionId(entity.getSessionId());
                return toSession(entity, messages);
            })
            .collect(Collectors.toList());
    }

    @Override
    public long count() {
        return sessionMapper.selectCount(null);
    }

    private SessionStateEntity toEntity(Session session) {
        SessionStateEntity entity = new SessionStateEntity();
        entity.setSessionId(session.sessionId());
        entity.setVersion(session.version());
        entity.setCreatedAt(session.createdAt());
        entity.setUpdatedAt(session.updatedAt());
        entity.setLastAccessedAt(session.lastAccessedAt());

        if (session.latestInferencePolicy() != null) {
            try {
                entity.setLatestInferencePolicy(objectMapper.writeValueAsString(session.latestInferencePolicy()));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize inference policy", e);
            }
        }
        if (session.latestResponseMode() != null) {
            entity.setLatestResponseMode(session.latestResponseMode().name());
        }
        if (!session.workingMemory().isEmpty()) {
            try {
                entity.setWorkingMemoryJson(objectMapper.writeValueAsString(session.workingMemory()));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize working memory", e);
            }
        }
        if (session.cumulativeUsage() != null && !session.cumulativeUsage().equals(TokenUsage.zero())) {
            try {
                entity.setCumulativeUsageJson(objectMapper.writeValueAsString(session.cumulativeUsage()));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize cumulative usage", e);
            }
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

    private Session toSession(SessionStateEntity entity, List<ConversationMessageEntity> messageEntities) {
        List<ConversationMessage> messages = new ArrayList<>();
        for (ConversationMessageEntity messageEntity : messageEntities) {
            ConversationMessage message = toConversationMessage(messageEntity);
            messages.add(message);
        }

        Map<String, Object> workingMemory = new HashMap<>();
        if (entity.getWorkingMemoryJson() != null) {
            try {
                workingMemory = objectMapper.readValue(entity.getWorkingMemoryJson(), new TypeReference<HashMap<String, Object>>() {});
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize working memory", e);
            }
        }

        TokenUsage cumulativeUsage = TokenUsage.zero();
        if (entity.getCumulativeUsageJson() != null) {
            try {
                cumulativeUsage = objectMapper.readValue(entity.getCumulativeUsageJson(), TokenUsage.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize cumulative usage", e);
            }
        }

        InferencePolicy inferencePolicy = null;
        if (entity.getLatestInferencePolicy() != null) {
            try {
                inferencePolicy = objectMapper.readValue(entity.getLatestInferencePolicy(), InferencePolicy.class);
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize inference policy", e);
            }
        }

        ResponseMode responseMode = null;
        if (entity.getLatestResponseMode() != null) {
            try {
                responseMode = ResponseMode.valueOf(entity.getLatestResponseMode());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid response mode: {}", entity.getLatestResponseMode());
            }
        }

        return new Session(
            entity.getVersion() != null ? entity.getVersion() : 1,
            entity.getSessionId(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getLastAccessedAt() != null ? entity.getLastAccessedAt() : entity.getUpdatedAt(),
            messages,
            workingMemory,
            new ArrayList<>(), // executionLog is not persisted in DB currently
            cumulativeUsage,
            inferencePolicy,
            responseMode
        );
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
