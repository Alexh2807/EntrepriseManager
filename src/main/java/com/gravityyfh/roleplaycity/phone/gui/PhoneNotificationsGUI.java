package com.gravityyfh.roleplaycity.phone.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.phone.PhoneManager;
import com.gravityyfh.roleplaycity.phone.model.CallRecord;
import com.gravityyfh.roleplaycity.phone.model.Message;
import com.gravityyfh.roleplaycity.phone.model.PhoneAccount;
import com.gravityyfh.roleplaycity.phone.service.PhoneService;
import de.lightplugins.economy.master.Main;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * GUI du centre de notifications.
 * Interface compacte de 4 blocs de large centree.
 */
public class PhoneNotificationsGUI implements InventoryProvider {

    private final RoleplayCity plugin;
    private final PhoneManager phoneManager;
    private final PhoneService phoneService;
    private int currentPage = 0;
    private FilterType currentFilter = FilterType.ALL;

    public PhoneNotificationsGUI(RoleplayCity plugin) {
        this.plugin = plugin;
        this.phoneManager = plugin.getPhoneManager();
        this.phoneService = plugin.getPhoneService();
    }

    public void open(Player player) {
        open(player, 0, FilterType.ALL);
    }

    public void open(Player player, int page, FilterType filter) {
        this.currentPage = page;
        this.currentFilter = filter;

        if (Main.bankMenuInventoryManager == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Interface non initialisee.");
            return;
        }

        SmartInventory.builder()
            .id("phone_notifications")
            .provider(this)
            .size(6, 9)
            .title(ChatColor.DARK_GRAY + "\u2709 Notifications")
            .manager(Main.bankMenuInventoryManager)
            .build()
            .open(player);
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        // Initialiser le layout standard (colonnes 0-3 ecran, 4-8 ItemsAdder)
        PhoneGUIUtils.initPhoneLayout(contents, 6);

        PhoneAccount account = phoneService.getOrCreateAccount(player);
        String myNumber = account != null ? account.getPhoneNumber() : "";

        // Recuperer les notifications
        List<Message> unreadMessages = phoneService.getReceivedMessages(player).stream()
            .filter(m -> !m.isRead())
            .toList();

        List<CallRecord> missedCalls = phoneService.getCallHistory(player, 50).stream()
            .filter(c -> c.getStatus() == CallRecord.CallStatus.MISSED)
            .filter(c -> !c.getCallerNumber().equals(myNumber))
            .toList();

        int smsCount = unreadMessages.size();
        int callCount = missedCalls.size();
        int totalNotifs = smsCount + callCount;

        // === LIGNE 0: Barre de statut ===
        String statusText = totalNotifs > 0
            ? ChatColor.YELLOW + "" + totalNotifs + " notification(s)"
            : ChatColor.GREEN + "Aucune notification";
        ItemStack statusItem = createGlass(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta statusMeta = statusItem.getItemMeta();
        if (statusMeta != null) {
            statusMeta.setDisplayName(statusText);
            statusItem.setItemMeta(statusMeta);
        }
        for (int col = 0; col <= 3; col++) {
            contents.set(0, col, ClickableItem.empty(statusItem));
        }

        // === LIGNE 1: Filtres ===
        // Tous
        ItemStack allItem = createItem(
            currentFilter == FilterType.ALL ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE,
            (currentFilter == FilterType.ALL ? ChatColor.GREEN : ChatColor.GRAY) + "Tous (" + totalNotifs + ")"
        );
        contents.set(1, 0, ClickableItem.of(allItem, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            open(player, 0, FilterType.ALL);
        }));

        // SMS
        ItemStack smsItem = createItem(
            currentFilter == FilterType.SMS ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE,
            (currentFilter == FilterType.SMS ? ChatColor.GREEN : ChatColor.GRAY) + "SMS (" + smsCount + ")"
        );
        contents.set(1, 1, ClickableItem.of(smsItem, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            open(player, 0, FilterType.SMS);
        }));

