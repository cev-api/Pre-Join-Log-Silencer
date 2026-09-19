pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    // All repositories are declared here so no module can accidentally disagree.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") {
            name = "spigotSnapshots"
            content {
                includeGroup("org.spigotmc")
                includeGroup("org.bukkit")
            }
        }
        maven("https://maven.fabricmc.net/") {
            name = "fabric"
            content {
                includeGroup("net.fabricmc")
            }
        }
    }
}

rootProject.name = "prejoin-log-silencer"

include("common")
include("bukkit")
include("fabric")
