package com.gravityyfh.roleplaycity.entreprise.persistence;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.database.ConnectionManager;
import com.gravityyfh.roleplaycity.entreprise.model.*;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Level;

/**
 * Implémentation SQLite du repository des entreprises.
 * Gère la persistence complète des entreprises avec toutes leurs relations.
 */
public class SQLiteEntrepriseRepository implements EntrepriseRepository {

    private final RoleplayCity plugin;
    private final ConnectionManager connectionManager;

    public SQLiteEntrepriseRepository(RoleplayCity plugin, ConnectionManager connectionManager) {
        this.plugin = plugin;
        this.connectionManager = connectionManager;
    }

    @Override
    public boolean save(Entreprise entreprise) {
        try {
            connectionManager.executeTransaction(conn -> {
                // 1. Sauvegarder l'entreprise elle-même
                saveEntrepriseCore(conn, entreprise);

                // 2. Sauvegarder les employés
                saveEmployees(conn, entreprise);

                // 3. Sauvegarder les transactions
                saveTransactions(conn, entreprise);

                // 4. Sauvegarder les activités employés
                saveEmployeeActivities(conn, entreprise);

                // 5. Sauvegarder la production globale
                saveGlobalProduction(conn, entreprise);
            });
            return true;

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur sauvegarde entreprise: " + entreprise.getNom(), e);
            return false;
        }
    }

