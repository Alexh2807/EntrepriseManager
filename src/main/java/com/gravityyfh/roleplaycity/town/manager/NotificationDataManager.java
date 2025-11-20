package com.gravityyfh.roleplaycity.town.manager;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.manager.NotificationManager.Notification;
import com.gravityyfh.roleplaycity.town.service.NotificationPersistenceService;

import java.util.*;

public class NotificationDataManager {

    private final RoleplayCity plugin;
    private final NotificationPersistenceService persistenceService;

    // Debouncing pour éviter trop de sauvegardes
    private boolean saveScheduled = false;
    private static final long SAVE_DELAY_TICKS = 100L; // 5 secondes

    public NotificationDataManager(RoleplayCity plugin, NotificationPersistenceService persistenceService) {
        this.plugin = plugin;
        this.persistenceService = persistenceService;
    }

    public void saveNotificationsAsync(Map<UUID, Queue<Notification>> offlineNotifications,
                                       Map<UUID, List<Notification>> notificationHistory) {
        if (saveScheduled) {
            return;
        }

        saveScheduled = true;

        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            saveNotificationsSync(offlineNotifications, notificationHistory);
            saveScheduled = false;
        }, SAVE_DELAY_TICKS);
    }

    public void saveNotificationsSync(Map<UUID, Queue<Notification>> offlineNotifications,
                                      Map<UUID, List<Notification>> notificationHistory) {
        persistenceService.saveAllNotifications(offlineNotifications, notificationHistory);
    }

    public Map<UUID, Queue<Notification>> loadOfflineNotifications() {
        return persistenceService.loadOfflineNotifications();
    }

    public Map<UUID, List<Notification>> loadNotificationHistory() {
        return persistenceService.loadNotificationHistory();
    }

    public void cleanupOldNotifications(Map<UUID, Queue<Notification>> offlineNotifications,
                                       Map<UUID, List<Notification>> notificationHistory) {
        // Déléguer au service SQL qui le fait plus efficacement
        persistenceService.cleanOldNotifications();
        
        // On peut aussi nettoyer la mémoire si nécessaire, mais le reload chargera la version propre
    }
}