package com.gravityyfh.roleplaycity.backpack.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.backpack.manager.BackpackItemManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

/**
 * Listener pour gérer l'équipement des backpacks sur la tête
 * Détecte quand un joueur tente de placer un backpack dans le slot casque
 */
public class BackpackArmorEquipListener implements Listener {
    private final RoleplayCity plugin;
    private final BackpackItemManager itemManager;

    public BackpackArmorEquipListener(RoleplayCity plugin, BackpackItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
    }

    /**
     * Gère les tentatives de placement d'un backpack sur la tête via l'inventaire
     * Autorise uniquement si le joueur est accroupi (shift)
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Vérifier que c'est l'inventaire du joueur
        if (event.getClickedInventory() == null) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        // Cas 1: Le joueur clique sur le slot casque avec un backpack sur le curseur
        if (event.getSlot() == 39 && event.getSlotType() == InventoryType.SlotType.ARMOR) {
            if (cursorItem != null && itemManager.isBackpack(cursorItem)) {
                handleBackpackEquip(event, player);
                return;
            }
        }

        // Cas 2: Shift-clic sur un backpack pour l'équiper automatiquement
        if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
            if (clickedItem != null && itemManager.isBackpack(clickedItem)) {
                // Vérifier si le casque est vide (car le shift-clic l'équiperait)
                if (player.getInventory().getHelmet() == null || player.getInventory().getHelmet().getType().isAir()) {
                    handleBackpackEquip(event, player);
                    return;
                }
            }
        }

        // Cas 3: Drag d'un backpack vers le slot casque
        if (event.getAction() == InventoryAction.PLACE_ALL ||
            event.getAction() == InventoryAction.PLACE_ONE ||
            event.getAction() == InventoryAction.PLACE_SOME) {
            if (event.getSlot() == 39 && event.getSlotType() == InventoryType.SlotType.ARMOR) {
                if (cursorItem != null && itemManager.isBackpack(cursorItem)) {
                    handleBackpackEquip(event, player);
                    return;
                }
            }
        }

        // Cas 4: Clic numérique (1-9) pour swapper avec le casque
        if (event.getClick().isKeyboardClick()) {
            if (event.getSlot() == 39 && event.getSlotType() == InventoryType.SlotType.ARMOR) {
                int hotbarSlot = event.getHotbarButton();
                if (hotbarSlot >= 0 && hotbarSlot < 9) {
                    ItemStack hotbarItem = player.getInventory().getItem(hotbarSlot);
                    if (hotbarItem != null && itemManager.isBackpack(hotbarItem)) {
                        handleBackpackEquip(event, player);
                    }
                }
            }
        }
    }

    /**
     * Gère la logique d'équipement du backpack
     * Annule si le joueur n'est pas accroupi
     */
    private void handleBackpackEquip(InventoryClickEvent event, Player player) {
        if (!player.isSneaking()) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Maintenez Shift et faites un clic droit hors inventaire pour placer le backpack sur la tête!");
            player.sendMessage(ChatColor.GRAY + "Utilisez un clic droit normal pour ouvrir le backpack.");
        } else {
            // Le joueur est accroupi, autoriser l'équipement
            player.sendMessage(ChatColor.GREEN + "Backpack équipé sur la tête!");
        }
    }
}
