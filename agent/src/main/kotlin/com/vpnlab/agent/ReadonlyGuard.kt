package com.vpnlab.agent

/**
 * 원본 readonly_guard.rs 와 1:1.
 *
 * Safety: Enforce 모드는 sudo 권한이 필요하며 이 에이전트는 절대 실행하지 않는다.
 * DryRun은 제안 명령 문자열만 만들어 로그로 남긴다.
 */
enum class GuardMode { DRY_RUN, ENFORCE }

object ReadonlyGuard {

    /** /Volumes/USB → /dev/disk4s1 (diskutil info 의 "Device Node:" 파싱) */
    fun getDeviceForVolume(volumePath: String): String? {
        return try {
            val process = ProcessBuilder("diskutil", "info", volumePath)
                .redirectErrorStream(false)
                .start()
            process.waitFor()
            process.inputStream.bufferedReader().readText()
                .lineSequence()
                .map { it.trimStart() }
                .firstOrNull { it.startsWith("Device Node:") }
                ?.substringAfter(':')
                ?.trim()
        } catch (e: Exception) {
            null
        }
    }

    fun buildReadonlyCommand(device: String): String = "sudo mount -u -o rdonly $device"

    fun buildReadwriteCommand(device: String): String = "sudo mount -u -o rw $device"

    /**
     * @return 성공: dry-run 제안 메시지 / 실패: 에러 메시지 (원본 Result<String,String> 대응)
     */
    fun applyReadonly(volumePath: String, mode: GuardMode = GuardMode.DRY_RUN): Result<String> {
        val device = getDeviceForVolume(volumePath)
            ?: return Result.failure(IllegalStateException("device 조회 실패: $volumePath"))

        val cmd = buildReadonlyCommand(device)
        return when (mode) {
            GuardMode.DRY_RUN ->
                Result.success("[DRY-RUN] 실행하지 않음. 제안 명령: $cmd")
            GuardMode.ENFORCE ->
                // 절대 실행하지 않음 — 원본과 동일하게 항상 에러 반환
                Result.failure(IllegalStateException("Enforce 모드는 sudo 권한 필요. 사용자가 직접 실행: $cmd"))
        }
    }

    /** mount 출력에서 해당 볼륨이 read-only 인지 확인 */
    fun isReadonly(volumePath: String): Boolean? {
        return try {
            val process = ProcessBuilder("mount").start()
            process.waitFor()
            process.inputStream.bufferedReader().readText()
                .lineSequence()
                .firstOrNull { it.contains(volumePath) }
                ?.let { it.contains("read-only") || it.contains("rdonly") }
        } catch (e: Exception) {
            null
        }
    }
}
