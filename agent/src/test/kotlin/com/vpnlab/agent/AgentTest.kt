package com.vpnlab.agent

import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/** 원본 Rust 테스트 7개 1:1 이식 + Kotlin 추가 검증 */
class AgentTest {

    private val validPolicyJson = """
        {
          "mode": "log-only",
          "rules": {
            "usb_write_block": {
              "enabled": true,
              "watch_paths": ["/Volumes"],
              "ignored_volumes": ["Macintosh HD", "Data"]
            }
          },
          "logging": { "log_path": "./logs/disk-events.jsonl", "log_level": "info" }
        }
    """.trimIndent()

    @Nested
    inner class EventSerde {

        @Test
        fun `event serialization matches original lowercase schema`() {
            val event = DiskEvent(
                ts = Instant.now(),
                event = EventType.WRITE,
                volume = "USB_TEST",
                path = "/Volumes/USB_TEST/file.pdf",
                action = Action.LOGGED,
                mode = "log-only",
            )
            val json = Policy.mapper.writeValueAsString(event)
            assertThat(json).contains("\"event\":\"write\"")
            assertThat(json).contains("\"action\":\"logged\"")
            assertThat(json).contains("USB_TEST")
        }

        @Test
        fun `event deserialization from original JSON`() {
            val json = """
                {"ts":"2026-05-18T12:00:00Z","event":"mount","volume":"USB_TEST",
                 "path":"/Volumes/USB_TEST","action":"blocked","mode":"block"}
            """.trimIndent()
            val event: DiskEvent = Policy.mapper.readValue(json)
            assertThat(event.event).isEqualTo(EventType.MOUNT)
            assertThat(event.action).isEqualTo(Action.BLOCKED)
        }

        @Test
        fun `ts serializes as ISO-8601 UTC string`() {
            val event = DiskEvent(
                Instant.parse("2026-06-09T10:00:00Z"),
                EventType.CREATE, "V", "/Volumes/V", Action.LOGGED, "log-only",
            )
            val json = Policy.mapper.writeValueAsString(event)
            assertThat(json).contains("\"ts\":\"2026-06-09T10:00:00Z\"")
        }
    }

    @Nested
    inner class LoggerTest {

        @Test
        fun `log_event writes jsonl`(@TempDir tmp: Path) {
            val logPath = tmp.resolve("events.jsonl").toString()
            val event = DiskEvent(Instant.now(), EventType.CREATE, "USB", "/Volumes/USB", Action.LOGGED, "log-only")
            EventLogger.logEvent(logPath, event)

            val lines = Files.readAllLines(Path.of(logPath))
            assertThat(lines).hasSize(1)
            assertThat(lines[0]).contains("\"event\":\"create\"")
        }

        @Test
        fun `log multiple events append`(@TempDir tmp: Path) {
            val logPath = tmp.resolve("events.jsonl").toString()
            repeat(3) { i ->
                EventLogger.logEvent(
                    logPath,
                    DiskEvent(Instant.now(), EventType.WRITE, "USB$i", "/Volumes/USB$i/f", Action.LOGGED, "log-only"),
                )
            }
            val lines = Files.readAllLines(Path.of(logPath))
            assertThat(lines).hasSize(3)
            assertThat(lines[2]).contains("USB2")
        }
    }

    @Nested
    inner class PolicyTest {

        @Test
        fun `policy load valid`(@TempDir tmp: Path) {
            val p = tmp.resolve("policy.json")
            Files.writeString(p, validPolicyJson)
            val policy = Policy.load(p)
            assertThat(policy.mode).isEqualTo(Mode.LOG_ONLY)
            assertThat(policy.rules.usbWriteBlock.watchPaths).containsExactly("/Volumes")
            assertThat(policy.isIgnored("Macintosh HD")).isTrue()
            assertThat(policy.isIgnored("MyUSB")).isFalse()
        }

        @Test
        fun `policy block mode`(@TempDir tmp: Path) {
            val p = tmp.resolve("policy.json")
            Files.writeString(p, validPolicyJson.replace("log-only", "block"))
            assertThat(Policy.load(p).mode).isEqualTo(Mode.BLOCK)
        }

        @Test
        fun `policy readonly mode`(@TempDir tmp: Path) {
            val p = tmp.resolve("policy.json")
            Files.writeString(p, validPolicyJson.replace("\"mode\": \"log-only\"", "\"mode\": \"readonly\""))
            assertThat(Policy.load(p).mode).isEqualTo(Mode.READONLY)
        }

        @Test
        fun `policy invalid json throws`(@TempDir tmp: Path) {
            val p = tmp.resolve("policy.json")
            Files.writeString(p, "{not valid json")
            assertThatThrownBy { Policy.load(p) }.isInstanceOf(Exception::class.java)
        }
    }

    @Nested
    inner class ReadonlyGuardTest {

        @Test
        fun `build readonly command`() {
            val cmd = ReadonlyGuard.buildReadonlyCommand("/dev/disk4s1")
            assertThat(cmd).contains("rdonly").contains("/dev/disk4s1").startsWith("sudo")
        }

        @Test
        fun `build readwrite command`() {
            val cmd = ReadonlyGuard.buildReadwriteCommand("/dev/disk4s1")
            assertThat(cmd).contains("rw").contains("/dev/disk4s1").startsWith("sudo")
        }

        @Test
        fun `dry run does not execute — nonexistent volume fails device lookup`() {
            val result = ReadonlyGuard.applyReadonly("/Volumes/NONEXISTENT_TEST_VOLUME", GuardMode.DRY_RUN)
            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()?.message).contains("device 조회 실패")
        }

        @Test
        fun `enforce mode always returns error`() {
            val result = ReadonlyGuard.applyReadonly("/Volumes/ANY", GuardMode.ENFORCE)
            assertThat(result.isFailure).isTrue()
        }
    }

    @Nested
    inner class BuildEventTest {

        private fun policy(mode: String) =
            Policy.mapper.readValue<Policy>(validPolicyJson.replace("\"mode\": \"log-only\"", "\"mode\": \"$mode\""))

        @Test
        fun `volume parsed from Volumes path`() {
            val e = VolumeWatcher.buildEvent(policy("log-only"), EventType.WRITE, "/Volumes/MyUSB/docs/f.txt")
            assertThat(e).isNotNull
            assertThat(e!!.volume).isEqualTo("MyUSB")
            assertThat(e.action).isEqualTo(Action.LOGGED)
            assertThat(e.mode).isEqualTo("log-only")
        }

        @Test
        fun `ignored volume returns null`() {
            val e = VolumeWatcher.buildEvent(policy("log-only"), EventType.WRITE, "/Volumes/Macintosh HD/x")
            assertThat(e).isNull()
        }

        @Test
        fun `block mode maps to blocked action`() {
            val e = VolumeWatcher.buildEvent(policy("block"), EventType.CREATE, "/Volumes/USB/f")
            assertThat(e!!.action).isEqualTo(Action.BLOCKED)
            assertThat(e.mode).isEqualTo("block")
        }

        @Test
        fun `readonly mode maps to blocked action`() {
            val e = VolumeWatcher.buildEvent(policy("readonly"), EventType.WRITE, "/Volumes/USB/f")
            assertThat(e!!.action).isEqualTo(Action.BLOCKED)
            assertThat(e.mode).isEqualTo("readonly")
        }

        @Test
        fun `non-Volumes path becomes unknown volume`() {
            val e = VolumeWatcher.buildEvent(policy("log-only"), EventType.DELETE, "/tmp/other")
            assertThat(e!!.volume).isEqualTo("unknown")
        }
    }
}
