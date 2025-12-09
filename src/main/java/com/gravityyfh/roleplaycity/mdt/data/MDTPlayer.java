package com.gravityyfh.roleplaycity.mdt.data;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Représente un joueur participant à une partie MDT Rush
 */
public class MDTPlayer {
    private final UUID playerUuid;
    private final String playerName;
    private MDTTeam team;
    private int bonusHearts;
    private boolean eliminated;
    private boolean spectating;
    private int kills;
    private int deaths;
    private int bedsDestroyed;

    public MDTPlayer(UUID playerUuid, String playerName) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.team = null;
        this.bonusHearts = 0;
        this.eliminated = false;
        this.spectating = false;
        this.kills = 0;
        this.deaths = 0;
        this.bedsDestroyed = 0;
    }

    public MDTPlayer(Player player) {
        this(player.getUniqueId(), player.getName());
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public Player getPlayer() {
        return Bukkit.getPlayer(playerUuid);
    }

    public boolean isOnline() {
        return getPlayer() != null;
    }

    public MDTTeam getTeam() {
        return team;
    }

    public void setTeam(MDTTeam team) {
        this.team = team;
    }

    public boolean hasTeam() {
        return team != null;
    }

    public int getBonusHearts() {
        return bonusHearts;
    }

    public void addBonusHearts(int hearts, int maxBonus) {
        this.bonusHearts = Math.min(this.bonusHearts + hearts, maxBonus);
    }

    public void setBonusHearts(int bonusHearts) {
        this.bonusHearts = bonusHearts;
    }

    /**
     * Retourne le nombre total de cœurs (10 de base + bonus)
     */
    public int getTotalHearts() {
        return 10 + bonusHearts;
    }

    /**
     * Retourne la vie maximale en points de vie (2 points = 1 cœur)
     */
    public double getMaxHealth() {
        return getTotalHearts() * 2.0;
    }

    public boolean isEliminated() {
        return eliminated;
    }

    public void setEliminated(boolean eliminated) {
        this.eliminated = eliminated;
    }

    public boolean isSpectating() {
        return spectating;
    }

    public void setSpectating(boolean spectating) {
        this.spectating = spectating;
    }

    public int getKills() {
        return kills;
    }

    public void addKill() {
        this.kills++;
    }

    public int getDeaths() {
        return deaths;
    }

    public void addDeath() {
        this.deaths++;
    }

    public int getBedsDestroyed() {
        return bedsDestroyed;
    }

    public void addBedDestroyed() {
        this.bedsDestroyed++;
    }

    /**
     * Applique les cœurs bonus au joueur
     */
    public void applyBonusHearts() {
        Player player = getPlayer();
        if (player != null) {
            double maxHealth = getMaxHealth();
            player.setMaxHealth(maxHealth);
            // Ne pas dépasser la vie max
            if (player.getHealth() > maxHealth) {
                player.setHealth(maxHealth);
            }
        }
    }

    /**
     * Réinitialise la vie du joueur à la normale
     */
    public void resetHealth() {
        Player player = getPlayer();
        if (player != null) {
            player.setMaxHealth(20.0);
            player.setHealth(20.0);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        MDTPlayer other = (MDTPlayer) obj;
        return playerUuid.equals(other.playerUuid);
    }

    @Override
    public int hashCode() {
        return playerUuid.hashCode();
    }
}
