// The common module owns all filtering logic. It must not know anything about
// Bukkit, Fabric or Minecraft classes: the only compile dependency is Log4j2,
// which every supported server already provides on the system classpath.

val log4jVersion = providers.gradleProperty("log4jVersion")
val junitVersion = providers.gradleProperty("junitVersion")

dependencies {
    // Provided by the server at runtime. Using compileOnly guarantees the jar we
    // ship can never introduce a second, conflicting copy of Log4j2.
    compileOnly("org.apache.logging.log4j:log4j-api:${log4jVersion.get()}")
    compileOnly("org.apache.logging.log4j:log4j-core:${log4jVersion.get()}")

    // Unit tests need a real Log4j2 on the test classpath, including log4j-core so
    // that Log4jLogEvent, LoggerConfig and AbstractFilter can be exercised directly.
    testImplementation("org.apache.logging.log4j:log4j-api:${log4jVersion.get()}")
    testImplementation("org.apache.logging.log4j:log4j-core:${log4jVersion.get()}")

    testImplementation(platform("org.junit:junit-bom:${junitVersion.get()}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    // The tests use a real Log4j2 core so that LoggerConfig composition and installation are covered for
    // real. The filter only ever denies Minecraft pre-join disconnect lines, so no test output is affected.
    systemProperty("log4j2.statusLoggerLevel", "ERROR")
    maxHeapSize = "512m"
}
