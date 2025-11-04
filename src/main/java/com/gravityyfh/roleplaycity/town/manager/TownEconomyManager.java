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

    // Historique des transactions par ville
    private final Map<String, List<PlotTransaction>> transactionHistory;

    public TownEconomyManager(RoleplayCity plugin, TownManager townManager, ClaimManager claimManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.claimManager = claimManager;
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
            buyer.sendMessage(ChatColor.GREEN + "✓ Terrain professionnel acheté avec succès !");
            buyer.sendMessage(ChatColor.YELLOW + "Entreprise: " + ChatColor.WHITE + buyerCompany.getNom());
            buyer.sendMessage(ChatColor.YELLOW + "Coordonnées: " + ChatColor.WHITE + plot.getCoordinates());
            buyer.sendMessage(ChatColor.YELLOW + "Prix payé: " + ChatColor.GOLD + String.format("%.2f€", price));
            buyer.sendMessage(ChatColor.YELLOW + "Solde entreprise restant: " + ChatColor.GOLD + String.format("%.2f€", buyerCompany.getSolde()));
            buyer.sendMessage(ChatColor.GRAY + "Les taxes seront prélevées du solde de l'entreprise.");
        } else {
            buyer.sendMessage(ChatColor.GREEN + "Vous avez acheté la parcelle " + plot.getCoordinates() + " pour " + price + "€ !");
        }

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

        // Vérifier que le locataire est membre de la ville
        if (!town.isMember(renter.getUniqueId())) {
            renter.sendMessage(ChatColor.RED + "Vous devez être membre de la ville pour louer une parcelle.");
            return false;
        }

        // Limiter à 30 jours max
        int actualDays = Math.min(days, 30);
        double totalCost = plot.getRentPricePerDay() * actualDays;

        // Vérifier que le locataire a assez d'argent
        if (!RoleplayCity.getEconomy().has(renter, totalCost)) {
            renter.sendMessage(ChatColor.RED + "Vous n'avez pas assez d'argent. Prix: " +
                String.format("%.2f€", totalCost));
            return false;
        }

        // Prélever l'argent
        RoleplayCity.getEconomy().withdrawPlayer(renter, totalCost);

        // Donner l'argent au propriétaire ou à la ville
        if (plot.getOwnerUuid() != null) {
            // Verser l'argent au propriétaire même s'il est hors ligne
            OfflinePlayer owner = Bukkit.getOfflinePlayer(plot.getOwnerUuid());
            RoleplayCity.getEconomy().depositPlayer(owner, totalCost);

            // Notifier si le propriétaire est en ligne
            if (owner.isOnline() && owner.getPlayer() != null) {
                owner.getPlayer().sendMessage(ChatColor.GREEN + "Votre parcelle a été louée pour " +
                    actualDays + " jours (" + String.format("%.2f€", totalCost) + ") !");
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

        // Vérifier que le locataire a assez d'argent
        if (!RoleplayCity.getEconomy().has(renter, totalCost)) {
            renter.sendMessage(ChatColor.RED + "Vous n'avez pas assez d'argent. Prix: " +
                String.format("%.2f€", totalCost) + " pour " + actualDaysToAdd + " jours");
            return false;
        }

        // Prélever l'argent
        RoleplayCity.getEconomy().withdrawPlayer(renter, totalCost);

        // Donner l'argent au propriétaire ou à la ville
        if (plot.getOwnerUuid() != null) {
            // Verser l'argent au propriétaire même s'il est hors ligne
            OfflinePlayer owner = Bukkit.getOfflinePlayer(plot.getOwnerUuid());
            RoleplayCity.getEconomy().depositPlayer(owner, totalCost);

            // Notifier si le propriétaire est en ligne
            if (owner.isOnline() && owner.getPlayer() != null) {
                owner.getPlayer().sendMessage(ChatColor.GREEN + "Location rechargée : +" + actualDaysToAdd +
                    " jours (" + String.format("%.2f€", totalCost) + ")");
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
     */
    public TaxCollectionResult collectTaxes(String townName) {
        Town town = townManager.getTown(townName);
        if (town == null) {
            return new TaxCollectionResult(0, 0, 0, new ArrayList<>());
        }

        double totalCollected = 0;
        int parcelsWithTax = 0;
        List<String> unpaidPlayers = new ArrayList<>();

        for (Plot plot : town.getPlots().values()) {
            double tax = plot.getDailyTax();
            if (tax <= 0) {
                continue; // Pas de taxe pour cette parcelle
            }

            // === NOUVEAU : Gestion des terrains PROFESSIONNEL avec entreprise ===
            if (plot.getType() == PlotType.PROFESSIONNEL && plot.getCompanySiret() != null) {
                CompanyPlotManager companyManager = plugin.getCompanyPlotManager();
                EntrepriseManagerLogic.Entreprise company = companyManager.getCompanyBySiret(plot.getCompanySiret());

                if (company != null) {
                    // Entreprise existe - prélever la taxe du solde entreprise
                    if (company.getSolde() >= tax) {
                        // Fonds suffisants - paiement réussi
                        company.setSolde(company.getSolde() - tax);
                        town.deposit(tax);
                        totalCollected += tax;
                        parcelsWithTax++;

                        // Réinitialiser la dette si le terrain était endetté
                        if (plot.getCompanyDebtAmount() > 0) {
                            plot.resetDebt();
                            plugin.getLogger().info(String.format(
                                "[TownEconomyManager] Dette remboursée pour terrain %s:%d,%d (Entreprise %s)",
                                plot.getWorldName(), plot.getChunkX(), plot.getChunkZ(), company.getNom()
                            ));
                        }

                        // Notifier le gérant si en ligne
                        UUID gerantUuid = plot.getOwnerUuid();
                        if (gerantUuid != null) {
                            Player gerant = Bukkit.getPlayer(gerantUuid);
                            if (gerant != null && gerant.isOnline()) {
                                gerant.sendMessage(ChatColor.YELLOW + "💼 Taxe entreprise: " + ChatColor.GOLD + String.format("%.2f€", tax) +
                                    ChatColor.GRAY + " prélevée pour " + plot.getCoordinates());
                                gerant.sendMessage(ChatColor.GRAY + "Entreprise: " + company.getNom() +
                                    " - Solde restant: " + String.format("%.2f€", company.getSolde()));
                            }
                        }

                        // Enregistrer la transaction
                        addTransaction(townName, new PlotTransaction(
                            PlotTransaction.TransactionType.TAX,
                            plot.getOwnerUuid(),
                            company.getNom() + " (PRO)",
                            tax,
                            "Taxe entreprise " + plot.getCoordinates()
                        ));
                    } else {
                        // Fonds insuffisants - créer/augmenter la dette
                        companyManager.handleInsufficientFunds(plot, company, tax);

                        plugin.getLogger().warning(String.format(
                            "[TownEconomyManager] Entreprise %s - Fonds insuffisants pour taxe de %.2f€ sur terrain %s:%d,%d",
                            company.getNom(), tax, plot.getWorldName(), plot.getChunkX(), plot.getChunkZ()
                        ));
                    }

                    // Vérifier si le délai de grâce est dépassé (7 jours)
                    if (companyManager.checkCompanyDebtStatus(plot)) {
                        // Saisie automatique du terrain
                        companyManager.seizePlotForDebt(plot, townName);

                        plugin.getLogger().warning(String.format(
                            "[TownEconomyManager] SAISIE AUTO - Terrain %s:%d,%d saisi pour dette (Entreprise %s)",
                            plot.getWorldName(), plot.getChunkX(), plot.getChunkZ(), company.getNom()
                        ));
                    }
                } else {
                    // Entreprise n'existe plus - vendre tous les terrains de cette entreprise
                    plugin.getLogger().warning(String.format(
                        "[TownEconomyManager] Entreprise SIRET %s introuvable - Vente automatique des terrains",
                        plot.getCompanySiret()
                    ));
                    companyManager.handleCompanyDeletion(plot.getCompanySiret(), townName);
                }

                continue; // Passer au terrain suivant
            }

            // === Gestion des terrains PARTICULIER (logique existante) ===
            UUID payerUuid = null;
            String payerName = null;

            // Déterminer qui doit payer
            if (plot.getRenterUuid() != null) {
                payerUuid = plot.getRenterUuid();
                payerName = "Locataire"; // On ne stocke pas le nom du locataire
            } else if (plot.getOwnerUuid() != null) {
                payerUuid = plot.getOwnerUuid();
                payerName = plot.getOwnerName();
            }

            if (payerUuid == null) {
                continue; // Pas de payeur pour cette parcelle
            }

            Player payer = Bukkit.getPlayer(payerUuid);
            if (payer == null || !payer.isOnline()) {
                unpaidPlayers.add(payerName != null ? payerName : payerUuid.toString());
                continue; // Joueur offline, skip
            }

            // Vérifier et prélever la taxe
            if (RoleplayCity.getEconomy().has(payer, tax)) {
                RoleplayCity.getEconomy().withdrawPlayer(payer, tax);
                town.deposit(tax);
                totalCollected += tax;
                parcelsWithTax++;

                payer.sendMessage(ChatColor.YELLOW + "Taxe parcelle: " + ChatColor.GOLD + tax + "€ " +
                    ChatColor.GRAY + "prélevée pour " + plot.getCoordinates());

                // Enregistrer la transaction
                addTransaction(townName, new PlotTransaction(
                    PlotTransaction.TransactionType.TAX,
                    payerUuid,
                    payer.getName(),
                    tax,
                    "Taxe parcelle " + plot.getCoordinates()
                ));
            } else {
                unpaidPlayers.add(payer.getName());
                payer.sendMessage(ChatColor.RED + "Vous n'avez pas pu payer la taxe de " + tax +
                    "€ pour la parcelle " + plot.getCoordinates());
            }
        }

        // Mettre à jour la date de dernière collecte
        town.setLastTaxCollection(LocalDateTime.now());

        // Sauvegarder immédiatement les changements de taxes et solde banque
        townManager.saveTownsNow();

        return new TaxCollectionResult(totalCollected, parcelsWithTax, unpaidPlayers.size(), unpaidPlayers);
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

        // Mettre aussi toutes les parcelles individuelles en vente
        for (String plotKey : group.getPlotKeys()) {
            String[] parts = plotKey.split(":");
            if (parts.length == 3) {
                String worldName = parts[0];
                int chunkX = Integer.parseInt(parts[1]);
                int chunkZ = Integer.parseInt(parts[2]);

                Plot plot = town.getPlot(worldName, chunkX, chunkZ);
                if (plot != null) {
                    plot.setForSale(false); // Empêcher vente individuelle
                }
            }
        }

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

        // === NOUVEAU : Détecter si le groupe contient des terrains PROFESSIONNEL ===
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

        // Si le groupe contient des terrains PRO, valider l'entreprise et utiliser les fonds d'entreprise
        if (hasProfessionalPlot) {
            if (!companyManager.validateCompanyOwnership(buyer, null)) {
                // Le message d'erreur est déjà envoyé par validateCompanyOwnership
                return false;
            }

            buyerCompany = companyManager.getPlayerCompany(buyer);
            if (buyerCompany == null) {
                buyer.sendMessage(ChatColor.RED + "Erreur: Entreprise introuvable.");
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
            // Vérifier si l'ancien propriétaire avait une entreprise
            String oldCompanySiret = null;
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
                // Ancien terrain PRO - argent va à l'ancienne entreprise
                EntrepriseManagerLogic.Entreprise previousCompany = companyManager.getCompanyBySiret(oldCompanySiret);
                if (previousCompany != null) {
                    previousCompany.setSolde(previousCompany.getSolde() + price);
                    OfflinePlayer previousOwner = Bukkit.getOfflinePlayer(group.getOwnerUuid());
                    if (previousOwner.isOnline() && previousOwner.getPlayer() != null) {
                        previousOwner.getPlayer().sendMessage(ChatColor.GREEN + "Votre groupe de parcelles a été vendu pour " + price + "€");
                        previousOwner.getPlayer().sendMessage(ChatColor.YELLOW + "L'argent a été versé à l'entreprise " + previousCompany.getNom());
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
                }
            }
        } else {
            town.deposit(price);
        }

        // Transférer la propriété du groupe
        group.setOwner(buyer.getUniqueId(), buyer.getName());
        group.setForSale(false);

        // Transférer toutes les parcelles individuelles + companySiret si nécessaire
        for (String plotKey : group.getPlotKeys()) {
            String[] parts = plotKey.split(":");
            if (parts.length == 3) {
                String worldName = parts[0];
                int chunkX = Integer.parseInt(parts[1]);
                int chunkZ = Integer.parseInt(parts[2]);

                Plot plot = town.getPlot(worldName, chunkX, chunkZ);
                if (plot != null) {
                    plot.setOwner(buyer.getUniqueId(), buyer.getName());
                    plot.setForSale(false);

                    // Si terrain PROFESSIONNEL, enregistrer l'entreprise
                    if (plot.getType() == PlotType.PROFESSIONNEL && buyerCompany != null) {
                        plot.setCompany(buyerCompany.getNom());
                        plot.setCompanySiret(buyerCompany.getSiret());
                        plot.resetDebt(); // Réinitialiser la dette si existante
                    } else {
                        plot.setCompany(null);
                        plot.setCompanySiret(null);
                    }
                }
            }
        }

        // Messages personnalisés selon le type
        if (hasProfessionalPlot && buyerCompany != null) {
            buyer.sendMessage(ChatColor.GREEN + "✓ Groupe de parcelles PROFESSIONNEL acheté avec succès !");
            buyer.sendMessage(ChatColor.YELLOW + "Entreprise: " + ChatColor.WHITE + buyerCompany.getNom());
            buyer.sendMessage(ChatColor.YELLOW + "Parcelles: " + ChatColor.GOLD + group.getPlotCount());
            buyer.sendMessage(ChatColor.YELLOW + "Prix payé: " + ChatColor.GOLD + String.format("%.2f€", price));
            buyer.sendMessage(ChatColor.YELLOW + "Solde entreprise restant: " + ChatColor.GOLD + String.format("%.2f€", buyerCompany.getSolde()));
            buyer.sendMessage(ChatColor.GRAY + "Les taxes seront prélevées du solde de l'entreprise.");
        } else {
            buyer.sendMessage(ChatColor.GREEN + "Groupe de parcelles acheté avec succès !");
            buyer.sendMessage(ChatColor.YELLOW + "Parcelles: " + ChatColor.GOLD + group.getPlotCount());
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

        // Scanner et protéger tous les blocs de toutes les parcelles du groupe
        for (String plotKey : group.getPlotKeys()) {
            String[] parts = plotKey.split(":");
            if (parts.length == 3) {
                String worldName = parts[0];
                int chunkX = Integer.parseInt(parts[1]);
                int chunkZ = Integer.parseInt(parts[2]);

                Plot plot = town.getPlot(worldName, chunkX, chunkZ);
                if (plot != null) {
                    Chunk chunk = owner.getWorld().getChunkAt(chunkX, chunkZ);
                    plot.scanAndProtectExistingBlocks(chunk);
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

        int actualDays = Math.min(days, 30);
        double totalCost = group.getRentPricePerDay() * actualDays;

        // Vérifier que le joueur a assez d'argent
        if (!RoleplayCity.getEconomy().has(renter, totalCost)) {
            renter.sendMessage(ChatColor.RED + "Vous n'avez pas assez d'argent. Prix: " + totalCost + "€");
            return false;
        }

        // Prélever l'argent
        RoleplayCity.getEconomy().withdrawPlayer(renter, totalCost);

        // Donner l'argent au propriétaire ou à la banque
        if (group.getOwnerUuid() != null) {
            // Verser l'argent au propriétaire même s'il est hors ligne
            OfflinePlayer owner = Bukkit.getOfflinePlayer(group.getOwnerUuid());
            RoleplayCity.getEconomy().depositPlayer(owner, totalCost);

            // Notifier si le propriétaire est en ligne
            if (owner.isOnline() && owner.getPlayer() != null) {
                owner.getPlayer().sendMessage(ChatColor.GREEN + "Votre groupe de parcelles a été loué pour " + actualDays + " jours: +" + totalCost + "€");
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
                }
            }
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

        // Vérifier que le joueur a assez d'argent
        if (!RoleplayCity.getEconomy().has(renter, totalCost)) {
            renter.sendMessage(ChatColor.RED + "Vous n'avez pas assez d'argent. Prix: " + totalCost + "€");
            return false;
        }

        // Prélever l'argent
        RoleplayCity.getEconomy().withdrawPlayer(renter, totalCost);

        // Donner l'argent au propriétaire ou à la banque
        if (group.getOwnerUuid() != null) {
            // Verser l'argent au propriétaire même s'il est hors ligne
            OfflinePlayer owner = Bukkit.getOfflinePlayer(group.getOwnerUuid());
            RoleplayCity.getEconomy().depositPlayer(owner, totalCost);

            // Notifier si le propriétaire est en ligne
            if (owner.isOnline() && owner.getPlayer() != null) {
                owner.getPlayer().sendMessage(ChatColor.GREEN + "Location rechargée: +" + totalCost + "€");
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

                    plugin.getLogger().info("Location de groupe expirée: " + group.getGroupName() + " dans " + townName);
                }
            }
        }
    }

    // === RÉSULTAT DE COLLECTE ===

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
