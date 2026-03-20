package com.openmanus.saa.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openmanus.mcp")
public class McpProperties {

    private boolean enabled = false;
    private Map<String, ServerConfig> servers = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, ServerConfig> getServers() {
        return servers;
    }

    public void setServers(Map<String, ServerConfig> servers) {
        this.servers = servers;
    }

    public static class ServerConfig {
        private String type = "sse";
        private String url;
        private String sseEndpoint = "/sse";
        private String command;
        private List<String> args = new ArrayList<>();

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getCommand() {
            return command;
        }

        public String getSseEndpoint() {
            return sseEndpoint;
        }

        public void setSseEndpoint(String sseEndpoint) {
            this.sseEndpoint = sseEndpoint;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public List<String> getArgs() {
            return args;
        }

        public void setArgs(List<String> args) {
            this.args = args;
        }
    }
}
