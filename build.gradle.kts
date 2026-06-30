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

// NOTE: 1.21.x requires Java 21 (Mojang's floor since 1.20.5). Bump this to
// the exact Paper version your server runs.
val paperApi = "1.21.4-R0.1-SNAPSHOT"

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApi")

    testImplementation("io.papermc.paper:paper-api:$paperApi")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    // MockBukkit for MC 1.21. Confirm the latest version for your Paper build
    // at https://mockbukkit.org / Maven Central if resolution fails.
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.40.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
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
