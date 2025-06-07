package com.gravityyfh.entreprisemanager.Models;

import com.gravityyfh.entreprisemanager.EntrepriseManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Entreprise {
    private String nom;
    private final String ville;
    private final String type;
    private final String gerantNom;
    private final String gerantUUID;
    private final Set<String> employesNoms;
    private final Map<String, Double> primes;
    private double solde;
    private final String siret;
    private double chiffreAffairesTotal;
    private List<Transaction> transactionLog;
    private Map<UUID, EmployeeActivityRecord> employeeActivityRecords;
    private List<ProductionRecord> globalProductionLog;
    private int niveauMaxEmployes;
    private int niveauMaxSolde;

    public Entreprise(String nom, String ville, String type, String gerantNom, String gerantUUID, Set<String> employesNoms, double solde, String siret) {
        this.nom = nom;
        this.ville = ville;
        this.type = type;
        this.gerantNom = gerantNom;
        this.gerantUUID = gerantUUID;
        this.employesNoms = (employesNoms != null) ? ConcurrentHashMap.newKeySet() : ConcurrentHashMap.newKeySet();
        if (employesNoms != null) this.employesNoms.addAll(employesNoms);
        this.solde = solde;
        this.siret = siret;
        this.chiffreAffairesTotal = 0.0;
        this.primes = new ConcurrentHashMap<>();
        if (employesNoms != null) {
            employesNoms.forEach(nomEmp -> {
                OfflinePlayer p = Bukkit.getOfflinePlayer(nomEmp);
                if (p != null) primes.putIfAbsent(p.getUniqueId().toString(), 0.0);
            });
        }
        this.transactionLog = Collections.synchronizedList(new ArrayList<>());
        this.employeeActivityRecords = new ConcurrentHashMap<>();
        this.globalProductionLog = Collections.synchronizedList(new ArrayList<>());
        this.niveauMaxEmployes = 0;
        this.niveauMaxSolde = 0;
    }

    // --- Getters ---
    public String getNom() { return nom; }
    public String getVille() { return ville; }
    public String getType() { return type; }
    public String getGerant() { return gerantNom; }
    public String getGerantUUID() { return gerantUUID; }
    public Set<String> getEmployes() { return Collections.unmodifiableSet(employesNoms); }
    public double getSolde() { return solde; }
    public String getSiret() { return siret; }
    public double getChiffreAffairesTotal() { return chiffreAffairesTotal; }
    public Map<String, Double> getPrimes() { return Collections.unmodifiableMap(primes); }
    public List<Transaction> getTransactionLog() { synchronized(transactionLog) { return Collections.unmodifiableList(new ArrayList<>(transactionLog)); } }
    public Map<UUID, EmployeeActivityRecord> getEmployeeActivityRecords() { return Collections.unmodifiableMap(employeeActivityRecords); }
    public List<ProductionRecord> getGlobalProductionLog() { synchronized(globalProductionLog) { return Collections.unmodifiableList(new ArrayList<>(globalProductionLog)); } }
    public int getNiveauMaxEmployes() { return niveauMaxEmployes; }
    public int getNiveauMaxSolde() { return niveauMaxSolde; }

    // --- Setters & Modifiers ---
    public void setNom(String nom) { this.nom = nom; }
    public Set<String> getEmployesInternal() { return this.employesNoms; }
    public synchronized void setSolde(double solde) { this.solde = solde; }
    public synchronized void setChiffreAffairesTotal(double ca) { this.chiffreAffairesTotal = ca; }
    public void setPrimes(Map<String, Double> p) { this.primes.clear(); if (p != null) this.primes.putAll(p); }
    public void setTransactionLog(List<Transaction> log) { synchronized(transactionLog) { this.transactionLog.clear(); if (log != null) this.transactionLog.addAll(log); } }
    public void setEmployeeActivityRecords(Map<UUID, EmployeeActivityRecord> r) { this.employeeActivityRecords.clear(); if (r != null) this.employeeActivityRecords.putAll(r); }
    public void setGlobalProductionLog(List<ProductionRecord> log) { synchronized(globalProductionLog) { this.globalProductionLog.clear(); if (log != null) this.globalProductionLog.addAll(log); } }
    public void setNiveauMaxEmployes(int niveau) { this.niveauMaxEmployes = niveau; }
    public void setNiveauMaxSolde(int niveau) { this.niveauMaxSolde = niveau; }

    public void addTransaction(Transaction tx) {
        synchronized(transactionLog) {
            this.transactionLog.add(tx);
            int maxLogSize = EntrepriseManager.getInstance().getConfig().getInt("entreprise.max-transaction-log-size", 200);
            if(transactionLog.size() > maxLogSize) {
                transactionLog.subList(0, transactionLog.size() - maxLogSize).clear();
            }
        }
    }

    public void addGlobalProductionRecord(LocalDateTime ts, Material m, int q, String employeeUUIDPerformingAction, DetailedActionType actionType) {
        synchronized(globalProductionLog) {
            this.globalProductionLog.add(new ProductionRecord(ts, m, q, employeeUUIDPerformingAction, actionType));
        }
    }

    public EmployeeActivityRecord getEmployeeActivityRecord(UUID employeeId) {
        return employeeActivityRecords.get(employeeId);
    }

    public EmployeeActivityRecord getOrCreateEmployeeActivityRecord(UUID employeeId, String employeeName) {
        return employeeActivityRecords.computeIfAbsent(employeeId, k -> new EmployeeActivityRecord(k, employeeName));
    }

    public double getPrimePourEmploye(String employeeUUID) {
        return this.primes.getOrDefault(employeeUUID, 0.0);
    }

    public void setPrimePourEmploye(String employeeUUID, double prime) {
        this.primes.put(employeeUUID, Math.max(0, prime));
    }

    public void retirerPrimeEmploye(String employeeUUID) {
        this.primes.remove(employeeUUID);
    }

    // --- Calculs et Logique Spécifique ---
    public double calculateProfitLoss(LocalDateTime start, LocalDateTime end) {
        synchronized(transactionLog) {
            double income = 0;
            double expense = 0;
            for (Transaction tx : transactionLog) {
                if (!tx.timestamp.isBefore(start) && tx.timestamp.isBefore(end)) {
                    if (tx.type.isOperationalIncome()) {
                        income += tx.amount;
                    } else if (tx.type.isOperationalExpense()) {
                        expense += Math.abs(tx.amount);
                    }
                }
            }
            return income - expense;
        }
    }

    public Map<Material, Integer> getEmployeeProductionStatsForPeriod(UUID employeeUUID, LocalDateTime start, LocalDateTime end, DetailedActionType actionTypeFilter, Set<Material> relevantMaterials) {
        EmployeeActivityRecord record = getEmployeeActivityRecord(employeeUUID);
        if (record == null) return Collections.emptyMap();
        return record.getDetailedStatsForPeriod(actionTypeFilter, start, end, relevantMaterials);
    }

    public Map<Material, Integer> getAggregatedProductionStatsForPeriod(LocalDateTime start, LocalDateTime end, DetailedActionType actionTypeFilter, Set<Material> relevantMaterials) {
        Map<Material, Integer> aggregatedStats = new HashMap<>();
        for (EmployeeActivityRecord record : employeeActivityRecords.values()) {
            Map<Material, Integer> employeeStats = record.getDetailedStatsForPeriod(actionTypeFilter, start, end, relevantMaterials);
            employeeStats.forEach((material, quantity) -> aggregatedStats.merge(material, quantity, Integer::sum));
        }
        return aggregatedStats;
    }

    public String getEmployeeSeniorityFormatted(UUID employeeId) {
        EmployeeActivityRecord record = getEmployeeActivityRecord(employeeId);
        return (record != null) ? record.getFormattedSeniority() : "N/A";
    }

    public Set<Material> getTrackedProductionMaterials() {
        Set<Material> materials = new HashSet<>();
        EntrepriseManager plugin = EntrepriseManager.getInstance();
        if (plugin == null || plugin.getConfig() == null) return materials;

        ConfigurationSection typeConfig = plugin.getConfig().getConfigurationSection("types-entreprise." + this.type);
        if (typeConfig == null) return materials;

        ConfigurationSection activitesPayantesConfig = typeConfig.getConfigurationSection("activites-payantes");
        if (activitesPayantesConfig != null) {
            for (String actionTypeKey : activitesPayantesConfig.getKeys(false)) {
                ConfigurationSection materialsConfig = activitesPayantesConfig.getConfigurationSection(actionTypeKey);
                if (materialsConfig != null) {
                    for (String materialKey : materialsConfig.getKeys(false)) {
                        Material mat = Material.matchMaterial(materialKey);
                        if (mat != null) materials.add(mat);
                    }
                }
            }
        }
        return materials;
    }

    @Override
    public String toString() {
        return "Entreprise{nom='" + nom + "', type='" + type + "', gérant='" + gerantNom + "', solde=" + solde + "}";
    }
}