package com.gravityyfh.roleplaycity.backpack.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.backpack.manager.BackpackItemManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Listener pour protéger les backpacks contre les abus
 */
public class BackpackProtectionListener implements Listener {
    private final RoleplayCity plugin;
    private final BackpackItemManager itemManager;

    public BackpackProtectionListener(RoleplayCity plugin, BackpackItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
    }

    /**
     * Empêche de placer un backpack comme bloc
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();

        if (!itemManager.isBackpack(item)) {
            return;
        }

        if (plugin.getConfig().getBoolean("backpack.protections.prevent-block-placement", true)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Vous ne pouvez pas placer un backpack comme bloc!");
        }
    }

    /**
     * Empêche de mettre un backpack dans un ender chest
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnderChestClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory topInventory = event.getView().getTopInventory();

        // Vérifier si l'inventaire du haut est un ender chest
        if (topInventory.getType() != InventoryType.ENDER_CHEST) {
            return;
        }

        if (!plugin.getConfig().getBoolean("backpack.protections.prevent-in-ender-chest", true)) {
            return;
        }

        // Vérifier l'item cliqué
        ItemStack clickedItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        // Cas 1: Le joueur place un item du curseur dans l'ender chest
        if (event.getClickedInventory() == topInventory) {
            if (cursorItem != null && itemManager.isBackpack(cursorItem)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Vous ne pouvez pas mettre un backpack dans un ender chest!");
                return;
            }
        }

        // Cas 2: Shift-click depuis l'inventaire du joueur vers l'ender chest
        if (event.isShiftClick() && event.getClickedInventory() == player.getInventory()) {
            if (clickedItem != null && itemManager.isBackpack(clickedItem)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Vous ne pouvez pas mettre un backpack dans un ender chest!");
                return;
            }
        }

        // Cas 3: Double-clic pour collecter tous les items similaires
        if (event.getClick().name().contains("DOUBLE_CLICK")) {
            if (cursorItem != null && itemManager.isBackpack(cursorItem)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Vous ne pouvez pas mettre un backpack dans un ender chest!");
            }
        }
    }

    /**
     * Empêche de mettre un backpack dans une shulker box
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShulkerBoxClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory topInventory = event.getView().getTopInventory();

        // Vérifier si l'inventaire du haut est une shulker box
        if (topInventory.getType() != InventoryType.SHULKER_BOX) {
            return;
        }

        if (!plugin.getConfig().getBoolean("backpack.protections.prevent-in-shulker-box", true)) {
            return;
        }

        // Vérifier l'item cliqué
        ItemStack clickedItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        // Cas 1: Le joueur place un item du curseur dans la shulker box
        if (event.getClickedInventory() == topInventory) {
            if (cursorItem != null && itemManager.isBackpack(cursorItem)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Vous ne pouvez pas mettre un backpack dans une shulker box!");
                return;
            }
        }

        // Cas 2: Shift-click depuis l'inventaire du joueur vers la shulker box
        if (event.isShiftClick() && event.getClickedInventory() == player.getInventory()) {
            if (clickedItem != null && itemManager.isBackpack(clickedItem)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Vous ne pouvez pas mettre un backpack dans une shulker box!");
                return;
            }
        }

        // Cas 3: Double-clic pour collecter tous les items similaires
        if (event.getClick().name().contains("DOUBLE_CLICK")) {
            if (cursorItem != null && itemManager.isBackpack(cursorItem)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Vous ne pouvez pas mettre un backpack dans une shulker box!");
            }
        }
    }

    /**
     * Empêche les interactions avec les backpacks dans certains inventaires
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryInteract(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        // Vérifier les anvils pour empêcher de renommer les backpacks
        if (event.getInventory().getType() == InventoryType.ANVIL) {
            if (plugin.getConfig().getBoolean("backpack.protections.prevent-rename", false)) {
                if ((clickedItem != null && itemManager.isBackpack(clickedItem)) ||
                    (cursorItem != null && itemManager.isBackpack(cursorItem))) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "Vous ne pouvez pas renommer un backpack!");
                }
            }
        }

        // Vérifier les grindstones pour empêcher de désenchanter les backpacks
        if (event.getInventory().getType() == InventoryType.GRINDSTONE) {
            if ((clickedItem != null && itemManager.isBackpack(clickedItem)) ||
                (cursorItem != null && itemManager.isBackpack(cursorItem))) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Vous ne pouvez pas utiliser un backpack dans un grindstone!");
            }
        }
    }
}
