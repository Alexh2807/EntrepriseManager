package com.gravityyfh.roleplaycity.phone.model;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represente un type de telephone configure dans phones.yml
 */
public class PhoneType {

    private final String id;
    private boolean enabled;
    private String displayName;
    private List<String> lore;

    // Item configuration
    private String itemType; // ITEMSADDER_ITEM ou VANILLA
    private String itemsAdderId;
    private Material vanillaMaterial;

    // Recipe
    private boolean craftEnabled;
    private boolean craftColoration; // True si c'est une recette de teinture (gérée par DyeCraftListener)
    private String[] craftPattern;
    private Map<Character, Material> craftIngredients;

    public PhoneType(String id) {
        this.id = id;
        this.lore = new ArrayList<>();
        this.craftIngredients = new HashMap<>();
    }

    /**
     * Charge la configuration depuis une section YAML
     */
    public static PhoneType fromConfig(String id, ConfigurationSection config) {
        PhoneType type = new PhoneType(id);

        type.enabled = config.getBoolean("enabled", true);
        type.displayName = config.getString("display_name", "&fTelephone");
        type.lore = config.getStringList("lore");

        // Item configuration
        ConfigurationSection itemSection = config.getConfigurationSection("item");
        if (itemSection != null) {
            type.itemType = itemSection.getString("type", "ITEMSADDER_ITEM");
            type.itemsAdderId = itemSection.getString("itemsadder_id");

            String materialName = itemSection.getString("material");
            if (materialName != null) {
                try {
                    type.vanillaMaterial = Material.valueOf(materialName.toUpperCase());
                } catch (IllegalArgumentException ignored) {
                    type.vanillaMaterial = Material.PAPER;
                }
            }
        }

        // Recipe configuration
        ConfigurationSection recipeSection = config.getConfigurationSection("craft_recipe");
        if (recipeSection != null) {
            type.craftEnabled = recipeSection.getBoolean("enabled", false);
            type.craftColoration = recipeSection.getBoolean("coloration", false);

            List<String> pattern = recipeSection.getStringList("pattern");
            type.craftPattern = pattern.toArray(new String[0]);

            ConfigurationSection ingredientsSection = recipeSection.getConfigurationSection("ingredients");
            if (ingredientsSection != null) {
                for (String key : ingredientsSection.getKeys(false)) {
                    String materialName = ingredientsSection.getString(key);
                    if (materialName != null && key.length() == 1) {
                        try {
                            Material mat = Material.valueOf(materialName.toUpperCase());
                            type.craftIngredients.put(key.charAt(0), mat);
                        } catch (IllegalArgumentException ignored) {
                            // Ignorer les materiaux invalides
                        }
                    }
                }
            }
        }

        return type;
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

    public String getItemType() {
        return itemType;
    }

    public String getItemsAdderId() {
        return itemsAdderId;
    }

    public Material getVanillaMaterial() {
        return vanillaMaterial;
    }

    public boolean isCraftEnabled() {
        return craftEnabled;
    }

    public boolean isCraftColoration() {
        return craftColoration;
    }

    public String[] getCraftPattern() {
        return craftPattern;
    }

    public Map<Character, Material> getCraftIngredients() {
        return craftIngredients;
    }

    public boolean isItemsAdderItem() {
        return "ITEMSADDER_ITEM".equalsIgnoreCase(itemType);
    }
}
