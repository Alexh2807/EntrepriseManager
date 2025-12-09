/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.enchantments.Enchantment
 *  org.bukkit.inventory.ItemFlag
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.persistence.PersistentDataContainer
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.plugin.Plugin
 */
package de.lightplugins.economy.items;

import de.lightplugins.economy.enums.PersistentDataPaths;
import de.lightplugins.economy.master.Main;
import java.util.ArrayList;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public class Voucher {
    public ItemStack createVoucher(double itemValue, String creator) {
        String materialName = Main.voucher.getConfig().getString("voucher.material");
        ItemStack itemStack = null;

        // Support ItemsAdder via Reflection to avoid compile-time dependency errors
        if (materialName != null && materialName.contains(":") && Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            try {
                Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
                Object customStack = customStackClass.getMethod("getInstance", String.class).invoke(null, materialName);
                
                if (customStack != null) {
                    itemStack = (ItemStack) customStackClass.getMethod("getItemStack").invoke(customStack);
                }
            } catch (Exception e) {
                Main.getInstance.getLogger().warning("Failed to load ItemsAdder item via Reflection: " + materialName + ". Falling back to material.");
            }
        }

        if (itemStack == null) {
            Material material;
            try {
                material = Material.valueOf(materialName);
            } catch (IllegalArgumentException e) {
                // Fallback to PAPER if material is invalid (e.g. iageneric:banknote without ItemsAdder)
                material = Material.PAPER;
            }
            itemStack = new ItemStack(material, 1);
        }

        boolean glow = Main.voucher.getConfig().getBoolean("voucher.glow");
        String displayname = Main.colorTranslation.hexTranslation(Objects.requireNonNull(Main.voucher.getConfig().getString("voucher.name")).replace("#amount#", String.valueOf(itemValue)).replace("#currency#", Main.util.getCurrency(itemValue)));
        
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return new ItemStack(Material.STONE, 1);
        }
        itemMeta.setDisplayName(displayname);
        ArrayList loreList = new ArrayList();
        Main.voucher.getConfig().getStringList("voucher.lore").forEach(lore -> loreList.add(Main.colorTranslation.hexTranslation((String)lore).replace("#creator#", creator).replace("#amount#", String.valueOf(itemValue)).replace("#currency#", Main.util.getCurrency(itemValue))));
        if (itemMeta.hasLore()) {
            Objects.requireNonNull(itemMeta.getLore()).clear();
        }
        itemMeta.setLore(loreList);
        boolean shouldUseModeData = Main.voucher.getConfig().getBoolean("voucher.custom-material.enable");
        // Only apply custom model data if NOT using ItemsAdder (as IA handles it itself)
        if (shouldUseModeData && (materialName == null || !materialName.contains(":"))) {
            itemMeta.setCustomModelData(Integer.valueOf(Main.voucher.getConfig().getInt("voucher.custom-material.custom-id")));
        }
        if (glow) {
            itemMeta.addEnchant(Enchantment.DURABILITY, 1, true);
            itemMeta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS});
        }
        PersistentDataContainer data = itemMeta.getPersistentDataContainer();
        NamespacedKey namespacedKeyValue = new NamespacedKey(Main.getInstance.asPlugin(), PersistentDataPaths.MONEY_VALUE.getType());
        if (namespacedKeyValue.getKey().equalsIgnoreCase(PersistentDataPaths.MONEY_VALUE.getType())) {
            data.set(namespacedKeyValue, PersistentDataType.DOUBLE, itemValue);
        }
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }
}