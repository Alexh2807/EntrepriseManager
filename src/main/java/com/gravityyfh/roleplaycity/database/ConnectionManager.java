package com.gravityyfh.roleplaycity.database;

import com.gravityyfh.roleplaycity.RoleplayCity;
import org.bukkit.Bukkit;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Gestionnaire global de connexions SQLite pour TOUT le plugin RoleplayCity
 *
 * ARCHITECTURE UNIFIÉE (Standard JDBC):
 * - Un seul fichier: roleplaycity.db
 * - Pool de connexions pour performance
 * - Support des transactions standard
 * - Backups automatiques
 * - Thread-safe
 *
 * @author Phase 7 - SQLite Migration (Standardized)
 */
public class ConnectionManager {
    private static final String DB_NAME = "roleplaycity.db";
    private static final int POOL_SIZE = 10;
    private static final int MAX_POOL_SIZE = 20;
    private static final long KEEP_ALIVE_TIME = 60L;

    private final RoleplayCity plugin;
    private final File databaseFile;
    private final BlockingQueue<Connection> connectionPool;
    private final AtomicInteger activeConnections;
    private final ExecutorService executor;

    private volatile boolean initialized = false;

    public ConnectionManager(RoleplayCity plugin) {
        this.plugin = plugin;
        this.databaseFile = new File(plugin.getDataFolder(), DB_NAME);
        this.connectionPool = new LinkedBlockingQueue<>();
        this.activeConnections = new AtomicInteger(0);
        this.executor = new ThreadPoolExecutor(
            POOL_SIZE,
            MAX_POOL_SIZE,
            KEEP_ALIVE_TIME,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            r -> new Thread(r, "RoleplayCity-DB-Worker")
        );
    }

