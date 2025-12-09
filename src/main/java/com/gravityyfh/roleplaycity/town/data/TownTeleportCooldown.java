package com.gravityyfh.roleplaycity.town.data;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gestion de la téléportation vers les villes avec warm-up de 5 secondes
 * Le joueur doit rester immobile 5 secondes avant d'être téléporté
 */
public class TownTeleportCooldown {

    private final org.bukkit.plugin.Plugin plugin;

    // UUID -> TeleportWarmup
    private final Map<UUID, TeleportWarmup> activeWarmups = new HashMap<>();

    public TownTeleportCooldown(org.bukkit.plugin.Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Classe interne pour gérer un warm-up de téléportation
     */
    private static class TeleportWarmup {
        final Location startLocation;
        final Location destination;
        final BukkitTask task;
        int secondsRemaining;

        TeleportWarmup(Location startLocation, Location destination, BukkitTask task, int seconds) {
            this.startLocation = startLocation;
            this.destination = destination;
            this.task = task;
            this.secondsRemaining = seconds;
        }
    }

    /**
     * Démarre un warm-up de téléportation de 5 secondes
     * @param player Le joueur à téléporter
     * @param destination La destination
     * @param townName Le nom de la ville (pour le message)
     * @return true si le warm-up a démarré, false si déjà en cours
     */
    public boolean startTeleportWarmup(Player player, Location destination, String townName) {
        UUID uuid = player.getUniqueId();

        // Annuler le warm-up existant si présent
        if (activeWarmups.containsKey(uuid)) {
            player.sendMessage(org.bukkit.ChatColor.RED + "Téléportation déjà en cours !");
            return false;
        }

        Location startLocation = player.getLocation().clone();

        // Créer le BukkitRunnable pour le compte à rebours
        BukkitTask task = new BukkitRunnable() {
            int countdown = 5;

            @Override
            public void run() {
                TeleportWarmup warmup = activeWarmups.get(uuid);
                if (warmup == null) {
                    cancel();
                    return;
                }

                // Vérifier si le joueur a bougé
                Location currentLoc = player.getLocation();
                if (hasMoved(warmup.startLocation, currentLoc)) {
                    player.sendMessage(org.bukkit.ChatColor.RED + "✗ Téléportation annulée - vous avez bougé !");
                    cancelTeleport(uuid);
                    cancel();
                    return;
                }

                if (countdown > 0) {
                    // Message de compte à rebours
                    player.sendMessage(org.bukkit.ChatColor.YELLOW + "⏱ Téléportation dans " +
                        org.bukkit.ChatColor.GOLD + countdown + org.bukkit.ChatColor.YELLOW + "s... " +
                        org.bukkit.ChatColor.GRAY + "(Ne bougez pas!)");
                    countdown--;
                } else {
                    // Téléportation !
                    player.teleport(destination);
                    player.sendMessage("");
                    player.sendMessage(org.bukkit.ChatColor.GREEN + "✓ Téléportation vers " +
                        org.bukkit.ChatColor.GOLD + townName + org.bukkit.ChatColor.GREEN + " réussie !");
                    player.sendMessage("");

                    activeWarmups.remove(uuid);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Toutes les secondes (20 ticks)

        // Enregistrer le warm-up
        activeWarmups.put(uuid, new TeleportWarmup(startLocation, destination, task, 5));

        player.sendMessage("");
        player.sendMessage(org.bukkit.ChatColor.GOLD + "⏱ Téléportation vers " + org.bukkit.ChatColor.YELLOW + townName +
            org.bukkit.ChatColor.GOLD + " dans 5 secondes...");
        player.sendMessage(org.bukkit.ChatColor.GRAY + "Restez immobile pendant le chargement !");
        player.sendMessage("");

        return true;
    }

    /**
     * Vérifie si le joueur a bougé de sa position initiale
     */
    private boolean hasMoved(Location start, Location current) {
        if (!start.getWorld().equals(current.getWorld())) {
            return true;
        }

        // Tolérance de 0.1 bloc pour éviter les micro-mouvements dus au serveur
        double distance = start.distance(current);
        return distance > 0.1;
    }

    /**
     * Vérifie si un joueur est en train de se téléporter
     */
    public boolean isWarmingUp(Player player) {
        return activeWarmups.containsKey(player.getUniqueId());
    }

    /**
     * Annule la téléportation d'un joueur
     */
    public void cancelTeleport(UUID uuid) {
        TeleportWarmup warmup = activeWarmups.remove(uuid);
        if (warmup != null) {
            warmup.task.cancel();
        }
    }

    /**
     * Annule la téléportation d'un joueur
     */
    public void cancelTeleport(Player player) {
        cancelTeleport(player.getUniqueId());
    }

    /**
     * Annule toutes les téléportations en cours
     */
    public void cancelAll() {
        for (TeleportWarmup warmup : activeWarmups.values()) {
            warmup.task.cancel();
        }
        activeWarmups.clear();
    }

    /**
     * Récupère le nombre de téléportations en cours
     */
    public int getActiveWarmups() {
        return activeWarmups.size();
    }
}
