package com.gravityyfh.roleplaycity.town.data;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration d'un niveau de ville
 * Contient tous les paramètres configurables depuis config.yml
 */
public class TownLevelConfig {
    private final TownLevel level;
    private final double creationCost;
    private final double upgradeCost;
    private final int minPopulation;
    private final int maxPopulation;
    private final int maxClaims;

    // Limites de rôles municipaux
    private final int maxPoliciers;
    private final int maxJuges;
    private final int maxMedecins;

    // Services municipaux disponibles
    private final boolean policeEnabled;
    private final boolean justiceEnabled;
    private final boolean hospitalEnabled;

    private TownLevelConfig(Builder builder) {
        this.level = builder.level;
        this.creationCost = builder.creationCost;
        this.upgradeCost = builder.upgradeCost;
        this.minPopulation = builder.minPopulation;
        this.maxPopulation = builder.maxPopulation;
        this.maxClaims = builder.maxClaims;
        this.maxPoliciers = builder.maxPoliciers;
        this.maxJuges = builder.maxJuges;
        this.maxMedecins = builder.maxMedecins;
        this.policeEnabled = builder.policeEnabled;
        this.justiceEnabled = builder.justiceEnabled;
        this.hospitalEnabled = builder.hospitalEnabled;
    }

    // Getters
    public TownLevel getLevel() { return level; }
    public double getCreationCost() { return creationCost; }
    public double getUpgradeCost() { return upgradeCost; }
    public int getMinPopulation() { return minPopulation; }
    public int getMaxPopulation() { return maxPopulation; }
    public int getMaxClaims() { return maxClaims; }
    public int getMaxPoliciers() { return maxPoliciers; }
    public int getMaxJuges() { return maxJuges; }
    public int getMaxMedecins() { return maxMedecins; }
    public boolean isPoliceEnabled() { return policeEnabled; }
    public boolean isJusticeEnabled() { return justiceEnabled; }
    public boolean isHospitalEnabled() { return hospitalEnabled; }

    /**
     * Récupère la limite maximale pour un rôle spécifique
     */
    public int getRoleLimit(TownRole role) {
        return switch (role) {
            case POLICIER -> maxPoliciers;
            case JUGE -> maxJuges;
            case MEDECIN -> maxMedecins;
            default -> Integer.MAX_VALUE; // Pas de limite pour les autres rôles
        };
    }

    /**
     * Vérifie si un service municipal est disponible à ce niveau
     */
    public boolean isServiceEnabled(String serviceName) {
        return switch (serviceName.toLowerCase()) {
            case "police" -> policeEnabled;
            case "justice", "tribunal" -> justiceEnabled;
            case "medical", "hopital", "hôpital" -> hospitalEnabled;
            default -> false;
        };
    }

    /**
     * Vérifie si un rôle est disponible à ce niveau
     */
    public boolean isRoleAvailable(TownRole role) {
        return switch (role) {
            case POLICIER -> policeEnabled && maxPoliciers > 0;
            case JUGE -> justiceEnabled && maxJuges > 0;
            case MEDECIN -> hospitalEnabled && maxMedecins > 0;
            default -> true; // Les autres rôles (Maire, Adjoint, Citoyen, etc.) sont toujours disponibles
        };
    }

    /**
     * Retourne un message d'erreur personnalisé selon le niveau et le rôle
     */
    public String getRestrictionMessage(TownRole role, Town town) {
        if (!level.allowsMunicipalStaff()) {
            // Campement - aucun personnel municipal
            return String.format(
                "§c⚠ Impossible !\n" +
                "§7Votre ville est trop petite pour attribuer ce rôle.\n\n" +
                "§eStatut actuel : §f%s §7(%d-%d joueurs)\n" +
                "§eRôle demandé : §f%s\n" +
                "§eCondition requise : §f%s §7(%d+ joueurs)\n\n" +
                "§6➜ Améliorez votre ville pour débloquer ce poste !",
                level.getDisplayName(),
                level.getMinPopulation(),
                level.getMaxPopulation(),
                role.getDisplayName(),
                TownLevel.VILLAGE.getDisplayName(),
                TownLevel.VILLAGE.getMinPopulation()
            );
        }

        // Limite atteinte pour un rôle spécifique
        int currentCount = town.getMembersByRole(role).size();
        int maxLimit = getRoleLimit(role);
        TownLevel nextLevel = level.getNextLevel();

        if (nextLevel != null) {
            return String.format(
                "§c⚠ Limite atteinte pour le rôle : §e%s\n\n" +
                "§7Votre statut actuel : §f%s\n" +
                "§7Limite actuelle : §f%d %s(s) maximum §7(actuellement: %d)\n" +
                "§6Améliorez votre ville au rang §e%s §6pour en nommer davantage.",
                role.getDisplayName(),
                level.getDisplayName(),
                maxLimit,
                role.getDisplayName(),
                currentCount,
                nextLevel.getDisplayName()
            );
        } else {
            return String.format(
                "§c⚠ Limite atteinte pour le rôle : §e%s\n\n" +
                "§7Votre statut actuel : §f%s §7(niveau maximum)\n" +
                "§7Limite : §f%d %s(s) maximum §7(actuellement: %d)\n" +
                "§cVous avez atteint le nombre maximum autorisé.",
                role.getDisplayName(),
                level.getDisplayName(),
                maxLimit,
                role.getDisplayName(),
                currentCount
            );
        }
    }

