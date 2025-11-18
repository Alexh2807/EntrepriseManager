package com.gravityyfh.roleplaycity.command;

import com.gravityyfh.roleplaycity.RoleplayCity;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.text.DecimalFormat;
import java.util.Map;

/**
 * FIX BASSE #30: Commande de diagnostic système
 *
 * Affiche des informations détaillées sur l'état du plugin et du serveur
 * pour faciliter le debugging et le support.
 *
 * Usage: /roleplaycity diagnostic
 */
public class DiagnosticCommand implements CommandExecutor {

    private final RoleplayCity plugin;
    private static final DecimalFormat DF = new DecimalFormat("#,##0.00");

    public DiagnosticCommand(RoleplayCity plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("roleplaycity.admin.diagnostic")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission d'utiliser cette commande.");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "DIAGNOSTIC SYSTÈME - RoleplayCity");
        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        // Section 1: Version et Serveur
        printServerInfo(sender);

        // Section 2: État du Plugin
        printPluginInfo(sender);

        // Section 3: Entreprises
        printEntrepriseInfo(sender);

        // Section 4: Villes
        printTownInfo(sender);

        // Section 5: Mémoire et Performance
        printPerformanceInfo(sender);

        // Section 6: Configuration
        printConfigInfo(sender);

        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage(ChatColor.GRAY + "Rapport généré à " + new java.util.Date());

        return true;
    }

    private void printServerInfo(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.AQUA + "▸ " + ChatColor.WHITE + ChatColor.BOLD + "SERVEUR");

        String bukkitVersion = Bukkit.getVersion();
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();

