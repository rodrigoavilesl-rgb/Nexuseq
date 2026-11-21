import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Versión de escritorio (Swing). Genera un JAR ejecutable: java -jar WreckArena-desktop.jar
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

dependencies {
    implementation(project(":core"))
}

tasks.register<Jar>("fatJar") {
    group = "distribution"
    archiveFileName.set("WreckArena-desktop.jar")
    manifest {
        attributes["Main-Class"] = "cl.nexus.wreck.desktop.DesktopMainKt"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) }
    })
}
