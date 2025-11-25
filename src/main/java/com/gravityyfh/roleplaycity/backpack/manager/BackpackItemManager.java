package com.gravityyfh.roleplaycity.backpack.manager;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.backpack.model.BackpackType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Logger;

/**
 * Gestionnaire pour la création et la validation des items backpack avec support multi-types et ItemsAdder
 */
public class BackpackItemManager {
    private final RoleplayCity plugin;
    private final Logger logger;
    private FileConfiguration backpacksConfig;

    // Clés pour le PersistentDataContainer
    private final NamespacedKey backpackKey;
    private final NamespacedKey backpackTypeKey;
    private final NamespacedKey backpackUniqueKey;
    private final NamespacedKey backpackContentKey;

    // Types de backpacks chargés
    private final Map<String, BackpackType> backpackTypes;

    // Support ItemsAdder
    private boolean itemsAdderAvailable;

    public BackpackItemManager(RoleplayCity plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        // Initialiser les clés
        this.backpackKey = new NamespacedKey(plugin, "backpack");
        this.backpackTypeKey = new NamespacedKey(plugin, "backpack_type");
        this.backpackUniqueKey = new NamespacedKey(plugin, "backpack_unique");
        this.backpackContentKey = new NamespacedKey(plugin, "backpack_content");

        this.backpackTypes = new HashMap<>();

        // Vérifier ItemsAdder
        checkItemsAdderAvailability();

        // Charger la configuration
        loadConfiguration();
    }

    /**
     * Vérifie si ItemsAdder est disponible
     */
    private void checkItemsAdderAvailability() {
        itemsAdderAvailable = Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;
        if (itemsAdderAvailable) {
            logger.info("[Backpacks] ItemsAdder détecté ! Support activé.");
        } else {
            logger.info("[Backpacks] ItemsAdder non détecté. Utilisation des items vanilla uniquement.");
        }
    }

