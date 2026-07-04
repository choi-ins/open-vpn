package com.vpnlab.controlplane.service

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import java.time.Duration

/**
 * 짧은 TTL 캐시 + single-flight (개선 2).
 *
 * 배경: docker exec 기반 조회(/clients, /status)는 컨테이너 수가 늘수록 느려지고
 * (100개 규모에서 실측 ~4초), 이 구간에 동시 요청이 몰리면 요청마다 매번
 * docker exec ×N을 새로 실행해 Docker 데몬 부하가 배가된다.
 *
 * VPN 연결 상태는 초당 여러 번 바뀌지 않으므로 짧은 TTL(1~2초)로 캐싱하면
 * 캐시 유효 구간 내 폭주하는 동시 요청을 실제 조회 1회로 흡수할 수 있다.
 *
 * Caffeine AsyncCache.get(key, mappingFunction)은 동일 key로 캐시 미스가
 * 동시에 여러 건 발생해도 실제 계산은 1회만 수행하고 나머지는 그 결과를 공유한다
 * (single-flight / dogpile 방지) — 별도 락 없이 이 특성을 그대로 활용한다.
 */
class SuspendCache<K : Any, V>(
    ttl: Duration,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {
    private val delegate: AsyncCache<K, V> = Caffeine.newBuilder()
        .expireAfterWrite(ttl)
        .buildAsync()

    suspend fun get(key: K, compute: suspend () -> V): V =
        delegate.get(key) { _, _ -> scope.future { compute() } }.await()

    /** 쓰기 이후 즉시 최신 상태를 보고 싶을 때 특정 키를 무효화 (예: connect/disconnect 직후). */
    fun invalidate(key: K) = delegate.synchronous().invalidate(key)

    fun invalidateAll() = delegate.synchronous().invalidateAll()
}
