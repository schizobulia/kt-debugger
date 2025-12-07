plugins {
    kotlin("jvm") version "1.9.10"
    kotlin("plugin.serialization") version "1.9.10"
    application
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    // JLine for terminal support
    implementation("org.jline:jline:3.26.1")

    // Kotlinx serialization for JSON handling
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Kotlinx coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // ASM for bytecode manipulation
    implementation("org.ow2.asm:asm:9.7")
    implementation("org.ow2.asm:asm-util:9.7")
    implementation("org.ow2.asm:asm-tree:9.7")

    // HTTP server for DAP
    implementation("io.ktor:ktor-server-core:2.3.11")
    implementation("io.ktor:ktor-server-netty:2.3.11")
    implementation("io.ktor:ktor-websockets:2.3.11")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.6")

    // IntelliJ annotations
    implementation("org.jetbrains:annotations:24.1.0")

    // Kotlin compiler embedding
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.9.10")
    implementation("org.jetbrains.kotlin:kotlin-scripting-common:1.9.10")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm:1.9.10")
}

application {
    mainClass.set("com.kotlindebugger.MainKt")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.kotlindebugger.MainKt"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// FatJar task for creating executable JAR with all dependencies
tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    manifest {
        attributes["Main-Class"] = "com.kotlindebugger.MainKt"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.jar.get())
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}