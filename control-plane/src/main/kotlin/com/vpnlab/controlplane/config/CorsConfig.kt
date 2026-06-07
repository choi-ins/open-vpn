package com.vpnlab.controlplane.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 원본 Rust 의 CorsLayer (any origin/methods/headers) 와 동일.
 * PoC 단계에선 전체 허용, 운영에선 도메인 제한 필요.
 */
@Configuration
class CorsConfig {
    @Bean
    fun corsConfigurer(): WebMvcConfigurer = object : WebMvcConfigurer {
        override fun addCorsMappings(registry: CorsRegistry) {
            registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("*")
                .allowedHeaders("*")
        }
    }
}