    /**
     * Charge ou recharge la configuration depuis backpacks.yml
     */
    public void loadConfiguration() {
        // Créer le fichier si inexistant
        File backpacksFile = new File(plugin.getDataFolder(), "backpacks.yml");
        if (!backpacksFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                InputStream defaultConfig = plugin.getResource("backpacks.yml");
                if (defaultConfig != null) {
                    Files.copy(defaultConfig, backpacksFile.toPath());
                    logger.info("[Backpacks] Fichier backpacks.yml créé avec les valeurs par défaut");
                }
            } catch (IOException e) {
                logger.severe("[Backpacks] Erreur lors de la création de backpacks.yml: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Charger le fichier
        backpacksConfig = YamlConfiguration.loadConfiguration(backpacksFile);

        // Charger tous les types de backpacks
        backpackTypes.clear();
        ConfigurationSection backpacksSection = backpacksConfig.getConfigurationSection("backpacks");
        if (backpacksSection != null) {
            for (String typeId : backpacksSection.getKeys(false)) {
                ConfigurationSection typeConfig = backpacksSection.getConfigurationSection(typeId);
                if (typeConfig != null) {
                    BackpackType type = new BackpackType(typeId, typeConfig);
                    if (type.isEnabled()) {
                        backpackTypes.put(typeId, type);
                        logger.info("[Backpacks] Type chargé: " + typeId + " (" + type.getDisplayName() + ")");
                    }
                }
            }
        }

        logger.info("[Backpacks] " + backpackTypes.size() + " types de backpacks chargés");
    }

    /**
     * Crée un nouveau backpack d'un type donné
     *
     * @param typeId L'ID du type de backpack
     * @return L'ItemStack du backpack, ou null si le type n'existe pas
     */
    public ItemStack createBackpack(String typeId) {
        BackpackType type = backpackTypes.get(typeId);
        if (type == null) {
            logger.warning("[Backpacks] Type inconnu: " + typeId);
            return null;
        }

        ItemStack item = null;

        // Créer l'item selon le type
        switch (type.getItemType()) {
            case ITEMSADDER_ITEM:
                item = createItemsAdderItem(type);
                break;
            case HEAD:
                item = createHeadItem(type);
                break;
            case VANILLA:
            default:
                item = createVanillaItem(type);
                break;
        }

        if (item == null) {
            logger.warning("[Backpacks] Échec de la création du backpack " + typeId);
            return null;
        }

        // Ajouter les métadonnées communes
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Nom et lore
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', type.getDisplayName()));

            List<String> lore = new ArrayList<>();
            for (String line : type.getLore()) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(lore);

            // Données persistantes
            UUID uniqueId = UUID.randomUUID();
            meta.getPersistentDataContainer().set(backpackKey, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(backpackTypeKey, PersistentDataType.STRING, typeId);
            meta.getPersistentDataContainer().set(backpackUniqueKey, PersistentDataType.STRING, uniqueId.toString());
            meta.getPersistentDataContainer().set(backpackContentKey, PersistentDataType.STRING, "");

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Crée un item vanilla
     */
    private ItemStack createVanillaItem(BackpackType type) {
        ItemStack item = new ItemStack(type.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null && type.getCustomModelData() > 0) {
            meta.setCustomModelData(type.getCustomModelData());
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
            Class<?> itemsAdderClass = Class.forName("dev.lone.itemsadder.api.ItemsAdder");
            Boolean loaded = (Boolean) itemsAdderClass.getMethod("areItemsLoaded").invoke(null);
            return loaded != null && loaded;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Rafraîchit le statut d'ItemsAdder
     */
    public void refreshItemsAdderAvailability() {
        boolean wasAvailable = itemsAdderAvailable;
        itemsAdderAvailable = Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;

        if (!wasAvailable && itemsAdderAvailable) {
            logger.info("[Backpacks] ItemsAdder maintenant disponible !");
        }
    }

    /**
     * Crée un item avec ItemsAdder
     */
    private ItemStack createItemsAdderItem(BackpackType type) {
        if (!itemsAdderAvailable) {
            logger.warning("[Backpacks] ItemsAdder non disponible, création d'un item vanilla pour " + type.getId());
            return createVanillaItem(type);
        }

        // IMPORTANT: Vérifier si les données ItemsAdder sont chargées
        if (!areItemsAdderDataLoaded()) {
            logger.warning("[Backpacks] Données ItemsAdder pas encore chargées, création d'un item vanilla pour " + type.getId());
            return createVanillaItem(type);
        }

        try {
            // Utiliser l'API ItemsAdder
            Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object customStack = customStackClass.getMethod("getInstance", String.class)
                    .invoke(null, type.getItemsAdderId());

            if (customStack == null) {
                logger.warning("[Backpacks] Item ItemsAdder introuvable: " + type.getItemsAdderId());
                return createVanillaItem(type);
            }

            ItemStack item = (ItemStack) customStackClass.getMethod("getItemStack").invoke(customStack);
            return item.clone();

        } catch (Exception e) {
            logger.warning("[Backpacks] Erreur lors de la création de l'item ItemsAdder: " + e.getMessage());
            return createVanillaItem(type);
        }
    }

    /**
     * Crée un item HEAD avec texture custom
     */
    private ItemStack createHeadItem(BackpackType type) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);

        if (type.getTexture().isEmpty()) {
            return head;
        }

        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            try {
                // Utiliser l'API Bukkit pour set la texture
                Class<?> skullMetaClass = Class.forName("org.bukkit.inventory.meta.SkullMeta");
                if (skullMetaClass.isInstance(meta)) {
                    // TODO: Implémenter la texture base64 via profile
                    // Pour l'instant, retourner un head basique
                }
            } catch (Exception e) {
                logger.warning("[Backpacks] Erreur lors de la création du HEAD: " + e.getMessage());
            }
            head.setItemMeta(meta);
        }

        return head;
    }

    /**
     * Vérifie si un item est un backpack valide
     */
    public boolean isBackpack(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        return meta != null &&
               meta.getPersistentDataContainer().has(backpackKey, PersistentDataType.BYTE);
    }

    /**
     * Récupère le type d'un backpack
     */
    public BackpackType getBackpackType(ItemStack item) {
        if (!isBackpack(item)) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        String typeId = meta.getPersistentDataContainer().get(backpackTypeKey, PersistentDataType.STRING);
        return backpackTypes.get(typeId);
    }

    /**
     * Récupère l'UUID unique d'un backpack
     */
    public UUID getBackpackUUID(ItemStack item) {
        if (!isBackpack(item)) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        String uuidString = meta.getPersistentDataContainer().get(backpackUniqueKey, PersistentDataType.STRING);
        if (uuidString == null) {
            return null;
        }

        try {
            return UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            logger.warning("UUID invalide pour backpack: " + uuidString);
            return null;
        }
    }

    /**
     * Récupère le contenu sérialisé d'un backpack
     */
    public String getBackpackContent(ItemStack item) {
        if (!isBackpack(item)) {
            return null;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        return meta.getPersistentDataContainer().get(backpackContentKey, PersistentDataType.STRING);
    }

    /**
     * Définit le contenu sérialisé d'un backpack
     */
    public boolean setBackpackContent(ItemStack item, String content) {
        if (!isBackpack(item)) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        meta.getPersistentDataContainer().set(backpackContentKey, PersistentDataType.STRING, content);
        item.setItemMeta(meta);
        return true;
    }

    // Getters
    public Map<String, BackpackType> getBackpackTypes() {
        return Collections.unmodifiableMap(backpackTypes);
    }

    public BackpackType getType(String typeId) {
        return backpackTypes.get(typeId);
    }

    public Set<String> getTypeIds() {
        return backpackTypes.keySet();
    }

    public FileConfiguration getBackpacksConfig() {
        return backpacksConfig;
    }

    public boolean isItemsAdderAvailable() {
        return itemsAdderAvailable;
    }

    public NamespacedKey getBackpackKey() {
        return backpackKey;
    }

    public NamespacedKey getBackpackTypeKey() {
        return backpackTypeKey;
    }

    public NamespacedKey getBackpackUniqueKey() {
        return backpackUniqueKey;
    }

    public NamespacedKey getBackpackContentKey() {
        return backpackContentKey;
    }
}
