package com.openmanus.saa.tool;

import com.openmanus.saa.config.OpenManusProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class ShellTools {

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

    public ShellTools(OpenManusProperties properties) {
        this.properties = properties;
    }

    @Tool(description = "Execute a non-destructive PowerShell command inside the workspace")
    public String runPowerShell(String command) throws IOException, InterruptedException {
        if (!properties.isShellEnabled()) {
            return "Shell tool is disabled. Enable openmanus.agent.shell-enabled to use it.";
        }
        String normalized = command.toLowerCase(Locale.ROOT);
        for (String blockedPattern : BLOCKED_PATTERNS) {
            if (normalized.contains(blockedPattern)) {
                return "Blocked command pattern detected: " + blockedPattern;
            }
        }

        Process process = new ProcessBuilder("powershell", "-Command", command)
                .directory(Paths.get(properties.getWorkspace()).toFile())
                .redirectErrorStream(true)
                .start();

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
}
