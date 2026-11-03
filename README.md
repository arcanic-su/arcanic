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

This project is subject to the [GNU General Public License v3.0](LICENSE). This does only apply for source code
located directly in this clean repository. During the development and compilation process, additional source code may
be used to which we have obtained no rights. Such code is not covered by the GPL license.

For those who are unfamiliar with the license, here is a summary of its main points. This is by no means legal advice
nor legally binding.

*Actions that you are allowed to do:*

- Use
- Share
- Modify

*If you do decide to use ANY code from the source:*

- **You must disclose the source code of your modified work and the source code you took from this project. This means
  you are not allowed to use code from this project (even partially) in a closed-source and/or obfuscated
  application.**
- **You must state clearly and obviously to all end users that you are using code from this project.**
- **Your application must also be licensed under the same license.**

Bundled third-party mods keep their own licenses, see [third-party](third-party).

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
