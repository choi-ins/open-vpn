package com.vpnlab.controlplane.diskcontrol

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/disk-control")
class DiskControlController(
    private val service: DiskControlService,
) {

    /** GET /status → {"running": bool, "pid": Int?} */
    @GetMapping("/status")
    fun status(): StatusResponse = service.status()

    /** GET /policy → policy.json 본문 또는 404 / 500 */
    @GetMapping("/policy")
    fun getPolicy(): ResponseEntity<Any> = when (val r = service.readPolicy()) {
        is PolicyReadResult.Ok -> ResponseEntity.ok<Any>(r.policy)
        PolicyReadResult.NotFound ->
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body<Any>(mapOf("error" to "Policy file not found"))
        PolicyReadResult.ParseError ->
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body<Any>(mapOf("error" to "Failed to parse policy file"))
    }

    /**
     * PUT /policy {"mode":"log-only"|"block"|"readonly"}
     *  - 200 {"ok":true,"mode":"..."}
     *  - 400 invalid mode
     *  - 404 file not found
     *  - 500 parse/write error
     */
    @PutMapping("/policy")
    fun updatePolicy(@RequestBody req: PolicyUpdate): ResponseEntity<Map<String, Any>> =
        when (val r = service.updatePolicy(req)) {
            is PolicyUpdateResult.Ok ->
                ResponseEntity.ok(mapOf("ok" to true, "mode" to r.mode))
            is PolicyUpdateResult.InvalidMode ->
                ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(mapOf("error" to r.message))
            PolicyUpdateResult.NotFound ->
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(mapOf("error" to "Policy file not found"))
            PolicyUpdateResult.ParseError ->
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(mapOf("error" to "Failed to parse policy file"))
            PolicyUpdateResult.WriteError ->
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(mapOf("error" to "Failed to write policy file"))
        }

    /** GET /events?limit=N → {"events":[...], "count":N, "total":T} */
    @GetMapping("/events")
    fun events(@RequestParam(defaultValue = "10") limit: Int): EventsResponse =
        service.events(limit)
}
