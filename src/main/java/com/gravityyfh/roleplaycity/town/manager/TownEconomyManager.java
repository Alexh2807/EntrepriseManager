package com.gravityyfh.roleplaycity.town.manager;

import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Gestionnaire de l'économie des villes
 * Gère les transactions, taxes, ventes et locations de parcelles
 */
public class TownEconomyManager {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final ClaimManager claimManager;
    private final NotificationManager notificationManager;
    private final DebtNotificationService debtNotificationService;

    // Historique des transactions par ville
    private final Map<String, List<PlotTransaction>> transactionHistory;

    public TownEconomyManager(RoleplayCity plugin, TownManager townManager, ClaimManager claimManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.claimManager = claimManager;
        this.notificationManager = plugin.getNotificationManager();
        this.debtNotificationService = plugin.getDebtNotificationService();
        this.transactionHistory = new HashMap<>();
    }

    // === VENTE DE PARCELLES ===

    /**
     * Met une parcelle en vente
     */
    public boolean putPlotForSale(String townName, Plot plot, double price, Player seller) {
        Town town = townManager.getTown(townName);
        if (town == null) {
            return false;
        }

        // ⚠️ NOUVEAU SYSTÈME : Vérifier si la parcelle fait partie d'un groupe
        TerritoryEntity territory = town.getTerritoryAt(plot.getWorldName(), plot.getChunkX(), plot.getChunkZ());
        if (territory instanceof PlotGroup) {
            seller.sendMessage(ChatColor.RED + "Cette parcelle fait partie d'un groupe !");
            seller.sendMessage(ChatColor.YELLOW + "Vous devez vendre le groupe entier depuis 'Mes Propriétés'.");
            return false;
        }

        // Vérifier que le vendeur a le droit
        TownRole role = town.getMemberRole(seller.getUniqueId());
        if (role == null) {
            return false;
        }

        // Seul le maire/adjoint peut vendre des parcelles municipales
        if (plot.isMunicipal() && role != TownRole.MAIRE && role != TownRole.ADJOINT) {
            return false;
        }

        // Pour les parcelles privées, seul le propriétaire peut vendre
        if (!plot.isMunicipal() && plot.getOwnerUuid() != null &&
                !plot.getOwnerUuid().equals(seller.getUniqueId()) &&
                role != TownRole.MAIRE) {
            return false;
        }

        plot.setSalePrice(price);
        plot.setForSale(true);

        // Sauvegarder immédiatement
        townManager.saveTownsNow();
        return true;
    }

    /**
     * Achète une parcelle
     */
    public boolean buyPlot(String townName, Plot plot, Player buyer) {
        Town town = townManager.getTown(townName);
        if (town == null || !plot.isForSale()) {
            return false;
        }

        // ⚠️ NOUVEAU SYSTÈME : Vérifier si la parcelle fait partie d'un groupe
        TerritoryEntity territory = town.getTerritoryAt(plot.getWorldName(), plot.getChunkX(), plot.getChunkZ());
        if (territory instanceof PlotGroup) {
            buyer.sendMessage(ChatColor.RED + "Cette parcelle fait partie d'un groupe !");
            buyer.sendMessage(ChatColor.YELLOW + "Vous devez acheter le groupe entier.");
            return false;
        }

        // Vérifier que l'acheteur est membre de la ville
        if (!town.isMember(buyer.getUniqueId())) {
            buyer.sendMessage(ChatColor.RED + "Vous devez être membre de la ville pour acheter une parcelle.");
            return false;
        }

        // NOUVEAU : Validation entreprise pour terrain PROFESSIONNEL
        CompanyPlotManager companyManager = plugin.getCompanyPlotManager();
        if (!companyManager.validateCompanyOwnership(buyer, plot)) {
            return false; // Message d'erreur déjà envoyé par validateCompanyOwnership
        }

        double price = plot.getSalePrice();

        // NOUVEAU : Gestion différente selon le type de terrain
        boolean isProfessional = (plot.getType() == PlotType.PROFESSIONNEL);
        EntrepriseManagerLogic.Entreprise buyerCompany = null;

        if (isProfessional) {
            // Terrain PROFESSIONNEL : Acheter avec l'entreprise
            buyerCompany = companyManager.getPlayerCompany(buyer);
            if (buyerCompany == null) {
                buyer.sendMessage(ChatColor.RED + "Erreur: Entreprise introuvable.");
                return false;
            }

            // Vérifier que l'entreprise a assez d'argent
            if (buyerCompany.getSolde() < price) {
                buyer.sendMessage(ChatColor.RED + "Votre entreprise n'a pas assez d'argent !");
                buyer.sendMessage(ChatColor.YELLOW + "Prix: " + ChatColor.GOLD + String.format("%.2f€", price));
                buyer.sendMessage(ChatColor.YELLOW + "Solde entreprise: " + ChatColor.GOLD + String.format("%.2f€", buyerCompany.getSolde()));
                buyer.sendMessage(ChatColor.GRAY + "Déposez de l'argent avec /entreprise deposit");
                return false;
            }

            // Prélever de l'entreprise
            buyerCompany.setSolde(buyerCompany.getSolde() - price);
        } else {
            // Terrain PARTICULIER : Acheter avec argent personnel
            if (!RoleplayCity.getEconomy().has(buyer, price)) {
                buyer.sendMessage(ChatColor.RED + "Vous n'avez pas assez d'argent. Prix: " + price + "€");
                return false;
            }

            // Prélever l'argent personnel
            RoleplayCity.getEconomy().withdrawPlayer(buyer, price);
        }

        // Si la parcelle appartenait à quelqu'un, lui donner l'argent
        if (plot.getOwnerUuid() != null) {
            UUID previousOwnerUuid = plot.getOwnerUuid();
            // Verser l'argent au propriétaire (ou son entreprise si PRO)

            if (plot.getCompanySiret() != null) {
                // Ancien terrain PRO - argent va à l'ancienne entreprise
                EntrepriseManagerLogic.Entreprise previousCompany = companyManager.getCompanyBySiret(plot.getCompanySiret());
                if (previousCompany != null) {
                    previousCompany.setSolde(previousCompany.getSolde() + price);

                    // Notifier l'ancien gérant
                    OfflinePlayer previousOwner = Bukkit.getOfflinePlayer(previousOwnerUuid);
                    if (previousOwner.isOnline() && previousOwner.getPlayer() != null) {
                        previousOwner.getPlayer().sendMessage(ChatColor.GREEN + "Votre terrain professionnel a été vendu pour " + price + "€ !");
                        previousOwner.getPlayer().sendMessage(ChatColor.YELLOW + "L'argent a été versé à " + previousCompany.getNom());
                    } else {
                        // FIX P2.4: Notification offline pour vente PRO
                        notificationManager.sendNotification(
                                previousOwnerUuid,
                                NotificationManager.NotificationType.ECONOMY,
                                "Terrain PRO vendu",
                                String.format("Votre terrain %s vendu pour %.2f€. Argent versé à %s",
                                        plot.getCoordinates(), price, previousCompany.getNom())
                        );
                    }
                } else {
                    // Entreprise n'existe plus - argent va à la ville
                    town.deposit(price);
                }
            } else {
                // Ancien terrain PARTICULIER - argent va au propriétaire
                OfflinePlayer previousOwner = Bukkit.getOfflinePlayer(previousOwnerUuid);
                RoleplayCity.getEconomy().depositPlayer(previousOwner, price);

                if (previousOwner.isOnline() && previousOwner.getPlayer() != null) {
                    previousOwner.getPlayer().sendMessage(ChatColor.GREEN + "Votre parcelle a été vendue pour " + price + "€ !");
                } else {
                    // FIX P2.4: Notification offline pour vente PARTICULIER
                    notificationManager.sendNotification(
                            previousOwnerUuid,
                            NotificationManager.NotificationType.ECONOMY,
                            "Terrain vendu",
                            String.format("Votre terrain %s vendu pour %.2f€",
                                    plot.getCoordinates(), price)
                    );
                }
            }
        } else {
            // Parcelle municipale, l'argent va à la ville
            town.deposit(price);
        }

        // Transférer la propriété
        plot.setOwner(buyer.getUniqueId(), buyer.getName());
        plot.setForSale(false);
        plot.setSalePrice(0);

        // FIX CRITIQUE: Réinitialiser TOUTES les dettes lors d'une vente
        plot.resetDebt();
        plot.resetParticularDebt();

        // NOUVEAU : Si terrain PROFESSIONNEL, enregistrer l'entreprise
        if (isProfessional && buyerCompany != null) {
            plot.setCompany(buyerCompany.getNom());
            plot.setCompanySiret(buyerCompany.getSiret());
        } else {
            plot.setCompany(null);
            plot.setCompanySiret(null);
        }

        // Enregistrer la transaction
        addTransaction(townName, new PlotTransaction(
                PlotTransaction.TransactionType.SALE,
                buyer.getUniqueId(),
                buyer.getName(),
                price,
                isProfessional ?
                        "Achat parcelle PRO " + plot.getCoordinates() + " par " + (buyerCompany != null ? buyerCompany.getNom() : "entreprise") :
                        "Achat parcelle " + plot.getCoordinates()
        ));

        // Messages personnalisés
        if (isProfessional && buyerCompany != null) {
            buyer.sendMessage(ChatColor.GREEN + "Ô£ô Terrain professionnel acheté avec succès !");
            buyer.sendMessage(ChatColor.YELLOW + "Entreprise: " + ChatColor.WHITE + buyerCompany.getNom());
            buyer.sendMessage(ChatColor.YELLOW + "Coordonnées: " + ChatColor.WHITE + plot.getCoordinates());
            buyer.sendMessage(ChatColor.YELLOW + "Prix payé: " + ChatColor.GOLD + String.format("%.2f€", price));
            buyer.sendMessage(ChatColor.YELLOW + "Solde entreprise restant: " + ChatColor.GOLD + String.format("%.2f€", buyerCompany.getSolde()));
            buyer.sendMessage(ChatColor.GRAY + "Les taxes seront prélevées du solde de l'entreprise.");
        } else {
            buyer.sendMessage(ChatColor.GREEN + "Vous avez acheté la parcelle " + plot.getCoordinates() + " pour " + price + "€ !");
        }

        // Notification d'achat
        notificationManager.notifyPurchaseSuccess(
                buyer.getUniqueId(),
                "Terrain " + plot.getCoordinates(),
                price
        );

        // Sauvegarder immédiatement
        townManager.saveTownsNow();
        return true;
    }

    // === LOCATION DE PARCELLES ===

