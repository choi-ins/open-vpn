package com.vpnlab.controlplane.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "vpn-control")
data class VpnControlProperties(
    val scriptsDir: String,
    val clientCount: Int = 5,
    val blocklist: BlocklistProperties = BlocklistProperties(),
    val diskControl: DiskControlProperties = DiskControlProperties(),
    // 개선 2: /clients, /status 캐시 TTL (ms). docker exec 폭주를 흡수하기 위한 짧은 캐시.
    val cacheTtlMs: Long = 1500,
)

data class BlocklistProperties(
    val hostsPath: String = "/Users/insuchoe/Desktop/vpn-lab/server-data/blocklist/hosts",
    val corefilePath: String = "/Users/insuchoe/Desktop/vpn-lab/server-data/coredns/Corefile",
    // 와일드카드 변경 시 CoreDNS 자동 재시작 (원본은 수동 docker restart 우회였음 — 자동화)
    val autoRestartCoredns: Boolean = true,
    val corednsContainer: String = "vpn-server",
    // 재시작 후 클라이언트 격리 iptables 규칙 자동 재적용 (개선 1)
    // vpn-server 재시작 시 FORWARD wg0→wg0 DROP 규칙이 소실되므로 재삽입
    val reapplyIsolation: Boolean = true,
)

data class DiskControlProperties(
    val policyPath: String = "/Users/insuchoe/Desktop/vpn-lab/disk-control/config/policy.json",
    val logPath: String = "/Users/insuchoe/Desktop/vpn-lab/disk-control/logs/disk-events.jsonl",
    val processPattern: String = "disk-control-poc",
)
