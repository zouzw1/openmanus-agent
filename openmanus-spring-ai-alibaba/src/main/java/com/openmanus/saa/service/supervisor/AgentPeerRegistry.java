package com.openmanus.saa.service.supervisor;

import com.openmanus.saa.agent.AgentDefinition;
import com.openmanus.saa.agent.AgentRegistryService;
import com.openmanus.saa.config.OpenManusProperties;
import com.openmanus.saa.service.agent.SpecialistAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing AgentPeer instances.
 * Handles creation, discovery, and lifecycle of agent peers.
 */
public class AgentPeerRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentPeerRegistry.class);

    private final Map<String, AgentPeer> peers;
    private final AgentRegistryService agentRegistryService;
    private final Map<String, SpecialistAgent> specialistAgents;
    private final OpenManusProperties.MultiAgentProperties multiAgentProperties;
    private final SharedContextStore sharedContextStore;
    private final List<AgentLifecycleHook> globalLifecycleHooks;

    public AgentPeerRegistry(
            AgentRegistryService agentRegistryService,
            Map<String, SpecialistAgent> specialistAgents,
            OpenManusProperties.MultiAgentProperties multiAgentProperties,
            SharedContextStore sharedContextStore,
            List<AgentLifecycleHook> globalLifecycleHooks
    ) {
        this.peers = new ConcurrentHashMap<>();
        this.agentRegistryService = agentRegistryService;
        this.specialistAgents = specialistAgents;
        this.multiAgentProperties = multiAgentProperties;
        this.sharedContextStore = sharedContextStore;
        this.globalLifecycleHooks = globalLifecycleHooks != null ? new ArrayList<>(globalLifecycleHooks) : new ArrayList<>();
    }

    /**
     * Register an agent peer.
     *
     * @param peer the peer to register
     */
    public void register(AgentPeer peer) {
        peers.put(peer.getPeerId(), peer);
        log.info("Registered agent peer: {} ({})", peer.getPeerId(), peer.getName());
    }

    /**
     * Unregister an agent peer.
     *
     * @param peerId the peer ID to unregister
     * @return the removed peer or empty
     */
    public Optional<AgentPeer> unregister(String peerId) {
        AgentPeer removed = peers.remove(peerId);
        if (removed != null) {
            log.info("Unregistered agent peer: {}", peerId);
        }
        return Optional.ofNullable(removed);
    }

    /**
     * Get a peer by ID.
     *
     * @param peerId the peer ID
     * @return the peer or empty
     */
    public Optional<AgentPeer> getPeer(String peerId) {
        return Optional.ofNullable(peers.get(peerId));
    }

    /**
     * Get all registered peers.
     *
     * @return list of all peers
     */
    public List<AgentPeer> getAllPeers() {
        return new ArrayList<>(peers.values());
    }

    /**
     * Get all running peers.
     *
     * @return list of running peers
     */
    public List<AgentPeer> getRunningPeers() {
        return peers.values().stream()
                .filter(AgentPeer::isRunning)
                .toList();
    }

    /**
     * Create a new agent peer for a specific agent type.
     *
     * @param agentId the agent type ID
     * @return the created peer
     */
    public AgentPeer createPeer(String agentId) {
        return createPeer(agentId, null);
    }

    /**
     * Create a new agent peer for a specific agent type with a custom peer ID.
     *
     * @param agentId the agent type ID
     * @param customPeerId custom peer ID (or null for auto-generated)
     * @return the created peer
     */
    public AgentPeer createPeer(String agentId, String customPeerId) {
        AgentDefinition definition = agentRegistryService.get(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        SpecialistAgent executor = specialistAgents.get(agentId);
        if (executor == null) {
            throw new IllegalArgumentException("No specialist agent executor found for: " + agentId);
        }

        String peerId = customPeerId != null ? customPeerId : generatePeerId(agentId);

        AgentPeer peer = new AgentPeer(
                peerId,
                definition,
                executor,
                sharedContextStore,
                multiAgentProperties.getContextMaxTurns(),
                multiAgentProperties.getContextMaxChars(),
                multiAgentProperties.getMessageQueueSize(),
                multiAgentProperties.getTaskTimeoutSeconds(),
                globalLifecycleHooks
        );

        register(peer);
        return peer;
    }

    /**
     * Create peers for all available agents.
     *
     * @return list of created peers
     */
    public List<AgentPeer> createPeersForAllAgents() {
        List<AgentPeer> createdPeers = new ArrayList<>();
        for (com.openmanus.saa.agent.AgentDefinition def : agentRegistryService.listEnabled()) {
            String agentId = def.getId();
            try {
                AgentPeer peer = createPeer(agentId);
                createdPeers.add(peer);
            } catch (Exception e) {
                log.warn("Failed to create peer for agent {}: {}", agentId, e.getMessage());
            }
        }
        return createdPeers;
    }

    /**
     * Find peers by agent ID.
     *
     * @param agentId the agent ID
     * @return list of matching peers
     */
    public List<AgentPeer> findPeersByAgentId(String agentId) {
        return peers.values().stream()
                .filter(peer -> peer.getAgentId().equals(agentId))
                .toList();
    }

    /**
     * Find peers by agent name.
     *
     * @param agentName the agent name
     * @return list of matching peers
     */
    public List<AgentPeer> findPeersByName(String agentName) {
        return peers.values().stream()
                .filter(peer -> peer.getName().equals(agentName))
                .toList();
    }

    /**
     * Start all registered peers.
     */
    public void startAll() {
        peers.values().forEach(AgentPeer::start);
        log.info("Started {} agent peers", peers.size());
    }

    /**
     * Stop all registered peers.
     */
    public void stopAll() {
        peers.values().forEach(AgentPeer::stop);
        log.info("Stopped {} agent peers", peers.size());
    }

    /**
     * Clear all registered peers.
     */
    public void clear() {
        stopAll();
        peers.clear();
        log.info("Cleared all agent peers");
    }

    /**
     * Get the number of registered peers.
     *
     * @return the count
     */
    public int size() {
        return peers.size();
    }

    /**
     * Check if a peer exists.
     *
     * @param peerId the peer ID
     * @return true if exists
     */
    public boolean hasPeer(String peerId) {
        return peers.containsKey(peerId);
    }

    /**
     * Route a message to the target peer.
     *
     * @param message the message to route
     */
    public void routeMessage(AgentMessage message) {
        if (message.isBroadcast()) {
            // Broadcast to all peers except sender
            peers.values().stream()
                    .filter(peer -> !peer.getPeerId().equals(message.fromPeerId()))
                    .forEach(peer -> peer.receiveMessage(message));
            log.debug("Broadcast message from {} to {} peers", message.fromPeerId(), peers.size() - 1);
        } else {
            // Direct message
            getPeer(message.toPeerId())
                    .ifPresentOrElse(
                            peer -> peer.receiveMessage(message),
                            () -> log.warn("Target peer {} not found for message from {}", message.toPeerId(), message.fromPeerId())
                    );
        }
    }

    private String generatePeerId(String agentId) {
        return agentId + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
