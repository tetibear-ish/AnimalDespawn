package uk.org.flushedpancake.animaldespawn;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.metadata.FixedMetadataValue;

public class AnimalDespawnCommand implements CommandExecutor {
    private static final String DIAGNOSTIC_USER = "flushedpancake";

    private final JavaPlugin plugin;
    private final DespawnManager manager;

    public AnimalDespawnCommand(JavaPlugin plugin, DespawnManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private boolean canDiagnose(CommandSender sender) {
        if (sender.hasPermission("animaldespawn.diagnose")) return true;
        return sender instanceof Player
                && DIAGNOSTIC_USER.equalsIgnoreCase(((Player) sender).getName());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && "diagnose".equalsIgnoreCase(args[0])) {
            if (!canDiagnose(sender)) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use diagnostics.");
                return true;
            }
            if (args.length > 1 && "reset".equalsIgnoreCase(args[1])) {
                manager.resetSpawnDiagnostics();
                sender.sendMessage(ChatColor.GREEN + "Spawn diagnostics reset.");
                return true;
            }
            sender.sendMessage(manager.getSpawnDiagnostics(sender instanceof Player ? (Player) sender : null));
            return true;
        }

        if (!sender.hasPermission("animaldespawn.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission."); return true;
        }
        if (args.length == 0 || "status".equalsIgnoreCase(args[0])) {
            sender.sendMessage(ChatColor.YELLOW + "AnimalDespawn " + plugin.getDescription().getVersion());
            sender.sendMessage(ChatColor.GRAY + "Preset: " + ChatColor.WHITE + manager.getPreset());
            sender.sendMessage(ChatColor.GRAY + "Enabled: " + ChatColor.WHITE + manager.isEnabled());
            sender.sendMessage(ChatColor.GRAY + "Loaded: " + ChatColor.WHITE + manager.countLoadedAnimals());
            sender.sendMessage(ChatColor.GRAY + "Protected: " + ChatColor.WHITE + manager.countProtectedLoadedAnimals());
            sender.sendMessage(ChatColor.GRAY + "Eligible: " + ChatColor.WHITE + manager.countEligibleLoadedAnimals());
            sender.sendMessage(ChatColor.GRAY + "<32: " + ChatColor.WHITE + manager.countWithinDistance(0,32));
            sender.sendMessage(ChatColor.GRAY + "32-128: " + ChatColor.WHITE + manager.countWithinDistance(32,128));
            sender.sendMessage(ChatColor.GRAY + ">128: " + ChatColor.WHITE + manager.countBeyondMaximumDistance());
            sender.sendMessage(ChatColor.GRAY + "Old enough: " + ChatColor.WHITE + manager.countOldEnough());
            return true;
        }
        if ("reload".equalsIgnoreCase(args[0])) {
            plugin.reloadConfig(); manager.reload();
            sender.sendMessage(ChatColor.GREEN + "Configuration reloaded."); return true;
        }
        if ("scan".equalsIgnoreCase(args[0])) {
            int removed=manager.manualScan();
            sender.sendMessage(ChatColor.GREEN + "Manual scan complete. Removed approximately " + removed + " animals."); return true;
        }
        if ("inspect".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "This command must be used by a player."); return true;
            }
            Player p=(Player)sender;
            if (p.hasMetadata("animaldespawn-inspect")) {
                p.removeMetadata("animaldespawn-inspect", plugin);
                p.sendMessage(ChatColor.YELLOW + "Inspection mode disabled.");
            } else {
                p.setMetadata("animaldespawn-inspect", new FixedMetadataValue(plugin, true));
                p.sendMessage(ChatColor.GREEN + "Inspection mode enabled. Right-click an animal to inspect it.");
            }
            return true;
        }
        sender.sendMessage(ChatColor.YELLOW + "Usage: /animaldespawn [status|reload|scan|inspect|diagnose [reset]]");
        return true;
    }
}
