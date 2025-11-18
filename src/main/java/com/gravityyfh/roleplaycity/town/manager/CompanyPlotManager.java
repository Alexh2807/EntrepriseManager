package com.gravityyfh.roleplaycity.town.manager;

import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Gère la logique entre les entreprises et les terrains PROFESSIONNEL
 * - Validation achat avec entreprise
 * - Gestion des taxes entreprise
 * - Système de dette et avertissements
 * - Saisie automatique des terrains
 * - Suppression d'entreprise → vente terrains
 */
public class CompanyPlotManager {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final EntrepriseManagerLogic entrepriseLogic;
    private final com.gravityyfh.roleplaycity.town.manager.DebtNotificationService debtNotificationService;

    // Délai de grâce: 7 jours avant saisie
    private static final long DEBT_GRACE_PERIOD_DAYS = 7;

    public CompanyPlotManager(RoleplayCity plugin, TownManager townManager, EntrepriseManagerLogic entrepriseLogic, com.gravityyfh.roleplaycity.town.manager.DebtNotificationService debtNotificationService) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.entrepriseLogic = entrepriseLogic;
        this.debtNotificationService = debtNotificationService;
    }

    /**
     * Valide qu'un joueur peut acheter un terrain PROFESSIONNEL
     * Vérifie qu'il possède une entreprise active
     */
    public boolean validateCompanyOwnership(Player player, Plot plot) {
        if (plot.getType() != PlotType.PROFESSIONNEL) {
            return true; // Pas besoin d'entreprise pour autres types
        }

        // Vérifier que le joueur a une entreprise
        String playerEntreprise = getPlayerCompanyName(player);
        if (playerEntreprise == null) {
            player.sendMessage(ChatColor.RED + "✗ Vous devez posséder une entreprise pour acheter un terrain PROFESSIONNEL !");
            player.sendMessage(ChatColor.YELLOW + "→ Discutez de votre projet avec le Maire pour obtenir un contrat d'entreprise");
            return false;
        }

        return true;
    }

    /**
     * Récupère le nom de l'entreprise du joueur (en tant que gérant)
     */
    public String getPlayerCompanyName(Player player) {
        // Parcourir toutes les entreprises pour trouver celle dont le joueur est gérant
        for (EntrepriseManagerLogic.Entreprise entreprise : entrepriseLogic.getEntreprises()) {
            String gerantUuidStr = entreprise.getGerantUUID();
            if (gerantUuidStr != null) {
                try {
                    UUID gerantUuid = UUID.fromString(gerantUuidStr);
                    if (gerantUuid.equals(player.getUniqueId())) {
                        return entreprise.getNom();
                    }
                } catch (IllegalArgumentException e) {
                    // UUID invalide, ignorer cette entreprise
                    plugin.getLogger().warning("UUID invalide pour entreprise " + entreprise.getNom() + ": " + gerantUuidStr);
                }
            }
        }
        return null;
    }

    /**
     * Récupère la PREMIÈRE entreprise d'un joueur (en tant que gérant)
     *
     * @deprecated Cette méthode retourne la PREMIÈRE entreprise du joueur de manière non-déterministe.
     * Si le joueur possède plusieurs entreprises, le comportement est imprévisible.
     * Utilisez {@link EnterpriseContextManager#getPlayerEnterprises(Player)} à la place,
     * qui retourne TOUTES les entreprises et permet au joueur de choisir.
     *
     * Cette méthode est conservée temporairement pour compatibilité backward,
     * mais sera supprimée dans une version future (v1.07+).
     *
     * @param player Le joueur
     * @return La première entreprise trouvée, ou null si aucune
     */
    @Deprecated
    public EntrepriseManagerLogic.Entreprise getPlayerCompany(Player player) {
        plugin.getLogger().warning("[DEPRECATED] getPlayerCompany() appelé pour " + player.getName() +
            ". Utilisez EnterpriseContextManager.getPlayerEnterprises() à la place.");

        for (EntrepriseManagerLogic.Entreprise entreprise : entrepriseLogic.getEntreprises()) {
            String gerantUuidStr = entreprise.getGerantUUID();
            if (gerantUuidStr != null) {
                try {
                    UUID gerantUuid = UUID.fromString(gerantUuidStr);
                    if (gerantUuid.equals(player.getUniqueId())) {
                        return entreprise; // Retourne la PREMIÈRE trouvée (non-déterministe!)
                    }
                } catch (IllegalArgumentException e) {
                    // UUID invalide, ignorer cette entreprise
                    plugin.getLogger().warning("UUID invalide pour entreprise " + entreprise.getNom() + ": " + gerantUuidStr);
                }
            }
        }
        return null;
    }

    /**
     * Récupère une entreprise par son SIRET
     */
    public EntrepriseManagerLogic.Entreprise getCompanyBySiret(String siret) {
        return entrepriseLogic.getEntrepriseBySiret(siret);
    }

    /**
     * Gère la suppression d'une entreprise - vend tous ses terrains
     */
    public void handleCompanyDeletion(String siret, String townName) {
        if (siret == null) {
            return;
        }

        // TODO: Supprimer TOUS les shops de cette entreprise (à réimplémenter)
        // com.gravityyfh.roleplaycity.Shop.ShopManager shopManager = plugin.getShopManager();
        // if (shopManager != null) {
        //     int deletedShops = shopManager.deleteAllShopsByCompany(
        //         siret,
        //         true, // Notifier
        //         "Dissolution de l'entreprise"
        //     );
        //
        //     if (deletedShops > 0) {
        //         plugin.getLogger().info(String.format(
        //             "[CompanyPlotManager] %d boutique(s) supprimée(s) suite à dissolution entreprise SIRET %s",
        //             deletedShops, siret
        //         ));
        //     }
        // }

        Town town = townManager.getTown(townName);
        if (town == null) {
            return;
        }

        // SYSTÈME UNIFIÉ : Récupérer tous les terrains de l'entreprise (individuels et groupés)
        List<Plot> companyPlots = townManager.getPlotsByCompanySiret(siret, townName);

        if (companyPlots.isEmpty()) {
            return;
        }

        // Compter plots individuels et groupés
        int individualCount = 0;
        int groupedCount = 0;
        for (Plot plot : companyPlots) {
            if (plot.isGrouped()) {
                groupedCount++;
            } else {
                individualCount++;
            }
        }

        plugin.getLogger().info(String.format(
            "[CompanyPlotManager] Entreprise SIRET %s supprimée - Transfert de %d terrain(s) (%d individuels + %d groupés) dans %s",
            siret, companyPlots.size(), individualCount, groupedCount, townName
        ));

        UUID gerantUuid = null;

        // Traiter tous les terrains de l'entreprise
        for (Plot plot : companyPlots) {
            if (gerantUuid == null) {
                gerantUuid = plot.getOwnerUuid();
            }

            // Transférer le terrain à la ville
            townManager.transferPlotToTown(plot, "Suppression de l'entreprise");
        }

        // Notifier l'ancien gérant s'il est en ligne
        if (gerantUuid != null) {
            Player gerant = Bukkit.getPlayer(gerantUuid);
            if (gerant != null && gerant.isOnline()) {
                gerant.sendMessage(ChatColor.YELLOW + "⚠ Vos terrains professionnels (" + companyPlots.size() + ") ont été transférés");
                gerant.sendMessage(ChatColor.YELLOW + "   à la ville suite à la dissolution de votre entreprise.");
            }
        }

        plugin.getLogger().info(String.format(
            "[CompanyPlotManager] %d terrain(s) transféré(s) à la ville %s",
            companyPlots.size(), townName
        ));

        // Sauvegarder immédiatement
        townManager.saveTownsNow();
    }

    /**
     * Vérifie le statut de dette d'une entreprise
     * Retourne true si le terrain doit être saisi
     */
    public boolean checkCompanyDebtStatus(Plot plot) {
        if (plot.getCompanyDebtAmount() <= 0) {
            return false; // Pas de dette
        }

        if (plot.getLastDebtWarningDate() == null) {
            return false; // Pas d'avertissement encore envoyé
        }

        // Vérifier si le délai de 7 jours est dépassé
        LocalDateTime warningDate = plot.getLastDebtWarningDate();
        LocalDateTime now = LocalDateTime.now();
        // ✅ FIX: Utiliser ChronoUnit.DAYS pour compter les jours calendaires
        long daysPassed = ChronoUnit.DAYS.between(warningDate.toLocalDate(), now.toLocalDate());

        // Délai dépassé - saisie automatique
        return daysPassed >= DEBT_GRACE_PERIOD_DAYS;
    }

    /**
     * Gère le manque de fonds d'une entreprise pour payer les taxes
     */
    public void handleInsufficientFunds(Plot plot, EntrepriseManagerLogic.Entreprise entreprise, double taxAmount) {
        if (plot == null || entreprise == null) {
            return;
        }

        // Ajouter à la dette
        double newDebt = plot.getCompanyDebtAmount() + taxAmount;
        plot.setCompanyDebtAmount(newDebt);

        // Si c'est le premier avertissement
        if (plot.getDebtWarningCount() == 0) {
            plot.setLastDebtWarningDate(LocalDateTime.now());
            plot.setDebtWarningCount(1);

            // Notifier le gérant
            String gerantUuidStr = entreprise.getGerantUUID();
                        if (gerantUuidStr != null) {
                            try {
                                UUID gerantUuid = UUID.fromString(gerantUuidStr);
                                debtNotificationService.refresh(gerantUuid, com.gravityyfh.roleplaycity.town.manager.DebtNotificationService.DebtUpdateReason.ECONOMY_EVENT);
                            } catch (IllegalArgumentException e) {
                                plugin.getLogger().warning("UUID invalide pour le gérant de l'entreprise " + entreprise.getNom() + ": " + gerantUuidStr);
                            }
                        }

            plugin.getLogger().warning(String.format(
                "[CompanyPlotManager] Entreprise %s (SIRET %s) - Dette de %.2f€ sur terrain %s:%d,%d. Délai: %d jours.",
                entreprise.getNom(), entreprise.getSiret(), newDebt,
                plot.getWorldName(), plot.getChunkX(), plot.getChunkZ(), DEBT_GRACE_PERIOD_DAYS
            ));
        } else {
            // Avertissement ultérieur - juste mettre à jour le montant
            plot.setDebtWarningCount(plot.getDebtWarningCount() + 1);

            String gerantUuidStr = entreprise.getGerantUUID();
                        if (gerantUuidStr != null) {
                            try {
                                UUID gerantUuid = UUID.fromString(gerantUuidStr);
                                debtNotificationService.refresh(gerantUuid, com.gravityyfh.roleplaycity.town.manager.DebtNotificationService.DebtUpdateReason.ECONOMY_EVENT);
                            } catch (IllegalArgumentException e) {
                                plugin.getLogger().warning("UUID invalide pour le gérant de l'entreprise " + entreprise.getNom() + ": " + gerantUuidStr);
                            }
                        }
        }

        // Sauvegarder immédiatement
        townManager.saveTownsNow();
    }

    /**
     * Saisit un terrain pour dette impayée
     */
    public void seizePlotForDebt(Plot plot) {
        if (plot == null) {
            return;
        }

        String companyName = plot.getCompanyName();
        UUID gerantUuid = plot.getOwnerUuid();
        double debtAmount = plot.getCompanyDebtAmount();

        // Notifier le gérant
        if (gerantUuid != null) {
            OfflinePlayer gerant = Bukkit.getOfflinePlayer(gerantUuid);
            if (gerant.isOnline() && gerant.getPlayer() != null) {
                Player gerantPlayer = gerant.getPlayer();
                gerantPlayer.sendMessage("");
                gerantPlayer.sendMessage(ChatColor.DARK_RED + "🚨🚨🚨 SAISIE DE TERRAIN 🚨🚨🚨");
                gerantPlayer.sendMessage(ChatColor.RED + "Votre terrain professionnel a été saisi pour dette impayée !");
                gerantPlayer.sendMessage(ChatColor.YELLOW + "Entreprise: " + ChatColor.WHITE + companyName);
                gerantPlayer.sendMessage(ChatColor.YELLOW + "Terrain: " + ChatColor.WHITE +
                    plot.getWorldName() + " (" + plot.getChunkX() + "," + plot.getChunkZ() + ")");
                gerantPlayer.sendMessage(ChatColor.YELLOW + "Dette totale: " + ChatColor.RED + String.format("%.2f€", debtAmount));
                gerantPlayer.sendMessage(ChatColor.GRAY + "Le terrain retourne à la ville.");
                gerantPlayer.sendMessage("");
            }
        }

        plugin.getLogger().warning(String.format(
            "[CompanyPlotManager] SAISIE - Terrain %s:%d,%d saisi pour dette de %.2f€ (Entreprise: %s)",
            plot.getWorldName(), plot.getChunkX(), plot.getChunkZ(), debtAmount, companyName
        ));

        // Retourner le terrain à la ville
        townManager.transferPlotToTown(plot, "Dette impayée: " + String.format("%.2f€", debtAmount));

        // Sauvegarder immédiatement
        townManager.saveTownsNow();
    }
}
