package com.gravityyfh.roleplaycity.entreprise.model;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Enregistrement de l'activité d'un employé dans une entreprise.
 * Suit les sessions de travail, les actions effectuées, et la valeur générée.
 */
public class EmployeeActivityRecord {
    public final UUID employeeId;
    public String employeeName;
    public LocalDateTime currentSessionStartTime;
    public LocalDateTime lastActivityTime;
    public Map<String, Long> actionsPerformedCount;
    public double totalValueGenerated;
    public LocalDateTime joinDate;
    public List<DetailedProductionRecord> detailedProductionLog;

    // TODO: Retirer cette dépendance statique lors du refactoring complet
    private static org.bukkit.plugin.Plugin pluginInstance;

    public static void setPluginInstance(org.bukkit.plugin.Plugin plugin) {
        pluginInstance = plugin;
    }

    public EmployeeActivityRecord(UUID employeeId, String employeeName) {
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.currentSessionStartTime = null;
        this.lastActivityTime = null;
        this.actionsPerformedCount = new ConcurrentHashMap<>();
        this.totalValueGenerated = 0;
        this.joinDate = LocalDateTime.now();
        this.detailedProductionLog = Collections.synchronizedList(new ArrayList<>());
    }

    /**
     * Démarre une session de travail pour cet employé.
     */
    public void startSession() {
        if (currentSessionStartTime == null) {
            currentSessionStartTime = LocalDateTime.now();
            lastActivityTime = LocalDateTime.now();

            if (pluginInstance != null && Bukkit.getPlayer(employeeId) != null) {
                pluginInstance.getLogger().fine("Session démarrée pour " + employeeName);
            }
        }
    }

    /**
     * Termine la session de travail en cours.
     */
    public void endSession() {
        if (currentSessionStartTime != null) {
            if (pluginInstance != null && Bukkit.getPlayer(employeeId) != null) {
                String duration = (lastActivityTime != null)
                    ? Duration.between(currentSessionStartTime, lastActivityTime).toMinutes() + "min"
                    : "N/A";
                pluginInstance.getLogger().fine("Session terminée pour " + employeeName + ". Durée: " + duration);
            }
            currentSessionStartTime = null;
        }
    }

    /**
     * Enregistre une action productive effectuée par l'employé.
     * @param genericActionKey Clé générique de l'action (ex: "BLOCK_BROKEN:STONE")
     * @param value Valeur économique générée
     * @param quantity Quantité d'items/blocks
     * @param detailedActionType Type détaillé de l'action
     * @param material Matériau concerné
     */
    public void recordAction(
        String genericActionKey,
        double value,
        int quantity,
        DetailedActionType detailedActionType,
        Material material
    ) {
        this.actionsPerformedCount.merge(genericActionKey, (long) quantity, Long::sum);
        this.totalValueGenerated += value;
        this.lastActivityTime = LocalDateTime.now();

        synchronized (detailedProductionLog) {
            this.detailedProductionLog.add(new DetailedProductionRecord(detailedActionType, material, quantity));

            // FIX CRITIQUE: Rotation des logs détaillés par employé (évite millions d'enregistrements)
            int maxLogSize = pluginInstance != null
                ? pluginInstance.getConfig().getInt("entreprise.max-detailed-production-log-size", 1000)
                : 1000;

            if (detailedProductionLog.size() > maxLogSize) {
                detailedProductionLog.subList(0, detailedProductionLog.size() - maxLogSize).clear();
            }
        }

        if (this.currentSessionStartTime == null) {
            startSession();
        }
    }

    /**
     * Récupère les statistiques détaillées pour une période donnée.
     * @param filterActionType Type d'action à filtrer (null = tous)
     * @param start Début de la période
     * @param end Fin de la période
     * @param relevantMaterials Matériaux à inclure (null = tous)
     * @return Map Material -> Quantité
     */
    public Map<Material, Integer> getDetailedStatsForPeriod(
        DetailedActionType filterActionType,
        LocalDateTime start,
        LocalDateTime end,
        Set<Material> relevantMaterials
    ) {
        Map<Material, Integer> stats = new HashMap<>();

        synchronized (detailedProductionLog) {
            for (DetailedProductionRecord record : detailedProductionLog) {
                boolean matchesActionType = (filterActionType == null || record.actionType() == filterActionType);
                boolean matchesMaterial = (relevantMaterials == null || relevantMaterials.contains(record.material()));
                boolean inTimeRange = !record.timestamp().isBefore(start) && record.timestamp().isBefore(end);

                if (matchesActionType && matchesMaterial && inTimeRange) {
                    stats.merge(record.material(), record.quantity(), Integer::sum);
                }
            }
        }

        return stats;
    }

