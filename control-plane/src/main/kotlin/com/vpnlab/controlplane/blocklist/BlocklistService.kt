package com.vpnlab.controlplane.blocklist

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

sealed class AddResult {
    object Inserted : AddResult()
    object Conflict : AddResult()
    data class Invalid(val reason: String) : AddResult()
}

sealed class DeleteResult {
    object Removed : DeleteResult()
    object NotFound : DeleteResult()
}

/**
 * 블록리스트 비즈니스 로직.
 * - MongoDB에 도메인 상태 저장
 * - 변경 시 파일(hosts + Corefile) 자동 재생성 → CoreDNS 가 reload 5s/10s 로 자동 픽업
 */
@Service
class BlocklistService(
    private val repo: BlocklistRepository,
    private val fileWriter: BlocklistFileWriter,
    private val corednsRestarter: CorednsRestarter,
) {
    private val log = LoggerFactory.getLogger(BlocklistService::class.java)

    /** 일반 + 와일드카드 모두 합쳐서 정렬해서 반환 (원본 list_domains() 동일). */
    fun listDomains(): List<String> =
        repo.findAll().map { it.domain }.sorted()

    fun addDomain(domain: String): AddResult {
        DomainValidator.validate(domain)?.let { return AddResult.Invalid(it) }

        if (repo.existsById(domain)) return AddResult.Conflict

        val isWildcard = domain.startsWith("*.")
        repo.save(BlockedDomain(domain = domain, isWildcard = isWildcard))
        regenerateAffectedFile(isWildcard)
        return AddResult.Inserted
    }

    fun removeDomain(domain: String): DeleteResult {
        if (!repo.existsById(domain)) return DeleteResult.NotFound

        val isWildcard = domain.startsWith("*.")
        repo.deleteById(domain)
        regenerateAffectedFile(isWildcard)
        return DeleteResult.Removed
    }

    /**
     * 변경된 종류에 따라 hosts 또는 Corefile만 재생성.
     * (원본 Rust도 동일 분기 — wildcard 변경 시 Corefile만, 일반 변경 시 hosts만)
     *
     * 일반 도메인: hosts plugin의 `reload 5s`가 자동 픽업 → 재시작 불필요.
     * 와일드카드: template block은 reload plugin이 반영 못 함 → CoreDNS 컨테이너 재시작.
     */
    private fun regenerateAffectedFile(wasWildcard: Boolean) {
        try {
            if (wasWildcard) {
                val wildcards = repo.findAllByIsWildcard(true).map { it.domain }
                fileWriter.writeCorefile(wildcards)
                corednsRestarter.restartAsync()
            } else {
                val regulars = repo.findAllByIsWildcard(false).map { it.domain }
                fileWriter.writeHosts(regulars)
            }
        } catch (e: Exception) {
            // 파일 쓰기 실패는 로깅만. DB는 이미 갱신됨. CoreDNS 강제 재시작 시 일관성 회복.
            log.error("Failed to regenerate blocklist file (wasWildcard={}): {}", wasWildcard, e.message)
        }
    }
}
