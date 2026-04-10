package com.openmanus.saa.service.session.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 会话状态 MyBatis Plus 实体
 */
@TableName("session_state")
public class SessionStateEntity {

    @com.baomidou.mybatisplus.annotation.TableId(type = com.baomidou.mybatisplus.annotation.IdType.INPUT)
    @TableField("session_id")
    private String sessionId;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;

    @TableField("last_accessed_at")
    private Instant lastAccessedAt;

    @TableField("expires_at")
    private Instant expiresAt;

    @TableField("version")
    private Integer version;

    @TableField("compacted_summary")
    private String compactedSummary;

    @TableField("latest_inference_policy")
    private String latestInferencePolicy;

    @TableField("latest_response_mode")
    private String latestResponseMode;

    @TableField("working_memory_json")
    private String workingMemoryJson;

    @TableField("cumulative_usage_json")
    private String cumulativeUsageJson;

    public SessionStateEntity() {
    }

    public SessionStateEntity(String sessionId) {
        this.sessionId = sessionId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(Instant lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getCompactedSummary() {
        return compactedSummary;
    }

    public void setCompactedSummary(String compactedSummary) {
        this.compactedSummary = compactedSummary;
    }

    public String getLatestInferencePolicy() {
        return latestInferencePolicy;
    }

    public void setLatestInferencePolicy(String latestInferencePolicy) {
        this.latestInferencePolicy = latestInferencePolicy;
    }

    public String getLatestResponseMode() {
        return latestResponseMode;
    }

    public void setLatestResponseMode(String latestResponseMode) {
        this.latestResponseMode = latestResponseMode;
    }

    public String getWorkingMemoryJson() {
        return workingMemoryJson;
    }

    public void setWorkingMemoryJson(String workingMemoryJson) {
        this.workingMemoryJson = workingMemoryJson;
    }

    public String getCumulativeUsageJson() {
        return cumulativeUsageJson;
    }

    public void setCumulativeUsageJson(String cumulativeUsageJson) {
        this.cumulativeUsageJson = cumulativeUsageJson;
    }
}
