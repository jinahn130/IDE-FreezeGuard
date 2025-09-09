plugins {
    // Kotlin for plugin sources
    kotlin("jvm") version "2.1.20"

    // IntelliJ Platform Gradle Plugin 2.x
    id("org.jetbrains.intellij.platform") version "2.8.0"
    
    // Gradle Changelog Plugin
    id("org.jetbrains.changelog") version "2.2.1"
}

kotlin {
    // The template uses Java 21 as of 2024+; keep it
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        // Target a modern baseline IDE. You can bump later.
        intellijIdeaCommunity("2024.2.5")
        // Java plugin is bundled in many IDEs; include it so actions work everywhere.
        bundledPlugin("com.intellij.java")
        
        // Plugin Verifier dependencies
        pluginVerifier()
    }
    
    // Testing dependencies
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

// Configure IntelliJ Platform plugin
intellijPlatform {
    pluginConfiguration {
        version = "1.0.0"
        name = "Freeze Guard"
        description = "Monitor and track IntelliJ IDEA UI freezes and performance"
    }
    
    // Configure plugin verification
    pluginVerification {
        ides {
            // Verify against the same IDE version we're targeting
            recommended()
        }
    }
}

// Configure changelog plugin
changelog {
    version.set("1.0.0")
    path.set("${project.projectDir}/CHANGELOG.md")
    header.set(provider { "[${version.get()}]" })
    headerParserRegex.set("""(\d+\.\d+\.\d+)""".toRegex())
    introduction.set("""
        IDE FreezeGuard is a cross-platform performance monitoring solution for IntelliJ IDEA and Visual Studio Code.
        It tracks UI freezes, measures performance metrics, and provides telemetry for monitoring system health.
    """.trimIndent())
    itemPrefix.set("-")
    keepUnreleasedSection.set(true)
    unreleasedTerm.set("[Unreleased]")
    groups.set(listOf("Added", "Changed", "Fixed", "Removed"))
}

tasks.test {
    useJUnitPlatform()
}
