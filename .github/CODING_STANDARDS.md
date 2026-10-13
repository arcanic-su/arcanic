# Coding standards

A short list of what we expect from a pull request. When in doubt, look at how the code around your change does it.

## Formatting

The code is formatted with [Spotless](https://github.com/diffplug/spotless) (palantir-java-format). Run

```
./gradlew spotlessApply
```

before you commit; CI runs `spotlessCheck` and fails on unformatted code. Every Java file starts with the licence
header from [`HEADER`](../HEADER) - Spotless adds it for you.

## Java

- The mod targets Java 21. Use Mojang mappings for Minecraft names.
- Import classes instead of writing their full names in the code.
- A module is one class in `fx`, `hud` or `cosmetic` that extends `Module` and is registered in `core/Modules`.
  Settings are fields created with `add(new Setting...)`; their keys are saved in the player's config, so do not
  rename a key without a reason.
- Text shown to the player is written in Russian in the code and translated in
  `assets/arcanicvisuals/i18n/en.json`. Add the English line together with the new text.

## Mixins

- Keep a mixin small: it should call into a normal class and do nothing clever itself.
- Prefer `@Inject` with `require = 0` for hooks the client can live without, so an update of another mod does not
  crash the game.
- Name injected methods `arcanicvisuals$whatItDoes`.

## Rendering

- Nothing in a render path may allocate per frame without need. Cache textures, buffers and pipelines.
- A visual effect must not change gameplay: no hidden information, no input automation.

## Commits and pull requests

- One topic per pull request. Describe what changed and how you tested it in the game.
- Do not commit the `run/` folder, build output or anything with personal data.