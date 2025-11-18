package com.gravityyfh.roleplaycity.backpack.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Représente les données d'un backpack physique
 */
public record BackpackData(UUID uniqueId, ItemStack[] contents, long creationTime, int size) {

    public BackpackData(UUID uniqueId, int size) {
        this(uniqueId, new ItemStack[size], System.currentTimeMillis(), size);
    }

    /**
     * Compte le nombre d'items non-null dans le backpack
     */
    public int getItemCount() {
        int count = 0;
        for (ItemStack item : contents) {
            if (item != null && item.getType() != Material.AIR) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /**
     * Compte le nombre de slots utilisés
     */
    public int getUsedSlots() {
        int used = 0;
        for (ItemStack item : contents) {
            if (item != null && item.getType() != Material.AIR) {
                used++;
            }
        }
        return used;
    }

    /**
     * Vérifie si le backpack est vide
     */
    public boolean isEmpty() {
        for (ItemStack item : contents) {
            if (item != null && item.getType() != Material.AIR) {
                return false;
            }
        }
        return true;
    }

    /**
     * Clone le contenu du backpack
     */
    public ItemStack[] cloneContents() {
        ItemStack[] cloned = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null) {
                cloned[i] = contents[i].clone();
            }
        }
        return cloned;
    }
}
