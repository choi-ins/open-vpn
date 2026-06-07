package com.vpnlab.controlplane.diskcontrol

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.vpnlab.controlplane.service.WireGuardService
import io.mockk.every
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
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
class DiskControlControllerTest {

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
            // policy 파일 미리 준비
            val policy = sharedTmp.resolve("policy.json")
            Files.writeString(policy, """{"mode":"log-only","rules":{"a":1},"logging":{"b":2}}""")
            registry.add("vpn-control.disk-control.policy-path") { policy.toString() }
            // log 파일 (events 테스트용)
            val log = sharedTmp.resolve("disk-events.jsonl")
            Files.write(log, listOf(
                """{"ts":"2026-01-01T00:00:00Z","event":"create","volume":"USB-A","path":"/Volumes/USB-A","action":"logged","mode":"log-only"}""",
                """{"ts":"2026-01-02T00:00:00Z","event":"write","volume":"USB-B","path":"/Volumes/USB-B","action":"logged","mode":"log-only"}""",
            ))
            registry.add("vpn-control.disk-control.log-path") { log.toString() }
            // 실행 안 하는 패턴 (status=false 예상)
            registry.add("vpn-control.disk-control.process-pattern") { "definitely-not-running-test-marker-xyz" }
        }
    }

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var om: ObjectMapper

    // 외부 의존성 우회
    @MockkBean(relaxed = true) lateinit var wireGuard: WireGuardService

    @Test
    fun `GET status returns running=false when process absent`() {
        every { wireGuard.isValidClientId(any()) } returns true
        mockMvc.perform(get("/api/v1/disk-control/status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.running").value(false))
    }

    @Test
    fun `GET policy returns json content`() {
        mockMvc.perform(get("/api/v1/disk-control/policy"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mode").exists())
            .andExpect(jsonPath("$.rules.a").value(1))
            .andExpect(jsonPath("$.logging.b").value(2))
    }

    @Test
    fun `PUT policy invalid mode returns 400`() {
        mockMvc.perform(put("/api/v1/disk-control/policy")
            .contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(mapOf("mode" to "nonsense"))))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error")
                .value("Invalid mode. Must be 'log-only', 'block', or 'readonly'"))
    }

    @Test
    fun `PUT policy block mode returns ok`() {
        mockMvc.perform(put("/api/v1/disk-control/policy")
            .contentType(MediaType.APPLICATION_JSON)
            .content(om.writeValueAsString(mapOf("mode" to "block"))))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ok").value(true))
            .andExpect(jsonPath("$.mode").value("block"))
    }

    @Test
    fun `GET events returns last N in reverse`() {
        mockMvc.perform(get("/api/v1/disk-control/events").param("limit", "10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.count").value(2))
            // 역순: USB-B (2026-01-02) 가 first
            .andExpect(jsonPath("$.events[0].volume").value("USB-B"))
            .andExpect(jsonPath("$.events[1].volume").value("USB-A"))
    }

    @Test
    fun `GET events default limit 10 works`() {
        mockMvc.perform(get("/api/v1/disk-control/events"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.count").value(2))
    }
}
