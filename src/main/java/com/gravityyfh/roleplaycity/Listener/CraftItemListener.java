package com.gravityyfh.roleplaycity.Listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.gravityyfh.roleplaycity.entreprise.storage.CompanyStorageManager;
import com.gravityyfh.roleplaycity.service.ServiceModeManager;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.*;

/**
 * Listener pour les crafts d'items.
 * Gère:
 * - Vérification des restrictions d'entreprise
 * - Comptage correct des quotas (y compris SHIFT-CLIC)
 * - Ajout automatique au coffre d'entreprise si en service
 * - Son et message de gain lors du craft en service
 */
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
        int amountPerSingleCraft = resultItem.getAmount();

        // Calculer le nombre total d'items qui seront craftés
        int totalCrafted = calculateTotalCrafted(event, resultItem);
        if (totalCrafted <= 0) {
            totalCrafted = amountPerSingleCraft;
        }

        // Vérifier si le joueur est en mode service
        ServiceModeManager serviceManager = plugin.getServiceModeManager();
        boolean isInService = serviceManager != null && serviceManager.isInService(player.getUniqueId());
        String companyName = (isInService && serviceManager != null) ? serviceManager.getActiveEnterprise(player.getUniqueId()) : null;

        // Gestion des Backpacks
        if (plugin.getBackpackItemManager() != null && plugin.getBackpackItemManager().isBackpack(resultItem)) {
            com.gravityyfh.roleplaycity.backpack.model.BackpackType backpackType = plugin.getBackpackItemManager().getBackpackType(resultItem);
            if (backpackType != null) {
                String backpackId = backpackType.getId();

                // Vérifier les restrictions avec le total réel
                boolean isBlocked = entrepriseLogic.verifierEtGererRestrictionAction(
                    player, "CRAFT_BACKPACK", backpackId, totalCrafted
                );

                if (isBlocked) {
                    event.setCancelled(true);
                    return;
                }

                // Enregistrer l'action avec la quantité correcte (appel unique avec quantité)
                int nombreBackpacks = totalCrafted / amountPerSingleCraft;
                entrepriseLogic.enregistrerCraftBackpack(player, backpackId, nombreBackpacks);

                // Gestion mode service
                if (isInService && companyName != null) {
                    handleServiceModeCraft(event, player, companyName, resultItem, totalCrafted);
                }
                return;
            }
        }

        // Gestion des Custom Items
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

                // Vérifier les restrictions avec le total réel
                boolean isBlocked = entrepriseLogic.verifierEtGererRestrictionAction(
                    player, "CRAFT_CUSTOM_ITEM", itemId, totalCrafted
                );

                if (isBlocked) {
                    event.setCancelled(true);
                    return;
                }

                // Enregistrer l'action avec la quantité correcte
                entrepriseLogic.enregistrerActionProductive(player, "CRAFT_CUSTOM_ITEM", itemId, totalCrafted);

                // Gestion mode service
                if (isInService && companyName != null) {
                    handleServiceModeCraft(event, player, companyName, resultItem, totalCrafted);
                }
                return;
            }
        }

        // Items vanilla
        Material itemType = resultItem.getType();
        String itemTypeName = itemType.name();

        // Vérifier les restrictions avec le total réel
        boolean isBlocked = entrepriseLogic.verifierEtGererRestrictionAction(
            player, "CRAFT_ITEM", itemTypeName, totalCrafted
        );

        if (isBlocked) {
            event.setCancelled(true);
            return;
        }

        // Enregistrer l'action avec la quantité correcte
        entrepriseLogic.enregistrerActionProductive(player, "CRAFT_ITEM", itemType, totalCrafted);

        // Gestion mode service
        if (isInService && companyName != null) {
            handleServiceModeCraft(event, player, companyName, resultItem, totalCrafted);
        }
    }

    /**
     * Calcule le nombre total d'items qui seront craftés.
     * Pour le shift-click, calcule le maximum possible basé sur les ingrédients.
     */
    private int calculateTotalCrafted(CraftItemEvent event, ItemStack result) {
        int resultAmount = result.getAmount();

        // Si ce n'est pas un shift-click, c'est un simple craft
        if (!event.isShiftClick()) {
            return resultAmount;
        }

        // Pour le shift-click, calculer le maximum possible
        CraftingInventory inventory = event.getInventory();
        ItemStack[] matrix = inventory.getMatrix();

        int maxCrafts = Integer.MAX_VALUE;

        for (ItemStack ingredient : matrix) {
            if (ingredient != null && !ingredient.getType().isAir()) {
                int ingredientAmount = ingredient.getAmount();
                // Chaque slot ne peut contribuer qu'une fois par craft
                maxCrafts = Math.min(maxCrafts, ingredientAmount);
            }
        }

        if (maxCrafts == Integer.MAX_VALUE || maxCrafts <= 0) {
            return resultAmount;
        }

        // Calculer aussi l'espace disponible dans l'inventaire
        int availableSpace = calculateAvailableSpace(event.getWhoClicked().getInventory(), result);
        int maxBySpace = availableSpace / resultAmount;
        if (maxBySpace <= 0) maxBySpace = 1; // Au moins 1 craft si possible

        return Math.min(maxCrafts, maxBySpace) * resultAmount;
    }

    /**
     * Calcule l'espace disponible dans l'inventaire pour un type d'item.
     */
    private int calculateAvailableSpace(Inventory inventory, ItemStack item) {
        int space = 0;
        int maxStack = item.getMaxStackSize();

        for (int i = 0; i < 36; i++) { // 36 slots d'inventaire joueur
            ItemStack slot = inventory.getItem(i);
            if (slot == null || slot.getType().isAir()) {
                space += maxStack;
            } else if (slot.isSimilar(item)) {
                space += maxStack - slot.getAmount();
            }
        }

        return space;
    }

    /**
     * Gère le craft en mode service: envoie les items au coffre d'entreprise
     * et affiche un message/son de confirmation.
     */
    private void handleServiceModeCraft(CraftItemEvent event, Player player, String companyName,
                                         ItemStack resultItem, int totalCrafted) {
        CompanyStorageManager storageManager = plugin.getCompanyStorageManager();
        if (storageManager == null) {
            return;
        }

        // Vérifier si ce craft est une activité de l'entreprise
        if (!isActionAllowedForStorage(companyName, resultItem)) {
            // Pas une activité de l'entreprise -> l'item va dans l'inventaire normalement
            return;
        }

        // Créer une copie de l'item avec la bonne quantité
        ItemStack itemToStore = resultItem.clone();
        itemToStore.setAmount(totalCrafted);

        // Ajouter au coffre d'entreprise
        boolean success = storageManager.addItemToStorage(companyName, itemToStore);

        if (success) {
            // Annuler l'event pour empêcher l'item d'aller dans l'inventaire
            event.setCancelled(true);

            // Consommer les ingrédients manuellement
            consumeIngredients(event, totalCrafted / resultItem.getAmount());

            // Son de confirmation
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);

            // Message de gain
            String itemName = getItemDisplayName(resultItem);
            player.sendMessage(ChatColor.GREEN + "+" + totalCrafted + " " + ChatColor.WHITE + itemName +
                ChatColor.GREEN + " ajoute(s) au coffre de " + ChatColor.GOLD + companyName);
        } else {
            // Coffre plein - l'item va dans l'inventaire normalement
            player.sendMessage(ChatColor.RED + "Le coffre de l'entreprise est plein ! L'item va dans votre inventaire.");
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 1.0f, 0.5f);
        }
    }

    /**
     * Consomme les ingrédients du craft manuellement.
     */
    private void consumeIngredients(CraftItemEvent event, int craftCount) {
        CraftingInventory inventory = event.getInventory();
        ItemStack[] matrix = inventory.getMatrix();

        for (int i = 0; i < matrix.length; i++) {
            ItemStack ingredient = matrix[i];
            if (ingredient != null && !ingredient.getType().isAir()) {
                int newAmount = ingredient.getAmount() - craftCount;
                if (newAmount <= 0) {
                    matrix[i] = null;
                } else {
                    ingredient.setAmount(newAmount);
                }
            }
        }

        inventory.setMatrix(matrix);
    }

    /**
     * Vérifie si un craft est une activité de l'entreprise (listée dans action_restrictions).
     */
    private boolean isActionAllowedForStorage(String companyName, ItemStack item) {
        com.gravityyfh.roleplaycity.entreprise.model.Entreprise entreprise =
            entrepriseLogic.getEntreprise(companyName);
        if (entreprise == null) return false;

        String type = entreprise.getType();
        String materialName = item.getType().name();

        String path = "types-entreprise." + type + ".action_restrictions.CRAFT_ITEM." + materialName;
        return plugin.getConfig().contains(path);
    }

    /**
     * Récupère le nom d'affichage d'un item.
     */
    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        // Convertir WHEAT_SEEDS en "Wheat Seeds"
        String name = item.getType().name().toLowerCase().replace("_", " ");
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
