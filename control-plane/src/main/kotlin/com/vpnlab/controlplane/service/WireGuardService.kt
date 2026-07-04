package com.vpnlab.controlplane.service

import com.vpnlab.controlplane.config.VpnControlProperties
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Service

data class ClientSummary(
    val n: Int,
    val container: String,
    val connected: Boolean,
    val transfer: String,
)

/**
 * VPN 클라이언트 제어 비즈니스 로직.
 * Phase 1: connect/disconnect/killswitch는 셸 스크립트 위임. status/list는 docker exec wg show 직접 호출.
 */
@Service
class WireGuardService(
    private val runner: CommandRunner,
    private val props: VpnControlProperties,
) {

    fun isValidClientId(n: Int): Boolean = n in 1..props.clientCount

    suspend fun connect(n: Int): CommandResult =
        runner.run(listOf("$scriptsDir/connect.sh", n.toString()))

    suspend fun disconnect(n: Int): CommandResult =
        runner.run(listOf("$scriptsDir/disconnect.sh", n.toString()))

    suspend fun killswitchOn(n: Int): CommandResult =
        runner.run(listOf("$scriptsDir/killswitch-on.sh", n.toString()))

    suspend fun killswitchOff(n: Int): CommandResult =
        runner.run(listOf("$scriptsDir/killswitch-off.sh", n.toString()))

    suspend fun status(n: Int): CommandResult =
        runner.run(listOf("docker", "exec", "client-$n", "wg", "show"))

    /**
     * 5대 클라이언트 상태를 병렬 조회 (개선 2).
     * 기존 순차 실행은 docker exec ×5 왕복이 누적돼 ~300ms.
     * async로 동시에 실행하면 가장 느린 1건 시간(~60ms)으로 수렴한다.
     * CommandRunner가 Dispatchers.IO에서 실행하므로 스레드 풀이 병렬성을 보장.
     */
    suspend fun listAll(): List<ClientSummary> = coroutineScope {
        (1..props.clientCount).map { n ->
            async {
                val container = "client-$n"
                val res = runner.run(listOf("docker", "exec", container, "wg", "show", "wg0", "transfer"))
                ClientSummary(
                    n = n,
                    container = container,
                    connected = res.ok,
                    transfer = if (res.ok) res.stdout.trim() else "",
                )
            }
        }.awaitAll()
    }

    private val scriptsDir: String get() = props.scriptsDir
}
