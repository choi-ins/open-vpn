package com.vpnlab.controlplane.service

import com.vpnlab.controlplane.config.VpnControlProperties
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Service
import java.time.Duration

data class ClientSummary(
    val n: Int,
    val container: String,
    val connected: Boolean,
    val transfer: String,
)

/**
 * VPN 클라이언트 제어 비즈니스 로직.
 * Phase 1: connect/disconnect/killswitch는 셸 스크립트 위임. status/list는 docker exec wg show 직접 호출.
 *
 * 개선 2: listAll/status는 짧은 TTL 캐시(SuspendCache)로 감싼다.
 * 100 컨테이너 규모 실측에서 docker exec 기반 조회가 병목으로 확인됐고
 * (Docker 데몬 자체가 동시 exec를 사실상 직렬화), 캐시로 동시 요청 폭주를
 * 실제 조회 1회로 흡수해 데몬 부하를 줄인다. connect/disconnect/killswitch
 * 처럼 상태를 바꾸는 작업 직후에는 해당 캐시를 무효화해 신선도를 보장한다.
 */
@Service
class WireGuardService(
    private val runner: CommandRunner,
    private val props: VpnControlProperties,
) {
    companion object {
        // 배포된 셸 스크립트(connect.sh 등)가 지원하는 최대 클라이언트 번호.
        // clientCount 설정이 이 값을 초과해도 스크립트는 동작하지 않으므로
        // 이 범위를 벗어난 n은 400으로 조기 거부한다.
        const val SCRIPT_MAX_CLIENT = 5
    }

    private val listCache = SuspendCache<String, List<ClientSummary>>(
        Duration.ofMillis(props.cacheTtlMs)
    )
    private val statusCache = SuspendCache<Int, CommandResult>(
        Duration.ofMillis(props.cacheTtlMs)
    )

    fun isValidClientId(n: Int): Boolean = n in 1..minOf(props.clientCount, SCRIPT_MAX_CLIENT)

    suspend fun connect(n: Int): CommandResult =
        runner.run(listOf("$scriptsDir/connect.sh", n.toString())).also { invalidateCaches(n) }

    suspend fun disconnect(n: Int): CommandResult =
        runner.run(listOf("$scriptsDir/disconnect.sh", n.toString())).also { invalidateCaches(n) }

    suspend fun killswitchOn(n: Int): CommandResult =
        runner.run(listOf("$scriptsDir/killswitch-on.sh", n.toString())).also { invalidateCaches(n) }

    suspend fun killswitchOff(n: Int): CommandResult =
        runner.run(listOf("$scriptsDir/killswitch-off.sh", n.toString())).also { invalidateCaches(n) }

    suspend fun status(n: Int): CommandResult = statusCache.get(n) {
        runner.run(listOf("docker", "exec", "client-$n", "wg", "show"))
    }

    /**
     * 전 클라이언트 상태를 병렬 조회 + TTL 캐시.
     * async로 동시 실행 후(개선 2-a), listCache가 캐시 유효 구간 내 중복 조회를 흡수(개선 2-b).
     */
    suspend fun listAll(): List<ClientSummary> = listCache.get("all") {
        coroutineScope {
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
    }

    private fun invalidateCaches(n: Int) {
        statusCache.invalidate(n)
        listCache.invalidate("all")
    }

    private val scriptsDir: String get() = props.scriptsDir
}
