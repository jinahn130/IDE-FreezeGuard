plugins {
    // Kotlin for plugin sources
    kotlin("jvm") version "2.1.20"

    // IntelliJ Platform Gradle Plugin 2.x
    id("org.jetbrains.intellij.platform") version "2.8.0"
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
    }
}
