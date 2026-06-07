package com.vpnlab.controlplane.blocklist

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.vpnlab.controlplane.service.WireGuardService
import io.mockk.every
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.file.Files
import java.nio.file.Path

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BlocklistIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val mongo: MongoDBContainer = MongoDBContainer("mongo:8.0")

        @TempDir
        @JvmStatic
        lateinit var sharedTmp: Path

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.mongodb.uri") { mongo.replicaSetUrl }
            registry.add("vpn-control.blocklist.hosts-path") { sharedTmp.resolve("hosts").toString() }
            registry.add("vpn-control.blocklist.corefile-path") { sharedTmp.resolve("Corefile").toString() }
        }
    }

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var om: ObjectMapper
    @Autowired lateinit var repo: BlocklistRepository

    // ClientController가 WireGuardService를 의존하지만 이 테스트엔 필요 없음 → MockkBean으로 우회
    @MockkBean(relaxed = true) lateinit var wireGuard: WireGuardService

    @BeforeEach
    fun clean() {
        repo.deleteAll()
        every { wireGuard.isValidClientId(any()) } returns true
    }

    @Test
    fun `GET domains returns empty initially`() {
        mockMvc.perform(get("/api/v1/blocklist/domains"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.domains").isArray)
            .andExpect(jsonPath("$.domains.length()").value(0))
    }

    @Test
    fun `POST add regular domain creates hosts entry`() {
        mockMvc.perform(post("/api/v1/blocklist/domains")
            .contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(mapOf("domain" to "example.com"))))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ok").value(true))
            .andExpect(jsonPath("$.domain").value("example.com"))

        val hosts = Files.readString(sharedTmp.resolve("hosts"))
        assert(hosts.contains("0.0.0.0 example.com")) { "hosts should contain example.com:\n$hosts" }
        assert(hosts.contains("::      example.com")) { "hosts should contain IPv6 line:\n$hosts" }
    }

    @Test
    fun `POST add wildcard domain creates Corefile template block`() {
        mockMvc.perform(post("/api/v1/blocklist/domains")
            .contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(mapOf("domain" to "*.ads.com"))))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ok").value(true))
            .andExpect(jsonPath("$.domain").value("*.ads.com"))

        val corefile = Files.readString(sharedTmp.resolve("Corefile"))
        assert(corefile.contains("(^|[.])ads[.]com[.]\$")) { "Corefile should contain regex:\n$corefile" }
        assert(corefile.contains("answer \"{{ .Name }} 0 IN A 0.0.0.0\"")) { "Corefile missing A answer:\n$corefile" }
    }

    @Test
    fun `POST duplicate returns 409 conflict`() {
        // 첫번째
        mockMvc.perform(post("/api/v1/blocklist/domains")
            .contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(mapOf("domain" to "dup.com"))))
            .andExpect(status().isOk)

        // 두번째 (중복)
        mockMvc.perform(post("/api/v1/blocklist/domains")
            .contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(mapOf("domain" to "dup.com"))))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("Domain already exists"))
            .andExpect(jsonPath("$.domain").value("dup.com"))
    }

    @Test
    fun `POST invalid domain returns 400 with reason`() {
        mockMvc.perform(post("/api/v1/blocklist/domains")
            .contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(mapOf("domain" to "no-dot-here"))))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Domain must contain at least one dot"))
            .andExpect(jsonPath("$.domain").value("no-dot-here"))
    }

    @Test
    fun `DELETE existing domain removes it`() {
        // setup
        mockMvc.perform(post("/api/v1/blocklist/domains")
            .contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(mapOf("domain" to "remove-me.com"))))
            .andExpect(status().isOk)

        mockMvc.perform(delete("/api/v1/blocklist/domains/{d}", "remove-me.com"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ok").value(true))
            .andExpect(jsonPath("$.domain").value("remove-me.com"))

        // 다시 list 하면 빠져 있음
        mockMvc.perform(get("/api/v1/blocklist/domains"))
            .andExpect(jsonPath("$.domains.length()").value(0))
    }

    @Test
    fun `DELETE nonexistent returns 404`() {
        mockMvc.perform(delete("/api/v1/blocklist/domains/{d}", "ghost.com"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").value("Domain not found"))
            .andExpect(jsonPath("$.domain").value("ghost.com"))
    }

    @Test
    fun `POST reload returns standard message`() {
        mockMvc.perform(post("/api/v1/blocklist/reload"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ok").value(true))
            .andExpect(jsonPath("$.message").value("CoreDNS will reload within 5 seconds"))
    }

    @Test
    fun `GET domains returns sorted combined list of regular and wildcards`() {
        // wildcards가 일반보다 알파벳상 빠를 수도 있음 (* < a). 그대로 정렬됨.
        listOf("z-last.com", "*.b-wild.com", "a-first.com").forEach { d ->
            mockMvc.perform(post("/api/v1/blocklist/domains")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(mapOf("domain" to d))))
                .andExpect(status().isOk)
        }
        mockMvc.perform(get("/api/v1/blocklist/domains"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.domains.length()").value(3))
            // 정렬 순서: *.b-wild.com < a-first.com < z-last.com (ASCII)
            .andExpect(jsonPath("$.domains[0]").value("*.b-wild.com"))
            .andExpect(jsonPath("$.domains[1]").value("a-first.com"))
            .andExpect(jsonPath("$.domains[2]").value("z-last.com"))
    }
}
