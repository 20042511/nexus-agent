pluginManagement {
    repositories {
        gradlePluginPortal()       // KSP/Hilt 插件必须从这里找
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
    }
}
rootProject.name = "nexus-agent"
include(":app")
