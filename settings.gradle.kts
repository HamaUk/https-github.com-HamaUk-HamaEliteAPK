pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.google.com")
        }
        maven {
            url = uri("https://maven.aliyun.com/repository/public")
        }
        maven {
            url = uri("https://maven.aliyun.com/repository/central")
        }
        maven {
            url = uri("https://maven.aliyun.com/repository/google")
        }
        maven {
            url = uri("https://maven.aliyun.com/repository/gradle-plugin")
        }
        maven {
            url = uri("https://maven.aliyun.com/repository/apache-snapshots")
        }
        maven {
            url = uri("https://jitpack.io")
        }
        // Legacy Bilibili IJK native AARs (read-only mirror)
        maven {
            url = uri("https://maven.aliyun.com/repository/jcenter")
            content {
                includeGroup("tv.danmaku.android")
            }
        }
    }
}

rootProject.name = "IPTV"
include(":app")
include(":ijkplayer-java")
project(":ijkplayer-java").projectDir = file("app/libraries/ijkplayer-java")
