package uk.org.flushedpancake.animaldespawn;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DespawnManager implements Listener {
    private static final String PROTECTED_META = "animaldespawn-protected";

    private final JavaPlugin plugin;
    private final Map<UUID, Long> ages = new HashMap<UUID, Long>();
    private final Set<UUID> protectedAnimals = new HashSet<UUID>();
    /** UUIDs of naturally spawned baby animals. These are intentionally not protected. */
    private final Set<UUID> naturalBabies = new HashSet<UUID>();
    private final java.util.Random random = new java.util.Random();

    private long scanInterval;
    private int maximumRemovals;
    private boolean enabled;
    private String preset;

    public DespawnManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadNaturalBabies();
        reload();
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("enabled", true);
        scanInterval = Math.max(1L, plugin.getConfig().getLong("despawn.scan-interval-ticks", 20L));
        maximumRemovals = Math.max(1, plugin.getConfig().getInt("despawn.maximum-removals-per-scan", 8));
        preset = plugin.getConfig().getString("preset", "BETA_1_7_3").toUpperCase();

        // Presets own the algorithm's values. CUSTOM uses config values.
        if ("BETA_1_7_3".equals(preset) || "LEGACY_CONSOLE".equals(preset) || "BEDROCK".equals(preset)) {
            plugin.getConfig().set("despawn.maximum-distance", 128);
            plugin.getConfig().set("despawn.minimum-distance", 32);
            plugin.getConfig().set("despawn.minimum-age-ticks", 600);
            plugin.getConfig().set("despawn.random-check-bound", 800);
        }
    }

    public long getScanInterval() {
        return scanInterval;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getPreset() {
        return preset;
    }

    public void tick(long elapsedTicks) {
        if (!enabled) {
            return;
        }

        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            if (!isWorldEnabled(world)) {
                continue;
            }

            for (Entity entity : world.getEntities()) {
                if (removed >= maximumRemovals) {
                    return;
                }
                if (!(entity instanceof Animals)) {
                    continue;
                }
                if (!isAnimalEnabled((Animals) entity)) {
                    continue;
                }

                UUID uuid = entity.getUniqueId();

                if (isProtected(entity)) {
                    ages.remove(uuid);
                    continue;
                }

                Player nearest = nearestPlayer(entity);
                if (nearest == null) {
                    // The entity is not currently being observed by a player.
                    // Do not artificially age it while there is no player to
                    // apply the distance test against.
                    continue;
                }

                double distanceSquared = entity.getLocation().distanceSquared(nearest.getLocation());
                double minDistance = getMinimumDistance();
                double maxDistance = getMaximumDistance();

                if (distanceSquared > maxDistance * maxDistance) {
                    entity.remove();
                    ages.remove(uuid);
                    removed++;
                    continue;
                }

                if (distanceSquared < minDistance * minDistance) {
                    ages.put(uuid, 0L);
                    continue;
                }

                long age = ages.containsKey(uuid) ? ages.get(uuid) : 0L;
                age += Math.max(1L, elapsedTicks);
                ages.put(uuid, age);

                if (age > getMinimumAgeTicks()) {
                    int bound = getRandomCheckBound();
                    long ticks = Math.max(1L, elapsedTicks);

                    // The configured bound is a per-tick probability. Because
                    // scans normally run every 20 ticks, combine the elapsed
                    // ticks into one equivalent roll instead of accidentally
                    // reducing the configured despawn rate by 20x.
                    boolean despawnRoll = bound <= 1
                            || random.nextDouble() < 1.0 - Math.pow(1.0 - (1.0 / bound), ticks);

                    if (despawnRoll) {
                        entity.remove();
                        ages.remove(uuid);
                        removed++;
                    }
                }
            }
        }
    }

    public int manualScan() {
        int before = countEligibleLoadedAnimals();
        tick(0L);
        return before - countEligibleLoadedAnimals();
    }

    public int countLoadedAnimals() {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            if (!isWorldEnabled(world)) continue;
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Animals && isAnimalEnabled((Animals) entity)) {
                    count++;
                }
            }
        }
        return count;
    }

    public int countProtectedLoadedAnimals() {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            if (!isWorldEnabled(world)) continue;
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Animals && isAnimalEnabled((Animals) entity) && isProtected(entity)) {
                    count++;
                }
            }
        }
        return count;
    }

    public int countEligibleLoadedAnimals() {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            if (!isWorldEnabled(world)) continue;
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Animals && isAnimalEnabled((Animals) entity) && !isProtected(entity)) {
                    count++;
                }
            }
        }
        return count;
    }

    public int countWithinDistance(int minInclusive, int maxExclusive) {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            if (!isWorldEnabled(world)) continue;
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof Animals) || !isAnimalEnabled((Animals) entity) || isProtected(entity)) continue;
                Player nearest = nearestPlayer(entity);
                if (nearest == null) continue;
                double d2 = entity.getLocation().distanceSquared(nearest.getLocation());
                if (d2 >= minInclusive * minInclusive && d2 < maxExclusive * maxExclusive) count++;
            }
        }
        return count;
    }

    public int countBeyondMaximumDistance() {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            if (!isWorldEnabled(world)) continue;
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof Animals) || !isAnimalEnabled((Animals) entity) || isProtected(entity)) continue;
                Player nearest = nearestPlayer(entity);
                if (nearest == null) continue;
                double d2 = entity.getLocation().distanceSquared(nearest.getLocation());
                if (d2 > getMaximumDistance() * getMaximumDistance()) count++;
            }
        }
        return count;
    }

    public int countOldEnough() {
        int count = 0;
        long threshold = getMinimumAgeTicks();
        for (Long age : ages.values()) {
            if (age != null && age > threshold) count++;
        }
        return count;
    }

    public long getMinimumAgeTicks() {
        return plugin.getConfig().getLong("despawn.minimum-age-ticks", 600L);
    }

    public int getRandomCheckBound() {
        return Math.max(1, plugin.getConfig().getInt("despawn.random-check-bound", 800));
    }

    public double getMinimumDistance() {
        return plugin.getConfig().getDouble("despawn.minimum-distance", 32.0);
    }

    public double getMaximumDistance() {
        return plugin.getConfig().getDouble("despawn.maximum-distance", 128.0);
    }

    public void markProtected(Entity entity, String reason) {
        if (!(entity instanceof Animals)) {
            return;
        }
        UUID uuid = entity.getUniqueId();
        protectedAnimals.add(uuid);
        entity.setMetadata(PROTECTED_META, new FixedMetadataValue(plugin, reason));
        ages.remove(uuid);
    }

    public boolean isProtected(Entity entity) {
        if (!(entity instanceof Animals)) {
            return false;
        }

        UUID uuid = entity.getUniqueId();

        if (protectedAnimals.contains(uuid)) return true;
        if (entity.hasMetadata(PROTECTED_META)) return true;

        boolean named = plugin.getConfig().getBoolean("protection.named", true);
        if (named && entity.getCustomName() != null && !entity.getCustomName().trim().isEmpty()) {
            return true;
        }

        boolean leashed = plugin.getConfig().getBoolean("protection.leashed", true);
        if (leashed && entity instanceof org.bukkit.entity.LivingEntity
                && ((org.bukkit.entity.LivingEntity) entity).isLeashed()) {
            return true;
        }

        boolean tamed = plugin.getConfig().getBoolean("protection.tamed", true);
        if (tamed && entity instanceof Tameable && ((Tameable) entity).isTamed()) {
            return true;
        }

        boolean babies = plugin.getConfig().getBoolean("protection.babies", true);
        if (babies && entity instanceof org.bukkit.entity.Ageable
                && !((org.bukkit.entity.Ageable) entity).isAdult()) {
            // Natural babies are deliberately eligible for despawning.
            // Player-bred babies (or babies from other non-natural sources)
            // retain the normal baby protection.
            if (naturalBabies.contains(uuid)) {
                return false;
            }
            return true;
        }

        // A natural-baby UUID is no longer needed once the entity is adult.
        if (entity instanceof org.bukkit.entity.Ageable
                && ((org.bukkit.entity.Ageable) entity).isAdult()) {
            naturalBabies.remove(uuid);
        }

        return false;
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Animals)) return;
        if (!(entity instanceof org.bukkit.entity.Ageable)) return;
        if (((org.bukkit.entity.Ageable) entity).isAdult()) return;

        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
            naturalBabies.add(entity.getUniqueId());
        }
    }

    @EventHandler
    public void onTame(EntityTameEvent event) {
        if (plugin.getConfig().getBoolean("protection.tamed", true)) {
            markProtected(event.getEntity(), "tamed");
        }
    }

    @EventHandler
    public void onBreed(EntityBreedEvent event) {
        if (plugin.getConfig().getBoolean("protection.bred", true)) {
            markProtected(event.getEntity(), "bred");
            if (event.getMother() != null) markProtected(event.getMother(), "bred");
            if (event.getFather() != null) markProtected(event.getFather(), "bred");
        }
    }

    @EventHandler
    public void onShear(PlayerShearEntityEvent event) {
        if (event.isCancelled()) return;
        if (event.getEntity() instanceof Sheep && plugin.getConfig().getBoolean("protection.sheared", true)) {
            markProtected(event.getEntity(), "sheared");
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.isCancelled()) return;

        Entity target = event.getRightClicked();
        if (event.getPlayer().hasMetadata("animaldespawn-inspect") && target instanceof Animals) {
            event.getPlayer().sendMessage(inspect(target));
            return;
        }
        if (!(target instanceof Animals)) return;

        ItemStack item = event.getPlayer().getItemInHand();
        if (item == null || item.getType() == Material.AIR) return;

        if (isDye(item)) {
            if (target instanceof Sheep && plugin.getConfig().getBoolean("protection.dyed", true)) {
                markProtected(target, "dyed");
                return;
            }
            if (target instanceof Wolf && plugin.getConfig().getBoolean("protection.wolf-collar-dyed", true)) {
                Wolf wolf = (Wolf) target;
                if (wolf.isTamed()) {
                    markProtected(target, "wolf-collar-dyed");
                    return;
                }
            }
        }

        if (plugin.getConfig().getBoolean("protection.fed", true)
                && isValidFood(target, item.getType())
                && shouldProtectWhenFed(target)) {
            markProtected(target, "fed");
        }
    }



    /**
     * Tameable animals are protected because they are tamed, not merely because
     * a player interacted with/fed them. This is important for wild wolves,
     * ocelots, horses, donkeys, llamas and mules: feeding an untamed animal
     * must not turn it into a permanently protected population-cap blocker.
     */
    private boolean shouldProtectWhenFed(Entity entity) {
        if (entity instanceof Tameable) {
            return ((Tameable) entity).isTamed();
        }
        return true;
    }
