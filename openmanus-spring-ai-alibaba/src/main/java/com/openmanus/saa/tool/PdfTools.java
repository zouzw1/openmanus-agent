package com.openmanus.saa.tool;

import com.openmanus.saa.config.OpenManusProperties;
import com.openmanus.saa.model.ToolMetadata.Category;
import com.openmanus.saa.tool.annotation.ToolDefinition;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * PDF processing tools using OpenPDF and Flying Saucer.
 */
@Component
public class PdfTools {

    private static final Logger log = LoggerFactory.getLogger(PdfTools.class);

    private final Path workspaceRoot;

    public PdfTools(OpenManusProperties properties) throws IOException {
        this.workspaceRoot = Paths.get(properties.getWorkspace()).toAbsolutePath().normalize();
        Files.createDirectories(this.workspaceRoot);
    }

    /**
     * Convert markdown content to a PDF file.
     * This is a LOCAL TOOL - call it directly by name, NOT via callMcpTool.
     */
    @Tool(description = "Convert markdown content to a PDF file. LOCAL TOOL: call directly, NOT via callMcpTool.")
    @ToolDefinition(
        category = Category.FILE,
        tags = {"pdf", "markdown", "convert", "export"},
        dangerous = false,
        requiresConfirm = false
    )
    public String markdownToPdf(
            @ToolParam(description = "Markdown content to convert to PDF", required = true)
            String markdownContent,
            @ToolParam(description = "Output relative path for the PDF file (e.g., output/document.pdf)", required = true)
            String outputPath
    ) {
        try {
            // Convert markdown to HTML
            String html = markdownToHtml(markdownContent);

            // Generate PDF
            Path target = resolve(outputPath);
            Files.createDirectories(target.getParent());

            try (FileOutputStream os = new FileOutputStream(target.toFile())) {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.useFastMode();
                builder.withHtmlContent(html, null);
                builder.toStream(os);
                builder.run();
            }

            return "PDF created: " + workspaceRoot.relativize(target);
        } catch (Exception e) {
            log.error("Failed to create PDF from markdown", e);
            return "Failed to create PDF: " + e.getMessage();
        }
    }

    @Tool(description = "Convert a markdown file to a PDF file")
    @ToolDefinition(
        category = Category.FILE,
        tags = {"pdf", "markdown", "convert", "file"},
        dangerous = false,
        requiresConfirm = false
    )
    public String markdownFileToPdf(
            @ToolParam(description = "Relative path to the markdown file to convert", required = true)
            String inputPath,
            @ToolParam(description = "Output relative path for the PDF file", required = true)
            String outputPath
    ) {
        try {
            Path input = resolve(inputPath);
            if (!Files.exists(input)) {
                return "Input file does not exist: " + input;
            }

            String markdown = Files.readString(input, StandardCharsets.UTF_8);
            return markdownToPdf(markdown, outputPath);
        } catch (Exception e) {
            log.error("Failed to convert markdown file to PDF", e);
            return "Failed to convert markdown file: " + e.getMessage();
        }
    }

    @Tool(description = "Create a PDF from HTML content")
    @ToolDefinition(
        category = Category.FILE,
        tags = {"pdf", "html", "convert", "export"},
        dangerous = false,
        requiresConfirm = false
    )
    public String htmlToPdf(
            @ToolParam(description = "HTML content to convert to PDF", required = true)
            String htmlContent,
            @ToolParam(description = "Output relative path for the PDF file", required = true)
            String outputPath
    ) {
        try {
            Path target = resolve(outputPath);
            Files.createDirectories(target.getParent());

            try (FileOutputStream os = new FileOutputStream(target.toFile())) {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.useFastMode();
                builder.withHtmlContent(htmlContent, null);
                builder.toStream(os);
                builder.run();
            }

            return "PDF created: " + workspaceRoot.relativize(target);
        } catch (Exception e) {
            log.error("Failed to create PDF from HTML", e);
            return "Failed to create PDF: " + e.getMessage();
        }
    }

    /**
     * Convert markdown to styled HTML.
     */
    private String markdownToHtml(String markdown) {
        MutableDataSet options = new MutableDataSet();
        Parser parser = Parser.builder(options).build();
        HtmlRenderer renderer = HtmlRenderer.builder(options).build();

        String bodyHtml = renderer.render(parser.parse(markdown));

        // Wrap in a styled HTML document with Chinese font support
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8"/>
                <style>
                    body {
                        font-family: "SimSun", "Microsoft YaHei", "Helvetica", sans-serif;
                        font-size: 12pt;
                        line-height: 1.6;
                        margin: 40px;
                        color: #333;
                    }
                    h1 { font-size: 24pt; color: #1a1a1a; margin-bottom: 10px; }
                    h2 { font-size: 18pt; color: #2a2a2a; margin-top: 20px; }
                    h3 { font-size: 14pt; color: #3a3a3a; margin-top: 15px; }
                    p { margin: 10px 0; text-align: justify; }
                    ul, ol { margin: 10px 0; padding-left: 20px; }
                    li { margin: 5px 0; }
                    code {
                        background: #f4f4f4;
                        padding: 2px 6px;
                        border-radius: 3px;
                        font-family: monospace;
                    }
                    pre {
                        background: #f4f4f4;
                        padding: 10px;
                        border-radius: 5px;
                        overflow-x: auto;
                    }
                    table {
                        border-collapse: collapse;
                        width: 100%;
                        margin: 15px 0;
                    }
                    th, td {
                        border: 1px solid #ddd;
                        padding: 8px;
                        text-align: left;
                    }
                    th { background: #f8f8f8; }
                    blockquote {
                        border-left: 4px solid #ddd;
                        margin: 15px 0;
                        padding-left: 15px;
                        color: #666;
                    }
                </style>
            </head>
            <body>
            """ + bodyHtml + """
            </body>
            </html>
            """;
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
}
