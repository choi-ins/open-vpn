package com.vpnlab.controlplane.api

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController {

    @GetMapping("/healthz")
    fun healthz(): Map<String, String> = mapOf("status" to "ok")
}
