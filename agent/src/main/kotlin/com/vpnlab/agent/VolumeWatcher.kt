package com.vpnlab.agent

import org.slf4j.LoggerFactory
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.time.Instant

/**
 * 원본 watcher.rs 와 동일한 이벤트 파이프라인.
 *
 * notify(FSEvents) 대신 java.nio WatchService 사용:
 * - watch_paths(/Volumes) 등록 → 볼륨 마운트/언마운트 = create/delete
 * - 마운트된 각 볼륨 루트도 등록 → 볼륨 내 파일 create/write/delete 감지
 *   (원본 RecursiveMode::Recursive의 PoC 검증 범위 — 볼륨 루트 파일 — 를 커버)
 */
class VolumeWatcher(private val policy: Policy) {

    private val log = LoggerFactory.getLogger(VolumeWatcher::class.java)
    private val watchService = FileSystems.getDefault().newWatchService()
    private val keyToDir = mutableMapOf<WatchKey, Path>()

    /** kind → EventType 매핑 (원본: Create→create, Modify→write, Remove→delete, 그 외 무시) */
    internal fun mapEventType(kind: WatchEvent.Kind<*>): EventType? = when (kind) {
        ENTRY_CREATE -> EventType.CREATE
        ENTRY_MODIFY -> EventType.WRITE
        ENTRY_DELETE -> EventType.DELETE
        else -> null
    }

    fun run() {
        for (watchPath in policy.rules.usbWriteBlock.watchPaths) {
            val p = Path.of(watchPath)
            if (Files.exists(p)) {
                register(p)
                log.info("Watching: {}", watchPath)
                // 이미 마운트돼 있는 볼륨 루트도 등록
                Files.list(p).use { stream ->
                    stream.filter { Files.isDirectory(it) }.forEach { registerQuietly(it) }
                }
            } else {
                log.warn("Path does not exist, skipping: {}", watchPath)
            }
        }
        log.info("USB watcher started. Mode: {}", policy.mode.label)

        while (true) {
            val key = watchService.take()
            val dir = keyToDir[key] ?: continue
            for (raw in key.pollEvents()) {
                val eventType = mapEventType(raw.kind()) ?: continue
                val rel = raw.context() as? Path ?: continue
                val full = dir.resolve(rel)
                handlePath(eventType, full)
                // /Volumes 바로 아래 새 디렉터리(=새 볼륨) 생기면 감시 대상에 추가
                if (eventType == EventType.CREATE && Files.isDirectory(full)) {
                    registerQuietly(full)
                }
            }
            if (!key.reset()) keyToDir.remove(key)
        }
    }

    private fun register(p: Path) {
        val key = p.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)
        keyToDir[key] = p
    }

    private fun registerQuietly(p: Path) {
        try {
            register(p)
            log.debug("Registered volume dir: {}", p)
        } catch (e: Exception) {
            log.warn("Cannot register {}: {}", p, e.message)
        }
    }

    internal fun handlePath(eventType: EventType, path: Path) {
        val event = buildEvent(policy, eventType, path.toString()) ?: return
        if (policy.mode == Mode.READONLY &&
            (eventType == EventType.WRITE || eventType == EventType.CREATE)
        ) {
            ReadonlyGuard.applyReadonly("/Volumes/${event.volume}", GuardMode.DRY_RUN)
                .onSuccess { log.warn("ReadOnly 모드 - {}", it) }
                .onFailure { log.error("ReadOnly 적용 실패: {}", it.message) }
        }
        log.info("Event: {}", event)
        EventLogger.logEvent(policy.logging.logPath, event)
    }

    companion object {
        /**
         * 경로 → DiskEvent 변환 (원본 handle_event 의 순수 로직).
         * ignored volume 이면 null.
         */
        fun buildEvent(policy: Policy, eventType: EventType, pathStr: String): DiskEvent? {
            val volume = pathStr
                .removePrefix("/Volumes/")
                .takeIf { it != pathStr }          // prefix 없으면 unknown
                ?.substringBefore('/')
                ?: "unknown"

            if (policy.isIgnored(volume)) return null

            val action = when (policy.mode) {
                Mode.LOG_ONLY -> Action.LOGGED
                Mode.BLOCK -> Action.BLOCKED     // Mock: 실제 차단 안 함 (원본 동일)
                Mode.READONLY -> Action.BLOCKED  // ReadOnly는 쓰기 차단 의도
            }

            return DiskEvent(
                ts = Instant.now(),
                event = eventType,
                volume = volume,
                path = pathStr,
                action = action,
                mode = policy.mode.label,
            )
        }
    }
}
