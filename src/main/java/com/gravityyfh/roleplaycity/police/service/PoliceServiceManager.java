package com.gravityyfh.roleplaycity.police.service;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.identity.data.Identity;
import com.gravityyfh.roleplaycity.identity.manager.IdentityManager;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownRole;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire du mode service pour les policiers
 * Gère l'état ON/OFF et le changement de skin basé sur le sexe de la carte d'identité
 */
public class PoliceServiceManager {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final IdentityManager identityManager;

    // Joueurs en service (UUID -> données de service)
    private final Map<UUID, PoliceServiceData> activeServices = new ConcurrentHashMap<>();

    // BossBars pour afficher le statut
    private final Map<UUID, BossBar> playerBossBars = new ConcurrentHashMap<>();

    // Configuration des skins (depuis config.yml)
    private String skinHomme;
    private String skinFemme;
    private String skinDefault;

    public PoliceServiceManager(RoleplayCity plugin, TownManager townManager, IdentityManager identityManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.identityManager = identityManager;
        loadConfig();
        startBossBarUpdateTask();
    }

    /**
     * Charge la configuration
     */
    public void loadConfig() {
        skinHomme = plugin.getConfig().getString("police-service.skin-homme", "policier");
        skinFemme = plugin.getConfig().getString("police-service.skin-femme", "policiere");
        skinDefault = plugin.getConfig().getString("police-service.skin-default", "policier");
    }

    /**
     * Toggle le service police pour un joueur
     * @return true si le service est maintenant actif, false sinon
     */
    public boolean toggleService(Player player) {
        UUID uuid = player.getUniqueId();

        if (isInService(uuid)) {
            deactivateService(player);
            return false;
        } else {
            return activateService(player);
        }
    }

    /**
     * Active le service police pour un joueur
     */
    public boolean activateService(Player player) {
        UUID uuid = player.getUniqueId();

        // Vérifier si déjà en service
        if (isInService(uuid)) {
            player.sendMessage(ChatColor.RED + "Vous êtes déjà en service !");
            return false;
        }

        // Vérifier que le joueur est policier dans une ville
        String townName = townManager.getPlayerTown(uuid);
        if (townName == null) {
            player.sendMessage(ChatColor.RED + "Vous devez être citoyen d'une ville.");
            return false;
        }

        Town town = townManager.getTown(townName);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Ville introuvable.");
            return false;
        }

        TownRole role = town.getMemberRole(uuid);
        if (role != TownRole.POLICIER) {
            player.sendMessage(ChatColor.RED + "Vous devez être policier pour prendre votre service.");
            return false;
        }

        // Vérifier la carte d'identité
        if (identityManager == null || !identityManager.hasIdentity(uuid)) {
            player.sendMessage(ChatColor.RED + "Vous devez avoir une carte d'identité pour prendre votre service.");
            return false;
        }

        Identity identity = identityManager.getIdentity(uuid);
        String sexe = identity.getSex();

        // Déterminer le skin à appliquer
        String skinToApply = determineSkin(sexe);

        // Créer les données de service
        PoliceServiceData data = new PoliceServiceData(uuid, player.getName(), townName, sexe);
        activeServices.put(uuid, data);

        // Appliquer le skin via SkinRestorer (commande console)
        applySkin(player, skinToApply);

        // Créer la BossBar
        createBossBar(player, townName);

