package com.vpnlab.controlplane.diskcontrol

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.vpnlab.controlplane.config.VpnControlProperties
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

data class StatusResponse(val running: Boolean, val pid: Int?)
data class PolicyUpdate(val mode: String)

sealed class PolicyReadResult {
    data class Ok(val policy: JsonNode) : PolicyReadResult()
    object NotFound : PolicyReadResult()
    object ParseError : PolicyReadResult()
}

sealed class PolicyUpdateResult {
    data class Ok(val mode: String) : PolicyUpdateResult()
    data class InvalidMode(val message: String) : PolicyUpdateResult()
    object NotFound : PolicyUpdateResult()
    object ParseError : PolicyUpdateResult()
    object WriteError : PolicyUpdateResult()
}

data class EventsResponse(
    val events: List<Map<String, Any?>>,
    val count: Int,
    val total: Int,
)

@Service
class DiskControlService(
    private val props: VpnControlProperties,
    private val om: ObjectMapper,
) {
    companion object {
        val VALID_MODES = setOf("log-only", "block", "readonly")
    }

    /** GET /status — pgrep -f <process-pattern> 으로 실행 여부 + PID 확인 (원본 동일) */
    fun status(): StatusResponse {
        val pb = ProcessBuilder("pgrep", "-f", props.diskControl.processPattern).redirectErrorStream(false)
        return try {
            val process = pb.start()
            process.waitFor()
            if (process.exitValue() == 0) {
                val out = process.inputStream.bufferedReader().readText().trim()
                // 여러 PID 라인일 수 있음. 첫 줄(원본 Rust는 trim+parse first ok)
                val firstLine = out.lineSequence().firstOrNull()?.trim().orEmpty()
                val pid = firstLine.toIntOrNull()
                StatusResponse(running = true, pid = pid)
            } else {
                StatusResponse(running = false, pid = null)
            }
        } catch (e: Exception) {
            StatusResponse(running = false, pid = null)
        }
    }

    /** GET /policy — policy.json 읽어 그대로 반환 */
    fun readPolicy(): PolicyReadResult {
        val path = Path.of(props.diskControl.policyPath)
        return try {
            val content = Files.readString(path)
            val node = om.readTree(content)
            PolicyReadResult.Ok(node)
        } catch (_: NoSuchFileException) {
            PolicyReadResult.NotFound
        } catch (_: java.io.FileNotFoundException) {
            PolicyReadResult.NotFound
        } catch (_: Exception) {
            // 파일은 있는데 JSON 파싱 실패
            if (!Files.exists(path)) PolicyReadResult.NotFound else PolicyReadResult.ParseError
        }
    }

    /** PUT /policy — mode 만 갱신 (원본 동일) */
    fun updatePolicy(req: PolicyUpdate): PolicyUpdateResult {
        if (req.mode !in VALID_MODES) {
            return PolicyUpdateResult.InvalidMode(
                "Invalid mode. Must be 'log-only', 'block', or 'readonly'"
            )
        }

        val path = Path.of(props.diskControl.policyPath)
        if (!Files.exists(path)) return PolicyUpdateResult.NotFound

        val content = try {
            Files.readString(path)
        } catch (_: Exception) {
            return PolicyUpdateResult.NotFound
        }

        val node: JsonNode = try {
            om.readTree(content)
        } catch (_: Exception) {
            return PolicyUpdateResult.ParseError
        }

        if (node !is ObjectNode) return PolicyUpdateResult.ParseError
        node.put("mode", req.mode)

        return try {
            Files.writeString(path, om.writerWithDefaultPrettyPrinter().writeValueAsString(node))
            PolicyUpdateResult.Ok(req.mode)
        } catch (_: Exception) {
            PolicyUpdateResult.WriteError
        }
    }

    /** GET /events?limit=N — 최신 N개 (역순), 없으면 빈 리스트 */
    fun events(limit: Int): EventsResponse {
        val path = Path.of(props.diskControl.logPath)
        if (!Files.exists(path)) return EventsResponse(events = emptyList(), count = 0, total = 0)

        val lines = try {
            Files.readAllLines(path)
        } catch (_: Exception) {
            return EventsResponse(events = emptyList(), count = 0, total = 0)
        }
        val total = lines.size
        val tail = lines.asReversed().asSequence()
            .take(limit)
            .mapNotNull { ln ->
                try {
                    @Suppress("UNCHECKED_CAST")
                    om.readValue(ln, Map::class.java) as Map<String, Any?>
                } catch (_: Exception) {
                    null
                }
            }
            .toList()
        return EventsResponse(events = tail, count = tail.size, total = total)
    }
}
