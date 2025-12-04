package com.gravityyfh.roleplaycity.phone.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.phone.PhoneManager;
import com.gravityyfh.roleplaycity.phone.PhoneMessages;
import com.gravityyfh.roleplaycity.phone.model.Contact;
import com.gravityyfh.roleplaycity.phone.service.CallService;
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

import java.util.*;

/**
 * GUI du composeur telephonique - interface compacte 4 blocs de large.
 * Clavier numerique pour composer un numero.
 *
 * Layout:
 * Ligne 0: Affichage numero + credits
 * Ligne 1-3: Clavier (1-9)
 * Ligne 4: Clear, 0, Backspace, Appeler
 * Ligne 5: Contacts, Retour
 */
public class PhoneDialerGUI implements InventoryProvider {

    private final RoleplayCity plugin;
    private final PhoneManager phoneManager;
    private final PhoneService phoneService;
    private final CallService callService;

    // Stockage du numero en cours de composition par joueur
    private static final Map<UUID, StringBuilder> dialingNumbers = new HashMap<>();

    public PhoneDialerGUI(RoleplayCity plugin) {
        this.plugin = plugin;
        this.phoneManager = plugin.getPhoneManager();
        this.phoneService = plugin.getPhoneService();
        this.callService = plugin.getCallService();
    }

    public void open(Player player) {
        if (Main.bankMenuInventoryManager == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Interface non initialisee.");
            return;
        }

        dialingNumbers.putIfAbsent(player.getUniqueId(), new StringBuilder());

        SmartInventory.builder()
            .id("phone_dialer")
            .provider(this)
            .size(6, 9)
            .title(ChatColor.GREEN + "\u260E Composer")
            .manager(Main.bankMenuInventoryManager)
            .build()
            .open(player);
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        UUID uuid = player.getUniqueId();
        StringBuilder currentNumber = dialingNumbers.computeIfAbsent(uuid, k -> new StringBuilder());

        // Initialiser le layout standard (colonnes 0-3 ecran, 4-8 ItemsAdder)
        PhoneGUIUtils.initPhoneLayout(contents, 6);

        // === LIGNE 0: Affichage numero ===
        String displayNumber = formatDisplayNumber(currentNumber.toString());
        ItemStack phoneItem = phoneManager.findPhoneInInventory(player);
        int credits = phoneItem != null ? phoneManager.getCredits(phoneItem) : 0;

        ItemStack numberDisplay = createItem(Material.NAME_TAG,
            ChatColor.GREEN + "" + ChatColor.BOLD + displayNumber,
            "",
            ChatColor.GRAY + "Credits: " + ChatColor.YELLOW + credits,
            ChatColor.DARK_GRAY + "Cout: " + phoneManager.getCallCostPerMinute() + "/min"
        );
        contents.set(0, 1, ClickableItem.empty(numberDisplay));
        contents.set(0, 2, ClickableItem.empty(numberDisplay));

        // Verification OpenAudioMc
        boolean isConnectedToAudio = callService != null && callService.isAvailable()
            && callService.isPlayerInVoiceChat(player);

        ItemStack audioStatus = createItem(
            isConnectedToAudio ? Material.LIME_DYE : Material.RED_DYE,
            isConnectedToAudio ? ChatColor.GREEN + "Audio OK" : ChatColor.RED + "Audio OFF",
            isConnectedToAudio
                ? ChatColor.GRAY + "Connecte au vocal"
                : ChatColor.RED + "Tapez /audio pour",
            isConnectedToAudio ? "" : ChatColor.RED + "activer les appels!"
        );
        contents.set(0, 0, ClickableItem.empty(audioStatus));
        contents.set(0, 3, ClickableItem.empty(audioStatus));

        // === LIGNE 1-3: Clavier numerique (colonnes 0-3) ===
        // Ligne 1: 1, 2, 3
        contents.set(1, 0, createNumberButton(player, "1"));
        contents.set(1, 1, createNumberButton(player, "2"));
        contents.set(1, 2, createNumberButton(player, "3"));

        // Ligne 2: 4, 5, 6
        contents.set(2, 0, createNumberButton(player, "4"));
        contents.set(2, 1, createNumberButton(player, "5"));
        contents.set(2, 2, createNumberButton(player, "6"));

        // Ligne 3: 7, 8, 9
        contents.set(3, 0, createNumberButton(player, "7"));
        contents.set(3, 1, createNumberButton(player, "8"));
        contents.set(3, 2, createNumberButton(player, "9"));

        // === LIGNE 4: C, 0, <, APPELER (separateur) ===
        // Clear
        contents.set(4, 0, ClickableItem.of(
            createItem(Material.RED_CONCRETE, ChatColor.RED + "" + ChatColor.BOLD + "C", ChatColor.GRAY + "Effacer tout"),
            e -> {
                currentNumber.setLength(0);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.5f);
                open(player);
            }
        ));

