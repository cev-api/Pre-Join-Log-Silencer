// Bukkit family bootstrap: CraftBukkit, Spigot, Paper, Purpur, Pufferfish and Folia.
//
// The shared filtering logic is compiled into this module from common/src/main/java
// instead of being packaged as a second jar. Server plugin class loaders expect a
// single self-contained artifact, and there is no third-party code to relocate.

val log4jVersion = providers.gradleProperty("log4jVersion")
val spigotApiVersion = providers.gradleProperty("spigotApiVersion")

// The shared filtering logic is compiled into this module from common/src/main/java. The unit tests live in the
// common module only: they exercise the shared code, so running them once is enough.
sourceSets {
    main {
        java.srcDir(rootProject.file("common/src/main/java"))
    }
}

dependencies {
    // compileOnly: the server provides all of these. Nothing is ever bundled.
    compileOnly("org.spigotmc:spigot-api:${spigotApiVersion.get()}")
    compileOnly("org.apache.logging.log4j:log4j-api:${log4jVersion.get()}")
    compileOnly("org.apache.logging.log4j:log4j-core:${log4jVersion.get()}")
    // The Bukkit API is annotated with JetBrains nullness annotations. Having them on the compile class path
    // keeps javac -Xlint:classfile quiet and gives the IDE real null information for the API we call into.
    compileOnly("org.jetbrains:annotations:24.1.0")
}

// Captured at configuration time: a filesMatching action runs at execution time, where touching the project
// model is deprecated and will be an error in Gradle 10.
val projectVersion = project.version.toString()

tasks.named<ProcessResources>("processResources") {
    filesMatching("plugin.yml") {
        expand("version" to projectVersion)
    }
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("prejoin-log-silencer-bukkit")
    archiveVersion.set(projectVersion)

    manifest {
        attributes(
            "Implementation-Title" to "Pre-Join Log Silencer (Bukkit)",
            "Implementation-Version" to projectVersion,
            "Specification-Title" to "Pre-Join Log Silencer",
        )
    }

    from(rootProject.file("LICENSE")) {
        into("META-INF")
    }
}
