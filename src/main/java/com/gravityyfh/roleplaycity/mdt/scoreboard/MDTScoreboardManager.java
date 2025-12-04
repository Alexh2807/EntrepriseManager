package com.gravityyfh.roleplaycity.mdt.scoreboard;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.mdt.MDTRushManager;
import com.gravityyfh.roleplaycity.mdt.config.MDTConfig;
import com.gravityyfh.roleplaycity.mdt.data.MDTGame;
import com.gravityyfh.roleplaycity.mdt.data.MDTGameState;
import com.gravityyfh.roleplaycity.mdt.data.MDTPlayer;
import com.gravityyfh.roleplaycity.mdt.data.MDTTeam;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gestionnaire du scoreboard pour le MDT Rush
 * Affiche: temps de jeu, joueurs restants, kills et points par equipe
 */
public class MDTScoreboardManager {
    private final RoleplayCity plugin;
    private final MDTRushManager manager;
    private final MDTConfig config;

    private BukkitTask updateTask;
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();

    // Titre du scoreboard
    private static final String SCOREBOARD_TITLE = ChatColor.RED + "" + ChatColor.BOLD + "MDT RUSH";

    public MDTScoreboardManager(RoleplayCity plugin, MDTRushManager manager, MDTConfig config) {
        this.plugin = plugin;
        this.manager = manager;
        this.config = config;
    }

