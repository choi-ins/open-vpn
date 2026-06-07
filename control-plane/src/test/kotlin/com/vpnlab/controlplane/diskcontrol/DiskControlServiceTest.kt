package com.vpnlab.controlplane.diskcontrol

import com.fasterxml.jackson.databind.ObjectMapper
import com.vpnlab.controlplane.config.DiskControlProperties
import com.vpnlab.controlplane.config.VpnControlProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DiskControlServiceTest {

    private fun svc(@TempDir tmp: Path, processPattern: String = "definitely-not-running-xyz-123"): DiskControlService {
        val policy = tmp.resolve("policy.json")
        val log = tmp.resolve("disk-events.jsonl")
        val props = VpnControlProperties(
            scriptsDir = "/tmp/unused",
            clientCount = 5,
            diskControl = DiskControlProperties(
                policyPath = policy.toString(),
                logPath = log.toString(),
                processPattern = processPattern,
            ),
        )
        return DiskControlService(props, ObjectMapper())
    }

    @Test
    fun `status returns running=false when process not found`(@TempDir tmp: Path) {
        val s = svc(tmp)
        val r = s.status()
        assertThat(r.running).isFalse()
        assertThat(r.pid).isNull()
    }

    @Test
    fun `status returns running=true with PID when process exists`(@TempDir tmp: Path) {
        // 현재 JVM 프로세스 자신을 매칭. 정규식 안 쓰고 단순 -f 매칭.
        // sleep 같은 일반 단어로는 다른 프로세스 끼어들 수 있음 → 고유한 UUID 사용
        val unique = "vpncontrol-test-marker-${java.util.UUID.randomUUID()}"
        val sleeper = ProcessBuilder("sh", "-c", "exec -a $unique sleep 30").start()
        try {
            Thread.sleep(300)
            val s = svc(tmp, processPattern = unique)
            val r = s.status()
            assertThat(r.running).isTrue()
            assertThat(r.pid).isNotNull()
            assertThat(r.pid).isGreaterThan(0)
        } finally {
            sleeper.destroyForcibly()
            sleeper.waitFor()
        }
    }

    @Test
    fun `readPolicy returns NotFound when file missing`(@TempDir tmp: Path) {
        val s = svc(tmp)
        assertThat(s.readPolicy()).isEqualTo(PolicyReadResult.NotFound)
    }

    @Test
    fun `readPolicy returns Ok with json content`(@TempDir tmp: Path) {
        val policy = tmp.resolve("policy.json")
        Files.writeString(policy, """{"mode":"log-only","rules":{"x":1},"logging":{"y":2}}""")
        val s = svc(tmp)
        val r = s.readPolicy()
        assertThat(r).isInstanceOf(PolicyReadResult.Ok::class.java)
        val node = (r as PolicyReadResult.Ok).policy
        assertThat(node.get("mode").asText()).isEqualTo("log-only")
        assertThat(node.get("rules").get("x").asInt()).isEqualTo(1)
    }

    @Test
    fun `readPolicy returns ParseError for invalid json`(@TempDir tmp: Path) {
        val policy = tmp.resolve("policy.json")
        Files.writeString(policy, "{invalid json")
        val s = svc(tmp)
        assertThat(s.readPolicy()).isEqualTo(PolicyReadResult.ParseError)
    }

    @Test
    fun `updatePolicy rejects invalid mode`(@TempDir tmp: Path) {
        val policy = tmp.resolve("policy.json")
        Files.writeString(policy, """{"mode":"log-only","rules":{},"logging":{}}""")
        val s = svc(tmp)
        val r = s.updatePolicy(PolicyUpdate("nonsense"))
        assertThat(r).isInstanceOf(PolicyUpdateResult.InvalidMode::class.java)
        assertThat((r as PolicyUpdateResult.InvalidMode).message)
            .isEqualTo("Invalid mode. Must be 'log-only', 'block', or 'readonly'")
    }

    @Test
    fun `updatePolicy succeeds for block mode`(@TempDir tmp: Path) {
        val policy = tmp.resolve("policy.json")
        Files.writeString(policy, """{"mode":"log-only","rules":{},"logging":{}}""")
        val s = svc(tmp)
        val r = s.updatePolicy(PolicyUpdate("block"))
        assertThat(r).isEqualTo(PolicyUpdateResult.Ok("block"))
        val updated = Files.readString(policy)
        assertThat(updated).contains("\"mode\" : \"block\"")
    }

    @Test
    fun `updatePolicy accepts all three valid modes`(@TempDir tmp: Path) {
        val policy = tmp.resolve("policy.json")
        Files.writeString(policy, """{"mode":"log-only","rules":{},"logging":{}}""")
        val s = svc(tmp)
        listOf("log-only", "block", "readonly").forEach {
            assertThat(s.updatePolicy(PolicyUpdate(it))).isEqualTo(PolicyUpdateResult.Ok(it))
        }
    }

    @Test
    fun `updatePolicy returns NotFound when file missing`(@TempDir tmp: Path) {
        val s = svc(tmp)
        assertThat(s.updatePolicy(PolicyUpdate("block"))).isEqualTo(PolicyUpdateResult.NotFound)
    }

    @Test
    fun `events returns empty when log file missing`(@TempDir tmp: Path) {
        val s = svc(tmp)
        val r = s.events(10)
        assertThat(r.events).isEmpty()
        assertThat(r.count).isEqualTo(0)
        assertThat(r.total).isEqualTo(0)
    }

    @Test
    fun `events returns last N lines in reverse order`(@TempDir tmp: Path) {
        val log = tmp.resolve("disk-events.jsonl")
        val lines = (1..5).map { """{"ts":"2026-01-0${it}T00:00:00Z","event":"create","seq":$it}""" }
        Files.write(log, lines)
        val s = svc(tmp)
        val r = s.events(3)
        assertThat(r.total).isEqualTo(5)
        assertThat(r.count).isEqualTo(3)
        // 최신(seq=5)이 first, seq=3 이 last
        assertThat(r.events[0]["seq"]).isEqualTo(5)
        assertThat(r.events[1]["seq"]).isEqualTo(4)
        assertThat(r.events[2]["seq"]).isEqualTo(3)
    }

    @Test
    fun `events skips malformed lines`(@TempDir tmp: Path) {
        val log = tmp.resolve("disk-events.jsonl")
        Files.write(log, listOf(
            """{"ts":"2026-01-01T00:00:00Z","event":"good","seq":1}""",
            "{broken json",
            """{"ts":"2026-01-02T00:00:00Z","event":"good","seq":2}""",
        ))
        val s = svc(tmp)
        val r = s.events(10)
        assertThat(r.total).isEqualTo(3)        // 라인 수는 모두 카운트
        assertThat(r.count).isEqualTo(2)        // 파싱된 것만
        assertThat(r.events[0]["seq"]).isEqualTo(2)
        assertThat(r.events[1]["seq"]).isEqualTo(1)
    }
}
