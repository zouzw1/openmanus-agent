package com.openmanus.saa.config;

import com.openmanus.saa.model.session.CompactionConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "openmanus.session")
public class SessionConfig {

    public enum StorageType {
        MEMORY, MYSQL, JSON_FILE
    }

    private StorageType storage = StorageType.MEMORY;
    private Duration sessionTtl = Duration.ofMinutes(30);
    private Duration cleanupInterval = Duration.ofMinutes(1);
    private boolean compactionEnabled = true;
    private CompactionConfig compaction = CompactionConfig.DEFAULT;

    // Getters and setters
    public StorageType getStorage() { return storage; }
    public void setStorage(StorageType storage) { this.storage = storage; }

    public Duration getSessionTtl() { return sessionTtl; }
    public void setSessionTtl(Duration sessionTtl) { this.sessionTtl = sessionTtl; }

    public Duration getCleanupInterval() { return cleanupInterval; }
    public void setCleanupInterval(Duration cleanupInterval) { this.cleanupInterval = cleanupInterval; }

    public boolean isCompactionEnabled() { return compactionEnabled; }
    public void setCompactionEnabled(boolean compactionEnabled) { this.compactionEnabled = compactionEnabled; }

    public CompactionConfig getCompaction() { return compaction; }
    public void setCompaction(CompactionConfig compaction) { this.compaction = compaction; }

    public CompactionConfig getCompactionConfig() { return compaction; }
}
