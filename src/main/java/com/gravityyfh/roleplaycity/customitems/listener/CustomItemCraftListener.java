package com.gravityyfh.roleplaycity.customitems.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.gravityyfh.roleplaycity.customitems.manager.CustomItemManager;
import com.gravityyfh.roleplaycity.customitems.model.CustomItem;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Listener pour enregistrer les recettes natives Bukkit des custom items
 * Les restrictions et quotas sont gérés dans CraftItemListener.onCraftItem()
 * Bukkit affiche automatiquement la preview via la recette enregistrée
 */
public class CustomItemCraftListener implements Listener {

    private final RoleplayCity plugin;
    private final CustomItemManager manager;
    private final EntrepriseManagerLogic entrepriseLogic;
    private final Map<String, NamespacedKey> registeredRecipes;

    public CustomItemCraftListener(RoleplayCity plugin, CustomItemManager manager, EntrepriseManagerLogic entrepriseLogic) {
        this.plugin = plugin;
        this.manager = manager;
        this.entrepriseLogic = entrepriseLogic;
        this.registeredRecipes = new HashMap<>();
    }

    /**
     * Enregistre les recettes natives Bukkit pour tous les custom items
     * IDENTIQUE au système des Backpacks
     */
    public void registerRecipes() {
        // Supprimer les anciennes recettes d'abord
        unregisterRecipes();

        for (CustomItem item : manager.getItems().values()) {
            // Vérifier si le craft est activé
            if (item.getRecipe() == null || !item.getRecipe().isEnabled()) {
                continue;
            }

            List<String> pattern = item.getRecipe().getShape();
            Map<Character, Material> ingredients = item.getRecipe().getIngredients();

            if (pattern == null || pattern.isEmpty() || pattern.size() != 3 || ingredients == null || ingredients.isEmpty()) {
                plugin.getLogger().warning("[CustomItems] Recette invalide pour: " + item.getId());
                continue;
            }

            // Créer un item exemple pour la recette
            ItemStack result = manager.createItemStack(item.getId());
            if (result == null) {
                plugin.getLogger().warning("[CustomItems] Impossible de créer l'item: " + item.getId());
                continue;
            }

            // Créer la clé unique
            NamespacedKey key = new NamespacedKey(plugin, "custom_item_" + item.getId());

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
                registeredRecipes.put(item.getId(), key);
                plugin.getLogger().info("[CustomItems] Recette enregistrée: " + item.getId());
            } catch (Exception e) {
                plugin.getLogger().severe("[CustomItems] Erreur lors de l'enregistrement de la recette " + item.getId() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Supprime toutes les recettes enregistrées
     * IDENTIQUE au système des Backpacks
     */
    public void unregisterRecipes() {
        for (NamespacedKey key : registeredRecipes.values()) {
            Bukkit.removeRecipe(key);
        }
        registeredRecipes.clear();
    }

    /**
     * PrepareItemCraftEvent - Affiche l'item ItemsAdder dans la preview
     * Vérifie aussi la restriction mayor_only (bloque la preview si pas maire)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCraftPrepare(PrepareItemCraftEvent event) {
        if (event.getRecipe() == null) {
            return;
        }

        ItemStack result = event.getRecipe().getResult();
        if (!manager.isCustomItem(result)) {
            return;
        }

        CustomItem customItem = manager.getItemByItemStack(result);
        if (customItem == null) {
            return;
        }

        // Vérification mayor_only - bloquer la preview si pas maire
        if (customItem.getRecipe() != null && customItem.getRecipe().isMayorOnly()) {
            if (!(event.getView().getPlayer() instanceof Player player)) {
                return;
            }

            TownManager townManager = plugin.getTownManager();
            if (!townManager.isPlayerMayor(player.getUniqueId())) {
                // Pas maire, bloquer la preview
                event.getInventory().setResult(new ItemStack(Material.AIR));
                return;
            }
        }

        // Afficher l'item ItemsAdder
        ItemStack itemsAdderItem = manager.createItemStack(customItem.getId());
        if (itemsAdderItem != null) {
            event.getInventory().setResult(itemsAdderItem);
        }
    }
}
