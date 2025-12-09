package com.gravityyfh.roleplaycity.entreprise.service;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.database.ConnectionManager;
import com.gravityyfh.roleplaycity.entreprise.persistence.EntrepriseRepository;
import com.gravityyfh.roleplaycity.entreprise.persistence.SQLiteEntrepriseRepository;

import java.sql.SQLException;

/**
 * Factory pour créer et gérer les instances de services.
 *
 * Responsabilités:
 * - Créer les services avec leurs dépendances
 * - Gérer le cycle de vie (singleton pattern)
 * - Fournir un point d'accès centralisé
 * - Gérer la configuration SQLite vs YAML
 */
public class ServiceFactory {

    private final RoleplayCity plugin;

    // Repository
    private EntrepriseRepository repository;
    private ConnectionManager connectionManager;

    // Services (singletons)
    private EntrepriseService entrepriseService;
    private EconomyService economyService;
    private PayrollService payrollService;
    private ProductionService productionService;

    // Mode de persistence
    private boolean useSQLite = false;

    public ServiceFactory(RoleplayCity plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialise la factory avec SQLite.
     * À appeler au démarrage du plugin si SQLite est activé.
     */
    public void initializeWithSQLite(ConnectionManager connectionManager) throws SQLException {
        plugin.getLogger().info("[ServiceFactory] Initialisation avec SQLite (Global)...");

        this.connectionManager = connectionManager;

        // Créer le repository SQLite
        repository = new SQLiteEntrepriseRepository(plugin, connectionManager);

        // Créer les services
        createServices();

        useSQLite = true;
        plugin.getLogger().info("[ServiceFactory] Services SQLite initialisés avec succès");
    }

    /**
     * Initialise la factory en mode YAML legacy.
     * Les services ne seront pas disponibles (use EntrepriseManagerLogic).
     */
    public void initializeWithYAML() {
        plugin.getLogger().info("[ServiceFactory] Mode YAML legacy - Services désactivés");
        useSQLite = false;
    }

    /**
     * Crée les instances de services.
     */
    private void createServices() {
        economyService = new EconomyService(plugin, repository);
        payrollService = new PayrollService(plugin, repository, economyService);
        productionService = new ProductionService(plugin, repository);
        entrepriseService = new EntrepriseService(plugin, repository);
    }

    /**
     * Récupère le service Entreprise.
     * @return EntrepriseService ou null si mode YAML
     */
    public EntrepriseService getEntrepriseService() {
        if (!useSQLite) {
            plugin.getLogger().warning("EntrepriseService appelé en mode YAML - retour null");
            return null;
        }
        return entrepriseService;
    }

    /**
     * Récupère le service Economy.
     * @return EconomyService ou null si mode YAML
     */
    public EconomyService getEconomyService() {
        if (!useSQLite) {
            plugin.getLogger().warning("EconomyService appelé en mode YAML - retour null");
            return null;
        }
        return economyService;
    }

    /**
     * Récupère le service Payroll.
     * @return PayrollService ou null si mode YAML
     */
    public PayrollService getPayrollService() {
        if (!useSQLite) {
            plugin.getLogger().warning("PayrollService appelé en mode YAML - retour null");
            return null;
        }
        return payrollService;
    }

    /**
     * Récupère le service Production.
     * @return ProductionService ou null si mode YAML
     */
    public ProductionService getProductionService() {
        if (!useSQLite) {
            plugin.getLogger().warning("ProductionService appelé en mode YAML - retour null");
            return null;
        }
        return productionService;
    }

    /**
     * Récupère le repository.
     * @return Repository ou null si mode YAML
     */
    public EntrepriseRepository getRepository() {
        return repository;
    }

    /**
     * Vérifie si SQLite est activé.
     */
    public boolean isSQLiteEnabled() {
        return useSQLite;
    }

    /**
     * Ferme proprement les connexions (shutdown).
     */
    public void shutdown() {
        // La fermeture du ConnectionManager est gérée par RoleplayCity
        // On ne ferme rien ici pour éviter de fermer la connexion globale trop tôt
    }

    /**
     * Sauvegarde toutes les données (flush cache si présent).
     * En mode SQLite, commit les transactions en attente.
     */
    public void saveAll() {
        if (!useSQLite) {
            return;
        }
        // Avec le ConnectionManager global, les transactions sont gérées automatiquement
        // lors de l'exécution. Il n'y a pas de transaction globale ouverte à commiter.
    }

    /**
     * Optimise la base de données (VACUUM).
     * À appeler périodiquement ou au shutdown.
     */
    public void optimizeDatabase() {
        if (!useSQLite || connectionManager == null) {
            return;
        }
        // TODO: Implémenter optimize() dans ConnectionManager si nécessaire
        // connectionManager.optimize();
    }

    /**
     * Crée un backup de la base SQLite.
     * @return Fichier de backup créé ou null si erreur
     */
    public java.io.File createBackup() {
        if (!useSQLite || connectionManager == null) {
            plugin.getLogger().warning("Backup demandé mais SQLite non activé");
            return null;
        }

        connectionManager.createBackup();
        // Note: createBackup() retourne void dans ConnectionManager, on retourne null
        // ou on modifie ConnectionManager pour retourner le fichier.
        // Pour l'instant on retourne null car on ne peut pas récupérer le fichier facilement
        return null;
    }
}
