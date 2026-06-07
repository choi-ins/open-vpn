package com.vpnlab.controlplane.blocklist

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * MongoDB 도큐먼트 — blocklist에 등록된 도메인 1건.
 * 일반 도메인과 와일드카드 모두 한 컬렉션에 저장하고 isWildcard 플래그로 구분.
 *
 * 원본 Rust 자료구조 매핑:
 * - BlocklistData { domains: HashSet, wildcards: HashSet } → 하나의 컬렉션 + isWildcard flag
 * - 식별자: domain 문자열 자체 (유니크)
 */
@Document(collection = "blocklist_domains")
data class BlockedDomain(
    @Id
    val domain: String,
    @Indexed
    val isWildcard: Boolean = false,
    val createdAt: Instant = Instant.now(),
)

/**
 * 도메인 형식 검증. 원본 Rust validate_domain() 과 동일한 규칙.
 *
 * 규칙:
 * - 비어 있으면 안 됨, 최대 255자
 * - 일반: 영숫자/하이픈/언더스코어/점, 시작·끝은 영숫자, 점 최소 1개
 * - 와일드카드 *.X: X가 일반 도메인 규칙 + 점 최소 1개 (예: *.com 거부, *.ads.com 허용)
 */
object DomainValidator {

    // 원본 정규식: ^[a-zA-Z0-9]([a-zA-Z0-9\-_.]*[a-zA-Z0-9])?$
    private val regex = Regex("^[a-zA-Z0-9]([a-zA-Z0-9\\-_.]*[a-zA-Z0-9])?$")

    /**
     * @return null = 검증 통과, 그 외 = 에러 메시지 (원본 Rust 영문 메시지 그대로)
     */
    fun validate(domain: String): String? {
        if (domain.isEmpty()) return "Domain cannot be empty"
        if (domain.length > 255) return "Domain too long (max 255 characters)"

        if (domain.startsWith("*.")) {
            val base = domain.substring(2)
            if (base.isEmpty()) return "Wildcard must have a base domain (e.g., *.example.com)"
            if (!regex.matches(base)) return "Invalid wildcard domain format"
            if (!base.contains('.')) return "Wildcard base domain must contain at least one dot"
            return null
        }

        if (!regex.matches(domain)) return "Invalid domain format"
        if (!domain.contains('.')) return "Domain must contain at least one dot"
        return null
    }
}
