package com.gravityyfh.roleplaycity.customitems.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.backpack.listener.BackpackCraftListener;
import com.gravityyfh.roleplaycity.backpack.manager.BackpackItemManager;
import com.gravityyfh.roleplaycity.customitems.manager.CustomItemManager;
import dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Listener unifié pour gérer le chargement d'ItemsAdder
 * Rafraîchit TOUS les systèmes qui dépendent d'ItemsAdder (CustomItems + Backpacks)
 */
public class ItemsAdderLoadListener implements Listener {

    private final RoleplayCity plugin;
    private final CustomItemManager customItemManager;
    private final CustomItemCraftListener customItemCraftListener;
    private final BackpackItemManager backpackItemManager;
    private final BackpackCraftListener backpackCraftListener;

    public ItemsAdderLoadListener(RoleplayCity plugin,
                                  CustomItemManager customItemManager,
                                  CustomItemCraftListener customItemCraftListener,
                                  BackpackItemManager backpackItemManager,
                                  BackpackCraftListener backpackCraftListener) {
        this.plugin = plugin;
        this.customItemManager = customItemManager;
        this.customItemCraftListener = customItemCraftListener;
        this.backpackItemManager = backpackItemManager;
        this.backpackCraftListener = backpackCraftListener;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemsAdderLoad(ItemsAdderLoadDataEvent event) {
        plugin.getLogger().info("════════════════════════════════════════════════════════════════");
        plugin.getLogger().info("[ItemsAdder] DONNÉES COMPLÈTEMENT CHARGÉES!");
        plugin.getLogger().info("[ItemsAdder] Rafraîchissement de tous les systèmes dépendants...");
        plugin.getLogger().info("════════════════════════════════════════════════════════════════");

        // ═══════════════════════════════════════════════════════════════════════
        // SYSTÈME CUSTOM ITEMS
        // ═══════════════════════════════════════════════════════════════════════
        if (customItemManager != null) {
            plugin.getLogger().info("[CustomItems] Rafraîchissement...");

            // Rafraîchir le statut ItemsAdder
            customItemManager.refreshItemsAdderAvailability();

            // Recharger les définitions d'items
            customItemManager.loadItems();
            plugin.getLogger().info("[CustomItems] ✓ Items rechargés");

            // Ré-enregistrer les recettes avec les vrais items ItemsAdder
            if (customItemCraftListener != null) {
                customItemCraftListener.registerRecipes();
                plugin.getLogger().info("[CustomItems] ✓ Recettes ré-enregistrées");
            }
        }

        // ═══════════════════════════════════════════════════════════════════════
        // SYSTÈME BACKPACKS
        // ═══════════════════════════════════════════════════════════════════════
        if (backpackItemManager != null) {
            plugin.getLogger().info("[Backpacks] Rafraîchissement...");

            // Rafraîchir le statut ItemsAdder
            backpackItemManager.refreshItemsAdderAvailability();

            // Recharger la configuration des backpacks
            backpackItemManager.loadConfiguration();
            plugin.getLogger().info("[Backpacks] ✓ Configuration rechargée");

            // Ré-enregistrer les recettes
            if (backpackCraftListener != null) {
                backpackCraftListener.registerRecipes();
                plugin.getLogger().info("[Backpacks] ✓ Recettes ré-enregistrées");
            }
        }

        plugin.getLogger().info("════════════════════════════════════════════════════════════════");
        plugin.getLogger().info("[ItemsAdder] Tous les systèmes sont prêts avec les textures ItemsAdder!");
        plugin.getLogger().info("════════════════════════════════════════════════════════════════");
    }
}
