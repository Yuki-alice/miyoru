pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "miyoru"

include(":shared")
include(":androidApp")
include(":desktopApp")
include(":plugin:api")
include(":plugin:runtime")
