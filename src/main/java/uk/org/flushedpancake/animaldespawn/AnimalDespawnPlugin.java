package uk.org.flushedpancake.animaldespawn;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class AnimalDespawnPlugin extends JavaPlugin {
    private DespawnManager manager;
    private BukkitTask task;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        refreshConfigIfNeeded();

        manager = new DespawnManager(this);
        Bukkit.getPluginManager().registerEvents(manager, this);
        getCommand("animaldespawn").setExecutor(new AnimalDespawnCommand(this, manager));

        scheduleManager();
        getLogger().info("AnimalDespawn enabled. Preset: " + manager.getPreset());
    }

    private static final String CONFIG_VERSION = "0.5.0";

    private void refreshConfigIfNeeded() {
        String installedVersion = getConfig().getString("config-version", "0.0.0");

        if (compareVersions(installedVersion, CONFIG_VERSION) < 0) {
            getLogger().info("Configuration " + installedVersion
                    + " is older than " + CONFIG_VERSION + "; refreshing config.yml.");
            saveResource("config.yml", true);
            reloadConfig();
            getLogger().info("config.yml refreshed to version " + CONFIG_VERSION + ".");
        }
    }

    private int compareVersions(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");

        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int av = i < a.length ? parseVersionPart(a[i]) : 0;
            int bv = i < b.length ? parseVersionPart(b[i]) : 0;
            if (av != bv) {
                return av < bv ? -1 : 1;
            }
        }
        return 0;
    }

    private int parseVersionPart(String value) {
        try {
            return Integer.parseInt(value.replaceAll("[^0-9].*$", ""));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private void scheduleManager() {
        if (task != null) {
            task.cancel();
        }

        long interval = Math.max(1L, manager.getScanInterval());
        task = Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                manager.tick(interval);
            }
        }, interval, interval);
    }

    @Override
    public void onDisable() {
        if (task != null) {
            task.cancel();
        }
        if (manager != null) {
            manager.save();
        }
    }
}