    /**
     * Met une parcelle en location
     */
    public boolean putPlotForRent(String townName, Plot plot, double totalPrice, int durationDays, Player owner) {
        Town town = townManager.getTown(townName);
        if (town == null) {
            return false;
        }

        // ⚠️ NOUVEAU SYSTÈME : Vérifier si la parcelle fait partie d'un groupe
        TerritoryEntity territory = town.getTerritoryAt(plot.getWorldName(), plot.getChunkX(), plot.getChunkZ());
        if (territory instanceof PlotGroup) {
            owner.sendMessage(ChatColor.RED + "Cette parcelle fait partie d'un groupe !");
            owner.sendMessage(ChatColor.YELLOW + "Vous devez louer le groupe entier depuis 'Mes Propriétés'.");
            return false;
        }

        // Vérifier les permissions
        TownRole role = town.getMemberRole(owner.getUniqueId());
        if (role == null) {
            return false;
        }

        // Seul le maire/adjoint peut louer des parcelles municipales
        if (plot.isMunicipal() && role != TownRole.MAIRE && role != TownRole.ADJOINT) {
            return false;
        }

        // Pour les parcelles privées, seul le propriétaire peut louer
        if (!plot.isMunicipal() && plot.getOwnerUuid() != null &&
                !plot.getOwnerUuid().equals(owner.getUniqueId()) &&
                role != TownRole.MAIRE) {
            return false;
        }

        // Nouveau système: calculer le prix par jour
        double pricePerDay = totalPrice / Math.max(1, durationDays);
        plot.setRentPricePerDay(pricePerDay);
        plot.setForRent(true);

        // Scanner et protéger tous les blocs existants
        Chunk chunk = owner.getWorld().getChunkAt(plot.getChunkX(), plot.getChunkZ());
        plot.scanAndProtectExistingBlocks(chunk);

        // Sauvegarder immédiatement
        townManager.saveTownsNow();
        return true;
    }

    /**
     * Loue une parcelle pour la première fois (paiement initial)
     */
    public boolean rentPlot(String townName, Plot plot, Player renter, int days) {
        Town town = townManager.getTown(townName);
        if (town == null || !plot.isForRent()) {
            return false;
        }

        // ⚠️ NOUVEAU SYSTÈME : Vérifier si la parcelle fait partie d'un groupe
        TerritoryEntity territory = town.getTerritoryAt(plot.getWorldName(), plot.getChunkX(), plot.getChunkZ());
        if (territory instanceof PlotGroup) {
            renter.sendMessage(ChatColor.RED + "Cette parcelle fait partie d'un groupe !");
            renter.sendMessage(ChatColor.YELLOW + "Vous devez louer le groupe entier.");
            return false;
        }

        // Vérifier que le locataire est membre de la ville
        if (!town.isMember(renter.getUniqueId())) {
            renter.sendMessage(ChatColor.RED + "Vous devez être membre de la ville pour louer une parcelle.");
            return false;
        }

        // NOUVEAU : Empêcher le propriétaire de louer son propre terrain
        if (plot.getOwnerUuid() != null && plot.getOwnerUuid().equals(renter.getUniqueId())) {
            renter.sendMessage(ChatColor.RED + "Vous ne pouvez pas louer votre propre parcelle !");
            return false;
        }

        // NOUVEAU : Validation entreprise pour terrain PROFESSIONNEL
        CompanyPlotManager companyManager = plugin.getCompanyPlotManager();
        if (plot.getType() == com.gravityyfh.roleplaycity.town.data.PlotType.PROFESSIONNEL) {
            if (!companyManager.validateCompanyOwnership(renter, plot)) {
                return false; // Message d'erreur déjà envoyé par validateCompanyOwnership
            }

            // Récupérer le SIRET sélectionné du cache (mis par CompanySelectionGUI)
            String selectedSiret = plugin.getTownCommandHandler().getAndClearSelectedCompany(renter.getUniqueId());
            if (selectedSiret == null) {
                // Pas de SIRET dans le cache, récupérer l'entreprise du joueur
                EntrepriseManagerLogic.Entreprise renterCompany = companyManager.getPlayerCompany(renter);
                if (renterCompany == null) {
                    renter.sendMessage(ChatColor.RED + "Erreur: Entreprise introuvable.");
                    return false;
                }
                selectedSiret = renterCompany.getSiret();
            }

            // Stocker le SIRET de l'entreprise du locataire
            plot.setRenterCompanySiret(selectedSiret);
        }

        // Limiter à 30 jours max
        int actualDays = Math.min(days, 30);
        double totalCost = plot.getRentPricePerDay() * actualDays;

        // NOUVEAU : Gestion différente selon le type de terrain
        boolean isProfessional = (plot.getType() == PlotType.PROFESSIONNEL);
        EntrepriseManagerLogic.Entreprise renterCompany = null;

        if (isProfessional) {
            // Terrain PROFESSIONNEL : Louer avec l'entreprise
            renterCompany = companyManager.getPlayerCompany(renter);
            if (renterCompany == null) {
                renter.sendMessage(ChatColor.RED + "Erreur: Entreprise introuvable.");
                return false;
            }

            // Vérifier que l'entreprise a assez d'argent
            if (renterCompany.getSolde() < totalCost) {
                renter.sendMessage(ChatColor.RED + "Votre entreprise n'a pas assez d'argent !");
                renter.sendMessage(ChatColor.YELLOW + "Prix: " + ChatColor.GOLD + String.format("%.2f€", totalCost));
                renter.sendMessage(ChatColor.YELLOW + "Solde entreprise: " + ChatColor.GOLD + String.format("%.2f€", renterCompany.getSolde()));
                return false;
            }

            // Prélever de l'entreprise
            renterCompany.setSolde(renterCompany.getSolde() - totalCost);
        } else {
            // Terrain PARTICULIER : Vérifier l'argent personnel
            if (!RoleplayCity.getEconomy().has(renter, totalCost)) {
                renter.sendMessage(ChatColor.RED + "Vous n'avez pas assez d'argent. Prix: " +
                        String.format("%.2f€", totalCost));
                return false;
            }

            // Prélever l'argent personnel
            RoleplayCity.getEconomy().withdrawPlayer(renter, totalCost);
        }

        // Donner l'argent au propriétaire ou à la ville
        if (plot.getOwnerUuid() != null) {
            if (isProfessional && plot.getCompanySiret() != null) {
                // Terrain PRO - argent va à l'entreprise du propriétaire
                EntrepriseManagerLogic.Entreprise ownerCompany = companyManager.getCompanyBySiret(plot.getCompanySiret());
                if (ownerCompany != null) {
                    ownerCompany.setSolde(ownerCompany.getSolde() + totalCost);

                    // Notifier le propriétaire si en ligne
                    OfflinePlayer owner = Bukkit.getOfflinePlayer(plot.getOwnerUuid());
                    if (owner.isOnline() && owner.getPlayer() != null) {
                        owner.getPlayer().sendMessage(ChatColor.GREEN + "Votre terrain professionnel a été loué pour " + actualDays + " jours!");
                        owner.getPlayer().sendMessage(ChatColor.YELLOW + "L'argent a été versé à " + ownerCompany.getNom() + ": +" + String.format("%.2f€", totalCost));
                    }
                } else {
                    // Entreprise n'existe plus - argent va à la ville
                    town.deposit(totalCost);
                }
            } else {
                // Terrain PARTICULIER - argent va au propriétaire
                OfflinePlayer owner = Bukkit.getOfflinePlayer(plot.getOwnerUuid());
                RoleplayCity.getEconomy().depositPlayer(owner, totalCost);

                // Notifier si le propriétaire est en ligne
                if (owner.isOnline() && owner.getPlayer() != null) {
                    owner.getPlayer().sendMessage(ChatColor.GREEN + "Votre parcelle a été louée pour " +
                            actualDays + " jours (" + String.format("%.2f€", totalCost) + ") !");
                }
            }
        } else {
            // Pas de propriétaire = l'argent va à la ville
            town.deposit(totalCost);
        }

        // Définir le locataire avec son solde de jours
        plot.setRenter(renter.getUniqueId(), actualDays);
        plot.setForRent(false);

        // Enregistrer la transaction
        addTransaction(townName, new PlotTransaction(
                PlotTransaction.TransactionType.RENT,
                renter.getUniqueId(),
                renter.getName(),
                totalCost,
                "Location parcelle " + plot.getCoordinates() + " pour " + actualDays + " jours"
        ));

        renter.sendMessage(ChatColor.GREEN + "Vous avez loué la parcelle " + plot.getCoordinates() +
                " pour " + actualDays + " jours !");

        // Notification de location
        notificationManager.notifyRentalSuccess(
                renter.getUniqueId(),
                "Terrain " + plot.getCoordinates(),
                actualDays,
                totalCost
        );

        // Sauvegarder immédiatement
        townManager.saveTownsNow();
        return true;
    }

