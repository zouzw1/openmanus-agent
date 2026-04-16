package com.openmanus.saa.tool;

import com.openmanus.saa.config.OpenManusProperties;
import com.openmanus.saa.config.SandboxProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class ShellTools {

    private static final Pattern PYTHON3_TOKEN = Pattern.compile("(?i)(?<![\\p{L}\\p{N}_-])python3(?![\\p{L}\\p{N}_-])");
    private static final Set<String> BLOCKED_PATTERNS = Set.of(
            "rm ",
            "del ",
            "rmdir ",
            "format ",
            "shutdown ",
            "reboot ",
            "git reset --hard"
    );

    private final OpenManusProperties properties;
    private final SandboxProperties sandboxProperties;
    private final SandboxTools sandboxTools;

    public ShellTools(
            OpenManusProperties properties,
            SandboxProperties sandboxProperties,
            SandboxTools sandboxTools
    ) {
        this.properties = properties;
        this.sandboxProperties = sandboxProperties;
        this.sandboxTools = sandboxTools;
    }

    @Tool(description = "Execute a non-destructive shell command, routed to sandbox or host based on configuration")
    public String runPowerShell(
            @ToolParam(description = "Non-destructive shell command to execute inside the configured workspace.", required = true)
            String command
    ) throws IOException, InterruptedException {
        // 透明路由：sandbox 启用时优先走 sandbox
        if (sandboxProperties.isEnabled()) {
            return sandboxTools.runSandboxCommand(command);
        }

        // fallback: 原有宿主机执行逻辑
        String normalizedCommand = normalizeCommand(command);
        if (!properties.isShellEnabled()) {
            return "Shell tool is disabled. Enable openmanus.agent.shell-enabled to use it.";
        }
        String normalized = normalizedCommand.toLowerCase(Locale.ROOT);
        for (String blockedPattern : BLOCKED_PATTERNS) {
            if (normalized.contains(blockedPattern)) {
                return "Blocked command pattern detected: " + blockedPattern;
            }
        }

        ProcessBuilder processBuilder = new ProcessBuilder("powershell", "-Command", normalizedCommand)
                .directory(Paths.get(properties.getWorkspace()).toFile())
                .redirectErrorStream(true);
        enrichProcessPath(processBuilder.environment());

        Process process = processBuilder.start();

        boolean finished = process.waitFor(
                Duration.ofSeconds(properties.getShellTimeoutSeconds()).toSeconds(),
                TimeUnit.SECONDS
        );
        if (!finished) {
            process.destroyForcibly();
            return "Command timed out after " + properties.getShellTimeoutSeconds() + " seconds.";
        }

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (output.length() > 8000) {
            output = output.substring(0, 8000) + "\n<output clipped>";
        }
        return "Exit code: " + process.exitValue() + "\n" + output;
    }

    private String normalizeCommand(String command) {
        if (command == null || command.isBlank()) {
            return "";
        }
        return PYTHON3_TOKEN.matcher(command).replaceAll("python");
    }

    private void enrichProcessPath(Map<String, String> environment) {
        configureUvEnvironment(environment);

        String userHome = environment.getOrDefault("USERPROFILE", System.getProperty("user.home", ""));
        if (userHome == null || userHome.isBlank()) {
            return;
        }

        Path uvBin = Paths.get(userHome, ".local", "bin");
        if (!Files.isDirectory(uvBin)) {
            return;
        }

        String pathSeparator = System.getProperty("path.separator", ";");
        String currentPath = environment.getOrDefault("Path", environment.getOrDefault("PATH", ""));
        String uvBinString = uvBin.toString();
        if (currentPath.toLowerCase(Locale.ROOT).contains(uvBinString.toLowerCase(Locale.ROOT))) {
            return;
        }

        String updatedPath = uvBinString + pathSeparator + currentPath;
        environment.put("Path", updatedPath);
        environment.put("PATH", updatedPath);
    }

    private void configureUvEnvironment(Map<String, String> environment) {
        if (environment.containsKey("UV_CACHE_DIR")) {
            return;
        }

        Path workspacePath = Paths.get(properties.getWorkspace()).toAbsolutePath().normalize();
        Path uvCacheDir = workspacePath.resolve(".uv-cache");
        try {
            Files.createDirectories(uvCacheDir);
            environment.put("UV_CACHE_DIR", uvCacheDir.toString());
        } catch (IOException ignored) {
            // Best-effort only. If directory creation fails, uv will fall back to its default cache location.
        }
    }
}
