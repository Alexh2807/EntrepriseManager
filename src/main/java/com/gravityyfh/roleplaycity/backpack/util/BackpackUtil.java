package com.gravityyfh.roleplaycity.backpack.util;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.backpack.model.BackpackData;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Utilitaires pour les backpacks (sons, lore, etc.)
 */
public class BackpackUtil {
    private final RoleplayCity plugin;
    private final FileConfiguration config;

    // Configuration des sons
    private boolean soundsEnabled;
    private Sound openSound;
    private Sound closeSound;
    private float soundVolume;
    private float soundPitch;

    // Configuration de la lore
    private boolean showContentInLore;
    private String contentLoreFormat;

    public BackpackUtil(RoleplayCity plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        loadConfiguration();
    }

    /**
     * Charge la configuration depuis config.yml
     */
    public void loadConfiguration() {
        this.soundsEnabled = config.getBoolean("backpack.sounds.enabled", true);
        this.soundVolume = (float) config.getDouble("backpack.sounds.volume", 1.0);
        this.soundPitch = (float) config.getDouble("backpack.sounds.pitch", 1.0);

        String openSoundName = config.getString("backpack.sounds.open", "BLOCK_CHEST_OPEN");
        String closeSoundName = config.getString("backpack.sounds.close", "BLOCK_CHEST_CLOSE");

        try {
            this.openSound = Sound.valueOf(openSoundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Son d'ouverture invalide: " + openSoundName + ", utilisation de BLOCK_CHEST_OPEN par défaut");
            this.openSound = Sound.BLOCK_CHEST_OPEN;
        }

        try {
            this.closeSound = Sound.valueOf(closeSoundName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Son de fermeture invalide: " + closeSoundName + ", utilisation de BLOCK_CHEST_CLOSE par défaut");
            this.closeSound = Sound.BLOCK_CHEST_CLOSE;
        }

        this.showContentInLore = config.getBoolean("backpack.show-content-in-lore", true);
        this.contentLoreFormat = config.getString("backpack.content-lore-format",
            "&7Contenu: &e{item_count} items ({used_slots}/{total_slots} slots)");
    }

    /**
     * Joue le son d'ouverture pour un joueur
     *
     * @param player Le joueur
     */
    public void playOpenSound(Player player) {
        if (soundsEnabled) {
            player.playSound(player.getLocation(), openSound, soundVolume, soundPitch);
        }
    }

    /**
     * Joue le son de fermeture pour un joueur
     *
     * @param player Le joueur
     */
    public void playCloseSound(Player player) {
        if (soundsEnabled) {
            player.playSound(player.getLocation(), closeSound, soundVolume, soundPitch);
        }
    }

    /**
     * Met à jour la lore d'un backpack pour afficher son contenu
     *
     * @param item L'item backpack
     * @param data Les données du backpack
     */
    public void updateBackpackLore(ItemStack item, BackpackData data) {
        if (!showContentInLore || item == null || !item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        // Récupérer la lore de base depuis la config
        List<String> baseLore = config.getStringList("backpack.lore");
        List<String> newLore = new ArrayList<>();

        // Ajouter la lore de base
        for (String line : baseLore) {
            newLore.add(ChatColor.translateAlternateColorCodes('&', line));
        }

        // Ajouter la ligne de contenu
        String contentLine = contentLoreFormat
            .replace("{item_count}", String.valueOf(data.getItemCount()))
            .replace("{used_slots}", String.valueOf(data.getUsedSlots()))
            .replace("{total_slots}", String.valueOf(data.size()));
        newLore.add(ChatColor.translateAlternateColorCodes('&', contentLine));

        // Optionnel: ajouter un aperçu des items
        if (config.getBoolean("backpack.show-items-preview", false)) {
            int maxPreview = config.getInt("backpack.max-items-preview", 5);
            int count = 0;

            for (ItemStack content : data.contents()) {
                if (content != null && content.getType() != Material.AIR && count < maxPreview) {
                    String itemName = getItemDisplayName(content);
                    newLore.add(ChatColor.GRAY + "  • " + ChatColor.WHITE + itemName + ChatColor.GRAY + " x" + content.getAmount());
                    count++;
                }
            }

            if (data.getUsedSlots() > maxPreview) {
                newLore.add(ChatColor.GRAY + "  ... et " + (data.getUsedSlots() - maxPreview) + " autres");
            }
        }

        meta.setLore(newLore);
        item.setItemMeta(meta);
    }

    /**
     * Obtient le nom d'affichage d'un item
     *
     * @param item L'item
     * @return Le nom d'affichage
     */
    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }

        // Formater le nom du matériau (ex: DIAMOND_SWORD -> Diamond Sword)
        String materialName = item.getType().name();
        String[] words = materialName.toLowerCase().split("_");
        StringBuilder displayName = new StringBuilder();

        for (String word : words) {
            if (displayName.length() > 0) {
                displayName.append(" ");
            }
            displayName.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }

        return displayName.toString();
    }

    /**
     * Vérifie si un item est dans la blacklist
     *
     * @param item L'item à vérifier
     * @return true si l'item est blacklisté, false sinon
     */
    public boolean isBlacklisted(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        List<String> blacklist = config.getStringList("backpack.blacklist");
        String materialName = item.getType().name();

        return blacklist.contains(materialName);
    }

    /**
     * Vérifie si les protections sont activées
     *
     * @param protectionType Le type de protection à vérifier
     * @return true si la protection est activée, false sinon
     */
    public boolean isProtectionEnabled(String protectionType) {
        return config.getBoolean("backpack.protections." + protectionType, true);
    }
}
