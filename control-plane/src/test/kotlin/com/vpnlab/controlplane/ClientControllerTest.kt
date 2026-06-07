package com.vpnlab.controlplane

import com.ninjasquad.springmockk.MockkBean
import com.vpnlab.controlplane.service.ClientSummary
import com.vpnlab.controlplane.service.CommandResult
import com.vpnlab.controlplane.service.WireGuardService
import io.mockk.coEvery
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.RequestBuilder
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class ClientControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    lateinit var wireGuard: WireGuardService

    /**
     * suspend 컨트롤러는 첫 응답으로 async DeferredResult를 반환하므로
     * 실제 응답을 받으려면 asyncDispatch가 필요하다.
     */
    private fun performAsync(builder: RequestBuilder): ResultActions {
        val mvcResult = mockMvc.perform(builder)
            .andExpect(request().asyncStarted())
            .andReturn()
        return mockMvc.perform(asyncDispatch(mvcResult))
    }

    @Test
    fun `GET clients returns list of summaries`() {
        every { wireGuard.isValidClientId(any()) } returns true
        coEvery { wireGuard.listAll() } returns listOf(
            ClientSummary(1, "client-1", true, "abc 100 200"),
            ClientSummary(2, "client-2", false, ""),
        )

        performAsync(get("/api/v1/clients"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.clients[0].n").value(1))
            .andExpect(jsonPath("$.clients[0].connected").value(true))
            .andExpect(jsonPath("$.clients[1].connected").value(false))
    }

    @Test
    fun `GET status with invalid n returns 400`() {
        every { wireGuard.isValidClientId(9) } returns false

        performAsync(get("/api/v1/clients/9/status"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("n must be 1..=5"))
            .andExpect(jsonPath("$.got").value(9))
    }

    @Test
    fun `POST connect with valid n returns script result`() {
        every { wireGuard.isValidClientId(1) } returns true
        coEvery { wireGuard.connect(1) } returns CommandResult(
            ok = true,
            stdout = "✓ client-1 연결됨\n",
            stderr = "",
            code = 0,
        )

        performAsync(post("/api/v1/clients/1/connect"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ok").value(true))
            .andExpect(jsonPath("$.code").value(0))
    }

    @Test
    fun `POST disconnect with invalid n returns 400`() {
        every { wireGuard.isValidClientId(0) } returns false

        performAsync(post("/api/v1/clients/0/disconnect"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `POST killswitch returns ok`() {
        every { wireGuard.isValidClientId(3) } returns true
        coEvery { wireGuard.killswitchOn(3) } returns CommandResult(true, "applied", "", 0)

        performAsync(post("/api/v1/clients/3/killswitch"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ok").value(true))
    }
}
