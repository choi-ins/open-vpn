package com.vpnlab.controlplane.api

import com.vpnlab.controlplane.service.ClientSummary
import com.vpnlab.controlplane.service.CommandResult
import com.vpnlab.controlplane.service.WireGuardService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/clients")
class ClientController(
    private val wireGuard: WireGuardService,
) {

    @GetMapping
    suspend fun list(): Map<String, List<ClientSummary>> =
        mapOf("clients" to wireGuard.listAll())

    @GetMapping("/{n}/status")
    suspend fun status(@PathVariable n: Int): ResponseEntity<Any> = validateThen(n) {
        ResponseEntity.ok(wireGuard.status(n))
    }

    @PostMapping("/{n}/connect")
    suspend fun connect(@PathVariable n: Int): ResponseEntity<Any> = validateThen(n) {
        ResponseEntity.ok(wireGuard.connect(n))
    }

    @PostMapping("/{n}/disconnect")
    suspend fun disconnect(@PathVariable n: Int): ResponseEntity<Any> = validateThen(n) {
        ResponseEntity.ok(wireGuard.disconnect(n))
    }

    @PostMapping("/{n}/killswitch")
    suspend fun killswitch(@PathVariable n: Int): ResponseEntity<Any> = validateThen(n) {
        ResponseEntity.ok(wireGuard.killswitchOn(n))
    }

    @PostMapping("/{n}/killswitch-off")
    suspend fun killswitchOff(@PathVariable n: Int): ResponseEntity<Any> = validateThen(n) {
        ResponseEntity.ok(wireGuard.killswitchOff(n))
    }

    private suspend fun validateThen(n: Int, block: suspend () -> ResponseEntity<Any>): ResponseEntity<Any> {
        if (!wireGuard.isValidClientId(n)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                mapOf("error" to "n must be 1..=5", "got" to n)
            )
        }
        return block()
    }
}
