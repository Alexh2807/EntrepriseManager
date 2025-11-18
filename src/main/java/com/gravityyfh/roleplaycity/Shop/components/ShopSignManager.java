package com.gravityyfh.roleplaycity.shop.components;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.shop.ShopStatus;
import com.gravityyfh.roleplaycity.shop.model.Shop;
import com.gravityyfh.roleplaycity.shop.validation.ShopValidator;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;

import java.text.DecimalFormat;

/**
 * Gestionnaire des panneaux de boutique
 * Crée et met à jour les panneaux
 */
public class ShopSignManager {
    private final RoleplayCity plugin;
    private final ShopValidator validator;
    private final DecimalFormat priceFormat = new DecimalFormat("#,##0.00");

    public ShopSignManager(RoleplayCity plugin, ShopValidator validator) {
        this.plugin = plugin;
        this.validator = validator;
    }

    /**
     * Crée ou met à jour le panneau d'une boutique
     */
    public void createOrUpdateSign(Shop shop) {
        Block block = shop.getSignLocation().getBlock();

        // Vérifier que c'est bien un panneau
        if (!(block.getState() instanceof Sign sign)) {
            plugin.getLogger().warning("[ShopSystem] Bloc à " + shop.getSignLocation() +
                " n'est pas un panneau!");
            return;
        }

        // Récupérer le stock actuel
        int stock = validator.countItemsInChest(shop);
        boolean hasStock = stock > 0;

        // Ligne 1: Séparateur supérieur
        sign.setLine(0, ChatColor.DARK_GRAY + "━━━━━━━━━━");

        // Ligne 2: Titre "BOUTIQUE"
        sign.setLine(1, ChatColor.WHITE + "" + ChatColor.BOLD + "  BOUTIQUE  ");

        // Ligne 3: Statut (OUVERT/FERMÉ)
        if (shop.getStatus() == ShopStatus.ACTIVE && hasStock) {
            sign.setLine(2, ChatColor.GREEN + "" + ChatColor.BOLD + "   OUVERT   ");
        } else if (shop.getStatus() == ShopStatus.DISABLED) {
            sign.setLine(2, ChatColor.GRAY + "" + ChatColor.BOLD + "   FERMÉ    ");
        } else {
            sign.setLine(2, ChatColor.RED + "" + ChatColor.BOLD + "   FERMÉ    ");
        }

        // Ligne 4: Séparateur inférieur
        sign.setLine(3, ChatColor.DARK_GRAY + "━━━━━━━━━━");

        // Colorer le panneau selon le stock (API 1.20+)
        try {
            if (hasStock) {
                sign.setColor(DyeColor.GREEN);
            } else {
                sign.setColor(DyeColor.RED);
            }
        } catch (Exception e) {
            // Version de Minecraft incompatible avec setColor
            plugin.getLogger().fine("[ShopSystem] Impossible de colorer le panneau (version Minecraft incompatible)");
        }

        sign.update(true); // Force update
    }

    /**
     * Nettoie un panneau (pour la suppression de boutique)
     */
    public void clearSign(Shop shop) {
        Block block = shop.getSignLocation().getBlock();

        if (!(block.getState() instanceof Sign sign)) {
            return;
        }

        sign.setLine(0, ChatColor.RED + "[SUPPRIMÉ]");
        sign.setLine(1, "");
        sign.setLine(2, "");
        sign.setLine(3, "");

        sign.update(true);
    }

    /**
     * Formate le prix
     */
    private String formatPrice(double price) {
        return priceFormat.format(price);
    }

    /**
     * Tronque un texte à une longueur maximale
     */
    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 1) + "…";
    }
}