    /**
     * Vérifie si l'employé est actuellement actif (en session et en ligne).
     */
    public boolean isActive() {
        Player player = Bukkit.getPlayer(employeeId);
        return currentSessionStartTime != null && player != null && player.isOnline();
    }

    /**
     * Retourne l'ancienneté formatée de façon lisible.
     */
    public String getFormattedSeniority() {
        if (joinDate == null) {
            return "N/A";
        }

        Duration seniority = Duration.between(joinDate, LocalDateTime.now());
        long days = seniority.toDays();
        long hours = seniority.toHours() % 24;
        long minutes = seniority.toMinutes() % 60;

        if (days > 365) {
            return String.format("%d an(s)", days / 365);
        }
        if (days > 30) {
            return String.format("%d mois", days / 30);
        }
        if (days > 0) {
            return String.format("%d j, %dh", days, hours);
        }
        if (hours > 0) {
            return String.format("%dh, %dmin", hours, minutes);
        }
        return String.format("%d min", Math.max(0, minutes));
    }

    /**
     * Sérialise le record en Map pour sauvegarde YAML.
     */
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("employeeId", employeeId.toString());
        map.put("employeeName", employeeName);
        map.put("currentSessionStartTime", currentSessionStartTime != null ? currentSessionStartTime.toString() : null);
        map.put("lastActivityTime", lastActivityTime != null ? lastActivityTime.toString() : null);
        map.put("actionsPerformedCount", actionsPerformedCount);
        map.put("totalValueGenerated", totalValueGenerated);
        map.put("joinDate", joinDate != null ? joinDate.toString() : null);

        synchronized (detailedProductionLog) {
            map.put("detailedProductionLog",
                detailedProductionLog.stream()
                    .map(DetailedProductionRecord::serialize)
                    .collect(Collectors.toList())
            );
        }

        return map;
    }

    /**
     * Désérialise un EmployeeActivityRecord depuis une Map YAML.
     */
    public static EmployeeActivityRecord deserialize(Map<String, Object> map) {
        try {
            UUID id = UUID.fromString((String) map.get("employeeId"));
            String name = (String) map.get("employeeName");
            EmployeeActivityRecord record = new EmployeeActivityRecord(id, name);

            if (map.get("currentSessionStartTime") != null) {
                record.currentSessionStartTime = LocalDateTime.parse((String) map.get("currentSessionStartTime"));
            }

            if (map.get("lastActivityTime") != null) {
                record.lastActivityTime = LocalDateTime.parse((String) map.get("lastActivityTime"));
            }

            if (map.get("actionsPerformedCount") instanceof Map<?, ?> rawMap) {
                rawMap.forEach((key, value) -> {
                    if (key instanceof String && value instanceof Number) {
                        record.actionsPerformedCount.put((String) key, ((Number) value).longValue());
                    }
                });
            }

            record.totalValueGenerated = ((Number) map.getOrDefault("totalValueGenerated", 0.0)).doubleValue();

            if (map.get("joinDate") != null) {
                record.joinDate = LocalDateTime.parse((String) map.get("joinDate"));
            } else {
                record.joinDate = null;
            }

            if (map.containsKey("detailedProductionLog")) {
                List<?> rawList = (List<?>) map.get("detailedProductionLog");
                if (rawList != null) {
                    synchronized (record.detailedProductionLog) {
                        record.detailedProductionLog.clear();
                        for (Object item : rawList) {
                            if (item instanceof Map) {
                                @SuppressWarnings("unchecked")
                                DetailedProductionRecord prodRecord = DetailedProductionRecord.deserialize((Map<String, Object>) item);
                                if (prodRecord != null) {
                                    record.detailedProductionLog.add(prodRecord);
                                }
                            }
                        }
                    }
                }
            }

            return record;
        } catch (Exception e) {
            if (pluginInstance != null) {
                pluginInstance.getLogger().log(Level.WARNING, "Erreur désérialisation EmployeeActivityRecord pour map: " + map, e);
            }
            return null;
        }
    }
}