        // 0
        contents.set(4, 1, createNumberButton(player, "0"));

        // Backspace
        contents.set(4, 2, ClickableItem.of(
            createItem(Material.ORANGE_CONCRETE, ChatColor.GOLD + "" + ChatColor.BOLD + "\u2190", ChatColor.GRAY + "Effacer"),
            e -> {
                if (currentNumber.length() > 0) {
                    currentNumber.deleteCharAt(currentNumber.length() - 1);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
                    open(player);
                }
            }
        ));

        // Bouton Appeler (vert si 7 chiffres, gris sinon)
        boolean canCall = currentNumber.length() == 7;
        contents.set(4, 3, ClickableItem.of(
            createItem(
                canCall ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE,
                (canCall ? ChatColor.GREEN : ChatColor.GRAY) + "" + ChatColor.BOLD + "\u260E",
                canCall ? ChatColor.GREEN + "APPELER" : ChatColor.RED + "7 chiffres requis"
            ),
            e -> {
                // Re-verifier au moment du clic
                StringBuilder dialNumber = dialingNumbers.get(uuid);
                if (dialNumber == null || dialNumber.length() != 7) {
                    player.sendMessage(PhoneMessages.ENTER_7_DIGITS);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
                    return;
                }

                // Verifier telephone en main
                if (!phoneManager.hasPhoneInHand(player)) {
                    player.sendMessage(PhoneMessages.PHONE_NOT_IN_HAND);
                    return;
                }

                // Verifier connexion OpenAudioMc
                if (callService != null && callService.isAvailable()) {
                    if (!callService.isPlayerInVoiceChat(player)) {
                        player.sendMessage(PhoneMessages.NOT_CONNECTED_AUDIO);
                        return;
                    }
                }

                String number = formatPhoneNumber(dialNumber.toString());
                player.closeInventory();
                dialingNumbers.remove(uuid);
                phoneService.initiateCall(player, number);
            }
        ));

        // === LIGNE 5: Dock - Contacts, Espace, Retour, Historique ===
        // Contacts
        List<Contact> contacts = phoneService.getContacts(player);
        contents.set(5, 0, ClickableItem.of(
            createItem(Material.PLAYER_HEAD,
                ChatColor.AQUA + "" + ChatColor.BOLD + "Contacts",
                ChatColor.GRAY + "" + contacts.size() + " contact(s)",
                "",
                ChatColor.DARK_GRAY + "Appel rapide"
            ),
            e -> {
                dialingNumbers.remove(uuid);
                openQuickContacts(player);
            }
        ));

        // Historique
        contents.set(5, 1, ClickableItem.of(
            createItem(Material.CLOCK, ChatColor.GOLD + "Historique", ChatColor.GRAY + "Appels recents"),
            e -> {
                dialingNumbers.remove(uuid);
                new PhoneCallHistoryGUI(plugin).open(player);
            }
        ));

        // Espace vide (vitre noire)
        contents.set(5, 2, ClickableItem.empty(PhoneGUIUtils.createScreenBackground()));