    /**
     * Recharge le solde de location d'une parcelle (max 30 jours total)
     */
    public boolean rechargePlotRent(String townName, Plot plot, Player renter, int daysToAdd) {
        Town town = townManager.getTown(townName);
        if (town == null) {
            return false;
        }

        // Vérifier que c'est bien le locataire actuel
        if (!plot.isRentedBy(renter.getUniqueId())) {
            renter.sendMessage(ChatColor.RED + "Vous ne louez pas cette parcelle.");
            return false;
        }

        // Calculer combien de jours peuvent être ajoutés (max 30 total)
        int currentDays = plot.getRentDaysRemaining();
        int maxCanAdd = 30 - currentDays;

        if (maxCanAdd <= 0) {
            renter.sendMessage(ChatColor.YELLOW + "Votre solde est déjà au maximum (30 jours).");
            return false;
        }

        int actualDaysToAdd = Math.min(daysToAdd, maxCanAdd);
        double totalCost = plot.getRentPricePerDay() * actualDaysToAdd;

        // NOUVEAU : Gestion différente selon le type de terrain
        boolean isProfessional = (plot.getType() == PlotType.PROFESSIONNEL);
        CompanyPlotManager companyManager = plugin.getCompanyPlotManager();
        EntrepriseManagerLogic.Entreprise renterCompany = null;

        if (isProfessional) {
            // Terrain PROFESSIONNEL : Recharger avec l'entreprise
            String renterSiret = plot.getRenterCompanySiret();
            if (renterSiret != null) {
                renterCompany = companyManager.getCompanyBySiret(renterSiret);
            }
            if (renterCompany == null) {
                renterCompany = companyManager.getPlayerCompany(renter);
            }

            if (renterCompany == null) {
                renter.sendMessage(ChatColor.RED + "Erreur: Entreprise introuvable.");
                return false;
            }

            // Vérifier que l'entreprise a assez d'argent
            if (renterCompany.getSolde() < totalCost) {
                renter.sendMessage(ChatColor.RED + "Votre entreprise n'a pas assez d'argent !");
                renter.sendMessage(ChatColor.YELLOW + "Prix: " + ChatColor.GOLD + String.format("%.2f€", totalCost));
                renter.sendMessage(ChatColor.YELLOW + "Solde entreprise: " + ChatColor.GOLD + String.format("%.2f€", renterCompany.getSolde()));
                return false;
            }

            // Prélever de l'entreprise
            renterCompany.setSolde(renterCompany.getSolde() - totalCost);
        } else {
            // Terrain PARTICULIER : Vérifier l'argent personnel
            if (!RoleplayCity.getEconomy().has(renter, totalCost)) {
                renter.sendMessage(ChatColor.RED + "Vous n'avez pas assez d'argent. Prix: " +
                        String.format("%.2f€", totalCost) + " pour " + actualDaysToAdd + " jours");
                return false;
            }

            // Prélever l'argent personnel
            RoleplayCity.getEconomy().withdrawPlayer(renter, totalCost);
        }

        // Donner l'argent au propriétaire ou à la ville
        if (plot.getOwnerUuid() != null) {
            if (isProfessional && plot.getCompanySiret() != null) {
                // Terrain PRO - argent va à l'entreprise du propriétaire
                EntrepriseManagerLogic.Entreprise ownerCompany = companyManager.getCompanyBySiret(plot.getCompanySiret());
                if (ownerCompany != null) {
                    ownerCompany.setSolde(ownerCompany.getSolde() + totalCost);

                    // Notifier le propriétaire si en ligne
                    OfflinePlayer owner = Bukkit.getOfflinePlayer(plot.getOwnerUuid());
                    if (owner.isOnline() && owner.getPlayer() != null) {
                        owner.getPlayer().sendMessage(ChatColor.GREEN + "Location rechargée: +" + actualDaysToAdd + " jours!");
                        owner.getPlayer().sendMessage(ChatColor.YELLOW + "L'argent a été versé à " + ownerCompany.getNom() + ": +" + String.format("%.2f€", totalCost));
                    }
                } else {
                    // Entreprise n'existe plus - argent va à la ville
                    town.deposit(totalCost);
                }
            } else {
                // Terrain PARTICULIER - argent va au propriétaire
                OfflinePlayer owner = Bukkit.getOfflinePlayer(plot.getOwnerUuid());
                RoleplayCity.getEconomy().depositPlayer(owner, totalCost);

                // Notifier si le propriétaire est en ligne
                if (owner.isOnline() && owner.getPlayer() != null) {
                    owner.getPlayer().sendMessage(ChatColor.GREEN + "Location rechargée : +" + actualDaysToAdd +
                            " jours (" + String.format("%.2f€", totalCost) + ")");
                }
            }
        } else {
            // Pas de propriétaire = l'argent va à la ville
            town.deposit(totalCost);
        }

        // Recharger le solde
        int actualAdded = plot.rechargeDays(actualDaysToAdd);

        // Enregistrer la transaction
        addTransaction(townName, new PlotTransaction(
                PlotTransaction.TransactionType.RENT,
                renter.getUniqueId(),
                renter.getName(),
                totalCost,
                "Recharge location parcelle " + plot.getCoordinates() + " (+" + actualAdded + " jours)"
        ));

        renter.sendMessage(ChatColor.GREEN + "Solde rechargé : +" + actualAdded + " jours");
        renter.sendMessage(ChatColor.YELLOW + "Jours restants : " + ChatColor.GOLD + plot.getRentDaysRemaining() + "/30");

        // Sauvegarder immédiatement
        townManager.saveTownsNow();
        return true;
    }

    /**
     * Vérifie les locations expirées et les termine
     */
    public void checkExpiredRents() {
        for (Town town : townManager.getAllTowns()) {
            for (Plot plot : town.getPlots().values()) {
                if (plot.getRenterUuid() != null && plot.isRentExpired()) {
                    // Location expirée
                    UUID renterUuid = plot.getRenterUuid();
                    Player renter = Bukkit.getPlayer(renterUuid);

                    if (renter != null && renter.isOnline()) {
                        renter.sendMessage(ChatColor.YELLOW + "Votre location de la parcelle " +
                                plot.getCoordinates() + " a expiré.");
                    }

                    // Notification d'expiration
                    notificationManager.notifyRentExpired(
                            renterUuid,
                            "Terrain " + plot.getCoordinates(),
                            town.getName()
                    );

                    plot.clearRenter();
                    plot.setForRent(true); // Remettre en location
                    plugin.getLogger().info("Location expirée pour parcelle " + plot.getCoordinates() +
                            " dans " + town.getName());
                }
            }
        }
    }

    // === COLLECTE DES TAXES ===

