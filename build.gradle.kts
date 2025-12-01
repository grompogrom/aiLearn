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
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-cio:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation("org.slf4j:slf4j-simple:2.0.13")
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

kotlin {
    jvmToolchain(21)
}