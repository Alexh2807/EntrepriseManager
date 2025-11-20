package com.gravityyfh.roleplaycity.town.manager;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Fine;
import com.gravityyfh.roleplaycity.town.data.Fine.FineStatus;
import com.gravityyfh.roleplaycity.town.service.FinesPersistenceService;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;

/**
 * Gestionnaire de sauvegarde/chargement des amendes
 */
public class TownFinesDataManager {
    private final RoleplayCity plugin;
    private final FinesPersistenceService persistenceService;
    private final File finesFile;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public TownFinesDataManager(RoleplayCity plugin, FinesPersistenceService persistenceService) {
        this.plugin = plugin;
        this.persistenceService = persistenceService;
        this.finesFile = new File(plugin.getDataFolder(), "fines.yml");
    }

    /**
     * Sauvegarder toutes les amendes (SQLite)
     */
    public void saveFines(Map<String, List<Fine>> townFines) {
        persistenceService.saveFines(townFines);
    }

    /**
     * Charger toutes les amendes (SQLite)
     */
    public Map<String, List<Fine>> loadFines() {
        return persistenceService.loadFines();
    }

    // ====================================================================================
    // LEGACY YAML SUPPORT
    // ====================================================================================

    public Map<String, List<Fine>> loadFromLegacyYAML() {
        Map<String, List<Fine>> townFines = new HashMap<>();
        
        if (!finesFile.exists()) return townFines;
        FileConfiguration finesConfig = YamlConfiguration.loadConfiguration(finesFile);

        ConfigurationSection finesSection = finesConfig.getConfigurationSection("fines");
        if (finesSection == null) return townFines;

        for (String townName : finesSection.getKeys(false)) {
            List<Fine> fines = new ArrayList<>();
            ConfigurationSection townSection = finesSection.getConfigurationSection(townName);
            if (townSection == null) continue;

            for (String key : townSection.getKeys(false)) {
                try {
                    Fine fine = loadFineLegacy(townName, townSection.getConfigurationSection(key));
                    if (fine != null) fines.add(fine);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Erreur lors du chargement legacy d'une amende", e);
                }
            }

            if (!fines.isEmpty()) townFines.put(townName, fines);
        }

        return townFines;
    }

    private Fine loadFineLegacy(String townName, ConfigurationSection section) {
        if (section == null) return null;

        UUID fineId = UUID.fromString(section.getString("fine-id"));
        UUID offenderUuid = UUID.fromString(section.getString("offender-uuid"));
        String offenderName = section.getString("offender-name");
        UUID policierUuid = UUID.fromString(section.getString("policier-uuid"));
        String policierName = section.getString("policier-name");
        String reason = section.getString("reason");
        double amount = section.getDouble("amount");
        LocalDateTime issueDate = LocalDateTime.parse(section.getString("issue-date"), DATE_FORMAT);
        FineStatus status = FineStatus.valueOf(section.getString("status"));

        Fine fine = new Fine(fineId, townName, offenderUuid, offenderName,
            policierUuid, policierName, reason, amount, issueDate, status);

        if (section.contains("paid-date")) fine.setPaidDate(LocalDateTime.parse(section.getString("paid-date"), DATE_FORMAT));
        if (section.contains("contested-date")) {
            fine.setContestedDate(LocalDateTime.parse(section.getString("contested-date"), DATE_FORMAT));
            fine.setContestReason(section.getString("contest-reason"));
        }
        if (section.contains("judge-uuid")) {
            fine.setJudgeUuid(UUID.fromString(section.getString("judge-uuid")));
            fine.setJudgeVerdict(section.getString("judge-verdict"));
            fine.setJudgeDate(LocalDateTime.parse(section.getString("judge-date"), DATE_FORMAT));
        }

        return fine;
    }
}
