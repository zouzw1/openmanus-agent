package com.openmanus.saa.tool.security;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 破坏性命令检测器
 *
 * <p>检测潜在的破坏性命令，如删除、格式化、强制推送等。
 * 参考 claw-code 的 destructiveCommandWarning 模块。
 */
public class DestructiveCommandDetector {

    /**
     * 危险程度级别
     */
    public enum DangerLevel {
        /**
         * 安全 - 无风险
         */
        SAFE,
        /**
         * 警告 - 可能造成影响
         */
        WARNING,
        /**
         * 危险 - 高风险操作
         */
        DANGEROUS,
        /**
         * 极其危险 - 可能造成不可逆损害
         */
        CRITICAL
    }

    /**
     * 检测结果
     */
    public static class DetectionResult {
        private final DangerLevel level;
        private final String reason;
        private final String suggestion;

        public DetectionResult(DangerLevel level, String reason, String suggestion) {
            this.level = level;
            this.reason = reason;
            this.suggestion = suggestion;
        }

        public DangerLevel getLevel() { return level; }
        public String getReason() { return reason; }
        public String getSuggestion() { return suggestion; }

        public boolean isDangerous() {
            return level == DangerLevel.DANGEROUS || level == DangerLevel.CRITICAL;
        }

        @Override
        public String toString() {
            return String.format("[%s] %s. %s", level, reason, suggestion);
        }
    }

    // 危险命令模式 (CRITICAL 级别)
    private static final Set<Pattern> CRITICAL_PATTERNS = Set.of(
        // 删除整个目录/磁盘
        Pattern.compile("(?i)\\brm\\s+(-[rf]+|-[fr]+|/--)"),
        Pattern.compile("(?i)\\brmdir\\s+/s"),
        Pattern.compile("(?i)\\bdel\\s+/s"),
        Pattern.compile("(?i)\\bdel\\s+/q"),
        Pattern.compile("(?i)\\bformat\\s+"),
        Pattern.compile("(?i)\\bdbcclean\\s+"),
        // 系统命令
        Pattern.compile("(?i)\\bshutdown\\b"),
        Pattern.compile("(?i)\\breboot\\b"),
        Pattern.compile("(?i)\\bhalt\\b"),
        Pattern.compile("(?i)\\bpoweroff\\b"),
        // 危险 git 操作
        Pattern.compile("(?i)\\bgit\\s+push\\s+.*--force"),
        Pattern.compile("(?i)\\bgit\\s+push\\s+.*-f\\b"),
        Pattern.compile("(?i)\\bgit\\s+reset\\s+--hard"),
        Pattern.compile("(?i)\\bgit\\s+clean\\s+-[fdx]+"),
        // 数据库
        Pattern.compile("(?i)\\bdrop\\s+(database|table|schema)"),
        Pattern.compile("(?i)\\btruncate\\s+"),
        // 网络
        Pattern.compile("(?i)\\bnetsh\\s+"),
        Pattern.compile("(?i)\\biptables\\s+-F"),
        Pattern.compile("(?i)\\bifconfig\\s+.*down"),
        // 进程
        Pattern.compile("(?i)\\bkill\\s+-9\\s+1\\b"),
        Pattern.compile("(?i)\\bkillall\\b"),
        // 文件系统
        Pattern.compile("(?i)\\bdd\\s+.*of=/dev/"),
        Pattern.compile("(?i)\\bmkfs\\s+")
    );

    // 危险命令模式 (DANGEROUS 级别)
    private static final Set<Pattern> DANGEROUS_PATTERNS = Set.of(
        // 删除文件
        Pattern.compile("(?i)\\brm\\s+"),
        Pattern.compile("(?i)\\bdel\\b"),
        Pattern.compile("(?i)\\berase\\b"),
        // 修改权限
        Pattern.compile("(?i)\\bchmod\\s+777"),
        Pattern.compile("(?i)\\bchown\\s+"),
        // 覆盖写入
        Pattern.compile("(?i)\\bshred\\b"),
        // 用户管理
        Pattern.compile("(?i)\\buserdel\\b"),
        Pattern.compile("(?i)\\buseradd\\b"),
        // 服务管理
        Pattern.compile("(?i)\\bsystemctl\\s+(stop|disable|restart)"),
        Pattern.compile("(?i)\\bservice\\s+.*\\s+(stop|restart)"),
        // 包管理 (可能影响系统)
        Pattern.compile("(?i)\\bapt(-get)?\\s+(remove|purge)"),
        Pattern.compile("(?i)\\byum\\s+remove"),
        Pattern.compile("(?i)\\bpip\\s+uninstall"),
        Pattern.compile("(?i)\\bnpm\\s+uninstall"),
        // Git 危险操作
        Pattern.compile("(?i)\\bgit\\s+reset\\b"),
        Pattern.compile("(?i)\\bgit\\s+checkout\\s+--\\."),
        Pattern.compile("(?i)\\bgit\\s+stash\\s+clear"),
        // Docker
        Pattern.compile("(?i)\\bdocker\\s+(rm|rmi|system\\s+prune)"),
        Pattern.compile("(?i)\\bdocker\\s+volume\\s+rm"),
        // Kubernetes
        Pattern.compile("(?i)\\bkubectl\\s+delete\\s+")
    );

