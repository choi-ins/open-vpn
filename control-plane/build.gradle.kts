plugins {
    id("org.springframework.boot") version "3.5.4"
    id("io.spring.dependency-management") version "1.1.6"
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.spring") version "2.1.20"
}

group = "com.vpnlab"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot core
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")

    // CORS는 spring-web에 포함

    // Kotlin support
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.9.0")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.mockito")
    }
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.testcontainers:mongodb:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// ── 개선 4: admin-ui 프로덕션 서빙 ────────────────────────────────────────────
// Vue 앱을 빌드해 Spring 정적 리소스(static/)로 편입 → 단일 jar 로 UI+API 서빙.
val uiDir = rootProject.projectDir.resolveSibling("admin-ui")
val uiDist = uiDir.resolve("dist")

val buildUi by tasks.registering(Exec::class) {
    group = "frontend"
    description = "npm install + build 로 admin-ui/dist 생성"
    workingDir = uiDir
    // 로컬 개발 편의: node_modules 없으면 install 포함
    commandLine("sh", "-c", "npm install --no-audit --no-fund && npm run build")
    // dist가 최신이면 재빌드 스킵
    inputs.dir(uiDir.resolve("src"))
    inputs.file(uiDir.resolve("package.json"))
    outputs.dir(uiDist)
}

// bootJar/bootRun 시 UI를 static/ 으로 함께 넣고 싶으면 -PwithUi 로 활성화.
// 기본 빌드/테스트는 npm 의존 없이 빠르게 돌도록 가드.
// processResources 파이프라인에 편입해 resolveMainClassName/bootJar/bootRun 모두
// 일관된 태스크 의존성을 갖게 한다.
if (project.hasProperty("withUi")) {
    tasks.named<ProcessResources>("processResources") {
        dependsOn(buildUi)
        from(uiDist) { into("static") }
    }
}
