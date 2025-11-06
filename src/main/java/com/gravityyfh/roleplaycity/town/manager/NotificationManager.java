package com.gravityyfh.roleplaycity.town.manager;

import com.gravityyfh.roleplaycity.RoleplayCity;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire centralisé des notifications
 * Gère les notifications en ligne et hors ligne pour tous les événements du plugin
 */
public class NotificationManager {

    private final RoleplayCity plugin;
    private final NotificationDataManager dataManager;
    private final TownManager townManager;
    private final Map<UUID, Queue<Notification>> offlineNotifications;
    private final Map<UUID, List<Notification>> notificationHistory;
    private final DateTimeFormatter timeFormatter;

    public enum NotificationType {
        URGENT(ChatColor.RED, Sound.ENTITY_ENDER_DRAGON_GROWL, 10),     // Très important
        IMPORTANT(ChatColor.YELLOW, Sound.BLOCK_NOTE_BLOCK_BELL, 7),    // Important
        INFO(ChatColor.GREEN, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 5),    // Information
        SOCIAL(ChatColor.AQUA, Sound.BLOCK_NOTE_BLOCK_CHIME, 3),        // Social
        ECONOMY(ChatColor.GOLD, Sound.ENTITY_VILLAGER_YES, 5),          // ├ëconomique
        WARNING(ChatColor.RED, Sound.BLOCK_ANVIL_LAND, 8);              // Avertissement

        private final ChatColor color;
        private final Sound sound;
        private final int priority;

        NotificationType(ChatColor color, Sound sound, int priority) {
            this.color = color;
            this.sound = sound;
            this.priority = priority;
        }

        public ChatColor getColor() { return color; }
        public Sound getSound() { return sound; }
        public int getPriority() { return priority; }
    }

    public static class Notification {
        private final NotificationType type;
        private final String title;
        private final String message;
        private final LocalDateTime timestamp;
        private boolean read;

        public Notification(NotificationType type, String title, String message) {
            this.type = type;
            this.title = title;
            this.message = message;
            this.timestamp = LocalDateTime.now();
            this.read = false;
        }

        // Constructeur pour charger depuis la sauvegarde
        public Notification(NotificationType type, String title, String message, LocalDateTime timestamp, boolean read) {
            this.type = type;
            this.title = title;
            this.message = message;
            this.timestamp = timestamp;
            this.read = read;
        }

        public NotificationType getType() { return type; }
        public String getTitle() { return title; }
        public String getMessage() { return message; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public boolean isRead() { return read; }
        public void markAsRead() { this.read = true; }
    }

    public NotificationManager(RoleplayCity plugin, NotificationDataManager dataManager, TownManager townManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.townManager = townManager;
        this.offlineNotifications = new ConcurrentHashMap<>();
        this.notificationHistory = new ConcurrentHashMap<>();
        this.timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
    }

    /**
     * Charge les notifications depuis la sauvegarde
     */
    public void loadNotifications() {
        offlineNotifications.putAll(dataManager.loadOfflineNotifications());
        notificationHistory.putAll(dataManager.loadNotificationHistory());
    }

    /**
     * Sauvegarde synchrone des notifications (utilisé lors de l'arrêt du serveur)
     */
    public void saveNotificationsSync() {
        dataManager.saveNotificationsSync(offlineNotifications, notificationHistory);
    }

    /**
     * Envoie une notification à un joueur
     */
    public void sendNotification(UUID playerUuid, NotificationType type, String title, String message) {
        Player player = Bukkit.getPlayer(playerUuid);
        Notification notification = new Notification(type, title, message);

        if (player != null && player.isOnline()) {
            // Joueur en ligne ─ envoi immédiat
            sendOnlineNotification(player, notification);

            // Ajouter à l'historique
            addToHistory(playerUuid, notification);
        } else {
            // Joueur hors ligne ─ sauvegarder pour plus tard
            addOfflineNotification(playerUuid, notification);
        }
    }

