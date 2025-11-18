package com.gravityyfh.roleplaycity.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * FIX BASSE #25: Validation config.yml au startup
 *
 * Valide toutes les entrées du fichier de configuration pour détecter:
 * - Clés manquantes
 * - Types de données incorrects
 * - Valeurs hors limites raisonnables
 * - Incohérences logiques
 * - Configurations potentiellement dangereuses
 */
public class ConfigValidator {

    private final Plugin plugin;
    private final FileConfiguration config;
    private final List<String> errors;
    private final List<String> warnings;

    public ConfigValidator(Plugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
    }

    /**
     * Valide l'ensemble de la configuration
     * @return true si la configuration est valide (aucune erreur critique)
     */
    public boolean validate() {
        plugin.getLogger().info("Validation de la configuration...");

        // Validation des sections principales
        validateEntrepriseTypes();
        validateFinanceSection();
        validateInvitationSection();
        validateSiretSection();
        validateShopDeletionSection();
        validateTownSection();
        validateTownLevels();
        validatePredefinedFines();

        // Rapport des résultats
        reportResults();

        return errors.isEmpty();
    }

    /**
     * Valide les types d'entreprise
     */
    private void validateEntrepriseTypes() {
        ConfigurationSection types = config.getConfigurationSection("types-entreprise");
        if (types == null) {
            errors.add("Section 'types-entreprise' manquante !");
            return;
        }

        Set<String> typeNames = types.getKeys(false);
        if (typeNames.isEmpty()) {
            errors.add("Aucun type d'entreprise défini !");
            return;
        }

        for (String typeName : typeNames) {
            String path = "types-entreprise." + typeName;

            // Vérifier description
            if (!config.contains(path + ".description")) {
                warnings.add(typeName + ": Description manquante");
            }

            // Vérifier coût création
            double coutCreation = config.getDouble(path + ".cout-creation", -1);
            if (coutCreation < 0) {
                errors.add(typeName + ": cout-creation manquant ou invalide");
            } else if (coutCreation < 1000) {
                warnings.add(typeName + ": cout-creation très bas (" + coutCreation + "€)");
            } else if (coutCreation > 50000) {
                warnings.add(typeName + ": cout-creation très élevé (" + coutCreation + "€)");
            }

            // Vérifier limite non-membre
            int limite = config.getInt(path + ".limite-non-membre-par-heure", -1);
            if (limite < 0) {
                errors.add(typeName + ": limite-non-membre-par-heure manquante");
            }

            // Vérifier activités payantes
            ConfigurationSection activites = config.getConfigurationSection(path + ".activites-payantes");
            if (activites == null || activites.getKeys(false).isEmpty()) {
                warnings.add(typeName + ": Aucune activité payante définie");
            } else {
                validateActivitesPayantes(typeName, activites);
            }

            // Vérifier message d'erreur restriction
            if (!config.contains(path + ".message-erreur-restriction")) {
                warnings.add(typeName + ": message-erreur-restriction manquant");
            }
        }
    }

    /**
     * Valide les activités payantes d'un type d'entreprise
     */
    private void validateActivitesPayantes(String typeName, ConfigurationSection activites) {
        for (String actionType : activites.getKeys(false)) {
            ConfigurationSection items = activites.getConfigurationSection(actionType);
            if (items == null) continue;

            for (String itemName : items.getKeys(false)) {
                double montant = items.getDouble(itemName, -1);
                if (montant < 0) {
                    errors.add(typeName + "." + actionType + "." + itemName + ": montant négatif !");
                } else if (montant == 0) {
                    warnings.add(typeName + "." + actionType + "." + itemName + ": montant à 0");
                } else if (montant > 1000) {
                    warnings.add(typeName + "." + actionType + "." + itemName + ": montant très élevé (" + montant + "€)");
                }
            }
        }
    }