        // Retour
        contents.set(5, 3, ClickableItem.of(
            createItem(Material.ARROW, ChatColor.RED + "Retour"),
            e -> {
                dialingNumbers.remove(uuid);
                new PhoneMainGUI(plugin).open(player);
            }
        ));
    }

    private ClickableItem createNumberButton(Player player, String digit) {
        ItemStack button = createItem(Material.LIGHT_GRAY_CONCRETE,
            ChatColor.WHITE + "" + ChatColor.BOLD + digit
        );

        return ClickableItem.of(button, e -> {
            StringBuilder currentNumber = dialingNumbers.get(player.getUniqueId());
            if (currentNumber != null && currentNumber.length() < 7) {
                currentNumber.append(digit);
                float pitch = 1.0f + (Float.parseFloat(digit) * 0.05f);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 0.5f, pitch);
                open(player);
            } else if (currentNumber != null && currentNumber.length() >= 7) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
            }
        });
    }

    private String formatDisplayNumber(String number) {
        if (number.isEmpty()) {
            return "___-____";
        }
        StringBuilder display = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if (i == 3) display.append("-");
            if (i < number.length()) {
                display.append(number.charAt(i));
            } else {
                display.append("_");
            }
        }
        return display.toString();
    }

    private String formatPhoneNumber(String number) {
        if (number.length() != 7) return number;
        return number.substring(0, 3) + "-" + number.substring(3);
    }

    private void openQuickContacts(Player player) {
        List<Contact> contacts = phoneService.getContacts(player);

        SmartInventory.builder()
            .id("phone_quick_contacts")
            .provider(new InventoryProvider() {
                @Override
                public void init(Player p, InventoryContents contents) {
                    // Initialiser le layout standard (colonnes 0-3 ecran, 4-8 ItemsAdder)
                    PhoneGUIUtils.initPhoneLayout(contents, 5);

                    if (contacts.isEmpty()) {
                        contents.set(2, 1, ClickableItem.empty(
                            createItem(Material.BARRIER, ChatColor.GRAY + "Aucun contact")
                        ));
                    } else {
                        // Afficher sur 4 colonnes (0-3) - max 12 contacts
                        int index = 0;
                        for (int row = 1; row <= 3; row++) {
                            for (int col = 0; col <= 3; col++) {
                                if (index >= contacts.size()) break;
                                Contact contact = contacts.get(index);

                                final String number = contact.getContactNumber();
                                contents.set(row, col, ClickableItem.of(
                                    createItem(Material.PLAYER_HEAD,
                                        ChatColor.GREEN + contact.getContactName(),
                                        ChatColor.GRAY + number,
                                        "",
                                        ChatColor.YELLOW + "Cliquez pour appeler"
                                    ),
                                    e -> {
                                        if (!phoneManager.hasPhoneInHand(p)) {
                                            p.sendMessage(PhoneMessages.PHONE_NOT_IN_HAND);
                                            return;
                                        }
                                        // Verifier OpenAudioMc
                                        if (callService != null && callService.isAvailable()) {
                                            if (!callService.isPlayerInVoiceChat(p)) {
                                                p.sendMessage(PhoneMessages.NOT_CONNECTED_AUDIO);
                                                return;
                                            }
                                        }
                                        p.closeInventory();
                                        phoneService.initiateCall(p, number);
                                    }
                                ));
                                index++;
                            }
                        }
                    }

                    // Retour (ligne 4 = separateur/dock)
                    contents.set(4, 1, ClickableItem.of(
                        createItem(Material.ARROW, ChatColor.RED + "Retour"),
                        e -> new PhoneDialerGUI(plugin).open(p)
                    ));
                }

                @Override
                public void update(Player player, InventoryContents contents) {}
            })
            .size(5, 9)
            .title(ChatColor.AQUA + "Contacts")
            .manager(Main.bankMenuInventoryManager)
            .build()
            .open(player);
    }

    @Override
    public void update(Player player, InventoryContents contents) {}

    public static void clearDialingNumber(UUID uuid) {
        dialingNumbers.remove(uuid);
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

}
