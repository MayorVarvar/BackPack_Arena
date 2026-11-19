plugins {
    java
    kotlin("jvm") version "2.1.21"
}

group = "dev.backpackarena"
version = "0.1.0"

// Версия Paper API: поменяйте под версию вашего сервера (1.21.x).
val paperVersion = "1.21.4-R0.1-SNAPSHOT"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

kotlin {
    jvmToolchain(21)
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
    // Kotlin: новый код режима (коллекции, карты редкостей, кирки, предложения дня, выкуп).
    // Стандартная библиотека кладётся внутрь jar — серверу ничего не нужно докачивать.
    implementation(kotlin("stdlib"))

    // core-пакет не зависит от Bukkit; для его тестов нужен только SnakeYAML
    testImplementation("org.yaml:snakeyaml:2.2")
    // тесты пакета plugin читают текст книг, панелей и босс-баров — им нужны типы Adventure из API Paper
    testImplementation("io.papermc.paper:paper-api:$paperVersion")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

// Ресурс-пак с иконками из макета (resourcepack/) — zip кладётся внутрь плагина и в dist/.
val packZip by tasks.registering(Zip::class) {
    from("resourcepack")
    archiveFileName.set("BackpackArena-pack.zip")
    destinationDirectory.set(layout.buildDirectory.dir("pack"))
    // одинаковый zip при каждой сборке → одинаковый SHA-1, клиент не перекачивает пак зря
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
    from(packZip) {
        into("pack")
    }
}

val copyPackToDist by tasks.registering(Copy::class) {
    from(packZip)
    into(layout.projectDirectory.dir("dist"))
}

tasks.test {
    useJUnitPlatform()
    systemProperty("file.encoding", "UTF-8")
}

tasks.jar {
    archiveFileName.set("BackpackArena-${project.version}.jar")
    // kotlin-stdlib внутри плагина
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) }
    }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/**", "META-INF/*.kotlin_module")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    destinationDirectory.set(layout.projectDirectory.dir("dist"))
    finalizedBy(copyPackToDist)
}
