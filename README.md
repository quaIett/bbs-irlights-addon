<div align="center">

# ✦ IRLights

**Dynamic point & spotlight lighting for Minecraft 1.20.1 · 1.20.4 · 1.21.1**

*A [BBS](https://github.com/mchorse/bbs) addon that brings real-time shadows, volumetric light shafts, and per-light specular to Iris shaderpacks — no shaderpack edits required.*

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1%20%7C%201.20.4%20%7C%201.21.1-62b47a?style=flat-square&logo=minecraft&logoColor=white)](https://minecraft.net)
[![Fabric](https://img.shields.io/badge/Fabric-Loom-dbb967?style=flat-square)](https://fabricmc.net)
[![License](https://img.shields.io/badge/License-MIT-3da639?style=flat-square)](LICENSE)

</div>

---

## What is IRLights?

IRLights is a client-side Fabric mod that extends [BBS](https://github.com/mchorse/bbs) with a full dynamic lighting system. You place **point lights** and **spotlights** in your scene from the BBS editor, and IRLights does the rest:

- bakes per-light shadow maps every frame
- uploads all light data to the GPU via a single SSBO
- injects the matching GLSL into your shaderpack via a one-click patcher

The result is physically-plausible diffuse + specular lighting, hard/soft shadows, and volumetric fog shafts — all driven by BBS animation keyframes.

---

## Features

| Feature | Details |
|---|---|
| **Point lights** | Omnidirectional, cube-map shadows, radius + falloff |
| **Spotlights** | Cone angle, penumbra, atlas shadows |
| **Volumetrics** | Per-light ray-marched shafts with shadow occlusion |
| **Specular** | GGX BRDF, per-light roughness + intensity |
| **Shadow quality** | Four presets — Low / Medium / High / Ultra |
| **SSBO pipeline** | std430 binding 7, up to 2048 lights per frame |
| **Patcher** | Injects GLSL into any supported shaderpack in one click |
| **BBS UI** | Light forms in the BBS editor, live preview |

---

## Supported Shaderpacks

IRLights ships ready-to-use `.irlights` patch files for all packs below.  
Apply them once with the in-game patcher — no manual GLSL edits needed.

| Shaderpack | Author | Patch file |
|---|---|---|
| [Photon](https://modrinth.com/shader/photon-shader) | SixthSurge | `patches/photon.irlights` |
| [Complementary Reimagined](https://modrinth.com/shader/complementary-reimagined) | EminGT | `patches/complementaryreimagined.irlights` |
| [BSL Shaders](https://modrinth.com/shader/bsl-shaders) | CaptTatsu | `patches/bsl.irlights` |
| [Solas](https://modrinth.com/shader/solas-shader) | Septonious | `patches/solas.irlights` |
| [Bliss](https://modrinth.com/shader/bliss-shader) | X0nk | `patches/bliss.irlights` |
| [Eclipse](https://github.com/Merlin1809/Eclipse-Shader) (Bliss edit) | Merlin1809 | `patches/eclipse.irlights` |

---

## How It Works

IRLights has three layers that work together:

```
┌─────────────────────────────────────────────────────┐
│  BBS Editor  →  PointLightForm / SpotlightForm       │  you place lights here
└────────────────────────┬────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────┐
│  Java Addon  →  LightCollector → ShadowBaker         │  bakes maps, fills SSBO
│               → LightBuffer (SSBO binding 7)         │
│               → Iris sampler injection               │
└────────────────────────┬────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────┐
│  Patcher     →  .irlights patches                    │  injects GLSL once
│               → PatchEngine (anchor-based edits)     │
│               → validate / apply / revert UI         │
└────────────────────────┬────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────┐
│  GLSL Inject →  irlite_lights.glsl                   │  runs on GPU every frame
│               → diffuse · specular · shadows · VL    │
└─────────────────────────────────────────────────────┘
```

---

## Installation

> **Requirements:** Minecraft 1.20.1 / 1.20.4 · Fabric Loader ≥ 0.19 · Iris ≥ 1.7 · BBS (latest)
>
> Jars are per-version — grab the one matching your instance: `irlite-1.1+mc1.20.1.jar` or `irlite-1.1+mc1.20.4.jar`.

1. Drop `irlite-*.jar` into your `mods/` folder alongside BBS and Iris.
2. Launch the game once to generate config.
3. Open the **IRLights** category in BBS settings and pick your shaderpack.
4. Click **Apply Patch** — the patcher injects the GLSL automatically.
5. Enable the patched shaderpack in Iris and reload shaders.

---

## Usage

1. Enter a BBS scene and open the **Lights** panel in the editor.
2. Add a **Point Light** or **Spotlight** and adjust radius, color, intensity, and shadow quality.
3. Keyframe any light property for animated sequences.
4. Reload shaders (`F3 + R`) to see the result in real time.

---

## Building from Source

The addon consumes the shared [irl-core](https://github.com/quaIett/irl-core) engine as a
**per-version Maven dependency**, so publish the matching core to mavenLocal first:

```bash
# 1) publish the matching core (from the irl-core repo)
../irl-core/gradlew publishToMavenLocal

# 2) build the addon for a specific Minecraft version
./gradlew build --refresh-dependencies -Pmc=1.20.4   # or -Pmc=1.20.1
# output: build/libs/irlite-1.1+mc1.20.4.jar

# dev-test against a specific version:
./gradlew runClient -Pmc=1.20.4
```

Each Minecraft version produces its own jar (the `+mc<version>` suffix); the old universal
`1.0-obt` jar is gone. `irl-core` is remapped to the target mappings and JiJ-bundled via Loom's
`include`, so the shipped jar is self-contained.

> **Minecraft 1.21.1** lives on the [`port/1.21.1`](https://github.com/quaIett/bbs-irlights-addon/tree/port/1.21.1)
> branch (the final addon line — BBS does not exist past 1.21.1).

---

## License

Released under the [MIT License](LICENSE) — © 2026 qualet.

The shared lighting & patcher engine lives in [irl-core](https://github.com/quaIett/irl-core)
(also MIT). Third-party shaderpacks referenced in `patches/` remain under the licenses of
their respective authors.
