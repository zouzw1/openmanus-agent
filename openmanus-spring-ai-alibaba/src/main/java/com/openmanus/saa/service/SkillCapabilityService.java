package com.openmanus.saa.service;

import com.openmanus.saa.model.SkillCapabilityDescriptor;
import com.openmanus.saa.model.SkillInfoResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SkillCapabilityService {

    private static final Logger log = LoggerFactory.getLogger(SkillCapabilityService.class);
    private static final Pattern FORMAT_PATTERN = Pattern.compile("\\.(docx|pptx|pdf|md|markdown|txt|html|htm|csv|json|xml|xlsx|xls|epub)\\b");
    private static final List<FormatKeyword> FORMAT_KEYWORDS = List.of(
            new FormatKeyword("docx", List.of("docx", "word document", "word file", "word 文档", "word")),
            new FormatKeyword("pptx", List.of("pptx", "powerpoint", "slide deck", "presentation", "slides", "幻灯片", "演示文稿")),
            new FormatKeyword("pdf", List.of("pdf", "portable document", "pdf 文件")),
            new FormatKeyword("md", List.of("markdown", ".md")),
            new FormatKeyword("txt", List.of("plain text", "text file", ".txt", "纯文本")),
            new FormatKeyword("html", List.of("html", "htm", "网页")),
            new FormatKeyword("csv", List.of("csv")),
            new FormatKeyword("json", List.of("json")),
            new FormatKeyword("xml", List.of("xml")),
            new FormatKeyword("xlsx", List.of("xlsx", "excel", "spreadsheet")),
            new FormatKeyword("xls", List.of("xls")),
            new FormatKeyword("epub", List.of("epub"))
    );

    private final SkillsService skillsService;

    public SkillCapabilityService(SkillsService skillsService) {
        this.skillsService = skillsService;
    }

    public Optional<SkillCapabilityDescriptor> getCapability(String skillName) {
        String normalizedQuery = normalizeSearchToken(skillName);
        if (normalizedQuery == null) {
            return Optional.empty();
        }
        List<SkillCapabilityDescriptor> capabilities = listAvailableCapabilities();
        Optional<SkillCapabilityDescriptor> exactSkillNameMatch = capabilities.stream()
                .filter(capability -> normalizedQuery.equals(normalizeSearchToken(capability.skillName())))
                .findFirst();
        if (exactSkillNameMatch.isPresent()) {
            return exactSkillNameMatch;
        }
        return capabilities.stream()
                .filter(capability -> matchesCapabilityAlias(capability, normalizedQuery))
                .findFirst();
    }

    public Optional<String> resolveSkillName(String skillName) {
        return getCapability(skillName).map(SkillCapabilityDescriptor::skillName);
    }

    public List<SkillCapabilityDescriptor> listAvailableCapabilities() {
        if (!skillsService.isEnabled()) {
            return List.of();
        }
        return skillsService.listSkills().stream()
                .map(this::buildDescriptor)
                .filter(Objects::nonNull)
                .toList();
    }

    public Optional<String> findMentionedSkillName(String text) {
        String normalizedText = normalizeSearchPhrase(text);
        if (normalizedText == null || !skillsService.isEnabled()) {
            return Optional.empty();
        }
        List<SkillCapabilityDescriptor> capabilities = listAvailableCapabilities();
        Optional<String> exactSkillNameMatch = capabilities.stream()
                .filter(capability -> phraseContainsToken(normalizedText, normalizeSearchToken(capability.skillName())))
                .map(SkillCapabilityDescriptor::skillName)
                .findFirst();
        if (exactSkillNameMatch.isPresent()) {
            return exactSkillNameMatch;
        }
        return capabilities.stream()
                .filter(capability -> capability.aliases().stream()
                        .map(this::normalizeSearchToken)
                        .filter(Objects::nonNull)
                        .anyMatch(alias -> phraseContainsToken(normalizedText, alias)))
                .map(SkillCapabilityDescriptor::skillName)
                .findFirst();
    }

    public Optional<String> resolveUniqueSkillForOutputFormat(String outputFormat) {
        if (outputFormat == null || outputFormat.isBlank()) {
            return Optional.empty();
        }
        String normalizedFormat = normalizeFormat(outputFormat);
        List<SkillCapabilityDescriptor> matches = listAvailableCapabilities().stream()
                .filter(capability -> capability.outputFormats().contains(normalizedFormat))
                .toList();
        if (matches.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(matches.get(0).skillName());
    }

    private SkillCapabilityDescriptor buildDescriptor(SkillInfoResponse skill) {
        if (skill == null || skill.name() == null || skill.name().isBlank()) {
            return null;
        }

        String content = readSkillContent(skill.name());
        SkillFrontMatter frontMatter = parseFrontMatter(content);
        String sourceText = String.join(
                "\n\n",
                safeText(skill.name()),
                safeText(skill.description()),
                safeText(content)
        );
        String normalized = sourceText.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> aliases = frontMatter.aliases().isEmpty()
                ? inferAliases(skill.name(), normalized)
                : new LinkedHashSet<>(frontMatter.aliases());
        LinkedHashSet<String> operations = frontMatter.operations().isEmpty()
                ? inferOperations(normalized)
                : new LinkedHashSet<>(frontMatter.operations());
        FormatInference formatInference = frontMatter.hasExplicitFormats()
                ? new FormatInference(new LinkedHashSet<>(frontMatter.inputFormats()), new LinkedHashSet<>(frontMatter.outputFormats()))
                : inferFormats(normalized, skill.name());
        LinkedHashSet<String> executionHints = frontMatter.executionHints().isEmpty()
                ? inferExecutionHints(normalized, formatInference, operations)
                : new LinkedHashSet<>(frontMatter.executionHints());
        String planningHint = !frontMatter.planningHint().isBlank()
                ? frontMatter.planningHint()
                : inferPlanningHint(skill, operations, formatInference);

        return new SkillCapabilityDescriptor(
                skill.name().trim(),
                List.copyOf(aliases),
                List.copyOf(operations),
                List.copyOf(formatInference.inputFormats()),
                List.copyOf(formatInference.outputFormats()),
                List.copyOf(executionHints),
                planningHint
        );
    }

    private String readSkillContent(String skillName) {
        try {
            return skillsService.readSkill(skillName);
        } catch (IOException ex) {
            log.warn("Failed to read skill content for capability inference: {}", skillName, ex);
            return "";
        }
    }

    private boolean matchesCapabilityAlias(SkillCapabilityDescriptor capability, String normalizedQuery) {
        return capability.aliases().stream()
                .map(this::normalizeSearchToken)
                .filter(Objects::nonNull)
                .anyMatch(alias -> alias.equals(normalizedQuery));
    }

    private List<String> capabilityAliases(SkillCapabilityDescriptor capability) {
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        aliases.add(capability.skillName());
        aliases.addAll(capability.aliases());
        return List.copyOf(aliases);
    }

    private LinkedHashSet<String> inferAliases(String skillName, String normalizedText) {
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        String cleaned = normalizeSkillName(skillName);
        if (cleaned != null) {
            aliases.add(cleaned);
            aliases.add(cleaned.replace('-', ' '));
            aliases.add(cleaned.replace("_", " "));

            List<String> parts = splitTokens(cleaned);
            if (parts.size() > 1) {
                String compact = String.join("-", parts);
                aliases.add(compact);
                aliases.add(String.join(" ", parts));
            }
            for (String part : parts) {
                if (part.length() >= 3 && !isGenericAliasToken(part)) {
                    aliases.add(part);
                }
            }
        }

        String formatFromSkillName = inferFormatFromSkillName(skillName);
        if (formatFromSkillName != null) {
            aliases.add(formatFromSkillName);
        }
        return aliases;
    }

    private LinkedHashSet<String> inferOperations(String normalizedText) {
        LinkedHashSet<String> operations = new LinkedHashSet<>();
        addIfContains(normalizedText, operations, "plan", "plan", "checklist", "roadmap", "schedule", "outline", "学习路线图", "计划");
        addIfContains(normalizedText, operations, "draft", "draft", "compose", "write", "初稿");
        addIfContains(normalizedText, operations, "convert", "convert", "转换", "转成", "导出为");
        addIfContains(normalizedText, operations, "export", "export", "导出", "generate", "create new", "save as");
        addIfContains(normalizedText, operations, "format", "format", "formatting", "排版", "样式", "格式");
        addIfContains(normalizedText, operations, "read", "read", "parse", "extract", "analyze", "读取", "分析", "提取");
        addIfContains(normalizedText, operations, "edit", "edit", "modify", "update", "批量修改", "修改");
        addIfContains(normalizedText, operations, "search", "search", "research", "查找", "检索");
        if (operations.isEmpty()) {
            operations.add("guide");
        }
        return operations;
    }

    private FormatInference inferFormats(String normalizedText, String skillName) {
        LinkedHashSet<String> mentionedFormats = extractMentionedFormats(normalizedText);
        String formatFromSkillName = inferFormatFromSkillName(skillName);
        if (formatFromSkillName != null) {
            mentionedFormats.add(formatFromSkillName);
        }
        LinkedHashSet<String> outputFormats = extractTargetFormats(normalizedText);
        LinkedHashSet<String> inputFormats = new LinkedHashSet<>();

        if (!outputFormats.isEmpty()) {
            inputFormats.addAll(mentionedFormats);
            inputFormats.removeAll(outputFormats);
        } else {
            inputFormats.addAll(mentionedFormats);
            outputFormats.addAll(mentionedFormats);
        }

        if (outputFormats.isEmpty() && looksLikeTextDraftSkill(normalizedText, skillName)) {
            outputFormats.add("md");
            outputFormats.add("txt");
        }

        if (inputFormats.isEmpty() && !outputFormats.isEmpty() && looksLikeExportSkill(normalizedText)) {
            inputFormats.add("md");
            inputFormats.add("txt");
            inputFormats.add("html");
        }

        if (mentionedFormats.isEmpty() && looksLikeTextDraftSkill(normalizedText, skillName)) {
            inputFormats.add("text");
            outputFormats.add("md");
            outputFormats.add("txt");
        }

        return new FormatInference(inputFormats, outputFormats);
    }

    private String inferFormatFromSkillName(String skillName) {
        if (skillName == null || skillName.isBlank()) {
            return null;
        }
        String normalized = skillName.toLowerCase(Locale.ROOT);
        for (FormatKeyword formatKeyword : FORMAT_KEYWORDS) {
            if (normalized.contains(formatKeyword.format())) {
                return formatKeyword.format();
            }
        }
        return null;
    }

    private LinkedHashSet<String> extractMentionedFormats(String normalizedText) {
        LinkedHashSet<String> formats = new LinkedHashSet<>();
        Matcher matcher = FORMAT_PATTERN.matcher(normalizedText);
        while (matcher.find()) {
            formats.add(normalizeFormat(matcher.group(1)));
        }
        for (FormatKeyword formatKeyword : FORMAT_KEYWORDS) {
            if (formatKeyword.matches(normalizedText)) {
                formats.add(formatKeyword.format());
            }
        }
        return formats;
    }

    private LinkedHashSet<String> extractTargetFormats(String normalizedText) {
        LinkedHashSet<String> outputs = new LinkedHashSet<>();
        for (FormatKeyword formatKeyword : FORMAT_KEYWORDS) {
            for (String keyword : formatKeyword.keywords()) {
                if (normalizedText.contains("to " + keyword)
                        || normalizedText.contains("into " + keyword)
                        || normalizedText.contains("输出为" + keyword)
                        || normalizedText.contains("导出为" + keyword)
                        || normalizedText.contains("转换为" + keyword)
                        || normalizedText.contains("save as " + keyword)) {
                    outputs.add(formatKeyword.format());
                }
            }
        }
        return outputs;
    }

    private LinkedHashSet<String> inferExecutionHints(
            String normalizedText,
            FormatInference formatInference,
            Set<String> operations
    ) {
        LinkedHashSet<String> hints = new LinkedHashSet<>();
        if (containsAny(normalizedText, "uv run", "python ", "python3 ", "powershell", "shell", "command line", "命令行")) {
            hints.add("runPowerShell");
        }
        if (containsAny(normalizedText, "save to file", "save as", "output file", "write the final text into the workspace",
                "写入文件", "保存到", "输出文件", "workspace")) {
            hints.add("writeWorkspaceFile");
        }
        if (containsAny(normalizedText, "input file", "read file", "analyze template", "读取文件", "输入文件")) {
            hints.add("readWorkspaceFile");
            hints.add("listWorkspaceFiles");
        }
        if (operations.contains("plan") || operations.contains("draft")) {
            hints.add("createPlan");
        }
        if (!formatInference.outputFormats().isEmpty() || !formatInference.inputFormats().isEmpty()) {
            hints.add("listWorkspaceFiles");
        }
        return hints;
    }

    private String inferPlanningHint(
            SkillInfoResponse skill,
            Set<String> operations,
            FormatInference formatInference
    ) {
        String description = safeText(skill.description());
        if (!description.isBlank()) {
            return description;
        }
        StringBuilder builder = new StringBuilder("Use this skill");
        if (!operations.isEmpty()) {
            builder.append(" for ").append(String.join(", ", operations));
        }
        if (!formatInference.outputFormats().isEmpty()) {
            builder.append(" with output ").append(String.join(", ", formatInference.outputFormats()));
        }
        builder.append(".");
        return builder.toString();
    }

    private SkillFrontMatter parseFrontMatter(String content) {
        if (content == null || content.isBlank()) {
            return SkillFrontMatter.empty();
        }
        String normalized = content.replace("\r\n", "\n");
        if (!normalized.startsWith("---\n")) {
            return SkillFrontMatter.empty();
        }
        int end = normalized.indexOf("\n---\n", 4);
        if (end < 0) {
            return SkillFrontMatter.empty();
        }
        String block = normalized.substring(4, end).trim();
        if (block.isBlank()) {
            return SkillFrontMatter.empty();
        }

        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        LinkedHashSet<String> operations = new LinkedHashSet<>();
        LinkedHashSet<String> inputFormats = new LinkedHashSet<>();
        LinkedHashSet<String> outputFormats = new LinkedHashSet<>();
        LinkedHashSet<String> executionHints = new LinkedHashSet<>();
        String planningHint = "";

        String currentKey = null;
        for (String rawLine : block.split("\n")) {
            String line = rawLine.trim();
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("- ") && currentKey != null) {
                addFrontMatterValue(currentKey, line.substring(2).trim(), aliases, operations, inputFormats, outputFormats, executionHints);
                continue;
            }
            int delimiter = line.indexOf(':');
            if (delimiter < 0) {
                continue;
            }
            currentKey = line.substring(0, delimiter).trim().toLowerCase(Locale.ROOT);
            String rawValue = stripQuoted(line.substring(delimiter + 1).trim());
            if (rawValue.startsWith("[") && rawValue.endsWith("]")) {
                for (String item : rawValue.substring(1, rawValue.length() - 1).split(",")) {
                    addFrontMatterValue(currentKey, stripQuoted(item.trim()), aliases, operations, inputFormats, outputFormats, executionHints);
                }
                continue;
            }
            if ("planning_hint".equals(currentKey) || "planning-hint".equals(currentKey) || "planninghint".equals(currentKey)) {
                planningHint = rawValue;
                continue;
            }
            addFrontMatterValue(currentKey, rawValue, aliases, operations, inputFormats, outputFormats, executionHints);
        }

        return new SkillFrontMatter(aliases, operations, inputFormats, outputFormats, executionHints, planningHint);
    }

    private void addFrontMatterValue(
            String key,
            String value,
            Set<String> aliases,
            Set<String> operations,
            Set<String> inputFormats,
            Set<String> outputFormats,
            Set<String> executionHints
    ) {
        if (value == null || value.isBlank()) {
            return;
        }
        switch (key) {
            case "alias", "aliases" -> aliases.add(value.trim());
            case "operation", "operations" -> operations.add(value.trim());
            case "input_format", "input_formats", "inputformats" -> inputFormats.add(normalizeFormat(value));
            case "output_format", "output_formats", "outputformats" -> outputFormats.add(normalizeFormat(value));
            case "execution_hint", "execution_hints", "executionhints", "required_tool", "required_tools", "requiredtools" ->
                    executionHints.add(value.trim());
            default -> {
            }
        }
    }

    private String stripQuoted(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    private boolean looksLikeTextDraftSkill(String normalizedText, String skillName) {
        return containsAny(normalizedText, "plan", "checklist", "roadmap", "schedule", "markdown", "plain text", "text draft")
                || containsAny(safeText(skillName).toLowerCase(Locale.ROOT), "plan", "markdown");
    }

    private boolean looksLikeExportSkill(String normalizedText) {
        return containsAny(normalizedText, "export", "render", "format", "create", "generate", "save as", "导出", "生成");
    }

    private void addIfContains(
            String normalizedText,
            LinkedHashSet<String> operations,
            String operation,
            String... keywords
    ) {
        if (containsAny(normalizedText, keywords)) {
            operations.add(operation);
        }
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private List<String> splitTokens(String skillName) {
        if (skillName == null || skillName.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String token : skillName.toLowerCase(Locale.ROOT).split("[-_\\s]+")) {
            if (token.isBlank() || isGenericAliasToken(token)) {
                continue;
            }
            tokens.add(token);
        }
        return List.copyOf(tokens);
    }

    private boolean isGenericAliasToken(String token) {
        return "skill".equals(token) || "main".equals(token) || "tool".equals(token) || "project".equals(token);
    }

    private String normalizeSkillName(String skillName) {
        if (skillName == null) {
            return null;
        }
        String normalized = skillName.trim();
        if (normalized.startsWith("skill:")) {
            normalized = normalized.substring("skill:".length()).trim();
        }
        return normalized.isBlank() ? null : normalized;
    }

    private String normalizeSearchToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return token.toLowerCase(Locale.ROOT)
                .replace("skill:", "")
                .replace('_', ' ')
                .replace('-', ' ')
                .trim();
    }

    private String normalizeSearchPhrase(String text) {
        String normalized = normalizeSearchToken(text);
        if (normalized == null) {
            return null;
        }
        return " " + normalized.replaceAll("[^\\p{L}\\p{N}]+", " ").trim() + " ";
    }

    private boolean phraseContainsToken(String normalizedPhrase, String normalizedToken) {
        if (normalizedPhrase == null || normalizedToken == null || normalizedToken.isBlank()) {
            return false;
        }
        String compactToken = normalizedToken.replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        if (compactToken.isBlank()) {
            return false;
        }
        return normalizedPhrase.contains(" " + compactToken + " ");
    }

    private String normalizeFormat(String format) {
        if (format == null || format.isBlank()) {
            return "";
        }
        String normalized = format.toLowerCase(Locale.ROOT).replace(".", "").trim();
        if ("markdown".equals(normalized)) {
            return "md";
        }
        if ("htm".equals(normalized)) {
            return "html";
        }
        return normalized;
    }

    private String safeText(String text) {
        return text == null ? "" : text.trim();
    }

    private record FormatInference(
            LinkedHashSet<String> inputFormats,
            LinkedHashSet<String> outputFormats
    ) {
    }

    private record SkillFrontMatter(
            LinkedHashSet<String> aliases,
            LinkedHashSet<String> operations,
            LinkedHashSet<String> inputFormats,
            LinkedHashSet<String> outputFormats,
            LinkedHashSet<String> executionHints,
            String planningHint
    ) {
        private static SkillFrontMatter empty() {
            return new SkillFrontMatter(
                    new LinkedHashSet<>(),
                    new LinkedHashSet<>(),
                    new LinkedHashSet<>(),
                    new LinkedHashSet<>(),
                    new LinkedHashSet<>(),
                    ""
            );
        }

        private boolean hasExplicitFormats() {
            return !inputFormats.isEmpty() || !outputFormats.isEmpty();
        }
    }

    private record FormatKeyword(
            String format,
            List<String> keywords
    ) {
        private boolean matches(String text) {
            if (text == null || text.isBlank()) {
                return false;
            }
            return keywords.stream().anyMatch(text::contains);
        }
    }
}
