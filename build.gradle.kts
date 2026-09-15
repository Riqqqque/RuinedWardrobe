import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.bundling.AbstractArchiveTask

plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "dev.rique"
version = "1.0.16"
val pluginVersion = version.toString()
val hangarMaxUploadBytes = 10_000_000L
val shadedJarFile = layout.buildDirectory.file("libs/${rootProject.name}-$pluginVersion.jar")
val paperApiVersion = providers.gradleProperty("paperApiVersion").orElse("1.21-R0.1-SNAPSHOT")
val javaToolchainVersion = providers.gradleProperty("javaToolchainVersion").map(String::toInt).orElse(21)
val javaReleaseVersion = providers.gradleProperty("javaReleaseVersion").map(String::toInt).orElse(21)

java {
    toolchain {
        languageVersion.set(javaToolchainVersion.map { JavaLanguageVersion.of(it) })
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:${paperApiVersion.get()}")
    compileOnly("me.clip:placeholderapi:2.12.3")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }

    implementation("com.github.ben-manes.caffeine:caffeine:3.2.4") {
        exclude(group = "com.google.errorprone", module = "error_prone_annotations")
        exclude(group = "org.jspecify", module = "jspecify")
    }
    implementation("com.zaxxer:HikariCP:7.1.0") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation("org.xerial:sqlite-jdbc:3.53.2.1")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.9") {
        // RuinedWardrobe uses TCP host/port connections, not Windows GSSAPI, pipes, or Unix sockets.
        exclude(group = "com.github.waffle", module = "waffle-jna")
    }
    implementation("org.bstats:bstats-bukkit:3.2.1")

    testImplementation(platform("org.junit:junit-bom:6.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("io.papermc.paper:paper-api:${paperApiVersion.get()}")
    testImplementation("me.clip:placeholderapi:2.12.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaReleaseVersion)
    options.compilerArgs.addAll(listOf("-Xlint:all", "-parameters"))
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.processResources {
    from(rootProject.file("LICENSE")) {
        into("META-INF")
        rename { "LICENSE.txt" }
    }
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
}

tasks.jar {
    archiveClassifier.set("plain")
}

tasks.shadowJar {
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
    exclude("org/sqlite/native/FreeBSD/**")
    exclude("org/sqlite/native/Linux/x86/**")
    exclude("org/sqlite/native/Linux/arm/**")
    exclude("org/sqlite/native/Linux/armv6/**")
    exclude("org/sqlite/native/Linux/armv7/**")
    exclude("org/sqlite/native/Linux/ppc64/**")
    exclude("org/sqlite/native/Linux/riscv64/**")
    exclude("org/sqlite/native/Linux-Android/**")
    exclude("org/sqlite/native/Linux-Musl/x86/**")
    exclude("org/sqlite/native/Windows/aarch64/**")
    exclude("org/sqlite/native/Windows/x86/**")
    exclude("org/sqlite/native/Windows/armv7/**")
    relocate("com.github.benmanes.caffeine", "dev.rique.ruinedwardrobe.libs.caffeine")
    relocate("com.zaxxer.hikari", "dev.rique.ruinedwardrobe.libs.hikari")
    relocate("org.bstats", "dev.rique.ruinedwardrobe.libs.bstats")
}

tasks.register("checkHangarUploadSize") {
    dependsOn(tasks.shadowJar)
    doLast {
        val jarFile = shadedJarFile.get().asFile
        check(jarFile.isFile) { "Missing shaded jar: ${jarFile.absolutePath}" }
        val size = jarFile.length()
        check(size < hangarMaxUploadBytes) {
            "Shaded jar is ${"%,d".format(size)} bytes; Hangar requires less than ${"%,d".format(hangarMaxUploadBytes)} bytes."
        }
    }
}

tasks.register<Copy>("copyStableJar") {
    dependsOn(tasks.shadowJar)
    from(shadedJarFile)
    into(layout.buildDirectory.dir("libs"))
    rename { "${rootProject.name}.jar" }
}

tasks.build {
    dependsOn(tasks.shadowJar)
    dependsOn(tasks.named("copyStableJar"))
    dependsOn(tasks.named("checkHangarUploadSize"))
}
