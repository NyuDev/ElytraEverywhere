plugins {
    id("dev.kikugie.stonecutter")
    id("fabric-loom") version "1.16-SNAPSHOT" apply false
}

stonecutter active "1.21.11" /* [SC] DO NOT EDIT */

// Build every version's jar at once: `gradlew chiseledBuild`.
stonecutter tasks {
    order("build")
}
