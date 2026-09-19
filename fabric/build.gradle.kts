// Fabric bootstrap. Depends on Fabric Loader only - not on Fabric API and not on
// Minecraft itself, because the mod never touches a Minecraft class.
//
// That is also why no remapping plugin is required: the mod contains no references
// to Minecraft types, so its bytecode is namespace independent and runs unchanged
// in a named development environment and in an intermediary production server.

val log4jVersion = providers.gradleProperty("log4jVersion")
val fabricLoaderVersion = providers.gradleProperty("fabricLoaderVersion")

// The shared filtering logic is compiled into this module from common/src/main/java. The shared unit tests live
// in the common module only: they exercise the shared code, so running them once is enough. This module still
// has its own small test source set, for the Fabric-only configuration defaults.
sourceSets {
    main {
        java.srcDir(rootProject.file("common/src/main/java"))
    }
}

dependencies {
    compileOnly("net.fabricmc:fabric-loader:${fabricLoaderVersion.get()}")
    compileOnly("org.apache.logging.log4j:log4j-api:${log4jVersion.get()}")
    compileOnly("org.apache.logging.log4j:log4j-core:${log4jVersion.get()}")

    // FabricSettings is plain file and property handling, so it needs no Fabric Loader or Minecraft to test.
    testImplementation(platform("org.junit:junit-bom:${providers.gradleProperty("junitVersion").get()}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Captured at configuration time: a filesMatching action runs at execution time, where touching the project
// model is deprecated and will be an error in Gradle 10.
val projectVersion = project.version.toString()

tasks.named<ProcessResources>("processResources") {
    filesMatching("fabric.mod.json") {
        expand("version" to projectVersion)
    }
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("prejoin-log-silencer-fabric")
    archiveVersion.set(projectVersion)

    manifest {
        attributes(
            "Implementation-Title" to "Pre-Join Log Silencer (Fabric)",
            "Implementation-Version" to projectVersion,
            "Specification-Title" to "Pre-Join Log Silencer",
        )
    }

    from(rootProject.file("LICENSE")) {
        into("META-INF")
    }
}