    /**
     * Demarre la mise a jour du scoreboard
     */
    public void startUpdating() {
        stopUpdating();

        // Mettre a jour toutes les secondes
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            MDTGame game = manager.getCurrentGame();
            if (game == null) {
                stopUpdating();
                return;
            }

            // Mettre a jour le scoreboard de chaque joueur
            for (MDTPlayer mdtPlayer : game.getAllPlayers()) {
                Player player = mdtPlayer.getPlayer();
                if (player != null && player.isOnline()) {
                    updateScoreboard(player, game, mdtPlayer);
                }
            }
        }, 0L, 20L); // Toutes les secondes
    }

    /**
     * Arrete la mise a jour du scoreboard
     */
    public void stopUpdating() {
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
            updateTask = null;
        }

        // Retirer les scoreboards des joueurs (copie pour éviter ConcurrentModificationException)
        for (UUID uuid : new java.util.ArrayList<>(playerScoreboards.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && Bukkit.getScoreboardManager() != null) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        playerScoreboards.clear();
    }

    /**
     * Met a jour le scoreboard d'un joueur
     */
    private void updateScoreboard(Player player, MDTGame game, MDTPlayer mdtPlayer) {
        Scoreboard scoreboard = playerScoreboards.get(player.getUniqueId());
        if (scoreboard == null) {
            scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            playerScoreboards.put(player.getUniqueId(), scoreboard);
        }

        // Supprimer l'ancien objective si existant
        Objective oldObj = scoreboard.getObjective("mdt");
        if (oldObj != null) {
            oldObj.unregister();
        }

        // Creer le nouvel objective
        Objective objective = scoreboard.registerNewObjective("mdt", Criteria.DUMMY, SCOREBOARD_TITLE);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        int line = 15;

        // Ligne vide en haut
        setLine(objective, line--, ChatColor.GRAY + "");

        // Temps de jeu ou temps restant
        if (game.getState() == MDTGameState.PLAYING) {
            String timeLabel = ChatColor.WHITE + "Temps: " + ChatColor.YELLOW + game.getFormattedRemainingTime(config.getMaxGameDuration());
            setLine(objective, line--, timeLabel);
        } else if (game.getState() == MDTGameState.LOBBY || game.getState() == MDTGameState.JOINING) {
            setLine(objective, line--, ChatColor.WHITE + "En attente...");
        } else if (game.getState() == MDTGameState.COUNTDOWN) {
            setLine(objective, line--, ChatColor.YELLOW + "Debut imminent!");
        }

        // Ligne de separation
        setLine(objective, line--, ChatColor.DARK_GRAY + "----------------");

        // Joueurs restants
        int alivePlayers = game.getAlivePlayers();
        int totalPlayers = game.getPlayerCount();
        setLine(objective, line--, ChatColor.WHITE + "Joueurs: " + ChatColor.GREEN + alivePlayers + "/" + totalPlayers);

        // Ligne vide
        setLine(objective, line--, ChatColor.GRAY + " ");

        // === EQUIPE ROUGE ===
        int redPoints = game.getTeamPoints(MDTTeam.RED);
        int redKills = game.getTeamKills(MDTTeam.RED);
        int redAlive = game.getAlivePlayersOfTeam(MDTTeam.RED).size();
        boolean redBedDestroyed = game.isTeamBedDestroyed(MDTTeam.RED);

        setLine(objective, line--, MDTTeam.RED.getChatColor() + "" + ChatColor.BOLD + "Rouge");
        String redBedStatus = redBedDestroyed ? (ChatColor.RED + "X") : (ChatColor.GREEN + "Lit OK");
        setLine(objective, line--, ChatColor.GRAY + " " + redBedStatus + ChatColor.GRAY + " | " + ChatColor.WHITE + redAlive + " en vie");
        setLine(objective, line--, ChatColor.GRAY + " Kills: " + ChatColor.WHITE + redKills + ChatColor.GRAY + " | Pts: " + ChatColor.GOLD + redPoints);

        // Ligne vide
        setLine(objective, line--, ChatColor.DARK_GRAY + " ");

        // === EQUIPE BLEUE ===
        int bluePoints = game.getTeamPoints(MDTTeam.BLUE);
        int blueKills = game.getTeamKills(MDTTeam.BLUE);
        int blueAlive = game.getAlivePlayersOfTeam(MDTTeam.BLUE).size();
        boolean blueBedDestroyed = game.isTeamBedDestroyed(MDTTeam.BLUE);

        setLine(objective, line--, MDTTeam.BLUE.getChatColor() + "" + ChatColor.BOLD + "Bleu");
        String blueBedStatus = blueBedDestroyed ? (ChatColor.RED + "X") : (ChatColor.GREEN + "Lit OK");
        setLine(objective, line--, ChatColor.GRAY + " " + blueBedStatus + ChatColor.GRAY + " | " + ChatColor.WHITE + blueAlive + " en vie");
        setLine(objective, line--, ChatColor.GRAY + " Kills: " + ChatColor.WHITE + blueKills + ChatColor.GRAY + " | Pts: " + ChatColor.GOLD + bluePoints);

        // Ligne de separation
        setLine(objective, line--, ChatColor.DARK_GRAY + "-----------------");

        // Equipe du joueur
        if (mdtPlayer.hasTeam()) {
            MDTTeam team = mdtPlayer.getTeam();
            setLine(objective, line--, ChatColor.WHITE + "Ton equipe: " + team.getColoredName());
        }

        // Appliquer le scoreboard au joueur
        player.setScoreboard(scoreboard);
    }

    /**
     * Definit une ligne du scoreboard
     * Utilise des caracteres invisibles uniques pour eviter les doublons
     */
    private void setLine(Objective objective, int score, String text) {
        // Limiter la longueur du texte
        if (text.length() > 40) {
            text = text.substring(0, 40);
        }

        // Ajouter des caracteres invisibles pour rendre chaque ligne unique
        String uniqueText = getUniqueText(text, score);

        Score line = objective.getScore(uniqueText);
        line.setScore(score);
    }

    /**
     * Genere un texte unique en ajoutant des caracteres invisibles
     */
    private String getUniqueText(String text, int score) {
        // Utiliser des codes couleur invisibles pour rendre unique
        StringBuilder sb = new StringBuilder(text);
        // Ajouter des reset codes invisibles a la fin selon le score
        for (int i = 0; i < (15 - score); i++) {
            sb.append(ChatColor.RESET);
        }
        return sb.toString();
    }

    /**
     * Retire le scoreboard d'un joueur
     */
    public void removeScoreboard(Player player) {
        playerScoreboards.remove(player.getUniqueId());
        // Remettre le scoreboard principal du serveur
        if (Bukkit.getScoreboardManager() != null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    /**
     * Cree et affiche le scoreboard initial pour un joueur
     */
    public void createScoreboard(Player player) {
        MDTGame game = manager.getCurrentGame();
        if (game == null) return;

        MDTPlayer mdtPlayer = game.getPlayer(player.getUniqueId());
        if (mdtPlayer == null) return;

        updateScoreboard(player, game, mdtPlayer);
    }
}
