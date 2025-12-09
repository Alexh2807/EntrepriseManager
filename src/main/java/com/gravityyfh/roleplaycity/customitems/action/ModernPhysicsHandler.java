package com.gravityyfh.roleplaycity.customitems.action;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.mdt.MDTRushManager;
import com.gravityyfh.roleplaycity.mdt.data.MDTBed;
import com.gravityyfh.roleplaycity.mdt.data.MDTGame;
import com.gravityyfh.roleplaycity.mdt.data.MDTGameState;
import com.gravityyfh.roleplaycity.mdt.data.MDTPlayer;
import com.gravityyfh.roleplaycity.mdt.data.MDTTeam;
// Note: BlockTracker n'existe plus avec le système FAWE
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;

import static com.gravityyfh.roleplaycity.EntrepriseManagerLogic.plugin;

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

                                // === PROTECTION MDT : Vérifier les blocs incassables du MDT ===
                                if (isInMDTWorld(loc.getWorld()) && isUnbreakableMDTBlock(block.getType(), plugin)) {
                                    continue; // Bloc MDT protégé, ne pas casser
                                }

                                // Calculer la "force" restante à cette distance
                                // Plus on est loin, moins la force est efficace
                                double remainingPower = power * (1 - (distance / radius));

                                // Obtenir la résistance du bloc
                                float blockResistance = block.getType().getBlastResistance();

                                // Si la force restante est supérieure à la résistance, détruire le bloc
                                if (remainingPower > blockResistance / 5.0) {
                                    // === GESTION SPÉCIALE DES LITS MDT ===
                                    if (block.getType().name().endsWith("_BED")) {
                                        // PAS de régénération pour les lits MDT - gérés par le système MDT
                                        handleBedDestructionByDynamite(block, source, plugin);
                                    } else {
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

    /**
     * Gère la destruction d'un lit par la dynamite
     * Reproduit la logique de MDTBedListener pour les explosions custom
     */
    private void handleBedDestructionByDynamite(Block block, Entity source, RoleplayCity plugin) {
        plugin.getLogger().info("[Dynamite-Debug] Explosion sur un lit détectée en: " + block.getLocation());

        // Vérifier si une partie MDT est active
        MDTRushManager mdtManager = plugin.getMDTRushManager();
        if (mdtManager == null || !mdtManager.hasActiveGame()) {
            plugin.getLogger().info("[Dynamite-Debug] Pas de partie active, destruction vanilla.");
            // Pas de partie MDT, détruire normalement
            block.breakNaturally();
            return;
        }

        MDTGame game = mdtManager.getCurrentGame();
        if (game.getState() != MDTGameState.PLAYING) {
            plugin.getLogger().info("[Dynamite-Debug] Partie non lancée (State=" + game.getState() + "), destruction vanilla.");
            block.breakNaturally();
            return;
        }

        // Vérifier si c'est un lit MDT
        MDTBed mdtBed = game.getBedAtLocation(block.getLocation());
        if (mdtBed == null) {
            plugin.getLogger().warning("[Dynamite-Debug] ⚠️ Lit trouvé physiquement mais NON ENREGISTRÉ dans la partie !");
            plugin.getLogger().warning("[Dynamite-Debug] Loc: " + block.getLocation().getBlockX() + "," + block.getLocation().getBlockY() + "," + block.getLocation().getBlockZ());
            plugin.getLogger().warning("[Dynamite-Debug] Vérifiez locations.beds dans mdt.yml !");
            block.breakNaturally();
            return;
        }

        if (mdtBed.isDestroyed()) {
            plugin.getLogger().info("[Dynamite-Debug] Lit déjà détruit, ignoré.");
            block.breakNaturally();
            return;
        }

        plugin.getLogger().info("[Dynamite-Debug] ✅ Lit MDT valide identifié (" + mdtBed.getId() + "). Destruction en cours...");

        // Récupérer le joueur source
        Player sourcePlayer = null;
        if (source instanceof Player) {
            sourcePlayer = (Player) source;
        }

        // Note: BlockTracker n'existe plus avec le système FAWE
        // On ne track plus les blocs individuellement

        // Tracker les deux parties du lit (comme dans MDTBedListener)
        trackBedBlockForDynamite(block);

        // Détruire physiquement le lit sans drops
        destroyBedBlocksForDynamite(block);

        // Gérer la destruction du lit (points, messages, etc.)
        handleBedDestructionForDynamite(mdtBed, sourcePlayer, game, plugin);
    }

    /**
     * Tracke un bloc de lit et son pendant pour la dynamite
     * Note: Avec FAWE, on ne track plus les blocs individuellement
     */
    private void trackBedBlockForDynamite(Block block) {
        if (!(block.getBlockData() instanceof Bed)) return;

        Bed bedData = (Bed) block.getBlockData();

        // Note: Le tracking des blocs n'est plus nécessaire avec FAWE

        // Trouver et tracker l'autre partie du lit
        Block otherPart;
        if (bedData.getPart() == Bed.Part.HEAD) {
            otherPart = block.getRelative(bedData.getFacing().getOppositeFace());
        } else {
            otherPart = block.getRelative(bedData.getFacing());
        }

        if (otherPart.getType().name().endsWith("_BED")) {
            // Note: Le tracking n'est plus nécessaire avec FAWE
            // La restauration est gérée par schématique complète
        }
    }

    /**
     * Détruit physiquement les deux parties d'un lit sans drops
     */
    private void destroyBedBlocksForDynamite(Block block) {
        if (!(block.getBlockData() instanceof Bed)) return;

        Bed bedData = (Bed) block.getBlockData();

        // Trouver l'autre partie du lit
        Block otherPart;
        if (bedData.getPart() == Bed.Part.HEAD) {
            otherPart = block.getRelative(bedData.getFacing().getOppositeFace());
        } else {
            otherPart = block.getRelative(bedData.getFacing());
        }

        // Détruire les deux parties sans drops
        block.setType(Material.AIR, false);
        if (otherPart.getType().name().endsWith("_BED")) {
            otherPart.setType(Material.AIR, false);
        }
    }

    /**
     * Gère la logique de destruction du lit (points, messages, etc.)
     * Copie de la logique de MDTBedListener.handleBedDestruction()
     */
    private void handleBedDestructionForDynamite(MDTBed mdtBed, Player player, MDTGame game, RoleplayCity plugin) {
        mdtBed.setDestroyed(true);

        // Déterminer l'équipe responsable
        MDTTeam attackerTeam = null;
        String attackerName = "une dynamite";

        if (player != null) {
            MDTPlayer p = game.getPlayer(player.getUniqueId());
            if (p != null) {
                attackerTeam = p.getTeam();
                attackerName = player.getName();
                p.addBedDestroyed();
            }
        }

        if (mdtBed.isTeamBed()) {
            MDTTeam victimTeam = mdtBed.getOwnerTeam();

            // Ajouter +10 points à l'équipe attaquante
            if (attackerTeam != null && attackerTeam != victimTeam) {
                game.addTeamPoints(attackerTeam, 10);
            }

            // Messages et effets
            String message = plugin.getMDTRushManager().getConfig().getFormattedMessage("bed-break",
                    "%team%", victimTeam.getColoredName(), "%player%", attackerName);
            plugin.getMDTRushManager().broadcastToGame(message);

            for (MDTPlayer p : game.getAllPlayers()) {
                Player pl = p.getPlayer();
                if (pl != null) {
                    pl.playSound(pl.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 1.0f);
                    if (p.getTeam() == victimTeam) {
                        pl.sendTitle(ChatColor.RED + "LIT DÉTRUIT!", ChatColor.GRAY + "Tu ne peux plus respawn!", 10, 70, 20);
                    } else if (p.getTeam() == attackerTeam) {
                        pl.sendMessage(ChatColor.GREEN + "+10 points pour la destruction du lit ennemi!");
                    }
                }
            }
        } else if (mdtBed.isNeutralBed()) {
            if (mdtBed.getClaimedBy() != null) return; // Déjà pris

            if (attackerTeam != null) {
                mdtBed.setClaimedBy(attackerTeam);
                int bonus = mdtBed.getBonusHearts();
                int maxBonus = plugin.getMDTRushManager().getConfig().getMaxBonusHearts();

                for (MDTPlayer teammate : game.getTeamPlayers(attackerTeam)) {
                    teammate.addBonusHearts(bonus, maxBonus);
                    teammate.applyBonusHearts();
                    if (teammate.getPlayer() != null) {
                        teammate.getPlayer().sendMessage(ChatColor.GREEN + "Lit neutre explosé ! +" + bonus + " cœurs permanents !");
                        teammate.getPlayer().playSound(teammate.getPlayer().getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                    }
                }
                plugin.getMDTRushManager().broadcastToGame(ChatColor.YELLOW + attackerName + " a explosé un lit neutre pour l'équipe " + attackerTeam.getColoredName() + " !");
            }
        }
    }

    /**
     * Vérifie si le monde est le monde MDT
     */
    private boolean isInMDTWorld(org.bukkit.World world) {
        if (world == null) return false;

        MDTRushManager mdtManager = plugin.getMDTRushManager();
        if (mdtManager == null) return false;

        String mdtWorldName = mdtManager.getConfig().getWorldName();
        return world.getName().equalsIgnoreCase(mdtWorldName);
    }

    /**
     * Vérifie si un bloc est dans la liste des blocs incassables du MDT
     */
    private boolean isUnbreakableMDTBlock(org.bukkit.Material material, RoleplayCity plugin) {
        MDTRushManager mdtManager = plugin.getMDTRushManager();
        if (mdtManager == null) return false;

        return mdtManager.getConfig().isUnbreakableBlock(material);
    }
}
