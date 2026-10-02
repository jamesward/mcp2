plugins {
    id("org.springframework.boot") version "4.2.0-M2" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    configure<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension> {
        imports {
            // latest milestone = latest MCP Java SDK
            mavenBom("org.springframework.ai:spring-ai-bom:2.1.0-M1")
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