    // Builder Pattern
    public static class Builder {
        private final TownLevel level;
        private double creationCost = 0;
        private double upgradeCost = 0;
        private int minPopulation = 1;
        private int maxPopulation = Integer.MAX_VALUE;
        private int maxClaims = 10;
        private int maxPoliciers = 0;
        private int maxJuges = 0;
        private int maxMedecins = 0;
        private boolean policeEnabled = false;
        private boolean justiceEnabled = false;
        private boolean hospitalEnabled = false;

        public Builder(TownLevel level) {
            this.level = level;
        }

        public Builder creationCost(double cost) {
            this.creationCost = cost;
            return this;
        }

        public Builder upgradeCost(double cost) {
            this.upgradeCost = cost;
            return this;
        }

        public Builder minPopulation(int min) {
            this.minPopulation = min;
            return this;
        }

        public Builder maxPopulation(int max) {
            this.maxPopulation = max;
            return this;
        }

        public Builder maxClaims(int max) {
            this.maxClaims = max;
            return this;
        }

        public Builder maxPoliciers(int max) {
            this.maxPoliciers = max;
            return this;
        }

        public Builder maxJuges(int max) {
            this.maxJuges = max;
            return this;
        }

        public Builder maxMedecins(int max) {
            this.maxMedecins = max;
            return this;
        }

        public Builder policeEnabled(boolean enabled) {
            this.policeEnabled = enabled;
            return this;
        }

        public Builder justiceEnabled(boolean enabled) {
            this.justiceEnabled = enabled;
            return this;
        }

        public Builder hospitalEnabled(boolean enabled) {
            this.hospitalEnabled = enabled;
            return this;
        }

        public TownLevelConfig build() {
            return new TownLevelConfig(this);
        }
    }

    @Override
    public String toString() {
        return String.format("TownLevelConfig{level=%s, cost=%.2f€, claims=%d, police=%d, judges=%d, medics=%d}",
            level.getName(), creationCost, maxClaims, maxPoliciers, maxJuges, maxMedecins);
    }

    /**
     * Crée une map avec les valeurs par défaut selon les spécifications
     */
    public static Map<TownLevel, TownLevelConfig> createDefaultConfigs() {
        Map<TownLevel, TownLevelConfig> configs = new HashMap<>();

        // CAMPEMENT : 5 000€, 1-4 joueurs, 10 chunks, aucun service municipal
        configs.put(TownLevel.CAMPEMENT, new Builder(TownLevel.CAMPEMENT)
            .creationCost(5000.0)
            .upgradeCost(0) // Premier niveau, pas d'upgrade
            .minPopulation(1)
            .maxPopulation(4)
            .maxClaims(10)
            .maxPoliciers(0)
            .maxJuges(0)
            .maxMedecins(0)
            .policeEnabled(false)
            .justiceEnabled(false)
            .hospitalEnabled(false)
            .build());

        // VILLAGE : +8 000€, 5-14 joueurs, 130 chunks, services limités
        configs.put(TownLevel.VILLAGE, new Builder(TownLevel.VILLAGE)
            .creationCost(5000.0 + 8000.0) // Coût total depuis le début
            .upgradeCost(8000.0) // Coût pour upgrade depuis Campement
            .minPopulation(5)
            .maxPopulation(14)
            .maxClaims(130)
            .maxPoliciers(1)
            .maxJuges(1)
            .maxMedecins(2)
            .policeEnabled(true)
            .justiceEnabled(true)
            .hospitalEnabled(true)
            .build());

        // VILLE : +15 000€, 15+ joueurs, 250 chunks, tous services
        configs.put(TownLevel.VILLE, new Builder(TownLevel.VILLE)
            .creationCost(5000.0 + 8000.0 + 15000.0) // Coût total depuis le début
            .upgradeCost(15000.0) // Coût pour upgrade depuis Village
            .minPopulation(15)
            .maxPopulation(Integer.MAX_VALUE)
            .maxClaims(250)
            .maxPoliciers(5)
            .maxJuges(5)
            .maxMedecins(10)
            .policeEnabled(true)
            .justiceEnabled(true)
            .hospitalEnabled(true)
            .build());

        return configs;
    }
}
