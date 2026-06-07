package com.vpnlab.controlplane.service

import com.vpnlab.controlplane.config.VpnControlProperties
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

    suspend fun listAll(): List<ClientSummary> {
        val list = mutableListOf<ClientSummary>()
        for (n in 1..props.clientCount) {
            val container = "client-$n"
            val res = runner.run(listOf("docker", "exec", container, "wg", "show", "wg0", "transfer"))
            list += ClientSummary(
                n = n,
                container = container,
                connected = res.ok,
                transfer = if (res.ok) res.stdout.trim() else "",
            )
        }
        return list
    }

    private val scriptsDir: String get() = props.scriptsDir
}
