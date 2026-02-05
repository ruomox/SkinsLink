dependencies {
    implementation(project(":core"))
    implementation(project(":platform-api"))
    // Paper API
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    from(project(":core").sourceSets.main.get().output)
}