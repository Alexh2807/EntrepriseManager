package com.gravityyfh.roleplaycity.phone.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.phone.PhoneManager;
import com.gravityyfh.roleplaycity.phone.listener.PhoneChatListener;
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
 * GUI pour les parametres du telephone.
 * Interface compacte de 4 blocs de large centree.
 * Design epure: noir a l'exterieur.
 */
public class PhoneSettingsGUI implements InventoryProvider {

    private final RoleplayCity plugin;
    private final PhoneManager phoneManager;
    private final PhoneService phoneService;

    public PhoneSettingsGUI(RoleplayCity plugin) {
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
            .id("phone_settings")
            .provider(this)
            .size(6, 9)
            .title(ChatColor.DARK_GRAY + "\u2699 Reglages")
            .manager(Main.bankMenuInventoryManager)
            .build()
            .open(player);
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        // Initialiser le layout standard (colonnes 0-3 ecran, 4-8 ItemsAdder)
        PhoneGUIUtils.initPhoneLayout(contents, 6);

        ItemStack phoneItem = phoneManager.findPhoneInInventory(player);
        PhoneAccount account = phoneService.getOrCreateAccount(player);

        String phoneNumber = phoneItem != null ? phoneManager.getPhoneNumber(phoneItem) : "???-????";
        int credits = phoneItem != null ? phoneManager.getCredits(phoneItem) : 0;

        // === LIGNE 1: Infos telephone ===
        var phoneTypeObj = phoneItem != null ? phoneManager.getPhoneType(phoneItem) : null;
        String phoneTypeName = phoneTypeObj != null ? phoneTypeObj.getDisplayName() : "Standard";

        ItemStack infoItem = createItem(Material.PAPER,
            ChatColor.WHITE + "Informations",
            "",
            ChatColor.GRAY + "Numero: " + ChatColor.WHITE + phoneNumber,
            ChatColor.GRAY + "Modele: " + ChatColor.WHITE + phoneTypeName,
            ChatColor.GRAY + "Credits: " + ChatColor.GREEN + credits,
            "",
            ChatColor.GRAY + "Cout appel: " + ChatColor.YELLOW + phoneManager.getCallCostPerMinute() + "/min",
            ChatColor.GRAY + "Cout SMS: " + ChatColor.YELLOW + phoneManager.getSmsCost()
        );
        contents.set(1, 1, ClickableItem.empty(infoItem));
        contents.set(1, 2, ClickableItem.empty(infoItem));

        // === LIGNE 2: Mode silencieux + Stats + Bloques ===
        boolean silentMode = account.isSilentMode();
        ItemStack silentItem = createItem(
            silentMode ? Material.GRAY_CONCRETE : Material.LIME_CONCRETE,
            (silentMode ? ChatColor.GRAY : ChatColor.GREEN) + "Silencieux",
            "",
            ChatColor.GRAY + "Status: " + (silentMode ? ChatColor.RED + "Active" : ChatColor.GREEN + "Desactive"),
            "",
            ChatColor.YELLOW + "Cliquez pour basculer"
        );
        contents.set(2, 0, ClickableItem.of(silentItem, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            phoneService.toggleSilentMode(player);
            open(player);
        }));

        // Stats
        int totalCalls = account.getTotalCalls();
        int totalSms = account.getTotalSms();
        long accountCreated = account.getCreatedAt();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        String createdDate = accountCreated > 0 ? sdf.format(new Date(accountCreated)) : "N/A";

        ItemStack statsItem = createItem(Material.BOOK,
            ChatColor.WHITE + "Statistiques",
            "",
            ChatColor.GRAY + "Appels: " + ChatColor.WHITE + totalCalls,
            ChatColor.GRAY + "SMS: " + ChatColor.WHITE + totalSms,
            ChatColor.GRAY + "Depuis: " + ChatColor.WHITE + createdDate
        );
        contents.set(2, 1, ClickableItem.empty(statsItem));
        contents.set(2, 2, ClickableItem.empty(statsItem));