    private void saveEntrepriseCore(Connection conn, Entreprise entreprise) throws SQLException {
        String sql = """
            INSERT INTO entreprises (
                siret, nom, ville, type, gerant_nom, gerant_uuid,
                solde, chiffre_affaires_total,
                niveau_max_employes, niveau_max_solde, niveau_restrictions,
                total_value, creation_date, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(siret) DO UPDATE SET
                nom = excluded.nom,
                gerant_nom = excluded.gerant_nom,
                gerant_uuid = excluded.gerant_uuid,
                solde = excluded.solde,
                chiffre_affaires_total = excluded.chiffre_affaires_total,
                niveau_max_employes = excluded.niveau_max_employes,
                niveau_max_solde = excluded.niveau_max_solde,
                niveau_restrictions = excluded.niveau_restrictions,
                total_value = excluded.total_value,
                updated_at = excluded.updated_at
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, entreprise.getSiret());
            stmt.setString(2, entreprise.getNom());
            stmt.setString(3, entreprise.getVille());
            stmt.setString(4, entreprise.getType());
            stmt.setString(5, entreprise.getGerant());
            stmt.setString(6, entreprise.getGerantUUID());
            stmt.setDouble(7, entreprise.getSolde());
            stmt.setDouble(8, entreprise.getChiffreAffairesTotal());
            stmt.setInt(9, entreprise.getNiveauMaxEmployes());
            stmt.setInt(10, entreprise.getNiveauMaxSolde());
            stmt.setInt(11, entreprise.getNiveauRestrictions());
            stmt.setDouble(12, entreprise.getTotalValue());

            // Gestion de creation_date (NOT NULL)
            String creationDate = entreprise.getCreationDate();
            if (creationDate == null) {
                creationDate = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
            stmt.setString(13, creationDate);

            stmt.setLong(14, System.currentTimeMillis());
            stmt.setLong(15, System.currentTimeMillis());
            stmt.executeUpdate();
        }
    }

    private void saveEmployees(Connection conn, Entreprise entreprise) throws SQLException {
        // Récupérer la liste des UUIDs actuels des employés (depuis la mémoire)
        Set<String> currentEmployeeUuids = new HashSet<>();
        for (String employeeName : entreprise.getEmployes()) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(employeeName);
            if (player != null) {
                currentEmployeeUuids.add(player.getUniqueId().toString());
            }
        }

        // ÉTAPE 1: Supprimer les employee_activities des employés qui ne sont PLUS dans l'entreprise
        // On doit le faire AVANT de supprimer les employés car sinon ON DELETE CASCADE les supprime
        // et on ne peut plus les filtrer correctement
        if (!currentEmployeeUuids.isEmpty()) {
            // Construire la clause IN pour les UUIDs actuels
            StringBuilder inClause = new StringBuilder();
            for (int i = 0; i < currentEmployeeUuids.size(); i++) {
                if (i > 0) inClause.append(",");
                inClause.append("?");
            }

            String deleteOrphanActivitiesSql =
                "DELETE FROM employee_activities WHERE entreprise_siret = ? AND employee_uuid NOT IN (" + inClause + ")";

            try (PreparedStatement stmt = conn.prepareStatement(deleteOrphanActivitiesSql)) {
                stmt.setString(1, entreprise.getSiret());
                int paramIndex = 2;
                for (String uuid : currentEmployeeUuids) {
                    stmt.setString(paramIndex++, uuid);
                }
                stmt.executeUpdate();
            }
        } else {
            // Si aucun employé actuel, supprimer TOUTES les activities de cette entreprise
            String deleteAllActivitiesSql = "DELETE FROM employee_activities WHERE entreprise_siret = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deleteAllActivitiesSql)) {
                stmt.setString(1, entreprise.getSiret());
                stmt.executeUpdate();
            }
        }

        // ÉTAPE 2: Supprimer TOUS les employés de cette entreprise (on va les recréer)
        String deleteSql = "DELETE FROM employes WHERE entreprise_siret = ?";
        try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
            stmt.setString(1, entreprise.getSiret());
            stmt.executeUpdate();
        }

        // ÉTAPE 3: Recréer les employés actuels
        String insertSql = """
            INSERT INTO employes (entreprise_siret, employe_nom, employe_uuid, prime, join_date)
            VALUES (?, ?, ?, ?, ?)
            """;

        try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            for (String employeeName : entreprise.getEmployes()) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(employeeName);
                if (player == null) continue;

                stmt.setString(1, entreprise.getSiret());
                stmt.setString(2, employeeName);
                stmt.setString(3, player.getUniqueId().toString());
                stmt.setDouble(4, entreprise.getPrimePourEmploye(player.getUniqueId().toString()));
                stmt.setLong(5, System.currentTimeMillis());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private void saveTransactions(Connection conn, Entreprise entreprise) throws SQLException {
        String deleteSql = "DELETE FROM transactions WHERE entreprise_siret = ?";
        try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
            stmt.setString(1, entreprise.getSiret());
            stmt.executeUpdate();
        }

        List<Transaction> transactions = entreprise.getTransactionLog();
        if (transactions == null || transactions.isEmpty()) return;

        List<Transaction> toSave = transactions.stream()
            .sorted((a, b) -> b.timestamp.compareTo(a.timestamp))
            .limit(200)
            .toList();

        String insertSql = """
            INSERT INTO transactions (entreprise_siret, type, amount, description, initiated_by, timestamp)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            for (Transaction tx : toSave) {
                stmt.setString(1, entreprise.getSiret());
                stmt.setString(2, tx.type.name());
                stmt.setDouble(3, tx.amount);
                stmt.setString(4, tx.description);
                stmt.setString(5, tx.initiatedBy);
                stmt.setLong(6, Timestamp.valueOf(tx.timestamp).getTime());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private void saveEmployeeActivities(Connection conn, Entreprise entreprise) throws SQLException {
        Map<UUID, EmployeeActivityRecord> activities = entreprise.getEmployeeActivityRecords();
        if (activities == null || activities.isEmpty()) return;

        // IMPORTANT: Ne sauvegarder QUE les activités des employés ACTUELS de l'entreprise
        // Les anciens employés ont été supprimés de la table employes, donc leurs activités
        // ne doivent plus être sauvegardées (sinon erreur de clé étrangère)
        Set<String> currentEmployeeNames = entreprise.getEmployes();

        for (EmployeeActivityRecord record : activities.values()) {
            // Vérifier que l'employé est toujours dans l'entreprise
            if (!currentEmployeeNames.contains(record.employeeName)) {
                continue; // Skip les anciens employés
            }

            try {
                long activityId = saveEmployeeActivity(conn, entreprise.getSiret(), record);
                saveEmployeeActions(conn, activityId, record);
                saveDetailedProduction(conn, activityId, record);
            } catch (SQLException e) {
                // Log l'erreur mais continue avec les autres employés
                plugin.getLogger().warning("Erreur sauvegarde activité employé " + record.employeeName + ": " + e.getMessage());
            }
        }
    }

    private long saveEmployeeActivity(Connection conn, String siret, EmployeeActivityRecord record) throws SQLException {
        String sql = """
            INSERT INTO employee_activities (
                entreprise_siret, employee_uuid, employee_name,
                current_session_start, last_activity_time, total_value_generated, join_date
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(entreprise_siret, employee_uuid) DO UPDATE SET
                employee_name = excluded.employee_name,
                current_session_start = excluded.current_session_start,
                last_activity_time = excluded.last_activity_time,
                total_value_generated = excluded.total_value_generated
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, siret);
            stmt.setString(2, record.employeeId.toString());
            stmt.setString(3, record.employeeName);
            stmt.setObject(4, record.currentSessionStartTime != null ? Timestamp.valueOf(record.currentSessionStartTime).getTime() : null);
            stmt.setObject(5, record.lastActivityTime != null ? Timestamp.valueOf(record.lastActivityTime).getTime() : null);
            stmt.setDouble(6, record.totalValueGenerated);
            stmt.setLong(7, record.joinDate != null ? Timestamp.valueOf(record.joinDate).getTime() : System.currentTimeMillis());

            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        }

        String selectSql = "SELECT id FROM employee_activities WHERE entreprise_siret = ? AND employee_uuid = ?";
        try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
            stmt.setString(1, siret);
            stmt.setString(2, record.employeeId.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getLong("id");
            }
        }
        throw new SQLException("Impossible de récupérer l'ID de l'activité employé");
    }

    private void saveEmployeeActions(Connection conn, long activityId, EmployeeActivityRecord record) throws SQLException {
        String deleteSql = "DELETE FROM employee_actions WHERE activity_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
            stmt.setLong(1, activityId);
            stmt.executeUpdate();
        }

        if (record.actionsPerformedCount == null || record.actionsPerformedCount.isEmpty()) return;

        String insertSql = "INSERT INTO employee_actions (activity_id, action_key, count) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            for (Map.Entry<String, Long> entry : record.actionsPerformedCount.entrySet()) {
                stmt.setLong(1, activityId);
                stmt.setString(2, entry.getKey());
                stmt.setLong(3, entry.getValue());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private void saveDetailedProduction(Connection conn, long activityId, EmployeeActivityRecord record) throws SQLException {
        String deleteSql = "DELETE FROM detailed_production WHERE activity_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
            stmt.setLong(1, activityId);
            stmt.executeUpdate();
        }

        if (record.detailedProductionLog == null || record.detailedProductionLog.isEmpty()) return;

        List<DetailedProductionRecord> toSave = record.detailedProductionLog.stream()
            .sorted((a, b) -> b.timestamp().compareTo(a.timestamp()))
            .limit(1000)
            .toList();

        String insertSql = """
            INSERT INTO detailed_production (activity_id, timestamp, action_type, material, quantity)
            VALUES (?, ?, ?, ?, ?)
            """;

        try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            for (DetailedProductionRecord prod : toSave) {
                stmt.setLong(1, activityId);
                stmt.setLong(2, Timestamp.valueOf(prod.timestamp()).getTime());
                stmt.setString(3, prod.actionType().name());
                stmt.setString(4, prod.material().name());
                stmt.setInt(5, prod.quantity());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private void saveGlobalProduction(Connection conn, Entreprise entreprise) throws SQLException {
        String deleteSql = "DELETE FROM global_production WHERE entreprise_siret = ?";
        try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
            stmt.setString(1, entreprise.getSiret());
            stmt.executeUpdate();
        }

        List<ProductionRecord> production = entreprise.getGlobalProductionLog();
        if (production == null || production.isEmpty()) return;

        List<ProductionRecord> toSave = production.stream()
            .sorted((a, b) -> b.timestamp().compareTo(a.timestamp()))
            .limit(500)
            .toList();

        String insertSql = """
            INSERT INTO global_production (entreprise_siret, timestamp, material, quantity, employee_uuid, action_type)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            for (ProductionRecord prod : toSave) {
                stmt.setString(1, entreprise.getSiret());
                stmt.setLong(2, Timestamp.valueOf(prod.timestamp()).getTime());
                stmt.setString(3, prod.material().name());
                stmt.setInt(4, prod.quantity());
                stmt.setString(5, prod.recordedByEmployeeUUID());
                stmt.setString(6, prod.actionType().name());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    @Override
    public Optional<Entreprise> findBySiret(String siret) {
        try {
            return loadEntreprise("SELECT * FROM entreprises WHERE siret = ?", siret);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur chargement entreprise SIRET: " + siret, e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<Entreprise> findByNom(String nom) {
        try {
            return loadEntreprise("SELECT * FROM entreprises WHERE nom = ?", nom);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur chargement entreprise nom: " + nom, e);
            return Optional.empty();
        }
    }

    @Override
    public List<Entreprise> findByVille(String ville) {
        return findEntreprises("SELECT * FROM entreprises WHERE ville = ?", ville);
    }

    @Override
    public List<Entreprise> findByType(String type) {
        return findEntreprises("SELECT * FROM entreprises WHERE type = ?", type);
    }

    @Override
    public List<Entreprise> findByGerant(UUID gerantUuid) {
        return findEntreprises("SELECT * FROM entreprises WHERE gerant_uuid = ?", gerantUuid.toString());
    }

    @Override
    public List<Entreprise> findByMember(String playerName) {
        List<Entreprise> result = new ArrayList<>();
        String sql = """
            SELECT DISTINCT e.* FROM entreprises e
            LEFT JOIN employes emp ON e.siret = emp.entreprise_siret
            WHERE e.gerant_nom = ? OR emp.employe_nom = ?
            """;
        
        Connection conn = null;
        try {
            conn = connectionManager.getRawConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerName);
                stmt.setString(2, playerName);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        result.add(loadEntrepriseFromResultSet(conn, rs));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur chargement entreprises membre: " + playerName, e);
        } finally {
            if (conn != null) connectionManager.releaseConnection(conn);
        }
        return result;
    }

    @Override
    public List<Entreprise> findAll() {
        return findEntreprises("SELECT * FROM entreprises", null);
    }

    @Override
    public boolean delete(String siret) {
        try {
            return connectionManager.executeUpdate("DELETE FROM entreprises WHERE siret = ?", siret) > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur suppression entreprise: " + siret, e);
            return false;
        }
    }

    @Override
    public boolean exists(String siret) {
        try {
            return connectionManager.executeQuery("SELECT 1 FROM entreprises WHERE siret = ?", 
                rs -> rs.next(), siret);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur vérification existence: " + siret, e);
            return false;
        }
    }

    @Override
    public boolean nomExists(String nom) {
        try {
            return connectionManager.executeQuery("SELECT 1 FROM entreprises WHERE nom = ?", 
                rs -> rs.next(), nom);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur vérification nom: " + nom, e);
            return false;
        }
    }

    @Override
    public int count() {
        try {
            return connectionManager.executeQuery("SELECT COUNT(*) FROM entreprises", 
                rs -> rs.next() ? rs.getInt(1) : 0);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur comptage entreprises", e);
            return 0;
        }
    }

    @Override
    public int countByGerant(UUID gerantUuid) {
        try {
            return connectionManager.executeQuery("SELECT COUNT(*) FROM entreprises WHERE gerant_uuid = ?", 
                rs -> rs.next() ? rs.getInt(1) : 0, gerantUuid.toString());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur comptage entreprises gérant: " + gerantUuid, e);
            return 0;
        }
    }

    private Optional<Entreprise> loadEntreprise(String sql, String param) throws SQLException {
        Connection conn = null;
        try {
            conn = connectionManager.getRawConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, param);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(loadEntrepriseFromResultSet(conn, rs));
                    }
                }
            }
        } finally {
            if (conn != null) connectionManager.releaseConnection(conn);
        }
        return Optional.empty();
    }

