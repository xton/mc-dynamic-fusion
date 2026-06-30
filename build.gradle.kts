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

// Minecraft 26.1+ requires Java 25 at runtime. 26.1.2 is the latest *stable*
// line (26.2.* is still -alpha), and it matches the MockBukkit build below.
// A plugin built against 26.1.2 runs fine on newer 26.x servers.
val paperApi = "26.1.2.build.72-stable"

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApi")

    testImplementation("io.papermc.paper:paper-api:$paperApi")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.1")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v26.1.2:4.113.4")
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