    /**
     * Envoie une notification à tous les membres d'une ville
     */
    public void sendTownNotification(String townName, NotificationType type, String title, String message) {
        // à implémenter avec TownManager
        // town.getMembers().forEach(member ─> sendNotification(...))
    }

    /**
     * Envoie une notification broadcast à tous les joueurs
     */
    public void broadcastNotification(NotificationType type, String title, String message) {
        Notification notification = new Notification(type, title, message);

        for (Player player : Bukkit.getOnlinePlayers()) {
            sendOnlineNotification(player, notification);
        }
    }

    /**
     * Envoie la notification à un joueur en ligne
     */
    private void sendOnlineNotification(Player player, Notification notification) {
        NotificationType type = notification.getType();

        // Message dans le chat
        player.sendMessage("");
        player.sendMessage(type.getColor() + "─────────────────────────────");
        player.sendMessage(type.getColor() + "" + ChatColor.BOLD + "­ƒôó " + notification.getTitle());
        player.sendMessage(ChatColor.WHITE + notification.getMessage());
        player.sendMessage(ChatColor.GRAY + "ÔÅ░ " + timeFormatter.format(notification.getTimestamp()));
        player.sendMessage(type.getColor() + "─────────────────────────────");
        player.sendMessage("");

        // ActionBar pour les notifications importantes
        if (type.getPriority() >= 7) {
            player.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                new TextComponent(type.getColor() + "⚠ " + notification.getTitle())
            );
        }

        // Son de notification
        player.playSound(player.getLocation(), type.getSound(), 1.0f, 1.0f);

        // Title pour les notifications URGENT
        if (type == NotificationType.URGENT) {
            player.sendTitle(
                type.getColor() + "" + ChatColor.BOLD + "⚠ URGENT",
                ChatColor.WHITE + notification.getTitle(),
                10, 70, 20
            );
        }
    }

    /**
     * Ajoute une notification hors ligne
     */
    private void addOfflineNotification(UUID playerUuid, Notification notification) {
        offlineNotifications.computeIfAbsent(playerUuid, k -> new LinkedList<>()).offer(notification);
    }

    /**
     * Ajoute à l'historique des notifications
     */
    private void addToHistory(UUID playerUuid, Notification notification) {
        notificationHistory.computeIfAbsent(playerUuid, k -> new ArrayList<>()).add(notification);

        // Limiter l'historique à 50 dernières notifications
        List<Notification> history = notificationHistory.get(playerUuid);
        if (history.size() > 50) {
            history.remove(0);
        }
    }

