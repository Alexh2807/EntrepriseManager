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
    private static final String SCOREBOARD_TITLE = ChatColor.YELLOW + "» " + ChatColor.AQUA + ChatColor.BOLD + "MDT RUSH" + ChatColor.YELLOW + " «";

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
                    updateScoreboard(player, game);
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
    private void updateScoreboard(Player player, MDTGame game) {
        Scoreboard scoreboard = playerScoreboards.computeIfAbsent(player.getUniqueId(), k -> Bukkit.getScoreboardManager().getNewScoreboard());

        Objective objective = scoreboard.getObjective("mdt_obj");
        if (objective == null) {
            objective = scoreboard.registerNewObjective("mdt_obj", "dummy", SCOREBOARD_TITLE);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        // === LIGNES DU SCOREBOARD (design moderne) ===
        // Le score (int) est l'ordre d'affichage de bas en haut
        int line = 15;

        setLine(scoreboard, objective, line--, ChatColor.GRAY + " "); // Ligne 15

        // Temps de jeu ou statut
        if (game.getState() == MDTGameState.PLAYING) {
            String timeLabel = ChatColor.WHITE + "🕒 Temps: " + ChatColor.GREEN + game.getFormattedRemainingTime(config.getMaxGameDuration());
            setLine(scoreboard, objective, line--, timeLabel); // Ligne 14
        } else if (game.getState() == MDTGameState.LOBBY || game.getState() == MDTGameState.JOINING) {
            setLine(scoreboard, objective, line--, ChatColor.WHITE + "🕒 " + ChatColor.YELLOW + "En attente..."); // Ligne 14
        } else if (game.getState() == MDTGameState.COUNTDOWN) {
            setLine(scoreboard, objective, line--, ChatColor.WHITE + "🕒 " + ChatColor.RED + "Début imminent !"); // Ligne 14
        }

        // Joueurs restants
        int alivePlayers = game.getAlivePlayers();
        int totalPlayers = game.getPlayerCount();
        setLine(scoreboard, objective, line--, ChatColor.WHITE + "👥 Joueurs: " + ChatColor.GREEN + alivePlayers + "/" + totalPlayers); // Ligne 13

        setLine(scoreboard, objective, line--, "  "); // Ligne 12 (vide)

        // === ÉQUIPE ROUGE ===
        setLine(scoreboard, objective, line--, "❤ " + MDTTeam.RED.getChatColor() + ChatColor.BOLD + "ROUGE"); // Ligne 11
        boolean redBedDestroyed = game.isTeamBedDestroyed(MDTTeam.RED);
        String redBedStatus = redBedDestroyed ? (ChatColor.RED + "❌ Cassé") : (ChatColor.GREEN + "✅ Intact");
        setLine(scoreboard, objective, line--, "  Lit: " + redBedStatus); // Ligne 10
        int redKills = game.getTeamKills(MDTTeam.RED);
        int redPoints = game.getTeamPoints(MDTTeam.RED);
        setLine(scoreboard, objective, line--, "  Kills: " + ChatColor.WHITE + redKills + ChatColor.GRAY + " | Pts: " + ChatColor.GOLD + redPoints); // Ligne 9

        setLine(scoreboard, objective, line--, "   "); // Ligne 8 (vide)

        // === ÉQUIPE BLEUE ===
        setLine(scoreboard, objective, line--, "💙 " + MDTTeam.BLUE.getChatColor() + ChatColor.BOLD + "BLEU"); // Ligne 7
        boolean blueBedDestroyed = game.isTeamBedDestroyed(MDTTeam.BLUE);
        String blueBedStatus = blueBedDestroyed ? (ChatColor.RED + "❌ Cassé") : (ChatColor.GREEN + "✅ Intact");
        setLine(scoreboard, objective, line--, "  Lit: " + blueBedStatus); // Ligne 6
        int blueKills = game.getTeamKills(MDTTeam.BLUE);
        int bluePoints = game.getTeamPoints(MDTTeam.BLUE);
        setLine(scoreboard, objective, line--, "  Kills: " + ChatColor.WHITE + blueKills + ChatColor.GRAY + " | Pts: " + ChatColor.GOLD + bluePoints); // Ligne 5

        setLine(scoreboard, objective, line--, "    "); // Ligne 4 (vide)

        setLine(scoreboard, objective, line--, ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "----------------"); // Ligne 3

        setLine(scoreboard, objective, line--, ChatColor.AQUA + "play.votreserveur.com"); // Ligne 2


        // Appliquer le scoreboard au joueur s'il n'a pas changé
        if (player.getScoreboard() != scoreboard) {
            player.setScoreboard(scoreboard);
        }
    }

    /**
     * Définit une ligne du scoreboard en utilisant des équipes pour supporter des textes > 40 chars et éviter le flickering.
     * C'est la méthode moderne et robuste pour gérer les scoreboards.
     */
    private void setLine(Scoreboard scoreboard, Objective objective, int score, String text) {
        String entry = getEntryForScore(score);
        Team team = getOrCreateTeam(scoreboard, "line" + score);

        // Diviser le texte s'il est plus long que 16 caractères (préfixe/suffixe)
        // La limite est plus élevée sur les versions modernes, mais 16/16 est sûr.
        String prefix;
        String suffix = "";

        if (text.length() > 16) {
            int splitIndex = findSplitPoint(text, 16);
            prefix = text.substring(0, splitIndex);
            // Le suffixe doit commencer par un code couleur s'il y en a un.
            String lastColors = ChatColor.getLastColors(prefix);
            suffix = lastColors + text.substring(splitIndex);
            
            if (suffix.length() > 16) {
                suffix = suffix.substring(0, 16);
            }
        } else {
            prefix = text;
        }

        team.setPrefix(prefix);
        team.setSuffix(suffix);
        
        // S'assurer que l'entry est bien dans l'équipe
        team.addEntry(entry);
        
        objective.getScore(entry).setScore(score);
    }

    /**
     * Trouve un point de coupure intelligent pour le texte du scoreboard.
     */
    private int findSplitPoint(String text, int ideal) {
        if (text.length() <= ideal) return text.length();

        // Essayer de couper après un code couleur
        int lastColor = text.lastIndexOf(ChatColor.COLOR_CHAR, ideal);
        if (lastColor != -1 && lastColor < ideal -1) {
            return lastColor + 2;
        }

        // Sinon, couper à l'espace le plus proche
        int lastSpace = text.lastIndexOf(' ', ideal);
        if (lastSpace != -1) {
            return lastSpace;
        }
        
        // Couper à l'idéal en dernier recours
        return ideal;
    }


    /**
     * Crée ou récupère une équipe pour une ligne de scoreboard.
     */
    private Team getOrCreateTeam(Scoreboard scoreboard, String name) {
        Team team = scoreboard.getTeam(name);
        if (team == null) {
            team = scoreboard.registerNewTeam(name);
        }
        return team;
    }

    /**
     * Génère une entrée unique pour une ligne de score en utilisant des codes couleur invisibles.
     */
    private String getEntryForScore(int score) {
        return ChatColor.values()[score].toString() + ChatColor.RESET;
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

        updateScoreboard(player, game);
    }
}
