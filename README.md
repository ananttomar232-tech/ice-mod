# Ice Age Yantra Mod (`ice_age_yantra`), Forge 1.20.1

Freeze the whole world with five comedic machines (Yantras), built for an escalating "MrBeast / ProBoiz"
style video. Every Yantra freezes blocks for you and **switches itself off the moment you can afford the
next one**, so the video paces itself from 0 to **10,000,000 BLOCKS FROZEN**.

The single file `build_ice_age_mod.py` generates the complete Forge MDK project: Java sources, blockstates,
OBJ rigs converted from the five 3D models in `source_models/` (with baked ambient-occlusion shading),
94 procedural 512px+ textures, 20 synthesized sounds, particles, the Frozen Showroom dimension and the
Gradle wrapper. A generated copy is already committed in `ice_age_yantra/`, so you can build straight away.

## Build

Requirements: a JDK 17+ on your PATH. Gradle downloads a Java 17 toolchain automatically if needed.
Regenerating the project also needs Python 3.8+ with `numpy` and `Pillow` (10.1 or newer recommended).

```bash
cd ice_age_yantra
```

```bash
./gradlew build
```

The mod jar is `ice_age_yantra/build/libs/ice_age_yantra-1.0.0.jar`. Put it into the `mods` folder of a
Minecraft **1.20.1 Forge (47.x)** install.

To regenerate the project from the generator instead (for example after editing it):

```bash
pip install numpy pillow
```

```bash
python3 build_ice_age_mod.py --clean
```

`--out DIR` changes the output folder (default `./ice_age_yantra`), and `--models DIR` points at the folder
with the five model zips (default `./source_models`). A full generation takes about 1.5 minutes.

> When you create or load a world, Minecraft 1.20.1 shows "Worlds using Experimental Settings are not
> supported" once per world, because the mod adds a dimension (the Frozen Showroom). Click **Proceed**.
> This is normal for dimension mods.

## How to play / film it

1. **Spawn.** You get the **Yantra Shop Portal** in hotbar slot 0. Right-click it anywhere to open the 3D
   **Yantra Progression Shop**. The live HUD at the top centre shows
   `BLOCKS FROZEN: [current] / 10,000,000` with rolling odometer digits, a ding and "+N" pop-ups.
2. **Buy** a Yantra in the shop GUI, or physically in the **Frozen Showroom**: walk through an Ice Portal
   (sneak + right-click a block with the Shop Portal to conjure one, or use `/iceage setup`). Right-click a
   pedestal to buy the Yantra floating above it.
3. Each Yantra stops by itself exactly when the next one is unlocked:

| Tier | Yantra | Price | Stops at | What happens |
|---|---|---|---|---|
| 1 | Desi Baraf Gola Machine | free | 25 | Right-click twice to crank. The handle snaps off with a cartoon *POP*, the wheel keeps spinning on its own and shoots crushed ice at the 25 surrounding blocks (slippery ice). It sputters smoke and squeaks when done. |
| 2 | Farrata Table Fan | 25 | 150 | Right-click for SPEED 3: roaring frost wind freezes a 15-block cone and gently nudges you 2-3 blocks back. |
| 3 | Retro Fridge | 150 | 1,000 | Right-click to open: freezing fog, launched ice blocks, and a sweaty villager walks over and sits inside, shivering with relief. Left-click the villager or the fridge: he screams, jumps out and sprints away. At 1,000 the door slams and locks. |
| 4 | Split AC + Turbo Remote | 1,000 | 50,000 | Right-click the AC with the remote to pair, then right-click anywhere: the remote LCD shows **16°C TURBO** and a blizzard packs a 40x40 field into ice (x16 points). At 50,000 the remote beeps **STANDBY**. |
| 5 | Musical Ice Cream Van | 50,000 | 10,000,000 | Place it, right-click to drive (W/S throttle, A/D steer, F5 for a chase camera). It plays its jingle, lays a 10-wide blue-ice highway, ploughs through anything in front of it and flash-freezes houses, trees and terrain. A growing combo surges the counter to 10,000,000 for the finale. Sneak-punch to pick it up. |

**Frozen blocks never melt.** Yantras only ever produce **Permafrost Ice** (a custom slippery ice with no
melting logic), packed ice or blue ice. None of them melt next to torches, lanterns, fire or in sunlight.

### Commands

| Command | Permission | Effect |
|---|---|---|
| `/iceage setup` | op | Flattens a 41x41 area around you, gives the Shop Portal (slot 0), the Turbo Remote and testing spawn eggs (Chill Villager, Ice Cream Van), and builds an Ice Portal behind you. |
| `/iceage shop` | everyone | Opens the shop GUI. |
| `/iceage showroom` | everyone | Teleports to or from the Frozen Showroom. |
| `/iceage points set\|add <amount> [players]` | op | Sets or adds frozen blocks (handy for re-filming a stage). |
| `/iceage reset [players]` | op | Resets progress to 0. |
| `/iceage rebuildshowroom` | op | Rebuilds the showroom hall. |

### Config

`config/ice_age_yantra-common.toml` tunes pacing: cranks before the handle snaps, fan cone length and nudge
strength, fridge freeze radius and launch rate, AC area and multiplier, and van speed, points per block,
combo, freeze radius and highway width.

## Credits

3D models (CC-BY-4.0; adapted: re-rigged, re-textured, ambient-occlusion baked):

- "Ice shaver" by VeinSyct: https://sketchfab.com/3d-models/ice-shaver-81f8e0fb483e4d3b8eaf5d61339b4204
- "80s Desk Fan Low Poly" by lucasalic99: https://sketchfab.com/3d-models/80s-desk-fan-low-poly-612736f7659d451e811c967c8fea370b
- "Damaged retro refrigerator low poly" by ViktorianWios: https://sketchfab.com/3d-models/damaged-retro-refrigerator-low-poly-48c2a8bfe5cf43178e91f791bce4fb89
- "External Air-Conditioner Unit" by Luuluubell: https://sketchfab.com/3d-models/external-air-conditioner-unit-885b4a83ad1a4db792715ba24c7a5d57
- "Low Poly Ice Cream Truck." by its_sagar_bro: https://sketchfab.com/3d-models/low-poly-ice-cream-truck-37ed6bca176049a0bfb5f88e15b612c4

Licence: Creative Commons Attribution 4.0, http://creativecommons.org/licenses/by/4.0/. Keep these credits
in your video description when you show the models.

All textures, the procedural models (ice portal, pedestal, chandelier, remote, shop orb, giant cone), the
sounds and the van jingle are original and generated by `build_ice_age_mod.py`.
