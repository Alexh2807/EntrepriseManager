package com.gravityyfh.roleplaycity.town.manager;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Fine;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Gestionnaire de sauvegarde/chargement des amendes
 */
public class TownFinesDataManager {
    private final RoleplayCity plugin;
    private final File finesFile;
    private FileConfiguration finesConfig;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public TownFinesDataManager(RoleplayCity plugin) {
        this.plugin = plugin;
        this.finesFile = new File(plugin.getDataFolder(), "fines.yml");
        loadConfig();
    }

    private void loadConfig() {
        if (!finesFile.exists()) {
            try {
                finesFile.getParentFile().mkdirs();
                finesFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Impossible de créer fines.yml: " + e.getMessage());
            }
        }
        finesConfig = YamlConfiguration.loadConfiguration(finesFile);
    }

    /**
     * Sauvegarder toutes les amendes
     */
    public void saveFines(Map<String, List<Fine>> townFines) {
        finesConfig = new YamlConfiguration();

        for (Map.Entry<String, List<Fine>> entry : townFines.entrySet()) {
            String townName = entry.getKey();
            List<Fine> fines = entry.getValue();

            int index = 0;
            for (Fine fine : fines) {
                String path = "fines." + townName + "." + index;

                finesConfig.set(path + ".fine-id", fine.getFineId().toString());
                finesConfig.set(path + ".offender-uuid", fine.getOffenderUuid().toString());
                finesConfig.set(path + ".offender-name", fine.getOffenderName());
                finesConfig.set(path + ".policier-uuid", fine.getPolicierUuid().toString());
                finesConfig.set(path + ".policier-name", fine.getPolicierName());
                finesConfig.set(path + ".reason", fine.getReason());
                finesConfig.set(path + ".amount", fine.getAmount());
                finesConfig.set(path + ".issue-date", fine.getIssueDate().format(DATE_FORMAT));
                finesConfig.set(path + ".status", fine.getStatus().name());

                if (fine.getPaidDate() != null) {
                    finesConfig.set(path + ".paid-date", fine.getPaidDate().format(DATE_FORMAT));
                }

                if (fine.getContestedDate() != null) {
                    finesConfig.set(path + ".contested-date", fine.getContestedDate().format(DATE_FORMAT));
                }

                if (fine.getContestReason() != null) {
                    finesConfig.set(path + ".contest-reason", fine.getContestReason());
                }

                if (fine.getJudgeUuid() != null) {
                    finesConfig.set(path + ".judge-uuid", fine.getJudgeUuid().toString());
                    finesConfig.set(path + ".judge-verdict", fine.getJudgeVerdict());
                    finesConfig.set(path + ".judge-date", fine.getJudgeDate().format(DATE_FORMAT));
                }

                index++;
            }
        }

        try {
            finesConfig.save(finesFile);
            int totalFines = townFines.values().stream().mapToInt(List::size).sum();
            plugin.getLogger().info("Sauvegardé " + totalFines + " amendes dans fines.yml");
        } catch (IOException e) {
            plugin.getLogger().severe("Erreur lors de la sauvegarde des amendes: " + e.getMessage());
        }
    }

    /**
     * Charger toutes les amendes
     */
    public Map<String, List<Fine>> loadFines() {
        Map<String, List<Fine>> townFines = new HashMap<>();
        loadConfig();

        ConfigurationSection finesSection = finesConfig.getConfigurationSection("fines");
        if (finesSection == null) {
            plugin.getLogger().info("Aucune amende à charger.");
            return townFines;
        }

        for (String townName : finesSection.getKeys(false)) {
            List<Fine> fines = new ArrayList<>();

            ConfigurationSection townSection = finesSection.getConfigurationSection(townName);
            if (townSection == null) continue;

            for (String key : townSection.getKeys(false)) {
                try {
                    Fine fine = loadFine(townName, townSection.getConfigurationSection(key));
                    if (fine != null) {
                        fines.add(fine);
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("Erreur lors du chargement d'une amende: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            if (!fines.isEmpty()) {
                townFines.put(townName, fines);
            }
        }

        int totalFines = townFines.values().stream().mapToInt(List::size).sum();
        plugin.getLogger().info("Chargé " + totalFines + " amendes depuis fines.yml");
        return townFines;
    }

    private Fine loadFine(String townName, ConfigurationSection section) {
        if (section == null) return null;

        UUID fineId = UUID.fromString(section.getString("fine-id"));
        UUID offenderUuid = UUID.fromString(section.getString("offender-uuid"));
        String offenderName = section.getString("offender-name");
        UUID policierUuid = UUID.fromString(section.getString("policier-uuid"));
        String policierName = section.getString("policier-name");
        String reason = section.getString("reason");
        double amount = section.getDouble("amount");
        LocalDateTime issueDate = LocalDateTime.parse(section.getString("issue-date"), DATE_FORMAT);
        Fine.FineStatus status = Fine.FineStatus.valueOf(section.getString("status"));

        Fine fine = new Fine(fineId, townName, offenderUuid, offenderName,
            policierUuid, policierName, reason, amount, issueDate, status);

        // Charger les données optionnelles
        if (section.contains("paid-date")) {
            fine.setPaidDate(LocalDateTime.parse(section.getString("paid-date"), DATE_FORMAT));
        }

        if (section.contains("contested-date")) {
            fine.setContestedDate(LocalDateTime.parse(section.getString("contested-date"), DATE_FORMAT));
        }

        if (section.contains("contest-reason")) {
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
