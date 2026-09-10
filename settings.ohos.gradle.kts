pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
        maven {
            url = uri("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
        }
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven {
            url = uri("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
        }
    }
}

rootProject.name = "AiStock_prices"

val buildFileName = "build.ohos.gradle.kts"
rootProject.buildFileName = buildFileName

include(":shared")
project(":shared").buildFileName = buildFileName
include(":chart")
project(":chart").buildFileName = buildFileName
include(":table")
project(":table").buildFileName = buildFileName