    // 警告模式 (WARNING 级别)
    private static final Set<Pattern> WARNING_PATTERNS = Set.of(
        // 文件操作
        Pattern.compile("(?i)\\bmv\\s+"),
        Pattern.compile("(?i)\\bcopy\\b"),
        Pattern.compile("(?i)\\bcp\\s+"),
        Pattern.compile("(?i)\\bmove\\b"),
        // 安装操作
        Pattern.compile("(?i)\\bapt(-get)?\\s+install"),
        Pattern.compile("(?i)\\byum\\s+install"),
        Pattern.compile("(?i)\\bpip\\s+install"),
        Pattern.compile("(?i)\\bnpm\\s+install"),
        Pattern.compile("(?i)\\bmvn\\s+deploy"),
        // Git 操作
        Pattern.compile("(?i)\\bgit\\s+push\\b"),
        Pattern.compile("(?i)\\bgit\\s+merge\\b"),
        Pattern.compile("(?i)\\bgit\\s+rebase\\b"),
        // 网络操作
        Pattern.compile("(?i)\\bcurl\\s+.*\\|\\s*\\w+"), // curl | something
        Pattern.compile("(?i)\\bwget\\s+.*\\|\\s*\\w+"), // wget | something
        // 环境变量
        Pattern.compile("(?i)\\bexport\\s+\\w+="),
        Pattern.compile("(?i)\\bsetx\\s+")
    );

    // 危险关键字 (用于快速检查)
    private static final Set<String> DANGER_KEYWORDS = Set.of(
        "rm -rf", "rm -fr", "del /s", "del /q", "format",
        "shutdown", "reboot", "git push --force", "git push -f",
        "git reset --hard", "drop database", "drop table",
        "truncate", "dd if=", "kill -9", "chmod 777"
    );

    /**
     * 检测命令的危险程度
     *
     * @param command 要检测的命令
     * @return 检测结果
     */
    public static DetectionResult detect(String command) {
        if (command == null || command.isBlank()) {
            return new DetectionResult(DangerLevel.SAFE, "Empty command", "No action needed");
        }

        String normalizedCommand = command.trim();

        // 1. 快速关键字检查
        String lowerCommand = normalizedCommand.toLowerCase();
        for (String keyword : DANGER_KEYWORDS) {
            if (lowerCommand.contains(keyword)) {
                return new DetectionResult(
                    DangerLevel.CRITICAL,
                    "Command contains dangerous keyword: " + keyword,
                    "Review carefully before executing. Consider using safer alternatives."
                );
            }
        }

        // 2. 检查 CRITICAL 模式
        for (Pattern pattern : CRITICAL_PATTERNS) {
            if (pattern.matcher(normalizedCommand).find()) {
                return new DetectionResult(
                    DangerLevel.CRITICAL,
                    "Command matches critical pattern: " + pattern.pattern(),
                    "This command may cause irreversible damage. Review carefully."
                );
            }
        }

        // 3. 检查 DANGEROUS 模式
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(normalizedCommand).find()) {
                return new DetectionResult(
                    DangerLevel.DANGEROUS,
                    "Command matches dangerous pattern: " + pattern.pattern(),
                    "This command may cause significant changes. Consider adding safeguards."
                );
            }
        }

        // 4. 检查 WARNING 模式
        for (Pattern pattern : WARNING_PATTERNS) {
            if (pattern.matcher(normalizedCommand).find()) {
                return new DetectionResult(
                    DangerLevel.WARNING,
                    "Command matches warning pattern: " + pattern.pattern(),
                    "This command may modify files or system state."
                );
            }
        }

        return new DetectionResult(DangerLevel.SAFE, "No dangerous patterns detected", "Command appears safe");
    }

    /**
     * 快速检查命令是否危险
     *
     * @param command 要检查的命令
     * @return 是否为危险命令
     */
    public static boolean isDangerous(String command) {
        return detect(command).isDangerous();
    }

    /**
     * 获取命令的警告信息
     *
     * @param command 要检查的命令
     * @return 警告信息，如果是安全命令则返回 null
     */
    public static String getWarning(String command) {
        DetectionResult result = detect(command);
        if (result.getLevel() == DangerLevel.SAFE) {
            return null;
        }
        return result.toString();
    }
}
