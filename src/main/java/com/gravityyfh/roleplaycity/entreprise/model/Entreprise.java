package com.gravityyfh.roleplaycity.entreprise.model;

import com.gravityyfh.roleplaycity.util.PlayerNameResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Représente une entreprise dans le système économique.
 * Gère les employés, les finances, les transactions et la production.
 */
public class Entreprise {
    private String nom;
    private final String ville;
    private final String type;
    private final String storedGerantNom; // Nom stocké pour fallback
    private final String gerantUUID;
    private final Set<String> employesNoms;
    private final Map<String, Double> primes;
    private double solde;
    private final String siret;
    private double chiffreAffairesTotal;
    private final List<Transaction> transactionLog;
    private final Map<UUID, EmployeeActivityRecord> employeeActivityRecords;
    private final List<ProductionRecord> globalProductionLog;
    private int niveauMaxEmployes;
    private int niveauMaxSolde;
    private int niveauRestrictions;
    private double totalValue;
    private String creationDate;

    // TODO: Retirer cette dépendance statique lors du refactoring complet
    private static org.bukkit.plugin.Plugin pluginInstance;

    public static void setPluginInstance(org.bukkit.plugin.Plugin plugin) {
        pluginInstance = plugin;
    }

    /**
     * Constructeur principal d'une entreprise.
     */
    public Entreprise(
            String nom,
            String ville,
            String type,
            String gerantNom,
            String gerantUUID,
            Set<String> employesNoms,
            double solde,
            String siret) {
        this.nom = nom;
        this.ville = ville;
        this.type = type;
        this.storedGerantNom = gerantNom;
        this.gerantUUID = gerantUUID;
        this.employesNoms = ConcurrentHashMap.newKeySet();

        if (employesNoms != null) {
            this.employesNoms.addAll(employesNoms);
        }

        this.solde = solde;
        this.siret = siret;
        this.chiffreAffairesTotal = 0.0;
        this.primes = new ConcurrentHashMap<>();

        if (employesNoms != null) {
            employesNoms.forEach(nomEmp -> {
                OfflinePlayer p = Bukkit.getOfflinePlayer(nomEmp);
                if (p != null) {
                    primes.putIfAbsent(p.getUniqueId().toString(), 0.0);
                }
            });
        }

        this.transactionLog = Collections.synchronizedList(new ArrayList<>());
        this.employeeActivityRecords = new ConcurrentHashMap<>();
        this.globalProductionLog = Collections.synchronizedList(new ArrayList<>());
        this.niveauMaxEmployes = 0;
        this.niveauMaxSolde = 0;
        this.niveauRestrictions = 0;
        this.totalValue = 0.0;
        this.creationDate = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    // --- Getters ---

    public String getNom() {
        return nom;
    }

    public String getVille() {
        return ville;
    }

    public String getType() {
        return type;
    }

    /**
     * Retourne le nom actuel du gérant (résolu dynamiquement via UUID).
     * Si le joueur a changé son pseudo sur Mojang, le nouveau nom sera retourné.
     */
    public String getGerant() {
        if (gerantUUID != null) {
            try {
                UUID uuid = UUID.fromString(gerantUUID);
                return PlayerNameResolver.getName(uuid, storedGerantNom);
            } catch (IllegalArgumentException e) {
                return storedGerantNom;
            }
        }
        return storedGerantNom;
    }

    /**
     * Retourne le nom stocké du gérant (pour sauvegarde).
     */
    public String getStoredGerantNom() {
        return storedGerantNom;
    }

    public String getGerantUUID() {
        return gerantUUID;
    }

    public Set<String> getEmployes() {
        return Collections.unmodifiableSet(employesNoms);
    }

    public double getSolde() {
        return solde;
    }

    public String getSiret() {
        return siret;
    }

    public double getChiffreAffairesTotal() {
        return chiffreAffairesTotal;
    }

    public Map<String, Double> getPrimes() {
        return Collections.unmodifiableMap(primes);
    }

    public List<Transaction> getTransactionLog() {
        synchronized (transactionLog) {
            return Collections.unmodifiableList(new ArrayList<>(transactionLog));
        }
    }

    public Map<UUID, EmployeeActivityRecord> getEmployeeActivityRecords() {
        return Collections.unmodifiableMap(employeeActivityRecords);
    }

    public List<ProductionRecord> getGlobalProductionLog() {
        synchronized (globalProductionLog) {
            return Collections.unmodifiableList(new ArrayList<>(globalProductionLog));
        }
    }

    public int getNiveauMaxEmployes() {
        return niveauMaxEmployes;
    }

    public int getNiveauMaxSolde() {
        return niveauMaxSolde;
    }

    public int getNiveauRestrictions() {
        return niveauRestrictions;
    }

    public double getTotalValue() {
        return totalValue;
    }

    public String getCreationDate() {
        return creationDate;
    }

    // --- Setters ---

    public void setNom(String nom) {
        this.nom = nom;
    }

    /**
     * Accès interne aux employés (mutable).
     * ATTENTION: À utiliser avec précaution, préférer les méthodes add/remove.
     */
    public Set<String> getEmployesInternal() {
        return this.employesNoms;
    }

    public synchronized void setSolde(double solde) {
        this.solde = solde;
    }

    public synchronized void setChiffreAffairesTotal(double totalRevenue) {
        this.chiffreAffairesTotal = totalRevenue;
    }

    public void setPrimes(Map<String, Double> newPrimes) {
        this.primes.clear();
        if (newPrimes != null) {
            this.primes.putAll(newPrimes);
        }
    }

    public void setTransactionLog(List<Transaction> transactions) {
        synchronized (transactionLog) {
            this.transactionLog.clear();
            if (transactions != null) {
                this.transactionLog.addAll(transactions);
            }
        }
    }

    public void setEmployeeActivityRecords(Map<UUID, EmployeeActivityRecord> records) {
        this.employeeActivityRecords.clear();
        if (records != null) {
            this.employeeActivityRecords.putAll(records);
        }
    }

    public void setGlobalProductionLog(List<ProductionRecord> productionRecords) {
        synchronized (globalProductionLog) {
            this.globalProductionLog.clear();
            if (productionRecords != null) {
                this.globalProductionLog.addAll(productionRecords);
            }
        }
    }

    public void setNiveauMaxEmployes(int niveau) {
        this.niveauMaxEmployes = niveau;
    }

    public void setNiveauMaxSolde(int niveau) {
        this.niveauMaxSolde = niveau;
    }

    public void setNiveauRestrictions(int niveau) {
        this.niveauRestrictions = Math.max(0, Math.min(4, niveau));
    }

    public void setTotalValue(double totalValue) {
        this.totalValue = totalValue;
    }

    public void setCreationDate(String creationDate) {
        this.creationDate = creationDate;
    }

    // --- Méthodes de gestion ---

    public synchronized void retirerSolde(double amount) {
        this.solde -= amount;
    }

    public synchronized void ajouterSolde(double amount) {
        this.solde += amount;
    }

    /**
     * Ajoute une transaction au log avec rotation automatique.
     */
    public void addTransaction(Transaction tx) {
        synchronized (transactionLog) {
            this.transactionLog.add(tx);

            int maxLogSize = pluginInstance != null
                    ? pluginInstance.getConfig().getInt("entreprise.max-transaction-log-size", 200)
                    : 200;

            if (transactionLog.size() > maxLogSize) {
                transactionLog.subList(0, transactionLog.size() - maxLogSize).clear();
            }
        }
    }

    /**
     * Ajoute un enregistrement de production global avec rotation automatique.
     */
    public void addGlobalProductionRecord(
            LocalDateTime timestamp,
            Material material,
            int quantity,
            String employeeUUIDPerformingAction,
            DetailedActionType actionType) {
        synchronized (globalProductionLog) {
            this.globalProductionLog.add(
                    new ProductionRecord(timestamp, material, quantity, employeeUUIDPerformingAction, actionType));

            // FIX CRITIQUE: Rotation des logs pour éviter fuite mémoire
            int maxLogSize = pluginInstance != null
                    ? pluginInstance.getConfig().getInt("entreprise.max-production-log-size", 500)
                    : 500;

            if (globalProductionLog.size() > maxLogSize) {
                globalProductionLog.subList(0, globalProductionLog.size() - maxLogSize).clear();
            }
        }
    }

    /**
     * Récupère l'enregistrement d'activité d'un employé.
     */
    public EmployeeActivityRecord getEmployeeActivityRecord(UUID employeeId) {
        return employeeActivityRecords.get(employeeId);
    }

    /**
     * Récupère ou crée l'enregistrement d'activité d'un employé.
     */
    public EmployeeActivityRecord getOrCreateEmployeeActivityRecord(UUID employeeId, String employeeName) {
        return employeeActivityRecords.computeIfAbsent(
                employeeId,
                k -> new EmployeeActivityRecord(k, employeeName));
    }

    /**
     * Supprime l'enregistrement d'activité d'un employé (quand il quitte l'entreprise).
     * Cela évite les données orphelines en mémoire et les erreurs de clé étrangère en base.
     */
    public void removeEmployeeActivityRecord(UUID employeeId) {
        employeeActivityRecords.remove(employeeId);
    }

    /**
     * Récupère la prime d'un employé.
     */
    public double getPrimePourEmploye(String employeeUUID) {
        return this.primes.getOrDefault(employeeUUID, 0.0);
    }

    /**
     * Définit la prime d'un employé (minimum 0).
     */
    public void setPrimePourEmploye(String employeeUUID, double prime) {
        this.primes.put(employeeUUID, Math.max(0, prime));
    }

    /**
     * Retire la prime d'un employé.
     */
    public void retirerPrimeEmploye(String employeeUUID) {
        this.primes.remove(employeeUUID);
    }

    /**
     * Calcule le profit ou la perte sur une période donnée.
     */
    public double calculateProfitLoss(LocalDateTime start, LocalDateTime end) {
        synchronized (transactionLog) {
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

    /**
     * Récupère les statistiques de production d'un employé spécifique.
     */
    public Map<Material, Integer> getEmployeeProductionStatsForPeriod(
            UUID employeeUUID,
            LocalDateTime start,
            LocalDateTime end,
            DetailedActionType actionTypeFilter,
            Set<Material> relevantMaterials) {
        EmployeeActivityRecord record = getEmployeeActivityRecord(employeeUUID);
        if (record == null) {
            return Collections.emptyMap();
        }
        return record.getDetailedStatsForPeriod(actionTypeFilter, start, end, relevantMaterials);
    }

    /**
     * Récupère les statistiques de production agrégées de tous les employés.
     */
    public Map<Material, Integer> getAggregatedProductionStatsForPeriod(
            LocalDateTime start,
            LocalDateTime end,
            DetailedActionType actionTypeFilter,
            Set<Material> relevantMaterials) {
        Map<Material, Integer> aggregatedStats = new HashMap<>();

        for (EmployeeActivityRecord record : employeeActivityRecords.values()) {
            Map<Material, Integer> employeeStats = record.getDetailedStatsForPeriod(
                    actionTypeFilter,
                    start,
                    end,
                    relevantMaterials);

            employeeStats.forEach((material, quantity) -> aggregatedStats.merge(material, quantity, Integer::sum));
        }

        return aggregatedStats;
    }

    /**
     * Récupère l'ancienneté formatée d'un employé.
     */
    public String getEmployeeSeniorityFormatted(UUID employeeId) {
        EmployeeActivityRecord record = getEmployeeActivityRecord(employeeId);
        return (record != null) ? record.getFormattedSeniority() : "N/A";
    }

    /**
     * Récupère la liste des matériaux suivis pour la production de cette
     * entreprise.
     * Basé sur la configuration du type d'entreprise.
     */
    public Set<Material> getTrackedProductionMaterials() {
        Set<Material> materials = new HashSet<>();

        if (pluginInstance == null || pluginInstance.getConfig() == null) {
            return materials;
        }

        ConfigurationSection typeConfig = pluginInstance.getConfig()
                .getConfigurationSection("types-entreprise." + this.type);

        if (typeConfig == null) {
            return materials;
        }

        ConfigurationSection activitesPayantesConfig = typeConfig.getConfigurationSection("activites-payantes");

        if (activitesPayantesConfig != null) {
            for (String actionTypeKey : activitesPayantesConfig.getKeys(false)) {
                ConfigurationSection materialsConfig = activitesPayantesConfig.getConfigurationSection(actionTypeKey);

                if (materialsConfig != null) {
                    for (String materialKey : materialsConfig.getKeys(false)) {
                        Material mat = Material.matchMaterial(materialKey);
                        if (mat != null) {
                            materials.add(mat);
                        }
                    }
                }
            }
        }

        return materials;
    }

    @Override
    public String toString() {
        return "Entreprise{" +
                "nom='" + nom + '\'' +
                ", type='" + type + '\'' +
                ", gérant='" + getGerant() + '\'' +
                ", solde=" + solde +
                '}';
    }
}
