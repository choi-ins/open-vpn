package com.vpnlab.controlplane.blocklist

import com.vpnlab.controlplane.config.BlocklistProperties
import com.vpnlab.controlplane.config.VpnControlProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis

/** 개선 1 — 격리 규칙 재적용 가드 검증 (docker 미의존 부분만). */
class CorednsRestarterTest {

    private fun restarter(reapply: Boolean, autoRestart: Boolean = true): CorednsRestarter {
        val props = VpnControlProperties(
            scriptsDir = "/tmp/x",
            clientCount = 5,
            blocklist = BlocklistProperties(
                autoRestartCoredns = autoRestart,
                reapplyIsolation = reapply,
            ),
        )
        return CorednsRestarter(props)
    }

    @Test
    fun `reapply disabled returns immediately without docker calls`() {
        val r = restarter(reapply = false)
        // 비활성이면 2초 sleep(활성 경로)에 진입하지 않고 즉시 반환
        val elapsed = measureTimeMillis {
            r.reapplyIsolationIfEnabled("nonexistent-container")
        }
        assertThat(elapsed).isLessThan(500)
    }

    @Test
    fun `restartAsync disabled when autoRestart false is a no-op`() {
        val r = restarter(reapply = true, autoRestart = false)
        // autoRestartCoredns=false 면 즉시 반환 (스레드 생성 안 함)
        val elapsed = measureTimeMillis { r.restartAsync() }
        assertThat(elapsed).isLessThan(200)
    }
}
