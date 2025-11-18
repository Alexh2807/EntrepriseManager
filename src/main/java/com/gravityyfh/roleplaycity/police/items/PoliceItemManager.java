package com.gravityyfh.roleplaycity.police.items;

import com.gravityyfh.roleplaycity.RoleplayCity;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Manager principal pour les items de police (Taser et Menottes)
 * Gère la création, validation et configuration des items
 */
public class PoliceItemManager {

    private final RoleplayCity plugin;

    // NamespacedKeys pour identifier les items
    private final NamespacedKey taserKey;
    private final NamespacedKey taserChargesKey;
    private final NamespacedKey taserUniqueKey;
    private final NamespacedKey handcuffsKey;
    private final NamespacedKey handcuffsUniqueKey;

    // Configuration
    private Material taserMaterial;
    private int taserCustomModelData;
    private int taserMaxCharges;
    private String taserName;
    private List<String> taserLore;

    private Material handcuffsMaterial;
    private int handcuffsCustomModelData;
    private String handcuffsName;
    private List<String> handcuffsLore;

    public PoliceItemManager(RoleplayCity plugin) {
        this.plugin = plugin;

        // Initialiser les NamespacedKeys
        this.taserKey = new NamespacedKey(plugin, "police_taser");
        this.taserChargesKey = new NamespacedKey(plugin, "taser_charges");
        this.taserUniqueKey = new NamespacedKey(plugin, "taser_unique");

        this.handcuffsKey = new NamespacedKey(plugin, "police_handcuffs");
        this.handcuffsUniqueKey = new NamespacedKey(plugin, "handcuffs_unique");

        loadConfiguration();
    }

    /**
     * Charge la configuration depuis config.yml
     * Peut être appelé lors du reload pour mettre à jour les valeurs
     */
    public void loadConfiguration() {
        FileConfiguration config = plugin.getConfig();

        // Configuration Taser
        taserMaterial = Material.valueOf(
            config.getString("police-equipment.taser.material", "STICK").toUpperCase()
        );
        taserCustomModelData = config.getInt("police-equipment.taser.custom-model-data", 10021);
        taserMaxCharges = config.getInt("police-equipment.taser.max-charges", 10);
        taserName = config.getString("police-equipment.taser.name", "§6Taser");

        // Charger le lore du taser
        taserLore = new ArrayList<>();
        List<String> configLore = config.getStringList("police-equipment.taser.lore");
        if (configLore.isEmpty()) {
            taserLore.add("§7Clic droit pour taser un joueur.");
            taserLore.add("§7Charges: §6§l%charges%");
        } else {
            taserLore.addAll(configLore);
        }

        // Configuration Menottes
        handcuffsMaterial = Material.valueOf(
            config.getString("police-equipment.handcuffs.material", "LEAD").toUpperCase()
        );
        handcuffsCustomModelData = config.getInt("police-equipment.handcuffs.custom-model-data", 5456);
        handcuffsName = config.getString("police-equipment.handcuffs.name", "§eMenottes");

        // Charger le lore des menottes
        handcuffsLore = new ArrayList<>();
        List<String> handcuffsConfigLore = config.getStringList("police-equipment.handcuffs.lore");
        if (handcuffsConfigLore.isEmpty()) {
            handcuffsLore.add("§7Clic droit sur un joueur pour le menotter.");
        } else {
            handcuffsLore.addAll(handcuffsConfigLore);
        }
    }

    /**
     * Crée un nouveau taser avec charges complètes
     */
    public ItemStack createTaser() {
        return createTaser(taserMaxCharges);
    }

