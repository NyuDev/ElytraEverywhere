# Elytra Everywhere

A small **Baritone addon** (Fabric, Minecraft 1.21.x) that makes Baritone's own
`#elytra` autopilot work in **every dimension** — Overworld and End, not just the
Nether — instead of locking up. It is not a fork and adds no commands of its own:
it patches Baritone at runtime with Mixins. Drop it next to Baritone and use
`#elytra` as usual.

> [!IMPORTANT]
> This addon hooks the **Meteor fork of Baritone** (`baritone-meteor`), **not** the
> official Baritone from cabaletta. The official build runs its internals through
> ProGuard, renaming every class to `a`, `aa`, `ab`… so there is no stable
> `baritone.process.ElytraProcess` to mixin into — and those names change every
> release. The Meteor fork keeps class names (it only minifies *method* names),
> which is exactly what makes a maintainable mixin addon possible. Install
> [Meteor's Baritone](https://maven.meteordev.org/) — not `baritone-api.jar`.

## The two problems it fixes

Baritone refuses, and then can't path, elytra outside the Nether. This addon fixes
both, entirely inside Baritone:

### 1. The dimension lock (it refuses to start)

| Location | Original check |
|---|---|
| `ElytraProcess#pathTo0` (minified `a(BlockPos,boolean)`) | `getRegistryKey() != World.NETHER` → silent `return` |
| `ElytraCommand#execute` | `getRegistryKey() != World.NETHER` → throws *"Only works in the nether"* |

[`ElytraProcessMixin`](src/main/java/fr/nyuway/elytraeverywhere/mixin/ElytraProcessMixin.java)
and [`ElytraCommandMixin`](src/main/java/fr/nyuway/elytraeverywhere/mixin/ElytraCommandMixin.java)
redirect the one dimension lookup each gate performs to report `NETHER`.

### 2. The pathfinder fed the wrong terrain (it spams "Failed to compute next segment")

Baritone's elytra octree is 128 tall and the whole system assumes `octreeY == worldY`.
`NetherPathfinderContext#writeChunkData` packs chunk **sections 0..7** into octree
y 0..127. In the Nether section 0 is world y 0 — correct. In the **Overworld**
section 0 is world y **-64**, so it loads the underground while you fly at y 64+, the
pathfinder thinks you're buried in rock, finds no path, and floods chat until the
client freezes.

[`NetherPathfinderContextMixin`](src/main/java/fr/nyuway/elytraeverywhere/mixin/NetherPathfinderContextMixin.java)
redirects the section lookup to hand back the 8 sections covering world y `[0,128)`
for the current dimension, so `octreeY == worldY` holds everywhere. Nether/End are
already aligned and pass through untouched.

The same mixin also fixes a hard crash: `queueBlockUpdate` writes each changed
block into the native octree guarded only against `y >= 128`. The Nether never has
blocks below y=0, so Baritone never added a lower guard — but Overworld deepslate
at y<0 hands the native lib a negative offset and **segfaults the JVM**
(`EXCEPTION_ACCESS_VIOLATION`). A cancellable `@Inject` drops block updates outside
`[0,128)`, so both octree write paths stay in bounds.

[`PredictTerrainPolicy`](src/main/java/fr/nyuway/elytraeverywhere/runtime/PredictTerrainPolicy.java)
keeps `elytraPredictTerrain` off outside the Nether so pathing uses the real (now
correctly packed) chunks instead of seed-generated Nether terrain.

### 3. The auto-landing froze the game on arrival

When a path completes, Baritone hunts for a landing spot accepting only
`NETHERRACK`/`GRAVEL`. Those don't exist in the Overworld, so the inlined search
scans every loaded air block (millions) and hangs the client thread the instant
you arrive — looks like a crash, but it's a freeze. The `isSafeBlock` `@Inject` in
`ElytraProcessMixin` accepts ordinary solid ground (minus obvious hazards) outside
the Nether, so the search ends at the ground below you and **lands** instead.

## Logging

The addon never writes to chat. All of its own decisions (landing search, water
landing, goal clamping, native-input guards) are logged verbosely to the
console/log under the `ElytraEverywhere/Debug` logger, always on. `/eedebug`
additionally mirrors in-game chat (Baritone's status messages) into the console,
handy for correlating Baritone's output with the addon's during a flight.

## Limitation

The octree is 128 tall, so the pathfinder only sees world y `[0,128)`. Elytra travel
in the Overworld therefore happens **below y≈128** (and Baritone already clamps elytra
goals to that band). Terrain above y=128 (tall mountains) is invisible to it — fly
over flatter ground for best results. The End fits entirely in the band.

## Build & run

Mirrors the StasisBot toolchain (JDK 21 via PrismLauncher; the AF_UNIX tmpdir fix).

```bat
build.cmd build       :: build the mod jar -> build/libs/
build.cmd runClient   :: dev client with Baritone + this addon
```

Build defaults to 1.21.11. To build another version, override the three coordinates
(same values the [CI matrix](.github/workflows/build.yml) uses):

```bat
gradlew build -Pminecraft_version=1.21.4 -Pyarn_mappings=1.21.4+build.8 -Pfabric_version=0.119.4+1.21.4
```

## Supported Minecraft versions

Built and released for the **1.21.x** line (Meteor publishes Baritone for each):
`1.21.1`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.10`, `1.21.11`. Each gets
its own jar (`elytraeverywhere-<ver>+mc<mc>.jar`) from the CI matrix.

**Only 1.21.11 is verified in game.** The other versions compile, but because the
mixins target Baritone's *minified* method names — which can differ from one Meteor
build to the next — each non-11 version still needs its hooks confirmed before it can
be trusted. Treat them as best-effort until tested.

## Install on your real instance

Drop **two** jars into your instance's `mods/` folder — **Meteor's Baritone** (the
`baritone-meteor` mod, *not* the official `baritone-api.jar`) **and** the matching
ElytraEverywhere jar for the **same** Minecraft version:

| Minecraft | Meteor Baritone (required) | ElytraEverywhere |
|---|---|---|
| **1.21.11** | [baritone-meteor ⬇](https://maven.meteordev.org/snapshots/meteordevelopment/baritone/1.21.11-SNAPSHOT/baritone-1.21.11-20260103.131549-1.jar) | [download ⬇](https://github.com/NyuDev/ElytraEverywhere/releases/download/v0.1.1/elytraeverywhere-0.1.1%2Bmc1.21.11.jar) |
| 1.21.10 | [baritone-meteor ⬇](https://maven.meteordev.org/snapshots/meteordevelopment/baritone/1.21.10-SNAPSHOT/baritone-1.21.10-20251017.214148-1.jar) | [download ⬇](https://github.com/NyuDev/ElytraEverywhere/releases/download/v0.1.1/elytraeverywhere-0.1.1%2Bmc1.21.10.jar) |
| 1.21.8 | [baritone-meteor ⬇](https://maven.meteordev.org/snapshots/meteordevelopment/baritone/1.21.8-SNAPSHOT/baritone-1.21.8-20250801.133826-1.jar) | [download ⬇](https://github.com/NyuDev/ElytraEverywhere/releases/download/v0.1.1/elytraeverywhere-0.1.1%2Bmc1.21.8.jar) |
| 1.21.5 | [baritone-meteor ⬇](https://maven.meteordev.org/snapshots/meteordevelopment/baritone/1.21.5-SNAPSHOT/baritone-1.21.5-20250518.131358-1.jar) | [download ⬇](https://github.com/NyuDev/ElytraEverywhere/releases/download/v0.1.1/elytraeverywhere-0.1.1%2Bmc1.21.5.jar) |
| 1.21.4 | [baritone-meteor ⬇](https://maven.meteordev.org/snapshots/meteordevelopment/baritone/1.21.4-SNAPSHOT/baritone-1.21.4-20250105.184728-1.jar) | [download ⬇](https://github.com/NyuDev/ElytraEverywhere/releases/download/v0.1.1/elytraeverywhere-0.1.1%2Bmc1.21.4.jar) |
| 1.21.3 | [baritone-meteor ⬇](https://maven.meteordev.org/snapshots/meteordevelopment/baritone/1.21.3-SNAPSHOT/baritone-1.21.3-20241117.084726-1.jar) | [download ⬇](https://github.com/NyuDev/ElytraEverywhere/releases/download/v0.1.1/elytraeverywhere-0.1.1%2Bmc1.21.3.jar) |
| 1.21.1 | [baritone-meteor ⬇](https://maven.meteordev.org/snapshots/meteordevelopment/baritone/1.21.1-SNAPSHOT/baritone-1.21.1-20240826.213754-1.jar) | [download ⬇](https://github.com/NyuDev/ElytraEverywhere/releases/download/v0.1.1/elytraeverywhere-0.1.1%2Bmc1.21.1.jar) |

The Baritone links point straight at Meteor's maven — one build per Minecraft version
(`mod id baritone-meteor`, with `nether-pathfinder` bundled inside). Meteor doesn't put
these on a normal download page, which is why the per-version direct links are listed
here. If one ever 404s (a re-published snapshot), browse
<https://maven.meteordev.org/#/snapshots/meteordevelopment/baritone> and grab the `.jar`
under `<your-version>-SNAPSHOT/`.

No separate `nether-pathfinder` needed — Baritone bundles it. If you install the wrong
Baritone (or none), Fabric stops with *"requires baritone-meteor, which is missing"* —
that guard is intentional.

## Use in-game

Equip an elytra + fireworks, then exactly like in the Nether:

```
#goto <x> <z>
#elytra
```
Now it works in the Overworld and End too.
