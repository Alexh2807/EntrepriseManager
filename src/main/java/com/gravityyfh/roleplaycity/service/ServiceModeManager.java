package com.gravityyfh.roleplaycity.service;

import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.gravityyfh.roleplaycity.entreprise.model.*;
import com.gravityyfh.roleplaycity.RoleplayCity;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire du mode service pour les employés d'entreprise
 */
public class ServiceModeManager {

    private final RoleplayCity plugin;
    private final EntrepriseManagerLogic entrepriseLogic;
    private final Economy economy;
    private final ServicePersistenceService persistenceService;

    // Stockage des données de service
    private final Map<UUID, ServiceModeData> serviceModeData = new ConcurrentHashMap<>();

    // BossBars pour afficher le statut
    private final Map<UUID, BossBar> playerBossBars = new ConcurrentHashMap<>();

    public ServiceModeManager(RoleplayCity plugin, EntrepriseManagerLogic entrepriseLogic, Economy economy, ServicePersistenceService persistenceService) {
        this.plugin = plugin;
        this.entrepriseLogic = entrepriseLogic;
        this.economy = economy;
        this.persistenceService = persistenceService;

        // Charger les sessions actives depuis la DB
        serviceModeData.putAll(persistenceService.loadActiveSessions());
        
        startBossBarUpdateTask();
    }

    /**
     * Toggle le mode service pour un joueur
     */
    public boolean toggleService(Player player, String enterpriseName) {
        UUID uuid = player.getUniqueId();
        ServiceModeData data = serviceModeData.get(uuid);

        if (data == null) {
            data = new ServiceModeData(uuid, player.getName());
            serviceModeData.put(uuid, data);
        }

        if (data.isActive()) {
            // Désactiver le service
            deactivateService(player);
            return false;
        } else {
            // Activer le service
            return activateService(player, enterpriseName);
        }
    }

    /**
     * Active le mode service pour un joueur
     */
    public boolean activateService(Player player, String enterpriseName) {
        UUID uuid = player.getUniqueId();

        // Vérifier qu'il n'est pas déjà en service ailleurs
        ServiceModeData existingData = serviceModeData.get(uuid);
        if (existingData != null && existingData.isActive()) {
            player.sendMessage(ChatColor.RED + "Vous êtes déjà en service pour l'entreprise '" +
                existingData.getActiveEnterprise() + "' !");
            player.sendMessage(ChatColor.YELLOW + "Utilisez /entreprise -> Mes entreprises -> NomEntreprise -> Mode Service OFF pour désactiver d'abord.");
            return false;
        }

        // Vérifier que le joueur fait partie de cette entreprise
        Entreprise entreprise = entrepriseLogic.getEntreprise(enterpriseName);
        if (entreprise == null) {
            player.sendMessage(ChatColor.RED + "Entreprise '" + enterpriseName + "' introuvable.");
            return false;
        }

        boolean isMember = entreprise.getGerant().equalsIgnoreCase(player.getName()) ||
                          entreprise.getEmployes().contains(player.getName());

        if (!isMember) {
            player.sendMessage(ChatColor.RED + "Vous n'êtes pas membre de l'entreprise '" + enterpriseName + "' !");
            return false;
        }

        // Activer le mode service
        ServiceModeData data = serviceModeData.computeIfAbsent(uuid,
            k -> new ServiceModeData(uuid, player.getName()));
        data.activate(enterpriseName);

        // Créer/mettre à jour la BossBar
        updateBossBar(player);

        player.sendMessage(ChatColor.GREEN + "✓ Mode Service ACTIVÉ pour " + ChatColor.GOLD + enterpriseName);
        player.sendMessage(ChatColor.GRAY + "• Les items récoltés ne tomberont pas au sol");
        player.sendMessage(ChatColor.GRAY + "• Vous gagnez 50% du montant des activités");
        player.sendMessage(ChatColor.GRAY + "• L'entreprise gagne 50% du montant");
        player.sendMessage(ChatColor.GRAY + "• Pas de restrictions de quotas");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);