        // Bloquer numeros
        ItemStack blockItem = createItem(Material.BARRIER,
            ChatColor.RED + "Bloques",
            "",
            ChatColor.GRAY + "Numeros bloques: " + ChatColor.WHITE + account.getBlockedNumbers().size(),
            "",
            ChatColor.YELLOW + "Cliquez pour gerer"
        );
        contents.set(2, 3, ClickableItem.of(blockItem, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            openBlockedNumbersGUI(player);
        }));

        // === LIGNE 3: Changer numero ===
        int changeNumberCost = phoneManager.getChangeNumberCost();
        ItemStack changeNumberItem = createItem(Material.NAME_TAG,
            ChatColor.LIGHT_PURPLE + "Nouveau numero",
            "",
            ChatColor.GRAY + "Cout: " + ChatColor.YELLOW + changeNumberCost + " credits",
            "",
            ChatColor.RED + "Irreversible!",
            "",
            ChatColor.YELLOW + "Cliquez pour changer"
        );
        contents.set(3, 1, ClickableItem.of(changeNumberItem, e -> {
            if (phoneItem == null) {
                player.sendMessage(ChatColor.RED + "[Tel] Aucun telephone trouve.");
                return;
            }
            if (credits < changeNumberCost) {
                player.sendMessage(ChatColor.RED + "[Tel] Credits insuffisants. Cout: " + changeNumberCost);
                return;
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            openChangeNumberConfirmation(player, changeNumberCost);
        }));
        contents.set(3, 2, ClickableItem.of(changeNumberItem, e -> {
            if (phoneItem == null) {
                player.sendMessage(ChatColor.RED + "[Tel] Aucun telephone trouve.");
                return;
            }
            if (credits < changeNumberCost) {
                player.sendMessage(ChatColor.RED + "[Tel] Credits insuffisants. Cout: " + changeNumberCost);
                return;
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            openChangeNumberConfirmation(player, changeNumberCost);
        }));

        // === LIGNE 5: Dock - Retour ===
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

    /**
     * Ouvre le GUI des numeros bloques (compact).
     */
    private void openBlockedNumbersGUI(Player player) {
        PhoneAccount account = phoneService.getOrCreateAccount(player);
        List<String> blockedNumbers = new ArrayList<>(account.getBlockedNumbers());

        SmartInventory.builder()
            .id("phone_blocked")
            .provider(new InventoryProvider() {
                @Override
                public void init(Player p, InventoryContents contents) {
                    // Initialiser le layout standard (colonnes 0-3 ecran, 4-8 ItemsAdder)
                    PhoneGUIUtils.initPhoneLayout(contents, 6);

                    // Ajouter
                    ItemStack addItem = createItem(Material.ANVIL,
                        ChatColor.GREEN + "+ Bloquer",
                        "",
                        ChatColor.YELLOW + "Cliquez pour ajouter"
                    );
                    contents.set(0, 2, ClickableItem.of(addItem, e -> {
                        p.closeInventory();
                        p.sendMessage("");
                        p.sendMessage(ChatColor.YELLOW + "[Tel] " + ChatColor.WHITE + "Entrez le numero a bloquer:");
                        p.sendMessage(ChatColor.GRAY + "(Format: XXX-XXXX ou tapez 'annuler')");
                        PhoneChatListener.awaitInput(p, PhoneChatListener.InputType.BLOCK_NUMBER, null);
                    }));
                    contents.set(0, 3, ClickableItem.of(addItem, e -> {
                        p.closeInventory();
                        p.sendMessage("");
                        p.sendMessage(ChatColor.YELLOW + "[Tel] " + ChatColor.WHITE + "Entrez le numero a bloquer:");
                        p.sendMessage(ChatColor.GRAY + "(Format: XXX-XXXX ou tapez 'annuler')");
                        PhoneChatListener.awaitInput(p, PhoneChatListener.InputType.BLOCK_NUMBER, null);
                    }));

                    // === LIGNES 1-4: Liste des bloques ===
                    if (blockedNumbers.isEmpty()) {
                        ItemStack emptyItem = createItem(Material.LIME_WOOL,
                            ChatColor.GREEN + "Aucun numero bloque",
                            "",
                            ChatColor.GRAY + "Votre liste est vide"
                        );
                        contents.set(2, 1, ClickableItem.empty(emptyItem));
                        contents.set(2, 2, ClickableItem.empty(emptyItem));
                    } else {
                        int index = 0;
                        for (int row = 1; row <= 4 && index < blockedNumbers.size(); row++) {
                            for (int col = 0; col <= 3 && index < blockedNumbers.size(); col++) {
                                String number = blockedNumbers.get(index);
                                String displayName = phoneService.getContactDisplayName(p.getUniqueId(), number);
                                if (displayName == null) displayName = number;

                                ItemStack blockedItem = createItem(Material.PLAYER_HEAD,
                                    ChatColor.RED + displayName,
                                    ChatColor.GRAY + number,
                                    "",
                                    ChatColor.YELLOW + "Clic = debloquer"
                                );

                                final String numberToUnblock = number;
                                contents.set(row, col, ClickableItem.of(blockedItem, e -> {
                                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                                    phoneService.unblockNumber(p, numberToUnblock);
                                    p.sendMessage(ChatColor.GREEN + "[Tel] " + numberToUnblock + " debloque.");
                                    openBlockedNumbersGUI(p);
                                }));
                                index++;
                            }
                        }
                    }

                    // === LIGNE 5: Dock - Retour ===
                    ItemStack backItem = createItem(Material.ARROW, ChatColor.RED + "Retour");
                    contents.set(5, 1, ClickableItem.of(backItem, e -> {
                        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
                        new PhoneSettingsGUI(plugin).open(p);
                    }));
                    contents.set(5, 2, ClickableItem.of(backItem, e -> {
                        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
                        new PhoneSettingsGUI(plugin).open(p);
                    }));
                }

                @Override
                public void update(Player player, InventoryContents contents) {}
            })
            .size(6, 9)
            .title(ChatColor.DARK_GRAY + "Bloques")
            .manager(Main.bankMenuInventoryManager)
            .build()
            .open(player);
    }

    /**
     * Ouvre la confirmation de changement de numero (compact).
     */
    private void openChangeNumberConfirmation(Player player, int cost) {
        SmartInventory.builder()
            .id("phone_change_number")
            .provider(new InventoryProvider() {
                @Override
                public void init(Player p, InventoryContents contents) {
                    // Initialiser le layout standard (colonnes 0-3 ecran, 4-8 ItemsAdder)
                    PhoneGUIUtils.initPhoneLayout(contents, 4);

                    // Question
                    ItemStack questionItem = createItem(Material.NAME_TAG,
                        ChatColor.YELLOW + "Changer de numero ?",
                        "",
                        ChatColor.GRAY + "Cout: " + ChatColor.YELLOW + cost + " credits",
                        "",
                        ChatColor.RED + "ATTENTION: Irreversible!"
                    );
                    contents.set(0, 1, ClickableItem.empty(questionItem));

                    // Confirmer
                    ItemStack confirmItem = createItem(Material.LIME_CONCRETE,
                        ChatColor.GREEN + "Confirmer",
                        "",
                        ChatColor.GRAY + "Changer mon numero"
                    );
                    contents.set(2, 0, ClickableItem.of(confirmItem, e -> {
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                        if (phoneService.changePhoneNumber(p, cost)) {
                            ItemStack phone = phoneManager.findPhoneInInventory(p);
                            String newNumber = phone != null ? phoneManager.getPhoneNumber(phone) : "???";
                            p.sendMessage(ChatColor.GREEN + "[Tel] Nouveau numero: " + ChatColor.WHITE + newNumber);
                        }
                        new PhoneSettingsGUI(plugin).open(p);
                    }));
                    contents.set(2, 1, ClickableItem.of(confirmItem, e -> {
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                        if (phoneService.changePhoneNumber(p, cost)) {
                            ItemStack phone = phoneManager.findPhoneInInventory(p);
                            String newNumber = phone != null ? phoneManager.getPhoneNumber(phone) : "???";
                            p.sendMessage(ChatColor.GREEN + "[Tel] Nouveau numero: " + ChatColor.WHITE + newNumber);
                        }
                        new PhoneSettingsGUI(plugin).open(p);
                    }));

                    // Annuler
                    ItemStack cancelItem = createItem(Material.RED_CONCRETE,
                        ChatColor.RED + "Annuler",
                        "",
                        ChatColor.GRAY + "Garder mon numero"
                    );
                    contents.set(2, 2, ClickableItem.of(cancelItem, e -> {
                        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
                        new PhoneSettingsGUI(plugin).open(p);
                    }));
                }

                @Override
                public void update(Player player, InventoryContents contents) {}
            })
            .size(4, 9)
            .title(ChatColor.DARK_GRAY + "Confirmation")
            .manager(Main.bankMenuInventoryManager)
            .build()
            .open(player);
    }

    @Override
    public void update(Player player, InventoryContents contents) {}

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
