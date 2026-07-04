package com.vpnlab.agent

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.file.Files
import java.nio.file.Path

/** 원본 Rust policy.rs 와 1:1 — config/policy.json 스키마 */
enum class Mode(val label: String) {
    @JsonProperty("log-only") LOG_ONLY("log-only"),
    @JsonProperty("block") BLOCK("block"),
    @JsonProperty("readonly") READONLY("readonly"),
}

data class UsbRule(
    val enabled: Boolean,
    @JsonProperty("watch_paths") val watchPaths: List<String>,
    @JsonProperty("ignored_volumes") val ignoredVolumes: List<String>,
)

data class LoggingConfig(
    @JsonProperty("log_path") val logPath: String,
    @JsonProperty("log_level") val logLevel: String,
)

data class Rules(
    @JsonProperty("usb_write_block") val usbWriteBlock: UsbRule,
)

data class Policy(
    val mode: Mode,
    val rules: Rules,
    val logging: LoggingConfig,
) {
    fun isIgnored(volume: String): Boolean =
        rules.usbWriteBlock.ignoredVolumes.any { it == volume }

    companion object {
        val mapper: ObjectMapper = ObjectMapper()
            .registerModule(kotlinModule())
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

        fun load(path: Path): Policy =
            mapper.readValue(Files.readString(path))
    }
}
