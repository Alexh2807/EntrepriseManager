package com.gravityyfh.roleplaycity.customitems.action;

import com.gravityyfh.roleplaycity.RoleplayCity;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;

public class ModernPhysicsHandler implements PhysicsHandler {

    @Override
    public void throwItem(Player p, ItemStack toThrow, double power, int fuseSeconds, double explosionPower, double explosionRadius,
                          boolean fire, boolean breakBlocks, boolean damageEntities,
                          double throwAngle, double gravity, boolean bounce, double bounceFactor,
                          boolean showTrail, String trailParticle, int particleCount, double particleRadius, RoleplayCity plugin) {

        // === PHYSIQUE AVANCÉE (ItemDisplay + RayTrace) ===
        Location startLoc = p.getEyeLocation();
        startLoc.add(startLoc.getDirection().multiply(0.5));

        ItemDisplay display = p.getWorld().spawn(startLoc, ItemDisplay.class);
        display.setItemStack(toThrow);
        display.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
        display.setTeleportDuration(1);

        Transformation transform = display.getTransformation();
        transform.getScale().set(0.5f);
        display.setTransformation(transform);

        Vector direction = p.getLocation().getDirection().normalize();

        if (throwAngle != 0) {
             double angleRad = Math.toRadians(throwAngle);
             direction.setY(direction.getY() + Math.sin(angleRad));
             direction.normalize();
        }

        Vector velocity = direction.multiply(power);

        Vector rotationAxis = new Vector(Math.random(), Math.random(), Math.random()).normalize();
        float rotationSpeed = 0.2f + (float)Math.random() * 0.2f;

        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;
            int maxTicks = fuseSeconds * 20;
            boolean grounded = false;
            Vector currentVel = velocity.clone();
            float currentAngle = 0;

            @Override
            public void run() {
                if (!display.isValid() || ticks >= maxTicks) {
                    if (display.isValid()) {
                        explode(display.getLocation().add(0, 0.5, 0), explosionPower, explosionRadius, fire, breakBlocks, damageEntities, particleCount, particleRadius, p, plugin);
                        display.remove();
                    }
                    cancel();
                    return;
                }
                
                ticks++;

                if (!grounded) {
                    currentVel.setY(currentVel.getY() - gravity);
                    currentVel.multiply(0.98);
                    
                    Location currentLoc = display.getLocation();
                    double speed = currentVel.length();
                    
                    org.bukkit.util.RayTraceResult result = display.getWorld().rayTraceBlocks(
                            currentLoc, 
                            currentVel.clone().normalize(), 
                            speed + 0.1
                    );

                    if (result != null && result.getHitBlock() != null) {
                        if (bounce && speed > 0.1) {
                            org.bukkit.block.BlockFace face = result.getHitBlockFace();
                            if (face != null) {
                                Vector normal = new Vector(face.getModX(), face.getModY(), face.getModZ()).normalize();
                                double dot = currentVel.dot(normal);
                                currentVel.subtract(normal.multiply(2 * dot));
                                currentVel.multiply(bounceFactor);
                                display.getWorld().playSound(currentLoc, Sound.ENTITY_ITEM_PICKUP, 0.5f, 0.8f);
                            } else {
                                currentVel.multiply(-bounceFactor); 
                            }
                            
                            if (currentVel.length() < 0.05) {
                                grounded = true;
                            }
                        } else {
                            grounded = true;
                            Location hitLoc = result.getHitPosition().toLocation(currentLoc.getWorld());
                            hitLoc.add(0, 0.05, 0);
                            hitLoc.setPitch(0);
                            
                            display.teleport(hitLoc);
                            
                            Transformation t = display.getTransformation();
                            t.getLeftRotation().set(new AxisAngle4f((float) (Math.PI / 2), 1f, 0f, 0f));
                            display.setTransformation(t);
                            
                            display.getWorld().playSound(display.getLocation(), Sound.BLOCK_WOOL_PLACE, 1.0f, 1.0f);
                        }
                    } else {
                        display.teleport(currentLoc.add(currentVel));
                        
                        currentAngle += rotationSpeed;
                        Transformation t = display.getTransformation();
                        t.getLeftRotation().setAngleAxis(currentAngle, (float)rotationAxis.getX(), (float)rotationAxis.getY(), (float)rotationAxis.getZ());
                        display.setTransformation(t);
                    }
                } else {
                    if (ticks % 5 == 0) {
                        display.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_NORMAL, 
                            display.getLocation().add(0, 0.2, 0), 1, 0.05, 0.05, 0.05, 0.01);
                    }
                    if (ticks % 20 == 0) {
                         display.getWorld().playSound(display.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 0.5f, 1.5f);
                    }
                }
                
                if (showTrail && !grounded && ticks % 2 == 0) {
                     try {
                        org.bukkit.Particle particle = org.bukkit.Particle.valueOf(trailParticle);
                        display.getWorld().spawnParticle(particle, display.getLocation(), 1, 0, 0, 0, 0);
                    } catch (Exception ignored) {}
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
    
    private void explode(Location loc, double power, double radius, boolean fire, boolean breakBlocks, boolean damageEntities, int particleCount, double particleRadius, Player source, RoleplayCity plugin) {
        // Effets visuels (Lave et Fumée) - utiliser les paramètres configurables
        loc.getWorld().spawnParticle(org.bukkit.Particle.LAVA, loc, particleCount, particleRadius, particleRadius, particleRadius, 0.1);
        loc.getWorld().spawnParticle(org.bukkit.Particle.SMOKE_LARGE, loc, particleCount / 2, particleRadius, particleRadius, particleRadius, 0.1);

        // HEIST OVERRIDE: Si on est dans une ville en phase de cambriolage (ROBBERY),
        // forcer breakBlocks = true pour que l'explosion casse les blocs
        boolean shouldBreak = breakBlocks;

        try {
            var heistManager = plugin.getHeistManager();
            if (heistManager != null) {
                // FIX: Vérifier aussi les blocs adjacents car l'explosion peut être sur la face externe d'un mur (hors plot)
                boolean heistFound = false;

                // 1. Vérifier location exacte
                var heist = heistManager.getActiveHeistAt(loc);
                if (heist != null && heist.getPhase() == com.gravityyfh.roleplaycity.heist.data.HeistPhase.ROBBERY) {
                    heistFound = true;
                }

                // 2. Si pas trouvé, vérifier les alentours (radius blocs)
                if (!heistFound) {
                    int searchRadius = (int) Math.ceil(radius);
                    for (int x = -searchRadius; x <= searchRadius; x++) {
                        for (int y = -searchRadius; y <= searchRadius; y++) {
                            for (int z = -searchRadius; z <= searchRadius; z++) {
                                Location nearby = loc.clone().add(x, y, z);
                                heist = heistManager.getActiveHeistAt(nearby);
                                if (heist != null && heist.getPhase() == com.gravityyfh.roleplaycity.heist.data.HeistPhase.ROBBERY) {
                                    heistFound = true;
                                    break;
                                }
                            }
                            if (heistFound) break;
                        }
                        if (heistFound) break;
                    }
                }

                if (heistFound) {
                    // Phase de vol active - autoriser la casse des blocs
                    shouldBreak = true;
                    plugin.getLogger().info("[Heist-Physics] Explosion dynamite pendant ROBBERY - Autorisation casse blocs!");
                }
            }
        } catch (Exception e) {
            // Silencieux - continuer avec la valeur par défaut
        }

        // 1. Créer l'explosion visuelle/sonore SANS casser de blocs
        // On désactive breakBlocks pour contrôler manuellement la destruction
        // Cela permet d'avoir une explosion puissante (pour l'effet) mais un rayon contrôlé
        loc.getWorld().createExplosion(loc, (float) power, fire, false, source);

        // 2. Destruction MANUELLE des blocs dans le rayon configuré
        // Cela permet d'avoir explosion_power élevé (pour casser la stone) mais explosion_radius petit
        if (shouldBreak) {
            int radiusInt = (int) Math.ceil(radius);
            for (int x = -radiusInt; x <= radiusInt; x++) {
                for (int y = -radiusInt; y <= radiusInt; y++) {
                    for (int z = -radiusInt; z <= radiusInt; z++) {
                        Location blockLoc = loc.clone().add(x, y, z);
                        double distance = blockLoc.distance(loc);

                        // Vérifier si le bloc est dans le rayon
                        if (distance <= radius) {
                            org.bukkit.block.Block block = blockLoc.getBlock();

                            // Ne pas détruire l'air, le bedrock, ou les blocs indestructibles
                            if (!block.getType().isAir() && block.getType() != org.bukkit.Material.BEDROCK) {

                                // === PROTECTION TERRAIN : Vérifier si le bloc peut être cassé ===
                                if (!canExplodeBlock(blockLoc, plugin)) {
                                    continue; // Bloc protégé, ne pas casser
                                }

                                // Calculer la "force" restante à cette distance
                                // Plus on est loin, moins la force est efficace
                                double remainingPower = power * (1 - (distance / radius));

                                // Obtenir la résistance du bloc
                                float blockResistance = block.getType().getBlastResistance();

                                // Si la force restante est supérieure à la résistance, détruire le bloc
                                if (remainingPower > blockResistance / 5.0) {
                                    // Enregistrer le bloc pour régénération AVANT destruction
                                    // Passer le centre de l'explosion pour le tri extérieur->intérieur
                                    var regenListener = plugin.getExplosionRegenerationListener();
                                    if (regenListener != null) {
                                        regenListener.registerBlockForRegeneration(block, loc);
                                    }
                                    block.breakNaturally();
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Gestion MANUELLE des dégâts (utiliser le radius configuré)
        if (damageEntities) {
            double damageRadius = radius;
            double maxDamage = power * 3.0;

            for (Entity entity : loc.getWorld().getNearbyEntities(loc, damageRadius, damageRadius, damageRadius)) {
                if (entity instanceof LivingEntity living) {
                    double distance = entity.getLocation().distance(loc);
                    if (distance <= damageRadius) {
                        double damage = maxDamage * (1 - (distance / damageRadius));
                        if (damage > 0.5) {
                             living.damage(damage, source);
                        }
                    }
                }
            }
        }
    }

    /**
     * Vérifie si un bloc peut être cassé par une explosion custom.
     * Protège les terrains de ville sauf si HEIST en phase ROBBERY ou flag EXPLOSION activé.
     */
    private boolean canExplodeBlock(Location blockLoc, RoleplayCity plugin) {
        try {
            // Récupérer le ClaimManager
            var claimManager = plugin.getClaimManager();
            if (claimManager == null) {
                return true; // Pas de système de claims, autoriser
            }

            // Vérifier si le bloc est sur un terrain claimé
            var plot = claimManager.getPlotAt(blockLoc);
            if (plot == null) {
                return true; // Pas de terrain, autoriser l'explosion
            }

            // Vérifier le flag EXPLOSION du terrain
            if (plot.getFlag(com.gravityyfh.roleplaycity.town.data.PlotFlag.EXPLOSION)) {
                return true; // Explosions autorisées sur ce terrain
            }

            // HEIST BYPASS: Pendant la phase ROBBERY, autoriser
            var heistManager = plugin.getHeistManager();
            if (heistManager != null) {
                var heist = heistManager.getActiveHeistAt(blockLoc);
                if (heist != null && heist.getPhase() == com.gravityyfh.roleplaycity.heist.data.HeistPhase.ROBBERY) {
                    return true; // Mode cambriolage actif
                }
            }

            // Terrain protégé, pas d'explosion
            return false;

        } catch (Exception e) {
            // En cas d'erreur, autoriser (comportement par défaut)
            return true;
        }
    }
}
