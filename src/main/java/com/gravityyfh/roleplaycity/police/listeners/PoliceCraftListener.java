package com.gravityyfh.roleplaycity.police.listeners;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.police.items.PoliceItemManager;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

/**
 * Listener pour gérer les restrictions de craft des items de police
 * Seul le maire de la ville peut crafter les tasers et menottes
 * Note: Les items sont maintenant gérés via ItemsAdder (/iaget)
 */
public class PoliceCraftListener implements Listener {

    private final RoleplayCity plugin;
    private final PoliceItemManager itemManager;
    private final TownManager townManager;

    public PoliceCraftListener(RoleplayCity plugin, PoliceItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.townManager = plugin.getTownManager();
    }

    /**
     * Événement déclenché lors de la préparation d'un craft
     * Retire le résultat si le joueur n'est pas maire
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (event.getRecipe() == null) {
            return;
        }

        Recipe recipe = event.getRecipe();
        ItemStack result = recipe.getResult();

        // Vérifier si c'est un taser ou des menottes (ItemsAdder)
        if (!itemManager.isTaser(result) && !itemManager.isHandcuffs(result)) {
            return;
        }

        // Vérifier si le joueur est maire
        if (!(event.getView().getPlayer() instanceof Player player)) {
            return;
        }

        // Vérifier si le craft du maire est requis
        boolean mayorOnly = false;

        if (itemManager.isTaser(result)) {
            mayorOnly = plugin.getConfig().getBoolean("police-equipment.taser.crafting.mayor-only", true);
        } else if (itemManager.isHandcuffs(result)) {
            mayorOnly = plugin.getConfig().getBoolean("police-equipment.handcuffs.crafting.mayor-only", true);
        }

        if (!mayorOnly) {
            return; // Pas de restriction maire
        }

        // Vérifier si le joueur est maire
        if (!townManager.isPlayerMayor(player.getUniqueId())) {
            // Pas maire, bloquer le craft en retirant le résultat
            CraftingInventory inventory = event.getInventory();
            inventory.setResult(new ItemStack(Material.AIR));
        }
    }

    /**
     * Événement déclenché lors du craft final
     * Envoie un message d'erreur si le joueur n'est pas maire
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (event.getRecipe() == null) {
            return;
        }

        ItemStack result = event.getRecipe().getResult();

        // Vérifier si c'est un taser ou des menottes (ItemsAdder)
        if (!itemManager.isTaser(result) && !itemManager.isHandcuffs(result)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Vérifier si le craft du maire est requis
        boolean mayorOnly = false;
        String itemType = "";

        if (itemManager.isTaser(result)) {
            mayorOnly = plugin.getConfig().getBoolean("police-equipment.taser.crafting.mayor-only", true);
            itemType = "taser";
        } else if (itemManager.isHandcuffs(result)) {
            mayorOnly = plugin.getConfig().getBoolean("police-equipment.handcuffs.crafting.mayor-only", true);
            itemType = "menottes";
        }

        if (!mayorOnly) {
            return; // Pas de restriction maire
        }

        // Vérifier si le joueur est maire
        if (!townManager.isPlayerMayor(player.getUniqueId())) {
            event.setCancelled(true);

            // Message d'erreur personnalisé
            String message = plugin.getConfig().getString(
                "police-equipment.messages.mayor-only-craft",
                "§cSeul le Maire de la ville peut fabriquer des %item%!"
            );
            message = message.replace("%item%", itemType);

            player.sendMessage(message);
        } else {
            // Maire a crafté, message de confirmation
            String message = plugin.getConfig().getString(
                "police-equipment.messages.craft-success",
                "§aVous avez fabriqué un(e) %item% !"
            );
            message = message.replace("%item%", itemType);

            player.sendMessage(message);
        }
    }

    /**
     * Recharge la configuration
     * Appelé lors d'un /rpc reload
     */
    public void reloadRecipes() {
        // Recharger la configuration de l'ItemManager
        itemManager.loadConfiguration();
        plugin.getLogger().info("Configuration police rechargée (Taser & Menottes via ItemsAdder)");
    }
}
