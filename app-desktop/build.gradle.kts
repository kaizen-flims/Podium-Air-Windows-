import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.zip.ZipFile
import java.io.File
import java.security.MessageDigest
plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
}
kotlin { jvmToolchain(21) }
val os = System.getProperty("os.name").lowercase()
val fxPlatform = when { os.contains("win") -> "win"; os.contains("mac") -> "mac"; else -> "linux" }
dependencies {
    implementation(project(":shared-domain"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("net.jthink:jaudiotagger:3.0.1")
    implementation("de.sfuhrm:jaad:0.8.7") { isTransitive = false }
    implementation("org.jflac:jflac-codec:1.5.2") { isTransitive = false }
    implementation("io.github.jaredmdobson:concentus:1.0.2") { isTransitive = false }
    implementation("javazoom:jlayer:1.0.1") { isTransitive = false }
    for (module in listOf("base", "graphics", "media")) {
        implementation("org.openjfx:javafx-$module:21.0.9:$fxPlatform")
    }
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}
tasks.test { useJUnitPlatform() }
compose.desktop {
    application {
        mainClass = "com.podium.air.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Podium Air"
            packageVersion = "0.2.0"
            description = "Podium Air — Windows Edition"
            vendor = "Prem Das aka Kaizen"
            copyright = "Copyright © 2026 Prem Das and upstream contributors"
            modules("java.desktop", "java.logging", "java.prefs", "java.net.http", "jdk.unsupported", "java.xml", "jdk.crypto.ec")
            licenseFile.set(rootProject.file("LICENSE"))
            windows {
                iconFile.set(project.file("icon.ico"))
                menuGroup = "Podium Air"
                shortcut = true
                dirChooser = true
                upgradeUuid = "2e06a9dc-fc0e-4f63-9232-ce7297e72cc2"
            }
        }
    }
}
// Include dependency-provided notices without dropping their original paths or text.
val dependencyNotices by tasks.registering {
    val output = layout.buildDirectory.dir("generated/notices")
    val nativeNotices = rootProject.layout.buildDirectory.dir("renderer-notices")
    inputs.files(configurations.runtimeClasspath)
    inputs.dir(project.file("src/main/resources/licenses"))
    inputs.dir(nativeNotices).optional()
    outputs.dir(output)
    doLast {
        val dest = output.get().asFile
        dest.deleteRecursively(); dest.mkdirs()
        val jars = configurations.runtimeClasspath.get().files.filter { it.extension == "jar" }
        val inventory = jars.sortedBy { it.name }.joinToString("\n") { jar ->
            val digest = MessageDigest.getInstance("SHA-256").digest(jar.readBytes()).joinToString("") { "%02x".format(it) }
            "$digest  ${jar.name}"
        }
        dest.resolve("licenses/DEPENDENCIES.txt").apply { parentFile.mkdirs(); writeText(inventory + "\n") }
        jars.forEach { jar ->
            ZipFile(jar).use { zip ->
                zip.entries().asSequence().filter { !it.isDirectory &&
                    (it.name.contains("LICENSE", true) || it.name.contains("NOTICE", true) ||
                     it.name.contains("COPYING", true) || it.name.contains("legal/", true)) }.forEach { entry ->
                    val out = dest.resolve("licenses/dependencies/${jar.name}/${entry.name}")
                    require(out.canonicalPath.startsWith(dest.canonicalPath + File.separator))
                    out.parentFile.mkdirs(); zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
                }
            }
        }
        nativeNotices.get().asFile.takeIf { it.isDirectory }?.copyRecursively(dest, overwrite = true)
        val manual = project.file("src/main/resources/licenses").walkTopDown().filter { it.isFile }.map { "licenses/" + it.relativeTo(project.file("src/main/resources/licenses")).invariantSeparatorsPath }.toList()
        val generated = dest.walkTopDown().filter { it.isFile }.map { it.relativeTo(dest).invariantSeparatorsPath }.toList()
        dest.resolve("licenses/INDEX.txt").writeText((listOf("licenses/LICENSE", "licenses/THIRD_PARTY_NOTICES.md") + manual + generated).distinct().sorted().joinToString("\n"))
    }
}
tasks.processResources {
    dependsOn(dependencyNotices)
    from(rootProject.layout.buildDirectory.dir("windows-helper")) { include("PodiumMediaBridge.exe"); into("windows") }
    from(rootProject.layout.buildDirectory.dir("windows-analysis")) { include("PodiumAnalysis.dll"); into("windows") }
    from(layout.buildDirectory.dir("generated/notices"))
    from(rootProject.file("LICENSE")) { into("licenses") }
    from(rootProject.file("THIRD_PARTY_NOTICES.md")) { into("licenses") }
}

val dependencySources by configurations.creating {
    isTransitive = false
    isCanBeConsumed = false
}
dependencies {
    dependencySources("net.jthink:jaudiotagger:3.0.1:sources")
    dependencySources("de.sfuhrm:jaad:0.8.7:sources")
    dependencySources("org.jflac:jflac-codec:1.5.2:sources")
    dependencySources("io.github.jaredmdobson:concentus:1.0.2:sources")
    dependencySources("javazoom:jlayer:1.0.1:sources")
    for (module in listOf("base", "graphics", "media")) dependencySources("org.openjfx:javafx-$module:21.0.9:sources")
}
tasks.register<Copy>("collectDependencySources") {
    from(dependencySources)
    into(rootProject.layout.buildDirectory.dir("dependency-sources"))
}
