package com.gravityyfh.roleplaycity.backpack.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.gravityyfh.roleplaycity.backpack.manager.BackpackItemManager;
import com.gravityyfh.roleplaycity.backpack.model.BackpackType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.RecipeChoice;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Listener pour gérer les crafts personnalisés des backpacks
 * UTILISE DES RECETTES NATIVES au lieu d'événements pour éviter les duplications
 * Vérifie que le joueur est membre d'une entreprise Styliste avant d'autoriser le craft
 */
public class BackpackCraftListener implements Listener {
    private final RoleplayCity plugin;
    private final BackpackItemManager itemManager;
    private final EntrepriseManagerLogic entrepriseLogic;
    private final Map<String, NamespacedKey> registeredRecipes;

    public BackpackCraftListener(RoleplayCity plugin, BackpackItemManager itemManager, EntrepriseManagerLogic entrepriseLogic) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.entrepriseLogic = entrepriseLogic;
        this.registeredRecipes = new HashMap<>();
    }

    /**
     * Enregistre les recettes natives Bukkit pour tous les types de backpacks
     */
    public void registerRecipes() {
        // Supprimer les anciennes recettes d'abord
        unregisterRecipes();

        for (BackpackType type : itemManager.getBackpackTypes().values()) {
            if (!type.isCraftEnabled()) {
                continue;
            }

            List<String> pattern = type.getCraftPattern();
            Map<Character, Material> ingredients = type.getCraftIngredients();

            if (pattern.isEmpty() || pattern.size() != 3 || ingredients.isEmpty()) {
                plugin.getLogger().warning("[Backpack] Recette invalide pour le type: " + type.getId());
                continue;
            }

            // Créer un backpack exemple pour la recette
            ItemStack result = itemManager.createBackpack(type.getId());
            if (result == null) {
                plugin.getLogger().warning("[Backpack] Impossible de créer le backpack: " + type.getId());
                continue;
            }

            // Créer la clé unique
            NamespacedKey key = new NamespacedKey(plugin, "backpack_" + type.getId());

            // Créer la recette shaped
            ShapedRecipe recipe = new ShapedRecipe(key, result);
            recipe.shape(
                pattern.get(0),
                pattern.get(1),
                pattern.get(2)
            );

            // Ajouter les ingrédients
            for (Map.Entry<Character, Material> entry : ingredients.entrySet()) {
                recipe.setIngredient(entry.getKey(), entry.getValue());
            }

            // Enregistrer la recette
            try {
                Bukkit.addRecipe(recipe);
                registeredRecipes.put(type.getId(), key);
                plugin.getLogger().info("[Backpack] Recette enregistrée: " + type.getId());
            } catch (Exception e) {
                plugin.getLogger().severe("[Backpack] Erreur lors de l'enregistrement de la recette " + type.getId() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Supprime toutes les recettes enregistrées
     */
    public void unregisterRecipes() {
        for (NamespacedKey key : registeredRecipes.values()) {
            Bukkit.removeRecipe(key);
        }
        registeredRecipes.clear();
    }

    /**
     * PrepareItemCraftEvent - Génère un nouveau backpack avec UUID unique à chaque fois
     * IMPORTANT: Ne pas réutiliser/cloner les backpacks pour éviter les duplications
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCraftPrepare(PrepareItemCraftEvent event) {
        // Vérifier si c'est un craft de backpack en regardant la recette
        if (event.getRecipe() == null) {
            return;
        }

        ItemStack result = event.getRecipe().getResult();
        if (!itemManager.isBackpack(result)) {
            return;
        }

        // Créer un NOUVEAU backpack avec un UUID unique pour cette preview
        // Cela évite les problèmes de clonage et de cache
        BackpackType backpackType = itemManager.getBackpackType(result);
        if (backpackType != null) {
            ItemStack freshBackpack = itemManager.createBackpack(backpackType.getId());
            if (freshBackpack != null) {
                event.getInventory().setResult(freshBackpack);
            }
        }
    }

    /**
     * CraftItemEvent - DÉSACTIVÉ
     * La gestion du craft des backpacks est maintenant centralisée dans CraftItemListener
     * pour bénéficier de la gestion unifiée des restrictions et des quotas (verifierEtGererRestrictionAction).
     */
    // @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraftBackpack(CraftItemEvent event) {
        /* LOGIQUE DÉPLACÉE DANS CraftItemListener
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // ...
        */
    }
}
