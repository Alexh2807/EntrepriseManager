package com.gravityyfh.roleplaycity.database;

import com.gravityyfh.roleplaycity.RoleplayCity;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;

/**
 * Gestionnaire de versions de schéma et migrations automatiques
 * Garantit que la structure de la base est toujours à jour
 */
public class SchemaVersionManager {

    private final RoleplayCity plugin;
    private final ConnectionManager connectionManager;
    private static final int CURRENT_VERSION = 4; // Version actuelle du schéma

    public SchemaVersionManager(RoleplayCity plugin, ConnectionManager connectionManager) {
        this.plugin = plugin;
        this.connectionManager = connectionManager;
    }

    /**
     * Vérifie et met à jour le schéma si nécessaire
     */
    public void checkAndUpdateSchema() {
        try (Connection conn = connectionManager.getRawConnection()) {

            // Créer la table metadata si elle n'existe pas
            ensureMetadataTable(conn);

            // Récupérer la version actuelle
            int currentVersion = getSchemaVersion(conn);

            plugin.getLogger().info("[Schema] Version actuelle: " + currentVersion + " | Requise: " + CURRENT_VERSION);

            // Appliquer les migrations nécessaires
            if (currentVersion < CURRENT_VERSION) {
                applyMigrations(conn, currentVersion, CURRENT_VERSION);
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Schema] Erreur lors de la vérification du schéma", e);
        }
    }

    /**
     * Garantit qu'une table a toutes les colonnes requises
     * Ajoute automatiquement les colonnes manquantes
     */
    public void ensureTableColumns(String tableName, Map<String, ColumnDefinition> requiredColumns) {
        try (Connection conn = connectionManager.getRawConnection();
             Statement stmt = conn.createStatement()) {

            // Récupérer les colonnes existantes
            ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ")");
            Set<String> existingColumns = new HashSet<>();

            while (rs.next()) {
                existingColumns.add(rs.getString("name").toLowerCase());
            }
            rs.close();

            // Ajouter les colonnes manquantes
            for (Map.Entry<String, ColumnDefinition> entry : requiredColumns.entrySet()) {
                String columnName = entry.getKey().toLowerCase();

                if (!existingColumns.contains(columnName)) {
                    ColumnDefinition def = entry.getValue();
                    String alterSQL = "ALTER TABLE " + tableName + " ADD COLUMN " +
                                      columnName + " " + def.type +
                                      (def.defaultValue != null ? " DEFAULT " + def.defaultValue : "");

                    stmt.execute(alterSQL);
                    plugin.getLogger().info("[Schema] ✓ Colonne ajoutée: " + tableName + "." + columnName);
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING,
                "[Schema] Erreur lors de la vérification de la table " + tableName, e);
        }
    }

    /**
     * Classe interne pour définir une colonne
     */
    public static class ColumnDefinition {
        String type;
        String defaultValue;

        public ColumnDefinition(String type, String defaultValue) {
            this.type = type;
            this.defaultValue = defaultValue;
        }

        public ColumnDefinition(String type) {
            this(type, null);
        }
    }

