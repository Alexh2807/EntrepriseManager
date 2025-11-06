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
 * Gestionnaire de l'├®conomie des villes
 * G├¿re les transactions, taxes, ventes et locations de parcelles
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

        // SYNCHRONISATION : V├®rifier si la parcelle fait partie d'un groupe
        if (town.isPlotInAnyGroup(plot)) {
            seller.sendMessage(ChatColor.RED + "Cette parcelle fait partie d'un groupe !");
            seller.sendMessage(ChatColor.YELLOW + "Vous devez vendre le groupe entier depuis 'Mes Propri├®t├®s'.");
            return false;
        }

        // V├®rifier que le vendeur a le droit
        TownRole role = town.getMemberRole(seller.getUniqueId());
        if (role == null) {
            return false;
        }

        // Seul le maire/adjoint peut vendre des parcelles municipales
        if (plot.isMunicipal() && role != TownRole.MAIRE && role != TownRole.ADJOINT) {
            return false;
        }

        // Pour les parcelles priv├®es, seul le propri├®taire peut vendre
        if (!plot.isMunicipal() && plot.getOwnerUuid() != null &&
            !plot.getOwnerUuid().equals(seller.getUniqueId()) &&
            role != TownRole.MAIRE) {
            return false;
        }

        plot.setSalePrice(price);
        plot.setForSale(true);

        // Sauvegarder imm├®diatement
        townManager.saveTownsNow();
        return true;
    }

    /**
     * Ach├¿te une parcelle
     */
    public boolean buyPlot(String townName, Plot plot, Player buyer) {
        Town town = townManager.getTown(townName);
        if (town == null || !plot.isForSale()) {
            return false;
        }

        // SYNCHRONISATION : V├®rifier si la parcelle fait partie d'un groupe
        if (town.isPlotInAnyGroup(plot)) {
            buyer.sendMessage(ChatColor.RED + "Cette parcelle fait partie d'un groupe !");
            buyer.sendMessage(ChatColor.YELLOW + "Vous devez acheter le groupe entier.");
            return false;
        }

        // V├®rifier que l'acheteur est membre de la ville
        if (!town.isMember(buyer.getUniqueId())) {
            buyer.sendMessage(ChatColor.RED + "Vous devez ├¬tre membre de la ville pour acheter une parcelle.");
            return false;
        }

        // NOUVEAU : Validation entreprise pour terrain PROFESSIONNEL
        CompanyPlotManager companyManager = plugin.getCompanyPlotManager();
        if (!companyManager.validateCompanyOwnership(buyer, plot)) {
            return false; // Message d'erreur d├®j├á envoy├® par validateCompanyOwnership
        }

        double price = plot.getSalePrice();

        // NOUVEAU : Gestion diff├®rente selon le type de terrain
        boolean isProfessional = (plot.getType() == PlotType.PROFESSIONNEL);
        EntrepriseManagerLogic.Entreprise buyerCompany = null;

        if (isProfessional) {
            // Terrain PROFESSIONNEL : Acheter avec l'entreprise
            buyerCompany = companyManager.getPlayerCompany(buyer);
            if (buyerCompany == null) {
                buyer.sendMessage(ChatColor.RED + "Erreur: Entreprise introuvable.");
                return false;
            }

            // V├®rifier que l'entreprise a assez d'argent
            if (buyerCompany.getSolde() < price) {
                buyer.sendMessage(ChatColor.RED + "Votre entreprise n'a pas assez d'argent !");
                buyer.sendMessage(ChatColor.YELLOW + "Prix: " + ChatColor.GOLD + String.format("%.2fÔé¼", price));
                buyer.sendMessage(ChatColor.YELLOW + "Solde entreprise: " + ChatColor.GOLD + String.format("%.2fÔé¼", buyerCompany.getSolde()));
                buyer.sendMessage(ChatColor.GRAY + "D├®posez de l'argent avec /entreprise deposit");
                return false;
            }

            // Pr├®lever de l'entreprise
            buyerCompany.setSolde(buyerCompany.getSolde() - price);
        } else {
            // Terrain PARTICULIER : Acheter avec argent personnel
            if (!RoleplayCity.getEconomy().has(buyer, price)) {
                buyer.sendMessage(ChatColor.RED + "Vous n'avez pas assez d'argent. Prix: " + price + "Ôé¼");
                return false;
            }

            // Pr├®lever l'argent personnel
            RoleplayCity.getEconomy().withdrawPlayer(buyer, price);
        }

        // Si la parcelle appartenait ├á quelqu'un, lui donner l'argent
        if (plot.getOwnerUuid() != null) {
            UUID previousOwnerUuid = plot.getOwnerUuid();
            // Verser l'argent au propri├®taire (ou son entreprise si PRO)

            if (plot.getCompanySiret() != null) {
                // Ancien terrain PRO - argent va ├á l'ancienne entreprise
                EntrepriseManagerLogic.Entreprise previousCompany = companyManager.getCompanyBySiret(plot.getCompanySiret());
                if (previousCompany != null) {
                    previousCompany.setSolde(previousCompany.getSolde() + price);

                    // Notifier l'ancien g├®rant
                    OfflinePlayer previousOwner = Bukkit.getOfflinePlayer(previousOwnerUuid);
                    if (previousOwner.isOnline() && previousOwner.getPlayer() != null) {
                        previousOwner.getPlayer().sendMessage(ChatColor.GREEN + "Votre terrain professionnel a ├®t├® vendu pour " + price + "Ôé¼ !");
                        previousOwner.getPlayer().sendMessage(ChatColor.YELLOW + "L'argent a ├®t├® vers├® ├á " + previousCompany.getNom());
                    } else {
                        // FIX P2.4: Notification offline pour vente PRO
                        notificationManager.sendNotification(
                            previousOwnerUuid,
                            NotificationManager.NotificationType.ECONOMY,
                            "Terrain PRO vendu",
                            String.format("Votre terrain %s vendu pour %.2fÔé¼. Argent vers├® ├á %s",
                                plot.getCoordinates(), price, previousCompany.getNom())
                        );
                    }
                } else {
                    // Entreprise n'existe plus - argent va ├á la ville
                    town.deposit(price);
                }
            } else {
                // Ancien terrain PARTICULIER - argent va au propri├®taire
                OfflinePlayer previousOwner = Bukkit.getOfflinePlayer(previousOwnerUuid);
                RoleplayCity.getEconomy().depositPlayer(previousOwner, price);

                if (previousOwner.isOnline() && previousOwner.getPlayer() != null) {
                    previousOwner.getPlayer().sendMessage(ChatColor.GREEN + "Votre parcelle a ├®t├® vendue pour " + price + "Ôé¼ !");
                } else {
                    // FIX P2.4: Notification offline pour vente PARTICULIER
                    notificationManager.sendNotification(
                        previousOwnerUuid,
                        NotificationManager.NotificationType.ECONOMY,
                        "Terrain vendu",
                        String.format("Votre terrain %s vendu pour %.2fÔé¼",
                            plot.getCoordinates(), price)
                    );
                }
            }
        } else {
            // Parcelle municipale, l'argent va ├á la ville
            town.deposit(price);
        }

        // Transf├®rer la propri├®t├®
        plot.setOwner(buyer.getUniqueId(), buyer.getName());
        plot.setForSale(false);
        plot.setSalePrice(0);

        // FIX CRITIQUE: R├®initialiser TOUTES les dettes lors d'une vente
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

        // Messages personnalis├®s
        if (isProfessional && buyerCompany != null) {
            buyer.sendMessage(ChatColor.GREEN + "Ô£ô Terrain professionnel achet├® avec succ├¿s !");
            buyer.sendMessage(ChatColor.YELLOW + "Entreprise: " + ChatColor.WHITE + buyerCompany.getNom());
            buyer.sendMessage(ChatColor.YELLOW + "Coordonn├®es: " + ChatColor.WHITE + plot.getCoordinates());
            buyer.sendMessage(ChatColor.YELLOW + "Prix pay├®: " + ChatColor.GOLD + String.format("%.2fÔé¼", price));
            buyer.sendMessage(ChatColor.YELLOW + "Solde entreprise restant: " + ChatColor.GOLD + String.format("%.2fÔé¼", buyerCompany.getSolde()));
            buyer.sendMessage(ChatColor.GRAY + "Les taxes seront pr├®lev├®es du solde de l'entreprise.");
        } else {
            buyer.sendMessage(ChatColor.GREEN + "Vous avez achet├® la parcelle " + plot.getCoordinates() + " pour " + price + "Ôé¼ !");
        }

        // Notification d'achat
        notificationManager.notifyPurchaseSuccess(
            buyer.getUniqueId(),
            "Terrain " + plot.getCoordinates(),
            price
        );

        // Sauvegarder imm├®diatement
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

        // SYNCHRONISATION : V├®rifier si la parcelle fait partie d'un groupe
        if (town.isPlotInAnyGroup(plot)) {
            owner.sendMessage(ChatColor.RED + "Cette parcelle fait partie d'un groupe !");
            owner.sendMessage(ChatColor.YELLOW + "Vous devez louer le groupe entier depuis 'Mes Propri├®t├®s'.");
            return false;
        }

        // V├®rifier les permissions
        TownRole role = town.getMemberRole(owner.getUniqueId());
        if (role == null) {
            return false;
        }

        // Seul le maire/adjoint peut louer des parcelles municipales
        if (plot.isMunicipal() && role != TownRole.MAIRE && role != TownRole.ADJOINT) {
            return false;
        }

        // Pour les parcelles priv├®es, seul le propri├®taire peut louer
        if (!plot.isMunicipal() && plot.getOwnerUuid() != null &&
            !plot.getOwnerUuid().equals(owner.getUniqueId()) &&
            role != TownRole.MAIRE) {
            return false;
        }

        // Nouveau syst├¿me: calculer le prix par jour
        double pricePerDay = totalPrice / Math.max(1, durationDays);
        plot.setRentPricePerDay(pricePerDay);
        plot.setForRent(true);

        // Scanner et prot├®ger tous les blocs existants
        Chunk chunk = owner.getWorld().getChunkAt(plot.getChunkX(), plot.getChunkZ());
        plot.scanAndProtectExistingBlocks(chunk);

        // Sauvegarder imm├®diatement
        townManager.saveTownsNow();
        return true;
    }

    /**
     * Loue une parcelle pour la premi├¿re fois (paiement initial)
     */
    public boolean rentPlot(String townName, Plot plot, Player renter, int days) {
        Town town = townManager.getTown(townName);
        if (town == null || !plot.isForRent()) {
            return false;
        }

        // SYNCHRONISATION : V├®rifier si la parcelle fait partie d'un groupe
        if (town.isPlotInAnyGroup(plot)) {
            renter.sendMessage(ChatColor.RED + "Cette parcelle fait partie d'un groupe !");
            renter.sendMessage(ChatColor.YELLOW + "Vous devez louer le groupe entier.");
            return false;
        }

        // V├®rifier que le locataire est membre de la ville
        if (!town.isMember(renter.getUniqueId())) {
            renter.sendMessage(ChatColor.RED + "Vous devez ├¬tre membre de la ville pour louer une parcelle.");
            return false;
        }

        // NOUVEAU : Emp├¬cher le propri├®taire de louer son propre terrain
        if (plot.getOwnerUuid() != null && plot.getOwnerUuid().equals(renter.getUniqueId())) {
            renter.sendMessage(ChatColor.RED + "Vous ne pouvez pas louer votre propre parcelle !");
            return false;
        }

        // NOUVEAU : Validation entreprise pour terrain PROFESSIONNEL
        CompanyPlotManager companyManager = plugin.getCompanyPlotManager();
        if (plot.getType() == com.gravityyfh.roleplaycity.town.data.PlotType.PROFESSIONNEL) {
            if (!companyManager.validateCompanyOwnership(renter, plot)) {
                return false; // Message d'erreur d├®j├á envoy├® par validateCompanyOwnership
            }

            // R├®cup├®rer le SIRET s├®lectionn├® du cache (mis par CompanySelectionGUI)
            String selectedSiret = plugin.getTownCommandHandler().getAndClearSelectedCompany(renter.getUniqueId());
            if (selectedSiret == null) {
                // Pas de SIRET dans le cache, r├®cup├®rer l'entreprise du joueur
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

        // Limiter ├á 30 jours max
        int actualDays = Math.min(days, 30);
        double totalCost = plot.getRentPricePerDay() * actualDays;

        // NOUVEAU : Gestion diff├®rente selon le type de terrain
        boolean isProfessional = (plot.getType() == PlotType.PROFESSIONNEL);
        EntrepriseManagerLogic.Entreprise renterCompany = null;

        if (isProfessional) {
            // Terrain PROFESSIONNEL : Louer avec l'entreprise
            renterCompany = companyManager.getPlayerCompany(renter);
            if (renterCompany == null) {
                renter.sendMessage(ChatColor.RED + "Erreur: Entreprise introuvable.");
                return false;
            }

            // V├®rifier que l'entreprise a assez d'argent
            if (renterCompany.getSolde() < totalCost) {
                renter.sendMessage(ChatColor.RED + "Votre entreprise n'a pas assez d'argent !");
                renter.sendMessage(ChatColor.YELLOW + "Prix: " + ChatColor.GOLD + String.format("%.2fÔé¼", totalCost));
                renter.sendMessage(ChatColor.YELLOW + "Solde entreprise: " + ChatColor.GOLD + String.format("%.2fÔé¼", renterCompany.getSolde()));
                return false;
            }

            // Pr├®lever de l'entreprise
            renterCompany.setSolde(renterCompany.getSolde() - totalCost);
        } else {
            // Terrain PARTICULIER : V├®rifier l'argent personnel
            if (!RoleplayCity.getEconomy().has(renter, totalCost)) {
                renter.sendMessage(ChatColor.RED + "Vous n'avez pas assez d'argent. Prix: " +
                    String.format("%.2fÔé¼", totalCost));
                return false;
            }

            // Pr├®lever l'argent personnel
            RoleplayCity.getEconomy().withdrawPlayer(renter, totalCost);
        }

        // Donner l'argent au propri├®taire ou ├á la ville
        if (plot.getOwnerUuid() != null) {
            if (isProfessional && plot.getCompanySiret() != null) {
                // Terrain PRO - argent va ├á l'entreprise du propri├®taire
                EntrepriseManagerLogic.Entreprise ownerCompany = companyManager.getCompanyBySiret(plot.getCompanySiret());
                if (ownerCompany != null) {
                    ownerCompany.setSolde(ownerCompany.getSolde() + totalCost);

                    // Notifier le propri├®taire si en ligne
                    OfflinePlayer owner = Bukkit.getOfflinePlayer(plot.getOwnerUuid());
                    if (owner.isOnline() && owner.getPlayer() != null) {
                        owner.getPlayer().sendMessage(ChatColor.GREEN + "Votre terrain professionnel a ├®t├® lou├® pour " + actualDays + " jours!");
                        owner.getPlayer().sendMessage(ChatColor.YELLOW + "L'argent a ├®t├® vers├® ├á " + ownerCompany.getNom() + ": +" + String.format("%.2fÔé¼", totalCost));
                    }
                } else {
                    // Entreprise n'existe plus - argent va ├á la ville
                    town.deposit(totalCost);
                }
            } else {
                // Terrain PARTICULIER - argent va au propri├®taire
                OfflinePlayer owner = Bukkit.getOfflinePlayer(plot.getOwnerUuid());
                RoleplayCity.getEconomy().depositPlayer(owner, totalCost);

                // Notifier si le propri├®taire est en ligne
                if (owner.isOnline() && owner.getPlayer() != null) {
                    owner.getPlayer().sendMessage(ChatColor.GREEN + "Votre parcelle a ├®t├® lou├®e pour " +
                        actualDays + " jours (" + String.format("%.2fÔé¼", totalCost) + ") !");
                }
            }
        } else {
            // Pas de propri├®taire = l'argent va ├á la ville
            town.deposit(totalCost);
        }

        // D├®finir le locataire avec son solde de jours
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

        renter.sendMessage(ChatColor.GREEN + "Vous avez lou├® la parcelle " + plot.getCoordinates() +
            " pour " + actualDays + " jours !");

        // Notification de location
        notificationManager.notifyRentalSuccess(
            renter.getUniqueId(),
            "Terrain " + plot.getCoordinates(),
            actualDays,
            totalCost
        );

        // Sauvegarder imm├®diatement
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

        // V├®rifier que c'est bien le locataire actuel
        if (!plot.isRentedBy(renter.getUniqueId())) {
            renter.sendMessage(ChatColor.RED + "Vous ne louez pas cette parcelle.");
            return false;
        }

        // Calculer combien de jours peuvent ├¬tre ajout├®s (max 30 total)
        int currentDays = plot.getRentDaysRemaining();
        int maxCanAdd = 30 - currentDays;

        if (maxCanAdd <= 0) {
            renter.sendMessage(ChatColor.YELLOW + "Votre solde est d├®j├á au maximum (30 jours).");
            return false;
        }

        int actualDaysToAdd = Math.min(daysToAdd, maxCanAdd);
        double totalCost = plot.getRentPricePerDay() * actualDaysToAdd;

        // NOUVEAU : Gestion diff├®rente selon le type de terrain
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

            // V├®rifier que l'entreprise a assez d'argent
            if (renterCompany.getSolde() < totalCost) {
                renter.sendMessage(ChatColor.RED + "Votre entreprise n'a pas assez d'argent !");
                renter.sendMessage(ChatColor.YELLOW + "Prix: " + ChatColor.GOLD + String.format("%.2fÔé¼", totalCost));
                renter.sendMessage(ChatColor.YELLOW + "Solde entreprise: " + ChatColor.GOLD + String.format("%.2fÔé¼", renterCompany.getSolde()));
                return false;
            }

            // Pr├®lever de l'entreprise
            renterCompany.setSolde(renterCompany.getSolde() - totalCost);
        } else {
            // Terrain PARTICULIER : V├®rifier l'argent personnel
            if (!RoleplayCity.getEconomy().has(renter, totalCost)) {
                renter.sendMessage(ChatColor.RED + "Vous n'avez pas assez d'argent. Prix: " +
                    String.format("%.2fÔé¼", totalCost) + " pour " + actualDaysToAdd + " jours");
                return false;
            }

            // Pr├®lever l'argent personnel
            RoleplayCity.getEconomy().withdrawPlayer(renter, totalCost);
        }

        // Donner l'argent au propri├®taire ou ├á la ville
        if (plot.getOwnerUuid() != null) {
            if (isProfessional && plot.getCompanySiret() != null) {
                // Terrain PRO - argent va ├á l'entreprise du propri├®taire
                EntrepriseManagerLogic.Entreprise ownerCompany = companyManager.getCompanyBySiret(plot.getCompanySiret());
                if (ownerCompany != null) {
                    ownerCompany.setSolde(ownerCompany.getSolde() + totalCost);

                    // Notifier le propri├®taire si en ligne
                    OfflinePlayer owner = Bukkit.getOfflinePlayer(plot.getOwnerUuid());
                    if (owner.isOnline() && owner.getPlayer() != null) {
                        owner.getPlayer().sendMessage(ChatColor.GREEN + "Location recharg├®e: +" + actualDaysToAdd + " jours!");
                        owner.getPlayer().sendMessage(ChatColor.YELLOW + "L'argent a ├®t├® vers├® ├á " + ownerCompany.getNom() + ": +" + String.format("%.2fÔé¼", totalCost));
                    }
                } else {
                    // Entreprise n'existe plus - argent va ├á la ville
                    town.deposit(totalCost);
                }
            } else {
                // Terrain PARTICULIER - argent va au propri├®taire
                OfflinePlayer owner = Bukkit.getOfflinePlayer(plot.getOwnerUuid());
                RoleplayCity.getEconomy().depositPlayer(owner, totalCost);

                // Notifier si le propri├®taire est en ligne
                if (owner.isOnline() && owner.getPlayer() != null) {
                    owner.getPlayer().sendMessage(ChatColor.GREEN + "Location recharg├®e : +" + actualDaysToAdd +
                        " jours (" + String.format("%.2fÔé¼", totalCost) + ")");
                }
            }
        } else {
            // Pas de propri├®taire = l'argent va ├á la ville
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

        renter.sendMessage(ChatColor.GREEN + "Solde recharg├® : +" + actualAdded + " jours");
        renter.sendMessage(ChatColor.YELLOW + "Jours restants : " + ChatColor.GOLD + plot.getRentDaysRemaining() + "/30");

        // Sauvegarder imm├®diatement
        townManager.saveTownsNow();
        return true;
    }

    /**
     * V├®rifie les locations expir├®es et les termine
     */
    public void checkExpiredRents() {
        for (Town town : townManager.getAllTowns()) {
            for (Plot plot : town.getPlots().values()) {
                if (plot.getRenterUuid() != null && plot.isRentExpired()) {
                    // Location expir├®e
                    UUID renterUuid = plot.getRenterUuid();
                    Player renter = Bukkit.getPlayer(renterUuid);

                    if (renter != null && renter.isOnline()) {
                        renter.sendMessage(ChatColor.YELLOW + "Votre location de la parcelle " +
                            plot.getCoordinates() + " a expir├®.");
                    }

                    // Notification d'expiration
                    notificationManager.notifyRentExpired(
                        renterUuid,
                        "Terrain " + plot.getCoordinates(),
                        town.getName()
                    );

                    plot.clearRenter();
                    plot.setForRent(true); // Remettre en location
                    plugin.getLogger().info("Location expir├®e pour parcelle " + plot.getCoordinates() +
                        " dans " + town.getName());
                }
            }
        }
    }

    // === COLLECTE DES TAXES ===

    /**
     * Collecte les taxes de toutes les parcelles d'une ville
     * SYST├êME AUTOMATIQUE : G├¿re les groupes de terrains automatiquement
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
            plotsInGroupsProcessed.addAll(group.getPlotKeys());

            double groupTax = 0;
            List<Plot> groupPlots = new ArrayList<>();
            for (String plotKey : group.getPlotKeys()) {
                String[] parts = plotKey.split(":");
                if (parts.length == 3) {
                    Plot plot = town.getPlot(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                    if (plot != null) {
                        groupPlots.add(plot);
                        groupTax += plot.getDailyTax();
                    }
                }
            }

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

            boolean isProfessionalGroup = false;
            String companySiret = null;
            EntrepriseManagerLogic.Entreprise company = null;

            for (Plot plot : groupPlots) {
                if (plot.getType() == PlotType.PROFESSIONNEL && plot.getCompanySiret() != null) {
                    isProfessionalGroup = true;
                    companySiret = plot.getCompanySiret();
                    company = plugin.getCompanyPlotManager().getCompanyBySiret(companySiret);
                    break;
                }
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
                parcelsWithTax += groupPlots.size();

                for (Plot plot : groupPlots) {
                    if (isProfessionalGroup) {
                        if (plot.getCompanyDebtAmount() > 0) {
                            plot.resetDebt();
                        }
                    } else {
                        if (plot.getParticularDebtAmount() > 0) {
                            plot.resetParticularDebt();
                        }
                    }
                }

                if (payer.isOnline() && payer.getPlayer() != null) {
                    if (isProfessionalGroup && company != null) {
                        payer.getPlayer().sendMessage(ChatColor.YELLOW + "Ôé¼ Taxe entreprise (groupe): " + ChatColor.GOLD +
                            String.format("%.2fÔé¼", groupTax) + ChatColor.GRAY + " pr├®lev├®e pour " +
                            group.getGroupName() + ChatColor.GRAY + " (" + groupPlots.size() + " parcelles)");
                    } else {
                        payer.getPlayer().sendMessage(ChatColor.YELLOW + "Taxe groupe: " + ChatColor.GOLD +
                            String.format("%.2fÔé¼", groupTax) + ChatColor.GRAY + " pr├®lev├®e pour " +
                            group.getGroupName() + ChatColor.GRAY + " (" + groupPlots.size() + " parcelles)");
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

                Plot firstPlot = groupPlots.isEmpty() ? null : groupPlots.get(0);
                if (firstPlot != null) {
                    if (isProfessionalGroup && company != null) {
                        double newDebt = firstPlot.getCompanyDebtAmount() + groupTax;
                        firstPlot.setCompanyDebtAmount(newDebt);

                        if (firstPlot.getDebtWarningCount() == 0) {
                            firstPlot.setLastDebtWarningDate(LocalDateTime.now());
                            firstPlot.setDebtWarningCount(1);

                                                    String gerantUuidStr = company.getGerantUUID();
                        if (gerantUuidStr != null) {
                            try {
                                UUID gerantUuid = UUID.fromString(gerantUuidStr);
                                debtNotificationService.refresh(gerantUuid, DebtNotificationService.DebtUpdateReason.ECONOMY_EVENT);
                            } catch (IllegalArgumentException ex) {
                                plugin.getLogger().warning("UUID invalide pour le gérant de l'entreprise " + company.getNom() + ": " + gerantUuidStr);
                            }
                        }
                            if (gerantUuidStr != null) {
                                UUID gerantUuid = UUID.fromString(gerantUuidStr);
                                OfflinePlayer gerant = Bukkit.getOfflinePlayer(gerantUuid);
                                if (false && gerant.isOnline() && gerant.getPlayer() != null) {
                                    Player gerantPlayer = gerant.getPlayer();
                                    gerantPlayer.sendMessage("");
                                    gerantPlayer.sendMessage(ChatColor.RED + "ÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉ AVERTISSEMENT - DETTE DE TERRAIN ÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉ");
                                    gerantPlayer.sendMessage(ChatColor.YELLOW + "Type: " + ChatColor.WHITE + "Groupe Professionnel");
                                    gerantPlayer.sendMessage(ChatColor.YELLOW + "Entreprise: " + ChatColor.WHITE + company.getNom());
                                    gerantPlayer.sendMessage(ChatColor.YELLOW + "Groupe: " + ChatColor.WHITE +
                                        group.getGroupName() + ChatColor.GRAY + " (" + groupPlots.size() + " parcelles)");
                                    gerantPlayer.sendMessage(ChatColor.YELLOW + "Ville: " + ChatColor.WHITE + townName);
                                    gerantPlayer.sendMessage(ChatColor.YELLOW + "Dette accumul├®e: " + ChatColor.RED +
                                        String.format("%.2fÔé¼", newDebt));
                                    gerantPlayer.sendMessage("");
                                    gerantPlayer.sendMessage(ChatColor.GOLD + "Votre entreprise n'a pas pu payer les taxes du groupe !");
                                    gerantPlayer.sendMessage(ChatColor.GOLD + "D├®lai: " + ChatColor.WHITE + "7 jours" +
                                        ChatColor.GOLD + " pour renflouer le compte.");
                                    gerantPlayer.sendMessage(ChatColor.RED + "Si la dette n'est pas pay├®e, les terrains seront SAISIS automatiquement.");
                                    gerantPlayer.sendMessage("");
                                }
                            }

                            plugin.getLogger().warning(String.format(
                                "[TownEconomyManager] Entreprise %s (SIRET %s) - Dette de %.2fÔé¼ sur groupe %s (%s)",
                                company.getNom(), company.getSiret(), newDebt, group.getGroupName(), townName
                            ));
                        } else {
                            debtNotificationService.refresh(payerUuid, DebtNotificationService.DebtUpdateReason.ECONOMY_EVENT);
                        }

                        CompanyPlotManager companyManager = plugin.getCompanyPlotManager();
                        if (companyManager.checkCompanyDebtStatus(firstPlot)) {
                            companyManager.seizePlotForDebt(firstPlot, townName);
                            town.removePlotGroup(group.getGroupId());
                        }
                    } else {
                        double newDebt = firstPlot.getParticularDebtAmount() + groupTax;
                        firstPlot.setParticularDebtAmount(newDebt);

                        if (firstPlot.getParticularDebtWarningCount() == 0) {
                            firstPlot.setParticularLastDebtWarningDate(LocalDateTime.now());
                            firstPlot.setParticularDebtWarningCount(1);

                            if (false && payer.isOnline() && payer.getPlayer() != null) {
                                payer.getPlayer().sendMessage("");
                                payer.getPlayer().sendMessage(ChatColor.RED + "ÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉ AVERTISSEMENT - DETTE DE TERRAIN ÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉ");
                                payer.getPlayer().sendMessage(ChatColor.YELLOW + "Type: " + ChatColor.WHITE + "Groupe Particulier");
                                payer.getPlayer().sendMessage(ChatColor.YELLOW + "Groupe: " + ChatColor.WHITE +
                                    group.getGroupName() + ChatColor.GRAY + " (" + groupPlots.size() + " parcelles)");
                                payer.getPlayer().sendMessage(ChatColor.YELLOW + "Ville: " + ChatColor.WHITE + townName);
                                payer.getPlayer().sendMessage(ChatColor.YELLOW + "Dette accumul├®e: " + ChatColor.RED +
                                    String.format("%.2fÔé¼", newDebt));
                                payer.getPlayer().sendMessage("");
                                payer.getPlayer().sendMessage(ChatColor.GOLD + "Vous n'avez pas pu payer les taxes du groupe !");
                                payer.getPlayer().sendMessage(ChatColor.GOLD + "D├®lai: " + ChatColor.WHITE + "7 jours" +
                                    ChatColor.GOLD + " pour renflouer le compte.");
                                payer.getPlayer().sendMessage(ChatColor.RED + "Si la dette n'est pas pay├®e, les terrains seront SAISIS automatiquement.");
                                payer.getPlayer().sendMessage("");
                            }

                            debtNotificationService.refresh(payerUuid, DebtNotificationService.DebtUpdateReason.ECONOMY_EVENT);
                        } else {
                            debtNotificationService.refresh(payerUuid, DebtNotificationService.DebtUpdateReason.ECONOMY_EVENT);
                        }

                        if (firstPlot.getParticularLastDebtWarningDate() != null) {
                            long daysSinceWarning = Duration.between(firstPlot.getParticularLastDebtWarningDate(), LocalDateTime.now()).toDays();
                            if (daysSinceWarning >= 7) {
                                plugin.getLogger().warning(String.format(
                                    "[TownEconomyManager] SAISIE AUTO - Groupe %s saisi pour dette (Propri├®taire: %s, Dette: %.2fÔé¼)",
                                    group.getGroupName(), payerName, firstPlot.getParticularDebtAmount()
                                ));

                                if (false && payer.isOnline() && payer.getPlayer() != null) {
                                    payer.getPlayer().sendMessage("");
                                    payer.getPlayer().sendMessage(ChatColor.DARK_RED + "ÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉ SAISIE DE GROUPE ÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉ");
                                    payer.getPlayer().sendMessage(ChatColor.RED + "Votre groupe " + group.getGroupName());
                                    payer.getPlayer().sendMessage(ChatColor.RED + "a ├®t├® saisi pour dette impay├®e!");
                                    payer.getPlayer().sendMessage("");
                                    payer.getPlayer().sendMessage(ChatColor.YELLOW + "Dette: " + ChatColor.GOLD + String.format("%.2fÔé¼", firstPlot.getParticularDebtAmount()));
                                    payer.getPlayer().sendMessage(ChatColor.YELLOW + "Parcelles: " + groupPlots.size());
                                    payer.getPlayer().sendMessage(ChatColor.GRAY + "Les terrains retournent ├á la ville.");
                                    payer.getPlayer().sendMessage("");
                                }

                                for (Plot plot : groupPlots) {
                                    townManager.transferPlotToTown(plot, "Dette impay├®e groupe: " + String.format("%.2fÔé¼", plot.getParticularDebtAmount()));
                                    plot.setForSale(true);
                                    plot.setSalePrice(1000.0);
                                }

                                town.removePlotGroup(group.getGroupId());
                            }
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
                                gerant.sendMessage(ChatColor.YELLOW + "Ôé¼ Taxe entreprise: " + ChatColor.GOLD + String.format("%.2fÔé¼", tax) +
                                    ChatColor.GRAY + " pr├®lev├®e pour " + plot.getCoordinates());
                                gerant.sendMessage(ChatColor.GRAY + "Entreprise: " + company.getNom() +
                                    " - Solde restant: " + String.format("%.2fÔé¼", company.getSolde()));
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

                if (false && payer.isOnline() && payer.getPlayer() != null) {
                    payer.getPlayer().sendMessage(ChatColor.YELLOW + "Taxe parcelle: " + ChatColor.GOLD +
                        String.format("%.2fÔé¼", tax) + ChatColor.GRAY + " pr├®lev├®e pour " + plot.getCoordinates());
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

                    if (false && payer.isOnline() && payer.getPlayer() != null) {
                        payer.getPlayer().sendMessage("");
                        payer.getPlayer().sendMessage(ChatColor.RED + "ÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉ ALERTE DETTE - PARTICULIER ÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉ");
                        payer.getPlayer().sendMessage(ChatColor.YELLOW + "Dette actuelle: " + ChatColor.GOLD +
                            String.format("%.2fÔé¼", newDebt));
                        payer.getPlayer().sendMessage(ChatColor.YELLOW + "Terrain: " + ChatColor.WHITE +
                            plot.getCoordinates() + ChatColor.GRAY + " (ville: " + townName + ")");
                        payer.getPlayer().sendMessage("");
                        payer.getPlayer().sendMessage(ChatColor.RED + "ÔÜá Vous avez 7 jours pour rembourser");
                        payer.getPlayer().sendMessage(ChatColor.RED + "   avant saisie automatique du terrain!");
                        payer.getPlayer().sendMessage("");
                        payer.getPlayer().sendMessage(ChatColor.YELLOW + "Ôé¼ R├®glez vos dettes via:");
                        payer.getPlayer().sendMessage(ChatColor.GRAY + "   /ville -> R├®gler vos Dettes");
                        payer.getPlayer().sendMessage("");
                    }

                    debtNotificationService.refresh(payerUuid, DebtNotificationService.DebtUpdateReason.ECONOMY_EVENT);
                } else {
                    debtNotificationService.refresh(payerUuid, DebtNotificationService.DebtUpdateReason.ECONOMY_EVENT);
                }

                if (plot.getParticularLastDebtWarningDate() != null) {
                    long daysSinceWarning = Duration.between(plot.getParticularLastDebtWarningDate(), LocalDateTime.now()).toDays();
                    if (daysSinceWarning >= 7) {
                        plugin.getLogger().warning(String.format(
                            "[TownEconomyManager] SAISIE AUTO - Terrain %s:%d,%d saisi pour dette (Particulier %s, Dette: %.2fÔé¼)",
                            plot.getWorldName(), plot.getChunkX(), plot.getChunkZ(), payerName, plot.getParticularDebtAmount()
                        ));

                        if (false && payer.isOnline() && payer.getPlayer() != null) {
                            payer.getPlayer().sendMessage("");
                            payer.getPlayer().sendMessage(ChatColor.DARK_RED + "ÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉ SAISIE DE TERRAIN ÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉ");
                            payer.getPlayer().sendMessage(ChatColor.RED + "Votre terrain " + plot.getCoordinates());
                            payer.getPlayer().sendMessage(ChatColor.RED + "a ├®t├® saisi pour dette impay├®e!");
                            payer.getPlayer().sendMessage("");
                            payer.getPlayer().sendMessage(ChatColor.YELLOW + "Dette: " + ChatColor.GOLD +
                                String.format("%.2fÔé¼", plot.getParticularDebtAmount()));
                            payer.getPlayer().sendMessage(ChatColor.GRAY + "Le terrain retourne ├á la ville.");
                            payer.getPlayer().sendMessage("");
                        }

                        townManager.transferPlotToTown(plot, "Dette impay├®e particulier: " +
                            String.format("%.2fÔé¼", plot.getParticularDebtAmount()));
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

        // SYST├êME AUTOMATIQUE : Collecter d'abord les taxes des groupes
        Set<String> plotsInGroupsProcessed = new HashSet<>();
        for (PlotGroup group : town.getPlotGroups().values()) {
            plotsInGroupsProcessed.addAll(group.getPlotKeys());

            // Calculer la taxe horaire totale du groupe
            double groupDailyTax = 0;
            List<Plot> groupPlots = new ArrayList<>();
            for (String plotKey : group.getPlotKeys()) {
                String[] parts = plotKey.split(":");
                if (parts.length == 3) {
                    Plot plot = town.getPlot(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                    if (plot != null) {
                        groupPlots.add(plot);
                        groupDailyTax += plot.getDailyTax();
                    }
                }
            }

            if (groupDailyTax <= 0) continue;

            // NOUVEAU : Montant HORAIRE au lieu de quotidien
            double groupHourlyTax = groupDailyTax / 24.0;

            // D├®terminer qui paie pour le groupe
            UUID payerUuid = group.getRenterUuid() != null ? group.getRenterUuid() : group.getOwnerUuid();
            if (payerUuid == null) continue;

            // NOUVEAU : Pr├®lever m├¬me OFFLINE
            OfflinePlayer payer = Bukkit.getOfflinePlayer(payerUuid);
            String payerName = payer.getName() != null ? payer.getName() : payerUuid.toString();

            // NOUVEAU : D├®tecter si c'est un groupe PROFESSIONNEL (entreprise)
            boolean isProfessionalGroup = false;
            String companySiret = null;
            EntrepriseManagerLogic.Entreprise company = null;

            // V├®rifier si au moins une parcelle du groupe est PROFESSIONNEL
            for (Plot plot : groupPlots) {
                if (plot.getType() == PlotType.PROFESSIONNEL && plot.getCompanySiret() != null) {
                    isProfessionalGroup = true;
                    companySiret = plot.getCompanySiret();
                    CompanyPlotManager companyManager = plugin.getCompanyPlotManager();
                    company = companyManager.getCompanyBySiret(companySiret);
                    break;
                }
            }

            // Pr├®lever la taxe du groupe
            boolean paymentSuccess = false;

            if (isProfessionalGroup && company != null) {
                // GROUPE PROFESSIONNEL : Pr├®lever du solde de l'entreprise
                if (company.getSolde() >= groupHourlyTax) {
                    company.setSolde(company.getSolde() - groupHourlyTax);
                    paymentSuccess = true;
                }
            } else {
                // GROUPE PARTICULIER : Pr├®lever de l'argent personnel
                if (RoleplayCity.getEconomy().has(payer, groupHourlyTax)) {
                    RoleplayCity.getEconomy().withdrawPlayer(payer, groupHourlyTax);
                    paymentSuccess = true;
                }
            }

            if (paymentSuccess) {
                town.deposit(groupHourlyTax);
                totalCollected += groupHourlyTax;
                parcelsWithTax += groupPlots.size();

                // R├®initialiser les dettes de toutes les parcelles du groupe si endett├®es
                for (Plot plot : groupPlots) {
                    if (isProfessionalGroup) {
                        // R├®initialiser dettes entreprise
                        if (plot.getCompanyDebtAmount() > 0) {
                            plot.resetDebt();
                        }
                    } else {
                        // R├®initialiser dettes particulier
                        if (plot.getParticularDebtAmount() > 0) {
                            plot.resetParticularDebt();
                        }
                    }
                }

                // Enregistrer pour le rapport individuel
                playerTaxes.put(payerUuid, playerTaxes.getOrDefault(payerUuid, 0.0) + groupHourlyTax);

                // Message si en ligne
                if (false && payer.isOnline() && payer.getPlayer() != null) {
                    if (isProfessionalGroup && company != null) {
                        payer.getPlayer().sendMessage(ChatColor.YELLOW + "­ƒÆ╝ Taxe horaire entreprise (groupe): " + ChatColor.GOLD +
                            String.format("%.2fÔé¼", groupHourlyTax) + ChatColor.GRAY + " pr├®lev├®e pour " +
                            group.getGroupName() + ChatColor.GRAY + " (" + groupPlots.size() + " parcelles)");
                    } else {
                        payer.getPlayer().sendMessage(ChatColor.YELLOW + "­ƒÆ░ Taxe horaire groupe: " + ChatColor.GOLD +
                            String.format("%.2fÔé¼", groupHourlyTax) + ChatColor.GRAY + " pr├®lev├®e pour " +
                            group.getGroupName() + ChatColor.GRAY + " (" + groupPlots.size() + " parcelles)");
                    }
                }

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
                // NOUVEAU : Fonds insuffisants - cr├®er/augmenter la dette sur la premi├¿re parcelle du groupe
                unpaidPlayers.add(payerName);

                // Utiliser la premi├¿re parcelle du groupe pour stocker la dette totale
                Plot firstPlot = groupPlots.isEmpty() ? null : groupPlots.get(0);
                if (firstPlot != null) {
                    double newDebt;

                    // D├®terminer le type de dette selon le type de groupe
                    if (isProfessionalGroup && company != null) {
                        // GROUPE PROFESSIONNEL : Dette d'entreprise
                        newDebt = firstPlot.getCompanyDebtAmount() + groupHourlyTax;
                        firstPlot.setCompanyDebtAmount(newDebt);

                        // Si c'est le premier avertissement
                        if (firstPlot.getDebtWarningCount() == 0) {
                            firstPlot.setLastDebtWarningDate(LocalDateTime.now());
                            firstPlot.setDebtWarningCount(1);

                            // Notifier le g├®rant - FORMAT UNIFI├ë
                                                    String gerantUuidStr = company.getGerantUUID();
                        if (gerantUuidStr != null) {
                            try {
                                UUID gerantUuid = UUID.fromString(gerantUuidStr);
                                debtNotificationService.refresh(gerantUuid, DebtNotificationService.DebtUpdateReason.ECONOMY_EVENT);
                            } catch (IllegalArgumentException ex) {
                                plugin.getLogger().warning("UUID invalide pour le gérant de l'entreprise " + company.getNom() + ": " + gerantUuidStr);
                            }
                        }
                            if (gerantUuidStr != null) {
                                UUID gerantUuid = UUID.fromString(gerantUuidStr);
                                OfflinePlayer gerant = Bukkit.getOfflinePlayer(gerantUuid);
                                if (false && gerant.isOnline() && gerant.getPlayer() != null) {
                                    Player gerantPlayer = gerant.getPlayer();
                                    gerantPlayer.sendMessage("");
                                    gerantPlayer.sendMessage(ChatColor.RED + "ÔÜáÔÜáÔÜá AVERTISSEMENT - DETTE DE TERRAIN ÔÜáÔÜáÔÜá");
                                    gerantPlayer.sendMessage(ChatColor.YELLOW + "Type: " + ChatColor.WHITE + "Groupe Professionnel");
                                    gerantPlayer.sendMessage(ChatColor.YELLOW + "Entreprise: " + ChatColor.WHITE + company.getNom());
                                    gerantPlayer.sendMessage(ChatColor.YELLOW + "Groupe: " + ChatColor.WHITE + group.getGroupName() +
                                        ChatColor.GRAY + " (" + groupPlots.size() + " parcelles)");
                                    gerantPlayer.sendMessage(ChatColor.YELLOW + "Ville: " + ChatColor.WHITE + townName);
                                    gerantPlayer.sendMessage(ChatColor.YELLOW + "Dette accumul├®e: " + ChatColor.RED +
                                        String.format("%.2fÔé¼", newDebt));
                                    gerantPlayer.sendMessage("");
                                    gerantPlayer.sendMessage(ChatColor.GOLD + "Votre entreprise n'a pas pu payer les taxes du groupe !");
                                    gerantPlayer.sendMessage(ChatColor.GOLD + "D├®lai: " + ChatColor.WHITE + "7 jours" +
                                        ChatColor.GOLD + " pour renflouer le compte.");
                                    gerantPlayer.sendMessage(ChatColor.RED + "Si la dette n'est pas pay├®e, les terrains seront SAISIS automatiquement.");
                                    gerantPlayer.sendMessage("");
                                }
                            }

                            debtNotificationService.refresh(payerUuid, DebtNotificationService.DebtUpdateReason.ECONOMY_EVENT);
                        } else {
                            // Dette d├®j├á existante
                                                    String gerantUuidStr = company.getGerantUUID();
                        if (gerantUuidStr != null) {
                            try {
                                UUID gerantUuid = UUID.fromString(gerantUuidStr);
                                debtNotificationService.refresh(gerantUuid, DebtNotificationService.DebtUpdateReason.ECONOMY_EVENT);
                            } catch (IllegalArgumentException ex) {
                                plugin.getLogger().warning("UUID invalide pour le gérant de l'entreprise " + company.getNom() + ": " + gerantUuidStr);
                            }
                        }
                            if (gerantUuidStr != null) {
                                UUID gerantUuid = UUID.fromString(gerantUuidStr);
                                OfflinePlayer gerant = Bukkit.getOfflinePlayer(gerantUuid);
                                if (false && gerant.isOnline() && gerant.getPlayer() != null) {
                                    long daysRemaining = 7 - java.time.Duration.between(firstPlot.getLastDebtWarningDate(), LocalDateTime.now()).toDays();
                                    gerant.getPlayer().sendMessage(ChatColor.RED + "ÔÜá Dette groupe augment├®e: " +
                                        ChatColor.GOLD + String.format("%.2fÔé¼", newDebt) + ChatColor.RED +
                                        " (J-" + daysRemaining + ")");
                                    gerant.getPlayer().sendMessage(ChatColor.YELLOW + "   Groupe: " + ChatColor.WHITE + group.getGroupName() +
                                        ChatColor.GRAY + " (" + groupPlots.size() + " parcelles)");
                                    gerant.getPlayer().sendMessage(ChatColor.YELLOW + "   R├®glez via: " +
                                        ChatColor.WHITE + "/ville ÔåÆ R├®gler vos Dettes");
                                }
                            }

                            firstPlot.setDebtWarningCount(firstPlot.getDebtWarningCount() + 1);
                            debtNotificationService.refresh(payerUuid, DebtNotificationService.DebtUpdateReason.ECONOMY_EVENT);
                        }

                        plugin.getLogger().warning(String.format(
                            "[TownEconomyManager] Groupe PRO %s - Fonds insuffisants pour taxe de %.2fÔé¼ (Entreprise: %s, Dette: %.2fÔé¼)",
                            group.getGroupName(), groupHourlyTax, company.getNom(), newDebt
                        ));

                        // V├®rifier saisie automatique
                        CompanyPlotManager companyManager = plugin.getCompanyPlotManager();
                        if (companyManager.checkCompanyDebtStatus(firstPlot)) {
                            companyManager.seizePlotForDebt(firstPlot, townName);
                            // Supprimer le groupe apr├¿s saisie
                            town.removePlotGroup(group.getGroupId());
                        }
                    } else {
                        // GROUPE PARTICULIER : Dette personnelle
                        newDebt = firstPlot.getParticularDebtAmount() + groupHourlyTax;
                        firstPlot.setParticularDebtAmount(newDebt);

                        // Si c'est le premier avertissement
                        if (firstPlot.getParticularDebtWarningCount() == 0) {
                            firstPlot.setParticularLastDebtWarningDate(LocalDateTime.now());
                            firstPlot.setParticularDebtWarningCount(1);

                            // Avertissement au joueur - FORMAT UNIFI├ë
                            if (false && payer.isOnline() && payer.getPlayer() != null) {
                                payer.getPlayer().sendMessage("");
                                payer.getPlayer().sendMessage(ChatColor.RED + "ÔÜáÔÜáÔÜá AVERTISSEMENT - DETTE DE TERRAIN ÔÜáÔÜáÔÜá");
                                payer.getPlayer().sendMessage(ChatColor.YELLOW + "Type: " + ChatColor.WHITE + "Groupe Particulier");
                                payer.getPlayer().sendMessage(ChatColor.YELLOW + "Groupe: " + ChatColor.WHITE +
                                    group.getGroupName() + ChatColor.GRAY + " (" + groupPlots.size() + " parcelles)");
                                payer.getPlayer().sendMessage(ChatColor.YELLOW + "Ville: " + ChatColor.WHITE + townName);
                                payer.getPlayer().sendMessage(ChatColor.YELLOW + "Dette accumul├®e: " + ChatColor.RED +
                                    String.format("%.2fÔé¼", newDebt));
                                payer.getPlayer().sendMessage("");
                                payer.getPlayer().sendMessage(ChatColor.GOLD + "Vous n'avez pas pu payer les taxes du groupe !");
                                payer.getPlayer().sendMessage(ChatColor.GOLD + "D├®lai: " + ChatColor.WHITE + "7 jours" +
                                    ChatColor.GOLD + " pour renflouer le compte.");
                                payer.getPlayer().sendMessage(ChatColor.RED + "Si la dette n'est pas pay├®e, les terrains seront SAISIS automatiquement.");
                                payer.getPlayer().sendMessage("");
                            }

                            debtNotificationService.refresh(payerUuid, DebtNotificationService.DebtUpdateReason.ECONOMY_EVENT);
                        } else {
                            // Dette d├®j├á existante
                            debtNotificationService.refresh(payerUuid, DebtNotificationService.DebtUpdateReason.ECONOMY_EVENT);

                            if (false && payer.isOnline() && payer.getPlayer() != null) {
                                payer.getPlayer().sendMessage(ChatColor.RED + "ÔÜá Taxe impay├®e groupe ajout├®e: " +
                                    ChatColor.GOLD + String.format("+%.2fÔé¼", groupHourlyTax) + ChatColor.RED +
                                    " (Total: " + String.format("%.2fÔé¼", newDebt) + ")");
                                payer.getPlayer().sendMessage(ChatColor.YELLOW + "   Groupe: " + ChatColor.WHITE + group.getGroupName());
                                payer.getPlayer().sendMessage(ChatColor.YELLOW + "   R├®glez via: " +
                                    ChatColor.WHITE + "/ville ÔåÆ R├®gler vos Dettes");
                            }
                        }

                        plugin.getLogger().warning(String.format(
                            "[TownEconomyManager] Groupe %s - Fonds insuffisants pour taxe de %.2fÔé¼ (Propri├®taire: %s, Dette: %.2fÔé¼)",
                            group.getGroupName(), groupHourlyTax, payerName, newDebt
                        ));

                        // NOUVEAU : V├®rifier si le d├®lai de gr├óce est d├®pass├® (7 jours)
                        if (firstPlot.getParticularLastDebtWarningDate() != null) {
                            LocalDateTime warningDate = firstPlot.getParticularLastDebtWarningDate();
                            long daysSinceWarning = java.time.Duration.between(warningDate, LocalDateTime.now()).toDays();

                        if (daysSinceWarning >= 7) {
                            // SAISIE AUTOMATIQUE de tous les terrains du groupe
                            plugin.getLogger().warning(String.format(
                                "[TownEconomyManager] SAISIE AUTO - Groupe %s saisi pour dette (Propri├®taire: %s, Dette: %.2fÔé¼)",
                                group.getGroupName(), payerName, firstPlot.getParticularDebtAmount()
                            ));

                            // Notifier le joueur
                            if (false && payer.isOnline() && payer.getPlayer() != null) {
                                payer.getPlayer().sendMessage("");
                                payer.getPlayer().sendMessage(ChatColor.DARK_RED + "ÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉ");
                                payer.getPlayer().sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "ÔÜá SAISIE DE GROUPE");
                                payer.getPlayer().sendMessage(ChatColor.DARK_RED + "ÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉ");
                                payer.getPlayer().sendMessage(ChatColor.RED + "Votre groupe " + group.getGroupName());
                                payer.getPlayer().sendMessage(ChatColor.RED + "a ├®t├® saisi pour dette impay├®e!");
                                payer.getPlayer().sendMessage("");
                                payer.getPlayer().sendMessage(ChatColor.YELLOW + "Dette: " + ChatColor.GOLD +
                                    String.format("%.2fÔé¼", firstPlot.getParticularDebtAmount()));
                                payer.getPlayer().sendMessage(ChatColor.YELLOW + "Parcelles: " + groupPlots.size());
                                payer.getPlayer().sendMessage(ChatColor.GRAY + "Les terrains retournent ├á la ville.");
                                payer.getPlayer().sendMessage(ChatColor.DARK_RED + "ÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉ");
                                payer.getPlayer().sendMessage("");
                            }

                            // FIX CRITIQUE: Retour de tous les terrains ├á la ville avec nettoyage complet
                            for (Plot plot : groupPlots) {
                                // Utiliser transferPlotToTown pour un nettoyage complet
                                townManager.transferPlotToTown(plot, "Dette impay├®e groupe: " +
                                    String.format("%.2fÔé¼", plot.getParticularDebtAmount()));

                                // Remettre en vente
                                plot.setForSale(true);
                                plot.setSalePrice(1000.0); // Prix par d├®faut
                            }

                            // Supprimer le groupe
                            town.removePlotGroup(group.getGroupId());
                        }
                        }  // <- Fermeture du if (firstPlot.getParticularLastDebtWarningDate() != null)
                    }
                } else {
                    // Pas de parcelle dans le groupe - juste notifier
                    debtNotificationService.refresh(payerUuid, DebtNotificationService.DebtUpdateReason.ECONOMY_EVENT);

                    if (false && payer.isOnline() && payer.getPlayer() != null) {
                        payer.getPlayer().sendMessage(ChatColor.RED + "ÔÜá Vous n'avez pas pu payer la taxe horaire de " +
                            String.format("%.2fÔé¼", groupHourlyTax) + " pour le groupe " + group.getGroupName());
                    }
                }
            }
        }

        // Puis collecter les taxes des parcelles individuelles (non group├®es)
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
                                gerant.sendMessage(ChatColor.YELLOW + "­ƒÆ╝ Taxe horaire entreprise: " +
                                    ChatColor.GOLD + String.format("%.2fÔé¼", hourlyTax) + ChatColor.GRAY +
                                    " pr├®lev├®e pour " + plot.getCoordinates());
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

            // NOUVEAU : Pr├®lever m├¬me OFFLINE
            OfflinePlayer payer = Bukkit.getOfflinePlayer(payerUuid);
            if (payer.getName() != null) {
                payerName = payer.getName();
            }

            if (RoleplayCity.getEconomy().has(payer, hourlyTax)) {
                RoleplayCity.getEconomy().withdrawPlayer(payer, hourlyTax);
                town.deposit(hourlyTax);
                totalCollected += hourlyTax;
                parcelsWithTax++;

                // R├®initialiser la dette si le terrain ├®tait endett├®
                if (plot.getParticularDebtAmount() > 0) {
                    plot.resetParticularDebt();
                }

                // Enregistrer pour le rapport individuel
                playerTaxes.put(payerUuid, playerTaxes.getOrDefault(payerUuid, 0.0) + hourlyTax);

                // Message si en ligne
                if (false && payer.isOnline() && payer.getPlayer() != null) {
                    payer.getPlayer().sendMessage(ChatColor.YELLOW + "­ƒÆ░ Taxe horaire parcelle: " +
                        ChatColor.GOLD + String.format("%.2fÔé¼", hourlyTax) + ChatColor.GRAY +
                        " pr├®lev├®e pour " + plot.getCoordinates());
                }

                addTransaction(townName, new PlotTransaction(
                    PlotTransaction.TransactionType.TAX,
                    payerUuid,
                    payerName,
                    hourlyTax,
                    "Taxe horaire parcelle " + plot.getCoordinates()
                ));
            } else {
                // NOUVEAU : Fonds insuffisants - cr├®er/augmenter la dette
                unpaidPlayers.add(payerName);

                double newDebt = plot.getParticularDebtAmount() + hourlyTax;
                plot.setParticularDebtAmount(newDebt);

                // Si c'est le premier avertissement
                if (plot.getParticularDebtWarningCount() == 0) {
                    plot.setParticularLastDebtWarningDate(LocalDateTime.now());
                    plot.setParticularDebtWarningCount(1);

                    // Avertissement au joueur
                    if (false && payer.isOnline() && payer.getPlayer() != null) {
                        payer.getPlayer().sendMessage("");
                        payer.getPlayer().sendMessage(ChatColor.RED + "ÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉ");
                        payer.getPlayer().sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "ÔÜá ALERTE DETTE - PARTICULIER");
                        payer.getPlayer().sendMessage(ChatColor.RED + "ÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉ");
                        payer.getPlayer().sendMessage(ChatColor.YELLOW + "Dette actuelle: " + ChatColor.GOLD +
                            String.format("%.2fÔé¼", newDebt));
                        payer.getPlayer().sendMessage(ChatColor.YELLOW + "Terrain: " + ChatColor.WHITE +
                            plot.getCoordinates() + ChatColor.GRAY + " (ville: " + townName + ")");
                        payer.getPlayer().sendMessage("");
                        payer.getPlayer().sendMessage(ChatColor.RED + "ÔÜá Vous avez 7 jours pour rembourser");
                        payer.getPlayer().sendMessage(ChatColor.RED + "   avant saisie automatique du terrain!");
                        payer.getPlayer().sendMessage("");
                        payer.getPlayer().sendMessage(ChatColor.YELLOW + "­ƒÆí R├®glez vos dettes via:");
                        payer.getPlayer().sendMessage(ChatColor.GRAY + "   /ville ÔåÆ R├®gler vos Dettes");
                        payer.getPlayer().sendMessage(ChatColor.RED + "ÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉ");
                        payer.getPlayer().sendMessage("");
                    }

                    debtNotificationService.refresh(payerUuid, DebtNotificationService.DebtUpdateReason.ECONOMY_EVENT);
                } else {
                    // Dette d├®j├á existante - simple notification
                    debtNotificationService.refresh(payerUuid, DebtNotificationService.DebtUpdateReason.ECONOMY_EVENT);

                    if (false && payer.isOnline() && payer.getPlayer() != null) {
                        payer.getPlayer().sendMessage(ChatColor.RED + "ÔÜá Taxe impay├®e ajout├®e ├á votre dette: " +
                            ChatColor.GOLD + String.format("+%.2fÔé¼", hourlyTax) + ChatColor.RED +
                            " (Total: " + String.format("%.2fÔé¼", newDebt) + ")");
                        payer.getPlayer().sendMessage(ChatColor.YELLOW + "   R├®glez via: " +
                            ChatColor.WHITE + "/ville ÔåÆ R├®gler vos Dettes");
                    }
                }

                plugin.getLogger().warning(String.format(
                    "[TownEconomyManager] Particulier %s - Fonds insuffisants pour taxe de %.2fÔé¼ sur terrain %s:%d,%d (Dette: %.2fÔé¼)",
                    payerName, hourlyTax, plot.getWorldName(), plot.getChunkX(), plot.getChunkZ(), newDebt
                ));
            }

            // NOUVEAU : V├®rifier si le d├®lai de gr├óce est d├®pass├® (7 jours) pour les particuliers
            if (plot.getParticularDebtAmount() > 0 && plot.getParticularLastDebtWarningDate() != null) {
                LocalDateTime warningDate = plot.getParticularLastDebtWarningDate();
                long daysSinceWarning = java.time.Duration.between(warningDate, LocalDateTime.now()).toDays();

                if (daysSinceWarning >= 7) {
                    // SAISIE AUTOMATIQUE du terrain
                    plugin.getLogger().warning(String.format(
                        "[TownEconomyManager] SAISIE AUTO - Terrain %s:%d,%d saisi pour dette (Particulier %s, Dette: %.2fÔé¼)",
                        plot.getWorldName(), plot.getChunkX(), plot.getChunkZ(), payerName, plot.getParticularDebtAmount()
                    ));

                    // Notifier le joueur
                    if (false && payer.isOnline() && payer.getPlayer() != null) {
                        payer.getPlayer().sendMessage("");
                        payer.getPlayer().sendMessage(ChatColor.DARK_RED + "ÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉ");
                        payer.getPlayer().sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "ÔÜá SAISIE DE TERRAIN");
                        payer.getPlayer().sendMessage(ChatColor.DARK_RED + "ÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉ");
                        payer.getPlayer().sendMessage(ChatColor.RED + "Votre terrain " + plot.getCoordinates());
                        payer.getPlayer().sendMessage(ChatColor.RED + "a ├®t├® saisi pour dette impay├®e!");
                        payer.getPlayer().sendMessage("");
                        payer.getPlayer().sendMessage(ChatColor.YELLOW + "Dette: " + ChatColor.GOLD +
                            String.format("%.2fÔé¼", plot.getParticularDebtAmount()));
                        payer.getPlayer().sendMessage(ChatColor.GRAY + "Le terrain retourne ├á la ville.");
                        payer.getPlayer().sendMessage(ChatColor.DARK_RED + "ÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉ");
                        payer.getPlayer().sendMessage("");
                    }

                    // FIX CRITIQUE: Retour du terrain ├á la ville avec nettoyage complet
                    townManager.transferPlotToTown(plot, "Dette impay├®e particulier: " +
                        String.format("%.2fÔé¼", plot.getParticularDebtAmount()));

                    // Remettre en vente
                    plot.setForSale(true);
                    plot.setSalePrice(1000.0); // Prix par d├®faut
                }
            }
        }

        // Sauvegarder
        townManager.saveTownsNow();

        // NOUVEAU : G├®n├®rer les rapports
        generateTaxReports(town, totalCollected, parcelsWithTax, unpaidPlayers.size(), playerTaxes);

        return new TaxCollectionResult(totalCollected, parcelsWithTax, unpaidPlayers.size(), unpaidPlayers);
    }

    /**
     * G├®n├¿re les rapports de taxes individuels et le rapport maire
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
                player.sendMessage(ChatColor.GOLD + "ÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉ");
                player.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "    RAPPORT TAXES HORAIRES");
                player.sendMessage(ChatColor.GOLD + "ÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉ");
                player.sendMessage(ChatColor.AQUA + "Ville: " + ChatColor.WHITE + town.getName());
                player.sendMessage(ChatColor.AQUA + "Montant pr├®lev├®: " + ChatColor.GOLD + String.format("%.2fÔé¼", taxAmount));
                player.sendMessage(ChatColor.GRAY + "Heure: " + ChatColor.WHITE +
                    java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
                player.sendMessage(ChatColor.GOLD + "ÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉ");
                player.sendMessage("");
            }
        }

        // === RAPPORT MAIRE ===
        UUID mayorUuid = town.getMayorUuid();
        if (mayorUuid != null) {
            Player mayor = Bukkit.getPlayer(mayorUuid);
            if (mayor != null && mayor.isOnline()) {
                mayor.sendMessage("");
                mayor.sendMessage(ChatColor.GOLD + "ÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉ");
                mayor.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "  RAPPORT MAIRE - TAXES HORAIRES");
                mayor.sendMessage(ChatColor.GOLD + "ÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉ");
                mayor.sendMessage(ChatColor.AQUA + "Ville: " + ChatColor.WHITE + town.getName());
                mayor.sendMessage(ChatColor.AQUA + "Total collect├®: " + ChatColor.GOLD + String.format("%.2fÔé¼", totalCollected));
                mayor.sendMessage(ChatColor.AQUA + "Parcelles tax├®es: " + ChatColor.WHITE + parcelsWithTax);
                mayor.sendMessage(ChatColor.AQUA + "Solde ville: " + ChatColor.GOLD + String.format("%.2fÔé¼", town.getBankBalance()));

                if (unpaidCount > 0) {
                    mayor.sendMessage("");
                    mayor.sendMessage(ChatColor.RED + "ÔÜá " + unpaidCount + " paiement(s) impay├®(s)");
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
                    mayor.sendMessage(ChatColor.GRAY + "  ÔÇó " + ChatColor.WHITE + name + ": " +
                        ChatColor.GOLD + String.format("%.2fÔé¼", entry.getValue()));
                    count++;
                }

                mayor.sendMessage(ChatColor.GRAY + "Heure: " + ChatColor.WHITE +
                    java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
                mayor.sendMessage(ChatColor.GOLD + "ÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉÔòÉ");
                mayor.sendMessage("");
            }
        }
    }

    /**
     * Collecte automatique des taxes pour toutes les villes
     */
    public void collectAllTaxes() {
        plugin.getLogger().info("D├®but de la collecte automatique des taxes...");
        int totalTowns = 0;
        double totalAmount = 0;

        for (String townName : townManager.getTownNames()) {
            Town town = townManager.getTown(townName);
            if (town == null) continue;

            // V├®rifier si c'est le moment de collecter
            LocalDateTime lastCollection = town.getLastTaxCollection();
            Duration duration = Duration.between(lastCollection, LocalDateTime.now());

            if (duration.toHours() >= 24) { // Collecte toutes les 24h
                TaxCollectionResult result = collectTaxes(townName);
                totalTowns++;
                totalAmount += result.totalCollected;

                plugin.getLogger().info("Ville " + townName + ": " +
                    result.totalCollected + "Ôé¼ collect├®s sur " + result.parcelsCollected + " parcelles");
            }
        }

        plugin.getLogger().info("Collecte termin├®e: " + totalTowns + " villes, " + totalAmount + "Ôé¼ total");
    }

    /**
     * NOUVEAU : Collecte horaire des taxes pour toutes les villes
     * - Collecte toutes les heures (au lieu de 24h)
     * - Pr├®l├¿ve les joueurs OFFLINE
     * - G├®n├¿re des rapports individuels et rapport maire
     * - Synchronis├® avec les paiements entreprises
     */
    public void collectAllTaxesHourly() {
        plugin.getLogger().info("[TAXES HORAIRES] D├®but de la collecte horaire des taxes...");
        int totalTowns = 0;
        double totalAmount = 0;

        for (String townName : townManager.getTownNames()) {
            Town town = townManager.getTown(townName);
            if (town == null) continue;

            // Collecte horaire (pas de v├®rification de 24h)
            TaxCollectionResult result = collectTaxesHourly(townName);
            totalTowns++;
            totalAmount += result.totalCollected;

            plugin.getLogger().info("[TAXES HORAIRES] Ville " + townName + ": " +
                String.format("%.2fÔé¼", result.totalCollected) + " collect├®s sur " +
                result.parcelsCollected + " parcelles");
        }

        plugin.getLogger().info("[TAXES HORAIRES] Collecte termin├®e: " + totalTowns + " villes, " +
            String.format("%.2fÔé¼", totalAmount) + " total");
    }

    // === TRANSACTIONS ===

    private void addTransaction(String townName, PlotTransaction transaction) {
        transactionHistory.computeIfAbsent(townName, k -> new ArrayList<>()).add(transaction);

        // Limiter l'historique ├á 100 transactions par ville
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

        // V├®rifier les permissions : propri├®taire OU maire/adjoint de la ville
        boolean isOwner = group.isOwnedBy(seller.getUniqueId());
        boolean isMayor = town.isMayor(seller.getUniqueId());
        TownRole role = town.getMemberRole(seller.getUniqueId());
        boolean isAdjoint = (role == TownRole.ADJOINT);

        if (!isOwner && !isMayor && !isAdjoint) {
            return false;
        }

        group.setSalePrice(price);
        group.setForSale(true);

        // Mettre aussi toutes les parcelles individuelles en vente
        for (String plotKey : group.getPlotKeys()) {
            String[] parts = plotKey.split(":");
            if (parts.length == 3) {
                String worldName = parts[0];
                int chunkX = Integer.parseInt(parts[1]);
                int chunkZ = Integer.parseInt(parts[2]);

                Plot plot = town.getPlot(worldName, chunkX, chunkZ);
                if (plot != null) {
                    plot.setForSale(false); // Emp├¬cher vente individuelle
                }
            }
        }

        // Sauvegarder imm├®diatement
        townManager.saveTownsNow();
        return true;
    }

    /**
     * Ach├¿te un groupe de parcelles
     */
    public boolean buyPlotGroup(String townName, PlotGroup group, Player buyer) {
        Town town = townManager.getTown(townName);
        if (town == null || !group.isForSale()) {
            return false;
        }

        // V├®rifier que l'acheteur est membre de la ville
        if (!town.isMember(buyer.getUniqueId())) {
            buyer.sendMessage(ChatColor.RED + "Vous devez ├¬tre membre de la ville pour acheter un groupe de parcelles.");
            return false;
        }

        // V├®rifier que le groupe n'est pas lou├®
        if (group.getRenterUuid() != null) {
            buyer.sendMessage(ChatColor.RED + "Ce groupe de parcelles est actuellement lou├®.");
            return false;
        }

        // === NOUVEAU : D├®tecter si le groupe contient des terrains PROFESSIONNEL ===
        boolean hasProfessionalPlot = false;
        for (String plotKey : group.getPlotKeys()) {
            String[] parts = plotKey.split(":");
            if (parts.length == 3) {
                String worldName = parts[0];
                int chunkX = Integer.parseInt(parts[1]);
                int chunkZ = Integer.parseInt(parts[2]);
                Plot plot = town.getPlot(worldName, chunkX, chunkZ);
                if (plot != null && plot.getType() == PlotType.PROFESSIONNEL) {
                    hasProfessionalPlot = true;
                    break;
                }
            }
        }

        double price = group.getSalePrice();
        CompanyPlotManager companyManager = plugin.getCompanyPlotManager();
        EntrepriseManagerLogic.Entreprise buyerCompany = null;
        String oldCompanySiret = null; // D├®clar├® ici pour ├¬tre accessible dans le bloc d'annulation

        // Si le groupe contient des terrains PRO, valider l'entreprise et utiliser les fonds d'entreprise
        if (hasProfessionalPlot) {
            buyerCompany = companyManager.getPlayerCompany(buyer);
            if (buyerCompany == null) {
                buyer.sendMessage(ChatColor.RED + "Ô£ù Vous devez poss├®der une entreprise pour acheter un groupe contenant des terrains PROFESSIONNELS !");
                buyer.sendMessage(ChatColor.YELLOW + "ÔåÆ Discutez de votre projet avec le Maire pour obtenir un contrat d'entreprise");
                return false;
            }

            // V├®rifier les fonds de l'entreprise
            if (buyerCompany.getSolde() < price) {
                buyer.sendMessage(ChatColor.RED + "Votre entreprise n'a pas assez d'argent !");
                buyer.sendMessage(ChatColor.YELLOW + "Prix: " + ChatColor.GOLD + String.format("%.2fÔé¼", price));
                buyer.sendMessage(ChatColor.YELLOW + "Solde entreprise: " + ChatColor.GOLD + String.format("%.2fÔé¼", buyerCompany.getSolde()));
                return false;
            }

            // Pr├®lever de l'entreprise
            buyerCompany.setSolde(buyerCompany.getSolde() - price);
        } else {
            // Groupe PARTICULIER uniquement - utiliser argent personnel
            if (!RoleplayCity.getEconomy().has(buyer, price)) {
                buyer.sendMessage(ChatColor.RED + "Vous n'avez pas assez d'argent. Prix: " + price + "Ôé¼");
                return false;
            }
            RoleplayCity.getEconomy().withdrawPlayer(buyer, price);
        }

        // Donner l'argent au propri├®taire ou ├á la banque
        if (group.getOwnerUuid() != null) {
            // V├®rifier si l'ancien propri├®taire avait une entreprise
            for (String plotKey : group.getPlotKeys()) {
                String[] parts = plotKey.split(":");
                if (parts.length == 3) {
                    Plot plot = town.getPlot(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                    if (plot != null && plot.getCompanySiret() != null) {
                        oldCompanySiret = plot.getCompanySiret();
                        break;
                    }
                }
            }

            if (oldCompanySiret != null) {
                // Ancien terrain PRO - argent va ├á l'ancienne entreprise
                EntrepriseManagerLogic.Entreprise previousCompany = companyManager.getCompanyBySiret(oldCompanySiret);
                if (previousCompany != null) {
                    previousCompany.setSolde(previousCompany.getSolde() + price);
                    OfflinePlayer previousOwner = Bukkit.getOfflinePlayer(group.getOwnerUuid());
                    if (previousOwner.isOnline() && previousOwner.getPlayer() != null) {
                        previousOwner.getPlayer().sendMessage(ChatColor.GREEN + "Votre groupe de parcelles a ├®t├® vendu pour " + price + "Ôé¼");
                        previousOwner.getPlayer().sendMessage(ChatColor.YELLOW + "L'argent a ├®t├® vers├® ├á l'entreprise " + previousCompany.getNom());
                    } else {
                        // FIX P2.4: Notification offline pour vente groupe PRO
                        notificationManager.sendNotification(
                            group.getOwnerUuid(),
                            NotificationManager.NotificationType.ECONOMY,
                            "Groupe PRO vendu",
                            String.format("Votre groupe vendu pour %.2fÔé¼. Argent vers├® ├á %s",
                                price, previousCompany.getNom())
                        );
                    }
                } else {
                    town.deposit(price); // Entreprise n'existe plus
                }
            } else {
                // Ancien terrain PARTICULIER - argent va au propri├®taire
                OfflinePlayer previousOwner = Bukkit.getOfflinePlayer(group.getOwnerUuid());
                RoleplayCity.getEconomy().depositPlayer(previousOwner, price);
                if (previousOwner.isOnline() && previousOwner.getPlayer() != null) {
                    previousOwner.getPlayer().sendMessage(ChatColor.GREEN + "Votre groupe de parcelles a ├®t├® vendu pour " + price + "Ôé¼");
                } else {
                    // FIX P2.4: Notification offline pour vente groupe PARTICULIER
                    notificationManager.sendNotification(
                        group.getOwnerUuid(),
                        NotificationManager.NotificationType.ECONOMY,
                        "Groupe vendu",
                        String.format("Votre groupe vendu pour %.2fÔé¼", price)
                    );
                }
            }
        } else {
            town.deposit(price);
        }

        // FIX CRITIQUE: V├®rifier d'abord que TOUTES les parcelles existent
        List<Plot> existingPlots = new ArrayList<>();
        for (String plotKey : group.getPlotKeys()) {
            String[] parts = plotKey.split(":");
            if (parts.length == 3) {
                String worldName = parts[0];
                int chunkX = Integer.parseInt(parts[1]);
                int chunkZ = Integer.parseInt(parts[2]);

                Plot plot = town.getPlot(worldName, chunkX, chunkZ);
                if (plot == null) {
                    buyer.sendMessage(ChatColor.RED + "ÔØî ERREUR: Le groupe contient des parcelles manquantes !");
                    buyer.sendMessage(ChatColor.YELLOW + "Parcelle introuvable: " + worldName + ":" + chunkX + "," + chunkZ);
                    buyer.sendMessage(ChatColor.GRAY + "Contactez un administrateur pour nettoyer ce groupe.");

                    // Rembourser l'acheteur
                    if (hasProfessionalPlot && buyerCompany != null) {
                        buyerCompany.setSolde(buyerCompany.getSolde() + price);
                    } else {
                        RoleplayCity.getEconomy().depositPlayer(buyer, price);
                    }

                    // Annuler la transaction avec le vendeur
                    if (group.getOwnerUuid() != null) {
                        OfflinePlayer previousOwner = Bukkit.getOfflinePlayer(group.getOwnerUuid());
                        if (oldCompanySiret != null) {
                            EntrepriseManagerLogic.Entreprise previousCompany = companyManager.getCompanyBySiret(oldCompanySiret);
                            if (previousCompany != null) {
                                previousCompany.setSolde(previousCompany.getSolde() - price);
                            }
                        } else {
                            RoleplayCity.getEconomy().withdrawPlayer(previousOwner, price);
                        }
                    } else {
                        town.withdraw(price);
                    }

                    return false;
                }
                existingPlots.add(plot);
            }
        }

        // Transf├®rer la propri├®t├® du groupe
        group.setOwner(buyer.getUniqueId(), buyer.getName());
        group.setForSale(false);

        // Transf├®rer toutes les parcelles individuelles + companySiret si n├®cessaire
        for (Plot plot : existingPlots) {
            plot.setOwner(buyer.getUniqueId(), buyer.getName());
            plot.setForSale(false);

            // FIX CRITIQUE: R├®initialiser TOUTES les dettes (entreprise ET particulier)
            plot.resetDebt();
            plot.resetParticularDebt();

            // Si terrain PROFESSIONNEL, enregistrer l'entreprise
            if (plot.getType() == PlotType.PROFESSIONNEL && buyerCompany != null) {
                plot.setCompany(buyerCompany.getNom());
                plot.setCompanySiret(buyerCompany.getSiret());
            } else {
                plot.setCompany(null);
                plot.setCompanySiret(null);
            }
        }

        // Messages personnalis├®s selon le type
        if (hasProfessionalPlot && buyerCompany != null) {
            buyer.sendMessage(ChatColor.GREEN + "Ô£ô Groupe de parcelles PROFESSIONNEL achet├® avec succ├¿s !");
            buyer.sendMessage(ChatColor.YELLOW + "Entreprise: " + ChatColor.WHITE + buyerCompany.getNom());
            buyer.sendMessage(ChatColor.YELLOW + "Parcelles: " + ChatColor.GOLD + group.getPlotCount());
            buyer.sendMessage(ChatColor.YELLOW + "Prix pay├®: " + ChatColor.GOLD + String.format("%.2fÔé¼", price));
            buyer.sendMessage(ChatColor.YELLOW + "Solde entreprise restant: " + ChatColor.GOLD + String.format("%.2fÔé¼", buyerCompany.getSolde()));
            buyer.sendMessage(ChatColor.GRAY + "Les taxes seront pr├®lev├®es du solde de l'entreprise.");
        } else {
            buyer.sendMessage(ChatColor.GREEN + "Groupe de parcelles achet├® avec succ├¿s !");
            buyer.sendMessage(ChatColor.YELLOW + "Parcelles: " + ChatColor.GOLD + group.getPlotCount());
            buyer.sendMessage(ChatColor.YELLOW + "Prix pay├®: " + ChatColor.GOLD + price + "Ôé¼");
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
            "Groupe '" + group.getGroupName() + "' (" + group.getPlotCount() + " terrains)",
            price
        );

        // Sauvegarder imm├®diatement
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

        // V├®rifier les permissions : propri├®taire OU maire/adjoint de la ville
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

        // Scanner et prot├®ger tous les blocs de toutes les parcelles du groupe
        for (String plotKey : group.getPlotKeys()) {
            String[] parts = plotKey.split(":");
            if (parts.length == 3) {
                String worldName = parts[0];
                int chunkX = Integer.parseInt(parts[1]);
                int chunkZ = Integer.parseInt(parts[2]);

                Plot plot = town.getPlot(worldName, chunkX, chunkZ);
                if (plot != null) {
                    // FIX CRITIQUE: Utiliser le monde de la parcelle, pas celui du propri├®taire
                    org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
                    if (world != null) {
                        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                        plot.scanAndProtectExistingBlocks(chunk);
                    }
                }
            }
        }

        // Sauvegarder imm├®diatement
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

        // V├®rifier que le loueur est membre de la ville
        if (!town.isMember(renter.getUniqueId())) {
            renter.sendMessage(ChatColor.RED + "Vous devez ├¬tre membre de la ville pour louer un groupe de parcelles.");
            return false;
        }

        // NOUVEAU : Emp├¬cher le propri├®taire de louer son propre groupe
        if (group.getOwnerUuid() != null && group.getOwnerUuid().equals(renter.getUniqueId())) {
            renter.sendMessage(ChatColor.RED + "Vous ne pouvez pas louer votre propre groupe de parcelles !");
            return false;
        }

        int actualDays = Math.min(days, 30);
        double totalCost = group.getRentPricePerDay() * actualDays;

        // NOUVEAU : D├®tecter si c'est un groupe PROFESSIONNEL (entreprise)
        boolean isProfessionalGroup = false;
        String renterCompanySiret = null;
        String ownerCompanySiret = null;
        CompanyPlotManager companyManager = plugin.getCompanyPlotManager();
        EntrepriseManagerLogic.Entreprise renterCompany = null;

        // V├®rifier si au moins une parcelle du groupe est PROFESSIONNEL
        for (String plotKey : group.getPlotKeys()) {
            String[] parts = plotKey.split(":");
            if (parts.length == 3) {
                String worldName = parts[0];
                int chunkX = Integer.parseInt(parts[1]);
                int chunkZ = Integer.parseInt(parts[2]);
                Plot plot = town.getPlot(worldName, chunkX, chunkZ);
                if (plot != null && plot.getType() == PlotType.PROFESSIONNEL) {
                    isProfessionalGroup = true;
                    if (plot.getCompanySiret() != null) {
                        ownerCompanySiret = plot.getCompanySiret();
                    }
                    break;
                }
            }
        }

        // Si groupe PRO, valider l'entreprise du locataire
        if (isProfessionalGroup) {
            renterCompany = companyManager.getPlayerCompany(renter);
            if (renterCompany == null) {
                renter.sendMessage(ChatColor.RED + "Ô£ù Vous devez poss├®der une entreprise pour louer un groupe contenant des terrains PROFESSIONNELS !");
                renter.sendMessage(ChatColor.YELLOW + "ÔåÆ Discutez de votre projet avec le Maire pour obtenir un contrat d'entreprise");
                return false;
            }
            renterCompanySiret = renterCompany.getSiret();

            // V├®rifier les fonds de l'entreprise
            if (renterCompany.getSolde() < totalCost) {
                renter.sendMessage(ChatColor.RED + "Votre entreprise n'a pas assez d'argent !");
                renter.sendMessage(ChatColor.YELLOW + "Prix: " + ChatColor.GOLD + String.format("%.2fÔé¼", totalCost));
                renter.sendMessage(ChatColor.YELLOW + "Solde entreprise: " + ChatColor.GOLD + String.format("%.2fÔé¼", renterCompany.getSolde()));
                return false;
            }

            // Pr├®lever de l'entreprise du locataire
            renterCompany.setSolde(renterCompany.getSolde() - totalCost);
        } else {
            // Groupe PARTICULIER : V├®rifier l'argent personnel
            if (!RoleplayCity.getEconomy().has(renter, totalCost)) {
                renter.sendMessage(ChatColor.RED + "Vous n'avez pas assez d'argent. Prix: " + totalCost + "Ôé¼");
                return false;
            }

            // Pr├®lever l'argent personnel
            RoleplayCity.getEconomy().withdrawPlayer(renter, totalCost);
        }

        // Donner l'argent au propri├®taire ou ├á la banque
        if (group.getOwnerUuid() != null) {
            if (isProfessionalGroup && ownerCompanySiret != null) {
                // Groupe PRO - argent va ├á l'entreprise du propri├®taire
                EntrepriseManagerLogic.Entreprise ownerCompany = companyManager.getCompanyBySiret(ownerCompanySiret);
                if (ownerCompany != null) {
                    ownerCompany.setSolde(ownerCompany.getSolde() + totalCost);

                    // Notifier le propri├®taire si en ligne
                    OfflinePlayer owner = Bukkit.getOfflinePlayer(group.getOwnerUuid());
                    if (owner.isOnline() && owner.getPlayer() != null) {
                        owner.getPlayer().sendMessage(ChatColor.GREEN + "Votre groupe professionnel a ├®t├® lou├® pour " + actualDays + " jours!");
                        owner.getPlayer().sendMessage(ChatColor.YELLOW + "L'argent a ├®t├® vers├® ├á " + ownerCompany.getNom() + ": +" + String.format("%.2fÔé¼", totalCost));
                    }
                } else {
                    // Entreprise n'existe plus - argent va ├á la ville
                    town.deposit(totalCost);
                }
            } else {
                // Groupe PARTICULIER - argent va au propri├®taire
                OfflinePlayer owner = Bukkit.getOfflinePlayer(group.getOwnerUuid());
                RoleplayCity.getEconomy().depositPlayer(owner, totalCost);

                // Notifier si le propri├®taire est en ligne
                if (owner.isOnline() && owner.getPlayer() != null) {
                    owner.getPlayer().sendMessage(ChatColor.GREEN + "Votre groupe de parcelles a ├®t├® lou├® pour " + actualDays + " jours: +" + totalCost + "Ôé¼");
                }
            }
        } else {
            town.deposit(totalCost);
        }

        // Configurer la location sur le groupe
        group.setRenter(renter.getUniqueId(), actualDays);
        group.setForRent(false);

        // Appliquer la location sur toutes les parcelles individuelles aussi
        for (String plotKey : group.getPlotKeys()) {
            String[] parts = plotKey.split(":");
            if (parts.length == 3) {
                String worldName = parts[0];
                int chunkX = Integer.parseInt(parts[1]);
                int chunkZ = Integer.parseInt(parts[2]);

                Plot plot = town.getPlot(worldName, chunkX, chunkZ);
                if (plot != null) {
                    plot.setRenter(renter.getUniqueId(), actualDays);
                    // Si groupe PRO, stocker le SIRET de l'entreprise du locataire
                    if (isProfessionalGroup && renterCompanySiret != null) {
                        plot.setRenterCompanySiret(renterCompanySiret);
                    }
                }
            }
        }

        renter.sendMessage(ChatColor.GREEN + "Groupe lou├® avec succ├¿s !");
        renter.sendMessage(ChatColor.YELLOW + "Dur├®e: " + ChatColor.GOLD + actualDays + " jours");
        renter.sendMessage(ChatColor.YELLOW + "Prix: " + ChatColor.GOLD + totalCost + "Ôé¼");

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
            "Groupe '" + group.getGroupName() + "' (" + group.getPlotCount() + " terrains)",
            actualDays,
            totalCost
        );

        // Sauvegarder imm├®diatement
        townManager.saveTownsNow();
        return true;
    }

    /**
     * Recharge le solde d'un groupe lou├®
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
            renter.sendMessage(ChatColor.YELLOW + "Votre solde est d├®j├á au maximum (30 jours).");
            return false;
        }

        int actualDaysToAdd = Math.min(daysToAdd, maxCanAdd);
        double totalCost = group.getRentPricePerDay() * actualDaysToAdd;

        // NOUVEAU : D├®tecter si c'est un groupe PROFESSIONNEL
        boolean isProfessionalGroup = false;
        String renterCompanySiret = null;
        String ownerCompanySiret = null;
        CompanyPlotManager companyManager = plugin.getCompanyPlotManager();
        EntrepriseManagerLogic.Entreprise renterCompany = null;

        // V├®rifier si au moins une parcelle du groupe est PROFESSIONNEL
        for (String plotKey : group.getPlotKeys()) {
            String[] parts = plotKey.split(":");
            if (parts.length == 3) {
                String worldName = parts[0];
                int chunkX = Integer.parseInt(parts[1]);
                int chunkZ = Integer.parseInt(parts[2]);
                Plot plot = town.getPlot(worldName, chunkX, chunkZ);
                if (plot != null && plot.getType() == PlotType.PROFESSIONNEL) {
                    isProfessionalGroup = true;
                    if (plot.getCompanySiret() != null) {
                        ownerCompanySiret = plot.getCompanySiret();
                    }
                    if (plot.getRenterCompanySiret() != null) {
                        renterCompanySiret = plot.getRenterCompanySiret();
                    }
                    break;
                }
            }
        }

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

            // V├®rifier les fonds de l'entreprise
            if (renterCompany.getSolde() < totalCost) {
                renter.sendMessage(ChatColor.RED + "Votre entreprise n'a pas assez d'argent !");
                renter.sendMessage(ChatColor.YELLOW + "Prix: " + ChatColor.GOLD + String.format("%.2fÔé¼", totalCost));
                renter.sendMessage(ChatColor.YELLOW + "Solde entreprise: " + ChatColor.GOLD + String.format("%.2fÔé¼", renterCompany.getSolde()));
                return false;
            }

            // Pr├®lever de l'entreprise du locataire
            renterCompany.setSolde(renterCompany.getSolde() - totalCost);
        } else {
            // Groupe PARTICULIER : V├®rifier l'argent personnel
            if (!RoleplayCity.getEconomy().has(renter, totalCost)) {
                renter.sendMessage(ChatColor.RED + "Vous n'avez pas assez d'argent. Prix: " + totalCost + "Ôé¼");
                return false;
            }

            // Pr├®lever l'argent personnel
            RoleplayCity.getEconomy().withdrawPlayer(renter, totalCost);
        }

        // Donner l'argent au propri├®taire ou ├á la banque
        if (group.getOwnerUuid() != null) {
            if (isProfessionalGroup && ownerCompanySiret != null) {
                // Groupe PRO - argent va ├á l'entreprise du propri├®taire
                EntrepriseManagerLogic.Entreprise ownerCompany = companyManager.getCompanyBySiret(ownerCompanySiret);
                if (ownerCompany != null) {
                    ownerCompany.setSolde(ownerCompany.getSolde() + totalCost);

                    // Notifier le propri├®taire si en ligne
                    OfflinePlayer owner = Bukkit.getOfflinePlayer(group.getOwnerUuid());
                    if (owner.isOnline() && owner.getPlayer() != null) {
                        owner.getPlayer().sendMessage(ChatColor.GREEN + "Location recharg├®e!");
                        owner.getPlayer().sendMessage(ChatColor.YELLOW + "L'argent a ├®t├® vers├® ├á " + ownerCompany.getNom() + ": +" + String.format("%.2fÔé¼", totalCost));
                    }
                } else {
                    // Entreprise n'existe plus - argent va ├á la ville
                    town.deposit(totalCost);
                }
            } else {
                // Groupe PARTICULIER - argent va au propri├®taire
                OfflinePlayer owner = Bukkit.getOfflinePlayer(group.getOwnerUuid());
                RoleplayCity.getEconomy().depositPlayer(owner, totalCost);

                // Notifier si le propri├®taire est en ligne
                if (owner.isOnline() && owner.getPlayer() != null) {
                    owner.getPlayer().sendMessage(ChatColor.GREEN + "Location recharg├®e: +" + totalCost + "Ôé¼");
                }
            }
        } else {
            town.deposit(totalCost);
        }

        // Recharger le solde du groupe
        int actualAdded = group.rechargeDays(actualDaysToAdd);

        // Synchroniser avec toutes les parcelles du groupe
        for (String plotKey : group.getPlotKeys()) {
            String[] parts = plotKey.split(":");
            if (parts.length == 3) {
                String worldName = parts[0];
                int chunkX = Integer.parseInt(parts[1]);
                int chunkZ = Integer.parseInt(parts[2]);

                Plot plot = town.getPlot(worldName, chunkX, chunkZ);
                if (plot != null && plot.getRenterUuid() != null) {
                    plot.rechargeDays(actualAdded);
                }
            }
        }

        renter.sendMessage(ChatColor.GREEN + "Solde recharg├® : +" + actualAdded + " jours");
        renter.sendMessage(ChatColor.YELLOW + "Jours restants : " + ChatColor.GOLD + group.getRentDaysRemaining() + "/30");

        // Enregistrer la transaction
        addTransaction(townName, new PlotTransaction(
            PlotTransaction.TransactionType.RENT,
            renter.getUniqueId(),
            renter.getName(),
            totalCost,
            "Recharge groupe: " + group.getGroupName()
        ));

        // Sauvegarder imm├®diatement
        townManager.saveTownsNow();
        return true;
    }

    /**
     * V├®rifie les locations de groupes expir├®es
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
                        renter.sendMessage(ChatColor.RED + "Votre location du groupe '" + group.getGroupName() + "' a expir├®.");
                    }

                    // Notification d'expiration de groupe
                    notificationManager.notifyRentExpired(
                        group.getRenterUuid(),
                        "Groupe '" + group.getGroupName() + "'",
                        townName
                    );

                    // Retirer la location du groupe
                    group.clearRenter();

                    // Retirer aussi de toutes les parcelles
                    for (String plotKey : group.getPlotKeys()) {
                        String[] parts = plotKey.split(":");
                        if (parts.length == 3) {
                            String worldName = parts[0];
                            int chunkX = Integer.parseInt(parts[1]);
                            int chunkZ = Integer.parseInt(parts[2]);

                            Plot plot = town.getPlot(worldName, chunkX, chunkZ);
                            if (plot != null) {
                                plot.clearRenter();
                            }
                        }
                    }

                    plugin.getLogger().info("Location de groupe expir├®e: " + group.getGroupName() + " dans " + townName);
                }
            }
        }
    }

    // === R├ëSULTAT DE COLLECTE ===

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


