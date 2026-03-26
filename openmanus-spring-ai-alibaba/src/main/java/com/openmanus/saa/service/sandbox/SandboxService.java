package com.openmanus.saa.service.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.openmanus.saa.config.OpenManusProperties;
import com.openmanus.saa.config.SandboxProperties;
import com.openmanus.saa.model.sandbox.SandboxCommandResponse;
import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class SandboxService {

    private static final Set<String> BLOCKED_PATTERNS = Set.of(
            "rm ",
            "del ",
            "rmdir ",
            "shutdown ",
            "reboot ",
            "mkfs",
            "format "
    );

    private final SandboxProperties sandboxProperties;
    private final OpenManusProperties openManusProperties;
    private DockerClient dockerClient;

    public SandboxService(SandboxProperties sandboxProperties, OpenManusProperties openManusProperties) {
        this.sandboxProperties = sandboxProperties;
        this.openManusProperties = openManusProperties;
    }

    public synchronized SandboxCommandResponse execute(String command) {
        if (!sandboxProperties.isEnabled()) {
            return new SandboxCommandResponse(false, false, "Sandbox is disabled.");
        }
        if (command == null || command.isBlank()) {
            return new SandboxCommandResponse(true, false, "Command is required.");
        }
        String normalized = command.toLowerCase(Locale.ROOT);
        for (String blockedPattern : BLOCKED_PATTERNS) {
            if (normalized.contains(blockedPattern)) {
                return new SandboxCommandResponse(true, false, "Blocked command pattern detected: " + blockedPattern);
            }
        }

        String containerId = null;
        try {
            DockerClient client = dockerClient();
            ensureImage(client);

            HostConfig hostConfig = buildHostConfig();

            CreateContainerResponse container = client.createContainerCmd(sandboxProperties.getImage())
                    .withCmd(buildShellCommand(command))
                    .withWorkingDir(sandboxProperties.getWorkingDirectory())
                    .withHostConfig(hostConfig)
                    .exec();
            containerId = container.getId();
            client.startContainerCmd(containerId).exec();

            int exitCode = client.waitContainerCmd(containerId)
                    .start()
                    .awaitStatusCode(sandboxProperties.getTimeoutSeconds(), TimeUnit.SECONDS);

            String output = new String(
                    client.logContainerCmd(containerId)
                            .withStdOut(true)
                            .withStdErr(true)
                            .exec(new InMemoryLogCallback())
                            .awaitCompletion()
                            .toByteArray(),
                    StandardCharsets.UTF_8
            );
            if (output.length() > 8000) {
                output = output.substring(0, 8000) + "\n<output clipped>";
            }
            return new SandboxCommandResponse(true, exitCode == 0, output);
        } catch (Exception ex) {
            return new SandboxCommandResponse(true, false, ex.getMessage());
        } finally {
            if (containerId != null) {
                try {
                    dockerClient().removeContainerCmd(containerId).withForce(true).exec();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private DockerClient dockerClient() {
        if (dockerClient != null) {
            return dockerClient;
        }
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(5))
                .responseTimeout(Duration.ofSeconds(sandboxProperties.getTimeoutSeconds()))
                .build();
        dockerClient = DockerClientImpl.getInstance(config, httpClient);
        return dockerClient;
    }

    private void ensureImage(DockerClient client) throws InterruptedException {
        if (!sandboxProperties.isAutoPullImage()) {
            return;
        }
        client.pullImageCmd(sandboxProperties.getImage())
                .start()
                .awaitCompletion();
    }

    private HostConfig buildHostConfig() throws IOException {
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withMemory(parseMemoryBytes(sandboxProperties.getMemoryLimit()))
                .withNetworkMode(sandboxProperties.isNetworkEnabled() ? "bridge" : "none");

        if (sandboxProperties.isMountWorkspace()) {
            Path hostWorkspace = resolveHostWorkspacePath();
            Files.createDirectories(hostWorkspace);
            hostConfig.withBinds(new Bind(
                    hostWorkspace.toAbsolutePath().normalize().toString(),
                    new Volume(sandboxProperties.getWorkspaceMountPath())
            ));
        }
        return hostConfig;
    }

    private String[] buildShellCommand(String command) {
        List<String> parts = new ArrayList<>();
        parts.add(sandboxProperties.getShellExecutable());
        if (sandboxProperties.getShellOption() != null && !sandboxProperties.getShellOption().isBlank()) {
            parts.add(sandboxProperties.getShellOption());
        }
        parts.add(command);
        return parts.toArray(String[]::new);
    }

    private Path resolveHostWorkspacePath() {
        String configuredPath = sandboxProperties.getHostWorkspacePath();
        if (configuredPath != null && !configuredPath.isBlank()) {
            return Paths.get(configuredPath).toAbsolutePath().normalize();
        }
        String workspace = openManusProperties.getWorkspace();
        if (workspace == null || workspace.isBlank()) {
            return Paths.get("./workspace").toAbsolutePath().normalize();
        }
        return Paths.get(workspace).toAbsolutePath().normalize();
    }

    private long parseMemoryBytes(String memoryLimit) {
        String value = memoryLimit.trim().toLowerCase(Locale.ROOT);
        if (value.endsWith("g")) {
            return Long.parseLong(value.substring(0, value.length() - 1)) * 1024L * 1024L * 1024L;
        }
        if (value.endsWith("m")) {
            return Long.parseLong(value.substring(0, value.length() - 1)) * 1024L * 1024L;
        }
        if (value.endsWith("k")) {
            return Long.parseLong(value.substring(0, value.length() - 1)) * 1024L;
        }
        return Long.parseLong(value);
    }

    @PreDestroy
    public synchronized void shutdown() throws IOException {
        if (dockerClient != null) {
            dockerClient.close();
            dockerClient = null;
        }
    }
}
