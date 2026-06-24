# Contributing to Elytra Everywhere

Thanks for wanting to help! This is a small, focused project: a **Baritone addon**
that unlocks `#elytra` autopilot in every dimension. This guide covers how to set
up a dev environment, the rules of the road, and — most importantly — the
**Meteor-Baritone obfuscation gotchas** that trip up every first mixin change.

For the deeper "why," read the [developer wiki](https://github.com/NyuDev/ElytraEverywhere/wiki/Architecture).

---

## Scope: what belongs here

The addon's entire job is to patch the **Meteor fork** of Baritone at runtime so
`#elytra` works in the **Overworld** and **End**, not just the Nether. Good
contributions:

- fix flight, pathing, or landing bugs outside the Nether,
- improve cross-version reliability (only 1.21.11 is runtime-verified today),
- improve docs, the wiki, or CI.

Out of scope: standalone autopilot engines (a previous `/efly` command was
removed — the fix belongs *inside* Baritone), anything that requires the official
obfuscated Baritone, and features that fight Baritone's own design.

---

## Hard requirement: Meteor Baritone only

This addon hooks the **Meteor** fork (`baritone-meteor`). It **cannot** work with
the official cabaletta Baritone (`baritone-api.jar`, mod id `baritone`).

The official build is fully **ProGuard-obfuscated**: every class is flattened to
`baritone/a.class`, `aa.class`, … and the names change every release, so there is
no stable `baritone.process.ElytraProcess` to mixin into. Meteor keeps class
names and only minifies method/field names — that's the entire reason this addon
is possible. Don't open PRs to "support official Baritone"; it isn't feasible.

---

## Dev environment

**You need JDK 21.** Loom will fail on newer JDKs (e.g. 26).

- **Windows:** use the bundled `build.cmd` — it auto-detects PrismLauncher's
  JDK 21. `build.cmd build`, `build.cmd runClient`.
- **Any OS / IDE:** point Gradle at a JDK 21 via your *user* Gradle properties so
  no machine path is committed:
  `~/.gradle/gradle.properties` → `org.gradle.java.home=/path/to/jdk-21`

> **Windows AF_UNIX note:** the committed `gradle.properties` pins
> `-Djdk.net.unixdomain.tmpdir=C:/Temp`. This machine's JDK fails to open NIO
> selectors without it. On Linux/macOS that arg is harmless; CI overrides
> `org.gradle.jvmargs` with a clean `-Xmx` via a user-level gradle.properties.

### Build & run

```bash
gradlew build        # builds the 1.21.11 jar into build/libs/
gradlew runClient    # launches a dev client with Baritone + the addon to test in game
```

`runClient` is how you actually verify a change — equip an elytra + fireworks,
`#goto <x> <z>` then `#elytra`, and watch the `ElytraEverywhere/Debug` console
output (run `/eedebug` in game to mirror Baritone's chat to the console too).

> The dev runtime needs `dev.babbaj:nether-pathfinder` pinned to **1.4.1** (the
> version Meteor Baritone was compiled against — it calls `newContext(long)`).
> 1.6 builds fine but throws `NoSuchMethodError` the instant `#elytra` starts.
> End users don't need this separately — Meteor bundles the native lib.

### Building other Minecraft versions

`gradlew build` defaults to 1.21.11. Override the coordinates to build another
version (the canonical list is the [CI matrix](.github/workflows/build.yml)):

| MC | yarn | fabric |
|---|---|---|
| 1.21.1  | 1.21.1+build.3  | 0.116.12+1.21.1 |
| 1.21.3  | 1.21.3+build.2  | 0.114.1+1.21.3  |
| 1.21.4  | 1.21.4+build.8  | 0.119.4+1.21.4  |
| 1.21.5  | 1.21.5+build.1  | 0.128.2+1.21.5  |
| 1.21.8  | 1.21.8+build.1  | 0.136.1+1.21.8  |
| 1.21.10 | 1.21.10+build.3 | 0.138.4+1.21.10 |
| 1.21.11 | 1.21.11+build.6 | 0.141.4+1.21.11 |

```bash
gradlew build -Pminecraft_version=1.21.4 -Pyarn_mappings=1.21.4+build.8 -Pfabric_version=0.119.4+1.21.4
```

> **Windows tip:** pass `-P` args through **Git Bash**, not PowerShell. Routing
> `build.cmd ... -Pminecraft_version=1.21.1` through PowerShell→cmd→batch mangles
> the args (Loom ends up seeing version `1`). Use `./gradlew` from Git Bash.

---

## Working with the mixins (read this before touching one)

All the real logic lives in `src/main/java/fr/nyuway/elytraeverywhere/mixin/`.
Because Meteor minifies method and field names, you **cannot** target them by
source name. The rules:

1. **Target by descriptor, not by name.** A minified method named `a` is resolved
   by its signature. `pathTo0(BlockPos, boolean)` is *the* `(BlockPos,boolean)void`
   method; `shouldLandForSafety` is *the* no-arg `boolean` method. Fields named `a`
   are disambiguated by **type** (e.g. the `BetterBlockPos`-typed field is
   `landingSpot`; the first `boolean` field is `goingToLandingSpot`).
   See `ElytraProcessAccessor` for the `@Invoker`/`@Accessor` pattern.

2. **`remap` matters.** Minecraft symbols (yarn-mapped) need `remap = true`;
   Baritone's own minified symbols need `remap = false`. Mixing these up is the
   #1 cause of "target not found" at launch.

3. **Some methods don't exist as methods.** The minifier **inlines** helpers. For
   example `findSafeLandingSpot` and its helpers are inlined into `onTick` — only
   `isSafeBlock(Block)` survives as a callable method. You can't `@Inject` into an
   inlined method; you take over *before* it runs (that's what the End-landing fix
   does).

4. **Use the Baritone sources jar.** It's in your Gradle cache at
   `~/.gradle/caches/modules-2/files-2.1/meteordevelopment/baritone/<ver>-SNAPSHOT/…-sources.jar`.
   Read the real (un-minified) `ElytraProcess.java` there, then map source names
   → minified names with `javap -p` on the non-sources jar.

5. **Mind cross-version API drift.** Methods get renamed between Minecraft
   versions and the multi-version CI will catch it. Known trap: `isGliding()` was
   `isFallFlying()` before 1.21.2 — prefer stable base-class methods like
   `Entity#isOnGround()`. When in doubt, check which version introduced a method.

6. **Octree is 0–128.** The elytra pathfinder models a 128-tall column. Don't
   write to octree y outside `[0, 128)` — out-of-bounds writes segfault the native
   lib (`EXCEPTION_ACCESS_VIOLATION`). Overworld terrain above ~y128 is invisible
   to it by design.

---

## Code conventions

- **Logging, not chat.** All addon output goes to the `ElytraEverywhere/Debug`
  logger (`debug/EELog`). The **one** deliberate chat message is re-printing
  Baritone's `Done :)` on a water landing (because we cancel before Baritone would
  print it). Don't add branded chat spam.
- **Prefix your mixin members.** `@Unique` fields/methods are prefixed
  `elytraeverywhere$…` to avoid clashing with Baritone's own minified members.
- **Keep dimension behaviour gated.** Overworld/End changes must not regress the
  Nether path. Guard on `World.END` / `World.NETHER` where relevant.
- Match the style of the surrounding code (4-space indent, no wildcard imports in
  new files where avoidable).

---

## Commits & pull requests

- Branch off `main`, keep each PR to **one logical change**.
- Write clear commit messages. Conventional-commit prefixes are used here
  (`feat:`, `fix:`, `docs:`, `fix(ci):` …) — follow the existing log style.
- Fill in the PR template. Say **which Minecraft version(s)** you built and
  whether you tested in a `runClient` session and in which dimension.
- CI builds all 7 matrix versions with `fail-fast: false`, so you'll see exactly
  which versions compile. A green 1.21.11 is the minimum bar; if you touched a
  mixin, ideally confirm the versions you can.

---

## Licensing of contributions

This project is licensed under **CC BY-SA 4.0**. By contributing, you agree that
your contributions are licensed under the same terms. Don't paste in code you
don't have the right to relicense.

---

## Reporting bugs & asking questions

- Bugs: use the [bug report form](https://github.com/NyuDev/ElytraEverywhere/issues/new/choose)
  and **include the console log**.
- Questions / ideas: the [wiki](https://github.com/NyuDev/ElytraEverywhere/wiki)
  first, then [Discussions](https://github.com/NyuDev/ElytraEverywhere/discussions).
- Be excellent to each other — see the [Code of Conduct](CODE_OF_CONDUCT.md).
