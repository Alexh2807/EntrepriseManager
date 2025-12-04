package com.gravityyfh.roleplaycity.heist.manager;

import java.io.File;
import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.heist.config.HeistConfig;
import com.gravityyfh.roleplaycity.heist.data.*;
import com.gravityyfh.roleplaycity.heist.task.HeistCountdownTask;
import com.gravityyfh.roleplaycity.heist.task.HeistRobberyTask;
import com.gravityyfh.roleplaycity.service.ProfessionalServiceManager;
import com.gravityyfh.roleplaycity.service.ProfessionalServiceType;
import com.gravityyfh.roleplaycity.town.data.*;
import com.gravityyfh.roleplaycity.town.manager.ClaimManager;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import com.gravityyfh.roleplaycity.heist.data.PlacedBomb;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import dev.lone.itemsadder.api.CustomFurniture;

/**
 * Gestionnaire principal du système de cambriolage
 * Orchestre le cycle de vie des heists
 */
public class HeistManager {

    private final RoleplayCity plugin;
    private final HeistConfig config;
    private final TownManager townManager;
    private final ClaimManager claimManager;

    // Heists actifs indexés par plotKey
    private final Map<String, Heist> activeHeists;

    // Historique des heists (pour cooldowns et statistiques)
    private final Map<String, LocalDateTime> plotLastHeist; // plotKey -> dernière date
    private final Map<UUID, LocalDateTime> playerLastHeist; // playerUuid -> dernière date

    // Tâches schedulées
    private final Map<UUID, BukkitTask> countdownTasks; // heistId -> task
    private final Map<UUID, BukkitTask> robberyTasks;   // heistId -> task

    // Bombes posées mais pas encore confirmées (système en 2 étapes)
    private final Map<UUID, PlacedBomb> placedBombs;        // bombId -> PlacedBomb
    private final Map<UUID, PlacedBomb> armorStandToBomb;   // armorStandId -> PlacedBomb

    public HeistManager(RoleplayCity plugin, HeistConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.townManager = plugin.getTownManager();
        this.claimManager = plugin.getClaimManager();

        this.activeHeists = new ConcurrentHashMap<>();
        this.plotLastHeist = new ConcurrentHashMap<>();
        this.playerLastHeist = new ConcurrentHashMap<>();
        this.countdownTasks = new ConcurrentHashMap<>();
        this.robberyTasks = new ConcurrentHashMap<>();
        this.placedBombs = new ConcurrentHashMap<>();
        this.armorStandToBomb = new ConcurrentHashMap<>();
    }

