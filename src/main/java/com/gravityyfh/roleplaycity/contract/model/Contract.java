package com.gravityyfh.roleplaycity.contract.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Représente un contrat entre une entreprise et un particulier (B2C) ou entre deux entreprises (B2B)
 */
public class Contract {

    private final UUID id;
    private final String serviceId; // Optionnel, peut être null si contrat personnalisé

    // Fournisseur
    private final String providerCompany;
    private final UUID providerOwnerUuid; // UUID du gérant qui crée le contrat

    // Client
    private final ContractType type;
    private final UUID clientUuid; // Si B2C (particulier)
    private final String clientCompany; // Si B2B (entreprise)
    private final UUID clientOwnerUuid; // Si B2B, UUID du gérant de l'entreprise cliente

    // Détails
    private final String title;
    private final String description;
    private final double amount;

    // État
    private ContractStatus status;
    private boolean fundsEscrowed; // True si l'argent est bloqué en escrow

    // Dates
    private final LocalDateTime proposalDate;
    private final LocalDateTime expirationDate;
    private LocalDateTime responseDate; // Acceptation/Rejet
    private LocalDateTime endDate; // Terminé/Résolu

    // Litige
    private UUID judgeUuid;
    private String disputeReason;
    private String disputeVerdict;

    /**
     * Constructeur pour création d'un nouveau contrat B2C
     */
    public Contract(String serviceId, String providerCompany, UUID providerOwnerUuid,
                    UUID clientUuid, String title, String description, double amount,
                    int validityDays) {
        this.id = UUID.randomUUID();
        this.serviceId = serviceId;
        this.providerCompany = providerCompany;
        this.providerOwnerUuid = providerOwnerUuid;

        this.type = ContractType.B2C;
        this.clientUuid = clientUuid;
        this.clientCompany = null;
        this.clientOwnerUuid = null;

        this.title = title;
        this.description = description;
        this.amount = amount;

        this.status = ContractStatus.PROPOSE;
        this.fundsEscrowed = false;

        this.proposalDate = LocalDateTime.now();
        this.expirationDate = this.proposalDate.plusDays(validityDays);
    }

    /**
     * Constructeur pour création d'un nouveau contrat B2B
     */
    public Contract(String serviceId, String providerCompany, UUID providerOwnerUuid,
                    String clientCompany, UUID clientOwnerUuid, String title, String description,
                    double amount, int validityDays) {
        this.id = UUID.randomUUID();
        this.serviceId = serviceId;
        this.providerCompany = providerCompany;
        this.providerOwnerUuid = providerOwnerUuid;

        this.type = ContractType.B2B;
        this.clientUuid = null;
        this.clientCompany = clientCompany;
        this.clientOwnerUuid = clientOwnerUuid;

        this.title = title;
        this.description = description;
        this.amount = amount;

        this.status = ContractStatus.PROPOSE;
        this.fundsEscrowed = false;

        this.proposalDate = LocalDateTime.now();
        this.expirationDate = this.proposalDate.plusDays(validityDays);
    }

