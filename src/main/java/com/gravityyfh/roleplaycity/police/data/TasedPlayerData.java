package com.gravityyfh.roleplaycity.police.data;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gestion des données des joueurs tasés
 * Stocke l'état de tase, le cooldown, et les tâches planifiées
 */
public class TasedPlayerData {

    // Joueurs actuellement tasés avec timestamp de fin
    private final Map<UUID, Long> tasedPlayers = new HashMap<>();

    // Cooldown d'utilisation du taser par joueur
    private final Map<UUID, Long> taserCooldowns = new HashMap<>();

    // Tâches de suppression automatique
    private final Map<UUID, BukkitTask> tasedTasks = new HashMap<>();

    /**
     * Vérifie si un joueur est actuellement tasé
     */
    public boolean isTased(Player player) {
        return isTased(player.getUniqueId());
    }

    public boolean isTased(UUID uuid) {
        if (!tasedPlayers.containsKey(uuid)) {
            return false;
        }

        long endTime = tasedPlayers.get(uuid);
        if (System.currentTimeMillis() > endTime) {
            // Expiré, nettoyer
            removeTased(uuid);
            return false;
        }

        return true;
    }

    /**
     * Ajoute un joueur à la liste des tasés
     */
    public void addTased(UUID uuid, int durationSeconds) {
        long endTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        tasedPlayers.put(uuid, endTime);
    }

    public void addTased(Player player, int durationSeconds) {
        addTased(player.getUniqueId(), durationSeconds);
    }

    /**
     * Retire un joueur de la liste des tasés
     */
    public void removeTased(UUID uuid) {
        tasedPlayers.remove(uuid);

        // Annuler la tâche si elle existe
        BukkitTask task = tasedTasks.remove(uuid);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    public void removeTased(Player player) {
        removeTased(player.getUniqueId());
    }

    /**
     * Enregistre une tâche de suppression automatique
     */
    public void setTasedTask(UUID uuid, BukkitTask task) {
        // Annuler l'ancienne tâche si elle existe
        BukkitTask oldTask = tasedTasks.get(uuid);
        if (oldTask != null && !oldTask.isCancelled()) {
            oldTask.cancel();
        }

        tasedTasks.put(uuid, task);
    }

    /**
     * Vérifie si un joueur est en cooldown pour utiliser le taser
     */
    public boolean isOnCooldown(Player player) {
        return isOnCooldown(player.getUniqueId());
    }

    public boolean isOnCooldown(UUID uuid) {
        if (!taserCooldowns.containsKey(uuid)) {
            return false;
        }

        long endTime = taserCooldowns.get(uuid);
        if (System.currentTimeMillis() > endTime) {
            taserCooldowns.remove(uuid);
            return false;
        }

        return true;
    }

    /**
     * Obtient le temps restant du cooldown en secondes
     */
    public int getCooldownRemaining(Player player) {
        return getCooldownRemaining(player.getUniqueId());
    }

    public int getCooldownRemaining(UUID uuid) {
        if (!taserCooldowns.containsKey(uuid)) {
            return 0;
        }

        long endTime = taserCooldowns.get(uuid);
        long remaining = endTime - System.currentTimeMillis();

        if (remaining <= 0) {
            taserCooldowns.remove(uuid);
            return 0;
        }

        return (int) Math.ceil(remaining / 1000.0);
    }

    /**
     * Ajoute un cooldown pour un joueur
     */
    public void addCooldown(UUID uuid, int cooldownSeconds) {
        long endTime = System.currentTimeMillis() + (cooldownSeconds * 1000L);
        taserCooldowns.put(uuid, endTime);
    }

    public void addCooldown(Player player, int cooldownSeconds) {
        addCooldown(player.getUniqueId(), cooldownSeconds);
    }

    /**
     * Retire le cooldown d'un joueur
     */
    public void removeCooldown(UUID uuid) {
        taserCooldowns.remove(uuid);
    }

    public void removeCooldown(Player player) {
        removeCooldown(player.getUniqueId());
    }

    /**
     * Nettoie toutes les données expirées
     */
    public void cleanupExpired() {
        // Nettoyer les joueurs tasés expirés
        tasedPlayers.entrySet().removeIf(entry ->
            System.currentTimeMillis() > entry.getValue()
        );

        // Nettoyer les cooldowns expirés
        taserCooldowns.entrySet().removeIf(entry ->
            System.currentTimeMillis() > entry.getValue()
        );
    }

    /**
     * Nettoie toutes les données
     */
    public void clear() {
        // Annuler toutes les tâches
        tasedTasks.values().forEach(task -> {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        });

        tasedPlayers.clear();
        taserCooldowns.clear();
        tasedTasks.clear();
    }

    /**
     * Obtient le nombre de joueurs actuellement tasés
     */
    public int getTasedCount() {
        cleanupExpired();
        return tasedPlayers.size();
    }
}
