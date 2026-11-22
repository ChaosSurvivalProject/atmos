rootProject.name = "atmos"

pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }
}

include("protocol")
include("server")
include("client")