    /**
     * Vérifie et envoie les notifications en attente quand un joueur se connecte
     */
    public void checkPendingNotifications(Player player) {
        UUID playerUuid = player.getUniqueId();
        Queue<Notification> pending = offlineNotifications.get(playerUuid);

        if (pending != null && !pending.isEmpty()) {
            player.sendMessage(ChatColor.GOLD + "─────────────────────────────");
            player.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD +
                             "Vous avez " + pending.size() + " notification(s) en attente !");
            player.sendMessage(ChatColor.GOLD + "─────────────────────────────");

            // Envoyer les notifications avec un délai
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                while (!pending.isEmpty()) {
                    Notification notif = pending.poll();
                    sendOnlineNotification(player, notif);
                    addToHistory(playerUuid, notif);
                }
                offlineNotifications.remove(playerUuid);
            }, 40L); // 2 secondes de délai
        }
    }

    /**
     * Notifications automatiques pour les événements récurrents
     */
    public void scheduleAutomaticNotifications() {
        // Location expiration check ─ toutes les heures
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkRentExpirations, 0L, 72000L);

        // Tax reminder ─ tous les jours à minuit (simulé toutes les 20 minutes pour test)
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::sendTaxReminders, 0L, 24000L);
    }

    /**
     * Vérifie les locations qui expirent bient├┤t
     */
    private void checkRentExpirations() {
        // à implémenter avec le TownManager
        // Parcourir toutes les locations et notifier si < 3 jours restants
    }

    /**
     * Envoie les rappels de taxes
     */
    private void sendTaxReminders() {
        // à implémenter avec le TownManager
        // Parcourir toutes les villes et notifier les citoyens des taxes dues
    }

    /**
     * Récupère l'historique des notifications d'un joueur
     */
    public List<Notification> getNotificationHistory(UUID playerUuid) {
        return notificationHistory.getOrDefault(playerUuid, new ArrayList<>());
    }

    /**
     * Marque toutes les notifications comme lues
     */
    public void markAllAsRead(UUID playerUuid) {
        List<Notification> history = notificationHistory.get(playerUuid);
        if (history != null) {
            history.forEach(Notification::markAsRead);
        }
    }

    /**
     * Efface l'historique d'un joueur
     */
    public void clearHistory(UUID playerUuid) {
        notificationHistory.remove(playerUuid);
        offlineNotifications.remove(playerUuid);
    }

    // === M├ëTHODES UTILITAIRES POUR NOTIFICATIONS SP├ëCIFIQUES ===

    /**
     * Notification de location expirante
     */
    public void notifyRentExpiring(UUID playerUuid, String plotInfo, int daysRemaining) {
        String title = "Location expire bient├┤t !";
        String message = String.format(
            "Votre location pour %s expire dans %d jour(s). Pensez à renouveler !",
            plotInfo, daysRemaining
        );
        sendNotification(playerUuid, NotificationType.WARNING, title, message);
    }

    /**
     * Notification d'achat réussi
     */
    public void notifyPurchaseSuccess(UUID playerUuid, String itemName, double price) {
        String title = "Achat effectué";
        String message = String.format(
            "Vous avez acheté %s pour %.2f€",
            itemName, price
        );
        sendNotification(playerUuid, NotificationType.ECONOMY, title, message);
    }

    /**
     * Notification d'invitation à une ville
     */
    public void notifyTownInvitation(UUID playerUuid, String townName, String inviterName) {
        String title = "Invitation à rejoindre une ville";
        String message = String.format(
            "%s vous invite à rejoindre la ville de %s. Utilisez /ville join %s pour accepter.",
            inviterName, townName, townName
        );
        sendNotification(playerUuid, NotificationType.SOCIAL, title, message);
    }

    /**
     * Notification de taxe due
     */
    public void notifyTaxDue(UUID playerUuid, String townName, double amount) {
        String title = "Taxes à payer";
        String message = String.format(
            "Vous devez %.2f€ de taxes à la ville de %s",
            amount, townName
        );
        sendNotification(playerUuid, NotificationType.IMPORTANT, title, message);
    }

    /**
     * Notification de nouveau maire élu
     */
    public void notifyNewMayor(UUID playerUuid, String townName, String mayorName) {
        String title = "Nouveau Maire élu";
        String message = String.format(
            "%s est maintenant le nouveau Maire de %s !",
            mayorName, townName
        );
        sendNotification(playerUuid, NotificationType.INFO, title, message);
    }

    /**
     * Notification de location réussie
     */
    public void notifyRentalSuccess(UUID playerUuid, String plotInfo, int days, double price) {
        String title = "Location effectuée";
        String message = String.format(
            "Vous avez loué %s pour %d jours (%.2f€)",
            plotInfo, days, price
        );
        sendNotification(playerUuid, NotificationType.ECONOMY, title, message);
    }

    /**
     * Notification de location expirée
     */
    public void notifyRentExpired(UUID playerUuid, String plotInfo, String townName) {
        String title = "Location expirée";
        String message = String.format(
            "Votre location de %s dans %s a expiré",
            plotInfo, townName
        );
        sendNotification(playerUuid, NotificationType.WARNING, title, message);
    }
}
