package com.gravityyfh.roleplaycity.customitems.action;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.customitems.model.ItemAction;
import com.gravityyfh.roleplaycity.heist.data.Heist;
import com.gravityyfh.roleplaycity.heist.data.PlacedBomb;
import com.gravityyfh.roleplaycity.heist.manager.HeistManager;
import com.gravityyfh.roleplaycity.service.ProfessionalServiceManager;
import com.gravityyfh.roleplaycity.service.ProfessionalServiceType;
import com.gravityyfh.roleplaycity.util.BankUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;

public class ActionExecutor {

    private final RoleplayCity plugin;
    private PhysicsHandler physicsHandler;

    public ActionExecutor(RoleplayCity plugin) {
        this.plugin = plugin;
        initializePhysicsHandler();
    }

    private void initializePhysicsHandler() {
        try {
            // Vérifier si ItemDisplay existe (1.19.4+)
            Class.forName("org.bukkit.entity.ItemDisplay");
            // Si oui, charger le handler moderne via Reflection pour éviter VerifyError sur ce fichier
            Class<?> clazz = Class.forName("com.gravityyfh.roleplaycity.customitems.action.ModernPhysicsHandler");
            physicsHandler = (PhysicsHandler) clazz.newInstance();
            plugin.getLogger().info("[ActionExecutor] Physique avancée activée (ItemDisplay détecté)");
        } catch (Throwable e) {
            plugin.getLogger().warning("[ActionExecutor] ItemDisplay non supporté ou erreur. Mode physique simplifié activé.");
            physicsHandler = null; // On utilisera le fallback
        }
    }

    public void execute(List<ItemAction> actions, Player player, Entity target) {
        for (ItemAction action : actions) {
            executeAction(action, player, target);
        }
    }

