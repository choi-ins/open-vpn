package com.vpnlab.agent

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/** 원본 logger.rs — JSONL append 전용 */
object EventLogger {
    fun logEvent(logPath: String, event: DiskEvent) {
        val path = Path.of(logPath)
        path.parent?.let { Files.createDirectories(it) }
        val line = Policy.mapper.writeValueAsString(event) + "\n"
        Files.writeString(
            path, line,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND,
        )
    }
}
