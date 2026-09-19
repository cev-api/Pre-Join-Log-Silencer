// Root build script. Every module shares the same Java level, compiler warnings
// configuration and reproducible-archive settings.

val javaRelease = providers.gradleProperty("javaRelease")

subprojects {
    apply(plugin = "java-library")

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        // --release keeps the bytecode at the target level and stops the build from
        // linking against APIs that do not exist on the oldest supported runtime.
        options.release.set(javaRelease.get().toInt())
        options.compilerArgs.addAll(listOf("-Xlint:all,-serial,-processing"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("failed", "skipped")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    tasks.withType<Jar>().configureEach {
        // Reproducible archives: identical inputs produce byte-identical jars.
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}