    /**
     * Crée un nouveau taser avec un nombre de charges spécifique
     */
    public ItemStack createTaser(int charges) {
        ItemStack item = new ItemStack(taserMaterial);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Définir le nom avec conversion des couleurs
            meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', taserName));

            // Définir le lore avec les charges et conversion des couleurs
            List<String> lore = new ArrayList<>();
            for (String line : taserLore) {
                String processedLine = line.replace("%charges%", String.valueOf(charges));
                lore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', processedLine));
            }
            meta.setLore(lore);

            // Définir le CustomModelData
            meta.setCustomModelData(taserCustomModelData);

            // Ajouter les données persistantes
            meta.getPersistentDataContainer().set(taserKey, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(taserChargesKey, PersistentDataType.INTEGER, charges);
            meta.getPersistentDataContainer().set(taserUniqueKey, PersistentDataType.LONG, System.currentTimeMillis());

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Crée des menottes
     */
    public ItemStack createHandcuffs() {
        ItemStack item = new ItemStack(handcuffsMaterial);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Définir le nom avec conversion des couleurs
            meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', handcuffsName));

            // Définir le lore avec conversion des couleurs
            List<String> lore = new ArrayList<>();
            for (String line : handcuffsLore) {
                lore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(lore);

            // Définir le CustomModelData
            meta.setCustomModelData(handcuffsCustomModelData);

            // Ajouter les données persistantes
            meta.getPersistentDataContainer().set(handcuffsKey, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(handcuffsUniqueKey, PersistentDataType.LONG, System.currentTimeMillis());

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Vérifie si un item est un taser
     */
    public boolean isTaser(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        return meta != null &&
               meta.getPersistentDataContainer().has(taserKey, PersistentDataType.BYTE);
    }

    /**
     * Vérifie si un item est des menottes
     */
    public boolean isHandcuffs(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        return meta != null &&
               meta.getPersistentDataContainer().has(handcuffsKey, PersistentDataType.BYTE);
    }

    /**
     * Obtient le nombre de charges restantes d'un taser
     */
    public int getTaserCharges(ItemStack item) {
        if (!isTaser(item)) {
            return 0;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            return meta.getPersistentDataContainer()
                .getOrDefault(taserChargesKey, PersistentDataType.INTEGER, 0);
        }

        return 0;
    }

    /**
     * Définit le nombre de charges d'un taser et met à jour le lore
     */
    public void setTaserCharges(ItemStack item, int charges) {
        if (!isTaser(item)) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Mettre à jour les charges dans le PersistentDataContainer
            meta.getPersistentDataContainer().set(taserChargesKey, PersistentDataType.INTEGER, charges);

            // Mettre à jour le lore avec conversion des couleurs
            List<String> lore = new ArrayList<>();
            for (String line : taserLore) {
                String processedLine = line.replace("%charges%", String.valueOf(charges));
                lore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', processedLine));
            }
            meta.setLore(lore);

            item.setItemMeta(meta);
        }
    }

    /**
     * Utilise une charge du taser
     * @return true si le taser a encore des charges après utilisation
     */
    public boolean useTaserCharge(ItemStack item) {
        int charges = getTaserCharges(item);
        if (charges <= 0) {
            return false;
        }

        setTaserCharges(item, charges - 1);
        return charges - 1 > 0;
    }

    /**
     * Recharge le taser d'une charge
     * @return true si le rechargement a réussi
     */
    public boolean rechargeTaser(ItemStack item) {
        int charges = getTaserCharges(item);
        if (charges >= taserMaxCharges) {
            return false; // Déjà plein
        }

        setTaserCharges(item, charges + 1);
        return true;
    }

    // Getters pour la configuration
    public int getTaserMaxCharges() {
        return taserMaxCharges;
    }

    public Material getTaserMaterial() {
        return taserMaterial;
    }

    public int getTaserCustomModelData() {
        return taserCustomModelData;
    }

    public Material getHandcuffsMaterial() {
        return handcuffsMaterial;
    }

    public int getHandcuffsCustomModelData() {
        return handcuffsCustomModelData;
    }

    public NamespacedKey getTaserKey() {
        return taserKey;
    }

    public NamespacedKey getTaserChargesKey() {
        return taserChargesKey;
    }

    public NamespacedKey getHandcuffsKey() {
        return handcuffsKey;
    }
}
