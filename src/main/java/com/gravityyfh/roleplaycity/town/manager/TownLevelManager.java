package com.gravityyfh.roleplaycity.town.manager;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownLevel;
import com.gravityyfh.roleplaycity.town.data.TownLevelConfig;
import com.gravityyfh.roleplaycity.town.data.TownRole;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Gestionnaire du système d'évolution des villes
 * Gère les niveaux, upgrades, et restrictions de rôles
 */
public class TownLevelManager {
    private final RoleplayCity plugin;
    private final Map<TownLevel, TownLevelConfig> levelConfigs;

    public TownLevelManager(RoleplayCity plugin) {
        this.plugin = plugin;
        this.levelConfigs = new HashMap<>();
        loadConfigurations();
    }

    /**
     * Charge les configurations depuis config.yml
     */
    private void loadConfigurations() {
        ConfigurationSection levelSection = plugin.getConfig().getConfigurationSection("town.levels");

        if (levelSection == null) {
            plugin.getLogger().warning("Configuration 'town.levels' non trouvée, utilisation des valeurs par défaut.");
            levelConfigs.putAll(TownLevelConfig.createDefaultConfigs());
            return;
        }

        // Charger la configuration pour chaque niveau
        for (TownLevel level : TownLevel.values()) {
            String levelKey = level.name().toLowerCase();
            ConfigurationSection config = levelSection.getConfigurationSection(levelKey);

            if (config == null) {
                plugin.getLogger().warning("Configuration manquante pour le niveau " + level.getName() + ", utilisation des valeurs par défaut.");
                TownLevelConfig defaultConfig = TownLevelConfig.createDefaultConfigs().get(level);
                levelConfigs.put(level, defaultConfig);
                continue;
            }

            TownLevelConfig.Builder builder = new TownLevelConfig.Builder(level)
                .creationCost(config.getDouble("creation-cost", 5000.0))
                .upgradeCost(config.getDouble("upgrade-cost", 0))
                .minPopulation(config.getInt("min-population", 1))
                .maxPopulation(config.getInt("max-population", Integer.MAX_VALUE))
                .maxClaims(config.getInt("max-claims", 10))
                .maxPoliciers(config.getInt("max-policiers", 0))
                .maxJuges(config.getInt("max-juges", 0))
                .maxMedecins(config.getInt("max-medecins", 0))
                .policeEnabled(config.getBoolean("police-enabled", false))
                .justiceEnabled(config.getBoolean("justice-enabled", false))
                .hospitalEnabled(config.getBoolean("hospital-enabled", false));

            levelConfigs.put(level, builder.build());
            plugin.getLogger().info("Configuration chargée pour " + level.getName() + ": " + builder.build());
        }
    }

    /**
     * Récupère la configuration d'un niveau
     */
    public TownLevelConfig getConfig(TownLevel level) {
        return levelConfigs.getOrDefault(level, TownLevelConfig.createDefaultConfigs().get(level));
    }

    /**
     * Vérifie si une ville peut être upgradée
     */
    public UpgradeResult canUpgrade(Town town) {
        TownLevel currentLevel = town.getLevel();
        TownLevel nextLevel = currentLevel.getNextLevel();

        if (nextLevel == null) {
            return new UpgradeResult(false, "§cVotre ville est déjà au niveau maximum !");
        }

        TownLevelConfig nextConfig = getConfig(nextLevel);
        int population = town.getMemberCount();

        // Vérifier la population
        if (population < nextConfig.getMinPopulation()) {
            return new UpgradeResult(false, String.format(
                "§cPopulation insuffisante !\n" +
                "§7Population actuelle : §f%d joueur(s)\n" +
                "§7Population requise : §f%d joueur(s)\n\n" +
                "§eRecrutez %d joueur(s) supplémentaire(s) pour débloquer l'upgrade.",
                population,
                nextConfig.getMinPopulation(),
                nextConfig.getMinPopulation() - population
            ));
        }

        // Vérifier le coût
        double upgradeCost = nextConfig.getUpgradeCost();
        if (town.getBankBalance() < upgradeCost) {
            return new UpgradeResult(false, String.format(
                "§cFonds insuffisants dans la banque de la ville !\n" +
                "§7Solde actuel : §f%.2f€\n" +
                "§7Coût de l'upgrade : §f%.2f€\n\n" +
                "§eIl manque §c%.2f€ §edans la banque de la ville.",
                town.getBankBalance(),
                upgradeCost,
                upgradeCost - town.getBankBalance()
            ));
        }

        return new UpgradeResult(true, "§aVotre ville peut être améliorée !");
    }

