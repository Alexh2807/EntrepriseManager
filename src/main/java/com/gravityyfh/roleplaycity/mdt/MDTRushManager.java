package com.gravityyfh.roleplaycity.mdt;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.mdt.config.MDTConfig;
import com.gravityyfh.roleplaycity.mdt.data.*;
import com.gravityyfh.roleplaycity.mdt.generator.GeneratorManager;
import com.gravityyfh.roleplaycity.mdt.inventory.InventoryBackupManager;
import com.gravityyfh.roleplaycity.mdt.listener.MDTBedListener;
import com.gravityyfh.roleplaycity.mdt.listener.MDTBlockListener;
import com.gravityyfh.roleplaycity.mdt.listener.MDTInventoryListener;
import com.gravityyfh.roleplaycity.mdt.listener.MDTMerchantProtectionListener;
import com.gravityyfh.roleplaycity.mdt.listener.MDTPlayerListener;
import com.gravityyfh.roleplaycity.mdt.merchant.MDTMerchantManager;
// Import pour le nouveau système FAWE
import com.gravityyfh.roleplaycity.mdt.schematic.MDTSchematicManager;
import com.gravityyfh.roleplaycity.mdt.schematic.MDTRegionManager;
import com.gravityyfh.roleplaycity.mdt.scoreboard.MDTScoreboardManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Gestionnaire principal du mini-jeu MDT Rush
 */
public class MDTRushManager {
    private final RoleplayCity plugin;
    private final MDTConfig config;

    // Sous-managers
    private InventoryBackupManager inventoryBackupManager;
    private GeneratorManager generatorManager;
    private MDTMerchantManager merchantManager;
    private MDTScoreboardManager scoreboardManager;

    // Nouveau système FAWE
    private MDTSchematicManager schematicManager;
    private MDTRegionManager regionManager;

    // Listeners
    private MDTPlayerListener playerListener;
    private MDTBedListener bedListener;
    private MDTBlockListener blockListener;
    private MDTInventoryListener inventoryListener;
    private MDTMerchantProtectionListener merchantProtectionListener;

    // État actuel
    private MDTGame currentGame;

    // Tasks
    private BukkitTask joinTask;
    private BukkitTask lobbyTask;
    private BukkitTask countdownTask;
    private BukkitTask gameTask;

    // Cooldowns et timestamps
    private final Map<UUID, Long> joinCooldowns = new ConcurrentHashMap<>();
    private static final long JOIN_COOLDOWN_MS = 3000;

    public MDTRushManager(RoleplayCity plugin, MDTConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.currentGame = null;

        initialize();
    }

    /**
     * Initialise tous les composants du système MDT
     */
    private void initialize() {
        plugin.getLogger().info("[MDT] Initialisation du système MDT Rush...");

        // Initialiser les sous-managers
        this.inventoryBackupManager = new InventoryBackupManager(plugin);
        this.generatorManager = new GeneratorManager(plugin, config);
        this.merchantManager = new MDTMerchantManager(plugin, config);
        this.scoreboardManager = new MDTScoreboardManager(plugin, this, config);

        // Initialiser le nouveau système FAWE
        this.schematicManager = new MDTSchematicManager(plugin, config);
        this.regionManager = new MDTRegionManager(plugin, config, this, schematicManager);

        // Restaurer les inventaires des joueurs qui auraient quitté pendant une partie
        inventoryBackupManager.restoreAllPendingBackups();

        // Configurer le monde MDT (meteo fixe 3H du matin)
        setupMDTWorld();

        plugin.getLogger().info("[MDT] Système MDT Rush initialisé avec succès!");
    }

    /**
     * Configure le monde MDT avec une heure et meteo fixe
     * Heure fixee a 20000 ticks (2H du matin)
     */
    private void setupMDTWorld() {
        World mdtWorld = config.getWorld();
        if (mdtWorld == null) {
            plugin.getLogger().warning("[MDT] Le monde '" + config.getWorldName() + "' n'existe pas encore.");
            return;
        }

        // 2H du matin = 20000 ticks
        mdtWorld.setTime(20000);

        // Desactiver le cycle jour/nuit
        mdtWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);

