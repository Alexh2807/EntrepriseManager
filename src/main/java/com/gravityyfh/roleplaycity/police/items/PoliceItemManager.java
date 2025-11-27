package com.gravityyfh.roleplaycity.police.items;

import com.gravityyfh.roleplaycity.RoleplayCity;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Manager principal pour les items de police (Taser et Menottes)
 * Gère la validation et configuration des items
 * Utilise ItemsAdder pour les items (/iaget)
 */
public class PoliceItemManager {

    private final RoleplayCity plugin;

    // NamespacedKeys pour identifier les items (compatibilité anciens items créés par le plugin)
    private final NamespacedKey taserKey;
    private final NamespacedKey taserChargesKey;
    private final NamespacedKey handcuffsKey;

    // Configuration
    private int taserMaxCharges;
    private List<String> taserLore;
    private String taserItemsAdderId;  // ID ItemsAdder (ex: "my_items:taser")

    private List<String> handcuffsLore;
    private String handcuffsItemsAdderId;  // ID ItemsAdder (ex: "my_items:handcuffs")

    // Vérification de disponibilité ItemsAdder
    private boolean itemsAdderAvailable;

    public PoliceItemManager(RoleplayCity plugin) {
        this.plugin = plugin;

        // Initialiser les NamespacedKeys (compatibilité anciens items)
        this.taserKey = new NamespacedKey(plugin, "police_taser");
        this.taserChargesKey = new NamespacedKey(plugin, "taser_charges");
        this.handcuffsKey = new NamespacedKey(plugin, "police_handcuffs");

        checkItemsAdderAvailability();
        loadConfiguration();
    }

    /**
     * Vérifie la disponibilité d'ItemsAdder au démarrage
     */
    private void checkItemsAdderAvailability() {
        itemsAdderAvailable = Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;
        if (itemsAdderAvailable) {
            plugin.getLogger().info("[PoliceItems] ItemsAdder détecté ! Support activé pour Taser/Menottes.");
        }
    }

    /**
     * Rafraîchit le statut d'ItemsAdder (appelé quand ItemsAdder se charge après ce plugin)
     */
    public void refreshItemsAdderAvailability() {
        boolean wasAvailable = itemsAdderAvailable;
        itemsAdderAvailable = Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;

        if (!wasAvailable && itemsAdderAvailable) {
            plugin.getLogger().info("[PoliceItems] ItemsAdder maintenant disponible !");
        }
    }

    /**
     * Charge la configuration depuis config.yml
     * Peut être appelé lors du reload pour mettre à jour les valeurs
     */
    public void loadConfiguration() {
        FileConfiguration config = plugin.getConfig();

        // Configuration Taser
        taserMaxCharges = config.getInt("police-equipment.taser.max-charges", 10);
        taserItemsAdderId = config.getString("police-equipment.taser.itemsadder-id", null);

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
        handcuffsItemsAdderId = config.getString("police-equipment.handcuffs.itemsadder-id", null);

        // Charger le lore des menottes
        handcuffsLore = new ArrayList<>();
        List<String> handcuffsConfigLore = config.getStringList("police-equipment.handcuffs.lore");
        if (handcuffsConfigLore.isEmpty()) {
            handcuffsLore.add("§7Clic droit sur un joueur pour le menotter.");
        } else {
            handcuffsLore.addAll(handcuffsConfigLore);
        }

        // Log si ItemsAdder IDs configurés
        if (taserItemsAdderId != null) {
            plugin.getLogger().info("[PoliceItems] Taser ItemsAdder ID: " + taserItemsAdderId);
        }
        if (handcuffsItemsAdderId != null) {
            plugin.getLogger().info("[PoliceItems] Handcuffs ItemsAdder ID: " + handcuffsItemsAdderId);
        }
    }

    /**
     * Vérifie si un item est un taser
     * Supporte: items créés par le plugin (PDC) ET items ItemsAdder (/iaget)
     */
    public boolean isTaser(ItemStack item) {
        if (item == null) {
            return false;
        }

        // Méthode 1: Vérifier le PDC (anciens items créés par notre plugin)
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(taserKey, PersistentDataType.BYTE)) {
                return true;
            }
        }

        // Méthode 2: Vérifier via ItemsAdder (items obtenus via /iaget)
        if (itemsAdderAvailable && taserItemsAdderId != null) {
            String itemAdderId = getItemsAdderIdFromItemStack(item);
            if (taserItemsAdderId.equals(itemAdderId)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Vérifie si un item est des menottes
     * Supporte: items créés par le plugin (PDC) ET items ItemsAdder (/iaget)
     */
    public boolean isHandcuffs(ItemStack item) {
        if (item == null) {
            return false;
        }

        // Méthode 1: Vérifier le PDC (anciens items créés par notre plugin)
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(handcuffsKey, PersistentDataType.BYTE)) {
                return true;
            }
        }

        // Méthode 2: Vérifier via ItemsAdder (items obtenus via /iaget)
        if (itemsAdderAvailable && handcuffsItemsAdderId != null) {
            String itemAdderId = getItemsAdderIdFromItemStack(item);
            if (handcuffsItemsAdderId.equals(itemAdderId)) {
                return true;
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

    /**
     * Obtient le nombre de charges restantes d'un taser
     * Pour les items ItemsAdder, retourne max-charges par défaut
     */
    public int getTaserCharges(ItemStack item) {
        if (!isTaser(item)) {
            return 0;
        }

        // Vérifier d'abord le PDC (items avec charges stockées)
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(taserChargesKey, PersistentDataType.INTEGER)) {
                return meta.getPersistentDataContainer().get(taserChargesKey, PersistentDataType.INTEGER);
            }
        }

        // Pour les items ItemsAdder sans PDC, retourner max charges
        return taserMaxCharges;
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

    public NamespacedKey getTaserKey() {
        return taserKey;
    }

    public NamespacedKey getTaserChargesKey() {
        return taserChargesKey;
    }

    public NamespacedKey getHandcuffsKey() {
        return handcuffsKey;
    }

    public String getTaserItemsAdderId() {
        return taserItemsAdderId;
    }

    public String getHandcuffsItemsAdderId() {
        return handcuffsItemsAdderId;
    }
}
