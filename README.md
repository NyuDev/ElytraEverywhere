# Elytra Everywhere

A Fabric **Baritone addon** that unlocks Baritone's `#elytra` autopilot in **every
dimension** — Overworld and End, not just the Nether. It adds no commands of its own; it
patches Baritone at runtime with mixins. Equip an elytra + fireworks and fly with
`#elytra` just like in the Nether.

## Requires Meteor's Baritone — not the official one

This addon hooks the **Meteor fork** of Baritone (mod id `baritone-meteor`), **not** the
official cabaletta Baritone (`baritone-api.jar`, mod id `baritone`). The official build is
ProGuard-obfuscated — its internal classes are renamed every release, so there's nothing
stable to mixin into. Meteor's fork keeps class names, which is what makes the addon
possible. On the wrong Baritone the mod still loads but stays idle and tells you in-game
where to get the right files — it never blocks the game.

## Download

Drop **both** jars into your instance's `mods/`, for the **same** Minecraft version:

| Minecraft | Meteor Baritone (required) | ElytraEverywhere |
|---|---|---|
| **1.21.11** | [baritone-meteor ⬇](https://maven.meteordev.org/snapshots/meteordevelopment/baritone/1.21.11-SNAPSHOT/baritone-1.21.11-20260103.131549-1.jar) | [download ⬇](https://github.com/NyuDev/ElytraEverywhere/releases/download/v0.1.3/elytraeverywhere-0.1.3%2Bmc1.21.11.jar) |
| 1.21.10 | [baritone-meteor ⬇](https://maven.meteordev.org/snapshots/meteordevelopment/baritone/1.21.10-SNAPSHOT/baritone-1.21.10-20251017.214148-1.jar) | [download ⬇](https://github.com/NyuDev/ElytraEverywhere/releases/download/v0.1.3/elytraeverywhere-0.1.3%2Bmc1.21.10.jar) |
| 1.21.8 | [baritone-meteor ⬇](https://maven.meteordev.org/snapshots/meteordevelopment/baritone/1.21.8-SNAPSHOT/baritone-1.21.8-20250801.133826-1.jar) | [download ⬇](https://github.com/NyuDev/ElytraEverywhere/releases/download/v0.1.3/elytraeverywhere-0.1.3%2Bmc1.21.8.jar) |
| 1.21.5 | [baritone-meteor ⬇](https://maven.meteordev.org/snapshots/meteordevelopment/baritone/1.21.5-SNAPSHOT/baritone-1.21.5-20250518.131358-1.jar) | [download ⬇](https://github.com/NyuDev/ElytraEverywhere/releases/download/v0.1.3/elytraeverywhere-0.1.3%2Bmc1.21.5.jar) |
| 1.21.4 | [baritone-meteor ⬇](https://maven.meteordev.org/snapshots/meteordevelopment/baritone/1.21.4-SNAPSHOT/baritone-1.21.4-20250105.184728-1.jar) | [download ⬇](https://github.com/NyuDev/ElytraEverywhere/releases/download/v0.1.3/elytraeverywhere-0.1.3%2Bmc1.21.4.jar) |
| 1.21.3 | [baritone-meteor ⬇](https://maven.meteordev.org/snapshots/meteordevelopment/baritone/1.21.3-SNAPSHOT/baritone-1.21.3-20241117.084726-1.jar) | [download ⬇](https://github.com/NyuDev/ElytraEverywhere/releases/download/v0.1.3/elytraeverywhere-0.1.3%2Bmc1.21.3.jar) |
| 1.21.1 | [baritone-meteor ⬇](https://maven.meteordev.org/snapshots/meteordevelopment/baritone/1.21.1-SNAPSHOT/baritone-1.21.1-20240826.213754-1.jar) | [download ⬇](https://github.com/NyuDev/ElytraEverywhere/releases/download/v0.1.3/elytraeverywhere-0.1.3%2Bmc1.21.1.jar) |

Newest jars are always on the [releases page](https://github.com/NyuDev/ElytraEverywhere/releases/latest).
**Only 1.21.11 is verified in game**; the rest of the 1.21.x line builds but each still
needs its Baritone hooks confirmed. No separate `nether-pathfinder` is needed — Baritone
bundles it. The Baritone links go straight to Meteor's maven (one build per Minecraft
version); if one ever 404s, browse
<https://maven.meteordev.org/#/snapshots/meteordevelopment/baritone>.

## Usage

```
#goto <x> <z>
#elytra
```
Now works in the Overworld and End too.

## How it works

Runtime mixins, all inside Baritone: drop the Nether-only gate, repack the elytra octree
so non-Nether terrain lines up (the system assumes `octreeY == worldY`, which breaks below
y=0 in the Overworld), guard the native pathfinder against out-of-bounds writes and
raytrace crashes, and land on solid ground or — over open ocean — the water surface. The
pathfinder only models y 0–128, so Overworld flight stays below ~y128. All output goes to
the console (`ElytraEverywhere/Debug`); `/eedebug` mirrors Baritone's chat there too.

## Build

JDK 21. `gradlew build` defaults to 1.21.11; build another version by overriding the
coordinates from the [CI matrix](.github/workflows/build.yml):

```
gradlew build -Pminecraft_version=1.21.4 -Pyarn_mappings=1.21.4+build.8 -Pfabric_version=0.119.4+1.21.4
```

MIT licensed.
