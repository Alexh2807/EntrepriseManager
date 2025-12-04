package com.gravityyfh.roleplaycity.customitems.manager;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.customitems.model.ActionType;
import com.gravityyfh.roleplaycity.customitems.model.CustomItem;
import com.gravityyfh.roleplaycity.customitems.model.ItemAction;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CustomItemManager {

    private final RoleplayCity plugin;
    private final Map<String, CustomItem> items = new HashMap<>();
    private final Map<String, String> displayNameToId = new HashMap<>();

    // PersistentDataContainer keys pour identifier rapidement les custom items
    // IDENTIQUE au système des Backpacks pour éviter le flash de duplication
    private final NamespacedKey customItemKey;
    private final NamespacedKey customItemTypeKey;
    private final NamespacedKey customItemUniqueKey;

    // Vérification de disponibilité ItemsAdder (comme BackpackItemManager ligne 41)
    private boolean itemsAdderAvailable;

    public CustomItemManager(RoleplayCity plugin) {
        this.plugin = plugin;
        this.customItemKey = new NamespacedKey(plugin, "custom_item");
        this.customItemTypeKey = new NamespacedKey(plugin, "custom_item_type");
        this.customItemUniqueKey = new NamespacedKey(plugin, "custom_item_unique");

        checkItemsAdderAvailability();
        loadItems();
    }

    /**
     * Vérifie la disponibilité d'ItemsAdder au démarrage
     * IDENTIQUE à BackpackItemManager lignes 67-72
     */
    private void checkItemsAdderAvailability() {
        itemsAdderAvailable = Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;
        if (itemsAdderAvailable) {
            plugin.getLogger().info("[CustomItems] ItemsAdder détecté ! Support activé.");
        } else {
            plugin.getLogger().info("[CustomItems] ItemsAdder non détecté. Utilisation des items vanilla uniquement.");
        }
    }

    /**
     * Rafraîchit le statut d'ItemsAdder (appelé quand ItemsAdder se charge après ce plugin)
     */
    public void refreshItemsAdderAvailability() {
        boolean wasAvailable = itemsAdderAvailable;
        itemsAdderAvailable = Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;

        if (!wasAvailable && itemsAdderAvailable) {
            plugin.getLogger().info("[CustomItems] ItemsAdder maintenant disponible ! Les items utiliseront les textures ItemsAdder.");
        }
    }

    /**
     * Vérifie si ItemsAdder est disponible
     */
    public boolean isItemsAdderAvailable() {
        return itemsAdderAvailable;
    }

    public void loadItems() {
        items.clear();
        displayNameToId.clear();
        
        File file = new File(plugin.getDataFolder(), "custom_items.yml");
        if (!file.exists()) {
            plugin.saveResource("custom_items.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection itemsSection = config.getConfigurationSection("items");

        if (itemsSection == null) return;

        for (String key : itemsSection.getKeys(false)) {
            try {
                ConfigurationSection section = itemsSection.getConfigurationSection(key);
                
                String displayName = section.getString("display_name");
                Material material = Material.valueOf(section.getString("material", "STICK"));
                String itemsAdderId = section.getString("itemsadder_id");
                List<String> lore = section.getStringList("lore");
                int modelData = section.getInt("model_data", 0);
                boolean stackable = section.getBoolean("stackable", true);  // Par défaut stackable

                // Load triggers
                Map<String, CustomItem.TriggerData> triggers = new HashMap<>();
                ConfigurationSection triggersSection = section.getConfigurationSection("triggers");
                if (triggersSection != null) {
                    for (String triggerKey : triggersSection.getKeys(false)) {
                        ConfigurationSection triggerSection = triggersSection.getConfigurationSection(triggerKey);
                        
                        // Conditions
                        Map<String, Object> conditions = new HashMap<>();
                        ConfigurationSection condSection = triggerSection.getConfigurationSection("conditions");
                        if (condSection != null) {
                            for (String condKey : condSection.getKeys(false)) {
                                conditions.put(condKey, condSection.get(condKey));
                            }
                        }

                        // Actions
                        List<ItemAction> actions = new ArrayList<>();
                        @SuppressWarnings("unchecked")
                        List<Map<?, ?>> actionsList = triggerSection.getMapList("actions");
                        
                        for (Map<?, ?> actionMap : actionsList) {
                            String typeStr = (String) actionMap.get("type");
                            ActionType type = ActionType.valueOf(typeStr);
                            
                            Map<String, Object> params = new HashMap<>();
                            for (Map.Entry<?, ?> entry : actionMap.entrySet()) {
                                if (!entry.getKey().equals("type")) {
                                    params.put((String) entry.getKey(), entry.getValue());
                                }
                            }
                            actions.add(new ItemAction(type, params));
                        }
                        
                        triggers.put(triggerKey, new CustomItem.TriggerData(conditions, actions));
                    }
                }

                // Load Recipe
                ConfigurationSection recipeSection = section.getConfigurationSection("recipe");
                CustomItem.RecipeData recipe = null;
                if (recipeSection != null && recipeSection.getBoolean("enabled")) {
                    Map<Character, Material> ingredients = new HashMap<>();
                    ConfigurationSection ingSection = recipeSection.getConfigurationSection("ingredients");
                    if (ingSection != null) {
                        for (String charKey : ingSection.getKeys(false)) {
                            ingredients.put(charKey.charAt(0), Material.valueOf(ingSection.getString(charKey)));
                        }
                    }

                    recipe = new CustomItem.RecipeData(
                        true,
                        recipeSection.getString("permission"),
                        recipeSection.getBoolean("mayor_only", false),  // Restriction maire
                        recipeSection.getStringList("shape"),
                        ingredients
                    );
                }

                CustomItem item = new CustomItem(key, displayName, material, itemsAdderId, lore, modelData, stackable, triggers, recipe);
                items.put(key, item);
                displayNameToId.put(org.bukkit.ChatColor.stripColor(org.bukkit.ChatColor.translateAlternateColorCodes('&', displayName)), key);

            } catch (Exception e) {
                plugin.getLogger().severe("Erreur chargement item custom " + key + ": " + e.getMessage());
            }
        }

        // NOTE: Les recettes sont maintenant gérées par CustomItemCraftListener
        // pour éviter le bug de duplication (même architecture que les Backpacks)
    }

    public Map<String, CustomItem> getItems() {
        return items;
    }

    public CustomItem getItem(String id) {
        return items.get(id);
    }

    /**
     * Récupère un CustomItem par son ID ItemsAdder
     * @param itemsAdderId L'ID au format "namespace:id" (ex: "my_items:casier_police")
     * @return Le CustomItem correspondant, ou null
     */
    public CustomItem getItemByItemsAdderId(String itemsAdderId) {
        if (itemsAdderId == null) return null;

        for (CustomItem item : items.values()) {
            if (itemsAdderId.equals(item.getItemsAdderId())) {
                return item;
            }
        }
        return null;
    }

    /**
     * Vérifie si un item est un custom item valide
     * Vérifie d'abord le PDC (items craftés), puis ItemsAdder (items /iaget)
     */
    public boolean isCustomItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }

        // Méthode 1: Vérifier le PDC (items créés par notre plugin)
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(customItemKey, PersistentDataType.BYTE)) {
                return true;
            }
        }

        // Méthode 2: Vérifier via ItemsAdder (items obtenus via /iaget)
        if (itemsAdderAvailable) {
            String itemsAdderId = getItemsAdderIdFromItemStack(item);
            if (itemsAdderId != null) {
                // Vérifier si cet ID ItemsAdder correspond à un de nos custom items
                return getItemByItemsAdderId(itemsAdderId) != null;
            }
        }

        return false;
    }

    /**
     * Récupère l'ID ItemsAdder d'un ItemStack via l'API CustomStack
     * Fonctionne pour les items obtenus via /iaget ou craftés dans ItemsAdder
     */
    private String getItemsAdderIdFromItemStack(ItemStack item) {
        if (!itemsAdderAvailable || item == null) {
            return null;
        }

        try {
            Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object customStack = customStackClass.getMethod("byItemStack", ItemStack.class).invoke(null, item);

            if (customStack != null) {
                // L'item est un custom item ItemsAdder, récupérer son ID
                return (String) customStackClass.getMethod("getNamespacedID").invoke(customStack);
            }
        } catch (Exception e) {
            // Silencieux - item non-ItemsAdder ou erreur
        }
        return null;
    }

    public CustomItem getItemByItemStack(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }

        // Méthode 1: Vérifier le PDC (items créés par notre plugin - priorité)
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String typeId = meta.getPersistentDataContainer().get(customItemTypeKey, PersistentDataType.STRING);
                if (typeId != null) {
                    return items.get(typeId);
                }
            }
        }

        // Méthode 2: Vérifier via ItemsAdder (items obtenus via /iaget)
        if (itemsAdderAvailable) {
            String itemsAdderId = getItemsAdderIdFromItemStack(item);
            if (itemsAdderId != null) {
                return getItemByItemsAdderId(itemsAdderId);
            }
        }

        return null;
    }
    
    /**
     * Crée un ItemStack pour un custom item
     * ARCHITECTURE 100% IDENTIQUE à BackpackItemManager.createBackpack() lignes 119-173
     */
    public ItemStack createItemStack(String id) {
        CustomItem ci = items.get(id);
        if (ci == null) {
            plugin.getLogger().warning("[CustomItems] Item inconnu: " + id);
            return null;
        }

        ItemStack item = null;

        // Créer l'item selon le type (EXACTEMENT comme Backpacks ligne 132-143)
        if (ci.getItemsAdderId() != null && !ci.getItemsAdderId().isEmpty()) {
            item = createItemsAdderItem(ci);
        } else {
            item = createVanillaItem(ci);
        }

        if (item == null) {
            plugin.getLogger().warning("[CustomItems] Échec de la création de l'item " + id);
            return null;
        }

        // Ajouter les métadonnées communes (IDENTIQUE Backpacks ligne 150-170)
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Nom et lore
            meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', ci.getDisplayName()));

            List<String> lore = new ArrayList<>();
            for (String line : ci.getLore()) {
                lore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(lore);

            // Données persistantes
            meta.getPersistentDataContainer().set(customItemKey, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(customItemTypeKey, PersistentDataType.STRING, id);

            // UUID unique UNIQUEMENT si l'item n'est pas stackable
            if (!ci.isStackable()) {
                UUID uniqueId = UUID.randomUUID();
                meta.getPersistentDataContainer().set(customItemUniqueKey, PersistentDataType.STRING, uniqueId.toString());
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Tente de récupérer un CustomItem à partir d'un bloc posé (via ItemsAdder)
     */
    public CustomItem getCustomItemFromBlock(org.bukkit.block.Block block) {
        if (block == null) return null;

        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") != null) {
            try {
                dev.lone.itemsadder.api.CustomBlock customBlock = dev.lone.itemsadder.api.CustomBlock.byAlreadyPlaced(block);

                if (customBlock != null) {
                    String id = customBlock.getNamespacedID();

                    for (CustomItem item : items.values()) {
                        if (id.equals(item.getItemsAdderId())) {
                            return item;
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[CustomItem] Erreur lors de la vérification du bloc: " + e.getMessage());
            }
        }
        return null;
    }

    /**
     * Tente de récupérer un CustomItem à partir d'un furniture ItemsAdder (ArmorStand)
     */
    public CustomItem getCustomItemFromFurniture(org.bukkit.entity.Entity entity) {
        if (entity == null) return null;

        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") != null) {
            try {
                dev.lone.itemsadder.api.CustomFurniture customFurniture = dev.lone.itemsadder.api.CustomFurniture.byAlreadySpawned(entity);

                if (customFurniture != null) {
                    String id = customFurniture.getNamespacedID();

                    for (CustomItem item : items.values()) {
                        if (id.equals(item.getItemsAdderId())) {
                            return item;
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[CustomItem] Erreur lors de la vérification du furniture: " + e.getMessage());
            }
        }
        return null;
    }

    /**
     * Crée un item vanilla avec CustomModelData
     * IDENTIQUE à BackpackItemManager lignes 178-186
     */
    private ItemStack createVanillaItem(CustomItem ci) {
        ItemStack item = new ItemStack(ci.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null && ci.getModelData() > 0) {
            meta.setCustomModelData(ci.getModelData());
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Vérifie si les données ItemsAdder sont complètement chargées
     */
    private boolean areItemsAdderDataLoaded() {
        if (!itemsAdderAvailable) {
            return false;
        }

        try {
            // Utiliser ItemsAdder.areItemsLoaded() pour vérifier si les données sont prêtes
            Class<?> itemsAdderClass = Class.forName("dev.lone.itemsadder.api.ItemsAdder");
            Boolean loaded = (Boolean) itemsAdderClass.getMethod("areItemsLoaded").invoke(null);
            return loaded != null && loaded;
        } catch (Exception e) {
            // Si la méthode n'existe pas ou erreur, supposer non chargé
            return false;
        }
    }

    /**
     * Crée un item avec ItemsAdder via réflexion
     * IDENTIQUE à BackpackItemManager lignes 191-215
     */
    private ItemStack createItemsAdderItem(CustomItem ci) {
        if (!itemsAdderAvailable) {
            plugin.getLogger().warning("[CustomItems] ItemsAdder non disponible, création d'un item vanilla pour " + ci.getId());
            return createVanillaItem(ci);
        }

        // IMPORTANT: Vérifier si les données ItemsAdder sont chargées
        if (!areItemsAdderDataLoaded()) {
            plugin.getLogger().warning("[CustomItems] Données ItemsAdder pas encore chargées, création d'un item vanilla pour " + ci.getId());
            return createVanillaItem(ci);
        }

        try {
            Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object customStack = customStackClass.getMethod("getInstance", String.class)
                    .invoke(null, ci.getItemsAdderId());

            if (customStack == null) {
                plugin.getLogger().warning("[CustomItems] Item ItemsAdder introuvable: " + ci.getItemsAdderId() + " (vérifiez que l'ID existe dans ItemsAdder)");
                return createVanillaItem(ci);
            }

            ItemStack item = (ItemStack) customStackClass.getMethod("getItemStack").invoke(customStack);
            return item.clone();

        } catch (Exception e) {
            plugin.getLogger().warning("[CustomItems] Erreur lors de la création de l'item ItemsAdder: " + e.getMessage());
            return createVanillaItem(ci);
        }
    }
}
