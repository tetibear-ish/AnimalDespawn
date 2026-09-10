# AnimalDespawn 0.6.0

Beta 1.7.3-style passive animal despawning for Paper 1.12.2, with targeted spawn assistance for passive mobs that fail to repopulate established chunks.

## 0.6.0

- Added targeted spawn assistance for **rabbits** and **parrots**.
- Spawn assistance is biome-aware and only attempts to repopulate an area when the configured minimum population is missing.
- Spawn assistance does not replace normal spawning for cows, pigs, sheep, chickens, wolves, horses, donkeys, llamas, or other animals.
- Added `/animaldespawn diagnose` and `/animaldespawn diagnose reset`.
- Diagnostics record animal spawn events by entity type and `CreatureSpawnEvent.SpawnReason`, and show current rabbit/parrot counts around the diagnostic player.
- The username `flushedpancake` may use the diagnostic commands even without OP or the diagnostic permission. This bypass applies only to diagnostics.
- Natural baby tracking now treats both `NATURAL` and `CHUNK_GEN` as natural baby sources.
- Existing tamed-animal, breeding, feeding, naming, leash, shearing, dye, and baby protection rules remain in place.
- Configuration versions below 0.6.0 are automatically replaced with the 0.6.0 default configuration.

## Commands

- `/animaldespawn status`
- `/animaldespawn reload`
- `/animaldespawn scan`
- `/animaldespawn inspect`
- `/animaldespawn diagnose`
- `/animaldespawn diagnose reset`

## Spawn assistance defaults

```yaml
spawn-assist:
  enabled: true
  interval-ticks: 1200
  radius: 64
  minimum-count: 1
  maximum-spawns-per-cycle: 4
  rabbit: true
  parrot: true
```

The assistant deliberately targets only rabbits and parrots for 0.6.0. The diagnostic tool is intended to gather evidence before extending assistance to other species.
