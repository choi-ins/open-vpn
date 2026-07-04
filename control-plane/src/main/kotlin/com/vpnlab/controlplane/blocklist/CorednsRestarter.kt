package com.vpnlab.controlplane.blocklist

import com.vpnlab.controlplane.config.VpnControlProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 와일드카드 변경 시 CoreDNS 컨테이너 재시작.
 *
 * 배경: CoreDNS reload plugin이 template block이 든 Corefile 변경을
 * "Corefile changed but reload failed"로 거부 → docker restart로만 반영 가능.
 * (원본에서는 수동 우회였고, 이 마이그레이션에서 자동화 — CLAUDE.md 재현 대상 6번)
 *
 * 재시작은 ~5-10초 걸리므로 API 응답을 막지 않도록 백그라운드 스레드에서 수행.
 * 연속 와일드카드 변경 시 재시작 중복을 피하기 위해 in-flight 가드를 둔다.
 */
@Component
class CorednsRestarter(
    private val props: VpnControlProperties,
) {
    private val log = LoggerFactory.getLogger(CorednsRestarter::class.java)
    private val inFlight = AtomicBoolean(false)

    /** 백그라운드로 docker restart 트리거. 이미 재시작 중이면 스킵(신규 Corefile은 어차피 반영됨). */
    fun restartAsync() {
        if (!props.blocklist.autoRestartCoredns) {
            log.debug("CoreDNS auto-restart disabled — skipping")
            return
        }
        if (!inFlight.compareAndSet(false, true)) {
            log.info("CoreDNS restart already in flight — skipping duplicate")
            return
        }
        val container = props.blocklist.corednsContainer
        Thread({
            try {
                log.info("Restarting CoreDNS container '{}' to apply wildcard Corefile...", container)
                val process = ProcessBuilder("docker", "restart", container)
                    .redirectErrorStream(true)
                    .start()
                val finished = process.waitFor(60, TimeUnit.SECONDS)
                val output = process.inputStream.bufferedReader().readText().trim()
                if (finished && process.exitValue() == 0) {
                    log.info("CoreDNS container '{}' restarted: {}", container, output)
                    reapplyIsolationIfEnabled(container)
                } else {
                    log.error("CoreDNS restart failed (exit={}): {}", if (finished) process.exitValue() else "timeout", output)
                }
            } catch (e: Exception) {
                log.error("CoreDNS restart error: {}", e.message)
            } finally {
                inFlight.set(false)
            }
        }, "coredns-restart").apply { isDaemon = true }.start()
    }

    /**
     * vpn-server 재시작 시 클라이언트 간 격리 iptables 규칙(FORWARD wg0→wg0 DROP)이
     * 소실되므로 멱등하게 재적용한다. (개선 1 — 핸드오프 문서 5절이 지적한 규칙 소실 문제)
     *
     * iptables -C 로 이미 존재하는지 먼저 확인 → 없을 때만 -I 로 삽입 (중복 방지).
     */
    internal fun reapplyIsolationIfEnabled(container: String) {
        if (!props.blocklist.reapplyIsolation) {
            log.debug("Isolation reapply disabled — skipping")
            return
        }
        // 컨테이너 iptables 준비까지 잠깐 대기 (재시작 직후엔 wg0/체인이 아직 없을 수 있음)
        Thread.sleep(2000)
        val rule = listOf("iptables", "-i", "wg0", "-o", "wg0", "-j", "DROP")
        try {
            // -C FORWARD ... : 규칙 존재 확인 (exit 0 = 있음)
            val checkExit = dockerExec(container, listOf("iptables", "-C", "FORWARD") + rule.drop(1))
            if (checkExit == 0) {
                log.info("Client isolation rule already present in '{}' — no action", container)
                return
            }
            // -I FORWARD ... : 규칙 삽입
            val insertExit = dockerExec(container, listOf("iptables", "-I", "FORWARD") + rule.drop(1))
            if (insertExit == 0) {
                log.info("Client isolation rule re-applied to '{}' (FORWARD wg0->wg0 DROP)", container)
            } else {
                log.error("Failed to re-apply isolation rule to '{}' (exit={})", container, insertExit)
            }
        } catch (e: Exception) {
            log.error("Isolation reapply error on '{}': {}", container, e.message)
        }
    }

    private fun dockerExec(container: String, cmd: List<String>): Int {
        val process = ProcessBuilder(listOf("docker", "exec", container) + cmd)
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(30, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return -1
        }
        return process.exitValue()
    }
}
