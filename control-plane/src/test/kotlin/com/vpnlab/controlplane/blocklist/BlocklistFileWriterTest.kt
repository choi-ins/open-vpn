package com.vpnlab.controlplane.blocklist

import com.vpnlab.controlplane.config.BlocklistProperties
import com.vpnlab.controlplane.config.VpnControlProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class BlocklistFileWriterTest {

    private fun writerForTmp(@TempDir tmp: Path): Pair<BlocklistFileWriter, BlocklistProperties> {
        val hostsPath = tmp.resolve("hosts")
        val corefilePath = tmp.resolve("Corefile")
        val blocklist = BlocklistProperties(
            hostsPath = hostsPath.toString(),
            corefilePath = corefilePath.toString(),
        )
        val props = VpnControlProperties(
            scriptsDir = "/tmp/unused",
            clientCount = 5,
            blocklist = blocklist,
        )
        return BlocklistFileWriter(props) to blocklist
    }

    @Test
    fun `wildcardToRegex matches Rust algorithm`() {
        assertThat(BlocklistFileWriter.wildcardToRegex("*.ads.com"))
            .isEqualTo("(^|[.])ads[.]com[.]\$")
        assertThat(BlocklistFileWriter.wildcardToRegex("*.tracker.net"))
            .isEqualTo("(^|[.])tracker[.]net[.]\$")
    }

    @Test
    fun `hosts file contains expected lines`(@TempDir tmp: Path) {
        val (writer, blocklist) = writerForTmp(tmp)
        writer.writeHosts(listOf("example.com", "ads.com"))
        val content = Files.readString(Path.of(blocklist.hostsPath))
        assertThat(content).contains("# VPN Lab Blocklist - Auto-generated")
        assertThat(content).contains("0.0.0.0 ads.com")
        assertThat(content).contains("::      ads.com")
        assertThat(content).contains("0.0.0.0 example.com")
        assertThat(content).contains("::      example.com")
        // 정렬 확인 — ads가 example보다 먼저
        val idxAds = content.indexOf("0.0.0.0 ads.com")
        val idxExample = content.indexOf("0.0.0.0 example.com")
        assertThat(idxAds).isLessThan(idxExample)
    }

    @Test
    fun `corefile contains hosts plugin and wildcard templates`(@TempDir tmp: Path) {
        val (writer, blocklist) = writerForTmp(tmp)
        writer.writeCorefile(listOf("*.ads.com", "*.tracker.net"))
        val content = Files.readString(Path.of(blocklist.corefilePath))

        // 핵심 블록 확인
        assertThat(content).contains("hosts /etc/coredns/blocklist/hosts {")
        assertThat(content).contains("        fallthrough")
        assertThat(content).contains("        reload 5s")
        assertThat(content).contains("forward . /etc/resolv.conf")
        assertThat(content).contains("    reload 10s")

        // 와일드카드 템플릿
        assertThat(content).contains("match \"(^|[.])ads[.]com[.]\$\"")
        assertThat(content).contains("answer \"{{ .Name }} 0 IN A 0.0.0.0\"")
        assertThat(content).contains("match \"(^|[.])tracker[.]net[.]\$\"")
        assertThat(content).contains("answer \"{{ .Name }} 0 IN AAAA ::\"")
    }
}
