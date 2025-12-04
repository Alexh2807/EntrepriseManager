package com.gravityyfh.roleplaycity.entreprise.persistence;

import com.gravityyfh.roleplaycity.RoleplayCity;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

/**
 * Gestionnaire de connexion SQLite pour la persistence des entreprises.
 *
 * Responsabilités:
 * - Créer et maintenir la connexion SQLite
 * - Créer le schéma de base de données
 * - Gérer le pool de connexions (simple pour SQLite)
 * - Assurer la cohérence transactionnelle
 */
public class SQLiteConnectionManager {

    private final RoleplayCity plugin;
    private final File databaseFile;
    private Connection connection;
    private final Lock connectionLock = new ReentrantLock();

    // Configuration
    private static final String DATABASE_NAME = "entreprises.db";
    private static final int BUSY_TIMEOUT_MS = 5000;

    public SQLiteConnectionManager(RoleplayCity plugin) {
        this.plugin = plugin;
        this.databaseFile = new File(plugin.getDataFolder(), DATABASE_NAME);
    }

    /**
     * Initialise la connexion et crée le schéma si nécessaire.
     */
    public void initialize() throws SQLException {
        connectionLock.lock();
        try {
            // Créer le dossier si nécessaire
            if (!databaseFile.getParentFile().exists()) {
                databaseFile.getParentFile().mkdirs();
            }

            // Charger le driver SQLite
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                throw new SQLException("Driver SQLite non trouvé", e);
            }

            // Créer la connexion
            String url = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);

            // Configuration optimale pour SQLite
            try (PreparedStatement stmt = connection.prepareStatement("PRAGMA journal_mode=WAL")) {
                stmt.execute();
            }
            try (PreparedStatement stmt = connection.prepareStatement("PRAGMA synchronous=NORMAL")) {
                stmt.execute();
            }
            try (PreparedStatement stmt = connection.prepareStatement("PRAGMA busy_timeout=" + BUSY_TIMEOUT_MS)) {
                stmt.execute();
            }
            try (PreparedStatement stmt = connection.prepareStatement("PRAGMA foreign_keys=ON")) {
                stmt.execute();
            }

            plugin.getLogger().info("Connexion SQLite établie: " + databaseFile.getAbsolutePath());

