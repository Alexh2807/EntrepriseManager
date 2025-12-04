package com.gravityyfh.roleplaycity.backpack.model;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Représente un type de backpack configurable
 */
public class BackpackType {
    private final String id;
    private final boolean enabled;
    private final String displayName;
    private final List<String> lore;

    // Configuration de l'item
    private final ItemType itemType;
    private final Material material;
    private final int customModelData;
    private final String texture;
    private final String itemsAdderId;

    // Taille et restrictions
    private final int size;
    private final List<String> whitelist;

    // Craft
    private final boolean craftEnabled;
    private final boolean craftColoration; // True si recette de teinture (gérée par DyeCraftListener)
    private final List<String> craftPattern;
    private final Map<Character, Material> craftIngredients;

    public enum ItemType {
        VANILLA,
        ITEMSADDER_ITEM,
        HEAD
    }

    public BackpackType(String id, ConfigurationSection config) {
        this.id = id;
        this.enabled = config.getBoolean("enabled", true);
        this.displayName = config.getString("display_name", "&7Backpack");
        this.lore = config.getStringList("lore");

        // Charger config de l'item
        ConfigurationSection itemConfig = config.getConfigurationSection("item");
        ItemType tempItemType;
        Material tempMaterial;
        int tempCustomModelData;
        String tempTexture;
        String tempItemsAdderId;

        if (itemConfig != null) {
            String typeStr = itemConfig.getString("type", "VANILLA").toUpperCase();
            try {
                tempItemType = ItemType.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                tempItemType = ItemType.VANILLA;
            }

            // Material (pour VANILLA)
            String materialName = itemConfig.getString("material", "CHEST");
            try {
                tempMaterial = Material.valueOf(materialName.toUpperCase());
            } catch (IllegalArgumentException e) {
                tempMaterial = Material.CHEST;
            }

            tempCustomModelData = itemConfig.getInt("custom_model_data", 0);
            tempTexture = itemConfig.getString("texture", "");
            tempItemsAdderId = itemConfig.getString("itemsadder_id", "");
        } else {
            tempItemType = ItemType.VANILLA;
            tempMaterial = Material.CHEST;
            tempCustomModelData = 0;
            tempTexture = "";
            tempItemsAdderId = "";
        }

        this.itemType = tempItemType;
        this.material = tempMaterial;
        this.customModelData = tempCustomModelData;
        this.texture = tempTexture;
        this.itemsAdderId = tempItemsAdderId;

        // Taille (en lignes, 1-6)
        int sizeLines = config.getInt("size", 3);
        this.size = Math.min(Math.max(sizeLines, 1), 6) * 9;

        // Whitelist
        this.whitelist = config.getStringList("whitelist");

        // Craft
        ConfigurationSection craftConfig = config.getConfigurationSection("craft_recipe");
        if (craftConfig != null) {
            this.craftEnabled = craftConfig.getBoolean("enabled", true);
            this.craftColoration = craftConfig.getBoolean("coloration", false);
            this.craftPattern = craftConfig.getStringList("pattern");

            // Charger les ingrédients
            this.craftIngredients = new HashMap<>();
            ConfigurationSection ingredientsConfig = craftConfig.getConfigurationSection("ingredients");
            if (ingredientsConfig != null) {
                for (String key : ingredientsConfig.getKeys(false)) {
                    if (key.length() == 1) {
                        char symbol = key.charAt(0);
                        String materialName = ingredientsConfig.getString(key);
                        try {
                            Material ingredientMat = Material.valueOf(materialName.toUpperCase());
                            this.craftIngredients.put(symbol, ingredientMat);
                        } catch (IllegalArgumentException e) {
                            // Material invalide, ignorer
                        }
                    }
                }
            }
        } else {
            this.craftEnabled = false;
            this.craftColoration = false;
            this.craftPattern = new ArrayList<>();
            this.craftIngredients = new HashMap<>();
        }
    }

    // Getters
    public String getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getLore() {
        return lore;
    }

    public ItemType getItemType() {
        return itemType;
    }

    public Material getMaterial() {
        return material;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public String getTexture() {
        return texture;
    }

    public String getItemsAdderId() {
        return itemsAdderId;
    }

    public int getSize() {
        return size;
    }

    public List<String> getWhitelist() {
        return whitelist;
    }

    public boolean hasWhitelist() {
        return whitelist != null && !whitelist.isEmpty();
    }

    public boolean isCraftEnabled() {
        return craftEnabled;
    }

    public boolean isCraftColoration() {
        return craftColoration;
    }

    public List<String> getCraftPattern() {
        return craftPattern;
    }

    public Map<Character, Material> getCraftIngredients() {
        return craftIngredients;
    }

    /**
     * Vérifie si un item est autorisé dans ce backpack
     */
    public boolean isItemAllowed(Material material) {
        if (!hasWhitelist()) {
            return true;
        }

        String materialName = material.name();
        return whitelist.contains(materialName);
    }

    @Override
    public String toString() {
        return "BackpackType{" +
                "id='" + id + '\'' +
                ", displayName='" + displayName + '\'' +
                ", size=" + size +
                ", itemType=" + itemType +
                '}';
    }
}
