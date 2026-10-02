rootProject.name = "mcp2-java-demo"

include("server", "secure-server", "cimd-client", "client")

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention").version("1.0.0")
}