            // Créer le schéma
            createSchema();

        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Crée le schéma de base de données.
     */
    private void createSchema() throws SQLException {
        String[] tables = {
            // Table principale des entreprises
            """
            CREATE TABLE IF NOT EXISTS entreprises (
                siret TEXT PRIMARY KEY,
                nom TEXT NOT NULL UNIQUE,
                ville TEXT NOT NULL,
                type TEXT NOT NULL,
                gerant_nom TEXT NOT NULL,
                gerant_uuid TEXT NOT NULL,
                solde REAL NOT NULL DEFAULT 0,
                chiffre_affaires_total REAL NOT NULL DEFAULT 0,
                niveau_max_employes INTEGER NOT NULL DEFAULT 0,
                niveau_max_solde INTEGER NOT NULL DEFAULT 0,
                niveau_restrictions INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """,

            // Index pour recherches fréquentes
            "CREATE INDEX IF NOT EXISTS idx_entreprises_gerant ON entreprises(gerant_uuid)",
            "CREATE INDEX IF NOT EXISTS idx_entreprises_ville ON entreprises(ville)",
            "CREATE INDEX IF NOT EXISTS idx_entreprises_type ON entreprises(type)",

            // Table des employés
            """
            CREATE TABLE IF NOT EXISTS employes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                entreprise_siret TEXT NOT NULL,
                nom TEXT NOT NULL,
                uuid TEXT NOT NULL,
                prime REAL NOT NULL DEFAULT 0,
                join_date INTEGER NOT NULL,
                FOREIGN KEY (entreprise_siret) REFERENCES entreprises(siret) ON DELETE CASCADE,
                UNIQUE(entreprise_siret, uuid)
            )
            """,

            "CREATE INDEX IF NOT EXISTS idx_employes_entreprise ON employes(entreprise_siret)",
            "CREATE INDEX IF NOT EXISTS idx_employes_uuid ON employes(uuid)",

            // Table des transactions
            """
            CREATE TABLE IF NOT EXISTS transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                entreprise_siret TEXT NOT NULL,
                type TEXT NOT NULL,
                amount REAL NOT NULL,
                description TEXT,
                initiated_by TEXT,
                timestamp INTEGER NOT NULL,
                FOREIGN KEY (entreprise_siret) REFERENCES entreprises(siret) ON DELETE CASCADE
            )
            """,

            "CREATE INDEX IF NOT EXISTS idx_transactions_entreprise ON transactions(entreprise_siret)",
            "CREATE INDEX IF NOT EXISTS idx_transactions_timestamp ON transactions(timestamp)",

            // Table des enregistrements d'activité employés
            """
            CREATE TABLE IF NOT EXISTS employee_activities (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                entreprise_siret TEXT NOT NULL,
                employee_uuid TEXT NOT NULL,
                employee_name TEXT NOT NULL,
                current_session_start INTEGER,
                last_activity_time INTEGER,
                total_value_generated REAL NOT NULL DEFAULT 0,
                join_date INTEGER NOT NULL,
                FOREIGN KEY (entreprise_siret) REFERENCES entreprises(siret) ON DELETE CASCADE,
                UNIQUE(entreprise_siret, employee_uuid)
            )
            """,

            "CREATE INDEX IF NOT EXISTS idx_activities_entreprise ON employee_activities(entreprise_siret)",
            "CREATE INDEX IF NOT EXISTS idx_activities_employee ON employee_activities(employee_uuid)",

            // Table des actions effectuées par employé
            """
            CREATE TABLE IF NOT EXISTS employee_actions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                activity_id INTEGER NOT NULL,
                action_key TEXT NOT NULL,
                count INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY (activity_id) REFERENCES employee_activities(id) ON DELETE CASCADE,
                UNIQUE(activity_id, action_key)
            )
            """,

            "CREATE INDEX IF NOT EXISTS idx_actions_activity ON employee_actions(activity_id)",

            // Table de production détaillée par employé
            """
            CREATE TABLE IF NOT EXISTS detailed_production (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                activity_id INTEGER NOT NULL,
                timestamp INTEGER NOT NULL,
                action_type TEXT NOT NULL,
                material TEXT NOT NULL,
                quantity INTEGER NOT NULL,
                FOREIGN KEY (activity_id) REFERENCES employee_activities(id) ON DELETE CASCADE
            )
            """,

            "CREATE INDEX IF NOT EXISTS idx_detailed_production_activity ON detailed_production(activity_id)",
            "CREATE INDEX IF NOT EXISTS idx_detailed_production_timestamp ON detailed_production(timestamp)",

            // Table de production globale entreprise
            """
            CREATE TABLE IF NOT EXISTS global_production (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                entreprise_siret TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                material TEXT NOT NULL,
                quantity INTEGER NOT NULL,
                employee_uuid TEXT NOT NULL,
                action_type TEXT NOT NULL,
                FOREIGN KEY (entreprise_siret) REFERENCES entreprises(siret) ON DELETE CASCADE
            )
            """,

            "CREATE INDEX IF NOT EXISTS idx_global_production_entreprise ON global_production(entreprise_siret)",
            "CREATE INDEX IF NOT EXISTS idx_global_production_timestamp ON global_production(timestamp)",

            // Table des demandes de création en attente
            """
            CREATE TABLE IF NOT EXISTS demandes_creation (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                maire_uuid TEXT NOT NULL,
                maire_name TEXT NOT NULL,
                gerant_uuid TEXT NOT NULL,
                gerant_name TEXT NOT NULL,
                type TEXT NOT NULL,
                ville TEXT NOT NULL,
                siret TEXT NOT NULL,
                nom_entreprise TEXT NOT NULL,
                cout REAL NOT NULL,
                expiration_time INTEGER NOT NULL,
                UNIQUE(gerant_uuid)
            )
            """,

            "CREATE INDEX IF NOT EXISTS idx_demandes_expiration ON demandes_creation(expiration_time)",

            // Table de métadonnées et version du schéma
            """
            CREATE TABLE IF NOT EXISTS schema_version (
                version INTEGER PRIMARY KEY,
                applied_at INTEGER NOT NULL
            )
            """,

            "INSERT OR IGNORE INTO schema_version (version, applied_at) VALUES (1, " + System.currentTimeMillis() + ")"
        };

        for (String sql : tables) {
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.execute();
            }
        }

        plugin.getLogger().info("Schéma SQLite créé avec succès (9 tables + indexes)");
    }

    /**
     * Retourne la connexion active.
     */
    public Connection getConnection() throws SQLException {
        connectionLock.lock();
        try {
            if (connection == null || connection.isClosed()) {
                throw new SQLException("Connexion fermée, appeler initialize() d'abord");
            }
            return connection;
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Démarre une transaction.
     */
    public void beginTransaction() throws SQLException {
        connectionLock.lock();
        try {
            connection.setAutoCommit(false);
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Commit une transaction.
     */
    public void commit() throws SQLException {
        connectionLock.lock();
        try {
            connection.commit();
            connection.setAutoCommit(true);
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Rollback une transaction.
     */
    public void rollback() {
        connectionLock.lock();
        try {
            if (connection != null && !connection.isClosed()) {
                connection.rollback();
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erreur lors du rollback", e);
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Ferme la connexion proprement.
     */
    public void close() {
        connectionLock.lock();
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Connexion SQLite fermée");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erreur lors de la fermeture de la connexion", e);
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Optimise la base de données (VACUUM).
     */
    public void optimize() {
        connectionLock.lock();
        try {
            try (PreparedStatement stmt = connection.prepareStatement("VACUUM")) {
                stmt.execute();
                plugin.getLogger().info("Base de données optimisée (VACUUM)");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Erreur lors de l'optimisation", e);
        } finally {
            connectionLock.unlock();
        }
    }

    /**
     * Crée une sauvegarde de la base de données.
     */
    public File createBackup() throws SQLException {
        File backupFile = new File(plugin.getDataFolder(),
            DATABASE_NAME + ".backup." + System.currentTimeMillis());

        connectionLock.lock();
        try {
            try (PreparedStatement stmt = connection.prepareStatement(
                "VACUUM INTO '" + backupFile.getAbsolutePath().replace("\\", "\\\\") + "'")) {
                stmt.execute();
            }
            plugin.getLogger().info("Sauvegarde créée: " + backupFile.getName());
            return backupFile;
        } finally {
            connectionLock.unlock();
        }
    }
}
