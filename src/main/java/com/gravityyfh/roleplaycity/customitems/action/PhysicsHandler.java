package com.gravityyfh.roleplaycity.customitems.action;

import com.gravityyfh.roleplaycity.RoleplayCity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface PhysicsHandler {
    void throwItem(Player p, ItemStack toThrow, double power, int fuseSeconds, double explosionPower, double explosionRadius,
                  boolean fire, boolean breakBlocks, boolean damageEntities,
                  double throwAngle, double gravity, boolean bounce, double bounceFactor,
                  boolean showTrail, String trailParticle, int particleCount, double particleRadius, RoleplayCity plugin);
}
