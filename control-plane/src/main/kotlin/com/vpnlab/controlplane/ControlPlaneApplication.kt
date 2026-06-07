package com.vpnlab.controlplane

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class ControlPlaneApplication

fun main(args: Array<String>) {
    runApplication<ControlPlaneApplication>(*args)
}
