plugins {
    id("java-library")
}

group = "com.ruomox.skinslink"
version = "1.0-SNAPSHOT"

subprojects {
    apply(plugin = "java-library")

    repositories {
        mavenCentral()
        // Paper API
        maven("https://repo.papermc.io/repository/maven-public/")
    }

    dependencies {
        implementation("com.google.code.gson:gson:2.10.1")
        compileOnly("org.jetbrains:annotations:24.1.0")
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}