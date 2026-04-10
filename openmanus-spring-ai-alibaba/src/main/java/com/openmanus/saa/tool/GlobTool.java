package com.openmanus.saa.tool;

import com.openmanus.saa.config.OpenManusProperties;
import com.openmanus.saa.model.ToolMetadata.Category;
import com.openmanus.saa.tool.annotation.ToolDefinition;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Collectors;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Glob Tool - File pattern matching search.
 */
@Component
public class GlobTool {

    private final Path workspaceRoot;

    public GlobTool(OpenManusProperties properties) throws IOException {
        this.workspaceRoot = Paths.get(properties.getWorkspace()).toAbsolutePath().normalize();
        Files.createDirectories(this.workspaceRoot);
    }

    @Tool(description = "Find files matching a glob pattern. Returns matching file paths relative to workspace.")
    @ToolDefinition(
        category = Category.FILE,
        tags = {"search", "readonly", "pattern"},
        dangerous = false,
        requiresConfirm = false
    )
    public String glob(
            @ToolParam(description = "Glob pattern to match files. Examples: doublestar.java finds all Java files, src doublestar doublestar.xml finds XML files in src.", required = true)
            String pattern
    ) throws IOException {
        if (pattern == null || pattern.isBlank()) {
            return "Error: Pattern cannot be empty.";
        }

        String normalizedPattern = normalizePattern(pattern);
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + normalizedPattern);

        StringBuilder result = new StringBuilder();
        result.append("Files matching '").append(pattern).append("':\n\n");

        long count = 0;
        long maxResults = 500;

        try (var stream = Files.walk(workspaceRoot)) {
            var matches = stream
                .filter(Files::isRegularFile)
                .filter(matcher::matches)
                .sorted(Comparator.naturalOrder())
                .limit(maxResults + 1)
                .collect(Collectors.toList());

            for (Path match : matches) {
                if (count >= maxResults) {
                    result.append("\n... Results truncated at ").append(maxResults).append(" files.");
                    break;
                }
                result.append(workspaceRoot.relativize(match)).append("\n");
                count++;
            }
        }

        if (count == 0) {
            result.append("(No files found matching the pattern)");
        } else {
            result.append("\nFound ").append(count).append(" file(s).");
        }

        return result.toString();
    }

    @Tool(description = "Find directories matching a glob pattern.")
    @ToolDefinition(
        category = Category.FILE,
        tags = {"search", "readonly", "directory"},
        dangerous = false,
        requiresConfirm = false
    )
    public String globDirs(
            @ToolParam(description = "Glob pattern to match directories.", required = true)
            String pattern
    ) throws IOException {
        if (pattern == null || pattern.isBlank()) {
            return "Error: Pattern cannot be empty.";
        }

        String normalizedPattern = normalizePattern(pattern);
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + normalizedPattern);

        StringBuilder result = new StringBuilder();
        result.append("Directories matching '").append(pattern).append("':\n\n");

        long count = 0;
        long maxResults = 200;

        try (var stream = Files.walk(workspaceRoot)) {
            var matches = stream
                .filter(Files::isDirectory)
                .filter(p -> !p.equals(workspaceRoot))
                .filter(matcher::matches)
                .sorted(Comparator.naturalOrder())
                .limit(maxResults + 1)
                .collect(Collectors.toList());

            for (Path match : matches) {
                if (count >= maxResults) {
                    result.append("\n... Results truncated at ").append(maxResults).append(" directories.");
                    break;
                }
                result.append(workspaceRoot.relativize(match)).append("/\n");
                count++;
            }
        }

        if (count == 0) {
            result.append("(No directories found matching the pattern)");
        } else {
            result.append("\nFound ").append(count).append(" director(y/ies).");
        }

        return result.toString();
    }

    private String normalizePattern(String pattern) {
        String normalized = pattern.trim();

        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        if (!normalized.contains("*") && !normalized.contains("?")) {
            normalized = "**/" + normalized;
        }

        return normalized;
    }
}
