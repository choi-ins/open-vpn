package com.vpnlab.controlplane

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class HealthIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `healthz returns ok status`() {
        mockMvc.perform(get("/healthz"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ok"))
    }

    @Test
    fun `actuator health endpoint is exposed`() {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
    }
}
