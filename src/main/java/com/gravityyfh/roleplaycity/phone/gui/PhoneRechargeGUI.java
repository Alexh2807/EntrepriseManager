package com.gravityyfh.roleplaycity.phone.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.phone.PhoneManager;
import com.gravityyfh.roleplaycity.phone.model.PlanType;
import com.gravityyfh.roleplaycity.phone.service.PhoneService;
import de.lightplugins.economy.master.Main;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * GUI de recharge du telephone.
 * Permet de deposer un forfait dans un slot pour recharger.
 */
public class PhoneRechargeGUI implements Listener {

    private final RoleplayCity plugin;
    private final PhoneManager phoneManager;
    private final PhoneService phoneService;

    // Nouveau layout: colonnes 0-3 = ecran, colonnes 4-8 = background ItemsAdder
    private static final int SLOT_FORFAIT = 19; // Slot dans la zone ecran (ligne 2, col 1)
    private static final int SLOT_VALIDER = 28; // Bouton valider (ligne 3, col 1)
    private static final int SLOT_RETOUR = 46; // Bouton retour (ligne 5, col 1)

    // Map pour tracker les GUIs ouverts
    private static final Map<UUID, PhoneRechargeGUI> openGuis = new HashMap<>();

    private Inventory inventory;
    private Player player;
    private boolean registered = false;

    public PhoneRechargeGUI(RoleplayCity plugin) {
        this.plugin = plugin;
        this.phoneManager = plugin.getPhoneManager();
        this.phoneService = plugin.getPhoneService();
    }

