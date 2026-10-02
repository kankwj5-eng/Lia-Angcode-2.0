pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "AngCode"

include(":app")
include(":runtime-core")
include(":agent-core")
include(":connector-core")

project(":runtime-core").projectDir = file("../runtime/core")
project(":agent-core").projectDir = file("../agents/core")
project(":connector-core").projectDir = file("../connectors/core")
