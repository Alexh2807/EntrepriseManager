package com.gravityyfh.roleplaycity.mdt.data;

import org.bukkit.Location;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Représente une partie MDT Rush en cours
 */
public class MDTGame {
    private final UUID gameId;
    private final LocalDateTime startTime;
    private MDTGameState state;

    // Joueurs
    private final Map<UUID, MDTPlayer> players;

    // Lits
    private final Map<String, MDTBed> teamBeds;
    private final Map<String, MDTBed> neutralBeds;

    // Générateurs
    private final List<MDTGenerator> generators;

    // Statistiques
    private MDTTeam winningTeam;
    private LocalDateTime endTime;

    // Système de points par équipe
    private final Map<MDTTeam, AtomicInteger> teamPoints;
    private final Map<MDTTeam, AtomicInteger> teamKills;

    // Timer de jeu
    private long gameStartTimeMillis;

    public MDTGame() {
        this.gameId = UUID.randomUUID();
        this.startTime = LocalDateTime.now();
        this.state = MDTGameState.LOBBY;
        this.players = new ConcurrentHashMap<>();
        this.teamBeds = new ConcurrentHashMap<>();
        this.neutralBeds = new ConcurrentHashMap<>();
        this.generators = Collections.synchronizedList(new ArrayList<>());

        // Initialiser les points
        this.teamPoints = new ConcurrentHashMap<>();
        this.teamKills = new ConcurrentHashMap<>();
        for (MDTTeam team : MDTTeam.values()) {
            teamPoints.put(team, new AtomicInteger(0));
            teamKills.put(team, new AtomicInteger(0));
        }

        this.gameStartTimeMillis = 0;
    }

    // ==================== JOUEURS ====================
    public void addPlayer(MDTPlayer player) { players.put(player.getPlayerUuid(), player); }
    public void removePlayer(UUID playerUuid) { players.remove(playerUuid); }
    public MDTPlayer getPlayer(UUID playerUuid) { return players.get(playerUuid); }
    public boolean hasPlayer(UUID playerUuid) { return players.containsKey(playerUuid); }
    public Collection<MDTPlayer> getAllPlayers() { return players.values(); }
    public int getPlayerCount() { return players.size(); }

    public List<MDTPlayer> getAlivePlayersOfTeam(MDTTeam team) {
        return players.values().stream()
                .filter(p -> p.getTeam() == team && !p.isEliminated())
                .collect(Collectors.toList());
    }
    
    public List<MDTPlayer> getTeamPlayers(MDTTeam team) {
        return players.values().stream()
                .filter(p -> p.getTeam() == team)
                .collect(Collectors.toList());
    }

    public int getTeamPlayerCount(MDTTeam team) {
        return (int) players.values().stream().filter(p -> p.getTeam() == team).count();
    }

    // ==================== LITS ====================
    public void addTeamBed(MDTBed bed) { teamBeds.put(bed.getId(), bed); }
    public void addNeutralBed(MDTBed bed) { neutralBeds.put(bed.getId(), bed); }
    
    public MDTBed getTeamBed(MDTTeam team) {
        return teamBeds.values().stream().filter(b -> b.getOwnerTeam() == team).findFirst().orElse(null);
    }

    public MDTBed getBedAtLocation(Location location) {
        for (MDTBed bed : teamBeds.values()) {
            if (bed.isAtLocation(location)) return bed;
        }
        for (MDTBed bed : neutralBeds.values()) {
            if (bed.isAtLocation(location)) return bed;
        }
        return null;
    }

    public boolean isTeamBedDestroyed(MDTTeam team) {
        MDTBed bed = getTeamBed(team);
        return bed == null || bed.isDestroyed();
    }

    public boolean canTeamRespawn(MDTTeam team) {
        return !isTeamBedDestroyed(team);
    }

    // ==================== GÉNÉRATEURS ====================
    public void addGenerator(MDTGenerator generator) { generators.add(generator); }
    public List<MDTGenerator> getGenerators() { return generators; }

