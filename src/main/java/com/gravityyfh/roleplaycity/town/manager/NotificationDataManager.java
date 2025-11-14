package com.gravityyfh.roleplaycity.town.manager;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.manager.NotificationManager.Notification;
import com.gravityyfh.roleplaycity.town.manager.NotificationManager.NotificationType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;

/**
 * [DataEngineer]
 * Gestionnaire de sauvegarde/chargement des notifications
 * Assure la persistance des notifications offline et de l\'historique
 *
 * Structure YAML:
 * offline-notifications:
 *   <player-uuid>:
 *     0:
 *       type: WARNING
 *       title: "Titre"
 *       message: "Message"
 *       timestamp: "2025-11-06T14:30:00"
 *       read: false
 *
 * notification-history:
 *   <player-uuid>:
 *     0:
 *       type: INFO
 *       title: "Titre"
 *       message: "Message"
 *       timestamp: "2025-11-06T14:30:00"
 *       read: true
 */
public class NotificationDataManager {

    private final RoleplayCity plugin;
    private final File notificationsFile;
    private FileConfiguration notificationsConfig;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // Debouncing pour éviter trop de sauvegardes
    private boolean saveScheduled = false;
    private static final long SAVE_DELAY_TICKS = 100L; // 5 secondes

    public NotificationDataManager(RoleplayCity plugin) {
        this.plugin = plugin;
        this.notificationsFile = new File(plugin.getDataFolder(), "notifications.yml");
        loadConfig();
    }

    /**
     * [DataEngineer] Charge ou crée le fichier de configuration
     */
    private void loadConfig() {
        if (!notificationsFile.exists()) {
            try {
                notificationsFile.getParentFile().mkdirs();
                notificationsFile.createNewFile();
                plugin.getLogger().info("Fichier notifications.yml créé.");
            } catch (IOException e) {
                plugin.getLogger().severe("Impossible de créer notifications.yml: " + e.getMessage());
            }
        }
        notificationsConfig = YamlConfiguration.loadConfiguration(notificationsFile);
    }