        // Appels
        ItemStack callsItem = createItem(
            currentFilter == FilterType.CALLS ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE,
            (currentFilter == FilterType.CALLS ? ChatColor.GREEN : ChatColor.GRAY) + "Appels (" + callCount + ")"
        );
        contents.set(1, 2, ClickableItem.of(callsItem, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            open(player, 0, FilterType.CALLS);
        }));

        // Actualiser
        ItemStack refreshItem = createItem(Material.CYAN_CONCRETE, ChatColor.AQUA + "Actualiser");
        contents.set(1, 3, ClickableItem.of(refreshItem, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            open(player, currentPage, currentFilter);
        }));

        // === LIGNES 2-4: Liste des notifications ===
        List<NotificationItem> notifications = buildNotificationList(player, unreadMessages, missedCalls);

        if (notifications.isEmpty()) {
            ItemStack noNotifItem = createItem(Material.LIME_WOOL,
                ChatColor.GREEN + "Aucune notification",
                "",
                ChatColor.GRAY + "Vous etes a jour!"
            );
            contents.set(3, 1, ClickableItem.empty(noNotifItem));
            contents.set(3, 2, ClickableItem.empty(noNotifItem));
        } else {
            int itemsPerPage = 12; // 4 colonnes x 3 lignes
            int totalPages = (int) Math.ceil((double) notifications.size() / itemsPerPage);
            int startIndex = currentPage * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, notifications.size());

            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm");

            int index = startIndex;
            for (int row = 2; row <= 4 && index < endIndex; row++) {
                for (int col = 0; col <= 3 && index < endIndex; col++) {
                    NotificationItem notif = notifications.get(index);

                    String displayName = phoneService.getContactDisplayName(player.getUniqueId(), notif.number);
                    if (displayName == null) displayName = notif.number;

                    Material mat;
                    String prefix;
                    List<String> lore = new ArrayList<>();

                    if (notif.type == NotificationType.SMS) {
                        mat = Material.WRITABLE_BOOK;
                        prefix = ChatColor.YELLOW + "\u2709 ";
                        lore.add(ChatColor.WHITE + truncate(notif.preview, 20));
                        lore.add("");
                        lore.add(ChatColor.GRAY + sdf.format(new Date(notif.timestamp)));
                        lore.add(ChatColor.YELLOW + "Cliquez pour lire");
                    } else {
                        mat = Material.RED_DYE;
                        prefix = ChatColor.RED + "\u260E ";
                        lore.add(ChatColor.RED + "Appel manque");
                        lore.add("");
                        lore.add(ChatColor.GRAY + sdf.format(new Date(notif.timestamp)));
                        lore.add(ChatColor.YELLOW + "Cliquez pour rappeler");
                    }

                    ItemStack notifItem = createItem(mat, prefix + truncate(displayName, 12), lore.toArray(new String[0]));

                    final NotificationItem finalNotif = notif;
                    final String finalDisplayName = displayName;
                    contents.set(row, col, ClickableItem.of(notifItem, e -> {
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                        handleNotificationClick(player, finalNotif, finalDisplayName, sdf);
                    }));
                    index++;
                }
            }

            // === LIGNE 5: Navigation ===
            // Page precedente
            if (currentPage > 0) {
                ItemStack prevItem = createItem(Material.ARROW, ChatColor.YELLOW + "< Page " + currentPage);
                contents.set(5, 0, ClickableItem.of(prevItem, e -> {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                    open(player, currentPage - 1, currentFilter);
                }));
            } else if (smsCount > 0) {
                // Marquer tout comme lu
                ItemStack markReadItem = createItem(Material.LIME_DYE,
                    ChatColor.GREEN + "Tout lu",
                    "",
                    ChatColor.GRAY + "Marquer comme lu"
                );
                contents.set(5, 0, ClickableItem.of(markReadItem, e -> {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    for (Message msg : unreadMessages) {
                        phoneService.markConversationAsRead(player, msg.getSenderNumber());
                    }
                    player.sendMessage(ChatColor.GREEN + "[Tel] Messages marques comme lus.");
                    open(player, 0, currentFilter);
                }));
            }

            // Page suivante
            if (currentPage < totalPages - 1) {
                ItemStack nextItem = createItem(Material.ARROW, ChatColor.YELLOW + "Page " + (currentPage + 2) + " >");
                contents.set(5, 3, ClickableItem.of(nextItem, e -> {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                    open(player, currentPage + 1, currentFilter);
                }));
            }
        }

        // Retour (toujours au centre)
        addBackButton(contents, player);
    }

    private void handleNotificationClick(Player player, NotificationItem notif, String displayName, SimpleDateFormat sdf) {
        if (notif.type == NotificationType.SMS) {
            phoneService.markConversationAsRead(player, notif.number);
            player.closeInventory();
            player.sendMessage("");
            player.sendMessage(ChatColor.GOLD + "=== Message de " + displayName + " ===");
            player.sendMessage(ChatColor.WHITE + notif.preview);
            player.sendMessage(ChatColor.GRAY + "Date: " + sdf.format(new Date(notif.timestamp)));
            player.sendMessage("");
            player.sendMessage(ChatColor.YELLOW + "Pour repondre, utilisez le menu Messages.");
        } else {
            player.closeInventory();
            if (!phoneManager.hasPhoneInHand(player)) {
                player.sendMessage(ChatColor.RED + "[Tel] Tenez votre telephone en main.");
                return;
            }
            phoneService.initiateCall(player, notif.number);
        }
    }

    private List<NotificationItem> buildNotificationList(Player player, List<Message> unreadMessages, List<CallRecord> missedCalls) {
        List<NotificationItem> notifications = new ArrayList<>();

        // Filtrer selon le type
        if (currentFilter == FilterType.ALL || currentFilter == FilterType.SMS) {
            for (Message msg : unreadMessages) {
                notifications.add(new NotificationItem(
                    NotificationType.SMS,
                    msg.getSenderNumber(),
                    msg.getPreview(),
                    msg.getSentAt()
                ));
            }
        }

        if (currentFilter == FilterType.ALL || currentFilter == FilterType.CALLS) {
            for (CallRecord call : missedCalls) {
                notifications.add(new NotificationItem(
                    NotificationType.MISSED_CALL,
                    call.getCallerNumber(),
                    "Appel manque",
                    call.getStartedAt()
                ));
            }
        }

        // Trier par date (plus recent en premier)
        notifications.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));

        return notifications;
    }

    private void addBackButton(InventoryContents contents, Player player) {
        ItemStack backItem = createItem(Material.ARROW, ChatColor.RED + "Retour");
        contents.set(5, 1, ClickableItem.of(backItem, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
            new PhoneMainGUI(plugin).open(player);
        }));
        contents.set(5, 2, ClickableItem.of(backItem, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
            new PhoneMainGUI(plugin).open(player);
        }));
    }

    @Override
    public void update(Player player, InventoryContents contents) {}

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength - 2) + ".." : text;
    }

    /**
     * Types de filtre.
     */
    public enum FilterType {
        ALL, SMS, CALLS
    }

    /**
     * Types de notification.
     */
    private enum NotificationType {
        SMS, MISSED_CALL
    }

    /**
     * Classe pour representer une notification.
     */
    private static class NotificationItem {
        final NotificationType type;
        final String number;
        final String preview;
        final long timestamp;

        NotificationItem(NotificationType type, String number, String preview, long timestamp) {
            this.type = type;
            this.number = number;
            this.preview = preview;
            this.timestamp = timestamp;
        }
    }

    private ItemStack createItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (loreLines.length > 0) {
                meta.setLore(Arrays.asList(loreLines));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createGlass(Material material) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        return pane;
    }
}
