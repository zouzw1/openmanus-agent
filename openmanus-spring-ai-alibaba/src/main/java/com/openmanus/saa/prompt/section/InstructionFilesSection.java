package com.openmanus.saa.prompt.section;

import com.openmanus.saa.prompt.InstructionFile;
import com.openmanus.saa.prompt.InstructionFileDiscoverer;
import com.openmanus.saa.prompt.PromptContext;
import com.openmanus.saa.prompt.PromptSection;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 项目指令文件 Section。
 *
 * <p>从工作目录发现并渲染项目级指令文件（MANUS.md 等）。
 */
@Component
public class InstructionFilesSection implements PromptSection {

    private static final Logger log = LoggerFactory.getLogger(InstructionFilesSection.class);

    private final InstructionFileDiscoverer discoverer;

    @Value("${openmanus.workspace:./workspace}")
    private Path workspace;

    public InstructionFilesSection(InstructionFileDiscoverer discoverer) {
        this.discoverer = discoverer;
    }

    @Override
    public int order() {
        return 50;  // 在 Agent Profile 之后
    }

    @Override
    public String title() {
        return "## Project Instructions";
    }

    @Override
    public String render(PromptContext context) {
        Path cwd = resolveWorkingDirectory(context);
        List<InstructionFile> files = discoverer.discover(cwd);

        if (files.isEmpty()) {
            return "(No instruction files found)";
        }

        StringBuilder sb = new StringBuilder();
        for (InstructionFile file : files) {
            sb.append("### ").append(file.fileName());
            sb.append(" (scope: ").append(getScope(file.path(), cwd)).append(")\n");
            sb.append(file.content()).append("\n\n");
        }

        return sb.toString().trim();
    }

    /**
     * 解析工作目录
     */
    private Path resolveWorkingDirectory(PromptContext context) {
        // 优先使用运行时提示中的工作目录
        Object cwdHint = context.getHint("cwd");
        if (cwdHint instanceof Path cwd) {
            return cwd;
        }
        if (cwdHint instanceof String cwdStr) {
            return Path.of(cwdStr);
        }
        // 使用默认工作目录
        return workspace;
    }

    /**
     * 获取文件的作用域（相对于工作目录）
     */
    private String getScope(Path filePath, Path cwd) {
        if (cwd == null) {
            return filePath.getParent() != null
                ? filePath.getParent().toString()
                : "workspace";
        }

        Path parent = filePath.getParent();
        if (parent == null) {
            return "workspace";
        }

        // 查找相对于 cwd 的作用域
        Path relative = cwd.relativize(parent);
        return relative.toString();
    }
}
