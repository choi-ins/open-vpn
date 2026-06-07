package com.vpnlab.controlplane.blocklist

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class AddDomainRequest(val domain: String)

@RestController
@RequestMapping("/api/v1/blocklist")
class BlocklistController(
    private val service: BlocklistService,
) {

    /** GET /api/v1/blocklist/domains → {"domains": [...]} */
    @GetMapping("/domains")
    fun list(): Map<String, List<String>> =
        mapOf("domains" to service.listDomains())

    /**
     * POST /api/v1/blocklist/domains
     * - 200 {"ok": true, "domain": "..."}
     * - 400 {"error": "...", "domain": "..."} (검증 실패)
     * - 409 {"error": "Domain already exists", "domain": "..."}
     */
    @PostMapping("/domains")
    fun add(@RequestBody req: AddDomainRequest): ResponseEntity<Map<String, Any>> {
        return when (val r = service.addDomain(req.domain)) {
            is AddResult.Inserted ->
                ResponseEntity.ok(mapOf("ok" to true, "domain" to req.domain))
            is AddResult.Conflict ->
                ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(mapOf("error" to "Domain already exists", "domain" to req.domain))
            is AddResult.Invalid ->
                ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(mapOf("error" to r.reason, "domain" to req.domain))
        }
    }

    /**
     * DELETE /api/v1/blocklist/domains/{domain}
     * - 200 {"ok": true, "domain": "..."}
     * - 404 {"error": "Domain not found", "domain": "..."}
     */
    @DeleteMapping("/domains/{domain}")
    fun delete(@PathVariable domain: String): ResponseEntity<Map<String, Any>> {
        return when (service.removeDomain(domain)) {
            is DeleteResult.Removed ->
                ResponseEntity.ok(mapOf("ok" to true, "domain" to domain))
            is DeleteResult.NotFound ->
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(mapOf("error" to "Domain not found", "domain" to domain))
        }
    }

    /**
     * POST /api/v1/blocklist/reload
     * CoreDNS는 `reload 5s` directive로 자동 reload되므로 응답만 안내.
     */
    @PostMapping("/reload")
    fun reload(): Map<String, Any> =
        mapOf("ok" to true, "message" to "CoreDNS will reload within 5 seconds")
}
