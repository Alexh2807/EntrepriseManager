package com.gravityyfh.roleplaycity.phone.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.phone.PhoneManager;
import com.gravityyfh.roleplaycity.phone.PhoneMessages;
import com.gravityyfh.roleplaycity.phone.listener.PhoneChatListener;
import com.gravityyfh.roleplaycity.phone.model.Contact;
import com.gravityyfh.roleplaycity.phone.service.PhoneService;
import de.lightplugins.economy.master.Main;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

/**
 * GUI pour la gestion des contacts - interface compacte 4 blocs de large.
 * Permet de: voir les contacts, ajouter (avec clavier numerique), supprimer, appeler, envoyer SMS.
 *
 * Layout:
 * Colonnes 0-2: Bordure noire/grise (gauche)
 * Colonnes 3-6: Zone utile (4 blocs de large)
 * Colonnes 7-8: Bordure grise/noire (droite)
 */
public class PhoneContactsGUI implements InventoryProvider {

    private final RoleplayCity plugin;
    private final PhoneManager phoneManager;
    private final PhoneService phoneService;

    // Stockage du numero en cours de composition pour ajout de contact
    private static final Map<UUID, StringBuilder> addingNumbers = new HashMap<>();

    public PhoneContactsGUI(RoleplayCity plugin) {
        this.plugin = plugin;
        this.phoneManager = plugin.getPhoneManager();
        this.phoneService = plugin.getPhoneService();
    }

    public void open(Player player) {
        if (Main.bankMenuInventoryManager == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Interface non initialisee.");
            return;
        }

        SmartInventory.builder()
            .id("phone_contacts")
            .provider(this)
            .size(6, 9)
            .title(ChatColor.GREEN + "\u260E Contacts")
            .manager(Main.bankMenuInventoryManager)
            .build()
            .open(player);
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        // Initialiser le layout standard (colonnes 0-3 ecran, 4-8 ItemsAdder)
        PhoneGUIUtils.initPhoneLayout(contents, 6);

        List<Contact> contacts = phoneService.getContacts(player);

        // === LIGNE 0: Titre + Ajouter ===
        ItemStack titleItem = createItem(Material.PLAYER_HEAD,
            ChatColor.GREEN + "" + ChatColor.BOLD + "Contacts",
            ChatColor.GRAY + "" + contacts.size() + " contact(s)"
        );
        contents.set(0, 0, ClickableItem.empty(titleItem));
        contents.set(0, 1, ClickableItem.empty(titleItem));

        // Bouton Ajouter - ouvre le GUI avec clavier numerique
        contents.set(0, 2, ClickableItem.of(
            createItem(Material.EMERALD,
                ChatColor.GREEN + "" + ChatColor.BOLD + "+",
                ChatColor.GRAY + "Ajouter contact"
            ),
            e -> {
                playClick(player);
                openAddContactGUI(player);
            }
        ));

        // === LIGNES 1-4: Contacts (4 par ligne, max 16) ===
        if (contacts.isEmpty()) {
            contents.set(2, 1, ClickableItem.empty(
                createItem(Material.BARRIER,
                    ChatColor.GRAY + "Aucun contact",
                    "",
                    ChatColor.YELLOW + "Cliquez + pour ajouter"
                )
            ));
        } else {
            int index = 0;
            for (int row = 1; row <= 4; row++) {
                for (int col = 0; col <= 3; col++) {
                    if (index < contacts.size()) {
                        Contact contact = contacts.get(index);
                        final String contactNumber = contact.getContactNumber();
                        final String contactName = contact.getContactName();
                        final int contactId = contact.getId();

                        ItemStack contactItem = createPlayerHead(contactName);
                        ItemMeta meta = contactItem.getItemMeta();
                        if (meta != null) {
                            meta.setDisplayName(ChatColor.GREEN + contactName);
                            List<String> lore = new ArrayList<>();
                            lore.add(ChatColor.GRAY + contactNumber);
                            lore.add("");
                            lore.add(ChatColor.YELLOW + "Gauche: " + ChatColor.WHITE + "Appeler");
                            lore.add(ChatColor.YELLOW + "Droit: " + ChatColor.WHITE + "SMS");
                            lore.add(ChatColor.RED + "Shift: " + ChatColor.WHITE + "Supprimer");
                            meta.setLore(lore);
                            contactItem.setItemMeta(meta);
                        }

                        contents.set(row, col, ClickableItem.of(contactItem, e -> {
                            if (e.isShiftClick()) {
                                // Supprimer le contact
                                openDeleteConfirmation(player, contactId, contactName);
                            } else if (e.isLeftClick()) {
                                // Appeler
                                player.closeInventory();
                                if (!phoneManager.hasPhoneInHand(player)) {
                                    player.sendMessage(PhoneMessages.PHONE_NOT_IN_HAND);
                                    return;
                                }
                                phoneService.initiateCall(player, contactNumber);
                            } else if (e.isRightClick()) {
                                // Envoyer SMS
                                player.closeInventory();
                                player.sendMessage(PhoneMessages.SMS_WRITE_PROMPT);
                                player.sendMessage(ChatColor.GRAY + "Destinataire: " + ChatColor.WHITE + contactName);
                                player.sendMessage(PhoneMessages.SMS_CANCEL_HINT);
                                PhoneChatListener.awaitInput(player, PhoneChatListener.InputType.SMS_CONTENT, contactNumber);
                            }
                        }));
                        index++;
                    }
                }
            }
        }

        // === LIGNE 5: Dock - Retour ===
        contents.set(5, 1, ClickableItem.of(
            createItem(Material.ARROW, ChatColor.RED + "Retour"),
            e -> new PhoneMainGUI(plugin).open(player)
        ));
    }