    /**
     * Collecte les taxes de toutes les parcelles d'une ville
     * SYSTêME AUTOMATIQUE : Gère les groupes de terrains automatiquement
     */
    public TaxCollectionResult collectTaxes(String townName) {
        Town town = townManager.getTown(townName);
        if (town == null) {
            return new TaxCollectionResult(0, 0, 0, new ArrayList<>());
        }

        double totalCollected = 0;
        int parcelsWithTax = 0;
        List<String> unpaidPlayers = new ArrayList<>();

        Set<String> plotsInGroupsProcessed = new HashSet<>();
        for (PlotGroup group : town.getPlotGroups().values()) {
            // ⚠️ NOUVEAU SYSTÈME : Track chunks in groups to avoid double-taxation
            plotsInGroupsProcessed.addAll(group.getChunkKeys());

            // ⚠️ NOUVEAU SYSTÈME : Get tax directly from autonomous group
            double groupTax = group.getDailyTax();

            if (groupTax <= 0) {
                continue;
            }

            UUID payerUuid = group.getRenterUuid() != null ? group.getRenterUuid() : group.getOwnerUuid();
            if (payerUuid == null) {
                continue;
            }

            OfflinePlayer payer = Bukkit.getOfflinePlayer(payerUuid);
            String payerName = payer.getName() != null ? payer.getName() :
                    (group.getOwnerName() != null ? group.getOwnerName() : payerUuid.toString());

            // ⚠️ NOUVEAU SYSTÈME : Vérifier le type du groupe autonome
            boolean isProfessionalGroup = group.getType() == PlotType.PROFESSIONNEL;
            String companySiret = group.getCompanySiret();
            EntrepriseManagerLogic.Entreprise company = null;

            if (isProfessionalGroup && companySiret != null) {
                company = plugin.getCompanyPlotManager().getCompanyBySiret(companySiret);
            }

            debtNotificationService.refresh(payerUuid, DebtNotificationService.DebtUpdateReason.PAYMENT);
            if (isProfessionalGroup && company != null && company.getGerantUUID() != null) {
                try {
                    UUID gerantUuid = UUID.fromString(company.getGerantUUID());
                    debtNotificationService.refresh(gerantUuid, DebtNotificationService.DebtUpdateReason.PAYMENT);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("UUID invalide pour le gérant de l'entreprise " + company.getNom() + ": " + company.getGerantUUID());
                }
            }

            boolean paymentSuccess = false;
            if (isProfessionalGroup && company != null) {
                if (company.getSolde() >= groupTax) {
                    company.setSolde(company.getSolde() - groupTax);
                    paymentSuccess = true;
                }
            } else {
                if (RoleplayCity.getEconomy().has(payer, groupTax)) {
                    RoleplayCity.getEconomy().withdrawPlayer(payer, groupTax);
                    paymentSuccess = true;
                }
            }

            if (paymentSuccess) {
                town.deposit(groupTax);
                totalCollected += groupTax;
                parcelsWithTax += group.getChunkCount();

                // ⚠️ NOUVEAU SYSTÈME : Réinitialiser les dettes du groupe autonome
                if (isProfessionalGroup) {
                    if (group.getCompanyDebtAmount() > 0) {
                        group.resetDebt();
                    }
                } else {
                    if (group.getParticularDebtAmount() > 0) {
                        group.resetParticularDebt();
                    }
                }

                if (payer.isOnline() && payer.getPlayer() != null) {
                    if (isProfessionalGroup && company != null) {
                        payer.getPlayer().sendMessage(ChatColor.YELLOW + "€ Taxe entreprise (groupe): " + ChatColor.GOLD +
                                String.format("%.2f€", groupTax) + ChatColor.GRAY + " prélevée pour " +
                                group.getGroupName() + ChatColor.GRAY + " (" + group.getChunkCount() + " parcelles)");
                    } else {
                        payer.getPlayer().sendMessage(ChatColor.YELLOW + "Taxe groupe: " + ChatColor.GOLD +
                                String.format("%.2f€", groupTax) + ChatColor.GRAY + " prélevée pour " +
                                group.getGroupName() + ChatColor.GRAY + " (" + group.getChunkCount() + " parcelles)");
                    }
                }

                String transactionLabel = isProfessionalGroup && company != null
                        ? company.getNom() + " (PRO-GROUPE)"
                        : payerName;
                addTransaction(townName, new PlotTransaction(
                        PlotTransaction.TransactionType.TAX,
                        payerUuid,
                        transactionLabel,
                        groupTax,
                        "Taxe groupe " + group.getGroupName()
                ));
            } else {
                unpaidPlayers.add(payerName);

                // ⚠️ NOUVEAU SYSTÈME : Gérer dettes directement au niveau du groupe autonome
                if (isProfessionalGroup && company != null) {
                    double newDebt = group.getCompanyDebtAmount() + groupTax;
                    group.setCompanyDebtAmount(newDebt);

                    if (group.getDebtWarningCount() == 0) {
                        group.setLastDebtWarningDate(LocalDateTime.now());
                        group.setDebtWarningCount(1);

                        String gerantUuidStr = company.getGerantUUID();
                        if (gerantUuidStr != null) {
                            try {
                                UUID gerantUuid = UUID.fromString(gerantUuidStr);
                                debtNotificationService.refresh(gerantUuid, DebtNotificationService.DebtUpdateReason.ECONOMY_EVENT);
                            } catch (IllegalArgumentException ex) {
                                plugin.getLogger().warning("UUID invalide pour le gérant de l'entreprise " + company.getNom() + ": " + gerantUuidStr);
                            }
                        }

                        plugin.getLogger().warning(String.format(
                                "[TownEconomyManager] Entreprise %s (SIRET %s) - Dette de %.2f€ sur groupe %s (%s)",
                                company.getNom(), company.getSiret(), newDebt, group.getGroupName(), townName
                        ));
                    } else {
                        debtNotificationService.refresh(payerUuid, DebtNotificationService.DebtUpdateReason.ECONOMY_EVENT);
                    }

                    // Vérifier saisie après 7 jours
                    if (group.getLastDebtWarningDate() != null) {
                        long daysSinceWarning = Duration.between(group.getLastDebtWarningDate(), LocalDateTime.now()).toDays();
                        if (daysSinceWarning >= 7) {
                            plugin.getLogger().warning(String.format(
                                    "[TownEconomyManager] SAISIE AUTO - Groupe %s saisi pour dette entreprise (Dette: %.2f€)",
                                    group.getGroupName(), group.getCompanyDebtAmount()
                            ));
                            // Transférer le groupe entier à la ville
                            group.setOwner(null, null);
                            group.setCompanyName(null);
                            group.setCompanySiret(null);
                            group.resetDebt();
                            group.setForSale(true);
                            group.setSalePrice(group.getChunkKeys().size() * 1000.0);
                        }
                    }
                } else {
                    // Dette particulier
                    double newDebt = group.getParticularDebtAmount() + groupTax;
                    group.setParticularDebtAmount(newDebt);

                    if (group.getParticularDebtWarningCount() == 0) {
                        group.setParticularLastDebtWarningDate(LocalDateTime.now());
                        group.setParticularDebtWarningCount(1);
                        debtNotificationService.refresh(payerUuid, DebtNotificationService.DebtUpdateReason.ECONOMY_EVENT);
                    } else {
                        debtNotificationService.refresh(payerUuid, DebtNotificationService.DebtUpdateReason.ECONOMY_EVENT);
                    }

                    // Vérifier saisie après 7 jours
                    if (group.getParticularLastDebtWarningDate() != null) {
                        long daysSinceWarning = Duration.between(group.getParticularLastDebtWarningDate(), LocalDateTime.now()).toDays();
                        if (daysSinceWarning >= 7) {
                            plugin.getLogger().warning(String.format(
                                    "[TownEconomyManager] SAISIE AUTO - Groupe %s saisi pour dette (Propriétaire: %s, Dette: %.2f€)",
                                    group.getGroupName(), payerName, group.getParticularDebtAmount()
                            ));
                            // Transférer le groupe entier à la ville
                            group.setOwner(null, null);
                            group.resetParticularDebt();
                            group.setForSale(true);
                            group.setSalePrice(group.getChunkKeys().size() * 1000.0);
                        }
                    }
                }
            }
        }

        for (Plot plot : town.getPlots().values()) {
            String plotKey = plot.getWorldName() + ":" + plot.getChunkX() + ":" + plot.getChunkZ();
            if (plotsInGroupsProcessed.contains(plotKey)) {
                continue;
            }

            double tax = plot.getDailyTax();
            if (tax <= 0) {
                continue;
            }

            if (plot.getType() == PlotType.PROFESSIONNEL && plot.getCompanySiret() != null) {
                CompanyPlotManager companyManager = plugin.getCompanyPlotManager();
                EntrepriseManagerLogic.Entreprise company = companyManager.getCompanyBySiret(plot.getCompanySiret());

                if (company != null) {
                    if (company.getSolde() >= tax) {
                        company.setSolde(company.getSolde() - tax);
                        town.deposit(tax);
                        totalCollected += tax;
                        parcelsWithTax++;

                        if (plot.getCompanyDebtAmount() > 0) {
                            plot.resetDebt();
                        }

                        UUID gerantUuid = plot.getOwnerUuid();
                        if (gerantUuid != null) {
                            Player gerant = Bukkit.getPlayer(gerantUuid);
                            if (gerant != null && gerant.isOnline()) {
                                gerant.sendMessage(ChatColor.YELLOW + "€ Taxe entreprise: " + ChatColor.GOLD + String.format("%.2f€", tax) +
                                        ChatColor.GRAY + " prélevée pour " + plot.getCoordinates());
                                gerant.sendMessage(ChatColor.GRAY + "Entreprise: " + company.getNom() +
                                        " - Solde restant: " + String.format("%.2f€", company.getSolde()));
                            }
                        }

                        addTransaction(townName, new PlotTransaction(
                                PlotTransaction.TransactionType.TAX,
                                plot.getOwnerUuid(),
                                company.getNom() + " (PRO)",
                                tax,
                                "Taxe entreprise " + plot.getCoordinates()
                        ));
                    } else {
                        companyManager.handleInsufficientFunds(plot, company, tax);

                        if (companyManager.checkCompanyDebtStatus(plot)) {
                            companyManager.seizePlotForDebt(plot, townName);
                        }
                    }
                } else {
                    plugin.getLogger().warning(String.format(
                            "[TownEconomyManager] Entreprise SIRET %s introuvable - Vente automatique des terrains",
                            plot.getCompanySiret()
                    ));
                    companyManager.handleCompanyDeletion(plot.getCompanySiret(), townName);
                }

                continue;
            }

            UUID payerUuid = null;
            String payerName = null;

            if (plot.getRenterUuid() != null) {
                payerUuid = plot.getRenterUuid();
                payerName = "Locataire";
            } else if (plot.getOwnerUuid() != null) {
                payerUuid = plot.getOwnerUuid();
                payerName = plot.getOwnerName();
            }

            if (payerUuid == null) {
                continue;
            }

            OfflinePlayer payer = Bukkit.getOfflinePlayer(payerUuid);
            if (payer.getName() != null) {
                payerName = payer.getName();
            }

            if (RoleplayCity.getEconomy().has(payer, tax)) {
                RoleplayCity.getEconomy().withdrawPlayer(payer, tax);
                town.deposit(tax);
                totalCollected += tax;
                parcelsWithTax++;

                if (plot.getParticularDebtAmount() > 0) {
                    plot.resetParticularDebt();
                }



                addTransaction(townName, new PlotTransaction(
                        PlotTransaction.TransactionType.TAX,
                        payerUuid,
                        payerName,
                        tax,
                        "Taxe parcelle " + plot.getCoordinates()
                ));
            } else {
                unpaidPlayers.add(payerName != null ? payerName : payerUuid.toString());

                double newDebt = plot.getParticularDebtAmount() + tax;
                plot.setParticularDebtAmount(newDebt);

                if (plot.getParticularDebtWarningCount() == 0) {
                    plot.setParticularLastDebtWarningDate(LocalDateTime.now());
                    plot.setParticularDebtWarningCount(1);



                    debtNotificationService.refresh(payerUuid, DebtNotificationService.DebtUpdateReason.ECONOMY_EVENT);
                } else {
                    debtNotificationService.refresh(payerUuid, DebtNotificationService.DebtUpdateReason.ECONOMY_EVENT);
                }

                if (plot.getParticularLastDebtWarningDate() != null) {
                    long daysSinceWarning = Duration.between(plot.getParticularLastDebtWarningDate(), LocalDateTime.now()).toDays();
                    if (daysSinceWarning >= 7) {
                        plugin.getLogger().warning(String.format(
                                "[TownEconomyManager] SAISIE AUTO - Terrain %s:%d,%d saisi pour dette (Particulier %s, Dette: %.2f€)",
                                plot.getWorldName(), plot.getChunkX(), plot.getChunkZ(), payerName, plot.getParticularDebtAmount()
                        ));



                        townManager.transferPlotToTown(plot, "Dette impayée particulier: " +
                                String.format("%.2f€", plot.getParticularDebtAmount()));
                        plot.setForSale(true);
                        plot.setSalePrice(1000.0);
                    }
                }
            }
        }

        town.setLastTaxCollection(LocalDateTime.now());

        townManager.saveTownsNow();

        return new TaxCollectionResult(totalCollected, parcelsWithTax, unpaidPlayers.size(), unpaidPlayers);
    }    public TaxCollectionResult collectTaxesHourly(String townName) {
        Town town = townManager.getTown(townName);
        if (town == null) {
            return new TaxCollectionResult(0, 0, 0, new ArrayList<>());
        }

        double totalCollected = 0;
        int parcelsWithTax = 0;
        List<String> unpaidPlayers = new ArrayList<>();
        Map<UUID, Double> playerTaxes = new HashMap<>(); // Pour les rapports individuels

        // ⚠️ NOUVEAU SYSTÈME : Collecter taxes directement depuis les groupes autonomes
        Set<String> plotsInGroupsProcessed = new HashSet<>();
        for (PlotGroup group : town.getPlotGroups().values()) {
            plotsInGroupsProcessed.addAll(group.getChunkKeys());

            // ⚠️ NOUVEAU SYSTÈME : Obtenir taxe directement du groupe
            double groupDailyTax = group.getDailyTax();

            if (groupDailyTax <= 0) continue;

            // NOUVEAU : Montant HORAIRE au lieu de quotidien
            double groupHourlyTax = groupDailyTax / 24.0;

            // Déterminer qui paie pour le groupe
            UUID payerUuid = group.getRenterUuid() != null ? group.getRenterUuid() : group.getOwnerUuid();
            if (payerUuid == null) continue;

            // NOUVEAU : Prélever même OFFLINE
            OfflinePlayer payer = Bukkit.getOfflinePlayer(payerUuid);
            String payerName = payer.getName() != null ? payer.getName() : payerUuid.toString();

            // NOUVEAU : Détecter si c'est un groupe PROFESSIONNEL (entreprise)
            // ⚠️ NOUVEAU SYSTÈME : Vérifier le type du groupe autonome
            boolean isProfessionalGroup = group.getType() == PlotType.PROFESSIONNEL;
            String companySiret = group.getCompanySiret();
            EntrepriseManagerLogic.Entreprise company = null;

            if (isProfessionalGroup && companySiret != null) {
                CompanyPlotManager companyManager = plugin.getCompanyPlotManager();
                company = companyManager.getCompanyBySiret(companySiret);
            }

            // Prélever la taxe du groupe
            boolean paymentSuccess = false;

            if (isProfessionalGroup && company != null) {
                // GROUPE PROFESSIONNEL : Prélever du solde de l'entreprise
                if (company.getSolde() >= groupHourlyTax) {
                    company.setSolde(company.getSolde() - groupHourlyTax);
                    paymentSuccess = true;
                }
            } else {
                // GROUPE PARTICULIER : Prélever de l'argent personnel
                if (RoleplayCity.getEconomy().has(payer, groupHourlyTax)) {
                    RoleplayCity.getEconomy().withdrawPlayer(payer, groupHourlyTax);
                    paymentSuccess = true;
                }
            }

            if (paymentSuccess) {
                town.deposit(groupHourlyTax);
                totalCollected += groupHourlyTax;
                parcelsWithTax += group.getChunkCount();

                // ⚠️ NOUVEAU SYSTÈME : Réinitialiser les dettes du groupe autonome
                if (isProfessionalGroup) {
                    // Réinitialiser dettes entreprise
                    if (group.getCompanyDebtAmount() > 0) {
                        group.resetDebt();
                    }
                } else {
                    // Réinitialiser dettes particulier
                    if (group.getParticularDebtAmount() > 0) {
                        group.resetParticularDebt();
                    }
                }

                // Enregistrer pour le rapport individuel
                playerTaxes.put(payerUuid, playerTaxes.getOrDefault(payerUuid, 0.0) + groupHourlyTax);

                // Message si en ligne


                // Transaction
                String transactionLabel = isProfessionalGroup && company != null
                        ? company.getNom() + " (PRO-GROUPE)"
                        : payerName;
                addTransaction(townName, new PlotTransaction(
                        PlotTransaction.TransactionType.TAX,
                        payerUuid,
                        transactionLabel,
                        groupHourlyTax,
                        "Taxe horaire groupe " + group.getGroupName()
                ));
            } else {
                // ⚠️ NOUVEAU SYSTÈME : Fonds insuffisants - créer/augmenter la dette directement dans le groupe autonome
                unpaidPlayers.add(payerName);

                double newDebt;

                // Déterminer le type de dette selon le type de groupe
                if (isProfessionalGroup && company != null) {
                    // GROUPE PROFESSIONNEL : Dette d'entreprise
                    newDebt = group.getCompanyDebtAmount() + groupHourlyTax;
                    group.setCompanyDebtAmount(newDebt);

                    // Si c'est le premier avertissement
                    if (group.getDebtWarningCount() == 0) {
                        group.setLastDebtWarningDate(LocalDateTime.now());
                        group.setDebtWarningCount(1);

                            // Notifier le gérant - FORMAT UNIFIë
                            String gerantUuidStr = company.getGerantUUID();
                            if (gerantUuidStr != null) {
                                try {
                                    UUID gerantUuid = UUID.fromString(gerantUuidStr);
                                    debtNotificationService.refresh(gerantUuid, DebtNotificationService.DebtUpdateReason.ECONOMY_EVENT);
                                } catch (IllegalArgumentException ex) {
                                    plugin.getLogger().warning("UUID invalide pour le gérant de l'entreprise " + company.getNom() + ": " + gerantUuidStr);
                                }
                            } else {
                                plugin.getLogger().warning("Impossible de notifier le gérant de l'entreprise " + company.getNom() + " (SIRET: " + company.getSiret() + ") car l'UUID du gérant est manquant.");
                            }
                            if (gerantUuidStr != null) {
                                UUID gerantUuid = UUID.fromString(gerantUuidStr);
                                OfflinePlayer gerant = Bukkit.getOfflinePlayer(gerantUuid);

                            }

                        debtNotificationService.refresh(payerUuid, DebtNotificationService.DebtUpdateReason.ECONOMY_EVENT);
                    } else {
                        // Dette déjà existante
                        String gerantUuidStr = company.getGerantUUID();
                        if (gerantUuidStr != null) {
                            try {
                                UUID gerantUuid = UUID.fromString(gerantUuidStr);
                                debtNotificationService.refresh(gerantUuid, DebtNotificationService.DebtUpdateReason.ECONOMY_EVENT);
                            } catch (IllegalArgumentException ex) {
                                plugin.getLogger().warning("UUID invalide pour le gérant de l'entreprise " + company.getNom() + ": " + gerantUuidStr);
                            }
                        } else {
                            plugin.getLogger().warning("Impossible de notifier le gérant de l'entreprise " + company.getNom() + " (SIRET: " + company.getSiret() + ") car l'UUID du gérant est manquant.");
                        }
                        if (gerantUuidStr != null) {
                            UUID gerantUuid = UUID.fromString(gerantUuidStr);
                            OfflinePlayer gerant = Bukkit.getOfflinePlayer(gerantUuid);

                        }

                        group.setDebtWarningCount(group.getDebtWarningCount() + 1);
                        debtNotificationService.refresh(payerUuid, DebtNotificationService.DebtUpdateReason.ECONOMY_EVENT);
                    }

                    plugin.getLogger().warning(String.format(
                            "[TownEconomyManager] Groupe PRO %s - Fonds insuffisants pour taxe de %.2f€ (Entreprise: %s, Dette: %.2f€)",
                            group.getGroupName(), groupHourlyTax, company.getNom(), newDebt
                    ));

                    // ⚠️ NOUVEAU SYSTÈME : Vérifier saisie automatique après 7 jours
                    if (group.getLastDebtWarningDate() != null) {
                        long daysSinceWarning = java.time.Duration.between(group.getLastDebtWarningDate(), LocalDateTime.now()).toDays();

                        if (daysSinceWarning >= 7) {
                            // SAISIE AUTOMATIQUE : Transférer le groupe entier à la ville
                            plugin.getLogger().warning(String.format(
                                    "[TownEconomyManager] SAISIE AUTO - Groupe PRO %s saisi pour dette (Entreprise: %s, Dette: %.2f€)",
                                    group.getGroupName(), company.getNom(), group.getCompanyDebtAmount()
                            ));

                            // Retour à la ville avec nettoyage complet
                            group.setOwner(null, null);
                            group.setCompanyName(null);
                            group.setCompanySiret(null);
                            group.resetDebt();
                            group.setForSale(true);
                            group.setSalePrice(group.getChunkKeys().size() * 1000.0);
                        }
                    }
                } else {
                    // GROUPE PARTICULIER : Dette personnelle
                    newDebt = group.getParticularDebtAmount() + groupHourlyTax;
                    group.setParticularDebtAmount(newDebt);

                    // Si c'est le premier avertissement
                    if (group.getParticularDebtWarningCount() == 0) {
                        group.setParticularLastDebtWarningDate(LocalDateTime.now());
                        group.setParticularDebtWarningCount(1);

                        // Avertissement au joueur - FORMAT UNIFIë


                        debtNotificationService.refresh(payerUuid, DebtNotificationService.DebtUpdateReason.ECONOMY_EVENT);
                    } else {
                        // Dette déjà existante
                        group.setParticularDebtWarningCount(group.getParticularDebtWarningCount() + 1);
                        debtNotificationService.refresh(payerUuid, DebtNotificationService.DebtUpdateReason.ECONOMY_EVENT);


                    }

                    plugin.getLogger().warning(String.format(
                            "[TownEconomyManager] Groupe %s - Fonds insuffisants pour taxe de %.2f€ (Propriétaire: %s, Dette: %.2f€)",
                            group.getGroupName(), groupHourlyTax, payerName, newDebt
                    ));

                    // ⚠️ NOUVEAU SYSTÈME : Vérifier si le délai de grâce est dépassé (7 jours)
                    if (group.getParticularLastDebtWarningDate() != null) {
                        LocalDateTime warningDate = group.getParticularLastDebtWarningDate();
                        long daysSinceWarning = java.time.Duration.between(warningDate, LocalDateTime.now()).toDays();

                        if (daysSinceWarning >= 7) {
                            // SAISIE AUTOMATIQUE : Transférer le groupe entier à la ville
                            plugin.getLogger().warning(String.format(
                                    "[TownEconomyManager] SAISIE AUTO - Groupe %s saisi pour dette (Propriétaire: %s, Dette: %.2f€)",
                                    group.getGroupName(), payerName, group.getParticularDebtAmount()
                            ));

                            // Notifier le joueur


                            // Retour à la ville avec nettoyage complet
                            group.setOwner(null, null);
                            group.setCompanyName(null);
                            group.setCompanySiret(null);
                            group.resetDebt();
                            group.setForSale(true);
                            group.setSalePrice(group.getChunkKeys().size() * 1000.0);
                        }
                    }
                }
            }
        }

        // Puis collecter les taxes des parcelles individuelles (non groupées)
        for (Plot plot : town.getPlots().values()) {
            String plotKey = plot.getWorldName() + ":" + plot.getChunkX() + ":" + plot.getChunkZ();
            if (plotsInGroupsProcessed.contains(plotKey)) continue;

            double dailyTax = plot.getDailyTax();
            if (dailyTax <= 0) continue;

            // NOUVEAU : Montant HORAIRE
            double hourlyTax = dailyTax / 24.0;

            // === Gestion des terrains PROFESSIONNEL avec entreprise ===
            if (plot.getType() == PlotType.PROFESSIONNEL && plot.getCompanySiret() != null) {
                CompanyPlotManager companyManager = plugin.getCompanyPlotManager();
                EntrepriseManagerLogic.Entreprise company = companyManager.getCompanyBySiret(plot.getCompanySiret());

                if (company != null) {
                    if (company.getSolde() >= hourlyTax) {
                        company.setSolde(company.getSolde() - hourlyTax);
                        town.deposit(hourlyTax);
                        totalCollected += hourlyTax;
                        parcelsWithTax++;

                        if (plot.getCompanyDebtAmount() > 0) {
                            plot.resetDebt();
                        }

                        UUID gerantUuid = plot.getOwnerUuid();
                        if (gerantUuid != null) {
                            playerTaxes.put(gerantUuid, playerTaxes.getOrDefault(gerantUuid, 0.0) + hourlyTax);

                            Player gerant = Bukkit.getPlayer(gerantUuid);
                            if (gerant != null && gerant.isOnline()) {
                                gerant.sendMessage(ChatColor.YELLOW + "€ Taxe horaire entreprise: " +
                                        ChatColor.GOLD + String.format("%.2f€", hourlyTax) + ChatColor.GRAY +
                                        " prélevée pour " + plot.getCoordinates());
                            }
                        }

                        addTransaction(townName, new PlotTransaction(
                                PlotTransaction.TransactionType.TAX,
                                plot.getOwnerUuid(),
                                company.getNom() + " (PRO)",
                                hourlyTax,
                                "Taxe horaire entreprise " + plot.getCoordinates()
                        ));
                    } else {
                        companyManager.handleInsufficientFunds(plot, company, hourlyTax);
                    }

                    if (companyManager.checkCompanyDebtStatus(plot)) {
                        companyManager.seizePlotForDebt(plot, townName);
                    }
                } else {
                    companyManager.handleCompanyDeletion(plot.getCompanySiret(), townName);
                }

                continue;
            }

            // === Gestion des terrains PARTICULIER ===
            UUID payerUuid = null;
            String payerName = null;

            if (plot.getRenterUuid() != null) {
                payerUuid = plot.getRenterUuid();
                payerName = "Locataire";
            } else if (plot.getOwnerUuid() != null) {
                payerUuid = plot.getOwnerUuid();
                payerName = plot.getOwnerName();
            }

            if (payerUuid == null) continue;

            // NOUVEAU : Prélever même OFFLINE
            OfflinePlayer payer = Bukkit.getOfflinePlayer(payerUuid);
            if (payer.getName() != null) {
                payerName = payer.getName();
            }

            if (RoleplayCity.getEconomy().has(payer, hourlyTax)) {
                RoleplayCity.getEconomy().withdrawPlayer(payer, hourlyTax);
                town.deposit(hourlyTax);
                totalCollected += hourlyTax;
                parcelsWithTax++;

                // Réinitialiser la dette si le terrain était endetté
                if (plot.getParticularDebtAmount() > 0) {
                    plot.resetParticularDebt();
                }

                // Enregistrer pour le rapport individuel
                playerTaxes.put(payerUuid, playerTaxes.getOrDefault(payerUuid, 0.0) + hourlyTax);

                // Message si en ligne


                addTransaction(townName, new PlotTransaction(
                        PlotTransaction.TransactionType.TAX,
                        payerUuid,
                        payerName,
                        hourlyTax,
                        "Taxe horaire parcelle " + plot.getCoordinates()
                ));
            } else {
                // NOUVEAU : Fonds insuffisants - créer/augmenter la dette
                unpaidPlayers.add(payerName);

                double newDebt = plot.getParticularDebtAmount() + hourlyTax;
                plot.setParticularDebtAmount(newDebt);

                // Si c'est le premier avertissement
                if (plot.getParticularDebtWarningCount() == 0) {
                    plot.setParticularLastDebtWarningDate(LocalDateTime.now());
                    plot.setParticularDebtWarningCount(1);

                    // Avertissement au joueur


                    debtNotificationService.refresh(payerUuid, DebtNotificationService.DebtUpdateReason.ECONOMY_EVENT);
                } else {
                    // Dette déjà existante - simple notification
                    debtNotificationService.refresh(payerUuid, DebtNotificationService.DebtUpdateReason.ECONOMY_EVENT);


                }

                plugin.getLogger().warning(String.format(
                        "[TownEconomyManager] Particulier %s - Fonds insuffisants pour taxe de %.2f€ sur terrain %s:%d,%d (Dette: %.2f€)",
                        payerName, hourlyTax, plot.getWorldName(), plot.getChunkX(), plot.getChunkZ(), newDebt
                ));
            }

            // NOUVEAU : Vérifier si le délai de grâce est dépassé (7 jours) pour les particuliers
            if (plot.getParticularDebtAmount() > 0 && plot.getParticularLastDebtWarningDate() != null) {
                LocalDateTime warningDate = plot.getParticularLastDebtWarningDate();
                long daysSinceWarning = java.time.Duration.between(warningDate, LocalDateTime.now()).toDays();

                if (daysSinceWarning >= 7) {
                    // SAISIE AUTOMATIQUE du terrain
                    plugin.getLogger().warning(String.format(
                            "[TownEconomyManager] SAISIE AUTO - Terrain %s:%d,%d saisi pour dette (Particulier %s, Dette: %.2f€)",
                            plot.getWorldName(), plot.getChunkX(), plot.getChunkZ(), payerName, plot.getParticularDebtAmount()
                    ));

                    // Notifier le joueur
                    if (false && payer.isOnline() && payer.getPlayer() != null) {
                        payer.getPlayer().sendMessage("");
                        payer.getPlayer().sendMessage(ChatColor.DARK_RED + "⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠");
                        payer.getPlayer().sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "⚠ SAISIE DE TERRAIN");
                        payer.getPlayer().sendMessage(ChatColor.DARK_RED + "⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠");
                        payer.getPlayer().sendMessage(ChatColor.RED + "Votre terrain " + plot.getCoordinates());
                        payer.getPlayer().sendMessage(ChatColor.RED + "a été saisi pour dette impayée!");
                        payer.getPlayer().sendMessage("");
                        payer.getPlayer().sendMessage(ChatColor.YELLOW + "Dette: " + ChatColor.GOLD +
                                String.format("%.2f€", plot.getParticularDebtAmount()));
                        payer.getPlayer().sendMessage(ChatColor.GRAY + "Le terrain retourne à la ville.");
                        payer.getPlayer().sendMessage(ChatColor.DARK_RED + "⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠");
                        payer.getPlayer().sendMessage("");
                    }

                    // FIX CRITIQUE: Retour du terrain à la ville avec nettoyage complet
                    townManager.transferPlotToTown(plot, "Dette impayée particulier: " +
                            String.format("%.2f€", plot.getParticularDebtAmount()));

                    // Remettre en vente
                    plot.setForSale(true);
                    plot.setSalePrice(1000.0); // Prix par défaut
                }
            }
        }

