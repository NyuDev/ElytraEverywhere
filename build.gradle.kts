plugins {
    id("fabric-loom")
}

// Stonecutter sets the active Minecraft version for this node.
val mcVersion = stonecutter.current.version

// Minecraft 26.x ships UNOBFUSCATED (real Mojang names already in the jar), so there
// are no official Mojang mappings to download/apply and it must build on Java 25+.
// The 1.21.x line is still obfuscated, so we deobfuscate it with Mojang's official
// mappings - which use the SAME names as 26.x, so one source serves both.
val isUnobf = mcVersion.startsWith("26.")

// The Minecraft version is baked into the jar name so a multi-version release can
// ship one jar per game version side by side (elytraeverywhere-0.1.3+mc1.21.11.jar).
version = "${property("mod_version")}+mc$mcVersion"
group = property("maven_group") as String

base {
    archivesName.set(property("archives_base_name") as String)
}

repositories {
    // Meteor's maven hosts the maintained Baritone fork (pathfinding) for current MC.
    maven("https://maven.meteordev.org/snapshots")
    maven("https://maven.meteordev.org/releases")
    // babbaj hosts nether-pathfinder, a native lib Baritone loads at startup.
    maven("https://babbaj.github.io/maven/")
}

// Per-version dependency coordinates. Fabric API tracks each Minecraft release; the
// Baritone (Meteor fork) artifact is "<mc>-SNAPSHOT" on the 1.21.x line but a single
// "26.1-SNAPSHOT" covers the whole 26.1.x line (it `depends` mc 26.1 / 26.1.1 / 26.1.2).
val fabricApiVersion = mapOf(
    "1.21.1" to "0.116.12+1.21.1",
    "1.21.3" to "0.114.1+1.21.3",
    "1.21.4" to "0.119.4+1.21.4",
    "1.21.5" to "0.128.2+1.21.5",
    "1.21.8" to "0.136.1+1.21.8",
    "1.21.10" to "0.138.4+1.21.10",
    "1.21.11" to "0.141.4+1.21.11",
    "26.1.2" to "0.145.1+26.1",
)[mcVersion] ?: error("No Fabric API version mapped for Minecraft $mcVersion")

val baritoneVersion = if (mcVersion.startsWith("26.")) "26.1-SNAPSHOT" else "$mcVersion-SNAPSHOT"

loom {
    runs {
        named("client") {
            programArgs("--username", "Shasync")
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    // 1.21.x is obfuscated -> deobfuscate with Mojang's official mappings. 26.x is
    // already unobfuscated, so Loom needs no mappings at all (and there are none to
    // fetch). Either way the resulting names are Mojang's official names.
    if (!isUnobf) {
        mappings(loom.officialMojangMappings())
    }
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    // Baritone (Meteor fork). We mixin into its ElytraProcess, and it must also be
    // present at runtime so the dev client actually has elytra autopilot to unlock.
    modImplementation("meteordevelopment:baritone:$baritoneVersion")
    // Compile-only access to the native pathfinder API so a mixin can clamp the y
    // passed to pathFind into the octree's valid range.
    compileOnly("dev.babbaj:nether-pathfinder:1.4.1")
    // Native pathfinder Baritone constructs for elytra (else NoClassDefFoundError in
    // dev). MUST match what this Baritone build bundles - every Meteor Baritone we
    // target (1.21.x and 26.1) ships nether-pathfinder-1.4.1.
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

// MC 26.x is compiled to Java 25 bytecode, so the addon must target 25 there; the
// 1.21.x line stays on Java 21. (Gradle itself must run on JDK 25+ for any 26.x node.)
val javaTarget = if (isUnobf) 25 else 21

java {
    // Generate a sources jar so VS Code can navigate into Minecraft/Fabric code.
    withSourcesJar()
    val v = JavaVersion.toVersion(javaTarget)
    sourceCompatibility = v
    targetCompatibility = v
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(javaTarget)
}