    /**
     * Ouvre le GUI pour ajouter un contact avec clavier numerique.
     */
    private void openAddContactGUI(Player player) {
        UUID uuid = player.getUniqueId();
        addingNumbers.putIfAbsent(uuid, new StringBuilder());

        SmartInventory.builder()
            .id("phone_add_contact")
            .provider(new InventoryProvider() {
                @Override
                public void init(Player p, InventoryContents contents) {
                    // Initialiser le layout standard (colonnes 0-3 ecran, 4-8 ItemsAdder)
                    PhoneGUIUtils.initPhoneLayout(contents, 6);

                    StringBuilder currentNumber = addingNumbers.computeIfAbsent(uuid, k -> new StringBuilder());

                    // === LIGNE 0: Affichage numero ===
                    String displayNumber = formatDisplayNumber(currentNumber.toString());
                    ItemStack numberDisplay = createItem(Material.NAME_TAG,
                        ChatColor.AQUA + "" + ChatColor.BOLD + displayNumber,
                        ChatColor.GRAY + "Numero du contact"
                    );
                    contents.set(0, 1, ClickableItem.empty(numberDisplay));
                    contents.set(0, 2, ClickableItem.empty(numberDisplay));

                    // === LIGNES 1-3: Clavier (colonnes 0-3) ===
                    // Ligne 1: 1, 2, 3
                    contents.set(1, 0, createNumberButton(p, "1"));
                    contents.set(1, 1, createNumberButton(p, "2"));
                    contents.set(1, 2, createNumberButton(p, "3"));

                    // Ligne 2: 4, 5, 6
                    contents.set(2, 0, createNumberButton(p, "4"));
                    contents.set(2, 1, createNumberButton(p, "5"));
                    contents.set(2, 2, createNumberButton(p, "6"));

                    // Ligne 3: 7, 8, 9
                    contents.set(3, 0, createNumberButton(p, "7"));
                    contents.set(3, 1, createNumberButton(p, "8"));
                    contents.set(3, 2, createNumberButton(p, "9"));

                    // === LIGNE 4: C, 0, <, VALIDER (separateur) ===
                    // Clear
                    contents.set(4, 0, ClickableItem.of(
                        createItem(Material.RED_CONCRETE, ChatColor.RED + "" + ChatColor.BOLD + "C", ChatColor.GRAY + "Effacer tout"),
                        e -> {
                            currentNumber.setLength(0);
                            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.5f);
                            openAddContactGUI(p);
                        }
                    ));

                    // 0
                    contents.set(4, 1, createNumberButton(p, "0"));

                    // Backspace
                    contents.set(4, 2, ClickableItem.of(
                        createItem(Material.ORANGE_CONCRETE, ChatColor.GOLD + "" + ChatColor.BOLD + "\u2190", ChatColor.GRAY + "Effacer"),
                        e -> {
                            if (currentNumber.length() > 0) {
                                currentNumber.deleteCharAt(currentNumber.length() - 1);
                                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
                                openAddContactGUI(p);
                            }
                        }
                    ));

                    // Bouton Valider
                    boolean canValidate = currentNumber.length() == 7;
                    contents.set(4, 3, ClickableItem.of(
                        createItem(
                            canValidate ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE,
                            (canValidate ? ChatColor.GREEN : ChatColor.GRAY) + "" + ChatColor.BOLD + "\u2714",
                            canValidate ? ChatColor.GREEN + "VALIDER" : ChatColor.RED + "7 chiffres"
                        ),
                        e -> {
                            StringBuilder compNumber = addingNumbers.get(uuid);
                            if (compNumber == null || compNumber.length() != 7) {
                                p.sendMessage(PhoneMessages.ENTER_7_DIGITS);
                                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
                                return;
                            }

                            String number = formatPhoneNumber(compNumber.toString());

                            // Verifier si le numero existe
                            if (!phoneService.phoneNumberExists(number)) {
                                p.sendMessage(PhoneMessages.INVALID_NUMBER);
                                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
                                return;
                            }

                            // Verifier si deja en contact
                            if (phoneService.hasContact(p, number)) {
                                p.sendMessage(PhoneMessages.CONTACT_EXISTS);
                                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
                                return;
                            }

                            // Demander le nom du contact
                            addingNumbers.remove(uuid);
                            p.closeInventory();
                            p.sendMessage(PhoneMessages.ENTER_CONTACT_NAME);
                            p.sendMessage(PhoneMessages.SMS_CANCEL_HINT);
                            PhoneChatListener.awaitInput(p, PhoneChatListener.InputType.CONTACT_NAME, number);
                        }
                    ));

                    // === LIGNE 5: Dock - Retour ===
                    contents.set(5, 1, ClickableItem.of(
                        createItem(Material.ARROW, ChatColor.RED + "Retour"),
                        e -> {
                            addingNumbers.remove(uuid);
                            new PhoneContactsGUI(plugin).open(p);
                        }
                    ));
                }

                @Override
                public void update(Player player, InventoryContents contents) {}
            })
            .size(6, 9)
            .title(ChatColor.AQUA + "+ Ajouter Contact")
            .manager(Main.bankMenuInventoryManager)
            .build()
            .open(player);
    }

    private ClickableItem createNumberButton(Player player, String digit) {
        UUID uuid = player.getUniqueId();
        ItemStack button = createItem(Material.LIGHT_GRAY_CONCRETE,
            ChatColor.WHITE + "" + ChatColor.BOLD + digit
        );
        return ClickableItem.of(button, e -> {
            StringBuilder currentNumber = addingNumbers.get(uuid);
            if (currentNumber != null && currentNumber.length() < 7) {
                currentNumber.append(digit);
                float pitch = 1.0f + (Float.parseFloat(digit) * 0.05f);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 0.5f, pitch);
                openAddContactGUI(player);
            } else if (currentNumber != null && currentNumber.length() >= 7) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
            }
        });
    }

    /**
     * Ouvre une confirmation de suppression de contact - compact.
     */
    private void openDeleteConfirmation(Player player, int contactId, String contactName) {
        SmartInventory.builder()
            .id("phone_delete_contact")
            .provider(new InventoryProvider() {
                @Override
                public void init(Player p, InventoryContents contents) {
                    // Initialiser le layout standard (colonnes 0-3 ecran, 4-8 ItemsAdder)
                    PhoneGUIUtils.initPhoneLayout(contents, 4);

                    // Question
                    ItemStack questionItem = createItem(Material.PLAYER_HEAD,
                        ChatColor.YELLOW + "Supprimer " + contactName + " ?",
                        ChatColor.RED + "Action irreversible"
                    );
                    contents.set(0, 1, ClickableItem.empty(questionItem));
                    contents.set(0, 2, ClickableItem.empty(questionItem));

                    // Confirmer
                    contents.set(2, 0, ClickableItem.of(
                        createItem(Material.LIME_CONCRETE,
                            ChatColor.GREEN + "" + ChatColor.BOLD + "\u2714 Oui",
                            ChatColor.GRAY + "Supprimer"
                        ),
                        e -> {
                            phoneService.deleteContact(p, contactId);
                            p.sendMessage(PhoneMessages.CONTACT_DELETED);
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
                            new PhoneContactsGUI(plugin).open(p);
                        }
                    ));

                    // Annuler
                    contents.set(2, 3, ClickableItem.of(
                        createItem(Material.RED_CONCRETE,
                            ChatColor.RED + "" + ChatColor.BOLD + "\u2716 Non",
                            ChatColor.GRAY + "Garder"
                        ),
                        e -> new PhoneContactsGUI(plugin).open(p)
                    ));

                    // Retour
                    contents.set(3, 1, ClickableItem.of(
                        createItem(Material.ARROW, ChatColor.RED + "Retour"),
                        e -> new PhoneContactsGUI(plugin).open(p)
                    ));
                }

                @Override
                public void update(Player player, InventoryContents contents) {}
            })
            .size(4, 9)
            .title(ChatColor.RED + "Supprimer ?")
            .manager(Main.bankMenuInventoryManager)
            .build()
            .open(player);
    }

    @Override
    public void update(Player player, InventoryContents contents) {}

    // === UTILITAIRES ===

    private void playClick(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }

    private String formatDisplayNumber(String number) {
        if (number.isEmpty()) return "___-____";
        StringBuilder display = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if (i == 3) display.append("-");
            display.append(i < number.length() ? number.charAt(i) : "_");
        }
        return display.toString();
    }

    private String formatPhoneNumber(String number) {
        if (number.length() != 7) return number;
        return number.substring(0, 3) + "-" + number.substring(3);
    }

    private ItemStack createItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (loreLines.length > 0) {
                List<String> lore = new ArrayList<>();
                for (String line : loreLines) {
                    if (line != null && !line.isEmpty()) lore.add(line);
                }
                if (!lore.isEmpty()) meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createPlayerHead(String playerName) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
            if (offlinePlayer.hasPlayedBefore()) {
                meta.setOwningPlayer(offlinePlayer);
            }
            head.setItemMeta(meta);
        }
        return head;
    }
}
