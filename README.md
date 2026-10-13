# Arcanic Visuals

A free and open-source visual client for Minecraft 1.21.11, built on [Fabric](https://fabricmc.net/).
Hit effects, hand animations, a custom HUD, melee skins and cosmetics that every Arcanic player can see.
Arcanic Visuals only changes what you see: it never touches game mechanics or sends anything that would give an
advantage.

Website: [arcanic.su](https://arcanic.su) · Discord: [discord.gg/zhKK8naQK2](https://discord.gg/zhKK8naQK2)

## Installation

The easiest way to play is the [Arcanic Launcher](https://arcanic.su/download/). It installs Minecraft, Fabric, Java
and the client, and keeps everything up to date.

## Issues

If you notice a bug or want to suggest a feature, please open an
[issue](https://github.com/arcanic-su/arcanic/issues). Before you do, check whether it has already been reported.

## License

Arcanic Visuals is free software under the [GNU General Public License v3.0](LICENSE). The licence covers the source
code in this repository. Minecraft and the mods bundled with the client are not ours and stay under their own terms,
see [third-party](third-party) and [libs](libs/NOTICE.txt).

In short, and not as legal advice: you may use, share and change the code. If you publish something built on it, you
have to publish your source as well, keep it under the GPL v3 and say that it is based on Arcanic Visuals.

## Contributing

We welcome contributions! Please read our [coding standards](.github/CODING_STANDARDS.md) before opening a pull
request.

## Setting up a workspace

Arcanic Visuals uses Gradle. Gradle itself needs Java 25 to run Loom; the mod is compiled for Java 21.

1. Clone the repository: `git clone https://github.com/arcanic-su/arcanic`
2. Open the folder as a Gradle project in your IDE (IntelliJ IDEA is recommended).
3. Run `./gradlew genSources` once to get readable Minecraft sources.
4. Start the game with `./gradlew runClient`, or build the jar with `./gradlew build` (output in `build/libs`).

## Project layout

| Path | What is there |
|---|---|
| `src/main/java/net/arcanic/visuals/core` | modules, settings, config, theme, sync with other players |
| `src/main/java/net/arcanic/visuals/fx` | visual effects: hits, trails, outlines, sky, trajectories |
| `src/main/java/net/arcanic/visuals/hud` | HUD elements, dynamic island, target HUD |
| `src/main/java/net/arcanic/visuals/gui` | menus and screens |
| `src/main/java/net/arcanic/visuals/arc` | melee skins (animated knives) and cloth capes |
| `src/main/java/net/arcanic/visuals/cosmetic` | hats, pets, wings, backpacks |
| `src/main/java/net/arcanic/visuals/mixin` | Mixins into Minecraft |
