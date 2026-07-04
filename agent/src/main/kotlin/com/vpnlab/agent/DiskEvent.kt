package com.vpnlab.agent

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * 원본 Rust event.rs 와 1:1. serde(rename_all = "lowercase") 대응.
 * JSONL 스키마: {"ts":"...Z","event":"write","volume":"...","path":"...","action":"logged","mode":"log-only"}
 */
enum class EventType {
    @JsonProperty("mount") MOUNT,
    @JsonProperty("unmount") UNMOUNT,
    @JsonProperty("write") WRITE,
    @JsonProperty("read") READ,
    @JsonProperty("create") CREATE,
    @JsonProperty("delete") DELETE,
}

enum class Action {
    @JsonProperty("logged") LOGGED,
    @JsonProperty("blocked") BLOCKED,
    @JsonProperty("allowed") ALLOWED,
}

data class DiskEvent(
    val ts: Instant,
    val event: EventType,
    val volume: String,
    val path: String,
    val action: Action,
    val mode: String,
)
