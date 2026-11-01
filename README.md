<p align="center">
  <img src="docs/header.png" alt="Skybreaker: Orbital Arsenal" width="720">
</p>

<h1 align="center">Skybreaker: Orbital Arsenal</h1>

<p align="center">
  <b>Call down the sky.</b> Eight orbital strike remotes for Minecraft (Fabric) with cinematic cameras, slow-motion impacts, custom sounds and a fully configurable arsenal.
</p>

<p align="center">
  <a href="https://github.com/KiegsGit/SkyBreaker---Beta-Testing-/releases/latest"><img alt="Download" src="https://img.shields.io/badge/Download-latest%20jar-2f6bff?style=for-the-badge"></a>
  <a href="https://fabricmc.net/use/"><img alt="Minecraft" src="https://img.shields.io/badge/Minecraft-26.3-3fae49?style=for-the-badge"></a>
  <a href="https://fabricmc.net/use/"><img alt="Fabric" src="https://img.shields.io/badge/Fabric-required-dbd0b4?style=for-the-badge"></a>
  <a href="https://github.com/KiegsGit/SkyBreaker---Beta-Testing-/blob/main/LICENSE"><img alt="License" src="https://img.shields.io/badge/License-MIT-lightgrey?style=for-the-badge"></a>
</p>

