package com.gravityyfh.roleplaycity.service;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.database.ConnectionManager;
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

import java.sql.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Gestionnaire unifié des services professionnels
 * Gère Police, Médical, Juge et Entreprise avec exclusivité
 */
public class ProfessionalServiceManager {

    private final RoleplayCity plugin;
    private final ConnectionManager connectionManager;
    private final TownManager townManager;
    private final IdentityManager identityManager;

    // Sessions actives en mémoire
    private final Map<UUID, ProfessionalServiceData> activeSessions = new ConcurrentHashMap<>();

    // BossBars pour afficher le statut
    private final Map<UUID, BossBar> playerBossBars = new ConcurrentHashMap<>();

    public ProfessionalServiceManager(RoleplayCity plugin, ConnectionManager connectionManager,
                                       TownManager townManager, IdentityManager identityManager) {
        this.plugin = plugin;
        this.connectionManager = connectionManager;
        this.townManager = townManager;
        this.identityManager = identityManager;

        initializeTable();
        loadActiveSessions();
        startBossBarUpdateTask();
    }

    /**
     * Crée la table si elle n'existe pas
     */
    private void initializeTable() {
        try (Connection conn = connectionManager.getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS professional_services (
                    player_uuid TEXT PRIMARY KEY,
                    player_name TEXT NOT NULL,
                    service_type TEXT NOT NULL,
                    town_name TEXT,
                    enterprise_name TEXT,
                    sexe TEXT,
                    skin_applied TEXT,
                    start_time INTEGER NOT NULL,
                    last_disconnect_time INTEGER
                )
                """);

            // Migration: Ajouter la colonne last_disconnect_time si elle n'existe pas
            try {
                stmt.execute("ALTER TABLE professional_services ADD COLUMN last_disconnect_time INTEGER");
                plugin.getLogger().info("[ProfessionalService] Colonne last_disconnect_time ajoutée");
            } catch (SQLException ignored) {
                // La colonne existe déjà, ignorer
            }

            plugin.getLogger().info("[ProfessionalService] Table initialisée");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[ProfessionalService] Erreur création table", e);
        }
    }

    /**
     * Charge les sessions actives depuis la DB au démarrage
     */
    private void loadActiveSessions() {
        try (Connection conn = connectionManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM professional_services")) {

            int count = 0;
            while (rs.next()) {
                UUID playerUUID = UUID.fromString(rs.getString("player_uuid"));
                String playerName = rs.getString("player_name");
                ProfessionalServiceType type = ProfessionalServiceType.valueOf(rs.getString("service_type"));
                String townName = rs.getString("town_name");
                String enterpriseName = rs.getString("enterprise_name");
                String sexe = rs.getString("sexe");
                String skinApplied = rs.getString("skin_applied");
                long startTime = rs.getLong("start_time");
                Long lastDisconnectTime = rs.getObject("last_disconnect_time") != null
                    ? rs.getLong("last_disconnect_time") : null;

                ProfessionalServiceData data = new ProfessionalServiceData(
                    playerUUID, playerName, type, townName, sexe, skinApplied, startTime, enterpriseName
                );
                // Restaurer le timestamp de déconnexion
                if (lastDisconnectTime != null) {
                    data.setLastDisconnectTime(lastDisconnectTime);
                }
                activeSessions.put(playerUUID, data);
                count++;
            }

            plugin.getLogger().info("[ProfessionalService] " + count + " sessions restaurées");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[ProfessionalService] Erreur chargement sessions", e);
        }
    }

    /**
     * Vérifie si un joueur est en service (tous types)
     */
    public boolean isInAnyService(UUID playerUUID) {
        return activeSessions.containsKey(playerUUID);
    }

    /**
     * Vérifie si un joueur est en service pour un type spécifique
     */
    public boolean isInService(UUID playerUUID, ProfessionalServiceType type) {
        ProfessionalServiceData data = activeSessions.get(playerUUID);
        return data != null && data.getServiceType() == type;
    }

    /**
     * Récupère les données de service d'un joueur
     */
    public ProfessionalServiceData getServiceData(UUID playerUUID) {
        return activeSessions.get(playerUUID);
    }

    /**
     * Récupère le type de service actif d'un joueur
     */
    public ProfessionalServiceType getActiveServiceType(UUID playerUUID) {
        ProfessionalServiceData data = activeSessions.get(playerUUID);
        return data != null ? data.getServiceType() : null;
    }

    /**
     * Toggle le service pour un joueur
     * @return true si le service est maintenant actif, false sinon
     */
    public boolean toggleService(Player player, ProfessionalServiceType type) {
        UUID uuid = player.getUniqueId();

        if (isInService(uuid, type)) {
            deactivateService(player);
            return false;
        } else {
            return activateService(player, type, null);
        }
    }

    /**
     * Active un service pour un joueur
     */
    public boolean activateService(Player player, ProfessionalServiceType type, String enterpriseName) {
        UUID uuid = player.getUniqueId();

        // Vérifier si déjà en service (exclusivité)
        if (isInAnyService(uuid)) {
            ProfessionalServiceData current = activeSessions.get(uuid);
            String serviceActuel = current.getServiceType().getDisplayName();
            if (current.getServiceType() == ProfessionalServiceType.ENTERPRISE) {
                serviceActuel = "Entreprise: " + current.getEnterpriseName();
            }
            player.sendMessage(ChatColor.RED + "Vous êtes déjà en service ! (" + serviceActuel + ")");
            player.sendMessage(ChatColor.RED + "Quittez d'abord votre service actuel.");
            return false;
        }

        // Validation selon le type
        String townName = null;
        String sexe = null;
        String skinToApply = null;

        if (type != ProfessionalServiceType.ENTERPRISE) {
            // Services municipaux - vérifier ville et rôle
            townName = townManager.getPlayerTown(uuid);
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
            if (!isRoleAuthorized(role, type)) {
                player.sendMessage(ChatColor.RED + "Vous n'avez pas le rôle requis pour ce service.");
                return false;
            }

            // Vérifier la carte d'identité pour le skin
            if (type.usesSkin() && identityManager != null && identityManager.hasIdentity(uuid)) {
                Identity identity = identityManager.getIdentity(uuid);
                sexe = identity.getSex();
                skinToApply = determineSkin(type, sexe);
            }
        }

        // Créer les données de session
        ProfessionalServiceData data;
        if (type == ProfessionalServiceType.ENTERPRISE) {
            data = new ProfessionalServiceData(uuid, player.getName(), enterpriseName);
        } else {
            data = new ProfessionalServiceData(uuid, player.getName(), type, townName, sexe, skinToApply);
        }

        // Sauvegarder en mémoire et DB
        activeSessions.put(uuid, data);
        saveToDatabase(data);

        // Appliquer le skin si nécessaire
        if (skinToApply != null) {
            applySkin(player, skinToApply);
        }

        // Créer la BossBar
        createBossBar(player, data);

        // Message de confirmation
        sendServiceActivatedMessage(player, type, data);

        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.0f);

        plugin.getLogger().info("[ProfessionalService] " + player.getName() + " a pris son service " + type.name() +
            (townName != null ? " (Ville: " + townName + ")" : "") +
            (enterpriseName != null ? " (Entreprise: " + enterpriseName + ")" : ""));

        return true;
    }

    /**
     * Désactive le service d'un joueur
     */
    public void deactivateService(Player player) {
        UUID uuid = player.getUniqueId();

        ProfessionalServiceData data = activeSessions.remove(uuid);
        if (data == null) {
            player.sendMessage(ChatColor.RED + "Vous n'êtes pas en service !");
            return;
        }

        // Retirer la BossBar
        BossBar bossBar = playerBossBars.remove(uuid);
        if (bossBar != null) {
            bossBar.removePlayer(player);
        }

        // Supprimer de la DB
        deleteFromDatabase(uuid);

        // Restaurer le skin
        if (data.getSkinApplied() != null) {
            restoreOriginalSkin(player);
        }

        // Message de confirmation
        sendServiceDeactivatedMessage(player, data);

        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 0.5f);

        plugin.getLogger().info("[ProfessionalService] " + player.getName() + " a quitté son service " +
            data.getServiceType().name() + " (Durée: " + formatDuration(data.getDuration()) + ")");
    }

    /**
     * Force l'arrêt du service (déconnexion, suppression de rôle, etc.)
     */
    public void forceStopService(UUID playerUUID) {
        ProfessionalServiceData data = activeSessions.remove(playerUUID);
        if (data != null) {
            BossBar bossBar = playerBossBars.remove(playerUUID);
            if (bossBar != null) {
                bossBar.removeAll();
            }

            // Note: On ne supprime PAS de la DB pour permettre la restauration à la reconnexion
            // La suppression se fait uniquement via deactivateService() (action manuelle)

            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                restoreOriginalSkin(player);
            }
        }
    }

    /**
     * Restaure le service à la reconnexion
     */
    public void restoreServiceOnJoin(Player player) {
        UUID uuid = player.getUniqueId();
        ProfessionalServiceData data = activeSessions.get(uuid);

        if (data != null) {
            // Recréer la BossBar
            createBossBar(player, data);

            // Réappliquer le skin
            if (data.getSkinApplied() != null) {
                applySkin(player, data.getSkinApplied());
            }

            player.sendMessage("");
            player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage(ChatColor.GREEN + "  " + data.getServiceType().getEmoji() + " SERVICE RESTAURÉ");
            player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage(ChatColor.GRAY + "  Type: " + ChatColor.WHITE + data.getServiceType().getDisplayName());
            player.sendMessage(ChatColor.GRAY + "  Lieu: " + ChatColor.WHITE + data.getLocationName());
            player.sendMessage(ChatColor.GRAY + "  Durée: " + ChatColor.WHITE + formatDuration(data.getDuration()));
            player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("");

            plugin.getLogger().info("[ProfessionalService] Service restauré pour " + player.getName());
        }
    }

    /**
     * Nettoie les données lors de la déconnexion (BossBar seulement)
     */
    public void cleanupOnQuit(UUID playerUUID) {
        BossBar bossBar = playerBossBars.remove(playerUUID);
        if (bossBar != null) {
            bossBar.removeAll();
        }
        // Note: On garde les données en mémoire et DB pour restauration
    }

    // ========================================
    // GESTION TIMEOUT ET VALIDATION RÔLE
    // ========================================

    /** Délai en millisecondes avant désactivation automatique (10 minutes par défaut) */
    private static final long DEFAULT_OFFLINE_TIMEOUT_MS = 10 * 60 * 1000;

    /**
     * Récupère le délai de timeout configuré
     */
    private long getOfflineTimeoutMs() {
        int minutes = plugin.getConfig().getInt("professional-service.offline-timeout-minutes", 10);
        return minutes * 60 * 1000L;
    }

    /**
     * Enregistre le timestamp de déconnexion d'un joueur
     * Appelé par le listener à la déconnexion
     */
    public void recordDisconnectTime(UUID playerUUID) {
        ProfessionalServiceData data = activeSessions.get(playerUUID);
        if (data != null) {
            data.setLastDisconnectTime(System.currentTimeMillis());
            updateDisconnectTimeInDatabase(playerUUID, data.getLastDisconnectTime());
            plugin.getLogger().info("[ProfessionalService] Déconnexion enregistrée pour " + data.getPlayerName());
        }
    }

    /**
     * Vérifie si le service a expiré et le désactive si nécessaire
     * Appelé à la reconnexion AVANT la restauration du service
     * @return true si le service a été désactivé (expiré), false sinon
     */
    public boolean checkAndDeactivateIfExpired(Player player) {
        UUID uuid = player.getUniqueId();
        ProfessionalServiceData data = activeSessions.get(uuid);

        if (data == null) {
            return false;
        }

        long timeoutMs = getOfflineTimeoutMs();
        if (data.isExpired(timeoutMs)) {
            // Service expiré - désactiver
            String serviceName = data.getServiceType().getDisplayName();

            // Supprimer le service
            activeSessions.remove(uuid);
            deleteFromDatabase(uuid);

            // Retirer le skin
            if (data.getSkinApplied() != null) {
                restoreOriginalSkin(player);
            }

            // Message au joueur
            long minutesAbsent = (System.currentTimeMillis() - data.getLastDisconnectTime()) / (60 * 1000);
            player.sendMessage("");
            player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage(ChatColor.RED + "  ⚠ SERVICE TERMINÉ AUTOMATIQUEMENT");
            player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage(ChatColor.GRAY + "  Service: " + ChatColor.WHITE + serviceName);
            player.sendMessage(ChatColor.GRAY + "  Raison: " + ChatColor.YELLOW + "Absence > 10 minutes");
            player.sendMessage(ChatColor.GRAY + "  Durée absence: " + ChatColor.WHITE + minutesAbsent + " min");
            player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("");

            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);

            plugin.getLogger().info("[ProfessionalService] Service " + serviceName +
                " expiré pour " + player.getName() + " (absent " + minutesAbsent + " min)");

            return true;
        }

        // Pas expiré - effacer le timestamp de déconnexion
        data.setLastDisconnectTime(null);
        clearDisconnectTimeInDatabase(uuid);
        return false;
    }

    /**
     * Vérifie si le joueur a toujours le rôle requis pour son service
     * Appelé à la reconnexion APRÈS checkAndDeactivateIfExpired
     * @return true si le service a été désactivé (rôle invalide), false sinon
     */
    public boolean checkRoleAndDeactivate(Player player) {
        UUID uuid = player.getUniqueId();
        ProfessionalServiceData data = activeSessions.get(uuid);

        if (data == null) {
            return false;
        }

        // Les services entreprise ne dépendent pas d'un rôle de ville
        if (data.getServiceType() == ProfessionalServiceType.ENTERPRISE) {
            return false;
        }

        // Vérifier le rôle actuel du joueur
        String townName = townManager.getPlayerTown(uuid);
        if (townName == null) {
            // Le joueur n'est plus dans une ville
            return forceDeactivateServiceInternal(uuid, player, "Vous n'êtes plus citoyen d'une ville");
        }

        Town town = townManager.getTown(townName);
        if (town == null) {
            return forceDeactivateServiceInternal(uuid, player, "Ville introuvable");
        }

        TownRole currentRole = town.getMemberRole(uuid);
        if (!isRoleAuthorized(currentRole, data.getServiceType())) {
            String serviceName = data.getServiceType().getDisplayName();
            return forceDeactivateServiceInternal(uuid, player,
                "Vous n'avez plus le rôle de " + serviceName.toLowerCase());
        }

        return false;
    }

    /**
     * Force la désactivation du service avec une raison personnalisée
     * Peut être appelé même si le joueur est hors ligne
     * @param playerUUID UUID du joueur
     * @param reason Raison de la désactivation (affichée au joueur)
     */
    public void forceDeactivateService(UUID playerUUID, String reason) {
        Player player = Bukkit.getPlayer(playerUUID);
        forceDeactivateServiceInternal(playerUUID, player, reason);
    }

    /**
     * Implémentation interne de la désactivation forcée
     */
    private boolean forceDeactivateServiceInternal(UUID playerUUID, Player player, String reason) {
        ProfessionalServiceData data = activeSessions.remove(playerUUID);
        if (data == null) {
            return false;
        }

        String serviceName = data.getServiceType().getDisplayName();

        // Retirer la BossBar
        BossBar bossBar = playerBossBars.remove(playerUUID);
        if (bossBar != null) {
            bossBar.removeAll();
        }

        // Supprimer de la DB
        deleteFromDatabase(playerUUID);

        // Retirer le skin si le joueur est en ligne
        if (player != null && player.isOnline()) {
            if (data.getSkinApplied() != null) {
                restoreOriginalSkin(player);
            }

            // Message au joueur
            player.sendMessage("");
            player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage(ChatColor.RED + "  ⚠ SERVICE TERMINÉ");
            player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage(ChatColor.GRAY + "  Service: " + ChatColor.WHITE + serviceName);
            player.sendMessage(ChatColor.GRAY + "  Raison: " + ChatColor.YELLOW + reason);
            player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("");

            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }

        plugin.getLogger().info("[ProfessionalService] Service " + serviceName +
            " désactivé pour " + data.getPlayerName() + " (" + reason + ")");

        return true;
    }

    /**
     * Met à jour le timestamp de déconnexion en base de données
     */
    private void updateDisconnectTimeInDatabase(UUID playerUUID, Long disconnectTime) {
        try {
            connectionManager.executeUpdate(
                "UPDATE professional_services SET last_disconnect_time = ? WHERE player_uuid = ?",
                disconnectTime,
                playerUUID.toString()
            );
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[ProfessionalService] Erreur mise à jour disconnect time", e);
        }
    }

    /**
     * Efface le timestamp de déconnexion en base de données (joueur reconnecté)
     */
    private void clearDisconnectTimeInDatabase(UUID playerUUID) {
        try {
            connectionManager.executeUpdate(
                "UPDATE professional_services SET last_disconnect_time = NULL WHERE player_uuid = ?",
                playerUUID.toString()
            );
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[ProfessionalService] Erreur clear disconnect time", e);
        }
    }

    // ========================================
    // MÉTHODES UTILITAIRES PRIVÉES
    // ========================================

    private boolean isRoleAuthorized(TownRole role, ProfessionalServiceType type) {
        if (role == null) return false;

        // Seul le grade correspondant peut utiliser le service
        // MAIRE et ADJOINT ne peuvent PAS utiliser les services sans avoir le grade
        return switch (type) {
            case POLICE -> role == TownRole.POLICIER;
            case MEDICAL -> role == TownRole.MEDECIN;
            case JUDGE -> role == TownRole.JUGE;
            case ENTERPRISE -> true; // Géré par ServiceModeManager
        };
    }

    private String determineSkin(ProfessionalServiceType type, String sexe) {
        String configPath = "professional-service.skins." + type.name().toLowerCase();
        String skinHomme = plugin.getConfig().getString(configPath + ".homme", type.getDefaultSkinHomme());
        String skinFemme = plugin.getConfig().getString(configPath + ".femme", type.getDefaultSkinFemme());

        if (sexe == null || sexe.isEmpty()) {
            return skinHomme; // Défaut homme
        }

        String sexeLower = sexe.toLowerCase();
        if (sexeLower.equals("homme") || sexeLower.equals("male") || sexeLower.equals("m")) {
            return skinHomme;
        } else if (sexeLower.equals("femme") || sexeLower.equals("female") || sexeLower.equals("f")) {
            return skinFemme;
        }

        return skinHomme;
    }

    private void applySkin(Player player, String skinName) {
        new BukkitRunnable() {
            @Override
            public void run() {
                String command = "skin set " + skinName + " " + player.getName();
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
        }.runTaskLater(plugin, 20L);
    }

    private void restoreOriginalSkin(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                String command = "skin clear " + player.getName();
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
        }.runTaskLater(plugin, 20L);
    }

    private void createBossBar(Player player, ProfessionalServiceData data) {
        UUID uuid = player.getUniqueId();

        // Supprimer l'ancienne si elle existe
        BossBar oldBar = playerBossBars.remove(uuid);
        if (oldBar != null) {
            oldBar.removePlayer(player);
        }

        String title = ChatColor.WHITE + data.getServiceType().getEmoji() + " " +
            ChatColor.valueOf(data.getServiceType().getBarColor().name()) + "EN SERVICE" +
            ChatColor.WHITE + " | " +
            ChatColor.GOLD + data.getLocationName();

        BossBar bossBar = Bukkit.createBossBar(
            title,
            data.getServiceType().getBarColor(),
            BarStyle.SOLID
        );
        bossBar.setProgress(1.0);
        bossBar.addPlayer(player);
        playerBossBars.put(uuid, bossBar);
    }

    private void startBossBarUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, ProfessionalServiceData> entry : activeSessions.entrySet()) {
                    UUID uuid = entry.getKey();
                    ProfessionalServiceData data = entry.getValue();
                    BossBar bossBar = playerBossBars.get(uuid);

                    if (bossBar != null) {
                        String durationStr = formatDuration(data.getDuration());

                        bossBar.setTitle(
                            ChatColor.WHITE + data.getServiceType().getEmoji() + " " +
                            ChatColor.valueOf(data.getServiceType().getBarColor().name()) + "EN SERVICE" +
                            ChatColor.WHITE + " | " +
                            ChatColor.GOLD + data.getLocationName() +
                            ChatColor.WHITE + " | " +
                            ChatColor.YELLOW + durationStr
                        );
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void sendServiceActivatedMessage(Player player, ProfessionalServiceType type, ProfessionalServiceData data) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(ChatColor.GREEN + "  " + type.getEmoji() + " SERVICE " + type.getDisplayName().toUpperCase() + " ACTIVÉ");
        player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(ChatColor.GRAY + "  Lieu: " + ChatColor.WHITE + data.getLocationName());
        if (data.getSkinApplied() != null) {
            player.sendMessage(ChatColor.GRAY + "  Uniforme: " + ChatColor.WHITE + (data.getSexe() != null ? data.getSexe() : "Standard"));
        }
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "  Vous pouvez maintenant exercer vos");
        player.sendMessage(ChatColor.YELLOW + "  fonctions de " + type.getDisplayName().toLowerCase() + ".");
        player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");
    }

    private void sendServiceDeactivatedMessage(Player player, ProfessionalServiceData data) {
        String durationStr = formatDuration(data.getDuration());

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(ChatColor.YELLOW + "  " + data.getServiceType().getEmoji() + " SERVICE TERMINÉ");
        player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(ChatColor.GRAY + "  Durée de service: " + ChatColor.WHITE + durationStr);
        if (data.getSkinApplied() != null) {
            player.sendMessage(ChatColor.GRAY + "  Votre apparence a été restaurée.");
        }
        player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");
    }

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

    // ========================================
    // PERSISTANCE BASE DE DONNÉES
    // ========================================

    private void saveToDatabase(ProfessionalServiceData data) {
        try {
            connectionManager.executeUpdate("""
                INSERT OR REPLACE INTO professional_services
                (player_uuid, player_name, service_type, town_name, enterprise_name, sexe, skin_applied, start_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                data.getPlayerUUID().toString(),
                data.getPlayerName(),
                data.getServiceType().name(),
                data.getTownName(),
                data.getEnterpriseName(),
                data.getSexe(),
                data.getSkinApplied(),
                data.getStartTime()
            );
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[ProfessionalService] Erreur sauvegarde session", e);
        }
    }

