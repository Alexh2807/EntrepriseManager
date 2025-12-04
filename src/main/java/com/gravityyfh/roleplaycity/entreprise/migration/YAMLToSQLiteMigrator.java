package com.gravityyfh.roleplaycity.entreprise.migration;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.entreprise.model.*;
import com.gravityyfh.roleplaycity.entreprise.persistence.EntrepriseRepository;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Level;

/**
 * Outil de migration des données depuis YAML vers SQLite.
 *
 * Processus:
 * 1. Backup du fichier YAML
 * 2. Chargement de toutes les entreprises depuis YAML
 * 3. Sauvegarde dans SQLite via repository
 * 4. Validation (count, integrity checks)
 * 5. Renommage YAML en .migrated
 *
 * Rollback disponible si erreur.
 */
public class YAMLToSQLiteMigrator {

    private final RoleplayCity plugin;
    private final EntrepriseRepository repository;
    private final File yamlFile;

    // Statistiques de migration
    private int totalEntreprises = 0;
    private int entreprisesMigrees = 0;
    private int employesMigres = 0;
    private int transactionsMigrees = 0;
    private int activitiesMigrees = 0;
    private List<String> erreurs = new ArrayList<>();

    public YAMLToSQLiteMigrator(RoleplayCity plugin, EntrepriseRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        this.yamlFile = new File(plugin.getDataFolder(), "entreprise.yml");
    }

    /**
     * Lance la migration complète avec backup automatique.
     *
     * @return Résultat de la migration
     */
    public MigrationResult migrate() {
        plugin.getLogger().info("========================================");
        plugin.getLogger().info("Début de la migration YAML → SQLite");
        plugin.getLogger().info("========================================");

        long startTime = System.currentTimeMillis();

        try {
            // Étape 1: Vérifications préliminaires
            if (!yamlFile.exists()) {
                return MigrationResult.error("Fichier YAML non trouvé: " + yamlFile.getPath());
            }

            // Étape 2: Backup du fichier YAML
            plugin.getLogger().info("[1/5] Création du backup YAML...");
            File backupFile = createBackup();
            plugin.getLogger().info("✓ Backup créé: " + backupFile.getName());

            // Étape 3: Chargement des données YAML
            plugin.getLogger().info("[2/5] Chargement des données YAML...");
            List<Entreprise> entreprises = loadFromYAML();
            totalEntreprises = entreprises.size();
            plugin.getLogger().info("✓ Chargé " + totalEntreprises + " entreprises depuis YAML");

            if (entreprises.isEmpty()) {
                return MigrationResult.warning("Aucune entreprise à migrer");
            }

            // Étape 4: Migration vers SQLite
            plugin.getLogger().info("[3/5] Migration vers SQLite...");
            migrateToSQLite(entreprises);
            plugin.getLogger().info("✓ Migré " + entreprisesMigrees + "/" + totalEntreprises + " entreprises");

            // Étape 5: Validation
            plugin.getLogger().info("[4/5] Validation de la migration...");
            ValidationResult validation = validate();
            if (!validation.isValid()) {
                plugin.getLogger().severe("✗ Validation échouée: " + validation.getMessage());
                return MigrationResult.error("Validation échouée", validation);
            }
            plugin.getLogger().info("✓ Validation réussie");

            // Étape 6: Finalisation
            plugin.getLogger().info("[5/5] Finalisation...");
            finalizeMigration();
            plugin.getLogger().info("✓ Fichier YAML renommé en .migrated");

            long duration = System.currentTimeMillis() - startTime;

            // Statistiques finales
            plugin.getLogger().info("========================================");
            plugin.getLogger().info("Migration réussie en " + duration + "ms");
            plugin.getLogger().info("Entreprises: " + entreprisesMigrees);
            plugin.getLogger().info("Employés: " + employesMigres);
            plugin.getLogger().info("Transactions: " + transactionsMigrees);
            plugin.getLogger().info("Activités: " + activitiesMigrees);
            plugin.getLogger().info("========================================");

            return MigrationResult.success(entreprisesMigrees, duration);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur critique durant la migration", e);
            return MigrationResult.error("Erreur: " + e.getMessage());
        }
    }

