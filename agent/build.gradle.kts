plugins {
    kotlin("jvm") version "2.1.20"
    application
    // 개선 3: GraalVM Native Image 빌드
    id("org.graalvm.buildtools.native") version "0.10.4"
}

group = "com.vpnlab"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")
    implementation("org.slf4j:slf4j-simple:2.0.16")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.vpnlab.agent.MainKt")
    // pgrep -f "disk-control-poc" 가 원본과 동일하게 매칭되도록 스크립트/jar 이름 고정
    applicationName = "disk-control-poc"
}

tasks.jar {
    archiveBaseName.set("disk-control-poc")
    manifest { attributes["Main-Class"] = "com.vpnlab.agent.MainKt" }
    // 단일 실행 jar (의존성 포함)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// GraalVM Native Image 설정.
// jackson 리플렉션은 reachability-metadata repo + 자체 reflect-config.json 으로 처리.
graalvmNative {
    // GraalVM Reachability Metadata Repository (jackson 등 라이브러리 메타데이터 자동 포함)
    metadataRepository {
        enabled.set(true)
    }
    binaries {
        named("main") {
            imageName.set("disk-control-poc")   // pgrep -f 매칭 유지
            mainClass.set("com.vpnlab.agent.MainKt")
            buildArgs.add("-O2")
            buildArgs.add("--no-fallback")
            buildArgs.add("-H:+ReportExceptionStackTraces")
        }
    }
}
