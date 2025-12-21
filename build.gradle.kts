plugins {
    kotlin("jvm") version "2.2.10"
    kotlin("plugin.serialization") version "2.2.10"
    id("com.github.gmazzo.buildconfig") version "5.5.1" // Добавляем этот плагин
    application
}

group = "org.pogrom"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-client-core:3.3.2")
    implementation("io.ktor:ktor-client-cio:3.3.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.3.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.2")
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("ch.qos.logback:logback-classic:1.5.8")
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")
    implementation("io.modelcontextprotocol:kotlin-sdk-client:0.8.1")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
buildConfig {
    packageName("org.pogrom")
    val apiKey = providers.gradleProperty("perplexityApiKey").getOrElse("")
    buildConfigField("API_KEY", apiKey)
}

application {
    mainClass.set("MainKt")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

kotlin {
    jvmToolchain(21)
}