    public void open(Player player) {
        if (Main.bankMenuInventoryManager == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Interface non initialisee.");
            return;
        }

        this.player = player;

        // Fermer l'ancien GUI si existant
        PhoneRechargeGUI oldGui = openGuis.get(player.getUniqueId());
        if (oldGui != null) {
            oldGui.cleanup();
        }

        // Creer l'inventaire
        inventory = Bukkit.createInventory(null, 54, ChatColor.DARK_GRAY + "\u26A1 Recharge");

        // Nouveau layout: colonnes 0-3 = ecran noir, colonnes 4-8 = background ItemsAdder
        ItemStack screenBg = PhoneGUIUtils.createScreenBackground();
        ItemStack externalBg = PhoneGUIUtils.createExternalBackground();

        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = row * 9 + col;
                if (col <= 3) {
                    inventory.setItem(slot, screenBg);
                } else {
                    inventory.setItem(slot, externalBg);
                }
            }
        }

        // Recuperer les credits actuels
        ItemStack phoneItem = phoneManager.findPhoneInInventory(player);
        int currentCredits = phoneItem != null ? phoneManager.getCredits(phoneItem) : 0;

        // Ligne 0: Barre de statut (colonnes 0-3)
        ItemStack statusItem = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta statusMeta = statusItem.getItemMeta();
        if (statusMeta != null) {
            statusMeta.setDisplayName(ChatColor.YELLOW + "Credits actuels: " + currentCredits);
            statusItem.setItemMeta(statusMeta);
        }
        for (int col = 0; col <= 3; col++) {
            inventory.setItem(col, statusItem);
        }

        // Ligne 1: Instructions (colonnes 1-2)
        ItemStack infoItem = createItem(Material.PAPER,
            ChatColor.WHITE + "Comment recharger?",
            "",
            ChatColor.GRAY + "1. Placez votre forfait",
            ChatColor.GRAY + "   dans le slot ci-dessous",
            "",
            ChatColor.GRAY + "2. Cliquez sur Valider"
        );
        inventory.setItem(10, infoItem); // Ligne 1, col 1
        inventory.setItem(11, infoItem); // Ligne 1, col 2

        // Ligne 2: Slot pour le forfait (vide par defaut)
        inventory.setItem(SLOT_FORFAIT, null); // Slot vide pour deposer (ligne 2, col 1)

        // Indicateur autour du slot
        ItemStack slotIndicator = createItem(Material.YELLOW_STAINED_GLASS_PANE,
            ChatColor.YELLOW + "Deposez ici",
            ChatColor.GRAY + "Glissez un forfait"
        );
        inventory.setItem(SLOT_FORFAIT - 1, slotIndicator); // col 0
        inventory.setItem(SLOT_FORFAIT + 1, slotIndicator); // col 2
        inventory.setItem(SLOT_FORFAIT - 9, slotIndicator); // ligne 1 col 1
        inventory.setItem(SLOT_FORFAIT + 9, slotIndicator); // ligne 3 col 1
        inventory.setItem(20, slotIndicator); // col 2 ligne 2

        // Ligne 3: Bouton Valider (colonnes 1-2)
        ItemStack validateItem = createItem(Material.LIME_CONCRETE,
            ChatColor.GREEN + "" + ChatColor.BOLD + "Valider",
            "",
            ChatColor.GRAY + "Appliquer le forfait"
        );
        inventory.setItem(SLOT_VALIDER, validateItem); // Ligne 3, col 1
        inventory.setItem(SLOT_VALIDER + 1, validateItem); // Ligne 3, col 2

        // Ligne 5: Bouton Retour (colonnes 1-2)
        ItemStack backItem = createItem(Material.ARROW, ChatColor.RED + "Retour");
        inventory.setItem(SLOT_RETOUR, backItem); // Ligne 5, col 1
        inventory.setItem(SLOT_RETOUR + 1, backItem); // Ligne 5, col 2

        // Enregistrer le listener
        if (!registered) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            registered = true;
        }

        openGuis.put(player.getUniqueId(), this);
        player.openInventory(inventory);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player clicker)) return;
        if (!clicker.getUniqueId().equals(player.getUniqueId())) return;
        if (!event.getView().getTitle().equals(ChatColor.DARK_GRAY + "\u26A1 Recharge")) return;

        int slot = event.getRawSlot();

        // Autoriser les clics dans l'inventaire du joueur (slots 54+)
        if (slot >= 54) {
            return; // Laisser le joueur interagir avec son inventaire
        }

        // Slot forfait - autoriser le depot/retrait
        if (slot == SLOT_FORFAIT) {
            // Laisser le joueur deposer/retirer un item
            return;
        }

        // Bloquer les autres slots
        event.setCancelled(true);

        // Bouton Valider
        if (slot == SLOT_VALIDER) {
            handleValidation(clicker);
            return;
        }

        // Bouton Retour
        if (slot == SLOT_RETOUR) {
            clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
            returnItemToPlayer(clicker);
            cleanup();
            new PhoneMainGUI(plugin).open(clicker);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player clicker)) return;
        if (!clicker.getUniqueId().equals(player.getUniqueId())) return;
        if (!event.getView().getTitle().equals(ChatColor.DARK_GRAY + "\u26A1 Recharge")) return;

        // Autoriser le drag seulement sur le slot forfait
        for (int slot : event.getRawSlots()) {
            if (slot < 54 && slot != SLOT_FORFAIT) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player clicker)) return;
        if (!clicker.getUniqueId().equals(player.getUniqueId())) return;
        if (!event.getView().getTitle().equals(ChatColor.DARK_GRAY + "\u26A1 Recharge")) return;

        // Rendre l'item au joueur si present
        returnItemToPlayer(clicker);
        cleanup();
    }

    private void handleValidation(Player clicker) {
        ItemStack forfaitItem = inventory.getItem(SLOT_FORFAIT);

        // Verifier si un forfait est present
        if (forfaitItem == null || forfaitItem.getType() == Material.AIR) {
            clicker.playSound(clicker.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            clicker.sendMessage(ChatColor.RED + "[Tel] Deposez un forfait dans le slot.");
            return;
        }

        // Verifier si c'est un forfait valide
        if (!phoneManager.isPlan(forfaitItem)) {
            clicker.playSound(clicker.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            clicker.sendMessage(ChatColor.RED + "[Tel] Cet item n'est pas un forfait valide.");
            return;
        }

        // Trouver le telephone du joueur
        ItemStack phoneItem = phoneManager.findPhoneInInventory(clicker);
        if (phoneItem == null) {
            clicker.playSound(clicker.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            clicker.sendMessage(ChatColor.RED + "[Tel] Vous n'avez pas de telephone.");
            return;
        }

        // Recuperer le type de forfait
        PlanType planType = phoneManager.getPlanType(forfaitItem);
        if (planType == null) {
            clicker.playSound(clicker.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
            clicker.sendMessage(ChatColor.RED + "[Tel] Forfait invalide.");
            return;
        }

        // Appliquer la recharge
        int creditsToAdd = planType.getCredits();
        phoneManager.addCredits(phoneItem, creditsToAdd);

        // Consommer le forfait (1 seul)
        if (forfaitItem.getAmount() > 1) {
            forfaitItem.setAmount(forfaitItem.getAmount() - 1);
        } else {
            inventory.setItem(SLOT_FORFAIT, null);
        }

        // Feedback
        clicker.playSound(clicker.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
        clicker.sendMessage(ChatColor.GREEN + "[Tel] +" + creditsToAdd + " credits ajoutes!");

        // Rafraichir l'affichage des credits
        int newCredits = phoneManager.getCredits(phoneItem);
        ItemStack statusItem = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta statusMeta = statusItem.getItemMeta();
        if (statusMeta != null) {
            statusMeta.setDisplayName(ChatColor.YELLOW + "Credits actuels: " + newCredits);
            statusItem.setItemMeta(statusMeta);
        }
        for (int col = 0; col <= 3; col++) {
            inventory.setItem(col, statusItem);
        }
    }

    private void returnItemToPlayer(Player clicker) {
        if (inventory == null) return;

        ItemStack forfaitItem = inventory.getItem(SLOT_FORFAIT);
        if (forfaitItem != null && forfaitItem.getType() != Material.AIR) {
            // Rendre l'item au joueur
            HashMap<Integer, ItemStack> leftover = clicker.getInventory().addItem(forfaitItem);
            if (!leftover.isEmpty()) {
                // Si l'inventaire est plein, drop l'item
                for (ItemStack item : leftover.values()) {
                    clicker.getWorld().dropItemNaturally(clicker.getLocation(), item);
                }
            }
            inventory.setItem(SLOT_FORFAIT, null);
        }
    }

    private void cleanup() {
        if (registered) {
            HandlerList.unregisterAll(this);
            registered = false;
        }
        openGuis.remove(player.getUniqueId());
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
