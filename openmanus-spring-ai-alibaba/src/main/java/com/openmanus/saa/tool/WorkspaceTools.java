package com.openmanus.saa.tool;

import com.openmanus.saa.config.OpenManusProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Collectors;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceTools {

    private final Path workspaceRoot;

    public WorkspaceTools(OpenManusProperties properties) throws IOException {
        this.workspaceRoot = Paths.get(properties.getWorkspace()).toAbsolutePath().normalize();
        Files.createDirectories(this.workspaceRoot);
    }

    @Tool(description = "List files under the configured workspace directory")
    public String listWorkspaceFiles(String relativePath) throws IOException {
        Path target = resolve(relativePath);
        if (!Files.exists(target)) {
            return "Path does not exist: " + target;
        }
        if (Files.isRegularFile(target)) {
            return "FILE " + workspaceRoot.relativize(target);
        }
        try (var stream = Files.walk(target, 2)) {
            return stream
                    .filter(path -> !path.equals(target))
                    .sorted(Comparator.naturalOrder())
                    .map(path -> {
                        String type = Files.isDirectory(path) ? "DIR " : "FILE ";
                        return type + workspaceRoot.relativize(path);
                    })
                    .collect(Collectors.joining("\n"));
        }
    }

    @Tool(description = "Read a UTF-8 text file from the workspace")
    public String readWorkspaceFile(String relativePath) throws IOException {
        Path target = resolve(relativePath);
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            return "File does not exist: " + target;
        }
        return Files.readString(target, StandardCharsets.UTF_8);
    }

    @Tool(description = "Write a UTF-8 text file to the workspace, replacing existing content")
    public String writeWorkspaceFile(String relativePath, String content) throws IOException {
        Path target = resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
        return "Wrote file: " + workspaceRoot.relativize(target);
    }

    private Path resolve(String relativePath) {
        String safePath = relativePath == null || relativePath.isBlank() ? "." : relativePath;
        Path target = workspaceRoot.resolve(safePath).normalize();
        if (!target.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("Path escapes workspace: " + relativePath);
        }
        return target;
    }
}
