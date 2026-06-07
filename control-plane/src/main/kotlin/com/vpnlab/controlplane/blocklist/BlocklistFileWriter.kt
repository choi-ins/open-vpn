package com.vpnlab.controlplane.blocklist

import com.vpnlab.controlplane.config.VpnControlProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * MongoDB의 블록리스트 상태를 CoreDNS가 읽는 파일로 동기화.
 * 원본 Rust의 generate_hosts(), generate_corefile() 과 동일 포맷.
 */
@Component
class BlocklistFileWriter(
    private val props: VpnControlProperties,
) {
    private val log = LoggerFactory.getLogger(BlocklistFileWriter::class.java)

    /**
     * hosts 파일 생성.
     * 원본 포맷:
     *   # VPN Lab Blocklist - Auto-generated
     *   # Format: 0.0.0.0 domain.com
     *
     *   0.0.0.0 example.com
     *   ::      example.com
     */
    fun writeHosts(regularDomains: List<String>) {
        val path = Paths.get(props.blocklist.hostsPath)
        ensureParent(path)
        val sb = StringBuilder()
        sb.appendLine("# VPN Lab Blocklist - Auto-generated")
        sb.appendLine("# Format: 0.0.0.0 domain.com")
        sb.appendLine()
        for (d in regularDomains.sorted()) {
            sb.appendLine("0.0.0.0 $d")
            sb.appendLine("::      $d")  // 원본 Rust 와 동일: 콜론2 + 공백6 + 도메인
        }
        Files.writeString(path, sb.toString())
        log.debug("hosts file written: {} ({} domains)", path, regularDomains.size)
    }

    /**
     * Corefile 생성.
     * 원본 구조:
     *   . {
     *       hosts /etc/coredns/blocklist/hosts {
     *           fallthrough
     *           reload 5s
     *       }
     *       <template blocks per wildcard>
     *       loop
     *       errors
     *       health
     *       forward . /etc/resolv.conf
     *       reload 10s
     *   }
     *
     * 와일드카드 → 정규식: "*.ads.com" → "(^|[.])ads[.]com[.]$"
     */
    fun writeCorefile(wildcards: List<String>) {
        val path = Paths.get(props.blocklist.corefilePath)
        ensureParent(path)
        val sb = StringBuilder()
        sb.appendLine(". {")
        sb.appendLine("    hosts /etc/coredns/blocklist/hosts {")
        sb.appendLine("        fallthrough")
        sb.appendLine("        reload 5s")
        sb.appendLine("    }")
        for (wc in wildcards.sorted()) {
            val regex = wildcardToRegex(wc)
            sb.appendLine("    template IN A {")
            sb.appendLine("        match \"$regex\"")
            sb.appendLine("        answer \"{{ .Name }} 0 IN A 0.0.0.0\"")
            sb.appendLine("        fallthrough")
            sb.appendLine("    }")
            sb.appendLine("    template IN AAAA {")
            sb.appendLine("        match \"$regex\"")
            sb.appendLine("        answer \"{{ .Name }} 0 IN AAAA ::\"")
            sb.appendLine("        fallthrough")
            sb.appendLine("    }")
        }
        sb.appendLine("    loop")
        sb.appendLine("    errors")
        sb.appendLine("    health")
        sb.appendLine("    forward . /etc/resolv.conf")
        sb.appendLine("    reload 10s")
        sb.appendLine("}")
        Files.writeString(path, sb.toString())
        log.debug("Corefile written: {} ({} wildcards)", path, wildcards.size)
    }

    private fun ensureParent(path: Path) {
        path.parent?.let { Files.createDirectories(it) }
    }

    companion object {
        /** *.ads.com → (^|[.])ads[.]com[.]$  (원본 Rust 알고리즘 그대로) */
        fun wildcardToRegex(pattern: String): String {
            val domain = if (pattern.startsWith("*.")) pattern.substring(2) else pattern
            val escaped = domain.replace(".", "[.]")
            return "(^|[.])${escaped}[.]\$"
        }
    }
}
