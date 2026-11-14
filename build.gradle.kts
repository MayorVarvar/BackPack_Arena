plugins {
    java
}

group = "dev.backpackarena"
version = "0.1.0"

// Версия Paper API: поменяйте под версию вашего сервера (1.21.x).
val paperVersion = "1.21.4-R0.1-SNAPSHOT"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

// На Windows тестовый процесс Gradle не находит классы, если путь содержит кириллицу
// (например, C:\Users\матвей\...). Тогда промежуточная сборка идёт в ASCII-папку,
// а готовый jar всё равно кладётся в dist/ внутри проекта.
if (projectDir.absolutePath.any { it.code > 127 }) {
    val base = System.getenv("PUBLIC") ?: System.getProperty("java.io.tmpdir")
    layout.buildDirectory.set(file("$base/backpack-arena-build"))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperVersion")

    // core-пакет не зависит от Bukkit; для тестов нужен только SnakeYAML
    testImplementation("org.yaml:snakeyaml:2.2")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.test {
    useJUnitPlatform()
    systemProperty("file.encoding", "UTF-8")
}

tasks.jar {
    archiveFileName.set("BackpackArena-${project.version}.jar")
    destinationDirectory.set(layout.projectDirectory.dir("dist"))
}