---

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for **Minecraft 26.3** (Java 25).
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) and put it in your `mods` folder.
3. Download the newest `skybreaker-<number>.jar` from the [latest release](https://github.com/KiegsGit/SkyBreaker---Beta-Testing-/releases/latest) and drop it in the same `mods` folder.
4. Launch the game. The config is generated at `config/skybreaker.json`.

> Works in singleplayer and on servers. Install it on the server **and** on every client that should see the effects.

## How to use

1. Craft or `/give` yourself a remote (see below).
2. Hold it and **aim at a block** (up to 128 blocks away). A targeting reticle shows where it will land.
3. **Right-click** to fire. A short countdown plays, then the strike lands.
4. The shot uses **Orbital Charges**, and the remote goes on cooldown. Hold a chip in your off-hand for bonuses.

The **Tracking Strike** is different: aim at a **mob or player** to lock on. It follows them and strikes where they end up. There is no cutscene for it: you keep full control and see the lock ring, a beam from the sky and a light trail stick to the target in the real world until it locks and strikes. Other remotes only take over the screen for a short orbital intro, then the countdown runs over the live world.

## The arsenal

Every shot burns **Orbital Charges** (creative is exempt). Costs and cooldowns are defaults and can be changed in the config.

| Item | Tier | What it does | Cooldown | Charges |
|---|---|---|---|---|
| **EMP Pulse** | I | Freezes mobs, blacks out lamps and torches, wrecks redstone. | 25s | 1 |
| **Tracking Strike** | I | Locks onto a mob or player and strikes where they end up. | 20s | 1 |
| **Orbital Railgun** | II | A tungsten rod dropped from orbit. Pinpoint hit, huge crater. | 20s | 2 |
| **Meteor Barrage** | II | Rains meteors over a wide area. | 40s | 3 |
| **Orbital Napalm** | II | Thermite rain, then a field of fire that burns everything inside. | 40s | 3 |
| **Satellite Laser** | III | A beam burns a path for seconds, then ends in a massive detonation. | 30s | 4 |
| **Singularity Cannon** | III | Opens a black hole that drags in blocks and mobs, then collapses in a giant blast. | 60s | 6 |
| **Tactical Nuke** | III | Long countdown, giant blast, mushroom cloud. **Stay far away.** | 120s | 10 |

All items are in the **Skybreaker: Orbital Arsenal** creative tab.

## Crafting progression

| Item | Recipe (shaped, 3x3) |
|---|---|
| **Uplink Circuit** | 4 Gold Ingot, 4 Redstone, 1 Ender Pearl (pearl in the centre) |
| **Orbital Core** | 4 Uplink Circuit (corners), 2 Diamond Block (top/bottom centre), 2 Netherite Ingot (sides), Ender Eye (centre) |
| **Orbital Charge** x2 | 4 Gunpowder (corners), 2 Blaze Powder (top/bottom centre), 2 Amethyst Shard (sides), Uplink Circuit (centre) |
| **Targeting Chip** | 4 Redstone, 4 Diamond, Uplink Circuit (centre) |
| **Overcharge Chip** | 4 Netherite Scrap, 4 Blaze Rod, Targeting Chip (centre) |
| **Efficiency Chip** | 4 Lapis Block, 4 Emerald, Targeting Chip (centre) |
| **EMP Pulse** | 4 Redstone Block, 2 Uplink Circuit, 2 Lightning Rod, Netherite Ingot (centre) |
| **Tracking Strike** | 4 Ender Eye, 2 Uplink Circuit, 2 Spyglass, Netherite Ingot (centre) |
| **Orbital Railgun** | 4 Netherite Ingot, 2 Lightning Rod, 2 Beacon, **Orbital Core** (centre) |
| **Meteor Barrage** | 4 Magma Block, 2 Netherite Ingot, 2 Fire Charge, **Orbital Core** (centre) |
| **Orbital Napalm** | 4 Fire Charge, 4 Lava Bucket, **Orbital Core** (centre) |
| **Satellite Laser** | 4 End Crystal, 2 Orbital Core, Nether Star, Netherite Block, **Orbital Railgun** (centre) |
| **Singularity Cannon** | 4 Crying Obsidian, Echo Shard, Ender Eye, Nether Star, Orbital Core, **Meteor Barrage** (centre) |
| **Tactical Nuke** | 2 TNT, Beacon, 2 Orbital Core, 2 Netherite Block, Nether Star, **Orbital Napalm** (centre) |

**Chips** go in your **off-hand** (one at a time): Targeting = 25% faster recharge, Overcharge = +35% damage, Efficiency = 40% chance a shot uses no charge.

## Owner controls

**Who is the "owner"?** Not an operator. On singleplayer/LAN it is the player hosting the world. On a dedicated server it is the console plus any UUID in `owners`. Make yourself owner once from the **server console**: `skybreaker owner add <your name>` (you must be online).

Press **Esc** and click **Sky Breaker Config** (top-left). Everyone sees the button, but non-owners are warned and logged. It opens **Config** (settings pages) and **Commands** (one-click actions).

| Command (owner only) | What it does |
|---|---|
| `/skybreaker reload` | Re-read `config/skybreaker.json` |
| `/skybreaker status` | Active strikes, permission mode, zones |
| `/skybreaker cancelall` / `clearcooldowns` | Stop all strikes / reset cooldowns |
| `/skybreaker set <key> <value>` | Change a setting (same list as the Config screen) |
| `/skybreaker allow add\|remove\|list <player>` | Allowlist (used when `firePermission` is `ALLOWLIST`) |
| `/skybreaker zone add <radius>` / `list` / `remove <n>` | Protected zones around where you stand (use it at spawn) |
| `/skybreaker dimension ban\|unban\|list <id>` | Ban strikes in a dimension |
| `/skybreaker owner add\|remove\|list <player>` | **Console only** |

Get items with `/give @s skybreaker:<item>` (needs cheats / operator), for example `skybreaker:orbital_railgun`, `skybreaker:orbital_charge`, `skybreaker:orbital_core`. `/recipe give @s *` unlocks every recipe.

## Configuration

Use the in-game **Sky Breaker Config** screen, or edit `config/skybreaker.json` and run `/skybreaker reload`. Missing keys are filled in with defaults. Every weapon has its own cooldown, countdown, radius and damage.

| Option | Default | What it does |
|---|---|---|
| `terrainDestruction` | `true` | **Master switch.** `false` means no block is ever destroyed by any weapon. |
| `maxBlocksPerStrike` | `6000` | Cap on blocks one strike can remove. |
| `maxBlockHardness` | `50` | Blocks harder than this (obsidian = 50) survive. Bedrock always survives. |
| `protectBlockEntities` | `true` | Chests, spawners, beacons and other data blocks are never destroyed. |
| `maxTargetRange` | `128` | Aiming distance (hard cap 128). |
| `damagePlayers` / `damageOwner` | `true` / `false` | Whether strikes hurt players, and whether they hurt you. |
| `firePermission` | `EVERYONE` | `EVERYONE`, `CREATIVE_ONLY`, `ALLOWLIST` or `OWNER_ONLY`. The owner can always fire. |
| `cutsceneMode` | `FIRER` | Who sees the cutscene: `FIRER`, `OWNER` (only if the owner fires) or `EVERYONE` nearby. |
| `maxActiveStrikes` / `maxActiveStrikesPerPlayer` | `3` / `1` | Simultaneous strike caps. |
| `globalCooldownSeconds` | `8` | Wait after ANY shot before the next remote. |
| `protectedZones`, `blockedDimensions` | empty | Zones (`"dimension x z radius"`) and dimensions where strikes are banned. |
| `respectClaims` | `true` | Ask claim mods through the Common Protection API. |
| `ammoEnabled`, `ammoCost` | `true` | Orbital Charges per shot. |
| `cinematic` / `cinematicCamera` | `true` | Intro cutscene and camera easing toward the target. |
| `slowMotion` | `true` | Bullet-time at impact (only when one player is on the server). |
| `lightning`, `debris`, `cameraShake` | `true` | Extra visual effects. |
| `particleScale` | `1.0` | Lower to `0.2` for a quick FPS boost on weaker PCs. |

Per-weapon keys follow the pattern `railgunCooldownSeconds`, `nukeBlastRadius`, `laserDurationSeconds`, `meteorCount`, `blackHolePullRadius` and so on. Open the generated file to see all of them.

**Safe mode for servers:** set `"terrainDestruction": false` and `"damagePlayers": false` to keep the show without the grief.

## Performance tips

- Lower `particleScale` (e.g. `0.5`).
- Set `debrisCount` to `0` or a small number.
- Turn off `cinematicCamera` and `slowMotion` if you get motion sick.

## Building from source

```bash
git clone https://github.com/KiegsGit/SkyBreaker---Beta-Testing-.git
cd SkyBreaker---Beta-Testing-
./gradlew build
```

The jar appears in `build/libs/`. Requires JDK 25. Every push builds automatically with GitHub Actions; each version gets its own release (`v1`, `v2`, ...) so you can compare them.

## Warning

These weapons are **extremely destructive**. Back up your world first, and be careful on shared servers.

## License

MIT, see [LICENSE](LICENSE).
