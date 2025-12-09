package com.gravityyfh.roleplaycity.mdt.generator;

import com.gravityyfh.roleplaycity.mdt.data.MDTGenerator;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Représente un item flottant au-dessus d'un générateur
 * Style BedWars - l'item flotte et tourne
 */
public class FloatingItemDisplay {
    private final MDTGenerator generator;
    private final Location location;
    private Item itemEntity;
    private ArmorStand hologramStand;

    private static final double FLOAT_HEIGHT = 1.5;
    private static final double ROTATION_SPEED = 0.05; // radians par tick

    public FloatingItemDisplay(MDTGenerator generator) {
        this.generator = generator;
        // Position au-dessus du générateur
        this.location = generator.getLocation().clone().add(0.5, FLOAT_HEIGHT, 0.5);
    }

    /**
     * Spawn l'item flottant
     */
    public void spawn() {
        if (itemEntity != null && !itemEntity.isDead()) {
            return; // Déjà spawné
        }

        // Créer l'item entity - utilise le premier matériau du générateur
        Material displayMaterial = generator.getResources().isEmpty()
            ? Material.BRICK
            : generator.getResources().get(0).getMaterial();
        ItemStack stack = new ItemStack(displayMaterial, 1);
        itemEntity = location.getWorld().dropItem(location, stack);

        // Configurer l'item pour qu'il flotte
        itemEntity.setGravity(false);
        itemEntity.setVelocity(new Vector(0, 0, 0));
        itemEntity.setPickupDelay(Integer.MAX_VALUE); // Ne peut pas être ramassé directement
        itemEntity.setInvulnerable(true);
        itemEntity.setPersistent(false);

        // Metadata pour identifier l'item comme display (non ramassable)
        itemEntity.setCustomNameVisible(false);
    }

    /**
     * Retire l'item flottant
     */
    public void remove() {
        if (itemEntity != null && !itemEntity.isDead()) {
            itemEntity.remove();
        }
        itemEntity = null;

        if (hologramStand != null && !hologramStand.isDead()) {
            hologramStand.remove();
        }
        hologramStand = null;
    }

    /**
     * Met à jour la position/rotation de l'item (appelé chaque tick)
     */
    public void update() {
        if (itemEntity == null || itemEntity.isDead()) {
            return;
        }

        // Maintenir l'item à sa position (annuler toute vélocité)
        if (!itemEntity.getLocation().equals(location)) {
            itemEntity.teleport(location);
        }
        itemEntity.setVelocity(new Vector(0, 0, 0));
    }

    /**
     * Vérifie si l'item existe et est valide
     */
    public boolean isValid() {
        return itemEntity != null && !itemEntity.isDead();
    }

    /**
     * Retourne l'entité item
     */
    public Item getItemEntity() {
        return itemEntity;
    }

    /**
     * Retourne l'UUID de l'entité item
     */
    public UUID getEntityUUID() {
        return itemEntity != null ? itemEntity.getUniqueId() : null;
    }

    /**
     * Retourne le générateur associé
     */
    public MDTGenerator getGenerator() {
        return generator;
    }

    /**
     * Retourne la location du display
     */
    public Location getLocation() {
        return location.clone();
    }
}
