# AnimalDespawn 0.5.0

Legacy-style passive animal despawning for Paper 1.12.2.

## What it does

This plugin deliberately does **not** implement its own animal spawner. Paper remains responsible for natural animal spawning.

The plugin restores a configurable legacy-style despawn process so that unprotected animals can leave the population and give Paper room to replenish it.

### Presets

`preset: BETA_1_7_3`

Classic Beta-style values:

- more than 128 blocks from the nearest player: immediate removal
- more than 32 blocks away: age accumulates
- after 600 ticks outside 32 blocks: 1/800 random removal roll per scan
- within 32 blocks: age resets

The random removal roll accounts for the configured scan interval: with the
default `scan-interval-ticks: 20`, the plugin combines 20 ticks' worth of
1/800 chances into a single equivalent roll per scan, rather than silently
lowering the effective despawn rate whenever the scan interval is raised
above 1 tick.

`LEGACY_CONSOLE` and `BEDROCK` are provided as named presets and currently use the same 32/128/600/800 despawn model. This keeps the configuration simple while leaving room for version-specific differences to be added later.

`CUSTOM` uses the values under `despawn:`.

## Protection

By default the plugin protects animals after:

- taming
- breeding
- feeding with a valid species food
- being leashed
- being named
- being sheared
- being dyed
- dyeing a tamed wolf's collar
- being a player-bred baby

Feeding is only protective on its own for animals that cannot be tamed
(cows, pigs, sheep, chickens, rabbits, mooshrooms). For tameable animals
(wolves, ocelots, horses, donkeys, mules, llamas), feeding an untamed
individual does **not** protect it — only taming does. This keeps a
handful of feed thrown at wild wolves or horses from turning them into
permanently protected, population-cap-blocking mobs; once actually tamed,
the `tamed` protection applies as normal.

Babies are split by origin: a baby produced by breeding is protected (it is
player-created), while a baby from ordinary natural spawning is treated as
an eligible wild spawn and left despawn-eligible like its adult form. Each
naturally spawned baby's UUID is tracked and persisted in `data.yml` so this
distinction survives a server restart; the entry is dropped once the animal
grows up.

Polar bears are now included in the default `animals:` list alongside
horses, donkeys, mules, and llamas.

## Configuration upgrades

`config.yml` carries a `config-version` field. On startup, if the installed
config's version is older than the plugin's bundled version, `config.yml`
is automatically regenerated to the bundled default and reloaded. Back up
any hand-edited `config.yml` before upgrading the plugin.

Feeding is only protective on its own for animals that cannot be tamed
(cows, pigs, sheep, chickens, rabbits, mooshrooms). For tameable animals
(wolves, ocelots, horses, donkeys, mules, llamas), feeding an untamed
individual does **not** protect it — only taming does. This keeps a
handful of feed thrown at wild wolves or horses from turning them into
permanently protected, population-cap-blocking mobs; once actually tamed,
the `tamed` protection applies as normal.

Polar bears are now included in the default `animals:` list alongside
horses, donkeys, mules, and llamas.

## Configuration upgrades

`config.yml` carries a `config-version` field. On startup, if the installed
config's version is older than the plugin's bundled version, `config.yml`
is automatically regenerated to the bundled default and reloaded. Back up
any hand-edited `config.yml` before upgrading the plugin.

## Paper settings

A useful 1.12.2 setup is:

    ticks-per:
      animal-spawns: 1

    spawn-limits:
      animals: 70

`animal-spawns: 1` means Paper attempts animal spawning every tick; it does not guarantee a successful spawn. The plugin therefore leaves spawning to Paper rather than adding another competing spawn system.

## Commands

- `/animaldespawn status`
- `/animaldespawn reload`
- `/animaldespawn scan`
- `/animaldespawn inspect` — toggles operator inspection mode; right-click an animal to see counted/protected/despawn state.

Permission:

`animaldespawn.admin`

## Building

Use the included `build.bat` on the Windows machine with a Java 8 JDK and Maven installed.

The output JAR is:

`target/AnimalDespawn-0.5.0.jar`