        // Messages
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(ChatColor.GREEN + "  🚔 SERVICE POLICE ACTIVÉ");
        player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(ChatColor.GRAY + "  Ville: " + ChatColor.WHITE + townName);
        player.sendMessage(ChatColor.GRAY + "  Uniforme: " + ChatColor.WHITE + (sexe != null ? sexe : "Standard"));
        player.sendMessage(ChatColor.GRAY + "  Skin: " + ChatColor.WHITE + skinToApply);
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "  Vous pouvez maintenant exercer vos");
        player.sendMessage(ChatColor.YELLOW + "  fonctions de policier.");
        player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");

        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.0f);

        plugin.getLogger().info("[PoliceService] " + player.getName() + " a pris son service (Ville: " + townName + ", Sexe: " + sexe + ")");

        return true;
    }

    /**
     * Désactive le service police pour un joueur
     */
    public void deactivateService(Player player) {
        UUID uuid = player.getUniqueId();

        PoliceServiceData data = activeServices.remove(uuid);
        if (data == null) {
            player.sendMessage(ChatColor.RED + "Vous n'êtes pas en service !");
            return;
        }

        // Retirer la BossBar
        BossBar bossBar = playerBossBars.remove(uuid);
        if (bossBar != null) {
            bossBar.removePlayer(player);
        }

        // Restaurer le skin original via SkinRestorer
        restoreOriginalSkin(player);

        // Calculer la durée de service
        long duration = System.currentTimeMillis() - data.getStartTime();
        String durationStr = formatDuration(duration);

        // Messages
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(ChatColor.YELLOW + "  🚔 SERVICE POLICE TERMINÉ");
        player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(ChatColor.GRAY + "  Durée de service: " + ChatColor.WHITE + durationStr);
        player.sendMessage(ChatColor.GRAY + "  Votre apparence a été restaurée.");
        player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");

        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 0.5f);

        plugin.getLogger().info("[PoliceService] " + player.getName() + " a terminé son service (Durée: " + durationStr + ")");
    }

    /**
     * Détermine le skin à appliquer selon le sexe
     */
    private String determineSkin(String sexe) {
        if (sexe == null || sexe.isEmpty()) {
            return skinDefault;
        }

        String sexeLower = sexe.toLowerCase();
        if (sexeLower.equals("homme") || sexeLower.equals("male") || sexeLower.equals("m")) {
            return skinHomme;
        } else if (sexeLower.equals("femme") || sexeLower.equals("female") || sexeLower.equals("f")) {
            return skinFemme;
        }

        return skinDefault;
    }

    /**
     * Applique un skin via SkinRestorer (commande console)
     */
    private void applySkin(Player player, String skinName) {
        // Délai pour laisser le temps au message d'être affiché
        new BukkitRunnable() {
            @Override
            public void run() {
                String command = "skin set " + skinName + " " + player.getName();
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
        }.runTaskLater(plugin, 20L); // 1 seconde de délai
    }

    /**
     * Restaure le skin original du joueur
     */
    private void restoreOriginalSkin(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Commande pour restaurer le skin original (SkinRestorer)
                String command = "skin clear " + player.getName();
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
        }.runTaskLater(plugin, 20L);
    }

    /**
     * Crée et affiche la BossBar de service
     */
    private void createBossBar(Player player, String townName) {
        UUID uuid = player.getUniqueId();

        BossBar bossBar = Bukkit.createBossBar(
            ChatColor.BLUE + "🚔 EN SERVICE" + ChatColor.WHITE + " | " +
            ChatColor.GOLD + townName + ChatColor.WHITE + " | " +
            ChatColor.GRAY + "Police Municipale",
            BarColor.BLUE,
            BarStyle.SOLID
        );
        bossBar.setProgress(1.0);
        bossBar.addPlayer(player);
        playerBossBars.put(uuid, bossBar);
    }

    /**
     * Vérifie si un joueur est en service
     */
    public boolean isInService(UUID playerUUID) {
        return activeServices.containsKey(playerUUID);
    }

    /**
     * Récupère les données de service d'un joueur
     */
    public PoliceServiceData getServiceData(UUID playerUUID) {
        return activeServices.get(playerUUID);
    }

    /**
     * Force l'arrêt du service (déconnexion, etc.)
     */
    public void forceStopService(UUID playerUUID) {
        PoliceServiceData data = activeServices.remove(playerUUID);
        if (data != null) {
            BossBar bossBar = playerBossBars.remove(playerUUID);
            if (bossBar != null) {
                bossBar.removeAll();
            }

            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                restoreOriginalSkin(player);
            }
        }
    }

    /**
     * Nettoie les données lors de la déconnexion
     */
    public void cleanupPlayer(UUID playerUUID) {
        forceStopService(playerUUID);
    }

    /**
     * Tâche périodique pour mettre à jour les BossBars (durée)
     */
    private void startBossBarUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, PoliceServiceData> entry : activeServices.entrySet()) {
                    UUID uuid = entry.getKey();
                    PoliceServiceData data = entry.getValue();
                    BossBar bossBar = playerBossBars.get(uuid);

                    if (bossBar != null) {
                        long duration = System.currentTimeMillis() - data.getStartTime();
                        String durationStr = formatDuration(duration);

                        bossBar.setTitle(
                            ChatColor.BLUE + "🚔 EN SERVICE" + ChatColor.WHITE + " | " +
                            ChatColor.GOLD + data.getTownName() + ChatColor.WHITE + " | " +
                            ChatColor.YELLOW + durationStr
                        );
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Toutes les secondes
    }

    /**
     * Formate une durée en format lisible
     */
    private String formatDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%dh%02dm", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%dm%02ds", minutes, seconds % 60);
        } else {
            return seconds + "s";
        }
    }

    /**
     * Classe interne pour stocker les données de service
     */
    public static class PoliceServiceData {
        private final UUID playerUUID;
        private final String playerName;
        private final String townName;
        private final String sexe;
        private final long startTime;

        public PoliceServiceData(UUID playerUUID, String playerName, String townName, String sexe) {
            this.playerUUID = playerUUID;
            this.playerName = playerName;
            this.townName = townName;
            this.sexe = sexe;
            this.startTime = System.currentTimeMillis();
        }

        public UUID getPlayerUUID() { return playerUUID; }
        public String getPlayerName() { return playerName; }
        public String getTownName() { return townName; }
        public String getSexe() { return sexe; }
        public long getStartTime() { return startTime; }
    }
}
