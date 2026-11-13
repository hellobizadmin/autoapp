/*******************************
 목   적 : 순수 Kotlin 코어 빌드와 단위 테스트 설정
 작성자 : 송우진
 --------------------------------
 2026-11-13 : 초안
 2026-11-13 : 파일 머리말 및 한글 설명 주석 추가
 2026-11-13 : FR-08·FR-13 및 앱 모듈 연결 지원
*******************************/
plugins {
    kotlin("jvm") version "2.2.21"
    id("com.android.application") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
}

repositories { mavenCentral() }
dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation(kotlin("test-junit"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
kotlin { jvmToolchain(21); compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
tasks.withType<JavaCompile> { sourceCompatibility = "17"; targetCompatibility = "17" }
sourceSets {
    main {
        kotlin.srcDir("app/src/main/java")

    }
    test { kotlin.srcDir("app/src/test/java"); resources.srcDir("app/src/main/assets") }
}
// 1단계는 JVM 전용이며 AGENTS.md에서 지정한 테스트 명령을 제공합니다.
tasks.register("testDebugUnitTest") { dependsOn(tasks.test) }
