package com.gravityyfh.entreprisemanager.Services;

import com.gravityyfh.entreprisemanager.EntrepriseManager;
import com.gravityyfh.entreprisemanager.Models.Entreprise;
import com.gravityyfh.entreprisemanager.Models.*;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Gère la persistance des données (sauvegarde et chargement des fichiers yml).
 */
public class DataManager {

    private final EntrepriseManager plugin;
    private final File entrepriseFile;
    private final File playerHistoryFile;

    public DataManager(EntrepriseManager plugin) {
        this.plugin = plugin;
        this.entrepriseFile = new File(plugin.getDataFolder(), "entreprise.yml");
        this.playerHistoryFile = new File(plugin.getDataFolder(), "player_history.yml");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Entreprise> loadEnterprises(Map<String, Double> activiteHoraireValeur) {
        Map<String, Entreprise> loadedEnterprises = new HashMap<>();
        if (!entrepriseFile.exists()) {
            plugin.getLogger().info("entreprise.yml non trouvé. Un nouveau sera créé à la prochaine sauvegarde.");
            return loadedEnterprises;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(entrepriseFile);
        ConfigurationSection entreprisesSection = config.getConfigurationSection("entreprises");
        if (entreprisesSection == null) {
            plugin.getLogger().info("Aucune section 'entreprises' dans entreprise.yml.");
            return loadedEnterprises;
        }

        for (String nomEnt : entreprisesSection.getKeys(false)) {
            try {
                String path = nomEnt + ".";
                String ville = entreprisesSection.getString(path + "ville");
                String type = entreprisesSection.getString(path + "type");
                String gerantNom = entreprisesSection.getString(path + "gerantNom");
                String gerantUUIDStr = entreprisesSection.getString(path + "gerantUUID");
                double solde = entreprisesSection.getDouble(path + "solde", 0.0);
                String siret = entreprisesSection.getString(path + "siret", UUID.randomUUID().toString().replace("-","").substring(0, 14));
                double caTotal = entreprisesSection.getDouble(path + "chiffreAffairesTotal", 0.0);
                double caHorairePotentiel = entreprisesSection.getDouble(path + "activiteHoraireValeur", 0.0);
                int niveauMaxEmployes = entreprisesSection.getInt(path + "niveauMaxEmployes", 0);
                int niveauMaxSolde = entreprisesSection.getInt(path + "niveauMaxSolde", 0);

                if (gerantNom == null || gerantUUIDStr == null || type == null || ville == null) {
                    plugin.getLogger().severe("Données essentielles manquantes pour l'entreprise '" + nomEnt + "'. Elle ne sera pas chargée.");
                    continue;
                }

                Set<String> employesSet = new HashSet<>();
                Map<String, Double> primesMap = new HashMap<>();
                ConfigurationSection employesSect = entreprisesSection.getConfigurationSection(path + "employes");
                if (employesSect != null) {
                    for (String uuidStr : employesSect.getKeys(false)) {
                        try {
                            UUID empUuid = UUID.fromString(uuidStr);
                            OfflinePlayer p = Bukkit.getOfflinePlayer(empUuid);
                            if (p != null && p.getName() != null) {
                                employesSet.add(p.getName());
                                primesMap.put(uuidStr, employesSect.getDouble(uuidStr + ".prime", 0.0));
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("UUID invalide pour un employé dans l'entreprise " + nomEnt + ": " + uuidStr);
                        }
                    }
                }

                List<Transaction> transactionLogList = new ArrayList<>();
                List<Map<?, ?>> rawTxList = entreprisesSection.getMapList(path + "transactionLog");
                for (Map<?, ?> rawMap : rawTxList) {
                    Transaction tx = Transaction.deserialize((Map<String, Object>) rawMap);
                    if (tx != null) transactionLogList.add(tx);
                }

                Map<UUID, EmployeeActivityRecord> activitiesMap = new HashMap<>();
                ConfigurationSection activityRecordsSect = entreprisesSection.getConfigurationSection(path + "employeeActivityRecords");
                if(activityRecordsSect != null) {
                    for(String uuidStr : activityRecordsSect.getKeys(false)) {
                        Map<String, Object> recordData = (Map<String, Object>) Objects.requireNonNull(activityRecordsSect.getConfigurationSection(uuidStr)).getValues(true);
                        EmployeeActivityRecord rec = EmployeeActivityRecord.deserialize(recordData);
                        if(rec != null) activitiesMap.put(UUID.fromString(uuidStr), rec);
                    }
                }

                Entreprise ent = new Entreprise(nomEnt, ville, type, gerantNom, gerantUUIDStr, employesSet, solde, siret);
                ent.setChiffreAffairesTotal(caTotal);
                ent.setPrimes(primesMap);
                ent.setTransactionLog(transactionLogList);
                ent.setEmployeeActivityRecords(activitiesMap);
                ent.setNiveauMaxEmployes(niveauMaxEmployes);
                ent.setNiveauMaxSolde(niveauMaxSolde);

                loadedEnterprises.put(nomEnt, ent);
                activiteHoraireValeur.put(nomEnt, caHorairePotentiel);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Erreur majeure lors du chargement de l'entreprise '" + nomEnt + "'.", e);
            }
        }
        plugin.getLogger().info(loadedEnterprises.size() + " entreprises chargées depuis entreprise.yml.");
        return loadedEnterprises;
    }

    public void saveEnterprises(Map<String, Entreprise> entreprises, Map<String, Double> activiteHoraireValeur) {
        FileConfiguration config = new YamlConfiguration();
        ConfigurationSection entreprisesSection = config.createSection("entreprises");

        entreprises.forEach((nomEnt, ent) -> {
            String path = nomEnt + ".";
            entreprisesSection.set(path + "ville", ent.getVille());
            entreprisesSection.set(path + "type", ent.getType());
            entreprisesSection.set(path + "gerantNom", ent.getGerant());
            entreprisesSection.set(path + "gerantUUID", ent.getGerantUUID());
            entreprisesSection.set(path + "solde", ent.getSolde());
            entreprisesSection.set(path + "siret", ent.getSiret());
            entreprisesSection.set(path + "chiffreAffairesTotal", ent.getChiffreAffairesTotal());
            entreprisesSection.set(path + "activiteHoraireValeur", activiteHoraireValeur.getOrDefault(nomEnt, 0.0));
            entreprisesSection.set(path + "niveauMaxEmployes", ent.getNiveauMaxEmployes());
            entreprisesSection.set(path + "niveauMaxSolde", ent.getNiveauMaxSolde());

            ConfigurationSection employesSect = entreprisesSection.createSection(path + "employes");
            ent.getPrimes().forEach((uuidStr, primeVal) -> employesSect.set(uuidStr + ".prime", primeVal));

            entreprisesSection.set(path + "transactionLog", ent.getTransactionLog().stream().map(Transaction::serialize).collect(Collectors.toList()));

            ConfigurationSection activityRecordsSect = entreprisesSection.createSection(path + "employeeActivityRecords");
            ent.getEmployeeActivityRecords().forEach((uuid, record) -> activityRecordsSect.set(uuid.toString(), record.serialize()));
        });

        try {
            config.save(entrepriseFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossible de sauvegarder entreprise.yml", e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<UUID, List<PastExperience>> loadPlayerHistory() {
        Map<UUID, List<PastExperience>> historyCache = new ConcurrentHashMap<>();
        if (!playerHistoryFile.exists()) {
            return historyCache;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(playerHistoryFile);
        ConfigurationSection historySection = config.getConfigurationSection("player-history");
        if (historySection == null) {
            return historyCache;
        }

        for (String uuidStr : historySection.getKeys(false)) {
            try {
                UUID playerUUID = UUID.fromString(uuidStr);
                List<Map<?, ?>> rawList = historySection.getMapList(uuidStr);
                List<PastExperience> experiences = rawList.stream()
                        .map(map -> PastExperience.deserialize((Map<String, Object>) map))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                historyCache.put(playerUUID, Collections.synchronizedList(experiences));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Erreur lors du chargement de l'historique pour l'UUID " + uuidStr, e);
            }
        }
        plugin.getLogger().info(historyCache.size() + " joueurs avec un historique chargé.");
        return historyCache;
    }

    public void savePlayerHistory(Map<UUID, List<PastExperience>> playerHistoryCache) {
        FileConfiguration config = new YamlConfiguration();
        ConfigurationSection historySection = config.createSection("player-history");
        playerHistoryCache.forEach((uuid, experiences) -> {
            synchronized (experiences) {
                historySection.set(uuid.toString(), experiences.stream().map(PastExperience::serialize).collect(Collectors.toList()));
            }
        });
        try {
            config.save(playerHistoryFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossible de sauvegarder " + playerHistoryFile.getName(), e);
        }
    }
}