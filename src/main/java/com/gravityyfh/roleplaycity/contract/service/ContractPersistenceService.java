package com.gravityyfh.roleplaycity.contract.service;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.contract.model.Contract;
import com.gravityyfh.roleplaycity.contract.model.ContractStatus;
import com.gravityyfh.roleplaycity.contract.model.ContractType;
import com.gravityyfh.roleplaycity.database.ConnectionManager;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Service de persistence pour les contrats
 * Gère la sauvegarde et le chargement des contrats depuis SQLite
 */
public class ContractPersistenceService {

    private final RoleplayCity plugin;
    private final ConnectionManager connectionManager;
    private final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public ContractPersistenceService(RoleplayCity plugin) {
        this.plugin = plugin;
        this.connectionManager = plugin.getConnectionManager();
        ensureTableExists();
    }

    /**
     * Crée la table contracts si elle n'existe pas
     */
    private void ensureTableExists() {
        try (Connection conn = connectionManager.getRawConnection();
             Statement stmt = conn.createStatement()) {

            // Vérifier si la table existe
            ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='contracts'");
            boolean tableExists = rs.next();
            rs.close();

            if (!tableExists) {
                plugin.getLogger().info("[Contracts] Table contracts non trouvée, création...");

                String createTableSQL = "CREATE TABLE contracts (" +
                    "id VARCHAR(36) PRIMARY KEY, " +
                    "service_id VARCHAR(255), " +
                    "provider_company VARCHAR(255) NOT NULL, " +
                    "provider_owner_uuid VARCHAR(36) NOT NULL, " +
                    "contract_type VARCHAR(10) NOT NULL, " +
                    "client_uuid VARCHAR(36), " +
                    "client_company VARCHAR(255), " +
                    "client_owner_uuid VARCHAR(36), " +
                    "title VARCHAR(255) NOT NULL, " +
                    "description TEXT, " +
                    "amount DOUBLE NOT NULL, " +
                    "status VARCHAR(50) NOT NULL, " +
                    "funds_escrowed BOOLEAN DEFAULT 0, " +
                    "proposal_date VARCHAR(30) NOT NULL, " +
                    "expiration_date VARCHAR(30) NOT NULL, " +
                    "response_date VARCHAR(30), " +
                    "end_date VARCHAR(30), " +
                    "judge_uuid VARCHAR(36), " +
                    "dispute_reason TEXT, " +
                    "dispute_verdict TEXT)";

                stmt.execute(createTableSQL);

                // Créer les index
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_contracts_provider ON contracts(provider_company)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_contracts_client_uuid ON contracts(client_uuid)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_contracts_client_company ON contracts(client_company)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_contracts_status ON contracts(status)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_contracts_type ON contracts(contract_type)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_contracts_provider_owner ON contracts(provider_owner_uuid)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_contracts_client_owner ON contracts(client_owner_uuid)");

                plugin.getLogger().info("[Contracts] Table contracts créée avec succès");
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Contracts] Erreur lors de la vérification/création de la table contracts", e);
        }
    }