\n    private boolean isValidFood(Entity entity, Material material) {
        if (!(entity instanceof Animals)) return false;

        // 1.12.2 feeding/luring foods for the supported passive animals.
        if (entity instanceof org.bukkit.entity.Cow || entity instanceof org.bukkit.entity.MushroomCow) {
            return material == Material.WHEAT;
        }
        if (entity instanceof org.bukkit.entity.Sheep) {
            return material == Material.WHEAT;
        }
        if (entity instanceof org.bukkit.entity.Pig) {
            return material == Material.CARROT_ITEM
                    || material == Material.POTATO_ITEM
                    || material == Material.BEETROOT;
        }
        if (entity instanceof org.bukkit.entity.Chicken) {
            return material == Material.SEEDS
                    || material == Material.MELON_SEEDS
                    || material == Material.PUMPKIN_SEEDS;
        }
        if (entity instanceof org.bukkit.entity.Rabbit) {
            return material == Material.CARROT_ITEM
                    || material == Material.GOLDEN_CARROT
                    || material == Material.YELLOW_FLOWER;
        }
        if (entity instanceof Wolf) {
            return material == Material.RAW_BEEF || material == Material.COOKED_BEEF
                    || material == Material.RAW_CHICKEN || material == Material.COOKED_CHICKEN
                    || material == Material.PORK || material == Material.GRILLED_PORK
                    || material == Material.MUTTON || material == Material.COOKED_MUTTON
                    || material == Material.RABBIT || material == Material.COOKED_RABBIT
                    || material == Material.ROTTEN_FLESH;
        }
        if (entity instanceof org.bukkit.entity.Ocelot) return material == Material.RAW_FISH;
        return false;
    }

    private boolean isDye(ItemStack item) {
        if (item.getType() != Material.INK_SACK) return false;
        return item.getDurability() <= 15;
    }

    private boolean isWorldEnabled(World world) {
        List<String> whitelist = plugin.getConfig().getStringList("worlds.whitelist");
        List<String> blacklist = plugin.getConfig().getStringList("worlds.blacklist");

        if (blacklist.contains(world.getName())) return false;
        return whitelist.isEmpty() || whitelist.contains(world.getName());
    }

    private boolean isAnimalEnabled(Animals animal) {
        String key = animal.getType().name();
        return plugin.getConfig().getBoolean("animals." + key, true);
    }

    private Player nearestPlayer(Entity entity) {
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(entity.getWorld())) continue;
            double distance = player.getLocation().distanceSquared(entity.getLocation());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    public String inspect(Entity entity) {
        if (!(entity instanceof Animals)) return "That entity is not an animal.";
        Animals animal = (Animals) entity;
        StringBuilder out = new StringBuilder();
        out.append(ChatColor.YELLOW).append(animal.getType().name()).append(ChatColor.WHITE)
           .append("  ").append(entity.getUniqueId()).append("\n");
        boolean counted = isAnimalEnabled(animal);
        boolean protectedNow = isProtected(entity);
        out.append(ChatColor.GRAY).append("counted: ")
           .append(counted ? ChatColor.GREEN + "YES" : ChatColor.RED + "NO").append("\n");
        out.append(ChatColor.GRAY).append("protected: ")
           .append(protectedNow ? ChatColor.GREEN + "YES" : ChatColor.RED + "NO").append("\n");
        Player nearest = nearestPlayer(entity);
        if (nearest == null) {
            out.append(ChatColor.GRAY).append("nearest player: ").append(ChatColor.WHITE).append("none");
        } else {
            double d = Math.sqrt(entity.getLocation().distanceSquared(nearest.getLocation()));
            out.append(ChatColor.GRAY).append("distance: ").append(ChatColor.WHITE)
               .append(String.format(java.util.Locale.US, "%.1f", d)).append("m\n");
            if (d > getMaximumDistance()) {
                out.append(ChatColor.GRAY).append("despawn state: ").append(ChatColor.RED).append("IMMEDIATE");
            } else if (d < getMinimumDistance()) {
                out.append(ChatColor.GRAY).append("despawn state: ").append(ChatColor.GREEN).append("SAFE / AGE RESET");
            } else {
                long age = ages.containsKey(entity.getUniqueId()) ? ages.get(entity.getUniqueId()) : 0L;
                out.append(ChatColor.GRAY).append("despawn state: ").append(ChatColor.WHITE).append("ROLL ZONE\n")
                   .append(ChatColor.GRAY).append("despawn age: ").append(ChatColor.WHITE).append(age)
                   .append(" / ").append(getMinimumAgeTicks()).append(" ticks\n")
                   .append(ChatColor.GRAY).append("random roll: ").append(ChatColor.WHITE)
                   .append("1/").append(getRandomCheckBound()).append(" per tick");
            }
        }
        return out.toString();
    }

    private void loadNaturalBabies() {
        org.bukkit.configuration.file.YamlConfiguration data =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                        new java.io.File(plugin.getDataFolder(), "data.yml"));
        for (String value : data.getStringList("natural-babies")) {
            try {
                naturalBabies.add(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed historical UUIDs.
            }
        }
    }

    public void save() {
        java.io.File folder = plugin.getDataFolder();
        if (!folder.exists()) folder.mkdirs();

        org.bukkit.configuration.file.YamlConfiguration data =
                new org.bukkit.configuration.file.YamlConfiguration();
        List<String> uuids = new ArrayList<String>();
        for (UUID uuid : naturalBabies) {
            uuids.add(uuid.toString());
        }
        data.set("natural-babies", uuids);
        try {
            data.save(new java.io.File(folder, "data.yml"));
        } catch (java.io.IOException ex) {
            plugin.getLogger().warning("Could not save natural baby tracking data: " + ex.getMessage());
        }
    }
}