    // ==================== ÉTAT ====================
    public UUID getGameId() { return gameId; }
    public MDTGameState getState() { return state; }
    public void setState(MDTGameState state) { this.state = state; }
    public boolean isEnded() { return state == MDTGameState.ENDED; }

    public MDTTeam getWinningTeam() { return winningTeam; }
    public void setWinningTeam(MDTTeam winningTeam) { this.winningTeam = winningTeam; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public MDTTeam checkForWinner() {
        List<MDTPlayer> aliveRed = getAlivePlayersOfTeam(MDTTeam.RED);
        List<MDTPlayer> aliveBlue = getAlivePlayersOfTeam(MDTTeam.BLUE);
        if (aliveRed.isEmpty() && !aliveBlue.isEmpty()) return MDTTeam.BLUE;
        if (aliveBlue.isEmpty() && !aliveRed.isEmpty()) return MDTTeam.RED;
        return null;
    }

    // ==================== SYSTÈME DE POINTS ====================

    /**
     * Ajoute des points à une équipe
     * @param team L'équipe qui gagne des points
     * @param points Le nombre de points à ajouter
     */
    public void addTeamPoints(MDTTeam team, int points) {
        if (team != null && teamPoints.containsKey(team)) {
            teamPoints.get(team).addAndGet(points);
        }
    }

    /**
     * Retourne les points d'une équipe
     */
    public int getTeamPoints(MDTTeam team) {
        return team != null && teamPoints.containsKey(team) ? teamPoints.get(team).get() : 0;
    }

    /**
     * Ajoute un kill à une équipe
     */
    public void addTeamKill(MDTTeam team) {
        if (team != null && teamKills.containsKey(team)) {
            teamKills.get(team).incrementAndGet();
        }
    }

    /**
     * Retourne le nombre de kills d'une équipe
     */
    public int getTeamKills(MDTTeam team) {
        return team != null && teamKills.containsKey(team) ? teamKills.get(team).get() : 0;
    }

    /**
     * Retourne le nombre total de kills de toutes les équipes
     */
    public int getTotalKills() {
        int total = 0;
        for (AtomicInteger kills : teamKills.values()) {
            total += kills.get();
        }
        return total;
    }

    /**
     * Retourne le nombre de joueurs encore en vie (non éliminés)
     */
    public int getAlivePlayers() {
        return (int) players.values().stream()
                .filter(p -> !p.isEliminated())
                .count();
    }

    /**
     * Détermine le gagnant par les points
     * @return L'équipe avec le plus de points, ou null si égalité
     */
    public MDTTeam getWinnerByPoints() {
        int redPoints = getTeamPoints(MDTTeam.RED);
        int bluePoints = getTeamPoints(MDTTeam.BLUE);

        if (redPoints > bluePoints) return MDTTeam.RED;
        if (bluePoints > redPoints) return MDTTeam.BLUE;
        return null; // Égalité
    }

    // ==================== TIMER DE JEU ====================

    /**
     * Définit le moment où la partie a commencé
     */
    public void setGameStartTimeMillis(long timeMillis) {
        this.gameStartTimeMillis = timeMillis;
    }

    /**
     * Retourne le temps de jeu écoulé en secondes
     */
    public int getElapsedTimeSeconds() {
        if (gameStartTimeMillis == 0) return 0;
        return (int) ((System.currentTimeMillis() - gameStartTimeMillis) / 1000);
    }

    /**
     * Retourne le temps de jeu formaté (MM:SS)
     */
    public String getFormattedElapsedTime() {
        int seconds = getElapsedTimeSeconds();
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    /**
     * Retourne le temps restant formaté (MM:SS)
     * @param maxDurationSeconds Durée maximale de la partie en secondes
     */
    public String getFormattedRemainingTime(int maxDurationSeconds) {
        int elapsed = getElapsedTimeSeconds();
        int remaining = Math.max(0, maxDurationSeconds - elapsed);
        int minutes = remaining / 60;
        int secs = remaining % 60;
        return String.format("%02d:%02d", minutes, secs);
    }
}