    /**
     * Valide la section finance
     */
    private void validateFinanceSection() {
        if (!config.contains("finance")) {
            errors.add("Section 'finance' manquante !");
            return;
        }

        // Max entreprises par gérant
        int maxEntreprises = config.getInt("finance.max-entreprises-par-gerant", -1);
        if (maxEntreprises < 1) {
            errors.add("finance.max-entreprises-par-gerant invalide (doit être >= 1)");
        } else if (maxEntreprises > 10) {
            warnings.add("finance.max-entreprises-par-gerant très élevé (" + maxEntreprises + ")");
        }

        // Max travail joueur
        int maxTravail = config.getInt("finance.max-travail-joueur", -1);
        if (maxTravail < 1) {
            errors.add("finance.max-travail-joueur invalide (doit être >= 1)");
        }

        // Pourcentage taxes
        double taxes = config.getDouble("finance.pourcentage-taxes", -1);
        if (taxes < 0 || taxes > 100) {
            errors.add("finance.pourcentage-taxes invalide (doit être entre 0 et 100)");
        } else if (taxes > 50) {
            warnings.add("finance.pourcentage-taxes très élevé (" + taxes + "%)");
        }

        // Charge salariale
        double chargeSalariale = config.getDouble("finance.charge-salariale-par-employe-horaire", -1);
        if (chargeSalariale < 0) {
            errors.add("finance.charge-salariale-par-employe-horaire invalide");
        }

        // Allocation chômage
        double allocChomage = config.getDouble("finance.allocation-chomage-horaire", -1);
        if (allocChomage < 0) {
            errors.add("finance.allocation-chomage-horaire invalide");
        } else if (allocChomage > 1000) {
            warnings.add("finance.allocation-chomage-horaire très élevé (" + allocChomage + "€)");
        }

        // Validation des progressions (max-employer)
        validateProgression("finance.max-employer-par-entreprise", 2, 100);
        validateProgression("finance.max-solde-par-niveau", 1000, 1000000);

        // Validation des coûts d'amélioration
        validateCostsProgression("finance.cout-amelioration-niveau-max-employer");
        validateCostsProgression("finance.cout-amelioration-niveau-max-solde");
    }

