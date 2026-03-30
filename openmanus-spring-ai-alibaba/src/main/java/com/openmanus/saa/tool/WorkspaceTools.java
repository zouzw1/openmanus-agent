package com.openmanus.saa.tool;

import com.openmanus.saa.config.OpenManusProperties;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceTools {

    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            ".doc", ".docx", ".pdf", ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp",
            ".zip", ".jar", ".class", ".exe", ".dll", ".so", ".pkl", ".bin"
    );

    private final Path workspaceRoot;

    public WorkspaceTools(OpenManusProperties properties) throws IOException {
        this.workspaceRoot = Paths.get(properties.getWorkspace()).toAbsolutePath().normalize();
        Files.createDirectories(this.workspaceRoot);
    }

    @Tool(description = "List files under the configured workspace directory")
    public String listWorkspaceFiles(
            @ToolParam(description = "Optional relative path inside the workspace to inspect. Defaults to the workspace root.", required = false)
            String relativePath
    ) throws IOException {
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
    public String readWorkspaceFile(
            @ToolParam(description = "Relative path of the UTF-8 text file to read from the workspace.", required = true)
            String relativePath
    ) throws IOException {
        Path target = resolve(relativePath);
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            return "File does not exist: " + target;
        }
        if (looksBinary(target)) {
            return "File is not a UTF-8 text file and cannot be read with readWorkspaceFile: "
                    + workspaceRoot.relativize(target)
                    + ". Use a format-appropriate tool instead.";
        }
        try {
            return Files.readString(target, StandardCharsets.UTF_8);
        } catch (MalformedInputException ex) {
            return "File is not valid UTF-8 text and cannot be read with readWorkspaceFile: "
                    + workspaceRoot.relativize(target)
                    + ". Use a format-appropriate tool instead.";
        }
    }

    @Tool(description = "Write a UTF-8 text file to the workspace, replacing existing content")
    public String writeWorkspaceFile(
            @ToolParam(description = "Relative path of the file to write inside the workspace.", required = true)
            String relativePath,
            @ToolParam(description = "Full UTF-8 text content that should replace the file contents.", required = true)
            String content
    ) throws IOException {
        Path target = resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
        return "Wrote file: " + workspaceRoot.relativize(target);
    }

    private Path resolve(String relativePath) {
        String safePath = normalizeWorkspaceRelativePath(relativePath);
        Path target = workspaceRoot.resolve(safePath == null || safePath.isBlank() ? "." : safePath).normalize();
        if (!target.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("Path escapes workspace: " + relativePath);
        }
        return target;
    }

    private String normalizeWorkspaceRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return ".";
        }
        String normalized = relativePath.replace('\\', '/').trim();
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        if (normalized.equals("workspace")) {
            return ".";
        }
        while (normalized.startsWith("workspace/")) {
            normalized = normalized.substring("workspace/".length());
        }
        return normalized;
    }

    private boolean looksBinary(Path target) {
        String fileName = target.getFileName() == null ? "" : target.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String extension : BINARY_EXTENSIONS) {
            if (fileName.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }
}
