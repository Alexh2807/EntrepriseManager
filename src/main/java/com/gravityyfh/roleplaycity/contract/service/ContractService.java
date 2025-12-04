package com.gravityyfh.roleplaycity.contract.service;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.contract.model.Contract;
import com.gravityyfh.roleplaycity.contract.model.ContractStatus;
import com.gravityyfh.roleplaycity.contract.model.ContractType;
import com.gravityyfh.roleplaycity.entreprise.model.Entreprise;
import com.gravityyfh.roleplaycity.entreprise.model.Transaction;
import com.gravityyfh.roleplaycity.entreprise.model.TransactionType;
import com.gravityyfh.roleplaycity.town.manager.NotificationManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service principal de gestion des contrats
 * Gère toute la logique métier des contrats (création, acceptation, complétion, litiges)
 */
public class ContractService {

    private final RoleplayCity plugin;
    private final ContractPersistenceService persistenceService;
    private final Map<UUID, Contract> contracts = new ConcurrentHashMap<>();

    public ContractService(RoleplayCity plugin) {
        this.plugin = plugin;
        this.persistenceService = new ContractPersistenceService(plugin);
        loadContracts();
        startExpirationTask();
    }

    /**
     * Charge tous les contrats depuis la base de données
     */
    private void loadContracts() {
        List<Contract> loaded = persistenceService.loadAllContracts();
        for (Contract c : loaded) {
            contracts.put(c.getId(), c);
        }
        plugin.getLogger().info("Chargé " + loaded.size() + " contrat(s).");
    }

    /**
     * Crée un nouveau contrat B2C (Entreprise -> Particulier)
     * @param providerOwnerUuid UUID du gérant qui crée le contrat (vérifié comme propriétaire)
     */
    public Contract createContractB2C(String providerCompany, UUID providerOwnerUuid,
                                      UUID clientUuid, String title, String description,
                                      double amount, int validityDays) {
        // Vérification: le créateur est bien gérant de l'entreprise
        if (!isCompanyOwner(providerOwnerUuid, providerCompany)) {
            return null;
        }

        Contract contract = new Contract(null, providerCompany, providerOwnerUuid,
                clientUuid, title, description, amount, validityDays);

        contracts.put(contract.getId(), contract);
        persistenceService.saveContract(contract);

        // Notification au client
        notifyPlayer(clientUuid, "Nouveau Contrat",
                "Vous avez reçu une proposition de contrat de " + providerCompany);

        plugin.getLogger().info("Contrat B2C créé: " + contract.getId() + " par " + providerCompany);
        return contract;
    }

    /**
     * Crée un nouveau contrat B2B (Entreprise -> Entreprise)
     * @param providerOwnerUuid UUID du gérant qui crée le contrat (vérifié comme propriétaire)
     */
    public Contract createContractB2B(String providerCompany, UUID providerOwnerUuid,
                                      String clientCompany, UUID clientOwnerUuid,
                                      String title, String description, double amount,
                                      int validityDays) {
        // Vérification: le créateur est bien gérant de l'entreprise fournisseur
        if (!isCompanyOwner(providerOwnerUuid, providerCompany)) {
            return null;
        }

        Contract contract = new Contract(null, providerCompany, providerOwnerUuid,
                clientCompany, clientOwnerUuid, title, description, amount, validityDays);

        contracts.put(contract.getId(), contract);
        persistenceService.saveContract(contract);

        // Notification au gérant de l'entreprise cliente
        notifyPlayer(clientOwnerUuid, "Nouveau Contrat B2B",
                "Votre entreprise " + clientCompany + " a reçu une proposition de contrat de " + providerCompany);

        plugin.getLogger().info("Contrat B2B créé: " + contract.getId() + " de " + providerCompany + " vers " + clientCompany);
        return contract;
    }

