package com.openmanus.saa.agent;

import java.util.LinkedHashSet;
import java.util.Set;

public class McpAccessPolicy {

    private final CapabilityAccessMode mode;
    private final Set<String> servers;
    private final Set<String> tools;

    public McpAccessPolicy(CapabilityAccessMode mode, Set<String> servers, Set<String> tools) {
        this.mode = mode == null ? CapabilityAccessMode.ALLOW_LIST : mode;
        this.servers = servers == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(servers));
        this.tools = tools == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(tools));
    }

    public CapabilityAccessMode getMode() {
        return mode;
    }

    public Set<String> getServers() {
        return servers;
    }

    public Set<String> getTools() {
        return tools;
    }

    public boolean isDenied() {
        return mode == CapabilityAccessMode.DENY_ALL;
    }

    public boolean allows(String serverId, String toolName) {
        if (mode == CapabilityAccessMode.DENY_ALL) {
            return false;
        }
        if (mode == CapabilityAccessMode.ALLOW_ALL) {
            return true;
        }
        String toolKey = serverId + "/" + toolName;
        return tools.contains(toolKey) || servers.contains(serverId);
    }
}
