package com.openmanus.saa.service.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.saa.model.session.Session;
import org.springframework.stereotype.Component;
import java.io.IOException;

/**
 * Session 序列化器，使用 Jackson 进行 JSON 序列化/反序列化。
 */
@Component
public class SessionSerializer {

    private final ObjectMapper objectMapper;

    public SessionSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将 Session 序列化为 JSON 字符串
     */
    public String toJson(Session session) {
        try {
            return objectMapper.writeValueAsString(session);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize Session", e);
        }
    }

    /**
     * 从 JSON 字符串反序列化 Session
     */
    public Session fromJson(String json) {
        try {
            return objectMapper.readValue(json, Session.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize Session", e);
        }
    }
}
