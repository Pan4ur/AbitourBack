plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktor)
    application
}

group = "dev.pan4ur.abitour"
version = "0.1.0"

application {
    mainClass.set("dev.pan4ur.abitour.server.ServerKt")
}

dependencies {
    implementation(libs.jbcrypt)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.contentNegotiation)
    implementation(libs.ktor.serialization.kotlinxJson)
    implementation(libs.ktor.server.callLogging)
    implementation(libs.ktor.server.statusPages)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.authJwt)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.configYaml)

    implementation(libs.logback.classic)

    implementation(libs.hikari)
    implementation(libs.sqlite.jdbc)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.pdfbox)
    implementation(libs.zxing.core)
    implementation(libs.zxing.javase)

    testImplementation(libs.ktor.server.tests)
    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(21)
}