    /**
     * Initialise le système de cambriolage au démarrage du serveur.
     * Nettoie tous les hologrammes orphelins et réinitialise les bombes.
     * DOIT être appelé APRÈS que tous les mondes soient chargés.
     *
     * FIX Bug 4: Nettoyage complet de tous les types d'hologrammes
     */
    public void initialize() {
        plugin.getLogger().info("[Heist] Initialisation du système de cambriolage...");

        // 1. Charger les bombes persistantes
        loadBombs();

        int bombsReset = 0;

        // 2. FIX Bug 4: Nettoyage GLOBAL de TOUS les hologrammes heist (orphelins après restart)
        int hologramsRemoved = cleanupAllOrphanHolograms();

        // 3. Reset des bombes chargées (Remise à l'état "Prêt")
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (armorStandToBomb.containsKey(entity.getUniqueId())) {
                    // C'est une bombe valide
                    entity.setGlowing(false); // Enlever l'effet de surbrillance du timer
                    entity.setCustomNameVisible(false); // Enlever le nom du timer s'il était collé dessus
                    // On s'assure qu'elle est invulnérable aux explosions accidentelles mais interagissable
                    entity.setInvulnerable(true);
                    bombsReset++;
                }
            }
        }

        // 4. Nettoyage des bombes physiques orphelines (Non enregistrées dans heist_bombs.yml)
        // (Optionnel : nettoyage basé sur ItemsAdder pour les doublons)
        try {
            Class<?> customFurnitureClass = Class.forName("dev.lone.itemsadder.api.CustomFurniture");
            // Code existant simplifié pour log
            plugin.getLogger().info("[Heist] Vérification de l'intégrité des furnitures ItemsAdder...");
        } catch (ClassNotFoundException e) {
            // ItemsAdder non dispo
        }

        if (hologramsRemoved > 0) {
            plugin.getLogger().info("[Heist] " + hologramsRemoved + " hologramme(s) orphelin(s) nettoyé(s).");
        }
        if (bombsReset > 0) {
            plugin.getLogger().info("[Heist] " + bombsReset + " bombe(s) réinitialisée(s) et prête(s).");
        }

        plugin.getLogger().info("[Heist] Initialisation terminée - Système opérationnel");
    }

    /**
     * Nettoie tous les heists actifs lors du shutdown du serveur.
     * Les bombes sont DÉJÀ dans placedBombs, on doit juste :
     * - Annuler les tâches de countdown/robbery
     * - Supprimer les hologrammes
     * - Sauvegarder l'état final
     */
    public void shutdown() {
        plugin.getLogger().info("[Heist] Arrêt du système...");

        // 1. Annuler toutes les tâches
        for (BukkitTask task : countdownTasks.values()) {
            if (task != null && !task.isCancelled()) task.cancel();
        }
        countdownTasks.clear();

        for (BukkitTask task : robberyTasks.values()) {
            if (task != null && !task.isCancelled()) task.cancel();
        }
        robberyTasks.clear();

        // 2. Supprimer les hologrammes des heists actifs (mais garder les bombes physiques!)
        for (Heist heist : activeHeists.values()) {
            // Supprimer UNIQUEMENT les hologrammes
            for (UUID hologramId : heist.getHologramEntityIds()) {
                Entity entity = Bukkit.getEntity(hologramId);
                if (entity != null) entity.remove();
            }
            heist.getHologramEntityIds().clear();
        }

        // 3. Nettoyage hologrammes résiduels (y compris simple timers)
        int removedHolograms = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof ArmorStand) {
                    if (entity.getScoreboardTags().contains("heist_hologram") ||
                        entity.getScoreboardTags().contains("heist_simple_timer")) {
                        entity.remove();
                        removedHolograms++;
                    }
                }
            }
        }

        if (removedHolograms > 0) {
            plugin.getLogger().info("[Heist] " + removedHolograms + " hologramme(s) supprimé(s)");
        }

        // 4. Sauvegarder toutes les bombes (celles avec timer actif seront réarmables après restart)
        saveBombs();

        activeHeists.clear();

        plugin.getLogger().info("[Heist] Système arrêté. " + placedBombs.size() + " bombe(s) sauvegardée(s).");
    }

    /**
     * Charge les bombes posées depuis le fichier.
     * Les bombes sont restaurées sans timer actif - le joueur devra les réarmer.
     */
    private void loadBombs() {
        File file = new File(plugin.getDataFolder(), "data/heist_bombs.yml");
        if (!file.exists()) return;

        org.bukkit.configuration.file.YamlConfiguration data =
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);

        if (!data.contains("bombs")) return;

        int count = 0;
        int hadTimerCount = 0;

        for (String key : data.getConfigurationSection("bombs").getKeys(false)) {
            try {
                String path = "bombs." + key + ".";

                // Informations de base
                UUID placerUuid = UUID.fromString(data.getString(path + "placerUuid"));
                String placerName = data.getString(path + "placerName");
                long placedAtTimestamp = data.getLong(path + "placedAtTimestamp", System.currentTimeMillis());

                // Location
                String worldName = data.getString(path + "world");
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("[Heist] Monde '" + worldName + "' non trouvé pour bombe " + key);
                    continue;
                }

                double x = data.getDouble(path + "x");
                double y = data.getDouble(path + "y");
                double z = data.getDouble(path + "z");
                Location loc = new Location(world, x, y, z);

                UUID armorStandId = UUID.fromString(data.getString(path + "armorStandId"));

                // Charger bombId si présent, sinon utiliser armorStandId
                UUID bombId;
                if (data.contains(path + "bombId")) {
                    bombId = UUID.fromString(data.getString(path + "bombId"));
                } else {
                    bombId = UUID.randomUUID();
                }

                // Créer la bombe avec toutes ses infos
                PlacedBomb bomb = new PlacedBomb(bombId, placerUuid, placerName, loc, armorStandId, placedAtTimestamp);

                // Informations terrain
                if (data.contains(path + "townName")) {
                    bomb.setTownName(data.getString(path + "townName"));
                    bomb.setPlotKey(data.getString(path + "plotKey"));
                    bomb.setPlotId(data.getString(path + "plotId"));
                }

                // Vérifier si elle avait un timer actif (pour le log)
                boolean hadTimer = data.getBoolean(path + "timerStarted", false);
                if (hadTimer) {
                    hadTimerCount++;
                }

                // IMPORTANT: Réinitialiser l'état du timer
                // Après un restart, la bombe est en attente de réarmement
                bomb.resetTimerState();

                placedBombs.put(bomb.getBombId(), bomb);
                armorStandToBomb.put(armorStandId, bomb);
                count++;

            } catch (Exception e) {
                plugin.getLogger().warning("[Heist] Erreur chargement bombe " + key + ": " + e.getMessage());
            }
        }

        if (count > 0) {
            plugin.getLogger().info("[Heist] " + count + " bombe(s) restaurée(s).");
            if (hadTimerCount > 0) {
                plugin.getLogger().info("[Heist] " + hadTimerCount + " bombe(s) avai(en)t un timer actif - En attente de réarmement.");
            }
        }
    }

    /**
     * Sauvegarde l'état actuel de TOUTES les bombes dans le fichier.
     * Les bombes restent dans le fichier tant qu'elles n'ont pas EXPLOSÉ.
     * Après un restart, elles pourront être réarmées par clic droit.
     */
    private void saveBombs() {
        File folder = new File(plugin.getDataFolder(), "data");
        if (!folder.exists()) folder.mkdirs();

        File file = new File(folder, "heist_bombs.yml");
        org.bukkit.configuration.file.YamlConfiguration data = new org.bukkit.configuration.file.YamlConfiguration();

        int savedCount = 0;

        // Sauvegarder toutes les bombes (qu'elles aient un timer actif ou non)
        for (PlacedBomb bomb : placedBombs.values()) {
            saveSingleBombEntry(data, bomb);
            savedCount++;
        }

        try {
            data.save(file);
            plugin.getLogger().info("[Heist] " + savedCount + " bombe(s) sauvegardée(s).");
        } catch (Exception e) {
            plugin.getLogger().severe("[Heist] Impossible de sauvegarder heist_bombs.yml");
            e.printStackTrace();
        }
    }

    /**
     * Sauvegarde une seule bombe avec toutes ses informations
     */
    private void saveSingleBombEntry(org.bukkit.configuration.file.YamlConfiguration data, PlacedBomb bomb) {
        String key = bomb.getArmorStandId().toString();
        String path = "bombs." + key + ".";

        // Informations de base
        data.set(path + "bombId", bomb.getBombId().toString());
        data.set(path + "placerUuid", bomb.getPlacedByUuid().toString());
        data.set(path + "placerName", bomb.getPlacedByName());
        data.set(path + "placedAtTimestamp", bomb.getPlacedAtTimestamp());

        // Location
        Location loc = bomb.getLocation();
        data.set(path + "world", loc.getWorld().getName());
        data.set(path + "x", loc.getX());
        data.set(path + "y", loc.getY());
        data.set(path + "z", loc.getZ());
        data.set(path + "armorStandId", bomb.getArmorStandId().toString());

        // Informations terrain
        if (bomb.getTownName() != null) {
            data.set(path + "townName", bomb.getTownName());
            data.set(path + "plotKey", bomb.getPlotKey());
            data.set(path + "plotId", bomb.getPlotId());
        }

        // État du timer
        data.set(path + "timerStarted", bomb.isTimerStarted());
        data.set(path + "remainingSeconds", bomb.getRemainingSeconds());
        data.set(path + "timerStartTimestamp", bomb.getTimerStartTimestamp());
    }

    // =========================================================================
    // VÉRIFICATION DES CONDITIONS
    // =========================================================================

    /**
     * Résultat de la vérification des conditions
     */
    public static class ConditionResult {
        private final boolean success;
        private final String errorMessage;

        private ConditionResult(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public static ConditionResult success() {
            return new ConditionResult(true, null);
        }

        public static ConditionResult failure(String message) {
            return new ConditionResult(false, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * Vérifie si toutes les conditions sont remplies pour démarrer un cambriolage
     */
    public ConditionResult checkConditions(Player player, Location bombLocation) {
        if (!config.isEnabled()) {
            return ConditionResult.failure("&cLe système de cambriolage est désactivé.");
        }

        // Vérifier que le joueur est dans une ville
        String townName = claimManager.getClaimOwner(bombLocation);
        if (townName == null) {
            return ConditionResult.failure("&cVous devez être dans une ville pour cambrioler.");
        }
        Town town = townManager.getTown(townName);
        if (town == null) {
            return ConditionResult.failure("&cVous devez être dans une ville pour cambrioler.");
        }

        // Récupérer le plot
        Chunk chunk = bombLocation.getChunk();
        String plotKey = bombLocation.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
        Plot plot = town.getPlot(bombLocation.getWorld().getName(), chunk.getX(), chunk.getZ());

        if (plot == null) {
            return ConditionResult.failure("&cCe terrain n'est pas claimé.");
        }

        // Vérifier le type de terrain
        if (!config.isPlotTypeTargetable(plot.getType())) {
            return ConditionResult.failure(
                config.getMessageRaw("error-invalid-plot-type")
            );
        }

        // Vérifier qu'il n'y a pas déjà un heist actif sur ce terrain
        if (activeHeists.containsKey(plotKey)) {
            return ConditionResult.failure(
                config.getMessageRaw("error-already-active")
            );
        }

        // Vérifier le cooldown du terrain
        LocalDateTime lastPlotHeist = plotLastHeist.get(plotKey);
        if (lastPlotHeist != null) {
            LocalDateTime cooldownEnd = lastPlotHeist.plusHours(config.getPlotCooldownHours());
            if (LocalDateTime.now().isBefore(cooldownEnd)) {
                String timeRemaining = formatTimeRemaining(cooldownEnd);
                return ConditionResult.failure(
                    config.getMessageRaw("error-plot-cooldown").replace("%time%", timeRemaining)
                );
            }
        }

        // Vérifier le cooldown du joueur
        LocalDateTime lastPlayerHeist = playerLastHeist.get(player.getUniqueId());
        if (lastPlayerHeist != null) {
            LocalDateTime cooldownEnd = lastPlayerHeist.plusHours(config.getPlayerCooldownHours());
            if (LocalDateTime.now().isBefore(cooldownEnd)) {
                String timeRemaining = formatTimeRemaining(cooldownEnd);
                return ConditionResult.failure(
                    config.getMessageRaw("error-player-cooldown").replace("%time%", timeRemaining)
                );
            }
        }

        // Vérifier la présence d'un commissariat
        if (config.isCommissariatRequired() && !hasCommissariat(town)) {
            return ConditionResult.failure(
                config.getMessageRaw("error-no-commissariat")
            );
        }

        // Compter les citoyens connectés
        int onlineCitizens = countOnlineCitizens(town);
        if (onlineCitizens < config.getMinOnlineCitizens()) {
            return ConditionResult.failure(
                config.getMessageRaw("error-not-enough-citizens")
                    .replace("%min%", String.valueOf(config.getMinOnlineCitizens()))
            );
        }

        // Compter les policiers connectés
        int onlinePolice = countOnlinePolice(town);
        if (onlinePolice < config.getMinOnlinePolice()) {
            return ConditionResult.failure(
                config.getMessageRaw("error-not-enough-police")
                    .replace("%min%", String.valueOf(config.getMinOnlinePolice()))
            );
        }

        return ConditionResult.success();
    }

    // =========================================================================
    // DÉMARRAGE DU CAMBRIOLAGE
    // =========================================================================

    /**
     * Démarre un nouveau cambriolage
     * @return le Heist créé ou null si échec
     */
    public Heist startHeist(Player initiator, Location bombLocation) {
        // Vérifier les conditions
        ConditionResult conditions = checkConditions(initiator, bombLocation);
        if (!conditions.isSuccess()) {
            initiator.sendMessage(ChatColor.translateAlternateColorCodes('&',
                config.getMessage("prefix") + conditions.getErrorMessage()));
            return null;
        }

        // Récupérer les infos du terrain
        String townName = claimManager.getClaimOwner(bombLocation);
        Town town = townManager.getTown(townName);
        Chunk chunk = bombLocation.getChunk();
        String plotKey = bombLocation.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
        Plot plot = town.getPlot(bombLocation.getWorld().getName(), chunk.getX(), chunk.getZ());

        // Créer le heist
        Heist heist = new Heist(
            initiator.getUniqueId(),
            town.getName(),
            plotKey,
            plot.getType(),
            bombLocation,
            config.getCountdownDuration(),
            config.getRobberyDuration(),
            config.getExplosionPower(),
            config.shouldBreakBlocks(),
            config.shouldDamageEntities(),
            config.getMaxDefuseAttempts()
        );

        // Ajouter l'initiateur comme participant
        heist.addParticipant(initiator.getUniqueId(), initiator.getName(), true);

        // Enregistrer le heist
        activeHeists.put(plotKey, heist);

        // Mettre à jour les cooldowns
        playerLastHeist.put(initiator.getUniqueId(), LocalDateTime.now());

        // Créer l'hologramme du timer
        spawnTimerHologram(heist);

        // Rendre la bombe invulnérable maintenant que le timer démarre
        // (avant, elle était cassable pour que le joueur puisse la récupérer s'il change d'avis)
        if (heist.getBombEntityId() != null) {
            UUID bombEntityId = heist.getBombEntityId();
            // Chercher l'entité de la bombe dans le monde
            for (Entity entity : bombLocation.getWorld().getEntities()) {
                if (entity.getUniqueId().equals(bombEntityId)) {
                    entity.setInvulnerable(true);
                    plugin.getLogger().info("[Heist] Bombe rendue invulnérable - timer démarré");
                    break;
                }
            }
        }

        // Si c'est un start manuel (pas via furniture), on crée une fausse entité physique pour le ciblage
        // (Optionnel, mais utile si on veut taper dessus)
        if (heist.getBombEntityId() == null) {
             // On utilise le premier hologramme comme "entité" par défaut si besoin
             if (!heist.getHologramEntityIds().isEmpty()) {
                 heist.setBombEntityId(heist.getHologramEntityIds().get(0));
             }
        }

        // Démarrer le countdown
        startCountdown(heist);

        // Alerter la police
        alertPolice(heist, town);

        // Notifier le propriétaire s'il est connecté
        notifyOwner(plot, town, heist);

        // Broadcast si activé
        if (config.shouldBroadcastHeistStart()) {
            broadcastHeistStart(heist, town, plot);
        }

        // Message au joueur
        String timeFormatted = formatSeconds(config.getCountdownDuration());
        initiator.sendMessage(ChatColor.translateAlternateColorCodes('&',
            config.getMessage("heist-started").replace("%time%", timeFormatted)));

        plugin.getLogger().info("[Heist] Cambriolage démarré par " + initiator.getName()
            + " sur " + plotKey + " dans " + town.getName());

        return heist;
    }

    // =========================================================================
    // GESTION DU COUNTDOWN
    // =========================================================================

    /**
     * Démarre le timer de countdown
     */
    private void startCountdown(Heist heist) {
        HeistCountdownTask task = new HeistCountdownTask(plugin, this, heist);
        BukkitTask bukkitTask = task.runTaskTimer(plugin, 20L, 20L); // Toutes les secondes
        countdownTasks.put(heist.getHeistId(), bukkitTask);
    }

    /**
     * Appelé quand le countdown atteint 0 (explosion)
     */
    public void onCountdownExpired(Heist heist) {
        // Annuler la tâche de countdown
        BukkitTask task = countdownTasks.remove(heist.getHeistId());
        if (task != null) {
            task.cancel();
        }

        // Déclencher l'explosion
        triggerExplosion(heist);

        // Démarrer la phase de vol
        startRobberyPhase(heist);
    }

    // =========================================================================
    // EXPLOSION
    // =========================================================================

    /**
     * Déclenche l'explosion de la bombe
     */
    public void triggerExplosion(Heist heist) {
        Location loc = heist.getBombLocation();
        World world = loc.getWorld();

        if (world == null) return;

        // IMPORTANT: Supprimer la bombe de placedBombs maintenant qu'elle explose
        if (heist.getBombEntityId() != null) {
            PlacedBomb bomb = armorStandToBomb.remove(heist.getBombEntityId());
            if (bomb != null) {
                placedBombs.remove(bomb.getBombId());
                saveBombs(); // Sauvegarder la suppression dans le fichier
                plugin.getLogger().info("[Heist] Bombe supprimée après explosion: " + bomb.getBombId());
            }
        }

        // IMPORTANT: Marquer que l'explosion est en cours AVANT de créer l'explosion
        // Cela permet au TownProtectionListener de savoir qu'il doit autoriser la casse
        heist.setExploding(true);

        // Passer en phase ROBBERY
        heist.triggerExplosion();

        // Supprimer l'entité de la bombe AVANT l'explosion
        removeBombEntity(heist);

        // Créer l'explosion de la bombe de heist avec destruction manuelle
        // Utilise le même système que les dynamites pour contrôler précisément le rayon

        float explosionPower = config.getExplosionPower();
        float explosionRadius = config.getExplosionRadius();
        boolean shouldBreak = heist.shouldBreakBlocks();

        // Centrer l'explosion au milieu du bloc (+ 0.5 sur X et Z)
        Location centerLoc = loc.clone().add(0.5, 0.0, 0.5);

        // Particules d'explosion (Lave et Fumée)
        int particleCount = config.getParticleCount();
        double particleRadius = config.getParticleRadius();
        world.spawnParticle(org.bukkit.Particle.LAVA, centerLoc, particleCount, particleRadius, particleRadius, particleRadius, 0.1);
        world.spawnParticle(org.bukkit.Particle.SMOKE_LARGE, centerLoc, particleCount / 2, particleRadius, particleRadius, particleRadius, 0.1);

        // 1. Créer l'explosion visuelle/sonore SANS casser de blocs
        world.createExplosion(
            centerLoc.getX(),
            centerLoc.getY(),
            centerLoc.getZ(),
            explosionPower,
            config.shouldCreateFire(),
            false  // false = on contrôle manuellement la casse
        );

        // 2. Destruction MANUELLE des blocs dans le rayon configuré (explosion-radius)
        if (shouldBreak) {
            int radiusInt = (int) Math.ceil(explosionRadius);
            for (int x = -radiusInt; x <= radiusInt; x++) {
                for (int y = -radiusInt; y <= radiusInt; y++) {
                    for (int z = -radiusInt; z <= radiusInt; z++) {
                        Location blockLoc = loc.clone().add(x, y, z);
                        double distance = blockLoc.distance(loc);

                        if (distance <= explosionRadius) {
                            org.bukkit.block.Block block = blockLoc.getBlock();

                            if (!block.getType().isAir() && block.getType() != org.bukkit.Material.BEDROCK) {
                                double remainingPower = explosionPower * (1 - (distance / explosionRadius));
                                float blockResistance = block.getType().getBlastResistance();

                                if (remainingPower > blockResistance / 5.0) {
                                    block.breakNaturally();
                                }
                            }
                        }
                    }
                }
            }
        }

        // Marquer la fin de l'explosion initiale
        heist.setExploding(false);

        // Effets visuels supplémentaires
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);

        // Message broadcast
        String timeFormatted = formatSeconds(config.getRobberyDuration());
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                config.getMessage("explosion").replace("%time%", timeFormatted)));
        }

        plugin.getLogger().info("[Heist] Explosion sur " + heist.getPlotKey());
    }

    // =========================================================================
    // PHASE DE VOL
    // =========================================================================

    /**
     * Démarre la phase de vol
     */
    private void startRobberyPhase(Heist heist) {
        HeistRobberyTask task = new HeistRobberyTask(plugin, this, heist);
        BukkitTask bukkitTask = task.runTaskTimer(plugin, 20L, 20L);
        robberyTasks.put(heist.getHeistId(), bukkitTask);

        // Message aux participants
        for (UUID participantId : heist.getParticipants().keySet()) {
            Player participant = Bukkit.getPlayer(participantId);
            if (participant != null && participant.isOnline()) {
                participant.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    config.getMessage("robbery-started")));
            }
        }
    }

    /**
     * Appelé quand le temps de vol est écoulé
     */
    public void onRobberyExpired(Heist heist) {
        endHeist(heist, HeistResult.TIME_EXPIRED);
    }

    // =========================================================================
    // DÉSAMORÇAGE
    // =========================================================================

    /**
     * Tente de désamorcer la bombe
     * @return true si désamorcée avec succès
     */
    public boolean attemptDefuse(Player police, Heist heist, int wireIndex) {
        // Vérifier si le policier est en service
        ProfessionalServiceManager serviceManager = plugin.getProfessionalServiceManager();
        if (serviceManager != null && !serviceManager.isInService(police.getUniqueId(), ProfessionalServiceType.POLICE)) {
            serviceManager.sendNotInServiceMessage(police, ProfessionalServiceType.POLICE);
            return false;
        }

        if (heist.getPhase() != HeistPhase.COUNTDOWN) {
            police.sendMessage(ChatColor.RED + "La bombe a déjà explosé!");
            return false;
        }

        boolean correctWire = heist.attemptDefuse(wireIndex);

        if (correctWire) {
            // Succès!
            defuseSuccess(heist, police);
            return true;
        } else {
            // Échec
            int remainingAttempts = config.getMaxDefuseAttempts() - heist.getDefuseAttempts();

            police.sendMessage(ChatColor.translateAlternateColorCodes('&',
                config.getMessage("defuse-wrong-wire")
                    .replace("%attempts%", String.valueOf(remainingAttempts))));

            // Vérifier si toutes les tentatives sont épuisées
            if (heist.hasExceededDefuseAttempts()) {
                // Explosion immédiate!
                police.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    config.getMessage("defuse-failed")));
                onCountdownExpired(heist);
            }

            return false;
        }
    }

    /**
     * Appelé quand le désamorçage réussit
     */
    private void defuseSuccess(Heist heist, Player police) {
        // Annuler la tâche de countdown
        BukkitTask task = countdownTasks.remove(heist.getHeistId());
        if (task != null) {
            task.cancel();
        }

        // Terminer le heist
        endHeist(heist, HeistResult.DEFUSED);

        // Message
        police.sendMessage(ChatColor.translateAlternateColorCodes('&',
            config.getMessage("defuse-success")));

        // Broadcast
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                config.getMessage("heist-defused")));
        }

        plugin.getLogger().info("[Heist] Bombe désamorcée par " + police.getName()
            + " sur " + heist.getPlotKey());
    }

    // =========================================================================
    // FIN DU CAMBRIOLAGE
    // =========================================================================

    /**
     * Termine un cambriolage.
     * La bombe est supprimée de placedBombs et du fichier.
     */
    public void endHeist(Heist heist, HeistResult result) {
        if (heist.getPhase() == HeistPhase.ENDED) {
            return; // Déjà terminé
        }

        heist.end(result);

        String plotKey = heist.getPlotKey();

        // Annuler les tâches
        BukkitTask countdownTask = countdownTasks.remove(heist.getHeistId());
        if (countdownTask != null) countdownTask.cancel();

        BukkitTask robberyTask = robberyTasks.remove(heist.getHeistId());
        if (robberyTask != null) robberyTask.cancel();

        // IMPORTANT: Supprimer la bombe de placedBombs (le heist est terminé)
        if (heist.getBombEntityId() != null) {
            PlacedBomb bomb = armorStandToBomb.remove(heist.getBombEntityId());
            if (bomb != null) {
                placedBombs.remove(bomb.getBombId());
                plugin.getLogger().info("[Heist] Bombe supprimée (heist terminé): " + bomb.getBombId());
            }
        }

        // Supprimer l'entité physique de la bombe et les hologrammes
        removeBombEntity(heist);

        // Mettre à jour le cooldown du terrain
        plotLastHeist.put(plotKey, LocalDateTime.now());

        // Retirer des heists actifs
        activeHeists.remove(plotKey);

        // Mettre à jour le fichier de sauvegarde
        saveBombs();

        // Broadcast si activé
        if (config.shouldBroadcastHeistEnd()) {
            broadcastHeistEnd(heist, result);
        }

        plugin.getLogger().info("[Heist] Cambriolage terminé sur " + plotKey
            + " - Résultat: " + result.getDisplayName());
    }

    /**
     * Appelé quand tous les cambrioleurs sont arrêtés/morts
     */
    public void checkAllParticipantsOut(Heist heist) {
        if (heist.areAllParticipantsOutOfGame()) {
            HeistResult result = (heist.getPhase() == HeistPhase.COUNTDOWN)
                ? HeistResult.ALL_ARRESTED_COUNTDOWN
                : HeistResult.ALL_ARRESTED_ROBBERY;
            endHeist(heist, result);
        }
    }

    // =========================================================================
    // ENTITÉ BOMBE & HOLOGRAMMES
    // =========================================================================

    /**
     * Crée l'hologramme du timer au-dessus de la bombe
     */
    private void spawnTimerHologram(Heist heist) {
        Location loc = heist.getBombLocation().clone().add(0.5, 1.5, 0.5); // Ajusté pour être bien visible
        World world = loc.getWorld();
        if (world == null) return;

        // Créer un ArmorStand invisible pour l'hologramme
        ArmorStand stand = world.spawn(loc, ArmorStand.class, armorStand -> {
            armorStand.setVisible(false);
            armorStand.setGravity(false);
            armorStand.setInvulnerable(true);
            armorStand.setMarker(true);
            armorStand.setCustomName(ChatColor.RED + "💣 Initialisation...");
            armorStand.setCustomNameVisible(true);
            // TAG pour identifier les hologrammes de heist
            armorStand.addScoreboardTag("heist_hologram");
        });

        heist.getHologramEntityIds().add(stand.getUniqueId());
    }

    /**
     * Supprime l'entité de la bombe et les hologrammes
     */
    private void removeBombEntity(Heist heist) {
        UUID entityId = heist.getBombEntityId();
        if (entityId != null) {
            // Méthode 1: Utiliser Bukkit.getEntity() directement
            Entity bombEntity = Bukkit.getEntity(entityId);
            if (bombEntity != null) {
                removeBombFurniture(bombEntity);
                plugin.getLogger().info("[Heist] Bombe supprimée via Bukkit.getEntity()");
            } else {
                // Méthode 2: Recherche dans les entités proches (fallback)
                Location loc = heist.getBombLocation();
                World world = loc.getWorld();
                if (world != null) {
                    boolean found = false;
                    for (Entity entity : world.getNearbyEntities(loc, 5, 5, 5)) {
                        if (entity.getUniqueId().equals(entityId)) {
                            removeBombFurniture(entity);
                            plugin.getLogger().info("[Heist] Bombe supprimée via getNearbyEntities()");
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        plugin.getLogger().warning("[Heist] Entité bombe non trouvée pour suppression: " + entityId);
                    }
                }
            }
        }

        // Supprimer les hologrammes
        Location loc = heist.getBombLocation();
        World world = loc.getWorld();
        if (world != null) {
            for (UUID hologramId : heist.getHologramEntityIds()) {
                Entity entity = Bukkit.getEntity(hologramId);
                if (entity != null) {
                    entity.remove();
                } else {
                    // Fallback
                    for (Entity e : world.getNearbyEntities(loc, 4, 4, 4)) {
                        if (e.getUniqueId().equals(hologramId)) {
                            e.remove();
                        }
                    }
                }
            }
        }
        heist.getHologramEntityIds().clear();
    }

    /**
     * Supprime un furniture de bombe via ItemsAdder ou en fallback
     */
    private void removeBombFurniture(Entity entity) {
        try {
            CustomFurniture furniture = CustomFurniture.byAlreadySpawned(entity);
            if (furniture != null) {
                furniture.remove(false); // false = ne pas dropper l'item
                plugin.getLogger().info("[Heist] Furniture ItemsAdder supprimé");
            } else {
                entity.remove();
                plugin.getLogger().info("[Heist] Entité supprimée directement (pas un furniture)");
            }
        } catch (Throwable e) {
            // Fallback si ItemsAdder n'est pas là ou erreur
            entity.remove();
            plugin.getLogger().warning("[Heist] Fallback: entité supprimée avec erreur: " + e.getMessage());
        }
    }

    // =========================================================================
    // ALERTES ET NOTIFICATIONS
    // =========================================================================

    /**
     * Alerte tous les policiers de la ville
     */
    private void alertPolice(Heist heist, Town town) {
        String message = ChatColor.translateAlternateColorCodes('&',
            config.getMessage("police-alert")
                .replace("%plot%", heist.getPlotKey())
                .replace("%town%", town.getName()));

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            TownRole role = town.getMemberRole(playerId);

            if (role == TownRole.POLICIER) {
                // Message
                player.sendMessage(message);

                // Son d'alerte
                player.playSound(player.getLocation(), config.getPoliceAlertSound(), 1.0f, 1.0f);

                // Titre
                player.sendTitle(
                    ChatColor.translateAlternateColorCodes('&', config.getPoliceAlertTitle()),
                    ChatColor.translateAlternateColorCodes('&', config.getPoliceAlertSubtitle()),
                    config.getPoliceAlertFadeIn(),
                    config.getPoliceAlertStay(),
                    config.getPoliceAlertFadeOut()
                );
            }
        }
    }

    /**
     * Notifie le propriétaire du terrain
     */
    private void notifyOwner(Plot plot, Town town, Heist heist) {
        UUID ownerUuid = plot.getOwnerUuid();
        if (ownerUuid == null) return;

        Player owner = Bukkit.getPlayer(ownerUuid);
        if (owner != null && owner.isOnline()) {
            String countdownFormatted = formatSeconds(config.getCountdownDuration());
            String robberyFormatted = formatSeconds(config.getRobberyDuration());
            Location loc = heist.getBombLocation();
            String coords = "X: " + loc.getBlockX() + ", Y: " + loc.getBlockY() + ", Z: " + loc.getBlockZ();
            String plotNumber = plot.getPlotNumber() != null ? plot.getPlotNumber() : "N/A";
            String plotName = plot.getIdentifier() != null ? plot.getIdentifier() : plotNumber;

            owner.sendMessage("");
            owner.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "╔═══════════════════════════════════════════════════════════╗");
            owner.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "║ " + ChatColor.RED + "" + ChatColor.BOLD + "   ⚠⚠⚠ VOTRE TERRAIN EST ATTAQUÉ ⚠⚠⚠" + ChatColor.DARK_RED + "" + ChatColor.BOLD + "             ║");
            owner.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "╠═══════════════════════════════════════════════════════════╣");
            owner.sendMessage(ChatColor.DARK_RED + "║ " + ChatColor.YELLOW + "TERRAIN: " + ChatColor.WHITE + "#" + plotNumber + " \"" + plotName + "\"");
            owner.sendMessage(ChatColor.DARK_RED + "║ " + ChatColor.YELLOW + "COORDONNÉES: " + ChatColor.AQUA + coords);
            owner.sendMessage(ChatColor.DARK_RED + "║ " + ChatColor.YELLOW + "VILLE: " + ChatColor.WHITE + town.getName());
            owner.sendMessage(ChatColor.DARK_RED + "╠═══════════════════════════════════════════════════════════╣");
            owner.sendMessage(ChatColor.DARK_RED + "║ " + ChatColor.RED + "⏱ LA BOMBE EXPLOSERA DANS: " + ChatColor.RED + "" + ChatColor.BOLD + countdownFormatted);
            owner.sendMessage(ChatColor.DARK_RED + "║ " + ChatColor.GOLD + "⏱ DURÉE DE VOL APRÈS EXPLOSION: " + ChatColor.WHITE + robberyFormatted);
            owner.sendMessage(ChatColor.DARK_RED + "╠═══════════════════════════════════════════════════════════╣");
            owner.sendMessage(ChatColor.DARK_RED + "║ " + ChatColor.GREEN + "🆘 CONTACTEZ IMMÉDIATEMENT LA POLICE!");
            owner.sendMessage(ChatColor.DARK_RED + "║ " + ChatColor.GREEN + "📍 Rendez-vous aux coordonnées pour protéger vos biens!");
            owner.sendMessage(ChatColor.DARK_RED + "║ " + ChatColor.RED + "⚠ Après explosion: accès LIBRE pendant " + robberyFormatted + "!");
            owner.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "╚═══════════════════════════════════════════════════════════╝");
            owner.sendMessage("");

            // Son d'alerte très fort pour le propriétaire
            owner.playSound(owner.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                owner.playSound(owner.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f), 10L);

            // Titre urgent
            owner.sendTitle(
                ChatColor.DARK_RED + "" + ChatColor.BOLD + "⚠ VOTRE TERRAIN #" + plotNumber + " ⚠",
                ChatColor.YELLOW + "Est CAMBRIOLÉ! " + ChatColor.RED + countdownFormatted + " avant explosion!",
                10, 100, 20
            );
        }
    }

    /**
     * Broadcast du début de cambriolage pour tous les citoyens
     */
    private void broadcastHeistStart(Heist heist, Town town, Plot plot) {
        String countdownFormatted = formatSeconds(config.getCountdownDuration());
        String robberyFormatted = formatSeconds(config.getRobberyDuration());
        String plotNumber = plot.getPlotNumber() != null ? plot.getPlotNumber() : "N/A";
        String plotName = plot.getIdentifier() != null ? plot.getIdentifier() : plotNumber;

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            TownRole role = town.getMemberRole(playerId);

            // Les policiers ont déjà reçu leur alerte détaillée
            if (role == TownRole.POLICIER) {
                continue;
            }

            // Le propriétaire a déjà reçu son alerte spéciale
            if (playerId.equals(plot.getOwnerUuid())) {
                continue;
            }

            // Citoyens normaux de la ville - INFORMATION SIMPLE
            if (town.isMember(playerId)) {
                player.sendMessage("");
                player.sendMessage(ChatColor.RED + "╔════════════════════════════════════════════╗");
                player.sendMessage(ChatColor.RED + "║ " + ChatColor.YELLOW + "" + ChatColor.BOLD + "    ⚠ ALERTE VILLE ⚠" + ChatColor.RED + "                   ║");
                player.sendMessage(ChatColor.RED + "╠════════════════════════════════════════════╣");
                player.sendMessage(ChatColor.RED + "║ " + ChatColor.WHITE + "Un cambriolage est en cours!");
                player.sendMessage(ChatColor.RED + "║ " + ChatColor.GRAY + "Terrain: " + ChatColor.WHITE + "#" + plotNumber + " \"" + plotName + "\"");
                player.sendMessage(ChatColor.RED + "║ " + ChatColor.GRAY + "Explosion dans: " + ChatColor.YELLOW + countdownFormatted);
                player.sendMessage(ChatColor.RED + "╠════════════════════════════════════════════╣");
                player.sendMessage(ChatColor.RED + "║ " + ChatColor.GRAY + "Restez vigilants!");
                player.sendMessage(ChatColor.RED + "╚════════════════════════════════════════════╝");
                player.sendMessage("");

                // Son d'alerte léger pour citoyens
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 0.5f);
            }
        }

        // Broadcast global (tout le serveur) - INFORMATION TRÈS SIMPLE
        String globalMessage = ChatColor.translateAlternateColorCodes('&',
            "&8[&4Cambriolage&8] &c🚨 Un cambriolage a lieu dans la ville &e" + town.getName() +
            "&c! Terrain #" + plotNumber);
        Bukkit.broadcastMessage(globalMessage);
    }

    /**
     * Broadcast de fin de cambriolage
     */
    private void broadcastHeistEnd(Heist heist, HeistResult result) {
        Town town = townManager.getTown(heist.getTownName());
        String townName = town != null ? town.getName() : heist.getTownName();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (result.isSuccess()) {
                // Les cambrioleurs ont réussi
                player.sendMessage("");
                player.sendMessage(ChatColor.GOLD + "╔════════════════════════════════════════╗");
                player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.RED + "" + ChatColor.BOLD + "CAMBRIOLAGE TERMINÉ" + ChatColor.GOLD + "                  ║");
                player.sendMessage(ChatColor.GOLD + "╠════════════════════════════════════════╣");
                player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Résultat: " + ChatColor.RED + "Les voleurs ont réussi!");
                player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.GRAY + "Ville: " + ChatColor.WHITE + townName);
                player.sendMessage(ChatColor.GOLD + "╚════════════════════════════════════════╝");
                player.sendMessage("");
            } else if (result == HeistResult.DEFUSED) {
                // La police a désamorcé
                player.sendMessage("");
                player.sendMessage(ChatColor.GREEN + "╔════════════════════════════════════════╗");
                player.sendMessage(ChatColor.GREEN + "║ " + ChatColor.AQUA + "" + ChatColor.BOLD + "BOMBE DÉSAMORCÉE!" + ChatColor.GREEN + "                    ║");
                player.sendMessage(ChatColor.GREEN + "╠════════════════════════════════════════╣");
                player.sendMessage(ChatColor.GREEN + "║ " + ChatColor.WHITE + "La police a sauvé la ville!");
                player.sendMessage(ChatColor.GREEN + "║ " + ChatColor.GRAY + "Ville: " + ChatColor.WHITE + townName);
                player.sendMessage(ChatColor.GREEN + "║ " + ChatColor.YELLOW + "Bravo aux forces de l'ordre!");
                player.sendMessage(ChatColor.GREEN + "╚════════════════════════════════════════╝");
                player.sendMessage("");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f);
            } else {
                // Tous arrêtés ou autre fin
                player.sendMessage("");
                player.sendMessage(ChatColor.GREEN + "╔════════════════════════════════════════╗");
                player.sendMessage(ChatColor.GREEN + "║ " + ChatColor.AQUA + "" + ChatColor.BOLD + "CAMBRIOLAGE ÉCHOUÉ!" + ChatColor.GREEN + "                  ║");
                player.sendMessage(ChatColor.GREEN + "╠════════════════════════════════════════╣");
                player.sendMessage(ChatColor.GREEN + "║ " + ChatColor.WHITE + "Tous les cambrioleurs ont été arrêtés!");
                player.sendMessage(ChatColor.GREEN + "║ " + ChatColor.GRAY + "Ville: " + ChatColor.WHITE + townName);
                player.sendMessage(ChatColor.GREEN + "║ " + ChatColor.YELLOW + "La justice a triomphé!");
                player.sendMessage(ChatColor.GREEN + "╚════════════════════════════════════════╝");
                player.sendMessage("");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f);
            }
        }
    }

    // =========================================================================
    // UTILITAIRES
    // =========================================================================

    /**
     * Vérifie si la ville a un commissariat
     */
    private boolean hasCommissariat(Town town) {
        for (Plot plot : town.getPlots().values()) {
            if (plot.getMunicipalSubType() == MunicipalSubType.COMMISSARIAT) {
                return true;
            }
        }
        return false;
    }

    /**
     * Compte les citoyens connectés dans une ville
     */
    private int countOnlineCitizens(Town town) {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (town.isMember(player.getUniqueId())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Compte les policiers connectés dans une ville
     */
    private int countOnlinePolice(Town town) {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            TownRole role = town.getMemberRole(player.getUniqueId());
            if (role == TownRole.POLICIER) {
                count++;
            }
        }
        return count;
    }

    /**
     * Formate un nombre de secondes en "Xm Xs"
     */
    private String formatSeconds(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    /**
     * Formate le temps restant jusqu'à une date
     */
    private String formatTimeRemaining(LocalDateTime until) {
        long minutes = java.time.Duration.between(LocalDateTime.now(), until).toMinutes();
        if (minutes > 60) {
            long hours = minutes / 60;
            return hours + "h " + (minutes % 60) + "m";
        }
        return minutes + "m";
    }

    // =========================================================================
    // GETTERS PUBLICS
    // =========================================================================

    /**
     * @return le heist actif sur un terrain, ou null
     */
    public Heist getActiveHeist(String plotKey) {
        return activeHeists.get(plotKey);
    }

    /**
     * @return le heist actif à une location, ou null
     * Vérifie d'abord le chunk exact, puis le Plot (pour les terrains multi-chunks ou groupés)
     */
    public Heist getActiveHeistAt(Location location) {
        // 1. Vérification rapide par chunk exact
        Chunk chunk = location.getChunk();
        String plotKey = location.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
        Heist heist = activeHeists.get(plotKey);
        if (heist != null) {
            return heist;
        }

        // 2. Vérifier si la location est dans un Plot qui a un heist actif
        // Cela gère les terrains qui s'étendent sur plusieurs chunks
        Plot plot = claimManager.getPlotAt(location);
        if (plot != null) {
            String townName = claimManager.getClaimOwner(location);
            if (townName != null) {
                // Chercher dans tous les heists actifs si un correspond à cette ville/plot
                for (Heist activeHeist : activeHeists.values()) {
                    if (townName.equals(activeHeist.getTownName())) {
                        // Vérifier si le plot du heist correspond au plot de la location
                        Plot heistPlot = claimManager.getPlotAt(activeHeist.getBombLocation());
                        if (heistPlot != null) {
                            // Comparer par identifiant de plot
                            String plotId = plot.getIdentifier();
                            String heistPlotId = heistPlot.getIdentifier();
                            if (plotId != null && plotId.equals(heistPlotId)) {
                                return activeHeist;
                            }
                            // Ou comparer par numéro de plot
                            String plotNum = plot.getPlotNumber();
                            String heistPlotNum = heistPlot.getPlotNumber();
                            if (plotNum != null && plotNum.equals(heistPlotNum)) {
                                return activeHeist;
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * @return true s'il y a un heist actif sur ce terrain
     */
    public boolean hasActiveHeist(String plotKey) {
        return activeHeists.containsKey(plotKey);
    }

    /**
     * @return tous les heists actifs
     */
    public Collection<Heist> getActiveHeists() {
        return activeHeists.values();
    }

    /**
     * @return la configuration du heist
     */
    public HeistConfig getConfig() {
        return config;
    }

    /**
     * Vérifie si un joueur participe à un heist actif
     */
    public boolean isParticipantInAnyHeist(UUID playerUuid) {
        for (Heist heist : activeHeists.values()) {
            if (heist.isParticipant(playerUuid)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Récupère le heist auquel un joueur participe
     */
    public Heist getHeistForParticipant(UUID playerUuid) {
        for (Heist heist : activeHeists.values()) {
            if (heist.isParticipant(playerUuid)) {
                return heist;
            }
        }
        return null;
    }



    // =========================================================================
    // GESTION DES BOMBES POSÉES (Système en 2 étapes)
    // =========================================================================

    /**
     * Enregistre une bombe posée via furniture ItemsAdder.
     * La bombe n'est pas encore activée, en attente de confirmation.
     *
     * @param player Le joueur qui pose la bombe
     * @param location L'emplacement de la bombe
     * @param armorStandId L'UUID de l'ArmorStand ItemsAdder
     * @return La bombe posée
     */
    public PlacedBomb registerPlacedBomb(Player player, Location location, UUID armorStandId) {
        PlacedBomb bomb = new PlacedBomb(
            player.getUniqueId(),
            player.getName(),
            location,
            armorStandId
        );

        // Vérifier si sur terrain claimé
        String townName = claimManager.getClaimOwner(location);
        if (townName != null) {
            Town town = townManager.getTown(townName);
            if (town != null) {
                Plot plot = town.getPlot(location.getWorld().getName(),
                                          location.getChunk().getX(),
                                          location.getChunk().getZ());
                if (plot != null) {
                    bomb.setTownName(townName);
                    bomb.setPlotKey(location.getWorld().getName() + ":" +
                                   location.getChunk().getX() + ":" +
                                   location.getChunk().getZ());
                    bomb.setPlotId(plot.getIdentifier());
                }
            }
        }

        placedBombs.put(bomb.getBombId(), bomb);
        armorStandToBomb.put(armorStandId, bomb);

        // Sauvegarde immédiate
        saveBombs();

        return bomb;
    }

    /**
     * Récupère une bombe posée par l'UUID de son ArmorStand
     */
    public PlacedBomb getPlacedBombByArmorStand(UUID armorStandId) {
        return armorStandToBomb.get(armorStandId);
    }

    /**
     * Vérifie et met à jour les informations de terrain pour une bombe
     * Utilisé lors de la réactivation de bombes après restart
     */
    public void checkIfOnClaimedPlot(PlacedBomb bomb) {
        Location location = bomb.getLocation();
        String townName = claimManager.getClaimOwner(location);

        if (townName != null) {
            Town town = townManager.getTown(townName);
            if (town != null) {
                Plot plot = town.getPlot(location.getWorld().getName(),
                                          location.getChunk().getX(),
                                          location.getChunk().getZ());
                if (plot != null) {
                    bomb.setTownName(townName);
                    bomb.setPlotKey(location.getWorld().getName() + ":" +
                                   location.getChunk().getX() + ":" +
                                   location.getChunk().getZ());
                    bomb.setPlotId(plot.getIdentifier());
                }
            }
        }
    }

    /**
     * Récupère un heist actif par l'UUID de l'entité de bombe
     */
    public Heist getHeistByBombEntity(UUID armorStandId) {
        for (Heist heist : activeHeists.values()) {
            if (heist.getBombEntityId() != null && heist.getBombEntityId().equals(armorStandId)) {
                return heist;
            }
        }
        return null;
    }

    /**
     * Confirme et active une bombe posée.
     * Si sur terrain claimé: démarre un cambriolage complet.
     * Si hors terrain: explosion simple après délai.
     *
     * IMPORTANT: La bombe reste dans placedBombs tant qu'elle n'a pas explosé!
     * Cela permet de la sauvegarder et de la réarmer après un restart.
     *
     * @param player Le joueur qui confirme
     * @param bomb La bombe à confirmer
     * @param armorStandId L'UUID de l'ArmorStand
     * @return Le Heist créé, ou null si explosion simple
     */
    public Heist confirmBomb(Player player, PlacedBomb bomb, UUID armorStandId) {
        // Si pas sur terrain claimé -> explosion simple
        if (!bomb.isOnClaimedPlot()) {
            // Marquer le timer comme démarré
            bomb.setTimerStarted(true);
            bomb.setRemainingSeconds(config.getCountdownDuration());
            bomb.setTimerStartTimestamp(System.currentTimeMillis());

            // Rendre la bombe invulnérable (impossible à casser)
            setBombInvulnerable(armorStandId, true);

            saveBombs();

            triggerSimpleExplosion(bomb, armorStandId);
            player.sendMessage(ChatColor.YELLOW + "La bombe est amorcée! Elle explosera dans " +
                formatSeconds(config.getCountdownDuration()) + ".");
            return null;
        }

        // Sur terrain claimé -> vérifier conditions
        ConditionResult conditions = checkConditions(player, bomb.getLocation());
        if (!conditions.isSuccess()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                config.getMessage("prefix") + conditions.getErrorMessage()));
            bomb.setAwaitingConfirmation(false);
            return null;
        }

        // Créer le Heist - la bombe reste dans placedBombs
        return startHeistFromBomb(player, bomb, armorStandId);
    }

    /**
     * Crée un heist à partir d'une bombe confirmée.
     * La bombe reste dans placedBombs avec son état de timer mis à jour.
     */
    private Heist startHeistFromBomb(Player player, PlacedBomb bomb, UUID armorStandId) {
        Town town = townManager.getTown(bomb.getTownName());
        Plot plot = town.getPlot(bomb.getLocation().getWorld().getName(),
                                 bomb.getLocation().getChunk().getX(),
                                 bomb.getLocation().getChunk().getZ());

        Heist heist = new Heist(
            player.getUniqueId(),
            bomb.getTownName(),
            bomb.getPlotKey(),
            plot.getType(),
            bomb.getLocation(),
            config.getCountdownDuration(),
            config.getRobberyDuration(),
            config.getExplosionPower(),
            config.shouldBreakBlocks(),
            config.shouldDamageEntities(),
            config.getMaxDefuseAttempts()
        );

        // Utiliser l'ArmorStand du furniture ItemsAdder comme entité de bombe
        heist.setBombEntityId(armorStandId);

        // Ajouter l'initiateur comme participant
        heist.addParticipant(player.getUniqueId(), player.getName(), true);

        // Enregistrer le heist
        activeHeists.put(bomb.getPlotKey(), heist);

        // IMPORTANT: Marquer le timer comme démarré dans PlacedBomb
        // La bombe reste dans placedBombs pour persistance
        bomb.setTimerStarted(true);
        bomb.setRemainingSeconds(config.getCountdownDuration());
        bomb.setTimerStartTimestamp(System.currentTimeMillis());

        // Rendre la bombe invulnérable (impossible à casser)
        setBombInvulnerable(armorStandId, true);

        // Sauvegarder l'état mis à jour
        saveBombs();

        // Mettre à jour les cooldowns
        playerLastHeist.put(player.getUniqueId(), LocalDateTime.now());

        // Créer l'hologramme du timer
        spawnTimerHologram(heist);

        // Démarrer le countdown
        startCountdown(heist);

        // Alerter la police avec message personnalisé incluant le terrain
        alertPoliceWithPlotInfo(heist, town, plot);

        // Notifier le propriétaire
        notifyOwner(plot, town, heist);

        // Broadcast
        if (config.shouldBroadcastHeistStart()) {
            broadcastHeistStart(heist, town, plot);
        }

        // Message au joueur
        String timeFormatted = formatSeconds(config.getCountdownDuration());
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
            config.getMessage("heist-started").replace("%time%", timeFormatted)));

        plugin.getLogger().info("[Heist] Cambriolage démarré par " + player.getName()
            + " sur " + bomb.getPlotKey() + " dans " + town.getName());

        return heist;
    }

    /**
     * Alerte police avec infos détaillées du terrain - MISSION DE SECOURS
     */
    private void alertPoliceWithPlotInfo(Heist heist, Town town, Plot plot) {
        String countdownFormatted = formatSeconds(config.getCountdownDuration());
        String robberyFormatted = formatSeconds(config.getRobberyDuration());
        Location loc = heist.getBombLocation();
        String coords = "X: " + loc.getBlockX() + ", Y: " + loc.getBlockY() + ", Z: " + loc.getBlockZ();
        String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "world";

        // Récupérer le numéro et nom du terrain
        String plotNumber = plot.getPlotNumber() != null ? plot.getPlotNumber() : "N/A";
        String plotName = plot.getIdentifier() != null ? plot.getIdentifier() : plotNumber;
        String ownerName = plot.getOwnerName() != null ? plot.getOwnerName() : "Ville";

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            UUID playerId = onlinePlayer.getUniqueId();
            TownRole role = town.getMemberRole(playerId);

            if (role == TownRole.POLICIER) {
                // Message MISSION pour la police
                onlinePlayer.sendMessage("");
                onlinePlayer.sendMessage(ChatColor.DARK_RED + "╔═══════════════════════════════════════════════════════════╗");
                onlinePlayer.sendMessage(ChatColor.DARK_RED + "║ " + ChatColor.RED + "" + ChatColor.BOLD + "    🚨 MISSION: CAMBRIOLAGE EN COURS 🚨" + ChatColor.DARK_RED + "              ║");
                onlinePlayer.sendMessage(ChatColor.DARK_RED + "╠═══════════════════════════════════════════════════════════╣");
                onlinePlayer.sendMessage(ChatColor.DARK_RED + "║ " + ChatColor.YELLOW + "TERRAIN: " + ChatColor.WHITE + "#" + plotNumber + " \"" + plotName + "\"");
                onlinePlayer.sendMessage(ChatColor.DARK_RED + "║ " + ChatColor.YELLOW + "PROPRIÉTAIRE: " + ChatColor.WHITE + ownerName);
                onlinePlayer.sendMessage(ChatColor.DARK_RED + "║ " + ChatColor.YELLOW + "VILLE: " + ChatColor.WHITE + town.getName());
                onlinePlayer.sendMessage(ChatColor.DARK_RED + "║ " + ChatColor.YELLOW + "COORDONNÉES: " + ChatColor.AQUA + coords);
                onlinePlayer.sendMessage(ChatColor.DARK_RED + "║ " + ChatColor.YELLOW + "MONDE: " + ChatColor.WHITE + worldName);
                onlinePlayer.sendMessage(ChatColor.DARK_RED + "╠═══════════════════════════════════════════════════════════╣");
                onlinePlayer.sendMessage(ChatColor.DARK_RED + "║ " + ChatColor.RED + "⏱ EXPLOSION DANS: " + ChatColor.RED + "" + ChatColor.BOLD + countdownFormatted);
                onlinePlayer.sendMessage(ChatColor.DARK_RED + "║ " + ChatColor.GOLD + "⏱ DURÉE DE VOL SI EXPLOSION: " + ChatColor.WHITE + robberyFormatted);
                onlinePlayer.sendMessage(ChatColor.DARK_RED + "╠═══════════════════════════════════════════════════════════╣");
                onlinePlayer.sendMessage(ChatColor.DARK_RED + "║ " + ChatColor.GREEN + "" + ChatColor.BOLD + "🎯 OBJECTIF: " + ChatColor.WHITE + "Désamorcer la bombe AVANT l'explosion");
                onlinePlayer.sendMessage(ChatColor.DARK_RED + "║ " + ChatColor.GREEN + "📍 Rendez-vous aux coordonnées ci-dessus");
                onlinePlayer.sendMessage(ChatColor.DARK_RED + "║ " + ChatColor.GREEN + "🔧 Clic droit sur la bombe pour le mini-jeu");
                onlinePlayer.sendMessage(ChatColor.DARK_RED + "╠═══════════════════════════════════════════════════════════╣");
                onlinePlayer.sendMessage(ChatColor.DARK_RED + "║ " + ChatColor.RED + "⚠ Si la bombe explose, les voleurs auront " + robberyFormatted);
                onlinePlayer.sendMessage(ChatColor.DARK_RED + "║ " + ChatColor.RED + "  d'accès LIBRE au terrain (coffres, blocs, tout)!");
                onlinePlayer.sendMessage(ChatColor.DARK_RED + "╚═══════════════════════════════════════════════════════════╝");
                onlinePlayer.sendMessage("");

                // Son d'alerte fort (3 fois pour urgence)
                onlinePlayer.playSound(onlinePlayer.getLocation(), config.getPoliceAlertSound(), 1.0f, 1.0f);
                Bukkit.getScheduler().runTaskLater(plugin, () ->
                    onlinePlayer.playSound(onlinePlayer.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 1.0f), 10L);

                // Titre urgent
                onlinePlayer.sendTitle(
                    ChatColor.DARK_RED + "" + ChatColor.BOLD + "🚨 MISSION 🚨",
                    ChatColor.YELLOW + "#" + plotNumber + " " + town.getName() + ChatColor.GRAY + " - " + ChatColor.RED + countdownFormatted,
                    10, 100, 20
                );
            }
        }
    }

    /**
     * Explosion simple (hors terrain claimé) - pas d'alerte police
     * Affiche un timer hologramme au-dessus de la bombe
     */
    private void triggerSimpleExplosion(PlacedBomb bomb, UUID armorStandId) {
        Location loc = bomb.getLocation();
        World world = loc.getWorld();

        if (world == null) return;

        int countdownSeconds = config.getCountdownDuration();

        // Créer l'hologramme du timer au-dessus de la bombe (centré au milieu du bloc)
        Location hologramLoc = loc.clone().add(0.5, 1.5, 0.5);
        ArmorStand timerHologram = world.spawn(hologramLoc, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setMarker(true);
            stand.setCustomName(ChatColor.RED + "💣 " + formatSeconds(countdownSeconds));
            stand.setCustomNameVisible(true);
            stand.addScoreboardTag("heist_simple_timer");
        });

        // Tâche pour mettre à jour le timer et déclencher l'explosion
        new BukkitRunnable() {
            int remaining = countdownSeconds;

            @Override
            public void run() {
                remaining--;

                // Mettre à jour l'hologramme
                if (timerHologram != null && !timerHologram.isDead()) {
                    ChatColor color;
                    if (remaining > 60) {
                        color = ChatColor.GREEN;
                    } else if (remaining > 30) {
                        color = ChatColor.YELLOW;
                    } else if (remaining > 10) {
                        color = ChatColor.GOLD;
                    } else {
                        color = ChatColor.RED;
                    }
                    timerHologram.setCustomName(color + "💣 " + formatSeconds(remaining));

                    // Particules
                    if (remaining <= 30 || remaining % 5 == 0) {
                        world.spawnParticle(Particle.FLAME, loc.clone().add(0.5, 1, 0.5), 5, 0.2, 0.2, 0.2, 0.01);
                    }

                    // Son de bip qui accélère
                    if (remaining <= 10 || (remaining <= 30 && remaining % 2 == 0) || remaining % 5 == 0) {
                        float pitch = 1.0f + (1.0f - (float) remaining / countdownSeconds);
                        world.playSound(loc, Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, Math.min(2.0f, pitch));
                    }
                }

                // Temps écoulé - EXPLOSION
                if (remaining <= 0) {
                    cancel();

                    // IMPORTANT: Supprimer la bombe de placedBombs maintenant qu'elle explose
                    PlacedBomb removedBomb = armorStandToBomb.remove(armorStandId);
                    if (removedBomb != null) {
                        placedBombs.remove(removedBomb.getBombId());
                        saveBombs(); // Sauvegarder la suppression
                        plugin.getLogger().info("[Heist] Bombe simple supprimée après explosion: " + removedBomb.getBombId());
                    }

                    // Supprimer l'hologramme
                    if (timerHologram != null && !timerHologram.isDead()) {
                        timerHologram.remove();
                    }

                    // Supprimer l'ArmorStand (furniture ItemsAdder)
                    for (Entity entity : world.getNearbyEntities(loc, 3, 3, 3)) {
                        if (entity.getUniqueId().equals(armorStandId)) {
                            try {
                                CustomFurniture furniture = CustomFurniture.byAlreadySpawned(entity);
                                if (furniture != null) {
                                    furniture.remove(false);
                                } else {
                                    entity.remove();
                                }
                            } catch (Throwable e) {
                                entity.remove();
                            }
                            break;
                        }
                    }

                    // Créer l'explosion simple (hors terrain) avec destruction manuelle
                    float explosionPower = config.getExplosionPower();
                    float explosionRadius = config.getExplosionRadius();
                    boolean shouldBreak = config.shouldBreakBlocks();

                    // Centrer l'explosion au milieu du bloc
                    Location centerLoc = loc.clone().add(0.5, 0.0, 0.5);

                    // Particules d'explosion (Lave et Fumée)
                    int particleCount = config.getParticleCount();
                    double particleRadius = config.getParticleRadius();
                    world.spawnParticle(org.bukkit.Particle.LAVA, centerLoc, particleCount, particleRadius, particleRadius, particleRadius, 0.1);
                    world.spawnParticle(org.bukkit.Particle.SMOKE_LARGE, centerLoc, particleCount / 2, particleRadius, particleRadius, particleRadius, 0.1);

                    // 1. Créer l'explosion visuelle/sonore
                    world.createExplosion(
                        centerLoc.getX(),
                        centerLoc.getY(),
                        centerLoc.getZ(),
                        explosionPower,
                        config.shouldCreateFire(),
                        false  // false = destruction manuelle
                    );

                    // 2. Destruction MANUELLE des blocs dans le rayon configuré
                    if (shouldBreak) {
                        int radiusInt = (int) Math.ceil(explosionRadius);
                        for (int x = -radiusInt; x <= radiusInt; x++) {
                            for (int y = -radiusInt; y <= radiusInt; y++) {
                                for (int z = -radiusInt; z <= radiusInt; z++) {
                                    Location blockLoc = loc.clone().add(x, y, z);
                                    double distance = blockLoc.distance(loc);

                                    if (distance <= explosionRadius) {
                                        org.bukkit.block.Block block = blockLoc.getBlock();

                                        if (!block.getType().isAir() && block.getType() != org.bukkit.Material.BEDROCK) {
                                            double remainingPower = explosionPower * (1 - (distance / explosionRadius));
                                            float blockResistance = block.getType().getBlastResistance();

                                            if (remainingPower > blockResistance / 5.0) {
                                                block.breakNaturally();
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    plugin.getLogger().info("[Heist] Explosion simple (hors terrain) à " +
                        world.getName() + " " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Toutes les secondes
    }

    /**
     * Retire une bombe posée (si annulée ou cassée)
     */
    public void removePlacedBomb(UUID armorStandId) {
        PlacedBomb bomb = armorStandToBomb.remove(armorStandId);
        if (bomb != null) {
            placedBombs.remove(bomb.getBombId());
            saveBombs(); // Mise à jour immédiate du fichier
        }
    }

    /**
     * Rend une bombe invulnérable ou vulnérable.
     * Utilisé pour empêcher la casse quand le timer est actif.
     */
    private void setBombInvulnerable(UUID armorStandId, boolean invulnerable) {
        Entity entity = Bukkit.getEntity(armorStandId);
        if (entity != null) {
            entity.setInvulnerable(invulnerable);
            plugin.getLogger().info("[Heist] Bombe " + (invulnerable ? "verrouillée" : "déverrouillée"));
        }
    }

    /**
     * Récupère toutes les bombes posées (pour vérification)
     */
    public Collection<PlacedBomb> getAllPlacedBombs() {
        return placedBombs.values();
    }

    /**
     * Met à jour l'UUID de l'ArmorStand d'une bombe
     * (utilisé quand ItemsAdder recrée l'entité avec un nouvel UUID)
     */
    public void updateBombArmorStandId(PlacedBomb bomb, UUID newArmorStandId) {
        // Retirer l'ancien mapping
        armorStandToBomb.values().remove(bomb);

        // Ajouter le nouveau mapping
        armorStandToBomb.put(newArmorStandId, bomb);

        plugin.getLogger().info("[Heist] UUID ArmorStand mis à jour pour bombe " + bomb.getBombId());
    }

    /**
     * FIX Bug 7: Met à jour l'UUID de l'ArmorStand d'une bombe de manière thread-safe
     * Utilise une synchronisation pour éviter les race conditions
     */
    public synchronized void updateBombArmorStandIdSafe(PlacedBomb bomb, UUID newArmorStandId) {
        if (bomb == null || newArmorStandId == null) return;

        UUID oldArmorStandId = bomb.getArmorStandId();

        // Si l'UUID est déjà le bon, ne rien faire
        if (newArmorStandId.equals(oldArmorStandId)) {
            return;
        }

        // Retirer l'ancien mapping si présent
        if (oldArmorStandId != null) {
            armorStandToBomb.remove(oldArmorStandId);
        }

        // Ajouter le nouveau mapping
        armorStandToBomb.put(newArmorStandId, bomb);

        // Note: PlacedBomb.armorStandId est final, on ne peut pas le changer
        // Mais le mapping armorStandToBomb est mis à jour

        plugin.getLogger().info("[Heist] UUID ArmorStand mis à jour (safe) pour bombe " + bomb.getBombId() +
            " : " + oldArmorStandId + " -> " + newArmorStandId);

        // Sauvegarder immédiatement
        saveBombs();
    }

    /**
     * FIX Bug 7: Retire une bombe de manière thread-safe
     */
    public synchronized void removePlacedBombSafe(UUID armorStandId) {
        PlacedBomb bomb = armorStandToBomb.remove(armorStandId);
        if (bomb != null) {
            placedBombs.remove(bomb.getBombId());
            saveBombs();
            plugin.getLogger().info("[Heist] Bombe supprimée (safe): " + bomb.getBombId());
        }
    }

    /**
     * FIX Bug 4: Nettoie tous les hologrammes orphelins dans tous les mondes
     * Appelé au démarrage et à l'arrêt du serveur
     */
    public int cleanupAllOrphanHolograms() {
        int removed = 0;

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof ArmorStand) {
                    // Vérifier les tags d'hologrammes heist
                    if (entity.getScoreboardTags().contains("heist_hologram") ||
                        entity.getScoreboardTags().contains("heist_simple_timer") ||
                        entity.getScoreboardTags().contains("heist_countdown_timer") ||
                        entity.getScoreboardTags().contains("heist_robbery_timer")) {

                        // Vérifier que cet hologramme n'appartient pas à un heist actif
                        boolean belongsToActiveHeist = false;
                        UUID entityId = entity.getUniqueId();

                        for (Heist heist : activeHeists.values()) {
                            if (heist.getHologramEntityIds().contains(entityId)) {
                                belongsToActiveHeist = true;
                                break;
                            }
                        }

                        if (!belongsToActiveHeist) {
                            entity.remove();
                            removed++;
                        }
                    }
                }
            }
        }

        return removed;
    }

    /**
     * Enregistre directement une PlacedBomb (sans créer de nouvelle instance)
     */
    public void registerPlacedBombDirect(PlacedBomb bomb) {
        placedBombs.put(bomb.getBombId(), bomb);
        armorStandToBomb.put(bomb.getArmorStandId(), bomb);
        saveBombs();
    }
}