    /**
     * Effectue l'upgrade d'une ville
     */
    public boolean upgradeTown(Town town, Player mayor) {
        UpgradeResult result = canUpgrade(town);
        if (!result.canUpgrade()) {
            mayor.sendMessage(result.message());
            return false;
        }

        TownLevel nextLevel = town.getLevel().getNextLevel();
        TownLevelConfig nextConfig = getConfig(nextLevel);

        // Prélever le coût
        if (!town.withdraw(nextConfig.getUpgradeCost())) {
            mayor.sendMessage("§cErreur lors du prélèvement du coût d'upgrade.");
            return false;
        }

        // Mettre à jour le niveau
        TownLevel previousLevel = town.getLevel();
        town.setLevel(nextLevel);

        // Message de succès
        mayor.sendMessage("");
        mayor.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        mayor.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "🎉 VILLE AMÉLIORÉE !");
        mayor.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        mayor.sendMessage("");
        mayor.sendMessage(ChatColor.YELLOW + "Votre ville est passée de " + previousLevel.getDisplayName() +
            ChatColor.YELLOW + " à " + nextLevel.getDisplayName() + ChatColor.YELLOW + " !");
        mayor.sendMessage("");
        mayor.sendMessage(ChatColor.AQUA + "📊 NOUVEAUX AVANTAGES :");
        mayor.sendMessage(ChatColor.GRAY + "  • Claims maximum : " + ChatColor.WHITE + nextConfig.getMaxClaims() + " chunks");
        mayor.sendMessage(ChatColor.GRAY + "  • Policiers : " + ChatColor.WHITE + nextConfig.getMaxPoliciers());
        mayor.sendMessage(ChatColor.GRAY + "  • Juges : " + ChatColor.WHITE + nextConfig.getMaxJuges());
        mayor.sendMessage(ChatColor.GRAY + "  • Médecins : " + ChatColor.WHITE + nextConfig.getMaxMedecins());
        mayor.sendMessage("");
        mayor.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        mayor.sendMessage("");

        // Notification au maire (les membres verront le changement dans le menu)
        // Pas besoin de broadcast, le changement est visible partout

        // Sauvegarder
        plugin.getTownManager().saveTownsNow();

        return true;
    }

    /**
     * Vérifie si un rôle peut être attribué selon le niveau de la ville
     */
    public RoleAssignmentResult canAssignRole(Town town, TownRole role) {
        TownLevel level = town.getLevel();
        TownLevelConfig config = getConfig(level);

        // Vérifier si le rôle est disponible à ce niveau
        if (!config.isRoleAvailable(role)) {
            return new RoleAssignmentResult(
                false,
                config.getRestrictionMessage(role, town)
            );
        }

        // Vérifier la limite du rôle
        int currentCount = town.getMembersByRole(role).size();
        int maxLimit = config.getRoleLimit(role);

        if (currentCount >= maxLimit) {
            return new RoleAssignmentResult(
                false,
                config.getRestrictionMessage(role, town)
            );
        }

        return new RoleAssignmentResult(true, "§aLe rôle peut être attribué.");
    }

    /**
     * Récupère le nombre de slots disponibles pour un rôle
     */
    public int getAvailableSlots(Town town, TownRole role) {
        TownLevelConfig config = getConfig(town.getLevel());
        int maxLimit = config.getRoleLimit(role);
        int currentCount = town.getMembersByRole(role).size();
        return Math.max(0, maxLimit - currentCount);
    }

    /**
     * Vérifie si une ville a atteint le maximum de claims
     */
    public boolean canClaimMore(Town town) {
        TownLevelConfig config = getConfig(town.getLevel());
        return town.getRealChunkCount() < config.getMaxClaims();
    }

    /**
     * Récupère le nombre de claims restants
     */
    public int getRemainingClaims(Town town) {
        TownLevelConfig config = getConfig(town.getLevel());
        return Math.max(0, config.getMaxClaims() - town.getRealChunkCount());
    }

    /**
     * Recharge les configurations depuis config.yml
     */
    public void reload() {
        levelConfigs.clear();
        loadConfigurations();
        plugin.getLogger().info("Configurations des niveaux de ville rechargées.");
    }

    /**
         * Résultat d'une vérification d'upgrade
         */
        public record UpgradeResult(boolean canUpgrade, String message) {
    }

    /**
         * Résultat d'une vérification d'attribution de rôle
         */
        public record RoleAssignmentResult(boolean canAssign, String message) {
    }
}
