package com.gravityyfh.roleplaycity.backpack.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.backpack.gui.BackpackGUI;
import com.gravityyfh.roleplaycity.backpack.manager.BackpackItemManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listener pour gérer l'ouverture et la fermeture des backpacks
 */
public class BackpackInteractionListener implements Listener {
    private final RoleplayCity plugin;
    private final BackpackItemManager itemManager;
    private final BackpackGUI backpackGUI;

    // Cooldown pour éviter le double-clic (500ms)
    private final Map<UUID, Long> openCooldown;
    private static final long COOLDOWN_MS = 500;

    public BackpackInteractionListener(RoleplayCity plugin, BackpackItemManager itemManager, BackpackGUI backpackGUI) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.backpackGUI = backpackGUI;
        this.openCooldown = new HashMap<>();
    }

    /**
     * Gère le clic droit sur un backpack pour l'ouvrir
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();

        // Vérifier que c'est un clic droit
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Vérifier la main principale (éviter le double événement)
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();

        // Vérifier si c'est un backpack
        if (!itemManager.isBackpack(item)) {
            return;
        }

        // Annuler l'événement pour éviter d'autres interactions
        event.setCancelled(true);

        // Vérifier le cooldown
        UUID playerUUID = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (openCooldown.containsKey(playerUUID)) {
            long lastUse = openCooldown.get(playerUUID);
            if (currentTime - lastUse < COOLDOWN_MS) {
                return; // Ignorer le double-clic
            }
        }

        // Mettre à jour le cooldown
        openCooldown.put(playerUUID, currentTime);

        // Ouvrir le backpack
        boolean success = backpackGUI.openBackpack(player, item);

        if (!success) {
            player.sendMessage(ChatColor.RED + "Impossible d'ouvrir le backpack!");
        }
    }

    /**
     * Gère la fermeture de l'inventaire pour sauvegarder le contenu
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        // Vérifier si c'est un backpack
        if (!backpackGUI.isBackpackInventory(event.getInventory(), player)) {
            return;
        }

        // Sauvegarder le backpack
        backpackGUI.closeBackpack(player, event.getInventory());
    }

    /**
     * Gère les clics dans l'inventaire du backpack pour vérifier la blacklist
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Vérifier si c'est un inventaire de backpack
        if (!backpackGUI.isBackpackInventory(event.getInventory(), player)) {
            return;
        }

        // Récupérer l'item cliqué
        ItemStack clickedItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        // Vérifier si le joueur essaie de mettre un item dans le backpack
        if (clickedItem == null || clickedItem.getType().isAir()) {
            // Le joueur place un item du curseur dans le backpack
            if (cursorItem != null && !cursorItem.getType().isAir()) {
                if (isProhibitedItem(cursorItem, player)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        // Vérifier les shift-clicks depuis l'inventaire du joueur
        if (event.isShiftClick() && event.getClickedInventory() == player.getInventory()) {
            if (clickedItem != null && !clickedItem.getType().isAir()) {
                if (isProhibitedItem(clickedItem, player)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    /**
     * Vérifie si un item est interdit dans le backpack
     *
     * @param item L'item à vérifier
     * @param player Le joueur qui essaie de placer l'item
     * @return true si l'item est interdit, false sinon
     */
    private boolean isProhibitedItem(ItemStack item, Player player) {
        // Vérifier si c'est un autre backpack (empêcher l'imbrication)
        if (itemManager.isBackpack(item)) {
            player.sendMessage(ChatColor.RED + "Vous ne pouvez pas mettre un backpack dans un autre backpack!");
            return true;
        }

        // Vérifier la blacklist globale
        String materialName = item.getType().name();

        // Vérifier les shulker boxes
        if (materialName.contains("SHULKER_BOX")) {
            player.sendMessage(ChatColor.RED + "Vous ne pouvez pas mettre de shulker box dans un backpack!");
            return true;
        }

        // Vérifier les bundles
        if (materialName.equals("BUNDLE")) {
            player.sendMessage(ChatColor.RED + "Vous ne pouvez pas mettre de bundle dans un backpack!");
            return true;
        }

        // Vérifier les ender chests
        if (materialName.equals("ENDER_CHEST")) {
            player.sendMessage(ChatColor.RED + "Vous ne pouvez pas mettre d'ender chest dans un backpack!");
            return true;
        }

        // Vérifier la whitelist du type de backpack
        var context = backpackGUI.getOpenBackpackContext(player);
        if (context != null) {
            var type = itemManager.getBackpackType(context.backpackItem());
            if (type != null && type.hasWhitelist()) {
                if (!type.isItemAllowed(item.getType())) {
                    player.sendMessage(ChatColor.RED + "Ce backpack n'accepte pas cet item!");
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Gère la déconnexion d'un joueur pour nettoyer le tracking
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Nettoyer le cooldown
        openCooldown.remove(playerUUID);

        // Nettoyer le tracking des backpacks ouverts (si le joueur se déconnecte sans fermer)
        if (backpackGUI.hasOpenBackpack(player)) {
            backpackGUI.closeBackpackWithoutSaving(player);
        }
    }
}