    /**
     * Crée un backup du fichier YAML.
     */
    private File createBackup() throws IOException {
        String timestamp = String.valueOf(System.currentTimeMillis());
        File backupFile = new File(plugin.getDataFolder(), "entreprise.yml.backup." + timestamp);

        Files.copy(yamlFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        return backupFile;
    }

    /**
     * Charge toutes les entreprises depuis le fichier YAML.
     */
    private List<Entreprise> loadFromYAML() {
        List<Entreprise> entreprises = new ArrayList<>();
        FileConfiguration config = YamlConfiguration.loadConfiguration(yamlFile);

        ConfigurationSection entreprisesSection = config.getConfigurationSection("entreprises");
        if (entreprisesSection == null) {
            plugin.getLogger().warning("Section 'entreprises' non trouvée dans YAML");
            return entreprises;
        }

        for (String nomEntreprise : entreprisesSection.getKeys(false)) {
            try {
                Entreprise entreprise = loadEntrepriseFromYAML(entreprisesSection, nomEntreprise);
                if (entreprise != null) {
                    entreprises.add(entreprise);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Erreur chargement entreprise: " + nomEntreprise, e);
                erreurs.add("Erreur chargement " + nomEntreprise + ": " + e.getMessage());
            }
        }

        return entreprises;
    }

    /**
     * Charge une entreprise spécifique depuis YAML.
     */
    private Entreprise loadEntrepriseFromYAML(ConfigurationSection section, String nomEntreprise) {
        ConfigurationSection entSection = section.getConfigurationSection(nomEntreprise);
        if (entSection == null) {
            return null;
        }

        // Données de base
        String ville = entSection.getString("ville", "Unknown");
        String type = entSection.getString("type", "Unknown");

        // Support ancien format (gerant) et nouveau format (gerantNom)
        String gerantNom = entSection.getString("gerantNom", entSection.getString("gerant", "Unknown"));
        String gerantUUID = entSection.getString("gerantUUID", entSection.getString("gerant-uuid", UUID.randomUUID().toString()));

        double solde = entSection.getDouble("solde", 0.0);
        String siret = entSection.getString("siret", generateSiret());

        // Employés - Support Map (nouveau format) et List (ancien format)
        Set<String> employes = new HashSet<>();
        ConfigurationSection employesSection = entSection.getConfigurationSection("employes");
        if (employesSection != null) {
            // Nouveau format: employes: { uuid: nom, ... }
            employes.addAll(employesSection.getKeys(false));
        } else {
            // Ancien format: employes: [nom1, nom2, ...]
            List<String> employesList = entSection.getStringList("employes");
            employes.addAll(employesList);
        }

        // Créer l'entreprise
        Entreprise entreprise = new Entreprise(
            nomEntreprise,
            ville,
            type,
            gerantNom,
            gerantUUID,
            employes,
            solde,
            siret
        );

        // Chiffre d'affaires - Support camelCase et kebab-case
        double chiffreAffaires = entSection.getDouble("chiffreAffairesTotal",
                                 entSection.getDouble("chiffre-affaires-total", 0.0));
        entreprise.setChiffreAffairesTotal(chiffreAffaires);

        // Niveaux - Support camelCase et kebab-case
        int niveauMaxEmployes = entSection.getInt("niveauMaxEmployes",
                                entSection.getInt("niveau-max-employes", 0));
        entreprise.setNiveauMaxEmployes(niveauMaxEmployes);

        int niveauMaxSolde = entSection.getInt("niveauMaxSolde",
                             entSection.getInt("niveau-max-solde", 0));
        entreprise.setNiveauMaxSolde(niveauMaxSolde);

        int niveauRestrictions = entSection.getInt("niveauRestrictions",
                                 entSection.getInt("niveau-restrictions", 0));
        entreprise.setNiveauRestrictions(niveauRestrictions);

        // Primes
        Map<String, Double> primes = loadPrimesFromYAML(entSection);
        entreprise.setPrimes(primes);

        // Transactions
        List<Transaction> transactions = loadTransactionsFromYAML(entSection);
        entreprise.setTransactionLog(transactions);

        // Activités employés
        Map<UUID, EmployeeActivityRecord> activities = loadActivitiesFromYAML(entSection);
        entreprise.setEmployeeActivityRecords(activities);

        // Production globale
        List<ProductionRecord> production = loadProductionFromYAML(entSection);
        entreprise.setGlobalProductionLog(production);

        return entreprise;
    }

    /**
     * Charge les primes depuis YAML.
     */
    private Map<String, Double> loadPrimesFromYAML(ConfigurationSection entSection) {
        Map<String, Double> primes = new HashMap<>();
        ConfigurationSection primesSection = entSection.getConfigurationSection("primes");

        if (primesSection != null) {
            for (String uuid : primesSection.getKeys(false)) {
                double prime = primesSection.getDouble(uuid, 0.0);
                primes.put(uuid, prime);
            }
        }

        return primes;
    }

    /**
     * Charge les transactions depuis YAML.
     */
    private List<Transaction> loadTransactionsFromYAML(ConfigurationSection entSection) {
        List<Transaction> transactions = new ArrayList<>();
        // Support ancien format (transactionLog) et nouveau format (transactions)
        List<Map<?, ?>> txList = entSection.getMapList("transactionLog");
        if (txList.isEmpty()) {
            txList = entSection.getMapList("transactions");
        }

        for (Map<?, ?> txMap : txList) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) txMap;

                TransactionType type = TransactionType.valueOf((String) map.get("type"));
                double amount = ((Number) map.get("amount")).doubleValue();
                String description = (String) map.getOrDefault("description", "");
                String initiatedBy = (String) map.getOrDefault("initiatedBy", "Unknown");
                String timestampStr = (String) map.get("timestamp");

                LocalDateTime timestamp = LocalDateTime.parse(timestampStr);

                transactions.add(new Transaction(type, amount, description, initiatedBy, timestamp));
                transactionsMigrees++;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Erreur chargement transaction", e);
            }
        }

