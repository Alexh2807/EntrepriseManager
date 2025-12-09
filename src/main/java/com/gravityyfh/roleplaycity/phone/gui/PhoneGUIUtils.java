package com.gravityyfh.roleplaycity.phone.gui;

import dev.lone.itemsadder.api.CustomStack;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.content.InventoryContents;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

/**
 * Utilitaires partages pour les GUIs du telephone.
 *
 * LAYOUT STANDARD (6 lignes x 9 colonnes):
 * - Colonnes 0-3: Zone ecran telephone (vitres noires)
 * - Colonnes 4-8: Background ItemsAdder (my_items:background_black)
 * - Ligne 4: Separateur (vitres noires sur colonnes 0-3)
 * - Ligne 5: Dock principal
 */
public class PhoneGUIUtils {

    public static final String BACKGROUND_ITEMSADDER_ID = "my_items:background_black";

    // Colonnes de l'ecran du telephone
    public static final int SCREEN_COL_START = 0;
    public static final int SCREEN_COL_END = 3;

    // Colonnes du background externe
    public static final int BG_COL_START = 4;
    public static final int BG_COL_END = 8;

    // Lignes speciales
    public static final int SEPARATOR_ROW = 4;
    public static final int DOCK_ROW = 5;

    private PhoneGUIUtils() {}

    /**
     * Initialise le layout standard pour un GUI de telephone 6 lignes.
     * - Colonnes 0-3: vitres noires (ecran)
     * - Colonnes 4-8: background ItemsAdder
     * - Ligne 4 (colonnes 0-3): separateur vitres noires
     * - Ligne 5: dock (a remplir par l'appelant)
     */
    public static void initPhoneLayout(InventoryContents contents, int rows) {
        ItemStack screenBg = createScreenBackground();
        ItemStack externalBg = createExternalBackground();

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                if (col <= SCREEN_COL_END) {
                    // Zone ecran (colonnes 0-3) - vitres noires
                    contents.set(row, col, ClickableItem.empty(screenBg));
                } else {
                    // Zone externe (colonnes 4-8) - ItemsAdder background
                    contents.set(row, col, ClickableItem.empty(externalBg));
                }
            }
        }
    }

    /**
     * Cree le background EXTERNE avec ItemsAdder (colonnes 4-8).
     */
    public static ItemStack createExternalBackground() {
        if (Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            try {
                CustomStack customStack = CustomStack.getInstance(BACKGROUND_ITEMSADDER_ID);
                if (customStack != null) {
                    ItemStack item = customStack.getItemStack();
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(" ");
                        item.setItemMeta(meta);
                    }
                    return item;
                }
            } catch (Exception e) {
                // Fallback silencieux
            }
        }
        return createGlass(Material.GRAY_STAINED_GLASS_PANE);
    }

    /**
     * Cree le fond d'ecran de la zone telephone (colonnes 0-3).
     * Vitres noires classiques.
     */
    public static ItemStack createScreenBackground() {
        return createGlass(Material.BLACK_STAINED_GLASS_PANE);
    }

    /**
     * Cree une vitre de verre avec un nom vide.
     */
    public static ItemStack createGlass(Material material) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        return pane;
    }

    /**
     * Cree un item avec un nom et une lore.
     */
    public static ItemStack createItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (loreLines.length > 0) {
                meta.setLore(Arrays.asList(loreLines));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    // Anciennes methodes pour compatibilite (a supprimer plus tard)
    @Deprecated
    public static ItemStack createBackground() {
        return createExternalBackground();
    }
}
