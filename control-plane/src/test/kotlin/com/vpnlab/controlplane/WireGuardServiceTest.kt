package com.vpnlab.controlplane

import com.vpnlab.controlplane.config.VpnControlProperties
import com.vpnlab.controlplane.service.CommandResult
import com.vpnlab.controlplane.service.CommandRunner
import com.vpnlab.controlplane.service.WireGuardService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis

/** 개선 2 검증 — listAll 이 5대를 병렬로 조회하는지 (타이밍 기반). */
class WireGuardServiceTest {

    private val props = VpnControlProperties(scriptsDir = "/tmp/x", clientCount = 5)

    @Test
    fun `listAll runs the 5 client queries concurrently`() = runBlocking {
        val runner = mockk<CommandRunner>()
        // 각 docker exec 이 100ms 걸린다고 가정
        coEvery { runner.run(any(), any()) } coAnswers {
            delay(100)
            CommandResult(ok = true, stdout = "key\t0\t0", stderr = "", code = 0)
        }
        val service = WireGuardService(runner, props)

        var result: List<com.vpnlab.controlplane.service.ClientSummary>
        val elapsed = measureTimeMillis {
            result = service.listAll()
        }

        assertThat(result).hasSize(5)
        assertThat(result.all { it.connected }).isTrue()
        // 순차라면 ~500ms. 병렬이면 ~100ms. 넉넉히 300ms 미만이면 병렬로 판단.
        assertThat(elapsed).isLessThan(300)
    }

    @Test
    fun `listAll marks disconnected clients when command fails`() = runBlocking {
        val runner = mockk<CommandRunner>()
        coEvery { runner.run(any(), any()) } returns
            CommandResult(ok = false, stdout = "", stderr = "no wg0", code = 1)
        val service = WireGuardService(runner, props)

        val result = service.listAll()

        assertThat(result).hasSize(5)
        assertThat(result.none { it.connected }).isTrue()
        assertThat(result.all { it.transfer.isEmpty() }).isTrue()
    }

    @Test
    fun `listAll preserves client ordering 1 to 5`() = runBlocking {
        val runner = mockk<CommandRunner>()
        coEvery { runner.run(any(), any()) } returns
            CommandResult(ok = true, stdout = "k\t1\t2", stderr = "", code = 0)
        val service = WireGuardService(runner, props)

        val result = service.listAll()

        assertThat(result.map { it.n }).containsExactly(1, 2, 3, 4, 5)
        assertThat(result.map { it.container })
            .containsExactly("client-1", "client-2", "client-3", "client-4", "client-5")
    }
}
