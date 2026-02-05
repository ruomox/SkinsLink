// Uses root Gradle configuration
plugins {
    // shadow
    id("com.gradleup.shadow") version "9.3.1"
}

dependencies {
    implementation(project(":core"))
    implementation(project(":platform-api"))
    runtimeOnly(project(":platform-paper"))

    // Paper API
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    // Adventure API
    compileOnly("net.kyori:adventure-api:4.14.0")
}

tasks {
    shadowJar {
        mergeServiceFiles()
        archiveFileName.set("SkinsLink-${rootProject.version}.jar")
    }

    build {
        dependsOn(shadowJar)
    }
}