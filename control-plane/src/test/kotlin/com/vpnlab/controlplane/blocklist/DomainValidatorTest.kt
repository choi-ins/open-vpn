package com.vpnlab.controlplane.blocklist

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** 원본 Rust test_validate_domain() 케이스 1:1 이식 */
class DomainValidatorTest {

    @Test
    fun `regular domains pass`() {
        assertThat(DomainValidator.validate("example.com")).isNull()
        assertThat(DomainValidator.validate("sub.example.com")).isNull()
        assertThat(DomainValidator.validate("example-site.co.uk")).isNull()
        assertThat(DomainValidator.validate("a.b")).isNull()
    }

    @Test
    fun `wildcard domains pass`() {
        assertThat(DomainValidator.validate("*.example.com")).isNull()
        assertThat(DomainValidator.validate("*.ads.com")).isNull()
        assertThat(DomainValidator.validate("*.tracker.net")).isNull()
    }

    @Test
    fun `empty domain rejected`() {
        assertThat(DomainValidator.validate("")).isEqualTo("Domain cannot be empty")
    }

    @Test
    fun `domain without dot rejected`() {
        assertThat(DomainValidator.validate("example")).isEqualTo("Domain must contain at least one dot")
    }

    @Test
    fun `domain starting with hyphen rejected`() {
        assertThat(DomainValidator.validate("-example.com")).isEqualTo("Invalid domain format")
    }

    @Test
    fun `domain ending with hyphen rejected`() {
        assertThat(DomainValidator.validate("example.com-")).isEqualTo("Invalid domain format")
    }

    @Test
    fun `domain with space rejected`() {
        assertThat(DomainValidator.validate("exam ple.com")).isEqualTo("Invalid domain format")
    }

    @Test
    fun `bare wildcard rejected`() {
        assertThat(DomainValidator.validate("*.")).isEqualTo("Wildcard must have a base domain (e.g., *.example.com)")
    }

    @Test
    fun `wildcard with single-word base rejected`() {
        assertThat(DomainValidator.validate("*.com")).isEqualTo("Wildcard base domain must contain at least one dot")
    }

    @Test
    fun `over-length domain rejected`() {
        val long = "a".repeat(256)
        assertThat(DomainValidator.validate(long)).isEqualTo("Domain too long (max 255 characters)")
    }
}
