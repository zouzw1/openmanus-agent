package com.openmanus.saa.tool.security;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Git 安全检查器
 *
 * <p>专门检测危险的 Git 操作。
 */
public class GitSafetyChecker {

    /**
     * Git 操作危险级别
     */
    public enum GitSafetyLevel {
        SAFE,       // 安全操作
        CAUTION,    // 需要谨慎
        DANGEROUS,  // 危险操作
        FORBIDDEN   // 禁止操作
    }

    /**
     * 检查结果
     */
    public static class GitCheckResult {
        private final GitSafetyLevel level;
        private final String operation;
        private final String reason;
        private final String suggestion;

        public GitCheckResult(GitSafetyLevel level, String operation, String reason, String suggestion) {
            this.level = level;
            this.operation = operation;
            this.reason = reason;
            this.suggestion = suggestion;
        }

        public GitSafetyLevel getLevel() { return level; }
        public String getOperation() { return operation; }
        public String getReason() { return reason; }
        public String getSuggestion() { return suggestion; }

        public boolean isBlocked() {
            return level == GitSafetyLevel.FORBIDDEN || level == GitSafetyLevel.DANGEROUS;
        }

        @Override
        public String toString() {
            return String.format("[Git %s] %s: %s. %s", level, operation, reason, suggestion);
        }
    }

    // 禁止的 Git 操作
    private static final Set<Pattern> FORBIDDEN_PATTERNS = Set.of(
        Pattern.compile("(?i)^\\s*git\\s+push\\s+.*--force"),
        Pattern.compile("(?i)^\\s*git\\s+push\\s+.*-f"),
        Pattern.compile("(?i)^\\s*git\\s+push\\s+.*force-with-lease"),
        Pattern.compile("(?i)^\\s*git\\s+reset\\s+--hard\\s+(HEAD|\\w{7,})"),
        Pattern.compile("(?i)^\\s*git\\s+reset\\s+--hard"),
        Pattern.compile("(?i)^\\s*git\\s+clean\\s+-[fdx]"),
        Pattern.compile("(?i)^\\s*git\\s+filter-branch"),
        Pattern.compile("(?i)^\\s*git\\s+rebase\\s+-i\\s+.*--root"),
        Pattern.compile("(?i)^\\s*git\\s+filter-repo")
    );

    // 危险的 Git 操作
    private static final Set<Pattern> DANGEROUS_PATTERNS = Set.of(
        Pattern.compile("(?i)^\\s*git\\s+reset\\s+"),
        Pattern.compile("(?i)^\\s*git\\s+rebase\\s+"),
        Pattern.compile("(?i)^\\s*git\\s+checkout\\s+--"),
        Pattern.compile("(?i)^\\s*git\\s+stash\\s+drop"),
        Pattern.compile("(?i)^\\s*git\\s+reflog\\s+expire"),
        Pattern.compile("(?i)^\\s*git\\s+branch\\s+-D"),
        Pattern.compile("(?i)^\\s*git\\s+branch\\s+--force"),
        Pattern.compile("(?i)^\\s*git\\s+update-ref\\s+--delete"),
        Pattern.compile("(?i)^\\s*git\\s+push\\s+--delete"),
        Pattern.compile("(?i)^\\s*git\\s+push\\s+.*:refs/heads/")
    );

    // 需要谨慎的 Git 操作
    private static final Set<Pattern> CAUTION_PATTERNS = Set.of(
        Pattern.compile("(?i)^\\s*git\\s+push\\s+"),
        Pattern.compile("(?i)^\\s*git\\s+merge\\s+"),
        Pattern.compile("(?i)^\\s*git\\s+cherry-pick\\s+"),
        Pattern.compile("(?i)^\\s*git\\s+revert\\s+"),
        Pattern.compile("(?i)^\\s*git\\s+stash\\s+pop"),
        Pattern.compile("(?i)^\\s*git\\s+stash\\s+apply"),
        Pattern.compile("(?i)^\\s*git\\s+pull\\s+")
    );

    /**
     * 检查 Git 命令的安全性
     *
     * @param command 要检查的命令
     * @return 检查结果
     */
    public static GitCheckResult check(String command) {
        if (command == null || command.isBlank()) {
            return new GitCheckResult(GitSafetyLevel.SAFE, "none", "Empty command", "No action needed");
        }

        String trimmed = command.trim();

        // 提取 git 命令部分
        String gitCmd = extractGitCommand(trimmed);
        if (gitCmd == null) {
            return new GitCheckResult(GitSafetyLevel.SAFE, "none", "Not a git command", "Command does not appear to be a git operation");
        }

        // 检查禁止操作
        for (Pattern pattern : FORBIDDEN_PATTERNS) {
            if (pattern.matcher(trimmed).find()) {
                return new GitCheckResult(
                    GitSafetyLevel.FORBIDDEN,
                    gitCmd,
                    "This git operation is forbidden",
                    "Use safer alternatives: git push (without --force), git reset (--soft), git revert"
                );
            }
        }

        // 检查危险操作
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(trimmed).find()) {
                return new GitCheckResult(
                    GitSafetyLevel.DANGEROUS,
                    gitCmd,
                    "This git operation can cause data loss",
                    "Consider using safer alternatives or ensure you have a backup"
                );
            }
        }

        // 检查需要谨慎的操作
        for (Pattern pattern : CAUTION_PATTERNS) {
            if (pattern.matcher(trimmed).find()) {
                return new GitCheckResult(
                    GitSafetyLevel.CAUTION,
                    gitCmd,
                    "This git operation modifies repository state",
                    "Ensure you understand the implications before proceeding"
                );
            }
        }

        return new GitCheckResult(GitSafetyLevel.SAFE, gitCmd, "Operation appears safe", "No concerns detected");
    }

    /**
     * 快速检查命令是否为禁止的 Git 操作
     *
     * @param command 要检查的命令
     * @return 是否禁止
     */
    public static boolean isForbidden(String command) {
        GitCheckResult result = check(command);
        return result.getLevel() == GitSafetyLevel.FORBIDDEN;
    }

    /**
     * 快速检查命令是否危险
     *
     * @param command 要检查的命令
     * @return 是否危险
     */
    public static boolean isDangerous(String command) {
        GitCheckResult result = check(command);
        return result.isBlocked();
    }

    private static String extractGitCommand(String command) {
        String[] parts = command.trim().split("\\s+");
        if (parts.length > 0 && "git".equalsIgnoreCase(parts[0])) {
            return parts.length > 1 ? parts[1] : "git";
        }
        return null;
    }
}
