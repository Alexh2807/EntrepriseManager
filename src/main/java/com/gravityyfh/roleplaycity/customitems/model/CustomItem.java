package com.gravityyfh.roleplaycity.customitems.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public class CustomItem {
    private final String id;
    private final String displayName;
    private final Material material;
    private final String itemsAdderId;
    private final List<String> lore;
    private final int modelData;
    private final boolean stackable;  // Nouveau champ

    // Map<TriggerType, List<ItemAction>>
    // TriggerType sera une chaîne pour simplifier (RIGHT_CLICK, LEFT_CLICK_ENTITY...)
    private final Map<String, TriggerData> triggers;

    private final RecipeData recipe;

    public CustomItem(String id, String displayName, Material material, String itemsAdderId, List<String> lore, int modelData, boolean stackable, Map<String, TriggerData> triggers, RecipeData recipe) {
        this.id = id;
        this.displayName = displayName;
        this.material = material;
        this.itemsAdderId = itemsAdderId;
        this.lore = lore;
        this.modelData = modelData;
        this.stackable = stackable;
        this.triggers = triggers;
        this.recipe = recipe;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public Material getMaterial() { return material; }
    public String getItemsAdderId() { return itemsAdderId; }
    public List<String> getLore() { return lore; }
    public int getModelData() { return modelData; }
    public boolean isStackable() { return stackable; }
    public Map<String, TriggerData> getTriggers() { return triggers; }
    public RecipeData getRecipe() { return recipe; }
    
    public static class TriggerData {
        private final Map<String, Object> conditions;
        private final List<ItemAction> actions;

        public TriggerData(Map<String, Object> conditions, List<ItemAction> actions) {
            this.conditions = conditions;
            this.actions = actions;
        }

        public Map<String, Object> getConditions() { return conditions; }
        public List<ItemAction> getActions() { return actions; }
    }
    
    public static class RecipeData {
        private final boolean enabled;
        private final String permission;
        private final boolean mayorOnly;  // Seul le maire peut crafter
        private final List<String> shape;
        private final Map<Character, Material> ingredients;

        public RecipeData(boolean enabled, String permission, boolean mayorOnly, List<String> shape, Map<Character, Material> ingredients) {
            this.enabled = enabled;
            this.permission = permission;
            this.mayorOnly = mayorOnly;
            this.shape = shape;
            this.ingredients = ingredients;
        }

        public boolean isEnabled() { return enabled; }
        public String getPermission() { return permission; }
        public boolean isMayorOnly() { return mayorOnly; }
        public List<String> getShape() { return shape; }
        public Map<Character, Material> getIngredients() { return ingredients; }
    }
}
