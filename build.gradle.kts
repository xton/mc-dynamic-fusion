plugins {
    java
}

group = "com.xton.fusion"
version = "0.0.1-phase0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

// Minecraft 26.1+ requires Java 25 at runtime; 26.2 is the latest line.
//
// CONFIRM LOCALLY: the paper-api version string changed format after 26.1.
// Pre-26.1 used "{version}-R0.1-SNAPSHOT"; 26.x publishes build-numbered
// versions (e.g. 26.2.build.NN-alpha). Resolve the exact current string from
// https://repo.papermc.io/ (blocked in the authoring environment) and pin it
// here. The Maven version range below asks for "26.2 build or newer".
val paperApi = "[26.2.build,)"

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApi")

    testImplementation("io.papermc.paper:paper-api:$paperApi")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    // CONFIRM LOCALLY: MockBukkit artifact/version for MC 26.x. Check the
    // latest at https://mockbukkit.org / Maven Central; the coordinate below
    // is a best guess and almost certainly needs the version updated.
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.40.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filteringCharset = "UTF-8"
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}