    /**
     * Valide une progression de valeurs par niveau
     */
    private void validateProgression(String path, int min, int max) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            errors.add(path + ": Section manquante");
            return;
        }

        int previousValue = 0;
        for (String level : section.getKeys(false)) {
            int value = section.getInt(level, -1);
            if (value < min || value > max) {
                errors.add(path + "." + level + ": Valeur hors limites (" + value + ", attendu " + min + "-" + max + ")");
            }
            if (value <= previousValue) {
                errors.add(path + "." + level + ": Valeur non croissante (" + value + " <= " + previousValue + ")");
            }
            previousValue = value;
        }
    }

    /**
     * Valide une progression de coûts
     */
    private void validateCostsProgression(String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            errors.add(path + ": Section manquante");
            return;
        }

        double previousCost = 0;
        for (String level : section.getKeys(false)) {
            double cost = section.getDouble(level, -1);
            if (cost < 0) {
                errors.add(path + "." + level + ": Coût négatif !");
            }
            if (cost <= previousCost) {
                warnings.add(path + "." + level + ": Coût non croissant");
            }
            previousCost = cost;
        }
    }

    /**
     * Valide la section invitation
     */
    private void validateInvitationSection() {
        int distanceMax = config.getInt("invitation.distance-max", -1);
        if (distanceMax < 1) {
            errors.add("invitation.distance-max invalide (doit être >= 1)");
        } else if (distanceMax > 100) {
            warnings.add("invitation.distance-max très élevé (" + distanceMax + " blocs)");
        }
    }

    /**
     * Valide la section SIRET
     */
    private void validateSiretSection() {
        int longueur = config.getInt("siret.longueur", -1);
        if (longueur < 8 || longueur > 20) {
            errors.add("siret.longueur invalide (recommandé: 8-20)");
        }
    }

    /**
     * Valide la section shop-deletion-on-events
     */
    private void validateShopDeletionSection() {
        if (!config.contains("shop-deletion-on-events")) {
            warnings.add("Section 'shop-deletion-on-events' manquante");
            return;
        }

        // Vérifier que ce sont des booléens
        String[] events = {
            "resident-leave-town", "resident-kick-from-town",
            "plot-owner-change", "plot-clear", "plot-type-change",
            "town-unclaim-plot", "town-ruin"
        };

        for (String event : events) {
            String path = "shop-deletion-on-events." + event;
            if (!config.contains(path)) {
                warnings.add(path + ": Configuration manquante");
            }
        }
    }

    /**
     * Valide la section town
     */
    private void validateTownSection() {
        if (!config.contains("town")) {
            errors.add("Section 'town' manquante !");
            return;
        }

        // Coûts de base
        validateCost("town.creation-cost", 1000, 100000);
        validateCost("town.join-cost", 0, 10000);
        validateCost("town.rename-cost", 100, 50000);
        validateCost("town.claim-cost-per-chunk", 100, 10000);

        // Expiration invitation
        int expirationHours = config.getInt("town.invitation-expiration-hours", -1);
        if (expirationHours < 1 || expirationHours > 168) {
            errors.add("town.invitation-expiration-hours invalide (recommandé: 1-168h)");
        }

        // Remboursement unclaim
        double refundPct = config.getDouble("town.unclaim-refund-percentage", -1);
        if (refundPct < 0 || refundPct > 100) {
            errors.add("town.unclaim-refund-percentage invalide (doit être 0-100%)");
        }

        // Commissions
        validatePercentage("town.commissions.police-commission-percentage");
        validatePercentage("town.commissions.judge-commission-percentage");
        validatePercentage("town.commissions.court-fees-percentage");
    }

    /**
     * Valide un coût
     */
    private void validateCost(String path, double min, double max) {
        double cost = config.getDouble(path, -1);
        if (cost < min) {
            errors.add(path + ": Coût trop bas (" + cost + "€, min: " + min + "€)");
        } else if (cost > max) {
            warnings.add(path + ": Coût très élevé (" + cost + "€, max recommandé: " + max + "€)");
        }
    }

    /**
     * Valide un pourcentage
     */
    private void validatePercentage(String path) {
        double pct = config.getDouble(path, -1);
        if (pct < 0 || pct > 100) {
            errors.add(path + ": Pourcentage invalide (doit être 0-100%)");
        }
    }

    /**
     * Valide les niveaux de ville
     */
    private void validateTownLevels() {
        ConfigurationSection levels = config.getConfigurationSection("town.levels");
        if (levels == null) {
            errors.add("town.levels: Section manquante !");
            return;
        }

        String[] expectedLevels = {"campement", "village", "ville"};
        for (String levelName : expectedLevels) {
            String path = "town.levels." + levelName;
            if (!config.contains(path)) {
                errors.add(path + ": Niveau manquant !");
                continue;
            }

            // Vérifier les champs requis
            validateTownLevel(path);
        }

        // Vérifier la cohérence entre niveaux
        validateTownLevelsProgression();
    }

    /**
     * Valide un niveau de ville spécifique
     */
    private void validateTownLevel(String path) {
        // Coûts
        validateCost(path + ".creation-cost", 0, 100000);
        validateCost(path + ".upgrade-cost", 0, 100000);

        // Population
        int minPop = config.getInt(path + ".min-population", -1);
        int maxPop = config.getInt(path + ".max-population", -1);
        if (minPop < 1) {
            errors.add(path + ".min-population invalide");
        }
        if (maxPop < minPop && maxPop != 999999) {
            errors.add(path + ": max-population < min-population");
        }

        // Claims
        int maxClaims = config.getInt(path + ".max-claims", -1);
        if (maxClaims < 1) {
            errors.add(path + ".max-claims invalide");
        }

        // Personnel
        int maxPoliciers = config.getInt(path + ".max-policiers", -1);
        int maxJuges = config.getInt(path + ".max-juges", -1);
        int maxMedecins = config.getInt(path + ".max-medecins", -1);

        if (maxPoliciers < 0 || maxJuges < 0 || maxMedecins < 0) {
            errors.add(path + ": Personnel municipal avec valeurs négatives");
        }

        // Services (doivent être des booléens)
        if (!config.isBoolean(path + ".police-enabled")) {
            errors.add(path + ".police-enabled doit être boolean");
        }
        if (!config.isBoolean(path + ".justice-enabled")) {
            errors.add(path + ".justice-enabled doit être boolean");
        }
        if (!config.isBoolean(path + ".hospital-enabled")) {
            errors.add(path + ".hospital-enabled doit être boolean");
        }
    }

    /**
     * Valide la progression entre niveaux de ville
     */
    private void validateTownLevelsProgression() {
        double campCreation = config.getDouble("town.levels.campement.creation-cost");
        double villageCreation = config.getDouble("town.levels.village.creation-cost");
        double villeCreation = config.getDouble("town.levels.ville.creation-cost");

        if (villageCreation <= campCreation) {
            warnings.add("town.levels: Coût village <= campement");
        }
        if (villeCreation <= villageCreation) {
            warnings.add("town.levels: Coût ville <= village");
        }

        int campClaims = config.getInt("town.levels.campement.max-claims");
        int villageClaims = config.getInt("town.levels.village.max-claims");
        int villeClaims = config.getInt("town.levels.ville.max-claims");

        if (villageClaims <= campClaims) {
            warnings.add("town.levels: Claims village <= campement");
        }
        if (villeClaims <= villageClaims) {
            warnings.add("town.levels: Claims ville <= village");
        }
    }

    /**
     * Valide les amendes prédéfinies
     */
    private void validatePredefinedFines() {
        List<?> fines = config.getList("town.predefined-fines");
        if (fines == null || fines.isEmpty()) {
            warnings.add("town.predefined-fines: Aucune amende prédéfinie");
            return;
        }

        for (int i = 0; i < fines.size(); i++) {
            Object fine = fines.get(i);
            if (!(fine instanceof ConfigurationSection fineSection)) continue;

            String title = fineSection.getString("title");
            double amount = fineSection.getDouble("amount", -1);
            String description = fineSection.getString("description");

            if (title == null || title.isEmpty()) {
                warnings.add("town.predefined-fines[" + i + "]: Titre manquant");
            }
            if (amount < 0) {
                errors.add("town.predefined-fines[" + i + "]: Montant invalide");
            }
            if (description == null || description.isEmpty()) {
                warnings.add("town.predefined-fines[" + i + "]: Description manquante");
            }
        }
    }

    /**
     * Affiche les résultats de la validation
     */
    private void reportResults() {
        if (errors.isEmpty() && warnings.isEmpty()) {
            plugin.getLogger().info("✅ Configuration valide ! Aucun problème détecté.");
            return;
        }

        if (!errors.isEmpty()) {
            plugin.getLogger().severe("╔═══════════════════════════════════════════════════════════════╗");
            plugin.getLogger().severe("║  ERREURS CRITIQUES DE CONFIGURATION (" + errors.size() + ")");
            plugin.getLogger().severe("╚═══════════════════════════════════════════════════════════════╝");
            for (String error : errors) {
                plugin.getLogger().severe("  ✗ " + error);
            }
        }

        if (!warnings.isEmpty()) {
            plugin.getLogger().warning("╔═══════════════════════════════════════════════════════════════╗");
            plugin.getLogger().warning("║  AVERTISSEMENTS DE CONFIGURATION (" + warnings.size() + ")");
            plugin.getLogger().warning("╚═══════════════════════════════════════════════════════════════╝");
            for (String warning : warnings) {
                plugin.getLogger().warning("  ⚠ " + warning);
            }
        }

        if (!errors.isEmpty()) {
            plugin.getLogger().severe("⚠ Le plugin peut ne pas fonctionner correctement avec ces erreurs !");
        }
    }

    public List<String> getErrors() {
        return errors;
    }

    public List<String> getWarnings() {
        return warnings;
    }
}