    /**
     * Crée la table metadata si elle n'existe pas
     */
    private void ensureMetadataTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)");

            // Initialiser la version si elle n'existe pas
            stmt.execute("INSERT OR IGNORE INTO metadata (key, value) VALUES ('schema_version', '0')");
        }
    }

    /**
     * Récupère la version actuelle du schéma
     */
    private int getSchemaVersion(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT value FROM metadata WHERE key = 'schema_version'")) {

            if (rs.next()) {
                return Integer.parseInt(rs.getString("value"));
            }
            return 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Met à jour la version du schéma
     */
    private void setSchemaVersion(Connection conn, int version) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT OR REPLACE INTO metadata (key, value) VALUES ('schema_version', '" + version + "')");
        }
    }

    /**
     * Applique les migrations nécessaires
     */
    private void applyMigrations(Connection conn, int fromVersion, int toVersion) throws SQLException {
        plugin.getLogger().info("[Schema] Application des migrations de v" + fromVersion + " à v" + toVersion);

        // Migration v0 -> v1 : Garantir toutes les colonnes de base
        if (fromVersion < 1) {
            migrationV1(conn);
            setSchemaVersion(conn, 1);
        }

        // Migration v1 -> v2 : Supprimer owner_uuid/owner_name redondants de mailboxes
        if (fromVersion < 2) {
            migrationV2(conn);
            setSchemaVersion(conn, 2);
        }

        // Migration v2 -> v3 : Ajouter cached_stock aux shops existants
        if (fromVersion < 3) {
            migrationV3(conn);
            setSchemaVersion(conn, 3);
        }

        // Migration v3 -> v4 : Ajouter total_items_sold aux shops
        if (fromVersion < 4) {
            migrationV4(conn);
            setSchemaVersion(conn, 4);
        }

        plugin.getLogger().info("[Schema] ✓ Migrations appliquées avec succès");
    }

    /**
     * Migration V1 : Garantir toutes les colonnes essentielles
     */
    private void migrationV1(Connection conn) throws SQLException {
        plugin.getLogger().info("[Schema] Migration v1: Vérification des colonnes...");

        // Colonnes requises pour la table entreprises
        Map<String, ColumnDefinition> entreprisesColumns = new LinkedHashMap<>();
        entreprisesColumns.put("siret", new ColumnDefinition("TEXT"));
        entreprisesColumns.put("nom", new ColumnDefinition("TEXT"));
        entreprisesColumns.put("ville", new ColumnDefinition("TEXT", "'Inconnue'"));
        entreprisesColumns.put("description", new ColumnDefinition("TEXT"));
        entreprisesColumns.put("type", new ColumnDefinition("TEXT"));
        entreprisesColumns.put("gerant_nom", new ColumnDefinition("TEXT"));
        entreprisesColumns.put("gerant_uuid", new ColumnDefinition("TEXT"));
        entreprisesColumns.put("capital", new ColumnDefinition("REAL", "0.0"));
        entreprisesColumns.put("solde", new ColumnDefinition("REAL", "0.0"));
        entreprisesColumns.put("chiffre_affaires_total", new ColumnDefinition("REAL", "0.0"));
        entreprisesColumns.put("niveau_max_employes", new ColumnDefinition("INTEGER", "0"));
        entreprisesColumns.put("niveau_max_solde", new ColumnDefinition("INTEGER", "0"));
        entreprisesColumns.put("niveau_restrictions", new ColumnDefinition("INTEGER", "0"));
        entreprisesColumns.put("total_value", new ColumnDefinition("REAL", "0.0"));
        entreprisesColumns.put("creation_date", new ColumnDefinition("TEXT"));
        entreprisesColumns.put("created_at", new ColumnDefinition("INTEGER"));
        entreprisesColumns.put("updated_at", new ColumnDefinition("INTEGER"));
        entreprisesColumns.put("is_dirty", new ColumnDefinition("INTEGER", "0"));

        ensureTableColumnsInternal(conn, "entreprises", entreprisesColumns);

        // Colonnes requises pour employes
        Map<String, ColumnDefinition> employesColumns = new LinkedHashMap<>();
        employesColumns.put("entreprise_siret", new ColumnDefinition("TEXT"));
        employesColumns.put("employe_uuid", new ColumnDefinition("TEXT"));
        employesColumns.put("employe_nom", new ColumnDefinition("TEXT"));
        employesColumns.put("poste", new ColumnDefinition("TEXT", "'EMPLOYE'"));
        employesColumns.put("salaire", new ColumnDefinition("REAL", "0.0"));
        employesColumns.put("join_date", new ColumnDefinition("INTEGER"));
        employesColumns.put("total_production_value", new ColumnDefinition("REAL", "0.0"));
        employesColumns.put("total_salary_paid", new ColumnDefinition("REAL", "0.0"));
        employesColumns.put("total_bonus_paid", new ColumnDefinition("REAL", "0.0"));
        employesColumns.put("prime", new ColumnDefinition("REAL", "0.0"));

        ensureTableColumnsInternal(conn, "employes", employesColumns);

        // Colonnes requises pour transactions
        Map<String, ColumnDefinition> transactionsColumns = new LinkedHashMap<>();
        transactionsColumns.put("entreprise_siret", new ColumnDefinition("TEXT"));
        transactionsColumns.put("type", new ColumnDefinition("TEXT"));
        transactionsColumns.put("amount", new ColumnDefinition("REAL"));
        transactionsColumns.put("description", new ColumnDefinition("TEXT"));
        transactionsColumns.put("initiated_by", new ColumnDefinition("TEXT"));
        transactionsColumns.put("timestamp", new ColumnDefinition("INTEGER"));

        ensureTableColumnsInternal(conn, "transactions", transactionsColumns);

        // Colonnes requises pour shops
        Map<String, ColumnDefinition> shopsColumns = new LinkedHashMap<>();
        shopsColumns.put("cached_stock", new ColumnDefinition("INTEGER", "0"));

        ensureTableColumnsInternal(conn, "shops", shopsColumns);

        // Création de la table de stockage d'entreprise (Nouveau système)
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS company_storage (company_name TEXT PRIMARY KEY, content TEXT, updated_at INTEGER)");
            plugin.getLogger().info("[Schema] Table company_storage vérifiée/créée.");
            
            // Création des tables pour le Loto
            stmt.execute("CREATE TABLE IF NOT EXISTS lotto_data (data_key TEXT PRIMARY KEY, data_value TEXT NOT NULL)");
            stmt.execute("CREATE TABLE IF NOT EXISTS lotto_tickets (id INTEGER PRIMARY KEY AUTOINCREMENT, player_uuid TEXT NOT NULL)");
            plugin.getLogger().info("[Schema] Tables Loto vérifiées/créées.");

            // Création de la table pour les blocs des locataires
            stmt.execute("CREATE TABLE IF NOT EXISTS plot_renter_blocks (" +
                    "plot_id INTEGER NOT NULL, " +
                    "renter_uuid TEXT NOT NULL, " +
                    "world TEXT NOT NULL, " +
                    "x INTEGER NOT NULL, " +
                    "y INTEGER NOT NULL, " +
                    "z INTEGER NOT NULL, " +
                    "FOREIGN KEY (plot_id) REFERENCES plots(id) ON DELETE CASCADE)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_renter_blocks_plot ON plot_renter_blocks(plot_id)");
            plugin.getLogger().info("[Schema] Table plot_renter_blocks vérifiée/créée.");

            // Création de la table d'identité virtuelle
            stmt.execute("CREATE TABLE IF NOT EXISTS player_identities (" +
                    "player_uuid TEXT PRIMARY KEY, " +
                    "real_name TEXT NOT NULL, " +
                    "birth_date TEXT NOT NULL, " +
                    "gender TEXT NOT NULL, " +
                    "job_title TEXT, " +
                    "creation_date TEXT NOT NULL, " +
                    "is_valid INTEGER DEFAULT 1)");
            plugin.getLogger().info("[Schema] Table player_identities vérifiée/créée.");
        }
    }

    /**
     * Migration V3 : Ajouter cached_stock aux shops existants
     */
    private void migrationV3(Connection conn) throws SQLException {
        plugin.getLogger().info("[Schema] Migration v3: Ajout cached_stock aux shops...");

        Map<String, ColumnDefinition> shopsColumns = new LinkedHashMap<>();
        shopsColumns.put("cached_stock", new ColumnDefinition("INTEGER", "0"));

        ensureTableColumnsInternal(conn, "shops", shopsColumns);
        plugin.getLogger().info("[Schema] ✓ Migration v3 terminée");
    }

    /**
     * Migration V4 : Ajouter total_items_sold aux shops (statistiques d'items vendus)
     */
    private void migrationV4(Connection conn) throws SQLException {
        plugin.getLogger().info("[Schema] Migration v4: Ajout total_items_sold aux shops...");

        Map<String, ColumnDefinition> shopsColumns = new LinkedHashMap<>();
        shopsColumns.put("total_items_sold", new ColumnDefinition("INTEGER", "0"));

        ensureTableColumnsInternal(conn, "shops", shopsColumns);
        plugin.getLogger().info("[Schema] ✓ Migration v4 terminée");
    }

    /**
     * Migration V2 : Supprimer les colonnes redondantes owner_uuid/owner_name de mailboxes
     * Ces infos sont déjà dans le Plot lié via plot_group_id
     */
    private void migrationV2(Connection conn) throws SQLException {
        plugin.getLogger().info("[Schema] Migration v2: Nettoyage table mailboxes...");

        try (Statement stmt = conn.createStatement()) {
            // Vérifier si la table mailboxes existe et a les anciennes colonnes
            ResultSet rs = stmt.executeQuery("PRAGMA table_info(mailboxes)");
            boolean hasOwnerUuid = false;
            while (rs.next()) {
                if ("owner_uuid".equalsIgnoreCase(rs.getString("name"))) {
                    hasOwnerUuid = true;
                    break;
                }
            }
            rs.close();

            if (hasOwnerUuid) {
                plugin.getLogger().info("[Schema] Ancienne structure mailboxes détectée, migration...");

                // SQLite ne supporte pas DROP COLUMN facilement, on recrée la table
                // 1. Supprimer l'ancienne table (les mailboxes seront recréées au prochain save)
                stmt.execute("DROP TABLE IF EXISTS mailboxes");
                plugin.getLogger().info("[Schema] ✓ Table mailboxes supprimée (sera recréée avec nouveau schéma)");

                // 2. Recréer avec le nouveau schéma
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS mailboxes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        plot_group_id TEXT UNIQUE NOT NULL,
                        town_name TEXT NOT NULL,
                        world TEXT NOT NULL,
                        x REAL NOT NULL,
                        y REAL NOT NULL,
                        z REAL NOT NULL,
                        mailbox_type TEXT NOT NULL,
                        creation_date TEXT NOT NULL
                    )
                """);
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_mailboxes_town ON mailboxes(town_name)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_mailboxes_plot ON mailboxes(plot_group_id)");
                plugin.getLogger().info("[Schema] ✓ Table mailboxes recréée avec nouveau schéma simplifié");
            } else {
                plugin.getLogger().info("[Schema] Table mailboxes déjà à jour");
            }
        }
    }

    /**
     * Version interne qui utilise une connexion existante
     */
    private void ensureTableColumnsInternal(Connection conn, String tableName, Map<String, ColumnDefinition> requiredColumns) throws SQLException {
        try (Statement stmt = conn.createStatement()) {

            // Récupérer les colonnes existantes
            ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ")");
            Set<String> existingColumns = new HashSet<>();

            while (rs.next()) {
                existingColumns.add(rs.getString("name").toLowerCase());
            }
            rs.close();

            // Ajouter les colonnes manquantes
            for (Map.Entry<String, ColumnDefinition> entry : requiredColumns.entrySet()) {
                String columnName = entry.getKey().toLowerCase();

                if (!existingColumns.contains(columnName)) {
                    ColumnDefinition def = entry.getValue();
                    String alterSQL = "ALTER TABLE " + tableName + " ADD COLUMN " +
                                      columnName + " " + def.type +
                                      (def.defaultValue != null ? " DEFAULT " + def.defaultValue : "");

                    stmt.execute(alterSQL);
                    plugin.getLogger().info("[Schema] ✓ Colonne ajoutée: " + tableName + "." + columnName);
                }
            }
        }
    }
}
