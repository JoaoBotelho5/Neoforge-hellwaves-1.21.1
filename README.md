# Hellwaves

Hellwaves is a wave-survival mod for Minecraft (NeoForge 1.21.1) built around **Activator Blocks** — portal-like structures that summon escalating waves of enemies, and **Guardians**, tameable-style undead allies you can equip, upgrade, and store.

## Features

### Wave Activators
Place an Activator Block to trigger a sequence of automatically-spawning enemy waves:
- **Standard Activator** — 3 waves, escalating from zombies/skeletons to a boss-tier Undead Lord.
- **Elite Activator** — 5 waves, culminating in a Warden encounter, with larger spawn radius and stronger rewards.

Each wave's mobs are equipped with weighted, JSON-configurable gear (weapons, armor, potion effects), so difficulty and loot can be tuned without touching code. The activator block visually grows as waves progress, and rewards (diamond/emerald blocks, ancient debris, and — for the elite version — a nether star) drop on completion. If an enemy reaches the activator, it detonates.

### Guardians
Zombie and Skeleton Guardians are upgradeable allies with:
- A dedicated **inventory GUI** (armor slots, hand slots, and extra storage) synced live with health, armor, toughness, and attack damage.
- **Soul Cages** — capture a guardian (preserving level, health, equipment, and custom name) and release it elsewhere.
- Friendly-fire protection: guardians won't be attacked by (or attack) Iron Golems, Snow Golems, and Wolves unless provoked first.
- A networked upgrade system triggered via custom packets.

### Warped Miner
A specialized hostile mob that pathfinds toward a target block and predictively mines through obstacles in its way (respecting a protected-blocks list), with a "3-hit" rule that switches it into direct combat once attacked enough times.

### Custom equipment
- **Greatsword** — a heavy two-handed weapon with a custom netherite-based tool tier.
- **Guardian Summoner items** for spawning Zombie/Skeleton Guardians directly.

## Technical highlights
- Built on **NeoForge 1.21.1**, using `DeferredRegister` for blocks, items, entities, block entities, and menu types.
- Custom `AbstractContainerMenu` + `Screen` implementation for the Guardian inventory, with a `ContainerData` bridge for live stat synchronization between server and client.
- Custom networking via `CustomPacketPayload` for guardian upgrade requests.
- Data-driven equipment loadouts per wave, loaded from JSON config at startup.
- Event-driven targeting rules (`LivingChangeTargetEvent`) to control guardian aggression logic.

## Installation
1. Clone this repository.
2. Open it in IntelliJ IDEA or Eclipse.
3. Run `gradlew --refresh-dependencies` if libraries are missing, or `gradlew clean` to reset the build environment.

## Mapping names
This project uses Mojang's official mappings for methods and fields. See the license terms at:
https://github.com/NeoForged/NeoForm/blob/main/Mojang.md

## Resources
- NeoForged docs: https://docs.neoforged.net/
- NeoForged Discord: https://discord.neoforged.net/
