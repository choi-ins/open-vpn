package com.vpnlab.controlplane

import com.vpnlab.controlplane.service.CommandRunner
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CommandRunnerTest {

    private val runner = CommandRunner()

    @Test
    fun `echo command succeeds with stdout`() = runTest {
        val result = runner.run(listOf("echo", "hello"))
        assertThat(result.ok).isTrue()
        assertThat(result.stdout.trim()).isEqualTo("hello")
        assertThat(result.code).isEqualTo(0)
    }

    @Test
    fun `nonexistent command returns spawn error`() = runTest {
        val result = runner.run(listOf("/nonexistent/binary-xyz-123"))
        assertThat(result.ok).isFalse()
        assertThat(result.stderr).contains("spawn error")
    }

    @Test
    fun `failing command returns non-zero exit code`() = runTest {
        val result = runner.run(listOf("sh", "-c", "exit 42"))
        assertThat(result.ok).isFalse()
        assertThat(result.code).isEqualTo(42)
    }

    @Test
    fun `command writes to stderr`() = runTest {
        val result = runner.run(listOf("sh", "-c", "echo oops >&2; exit 1"))
        assertThat(result.ok).isFalse()
        assertThat(result.stderr.trim()).isEqualTo("oops")
    }

    @Test
    fun `command timeout is enforced`() = runTest {
        val result = runner.run(listOf("sleep", "5"), timeoutSeconds = 1)
        assertThat(result.ok).isFalse()
        assertThat(result.stderr).contains("timed out")
    }
}
