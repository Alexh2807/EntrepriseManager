package com.gravityyfh.roleplaycity.Listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.*;

public class CraftItemListener implements Listener {

    private final EntrepriseManagerLogic entrepriseLogic;
    private final RoleplayCity plugin;

    public CraftItemListener(RoleplayCity plugin, EntrepriseManagerLogic entrepriseLogic) {
        this.plugin = plugin;
        this.entrepriseLogic = entrepriseLogic;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        Recipe recipe = event.getRecipe();
        if (recipe == null) return;

        ItemStack resultItem = recipe.getResult();

        // SIMPLIFIÉ: Ne plus utiliser calculateMaxCrafts() - cause le bug de désynchronisation
        // Le shift-click est géré automatiquement par Bukkit qui appelle cet event plusieurs fois
        int amountPerSingleCraft = resultItem.getAmount();

        // Gestion des Backpacks - Vérifier restrictions + enregistrer
        // IMPORTANT: Ne PAS utiliser calculateMaxCrafts() - on vérifie pour amountPerSingleCraft uniquement
        if (plugin.getBackpackItemManager() != null && plugin.getBackpackItemManager().isBackpack(resultItem)) {
            com.gravityyfh.roleplaycity.backpack.model.BackpackType backpackType = plugin.getBackpackItemManager().getBackpackType(resultItem);
            if (backpackType != null) {
                String backpackId = backpackType.getId();

                // Vérifier les restrictions pour CRAFT_BACKPACK (amountPerSingleCraft uniquement)
                boolean isBlocked = entrepriseLogic.verifierEtGererRestrictionAction(
                    player,
                    "CRAFT_BACKPACK",
                    backpackId,
                    amountPerSingleCraft
                );

                if (isBlocked) {
                    event.setCancelled(true);
                    return;
                }

                // Enregistrer l'action
                entrepriseLogic.enregistrerCraftBackpack(player, backpackId);

                // RETURN ICI pour éviter la logique vanilla (sinon ça compte aussi les matériaux LEATHER, etc.)
                return;
            }
        }

        // Gestion des Custom Items - Vérifier restrictions + enregistrer
        // IMPORTANT: Ne PAS utiliser calculateMaxCrafts() - on vérifie pour amountPerSingleCraft uniquement
        if (plugin.getCustomItemManager() != null && plugin.getCustomItemManager().isCustomItem(resultItem)) {
            com.gravityyfh.roleplaycity.customitems.model.CustomItem customItem = plugin.getCustomItemManager().getItemByItemStack(resultItem);
            if (customItem != null) {
                String itemId = customItem.getId();

                // Vérifier la restriction mayor_only
                if (customItem.getRecipe() != null && customItem.getRecipe().isMayorOnly()) {
                    TownManager townManager = plugin.getTownManager();
                    if (!townManager.isPlayerMayor(player.getUniqueId())) {
                        event.setCancelled(true);
                        String message = plugin.getConfig().getString(
                            "custom-items.messages.mayor-only-craft",
                            "§cSeul le Maire de la ville peut fabriquer cet item!"
                        );
                        player.sendMessage(message);
                        return;
                    }
                }

                // Vérifier les restrictions pour CRAFT_CUSTOM_ITEM (amountPerSingleCraft uniquement)
                boolean isBlocked = entrepriseLogic.verifierEtGererRestrictionAction(
                    player,
                    "CRAFT_CUSTOM_ITEM",
                    itemId,
                    amountPerSingleCraft
                );

                if (isBlocked) {
                    event.setCancelled(true);
                    return;
                }

                // Enregistrer l'action
                entrepriseLogic.enregistrerActionProductive(player, "CRAFT_CUSTOM_ITEM", itemId, 1);

                // RETURN ICI pour éviter la logique vanilla
                return;
            }
        }

        Material itemType = resultItem.getType();
        String itemTypeName = itemType.name();

        // Vérification et enregistrement pour 1 item à la fois (ou amountPerSingleCraft si recette produit plusieurs)
        boolean isBlocked = entrepriseLogic.verifierEtGererRestrictionAction(player, "CRAFT_ITEM", itemTypeName, amountPerSingleCraft);

        if (isBlocked) {
            event.setCancelled(true);
            return;
        }

        entrepriseLogic.enregistrerActionProductive(player, "CRAFT_ITEM", itemType, amountPerSingleCraft);
    }

    // NOTE: calculateMaxCrafts() SUPPRIMÉE
    // Cette méthode causait le bug de désynchronisation client/serveur (30 → 58 → 29)
    // en lisant inventory.getMatrix() pendant CraftItemEvent.
    // Solution: Bukkit gère automatiquement le shift-click en appelant cet event plusieurs fois.
}