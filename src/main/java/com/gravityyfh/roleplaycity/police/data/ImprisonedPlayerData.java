package com.gravityyfh.roleplaycity.police.data;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Gestion des données des joueurs emprisonnés
 * Stocke l'état des prisonniers, leurs boss bars, et l'historique
 */
public class ImprisonedPlayerData {

    // Joueurs actuellement emprisonnés (UUID -> PrisonData)
    private final Map<UUID, PrisonData> imprisonedPlayers = new HashMap<>();

    // Boss bars pour afficher le temps restant
    private final Map<UUID, BossBar> prisonBossBars = new HashMap<>();

    // Historique des emprisonnements (max 50 par joueur)
    private final Map<UUID, List<PrisonData>> prisonHistory = new HashMap<>();

    private static final int MAX_HISTORY_PER_PLAYER = 50;

    /**
     * Vérifie si un joueur est emprisonné
     */
    public boolean isImprisoned(Player player) {
        return isImprisoned(player.getUniqueId());
    }

    public boolean isImprisoned(UUID uuid) {
        return imprisonedPlayers.containsKey(uuid);
    }

    /**
     * Emprisonne un joueur
     */
    public void imprison(UUID playerUuid, String townName, String plotIdentifier,
                        int durationMinutes, String reason, UUID imprisonedBy) {

        PrisonData prisonData = new PrisonData(
            playerUuid,
            townName,
            plotIdentifier,
            LocalDateTime.now(),
            durationMinutes,
            reason,
            imprisonedBy
        );

        imprisonedPlayers.put(playerUuid, prisonData);

        // Créer la boss bar
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            createBossBar(player, prisonData);
        }
    }

    /**
     * Crée la boss bar pour un prisonnier
     */
    private void createBossBar(Player player, PrisonData prisonData) {
        String title = "⛓️ Prison : " + prisonData.getFormattedRemainingTime();

        BossBar bossBar = Bukkit.createBossBar(
            title,
            BarColor.RED,
            BarStyle.SOLID
        );

        // Progression basée sur le temps restant
        long remainingSeconds = prisonData.getRemainingSeconds();
        long totalSeconds = prisonData.getCurrentDurationMinutes() * 60L;
        double progress = (double) remainingSeconds / totalSeconds;

        bossBar.setProgress(Math.min(1.0, Math.max(0.0, progress)));
        bossBar.addPlayer(player);
        bossBar.setVisible(true);

        prisonBossBars.put(player.getUniqueId(), bossBar);
    }

    /**
     * Libère un joueur de prison
     */
    public void release(UUID playerUuid) {
        PrisonData prisonData = imprisonedPlayers.remove(playerUuid);

        if (prisonData != null) {
            // Ajouter à l'historique
            addToHistory(playerUuid, prisonData);
        }

        // Retirer la boss bar
        BossBar bossBar = prisonBossBars.remove(playerUuid);
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar.setVisible(false);
        }
    }

    /**
     * Ajoute une entrée à l'historique
     */
    private void addToHistory(UUID playerUuid, PrisonData prisonData) {
        List<PrisonData> history = prisonHistory.computeIfAbsent(playerUuid, k -> new ArrayList<>());

        history.add(prisonData);

        // Limiter à MAX_HISTORY_PER_PLAYER entrées
        while (history.size() > MAX_HISTORY_PER_PLAYER) {
            history.remove(0);
        }
    }

    /**
     * Met à jour la boss bar d'un prisonnier
     */
    public void updateBossBar(UUID playerUuid) {
        PrisonData prisonData = imprisonedPlayers.get(playerUuid);
        BossBar bossBar = prisonBossBars.get(playerUuid);

        if (prisonData == null || bossBar == null) {
            return;
        }

        // Mettre à jour le titre
        String title = "⛓️ Prison : " + prisonData.getFormattedRemainingTime();
        bossBar.setTitle(title);

        // Mettre à jour la progression
        long remainingSeconds = prisonData.getRemainingSeconds();
        long totalSeconds = prisonData.getCurrentDurationMinutes() * 60L;
        double progress = (double) remainingSeconds / totalSeconds;

        bossBar.setProgress(Math.min(1.0, Math.max(0.0, progress)));

        // Changer la couleur selon le temps restant
        if (progress <= 0.25) {
            bossBar.setColor(BarColor.GREEN); // Presque libre
        } else if (progress <= 0.5) {
            bossBar.setColor(BarColor.YELLOW);
        } else {
            bossBar.setColor(BarColor.RED);
        }
    }

    /**
     * Obtient les données de prison d'un joueur
     */
    public PrisonData getPrisonData(UUID playerUuid) {
        return imprisonedPlayers.get(playerUuid);
    }

    /**
     * Obtient tous les joueurs emprisonnés
     */
    public Map<UUID, PrisonData> getAllImprisoned() {
        return new HashMap<>(imprisonedPlayers);
    }

    /**
     * Obtient l'historique d'emprisonnement d'un joueur
     */
    public List<PrisonData> getHistory(UUID playerUuid) {
        return new ArrayList<>(prisonHistory.getOrDefault(playerUuid, Collections.emptyList()));
    }

    /**
     * Prolonge la durée de prison d'un joueur
     */
    public void extendDuration(UUID playerUuid, int additionalMinutes) {
        PrisonData prisonData = imprisonedPlayers.get(playerUuid);
        if (prisonData != null) {
            prisonData.extendDuration(additionalMinutes);
            updateBossBar(playerUuid);
        }
    }

    /**
     * Obtient tous les prisonniers d'une ville spécifique
     */
    public List<PrisonData> getPrisonersByTown(String townName) {
        List<PrisonData> result = new ArrayList<>();

        for (PrisonData data : imprisonedPlayers.values()) {
            if (data.getTownName().equalsIgnoreCase(townName)) {
                result.add(data);
            }
        }

        return result;
    }

    /**
     * Obtient tous les prisonniers d'un plot spécifique
     */
    public List<PrisonData> getPrisonersByPlot(String townName, String plotIdentifier) {
        List<PrisonData> result = new ArrayList<>();

        for (PrisonData data : imprisonedPlayers.values()) {
            if (data.getTownName().equalsIgnoreCase(townName) &&
                data.getPlotIdentifier().equals(plotIdentifier)) {
                result.add(data);
            }
        }

        return result;
    }

    /**
     * Libère tous les prisonniers d'un plot (en cas de suppression du claim)
     */
    public void releaseAllFromPlot(String townName, String plotIdentifier) {
        List<UUID> toRelease = new ArrayList<>();

        for (Map.Entry<UUID, PrisonData> entry : imprisonedPlayers.entrySet()) {
            PrisonData data = entry.getValue();
            if (data.getTownName().equalsIgnoreCase(townName) &&
                data.getPlotIdentifier().equals(plotIdentifier)) {
                toRelease.add(entry.getKey());
            }
        }

        toRelease.forEach(this::release);
    }

    /**
     * Restaure la boss bar pour un joueur qui se reconnecte
     */
    public void restoreBossBar(Player player) {
        UUID uuid = player.getUniqueId();
        PrisonData prisonData = imprisonedPlayers.get(uuid);

        if (prisonData != null) {
            // Recréer la boss bar
            createBossBar(player, prisonData);
        }
    }

    /**
     * Nettoie toutes les données
     */
    public void clear() {
        // Retirer toutes les boss bars
        prisonBossBars.values().forEach(bossBar -> {
            bossBar.removeAll();
            bossBar.setVisible(false);
        });

        imprisonedPlayers.clear();
        prisonBossBars.clear();
        // On garde l'historique même après clear
    }

    /**
     * Obtient le nombre de joueurs emprisonnés
     */
    public int getImprisonedCount() {
        return imprisonedPlayers.size();
    }
}
