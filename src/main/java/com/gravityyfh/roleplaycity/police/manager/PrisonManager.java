package com.gravityyfh.roleplaycity.police.manager;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.police.data.HandcuffedPlayerData;
import com.gravityyfh.roleplaycity.police.data.ImprisonedPlayerData;
import com.gravityyfh.roleplaycity.police.data.PrisonData;
import com.gravityyfh.roleplaycity.police.data.PrisonSpawnPoint;
import com.gravityyfh.roleplaycity.town.data.MunicipalSubType;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownRole;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire du système de prison
 * Gère l'emprisonnement, la libération, et les spawns de prison
 */
public class PrisonManager {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final HandcuffedPlayerData handcuffedPlayerData;
    private final ImprisonedPlayerData imprisonedPlayerData;

    // Points de spawn des prisons (clé: "townName:plotIdentifier")
    private final Map<String, PrisonSpawnPoint> prisonSpawns;

    // Tâche du scheduler pour vérifier les expirations
    private BukkitTask expirationCheckTask;

    public PrisonManager(RoleplayCity plugin, TownManager townManager, HandcuffedPlayerData handcuffedPlayerData) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.handcuffedPlayerData = handcuffedPlayerData;
        this.imprisonedPlayerData = new ImprisonedPlayerData();
        this.prisonSpawns = new ConcurrentHashMap<>();
    }

    /**
     * Obtient les données des prisonniers
     */
    public ImprisonedPlayerData getImprisonedData() {
        return imprisonedPlayerData;
    }

    /**
     * Démarre le scheduler de vérification des expirations
     */
    public void startExpirationChecker() {
        // Vérifier toutes les secondes
        expirationCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            checkExpirations();
            updateAllBossBars();
        }, 20L, 20L); // 20 ticks = 1 seconde
    }

    /**
     * Arrête le scheduler
     */
    public void stopExpirationChecker() {
        if (expirationCheckTask != null) {
            expirationCheckTask.cancel();
            expirationCheckTask = null;
        }
    }

    /**
     * Libère tous les prisonniers (appelé au shutdown du serveur)
     */
    public void releaseAllPrisoners(boolean automated) {
        List<UUID> allPrisoners = new ArrayList<>(imprisonedPlayerData.getAllImprisoned().keySet());
        for (UUID prisonerUuid : allPrisoners) {
            releasePrisoner(prisonerUuid, automated);
        }
    }

    /**
     * Vérifie et libère les prisonniers dont la peine est expirée
     */
    private void checkExpirations() {
        List<UUID> toRelease = new ArrayList<>();

        for (Map.Entry<UUID, PrisonData> entry : imprisonedPlayerData.getAllImprisoned().entrySet()) {
            PrisonData prisonData = entry.getValue();

            if (prisonData.isExpired()) {
                toRelease.add(entry.getKey());
            }
        }

        // Libérer les prisonniers expirés
        for (UUID playerUuid : toRelease) {
            releasePrisoner(playerUuid, true);
        }
    }

    /**
     * Met à jour toutes les boss bars
     */
    private void updateAllBossBars() {
        for (UUID playerUuid : imprisonedPlayerData.getAllImprisoned().keySet()) {
            imprisonedPlayerData.updateBossBar(playerUuid);
        }
    }

    /**
     * Définit le spawn de prison pour un plot COMMISSARIAT
     */
    public boolean setPrisonSpawn(Town town, Plot plot, Location location, Player setter) {
        // Vérifier que c'est bien un COMMISSARIAT
        if (plot.getMunicipalSubType() != MunicipalSubType.COMMISSARIAT) {
            setter.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            setter.sendMessage("§c✖ §lERREUR");
            setter.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            setter.sendMessage("§7Ce plot n'est pas un COMMISSARIAT");
            setter.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            return false;
        }

        // Vérifier le rôle
        TownRole role = town.getMemberRole(setter.getUniqueId());
        if (role != TownRole.MAIRE && role != TownRole.ADJOINT) {
            setter.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            setter.sendMessage("§c✖ §lPERMISSION REFUSÉE");
            setter.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            setter.sendMessage("§7Réservé au maire et adjoints");
            setter.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            return false;
        }

        // Créer le spawn point
        String plotIdentifier = plot.getPlotNumber();
        PrisonSpawnPoint spawnPoint = new PrisonSpawnPoint(town.getName(), plotIdentifier, location);

        prisonSpawns.put(spawnPoint.getKey(), spawnPoint);

        // Sauvegarder dans le plot
        plot.setPrisonSpawn(location);

        // Message de confirmation
        setter.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        setter.sendMessage("§a✔ §lSPAWN DÉFINI");
        setter.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        setter.sendMessage("§7Le spawn de prison a été défini");
        setter.sendMessage("§7à votre position actuelle");
        setter.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        plugin.getLogger().info("Spawn prison défini pour " + town.getName() + " plot " + plotIdentifier +
            " par " + setter.getName());

        return true;
    }

    /**
     * Emprisonne un joueur
     */
    public boolean imprisonPlayer(Player prisoner, Town town, Plot plot, int durationMinutes,
                                  String reason, Player policier) {

        // Vérifier que c'est bien un COMMISSARIAT
        if (plot.getMunicipalSubType() != MunicipalSubType.COMMISSARIAT) {
            policier.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            policier.sendMessage("§c✖ Ce plot n'est pas un COMMISSARIAT");
            policier.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            return false;
        }

        // Vérifier que le joueur est menotté
        if (!handcuffedPlayerData.isHandcuffed(prisoner)) {
            policier.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            policier.sendMessage("§c✖ Le joueur doit être menotté");
            policier.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            return false;
        }

        // Vérifier qu'un spawn prison est défini
        if (!plot.hasPrisonSpawn() || plot.getPrisonSpawn() == null) {
            policier.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            policier.sendMessage("§c✖ §lSPAWN NON DÉFINI");
            policier.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            policier.sendMessage("§7Aucun spawn de prison n'a été");
            policier.sendMessage("§7défini pour ce COMMISSARIAT");
            policier.sendMessage("§7Plot: §e" + plot.getPlotNumber());
            policier.sendMessage("§7hasPrisonSpawn: §e" + plot.hasPrisonSpawn());
            policier.sendMessage("§7getPrisonSpawn: §e" + (plot.getPrisonSpawn() != null ? "non-null" : "null"));
            policier.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

            // Debug: Lister tous les plots COMMISSARIAT de la ville
            policier.sendMessage("§6§lDEBUG - Plots COMMISSARIAT:");
            for (Plot p : town.getPlots().values()) {
                if (p.getMunicipalSubType() == MunicipalSubType.COMMISSARIAT) {
                    policier.sendMessage("§7- Plot " + p.getPlotNumber() + " | Spawn: " +
                        (p.hasPrisonSpawn() ? "§aOUI" : "§cNON"));
                }
            }

            return false;
        }

        // Récupérer l'identifiant du plot
        String plotIdentifier = plot.getPlotNumber();

        // Vérifier la durée maximale
        int maxDuration = plugin.getConfig().getInt("prison-system.max-duration-minutes", 60);
        if (durationMinutes > maxDuration) {
            policier.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            policier.sendMessage("§c✖ Durée maximale: " + maxDuration + " minutes");
            policier.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            return false;
        }

        // Retirer les menottes
        handcuffedPlayerData.removeHandcuffs(prisoner);

        // Téléporter au spawn prison
        Location prisonSpawn = plot.getPrisonSpawn();
        prisoner.teleport(prisonSpawn);

        // Emprisonner
        imprisonedPlayerData.imprison(
            prisoner.getUniqueId(),
            town.getName(),
            plotIdentifier,
            durationMinutes,
            reason,
            policier.getUniqueId()
        );

        // Broadcast notification si activé
        boolean broadcastEnabled = plugin.getConfig().getBoolean("prison-system.notification-broadcast", true);
        if (broadcastEnabled) {
            String broadcast = String.format("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n" +
                "§c§l🚨 EMPRISONNEMENT\n" +
                "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n" +
                "§7Prisonnier: §e%s\n" +
                "§7Durée: §c%d minutes\n" +
                "§7Raison: §f%s\n" +
                "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                prisoner.getName(), durationMinutes, reason);

            Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(broadcast));
        }

        // Message au prisonnier
        prisoner.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        prisoner.sendMessage("§c§l⛓️ VOUS ÊTES EN PRISON");
        prisoner.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        prisoner.sendMessage("§7Durée: §c" + durationMinutes + " minutes");
        prisoner.sendMessage("§7Raison: §f" + reason);
        prisoner.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        // Message au policier
        policier.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        policier.sendMessage("§a✔ §lEMPRISONNEMENT EFFECTUÉ");
        policier.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        policier.sendMessage("§7" + prisoner.getName() + " a été emprisonné");
        policier.sendMessage("§7pour " + durationMinutes + " minutes");
        policier.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        plugin.getLogger().info("Emprisonnement: " + prisoner.getName() + " par " + policier.getName() +
            " (" + durationMinutes + "min, " + reason + ")");

        return true;
    }

    /**
     * Libère un prisonnier
     */
    public void releasePrisoner(UUID playerUuid, boolean automated) {
        PrisonData prisonData = imprisonedPlayerData.getPrisonData(playerUuid);

        if (prisonData == null) {
            return;
        }

        // Libérer
        imprisonedPlayerData.release(playerUuid);

        // Téléporter au spawn si configuré et joueur en ligne
        Player player = Bukkit.getPlayer(playerUuid);

        if (player != null && player.isOnline()) {
            boolean teleportOnRelease = plugin.getConfig().getBoolean("prison-system.teleport-on-release", true);

            if (teleportOnRelease) {
                // Téléporter au spawn principal
                Location spawnLocation = Bukkit.getWorlds().get(0).getSpawnLocation();
                player.teleport(spawnLocation);
            }

            // Message de libération
            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage("§a✔ §lVOUS ÊTES LIBRE");
            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

            if (automated) {
                player.sendMessage("§7Votre peine de prison est terminée");
            } else {
                player.sendMessage("§7Vous avez été libéré par un policier");
            }

            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        }

        plugin.getLogger().info("Libération: " + (player != null ? player.getName() : playerUuid.toString()) +
            (automated ? " (automatique)" : " (manuelle)"));
    }

    /**
     * Prolonge la durée d'emprisonnement
     */
    public boolean extendPrison(UUID playerUuid, int additionalMinutes, Player extender) {
        PrisonData prisonData = imprisonedPlayerData.getPrisonData(playerUuid);

        if (prisonData == null) {
            extender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            extender.sendMessage("§c✖ Ce joueur n'est pas emprisonné");
            extender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            return false;
        }

        // Vérifier la durée maximale
        int maxDuration = plugin.getConfig().getInt("prison-system.max-duration-minutes", 60);
        int newDuration = prisonData.getCurrentDurationMinutes() + additionalMinutes;

        if (newDuration > maxDuration) {
            extender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            extender.sendMessage("§c✖ Durée maximale: " + maxDuration + " minutes");
            extender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            return false;
        }

        // Prolonger
        imprisonedPlayerData.extendDuration(playerUuid, additionalMinutes);

        // Notifier le prisonnier
        Player prisoner = Bukkit.getPlayer(playerUuid);
        if (prisoner != null && prisoner.isOnline()) {
            prisoner.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            prisoner.sendMessage("§c§l⏱ PROLONGATION DE PEINE");
            prisoner.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            prisoner.sendMessage("§7Temps ajouté: §c+" + additionalMinutes + " minutes");
            prisoner.sendMessage("§7Nouveau temps restant: §c" + imprisonedPlayerData.getPrisonData(playerUuid).getFormattedRemainingTime());
            prisoner.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        }

        // Message au policier
        extender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        extender.sendMessage("§a✔ §lPROLONGATION EFFECTUÉE");
        extender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        extender.sendMessage("§7+" + additionalMinutes + " minutes ajoutées");
        extender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        plugin.getLogger().info("Prolongation: " + (prisoner != null ? prisoner.getName() : playerUuid.toString()) +
            " (+" + additionalMinutes + "min par " + extender.getName() + ")");

        return true;
    }

    /**
     * Transfère un prisonnier vers un autre COMMISSARIAT
     */
    public boolean transferPrisoner(UUID playerUuid, Town targetTown, Plot targetPlot, Player transferrer) {
        PrisonData currentPrison = imprisonedPlayerData.getPrisonData(playerUuid);

        if (currentPrison == null) {
            transferrer.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            transferrer.sendMessage("§c✖ Ce joueur n'est pas emprisonné");
            transferrer.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            return false;
        }

        // Vérifier que le plot cible est un COMMISSARIAT
        if (targetPlot.getMunicipalSubType() != MunicipalSubType.COMMISSARIAT) {
            transferrer.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            transferrer.sendMessage("§c✖ Le plot cible n'est pas un COMMISSARIAT");
            transferrer.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            return false;
        }

        // Vérifier qu'un spawn est défini
        if (targetPlot.getPrisonSpawn() == null) {
            transferrer.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            transferrer.sendMessage("§c✖ Aucun spawn défini dans le COMMISSARIAT cible");
            transferrer.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            return false;
        }

        // Libérer de l'ancienne prison
        imprisonedPlayerData.release(playerUuid);

        // Ré-emprisonner dans la nouvelle
        long remainingSeconds = currentPrison.getRemainingSeconds();
        int remainingMinutes = (int) Math.ceil(remainingSeconds / 60.0);

        imprisonedPlayerData.imprison(
            playerUuid,
            targetTown.getName(),
            targetPlot.getPlotNumber(),
            remainingMinutes,
            currentPrison.getReason(),
            currentPrison.getImprisonedBy()
        );

        // Téléporter si en ligne
        Player prisoner = Bukkit.getPlayer(playerUuid);
        if (prisoner != null && prisoner.isOnline()) {
            prisoner.teleport(targetPlot.getPrisonSpawn());

            prisoner.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            prisoner.sendMessage("§e§l🚐 TRANSFERT DE PRISON");
            prisoner.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            prisoner.sendMessage("§7Nouveau COMMISSARIAT: §f" + targetTown.getName());
            prisoner.sendMessage("§7Temps restant: §c" + imprisonedPlayerData.getPrisonData(playerUuid).getFormattedRemainingTime());
            prisoner.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        }

        // Message au transférant
        transferrer.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        transferrer.sendMessage("§a✔ §lTRANSFERT EFFECTUÉ");
        transferrer.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        transferrer.sendMessage("§7Le prisonnier a été transféré");
        transferrer.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        plugin.getLogger().info("Transfert: " + (prisoner != null ? prisoner.getName() : playerUuid.toString()) +
            " vers " + targetTown.getName() + " par " + transferrer.getName());

        return true;
    }

    /**
     * Libère tous les prisonniers d'un plot (en cas de suppression)
     */
    public void releaseAllFromPlot(String townName, String plotIdentifier) {
        imprisonedPlayerData.releaseAllFromPlot(townName, plotIdentifier);

        // Retirer le spawn
        prisonSpawns.remove(townName + ":" + plotIdentifier);

        plugin.getLogger().info("Libération de tous les prisonniers du plot " + townName + ":" + plotIdentifier);
    }

    /**
     * Restaure la boss bar pour un joueur qui se reconnecte
     */
    public void handlePlayerLogin(Player player) {
        if (imprisonedPlayerData.isImprisoned(player.getUniqueId())) {
            imprisonedPlayerData.restoreBossBar(player);
        }
    }

    // Getters

    public ImprisonedPlayerData getImprisonedPlayerData() {
        return imprisonedPlayerData;
    }

    public Map<String, PrisonSpawnPoint> getPrisonSpawns() {
        return new HashMap<>(prisonSpawns);
    }

    public void addPrisonSpawn(PrisonSpawnPoint spawnPoint) {
        prisonSpawns.put(spawnPoint.getKey(), spawnPoint);
    }

    /**
     * Nettoie toutes les données
     */
    public void clear() {
        stopExpirationChecker();
        imprisonedPlayerData.clear();
        prisonSpawns.clear();
    }
}
