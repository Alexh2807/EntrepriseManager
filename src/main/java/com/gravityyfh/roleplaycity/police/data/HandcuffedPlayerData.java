package com.gravityyfh.roleplaycity.police.data;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gestion des données des joueurs menottés
 * Stocke l'état des menottes, la santé de casse, et le système de suivi
 */
public class HandcuffedPlayerData {

    // Joueurs actuellement menottés
    private final Map<UUID, Boolean> handcuffedPlayers = new HashMap<>();

    // Boss bars pour la santé des menottes
    private final Map<UUID, BossBar> handcuffsBossBars = new HashMap<>();

    // Santé des menottes (0.0 à 1.0)
    private final Map<UUID, Double> handcuffsHealth = new HashMap<>();

    // Système de suivi : UUID menotteur -> UUID menotté
    private final Map<UUID, UUID> followingMap = new HashMap<>();

    // Historique des positions pour le suivi fluide
    private final Map<UUID, java.util.Queue<org.bukkit.Location>> pathHistory = new HashMap<>();

    /**
     * Vérifie si un joueur est menotté
     */
    public boolean isHandcuffed(UUID uuid) {
        return handcuffedPlayers.getOrDefault(uuid, false);
    }

    /**
     * Vérifie si un joueur est menotté (helper pour Player)
     */
    public boolean isPlayerHandcuffed(Player player) {
        return isHandcuffed(player.getUniqueId());
    }

    /**
     * Menotte un joueur
     */
    public void handcuff(Player player, String bossBarTitle) {
        UUID uuid = player.getUniqueId();
        handcuffedPlayers.put(uuid, true);
        handcuffsHealth.put(uuid, 1.0); // Santé complète

        // Créer la boss bar
        BossBar bossBar = Bukkit.createBossBar(
            bossBarTitle,
            BarColor.WHITE,
            BarStyle.SOLID
        );
        bossBar.setProgress(1.0);
        bossBar.addPlayer(player);
        bossBar.setVisible(true);

        handcuffsBossBars.put(uuid, bossBar);

        // Empêcher la chute (permettre le vol)
        player.setAllowFlight(true);
        player.setFlying(false);
    }

    /**
     * Charge un joueur menotté (Persistence)
     * Ne crée pas de BossBar (sera fait au join)
     */
    public void loadHandcuffed(UUID uuid, double health) {
        handcuffedPlayers.put(uuid, true);
        handcuffsHealth.put(uuid, health);
    }

    /**
     * Récupère toutes les données pour la sauvegarde
     */
    public Map<UUID, Double> getAllHandcuffedData() {
        return new HashMap<>(handcuffsHealth);
    }

    /**
     * Réapplique les effets visuels (BossBar, Fly) pour un joueur déjà menotté
     * Appelé au PlayerJoinEvent
     */
    public void reapplyHandcuffs(Player player) {
        UUID uuid = player.getUniqueId();
        if (!isHandcuffed(uuid)) return;

        double health = handcuffsHealth.getOrDefault(uuid, 1.0);
        String bossBarTitle = "§fSanté des Menottes - Shift pour affaiblir"; // TODO: Utiliser config

        // Recréer la BossBar
        BossBar bossBar = Bukkit.createBossBar(
            bossBarTitle,
            BarColor.WHITE,
            BarStyle.SOLID
        );
        bossBar.setProgress(health);
        
        // Couleur selon santé
        if (health <= 0.25) bossBar.setColor(BarColor.RED);
        else if (health <= 0.5) bossBar.setColor(BarColor.YELLOW);
        
        bossBar.addPlayer(player);
        bossBar.setVisible(true);
        handcuffsBossBars.put(uuid, bossBar);

        // Réappliquer vol (anti-chute)
        player.setAllowFlight(true);
        player.setFlying(false);
    }

    /**
     * Libère un joueur des menottes
     */
    public void removeHandcuffs(Player player) {
        UUID uuid = player.getUniqueId();
        handcuffedPlayers.remove(uuid);
        handcuffsHealth.remove(uuid);

        // Retirer la boss bar
        BossBar bossBar = handcuffsBossBars.remove(uuid);
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar.setVisible(false);
        }

        // Retirer du suivi
        stopFollowing(uuid);

        // Retirer l'historique de positions
        pathHistory.remove(uuid);

