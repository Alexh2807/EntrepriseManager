package com.gravityyfh.roleplaycity.mdt.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.mdt.MDTRushManager;
import com.gravityyfh.roleplaycity.mdt.data.MDTGame;
import com.gravityyfh.roleplaycity.mdt.data.MDTGameState;
import com.gravityyfh.roleplaycity.mdt.data.MDTPlayer;
import com.gravityyfh.roleplaycity.mdt.data.MDTTeam;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Listener pour les événements des joueurs pendant le MDT Rush
 */
public class MDTPlayerListener implements Listener {
    private final RoleplayCity plugin;
    private final MDTRushManager manager;

    public MDTPlayerListener(RoleplayCity plugin, MDTRushManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /**
     * Gestion de la connexion d'un joueur
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Vérifier si le joueur a un backup d'inventaire en attente
        manager.getInventoryBackupManager().checkAndRestoreOnJoin(player);
    }

    /**
     * Gestion de la déconnexion d'un joueur
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (!manager.isPlayerInGame(player.getUniqueId())) {
            return;
        }

        MDTGame game = manager.getCurrentGame();
        if (game == null) return;

        MDTPlayer mdtPlayer = game.getPlayer(player.getUniqueId());
        if (mdtPlayer == null) return;

        // Si en lobby, simplement retirer le joueur
        if (game.getState() == MDTGameState.LOBBY) {
            game.removePlayer(player.getUniqueId());
            manager.broadcastToGame(manager.getConfig().getFormattedMessage("player-left",
                    "%player%", player.getName()));
            return;
        }

        // Si en jeu, marquer comme éliminé (il pourra récupérer son inventaire à la reconnexion)
        if (game.getState() == MDTGameState.PLAYING) {
            mdtPlayer.setEliminated(true);
            mdtPlayer.setSpectating(true);

            String message = manager.getConfig().getFormattedMessage("player-eliminated",
                    "%player%", player.getName());
            manager.broadcastToGame(message);

            // Vérifier si c'est la fin
            MDTTeam winner = game.checkForWinner();
            if (winner != null) {
                manager.endGame(winner);
            }
        }
    }

    /**
     * Gestion de la mort d'un joueur
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (!manager.isPlayerInGame(player.getUniqueId())) {
            return;
        }

        MDTGame game = manager.getCurrentGame();
        if (game == null || game.getState() != MDTGameState.PLAYING) {
            return;
        }

        MDTPlayer mdtPlayer = game.getPlayer(player.getUniqueId());
        if (mdtPlayer == null) return;

        // SUPPRIMER le message de mort vanilla (visible par tous sinon)
        event.setDeathMessage(null);

        // Empêcher le drop d'items
        event.getDrops().clear();
        event.setDroppedExp(0);

        // Comptabiliser la mort
        mdtPlayer.addDeath();

        // Trouver le tueur
        Player killer = player.getKiller();
        if (killer != null && manager.isPlayerInGame(killer.getUniqueId())) {
            MDTPlayer mdtKiller = game.getPlayer(killer.getUniqueId());
            if (mdtKiller != null) {
                mdtKiller.addKill();

                // Ajouter +1 point et +1 kill a l'equipe du tueur
                MDTTeam killerTeam = mdtKiller.getTeam();
                if (killerTeam != null) {
                    game.addTeamPoints(killerTeam, 1);  // +1 point par kill
                    game.addTeamKill(killerTeam);       // Compteur de kills equipe
                }
            }

            // Message de kill (seulement pour les joueurs MDT)
            String killMessage = manager.getConfig().getFormattedMessage("player-killed",
                    "%victim%", player.getName(),
                    "%killer%", killer.getName());
            manager.broadcastToGame(killMessage);
        }

        // Vérifier si le joueur peut respawn
        MDTTeam team = mdtPlayer.getTeam();
        if (team != null && !game.canTeamRespawn(team)) {
            // Lit détruit = élimination définitive
            mdtPlayer.setEliminated(true);

            String elimMessage = manager.getConfig().getFormattedMessage("player-eliminated",
                    "%player%", player.getName());
            manager.broadcastToGame(elimMessage);

            // Vérifier victoire
            MDTTeam winner = game.checkForWinner();
            if (winner != null) {
                manager.endGame(winner);
            }
        }

        // Jouer le son de mort
        player.playSound(player.getLocation(), manager.getConfig().getSound("player-death"), 1.0f, 1.0f);
    }

    /**
     * Gestion du respawn d'un joueur
     * Respawn avec délai de 5 secondes pour les joueurs non éliminés
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        if (!manager.isPlayerInGame(player.getUniqueId())) {
            return;
        }

        MDTGame game = manager.getCurrentGame();
        if (game == null || game.getState() != MDTGameState.PLAYING) {
            return;
        }

        MDTPlayer mdtPlayer = game.getPlayer(player.getUniqueId());
        if (mdtPlayer == null) return;

        MDTTeam team = mdtPlayer.getTeam();

        if (mdtPlayer.isEliminated()) {
            // Joueur éliminé = mode spectateur
            mdtPlayer.setSpectating(true);

            // Téléporter au spawn du lobby pour observer
            Location lobbySpawn = manager.getConfig().getLobbySpawn();
            if (lobbySpawn != null) {
                event.setRespawnLocation(lobbySpawn);
            }

            // Mettre en mode spectateur après le respawn
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage(manager.getConfig().getFormattedMessage("no-respawn"));
                player.sendMessage(ChatColor.GRAY + "Tu es maintenant spectateur. Utilise " +
                        ChatColor.YELLOW + "/mdt leave" + ChatColor.GRAY + " pour quitter.");
            }, 1L);

        } else if (team != null && game.canTeamRespawn(team)) {
            // Respawn avec délai de 5 secondes (seulement si le lit n'est pas détruit)
            // D'abord, respawn temporaire au lobby en mode spectateur
            Location lobbySpawn = manager.getConfig().getLobbySpawn();
            Location teamSpawn = manager.getConfig().getTeamSpawn(team);

            if (lobbySpawn != null) {
                event.setRespawnLocation(lobbySpawn);
            }

            // Mettre temporairement en spectateur pendant le délai
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                player.setGameMode(GameMode.SPECTATOR);

                // Afficher le titre "TU ES MORT" avec countdown
                player.sendTitle(
                    ChatColor.RED + "" + ChatColor.BOLD + "TU ES MORT!",
                    ChatColor.GRAY + "Respawn dans 5 secondes...",
                    10, 100, 10
                );

                // Countdown 5 secondes puis téléportation
                for (int i = 4; i >= 1; i--) {
                    final int seconds = i;
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline() && manager.isPlayerInGame(player.getUniqueId())) {
                            player.sendTitle(
                                ChatColor.RED + "" + ChatColor.BOLD + "TU ES MORT!",
                                ChatColor.GRAY + "Respawn dans " + ChatColor.YELLOW + seconds + ChatColor.GRAY + " seconde" + (seconds > 1 ? "s" : "") + "...",
                                0, 25, 0
                            );
                        }
                    }, (5 - seconds) * 20L);
                }

                // Après 5 secondes, téléporter au spawn et remettre en survie
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && manager.isPlayerInGame(player.getUniqueId())) {
                        // Téléporter au spawn de l'équipe
                        if (teamSpawn != null) {
                            player.teleport(teamSpawn);
                        }

                        // Remettre en mode survie
                        player.setGameMode(GameMode.SURVIVAL);

                        // Appliquer les cœurs bonus
                        mdtPlayer.applyBonusHearts();
                        player.setHealth(player.getMaxHealth());
                        player.setFoodLevel(20);

                        // Message de respawn
                        player.sendTitle(
                            ChatColor.GREEN + "RESPAWN!",
                            "",
                            10, 40, 10
                        );
                        player.sendMessage(ChatColor.GREEN + "Tu as respawn!");
                    }
                }, 100L); // 5 secondes = 100 ticks
            }, 1L);

        } else {
            // Cas où le lit est détruit mais le joueur n'est pas encore marqué comme éliminé
            mdtPlayer.setEliminated(true);
            mdtPlayer.setSpectating(true);

            // Téléporter au spawn du lobby pour observer
            Location lobbySpawn = manager.getConfig().getLobbySpawn();
            if (lobbySpawn != null) {
                event.setRespawnLocation(lobbySpawn);
            }

            // Mettre en mode spectateur après le respawn
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage(manager.getConfig().getFormattedMessage("no-respawn"));
                player.sendMessage(ChatColor.GRAY + "Tu es maintenant spectateur. Utilise " +
                        ChatColor.YELLOW + "/mdt leave" + ChatColor.GRAY + " pour quitter.");
            }, 1L);
        }
    }

    /**
     * Gestion du PvP (empêcher le friendly fire)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        if (!manager.isPlayerInGame(victim.getUniqueId()) ||
                !manager.isPlayerInGame(attacker.getUniqueId())) {
            return;
        }

        MDTGame game = manager.getCurrentGame();
        if (game == null) return;

        // Pas de PvP en lobby ou countdown
        if (game.getState() != MDTGameState.PLAYING) {
            event.setCancelled(true);
            return;
        }

        MDTPlayer mdtVictim = game.getPlayer(victim.getUniqueId());
        MDTPlayer mdtAttacker = game.getPlayer(attacker.getUniqueId());

        if (mdtVictim == null || mdtAttacker == null) return;

        // Empêcher le friendly fire (même équipe)
        if (mdtVictim.getTeam() == mdtAttacker.getTeam()) {
            event.setCancelled(true);
        }
    }

    /**
     * Gestion du void kill - mort automatique si le joueur tombe trop bas
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Verifier si le joueur est dans une partie MDT
        if (!manager.isPlayerInGame(player.getUniqueId())) {
            return;
        }

        MDTGame game = manager.getCurrentGame();
        if (game == null) {
            return;
        }

        // Geler les joueurs pendant la phase COUNTDOWN (Preparez-vous)
        if (game.getState() == MDTGameState.COUNTDOWN) {
            // Annuler le mouvement pendant le countdown
            event.setCancelled(true);
            return;
        }

        // Le reste du code s'applique uniquement pendant la phase PLAYING
        if (game.getState() != MDTGameState.PLAYING) {
            return;
        }

        // Verifier la hauteur du void
        int voidHeight = manager.getConfig().getVoidKillHeight();
        if (player.getLocation().getY() < voidHeight) {
            // Message void death
            String voidMessage = manager.getConfig().getFormattedMessage("void-death",
                    "%player%", player.getName());
            manager.broadcastToGame(voidMessage);

            // Tuer le joueur (declenche le systeme de mort normal)
            player.setHealth(0);
        }
    }
}
