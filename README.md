# Sable Light Overhaul

**English** | [简体中文](README.zh-CN.md)

Lighting, cannon effects and distant rendering for Create: Big Cannons on Minecraft 1.21.1 / NeoForge.

SLO adds configurable muzzle smoke, dust, tracers, explosions, flares and lights, with dedicated shader adapters and a shader-free lighting backend. Configure effects, devices and budgets through **F9**. Interface labels, item names and descriptions support English and Simplified Chinese.

**Development build 0.1.716.** Public release numbers and Minecraft's internal version can differ.

## Features

- **Cannon smoke and dust:** hot and cold smoke layers, gradual expansion and edge erosion, charge-scaled big-cannon smoke, tank muzzle smoke and ground dust, and lightweight autocannon muzzle effects. Separate configurable smoke budgets evict the oldest effects when full.
- **Tracers and explosions:** short luminous projectile trails with distant rendering, scene/cloud/smoke occlusion in supported pipelines, impact termination and network smoothing; custom flak smoke, sparks and shockwaves.
- **Illumination and signal shells:** CBC shells with illumination flares, RGB signal flares and an altitude fuze. Burning lifetime and brightness can be stored per shell; flares descend slowly and remain visible at long range.
- **Lighting devices:** articulated spotlights, flashlights and hidden point lights, with configurable beam, glow and offsets. Ships use ship-relative transforms where supported.
- **Sable Hostile integration:** per-turret effect controls, distant projectiles and tracers, cannon audio and turret rotation sounds. Compatible Sable Healthbar versions enable mathematical trajectory integration and native-entity handoff.
- **Distant entity markers:** dedicated-server entities beyond client view distance can appear as occluded colored bounds, names or available portraits, with optional health display. Range is bounded by server view distance and the configured request range; this does not load extra terrain or provide full entity simulation.
- **Global wind:** fixed or slowly varying wind drives effects that support the wind field.

## Explosion visuals

- **Ground bursts** use a compact fireball that rises, expands and erodes into smoke, a ground-level dust fan and configurable outward dust columns. **Buried bursts** use the detected outlet and a smaller fireball for shallow vents. **Airbursts** use a smaller fireball and fine sparks in all directions. Missile interceptions have their own visual path.
- The custom impact particle set starts at explosion power 6. MW medium HE uses 6 and CBC standard HE uses 8 for block damage. Vanilla TNT uses its own particles and can still draw the SLO mesh shockwave.
- In **F9 → CBC HE visuals**, adjust ground fireball growth and rise, dust-column shape, and the optional height scaling by blocks actually destroyed. Smoke quality uses the existing muzzle-smoke slider without shaders or with patched Photon Advanced.
- Photon updates for this build include the cached cold-smoke lighting path and the explosion smoke material. Use the patch that matches the mod build. A thin Photon cloud layer has intermittently shown through smoke and is still awaiting a reproducible scene.

## Requirements and installation

- Minecraft **1.21.1**, Java **21**, NeoForge **21.1.228 or newer**, and Create: Big Cannons **5.11.7** with its required dependencies.
- Install **LDLib2 2.2.17 or newer** for the F9 configuration interface. Integrations such as Voxy, Iris, Sable Hostile and Sable Healthbar require compatible versions; they are not bundled.
- Install the SLO JAR in `mods`, replacing the older SLO JAR. Do not keep multiple versions together.
- For server-synchronized features, install a matching SLO version and required dependencies on the dedicated server and clients.
- Select the appropriate lighting backend in **F9 → Lights → Lighting backend**.

| Backend | Intended use |
| --- | --- |
| **SLO Only** | No shader pack. SLO lighting/effects and supported Voxy distant lighting/occlusion. Volumetric beams are disabled; refraction defaults off. Surface lighting is an approximation, not a reconstruction of vanilla's lightmap. |
| **Screen-space approximation** | Approximate lighting fallback, including shader packs without a dedicated SLO adapter. Compatibility and occlusion quality depend on the active pipeline. |
| **Advanced** | A supported shader pack with its matching SLO patch. |
| **Off** | Disable the lighting backend for troubleshooting. |

## Shader compatibility

Download originals from their authors, then use the matching **Windows x64 offline patcher** from the [releases](https://github.com/TeamGuy10086/sable-light-overhaul/releases). SLO does not distribute complete shader packs.

| Patcher | Supported original |
| --- | --- |
| Photon | [voxy-support snapshot `ba11ae2`](https://github.com/sixthsurge/photon/archive/ba11ae2ad9d506c3a2fd9ca10c317be259b94d69.zip), by [SixthSurge](https://modrinth.com/shader/photon-shader) |
| Complementary | [Unbound r5.8.1](https://www.complementary.dev/shaders/), optionally with [Euphoria Patches 1.9.3](https://www.euphoriapatches.com/) installed by its own installer |
| Mellow | [v3.5](https://modrinth.com/shader/mellow/version/3.5), by TheCMK |

Select an original ZIP or extracted directory and a new output ZIP. The patcher checks source fingerprints, rejects unsupported or modified inputs, and verifies the result. Renaming a different version will not bypass validation. The input and existing outputs are never overwritten. See the [patcher guide](tools/patcher/README.md) for CLI use and rebuilding.

Select the generated pack in Iris and use **Advanced**. A successful patch validates files, not GPU compilation. These adapters are not official upstream releases. Mellow's SLO light shadows default off because of remaining artifacts. Photon and Complementary have separate tuning; settings are not assumed interchangeable.

## Building

Use Java 21 and the Gradle wrapper. Third-party mod binaries are not included. Supply the compile/test dependencies described in [libs/README.txt](libs/README.txt); optional integration contracts are also checked by the test suite.

Some shader contract tests need a local original Photon snapshot and its generated patched files. Prepare these without committing upstream sources:

```powershell
python tools/prepare-shader-tests.py path/to/photon-original.zip
./gradlew.bat test jar
```

On Windows, `build` can also deploy to a local test instance **only when explicitly configured** with `SLO_INSTANCE` or an ignored `local-deploy.json` containing an `instance` path. It skips deployment while Minecraft is running. The repository does not include an author's instance configuration.

Internal investigation notes, deployment records, original shader archives and generated complete packs are maintained locally and excluded from this repository.

## Bug reports

Use [GitHub Issues](https://github.com/TeamGuy10086/sable-light-overhaul/issues). Include SLO's internal version, Minecraft/NeoForge/Iris/Voxy versions, shader name and exact version, lighting backend, reproduction steps, and relevant logs or screenshots. State whether it reproduces without shaders and in single-player or on a dedicated server. Remove credentials and private server addresses from logs before posting.

## License and credits

SLO's original mod and patcher code use the [MIT License](LICENSE). Third-party shader code, derived patches and runtime components retain their own licenses; see [patcher license notices](tools/patcher/licenses). Shader downloads belong to their original authors. A patcher does not grant permission to redistribute complete modified packs.