        // Réinitialiser le vol si le joueur n'est pas en mode créatif/spectateur
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE &&
            player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
            player.setAllowFlight(false);
            player.setFlying(false);
        }
    }

    /**
     * Diminue la santé des menottes lors d'un sneak
     * @param decreaseAmount Montant à diminuer (ex: 0.005 = 0.5%)
     * @return true si les menottes sont cassées
     */
    public boolean damageHandcuffs(Player player, double decreaseAmount) {
        UUID uuid = player.getUniqueId();
        if (!isHandcuffed(uuid)) {
            return false;
        }

        double currentHealth = handcuffsHealth.getOrDefault(uuid, 1.0);
        double newHealth = Math.max(0.0, currentHealth - decreaseAmount);

        handcuffsHealth.put(uuid, newHealth);

        // Mettre à jour la boss bar
        BossBar bossBar = handcuffsBossBars.get(uuid);
        if (bossBar != null) {
            bossBar.setProgress(newHealth);

            // Changer la couleur selon la santé
            if (newHealth <= 0.25) {
                bossBar.setColor(BarColor.RED);
            } else if (newHealth <= 0.5) {
                bossBar.setColor(BarColor.YELLOW);
            }
        }

        // Vérifier si cassé
        if (newHealth <= 0.0) {
            removeHandcuffs(player);
            return true;
        }

        return false;
    }

    /**
     * Obtient la santé actuelle des menottes (0.0 à 1.0)
     */
    public double getHandcuffsHealth(UUID uuid) {
        return handcuffsHealth.getOrDefault(uuid, 1.0);
    }

    /**
     * Démarre le suivi d'un joueur menotté
     */
    public void startFollowing(UUID handcuffer, UUID handcuffed) {
        followingMap.put(handcuffer, handcuffed);

        // Initialiser l'historique de positions
        pathHistory.put(handcuffer, new java.util.LinkedList<>());
    }

    /**
     * Arrête le suivi
     */
    public void stopFollowing(UUID handcuffer) {
        followingMap.remove(handcuffer);
        pathHistory.remove(handcuffer);
    }

    /**
     * Vérifie si un joueur fait suivre quelqu'un
     */
    public boolean isFollowing(UUID handcuffer) {
        return followingMap.containsKey(handcuffer);
    }

    /**
     * Obtient le joueur suivi par ce handcuffer
     */
    public UUID getFollowedPlayer(UUID handcuffer) {
        return followingMap.get(handcuffer);
    }

    /**
     * Obtient tous les joueurs en train de suivre
     */
    public Map<UUID, UUID> getFollowingMap() {
        return new HashMap<>(followingMap);
    }

    /**
     * Ajoute une position à l'historique de chemin
     */
    public void addPathLocation(UUID handcuffer, org.bukkit.Location location) {
        java.util.Queue<org.bukkit.Location> queue = pathHistory.get(handcuffer);
        if (queue != null) {
            queue.add(location);

            // Limiter à 20 positions
            while (queue.size() > 20) {
                queue.poll();
            }
        }
    }

    /**
     * Obtient une position de l'historique (10 pas en arrière)
     */
    public org.bukkit.Location getPathLocation(UUID handcuffer, int stepsBack) {
        java.util.Queue<org.bukkit.Location> queue = pathHistory.get(handcuffer);
        if (queue == null || queue.isEmpty()) {
            return null;
        }

        java.util.List<org.bukkit.Location> list = new java.util.ArrayList<>(queue);
        int index = Math.max(0, list.size() - stepsBack);

        if (index < list.size()) {
            return list.get(index);
        }

        return null;
    }

    /**
     * Nettoie toutes les données
     */
    public void clear() {
        // Retirer toutes les boss bars
        handcuffsBossBars.values().forEach(bossBar -> {
            bossBar.removeAll();
            bossBar.setVisible(false);
        });

        // Réinitialiser le vol pour tous les joueurs menottés
        handcuffedPlayers.keySet().forEach(uuid -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                if (player.getGameMode() != org.bukkit.GameMode.CREATIVE &&
                    player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                    player.setAllowFlight(false);
                    player.setFlying(false);
                }
            }
        });

        handcuffedPlayers.clear();
        handcuffsBossBars.clear();
        handcuffsHealth.clear();
        followingMap.clear();
        pathHistory.clear();
    }

    /**
     * Obtient le nombre de joueurs menottés
     */
    public int getHandcuffedCount() {
        return handcuffedPlayers.size();
    }
}