    /**
     * Constructeur de chargement (Persistence)
     */
    public Contract(UUID id, String serviceId, String providerCompany, UUID providerOwnerUuid,
                    ContractType type, UUID clientUuid, String clientCompany, UUID clientOwnerUuid,
                    String title, String description, double amount,
                    ContractStatus status, boolean fundsEscrowed,
                    LocalDateTime proposalDate, LocalDateTime expirationDate,
                    LocalDateTime responseDate, LocalDateTime endDate,
                    UUID judgeUuid, String disputeReason, String disputeVerdict) {
        this.id = id;
        this.serviceId = serviceId;
        this.providerCompany = providerCompany;
        this.providerOwnerUuid = providerOwnerUuid;
        this.type = type;
        this.clientUuid = clientUuid;
        this.clientCompany = clientCompany;
        this.clientOwnerUuid = clientOwnerUuid;
        this.title = title;
        this.description = description;
        this.amount = amount;
        this.status = status;
        this.fundsEscrowed = fundsEscrowed;
        this.proposalDate = proposalDate;
        this.expirationDate = expirationDate;
        this.responseDate = responseDate;
        this.endDate = endDate;
        this.judgeUuid = judgeUuid;
        this.disputeReason = disputeReason;
        this.disputeVerdict = disputeVerdict;
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public String getServiceId() {
        return serviceId;
    }

    public String getProviderCompany() {
        return providerCompany;
    }

    public UUID getProviderOwnerUuid() {
        return providerOwnerUuid;
    }

    public ContractType getType() {
        return type;
    }

    public UUID getClientUuid() {
        return clientUuid;
    }

    public String getClientCompany() {
        return clientCompany;
    }

    public UUID getClientOwnerUuid() {
        return clientOwnerUuid;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public double getAmount() {
        return amount;
    }

    public ContractStatus getStatus() {
        return status;
    }

    public boolean isFundsEscrowed() {
        return fundsEscrowed;
    }

    public LocalDateTime getProposalDate() {
        return proposalDate;
    }

    public LocalDateTime getExpirationDate() {
        return expirationDate;
    }

    public LocalDateTime getResponseDate() {
        return responseDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public UUID getJudgeUuid() {
        return judgeUuid;
    }

    public String getDisputeReason() {
        return disputeReason;
    }

    public String getDisputeVerdict() {
        return disputeVerdict;
    }

    // Setters & Logic
    public void setStatus(ContractStatus status) {
        this.status = status;
        LocalDateTime now = LocalDateTime.now();

        if (status == ContractStatus.ACCEPTE || status == ContractStatus.REJETE) {
            this.responseDate = now;
        } else if (status == ContractStatus.TERMINE || status == ContractStatus.RESOLU) {
            this.endDate = now;
        }
    }

    public void setFundsEscrowed(boolean fundsEscrowed) {
        this.fundsEscrowed = fundsEscrowed;
    }

    public void openDispute(String reason) {
        this.status = ContractStatus.LITIGE;
        this.disputeReason = reason;
    }

    public void resolveDispute(UUID judgeUuid, String verdict) {
        this.judgeUuid = judgeUuid;
        this.disputeVerdict = verdict;
        this.status = ContractStatus.RESOLU;
        this.endDate = LocalDateTime.now();
    }

    /**
     * @return true si le contrat est expiré
     */
    public boolean isExpired() {
        return status == ContractStatus.PROPOSE && LocalDateTime.now().isAfter(expirationDate);
    }

    /**
     * @return true si le joueur est le client du contrat
     */
    public boolean isClient(UUID playerUuid) {
        return clientUuid != null && clientUuid.equals(playerUuid);
    }

    /**
     * @return true si le joueur est le fournisseur (créateur) du contrat
     */
    public boolean isProvider(UUID playerUuid) {
        return providerOwnerUuid.equals(playerUuid);
    }

    /**
     * @return true si le joueur est impliqué dans ce contrat (client, fournisseur, ou gérant client B2B)
     */
    public boolean involvesPlayer(UUID playerUuid) {
        return providerOwnerUuid.equals(playerUuid)
            || (clientUuid != null && clientUuid.equals(playerUuid))
            || (clientOwnerUuid != null && clientOwnerUuid.equals(playerUuid));
    }

    /**
     * @return true si l'entreprise est impliquée dans ce contrat (fournisseur ou client B2B)
     */
    public boolean involvesCompany(String companyName) {
        return providerCompany.equalsIgnoreCase(companyName)
            || (clientCompany != null && clientCompany.equalsIgnoreCase(companyName));
    }

    /**
     * @return le nom du client (entreprise B2B ou nom du joueur B2C)
     */
    public String getClientDisplayName(org.bukkit.Server server) {
        if (type == ContractType.B2B) {
            return clientCompany;
        } else {
            org.bukkit.OfflinePlayer player = server.getOfflinePlayer(clientUuid);
            return player.getName() != null ? player.getName() : "Joueur Inconnu";
        }
    }
}
