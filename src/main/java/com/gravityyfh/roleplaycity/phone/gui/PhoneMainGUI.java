package com.gravityyfh.roleplaycity.phone.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.phone.PhoneManager;
import com.gravityyfh.roleplaycity.phone.PhoneMessages;
import com.gravityyfh.roleplaycity.phone.model.ActiveCall;
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

import java.util.Arrays;

/**
 * GUI principal du telephone style iPhone.
 *
 * LAYOUT:
 * - Colonnes 0-3: Zone ecran telephone (vitres noires)
 * - Colonnes 4-8: Background ItemsAdder
 * - Ligne 4: Separateur
 * - Ligne 5: Dock principal
 */
public class PhoneMainGUI implements InventoryProvider {

    private final RoleplayCity plugin;
    private final PhoneManager phoneManager;
    private final PhoneService phoneService;

    public PhoneMainGUI(RoleplayCity plugin) {
        this.plugin = plugin;
        this.phoneManager = plugin.getPhoneManager();
        this.phoneService = plugin.getPhoneService();
    }

    public void open(Player player) {
        if (Main.bankMenuInventoryManager == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Interface non initialisee.");
            return;
        }

        // Verifier si le joueur a un appel en cours
        ActiveCall call = phoneService.getActiveCall(player.getUniqueId());
        if (call != null) {
            if (call.getState() == ActiveCall.CallState.RINGING && !call.isCaller(player.getUniqueId())) {
                new PhoneIncomingCallGUI(plugin, call).open(player);
                return;
            }
            if (call.getState() == ActiveCall.CallState.CONNECTED) {
                new PhoneInCallGUI(plugin, call).open(player);
                return;
            }
        }

        SmartInventory.builder()
            .id("phone_main")
            .provider(this)
            .size(6, 9)
            .title(ChatColor.DARK_GRAY + "\u260E Telephone")
            .manager(Main.bankMenuInventoryManager)
            .build()
            .open(player);
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        // Initialiser le layout standard du telephone
        PhoneGUIUtils.initPhoneLayout(contents, 6);

        // Recuperer les infos du compte
        PhoneAccount account = phoneService.getOrCreateAccount(player);
        String phoneNumber = account != null ? account.getPhoneNumber() : "???-????";

        ItemStack phoneItem = phoneManager.findPhoneInInventory(player);
        int credits = phoneItem != null ? phoneManager.getCredits(phoneItem) : 0;
        int unreadMessages = phoneService.countUnreadMessages(player);
        int missedCalls = countMissedCalls(player, account);

        // === LIGNE 0: Apps principales (colonnes 0-3) ===
        int contactCount = phoneService.getContacts(player).size();
        contents.set(0, 0, ClickableItem.of(
            createAppItem(Material.PLAYER_HEAD, ChatColor.WHITE, "Contacts", contactCount + " enregistre(s)"),
            e -> {
                playClick(player);
                new PhoneContactsGUI(plugin).open(player);
            }
        ));

        contents.set(0, 1, ClickableItem.of(
            createAppItem(Material.CLOCK, ChatColor.WHITE, "Historique", "Appels recents"),
            e -> {
                playClick(player);
                new PhoneCallHistoryGUI(plugin).open(player);
            }
        ));

        contents.set(0, 2, ClickableItem.of(
            createAppItem(Material.COMPARATOR, ChatColor.WHITE, "Reglages", "Configuration"),
            e -> {
                playClick(player);
                new PhoneSettingsGUI(plugin).open(player);
            }
        ));

        contents.set(0, 3, ClickableItem.of(
            createAppItem(Material.JUKEBOX, ChatColor.WHITE, "Musique", "Lecteur audio"),
            e -> {
                playClick(player);
                new PhoneMusicGUI(plugin).open(player);
            }
        ));

        // === LIGNE 1: Apps secondaires ===
        contents.set(1, 0, ClickableItem.of(
            createAppItem(Material.NAME_TAG, ChatColor.WHITE, "Mon Profil", phoneNumber),
            e -> {
                playClick(player);
                openProfileGUI(player, account, credits, phoneNumber);
            }
        ));

        contents.set(1, 1, ClickableItem.of(
            createAppItem(Material.GOLD_INGOT, ChatColor.YELLOW, "Recharge", credits + " credits"),
            e -> {
                playClick(player);
                new PhoneRechargeGUI(plugin).open(player);
            }
        ));

        // Si en appel, afficher le bouton raccrocher
        if (phoneService.isInCall(player.getUniqueId())) {
            var call = phoneService.getActiveCall(player.getUniqueId());
            if (call != null) {
                String otherNumber = call.isCaller(player.getUniqueId()) ? call.getCalleeNumber() : call.getCallerNumber();
                String displayName = phoneService.getContactDisplayName(player.getUniqueId(), otherNumber);
                if (displayName == null) displayName = otherNumber;

                ItemStack inCallItem = createItem(Material.RED_CONCRETE,
                    ChatColor.RED + "" + ChatColor.BOLD + "RACCROCHER",
                    "",
                    ChatColor.GRAY + "Avec: " + ChatColor.WHITE + displayName,
                    ChatColor.GRAY + "Duree: " + ChatColor.WHITE + call.getFormattedDuration()
                );
                contents.set(2, 1, ClickableItem.of(inCallItem, e -> {
                    phoneService.hangUp(player);
                    player.sendMessage(PhoneMessages.CALL_ENDED);
                    open(player);
                }));
                contents.set(2, 2, ClickableItem.of(inCallItem, e -> {
                    phoneService.hangUp(player);
                    player.sendMessage(PhoneMessages.CALL_ENDED);
                    open(player);
                }));
            }
        }

        // === LIGNE 5: Dock principal (colonnes 0-3) ===
        contents.set(5, 0, ClickableItem.of(
            createDockItem(Material.LIME_CONCRETE, ChatColor.GREEN, "Appeler"),
            e -> {
                playClick(player);
                new PhoneDialerGUI(plugin).open(player);
            }
        ));

        String smsLabel = unreadMessages > 0 ? "SMS (" + unreadMessages + ")" : "SMS";
        contents.set(5, 1, ClickableItem.of(
            createDockItem(unreadMessages > 0 ? Material.ORANGE_CONCRETE : Material.YELLOW_CONCRETE,
                unreadMessages > 0 ? ChatColor.RED : ChatColor.YELLOW, smsLabel),
            e -> {
                playClick(player);
                new PhoneSmsGUI(plugin).openInbox(player);
            }
        ));

        int totalNotifs = unreadMessages + missedCalls;
        String notifLabel = totalNotifs > 0 ? "Notifs (" + totalNotifs + ")" : "Notifs";
        contents.set(5, 2, ClickableItem.of(
            createDockItem(totalNotifs > 0 ? Material.RED_CONCRETE : Material.LIGHT_GRAY_CONCRETE,
                totalNotifs > 0 ? ChatColor.RED : ChatColor.GRAY, notifLabel),
            e -> {
                playClick(player);
                new PhoneNotificationsGUI(plugin).open(player);
            }
        ));

        contents.set(5, 3, ClickableItem.of(
            createDockItem(Material.GRAY_CONCRETE, ChatColor.DARK_GRAY, "Fermer"),
            e -> {
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
            }
        ));
    }