    private List<Entreprise> findEntreprises(String sql, String param) {
        List<Entreprise> result = new ArrayList<>();
        Connection conn = null;
        try {
            conn = connectionManager.getRawConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (param != null) {
                    stmt.setString(1, param);
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        result.add(loadEntrepriseFromResultSet(conn, rs));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur chargement entreprises: " + sql, e);
        } finally {
            if (conn != null) connectionManager.releaseConnection(conn);
        }
        return result;
    }

    private Entreprise loadEntrepriseFromResultSet(Connection conn, ResultSet rs) throws SQLException {
        String siret = rs.getString("siret");
        Set<String> employes = loadEmployees(conn, siret);

        Entreprise entreprise = new Entreprise(
            rs.getString("nom"),
            rs.getString("ville"),
            rs.getString("type"),
            rs.getString("gerant_nom"),
            rs.getString("gerant_uuid"),
            employes,
            rs.getDouble("solde"),
            siret
        );

        entreprise.setChiffreAffairesTotal(rs.getDouble("chiffre_affaires_total"));
        entreprise.setNiveauMaxEmployes(rs.getInt("niveau_max_employes"));
        entreprise.setNiveauMaxSolde(rs.getInt("niveau_max_solde"));
        entreprise.setNiveauRestrictions(rs.getInt("niveau_restrictions"));
        entreprise.setTotalValue(rs.getDouble("total_value"));
        entreprise.setCreationDate(rs.getString("creation_date"));

        entreprise.setPrimes(loadPrimes(conn, siret));
        entreprise.setTransactionLog(loadTransactions(conn, siret));
        entreprise.setEmployeeActivityRecords(loadEmployeeActivities(conn, siret));
        entreprise.setGlobalProductionLog(loadGlobalProduction(conn, siret));

        return entreprise;
    }

    private Set<String> loadEmployees(Connection conn, String siret) throws SQLException {
        Set<String> employes = new HashSet<>();
        String sql = "SELECT employe_nom FROM employes WHERE entreprise_siret = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, siret);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    employes.add(rs.getString("employe_nom"));
                }
            }
        }
        return employes;
    }

    private Map<String, Double> loadPrimes(Connection conn, String siret) throws SQLException {
        Map<String, Double> primes = new HashMap<>();
        String sql = "SELECT employe_uuid, prime FROM employes WHERE entreprise_siret = ? AND prime > 0";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, siret);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    primes.put(rs.getString("employe_uuid"), rs.getDouble("prime"));
                }
            }
        }
        return primes;
    }

    private List<Transaction> loadTransactions(Connection conn, String siret) throws SQLException {
        List<Transaction> transactions = new ArrayList<>();
        String sql = "SELECT * FROM transactions WHERE entreprise_siret = ? ORDER BY timestamp DESC LIMIT 200";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, siret);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    transactions.add(new Transaction(
                        TransactionType.valueOf(rs.getString("type")),
                        rs.getDouble("amount"),
                        rs.getString("description"),
                        rs.getString("initiated_by"),
                        new Timestamp(rs.getLong("timestamp")).toLocalDateTime()
                    ));
                }
            }
        }
        return transactions;
    }

    private Map<UUID, EmployeeActivityRecord> loadEmployeeActivities(Connection conn, String siret) throws SQLException {
        Map<UUID, EmployeeActivityRecord> activities = new HashMap<>();
        String sql = "SELECT * FROM employee_activities WHERE entreprise_siret = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, siret);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long activityId = rs.getLong("id");
                    UUID employeeId = UUID.fromString(rs.getString("employee_uuid"));
                    EmployeeActivityRecord record = new EmployeeActivityRecord(employeeId, rs.getString("employee_name"));
                    
                    long sessionStart = rs.getLong("current_session_start");
                    if (sessionStart > 0) record.currentSessionStartTime = new Timestamp(sessionStart).toLocalDateTime();
                    
                    long lastActivity = rs.getLong("last_activity_time");
                    if (lastActivity > 0) record.lastActivityTime = new Timestamp(lastActivity).toLocalDateTime();
                    
                    record.totalValueGenerated = rs.getDouble("total_value_generated");
                    
                    long joinDate = rs.getLong("join_date");
                    if (joinDate > 0) record.joinDate = new Timestamp(joinDate).toLocalDateTime();
                    
                    record.actionsPerformedCount.putAll(loadEmployeeActions(conn, activityId));
                    record.detailedProductionLog.addAll(loadDetailedProduction(conn, activityId));
                    
                    activities.put(employeeId, record);
                }
            }
        }
        return activities;
    }

    private Map<String, Long> loadEmployeeActions(Connection conn, long activityId) throws SQLException {
        Map<String, Long> actions = new HashMap<>();
        String sql = "SELECT action_key, count FROM employee_actions WHERE activity_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, activityId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    actions.put(rs.getString("action_key"), rs.getLong("count"));
                }
            }
        }
        return actions;
    }

    private List<DetailedProductionRecord> loadDetailedProduction(Connection conn, long activityId) throws SQLException {
        List<DetailedProductionRecord> production = new ArrayList<>();
        String sql = "SELECT * FROM detailed_production WHERE activity_id = ? ORDER BY timestamp DESC LIMIT 1000";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, activityId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    production.add(new DetailedProductionRecord(
                        new Timestamp(rs.getLong("timestamp")).toLocalDateTime(),
                        DetailedActionType.valueOf(rs.getString("action_type")),
                        org.bukkit.Material.valueOf(rs.getString("material")),
                        rs.getInt("quantity")
                    ));
                }
            }
        }
        return production;
    }

    private List<ProductionRecord> loadGlobalProduction(Connection conn, String siret) throws SQLException {
        List<ProductionRecord> production = new ArrayList<>();
        String sql = "SELECT * FROM global_production WHERE entreprise_siret = ? ORDER BY timestamp DESC LIMIT 500";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, siret);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    production.add(new ProductionRecord(
                        new Timestamp(rs.getLong("timestamp")).toLocalDateTime(),
                        org.bukkit.Material.valueOf(rs.getString("material")),
                        rs.getInt("quantity"),
                        rs.getString("employee_uuid"),
                        DetailedActionType.valueOf(rs.getString("action_type"))
                    ));
                }
            }
        }
        return production;
    }
}
