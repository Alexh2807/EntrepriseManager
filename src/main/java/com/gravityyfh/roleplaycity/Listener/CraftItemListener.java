package com.gravityyfh.roleplaycity.Listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.*;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class CraftItemListener implements Listener {

    private final EntrepriseManagerLogic entrepriseLogic;
    private final RoleplayCity plugin;

    public CraftItemListener(RoleplayCity plugin, EntrepriseManagerLogic entrepriseLogic) {
        this.plugin = plugin;
        this.entrepriseLogic = entrepriseLogic;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();

        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        Recipe recipe = event.getRecipe();
        if (recipe == null) return;

        ItemStack resultItem = recipe.getResult();
        Material itemType = resultItem.getType();
        String itemTypeName = itemType.name();

        int amountPerSingleCraftExecution = resultItem.getAmount();
        int actualCraftedAmount = amountPerSingleCraftExecution;

        if (event.isShiftClick()) {
            CraftingInventory craftInv = (CraftingInventory) event.getInventory();
            actualCraftedAmount = calculateMaxCrafts(recipe, craftInv);
            if (actualCraftedAmount == 0) {
                actualCraftedAmount = amountPerSingleCraftExecution;
            }
        }

        boolean isBlocked = entrepriseLogic.verifierEtGererRestrictionAction(player, "CRAFT_ITEM", itemTypeName, actualCraftedAmount);

        if (isBlocked) {
            event.setCancelled(true);
            return;
        }

        entrepriseLogic.enregistrerActionProductive(player, "CRAFT_ITEM", itemType, actualCraftedAmount);
    }

    /**
     * Calcule le nombre maximum d'items qui seront produits lors d'un craft en série (shift-click).
     * Cette version est corrigée pour agréger correctement les ingrédients requis, que la recette
     * soit avec ou sans forme, évitant ainsi les erreurs de calcul.
     * @param recipe La recette exécutée.
     * @param inventory L'inventaire de craft.
     * @return Le nombre total d'items qui seront fabriqués.
     */
    private int calculateMaxCrafts(Recipe recipe, CraftingInventory inventory) {
        ItemStack resultItem = recipe.getResult();
        int amountPerSingleCraft = resultItem.getAmount();

        // Étape 1: Déterminer les ingrédients requis et leurs quantités pour UN SEUL craft.
        Map<Material, Integer> requiredMaterials = new HashMap<>();

        if (recipe instanceof ShapedRecipe) {
            ShapedRecipe shapedRecipe = (ShapedRecipe) recipe;
            Map<Character, ItemStack> ingredientMap = shapedRecipe.getIngredientMap();
            for (String row : shapedRecipe.getShape()) {
                for (char symbol : row.toCharArray()) {
                    ItemStack requiredIngredient = ingredientMap.get(symbol);
                    if (requiredIngredient != null && requiredIngredient.getType() != Material.AIR) {
                        requiredMaterials.merge(requiredIngredient.getType(), 1, Integer::sum);
                    }
                }
            }
        } else if (recipe instanceof ShapelessRecipe) {
            ShapelessRecipe shapelessRecipe = (ShapelessRecipe) recipe;
            for (ItemStack requiredIngredient : shapelessRecipe.getIngredientList()) {
                if (requiredIngredient != null && requiredIngredient.getType() != Material.AIR) {
                    requiredMaterials.merge(requiredIngredient.getType(), requiredIngredient.getAmount(), Integer::sum);
                }
            }
        } else {
            // Fallback pour les recettes non standards
            return amountPerSingleCraft;
        }

        if (requiredMaterials.isEmpty()) {
            return amountPerSingleCraft;
        }

        // Étape 2: Compter les matériaux disponibles dans la grille de craft.
        Map<Material, Integer> availableMaterials = new HashMap<>();
        for (ItemStack itemInGrid : inventory.getMatrix()) {
            if (itemInGrid != null && itemInGrid.getType() != Material.AIR) {
                availableMaterials.merge(itemInGrid.getType(), itemInGrid.getAmount(), Integer::sum);
            }
        }

        // Étape 3: Trouver le facteur limitant (combien de crafts complets sont possibles).
        int maxCrafts = Integer.MAX_VALUE;
        for (Map.Entry<Material, Integer> required : requiredMaterials.entrySet()) {
            Material material = required.getKey();
            int requiredAmount = required.getValue();
            int availableAmount = availableMaterials.getOrDefault(material, 0);

            if (availableAmount < requiredAmount) {
                return 0; // Pas assez d'ingrédients pour un seul craft.
            }
            maxCrafts = Math.min(maxCrafts, availableAmount / requiredAmount);
        }

        return (maxCrafts == Integer.MAX_VALUE ? 0 : maxCrafts) * amountPerSingleCraft;
    }
}