    private void deleteFromDatabase(UUID playerUUID) {
        try {
            connectionManager.executeUpdate(
                "DELETE FROM professional_services WHERE player_uuid = ?",
                playerUUID.toString()
            );
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[ProfessionalService] Erreur suppression session", e);
        }
    }

    /**
     * Arrête proprement le manager
     */
    public void shutdown() {
        // Les BossBars seront automatiquement nettoyées
        playerBossBars.values().forEach(BossBar::removeAll);
        playerBossBars.clear();

        // Les sessions restent en DB pour restauration au prochain démarrage
        plugin.getLogger().info("[ProfessionalService] " + activeSessions.size() + " sessions sauvegardées");
    }

    /**
     * Vérifie si un joueur peut effectuer une action de police
     */
    public boolean canPerformPoliceAction(Player player) {
        return isInService(player.getUniqueId(), ProfessionalServiceType.POLICE);
    }

    /**
     * Vérifie si un joueur peut effectuer une action médicale
     */
    public boolean canPerformMedicalAction(Player player) {
        return isInService(player.getUniqueId(), ProfessionalServiceType.MEDICAL);
    }

    /**
     * Vérifie si un joueur peut effectuer une action de justice
     */
    public boolean canPerformJudgeAction(Player player) {
        return isInService(player.getUniqueId(), ProfessionalServiceType.JUDGE);
    }

    /**
     * Envoie un message d'erreur standardisé si pas en service
     */
    public void sendNotInServiceMessage(Player player, ProfessionalServiceType requiredType) {
        String configKey = "professional-service.messages.not-in-service";
        String message = plugin.getConfig().getString(configKey,
            "&cVous devez être en service pour effectuer cette action!");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }
}