    /**
     * Initialise la base de données et crée le pool de connexions
     */
    public void initialize() {
        if (initialized) {
            plugin.getLogger().warning("[Database] Déjà initialisé");
            return;
        }

        try {
            // Charger le driver SQLite
            Class.forName("org.sqlite.JDBC");

            // Créer le fichier si nécessaire
            if (!databaseFile.exists()) {
                plugin.getLogger().info("[Database] Création de " + DB_NAME);
                databaseFile.getParentFile().mkdirs();
            }

            // Créer le pool de connexions
            for (int i = 0; i < POOL_SIZE; i++) {
                connectionPool.offer(createConnection());
            }

            // IMPORTANT: Mettre initialized = true AVANT createTables()
            initialized = true;
            plugin.getLogger().info("[Database] Pool initialisé");

            // Créer les tables
            createTables();

            // Patch de migration pour la table entreprises (ajout des colonnes manquantes si nécessaire)
            patchEntreprisesTable();
            
            // Patch pour la table employes (ajout colonne prime)
            patchEmployesTable();
            
            // Patch pour la table transactions (ajout amount, timestamp, initiated_by)
            patchTransactionsTable();

            // Patch pour la table shops (ajout cached_stock)
            patchShopsTable();

            // Patch pour la table contracts (création si n'existe pas)
            patchContractsTable();

            // Vérification et mise à jour automatique du schéma
            SchemaVersionManager schemaManager = new SchemaVersionManager(plugin, this);
            schemaManager.checkAndUpdateSchema();

            // Migrer depuis entreprises.db si nécessaire
            migrateFromOldDatabase();

            plugin.getLogger().info("[Database] Initialisation complète");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Database] Échec de l'initialisation", e);
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    /**
     * Crée une nouvelle connexion SQLite avec optimisations
     */
    private Connection createConnection() throws SQLException {
        String url = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
        Connection conn = DriverManager.getConnection(url);

        // Optimisations SQLite
        try (Statement stmt = conn.createStatement()) {
            // WAL mode pour meilleure concurrence
            stmt.execute("PRAGMA journal_mode=WAL");
            // Sync moins agressif (plus rapide, moins sûr)
            stmt.execute("PRAGMA synchronous=NORMAL");
            // Cache plus grand
            stmt.execute("PRAGMA cache_size=10000");
            // Foreign keys activées
            stmt.execute("PRAGMA foreign_keys=ON");
            // Temp store en mémoire
            stmt.execute("PRAGMA temp_store=MEMORY");
        }

        activeConnections.incrementAndGet();
        return conn;
    }

    /**
     * Crée toutes les tables depuis schema.sql
     */
    private void createTables() {
        plugin.getLogger().info("[Database] Création des tables...");

        try (Connection conn = getConnection();
             InputStream schemaStream = plugin.getResource("schema.sql")) {

            if (schemaStream == null) {
                plugin.getLogger().warning("[Database] schema.sql introuvable dans resources");
                return;
            }

            String schema = new String(schemaStream.readAllBytes());
            String[] statements = schema.split(";");

            try (Statement stmt = conn.createStatement()) {
                for (String sql : statements) {
                    // Nettoyer: retirer les lignes de commentaires et les commentaires inline
                    String cleanedSql = java.util.Arrays.stream(sql.split("\n"))
                        .map(line -> {
                            int commentIndex = line.indexOf("--");
                            return (commentIndex != -1) ? line.substring(0, commentIndex) : line;
                        })
                        .map(String::trim)
                        .filter(line -> !line.isEmpty())
                        .reduce((l1, l2) -> l1 + " " + l2)
                        .orElse("")
                        .trim();

                    if (!cleanedSql.isEmpty()) {
                        try {
                            stmt.execute(cleanedSql);
                        } catch (SQLException e) {
                            // Ignorer "table already exists" (normal) mais logger le reste
                            if (!e.getMessage().contains("already exists")) {
                                plugin.getLogger().severe("[Database] Erreur sur: " + (cleanedSql.length() > 100 ? cleanedSql.substring(0, 100) + "..." : cleanedSql));
                                plugin.getLogger().severe("[Database] " + e.getMessage());
                            }
                        }
                    }
                }

                // Table pour la persistance des menottes
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS handcuffed_players (
                        player_uuid TEXT PRIMARY KEY,
                        health REAL DEFAULT 1.0,
                        timestamp INTEGER
                    )
                    """);

                // Table pour la persistance des joueurs tasés
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS tased_players (
                        player_uuid TEXT PRIMARY KEY,
                        expiration_timestamp INTEGER
                    )
                    """);

                // Table pour la persistance du stock des boutiques
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS shop_stock (
                        shop_uuid TEXT,
                        item_serial TEXT NOT NULL,
                        quantity INTEGER DEFAULT 0,
                        PRIMARY KEY (shop_uuid, item_serial),
                        FOREIGN KEY (shop_uuid) REFERENCES shops(shop_uuid) ON DELETE CASCADE
                    )
                    """);

                // Table pour la persistance du mode service
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS service_sessions (
                        player_uuid TEXT PRIMARY KEY,
                        player_name TEXT,
                        entreprise_siret TEXT,
                        start_time TEXT,
                        end_time TEXT,
                        total_earned REAL DEFAULT 0.0,
                        is_active INTEGER DEFAULT 0
                    )
                    """);
            } finally {
                releaseConnection(conn);
            }

            plugin.getLogger().info("[Database] Tables vérifiées");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Database] Erreur globale création tables", e);
        }
    }

    /**
     * Patch pour ajouter les colonnes manquantes à la table entreprises
     */
    private void patchEntreprisesTable() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Récupérer les colonnes existantes
            ResultSet rs = stmt.executeQuery("PRAGMA table_info(entreprises)");
            java.util.Set<String> columns = new java.util.HashSet<>();
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
            rs.close();

            // Liste des colonnes à vérifier et ajouter
            if (!columns.contains("ville")) {
                stmt.execute("ALTER TABLE entreprises ADD COLUMN ville TEXT DEFAULT 'Inconnue'");
                plugin.getLogger().info("[Database] Colonne 'ville' ajoutée à la table entreprises");
            }
            if (!columns.contains("solde")) {
                stmt.execute("ALTER TABLE entreprises ADD COLUMN solde REAL DEFAULT 0.0");
                plugin.getLogger().info("[Database] Colonne 'solde' ajoutée à la table entreprises");
            }
            if (!columns.contains("chiffre_affaires_total")) {
                stmt.execute("ALTER TABLE entreprises ADD COLUMN chiffre_affaires_total REAL DEFAULT 0.0");
                plugin.getLogger().info("[Database] Colonne 'chiffre_affaires_total' ajoutée à la table entreprises");
            }
            if (!columns.contains("niveau_max_employes")) {
                stmt.execute("ALTER TABLE entreprises ADD COLUMN niveau_max_employes INTEGER DEFAULT 0");
                plugin.getLogger().info("[Database] Colonne 'niveau_max_employes' ajoutée à la table entreprises");
            }
            if (!columns.contains("niveau_max_solde")) {
                stmt.execute("ALTER TABLE entreprises ADD COLUMN niveau_max_solde INTEGER DEFAULT 0");
                plugin.getLogger().info("[Database] Colonne 'niveau_max_solde' ajoutée à la table entreprises");
            }
            if (!columns.contains("niveau_restrictions")) {
                stmt.execute("ALTER TABLE entreprises ADD COLUMN niveau_restrictions INTEGER DEFAULT 0");
                plugin.getLogger().info("[Database] Colonne 'niveau_restrictions' ajoutée à la table entreprises");
            }
            if (!columns.contains("created_at")) {
                stmt.execute("ALTER TABLE entreprises ADD COLUMN created_at INTEGER");
                plugin.getLogger().info("[Database] Colonne 'created_at' ajoutée à la table entreprises");
            }
            if (!columns.contains("updated_at")) {
                stmt.execute("ALTER TABLE entreprises ADD COLUMN updated_at INTEGER");
                plugin.getLogger().info("[Database] Colonne 'updated_at' ajoutée à la table entreprises");
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Database] Erreur lors du patch de la table entreprises", e);
        }
    }

    /**
     * Patch pour ajouter les colonnes manquantes à la table employes
     */
    private void patchEmployesTable() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            plugin.getLogger().info("[Database] Vérification de la table employes...");

            ResultSet rs = stmt.executeQuery("PRAGMA table_info(employes)");
            java.util.Set<String> columns = new java.util.HashSet<>();
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
            rs.close();

            // Ajouter colonne poste si manquante
            if (!columns.contains("poste")) {
                stmt.execute("ALTER TABLE employes ADD COLUMN poste TEXT DEFAULT 'EMPLOYE'");
                plugin.getLogger().info("[Database] Colonne 'poste' ajoutée à la table employes");
            }

            // Ajouter colonne prime si manquante
            if (!columns.contains("prime")) {
                stmt.execute("ALTER TABLE employes ADD COLUMN prime REAL DEFAULT 0.0");
                plugin.getLogger().info("[Database] Colonne 'prime' ajoutée à la table employes");
            }

            // Ajouter colonne salaire si manquante
            if (!columns.contains("salaire")) {
                stmt.execute("ALTER TABLE employes ADD COLUMN salaire REAL DEFAULT 0.0");
                plugin.getLogger().info("[Database] Colonne 'salaire' ajoutée à la table employes");
            }

            // Ajouter colonnes statistiques si manquantes
            if (!columns.contains("total_production_value")) {
                stmt.execute("ALTER TABLE employes ADD COLUMN total_production_value REAL DEFAULT 0.0");
                plugin.getLogger().info("[Database] Colonne 'total_production_value' ajoutée");
            }

            if (!columns.contains("total_salary_paid")) {
                stmt.execute("ALTER TABLE employes ADD COLUMN total_salary_paid REAL DEFAULT 0.0");
                plugin.getLogger().info("[Database] Colonne 'total_salary_paid' ajoutée");
            }

            if (!columns.contains("total_bonus_paid")) {
                stmt.execute("ALTER TABLE employes ADD COLUMN total_bonus_paid REAL DEFAULT 0.0");
                plugin.getLogger().info("[Database] Colonne 'total_bonus_paid' ajoutée");
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Database] Erreur lors du patch de la table employes", e);
        }
    }

    /**
     * Patch pour mettre à jour la table transactions (colonnes manquantes)
     */
    private void patchTransactionsTable() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            plugin.getLogger().info("[Database] Vérification de la table transactions...");

            ResultSet rs = stmt.executeQuery("PRAGMA table_info(transactions)");
            java.util.Set<String> columns = new java.util.HashSet<>();
            while (rs.next()) {
                String colName = rs.getString("name");
                columns.add(colName);
            }
            rs.close();

            // Si la colonne 'montant' existe, on doit recréer la table pour la remplacer par 'amount'
            if (columns.contains("montant") && !columns.contains("amount")) {
                plugin.getLogger().warning("[Database] ⚠️ MIGRATION CRITIQUE: Conversion montant -> amount");

                // Créer une nouvelle table avec le bon schéma
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS transactions_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        entreprise_siret TEXT NOT NULL,
                        type TEXT NOT NULL,
                        amount REAL NOT NULL,
                        description TEXT,
                        initiated_by TEXT,
                        timestamp INTEGER NOT NULL,
                        FOREIGN KEY (entreprise_siret) REFERENCES entreprises(siret) ON DELETE CASCADE
                    )
                    """);
                plugin.getLogger().info("[Database] Table transactions_new créée");

                // Copier les données existantes (en mappant montant -> amount)
                stmt.execute("""
                    INSERT INTO transactions_new (id, entreprise_siret, type, amount, description, initiated_by, timestamp)
                    SELECT id, entreprise_siret, type, montant, description,
                           COALESCE(initiated_by, 'System'),
                           COALESCE(timestamp, strftime('%s', 'now') * 1000)
                    FROM transactions
                    """);
                plugin.getLogger().info("[Database] Données copiées vers transactions_new");

                // Supprimer l'ancienne table
                stmt.execute("DROP TABLE transactions");
                plugin.getLogger().info("[Database] Ancienne table transactions supprimée");

                // Renommer la nouvelle table
                stmt.execute("ALTER TABLE transactions_new RENAME TO transactions");
                plugin.getLogger().info("[Database] Table renommée en transactions");

                // Recréer les index
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_transactions_entreprise ON transactions(entreprise_siret)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_transactions_date ON transactions(timestamp)");

                plugin.getLogger().warning("[Database] ✅ MIGRATION RÉUSSIE: montant -> amount");
            } else if (columns.contains("montant") && columns.contains("amount")) {
                plugin.getLogger().warning("[Database] ⚠️ ATTENTION: Les colonnes 'montant' ET 'amount' existent!");
                plugin.getLogger().warning("[Database] Cela peut causer des problèmes. Suppression de la colonne 'montant' recommandée.");
                
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS transactions_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        entreprise_siret TEXT NOT NULL,
                        type TEXT NOT NULL,
                        amount REAL NOT NULL,
                        description TEXT,
                        initiated_by TEXT,
                        timestamp INTEGER NOT NULL,
                        FOREIGN KEY (entreprise_siret) REFERENCES entreprises(siret) ON DELETE CASCADE
                    )
                    """);

                stmt.execute("""
                    INSERT INTO transactions_new (id, entreprise_siret, type, amount, description, initiated_by, timestamp)
                    SELECT id, entreprise_siret, type, amount, description, initiated_by, timestamp
                    FROM transactions
                    """);

                stmt.execute("DROP TABLE transactions");
                stmt.execute("ALTER TABLE transactions_new RENAME TO transactions");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_transactions_entreprise ON transactions(entreprise_siret)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_transactions_date ON transactions(timestamp)");

                plugin.getLogger().warning("[Database] ✅ Table transactions nettoyée (montant supprimé)");
            } else {
                // Si pas de migration nécessaire, juste ajouter les colonnes manquantes
                if (!columns.contains("amount")) {
                    stmt.execute("ALTER TABLE transactions ADD COLUMN amount REAL DEFAULT 0.0");
                    plugin.getLogger().info("[Database] Colonne 'amount' ajoutée à la table transactions");
                }

                if (!columns.contains("timestamp")) {
                    stmt.execute("ALTER TABLE transactions ADD COLUMN timestamp INTEGER DEFAULT 0");
                    plugin.getLogger().info("[Database] Colonne 'timestamp' ajoutée à la table transactions");
                }

                if (!columns.contains("initiated_by")) {
                    stmt.execute("ALTER TABLE transactions ADD COLUMN initiated_by TEXT DEFAULT 'System'");
                    plugin.getLogger().info("[Database] Colonne 'initiated_by' ajoutée à la table transactions");
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Database] ❌ ERREUR CRITIQUE lors du patch de la table transactions", e);
        }
    }

    /**
     * Patch pour ajouter les colonnes manquantes à la table shops
     */
    private void patchShopsTable() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            plugin.getLogger().info("[Database] Vérification de la table shops...");

            ResultSet rs = stmt.executeQuery("PRAGMA table_info(shops)");
            java.util.Set<String> columns = new java.util.HashSet<>();
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
            rs.close();

            // Ajouter colonne cached_stock si manquante
            if (!columns.contains("cached_stock")) {
                stmt.execute("ALTER TABLE shops ADD COLUMN cached_stock INTEGER DEFAULT 0");
                plugin.getLogger().info("[Database] Colonne 'cached_stock' ajoutée à la table shops");
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Database] Erreur lors du patch de la table shops", e);
        }
    }

    /**
     * Crée la table contracts si elle n'existe pas
     */
    private void patchContractsTable() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            plugin.getLogger().info("[Database] Vérification de la table contracts...");

            // Vérifier si la table existe
            ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='contracts'");
            boolean tableExists = rs.next();
            rs.close();

            if (!tableExists) {
                plugin.getLogger().info("[Database] Création de la table contracts...");

                String createTableSQL = """
                    CREATE TABLE contracts (
                        id VARCHAR(36) PRIMARY KEY,
                        service_id VARCHAR(255),
                        provider_company VARCHAR(255) NOT NULL,
                        provider_owner_uuid VARCHAR(36) NOT NULL,
                        contract_type VARCHAR(10) NOT NULL,
                        client_uuid VARCHAR(36),
                        client_company VARCHAR(255),
                        client_owner_uuid VARCHAR(36),
                        title VARCHAR(255) NOT NULL,
                        description TEXT,
                        amount DOUBLE NOT NULL,
                        status VARCHAR(50) NOT NULL,
                        funds_escrowed BOOLEAN DEFAULT 0,
                        proposal_date VARCHAR(30) NOT NULL,
                        expiration_date VARCHAR(30) NOT NULL,
                        response_date VARCHAR(30),
                        end_date VARCHAR(30),
                        judge_uuid VARCHAR(36),
                        dispute_reason TEXT,
                        dispute_verdict TEXT
                    )
                """;

                stmt.execute(createTableSQL);

                // Créer les index
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_contracts_provider ON contracts(provider_company)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_contracts_client_uuid ON contracts(client_uuid)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_contracts_client_company ON contracts(client_company)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_contracts_status ON contracts(status)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_contracts_type ON contracts(contract_type)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_contracts_provider_owner ON contracts(provider_owner_uuid)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_contracts_client_owner ON contracts(client_owner_uuid)");

                plugin.getLogger().info("[Database] Table contracts créée avec succès");
            } else {
                plugin.getLogger().info("[Database] Table contracts existe déjà");
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Database] Erreur lors de la création de la table contracts", e);
        }
    }

    /**
     * Migre les données depuis l'ancienne base entreprises.db
     */
    private void migrateFromOldDatabase() {
        File oldDb = new File(plugin.getDataFolder(), "entreprises.db");
        if (!oldDb.exists()) {
            plugin.getLogger().info("[Database] Pas d'ancienne base à migrer");
            return;
        }

        plugin.getLogger().info("[Database] Migration depuis entreprises.db...");

        try {
            // Attach l'ancienne base
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {

                stmt.execute("ATTACH DATABASE '" + oldDb.getAbsolutePath() + "' AS old_db");

                // Copier les entreprises
                stmt.execute("INSERT OR IGNORE INTO entreprises SELECT * FROM old_db.entreprises");
                int entreprises = stmt.getUpdateCount();

                // Copier les employés
                stmt.execute("INSERT OR IGNORE INTO employes SELECT * FROM old_db.employes");
                int employes = stmt.getUpdateCount();

                // Copier les transactions
                stmt.execute("INSERT OR IGNORE INTO transactions SELECT * FROM old_db.transactions");
                int transactions = stmt.getUpdateCount();

                // Copier le log de production
                stmt.execute("INSERT OR IGNORE INTO production_log SELECT * FROM old_db.production_log");
                int production = stmt.getUpdateCount();

                stmt.execute("DETACH DATABASE old_db");

                plugin.getLogger().info("[Database] Migration réussie:");
                plugin.getLogger().info("  - " + entreprises + " entreprises");
                plugin.getLogger().info("  - " + employes + " employés");
                plugin.getLogger().info("  - " + transactions + " transactions");
                plugin.getLogger().info("  - " + production + " logs de production");

                // Renommer l'ancienne base
                File backup = new File(plugin.getDataFolder(), "entreprises.db.backup");
                Files.move(oldDb.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("[Database] Ancienne base renommée en entreprises.db.backup");
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Database] Erreur migration", e);
        }
    }

    /**
     * Obtient une connexion brute depuis le pool (pour opérations DDL uniquement)
     */
    public Connection getRawConnection() throws SQLException {
        return getConnection();
    }

    /**
     * Obtient une connexion standard depuis le pool
     */
    public Connection getConnection() throws SQLException {
        if (!initialized) {
            throw new SQLException("ConnectionManager not initialized");
        }

        Connection conn = connectionPool.poll();
        if (conn == null || conn.isClosed()) {
            // Pool vide ou connexion fermée, créer une nouvelle
            conn = createConnection();
        }

        return conn;
    }

    /**
     * Retourne une connexion au pool
     */
    public void releaseConnection(Connection conn) {
        if (conn != null) {
            try {
                if (!conn.isClosed()) {
                    connectionPool.offer(conn);
                } else {
                    activeConnections.decrementAndGet();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "[Database] Erreur release connexion", e);
            }
        }
    }

    /**
     * Exécute une requête avec gestion automatique de connexion
     */
    public <T> T executeQuery(String sql, ResultSetHandler<T> handler, Object... params) throws SQLException {
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    return handler.handle(rs);
                }
            }
        } finally {
            if (conn != null) releaseConnection(conn);
        }
    }

    /**
     * Exécute une mise à jour avec gestion automatique de connexion
     */
    public int executeUpdate(String sql, Object... params) throws SQLException {
        Connection conn = null;
        try {
            conn = getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                return stmt.executeUpdate();
            }
        } finally {
            if (conn != null) releaseConnection(conn);
        }
    }

    /**
     * Exécute une transaction
     */
    public void executeTransaction(TransactionCallback callback) throws SQLException {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            try {
                callback.execute(conn);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } finally {
            if (conn != null) releaseConnection(conn);
        }
    }

    /**
     * Exécute une opération en mode asynchrone
     */
    public CompletableFuture<Void> executeAsync(Runnable task) {
        return CompletableFuture.runAsync(task, executor);
    }

    /**
     * Crée un backup de la base de données
     */
    public void createBackup() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        File backupFile = new File(plugin.getDataFolder(), "backups/" + DB_NAME + "." + timestamp);

        try {
            backupFile.getParentFile().mkdirs();
            Files.copy(databaseFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("[Database] Backup créé: " + backupFile.getName());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[Database] Erreur backup", e);
        }
    }

    /**
     * Ferme toutes les connexions et libère les ressources
     */
    public void shutdown() {
        plugin.getLogger().info("[Database] Fermeture...");

        // Fermer le pool de connexions
        Connection conn;
        while ((conn = connectionPool.poll()) != null) {
            try {
                if (!conn.isClosed()) {
                    conn.close();
                    activeConnections.decrementAndGet();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "[Database] Erreur fermeture connexion", e);
            }
        }

        // Arrêter l'executor
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }

        initialized = false;
        plugin.getLogger().info("[Database] Fermé (" + activeConnections.get() + " connexions restantes)");
    }

    /**
     * Vérifie si la base est initialisée
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Obtient des statistiques sur le pool
     */
    public String getStats() {
        return String.format("Pool: %d disponibles / %d actives",
            connectionPool.size(),
            activeConnections.get());
    }

    // ========================================
    // INTERFACES FONCTIONNELLES
    // ========================================

    @FunctionalInterface
    public interface ResultSetHandler<T> {
        T handle(ResultSet rs) throws SQLException;
    }

    @FunctionalInterface
    public interface TransactionCallback {
        void execute(Connection conn) throws SQLException;
    }
}