        sender.sendMessage(ChatColor.GRAY + "  Version: " + ChatColor.WHITE + bukkitVersion);
        sender.sendMessage(ChatColor.GRAY + "  Joueurs: " + ChatColor.WHITE +
            onlinePlayers + "/" + maxPlayers + getPlayerLoadIndicator(onlinePlayers, maxPlayers));
        sender.sendMessage(ChatColor.GRAY + "  Vault: " + ChatColor.WHITE +
            (RoleplayCity.getEconomy() != null ? ChatColor.GREEN + "✓ Connecté" : ChatColor.RED + "✗ Non disponible"));
    }

    private void printPluginInfo(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.AQUA + "▸ " + ChatColor.WHITE + ChatColor.BOLD + "PLUGIN");

        sender.sendMessage(ChatColor.GRAY + "  Version: " + ChatColor.WHITE + plugin.getDescription().getVersion());
        sender.sendMessage(ChatColor.GRAY + "  Debug: " + ChatColor.WHITE +
            (plugin.getDebugLogger().isDebugEnabled() ?
                ChatColor.GREEN + "✓ Activé" : ChatColor.YELLOW + "✗ Désactivé"));
        sender.sendMessage(ChatColor.GRAY + "  Métriques: " + ChatColor.WHITE +
            (plugin.getPerformanceMetrics().isEnabled() ?
                ChatColor.GREEN + "✓ Activées" : ChatColor.YELLOW + "✗ Désactivées"));
    }

    private void printEntrepriseInfo(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.AQUA + "▸ " + ChatColor.WHITE + ChatColor.BOLD + "ENTREPRISES");

        try {
            int totalEntreprises = plugin.getEntrepriseManagerLogic().getEntreprises().size();
            // Note: ShopManager n'a pas de méthode publique getAllShops()
            // Pour l'instant, on ne l'affiche pas

            sender.sendMessage(ChatColor.GRAY + "  Total: " + ChatColor.WHITE + totalEntreprises);
            // sender.sendMessage(ChatColor.GRAY + "  Boutiques: " + ChatColor.WHITE + totalShops);

            // Calculer la mémoire approximative
            long entrepriseMemory = estimateEntrepriseMemory(totalEntreprises);
            sender.sendMessage(ChatColor.GRAY + "  Mémoire estimée: " + ChatColor.WHITE +
                formatBytes(entrepriseMemory));
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "  Erreur lors de la collecte des données entreprises");
            plugin.getDebugLogger().error("Diagnostic - Entreprises", e);
        }
    }

    private void printTownInfo(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.AQUA + "▸ " + ChatColor.WHITE + ChatColor.BOLD + "VILLES");

        try {
            int totalTowns = plugin.getTownManager().getAllTowns().size();
            int totalPlots = plugin.getTownManager().getAllTowns().stream()
                .mapToInt(town -> town.getPlots().size())
                .sum();
            int totalMembers = plugin.getTownManager().getAllTowns().stream()
                .mapToInt(town -> town.getMemberCount())
                .sum();

            sender.sendMessage(ChatColor.GRAY + "  Total: " + ChatColor.WHITE + totalTowns);
            sender.sendMessage(ChatColor.GRAY + "  Parcelles: " + ChatColor.WHITE + totalPlots);
            sender.sendMessage(ChatColor.GRAY + "  Membres: " + ChatColor.WHITE + totalMembers);

            // Calculer la mémoire approximative
            long townMemory = estimateTownMemory(totalTowns, totalPlots, totalMembers);
            sender.sendMessage(ChatColor.GRAY + "  Mémoire estimée: " + ChatColor.WHITE +
                formatBytes(townMemory));
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "  Erreur lors de la collecte des données villes");
            plugin.getDebugLogger().error("Diagnostic - Villes", e);
        }
    }

    private void printPerformanceInfo(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.AQUA + "▸ " + ChatColor.WHITE + ChatColor.BOLD + "PERFORMANCE");

        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();

        long usedMemory = heapUsage.getUsed();
        long maxMemory = heapUsage.getMax();
        double usagePercent = (double) usedMemory / maxMemory * 100;

        sender.sendMessage(ChatColor.GRAY + "  Mémoire JVM: " + ChatColor.WHITE +
            formatBytes(usedMemory) + " / " + formatBytes(maxMemory) +
            getMemoryIndicator(usagePercent));
        sender.sendMessage(ChatColor.GRAY + "  Utilisation: " + ChatColor.WHITE +
            DF.format(usagePercent) + "%");

        // Afficher les tâches async
        try {
            int activeTasks = Bukkit.getScheduler().getActiveWorkers().size();
            int pendingTasks = Bukkit.getScheduler().getPendingTasks().size();

            sender.sendMessage(ChatColor.GRAY + "  Tâches actives: " + ChatColor.WHITE + activeTasks);
            sender.sendMessage(ChatColor.GRAY + "  Tâches en attente: " + ChatColor.WHITE + pendingTasks);
        } catch (Exception e) {
            // Ignorer silencieusement
        }
    }

    private void printConfigInfo(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.AQUA + "▸ " + ChatColor.WHITE + ChatColor.BOLD + "CONFIGURATION");

        sender.sendMessage(ChatColor.GRAY + "  Max entreprises/gérant: " + ChatColor.WHITE +
            plugin.getConfig().getInt("finance.max-entreprises-par-gerant", 1));
        sender.sendMessage(ChatColor.GRAY + "  Max emplois/joueur: " + ChatColor.WHITE +
            plugin.getConfig().getInt("finance.max-travail-joueur", 1));
        sender.sendMessage(ChatColor.GRAY + "  Taux de taxe: " + ChatColor.WHITE +
            plugin.getConfig().getDouble("finance.pourcentage-taxes", 15.0) + "%");
        sender.sendMessage(ChatColor.GRAY + "  Alloc. chômage: " + ChatColor.WHITE +
            plugin.getConfig().getDouble("finance.allocation-chomage-horaire", 0) + "€/h");
    }

    // Méthodes utilitaires

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private long estimateEntrepriseMemory(int count) {
        // Estimation grossière: ~5KB par entreprise (objets + collections)
        return count * 5 * 1024L;
    }

    private long estimateTownMemory(int towns, int plots, int members) {
        // Estimation: 10KB/ville + 2KB/parcelle + 500B/membre
        return (towns * 10 * 1024L) + (plots * 2 * 1024L) + (members * 512L);
    }

    private String getPlayerLoadIndicator(int online, int max) {
        double ratio = (double) online / max;
        if (ratio < 0.5) return ChatColor.GREEN + " ●";
        if (ratio < 0.75) return ChatColor.YELLOW + " ●";
        return ChatColor.RED + " ●";
    }

    private String getMemoryIndicator(double percent) {
        if (percent < 60) return ChatColor.GREEN + " ●";
        if (percent < 80) return ChatColor.YELLOW + " ●";
        return ChatColor.RED + " ●";
    }
}