    /**
     * [DataEngineer] Sauvegarde toutes les notifications avec debouncing
     * Utilise un système de délai pour éviter trop de sauvegardes consécutives
     */
    public void saveNotificationsAsync(Map<UUID, Queue<Notification>> offlineNotifications,
                                       Map<UUID, List<Notification>> notificationHistory) {
        if (saveScheduled) {
            return; // Une sauvegarde est déjà planifiée
        }

        saveScheduled = true;

        // Utiliser runTaskLaterAsynchronously pour éviter de bloquer le thread principal
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            saveNotificationsSync(offlineNotifications, notificationHistory);
            saveScheduled = false;
        }, SAVE_DELAY_TICKS);
    }

    /**
     * [DataEngineer] Sauvegarde synchrone de toutes les notifications
     * Utilisée lors de l'arrêt du serveur
     */
    public void saveNotificationsSync(Map<UUID, Queue<Notification>> offlineNotifications,
                                      Map<UUID, List<Notification>> notificationHistory) {
        notificationsConfig = new YamlConfiguration();

        // Sauvegarder les notifications offline
        for (Map.Entry<UUID, Queue<Notification>> entry : offlineNotifications.entrySet()) {
            String playerUuid = entry.getKey().toString();
            Queue<Notification> notifications = entry.getValue();

            int index = 0;
            for (Notification notif : notifications) {
                String path = "offline-notifications." + playerUuid + "." + index;
                saveNotificationToConfig(path, notif);
                index++;
            }
        }

        // Sauvegarder l'historique des notifications
        for (Map.Entry<UUID, List<Notification>> entry : notificationHistory.entrySet()) {
            String playerUuid = entry.getKey().toString();
            List<Notification> history = entry.getValue();

            int index = 0;
            for (Notification notif : history) {
                String path = "notification-history." + playerUuid + "." + index;
                saveNotificationToConfig(path, notif);
                index++;
            }
        }

        try {
            notificationsConfig.save(notificationsFile);

            int totalOffline = offlineNotifications.values().stream().mapToInt(Queue::size).sum();
            int totalHistory = notificationHistory.values().stream().mapToInt(List::size).sum();

            plugin.getLogger().info("Sauvegardé " + totalOffline + " notifications offline et " +
                                  totalHistory + " notifications d'historique dans notifications.yml");
        } catch (IOException e) {
            // FIX BASSE: Utiliser logging avec exception complète
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la sauvegarde des notifications", e);
        }
    }

    /**
     * [DataEngineer] Sauvegarde une notification individuelle dans la config
     */
    private void saveNotificationToConfig(String path, Notification notif) {
        notificationsConfig.set(path + ".type", notif.getType().name());
        notificationsConfig.set(path + ".title", notif.getTitle());
        notificationsConfig.set(path + ".message", notif.getMessage());
        notificationsConfig.set(path + ".timestamp", notif.getTimestamp().format(DATE_FORMAT));
        notificationsConfig.set(path + ".read", notif.isRead());
    }

    /**
     * [DataEngineer] Charge toutes les notifications offline depuis le fichier YAML
     * Retourne une Map<UUID, Queue<Notification>>
     */
    public Map<UUID, Queue<Notification>> loadOfflineNotifications() {
        Map<UUID, Queue<Notification>> offlineNotifications = new HashMap<>();
        loadConfig();

        ConfigurationSection offlineSection = notificationsConfig.getConfigurationSection("offline-notifications");
        if (offlineSection == null) {
            plugin.getLogger().info("Aucune notification offline à charger.");
            return offlineNotifications;
        }

        for (String playerUuidStr : offlineSection.getKeys(false)) {
            try {
                UUID playerUuid = UUID.fromString(playerUuidStr);
                Queue<Notification> notifications = new LinkedList<>();

                ConfigurationSection playerSection = offlineSection.getConfigurationSection(playerUuidStr);
                if (playerSection == null) continue;

                for (String key : playerSection.getKeys(false)) {
                    try {
                        Notification notif = loadNotification(playerSection.getConfigurationSection(key));
                        if (notif != null) {
                            notifications.offer(notif);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().severe("Erreur lors du chargement d'une notification offline pour " +
                                                playerUuidStr + ": " + e.getMessage());
                    }
                }

                if (!notifications.isEmpty()) {
                    offlineNotifications.put(playerUuid, notifications);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().severe("UUID invalide dans notifications.yml: " + playerUuidStr);
            }
        }

        int total = offlineNotifications.values().stream().mapToInt(Queue::size).sum();
        plugin.getLogger().info("Chargé " + total + " notifications offline depuis notifications.yml");

        return offlineNotifications;
    }

    /**
     * [DataEngineer] Charge l'historique des notifications depuis le fichier YAML
     * Retourne une Map<UUID, List<Notification>>
     */
    public Map<UUID, List<Notification>> loadNotificationHistory() {
        Map<UUID, List<Notification>> notificationHistory = new HashMap<>();
        loadConfig();

        ConfigurationSection historySection = notificationsConfig.getConfigurationSection("notification-history");
        if (historySection == null) {
            plugin.getLogger().info("Aucun historique de notifications à charger.");
            return notificationHistory;
        }

        for (String playerUuidStr : historySection.getKeys(false)) {
            try {
                UUID playerUuid = UUID.fromString(playerUuidStr);
                List<Notification> history = new ArrayList<>();

                ConfigurationSection playerSection = historySection.getConfigurationSection(playerUuidStr);
                if (playerSection == null) continue;

                for (String key : playerSection.getKeys(false)) {
                    try {
                        Notification notif = loadNotification(playerSection.getConfigurationSection(key));
                        if (notif != null) {
                            history.add(notif);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().severe("Erreur lors du chargement d'un historique de notification pour " +
                                                playerUuidStr + ": " + e.getMessage());
                    }
                }

                if (!history.isEmpty()) {
                    notificationHistory.put(playerUuid, history);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().severe("UUID invalide dans notifications.yml: " + playerUuidStr);
            }
        }

        int total = notificationHistory.values().stream().mapToInt(List::size).sum();
        plugin.getLogger().info("Chargé " + total + " notifications d'historique depuis notifications.yml");

        return notificationHistory;
    }

    /**
     * [DataEngineer] Charge une notification individuelle depuis une ConfigurationSection
     * Gère les erreurs de parsing avec des valeurs par défaut sécurisées
     */
    private Notification loadNotification(ConfigurationSection section) {
        if (section == null) return null;

        try {
            String typeStr = section.getString("type", "INFO");
            NotificationType type;

            try {
                type = NotificationType.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Type de notification invalide: " + typeStr + ", utilisation de INFO par défaut");
                type = NotificationType.INFO;
            }

            String title = section.getString("title", "Notification");
            String message = section.getString("message", "");

            LocalDateTime timestamp;
            String timestampStr = section.getString("timestamp");
            if (timestampStr != null) {
                try {
                    timestamp = LocalDateTime.parse(timestampStr, DATE_FORMAT);
                } catch (Exception e) {
                    plugin.getLogger().warning("Format de timestamp invalide: " + timestampStr);
                    timestamp = LocalDateTime.now();
                }
            } else {
                timestamp = LocalDateTime.now();
            }

            boolean read = section.getBoolean("read", false);

            return new Notification(type, title, message, timestamp, read);

        } catch (Exception e) {
            // FIX BASSE: Utiliser logging avec exception complète
            plugin.getLogger().log(Level.SEVERE, "Erreur lors du chargement d'une notification", e);
            return null;
        }
    }

    /**
     * [PerformanceOpsAgent] Nettoie les anciennes notifications pour éviter l'accumulation
     * Supprime les notifications de plus de 30 jours
     */
    public void cleanupOldNotifications(Map<UUID, Queue<Notification>> offlineNotifications,
                                       Map<UUID, List<Notification>> notificationHistory) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        int removedCount = 0;

        // Nettoyer les notifications offline
        for (Queue<Notification> queue : offlineNotifications.values()) {
            Iterator<Notification> iterator = queue.iterator();
            while (iterator.hasNext()) {
                Notification notif = iterator.next();
                if (notif.getTimestamp().isBefore(thirtyDaysAgo)) {
                    iterator.remove();
                    removedCount++;
                }
            }
        }

        // Nettoyer l'historique
        for (List<Notification> list : notificationHistory.values()) {
            int before = list.size();
            list.removeIf(notif -> notif.getTimestamp().isBefore(thirtyDaysAgo));
            removedCount += Math.max(0, before - list.size());
        }

        if (removedCount > 0) {
            plugin.getLogger().info("Nettoyé " + removedCount + " notifications de plus de 30 jours.");
        }
    }
}