        // Desactiver la meteo
        mdtWorld.setStorm(false);
        mdtWorld.setThundering(false);
        mdtWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);

        plugin.getLogger().info("[MDT] Monde '" + config.getWorldName() + "' configure: heure fixe 20000 ticks, meteo desactivee.");
    }

    /**
     * Enregistre les listeners
     */
    public void registerListeners() {
        playerListener = new MDTPlayerListener(plugin, this);
        bedListener = new MDTBedListener(plugin, this);
        blockListener = new MDTBlockListener(plugin, this);
        inventoryListener = new MDTInventoryListener(plugin, this);
        merchantProtectionListener = new MDTMerchantProtectionListener(plugin, this);

        Bukkit.getPluginManager().registerEvents(playerListener, plugin);
        Bukkit.getPluginManager().registerEvents(bedListener, plugin);
        Bukkit.getPluginManager().registerEvents(blockListener, plugin);
        Bukkit.getPluginManager().registerEvents(inventoryListener, plugin);
        Bukkit.getPluginManager().registerEvents(merchantProtectionListener, plugin);
        Bukkit.getPluginManager().registerEvents(regionManager, plugin);

        plugin.getLogger().info("[MDT] Listeners enregistrés.");
    }

    /**
     * Arrête proprement le système MDT
     */
    public void shutdown() {
        plugin.getLogger().info("[MDT] Arrêt du système MDT Rush...");

        // Arrêter la partie en cours si nécessaire
        if (currentGame != null) {
            forceEndGame("Arrêt du serveur");
        }

        // Supprimer le marchand
        if (merchantManager != null) {
            merchantManager.despawnMerchant();
        }

        // Annuler toutes les tasks
        cancelAllTasks();

        plugin.getLogger().info("[MDT] Système MDT Rush arrêté.");
    }

    // ==================== CYCLE DE VIE DE LA PARTIE ====================

    /**
     * Démarre un nouvel event MDT Rush
     */
    public boolean startEvent(Player starter) {
        if (!config.isEnabled()) {
            starter.sendMessage(config.getFormattedMessage("prefix") + ChatColor.RED + "Le MDT Rush est désactivé.");
            return false;
        }

        if (config.getWorld() == null) {
            starter.sendMessage(config.getFormattedMessage("prefix") + ChatColor.RED +
                    "Le monde '" + config.getWorldName() + "' n'existe pas!");
            return false;
        }

        if (currentGame != null) {
            starter.sendMessage(config.getFormattedMessage("prefix") + ChatColor.RED +
                    "Une partie est déjà en cours!");
            return false;
        }

        // Créer une nouvelle partie
        currentGame = new MDTGame();
        currentGame.setState(MDTGameState.JOINING);

        // Charger les lits
        for (MDTBed bed : config.loadTeamBeds()) {
            currentGame.addTeamBed(bed);
        }
        for (MDTBed bed : config.loadNeutralBeds()) {
            currentGame.addNeutralBed(bed);
        }

        // Charger les générateurs
        for (MDTGenerator generator : config.loadGeneratorsFromLocations()) {
            currentGame.addGenerator(generator);
        }

        // Annoncer l'event
        broadcastEventAnnouncement();

        // Démarrer la phase de recrutement (60s pour rejoindre)
        startJoinPhase();

        plugin.getLogger().info("[MDT] Event démarré par " + starter.getName());
        return true;
    }

    /**
     * Démarre la phase de recrutement (JOINING)
     * Les joueurs ont 60 secondes pour rejoindre
     */
    private void startJoinPhase() {
        final int[] timeLeft = {config.getJoinPeriod()};

        joinTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (currentGame == null || currentGame.getState() != MDTGameState.JOINING) {
                cancelTask(joinTask);
                return;
            }

            // Annoncer le temps restant toutes les 15 secondes ou les 10 dernieres secondes
            if (timeLeft[0] <= 10 || timeLeft[0] == 30 || timeLeft[0] == 45 || timeLeft[0] == 60) {
                String countdownMessage = config.getFormattedMessage("join-phase-countdown",
                        "%time%", String.valueOf(timeLeft[0]));

                // Broadcast a tout le serveur
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendMessage(countdownMessage);
                    if (timeLeft[0] <= 5) {
                        p.playSound(p.getLocation(), config.getSound("countdown-tick"), 1.0f, 1.0f);
                    }
                }
            }

            timeLeft[0]--;

            if (timeLeft[0] <= 0) {
                cancelTask(joinTask);

                // Verifier qu'on a assez de joueurs
                if (currentGame.getPlayerCount() < config.getMinTotalPlayers()) {
                    String notEnoughMsg = config.getFormattedMessage("not-enough-players");
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendMessage(notEnoughMsg);
                    }
                    forceEndGame("Pas assez de joueurs");
                    return;
                }

                // Si nombre impair, retirer le dernier joueur inscrit
                if (currentGame.getPlayerCount() % 2 != 0) {
                    handleOddPlayerCount();
                }

                // Passer en phase LOBBY avec sélection d'équipe
                currentGame.setState(MDTGameState.LOBBY);
                startTeamSelectionPhase();
            }
        }, 20L, 20L);
    }

    /**
     * Démarre un mode test pour configurer/visualiser la map
     * @param tester Le joueur qui lance le test
     * @param teleport Si true, téléporte le joueur au spawn lobby
     */
    public boolean startTestMode(Player tester, boolean teleport) {
        if (config.getWorld() == null) {
            tester.sendMessage(config.getFormattedMessage("prefix") + ChatColor.RED +
                    "Le monde '" + config.getWorldName() + "' n'existe pas!");
            return false;
        }

        if (currentGame != null) {
            tester.sendMessage(config.getFormattedMessage("prefix") + ChatColor.RED +
                    "Une partie est déjà en cours!");
            return false;
        }

        // Créer une partie en mode test
        currentGame = new MDTGame();
        currentGame.setState(MDTGameState.PLAYING); // Directement en mode PLAYING pour activer tout

        // Charger les lits
        for (MDTBed bed : config.loadTeamBeds()) {
            currentGame.addTeamBed(bed);
        }
        for (MDTBed bed : config.loadNeutralBeds()) {
            currentGame.addNeutralBed(bed);
        }

        // Charger les générateurs
        for (MDTGenerator generator : config.loadGeneratorsFromLocations()) {
            currentGame.addGenerator(generator);
        }

        // Téléporter si demandé
        if (teleport) {
            Location lobbySpawn = config.getLobbySpawn();
            if (lobbySpawn != null) {
                tester.teleport(lobbySpawn);
            } else {
                tester.sendMessage(ChatColor.YELLOW + "Spawn lobby non configuré, tu restes sur place.");
            }
        }

        // Démarrer les générateurs immédiatement
        if (generatorManager != null) {
            generatorManager.startGenerators(currentGame);
        }

        // Spawner les marchands immédiatement
        if (merchantManager != null) {
            merchantManager.spawnMerchants();
        }

        plugin.getLogger().info("[MDT] Mode TEST démarré par " + tester.getName() + " (teleport=" + teleport + ")");
        return true;
    }

    /**
     * Annonce l'event dans le chat avec un message cliquable
     */
    private void broadcastEventAnnouncement() {
        String message = config.getFormattedMessage("event-started");
        String hoverText = "Clic pour rejoindre le MDT Rush!";

        // Créer le message cliquable
        TextComponent mainMessage = new TextComponent("");

        // Ligne de séparation
        TextComponent separator = new TextComponent("\n" + ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n");
        mainMessage.addExtra(separator);

        // Message principal
        TextComponent eventText = new TextComponent(message + "\n\n");
        mainMessage.addExtra(eventText);

        // Bouton cliquable
        TextComponent joinButton = new TextComponent(ChatColor.GREEN + "" + ChatColor.BOLD + "  [CLIQUEZ POUR REJOINDRE]  ");
        joinButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mdt join"));
        joinButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hoverText)));
        mainMessage.addExtra(joinButton);

        // Ligne de séparation finale
        TextComponent separator2 = new TextComponent("\n" + ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n");
        mainMessage.addExtra(separator2);

        // Envoyer à tous les joueurs
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.spigot().sendMessage(mainMessage);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }
    }

    /**
     * Un joueur rejoint le lobby
     */
    public boolean joinGame(Player player) {
        if (currentGame == null) {
            player.sendMessage(config.getFormattedMessage("prefix") + ChatColor.RED +
                    "Aucune partie n'est en cours. Utilisez /mdt start pour en lancer une.");
            return false;
        }

        // Permettre de rejoindre pendant JOINING ou LOBBY
        if (currentGame.getState() != MDTGameState.JOINING && currentGame.getState() != MDTGameState.LOBBY) {
            player.sendMessage(config.getFormattedMessage("prefix") + ChatColor.RED +
                    "La partie a déjà commencé!");
            return false;
        }

        if (currentGame.hasPlayer(player.getUniqueId())) {
            player.sendMessage(config.getFormattedMessage("prefix") + ChatColor.RED +
                    "Tu es déjà dans le lobby!");
            return false;
        }

        if (currentGame.getPlayerCount() >= config.getMaxTotalPlayers()) {
            player.sendMessage(config.getFormattedMessage("prefix") + ChatColor.RED +
                    "La partie est pleine!");
            return false;
        }

        // Bloquer si rejoindre créerait un nombre impair de joueurs (pendant LOBBY uniquement)
        // Pendant JOINING, on laisse rejoindre librement, on gérera à la fin de la phase
        if (currentGame.getState() == MDTGameState.LOBBY) {
            int newCount = currentGame.getPlayerCount() + 1;
            if (newCount % 2 != 0) {
                player.sendMessage(config.getFormattedMessage("prefix") + ChatColor.RED +
                        "Impossible de rejoindre: les équipes doivent être équilibrées!");
                player.sendMessage(ChatColor.GRAY + "Attends qu'un autre joueur rejoigne pour entrer.");
                return false;
            }
        }

        // Anti-spam
        Long lastJoin = joinCooldowns.get(player.getUniqueId());
        if (lastJoin != null && System.currentTimeMillis() - lastJoin < JOIN_COOLDOWN_MS) {
            return false;
        }
        joinCooldowns.put(player.getUniqueId(), System.currentTimeMillis());

        // Sauvegarder l'inventaire
        inventoryBackupManager.savePlayerState(player);

        // Créer le joueur MDT
        MDTPlayer mdtPlayer = new MDTPlayer(player);
        currentGame.addPlayer(mdtPlayer);

        // Téléporter au lobby
        Location lobbySpawn = config.getLobbySpawn();
        if (lobbySpawn != null) {
            player.teleport(lobbySpawn);
        } else {
            // Recharger la config si la location n'est pas définie (monde pas encore chargé au démarrage)
            config.loadConfig();
            lobbySpawn = config.getLobbySpawn();
            if (lobbySpawn != null) {
                player.teleport(lobbySpawn);
            } else {
                plugin.getLogger().warning("[MDT] Lobby spawn non défini! Utilisez /mdt setup pour le configurer.");
                player.sendMessage(ChatColor.RED + "[MDT] Erreur: spawn lobby non configuré!");
            }
        }

        // Préparer le joueur
        player.getInventory().clear();
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setGameMode(GameMode.ADVENTURE);

        // Annoncer
        String joinMessage = config.getFormattedMessage("player-joined", "%player%", player.getName());
        broadcastToGame(joinMessage);
        player.playSound(player.getLocation(), config.getSound("join"), 1.0f, 1.0f);

        player.sendMessage(config.getFormattedMessage("inventory-saved"));

        return true;
    }

    /**
     * Un joueur quitte la partie
     */
    public boolean leaveGame(Player player) {
        if (currentGame == null || !currentGame.hasPlayer(player.getUniqueId())) {
            player.sendMessage(config.getFormattedMessage("prefix") + ChatColor.RED +
                    "Tu n'es pas dans une partie MDT!");
            return false;
        }

        MDTPlayer mdtPlayer = currentGame.getPlayer(player.getUniqueId());
        currentGame.removePlayer(player.getUniqueId());

        // Restaurer l'inventaire
        inventoryBackupManager.restorePlayerState(player);

        // Réinitialiser le joueur
        player.setGameMode(GameMode.SURVIVAL);
        mdtPlayer.resetHealth();

        // Téléporter au spawn principal du monde
        Location mainSpawn = plugin.getServer().getWorlds().get(0).getSpawnLocation();
        player.teleport(mainSpawn);

        String leaveMessage = config.getFormattedMessage("player-left", "%player%", player.getName());
        broadcastToGame(leaveMessage);
        player.playSound(player.getLocation(), config.getSound("leave"), 1.0f, 1.0f);

        player.sendMessage(config.getFormattedMessage("inventory-restored"));

        // Vérifier s'il reste assez de joueurs
        checkGameState();

        return true;
    }

    /**
     * Un joueur choisit une équipe
     */
    public boolean selectTeam(Player player, MDTTeam team) {
        if (currentGame == null || !currentGame.hasPlayer(player.getUniqueId())) {
            return false;
        }

        // Permettre de changer d'equipe pendant JOINING ou LOBBY
        if (currentGame.getState() != MDTGameState.JOINING && currentGame.getState() != MDTGameState.LOBBY) {
            player.sendMessage(config.getFormattedMessage("prefix") + ChatColor.RED +
                    "Tu ne peux plus changer d'équipe!");
            return false;
        }

        MDTPlayer mdtPlayer = currentGame.getPlayer(player.getUniqueId());

        // Vérifier si l'équipe n'est pas pleine
        if (currentGame.getTeamPlayerCount(team) >= config.getMaxPlayersPerTeam()) {
            player.sendMessage(config.getFormattedMessage("prefix") + ChatColor.RED +
                    "Cette équipe est pleine!");
            return false;
        }

        mdtPlayer.setTeam(team);

        String teamMessage = config.getFormattedMessage("team-joined",
                "%player%", player.getName(),
                "%team%", team.getColoredName());
        broadcastToGame(teamMessage);

        return true;
    }

    /**
     * Démarre le countdown du lobby
     */
    private void startLobbyCountdown() {
        final int[] timeLeft = {config.getLobbyCountdown()};

        lobbyTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (currentGame == null || currentGame.getState() != MDTGameState.LOBBY) {
                cancelTask(lobbyTask);
                return;
            }

            // Vérifier si on a assez de joueurs
            if (currentGame.getPlayerCount() < config.getMinTotalPlayers()) {
                // Pas assez de joueurs, attendre
                return;
            }

            // Annoncer le countdown toutes les 10 secondes ou les 5 dernières secondes
            if (timeLeft[0] <= 5 || timeLeft[0] % 10 == 0) {
                String countdownMessage = config.getFormattedMessage("lobby-countdown",
                        "%time%", String.valueOf(timeLeft[0]));
                broadcastToGame(countdownMessage);

                for (MDTPlayer mdtPlayer : currentGame.getAllPlayers()) {
                    Player p = mdtPlayer.getPlayer();
                    if (p != null) {
                        p.playSound(p.getLocation(), config.getSound("countdown-tick"), 1.0f, 1.0f);
                    }
                }
            }

            timeLeft[0]--;

            if (timeLeft[0] <= 0) {
                cancelTask(lobbyTask);
                startGameCountdown();
            }
        }, 20L, 20L); // Toutes les secondes
    }

    /**
     * Démarre le countdown final avant le début de la partie
     */
    private void startGameCountdown() {
        currentGame.setState(MDTGameState.COUNTDOWN);

        // Assigner automatiquement les équipes aux joueurs sans équipe
        autoAssignTeams();

        // Téléporter les joueurs à leurs spawns
        teleportTeamsToSpawns();

        final int[] timeLeft = {config.getGameStartCountdown()};

        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (currentGame == null) {
                cancelTask(countdownTask);
                return;
            }

            // Afficher le titre
            for (MDTPlayer mdtPlayer : currentGame.getAllPlayers()) {
                Player p = mdtPlayer.getPlayer();
                if (p != null) {
                    p.sendTitle(
                            ChatColor.RED + "" + timeLeft[0],
                            ChatColor.GRAY + "Préparez-vous!",
                            0, 25, 5
                    );
                    p.playSound(p.getLocation(), config.getSound("countdown-tick"), 1.0f, 1.0f);
                }
            }

            timeLeft[0]--;

            if (timeLeft[0] <= 0) {
                cancelTask(countdownTask);
                startGame();
            }
        }, 20L, 20L);
    }

    /**
     * Démarre effectivement la partie
     */
    private void startGame() {
        currentGame.setState(MDTGameState.PLAYING);

        // Configurer le monde MDT (heure et météo fixes)
        setupMDTWorld();

        // Sauvegarder automatiquement la map si nécessaire et activer la protection
        if (schematicManager.hasFAWE()) {
            if (!schematicManager.hasSavedSchematic()) {
                plugin.getLogger().info("[MDT] Première partie détectée, sauvegarde automatique de la map...");
                schematicManager.saveMDTRegion().thenAccept(success -> {
                    if (success) {
                        plugin.getLogger().info("[MDT] ✅ Map MDT sauvegardée automatiquement !");
                    } else {
                        plugin.getLogger().warning("[MDT] ⚠️ Échec de la sauvegarde automatique !");
                    }
                });
            }

            // Activer la protection de la région
            regionManager.enableProtection("Partie MDT en cours");
            plugin.getLogger().info("[MDT] Protection de la région activée pour la partie");
        }

        // Note: Le tracking des lits est géré par le BedStateManager, pas besoin de l'initialiser ici avec FAWE

        // Enregistrer le temps de debut pour le scoreboard
        currentGame.setGameStartTimeMillis(System.currentTimeMillis());

        // Message de début
        String startMessage = config.getFormattedMessage("game-started");
        for (MDTPlayer mdtPlayer : currentGame.getAllPlayers()) {
            Player p = mdtPlayer.getPlayer();
            if (p != null) {
                p.sendTitle(
                        ChatColor.GREEN + "GO!",
                        "",
                        0, 30, 10
                );
                p.sendMessage(startMessage);
                p.playSound(p.getLocation(), config.getSound("game-start"), 1.0f, 0.5f);
                p.setGameMode(GameMode.SURVIVAL);
            }
        }

        // Démarrer les générateurs
        generatorManager.startGenerators(currentGame);

        // Spawn le marchand
        merchantManager.spawnMerchant();

        // Demarrer le scoreboard
        scoreboardManager.startUpdating();

        // Démarrer la task de jeu principale
        startGameTask();

        plugin.getLogger().info("[MDT] Partie démarrée avec " + currentGame.getPlayerCount() + " joueurs");
    }

    /**
     * Task principale de la partie
     */
    private void startGameTask() {
        final int[] elapsedSeconds = {0};
        final int maxDuration = config.getMaxGameDuration();

        gameTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (currentGame == null || currentGame.getState() != MDTGameState.PLAYING) {
                cancelTask(gameTask);
                return;
            }

            elapsedSeconds[0]++;

            // Vérifier la condition de victoire (elimination)
            MDTTeam winner = currentGame.checkForWinner();
            if (winner != null) {
                endGame(winner);
                return;
            }

            // Avertissements de temps restant
            int remaining = maxDuration - elapsedSeconds[0];
            if (remaining == 300 || remaining == 60 || remaining == 30 || remaining == 10) {
                String timeWarning = config.getFormattedMessage("time-warning",
                        "%time%", formatTime(remaining));
                broadcastToGame(timeWarning);
            }

            // Vérifier le temps max - victoire par points
            if (elapsedSeconds[0] >= maxDuration) {
                plugin.getLogger().info("[MDT] Temps maximum atteint - decision par points");

                // Determiner le gagnant par les points
                MDTTeam winnerByPoints = currentGame.getWinnerByPoints();
                if (winnerByPoints != null) {
                    endGameByPoints(winnerByPoints);
                } else {
                    // Egalite parfaite
                    forceEndGame("Temps ecoule - Egalite parfaite!");
                }
            }
        }, 20L, 20L);
    }

    /**
     * Formate le temps en minutes:secondes
     */
    private String formatTime(int seconds) {
        int mins = seconds / 60;
        int secs = seconds % 60;
        if (mins > 0) {
            return mins + "min " + secs + "s";
        }
        return secs + "s";
    }

    /**
     * Termine la partie avec victoire par points (temps ecoule)
     */
    public void endGameByPoints(MDTTeam winner) {
        if (currentGame == null) return;

        currentGame.setState(MDTGameState.ENDED);
        currentGame.setWinningTeam(winner);
        currentGame.setEndTime(java.time.LocalDateTime.now());

        // Arrêter les générateurs
        generatorManager.stopGenerators();

        // Retirer le marchand
        merchantManager.despawnMerchant();

        // Arreter le scoreboard
        scoreboardManager.stopUpdating();

        // Statistiques finales
        int winnerPoints = currentGame.getTeamPoints(winner);
        int loserPoints = currentGame.getTeamPoints(winner.getOpposite());

        // Annonce de victoire par points
        String winMessage = config.getFormattedMessage("team-wins-points",
                "%team%", winner.getColoredName(),
                "%points%", String.valueOf(winnerPoints),
                "%loser_points%", String.valueOf(loserPoints));

        for (MDTPlayer mdtPlayer : currentGame.getAllPlayers()) {
            Player p = mdtPlayer.getPlayer();
            if (p != null) {
                // Afficher VICTOIRE ou DÉFAITE selon l'équipe du joueur
                if (mdtPlayer.getTeam() == winner) {
                    // GAGNANT
                    p.sendTitle(
                            ChatColor.GREEN + "" + ChatColor.BOLD + "VICTOIRE!",
                            ChatColor.GOLD + "Temps écoulé - " + winnerPoints + " points!",
                            10, 70, 20
                    );
                    p.playSound(p.getLocation(), config.getSound("victory"), 1.0f, 1.0f);
                } else {
                    // PERDANT
                    p.sendTitle(
                            ChatColor.RED + "" + ChatColor.BOLD + "DÉFAITE",
                            ChatColor.GOLD + "Temps écoulé - " + winner.getColoredName() + ChatColor.GOLD + " gagne",
                            10, 70, 20
                    );
                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 1.0f);
                }
                p.sendMessage(winMessage);
            }
        }

        plugin.getLogger().info("[MDT] Partie terminee par points - Gagnant: " + winner.getDisplayName() +
                " (" + winnerPoints + " pts vs " + loserPoints + " pts)");

        // Nettoyage après 5 secondes
        Bukkit.getScheduler().runTaskLater(plugin, this::cleanupGame, 100L);
    }

    /**
     * Termine la partie avec un gagnant (par elimination)
     */
    public void endGame(MDTTeam winner) {
        if (currentGame == null) return;

        currentGame.setState(MDTGameState.ENDED);
        currentGame.setWinningTeam(winner);
        currentGame.setEndTime(java.time.LocalDateTime.now());

        // Arrêter les générateurs
        generatorManager.stopGenerators();

        // Retirer le marchand
        merchantManager.despawnMerchant();

        // Arreter le scoreboard
        scoreboardManager.stopUpdating();

        // Statistiques finales
        int winnerPoints = currentGame.getTeamPoints(winner);
        int winnerKills = currentGame.getTeamKills(winner);

        // Annonce de victoire
        String winMessage = config.getFormattedMessage("team-wins", "%team%", winner.getColoredName());

        for (MDTPlayer mdtPlayer : currentGame.getAllPlayers()) {
            Player p = mdtPlayer.getPlayer();
            if (p != null) {
                // Afficher VICTOIRE ou DÉFAITE selon l'équipe du joueur
                if (mdtPlayer.getTeam() == winner) {
                    // GAGNANT
                    p.sendTitle(
                            ChatColor.GREEN + "" + ChatColor.BOLD + "VICTOIRE!",
                            ChatColor.GREEN + "Ton équipe a gagné!",
                            10, 70, 20
                    );
                    p.playSound(p.getLocation(), config.getSound("victory"), 1.0f, 1.0f);
                } else {
                    // PERDANT
                    p.sendTitle(
                            ChatColor.RED + "" + ChatColor.BOLD + "DÉFAITE",
                            ChatColor.GRAY + "L'équipe " + winner.getColoredName() + ChatColor.GRAY + " a gagné",
                            10, 70, 20
                    );
                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 1.0f);
                }
                p.sendMessage(winMessage);
                p.sendMessage(ChatColor.GRAY + "Stats: " + ChatColor.WHITE + winnerKills + " kills, " +
                        ChatColor.GOLD + winnerPoints + " points");
            }
        }

        plugin.getLogger().info("[MDT] Partie terminée - Gagnant: " + winner.getDisplayName() +
                " (" + winnerKills + " kills, " + winnerPoints + " pts)");

        // Nettoyage après 5 secondes
        Bukkit.getScheduler().runTaskLater(plugin, this::cleanupGame, 100L);
    }

    /**
     * Force la fin de la partie
     */
    public void forceEndGame(String reason) {
        if (currentGame == null) return;

        currentGame.setState(MDTGameState.ENDED);

        // Arrêter les générateurs
        generatorManager.stopGenerators();

        // Retirer le marchand
        merchantManager.despawnMerchant();

        // Arreter le scoreboard
        scoreboardManager.stopUpdating();

        broadcastToGame(config.getFormattedMessage("prefix") + ChatColor.RED + "Partie annulée: " + reason);

        plugin.getLogger().info("[MDT] Partie forcée à se terminer: " + reason);

        cleanupGame();
    }

    /**
     * Nettoie après une partie
     */
    private void cleanupGame() {
        if (currentGame == null) return;

        String endMessage = config.getFormattedMessage("game-ended");

        // Restaurer tous les joueurs
        for (MDTPlayer mdtPlayer : currentGame.getAllPlayers()) {
            Player p = mdtPlayer.getPlayer();
            if (p != null) {
                p.sendMessage(endMessage);

                // Restaurer l'inventaire
                inventoryBackupManager.restorePlayerState(p);

                // Réinitialiser
                p.setGameMode(GameMode.SURVIVAL);
                mdtPlayer.resetHealth();

                // Téléporter au spawn principal
                Location mainSpawn = plugin.getServer().getWorlds().get(0).getSpawnLocation();
                p.teleport(mainSpawn);
            }
        }

        // Supprimer tous les items au sol dans le monde MDT
        removeDroppedItems();

        // Reset de la map avec le nouveau système FAWE
        plugin.getLogger().info("[MDT] Restauration de la map MDT avec FAWE...");

        if (schematicManager.hasSavedSchematic()) {
            // Utiliser restoreMDTRegion qui pointe vers "latest"
            schematicManager.restoreMDTRegion().thenAccept(success -> {
                if (success) {
                    plugin.getLogger().info("[MDT] ✅ Schématique MDT restaurée avec succès !");
                    broadcastToGame(ChatColor.GREEN + "📸 La carte MDT a été restaurée automatiquement !");
                } else {
                    plugin.getLogger().severe("[MDT] ❌ Échec de la restauration de la schématique MDT !");
                }
            });
        } else {
            plugin.getLogger().warning("[MDT] ⚠️ Aucune schématique MDT trouvée ! La map n'a pas été restaurée.");
            broadcastToGame(ChatColor.YELLOW + "⚠️ Aucune sauvegarde de map trouvée !");
        }

        // Désactiver la protection de région après la restauration
        if (regionManager != null) {
            regionManager.disableProtection();
        }

        // Annuler toutes les tasks
        cancelAllTasks();

        // Réinitialiser l'état
        currentGame = null;

        plugin.getLogger().info("[MDT] Nettoyage terminé");
    }

    /**
     * Supprime tous les items au sol dans le monde MDT pour éviter que les joueurs
     * ne récupèrent les items de la partie précédente
     */
    private void removeDroppedItems() {
        World mdtWorld = config.getWorld();
        if (mdtWorld == null) {
            plugin.getLogger().warning("[MDT] Monde MDT non trouvé, impossible de supprimer les items");
            return;
        }

        // Compter et supprimer tous les items au sol (org.bukkit.entity.Item)
        int removedCount = 0;
        for (org.bukkit.entity.Item item : mdtWorld.getEntitiesByClass(org.bukkit.entity.Item.class)) {
            item.remove();
            removedCount++;
        }

        if (removedCount > 0) {
            plugin.getLogger().info("[MDT] ✅ " + removedCount + " items au sol supprimés du monde MDT");
        }
    }

    // ==================== MÉTHODES UTILITAIRES ====================

    /**
     * Gère le cas où il y a un nombre impair de joueurs à la fin de JOINING
     * Retire le dernier joueur inscrit et le notifie
     */
    private void handleOddPlayerCount() {
        if (currentGame == null || currentGame.getPlayerCount() == 0) return;

        // Trouver le dernier joueur inscrit (par ordre d'UUID, pas idéal mais fonctionnel)
        MDTPlayer lastPlayer = null;
        for (MDTPlayer mdtPlayer : currentGame.getAllPlayers()) {
            lastPlayer = mdtPlayer;
        }

        if (lastPlayer != null && lastPlayer.getPlayer() != null) {
            Player player = lastPlayer.getPlayer();
            currentGame.removePlayer(player.getUniqueId());

            // Restaurer son inventaire
            inventoryBackupManager.restorePlayerState(player);
            player.setGameMode(GameMode.SURVIVAL);

            // Téléporter au spawn principal
            Location mainSpawn = plugin.getServer().getWorlds().get(0).getSpawnLocation();
            player.teleport(mainSpawn);

            player.sendMessage(config.getFormattedMessage("prefix") + ChatColor.RED +
                    "Tu as été retiré car le nombre de joueurs était impair.");
            player.sendMessage(ChatColor.GRAY + "Les équipes doivent être équilibrées (2v2, 3v3, etc.)");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);

            broadcastToGame(ChatColor.YELLOW + player.getName() + " a été retiré (équilibrage des équipes).");
        }
    }

    /**
     * Démarre la phase de sélection d'équipe
     * Ouvre le GUI de sélection pour tous les joueurs
     */
    private void startTeamSelectionPhase() {
        // Annoncer la phase de sélection
        int totalPlayers = currentGame.getPlayerCount();
        int playersPerTeam = totalPlayers / 2;

        broadcastToGame("");
        broadcastToGame(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        broadcastToGame(ChatColor.YELLOW + "" + ChatColor.BOLD + "  SÉLECTION D'ÉQUIPE");
        broadcastToGame(ChatColor.GRAY + "  Mode: " + ChatColor.WHITE + playersPerTeam + "v" + playersPerTeam);
        broadcastToGame(ChatColor.GRAY + "  Choisis ton équipe avant le début!");
        broadcastToGame(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        broadcastToGame("");

        // Ouvrir le GUI de sélection pour chaque joueur
        for (MDTPlayer mdtPlayer : currentGame.getAllPlayers()) {
            Player p = mdtPlayer.getPlayer();
            if (p != null) {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                // Ouvrir le GUI de sélection d'équipe
                com.gravityyfh.roleplaycity.mdt.gui.MDTTeamSelectionGUI.open(plugin, this, p);
            }
        }

        // Démarrer le countdown du lobby
        startLobbyCountdown();
    }

    /**
     * Assigne automatiquement les équipes aux joueurs sans équipe
     */
    private void autoAssignTeams() {
        List<MDTPlayer> unassigned = new ArrayList<>();
        for (MDTPlayer p : currentGame.getAllPlayers()) {
            if (!p.hasTeam()) {
                unassigned.add(p);
            }
        }

        // Mélanger pour l'aléatoire
        Collections.shuffle(unassigned);

        for (MDTPlayer p : unassigned) {
            // Assigner à l'équipe la moins remplie
            int redCount = currentGame.getTeamPlayerCount(MDTTeam.RED);
            int blueCount = currentGame.getTeamPlayerCount(MDTTeam.BLUE);

            MDTTeam team = redCount <= blueCount ? MDTTeam.RED : MDTTeam.BLUE;
            p.setTeam(team);

            Player player = p.getPlayer();
            if (player != null) {
                player.sendMessage(config.getFormattedMessage("prefix") +
                        "Tu as été assigné à l'équipe " + team.getColoredName() + "!");
            }
        }
    }

    /**
     * Téléporte les joueurs à leurs spawns d'équipe
     */
    private void teleportTeamsToSpawns() {
        for (MDTPlayer mdtPlayer : currentGame.getAllPlayers()) {
            Player p = mdtPlayer.getPlayer();
            if (p == null) continue;

            if (!mdtPlayer.hasTeam()) {
                plugin.getLogger().warning("[MDT] Joueur " + p.getName() + " n'a pas d'équipe assignée!");
                continue;
            }

            MDTTeam team = mdtPlayer.getTeam();
            Location spawn = config.getTeamSpawn(team);

            // Fallback: utiliser la position du lit si le spawn n'est pas défini
            if (spawn == null) {
                spawn = config.getBedLocation(team);
                if (spawn != null) {
                    spawn = spawn.clone().add(0.5, 0.1, 0.5); // Ajuster pour être à côté du lit
                    plugin.getLogger().warning("[MDT] Spawn de l'équipe " + team.name() +
                        " non défini, utilisation de la position du lit");
                }
            }

            if (spawn != null) {
                p.teleport(spawn);
                plugin.getLogger().info("[MDT] " + p.getName() + " téléporté au spawn " + team.name());
            } else {
                plugin.getLogger().severe("[MDT] ERREUR: Impossible de téléporter " + p.getName() +
                    " - Spawn ET lit de l'équipe " + team.name() + " non configurés!");
                p.sendMessage(ChatColor.RED + "[MDT] Erreur: Spawn non configuré! Contactez un admin.");
            }
        }
    }

    /**
     * Envoie un message à tous les joueurs de la partie
     */
    public void broadcastToGame(String message) {
        if (currentGame == null) return;

        for (MDTPlayer mdtPlayer : currentGame.getAllPlayers()) {
            Player p = mdtPlayer.getPlayer();
            if (p != null) {
                p.sendMessage(message);
            }
        }
    }

    /**
     * Vérifie l'état de la partie (joueurs restants, etc.)
     */
    private void checkGameState() {
        if (currentGame == null) return;

        // En phase JOINING, si plus de joueurs annuler
        if (currentGame.getState() == MDTGameState.JOINING) {
            if (currentGame.getPlayerCount() == 0) {
                // Pas de message si personne n'avait rejoint
            }
        }

        // En lobby, vérifier si on a encore des joueurs
        if (currentGame.getState() == MDTGameState.LOBBY) {
            if (currentGame.getPlayerCount() == 0) {
                forceEndGame("Plus de joueurs dans le lobby");
            }
        }

        // En jeu, vérifier le gagnant
        if (currentGame.getState() == MDTGameState.PLAYING) {
            MDTTeam winner = currentGame.checkForWinner();
            if (winner != null) {
                endGame(winner);
            }
        }
    }

    private void cancelTask(BukkitTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    private void cancelAllTasks() {
        cancelTask(joinTask);
        cancelTask(lobbyTask);
        cancelTask(countdownTask);
        cancelTask(gameTask);
    }

    // ==================== GETTERS ====================

    public RoleplayCity getPlugin() {
        return plugin;
    }

    public MDTConfig getConfig() {
        return config;
    }

    public MDTGame getCurrentGame() {
        return currentGame;
    }

    public boolean hasActiveGame() {
        return currentGame != null;
    }

    public boolean isPlayerInGame(UUID playerUuid) {
        return currentGame != null && currentGame.hasPlayer(playerUuid);
    }

    public MDTPlayer getMDTPlayer(UUID playerUuid) {
        return currentGame != null ? currentGame.getPlayer(playerUuid) : null;
    }

    public InventoryBackupManager getInventoryBackupManager() {
        return inventoryBackupManager;
    }

    public GeneratorManager getGeneratorManager() {
        return generatorManager;
    }

    // Nouveaux getters pour le système FAWE
    public MDTSchematicManager getSchematicManager() {
        return schematicManager;
    }

    public MDTRegionManager getRegionManager() {
        return regionManager;
    }

    // Anciens getters gardés pour compatibilité (retournent null)
    @Deprecated
    public Object getBlockTracker() {
        plugin.getLogger().warning("[MDT] BlockTracker n'existe plus avec le système FAWE");
        return null;
    }

    @Deprecated
    public Object getMapResetManager() {
        plugin.getLogger().warning("[MDT] MapResetManager n'existe plus avec le système FAWE");
        return null;
    }

    @Deprecated
    public Object getSnapshotManager() {
        plugin.getLogger().warning("[MDT] MDTMapSnapshotManager n'existe plus avec le système FAWE");
        return null;
    }

    @Deprecated
    public Object getRegionProtectionManager() {
        plugin.getLogger().warning("[MDT] MDTRegionProtectionManager n'existe plus avec le système FAWE");
        return null;
    }
}