        // Sauvegarder
        townManager.saveTownsNow();

        // NOUVEAU : Générer les rapports
        generateTaxReports(town, totalCollected, parcelsWithTax, unpaidPlayers.size(), playerTaxes);

        return new TaxCollectionResult(totalCollected, parcelsWithTax, unpaidPlayers.size(), unpaidPlayers);
    }

    /**
     * Génère les rapports de taxes individuels et le rapport maire
     */
    private void generateTaxReports(Town town, double totalCollected, int parcelsWithTax,
                                    int unpaidCount, Map<UUID, Double> playerTaxes) {
        // === RAPPORTS INDIVIDUELS ===
        for (Map.Entry<UUID, Double> entry : playerTaxes.entrySet()) {
            UUID playerUuid = entry.getKey();
            double taxAmount = entry.getValue();

            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                // Envoyer rapport individuel
                player.sendMessage("");
                player.sendMessage(ChatColor.GOLD + "⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠");
                player.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "    RAPPORT TAXES HORAIRES");
                player.sendMessage(ChatColor.GOLD + "⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠");
                player.sendMessage(ChatColor.AQUA + "Ville: " + ChatColor.WHITE + town.getName());
                player.sendMessage(ChatColor.AQUA + "Montant prélevé: " + ChatColor.GOLD + String.format("%.2f€", taxAmount));
                player.sendMessage(ChatColor.GRAY + "Heure: " + ChatColor.WHITE +
                        java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
                player.sendMessage(ChatColor.GOLD + "⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠");
                player.sendMessage("");
            }
        }

        // === RAPPORT MAIRE ===
        UUID mayorUuid = town.getMayorUuid();
        if (mayorUuid != null) {
            Player mayor = Bukkit.getPlayer(mayorUuid);
            if (mayor != null && mayor.isOnline()) {
                mayor.sendMessage("");
                mayor.sendMessage(ChatColor.GOLD + "⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠");
                mayor.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "  RAPPORT MAIRE - TAXES HORAIRES");
                mayor.sendMessage(ChatColor.GOLD + "⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠");
                mayor.sendMessage(ChatColor.AQUA + "Ville: " + ChatColor.WHITE + town.getName());
                mayor.sendMessage(ChatColor.AQUA + "Total collecté: " + ChatColor.GOLD + String.format("%.2f€", totalCollected));
                mayor.sendMessage(ChatColor.AQUA + "Parcelles taxées: " + ChatColor.WHITE + parcelsWithTax);
                mayor.sendMessage(ChatColor.AQUA + "Solde ville: " + ChatColor.GOLD + String.format("%.2f€", town.getBankBalance()));

                if (unpaidCount > 0) {
                    mayor.sendMessage("");
                    mayor.sendMessage(ChatColor.RED + "⚠ " + unpaidCount + " paiement(s) impayé(s)");
                }

                mayor.sendMessage("");
                mayor.sendMessage(ChatColor.YELLOW + "Contribuables (" + playerTaxes.size() + "):");
                int count = 0;
                for (Map.Entry<UUID, Double> entry : playerTaxes.entrySet()) {
                    if (count >= 10) {
                        mayor.sendMessage(ChatColor.GRAY + "  ... et " + (playerTaxes.size() - 10) + " autre(s)");
                        break;
                    }
                    OfflinePlayer taxpayer = Bukkit.getOfflinePlayer(entry.getKey());
                    String name = taxpayer.getName() != null ? taxpayer.getName() : "Inconnu";
                    mayor.sendMessage(ChatColor.GRAY + "  → " + ChatColor.WHITE + name + ": " +
                            ChatColor.GOLD + String.format("%.2f€", entry.getValue()));
                    count++;
                }

                mayor.sendMessage(ChatColor.GRAY + "Heure: " + ChatColor.WHITE +
                        java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
                mayor.sendMessage(ChatColor.GOLD + "⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠⚠");
                mayor.sendMessage("");
            }
        }
    }

    /**
     * Collecte automatique des taxes pour toutes les villes
     */
    public void collectAllTaxes() {
        plugin.getLogger().info("Début de la collecte automatique des taxes...");
        int totalTowns = 0;
        double totalAmount = 0;

        for (String townName : townManager.getTownNames()) {
            Town town = townManager.getTown(townName);
            if (town == null) continue;

            // Vérifier si c'est le moment de collecter
            LocalDateTime lastCollection = town.getLastTaxCollection();
            Duration duration = Duration.between(lastCollection, LocalDateTime.now());

            if (duration.toHours() >= 24) { // Collecte toutes les 24h
                TaxCollectionResult result = collectTaxes(townName);
                totalTowns++;
                totalAmount += result.totalCollected;

                plugin.getLogger().info("Ville " + townName + ": " +
                        result.totalCollected + "€ collectés sur " + result.parcelsCollected + " parcelles");
            }
        }

        plugin.getLogger().info("Collecte terminée: " + totalTowns + " villes, " + totalAmount + "€ total");
    }

    /**
     * NOUVEAU : Collecte horaire des taxes pour toutes les villes
     * - Collecte toutes les heures (au lieu de 24h)
     * - Prélève les joueurs OFFLINE
     * - Génère des rapports individuels et rapport maire
     * - Synchronisé avec les paiements entreprises
     */
    public void collectAllTaxesHourly() {
        plugin.getLogger().info("[TAXES HORAIRES] Début de la collecte horaire des taxes...");
        int totalTowns = 0;
        double totalAmount = 0;

        for (String townName : townManager.getTownNames()) {
            Town town = townManager.getTown(townName);
            if (town == null) continue;

            // Collecte horaire (pas de vérification de 24h)
            TaxCollectionResult result = collectTaxesHourly(townName);
            totalTowns++;
            totalAmount += result.totalCollected;

            plugin.getLogger().info("[TAXES HORAIRES] Ville " + townName + ": " +
                    String.format("%.2f€", result.totalCollected) + " collectés sur " +
                    result.parcelsCollected + " parcelles");
        }

        plugin.getLogger().info("[TAXES HORAIRES] Collecte terminée: " + totalTowns + " villes, " +
                String.format("%.2f€", totalAmount) + " total");
    }

    // === TRANSACTIONS ===

    private void addTransaction(String townName, PlotTransaction transaction) {
        transactionHistory.computeIfAbsent(townName, k -> new ArrayList<>()).add(transaction);

        // Limiter l'historique à 100 transactions par ville
        List<PlotTransaction> history = transactionHistory.get(townName);
        if (history.size() > 100) {
            history.remove(0);
        }
    }

    public List<PlotTransaction> getTransactionHistory(String townName) {
        return transactionHistory.getOrDefault(townName, new ArrayList<>());
    }

    public List<PlotTransaction> getRecentTransactions(String townName, int limit) {
        List<PlotTransaction> history = getTransactionHistory(townName);
        int size = history.size();
        return history.subList(Math.max(0, size - limit), size);
    }

    // === GESTION DES GROUPES DE PARCELLES ===

    /**
     * Met un groupe de parcelles en vente
     */
    public boolean putPlotGroupForSale(String townName, PlotGroup group, double price, Player seller) {
        Town town = townManager.getTown(townName);
        if (town == null) {
            return false;
        }

        // Vérifier les permissions : propriétaire OU maire/adjoint de la ville
        boolean isOwner = group.isOwnedBy(seller.getUniqueId());
        boolean isMayor = town.isMayor(seller.getUniqueId());
        TownRole role = town.getMemberRole(seller.getUniqueId());
        boolean isAdjoint = (role == TownRole.ADJOINT);

        if (!isOwner && !isMayor && !isAdjoint) {
            return false;
        }

        group.setSalePrice(price);
        group.setForSale(true);

        // ⚠️ NOUVEAU SYSTÈME : PlotGroup autonome, pas besoin de modifier les plots individuels
        // Le groupe est l'entité complète, la vente se fait via le groupe uniquement

        // Sauvegarder immédiatement
        townManager.saveTownsNow();
        return true;
    }

    /**
     * Achète un groupe de parcelles
     */
    public boolean buyPlotGroup(String townName, PlotGroup group, Player buyer) {
        Town town = townManager.getTown(townName);
        if (town == null || !group.isForSale()) {
            return false;
        }

        // Vérifier que l'acheteur est membre de la ville
        if (!town.isMember(buyer.getUniqueId())) {
            buyer.sendMessage(ChatColor.RED + "Vous devez être membre de la ville pour acheter un groupe de parcelles.");
            return false;
        }

        // Vérifier que le groupe n'est pas loué
        if (group.getRenterUuid() != null) {
            buyer.sendMessage(ChatColor.RED + "Ce groupe de parcelles est actuellement loué.");
            return false;
        }

        // ⚠️ NOUVEAU SYSTÈME : Utiliser le type du groupe directement (plus de recherche de plots)
        boolean hasProfessionalPlot = (group.getType() == PlotType.PROFESSIONNEL);

        double price = group.getSalePrice();
        CompanyPlotManager companyManager = plugin.getCompanyPlotManager();
        EntrepriseManagerLogic.Entreprise buyerCompany = null;
        String oldCompanySiret = group.getCompanySiret(); // Utiliser directement le SIRET du groupe

        // Si le groupe est PROFESSIONNEL, valider l'entreprise et utiliser les fonds d'entreprise
        if (hasProfessionalPlot) {
            buyerCompany = companyManager.getPlayerCompany(buyer);
            if (buyerCompany == null) {
                buyer.sendMessage(ChatColor.RED + "Ô£ù Vous devez posséder une entreprise pour acheter un groupe contenant des terrains PROFESSIONNELS !");
                buyer.sendMessage(ChatColor.YELLOW + "ÔåÆ Discutez de votre projet avec le Maire pour obtenir un contrat d'entreprise");
                return false;
            }

            // Vérifier les fonds de l'entreprise
            if (buyerCompany.getSolde() < price) {
                buyer.sendMessage(ChatColor.RED + "Votre entreprise n'a pas assez d'argent !");
                buyer.sendMessage(ChatColor.YELLOW + "Prix: " + ChatColor.GOLD + String.format("%.2f€", price));
                buyer.sendMessage(ChatColor.YELLOW + "Solde entreprise: " + ChatColor.GOLD + String.format("%.2f€", buyerCompany.getSolde()));
                return false;
            }

            // Prélever de l'entreprise
            buyerCompany.setSolde(buyerCompany.getSolde() - price);
        } else {
            // Groupe PARTICULIER uniquement - utiliser argent personnel
            if (!RoleplayCity.getEconomy().has(buyer, price)) {
                buyer.sendMessage(ChatColor.RED + "Vous n'avez pas assez d'argent. Prix: " + price + "€");
                return false;
            }
            RoleplayCity.getEconomy().withdrawPlayer(buyer, price);
        }

        // Donner l'argent au propriétaire ou à la banque
        if (group.getOwnerUuid() != null) {
            // ⚠️ NOUVEAU SYSTÈME : oldCompanySiret déjà récupéré directement du groupe

            if (oldCompanySiret != null) {
                // Ancien terrain PRO - argent va à l'ancienne entreprise
                EntrepriseManagerLogic.Entreprise previousCompany = companyManager.getCompanyBySiret(oldCompanySiret);
                if (previousCompany != null) {
                    previousCompany.setSolde(previousCompany.getSolde() + price);
                    OfflinePlayer previousOwner = Bukkit.getOfflinePlayer(group.getOwnerUuid());
                    if (previousOwner.isOnline() && previousOwner.getPlayer() != null) {
                        previousOwner.getPlayer().sendMessage(ChatColor.GREEN + "Votre groupe de parcelles a été vendu pour " + price + "€");
                        previousOwner.getPlayer().sendMessage(ChatColor.YELLOW + "L'argent a été versé à l'entreprise " + previousCompany.getNom());
                    } else {
                        // FIX P2.4: Notification offline pour vente groupe PRO
                        notificationManager.sendNotification(
                                group.getOwnerUuid(),
                                NotificationManager.NotificationType.ECONOMY,
                                "Groupe PRO vendu",
                                String.format("Votre groupe vendu pour %.2f€. Argent versé à %s",
                                        price, previousCompany.getNom())
                        );
                    }
                } else {
                    town.deposit(price); // Entreprise n'existe plus
                }
            } else {
                // Ancien terrain PARTICULIER - argent va au propriétaire
                OfflinePlayer previousOwner = Bukkit.getOfflinePlayer(group.getOwnerUuid());
                RoleplayCity.getEconomy().depositPlayer(previousOwner, price);
                if (previousOwner.isOnline() && previousOwner.getPlayer() != null) {
                    previousOwner.getPlayer().sendMessage(ChatColor.GREEN + "Votre groupe de parcelles a été vendu pour " + price + "€");
                } else {
                    // FIX P2.4: Notification offline pour vente groupe PARTICULIER
                    notificationManager.sendNotification(
                            group.getOwnerUuid(),
                            NotificationManager.NotificationType.ECONOMY,
                            "Groupe vendu",
                            String.format("Votre groupe vendu pour %.2f€", price)
                    );
                }
            }
        } else {
            town.deposit(price);
        }

        // ⚠️ NOUVEAU SYSTÈME : PlotGroup autonome - plus besoin de chercher les plots individuels
        // Le groupe est l'entité complète, on met à jour directement ses propriétés

        // Transférer la propriété du groupe
        group.setOwner(buyer.getUniqueId(), buyer.getName());
        group.setForSale(false);

        // Réinitialiser TOUTES les dettes (entreprise ET particulier)
        group.resetDebt();
        group.resetParticularDebt();

        // Si terrain PROFESSIONNEL, enregistrer l'entreprise
        if (group.getType() == PlotType.PROFESSIONNEL && buyerCompany != null) {
            group.setCompanyName(buyerCompany.getNom());
            group.setCompanySiret(buyerCompany.getSiret());
        } else {
            group.setCompanyName(null);
            group.setCompanySiret(null);
        }

        // Messages personnalisés selon le type
        if (hasProfessionalPlot && buyerCompany != null) {
            buyer.sendMessage(ChatColor.GREEN + "Ô£ô Groupe de parcelles PROFESSIONNEL acheté avec succès !");
            buyer.sendMessage(ChatColor.YELLOW + "Entreprise: " + ChatColor.WHITE + buyerCompany.getNom());
            buyer.sendMessage(ChatColor.YELLOW + "Parcelles: " + ChatColor.GOLD + group.getChunkKeys().size());
            buyer.sendMessage(ChatColor.YELLOW + "Prix payé: " + ChatColor.GOLD + String.format("%.2f€", price));
            buyer.sendMessage(ChatColor.YELLOW + "Solde entreprise restant: " + ChatColor.GOLD + String.format("%.2f€", buyerCompany.getSolde()));
            buyer.sendMessage(ChatColor.GRAY + "Les taxes seront prélevées du solde de l'entreprise.");
        } else {
            buyer.sendMessage(ChatColor.GREEN + "Groupe de parcelles acheté avec succès !");
            buyer.sendMessage(ChatColor.YELLOW + "Parcelles: " + ChatColor.GOLD + group.getChunkKeys().size());
            buyer.sendMessage(ChatColor.YELLOW + "Prix payé: " + ChatColor.GOLD + price + "€");
        }

        // Enregistrer la transaction
        String transactionLabel = hasProfessionalPlot && buyerCompany != null ?
                "Achat groupe PRO: " + group.getGroupName() + " (" + buyerCompany.getNom() + ")" :
                "Achat groupe: " + group.getGroupName();

        addTransaction(townName, new PlotTransaction(
                PlotTransaction.TransactionType.SALE,
                buyer.getUniqueId(),
                buyer.getName(),
                price,
                transactionLabel
        ));

        // Notification d'achat de groupe
        notificationManager.notifyPurchaseSuccess(
                buyer.getUniqueId(),
                "Groupe '" + group.getGroupName() + "' (" + group.getChunkKeys().size() + " terrains)",
                price
        );

        // Sauvegarder immédiatement
        townManager.saveTownsNow();
        return true;
    }

    /**
     * Met un groupe de parcelles en location
     */
    public boolean putPlotGroupForRent(String townName, PlotGroup group, double totalPrice, int durationDays, Player owner) {
        Town town = townManager.getTown(townName);
        if (town == null) {
            return false;
        }

        // Vérifier les permissions : propriétaire OU maire/adjoint de la ville
        boolean isOwner = group.isOwnedBy(owner.getUniqueId());
        boolean isMayor = town.isMayor(owner.getUniqueId());
        TownRole role = town.getMemberRole(owner.getUniqueId());
        boolean isAdjoint = (role == TownRole.ADJOINT);

        if (!isOwner && !isMayor && !isAdjoint) {
            return false;
        }

        // Calculer le prix par jour
        double pricePerDay = totalPrice / Math.max(1, durationDays);
        group.setRentPricePerDay(pricePerDay);
        group.setForRent(true);

        // ⚠️ NOUVEAU SYSTÈME : Scanner et protéger tous les blocs directement via le groupe
        // Le PlotGroup a son propre RenterBlockTracker
        for (String chunkKey : group.getChunkKeys()) {
            String[] parts = chunkKey.split(":");
            if (parts.length == 3) {
                String worldName = parts[0];
                int chunkX = Integer.parseInt(parts[1]);
                int chunkZ = Integer.parseInt(parts[2]);

                org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
                if (world != null) {
                    Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                    group.scanAndProtectExistingBlocks(chunk);
                }
            }
        }

        // Sauvegarder immédiatement
        townManager.saveTownsNow();
        return true;
    }

    /**
     * Loue un groupe de parcelles
     */
    public boolean rentPlotGroup(String townName, PlotGroup group, Player renter, int days) {
        Town town = townManager.getTown(townName);
        if (town == null || !group.isForRent()) {
            return false;
        }

        // Vérifier que le loueur est membre de la ville
        if (!town.isMember(renter.getUniqueId())) {
            renter.sendMessage(ChatColor.RED + "Vous devez être membre de la ville pour louer un groupe de parcelles.");
            return false;
        }

        // NOUVEAU : Empêcher le propriétaire de louer son propre groupe
        if (group.getOwnerUuid() != null && group.getOwnerUuid().equals(renter.getUniqueId())) {
            renter.sendMessage(ChatColor.RED + "Vous ne pouvez pas louer votre propre groupe de parcelles !");
            return false;
        }

        int actualDays = Math.min(days, 30);
        double totalCost = group.getRentPricePerDay() * actualDays;

        // ⚠️ NOUVEAU SYSTÈME : Détecter si c'est un groupe PROFESSIONNEL directement
        boolean isProfessionalGroup = (group.getType() == PlotType.PROFESSIONNEL);
        String renterCompanySiret = null;
        String ownerCompanySiret = group.getCompanySiret();  // Direct from autonomous group
        CompanyPlotManager companyManager = plugin.getCompanyPlotManager();
        EntrepriseManagerLogic.Entreprise renterCompany = null;

        // Si groupe PRO, valider l'entreprise du locataire
        if (isProfessionalGroup) {
            renterCompany = companyManager.getPlayerCompany(renter);
            if (renterCompany == null) {
                renter.sendMessage(ChatColor.RED + "Ô£ù Vous devez posséder une entreprise pour louer un groupe contenant des terrains PROFESSIONNELS !");
                renter.sendMessage(ChatColor.YELLOW + "ÔåÆ Discutez de votre projet avec le Maire pour obtenir un contrat d'entreprise");
                return false;
            }
            renterCompanySiret = renterCompany.getSiret();

            // Vérifier les fonds de l'entreprise
            if (renterCompany.getSolde() < totalCost) {
                renter.sendMessage(ChatColor.RED + "Votre entreprise n'a pas assez d'argent !");
                renter.sendMessage(ChatColor.YELLOW + "Prix: " + ChatColor.GOLD + String.format("%.2f€", totalCost));
                renter.sendMessage(ChatColor.YELLOW + "Solde entreprise: " + ChatColor.GOLD + String.format("%.2f€", renterCompany.getSolde()));
                return false;
            }

            // Prélever de l'entreprise du locataire
            renterCompany.setSolde(renterCompany.getSolde() - totalCost);
        } else {
            // Groupe PARTICULIER : Vérifier l'argent personnel
            if (!RoleplayCity.getEconomy().has(renter, totalCost)) {
                renter.sendMessage(ChatColor.RED + "Vous n'avez pas assez d'argent. Prix: " + totalCost + "€");
                return false;
            }

            // Prélever l'argent personnel
            RoleplayCity.getEconomy().withdrawPlayer(renter, totalCost);
        }

        // Donner l'argent au propriétaire ou à la banque
        if (group.getOwnerUuid() != null) {
            if (isProfessionalGroup && ownerCompanySiret != null) {
                // Groupe PRO - argent va à l'entreprise du propriétaire
                EntrepriseManagerLogic.Entreprise ownerCompany = companyManager.getCompanyBySiret(ownerCompanySiret);
                if (ownerCompany != null) {
                    ownerCompany.setSolde(ownerCompany.getSolde() + totalCost);

                    // Notifier le propriétaire si en ligne
                    OfflinePlayer owner = Bukkit.getOfflinePlayer(group.getOwnerUuid());
                    if (owner.isOnline() && owner.getPlayer() != null) {
                        owner.getPlayer().sendMessage(ChatColor.GREEN + "Votre groupe professionnel a été loué pour " + actualDays + " jours!");
                        owner.getPlayer().sendMessage(ChatColor.YELLOW + "L'argent a été versé à " + ownerCompany.getNom() + ": +" + String.format("%.2f€", totalCost));
                    }
                } else {
                    // Entreprise n'existe plus - argent va à la ville
                    town.deposit(totalCost);
                }
            } else {
                // Groupe PARTICULIER - argent va au propriétaire
                OfflinePlayer owner = Bukkit.getOfflinePlayer(group.getOwnerUuid());
                RoleplayCity.getEconomy().depositPlayer(owner, totalCost);

                // Notifier si le propriétaire est en ligne
                if (owner.isOnline() && owner.getPlayer() != null) {
                    owner.getPlayer().sendMessage(ChatColor.GREEN + "Votre groupe de parcelles a été loué pour " + actualDays + " jours: +" + totalCost + "€");
                }
            }
        } else {
            town.deposit(totalCost);
        }

        // ⚠️ NOUVEAU SYSTÈME : Configurer la location directement sur le groupe autonome
        group.setRenter(renter.getUniqueId(), actualDays);
        group.setForRent(false);

        // Si groupe PRO, stocker le SIRET de l'entreprise du locataire
        if (isProfessionalGroup && renterCompanySiret != null) {
            group.setRenterCompanySiret(renterCompanySiret);
        }

        renter.sendMessage(ChatColor.GREEN + "Groupe loué avec succès !");
        renter.sendMessage(ChatColor.YELLOW + "Durée: " + ChatColor.GOLD + actualDays + " jours");
        renter.sendMessage(ChatColor.YELLOW + "Prix: " + ChatColor.GOLD + totalCost + "€");

        // Enregistrer la transaction
        addTransaction(townName, new PlotTransaction(
                PlotTransaction.TransactionType.RENT,
                renter.getUniqueId(),
                renter.getName(),
                totalCost,
                "Location groupe: " + group.getGroupName()
        ));

        // Notification de location de groupe
        notificationManager.notifyRentalSuccess(
                renter.getUniqueId(),
                "Groupe '" + group.getGroupName() + "' (" + group.getChunkKeys().size() + " terrains)",
                actualDays,
                totalCost
        );

        // Sauvegarder immédiatement
        townManager.saveTownsNow();
        return true;
    }

    /**
     * Recharge le solde d'un groupe loué
     */
    public boolean rechargePlotGroupRent(String townName, PlotGroup group, Player renter, int daysToAdd) {
        if (!group.isRentedBy(renter.getUniqueId())) {
            renter.sendMessage(ChatColor.RED + "Vous ne louez pas ce groupe de parcelles.");
            return false;
        }

        Town town = townManager.getTown(townName);
        if (town == null) return false;

        int currentDays = group.getRentDaysRemaining();
        int maxCanAdd = 30 - currentDays;

        if (maxCanAdd <= 0) {
            renter.sendMessage(ChatColor.YELLOW + "Votre solde est déjà au maximum (30 jours).");
            return false;
        }

        int actualDaysToAdd = Math.min(daysToAdd, maxCanAdd);
        double totalCost = group.getRentPricePerDay() * actualDaysToAdd;

        // ⚠️ NOUVEAU SYSTÈME : Détecter groupe PROFESSIONNEL directement
        boolean isProfessionalGroup = (group.getType() == PlotType.PROFESSIONNEL);
        String renterCompanySiret = group.getRenterCompanySiret();
        String ownerCompanySiret = group.getCompanySiret();
        CompanyPlotManager companyManager = plugin.getCompanyPlotManager();
        EntrepriseManagerLogic.Entreprise renterCompany = null;

        // Si groupe PRO, utiliser l'entreprise du locataire
        if (isProfessionalGroup) {
            if (renterCompanySiret != null) {
                renterCompany = companyManager.getCompanyBySiret(renterCompanySiret);
            }
            if (renterCompany == null) {
                renterCompany = companyManager.getPlayerCompany(renter);
            }

            if (renterCompany == null) {
                renter.sendMessage(ChatColor.RED + "Erreur: Entreprise introuvable.");
                return false;
            }

            // Vérifier les fonds de l'entreprise
            if (renterCompany.getSolde() < totalCost) {
                renter.sendMessage(ChatColor.RED + "Votre entreprise n'a pas assez d'argent !");
                renter.sendMessage(ChatColor.YELLOW + "Prix: " + ChatColor.GOLD + String.format("%.2f€", totalCost));
                renter.sendMessage(ChatColor.YELLOW + "Solde entreprise: " + ChatColor.GOLD + String.format("%.2f€", renterCompany.getSolde()));
                return false;
            }

            // Prélever de l'entreprise du locataire
            renterCompany.setSolde(renterCompany.getSolde() - totalCost);
        } else {
            // Groupe PARTICULIER : Vérifier l'argent personnel
            if (!RoleplayCity.getEconomy().has(renter, totalCost)) {
                renter.sendMessage(ChatColor.RED + "Vous n'avez pas assez d'argent. Prix: " + totalCost + "€");
                return false;
            }

            // Prélever l'argent personnel
            RoleplayCity.getEconomy().withdrawPlayer(renter, totalCost);
        }

        // Donner l'argent au propriétaire ou à la banque
        if (group.getOwnerUuid() != null) {
            if (isProfessionalGroup && ownerCompanySiret != null) {
                // Groupe PRO - argent va à l'entreprise du propriétaire
                EntrepriseManagerLogic.Entreprise ownerCompany = companyManager.getCompanyBySiret(ownerCompanySiret);
                if (ownerCompany != null) {
                    ownerCompany.setSolde(ownerCompany.getSolde() + totalCost);

                    // Notifier le propriétaire si en ligne
                    OfflinePlayer owner = Bukkit.getOfflinePlayer(group.getOwnerUuid());
                    if (owner.isOnline() && owner.getPlayer() != null) {
                        owner.getPlayer().sendMessage(ChatColor.GREEN + "Location rechargée!");
                        owner.getPlayer().sendMessage(ChatColor.YELLOW + "L'argent a été versé à " + ownerCompany.getNom() + ": +" + String.format("%.2f€", totalCost));
                    }
                } else {
                    // Entreprise n'existe plus - argent va à la ville
                    town.deposit(totalCost);
                }
            } else {
                // Groupe PARTICULIER - argent va au propriétaire
                OfflinePlayer owner = Bukkit.getOfflinePlayer(group.getOwnerUuid());
                RoleplayCity.getEconomy().depositPlayer(owner, totalCost);

                // Notifier si le propriétaire est en ligne
                if (owner.isOnline() && owner.getPlayer() != null) {
                    owner.getPlayer().sendMessage(ChatColor.GREEN + "Location rechargée: +" + totalCost + "€");
                }
            }
        } else {
            town.deposit(totalCost);
        }

        // ⚠️ NOUVEAU SYSTÈME : Recharger directement le groupe autonome
        int actualAdded = group.rechargeDays(actualDaysToAdd);

        renter.sendMessage(ChatColor.GREEN + "Solde rechargé : +" + actualAdded + " jours");
        renter.sendMessage(ChatColor.YELLOW + "Jours restants : " + ChatColor.GOLD + group.getRentDaysRemaining() + "/30");

        // Enregistrer la transaction
        addTransaction(townName, new PlotTransaction(
                PlotTransaction.TransactionType.RENT,
                renter.getUniqueId(),
                renter.getName(),
                totalCost,
                "Recharge groupe: " + group.getGroupName()
        ));

        // Sauvegarder immédiatement
        townManager.saveTownsNow();
        return true;
    }

    /**
     * Vérifie les locations de groupes expirées
     */
    public void checkExpiredGroupRents() {
        for (String townName : townManager.getTownNames()) {
            Town town = townManager.getTown(townName);
            if (town == null) continue;

            for (PlotGroup group : town.getPlotGroups().values()) {
                if (group.getRenterUuid() != null && group.getRentDaysRemaining() <= 0) {
                    // Notifier le locataire si en ligne
                    Player renter = Bukkit.getPlayer(group.getRenterUuid());
                    if (renter != null && renter.isOnline()) {
                        renter.sendMessage(ChatColor.RED + "Votre location du groupe '" + group.getGroupName() + "' a expiré.");
                    }

                    // Notification d'expiration de groupe
                    notificationManager.notifyRentExpired(
                            group.getRenterUuid(),
                            "Groupe '" + group.getGroupName() + "'",
                            townName
                    );

                    // ⚠️ NOUVEAU SYSTÈME : Retirer location directement du groupe autonome
                    group.clearRenter();

                    plugin.getLogger().info("Location de groupe expirée: " + group.getGroupName() + " dans " + townName);
                }
            }
        }
    }

    // === RëSULTAT DE COLLECTE ===

    public static class TaxCollectionResult {
        public final double totalCollected;
        public final int parcelsCollected;
        public final int unpaidCount;
        public final List<String> unpaidPlayers;

        public TaxCollectionResult(double totalCollected, int parcelsCollected,
                                   int unpaidCount, List<String> unpaidPlayers) {
            this.totalCollected = totalCollected;
            this.parcelsCollected = parcelsCollected;
            this.unpaidCount = unpaidCount;
            this.unpaidPlayers = unpaidPlayers;
        }
    }
}


