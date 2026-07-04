package com.vpnlab.agent

import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * 원본 main.rs CLI 와 동일:
 *   disk-control-poc start  [--config config/policy.json]
 *   disk-control-poc status [--config config/policy.json]
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        usage()
        exitProcess(1)
    }

    val command = args[0]
    val config = parseConfig(args.drop(1)) ?: Path.of("config/policy.json")

    when (command) {
        "start" -> {
            val policy = Policy.load(config)
            VolumeWatcher(policy).run()
        }
        "status" -> {
            val policy = Policy.load(config)
            println("Mode: ${policy.mode.label}")
            println("Watching: ${policy.rules.usbWriteBlock.watchPaths}")
            println("Log path: ${policy.logging.logPath}")
        }
        else -> {
            usage()
            exitProcess(1)
        }
    }
}

private fun parseConfig(rest: List<String>): Path? {
    val idx = rest.indexOfFirst { it == "--config" || it == "-c" }
    if (idx >= 0 && idx + 1 < rest.size) return Path.of(rest[idx + 1])
    return null
}

private fun usage() {
    System.err.println(
        """
        USB write blocking PoC (Kotlin migration)
        Usage: disk-control-poc <start|status> [--config <path>]
        """.trimIndent()
    )
}
