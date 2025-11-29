package com.gravityyfh.roleplaycity.town.manager;

import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.gravityyfh.roleplaycity.entreprise.model.*;
import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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

        // Vérifier que le vendeur a le droit
        TownRole role = town.getMemberRole(seller.getUniqueId());
        if (role == null) {
            return false;
        }

        // Seul le maire/adjoint peut vendre des parcelles municipales
        if (plot.isMunicipal() && role != TownRole.MAIRE && role != TownRole.ADJOINT) {
            return false;
        }

        // Pour les parcelles privées, seul le propriétaire ou le maire peut vendre
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

        // Vérifier que l'acheteur est membre de la ville
        if (!town.isMember(buyer.getUniqueId())) {
            buyer.sendMessage(ChatColor.RED + "Vous devez être membre de la ville pour acheter une parcelle.");
            return false;
        }

        // Empêcher le propriétaire d'acheter son propre terrain
        if (plot.getOwnerUuid() != null && plot.getOwnerUuid().equals(buyer.getUniqueId())) {
            buyer.sendMessage(ChatColor.RED + "Vous ne pouvez pas acheter votre propre parcelle !");
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
        Entreprise buyerCompany = null;

        if (isProfessional) {
            // Terrain PROFESSIONNEL : Acheter avec l'entreprise
            // Récupérer le SIRET via EnterpriseContextManager
            com.gravityyfh.roleplaycity.town.manager.EnterpriseContextManager enterpriseContextManager =
                plugin.getEnterpriseContextManager();

            String selectedSiret = null;
            if (enterpriseContextManager != null) {
                selectedSiret = enterpriseContextManager.getAndClearSelectedEnterprise(
                    buyer.getUniqueId(),
                    com.gravityyfh.roleplaycity.town.manager.EnterpriseContextManager.OperationType.PURCHASE
                );
            }

            if (selectedSiret == null) {
                // VALIDATION STRICTE: Pas de fallback silencieux
                buyer.sendMessage(ChatColor.RED + "✗ Erreur: Aucune entreprise sélectionnée.");
                buyer.sendMessage(ChatColor.YELLOW + "→ Veuillez recommencer l'achat.");
                plugin.getLogger().warning("[TownEconomy] buyPlot sans SIRET pour " + buyer.getName());
                return false;
            }

            // Valider que l'entreprise existe et que le joueur en est gérant
            if (enterpriseContextManager != null) {
                com.gravityyfh.roleplaycity.town.manager.EnterpriseContextManager.ValidationResult validation =
                    enterpriseContextManager.validateEnterprise(buyer, selectedSiret);

                if (!validation.isSuccess()) {
                    buyer.sendMessage(ChatColor.RED + "✗ Erreur: " + validation.getErrorMessage());
                    return false;
                }
                buyerCompany = validation.getEntreprise();
            } else {
                // Fallback
                buyerCompany = companyManager.getCompanyBySiret(selectedSiret);
                if (buyerCompany == null) {
                    buyer.sendMessage(ChatColor.RED + "✗ Erreur: Entreprise invalide.");
                    return false;
                }
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
                Entreprise previousCompany = companyManager.getCompanyBySiret(plot.getCompanySiret());
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
    public boolean putPlotForRent(String townName, Plot plot, double pricePerDay, Player owner) {
        Town town = townManager.getTown(townName);
        if (town == null) {
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

        // Pour les parcelles privées, seul le propriétaire ou le maire peut louer
        if (!plot.isMunicipal() && plot.getOwnerUuid() != null &&
                !plot.getOwnerUuid().equals(owner.getUniqueId()) &&
                role != TownRole.MAIRE) {
            return false;
        }

        // Définir le prix par jour directement
        plot.setRentPricePerDay(pricePerDay);
        plot.setForRent(true);

        // FIX: Ne plus scanner les blocs existants lors de la mise en location
        // Le système RenterBlockTracker gère automatiquement les blocs posés par le locataire
        // Les blocs existants appartiennent au propriétaire et sont protégés par le système de permissions
        // Nettoyer la liste des blocs protégés (pas besoin de stocker 20000+ blocs dans le YAML)
        plot.clearProtectedBlocks();

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
        Entreprise renterCompany = null; // Déclaration ici pour portée plus large

        if (plot.getType() == com.gravityyfh.roleplaycity.town.data.PlotType.PROFESSIONNEL) {
            if (!companyManager.validateCompanyOwnership(renter, plot)) {
                return false; // Message d'erreur déjà envoyé par validateCompanyOwnership
            }

            // Récupérer le SIRET via EnterpriseContextManager
            com.gravityyfh.roleplaycity.town.manager.EnterpriseContextManager enterpriseContextManager =
                plugin.getEnterpriseContextManager();

            String selectedSiret = null;
            if (enterpriseContextManager != null) {
                selectedSiret = enterpriseContextManager.getAndClearSelectedEnterprise(
                    renter.getUniqueId(),
                    com.gravityyfh.roleplaycity.town.manager.EnterpriseContextManager.OperationType.RENTAL
                );
            }

            if (selectedSiret == null) {
                // VALIDATION STRICTE: Pas de fallback silencieux
                renter.sendMessage(ChatColor.RED + "✗ Erreur: Aucune entreprise sélectionnée.");
                renter.sendMessage(ChatColor.YELLOW + "→ Veuillez recommencer la location.");
                plugin.getLogger().warning("[TownEconomy] rentPlot sans SIRET pour " + renter.getName());
                return false;
            }

            // Valider que l'entreprise existe et que le joueur en est gérant
            if (enterpriseContextManager != null) {
                com.gravityyfh.roleplaycity.town.manager.EnterpriseContextManager.ValidationResult validation =
                    enterpriseContextManager.validateEnterprise(renter, selectedSiret);

                if (!validation.isSuccess()) {
                    renter.sendMessage(ChatColor.RED + "✗ Erreur: " + validation.getErrorMessage());
                    return false;
                }
                renterCompany = validation.getEntreprise();
            } else {
                // Fallback
                renterCompany = companyManager.getCompanyBySiret(selectedSiret);
                if (renterCompany == null) {
                    renter.sendMessage(ChatColor.RED + "✗ Erreur: Entreprise invalide.");
                    return false;
                }
            }

            // Stocker le SIRET de l'entreprise du locataire
            plot.setRenterCompanySiret(selectedSiret);
        }

        // Limiter à 30 jours max
        int actualDays = Math.min(days, 30);
        double totalCost = plot.getRentPricePerDay() * actualDays;

        // NOUVEAU : Gestion différente selon le type de terrain
        boolean isProfessional = (plot.getType() == PlotType.PROFESSIONNEL);

        // IMPORTANT: Ne pas redéclarer renterCompany ici, on utilise celle récupérée plus haut (ligne 388)
        // qui correspond à l'entreprise SÉLECTIONNÉE par le joueur

        if (isProfessional) {
            // Terrain PROFESSIONNEL : Utiliser l'entreprise SÉLECTIONNÉE (déjà récupérée ligne 388)
            // NOTE: renterCompany est déjà définie plus haut avec l'entreprise sélectionnée
            if (renterCompany == null) {
                renter.sendMessage(ChatColor.RED + "Erreur: Entreprise sélectionnée introuvable.");
                return false;
            }

            // Vérifier que l'entreprise a assez d'argent
            if (renterCompany.getSolde() < totalCost) {
                renter.sendMessage(ChatColor.RED + "Votre entreprise n'a pas assez d'argent !");
                renter.sendMessage(ChatColor.YELLOW + "Prix: " + ChatColor.GOLD + String.format("%.2f€", totalCost));
                renter.sendMessage(ChatColor.YELLOW + "Solde entreprise: " + ChatColor.GOLD + String.format("%.2f€", renterCompany.getSolde()));
                return false;
            }

            // Prélever de l'entreprise SÉLECTIONNÉE
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
                Entreprise ownerCompany = companyManager.getCompanyBySiret(plot.getCompanySiret());
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
        Entreprise renterCompany = null;

        if (isProfessional) {
            // Terrain PROFESSIONNEL : Recharger avec l'entreprise qui a fait la location initiale
            String renterSiret = plot.getRenterCompanySiret();
            if (renterSiret != null) {
                renterCompany = companyManager.getCompanyBySiret(renterSiret);
                if (renterCompany == null) {
                    renter.sendMessage(ChatColor.RED + "Erreur: L'entreprise qui a loué ce terrain n'existe plus (SIRET: " + renterSiret + ").");
                    renter.sendMessage(ChatColor.YELLOW + "Contactez un administrateur pour régulariser la situation.");
                    return false;
                }
            } else {
                // Fallback pour anciennes locations (avant le système de SIRET)
                plugin.getLogger().warning("[TownEconomy] Recharge sans SIRET stocké pour plot " + plot.getCoordinates() + ", utilisation de getPlayerCompany() (DEPRECATED)");
                renterCompany = companyManager.getPlayerCompany(renter);
                if (renterCompany == null) {
                    renter.sendMessage(ChatColor.RED + "Erreur: Entreprise introuvable.");
                    return false;
                }
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
                Entreprise ownerCompany = companyManager.getCompanyBySiret(plot.getCompanySiret());
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
                // FIX BASSE #7: Utiliser getRentDaysRemaining() au lieu de isRentExpired() deprecated
                if (plot.getRenterUuid() != null && plot.getRentDaysRemaining() <= 0) {
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

        // Traiter tous les plots (individuels et groupés)
        for (Plot plot : town.getPlots().values()) {

            double tax = plot.getDailyTax();
            if (tax <= 0) {
                continue;
            }

            if (plot.getType() == PlotType.PROFESSIONNEL && plot.getCompanySiret() != null) {
                CompanyPlotManager companyManager = plugin.getCompanyPlotManager();
                Entreprise company = companyManager.getCompanyBySiret(plot.getCompanySiret());

                if (company != null) {
                    // FIX: D'abord tenter de rembourser les dettes entreprise existantes
                    double existingCompanyDebt = plot.getCompanyDebtAmount();
                    if (existingCompanyDebt > 0) {
                        double totalDue = existingCompanyDebt + tax;
                        if (company.getSolde() >= totalDue) {
                            // Payer la dette existante
                            company.setSolde(company.getSolde() - existingCompanyDebt);
                            town.deposit(existingCompanyDebt);
                            totalCollected += existingCompanyDebt;

                            plugin.getLogger().info(String.format(
                                    "[TownEconomyManager] Dette entreprise remboursée automatiquement: %s a payé %.2f€ pour %s",
                                    company.getNom(), existingCompanyDebt, plot.getCoordinates()
                            ));

                            UUID gerantUuid = plot.getOwnerUuid();
                            if (gerantUuid != null) {
                                Player gerant = Bukkit.getPlayer(gerantUuid);
                                if (gerant != null && gerant.isOnline()) {
                                    gerant.sendMessage(ChatColor.GREEN + "✓ Dette entreprise de " +
                                            String.format("%.2f€", existingCompanyDebt) + " remboursée automatiquement pour " +
                                            plot.getCoordinates());
                                }
                                debtNotificationService.refresh(gerantUuid, DebtNotificationService.DebtUpdateReason.PAYMENT);
                            }

                            addTransaction(townName, new PlotTransaction(
                                    PlotTransaction.TransactionType.TAX,
                                    plot.getOwnerUuid(),
                                    company.getNom() + " (PRO)",
                                    existingCompanyDebt,
                                    "Remboursement dette entreprise " + plot.getCoordinates()
                            ));

                            plot.resetDebt();
                        } else if (company.getSolde() >= existingCompanyDebt) {
                            // Payer la dette mais pas la taxe
                            company.setSolde(company.getSolde() - existingCompanyDebt);
                            town.deposit(existingCompanyDebt);
                            totalCollected += existingCompanyDebt;

                            plugin.getLogger().info(String.format(
                                    "[TownEconomyManager] Dette entreprise remboursée (sans taxe): %s a payé %.2f€ pour %s",
                                    company.getNom(), existingCompanyDebt, plot.getCoordinates()
                            ));

                            UUID gerantUuid = plot.getOwnerUuid();
                            if (gerantUuid != null) {
                                Player gerant = Bukkit.getPlayer(gerantUuid);
                                if (gerant != null && gerant.isOnline()) {
                                    gerant.sendMessage(ChatColor.GREEN + "✓ Dette entreprise de " +
                                            String.format("%.2f€", existingCompanyDebt) + " remboursée pour " +
                                            plot.getCoordinates());
                                    gerant.sendMessage(ChatColor.YELLOW + "⚠ Taxe de " +
                                            String.format("%.2f€", tax) + " ajoutée comme nouvelle dette (fonds insuffisants)");
                                }
                            }

                            addTransaction(townName, new PlotTransaction(
                                    PlotTransaction.TransactionType.TAX,
                                    plot.getOwnerUuid(),
                                    company.getNom() + " (PRO)",
                                    existingCompanyDebt,
                                    "Remboursement dette entreprise " + plot.getCoordinates()
                            ));

                            plot.resetDebt();
                            // Créer une nouvelle dette pour la taxe
                            companyManager.handleInsufficientFunds(plot, company, tax);

                            if (companyManager.checkCompanyDebtStatus(plot)) {
                                companyManager.seizePlotForDebt(plot);
                            }
                            continue;
                        }
                    }

                    if (company.getSolde() >= tax) {
                        company.setSolde(company.getSolde() - tax);
                        town.deposit(tax);
                        totalCollected += tax;
                        parcelsWithTax++;

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
                            companyManager.seizePlotForDebt(plot);
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
            // FIX CRITIQUE: Utiliser le nom stocké si getName() retourne null
            // Ceci arrive quand le joueur n'a pas été vu depuis le redémarrage du serveur
            String effectiveName = payer.getName();
            if (effectiveName == null) {
                effectiveName = payerName; // Utiliser le nom stocké dans le plot
            } else {
                payerName = effectiveName;
            }

            // Si on n'a toujours pas de nom, on ne peut pas faire la transaction
            if (effectiveName == null) {
                plugin.getLogger().warning(String.format(
                        "[TownEconomyManager] Impossible de collecter la taxe: nom inconnu pour UUID %s sur parcelle %s",
                        payerUuid, plot.getCoordinates()
                ));
                continue;
            }

            // FIX: D'abord tenter de rembourser les dettes existantes AVANT la taxe journalière
            double existingDebt = plot.getParticularDebtAmount();
            if (existingDebt > 0) {
                // Vérifier si le joueur peut payer la dette + la taxe (utiliser le nom effectif)
                double totalDue = existingDebt + tax;
                if (hasMoneyByName(effectiveName, totalDue)) {
                    // Payer la dette existante
                    withdrawByName(effectiveName, existingDebt);
                    town.deposit(existingDebt);
                    totalCollected += existingDebt;

                    plugin.getLogger().info(String.format(
                            "[TownEconomyManager] Dette remboursée automatiquement: %s a payé %.2f€ de dette pour %s",
                            payerName, existingDebt, plot.getCoordinates()
                    ));

                    if (payer.isOnline() && payer.getPlayer() != null) {
                        payer.getPlayer().sendMessage(ChatColor.GREEN + "✓ Dette de " +
                                String.format("%.2f€", existingDebt) + " remboursée automatiquement pour " +
                                plot.getCoordinates());
                    }

                    addTransaction(townName, new PlotTransaction(
                            PlotTransaction.TransactionType.TAX,
                            payerUuid,
                            payerName,
                            existingDebt,
                            "Remboursement dette parcelle " + plot.getCoordinates()
                    ));

                    plot.resetParticularDebt();
                    debtNotificationService.refresh(payerUuid, DebtNotificationService.DebtUpdateReason.PAYMENT);
                } else if (hasMoneyByName(effectiveName, existingDebt)) {
                    // Le joueur peut payer la dette mais pas la taxe
                    withdrawByName(effectiveName, existingDebt);
                    town.deposit(existingDebt);
                    totalCollected += existingDebt;

                    plugin.getLogger().info(String.format(
                            "[TownEconomyManager] Dette remboursée automatiquement (sans taxe): %s a payé %.2f€ de dette pour %s",
                            payerName, existingDebt, plot.getCoordinates()
                    ));

                    if (payer.isOnline() && payer.getPlayer() != null) {
                        payer.getPlayer().sendMessage(ChatColor.GREEN + "✓ Dette de " +
                                String.format("%.2f€", existingDebt) + " remboursée automatiquement pour " +
                                plot.getCoordinates());
                        payer.getPlayer().sendMessage(ChatColor.YELLOW + "⚠ Taxe de " +
                                String.format("%.2f€", tax) + " ajoutée comme nouvelle dette (fonds insuffisants)");
                    }

                    addTransaction(townName, new PlotTransaction(
                            PlotTransaction.TransactionType.TAX,
                            payerUuid,
                            payerName,
                            existingDebt,
                            "Remboursement dette parcelle " + plot.getCoordinates()
                    ));

                    plot.resetParticularDebt();
                    plot.setParticularDebtAmount(tax);
                    plot.setParticularLastDebtWarningDate(LocalDateTime.now());
                    plot.setParticularDebtWarningCount(1);

                    unpaidPlayers.add(payerName != null ? payerName : payerUuid.toString());
                    debtNotificationService.refresh(payerUuid, DebtNotificationService.DebtUpdateReason.ECONOMY_EVENT);
                    continue; // Passer au plot suivant
                }
            }

            if (hasMoneyByName(effectiveName, tax)) {
                withdrawByName(effectiveName, tax);
                town.deposit(tax);
                totalCollected += tax;
                parcelsWithTax++;

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
                    long daysSinceWarning = ChronoUnit.DAYS.between(plot.getParticularLastDebtWarningDate().toLocalDate(), LocalDateTime.now().toLocalDate());
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

        // Collecter les taxes de toutes les parcelles (individuelles et groupées)
        for (Plot plot : town.getPlots().values()) {
            double dailyTax = plot.getDailyTax();
            if (dailyTax <= 0) continue;

            // NOUVEAU : Montant HORAIRE
            double hourlyTax = dailyTax / 24.0;

            // === Gestion des terrains PROFESSIONNEL avec entreprise ===
            if (plot.getType() == PlotType.PROFESSIONNEL && plot.getCompanySiret() != null) {
                CompanyPlotManager companyManager = plugin.getCompanyPlotManager();
                Entreprise company = companyManager.getCompanyBySiret(plot.getCompanySiret());

                if (company != null) {
                    // FIX: D'abord tenter de rembourser les dettes entreprise existantes
                    double existingCompanyDebt = plot.getCompanyDebtAmount();
                    if (existingCompanyDebt > 0) {
                        double totalDue = existingCompanyDebt + hourlyTax;
                        if (company.getSolde() >= totalDue) {
                            // Payer la dette existante
                            company.setSolde(company.getSolde() - existingCompanyDebt);
                            town.deposit(existingCompanyDebt);
                            totalCollected += existingCompanyDebt;

                            plugin.getLogger().info(String.format(
                                    "[TownEconomyManager] Dette entreprise remboursée automatiquement: %s a payé %.2f€ pour %s",
                                    company.getNom(), existingCompanyDebt, plot.getCoordinates()
                            ));

                            UUID gerantUuid = plot.getOwnerUuid();
                            if (gerantUuid != null) {
                                Player gerant = Bukkit.getPlayer(gerantUuid);
                                if (gerant != null && gerant.isOnline()) {
                                    gerant.sendMessage(ChatColor.GREEN + "✓ Dette entreprise de " +
                                            String.format("%.2f€", existingCompanyDebt) + " remboursée automatiquement pour " +
                                            plot.getCoordinates());
                                }
                                debtNotificationService.refresh(gerantUuid, DebtNotificationService.DebtUpdateReason.PAYMENT);
                            }

                            addTransaction(townName, new PlotTransaction(
                                    PlotTransaction.TransactionType.TAX,
                                    plot.getOwnerUuid(),
                                    company.getNom() + " (PRO)",
                                    existingCompanyDebt,
                                    "Remboursement dette entreprise " + plot.getCoordinates()
                            ));

                            plot.resetDebt();
                        } else if (company.getSolde() >= existingCompanyDebt) {
                            // Payer la dette mais pas la taxe horaire
                            company.setSolde(company.getSolde() - existingCompanyDebt);
                            town.deposit(existingCompanyDebt);
                            totalCollected += existingCompanyDebt;

                            plugin.getLogger().info(String.format(
                                    "[TownEconomyManager] Dette entreprise remboursée (sans taxe): %s a payé %.2f€ pour %s",
                                    company.getNom(), existingCompanyDebt, plot.getCoordinates()
                            ));

                            UUID gerantUuid = plot.getOwnerUuid();
                            if (gerantUuid != null) {
                                Player gerant = Bukkit.getPlayer(gerantUuid);
                                if (gerant != null && gerant.isOnline()) {
                                    gerant.sendMessage(ChatColor.GREEN + "✓ Dette entreprise de " +
                                            String.format("%.2f€", existingCompanyDebt) + " remboursée pour " +
                                            plot.getCoordinates());
                                    gerant.sendMessage(ChatColor.YELLOW + "⚠ Taxe horaire de " +
                                            String.format("%.2f€", hourlyTax) + " ajoutée comme nouvelle dette (fonds insuffisants)");
                                }
                            }

                            addTransaction(townName, new PlotTransaction(
                                    PlotTransaction.TransactionType.TAX,
                                    plot.getOwnerUuid(),
                                    company.getNom() + " (PRO)",
                                    existingCompanyDebt,
                                    "Remboursement dette entreprise " + plot.getCoordinates()
                            ));

                            plot.resetDebt();
                            // Créer une nouvelle dette pour la taxe horaire
                            companyManager.handleInsufficientFunds(plot, company, hourlyTax);

                            if (companyManager.checkCompanyDebtStatus(plot)) {
                                companyManager.seizePlotForDebt(plot);
                            }
                            continue;
                        }
                    }

                    if (company.getSolde() >= hourlyTax) {
                        company.setSolde(company.getSolde() - hourlyTax);
                        town.deposit(hourlyTax);
                        totalCollected += hourlyTax;
                        parcelsWithTax++;

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
                        companyManager.seizePlotForDebt(plot);
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
            // FIX CRITIQUE: Utiliser le nom stocké si getName() retourne null
            String effectiveName = payer.getName();
            if (effectiveName == null) {
                effectiveName = payerName;
            } else {
                payerName = effectiveName;
            }

            // Si on n'a toujours pas de nom, on ne peut pas faire la transaction
            if (effectiveName == null) {
                plugin.getLogger().warning(String.format(
                        "[TownEconomyManager] Impossible de collecter la taxe horaire: nom inconnu pour UUID %s sur parcelle %s",
                        payerUuid, plot.getCoordinates()
                ));
                continue;
            }

            // FIX: D'abord tenter de rembourser les dettes existantes AVANT la taxe horaire
            double existingDebt = plot.getParticularDebtAmount();
            if (existingDebt > 0) {
                // Vérifier si le joueur peut payer la dette + la taxe horaire
                double totalDue = existingDebt + hourlyTax;
                if (hasMoneyByName(effectiveName, totalDue)) {
                    // Payer la dette existante
                    withdrawByName(effectiveName, existingDebt);
                    town.deposit(existingDebt);
                    totalCollected += existingDebt;

                    plugin.getLogger().info(String.format(
                            "[TownEconomyManager] Dette remboursée automatiquement: %s a payé %.2f€ de dette pour %s",
                            payerName, existingDebt, plot.getCoordinates()
                    ));

                    // Notifier le joueur si en ligne
                    if (payer.isOnline() && payer.getPlayer() != null) {
                        payer.getPlayer().sendMessage(ChatColor.GREEN + "✓ Dette de " +
                                String.format("%.2f€", existingDebt) + " remboursée automatiquement pour " +
                                plot.getCoordinates());
                    }

                    addTransaction(townName, new PlotTransaction(
                            PlotTransaction.TransactionType.TAX,
                            payerUuid,
                            payerName,
                            existingDebt,
                            "Remboursement dette parcelle " + plot.getCoordinates()
                    ));

                    // Réinitialiser la dette
                    plot.resetParticularDebt();

                    // Rafraîchir les notifications de dette
                    debtNotificationService.refresh(payerUuid, DebtNotificationService.DebtUpdateReason.PAYMENT);
                } else if (hasMoneyByName(effectiveName, existingDebt)) {
                    // Le joueur peut payer la dette mais pas la taxe horaire
                    // Priorité: payer la dette d'abord pour éviter la saisie
                    withdrawByName(effectiveName, existingDebt);
                    town.deposit(existingDebt);
                    totalCollected += existingDebt;

                    plugin.getLogger().info(String.format(
                            "[TownEconomyManager] Dette remboursée automatiquement (sans taxe): %s a payé %.2f€ de dette pour %s",
                            payerName, existingDebt, plot.getCoordinates()
                    ));

                    if (payer.isOnline() && payer.getPlayer() != null) {
                        payer.getPlayer().sendMessage(ChatColor.GREEN + "✓ Dette de " +
                                String.format("%.2f€", existingDebt) + " remboursée automatiquement pour " +
                                plot.getCoordinates());
                        payer.getPlayer().sendMessage(ChatColor.YELLOW + "⚠ Taxe horaire de " +
                                String.format("%.2f€", hourlyTax) + " ajoutée comme nouvelle dette (fonds insuffisants)");
                    }

                    addTransaction(townName, new PlotTransaction(
                            PlotTransaction.TransactionType.TAX,
                            payerUuid,
                            payerName,
                            existingDebt,
                            "Remboursement dette parcelle " + plot.getCoordinates()
                    ));

                    // Réinitialiser la dette puis recréer avec la taxe horaire
                    plot.resetParticularDebt();
                    plot.setParticularDebtAmount(hourlyTax);
                    plot.setParticularLastDebtWarningDate(LocalDateTime.now());
                    plot.setParticularDebtWarningCount(1);

                    unpaidPlayers.add(payerName);
                    debtNotificationService.refresh(payerUuid, DebtNotificationService.DebtUpdateReason.ECONOMY_EVENT);
                    continue; // Passer au plot suivant
                }
            }

            if (hasMoneyByName(effectiveName, hourlyTax)) {
                withdrawByName(effectiveName, hourlyTax);
                town.deposit(hourlyTax);
                totalCollected += hourlyTax;
                parcelsWithTax++;

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
                // ✅ FIX: Utiliser ChronoUnit.DAYS pour compter les jours calendaires
                long daysSinceWarning = ChronoUnit.DAYS.between(warningDate.toLocalDate(), LocalDateTime.now().toLocalDate());

                if (daysSinceWarning >= 7) {
                    // SAISIE AUTOMATIQUE du terrain
                    plugin.getLogger().warning(String.format(
                            "[TownEconomyManager] SAISIE AUTO - Terrain %s:%d,%d saisi pour dette (Particulier %s, Dette: %.2f€)",
                            plot.getWorldName(), plot.getChunkX(), plot.getChunkZ(), payerName, plot.getParticularDebtAmount()
                    ));

                    // Notifier le joueur
                    if (false) {
                        payer.getPlayer().sendMessage("");
                        payer.getPlayer().sendMessage(ChatColor.DARK_RED + "⚠――――――――――――――――――――――⚠");
                        payer.getPlayer().sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "⚠ SAISIE DE TERRAIN");
                        payer.getPlayer().sendMessage(ChatColor.DARK_RED + "⚠――――――――――――――――――――――⚠");
                        payer.getPlayer().sendMessage(ChatColor.RED + "Votre terrain " + plot.getCoordinates());
                        payer.getPlayer().sendMessage(ChatColor.RED + "a été saisi pour dette impayée!");
                        payer.getPlayer().sendMessage("");
                        payer.getPlayer().sendMessage(ChatColor.YELLOW + "Dette: " + ChatColor.GOLD +
                                String.format("%.2f€", plot.getParticularDebtAmount()));
                        payer.getPlayer().sendMessage(ChatColor.GRAY + "Le terrain retourne à la ville.");
                        payer.getPlayer().sendMessage(ChatColor.DARK_RED + "⚠――――――――――――――――――――――⚠");
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
                player.sendMessage(ChatColor.GOLD + "⚠――――――――――――――――――――――⚠");
                player.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "    RAPPORT TAXES HORAIRES");
                player.sendMessage(ChatColor.GOLD + "⚠――――――――――――――――――――――⚠");
                player.sendMessage(ChatColor.AQUA + "Ville: " + ChatColor.WHITE + town.getName());
                player.sendMessage(ChatColor.AQUA + "Montant prélevé: " + ChatColor.GOLD + String.format("%.2f€", taxAmount));
                player.sendMessage(ChatColor.GRAY + "Heure: " + ChatColor.WHITE +
                        java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
                player.sendMessage(ChatColor.GOLD + "⚠――――――――――――――――――――――⚠");
                player.sendMessage("");
            }
        }

        // === RAPPORT MAIRE ===
        UUID mayorUuid = town.getMayorUuid();
        if (mayorUuid != null) {
            Player mayor = Bukkit.getPlayer(mayorUuid);
            if (mayor != null && mayor.isOnline()) {
                mayor.sendMessage("");
                mayor.sendMessage(ChatColor.GOLD + "⚠――――――――――――――――――――――⚠");
                mayor.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "  RAPPORT MAIRE - TAXES HORAIRES");
                mayor.sendMessage(ChatColor.GOLD + "⚠――――――――――――――――――――――⚠");
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
                mayor.sendMessage(ChatColor.GOLD + "⚠――――――――――――――――――――――⚠");
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

    // === MÉTHODES UTILITAIRES POUR TRANSACTIONS OFFLINE ===

    /**
     * Vérifie si un joueur (par nom) a assez d'argent.
     * Utilise directement le nom pour éviter les problèmes avec OfflinePlayer.getName() null.
     */
    private boolean hasMoneyByName(String playerName, double amount) {
        if (playerName == null || playerName.isEmpty()) {
            return false;
        }
        try {
            double balance = RoleplayCity.getEconomy().getBalance(playerName);
            return balance >= amount;
        } catch (Exception e) {
            plugin.getLogger().warning("[TownEconomyManager] Erreur lors de la vérification du solde pour " + playerName + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Retire de l'argent d'un joueur (par nom).
     * Utilise directement le nom pour éviter les problèmes avec OfflinePlayer.getName() null.
     * @return true si le retrait a réussi
     */
    private boolean withdrawByName(String playerName, double amount) {
        if (playerName == null || playerName.isEmpty()) {
            return false;
        }
        try {
            net.milkbowl.vault.economy.EconomyResponse response = RoleplayCity.getEconomy().withdrawPlayer(playerName, amount);
            return response.transactionSuccess();
        } catch (Exception e) {
            plugin.getLogger().warning("[TownEconomyManager] Erreur lors du retrait pour " + playerName + ": " + e.getMessage());
            return false;
        }
    }

    // === RÉSULTAT DE COLLECTE ===

    public record TaxCollectionResult(double totalCollected, int parcelsCollected, int unpaidCount,
                                      List<String> unpaidPlayers) {
    }
}
