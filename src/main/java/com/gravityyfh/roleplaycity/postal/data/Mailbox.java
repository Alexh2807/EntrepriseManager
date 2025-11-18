package com.gravityyfh.roleplaycity.postal.data;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Représente une boîte aux lettres placée sur un terrain
 * REFONTE: Simplifiée, appartient maintenant directement au Plot
 *
 * @param type         Type de boîte aux lettres (couleur)
 * @param headLocation Position de la tête (player head) - UN SEUL BLOC
 * @param items        Inventaire virtuel (slot -> item)
 */
public record Mailbox(MailboxType type, Location headLocation, Map<Integer, ItemStack> items) {
    /**
     * Constructeur principal
     */
    public Mailbox(MailboxType type, Location headLocation) {
        this(type, headLocation, new HashMap<>());
    }

    /**
     * Constructeur pour le chargement depuis la sauvegarde
     */
    public Mailbox(MailboxType type, Location headLocation, Map<Integer, ItemStack> items) {
        this.type = type;
        this.headLocation = headLocation;
        this.items = items != null ? new HashMap<>(items) : new HashMap<>();
    }

    @Override
    public Map<Integer, ItemStack> items() {
        return new HashMap<>(items);
    }

    public void setItem(int slot, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            items.remove(slot);
        } else {
            items.put(slot, item.clone());
        }
    }

    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    public void clear() {
        items.clear();
    }

    /**
     * Retourne une clé unique pour identifier cette boîte aux lettres par position
     */
    public String getLocationKey() {
        return locationToKey(headLocation);
    }

    /**
     * Convertit une location en clé unique
     */
    public static String locationToKey(Location loc) {
        return loc.getWorld().getName() + ":" +
                loc.getBlockX() + ":" +
                loc.getBlockY() + ":" +
                loc.getBlockZ();
    }

    /**
     * Vérifie si cette boîte aux lettres contient du courrier
     */
    public boolean hasMail() {
        return !items.isEmpty();
    }

    /**
     * Compte le nombre d'items dans la boîte
     */
    public int getMailCount() {
        return items.size();
    }
}
