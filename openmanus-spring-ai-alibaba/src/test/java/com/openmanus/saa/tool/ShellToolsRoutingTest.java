package com.openmanus.saa.tool;

import com.openmanus.saa.config.OpenManusProperties;
import com.openmanus.saa.config.SandboxProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShellToolsRoutingTest {

    @Mock
    private OpenManusProperties openManusProperties;

    @Mock
    private SandboxProperties sandboxProperties;

    @Mock
    private SandboxTools sandboxTools;

    private ShellTools shellTools;

    @BeforeEach
    void setUp() {
        shellTools = new ShellTools(openManusProperties, sandboxProperties, sandboxTools);
    }

    @Test
    @DisplayName("当 sandbox 启用时，runPowerShell 应路由到 sandbox")
    void whenSandboxEnabled_routesToSandbox() throws Exception {
        when(sandboxProperties.isEnabled()).thenReturn(true);
        when(sandboxTools.runSandboxCommand(anyString()))
                .thenReturn("Sandbox output: success");

        String result = shellTools.runPowerShell("echo hello");

        assertThat(result).isEqualTo("Sandbox output: success");
        verify(sandboxTools).runSandboxCommand("echo hello");
        verifyNoInteractions(openManusProperties);
    }

    @Test
    @DisplayName("当 sandbox 禁用时，runPowerShell 应 fallback 到 host")
    void whenSandboxDisabled_fallbackToHost() throws Exception {
        when(sandboxProperties.isEnabled()).thenReturn(false);
        when(openManusProperties.isShellEnabled()).thenReturn(true);
        when(openManusProperties.getShellTimeoutSeconds()).thenReturn(30);
        when(openManusProperties.getWorkspace()).thenReturn("./workspace");

        // 这个测试需要实际的 PowerShell 环境，这里只验证路由逻辑
        // 实际 host 执行可能会失败（如果环境不支持）
        String result = shellTools.runPowerShell("echo hello");

        // 验证没有调用 sandbox
        verifyNoInteractions(sandboxTools);
        // 验证检查了 host shell 配置
        verify(openManusProperties).isShellEnabled();
    }

    @Test
    @DisplayName("当 sandbox 禁用且 shell 也禁用时，应返回禁用消息")
    void whenBothDisabled_returnsDisabledMessage() throws Exception {
        when(sandboxProperties.isEnabled()).thenReturn(false);
        when(openManusProperties.isShellEnabled()).thenReturn(false);

        String result = shellTools.runPowerShell("echo hello");

        assertThat(result).contains("Shell tool is disabled");
        verifyNoInteractions(sandboxTools);
    }
}