package com.openmanus.saa.tool;

import com.openmanus.saa.config.OpenManusProperties;
import com.openmanus.saa.model.ToolMetadata.Category;
import com.openmanus.saa.tool.annotation.ToolDefinition;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Grep Tool - File content search using regex.
 */
@Component
public class GrepTool {

    private final Path workspaceRoot;

    public GrepTool(OpenManusProperties properties) throws IOException {
        this.workspaceRoot = Paths.get(properties.getWorkspace()).toAbsolutePath().normalize();
        Files.createDirectories(this.workspaceRoot);
    }

    @Tool(description = "Search for a regex pattern in files. Returns matching lines with file paths and line numbers.")
    @ToolDefinition(
        category = Category.FILE,
        tags = {"search", "readonly", "regex"},
        dangerous = false,
        requiresConfirm = false
    )
    public String grep(
            @ToolParam(description = "Regex pattern to search for.", required = true)
            String pattern,
            @ToolParam(description = "Optional glob pattern to filter files (e.g. doublestar.java).", required = false)
            String glob,
            @ToolParam(description = "Maximum number of results. Default is 100.", required = false)
            Integer maxResults
    ) throws IOException {
        if (pattern == null || pattern.isBlank()) {
            return "Error: Pattern cannot be empty.";
        }

        int max = maxResults != null && maxResults > 0 ? maxResults : 100;

        try {
            Pattern compiledPattern = Pattern.compile(pattern);
            StringBuilder result = new StringBuilder();

            result.append("Searching for: '").append(pattern).append("'");
            if (glob != null && !glob.isBlank()) {
                result.append(" in files matching: ").append(glob);
            }
            result.append("\n\n");

            List<SearchResult> allMatches = new ArrayList<>();
            searchFiles(compiledPattern, glob, allMatches);

            if (allMatches.isEmpty()) {
                result.append("(No matches found)");
                return result.toString();
            }

            int count = 0;
            Path lastFile = null;

            for (SearchResult match : allMatches) {
                if (count >= max) {
                    result.append("\n... Results truncated at ").append(max).append(" matches.");
                    break;
                }

                if (!match.file.equals(lastFile)) {
                    if (lastFile != null) {
                        result.append("\n");
                    }
                    result.append("=== ").append(workspaceRoot.relativize(match.file)).append(" ===\n");
                    lastFile = match.file;
                }

                String linePrefix = String.format("%4d: ", match.lineNumber);
                result.append(linePrefix).append(match.content).append("\n");
                count++;
            }

            result.append("\nFound ").append(count).append(" match(es) in ")
                  .append(allMatches.stream().map(m -> m.file).distinct().count())
                  .append(" file(s).");

            return result.toString();

        } catch (PatternSyntaxException e) {
            return "Error: Invalid regex pattern - " + e.getMessage();
        }
    }

    @Tool(description = "Count occurrences of a pattern in files.")
    @ToolDefinition(
        category = Category.FILE,
        tags = {"search", "readonly", "count"},
        dangerous = false,
        requiresConfirm = false
    )
    public String grepCount(
            @ToolParam(description = "Regex pattern to count.", required = true)
            String pattern,
            @ToolParam(description = "Optional glob pattern to filter files.", required = false)
            String glob
    ) throws IOException {
        if (pattern == null || pattern.isBlank()) {
            return "Error: Pattern cannot be empty.";
        }

        try {
            Pattern compiledPattern = Pattern.compile(pattern);
            StringBuilder result = new StringBuilder();

            result.append("Counting: '").append(pattern).append("'\n\n");

            List<SearchResult> allMatches = new ArrayList<>();
            searchFiles(compiledPattern, glob, allMatches);

            if (allMatches.isEmpty()) {
                result.append("(No matches found)");
                return result.toString();
            }

            var byFile = allMatches.stream()
                .collect(java.util.stream.Collectors.groupingBy(m -> m.file));

            long totalCount = 0;
            for (var entry : byFile.entrySet()) {
                long count = entry.getValue().size();
                totalCount += count;
                result.append(workspaceRoot.relativize(entry.getKey()))
                      .append(": ")
                      .append(count)
                      .append(" match(es)\n");
            }

            result.append("\nTotal: ").append(totalCount).append(" match(es) in ")
                  .append(byFile.size()).append(" file(s).");

            return result.toString();

        } catch (PatternSyntaxException e) {
            return "Error: Invalid regex pattern - " + e.getMessage();
        }
    }

    private void searchFiles(Pattern pattern, String glob, List<SearchResult> results) throws IOException {
        if (glob != null && !glob.isBlank()) {
            searchFilesWithGlob(pattern, glob, results);
        } else {
            searchAllFiles(pattern, results);
        }
    }

    private void searchFilesWithGlob(Pattern pattern, String glob, List<SearchResult> results) throws IOException {
        java.nio.file.FileSystem fs = FileSystems.getDefault();
        PathMatcher matcher = fs.getPathMatcher("glob:" + glob);

        try (var stream = Files.walk(workspaceRoot)) {
            stream.filter(Files::isRegularFile)
                .filter(matcher::matches)
                .filter(this::isTextFile)
                .forEach(file -> searchInFile(file, pattern, results));
        }
    }

    private void searchAllFiles(Pattern pattern, List<SearchResult> results) throws IOException {
        try ( var stream = Files.walk(workspaceRoot)) {
            stream.filter(Files::isRegularFile)
                .filter(this::isTextFile)
                .forEach(file -> searchInFile(file, pattern, results));
        }
    }

    private void searchInFile(Path file, Pattern pattern, List<SearchResult> results) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (pattern.matcher(line).find()) {
                    results.add(new SearchResult(file, i + 1, line));
                }
            }
        } catch (Exception e) {
            // Skip binary or unreadable files
        }
    }

    private boolean isTextFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return !name.endsWith(".class") && !name.endsWith(".jar") &&
               !name.endsWith(".png") && !name.endsWith(".jpg") &&
               !name.endsWith(".gif") && !name.endsWith(".pdf") &&
               !name.endsWith(".zip") && !name.endsWith(".tar") &&
               !name.endsWith(".gz") && !name.endsWith(".exe") &&
               !name.endsWith(".dll") && !name.endsWith(".so") &&
               !name.endsWith(".doc") && !name.endsWith(".docx");
    }

    private static class SearchResult {
        final Path file;
        final int lineNumber;
        final String content;

        SearchResult(Path file, int lineNumber, String content) {
            this.file = file;
            this.lineNumber = lineNumber;
            this.content = content;
        }
    }
}
