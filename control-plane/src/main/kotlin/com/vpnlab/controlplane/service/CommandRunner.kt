package com.vpnlab.controlplane.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * 결과 객체 — Rust 버전의 ScriptResult와 1:1 매핑.
 */
data class CommandResult(
    val ok: Boolean,
    val stdout: String,
    val stderr: String,
    val code: Int?,
)

/**
 * ProcessBuilder 래퍼. 셸 스크립트 또는 docker exec 호출에 사용.
 * Phase 1: 옵션 A (셸 스크립트 호출) 유지. Phase 2 이후 fabric8 docker-client 또는 bollard-jvm으로 대체 검토.
 */
@Component
class CommandRunner {

    private val log = LoggerFactory.getLogger(CommandRunner::class.java)

    suspend fun run(
        command: List<String>,
        timeoutSeconds: Long = 30,
    ): CommandResult = withContext(Dispatchers.IO) {
        log.debug("exec: {}", command.joinToString(" "))
        val pb = ProcessBuilder(command).redirectErrorStream(false)
        val process = try {
            pb.start()
        } catch (e: Exception) {
            return@withContext CommandResult(
                ok = false,
                stdout = "",
                stderr = "spawn error: ${e.message}",
                code = null,
            )
        }

        val stdout = StringBuilder()
        val stderr = StringBuilder()
        // 큰 출력 대비 별도 스레드로 흡수 (PoC 단계는 sequential read; 작은 출력만 처리되므로 OK)
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return@withContext CommandResult(
                ok = false,
                stdout = "",
                stderr = "command timed out after ${timeoutSeconds}s",
                code = null,
            )
        }

        BufferedReader(InputStreamReader(process.inputStream)).use { stdout.append(it.readText()) }
        BufferedReader(InputStreamReader(process.errorStream)).use { stderr.append(it.readText()) }

        CommandResult(
            ok = process.exitValue() == 0,
            stdout = stdout.toString(),
            stderr = stderr.toString(),
            code = process.exitValue(),
        )
    }
}