    private int countMissedCalls(Player player, PhoneAccount account) {
        if (account == null) return 0;
        return (int) phoneService.getCallHistory(player, 20).stream()
            .filter(c -> c.getStatus() == com.gravityyfh.roleplaycity.phone.model.CallRecord.CallStatus.MISSED)
            .filter(c -> !c.getCallerNumber().equals(account.getPhoneNumber()))
            .count();
    }

    private void openProfileGUI(Player player, PhoneAccount account, int credits, String phoneNumber) {
        SmartInventory.builder()
            .id("phone_profile")
            .provider(new InventoryProvider() {
                @Override
                public void init(Player p, InventoryContents contents) {
                    PhoneGUIUtils.initPhoneLayout(contents, 5);

                    // Info profil
                    ItemStack profileItem = createItem(Material.PLAYER_HEAD,
                        ChatColor.WHITE + p.getName(),
                        "",
                        ChatColor.GRAY + "Numero: " + ChatColor.GREEN + phoneNumber,
                        ChatColor.GRAY + "Credits: " + ChatColor.YELLOW + credits,
                        "",
                        ChatColor.GRAY + "Appels: " + ChatColor.WHITE + (account != null ? account.getTotalCalls() : 0),
                        ChatColor.GRAY + "SMS: " + ChatColor.WHITE + (account != null ? account.getTotalSms() : 0)
                    );
                    contents.set(1, 1, ClickableItem.empty(profileItem));
                    contents.set(1, 2, ClickableItem.empty(profileItem));

                    // Retour
                    contents.set(4, 1, ClickableItem.of(
                        createItem(Material.ARROW, ChatColor.RED + "Retour"),
                        e -> {
                            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
                            new PhoneMainGUI(plugin).open(p);
                        }
                    ));
                }

                @Override
                public void update(Player player, InventoryContents contents) {}
            })
            .size(5, 9)
            .title(ChatColor.DARK_GRAY + "Mon Profil")
            .manager(Main.bankMenuInventoryManager)
            .build()
            .open(player);
    }

    @Override
    public void update(Player player, InventoryContents contents) {}

    private void playClick(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
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

    private ItemStack createAppItem(Material material, ChatColor color, String name, String desc) {
        return createItem(material, color + name, ChatColor.GRAY + desc);
    }

    private ItemStack createDockItem(Material material, ChatColor color, String name) {
        return createItem(material, color + "" + ChatColor.BOLD + name);
    }
}