        return transactions;
    }

    /**
     * Charge les activités employés depuis YAML.
     */
    private Map<UUID, EmployeeActivityRecord> loadActivitiesFromYAML(ConfigurationSection entSection) {
        Map<UUID, EmployeeActivityRecord> activities = new HashMap<>();
        // Support ancien format (employeeActivityRecords) et nouveau format (employee-activity-records)
        ConfigurationSection activitiesSection = entSection.getConfigurationSection("employeeActivityRecords");
        if (activitiesSection == null) {
            activitiesSection = entSection.getConfigurationSection("employee-activity-records");
        }

        if (activitiesSection != null) {
            for (String uuidStr : activitiesSection.getKeys(false)) {
                try {
                    ConfigurationSection actSection = activitiesSection.getConfigurationSection(uuidStr);
                    if (actSection == null) continue;

                    // employeeId peut être dans la section OU être la clé elle-même (uuidStr)
                    String employeeIdStr = actSection.getString("employeeId", uuidStr);
                    UUID employeeId = UUID.fromString(employeeIdStr);
                    String employeeName = actSection.getString("employeeName", "Unknown");

                    EmployeeActivityRecord record = new EmployeeActivityRecord(employeeId, employeeName);

                    // Sessions
                    String sessionStartStr = actSection.getString("currentSessionStartTime");
                    if (sessionStartStr != null && !sessionStartStr.equals("null")) {
                        record.currentSessionStartTime = LocalDateTime.parse(sessionStartStr);
                    }

                    String lastActivityStr = actSection.getString("lastActivityTime");
                    if (lastActivityStr != null && !lastActivityStr.equals("null")) {
                        record.lastActivityTime = LocalDateTime.parse(lastActivityStr);
                    }

                    // Valeur générée
                    record.totalValueGenerated = actSection.getDouble("totalValueGenerated", 0.0);

                    // Date d'entrée
                    String joinDateStr = actSection.getString("joinDate");
                    if (joinDateStr != null && !joinDateStr.equals("null")) {
                        record.joinDate = LocalDateTime.parse(joinDateStr);
                    }

                    // Actions (compteurs simplifiés seulement)
                    ConfigurationSection actionsSection = actSection.getConfigurationSection("actionsPerformedCount");
                    if (actionsSection != null) {
                        for (String actionKey : actionsSection.getKeys(false)) {
                            long count = actionsSection.getLong(actionKey);
                            record.actionsPerformedCount.put(actionKey, count);
                        }
                    }

                    // ⚠️ PRODUCTION DÉTAILLÉE IGNORÉE VOLONTAIREMENT
                    // On ne migre PAS le detailedProductionLog car il fait exploser la taille
                    // On garde uniquement totalValueGenerated (déjà chargé ligne 331)

                    activities.put(employeeId, record);
                    activitiesMigrees++;
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Erreur chargement activité: " + uuidStr, e);
                }
            }
        }

        return activities;
    }

    /**
     * Charge la production globale depuis YAML.
     */
    private List<ProductionRecord> loadProductionFromYAML(ConfigurationSection entSection) {
        List<ProductionRecord> production = new ArrayList<>();
        // Support ancien format (productionLog) et nouveau format (global-production-log)
        List<Map<?, ?>> prodList = entSection.getMapList("productionLog");
        if (prodList.isEmpty()) {
            prodList = entSection.getMapList("global-production-log");
        }

        for (Map<?, ?> prodMap : prodList) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) prodMap;

                LocalDateTime timestamp = LocalDateTime.parse((String) map.get("timestamp"));
                Material material = Material.valueOf((String) map.get("material"));
                int quantity = ((Number) map.get("quantity")).intValue();
                String employeeUuid = (String) map.get("recordedByEmployeeUUID");
                DetailedActionType actionType = DetailedActionType.valueOf((String) map.get("actionType"));

                production.add(new ProductionRecord(timestamp, material, quantity, employeeUuid, actionType));
            } catch (Exception e) {
                // Ignorer les enregistrements invalides
            }
        }

        return production;
    }

    /**
     * Migre toutes les entreprises vers SQLite.
     */
    private void migrateToSQLite(List<Entreprise> entreprises) {
        for (Entreprise entreprise : entreprises) {
            try {
                if (repository.save(entreprise)) {
                    entreprisesMigrees++;
                    employesMigres += entreprise.getEmployes().size();

                    // Log tous les 10 entreprises
                    if (entreprisesMigrees % 10 == 0) {
                        plugin.getLogger().info("  Progression: " + entreprisesMigrees + "/" + totalEntreprises);
                    }
                } else {
                    erreurs.add("Échec sauvegarde: " + entreprise.getNom());
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Erreur migration: " + entreprise.getNom(), e);
                erreurs.add("Erreur migration " + entreprise.getNom() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Valide la migration.
     */
    private ValidationResult validate() {
        // Vérifier le nombre d'entreprises
        int countSQLite = repository.count();
        if (countSQLite != totalEntreprises) {
            return ValidationResult.invalid(
                "Nombre d'entreprises différent: YAML=" + totalEntreprises + ", SQLite=" + countSQLite
            );
        }

        // Vérifier quelques entreprises aléatoirement
        List<Entreprise> allEntreprises = repository.findAll();
        Random random = new Random();

        for (int i = 0; i < Math.min(5, allEntreprises.size()); i++) {
            Entreprise entreprise = allEntreprises.get(random.nextInt(allEntreprises.size()));

            // Vérifier intégrité basique
            if (entreprise.getNom() == null || entreprise.getSiret() == null) {
                return ValidationResult.invalid("Entreprise invalide trouvée: " + entreprise);
            }
        }

        return ValidationResult.valid();
    }

    /**
     * Finalise la migration en renommant le fichier YAML.
     */
    private void finalizeMigration() throws IOException {
        File migratedFile = new File(plugin.getDataFolder(), "entreprises.yml.migrated");
        Files.move(yamlFile.toPath(), migratedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Rollback: restaure le backup YAML et supprime la base SQLite.
     */
    public boolean rollback(File backupFile) {
        try {
            plugin.getLogger().warning("ROLLBACK: Restauration du backup YAML...");

            // Restaurer le backup
            if (backupFile.exists()) {
                Files.copy(backupFile.toPath(), yamlFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("✓ Backup restauré");
            }

            // Note: Ne pas supprimer la DB SQLite (garde les données pour debugging)
            plugin.getLogger().warning("Base SQLite conservée pour analyse");

            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur durant le rollback", e);
            return false;
        }
    }

    /**
     * Génère un SIRET aléatoire.
     */
    private String generateSiret() {
        return String.format("%014d", new Random().nextLong(100000000000000L));
    }

    /**
     * Récupère les erreurs rencontrées.
     */
    public List<String> getErrors() {
        return erreurs;
    }

    /**
     * Résultat de validation.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String message;

        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, "OK");
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * Résultat de migration.
     */
    public static class MigrationResult {
        private final boolean success;
        private final String message;
        private final int entreprisesMigrees;
        private final long durationMs;
        private final ValidationResult validation;

        private MigrationResult(boolean success, String message, int entreprisesMigrees, long durationMs, ValidationResult validation) {
            this.success = success;
            this.message = message;
            this.entreprisesMigrees = entreprisesMigrees;
            this.durationMs = durationMs;
            this.validation = validation;
        }

        public static MigrationResult success(int count, long duration) {
            return new MigrationResult(true, "Migration réussie", count, duration, null);
        }

        public static MigrationResult error(String message) {
            return new MigrationResult(false, message, 0, 0, null);
        }

        public static MigrationResult error(String message, ValidationResult validation) {
            return new MigrationResult(false, message, 0, 0, validation);
        }

        public static MigrationResult warning(String message) {
            return new MigrationResult(true, message, 0, 0, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public int getEntreprisesMigrees() {
            return entreprisesMigrees;
        }

        public long getDurationMs() {
            return durationMs;
        }
    }
}
