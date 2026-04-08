# Gravity Gun

A Minecraft Fabric mod that adds a Gravity Gun item to the game. Use it to grab blocks and entities, then launch them at high speed!

## Features

- **Grab blocks and entities**: Left-click while holding the Gravity Gun to grab the block or entity you're looking at
- **Launch projectiles**: Right-click to launch grabbed objects at high speed in the direction you're looking
- **Follows your crosshair**: Grabbed entities float 3 blocks in front of you and follow your aim
- **Block placement**: Launched blocks automatically place themselves where they land
- **Cooldown system**: 2-second cooldown between grabs to balance gameplay

## Crafting Recipe

```
[Ender Pearl] [Nether Star] [Ender Pearl]
[Iron Ingot]  [Redstone Block] [Iron Ingot]
    [ ]       [Iron Ingot]      [ ]
```

Ingredients:
- 2x Ender Pearl
- 1x Nether Star
- 3x Iron Ingot
- 1x Redstone Block

## Requirements

- Minecraft 1.21.1
- Fabric Loader 0.16+
- Fabric API

## Installation

1. Install Fabric for Minecraft 1.21.1
2. Download the JAR from `build/libs/`
3. Place it in your `.minecraft/mods/` folder
4. Launch the game with the Fabric profile

## Building

```bash
gradle build
```

The built JAR will be in `build/libs/`.
