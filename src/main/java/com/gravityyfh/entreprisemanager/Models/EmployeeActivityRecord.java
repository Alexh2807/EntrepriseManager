package com.gravityyfh.entreprisemanager.Models;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class EmployeeActivityRecord {
    public final UUID employeeId;
    public String employeeName;
    public LocalDateTime currentSessionStartTime;
    public LocalDateTime lastActivityTime;
    public Map<String, Long> actionsPerformedCount;
    public double totalValueGenerated;
    public LocalDateTime joinDate;
    public List<DetailedProductionRecord> detailedProductionLog;

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

    public void startSession() {
        if (currentSessionStartTime == null) {
            currentSessionStartTime = LocalDateTime.now();
            lastActivityTime = LocalDateTime.now();
        }
    }

    public void endSession() {
        currentSessionStartTime = null;
    }

    public void recordAction(String genericActionKey, double value, int quantity, DetailedActionType detailedActionType, org.bukkit.Material material) {
        this.actionsPerformedCount.merge(genericActionKey, (long) quantity, Long::sum);
        this.totalValueGenerated += value;
        this.lastActivityTime = LocalDateTime.now();
        synchronized(detailedProductionLog) {
            this.detailedProductionLog.add(new DetailedProductionRecord(detailedActionType, material, quantity));
        }
        if (this.currentSessionStartTime == null) {
            startSession();
        }
    }

    public Map<org.bukkit.Material, Integer> getDetailedStatsForPeriod(DetailedActionType filterActionType, LocalDateTime start, LocalDateTime end, Set<org.bukkit.Material> relevantMaterials) {
        Map<org.bukkit.Material, Integer> stats = new HashMap<>();
        synchronized(detailedProductionLog){
            for (DetailedProductionRecord record : detailedProductionLog) {
                if ((filterActionType == null || record.actionType == filterActionType) &&
                        (relevantMaterials == null || relevantMaterials.contains(record.material)) &&
                        !record.timestamp.isBefore(start) && record.timestamp.isBefore(end)) {
                    stats.merge(record.material, record.quantity, Integer::sum);
                }
            }
        }
        return stats;
    }

    public boolean isActive() {
        Player player = Bukkit.getPlayer(employeeId);
        return currentSessionStartTime != null && player != null && player.isOnline();
    }

    public String getFormattedSeniority() {
        if (joinDate == null) return "N/A";
        Duration seniority = Duration.between(joinDate, LocalDateTime.now());
        long days = seniority.toDays();
        if (days > 365) return String.format("%d an(s)", days / 365);
        if (days > 30) return String.format("%d mois", days / 30);
        if (days > 0) return String.format("%d jour(s)", days);
        long hours = seniority.toHours();
        if (hours > 0) return String.format("%d heure(s)", hours);
        long minutes = seniority.toMinutes();
        return String.format("%d minute(s)", Math.max(0, minutes));
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("employeeId", employeeId.toString());
        map.put("employeeName", employeeName);
        map.put("currentSessionStartTime", currentSessionStartTime != null ? currentSessionStartTime.toString() : null);
        map.put("lastActivityTime", lastActivityTime != null ? lastActivityTime.toString() : null);
        map.put("actionsPerformedCount", actionsPerformedCount);
        map.put("totalValueGenerated", totalValueGenerated);
        map.put("joinDate", joinDate != null ? joinDate.toString() : null);
        synchronized(detailedProductionLog){
            map.put("detailedProductionLog", detailedProductionLog.stream().map(DetailedProductionRecord::serialize).collect(Collectors.toList()));
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    public static EmployeeActivityRecord deserialize(Map<String, Object> map) {
        try {
            UUID id = UUID.fromString((String) map.get("employeeId"));
            String name = (String) map.get("employeeName");
            EmployeeActivityRecord record = new EmployeeActivityRecord(id, name);
            if (map.get("currentSessionStartTime") != null) record.currentSessionStartTime = LocalDateTime.parse((String) map.get("currentSessionStartTime"));
            if (map.get("lastActivityTime") != null) record.lastActivityTime = LocalDateTime.parse((String) map.get("lastActivityTime"));
            if (map.get("actionsPerformedCount") instanceof Map) {
                Map<?,?> rawMap = (Map<?,?>) map.get("actionsPerformedCount");
                rawMap.forEach((key, value) -> {
                    if (key instanceof String && value instanceof Number) record.actionsPerformedCount.put((String) key, ((Number)value).longValue());
                });
            }
            record.totalValueGenerated = ((Number) map.getOrDefault("totalValueGenerated", 0.0)).doubleValue();
            if (map.get("joinDate") != null) record.joinDate = LocalDateTime.parse((String) map.get("joinDate"));
            else record.joinDate = null;
            if (map.containsKey("detailedProductionLog")) {
                List<Map<String, Object>> rawList = (List<Map<String, Object>>) map.get("detailedProductionLog");
                if(rawList != null) {
                    synchronized(record.detailedProductionLog){
                        record.detailedProductionLog.clear();
                        for (Map<String, Object> item : rawList) {
                            DetailedProductionRecord prodRecord = DetailedProductionRecord.deserialize(item);
                            if (prodRecord != null) record.detailedProductionLog.add(prodRecord);
                        }
                    }
                }
            }
            return record;
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.WARNING, "Erreur désérialisation EmployeeActivityRecord pour map: " + map, e);
            return null;
        }
    }
}