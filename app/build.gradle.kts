/*******************************
 목   적 : Compose 데모 앱 빌드 설정
 작성자 : 송우진
 --------------------------------
 2026-11-13 : 초안
*******************************/
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
repositories {
    google(); mavenCentral()
    // FR-05: 공식 Pleos 음성 SDK 검증용 저장소입니다.
    maven("https://nexus-playground.pleos.ai/repository/maven-releases/")
}
android {
    namespace = "chageun.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "kr.hellobiz.chageun"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.2.0-demo"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets["main"].java.setSrcDirs(listOf("src/main/kotlin"))
    sourceSets["test"].java.setSrcDirs(emptyList<String>())
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }

}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(project(":"))
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")
    // FR-05·13: JNA 5.17+의 Android 16KB 수정 및 5.18의 교착 수정 포함.
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.06.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