    /**
     * Sauvegarde un contrat dans la base de données
     */
    public void saveContract(Contract contract) {
        String sql = "REPLACE INTO contracts (" +
                "id, service_id, provider_company, provider_owner_uuid, contract_type, " +
                "client_uuid, client_company, client_owner_uuid, title, description, amount, " +
                "status, funds_escrowed, proposal_date, expiration_date, response_date, end_date, " +
                "judge_uuid, dispute_reason, dispute_verdict) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        plugin.getLogger().info("[Contracts] Sauvegarde du contrat " + contract.getId() + " (Statut: " + contract.getStatus() + ")...");

        try {
            connectionManager.executeTransaction(conn -> {
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, contract.getId().toString());
                    pstmt.setString(2, contract.getServiceId());
                    pstmt.setString(3, contract.getProviderCompany());
                    pstmt.setString(4, contract.getProviderOwnerUuid().toString());
                    pstmt.setString(5, contract.getType().name());
                    pstmt.setString(6, contract.getClientUuid() != null ? contract.getClientUuid().toString() : null);
                    pstmt.setString(7, contract.getClientCompany());
                    pstmt.setString(8, contract.getClientOwnerUuid() != null ? contract.getClientOwnerUuid().toString() : null);
                    pstmt.setString(9, contract.getTitle());
                    pstmt.setString(10, contract.getDescription());
                    pstmt.setDouble(11, contract.getAmount());
                    pstmt.setString(12, contract.getStatus().name());
                    pstmt.setBoolean(13, contract.isFundsEscrowed());
                    pstmt.setString(14, contract.getProposalDate().format(formatter));
                    pstmt.setString(15, contract.getExpirationDate().format(formatter));
                    pstmt.setString(16, contract.getResponseDate() != null ? contract.getResponseDate().format(formatter) : null);
                    pstmt.setString(17, contract.getEndDate() != null ? contract.getEndDate().format(formatter) : null);
                    pstmt.setString(18, contract.getJudgeUuid() != null ? contract.getJudgeUuid().toString() : null);
                    pstmt.setString(19, contract.getDisputeReason());
                    pstmt.setString(20, contract.getDisputeVerdict());

                    int rows = pstmt.executeUpdate();
                    plugin.getLogger().info("[Contracts] Contrat " + contract.getId() + " sauvegardé avec succès (" + rows + " lignes).");
                }
            });
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la sauvegarde du contrat " + contract.getId(), e);
        }
    }

    /**
     * Charge tous les contrats depuis la base de données
     */
    public List<Contract> loadAllContracts() {
        List<Contract> contracts = new ArrayList<>();
        String sql = "SELECT * FROM contracts";

        try (Connection conn = connectionManager.getRawConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                try {
                    Contract contract = loadContractFromResultSet(rs);
                    contracts.add(contract);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Erreur lors du chargement d'un contrat", e);
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors du chargement des contrats", e);
        }

        return contracts;
    }

    /**
     * Charge un contrat par son ID
     */
    public Contract loadContract(UUID contractId) {
        String sql = "SELECT * FROM contracts WHERE id = ?";

        try (Connection conn = connectionManager.getRawConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, contractId.toString());
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return loadContractFromResultSet(rs);
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors du chargement du contrat " + contractId, e);
        }

        return null;
    }

    /**
     * Charge les contrats d'une entreprise (fournisseur ou client)
     */
    public List<Contract> loadContractsByCompany(String companyName) {
        List<Contract> contracts = new ArrayList<>();
        String sql = "SELECT * FROM contracts WHERE provider_company = ? OR client_company = ?";

        try (Connection conn = connectionManager.getRawConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, companyName);
            pstmt.setString(2, companyName);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                try {
                    Contract contract = loadContractFromResultSet(rs);
                    contracts.add(contract);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Erreur lors du chargement d'un contrat", e);
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors du chargement des contrats de l'entreprise " + companyName, e);
        }

        return contracts;
    }

    /**
     * Charge les contrats d'un joueur (client particulier, gérant fournisseur, ou gérant client)
     */
    public List<Contract> loadContractsByPlayer(UUID playerUuid) {
        List<Contract> contracts = new ArrayList<>();
        String sql = "SELECT * FROM contracts WHERE client_uuid = ? OR provider_owner_uuid = ? OR client_owner_uuid = ?";

        try (Connection conn = connectionManager.getRawConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            String uuid = playerUuid.toString();
            pstmt.setString(1, uuid);
            pstmt.setString(2, uuid);
            pstmt.setString(3, uuid);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                try {
                    Contract contract = loadContractFromResultSet(rs);
                    contracts.add(contract);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Erreur lors du chargement d'un contrat", e);
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors du chargement des contrats du joueur " + playerUuid, e);
        }

        return contracts;
    }

    /**
     * Supprime un contrat
     */
    public void deleteContract(UUID contractId) {
        String sql = "DELETE FROM contracts WHERE id = ?";

        try {
            connectionManager.executeTransaction(conn -> {
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, contractId.toString());
                    pstmt.executeUpdate();
                    plugin.getLogger().info("[Contracts] Contrat " + contractId + " supprimé.");
                }
            });
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la suppression du contrat " + contractId, e);
        }
    }

    /**
     * Charge un contrat depuis un ResultSet
     */
    private Contract loadContractFromResultSet(ResultSet rs) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        String serviceId = rs.getString("service_id");
        String providerCompany = rs.getString("provider_company");
        UUID providerOwnerUuid = UUID.fromString(rs.getString("provider_owner_uuid"));
        ContractType type = ContractType.valueOf(rs.getString("contract_type"));

        String clientUuidStr = rs.getString("client_uuid");
        UUID clientUuid = clientUuidStr != null ? UUID.fromString(clientUuidStr) : null;

        String clientCompany = rs.getString("client_company");

        String clientOwnerUuidStr = rs.getString("client_owner_uuid");
        UUID clientOwnerUuid = clientOwnerUuidStr != null ? UUID.fromString(clientOwnerUuidStr) : null;

        String title = rs.getString("title");
        String description = rs.getString("description");
        double amount = rs.getDouble("amount");
        ContractStatus status = ContractStatus.valueOf(rs.getString("status"));
        boolean fundsEscrowed = rs.getBoolean("funds_escrowed");

        LocalDateTime proposalDate = LocalDateTime.parse(rs.getString("proposal_date"), formatter);
        LocalDateTime expirationDate = LocalDateTime.parse(rs.getString("expiration_date"), formatter);

        String responseDateStr = rs.getString("response_date");
        LocalDateTime responseDate = responseDateStr != null ? LocalDateTime.parse(responseDateStr, formatter) : null;

        String endDateStr = rs.getString("end_date");
        LocalDateTime endDate = endDateStr != null ? LocalDateTime.parse(endDateStr, formatter) : null;

        String judgeUuidStr = rs.getString("judge_uuid");
        UUID judgeUuid = judgeUuidStr != null ? UUID.fromString(judgeUuidStr) : null;

        String disputeReason = rs.getString("dispute_reason");
        String disputeVerdict = rs.getString("dispute_verdict");

        return new Contract(id, serviceId, providerCompany, providerOwnerUuid, type,
                clientUuid, clientCompany, clientOwnerUuid, title, description, amount,
                status, fundsEscrowed, proposalDate, expirationDate, responseDate, endDate,
                judgeUuid, disputeReason, disputeVerdict);
    }
}