        // Sauvegarder en DB
        persistenceService.saveSession(data);
        return true;
    }

    /**
     * Désactive le mode service pour un joueur
     */
    public void deactivateService(Player player) {
        UUID uuid = player.getUniqueId();
        ServiceModeData data = serviceModeData.get(uuid);

        if (data == null || !data.isActive()) {
            player.sendMessage(ChatColor.RED + "Vous n'êtes pas en mode service !");
            return;
        }

        String enterpriseName = data.getActiveEnterprise();
        long duration = data.getServiceDuration();
        double earned = data.getEarnedThisHour();

        data.deactivate();

        // Retirer la BossBar
        BossBar bossBar = playerBossBars.remove(uuid);
        if (bossBar != null) {
            bossBar.removePlayer(player);
        }

        player.sendMessage(ChatColor.YELLOW + "✓ Mode Service DÉSACTIVÉ");
        player.sendMessage(ChatColor.GRAY + "Entreprise: " + ChatColor.WHITE + enterpriseName);
        player.sendMessage(ChatColor.GRAY + "Durée: " + ChatColor.WHITE + formatDuration(duration));
        player.sendMessage(ChatColor.GRAY + "Gains cette heure: " + ChatColor.GREEN +
            String.format("%,.2f€", earned));
        player.sendMessage(ChatColor.YELLOW + "Vous êtes désormais soumis aux restrictions horaires.");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);

        // Sauvegarder la fin de session en DB
        persistenceService.saveSession(data);
    }

    /**
     * Arrêt forcé du service pour un joueur (ex: licenciement, départ)
     */
    public void forceStopService(UUID uuid) {
        ServiceModeData data = serviceModeData.get(uuid);
        if (data != null && data.isActive()) {
            data.deactivate();
            cleanupPlayer(uuid); // Retirer la bossbar
            persistenceService.saveSession(data);
            
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(ChatColor.RED + "Votre service a été interrompu (Vous n'êtes plus membre de l'entreprise).");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            }
        }
    }

    /**
     * Arrêt forcé de tous les employés d'une entreprise (ex: dissolution)
     */
    public void forceStopAllForCompany(String companyName) {
        for (ServiceModeData data : serviceModeData.values()) {
            if (data.isActive() && data.getActiveEnterprise().equals(companyName)) {
                forceStopService(data.getPlayerUUID());
            }
        }
    }

    /**
     * Vérifie si un joueur est en mode service
     */
    public boolean isInService(UUID playerUUID) {
        ServiceModeData data = serviceModeData.get(playerUUID);
        return data != null && data.isActive();
    }

    /**
     * Vérifie si un joueur est en service pour une entreprise spécifique
     */
    public boolean isInServiceFor(UUID playerUUID, String enterpriseName) {
        ServiceModeData data = serviceModeData.get(playerUUID);
        return data != null && data.isActive() &&
               enterpriseName.equals(data.getActiveEnterprise());
    }

    /**
     * Récupère le nom de l'entreprise en service
     */
    public String getActiveEnterprise(UUID playerUUID) {
        ServiceModeData data = serviceModeData.get(playerUUID);
        return (data != null && data.isActive()) ? data.getActiveEnterprise() : null;
    }

    /**
     * Enregistre un gain pour un joueur en service
     * Les gains sont ACCUMULÉS et payés au paiement horaire
     * Retourne la part de l'entreprise pour le CA
     */
    public double processServiceEarnings(Player player, double totalValue) {
        UUID uuid = player.getUniqueId();
        ServiceModeData data = serviceModeData.get(uuid);

        if (data == null || !data.isActive()) {
            return 0.0;
        }

        // Configuration du split (par défaut 50/50)
        double employeeCut = plugin.getConfig().getDouble("service-mode.revenue-split.employee", 50.0) / 100.0;
        double companyCut = plugin.getConfig().getDouble("service-mode.revenue-split.company", 50.0) / 100.0;

        double playerAmount = totalValue * employeeCut;
        double companyAmount = totalValue * companyCut;

        // ACCUMULER les gains (ne PAS payer immédiatement)
        if (playerAmount > 0) {
            data.addEarnings(playerAmount);
            
            // Sauvegarder les gains accumulés immédiatement (persistance)
            persistenceService.saveSession(data);

            // Message d'action bar pour montrer l'accumulation
            player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                net.md_5.bungee.api.chat.TextComponent.fromLegacyText(
                    ChatColor.YELLOW + "+" + String.format("%.2f€", playerAmount) +
                    ChatColor.GRAY + " accumulé (Service) " + ChatColor.GOLD + "Total: " +
                    String.format("%.2f€", data.getEarnedThisHour())
                ));

            plugin.getLogger().fine("Gains accumulés pour " + player.getName() + " en service: +" +
                String.format("%.2f€", playerAmount) + " (Total: " + String.format("%.2f€", data.getEarnedThisHour()) + ")");
        }

        return companyAmount; // Retourner la part de l'entreprise
    }

    /**
     * Récupère et reset les gains accumulés d'un joueur pour le paiement horaire
     * Cette méthode est appelée lors du paiement horaire
     */
    public double getAndResetAccumulatedEarnings(UUID playerUUID) {
        ServiceModeData data = serviceModeData.get(playerUUID);
        if (data == null) {
            return 0.0;
        }

        double earned = data.getEarnedThisHour();
        data.resetHourlyEarnings();
        persistenceService.saveSession(data);
        return earned;
    }

    /**
     * Mise à jour de la BossBar pour un joueur
     */
    private void updateBossBar(Player player) {
        UUID uuid = player.getUniqueId();
        ServiceModeData data = serviceModeData.get(uuid);

        if (data == null || !data.isActive()) {
            // Retirer la BossBar si inactive
            BossBar bossBar = playerBossBars.remove(uuid);
            if (bossBar != null) {
                bossBar.removePlayer(player);
            }
            return;
        }

        BossBar bossBar = playerBossBars.get(uuid);
        if (bossBar == null) {
            bossBar = Bukkit.createBossBar(
                "",
                BarColor.GREEN,
                BarStyle.SOLID
            );
            bossBar.addPlayer(player);
            playerBossBars.put(uuid, bossBar);
        }

        // Récupérer les informations d'entreprise
        Entreprise entreprise = entrepriseLogic.getEntreprise(data.getActiveEnterprise());
        int quotaUtilise = 0;
        int quotaLimite = -1;
        double quotaPercentage = 0.0;

        if (entreprise != null) {
            String typeEntreprise = entreprise.getType();
            quotaUtilise = entrepriseLogic.getQuotaUtilisePourEntreprise(uuid, typeEntreprise);
            quotaLimite = entrepriseLogic.getLimiteRestrictionActuelle(entreprise, typeEntreprise);

            if (quotaLimite > 0) {
                quotaPercentage = (double) quotaUtilise / quotaLimite;
            }
        }

        // Mettre à jour le texte
        String earnings = String.format("%.2f€", data.getEarnedThisHour());

        // Construire la partie quota
        String quotaDisplay;
        if (quotaLimite == -1) {
            quotaDisplay = ChatColor.GREEN + "Quota: " + quotaUtilise + "/∞";
        } else {
            int percentage = (int) (quotaPercentage * 100);
            quotaDisplay = ChatColor.GRAY + "Quota: " + ChatColor.WHITE + quotaUtilise + "/" + quotaLimite +
                          ChatColor.GRAY + " (" + percentage + "%)";
        }

        String title = ChatColor.GREEN + "⚒ EN SERVICE " + ChatColor.WHITE + "| " +
                      ChatColor.GOLD + data.getActiveEnterprise() + ChatColor.WHITE + " | " +
                      ChatColor.YELLOW + "Gains: " + earnings + ChatColor.WHITE + " | " +
                      quotaDisplay;

        bossBar.setTitle(title);

        // Définir la progression et la couleur selon le % de quota
        if (quotaLimite == -1) {
            // Quota illimité
            bossBar.setProgress(1.0);
            bossBar.setColor(BarColor.GREEN);
        } else {
            // Quota limité: ajuster la barre
            double progress = Math.min(1.0, quotaPercentage);
            bossBar.setProgress(progress);

            // Couleur dynamique selon le %
            if (quotaPercentage >= 0.81) {
                bossBar.setColor(BarColor.RED);    // 81-100%: Rouge
            } else if (quotaPercentage >= 0.51) {
                bossBar.setColor(BarColor.YELLOW); // 51-80%: Jaune
            } else {
                bossBar.setColor(BarColor.GREEN);  // 0-50%: Vert
            }
        }
    }

    /**
     * Tâche périodique pour mettre à jour les BossBars
     */
    private void startBossBarUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateBossBar(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Chaque seconde
    }

    /**
     * Reset les gains horaires (appelé par le système de paie)
     */
    public void resetHourlyEarnings() {
        for (ServiceModeData data : serviceModeData.values()) {
            data.resetHourlyEarnings();
            persistenceService.saveSession(data);
        }
    }

    /**
     * Nettoie les BossBars pour un joueur qui se déconnecte
     */
    public void cleanupPlayer(UUID playerUUID) {
        BossBar bossBar = playerBossBars.remove(playerUUID);
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    /**
     * Formate une durée en millisecondes en format lisible
     */
    private String formatDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%dh%02dm", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%dm%02ds", minutes, seconds % 60);
        } else {
            return seconds + "s";
        }
    }

    /**
     * Récupère les données de service d'un joueur
     */
    public ServiceModeData getServiceData(UUID playerUUID) {
        return serviceModeData.get(playerUUID);
    }
}