    private void executeAction(ItemAction action, Player player, Entity target) {
        String targetType = action.getString("target", "SELF");
        Entity finalTarget = targetType.equalsIgnoreCase("TARGET") ? target : player;

        if (finalTarget == null && targetType.equalsIgnoreCase("TARGET")) {
            return; // Pas de cible
        }

        switch (action.getType()) {
            case MESSAGE -> {
                if (finalTarget instanceof Player p) {
                    String msg = action.getString("content", "");
                    p.sendMessage(ChatColor.translateAlternateColorCodes('&', replacePlaceholders(msg, player, target)));
                }
            }
            case CONSOLE_COMMAND -> {
                String cmd = action.getString("command", "");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), replacePlaceholders(cmd, player, target));
            }
            case PLAYER_COMMAND -> {
                if (finalTarget instanceof Player p) {
                    String cmd = action.getString("command", "");
                    p.performCommand(replacePlaceholders(cmd, player, target));
                }
            }
            case PLAY_SOUND -> {
                if (finalTarget instanceof Player p) {
                    try {
                        Sound sound = Sound.valueOf(action.getString("sound", "BLOCK_NOTE_BLOCK_PLING"));
                        float volume = (float) action.getDouble("volume", 1.0);
                        float pitch = (float) action.getDouble("pitch", 1.0);
                        p.playSound(p.getLocation(), sound, volume, pitch);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Son invalide: " + action.getString("sound", ""));
                    }
                }
            }
            case POTION_EFFECT -> {
                if (finalTarget instanceof LivingEntity le) {
                    try {
                        PotionEffectType type = PotionEffectType.getByName(action.getString("effect", ""));
                        if (type != null) {
                            int duration = action.getInt("duration", 60);
                            int amplifier = action.getInt("amplifier", 0);
                            le.addPotionEffect(new PotionEffect(type, duration, amplifier));
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Effet invalide: " + action.getString("effect", ""));
                    }
                }
            }
            case DAMAGE -> {
                if (finalTarget instanceof LivingEntity le) {
                    le.damage(action.getDouble("amount", 1.0));
                }
            }
            case HEAL -> {
                if (finalTarget instanceof LivingEntity le) {
                    double amount = action.getDouble("amount", 1.0);
                    double newHealth = Math.min(le.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue(), le.getHealth() + amount);
                    le.setHealth(newHealth);
                }
            }
            case TAKE_ITEM -> {
                if (player.getInventory().getItemInMainHand().getAmount() > 1) {
                    player.getInventory().getItemInMainHand().setAmount(player.getInventory().getItemInMainHand().getAmount() - 1);
                } else {
                    player.getInventory().setItemInMainHand(null);
                }
            }
            case TELEPORT -> {
                if (finalTarget instanceof Player p) {
                    String dest = action.getString("destination", "");
                    if (dest.equals("town_spawn")) {
                        String townName = plugin.getTownManager().getPlayerTown(p.getUniqueId());
                        if (townName != null) {
                            com.gravityyfh.roleplaycity.town.data.Town town = plugin.getTownManager().getTown(townName);
                            if (town != null && town.getSpawnLocation() != null) {
                                p.teleport(town.getSpawnLocation());
                            }
                        }
                    }
                }
            }
            case OPEN_BANK -> {
                if (finalTarget instanceof Player p) {
                    // Ouvrir le flux bancaire (Création -> Loading -> Menu)
                    try {
                        BankUtils.openBankFlow(p);
                    } catch (Throwable e) {
                        p.sendMessage("§cErreur: Système bancaire indisponible.");
                        plugin.getLogger().severe("[ActionExecutor] Erreur BankUtils: " + e.getMessage());
                    }
                }
            }
            case OPEN_MAIRIE -> {
                if (finalTarget instanceof Player p) {
                    // Ouvrir le nouveau système de mairie avec borne automatique
                    try {
                        // Récupérer la ville du joueur (citoyenneté) ou première ville disponible
                        String townName = null;

                        // 1. D'abord, essayer la ville de citoyenneté
                        if (plugin.getTownManager() != null) {
                            townName = plugin.getTownManager().getPlayerTown(p.getUniqueId());
                        }

                        // 2. Si pas citoyen, prendre la première ville disponible
                        if (townName == null && plugin.getTownManager() != null) {
                            var towns = plugin.getTownManager().getTowns();
                            if (!towns.isEmpty()) {
                                townName = towns.values().iterator().next().getName();
                            }
                        }

                        if (townName != null) {
                            // Ouvrir le nouveau MairieGUI avec animation de scan
                            com.gravityyfh.roleplaycity.mairie.gui.MairieGUI mairieGUI =
                                new com.gravityyfh.roleplaycity.mairie.gui.MairieGUI(
                                    plugin,
                                    plugin.getTownManager(),
                                    plugin.getIdentityManager(),
                                    plugin.getAppointmentManager(),
                                    townName
                                );
                            mairieGUI.open(p);
                        } else {
                            p.sendMessage("§cErreur: Aucune ville disponible.");
                        }
                    } catch (Throwable e) {
                        p.sendMessage("§cErreur: Système de mairie indisponible.");
                        plugin.getLogger().severe("[ActionExecutor] Erreur MairieGUI: " + e.getMessage());
                    }
                }
            }
            case THROW_ITEM -> {
                if (finalTarget instanceof Player p) {
                    // Configuration
                    double power = action.getDouble("power", 0.8);
                    int fuseSeconds = action.getInt("fuse_seconds", 4);
                    double explosionPower = action.getDouble("explosion_power", 2.0);
                    double explosionRadius = action.getDouble("explosion_radius", explosionPower);
                    boolean fire = Boolean.parseBoolean(action.getString("fire", "false"));
                    boolean breakBlocks = Boolean.parseBoolean(action.getString("break_blocks", "true"));
                    boolean damageEntities = Boolean.parseBoolean(action.getString("damage_entities", "true"));

                    boolean advancedPhysics = action.getBoolean("advanced_physics", true);
                    double throwAngle = action.getDouble("throw_angle", 10.0);
                    double gravity = action.getDouble("gravity", 0.04);
                    boolean bounce = action.getBoolean("bounce", true);
                    double bounceFactor = action.getDouble("bounce_factor", 0.5);

                    boolean showTrail = action.getBoolean("show_trail", true);
                    String trailParticle = action.getString("trail_particle", "SMOKE_NORMAL");

                    // Configuration des particules d'explosion
                    int particleCount = action.getInt("particle_count", 20);
                    double particleRadius = action.getDouble("particle_radius", 0.5);

                    // Item à lancer
                    org.bukkit.inventory.ItemStack toThrow = p.getInventory().getItemInMainHand().clone();
                    toThrow.setAmount(1);

                    // Si physicsHandler est dispo et activé
                    if (advancedPhysics && physicsHandler != null) {
                         physicsHandler.throwItem(p, toThrow, power, fuseSeconds, explosionPower, explosionRadius, fire, breakBlocks, damageEntities, throwAngle, gravity, bounce, bounceFactor, showTrail, trailParticle, particleCount, particleRadius, plugin);
                    } else {
                        // Fallback sur ancienne méthode (Entity Item classique)
                        org.bukkit.entity.Item itemEntity = p.getWorld().dropItem(p.getEyeLocation(), toThrow);
                        itemEntity.setPickupDelay(Integer.MAX_VALUE);
                        itemEntity.setVelocity(p.getLocation().getDirection().multiply(power * 1.5));

                        new org.bukkit.scheduler.BukkitRunnable() {
                            @Override
                            public void run() {
                                if (itemEntity.isValid()) {
                                    explode(itemEntity.getLocation(), explosionPower, explosionRadius, fire, breakBlocks, damageEntities, particleCount, particleRadius, p);
                                    itemEntity.remove();
                                }
                            }
                        }.runTaskLater(plugin, fuseSeconds * 20L);
                    }
                }
            }
            case START_HEIST -> {
                if (finalTarget instanceof Player p) {
                    // Récupérer le HeistManager
                    HeistManager heistManager = plugin.getHeistManager();
                    if (heistManager == null) {
                        p.sendMessage(ChatColor.RED + "Le système de cambriolage n'est pas activé.");
                        return;
                    }

                    // Récupérer le bloc ciblé (où la bombe sera posée)
                    org.bukkit.block.Block targetBlock = p.getTargetBlockExact(5);
                    if (targetBlock == null || targetBlock.getType().isAir()) {
                        p.sendMessage(ChatColor.RED + "Vous devez viser un bloc pour poser la bombe.");
                        return;
                    }

                    // Location au-dessus du bloc ciblé
                    Location bombLocation = targetBlock.getLocation().add(0, 1, 0);

                    // Tenter de démarrer le cambriolage
                    Heist heist = heistManager.startHeist(p, bombLocation);

                    if (heist != null) {
                        // Succès! L'item sera consommé par TAKE_ITEM
                        p.playSound(p.getLocation(), Sound.ENTITY_TNT_PRIMED, 1.0f, 0.5f);
                    }
                    // Si null, le HeistManager a déjà envoyé le message d'erreur
                }
            }
            case PLAYER_POLICE_SERVICE -> {
                if (finalTarget instanceof Player p) {
                    // Toggle le service police via ProfessionalServiceManager
                    ProfessionalServiceManager serviceManager = plugin.getProfessionalServiceManager();
                    if (serviceManager == null) {
                        p.sendMessage(ChatColor.RED + "Le système de service professionnel n'est pas activé.");
                        return;
                    }
                    serviceManager.toggleService(p, ProfessionalServiceType.POLICE);
                }
            }
            case PLAYER_MEDICAL_SERVICE -> {
                if (finalTarget instanceof Player p) {
                    // Toggle le service médical via ProfessionalServiceManager
                    ProfessionalServiceManager serviceManager = plugin.getProfessionalServiceManager();
                    if (serviceManager == null) {
                        p.sendMessage(ChatColor.RED + "Le système de service professionnel n'est pas activé.");
                        return;
                    }
                    serviceManager.toggleService(p, ProfessionalServiceType.MEDICAL);
                }
            }
            case PLAYER_JUDGE_SERVICE -> {
                if (finalTarget instanceof Player p) {
                    // Toggle le service juge via ProfessionalServiceManager
                    ProfessionalServiceManager serviceManager = plugin.getProfessionalServiceManager();
                    if (serviceManager == null) {
                        p.sendMessage(ChatColor.RED + "Le système de service professionnel n'est pas activé.");
                        return;
                    }
                    serviceManager.toggleService(p, ProfessionalServiceType.JUDGE);
                }
            }
            case PLACE_HEIST_BOMB -> {
                if (finalTarget instanceof Player p) {
                    // Récupérer le HeistManager
                    HeistManager heistManager = plugin.getHeistManager();
                    if (heistManager == null) {
                        p.sendMessage(ChatColor.RED + "Le système de cambriolage n'est pas activé.");
                        return;
                    }

                    // Récupérer le bloc ciblé (où la bombe sera posée)
                    org.bukkit.block.Block targetBlock = p.getTargetBlockExact(5);
                    if (targetBlock == null || targetBlock.getType().isAir()) {
                        p.sendMessage(ChatColor.RED + "Vous devez viser un bloc pour poser la bombe.");
                        return;
                    }

                    // Location au-dessus du bloc ciblé (centré sur le bloc)
                    Location bombLocation = targetBlock.getLocation().add(0.5, 1.0, 0.5);

                    // Placer la bombe comme furniture ItemsAdder (comme /iaget my_items:bomb posé)
                    Entity bombEntity = spawnBombFurniture(p, bombLocation);

                    if (bombEntity != null) {
                        // Enregistrer la bombe dans HeistManager
                        PlacedBomb bomb = heistManager.registerPlacedBomb(p, bombLocation, bombEntity.getUniqueId());

                        // Son de placement
                        p.playSound(p.getLocation(), Sound.BLOCK_STONE_PLACE, 1.0f, 0.8f);

                        // Message au joueur selon le contexte
                        if (bomb.isOnClaimedPlot()) {
                            p.sendMessage(ChatColor.YELLOW + "Bombe posée sur le terrain " +
                                ChatColor.WHITE + bomb.getPlotId() +
                                ChatColor.YELLOW + " de " + ChatColor.WHITE + bomb.getTownName());
                        } else {
                            p.sendMessage(ChatColor.YELLOW + "Bombe posée (hors zone protégée)");
                        }
                        p.sendMessage(ChatColor.GREEN + "➤ Clic droit sur la bombe pour confirmer le déclenchement.");
                    }
                }
            }
        }
    }

    /**
     * Spawn la bombe comme un furniture ItemsAdder (comme si le joueur l'avait posé avec /iaget)
     * Retourne l'entité (ArmorStand) du furniture, ou null si échec
     */
    private Entity spawnBombFurniture(Player player, Location location) {
        // ID ItemsAdder de la bombe (doit correspondre à custom_items.yml)
        String bombItemsAdderId = "my_items:bomb";

        // Vérifier si ItemsAdder est disponible
        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) {
            plugin.getLogger().warning("[Heist] ItemsAdder non disponible, impossible de placer la bombe furniture");
            return spawnBombItemFallback(player, location);
        }

        try {
            // Utiliser CustomFurniture.spawnPreciseNonSolid() pour placer le furniture
            Class<?> customFurnitureClass = Class.forName("dev.lone.itemsadder.api.CustomFurniture");

            // Ajuster la location pour que la bombe soit bien posée sur le bloc
            Location spawnLoc = location.clone();
            // Centrer sur le bloc et au niveau du sol
            spawnLoc.setX(Math.floor(spawnLoc.getX()) + 0.5);
            spawnLoc.setZ(Math.floor(spawnLoc.getZ()) + 0.5);
            spawnLoc.setY(Math.floor(spawnLoc.getY()));

            // Orienter la bombe vers le joueur
            spawnLoc.setYaw(player.getLocation().getYaw() + 180);

            Object customFurniture = customFurnitureClass.getMethod("spawnPreciseNonSolid", String.class, Location.class)
                    .invoke(null, bombItemsAdderId, spawnLoc);

            if (customFurniture == null) {
                plugin.getLogger().warning("[Heist] Impossible de spawn le furniture ItemsAdder: " + bombItemsAdderId);
                return spawnBombItemFallback(player, location);
            }

            // Récupérer l'ArmorStand associé au furniture
            ArmorStand armorStand = (ArmorStand) customFurnitureClass.getMethod("getArmorstand").invoke(customFurniture);

            if (armorStand != null) {
                // Ajouter le tag pour identifier la bombe
                armorStand.addScoreboardTag("heist_bomb");
                // NE PAS mettre Invulnerable ici - la bombe doit pouvoir être cassée avant confirmation
                // Elle sera rendue invulnérable au démarrage du timer dans HeistManager

                plugin.getLogger().info("[Heist] Bombe furniture posée par " + player.getName() + " à " + spawnLoc);
                return armorStand;
            }

        } catch (Exception e) {
            plugin.getLogger().warning("[Heist] Erreur lors du spawn du furniture bombe: " + e.getMessage());
            e.printStackTrace();
        }

        // Fallback si ItemsAdder échoue
        return spawnBombItemFallback(player, location);
    }

    /**
     * Fallback: Spawn un item droppé si ItemsAdder n'est pas disponible
     */
    private Entity spawnBombItemFallback(Player player, Location location) {
        org.bukkit.inventory.ItemStack bombStack = new org.bukkit.inventory.ItemStack(org.bukkit.Material.TNT);
        org.bukkit.inventory.meta.ItemMeta meta = bombStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "💣 Bombe de Cambriolage");
            bombStack.setItemMeta(meta);
        }

        org.bukkit.entity.Item droppedItem = location.getWorld().dropItem(location, bombStack);
        droppedItem.setPickupDelay(Integer.MAX_VALUE);
        droppedItem.setVelocity(new Vector(0, 0, 0));
        droppedItem.setGravity(false); // Ne bouge pas
        // NE PAS mettre Invulnerable - la bombe doit pouvoir être cassée avant confirmation
        droppedItem.setGlowing(true);
        droppedItem.setCustomName(ChatColor.RED + "💣 BOMBE");
        droppedItem.setCustomNameVisible(true);
        droppedItem.addScoreboardTag("heist_bomb");

        plugin.getLogger().info("[Heist] Bombe fallback posée par " + player.getName() + " à " + location);
        return droppedItem;
    }
    
    private void explode(Location loc, double power, double radius, boolean fire, boolean breakBlocks, boolean damageEntities, int particleCount, double particleRadius, Player source) {
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
                    plugin.getLogger().info("[Heist-Action] Explosion custom item pendant ROBBERY - Autorisation casse blocs!");
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
                                // Vérification protection terrain (comme TownProtectionListener)
                                if (!canExplodeBlock(blockLoc)) {
                                    continue; // Bloc protégé, ne pas casser
                                }

                                // Calculer la "force" restante à cette distance
                                // Plus on est loin, moins la force est efficace
                                double remainingPower = power * (1 - (distance / radius));

                                // Obtenir la résistance du bloc
                                float blockResistance = block.getType().getBlastResistance();

                                // Si la force restante est supérieure à la résistance, détruire le bloc
                                if (remainingPower > blockResistance / 5.0) {
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

    private String replacePlaceholders(String text, Player player, Entity target) {
        text = text.replace("%player%", player.getName());
        if (target != null) {
            text = text.replace("%target%", target.getName());
            if (target instanceof Player) {
                text = text.replace("%target_name%", target.getName());
            } else {
                text = text.replace("%target_name%", target.getType().name());
            }
        } else {
            text = text.replace("%target%", "Aucun");
            text = text.replace("%target_name%", "Aucun");
        }
        return text;
    }

    /**
     * Vérifie si un bloc peut être cassé par une explosion custom.
     * Réutilise la même logique que TownProtectionListener.shouldProtectFromExplosion()
     * pour garantir un comportement cohérent entre TNT et items custom.
     */
    private boolean canExplodeBlock(Location blockLoc) {
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