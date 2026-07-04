package com.vpnlab.controlplane.blocklist

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional

/**
 * 와일드카드/일반 도메인 변경 시 파일 재생성 + CoreDNS 재시작 분기 검증.
 * - 와일드카드 추가/삭제 → Corefile 재생성 + restartAsync() 호출
 * - 일반 도메인 추가/삭제 → hosts 재생성만, 재시작 없음 (reload 5s가 픽업)
 */
class BlocklistServiceUnitTest {

    private lateinit var repo: BlocklistRepository
    private lateinit var fileWriter: BlocklistFileWriter
    private lateinit var restarter: CorednsRestarter
    private lateinit var service: BlocklistService

    @BeforeEach
    fun setup() {
        repo = mockk(relaxed = true)
        // relaxed mock은 제네릭 반환형(save)에 Object를 돌려줘 ClassCastException 발생 → 명시 stub
        every { repo.save(any<BlockedDomain>()) } answers { firstArg() }
        fileWriter = mockk(relaxed = true)
        restarter = mockk(relaxed = true)
        service = BlocklistService(repo, fileWriter, restarter)
    }

    @Test
    fun `adding wildcard regenerates Corefile and restarts CoreDNS`() {
        every { repo.existsById("*.ads.com") } returns false
        every { repo.findAllByIsWildcard(true) } returns listOf(
            BlockedDomain("*.ads.com", isWildcard = true)
        )

        val result = service.addDomain("*.ads.com")

        assertThat(result).isEqualTo(AddResult.Inserted)
        verify(exactly = 1) { fileWriter.writeCorefile(listOf("*.ads.com")) }
        verify(exactly = 1) { restarter.restartAsync() }
        verify(exactly = 0) { fileWriter.writeHosts(any()) }
    }

    @Test
    fun `adding regular domain regenerates hosts without restart`() {
        every { repo.existsById("example.com") } returns false
        every { repo.findAllByIsWildcard(false) } returns listOf(
            BlockedDomain("example.com", isWildcard = false)
        )

        val result = service.addDomain("example.com")

        assertThat(result).isEqualTo(AddResult.Inserted)
        verify(exactly = 1) { fileWriter.writeHosts(listOf("example.com")) }
        verify(exactly = 0) { restarter.restartAsync() }
        verify(exactly = 0) { fileWriter.writeCorefile(any()) }
    }

    @Test
    fun `removing wildcard regenerates Corefile and restarts CoreDNS`() {
        every { repo.existsById("*.ads.com") } returns true
        every { repo.findAllByIsWildcard(true) } returns emptyList()

        val result = service.removeDomain("*.ads.com")

        assertThat(result).isEqualTo(DeleteResult.Removed)
        verify(exactly = 1) { fileWriter.writeCorefile(emptyList()) }
        verify(exactly = 1) { restarter.restartAsync() }
    }

    @Test
    fun `removing regular domain does not restart CoreDNS`() {
        every { repo.existsById("example.com") } returns true
        every { repo.findAllByIsWildcard(false) } returns emptyList()

        val result = service.removeDomain("example.com")

        assertThat(result).isEqualTo(DeleteResult.Removed)
        verify(exactly = 1) { fileWriter.writeHosts(emptyList()) }
        verify(exactly = 0) { restarter.restartAsync() }
    }

    @Test
    fun `conflict on add does not touch files or restart`() {
        every { repo.existsById("dup.com") } returns true

        val result = service.addDomain("dup.com")

        assertThat(result).isEqualTo(AddResult.Conflict)
        verify(exactly = 0) { fileWriter.writeHosts(any()) }
        verify(exactly = 0) { fileWriter.writeCorefile(any()) }
        verify(exactly = 0) { restarter.restartAsync() }
    }
}
