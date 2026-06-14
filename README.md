<p align="center">
<img src=".github/banner.png" alt="ReviveGraves Banner" width="1000"/>

# ReviveGraves

**ReviveGraves** (Fabric 26.1.2) adds cooperative rescue mechanics to your Minecraft server.

---

## Features

- **Gravestones on Death**
  An indestructible gravestone spawns at your death location with a skull and your name displayed above it. The gravestone glows softly and firefly particles drift around it.

- **Ghost Chicken Mode**
  Fallen players respawn as invisible ghost chickens in Adventure mode — other players see a chicken, while you experience the world from chicken eye level with slow falling and reduced speed.

- **Revive Token**
  Teammates right-click the gravestone with a Revive Token to bring you back — you teleport to the gravestone, return to Survival mode, and the token is consumed.

- **Ghost Mechanics**
  As a ghost chicken you can open doors and gates, but you can't interact with containers, armor stands, or other entities. Soul particles trail behind you and chicken sounds play at random intervals.

- **Server-Side Config**
  Fully configurable via `config/revivegraves.json` — loot drop chances, start tokens for new players, gravestone expiry timer, ghost speed, slow falling, particles, and more.

- **Custom Advancements**
  6 unique advancements in a dedicated mod tab — track your deaths, revives, and time spent as a ghost chicken.

- **Loot Integration**
  Revive Tokens appear naturally in End City treasure chests and Ominous Trial Vaults. New players receive starter tokens on first join (configurable).

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/) and [Fabric API](https://modrinth.com/mod/fabric-api) for Minecraft 26.1.2
2. Download the latest ReviveGraves JAR from [Modrinth](https://modrinth.com/mod/revivegraves) or [Releases](../../releases)
3. Place the JAR in your `mods/` folder
4. Launch the game

---

## Configuration

After first launch, edit `config/revivegraves.json` on your server:

| Section | Option | Default | Description |
|---------|--------|---------|-------------|
| **loot** | `enabled` | `true` | Master switch for loot table injection |
| | `endCityChance` | `0.01` | Revive Token drop chance in End City chests (1%) |
| | `ominousVaultChance` | `0.03` | Drop chance in Ominous Trial Vaults (3%) |
| **startTokens** | `enabled` | `true` | Give new players tokens on first join |
| | `amount` | `5` | Number of starter tokens |
| **gravestone** | `timerEnabled` | `false` | Enable gravestone expiry timer |
| | `timerSeconds` | `300` | Seconds until gravestone disappears |
| | `storeItems` | `true` | Store inventory in gravestone, return on revive |
| | `storeXp` | `false` | Store XP in gravestone, return on revive |
| | `fireflyParticlesEnabled` | `true` | Show firefly particles around gravestone |
| **ghost** | `speedMultiplier` | `0.75` | Ghost speed (75% of normal) |
| | `sprintEnabled` | `false` | Allow ghosts to sprint |
| | `spawnAtGravestone` | `false` | Ghost spawns at gravestone instead of spawnpoint |
| | `slowFallingEnabled` | `true` | Slow falling like a real chicken |
| | `particlesEnabled` | `true` | Soul fire flame particles |

All changes require a server restart.

---

## Inspiration & Thanks

**FWhip's Hardcore SOS**, **Kaupenjoe** (Fabric tutorials) & **B1n-ry / [You're in Grave Danger](https://github.com/B1n-ry/Youre-in-grave-danger)**.

---

MIT License

If you have questions or feedback feel free to join my Discord: https://discord.gg/VDzC4v8MXV
