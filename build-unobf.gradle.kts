// Build file for the UNOBFUSCATED Minecraft line (26.x).
//
// From 26.1 onward Minecraft ships with real (Mojang) names and parameter names
// already in the jar, so there is nothing to deobfuscate. Per Fabric's official
// "Porting to 26.1" guide this needs the non-remapping Loom variant:
//   - plugin id `net.fabricmc.fabric-loom` (not `fabric-loom`),
//   - no `mappings` dependency,
//   - plain `implementation`/`compileOnly` instead of `modImplementation`/`modCompileOnly`,
//   - Java 25.
// Our source is already written in Mojang names (see build.gradle.kts), which are
// exactly the names 26.x uses, so the same source compiles here unchanged.
plugins {
    id("net.fabricmc.fabric-loom")
}

val mcVersion = stonecutter.current.version

version = "${property("mod_version")}+mc$mcVersion"
group = property("maven_group") as String

base {
    archivesName.set(property("archives_base_name") as String)
}

repositories {
    maven("https://maven.meteordev.org/snapshots")
    maven("https://maven.meteordev.org/releases")
    maven("https://babbaj.github.io/maven/")
}

// A single 26.1-SNAPSHOT Baritone covers the whole 26.1.x line (depends mc 26.1/26.1.1/26.1.2).
val fabricApiVersion = mapOf(
    "26.1.2" to "0.145.1+26.1",
)[mcVersion] ?: error("No Fabric API version mapped for Minecraft $mcVersion")

val baritoneVersion = "26.1-SNAPSHOT"

loom {
    runs {
        named("client") {
            programArgs("--username", "Shasync")
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    // No mappings: 26.x is unobfuscated.
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    // Baritone (Meteor fork) for 26.1, and the native pathfinder it bundles (1.4.1).
    implementation("meteordevelopment:baritone:$baritoneVersion")
    compileOnly("dev.babbaj:nether-pathfinder:1.4.1")
    runtimeOnly("dev.babbaj:nether-pathfinder:1.4.1")
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", mcVersion)
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to mcVersion,
        )
    }
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

// Fabric API renamed some classes for the official-mappings move in 26.1. The only
// one we use: ClientCommandManager -> ClientCommands (import + call site in
// DebugCommand). A build-time string swap keeps the 1.21.x source (which uses the
// old name) untouched while this unobf node compiles against the new name.
stonecutter {
    replacements.string(eval(current.version, ">=26.1")) {
        replace("ClientCommandManager", "ClientCommands")
    }
}
