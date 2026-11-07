# Ice Age Yantra Mod (`ice_age_yantra`), Forge 1.20.1

Freeze the whole world with five comedic machines (Yantras), built for an escalating "MrBeast / ProBoiz"
style video. Every Yantra freezes blocks for you and pauses once you can afford the next one, so the video paces
itself from 0 to **10,000,000 BLOCKS FROZEN**. Every Yantra stays reusable: use it again any time for
another run. The screen stays clean: no banners and no chat spam, only the top-left counter and Gotiya's
lines of dialogue.

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
   **Yantra Progression Shop**. The live counter in the top-left corner shows
   `[current] / 10,000,000` with rolling odometer digits, a ding, one floating "+N" and the live multiplier.
2. **Buy** a Yantra in the shop GUI, or physically in the **Frozen Showroom**: walk through an Ice Portal
   (sneak + right-click a block with the Shop Portal to conjure one, or use `/iceage setup`). There is no
   loading screen. You arrive on the solid arrival pad (X 0.5, Y 65, Z 0.5), and walking back out puts you
   on the ground in front of your portal. Right-click a pedestal to buy the Yantra floating above it.
3. Each run of a Yantra pauses when the next one is unlocked. Use it again for another run:

| Tier | Yantra | Price | Stops at | What happens |
|---|---|---|---|---|
| 1 | Desi Baraf Gola Machine | free | 25 | Right-click twice to crank. **Gotiya** storms over with an empty bowl ("Gotiya: Tum is world ko barf me nahi jama sakte! Main ye nahi hone dunga!"), gets blasted in the face with crushed ice and runs off crying. The handle snaps off with a cartoon *POP* and the wheel shoots crushed ice at the 25 surrounding blocks. Crank again for another run. |
| 2 | Farrata Table Fan | 25 | 150 | Right-click for SPEED 3: roaring frost wind freezes a 15-block cone and gently nudges you back. Gotiya walks over and sits right in front of the fan, shivering. Hit or left-click him: he screams and runs away. |
| 3 | Retro Fridge | 150 | 1,000 | Right-click to open: freezing fog, launched ice blocks, and Gotiya walks over and sits inside, shivering with relief. Left-click him or the fridge: he screams, jumps out and runs. The door closes at the end of a run and opens again on the next click. |
| 4 | Split AC + Turbo Remote | 1,000 | 50,000 | Right-click the AC with the remote to pair, then right-click anywhere: the remote LCD shows **16°C TURBO** and a blizzard packs a 40x40 field into ice (x16 points). At the end of a run the remote shows **STANDBY**; press again for another run. |
| 5 | Musical Ice Cream Van | 50,000 | 10,000,000 | Place it, right-click to drive (W/S throttle, A/D steer, F5 for a chase camera). The 1.4x-size van plays its jingle, fires packed-ice blocks left and right at high speed, lays a 10-wide packed-ice highway and flash-freezes everything around it. A growing combo surges the counter to 10,000,000. **Finale:** Gotiya staggers in carrying a giant industrial hairdryer with a glowing red-hot coil and blasts flames at the frozen world... which never melts. |

**Frozen blocks never melt.** Yantras only ever produce **Permafrost Ice** (a custom slippery ice with no
melting logic), packed ice or blue ice. Not even Gotiya's hairdryer can melt them. None of them melt next to torches, lanterns, fire or in sunlight.

### Commands

| Command | Permission | Effect |
|---|---|---|
| `/iceage setup` | op | Flattens a 41x41 area around you, gives the Shop Portal (slot 0), the Turbo Remote and testing spawn eggs (Chill Villager, Ice Cream Van), and builds an Ice Portal behind you. |
| `/iceage shop` | everyone | Opens the shop GUI. |
| `/iceage showroom` | everyone | Teleports to or from the Frozen Showroom. |
| `/iceage counter reset` | op | Sets your counter to 0. |
| `/iceage counter set <amount>` | op | Sets your counter to any value. |
| `/iceage counter speed <multiplier>` | op | Multiplies every gain (0.1 to 1000). The HUD's x label shows it. |
| `/iceage gotiya <tier_number>` | op | Summons Gotiya at the nearest Yantra of that tier (1 to 4), or the hairdryer finale (5). |
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


---

## Fabric 1.20.1 version (port)

`build_fabric_mod.py` / `ice_age_yantra_fabric/` is a **port of this mod to Fabric** (Loom 1.7.4, yarn 1.20.1+build.10,
Fabric API 0.92.2, loader 0.14.22+). It uses the **same models and textures as the Forge version**: the baked OBJ rigs
(gola machine, table fan, retro fridge, split AC, ice cream van, crystal portal, carved pedestal, hairdryer), the 512px
textures, the HUD counter bar, particles and sounds. Fabric has no OBJ loader, so the port ships its own
(`client/obj/ObjModel.java`, reads the same `.obj/.mtl` incl. the baked AO vertex colours and emissive materials).
**No mixins**, so it does not conflict with Sodium, Lithium, Iris or Replay Mod.

```bash
python3 build_fabric_mod.py          # writes ./ice_age_yantra_fabric (assets are copied from ./ice_age_yantra)
cd ice_age_yantra_fabric && ./gradlew build
```

The jar is `ice_age_yantra_fabric/build/libs/ice_age_yantra-1.0.0.jar` (a built copy is in `fabric_release/`). Put it
in `mods/` with Fabric API.

What differs from the Forge version (per the Fabric spec):

* **No custom dimension.** The Ice Shop is a 15x8x15 room in the Overworld at X 10000 / Y 120 / Z 10000
  (packed ice, blue ice, glowstone, glass display plates under the carved pedestals).
* **Village Portal** (item given on first join, or `/iceage portal`): walking in saves your position as NBT
  `IcePortalReturnPos` and teleports you to (10000.5, 121.0, 10000.5) with fall distance 0. The **Exit Portal** arch in the
  shop returns you to solid ground at the saved position. Same dimension, so no loading screen.
* **Yantras** (Gola, Fan, Fridge, AC): right-click = 20-30 flying ice blocks + an instant packed-ice freeze around the
  machine, reusable forever. Pedestals in the shop give the item once your counter reaches the tier's price.
* **Ice Cream Van** is a real custom `Entity` (no pig): WASD driving, driver seated in the cab, 1.4x scale, lays a
  packed-ice road, fires snowballs and spinning ice particles left and right.
* **Gotiya** (custom villager, bold aqua name, panic speed 0.5): driving the van triggers the climax: he appears ahead
  carrying the giant hairdryer and melts the frozen road back to water/ground with flames and hot air.
* HUD: the same top-left counter bar as Forge, one floating "+N", no banners, no chat spam.
* Commands (op): `/iceage portal | shop | setup | rebuildshop | gotiya | counter reset|set <n>|speed <x>`.