    /**
     * Accepte un contrat et place les fonds sous séquestre
     */
    public boolean acceptContract(UUID contractId, UUID playerUuid) {
        Contract contract = contracts.get(contractId);
        if (contract == null) {
            return false;
        }

        // Vérification du statut
        if (contract.getStatus() != ContractStatus.PROPOSE) {
            return false;
        }

        // Vérification d'expiration
        if (contract.isExpired()) {
            contract.setStatus(ContractStatus.EXPIRE);
            persistenceService.saveContract(contract);
            return false;
        }

        // Vérification des permissions
        if (!canAcceptContract(playerUuid, contract)) {
            return false;
        }

        double amount = contract.getAmount();
        boolean transactionSuccess = false;
        String errorMessage = null;

        if (contract.getType() == ContractType.B2B) {
            // Paiement Entreprise -> Escrow
            Entreprise clientEnt = plugin.getEntrepriseManagerLogic().getEntreprise(contract.getClientCompany());
            if (clientEnt != null && clientEnt.getSolde() >= amount) {
                clientEnt.retirerSolde(amount);
                clientEnt.addTransaction(new Transaction(
                        TransactionType.EXPENSE,
                        amount,
                        "Contrat (Séquestre): " + contract.getTitle(),
                        Bukkit.getOfflinePlayer(playerUuid).getName()));
                transactionSuccess = true;
            } else {
                errorMessage = "Fonds insuffisants dans l'entreprise " + contract.getClientCompany();
            }
        } else {
            // Paiement Joueur -> Escrow
            OfflinePlayer client = Bukkit.getOfflinePlayer(contract.getClientUuid());
            if (RoleplayCity.getEconomy().has(client, amount)) {
                RoleplayCity.getEconomy().withdrawPlayer(client, amount);
                transactionSuccess = true;
            } else {
                errorMessage = "Fonds insuffisants pour accepter ce contrat.";
            }
        }

        if (transactionSuccess) {
            contract.setStatus(ContractStatus.ACCEPTE);
            contract.setFundsEscrowed(true);
            persistenceService.saveContract(contract);

            // Notification au fournisseur
            notifyPlayer(contract.getProviderOwnerUuid(), "Contrat Accepté",
                    "Le contrat '" + contract.getTitle() + "' a été accepté. Fonds sécurisés: " + amount + "€");

            return true;
        } else {
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && errorMessage != null) {
                player.sendMessage(ChatColor.RED + errorMessage);
            }
            return false;
        }
    }

    /**
     * Refuse un contrat
     */
    public boolean rejectContract(UUID contractId, UUID playerUuid) {
        Contract contract = contracts.get(contractId);
        if (contract == null) {
            return false;
        }

        // Vérification du statut
        if (contract.getStatus() != ContractStatus.PROPOSE) {
            return false;
        }

        // Vérification des permissions
        if (!canAcceptContract(playerUuid, contract)) {
            return false;
        }

        contract.setStatus(ContractStatus.REJETE);
        persistenceService.saveContract(contract);

        // Notification au fournisseur
        notifyPlayer(contract.getProviderOwnerUuid(), "Contrat Refusé",
                "Le contrat '" + contract.getTitle() + "' a été refusé.");

        return true;
    }

    /**
     * Termine un contrat et libère les fonds vers le fournisseur
     */
    public boolean completeContract(UUID contractId, UUID playerUuid) {
        Contract contract = contracts.get(contractId);
        if (contract == null) {
            return false;
        }

        // Vérification du statut
        if (contract.getStatus() != ContractStatus.ACCEPTE) {
            return false;
        }

        // Vérification: seul le gérant fournisseur peut terminer
        if (!contract.isProvider(playerUuid)) {
            return false;
        }

        if (contract.isFundsEscrowed()) {
            double amount = contract.getAmount();
            Entreprise supplier = plugin.getEntrepriseManagerLogic().getEntreprise(contract.getProviderCompany());

            if (supplier != null) {
                supplier.ajouterSolde(amount);
                supplier.addTransaction(new Transaction(
                        TransactionType.REVENUE,
                        amount,
                        "Contrat (Libération): " + contract.getTitle(),
                        "Système"));

                contract.setFundsEscrowed(false);
                contract.setStatus(ContractStatus.TERMINE);
                persistenceService.saveContract(contract);

                notifyPlayer(contract.getProviderOwnerUuid(), "Contrat Terminé",
                        "Fonds libérés: " + amount + "€");

                // Notifier le client
                if (contract.getType() == ContractType.B2B) {
                    notifyPlayer(contract.getClientOwnerUuid(), "Contrat Terminé",
                            "Le contrat '" + contract.getTitle() + "' a été marqué comme terminé.");
                } else {
                    notifyPlayer(contract.getClientUuid(), "Contrat Terminé",
                            "Le contrat '" + contract.getTitle() + "' a été marqué comme terminé.");
                }

                return true;
            }
        }

        return false;
    }

    /**
     * Ouvre un litige sur un contrat
     */
    public boolean disputeContract(UUID contractId, UUID playerUuid, String reason) {
        Contract contract = contracts.get(contractId);
        if (contract == null) {
            return false;
        }

        // Vérification du statut
        if (contract.getStatus() != ContractStatus.ACCEPTE) {
            return false;
        }

        // Vérification: seul le client peut ouvrir un litige
        if (!isClient(playerUuid, contract)) {
            return false;
        }

        contract.openDispute(reason);
        persistenceService.saveContract(contract);

        // Notification au fournisseur
        notifyPlayer(contract.getProviderOwnerUuid(), "Litige Ouvert",
                "Un litige a été ouvert sur le contrat '" + contract.getTitle() + "'");

        plugin.getLogger().info("Litige ouvert sur contrat " + contractId + " par " + playerUuid);
        return true;
    }

    /**
     * Résout un litige (Juge uniquement)
     */
    public boolean resolveDispute(UUID contractId, UUID judgeUuid, boolean validDispute, String verdict) {
        Contract contract = contracts.get(contractId);
        if (contract == null) {
            return false;
        }

        // Vérification du statut
        if (contract.getStatus() != ContractStatus.LITIGE) {
            return false;
        }

        contract.resolveDispute(judgeUuid, verdict);
        double amount = contract.getAmount();

        if (validDispute) {
            // Litige validé -> Remboursement du client
            if (contract.getType() == ContractType.B2B) {
                Entreprise clientEnt = plugin.getEntrepriseManagerLogic().getEntreprise(contract.getClientCompany());
                if (clientEnt != null) {
                    clientEnt.ajouterSolde(amount);
                    clientEnt.addTransaction(new Transaction(
                            TransactionType.REVENUE,
                            amount,
                            "Remboursement Litige: " + contract.getTitle(),
                            "Juge"));
                }
            } else {
                RoleplayCity.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(contract.getClientUuid()), amount);
            }

            notifyPlayer(contract.getProviderOwnerUuid(), "Litige Résolu",
                    "Litige validé. Fonds remboursés au client.");
        } else {
            // Litige rejeté -> Paiement du fournisseur
            Entreprise supplier = plugin.getEntrepriseManagerLogic().getEntreprise(contract.getProviderCompany());
            if (supplier != null) {
                supplier.ajouterSolde(amount);
                supplier.addTransaction(new Transaction(
                        TransactionType.REVENUE,
                        amount,
                        "Litige Gagné: " + contract.getTitle(),
                        "Juge"));
            }

            notifyPlayer(contract.getProviderOwnerUuid(), "Litige Résolu",
                    "Litige rejeté. Fonds libérés.");
        }

        contract.setFundsEscrowed(false);
        persistenceService.saveContract(contract);

        return true;
    }

    // ===== REQUÊTES ET FILTRES =====

    /**
     * Récupère un contrat par son ID
     */
    public Contract getContract(UUID contractId) {
        return contracts.get(contractId);
    }

    /**
     * Récupère tous les contrats d'une entreprise (fournisseur OU client)
     */
    public List<Contract> getContractsByCompany(String companyName) {
        return contracts.values().stream()
                .filter(c -> c.involvesCompany(companyName))
                .collect(Collectors.toList());
    }

    /**
     * Récupère les contrats REÇUS par une entreprise (où elle est cliente)
     */
    public List<Contract> getReceivedContractsByCompany(String companyName) {
        return contracts.values().stream()
                .filter(c -> {
                    if (c.getType() == ContractType.B2B) {
                        return companyName.equalsIgnoreCase(c.getClientCompany());
                    }
                    return false;
                })
                .collect(Collectors.toList());
    }

    /**
     * Récupère les contrats ENVOYÉS par une entreprise (où elle est fournisseur)
     */
    public List<Contract> getSentContractsByCompany(String companyName) {
        return contracts.values().stream()
                .filter(c -> companyName.equalsIgnoreCase(c.getProviderCompany()))
                .collect(Collectors.toList());
    }

    /**
     * Récupère les contrats actifs d'une entreprise
     */
    public List<Contract> getActiveContractsByCompany(String companyName) {
        return contracts.values().stream()
                .filter(c -> c.involvesCompany(companyName) && c.getStatus().isActive())
                .collect(Collectors.toList());
    }

    /**
     * Récupère l'historique des contrats d'une entreprise
     */
    public List<Contract> getHistoryContractsByCompany(String companyName) {
        return contracts.values().stream()
                .filter(c -> c.involvesCompany(companyName) && c.getStatus().isHistorical())
                .collect(Collectors.toList());
    }

    /**
     * Récupère tous les contrats d'un joueur (client particulier, gérant fournisseur, ou gérant client)
     */
    public List<Contract> getContractsByPlayer(UUID playerUuid) {
        return contracts.values().stream()
                .filter(c -> c.involvesPlayer(playerUuid))
                .collect(Collectors.toList());
    }

    /**
     * Récupère tous les contrats en litige
     */
    public List<Contract> getDisputedContracts() {
        return contracts.values().stream()
                .filter(c -> c.getStatus() == ContractStatus.LITIGE)
                .collect(Collectors.toList());
    }

    // ===== VÉRIFICATIONS DE PERMISSIONS =====

    /**
     * Vérifie si un joueur est le propriétaire d'une entreprise
     */
    private boolean isCompanyOwner(UUID playerUuid, String companyName) {
        Entreprise entreprise = plugin.getEntrepriseManagerLogic().getEntreprise(companyName);
        if (entreprise == null) {
            return false;
        }
        return UUID.fromString(entreprise.getGerantUUID()).equals(playerUuid);
    }

    /**
     * Vérifie si un joueur peut accepter un contrat
     */
    private boolean canAcceptContract(UUID playerUuid, Contract contract) {
        if (contract.getType() == ContractType.B2B) {
            // Pour B2B, le joueur doit être le gérant de l'entreprise cliente
            return contract.getClientOwnerUuid() != null
                    && contract.getClientOwnerUuid().equals(playerUuid);
        } else {
            // Pour B2C, le joueur doit être le client
            return contract.getClientUuid() != null
                    && contract.getClientUuid().equals(playerUuid);
        }
    }

    /**
     * Vérifie si un joueur est le client d'un contrat
     */
    private boolean isClient(UUID playerUuid, Contract contract) {
        if (contract.getType() == ContractType.B2B) {
            return contract.getClientOwnerUuid() != null
                    && contract.getClientOwnerUuid().equals(playerUuid);
        } else {
            return contract.getClientUuid() != null
                    && contract.getClientUuid().equals(playerUuid);
        }
    }

    // ===== TÂCHE D'EXPIRATION =====

    /**
     * Démarre la tâche périodique qui marque les contrats expirés
     */
    private void startExpirationTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                int expired = 0;
                for (Contract c : contracts.values()) {
                    if (c.isExpired() && c.getStatus() == ContractStatus.PROPOSE) {
                        c.setStatus(ContractStatus.EXPIRE);
                        persistenceService.saveContract(c);
                        expired++;
                    }
                }
                if (expired > 0) {
                    plugin.getLogger().info(expired + " contrat(s) expiré(s).");
                }
            }
        }.runTaskTimer(plugin, 20L * 60 * 60, 20L * 60 * 60); // Vérifier toutes les heures
    }

    /**
     * Envoie une notification à un joueur
     */
    private void notifyPlayer(UUID uuid, String title, String message) {
        if (uuid == null) return;
        if (plugin.getNotificationManager() != null) {
            plugin.getNotificationManager().sendNotification(
                    uuid,
                    NotificationManager.NotificationType.INFO,
                    title,
                    message);
        }
    }
}
