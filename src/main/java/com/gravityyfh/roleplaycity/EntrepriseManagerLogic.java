package com.gravityyfh.roleplaycity;

// --- Imports ---
// Towny imports supprimés - Nous utilisons maintenant notre propre système de ville
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
// import org.bukkit.World; // N'est plus nécessaire si deserializeLocation est supprimé
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class EntrepriseManagerLogic {
    // FIX CRITIQUE: Retirer 'static' pour éviter race conditions et problèmes de synchronisation
    // Le plugin reste statique pour être accessible aux classes internes
    public static RoleplayCity plugin;
    private Map<String, Entreprise> entreprises;
    // FIX PERFORMANCE HAUTE: Index SIRET pour recherche O(1) au lieu de O(n)
    private Map<String, Entreprise> entreprisesBySiret;
    private File entrepriseFile;

    private BukkitTask activityCheckTask;
    private BukkitTask inactivityKickTask; // FIX MOYENNE: Task pour vérifier les employés inactifs
    private LocalDateTime nextPaymentTime; // <-- CHAMP À AJOUTER
    private static final long INACTIVITY_THRESHOLD_SECONDS = 15;

    // FIX MOYENNE: Constantes pour auto-kick des employés inactifs
    private static final int INACTIVITY_WARNING_DAYS = 30; // Avertissement après 30 jours
    private static final int INACTIVITY_KICK_DAYS = 45; // Licenciement après 45 jours
    private static final long INACTIVITY_CHECK_INTERVAL_TICKS = 20L * 60L * 60L * 24L; // 1 fois par jour

    // --- Historique ---
    private static File playerHistoryFile;
    private static Map<UUID, List<PastExperience>> playerHistoryCache;
    // --- Fin Historique ---

    // --- MODIFICATION: Renamed and added new map for shop revenue ---
    private final Map<String, Double> activiteProductiveHoraireValeur = new ConcurrentHashMap<>();
    private final Map<String, Double> activiteMagasinHoraireValeur = new ConcurrentHashMap<>();
    // --- END MODIFICATION ---

    private final Map<String, String> invitations = new ConcurrentHashMap<>();
    private final Map<UUID, DemandeCreation> demandesEnAttente = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, ActionInfo>> joueurActivitesRestrictions = new ConcurrentHashMap<>();

    // FIX HAUTE: Système de confirmation pour suppression d'entreprise
    // Map: UUID joueur -> Nom entreprise à supprimer
    private final Map<UUID, String> suppressionsEnAttente = new ConcurrentHashMap<>();

    // FIX MOYENNE: Système de confirmation pour retrait d'argent
    // Map: UUID joueur -> Pair<Nom entreprise, Montant>
    private static class WithdrawalRequest {
        final String entrepriseName;
        final double amount;
        final long timestamp;
        WithdrawalRequest(String entrepriseName, double amount) {
            this.entrepriseName = entrepriseName;
            this.amount = amount;
            this.timestamp = System.currentTimeMillis();
        }
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CONFIRMATION_TIMEOUT_MS;
        }
    }
    private final Map<UUID, WithdrawalRequest> retraitsEnAttente = new ConcurrentHashMap<>();

    // FIX MOYENNE: Système de confirmation pour licenciement employé
    // Map: UUID gérant -> Pair<Nom entreprise, Nom employé>
    private static class KickRequest {
        final String entrepriseName;
        final String employeeName;
        final long timestamp;
        KickRequest(String entrepriseName, String employeeName) {
            this.entrepriseName = entrepriseName;
            this.employeeName = employeeName;
            this.timestamp = System.currentTimeMillis();
        }
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CONFIRMATION_TIMEOUT_MS;
        }
    }
    private final Map<UUID, KickRequest> kicksEnAttente = new ConcurrentHashMap<>();

    // FIX PERFORMANCE HAUTE: Cache des restrictions pour éviter O(n) à chaque action
    // Structure: "ACTION_TYPE:MATERIAL" -> List<RestrictionInfo>
    private final Map<String, List<RestrictionInfo>> restrictionsCache = new ConcurrentHashMap<>();
    // FIX MOYENNE: Ajouter un TTL (Time To Live) au cache des restrictions
    private long restrictionsCacheLoadTime = 0;
    private static final long RESTRICTIONS_CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

    private BukkitTask hourlyTask;
    private static final long ACTIVITY_CHECK_INTERVAL_TICKS = 20L * 10L;

    // FIX MOYENNE: Constantes pour les timeouts de confirmation (magic numbers)
    private static final long CONFIRMATION_TIMEOUT_MS = 30000; // 30 secondes
    private static final long CONFIRMATION_TIMEOUT_TICKS = 600L; // 30 secondes (20 ticks/sec * 30)

    // Classe pour stocker les infos de restriction (pour le cache)
    private static class RestrictionInfo {
        final String entrepriseType;
        final int limiteNonMembre;
        final List<String> messagesErreur;

        RestrictionInfo(String entrepriseType, int limiteNonMembre, List<String> messagesErreur) {
            this.entrepriseType = entrepriseType;
            this.limiteNonMembre = limiteNonMembre;
            this.messagesErreur = messagesErreur;
        }
    }


    // --- Enumérations et Classes Internes ---
    public enum TransactionType {
        DEPOSIT("Dépôt de Capital"), WITHDRAWAL("Retrait de Capital"), REVENUE("Revenu d'Activité"),
        TAXES("Impôts sur Revenu"), PRIMES("Paiement des Primes"), OTHER_EXPENSE("Autre Dépense Op."),
        OTHER_INCOME("Autre Revenu Op."), CREATION_COST("Frais de Création"),
        RENAME_COST("Frais de Renommage"),
        PAYROLL_TAX("Charges Salariales");

        private final String displayName;
        TransactionType(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
        public boolean isOperationalIncome() { return this == REVENUE || this == OTHER_INCOME; }
        public boolean isOperationalExpense() { return this == TAXES || this == PRIMES || this == OTHER_EXPENSE || this == CREATION_COST || this == RENAME_COST || this == PAYROLL_TAX; }
    }
    public static class Transaction {
        public final TransactionType type; public final double amount;
        public final String description; public final LocalDateTime timestamp;
        public final String initiatedBy;
        public Transaction(TransactionType type, double amount, String description, String initiatedBy) {
            this.type = type;
            if (type.isOperationalExpense() && amount > 0) this.amount = -amount;
            else if (type == TransactionType.WITHDRAWAL && amount > 0) this.amount = -amount;
            else this.amount = amount;
            this.description = description != null ? description : "";
            this.timestamp = LocalDateTime.now();
            this.initiatedBy = initiatedBy != null ? initiatedBy : "System";
        }
        public Transaction(TransactionType type, double amount, String description, String initiatedBy, LocalDateTime timestamp) {
            this.type = type;
            if (type.isOperationalExpense() && amount > 0) this.amount = -amount;
            else if (type == TransactionType.WITHDRAWAL && amount > 0) this.amount = -amount;
            else this.amount = amount;
            this.description = description != null ? description : "";
            this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
            this.initiatedBy = initiatedBy != null ? initiatedBy : "System";
        }
        public Map<String, Object> serialize() {
            Map<String, Object> map = new HashMap<>();
            map.put("type", type.name()); map.put("amount", amount); map.put("description", description);
            map.put("timestamp", timestamp.toString()); map.put("initiatedBy", initiatedBy); return map;
        }
        public static Transaction deserialize(Map<String, Object> map) {
            try {
                TransactionType transacType = TransactionType.valueOf((String)map.get("type"));
                double transacAmount = ((Number)map.get("amount")).doubleValue();
                String transacDesc = (String)map.getOrDefault("description", "");
                String transacInitiator = (String)map.getOrDefault("initiatedBy", "Unknown");
                LocalDateTime transacTimestamp = LocalDateTime.parse((String)map.get("timestamp"));
                return new Transaction(transacType, transacAmount, transacDesc, transacInitiator, transacTimestamp);
            } catch (Exception e) { if (plugin != null) plugin.getLogger().warning("Erreur désérialisation Transaction: " + e.getMessage() + " pour map: " + map); return null; }
        }
        @Override public String toString() { return String.format("[%s] %s: %.2f€ (%s) par %s", timestamp.format(DateTimeFormatter.ofPattern("dd/MM HH:mm")), type.getDisplayName(), amount, description, initiatedBy); }
    }
    public enum DetailedActionType { BLOCK_BROKEN("Bloc Cassé"), ITEM_CRAFTED("Item Crafté"), BLOCK_PLACED("Bloc Posé");
        private final String displayName; DetailedActionType(String displayName) { this.displayName = displayName; } public String getDisplayName() { return displayName; }
    }
    public static class DetailedProductionRecord {
        public final LocalDateTime timestamp; public final DetailedActionType actionType;
        public final Material material; public final int quantity;
        public DetailedProductionRecord(DetailedActionType actionType, Material material, int quantity) { this.timestamp = LocalDateTime.now(); this.actionType = actionType; this.material = material; this.quantity = quantity; }
        public DetailedProductionRecord(LocalDateTime timestamp, DetailedActionType actionType, Material material, int quantity) { this.timestamp = timestamp; this.actionType = actionType; this.material = material; this.quantity = quantity; }
        public Map<String, Object> serialize() { Map<String, Object> map = new HashMap<>(); map.put("timestamp", timestamp.toString()); map.put("actionType", actionType.name()); map.put("material", material.name()); map.put("quantity", quantity); return map; }
        public static DetailedProductionRecord deserialize(Map<String, Object> map) {
            try { LocalDateTime ts = LocalDateTime.parse((String) map.get("timestamp")); DetailedActionType dat = DetailedActionType.valueOf((String) map.get("actionType")); Material mat = Material.matchMaterial((String) map.get("material")); int qty = ((Number) map.get("quantity")).intValue(); if (mat == null) { if (plugin != null) plugin.getLogger().warning("Material null désérialisation DPR: " + map.get("material")); return null; } return new DetailedProductionRecord(ts, dat, mat, qty); }
            catch (Exception e) { if (plugin != null) plugin.getLogger().warning("Erreur désérialisation DPR: " + e.getMessage() + " pour map: " + map); return null; }
        }
    }
    public static class EmployeeActivityRecord {
        public final UUID employeeId; public String employeeName;
        public LocalDateTime currentSessionStartTime; public LocalDateTime lastActivityTime;
        public Map<String, Long> actionsPerformedCount; public double totalValueGenerated;
        public LocalDateTime joinDate; public List<DetailedProductionRecord> detailedProductionLog;
        public EmployeeActivityRecord(UUID employeeId, String employeeName) { this.employeeId = employeeId; this.employeeName = employeeName; this.currentSessionStartTime = null; this.lastActivityTime = null; this.actionsPerformedCount = new ConcurrentHashMap<>(); this.totalValueGenerated = 0; this.joinDate = LocalDateTime.now(); this.detailedProductionLog = Collections.synchronizedList(new ArrayList<>()); }
        public void startSession() { if (currentSessionStartTime == null) { currentSessionStartTime = LocalDateTime.now(); lastActivityTime = LocalDateTime.now(); if (plugin != null && Bukkit.getPlayer(employeeId) != null) plugin.getLogger().fine("Session démarrée pour " + employeeName); } }
        public void endSession() { if (currentSessionStartTime != null) { if (plugin != null && Bukkit.getPlayer(employeeId) != null) plugin.getLogger().fine("Session terminée pour " + employeeName + ". Durée: " + (lastActivityTime != null ? Duration.between(currentSessionStartTime, lastActivityTime).toMinutes() + "min" : "N/A")); currentSessionStartTime = null; } }
        public void recordAction(String genericActionKey, double value, int quantity, DetailedActionType detailedActionType, Material material) {
            this.actionsPerformedCount.merge(genericActionKey, (long) quantity, Long::sum);
            this.totalValueGenerated += value;
            this.lastActivityTime = LocalDateTime.now();
            synchronized(detailedProductionLog) {
                this.detailedProductionLog.add(new DetailedProductionRecord(detailedActionType, material, quantity));
                // FIX CRITIQUE: Rotation des logs détaillés par employé (évite millions d'enregistrements)
                int maxLogSize = plugin.getConfig().getInt("entreprise.max-detailed-production-log-size", 1000);
                if(detailedProductionLog.size() > maxLogSize)
                    detailedProductionLog.subList(0, detailedProductionLog.size() - maxLogSize).clear();
            }
            if (this.currentSessionStartTime == null) { startSession(); }
        }
        public Map<Material, Integer> getDetailedStatsForPeriod(DetailedActionType filterActionType, LocalDateTime start, LocalDateTime end, Set<Material> relevantMaterials) { Map<Material, Integer> stats = new HashMap<>(); synchronized(detailedProductionLog){ for (DetailedProductionRecord record : detailedProductionLog) { if ((filterActionType == null || record.actionType == filterActionType) && (relevantMaterials == null || relevantMaterials.contains(record.material)) && !record.timestamp.isBefore(start) && record.timestamp.isBefore(end)) { stats.merge(record.material, record.quantity, Integer::sum); } } } return stats; }
        public boolean isActive() { Player player = Bukkit.getPlayer(employeeId); return currentSessionStartTime != null && player != null && player.isOnline(); }
        public String getFormattedSeniority() { if (joinDate == null) return "N/A"; Duration seniority = Duration.between(joinDate, LocalDateTime.now()); long days = seniority.toDays(); long hours = seniority.toHours() % 24; long minutes = seniority.toMinutes() % 60; if (days > 365) return String.format("%d an(s)", days / 365); if (days > 30) return String.format("%d mois", days / 30); if (days > 0) return String.format("%d j, %dh", days, hours); if (hours > 0) return String.format("%dh, %dmin", hours, minutes); return String.format("%d min", Math.max(0, minutes)); }
        public Map<String, Object> serialize() { Map<String, Object> map = new HashMap<>(); map.put("employeeId", employeeId.toString()); map.put("employeeName", employeeName); map.put("currentSessionStartTime", currentSessionStartTime != null ? currentSessionStartTime.toString() : null); map.put("lastActivityTime", lastActivityTime != null ? lastActivityTime.toString() : null); map.put("actionsPerformedCount", actionsPerformedCount); map.put("totalValueGenerated", totalValueGenerated); map.put("joinDate", joinDate != null ? joinDate.toString() : null); synchronized(detailedProductionLog){ map.put("detailedProductionLog", detailedProductionLog.stream().map(DetailedProductionRecord::serialize).collect(Collectors.toList())); } return map; }
        public static EmployeeActivityRecord deserialize(Map<String, Object> map) {
            try { UUID id = UUID.fromString((String) map.get("employeeId")); String name = (String) map.get("employeeName"); EmployeeActivityRecord record = new EmployeeActivityRecord(id, name); if (map.get("currentSessionStartTime") != null) record.currentSessionStartTime = LocalDateTime.parse((String) map.get("currentSessionStartTime")); if (map.get("lastActivityTime") != null) record.lastActivityTime = LocalDateTime.parse((String) map.get("lastActivityTime")); if (map.get("actionsPerformedCount") instanceof Map) { Map<?,?> rawMap = (Map<?,?>) map.get("actionsPerformedCount"); rawMap.forEach((key, value) -> { if (key instanceof String && value instanceof Number) record.actionsPerformedCount.put((String) key, ((Number)value).longValue()); }); } record.totalValueGenerated = ((Number) map.getOrDefault("totalValueGenerated", 0.0)).doubleValue(); if (map.get("joinDate") != null) record.joinDate = LocalDateTime.parse((String) map.get("joinDate")); else record.joinDate = null; if (map.containsKey("detailedProductionLog")) { List<?> rawList = (List<?>) map.get("detailedProductionLog"); if(rawList != null) synchronized(record.detailedProductionLog){ record.detailedProductionLog.clear(); for (Object item : rawList) { if (item instanceof Map) { @SuppressWarnings("unchecked") DetailedProductionRecord prodRecord = DetailedProductionRecord.deserialize((Map<String, Object>) item); if (prodRecord != null) record.detailedProductionLog.add(prodRecord); } } } } return record; }
            catch (Exception e) { if (plugin != null) plugin.getLogger().log(Level.WARNING, "Erreur désérialisation EAR pour map: " + map, e); return null; }
        }
    }
    // --- Fin Enumérations et Classes Internes ---

    public EntrepriseManagerLogic(RoleplayCity plugin) {
        EntrepriseManagerLogic.plugin = plugin;
        entreprises = new ConcurrentHashMap<>();
        entreprisesBySiret = new ConcurrentHashMap<>();
        entrepriseFile = new File(plugin.getDataFolder(), "entreprise.yml");
        // --- Suppression de l'initialisation de playerPlacedBlocksFile et playerPlacedBlocksLocations ---

        playerHistoryFile = new File(plugin.getDataFolder(), "player_history.yml");
        playerHistoryCache = new ConcurrentHashMap<>();

        loadEntreprises();
        loadPlayerHistory();
        planifierTachesHoraires();
        planifierVerificationActiviteEmployes();
        planifierVerificationInactiviteEmployes(); // FIX MOYENNE: Vérifier employés inactifs longue durée
    }

    public Entreprise getEntrepriseBySiret(String siret) {
        if (siret == null || siret.isEmpty()) {
            return null;
        }
        // FIX PERFORMANCE HAUTE: Recherche O(1) avec l'index au lieu de O(n) itération
        Entreprise result = entreprisesBySiret.get(siret);

        // DEBUG: Log pour tracer les recherches de SIRET
        if (result == null) {
            plugin.getLogger().warning("[DEBUG] SIRET introuvable: " + siret);
            plugin.getLogger().warning("[DEBUG] SIRET disponibles dans l'index: " + entreprisesBySiret.keySet());
        } else {
            plugin.getLogger().info("[DEBUG] SIRET trouvé: " + siret + " -> " + result.getNom());
        }

        return result;
    }
    public int countTotalPlayerInvolvements(String playerName) {
        if (playerName == null) return 0;
        Set<String> involvedEnterprises = new HashSet<>(); // Utilise un Set pour éviter de compter une entreprise plusieurs fois

        for (Entreprise entreprise : entreprises.values()) {
            // Vérifie si le joueur est le gérant
            if (entreprise.getGerant() != null && entreprise.getGerant().equalsIgnoreCase(playerName)) {
                involvedEnterprises.add(entreprise.getNom());
            }
            // Vérifie si le joueur est un employé (même s'il est gérant, cela ne devrait pas ajouter une deuxième fois grâce au Set)
            if (entreprise.getEmployes().contains(playerName)) {
                involvedEnterprises.add(entreprise.getNom());
            }
        }
        plugin.getLogger().log(Level.FINER, "[DEBUG Involvements] Le joueur " + playerName + " est impliqué dans " + involvedEnterprises.size() + " entreprise(s).");
        return involvedEnterprises.size();
    }

    // Dans EntrepriseManagerLogic.java

    /**
     * Compte le nombre d'entreprises où un joueur est actuellement employé
     * (et non gérant de ces mêmes entreprises).
     * @param playerName Le nom du joueur.
     * @return Le nombre d'entreprises où le joueur est salarié.
     */
    public int countPlayerSalariedJobs(String playerName) {
        if (playerName == null) return 0;
        int salariedJobCount = 0;
        for (Entreprise entreprise : entreprises.values()) {
            // Vérifie si le joueur est un employé ET PAS le gérant de cette entreprise
            if (entreprise.getEmployes().contains(playerName) &&
                    (entreprise.getGerant() == null || !entreprise.getGerant().equalsIgnoreCase(playerName))) {
                salariedJobCount++;
            }
        }
        plugin.getLogger().log(Level.FINER, "[DEBUG JOBS] Le joueur " + playerName + " a " + salariedJobCount + " emploi(s) salarié(s).");
        return salariedJobCount;
    }
    // --- Activité/Productivité ---
    private void planifierVerificationActiviteEmployes() {
        if (activityCheckTask != null && !activityCheckTask.isCancelled()) activityCheckTask.cancel();
        activityCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                LocalDateTime now = LocalDateTime.now();
                for (Entreprise entreprise : entreprises.values()) {
                    for (EmployeeActivityRecord record : entreprise.getEmployeeActivityRecords().values()) {
                        Player onlinePlayer = Bukkit.getPlayer(record.employeeId);
                        if (onlinePlayer == null || !onlinePlayer.isOnline()) {
                            if (record.isActive()) record.endSession();
                            continue;
                        }
                        if (record.isActive() && record.lastActivityTime != null && Duration.between(record.lastActivityTime, now).toSeconds() >= INACTIVITY_THRESHOLD_SECONDS) {
                            record.endSession();
                        }
                    }
                }
            }
        }.runTaskTimerAsynchronously(plugin, ACTIVITY_CHECK_INTERVAL_TICKS, ACTIVITY_CHECK_INTERVAL_TICKS);
    }

    /**
     * FIX MOYENNE: Vérification quotidienne des employés inactifs depuis longtemps
     * - Avertissement après 30 jours d'inactivité
     * - Licenciement automatique après 45 jours d'inactivité
     */
    private void planifierVerificationInactiviteEmployes() {
        if (inactivityKickTask != null && !inactivityKickTask.isCancelled()) inactivityKickTask.cancel();

        inactivityKickTask = new BukkitRunnable() {
            @Override
            public void run() {
                LocalDateTime now = LocalDateTime.now();
                int totalWarnings = 0;
                int totalKicks = 0;

                for (Entreprise entreprise : entreprises.values()) {
                    List<UUID> employesToKick = new ArrayList<>();

                    for (EmployeeActivityRecord record : entreprise.getEmployeeActivityRecords().values()) {
                        // Ignorer le gérant
                        if (record.employeeId.equals(UUID.fromString(entreprise.getGerantUUID()))) {
                            continue;
                        }

                        // Déterminer la date de dernière activité
                        LocalDateTime lastActivity = record.lastActivityTime != null ? record.lastActivityTime : record.joinDate;
                        if (lastActivity == null) {
                            continue; // Pas de données, on ignore
                        }

                        long daysSinceLastActivity = Duration.between(lastActivity, now).toDays();

                        // Licenciement après 45 jours
                        if (daysSinceLastActivity >= INACTIVITY_KICK_DAYS) {
                            employesToKick.add(record.employeeId);
                            totalKicks++;

                            // Message différé au gérant
                            String messageGerant = ChatColor.YELLOW + "L'employé " + record.employeeName +
                                " a été automatiquement licencié pour inactivité (" + daysSinceLastActivity + " jours sans activité).";
                            ajouterMessageGerantDifferre(entreprise.getGerantUUID(), messageGerant, entreprise.getNom(), 0);

                            plugin.getLogger().info("[Inactivité] " + record.employeeName + " licencié de '" +
                                entreprise.getNom() + "' après " + daysSinceLastActivity + " jours d'inactivité");
                        }
                        // Avertissement après 30 jours
                        else if (daysSinceLastActivity >= INACTIVITY_WARNING_DAYS) {
                            totalWarnings++;

                            // Message différé à l'employé
                            String messageEmploye = ChatColor.GOLD + "⚠ Vous êtes inactif depuis " + daysSinceLastActivity +
                                " jours dans l'entreprise '" + entreprise.getNom() + "'. " +
                                ChatColor.YELLOW + "Vous serez licencié automatiquement après " + INACTIVITY_KICK_DAYS +
                                " jours d'inactivité.";
                            ajouterMessageEmployeDifferre(record.employeeId.toString(), messageEmploye, entreprise.getNom(), 0);

                            // Message au gérant
                            String messageGerant = ChatColor.YELLOW + "Votre employé " + record.employeeName +
                                " est inactif depuis " + daysSinceLastActivity + " jours.";
                            ajouterMessageGerantDifferre(entreprise.getGerantUUID(), messageGerant, entreprise.getNom(), 0);
                        }
                    }

                    // Exécuter les licenciements dans le thread principal
                    if (!employesToKick.isEmpty()) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            for (UUID employeeId : employesToKick) {
                                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(employeeId);
                                String employeeName = offlinePlayer.getName();
                                if (employeeName != null && entreprise.getEmployesInternal().remove(employeeName)) {
                                    recordPlayerHistoryEntry(employeeId, entreprise, "Employé", LocalDateTime.now());
                                    entreprise.retirerPrimeEmploye(employeeId.toString());
                                    EmployeeActivityRecord record = entreprise.getEmployeeActivityRecord(employeeId);
                                    if (record != null) record.endSession();
                                }
                            }
                            saveEntreprises();
                        });
                    }
                }

                if (totalWarnings > 0 || totalKicks > 0) {
                    plugin.getLogger().info("[Inactivité] Vérification terminée: " + totalWarnings +
                        " avertissement(s), " + totalKicks + " licenciement(s)");
                }
            }
        }.runTaskTimerAsynchronously(plugin, INACTIVITY_CHECK_INTERVAL_TICKS, INACTIVITY_CHECK_INTERVAL_TICKS);
    }

    // --- NOUVELLE MÉTHODE ---
    /**
     * Enregistre le revenu d'une vente en boutique dans le pot commun horaire.
     * @param nomEntreprise Le nom de l'entreprise qui a réalisé la vente.
     * @param montant Le montant de la vente.
     */
    public void enregistrerRevenuMagasin(String nomEntreprise, double montant) {
        if (nomEntreprise != null && montant > 0) {
            activiteMagasinHoraireValeur.merge(nomEntreprise, montant, Double::sum);
            plugin.getLogger().fine("Revenu boutique de " + montant + "€ enregistré pour le CA horaire de '" + nomEntreprise + "'.");
        }
    }

    /**
     * FIX CRITIQUE P1.1: Annule un revenu de vente en boutique (en cas de transaction échouée).
     * @param nomEntreprise Le nom de l'entreprise dont il faut annuler le revenu.
     * @param montant Le montant à soustraire du CA horaire.
     */
    public void annulerRevenuMagasin(String nomEntreprise, double montant) {
        if (nomEntreprise != null && montant > 0) {
            activiteMagasinHoraireValeur.compute(nomEntreprise, (key, oldValue) -> {
                if (oldValue == null) {
                    return 0.0; // Aucun revenu enregistré
                }
                double newValue = oldValue - montant;
                return newValue > 0 ? newValue : 0.0; // Ne pas avoir de valeur négative
            });
            plugin.getLogger().warning("Revenu boutique de " + montant + "€ ANNULÉ pour '" + nomEntreprise + "' (transaction échouée).");
        }
    }

    // --- Fin Activité/Productivité ---

    // --- Gestion Entreprises ---
    public String getNomEntrepriseDuMembre(String nomJoueur) { if (nomJoueur == null) return null; for (Entreprise entreprise : entreprises.values()) { if (entreprise.getGerant() != null && entreprise.getGerant().equalsIgnoreCase(nomJoueur) || entreprise.getEmployes().contains(nomJoueur)) return entreprise.getNom(); } return null; }
    public Entreprise getEntrepriseDuJoueur(Player player) { if (player == null) return null; String nomEnt = getNomEntrepriseDuMembre(player.getName()); return (nomEnt != null) ? getEntreprise(nomEnt) : null; }
    // --- Fin Gestion Entreprises ---

    // --- Tâches Horaires ---
    private void planifierTachesHoraires() {
        if (hourlyTask != null && !hourlyTask.isCancelled()) {
            hourlyTask.cancel();
        }

        // Calcule le temps restant jusqu'à la prochaine heure pleine
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextFullHour = now.withMinute(0).withSecond(0).withNano(0).plusHours(1);
        long initialDelayTicks = java.time.Duration.between(now, nextFullHour).toSeconds() * 20L;

        // Sécurité pour éviter un délai négatif si la tâche est lancée exactement à l'heure pile
        if (initialDelayTicks <= 0) {
            initialDelayTicks = 20L * 60L * 60L; // Reprogramme dans une heure
            nextFullHour = nextFullHour.plusHours(1);
        }

        this.nextPaymentTime = nextFullHour; // Mémorise l'heure de la prochaine exécution
        long ticksParHeure = 20L * 60L * 60L;

        hourlyTask = new BukkitRunnable() {
            @Override
            public void run() {
                // --- MODIFICATION: Calls the new central processing method ---
                executerCycleFinancierHoraire();
                // --- END MODIFICATION ---

                // Met à jour l'heure du prochain paiement pour le cycle suivant
                nextPaymentTime = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0).plusHours(1);
            }
        }.runTaskTimer(plugin, initialDelayTicks, ticksParHeure);

        plugin.getLogger().info("Tâches horaires planifiées. Prochaine exécution vers: " + nextFullHour.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }

    public void resetHourlyLimitsForAllPlayers() {
        joueurActivitesRestrictions.clear();
        plugin.getLogger().info("Les limites d'actions horaires pour les non-membres ont été réinitialisées globalement.");
    }
    // --- Fin Tâches Horaires ---

    public void enregistrerActionProductive(Player player, String actionTypeString, Material material, int quantite, Block block) {
        if (player == null || material == null || quantite <= 0) {
            plugin.getLogger().warning("Tentative d'enregistrement d'action productive avec des paramètres nuls ou invalides (Joueur: " + player + ", Material: " + material + ", Quantité: " + quantite + ")");
            return;
        }
        Entreprise entreprise = getEntrepriseDuJoueur(player);
        if (entreprise == null) {
            return;
        }

        String typeEntreprise = entreprise.getType();
        String materialPathConfig = "types-entreprise." + typeEntreprise + ".activites-payantes." + actionTypeString.toUpperCase() + "." + material.name();
        boolean estActionValorisee = plugin.getConfig().contains(materialPathConfig);

        if (actionTypeString.equalsIgnoreCase("BLOCK_BREAK")) {
            if (block == null) {
                plugin.getLogger().warning("BLOCK_BREAK enregistré sans référence de bloc pour " + player.getName() + " sur " + material.name());
            } else {
                if (!canPlayerBreakBlock(player, block.getLocation(), material)) {
                    plugin.getLogger().fine("Action BLOCK_BREAK sur ("+ material.name() +") annulée par protection de Plot pour " + player.getName());
                    return;
                }
            }
        } else if (actionTypeString.equalsIgnoreCase("BLOCK_PLACE")) {
            if (block == null) {
                plugin.getLogger().warning("BLOCK_PLACE enregistré sans référence de bloc pour " + player.getName() + " sur " + material.name());
            } else {
                if (!canPlayerPlaceBlock(player, block.getLocation(), material)) {
                    plugin.getLogger().fine("Action BLOCK_PLACE de ("+ material.name() +") annulée par protection de Plot pour " + player.getName());
                    return;
                }
            }
        }

        EmployeeActivityRecord activityRecord = entreprise.getOrCreateEmployeeActivityRecord(player.getUniqueId(), player.getName());
        String genericActionKey = actionTypeString.toUpperCase() + ":" + material.name();
        double valeurUnitaire = estActionValorisee ? plugin.getConfig().getDouble(materialPathConfig, 0.0) : 0.0;
        double valeurTotaleAction = valeurUnitaire * quantite;

        DetailedActionType detailedActionType;
        String upperAction = actionTypeString.toUpperCase();

        if (upperAction.equals("BLOCK_BREAK")) {
            detailedActionType = DetailedActionType.BLOCK_BROKEN;
        } else if (upperAction.equals("CRAFT_ITEM")) {
            detailedActionType = DetailedActionType.ITEM_CRAFTED;
        } else if (upperAction.equals("BLOCK_PLACE")) {
            detailedActionType = DetailedActionType.BLOCK_PLACED;
        } else {
            plugin.getLogger().severe("Type d'action détaillé INCONNU pour enregistrement productif : '" + actionTypeString + "'.");
            detailedActionType = null;
        }

        if (detailedActionType == null) {
            plugin.getLogger().severe("Impossible de déterminer le DetailedActionType pour " + actionTypeString + ". Log détaillé incomplet pour " + player.getName());
        }

        if (detailedActionType != null) {
            activityRecord.recordAction(genericActionKey, valeurTotaleAction, quantite, detailedActionType, material);
        } else {
            activityRecord.actionsPerformedCount.merge(genericActionKey, (long) quantite, Long::sum);
            activityRecord.totalValueGenerated += valeurTotaleAction;
            activityRecord.lastActivityTime = LocalDateTime.now();
            if (activityRecord.currentSessionStartTime == null) activityRecord.startSession();
            plugin.getLogger().warning("Log détaillé de production omis pour " + player.getName() + " (type d'action détaillé non résolu: " + actionTypeString + ")");
        }

        if (valeurTotaleAction > 0) {
            // --- MODIFICATION: Use the correct map ---
            activiteProductiveHoraireValeur.merge(entreprise.getNom(), valeurTotaleAction, Double::sum);
            // --- END MODIFICATION ---
            plugin.getLogger().fine("CA Productif Horaire pour '" + entreprise.getNom() + "' augmenté de " + valeurTotaleAction + " (" + actionTypeString + ": " + material.name() + ")");
        }

        if (detailedActionType != null) {
            entreprise.addGlobalProductionRecord(LocalDateTime.now(), material, quantite, player.getUniqueId().toString(), detailedActionType);
        }

        plugin.getLogger().fine("Action productive enregistrée pour " + player.getName() + ": " +
                (detailedActionType != null ? detailedActionType.getDisplayName() : actionTypeString.toUpperCase()) +
                " de " + material.name() + " x" + quantite +
                (valeurTotaleAction > 0 ? " (Valeur: " + valeurTotaleAction + "€)" : " (Non valorisé)"));
    }

    public void enregistrerActionProductive(Player player, String actionTypeString, Material material, int quantite) {
        // Surcharge principalement pour CRAFT_ITEM
        enregistrerActionProductive(player, actionTypeString, material, quantite, null);
    }

    public void enregistrerActionProductive(Player player, String actionType, String entityTypeName, int quantite) {
        if (!actionType.equalsIgnoreCase("ENTITY_KILL")) {
            plugin.getLogger().warning("enregistrerActionProductive pour entité appelée avec un actionType incorrect: " + actionType);
            return;
        }
        if (player == null || entityTypeName == null || entityTypeName.isEmpty() || quantite <= 0) {
            plugin.getLogger().warning("Tentative d'enregistrement d'action ENTITY_KILL avec des paramètres nuls ou invalides.");
            return;
        }

        Entreprise entreprise = getEntrepriseDuJoueur(player);
        if (entreprise == null) {
            return;
        }

        String typeEntrepriseJoueur = entreprise.getType();
        String configPathValeur = "types-entreprise." + typeEntrepriseJoueur + ".activites-payantes." + actionType.toUpperCase() + "." + entityTypeName.toUpperCase();
        double valeurUnitaire = plugin.getConfig().getDouble(configPathValeur, 0.0);
        double valeurTotaleAction = valeurUnitaire * quantite;

        EmployeeActivityRecord activityRecord = entreprise.getOrCreateEmployeeActivityRecord(player.getUniqueId(), player.getName());
        String genericActionKey = actionType.toUpperCase() + ":" + entityTypeName.toUpperCase();

        Material materialEquivalentPourLogDetaille = null;
        DetailedActionType actionDetailleePourLog = DetailedActionType.BLOCK_BROKEN; // Placeholder

        try {
            EntityType typeEnum = EntityType.valueOf(entityTypeName.toUpperCase());
            switch (typeEnum) {
                case SHEEP: materialEquivalentPourLogDetaille = Material.MUTTON; break;
                case COW: materialEquivalentPourLogDetaille = Material.BEEF; break;
                case PIG: materialEquivalentPourLogDetaille = Material.PORKCHOP; break;
                case CHICKEN: materialEquivalentPourLogDetaille = Material.CHICKEN; break;
                case RABBIT: materialEquivalentPourLogDetaille = Material.RABBIT; break;
                default: break;
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().fine("EntityType '" + entityTypeName + "' non reconnue pour mapping matériel facultatif lors du kill.");
        }

        if (materialEquivalentPourLogDetaille != null) {
            activityRecord.recordAction(genericActionKey, valeurTotaleAction, quantite, actionDetailleePourLog, materialEquivalentPourLogDetaille);
        } else {
            activityRecord.actionsPerformedCount.merge(genericActionKey, (long) quantite, Long::sum);
            activityRecord.totalValueGenerated += valeurTotaleAction;
            activityRecord.lastActivityTime = LocalDateTime.now();
            if (activityRecord.currentSessionStartTime == null) {
                activityRecord.startSession();
            }
        }

        if (valeurTotaleAction > 0) {
            // --- MODIFICATION: Use the correct map ---
            activiteProductiveHoraireValeur.merge(entreprise.getNom(), valeurTotaleAction, Double::sum);
            // --- END MODIFICATION ---
            plugin.getLogger().fine("CA Productif Horaire pour '" + entreprise.getNom() + "' augmenté de " + valeurTotaleAction + " (Action: " + actionType + ", Cible: " + entityTypeName + ")");
        }

        if (materialEquivalentPourLogDetaille != null) {
            entreprise.addGlobalProductionRecord(LocalDateTime.now(), materialEquivalentPourLogDetaille, quantite, player.getUniqueId().toString(), actionDetailleePourLog);
        }

        plugin.getLogger().fine("Action productive (ENTITY_KILL) enregistrée pour " + player.getName() + ": " + quantite + "x " + entityTypeName +
                (valeurTotaleAction > 0 ? " (Valeur: " + valeurTotaleAction + "€)" : " (Non valorisé)") +
                (materialEquivalentPourLogDetaille != null ? ", log détaillé comme " + materialEquivalentPourLogDetaille.name() : ", pas de log détaillé matériel") );
    }

    /**
     * Enregistre l'activité productive pour le craft d'un backpack.
     * Cette méthode est appelée APRÈS que la vérification des restrictions a été faite.
     *
     * @param player Le joueur qui a crafté le backpack
     * @param backpackType Le type de backpack (sacoche, valisette, sac_course, etc.)
     */
    public void enregistrerCraftBackpack(Player player, String backpackType) {
        if (player == null || backpackType == null) {
            plugin.getLogger().warning("enregistrerCraftBackpack appelé avec des paramètres nuls");
            return;
        }

        Entreprise entreprise = getEntrepriseDuJoueur(player);
        if (entreprise == null) {
            return; // Pas d'entreprise, pas d'enregistrement
        }

        // Récupérer la valeur du backpack depuis la config
        String typeEntreprise = entreprise.getType();
        String configPath = "types-entreprise." + typeEntreprise + ".activites-payantes.CRAFT_BACKPACK." + backpackType;
        double valeurBackpack = plugin.getConfig().getDouble(configPath, 0.0);

        if (valeurBackpack > 0) {
            // Enregistrer l'activité dans le record de l'employé
            EmployeeActivityRecord activityRecord = entreprise.getOrCreateEmployeeActivityRecord(
                player.getUniqueId(),
                player.getName()
            );

            String actionKey = "CRAFT_BACKPACK:" + backpackType;

            // Utiliser LEATHER comme Material de référence pour les backpacks dans les logs
            Material backpackMaterial = Material.LEATHER;

            // Enregistrer l'action détaillée
            activityRecord.recordAction(
                actionKey,
                valeurBackpack,
                1,
                DetailedActionType.ITEM_CRAFTED,
                backpackMaterial
            );

            // Ajouter au chiffre d'affaires productif horaire
            activiteProductiveHoraireValeur.merge(entreprise.getNom(), valeurBackpack, Double::sum);

            // Ajouter au log global de production de l'entreprise
            entreprise.addGlobalProductionRecord(
                LocalDateTime.now(),
                backpackMaterial,
                1,
                player.getUniqueId().toString(),
                DetailedActionType.ITEM_CRAFTED
            );

            plugin.getLogger().fine("Craft de backpack '" + backpackType + "' enregistré pour " + player.getName() +
                    " (Entreprise: " + entreprise.getNom() + ", Type: " + typeEntreprise + ", Valeur: " + valeurBackpack + "€)");
        } else {
            plugin.getLogger().fine("Backpack '" + backpackType + "' n'a pas de valeur configurée dans activites-payantes.CRAFT_BACKPACK pour " + typeEntreprise);
        }
    }

    // --- REFACTORED AND NEW HOURLY METHODS ---

    /**
     * The main hourly financial processing cycle.
     * This method orchestrates the calculation of revenues, expenses, and profits,
     * updates company balances, and sends a detailed financial report to managers.
     */
    public void executerCycleFinancierHoraire() {
        plugin.getLogger().info("Début du cycle financier horaire...");
        boolean dataModified = false;

        // FIX BASSE #26: Utiliser ConfigDefaults
        double pourcentageTaxes = plugin.getConfig().getDouble("finance.pourcentage-taxes",
            com.gravityyfh.roleplaycity.util.ConfigDefaults.FINANCE_POURCENTAGE_TAXES);

        for (Entreprise entreprise : entreprises.values()) {
            double ancienSolde = entreprise.getSolde();

            // --- 1. Data Collection ---
            double revenuActivite = activiteProductiveHoraireValeur.getOrDefault(entreprise.getNom(), 0.0);
            double revenuMagasins = activiteMagasinHoraireValeur.getOrDefault(entreprise.getNom(), 0.0);
            double caBrut = revenuActivite + revenuMagasins;
            double montantTaxes = (caBrut > 0) ? caBrut * (pourcentageTaxes / 100.0) : 0.0;
            double caNet = caBrut - montantTaxes;

            // --- 2. Expense Calculation & Deduction ---
            double fraisPrimes = payerPrimesPourEntreprise(entreprise);
            double fraisChargesSalariales = payerChargesSalarialesPourEntreprise(entreprise);

            // --- 3. Final Calculation & Balance Update ---
            double beneficeFinal = caNet - fraisPrimes - fraisChargesSalariales;

            if (caBrut > 0 || fraisPrimes > 0 || fraisChargesSalariales > 0) {
                dataModified = true;

                // The balance for primes and charges was already updated within their respective methods.
                // Now, we just add the net revenue from this hour's activities.
                entreprise.setSolde(entreprise.getSolde() + caNet);
                entreprise.setChiffreAffairesTotal(entreprise.getChiffreAffairesTotal() + caBrut);

                // --- 4. Transaction Logging ---
                if (caBrut > 0) {
                    entreprise.addTransaction(new Transaction(TransactionType.REVENUE, caBrut, "Revenus bruts horaires (Activité + Boutiques)", "System"));
                }
                if (montantTaxes > 0) {
                    entreprise.addTransaction(new Transaction(TransactionType.TAXES, montantTaxes, "Impôts (" + pourcentageTaxes + "%) sur CA", "System"));
                }

                // --- 5. Report Generation ---
                genererEtEnvoyerRapportFinancier(entreprise, ancienSolde, revenuActivite, revenuMagasins, montantTaxes, fraisPrimes, fraisChargesSalariales, beneficeFinal);
            }

            // --- 6. Reset Hourly Data for the next cycle ---
            activiteProductiveHoraireValeur.put(entreprise.getNom(), 0.0);
            activiteMagasinHoraireValeur.put(entreprise.getNom(), 0.0);
        }

        // --- 7. Process global, non-company-specific tasks ---
        payerAllocationChomageHoraire();
        resetHourlyLimitsForAllPlayers();

        if (dataModified) {
            saveEntreprises();
            plugin.getLogger().info("Cycle financier horaire terminé. Des données ont été modifiées et sauvegardées.");
        } else {
            plugin.getLogger().info("Cycle financier horaire terminé. Aucune transaction financière à traiter.");
        }
    }

    /**
     * Pays hourly primes for a single specified company.
     * This method modifies the company's balance, logs the transactions, and returns the total amount of primes paid.
     * @param entreprise The company to process.
     * @return The total amount paid in primes.
     */
    private double payerPrimesPourEntreprise(Entreprise entreprise) {
        double totalPrimesPayees = 0;
        Player gerantPlayer = Bukkit.getPlayerExact(entreprise.getGerant());
        OfflinePlayer offlineGerant = Bukkit.getOfflinePlayer(UUID.fromString(entreprise.getGerantUUID()));

        for (String employeNom : new HashSet<>(entreprise.getEmployes())) {
            OfflinePlayer employeOffline = Bukkit.getOfflinePlayer(employeNom);
            UUID employeUUID;
            try {
                if(employeOffline != null) employeUUID = employeOffline.getUniqueId(); else continue;
            } catch (Exception ignored) { continue; }

            double primeConfigurée = entreprise.getPrimePourEmploye(employeUUID.toString());
            if (primeConfigurée <= 0) continue;

            EmployeeActivityRecord activity = entreprise.getEmployeeActivityRecord(employeUUID);
            if (activity == null || !activity.isActive()) continue; // Only active employees get primes

            if (entreprise.getSolde() >= primeConfigurée) {
                // FIX MOYENNE: Log détaillé transaction Vault
                plugin.getLogger().fine("[Vault] DEPOSIT (Prime): " + employeNom + " ← " + String.format("%.2f€", primeConfigurée) +
                    " (Entreprise: " + entreprise.getNom() + ")");

                EconomyResponse er = RoleplayCity.getEconomy().depositPlayer(employeOffline, primeConfigurée);

                if (er.transactionSuccess()) {
                    plugin.getLogger().fine("[Vault] DEPOSIT SUCCESS (Prime): " + employeNom + " ← " + String.format("%.2f€", primeConfigurée));
                    entreprise.setSolde(entreprise.getSolde() - primeConfigurée);
                    entreprise.addTransaction(new Transaction(TransactionType.PRIMES, primeConfigurée, "Prime horaire: " + employeNom, "System"));
                    totalPrimesPayees += primeConfigurée;

                    String msgEmploye = String.format("&aPrime horaire reçue: &e+%.2f€&a de '&6%s&a'.", primeConfigurée, entreprise.getNom());
                    Player onlineEmp = employeOffline.getPlayer();
                    if (onlineEmp != null) onlineEmp.sendMessage(ChatColor.translateAlternateColorCodes('&', msgEmploye));
                    else ajouterMessageEmployeDifferre(employeUUID.toString(), ChatColor.translateAlternateColorCodes('&', msgEmploye), entreprise.getNom(), primeConfigurée);

                } else {
                    // FIX MOYENNE: Log détaillé échec transaction Vault
                    plugin.getLogger().severe("[Vault] DEPOSIT FAILED (Prime): " + employeNom + " ← " + primeConfigurée + "€ - Reason: " + er.errorMessage);
                }
            } else {
                String msgEchecEmp = String.format("&cL'entreprise '&6%s&c' n'a pas pu verser votre prime de &e%.2f€ &c(solde insuffisant).", entreprise.getNom(), primeConfigurée);
                Player onlineEmp = employeOffline.getPlayer();
                if (onlineEmp != null) onlineEmp.sendMessage(ChatColor.translateAlternateColorCodes('&',msgEchecEmp));
                else ajouterMessageEmployeDifferre(employeUUID.toString(), ChatColor.translateAlternateColorCodes('&',msgEchecEmp), entreprise.getNom(), 0);
            }
        }
        return totalPrimesPayees;
    }

    /**
     * Pays hourly payroll taxes for a single specified company.
     * This method modifies the company's balance, logs the transactions, and returns the total amount of charges paid.
     * @param entreprise The company to process.
     * @return The total amount paid in charges.
     */
    private double payerChargesSalarialesPourEntreprise(Entreprise entreprise) {
        // FIX BASSE #26: Utiliser ConfigDefaults
        double chargeParEmploye = plugin.getConfig().getDouble("finance.charge-salariale-par-employe-horaire",
            com.gravityyfh.roleplaycity.util.ConfigDefaults.FINANCE_CHARGE_SALARIALE_PAR_EMPLOYE);
        if (chargeParEmploye <= 0) return 0;

        boolean actifsSeulement = plugin.getConfig().getBoolean("finance.charges-sur-employes-actifs-seulement", true);

        long nbEmployesConcernes;
        if (actifsSeulement) {
            nbEmployesConcernes = entreprise.getEmployeeActivityRecords().values().stream()
                    .filter(EmployeeActivityRecord::isActive)
                    .count();
        } else {
            nbEmployesConcernes = entreprise.getEmployes().size();
        }

        if (nbEmployesConcernes == 0) return 0;

        double totalCharges = nbEmployesConcernes * chargeParEmploye;
        if (entreprise.getSolde() >= totalCharges) {
            entreprise.setSolde(entreprise.getSolde() - totalCharges);
            entreprise.addTransaction(new Transaction(TransactionType.PAYROLL_TAX, totalCharges, "Charges salariales (" + nbEmployesConcernes + " emp.)", "System"));
            return totalCharges;
        } else {
            plugin.getLogger().warning("Solde insuffisant pour charges salariales pour '" + entreprise.getNom() + "'. Requis: " + totalCharges + "€, Solde: " + entreprise.getSolde());
            return 0;
        }
    }

    /**
     * Generates and sends the detailed hourly financial report to the company manager.
     */
    private void genererEtEnvoyerRapportFinancier(Entreprise entreprise, double ancienSolde, double revActivite, double revMagasins, double taxes, double primes, double charges, double benefice) {
        double caBrut = revActivite + revMagasins;
        double totalDepenses = taxes + primes + charges;
        double nouveauSolde = ancienSolde + benefice;
        String typeEntreprise = entreprise.getType() != null ? entreprise.getType() : "N/A";
        // FIX BASSE #26: Utiliser ConfigDefaults
        double taxRate = plugin.getConfig().getDouble("finance.pourcentage-taxes",
            com.gravityyfh.roleplaycity.util.ConfigDefaults.FINANCE_POURCENTAGE_TAXES);

        List<String> report = new ArrayList<>();
        report.add(ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "----------------------------------------------------");
        report.add(" " + ChatColor.GOLD + "" + ChatColor.BOLD + "Entreprise : " + ChatColor.YELLOW + entreprise.getNom() + ChatColor.GRAY + " (" + typeEntreprise + ")");
        report.add(" " + ChatColor.GOLD + "Rapport Financier Horaire");
        report.add("");
        report.add(ChatColor.AQUA + "  Chiffre d'Affaires Brut");
        report.add(String.format(ChatColor.GRAY + "    - Revenus d'activité : %s+%,.2f €", ChatColor.GREEN, revActivite));
        report.add(String.format(ChatColor.GRAY + "    - Revenus des boutiques : %s+%,.2f €", ChatColor.GREEN, revMagasins));
        report.add(ChatColor.DARK_AQUA + "    -------------------------------");
        report.add(String.format(ChatColor.AQUA + "      Total Brut : %s+%,.2f €", ChatColor.GREEN, caBrut));
        report.add("");
        report.add(ChatColor.RED + "  Dépenses Opérationnelles");
        report.add(String.format(ChatColor.GRAY + "    - Impôts sur le CA (%.0f%%) : %s-%,.2f €", taxRate, ChatColor.RED, taxes));
        report.add(String.format(ChatColor.GRAY + "    - Primes versées : %s-%,.2f €", ChatColor.RED, primes));
        report.add(String.format(ChatColor.GRAY + "    - Charges salariales : %s-%,.2f €", ChatColor.RED, charges));
        report.add(ChatColor.DARK_RED + "    -------------------------------");
        report.add(String.format(ChatColor.RED + "      Total Dépenses : %s-%,.2f €", ChatColor.RED, totalDepenses));
        report.add("");

        String beneficePrefix = benefice >= 0 ? ChatColor.GREEN + "+" : ChatColor.RED + "";
        report.add(String.format(ChatColor.BOLD + "  Bénéfice/Perte Horaire : %s%,.2f €", beneficePrefix, benefice));
        report.add("");
        report.add(ChatColor.DARK_AQUA + "  Solde de l'entreprise :");
        report.add(String.format(ChatColor.GRAY + "    Ancien solde : " + ChatColor.WHITE + "%,.2f €", ancienSolde));
        report.add(String.format(ChatColor.GRAY + "    Nouveau solde : " + ChatColor.WHITE + "" + ChatColor.BOLD + "%,.2f €", nouveauSolde));

        report.add(ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "----------------------------------------------------");

        Player gerantPlayer = Bukkit.getPlayerExact(entreprise.getGerant());
        String fullReportString = String.join("\n", report);

        if (gerantPlayer != null && gerantPlayer.isOnline()) {
            gerantPlayer.sendMessage(fullReportString);
        } else {
            OfflinePlayer offlineGerant = Bukkit.getOfflinePlayer(UUID.fromString(entreprise.getGerantUUID()));
            if(offlineGerant.hasPlayedBefore() || offlineGerant.isOnline()){
                String resume = String.format("Rapport Horaire '%s': Bénéfice/Perte de %.2f€. Nouveau solde: %.2f€.", entreprise.getNom(), benefice, nouveauSolde);
                ajouterMessageGerantDifferre(entreprise.getGerantUUID(), ChatColor.GREEN + resume, entreprise.getNom(), benefice);
            }
        }
    }

    public void payerAllocationChomageHoraire() {
        // FIX BASSE #26: Utiliser ConfigDefaults pour valeur par défaut
        double montantAllocation = plugin.getConfig().getDouble("finance.allocation-chomage-horaire",
            com.gravityyfh.roleplaycity.util.ConfigDefaults.FINANCE_ALLOCATION_CHOMAGE_HORAIRE);
        if (montantAllocation <= 0) return;
        int joueursPayes = 0;
        for (Player joueurConnecte : Bukkit.getOnlinePlayers()) {
            if (getNomEntrepriseDuMembre(joueurConnecte.getName()) == null) {
                EconomyResponse er = RoleplayCity.getEconomy().depositPlayer(joueurConnecte, montantAllocation);
                if (er.transactionSuccess()) { joueurConnecte.sendMessage(ChatColor.translateAlternateColorCodes('&', String.format("&6[Alloc. Chômage] &a+%.2f€", montantAllocation))); joueursPayes++; }
                else { plugin.getLogger().warning("Impossible de verser alloc. chômage à " + joueurConnecte.getName() + ": " + er.errorMessage); }
            }
        }
        if (joueursPayes > 0) plugin.getLogger().fine(joueursPayes + " joueur(s) ont reçu l'allocation chômage.");
    }

    /**
     * FIX PERFORMANCE HAUTE: Version optimisée avec cache O(1) au lieu de O(n)
     * FIX MOYENNE: Le cache expire après 5 minutes et se recharge automatiquement
     */
    public boolean verifierEtGererRestrictionAction(Player player, String actionTypeString, String targetName, int quantite) {
        Entreprise entrepriseJoueurObj = getEntrepriseDuJoueur(player);

        // FIX MOYENNE: Vérifier si le cache a expiré et le recharger si nécessaire
        long currentTime = System.currentTimeMillis();
        if (currentTime - restrictionsCacheLoadTime > RESTRICTIONS_CACHE_TTL_MS) {
            plugin.getLogger().fine("Cache de restrictions expiré, rechargement...");
            loadRestrictionsCache();
        }

        // Recherche O(1) dans le cache au lieu de parcourir tous les types d'entreprise
        String cacheKey = actionTypeString.toUpperCase() + ":" + targetName.toUpperCase();
        List<RestrictionInfo> restrictions = restrictionsCache.get(cacheKey);

        if (restrictions == null || restrictions.isEmpty()) {
            return false; // Aucune restriction pour cette action
        }

        // Vérifier chaque restriction trouvée
        for (RestrictionInfo restriction : restrictions) {
            boolean estMembreDeCeTypeSpecialise = (entrepriseJoueurObj != null &&
                entrepriseJoueurObj.getType().equals(restriction.entrepriseType));

            if (estMembreDeCeTypeSpecialise) {
                return false; // Les membres de ce type ne sont pas restreints
            }

            // Le joueur n'est pas membre, appliquer la limite
            if (restriction.limiteNonMembre == -1) {
                continue; // Pas de limite pour ce type
            }

            if (restriction.limiteNonMembre == 0) {
                restriction.messagesErreur.forEach(msg ->
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg.replace("%limite%", "0"))));
                return true; // Action bloquée
            }

            String actionIdPourRestriction = restriction.entrepriseType + "_" +
                actionTypeString.toUpperCase() + "_" + targetName.toUpperCase();

            ActionInfo info = joueurActivitesRestrictions
                .computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                .computeIfAbsent(actionIdPourRestriction, k -> new ActionInfo());

            synchronized(info) {
                if (info.getNombreActions() + quantite > restriction.limiteNonMembre) {
                    final int currentCount = info.getNombreActions();
                    restriction.messagesErreur.forEach(msg ->
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            msg.replace("%limite%", String.valueOf(restriction.limiteNonMembre)))));
                    player.sendMessage(ChatColor.GRAY + "(Limite atteinte: " + currentCount + "/" + restriction.limiteNonMembre + ")");
                    return true; // Action bloquée
                } else {
                    info.incrementerActions(quantite);
                }
            }
        }

        return false; // Aucune restriction bloquante
    }
    public LocalDateTime getNextPaymentTime() {
        return nextPaymentTime;
    }
    // --- Accès Entreprises ---
    public Entreprise getEntreprise(String nomEntreprise) { return entreprises.get(nomEntreprise); }
    public Collection<Entreprise> getEntreprises() { return Collections.unmodifiableCollection(entreprises.values()); }
    public List<Entreprise> getEntreprisesByVille(String ville) { return entreprises.values().stream().filter(e -> e.getVille() != null && e.getVille().equalsIgnoreCase(ville)).collect(Collectors.toList()); }
    public Set<String> getTypesEntreprise() { ConfigurationSection s = plugin.getConfig().getConfigurationSection("types-entreprise"); return (s != null) ? s.getKeys(false) : Collections.emptySet(); }
    public List<Entreprise> getEntreprisesGereesPar(String nomGerant) { return entreprises.values().stream().filter(e -> e.getGerant() != null && e.getGerant().equalsIgnoreCase(nomGerant)).collect(Collectors.toList()); }

    /**
     * Vérifie si un item peut être vendu dans un shop selon le type d'entreprise
     * @param typeEntreprise Le type de l'entreprise (ex: "Minage", "Agriculture", "Supermarche", "Styliste")
     * @param itemMaterial Le matériau de l'item à vendre
     * @return true si l'item est autorisé, false sinon
     */
    public boolean isItemAllowedInShop(String typeEntreprise, Material itemMaterial) {
        if (typeEntreprise == null || itemMaterial == null) {
            return false;
        }

        // Vérifier si ce type d'entreprise a la restriction de shop désactivée
        boolean shopSansRestriction = plugin.getConfig().getBoolean(
            "types-entreprise." + typeEntreprise + ".shop-sans-restriction",
            false
        );

        // Si shop-sans-restriction est true, tous les items sont autorisés
        if (shopSansRestriction) {
            return true;
        }

        // Sinon, vérifier que l'item est dans les action_restrictions
        ConfigurationSection actionsSection = plugin.getConfig().getConfigurationSection(
            "types-entreprise." + typeEntreprise + ".action_restrictions"
        );

        if (actionsSection == null) {
            // Pas de restrictions définies = rien n'est autorisé par défaut
            return false;
        }

        // Parcourir tous les types d'actions (BLOCK_BREAK, CRAFT_ITEM, ENTITY_KILL, BLOCK_PLACE, CRAFT_BACKPACK)
        for (String actionType : actionsSection.getKeys(false)) {
            ConfigurationSection materialsSection = plugin.getConfig().getConfigurationSection(
                "types-entreprise." + typeEntreprise + ".action_restrictions." + actionType
            );

            if (materialsSection == null) {
                continue;
            }

            // Vérifier si le matériau est dans cette section
            if (materialsSection.contains(itemMaterial.name())) {
                return true;
            }
        }

        // L'item n'est pas dans les restrictions = pas autorisé
        return false;
    }

    /**
     * Vérifie si un backpack custom peut être vendu dans un shop selon le type d'entreprise
     * @param typeEntreprise Le type de l'entreprise
     * @param backpackType Le type de backpack (ex: "sacoche", "valisette", etc.)
     * @return true si le backpack est autorisé, false sinon
     */
    public boolean isBackpackAllowedInShop(String typeEntreprise, String backpackType) {
        if (typeEntreprise == null || backpackType == null) {
            return false;
        }

        // Vérifier si ce type d'entreprise a la restriction de shop désactivée
        boolean shopSansRestriction = plugin.getConfig().getBoolean(
            "types-entreprise." + typeEntreprise + ".shop-sans-restriction",
            false
        );

        // Si shop-sans-restriction est true, tous les items sont autorisés
        if (shopSansRestriction) {
            return true;
        }

        // Vérifier si le backpack est dans les action_restrictions.CRAFT_BACKPACK
        ConfigurationSection backpackSection = plugin.getConfig().getConfigurationSection(
            "types-entreprise." + typeEntreprise + ".action_restrictions.CRAFT_BACKPACK"
        );

        if (backpackSection == null) {
            return false;
        }

        // Vérifier si ce type de backpack est autorisé
        return backpackSection.contains(backpackType);
    }

    /**
     * Récupère quelques exemples d'items autorisés pour un type d'entreprise
     * @param typeEntreprise Le type de l'entreprise
     * @param maxExamples Nombre maximum d'exemples à retourner
     * @return Une chaîne avec les exemples séparés par des virgules, ou null si aucun
     */
    public String getAuthorizedItemsExamples(String typeEntreprise, int maxExamples) {
        if (typeEntreprise == null) {
            return null;
        }

        // Vérifier si shop-sans-restriction est activé
        boolean shopSansRestriction = plugin.getConfig().getBoolean(
            "types-entreprise." + typeEntreprise + ".shop-sans-restriction",
            false
        );

        if (shopSansRestriction) {
            return "TOUS LES ITEMS (entreprise sans restriction)";
        }

        ConfigurationSection actionsSection = plugin.getConfig().getConfigurationSection(
            "types-entreprise." + typeEntreprise + ".action_restrictions"
        );

        if (actionsSection == null) {
            return null;
        }

        List<String> examples = new ArrayList<>();
        int count = 0;

        // Parcourir tous les types d'actions et collecter des exemples
        for (String actionType : actionsSection.getKeys(false)) {
            if (count >= maxExamples) {
                break;
            }

            ConfigurationSection materialsSection = plugin.getConfig().getConfigurationSection(
                "types-entreprise." + typeEntreprise + ".action_restrictions." + actionType
            );

            if (materialsSection == null) {
                continue;
            }

            for (String materialName : materialsSection.getKeys(false)) {
                if (count >= maxExamples) {
                    break;
                }
                examples.add(materialName);
                count++;
            }
        }

        if (examples.isEmpty()) {
            return null;
        }

        return String.join(", ", examples);
    }

    // --- Fin Accès Entreprises ---

    // --- Suppression et Dissolution (AVEC HISTORIQUE) ---
    public void handleEntrepriseRemoval(Entreprise entreprise, String reason) {
        if (entreprise == null) return;

        // 1. Supprimer toutes les boutiques de l'entreprise
        int shopsSupprimees = 0;
        if (plugin.getShopManager() != null) {
            shopsSupprimees = plugin.getShopManager().deleteShopsBySiret(
                entreprise.getSiret(),
                "Entreprise supprimée: " + reason
            );
            if (shopsSupprimees > 0) {
                plugin.getLogger().info(String.format(
                    "[EntrepriseManager] %d boutique(s) de l'entreprise '%s' supprimée(s)",
                    shopsSupprimees, entreprise.getNom()
                ));
            }
        }

        // 2. Vendre tous les terrains PROFESSIONNEL de cette entreprise
        if (plugin.getTownManager() != null && plugin.getCompanyPlotManager() != null) {
            String siret = entreprise.getSiret();
            int totalPlotsSold = 0;

            // Parcourir toutes les villes pour trouver les terrains de cette entreprise
            for (String townName : plugin.getTownManager().getTownNames()) {
                java.util.List<com.gravityyfh.roleplaycity.town.data.Plot> companyPlots =
                    plugin.getTownManager().getPlotsByCompanySiret(siret, townName);

                if (!companyPlots.isEmpty()) {
                    plugin.getLogger().info(String.format(
                        "[EntrepriseManagerLogic] Entreprise '%s' (SIRET %s) dissoute - Vente de %d terrain(s) dans %s",
                        entreprise.getNom(), siret, companyPlots.size(), townName
                    ));

                    plugin.getCompanyPlotManager().handleCompanyDeletion(siret, townName);
                    totalPlotsSold += companyPlots.size();
                }
            }

            if (totalPlotsSold > 0) {
                plugin.getLogger().info(String.format(
                    "[EntrepriseManagerLogic] Total: %d terrain(s) vendu(s) suite à la dissolution de '%s'",
                    totalPlotsSold, entreprise.getNom()
                ));
            }
        }
        // --- FIN DU NOUVEAU CODE ---

        String nomEntreprise = entreprise.getNom();
        UUID gerantUUID = null;
        try {
            gerantUUID = UUID.fromString(entreprise.getGerantUUID());
        } catch (Exception ignored) {
        }

        plugin.getLogger().info("[EntrepriseManagerLogic] Enregistrement historique avant dissolution de '" + nomEntreprise + "'. Raison: " + reason);
        LocalDateTime dateDissolution = LocalDateTime.now();
        if (gerantUUID != null) {
            recordPlayerHistoryEntry(gerantUUID, entreprise, "Gérant", dateDissolution);
        } else {
            plugin.getLogger().warning("UUID gérant invalide pour " + nomEntreprise + ", historique gérant non enregistré.");
        }

        Set<String> employesAvantDissolution = new HashSet<>(entreprise.getEmployes());
        for (String employeNom : employesAvantDissolution) {
            OfflinePlayer offlineEmp = Bukkit.getOfflinePlayer(employeNom);
            if (offlineEmp != null && (offlineEmp.hasPlayedBefore() || offlineEmp.isOnline())) {
                recordPlayerHistoryEntry(offlineEmp.getUniqueId(), entreprise, "Employé", dateDissolution);
            } else {
                plugin.getLogger().warning("UUID invalide pour employé " + employeNom + " (ent: " + nomEntreprise + "), historique non enregistré.");
            }
        }

        plugin.getLogger().info("[EntrepriseManagerLogic] Suppression effective de '" + nomEntreprise + "'.");
        entreprise.getEmployeeActivityRecords().values().forEach(EmployeeActivityRecord::endSession);
        entreprises.remove(nomEntreprise);
        // FIX PERFORMANCE HAUTE: Maintenir l'index SIRET
        entreprisesBySiret.remove(entreprise.getSiret());
        activiteProductiveHoraireValeur.remove(nomEntreprise);
        saveEntreprises();

        Player gerantPlayer = (gerantUUID != null) ? Bukkit.getPlayer(gerantUUID) : null;
        if (gerantPlayer != null && gerantPlayer.isOnline()) {
            gerantPlayer.sendMessage(ChatColor.RED + "Votre entreprise '" + nomEntreprise + "' a été dissoute. Raison: " + reason);
        }

        for (String employeNom : employesAvantDissolution) {
            OfflinePlayer offlineEmp = Bukkit.getOfflinePlayer(employeNom);
            Player onlineEmp = (offlineEmp != null) ? offlineEmp.getPlayer() : null;
            if (onlineEmp != null) {
                onlineEmp.sendMessage(ChatColor.RED + "L'entreprise '" + nomEntreprise + "' dont vous étiez membre a été dissoute. Raison: " + reason);
            }
        }
    }
    /**
     * FIX HAUTE: Système de confirmation obligatoire avant suppression
     */
    public void supprimerEntreprise(Player initiator, String nomEntreprise) {
        Entreprise entrepriseASupprimer = getEntreprise(nomEntreprise);
        if (entrepriseASupprimer == null) {
            initiator.sendMessage(ChatColor.RED + "Entreprise '" + nomEntreprise + "' non trouvée.");
            return;
        }

        boolean isAdmin = initiator.hasPermission("entreprisemanager.admin.deleteany");
        boolean isGerant = entrepriseASupprimer.getGerant().equalsIgnoreCase(initiator.getName());

        if (!isGerant && !isAdmin) {
            initiator.sendMessage(ChatColor.RED + "Permission refusée.");
            return;
        }

        // Vérifier si une confirmation est en attente
        String entrepriseEnAttente = suppressionsEnAttente.get(initiator.getUniqueId());

        if (entrepriseEnAttente != null && entrepriseEnAttente.equals(nomEntreprise)) {
            // Confirmation reçue, procéder à la suppression
            suppressionsEnAttente.remove(initiator.getUniqueId());

            // FIX MOYENNE: Log opération critique de suppression
            plugin.getLogger().warning("[OPERATION CRITIQUE] Suppression entreprise '" + nomEntreprise + "' (SIRET: " +
                entrepriseASupprimer.getSiret() + ") par " + initiator.getName() +
                " (Admin: " + isAdmin + ", Gérant: " + isGerant + ")");
            plugin.getLogger().info("[SUPPRESSION] Solde: " + String.format("%.2f€", entrepriseASupprimer.getSolde()) +
                ", Employés: " + entrepriseASupprimer.getEmployes().size());

            // TODO: Supprimer toutes les boutiques associées (à réimplémenter)
            // if (plugin.getShopManager() != null) {
            //     plugin.getShopManager().deleteAllShopsForEnterprise(entrepriseASupprimer.getSiret());
            // }

            String reason = "Dissolution par " + initiator.getName() + (isAdmin && !isGerant ? " (Admin)" : " (Gérant)");
            handleEntrepriseRemoval(entrepriseASupprimer, reason);

            initiator.sendMessage(ChatColor.GREEN + "✓ L'entreprise '" + nomEntreprise + "' a été définitivement dissoute.");

        } else {
            // Première demande, enregistrer et demander confirmation
            suppressionsEnAttente.put(initiator.getUniqueId(), nomEntreprise);

            initiator.sendMessage(ChatColor.DARK_RED + "⚠ ATTENTION - SUPPRESSION D'ENTREPRISE ⚠");
            initiator.sendMessage(ChatColor.RED + "Vous êtes sur le point de supprimer l'entreprise '" + nomEntreprise + "'");
            initiator.sendMessage(ChatColor.YELLOW + "Cette action est IRRÉVERSIBLE et supprimera :");
            initiator.sendMessage(ChatColor.GRAY + "  • Tous les employés (" + entrepriseASupprimer.getEmployes().size() + ")");
            initiator.sendMessage(ChatColor.GRAY + "  • Toutes les boutiques");
            initiator.sendMessage(ChatColor.GRAY + "  • Tous les terrains professionnels");
            initiator.sendMessage(ChatColor.GRAY + "  • Le solde de l'entreprise (" + String.format("%,.2f", entrepriseASupprimer.getSolde()) + "€)");
            initiator.sendMessage(ChatColor.GREEN + "Pour confirmer, retapez la même commande dans les 30 secondes.");
            initiator.sendMessage(ChatColor.GRAY + "(Timeout automatique après 30s)");

            // Nettoyer après 30 secondes
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (suppressionsEnAttente.remove(initiator.getUniqueId(), nomEntreprise)) {
                    initiator.sendMessage(ChatColor.YELLOW + "Demande de suppression de '" + nomEntreprise + "' annulée (timeout).");
                }
            }, 30 * 20L); // 30 secondes
        }
    }
    // --- Fin Suppression / Dissolution ---

    // --- Création / Invitations ---
    /**
     * FIX HAUTE + MOYENNE: Validation du nom d'entreprise à la création (centralisée)
     */
    public void declareEntreprise(Player maireCreateur, String ville, String nomEntreprise, String type, Player gerantCible, String siret, double coutCreation) {
        // FIX MOYENNE: Utiliser la validation centralisée
        if (!isValidEntrepriseName(nomEntreprise, maireCreateur)) {
            return;
        }

        if (entreprises.containsKey(nomEntreprise)) {
            maireCreateur.sendMessage(ChatColor.RED + "Nom entreprise déjà pris.");
            return;
        }

        Entreprise nouvelleEntreprise = new Entreprise(nomEntreprise, ville, type, gerantCible.getName(), gerantCible.getUniqueId().toString(), new HashSet<>(), 0.0, siret);
        nouvelleEntreprise.addTransaction(new Transaction(TransactionType.CREATION_COST, coutCreation, "Frais de création", gerantCible.getName()));
        entreprises.put(nomEntreprise, nouvelleEntreprise);
        // FIX PERFORMANCE HAUTE: Maintenir l'index SIRET
        entreprisesBySiret.put(siret, nouvelleEntreprise);
        activiteProductiveHoraireValeur.put(nomEntreprise, 0.0);

        // FIX MOYENNE: Log opération critique de création
        plugin.getLogger().info("[OPERATION CRITIQUE] Création entreprise '" + nomEntreprise + "' (SIRET: " + siret +
            ", Type: " + type + ") par maire " + maireCreateur.getName() + " pour gérant " + gerantCible.getName() +
            " (Ville: " + ville + ", Coût: " + String.format("%.2f€", coutCreation) + ")");

        saveEntreprises();
        maireCreateur.sendMessage(ChatColor.GREEN + "Entreprise '" + nomEntreprise + "' (Type: " + type + ") créée pour " + gerantCible.getName() + " à " + ville + ".");
        gerantCible.sendMessage(ChatColor.GREEN + "Félicitations ! Vous gérez '" + nomEntreprise + "'. Coût: " + String.format("%,.2f", coutCreation) + "€.");
    }
// Dans EntrepriseManagerLogic.java

    public void inviterEmploye(Player gerant, String nomEntreprise, Player joueurInvite) {
        Entreprise entreprise = getEntreprise(nomEntreprise);
        if (entreprise == null || !entreprise.getGerant().equalsIgnoreCase(gerant.getName())) {
            gerant.sendMessage(ChatColor.RED + "Action impossible (vous n'êtes pas gérant de cette entreprise ou elle n'existe pas).");
            return;
        }
        if (gerant.getName().equalsIgnoreCase(joueurInvite.getName())) {
            gerant.sendMessage(ChatColor.RED + "Vous ne pouvez pas vous inviter vous-même comme employé.");
            return;
        }

        if (entreprise.getEmployes().contains(joueurInvite.getName()) || entreprise.getGerant().equalsIgnoreCase(joueurInvite.getName())) {
            gerant.sendMessage(ChatColor.RED + joueurInvite.getName() + " est déjà affilié à votre entreprise '" + nomEntreprise + "'.");
            return;
        }

        int maxSalariedJobs = plugin.getConfig().getInt("finance.max-travail-joueur", 1);
        int currentSalariedJobs = countPlayerSalariedJobs(joueurInvite.getName());
        plugin.getLogger().log(Level.INFO, "[DEBUG INVITE] " + joueurInvite.getName() + " a " + currentSalariedJobs + " emploi(s) salarié(s). Limite: " + maxSalariedJobs);

        if (currentSalariedJobs >= maxSalariedJobs) {
            gerant.sendMessage(ChatColor.RED + joueurInvite.getName() + " a atteint la limite de " + maxSalariedJobs + " emploi(s) salarié(s) et ne peut pas en rejoindre un de plus.");
            joueurInvite.sendMessage(ChatColor.YELLOW + "Vous ne pouvez pas accepter cette invitation car vous avez atteint votre limite d'emplois salariés ("+maxSalariedJobs+").");
            plugin.getLogger().log(Level.INFO, "[DEBUG INVITE] Échec: " + joueurInvite.getName() + " a atteint sa limite d'emplois salariés (" + currentSalariedJobs + "/" + maxSalariedJobs + ").");
            return;
        }

        // --- CORRECTION IMPORTANTE ---
        // Utiliser la limite d'employés actuelle de l'entreprise basée sur son niveau
        int maxEmployesActuel = getLimiteMaxEmployesActuelle(entreprise); // Utilise la méthode corrigée
        if (entreprise.getEmployes().size() >= maxEmployesActuel) {
            gerant.sendMessage(ChatColor.RED + "Votre entreprise '" + nomEntreprise + "' a atteint sa limite d'employés actuelle (" + maxEmployesActuel + "). Améliorez votre entreprise pour en recruter plus.");
            return;
        }
        // --- FIN CORRECTION ---

        double distanceMaxInvitation = plugin.getConfig().getDouble("invitation.distance-max", 10.0);
        if (!gerant.getWorld().equals(joueurInvite.getWorld()) || gerant.getLocation().distanceSquared(joueurInvite.getLocation()) > distanceMaxInvitation * distanceMaxInvitation) {
            gerant.sendMessage(ChatColor.RED + joueurInvite.getName() + " est trop loin pour être invité.");
            return;
        }

        invitations.put(joueurInvite.getName(), nomEntreprise);
        envoyerInvitationVisuelle(joueurInvite, nomEntreprise, gerant.getName(), entreprise.getType());
        gerant.sendMessage(ChatColor.GREEN + "Invitation pour rejoindre '" + nomEntreprise + "' envoyée à " + joueurInvite.getName() + ".");
        plugin.getLogger().log(Level.INFO, "[DEBUG INVITE] Invitation envoyée à " + joueurInvite.getName() + " pour " + nomEntreprise);
    }
    private void envoyerInvitationVisuelle(Player joueurInvite, String nomEntreprise, String nomGerant, String typeEntreprise) {
        TextComponent msg = new TextComponent("------------------------------------------\n"); msg.setColor(net.md_5.bungee.api.ChatColor.GOLD);
        TextComponent invite = new TextComponent(nomGerant + " (Gérant '" + nomEntreprise + "' - " + typeEntreprise + ") vous invite !"); invite.setColor(net.md_5.bungee.api.ChatColor.YELLOW);
        msg.addExtra(invite); msg.addExtra("\n");
        TextComponent accepter = new TextComponent("[ACCEPTER]"); accepter.setColor(net.md_5.bungee.api.ChatColor.GREEN); accepter.setBold(true); accepter.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/entreprise accepter")); accepter.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Rejoindre").create()));
        TextComponent refuser = new TextComponent("   [REFUSER]"); refuser.setColor(net.md_5.bungee.api.ChatColor.RED); refuser.setBold(true); refuser.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/entreprise refuser")); refuser.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Refuser").create()));
        TextComponent actions = new TextComponent("        "); actions.addExtra(accepter); actions.addExtra(refuser);
        msg.addExtra(actions); msg.addExtra("\n------------------------------------------");
        joueurInvite.spigot().sendMessage(msg);
    }
    public void handleAccepterCommand(Player joueur) {
        String nomEntreprise = invitations.remove(joueur.getName());
        if (nomEntreprise == null) {
            joueur.sendMessage(ChatColor.RED + "Vous n'avez aucune invitation en attente ou elle a expiré.");
            return;
        }
        Entreprise entreprise = getEntreprise(nomEntreprise);
        if (entreprise == null) {
            joueur.sendMessage(ChatColor.RED + "L'entreprise '" + nomEntreprise + "' n'existe plus.");
            return;
        }

        int maxSalariedJobs = plugin.getConfig().getInt("finance.max-travail-joueur", 1);
        int currentSalariedJobs = countPlayerSalariedJobs(joueur.getName());
        plugin.getLogger().log(Level.INFO, "[DEBUG ACCEPTER_INVITE] " + joueur.getName() + " a " + currentSalariedJobs + " emploi(s) salarié(s). Limite: " + maxSalariedJobs);

        if (currentSalariedJobs >= maxSalariedJobs) {
            joueur.sendMessage(ChatColor.RED + "Vous avez atteint votre limite de " + maxSalariedJobs + " emploi(s) salarié(s) et ne pouvez pas rejoindre cette entreprise.");
            Player gerantPlayer = Bukkit.getPlayerExact(entreprise.getGerant());
            if (gerantPlayer != null) {
                gerantPlayer.sendMessage(ChatColor.YELLOW + joueur.getName() + " a tenté de rejoindre '" + nomEntreprise + "' mais a atteint sa limite d'emplois salariés.");
            }
            plugin.getLogger().log(Level.INFO, "[DEBUG ACCEPTER_INVITE] Échec: " + joueur.getName() + " a atteint sa limite d'emplois salariés (" + currentSalariedJobs + "/" + maxSalariedJobs + ") en acceptant pour " + nomEntreprise);
            return;
        }

        // --- CORRECTION IMPORTANTE ---
        // Utiliser la limite d'employés actuelle de l'entreprise basée sur son niveau
        int maxEmployesActuel = getLimiteMaxEmployesActuelle(entreprise); // Utilise la méthode corrigée
        if (entreprise.getEmployes().size() >= maxEmployesActuel) {
            joueur.sendMessage(ChatColor.RED + "L'entreprise '" + nomEntreprise + "' a atteint sa limite d'employés actuelle ("+ maxEmployesActuel +"). Impossible de rejoindre.");
            Player gerantPlayer = Bukkit.getPlayerExact(entreprise.getGerant());
            if (gerantPlayer != null) {
                gerantPlayer.sendMessage(ChatColor.YELLOW + joueur.getName() + " a tenté de rejoindre '" + nomEntreprise + "' mais l'entreprise est pleine (limite actuelle: " + maxEmployesActuel + ").");
            }
            return;
        }
        // --- FIN CORRECTION ---

        if (entreprise.getGerant().equalsIgnoreCase(joueur.getName())) {
            joueur.sendMessage(ChatColor.RED + "Vous êtes déjà le gérant de cette entreprise, vous ne pouvez pas la rejoindre en tant qu'employé.");
            return;
        }

        addEmploye(nomEntreprise, joueur.getName(), joueur.getUniqueId());
        joueur.sendMessage(ChatColor.GREEN + "Vous avez rejoint l'entreprise '" + nomEntreprise + "' !");
        EmployeeActivityRecord record = entreprise.getOrCreateEmployeeActivityRecord(joueur.getUniqueId(), joueur.getName());
        record.startSession();
        Player gerantPlayer = Bukkit.getPlayerExact(entreprise.getGerant());
        if (gerantPlayer != null && gerantPlayer.isOnline()) {
            gerantPlayer.sendMessage(ChatColor.GREEN + joueur.getName() + " a rejoint votre entreprise '" + nomEntreprise + "'.");
        }
        plugin.getLogger().log(Level.INFO, "[DEBUG ACCEPTER_INVITE] " + joueur.getName() + " a rejoint " + nomEntreprise + " avec succès.");
    }
    public void handleRefuserCommand(Player joueur) {
        String nomEntreprise = invitations.remove(joueur.getName());
        if (nomEntreprise == null) { joueur.sendMessage(ChatColor.RED + "Aucune invitation à refuser."); return; }
        joueur.sendMessage(ChatColor.YELLOW + "Invitation pour '" + nomEntreprise + "' refusée.");
        Entreprise entreprise = getEntreprise(nomEntreprise); if (entreprise != null) { Player gerantPlayer = Bukkit.getPlayerExact(entreprise.getGerant()); if (gerantPlayer != null) gerantPlayer.sendMessage(ChatColor.YELLOW + joueur.getName() + " a refusé l'invitation."); }
    }
    /**
     * FIX HAUTE: Ne pas écraser joinDate si l'employé existe déjà
     */
    public void addEmploye(String nomEntreprise, String nomJoueur, UUID joueurUUID) {
        Entreprise entreprise = entreprises.get(nomEntreprise);
        if (entreprise != null && entreprise.getEmployesInternal().add(nomJoueur)) {
            entreprise.setPrimePourEmploye(joueurUUID.toString(), 0.0);

            // Vérifier si l'employé avait déjà un record (réembauche)
            EmployeeActivityRecord existingRecord = entreprise.getEmployeeActivityRecord(joueurUUID);
            boolean isRejoining = (existingRecord != null && existingRecord.joinDate != null);

            EmployeeActivityRecord record = entreprise.getOrCreateEmployeeActivityRecord(joueurUUID, nomJoueur);

            // Ne définir joinDate que pour les nouveaux employés
            if (!isRejoining) {
                record.joinDate = LocalDateTime.now();
                plugin.getLogger().fine("Nouvel employé ajouté: " + nomJoueur + " dans " + nomEntreprise);
            } else {
                plugin.getLogger().fine("Employé réembauché: " + nomJoueur + " (ancienneté conservée)");
            }

            saveEntreprises();
        }
    }
    // --- Fin Création / Invitations ---

    // --- Départ et Licenciement (AVEC HISTORIQUE) ---
    public void kickEmploye(Player gerant, String nomEntreprise, String nomEmployeAKick) {
        Entreprise entreprise = getEntreprise(nomEntreprise);
        // FIX MOYENNE: Message d'erreur plus spécifique
        if (entreprise == null) {
            gerant.sendMessage(ChatColor.RED + "Impossible de licencier: l'entreprise '" + nomEntreprise + "' n'existe pas.");
            return;
        }
        if (!entreprise.getGerant().equalsIgnoreCase(gerant.getName())) {
            gerant.sendMessage(ChatColor.RED + "Impossible de licencier: vous n'êtes pas le gérant de '" + nomEntreprise + "'.");
            return;
        }
        OfflinePlayer employeOffline = Bukkit.getOfflinePlayer(nomEmployeAKick);
        if ((!employeOffline.hasPlayedBefore() && !employeOffline.isOnline()) || !entreprise.getEmployes().contains(nomEmployeAKick)) { gerant.sendMessage(ChatColor.RED + "Employé '" + nomEmployeAKick + "' introuvable ou pas ici."); return; }
        UUID employeUUID = employeOffline.getUniqueId();

        // FIX MOYENNE: Système de confirmation pour licenciement
        KickRequest requestEnAttente = kicksEnAttente.get(gerant.getUniqueId());

        if (requestEnAttente != null &&
            requestEnAttente.entrepriseName.equals(nomEntreprise) &&
            requestEnAttente.employeeName.equals(nomEmployeAKick) &&
            !requestEnAttente.isExpired()) {

            // Confirmation reçue, procéder au licenciement
            kicksEnAttente.remove(gerant.getUniqueId());
        } else {
            // Première demande, avertir
            kicksEnAttente.put(gerant.getUniqueId(), new KickRequest(nomEntreprise, nomEmployeAKick));

            gerant.sendMessage("");
            gerant.sendMessage(ChatColor.GOLD + "⚠ CONFIRMATION LICENCIEMENT ⚠");
            gerant.sendMessage(ChatColor.YELLOW + "Entreprise: " + ChatColor.WHITE + nomEntreprise);
            gerant.sendMessage(ChatColor.YELLOW + "Employé à licencier: " + ChatColor.WHITE + nomEmployeAKick);
            gerant.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Pour confirmer, retapez la même commande dans les 30 secondes.");
            gerant.sendMessage("");

            // Timeout après 30 secondes
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                KickRequest req = kicksEnAttente.remove(gerant.getUniqueId());
                if (req != null && req.entrepriseName.equals(nomEntreprise) && req.employeeName.equals(nomEmployeAKick)) {
                    gerant.sendMessage(ChatColor.GRAY + "[Entreprise] Demande de licenciement expirée.");
                }
            }, CONFIRMATION_TIMEOUT_TICKS);

            return;
        }

        if (entreprise.getEmployesInternal().remove(nomEmployeAKick)) {
            recordPlayerHistoryEntry(employeUUID, entreprise, "Employé", LocalDateTime.now());
            entreprise.retirerPrimeEmploye(employeUUID.toString());
            EmployeeActivityRecord record = entreprise.getEmployeeActivityRecord(employeUUID); if (record != null) record.endSession();
            saveEntreprises();
            gerant.sendMessage(ChatColor.GREEN + nomEmployeAKick + " viré de '" + nomEntreprise + "'.");
            Player onlineEmp = employeOffline.getPlayer(); if (onlineEmp != null) { onlineEmp.sendMessage(ChatColor.RED + "Vous avez été viré de '" + nomEntreprise + "'."); }
        } else {
            // FIX MOYENNE: Message d'erreur plus spécifique
            gerant.sendMessage(ChatColor.RED + "Erreur lors du licenciement de " + nomEmployeAKick + ": employé introuvable dans la liste (corruption de données?).");
            plugin.getLogger().warning("[Entreprise] Échec du remove() pour " + nomEmployeAKick + " de l'entreprise '" + nomEntreprise + "' - incohérence de données");
        }
    }
    public void leaveEntreprise(Player joueur, String nomEntreprise) {
        Entreprise entreprise = getEntreprise(nomEntreprise);
        if (entreprise == null || !entreprise.getEmployes().contains(joueur.getName())) { joueur.sendMessage(ChatColor.RED + "Vous n'êtes pas employé ici."); return; }
        if (entreprise.getGerant().equalsIgnoreCase(joueur.getName())) { joueur.sendMessage(ChatColor.RED + "Le gérant doit utiliser /entreprise delete."); return; }

        if (entreprise.getEmployesInternal().remove(joueur.getName())) {
            recordPlayerHistoryEntry(joueur.getUniqueId(), entreprise, "Employé", LocalDateTime.now());
            entreprise.retirerPrimeEmploye(joueur.getUniqueId().toString());
            EmployeeActivityRecord record = entreprise.getEmployeeActivityRecord(joueur.getUniqueId()); if (record != null) record.endSession();
            saveEntreprises();
            joueur.sendMessage(ChatColor.GREEN + "Vous avez quitté '" + nomEntreprise + "'.");
            Player gerantPlayer = Bukkit.getPlayerExact(entreprise.getGerant()); if (gerantPlayer != null && gerantPlayer.isOnline()) { gerantPlayer.sendMessage(ChatColor.YELLOW + joueur.getName() + " a quitté '" + nomEntreprise + "'."); }
        } else {
            // FIX MOYENNE: Message d'erreur plus spécifique
            joueur.sendMessage(ChatColor.RED + "Erreur lors du départ de l'entreprise: vous n'étiez pas dans la liste des employés (corruption de données?).");
            plugin.getLogger().warning("[Entreprise] Échec du remove() pour " + joueur.getName() + " quittant l'entreprise '" + nomEntreprise + "' - incohérence de données");
        }
    }
    // --- Fin Départ / Licenciement ---

    // --- Création / Contrat ---

    /**
     * FIX MOYENNE: Validation centralisée du nom d'entreprise
     * Prévient les caractères spéciaux qui peuvent causer des problèmes (YAML injection, etc.)
     */
    /**
     * FIX BASSE #32-33: Validation robuste des noms d'entreprise
     */
    private boolean isValidEntrepriseName(String name, Player player) {
        // Utiliser le NameValidator pour une validation robuste
        com.gravityyfh.roleplaycity.util.NameValidator.ValidationResult validation =
            plugin.getNameValidator().validateEntrepriseName(name);

        if (!validation.isValid()) {
            player.sendMessage(ChatColor.RED + "❌ " + validation.getError());
            return false;
        }

        return true;
    }

    public void proposerCreationEntreprise(Player maire, Player gerantCible, String type, String ville, String nomEntreprisePropose, String siret) {
        double coutCreation = plugin.getConfig().getDouble("types-entreprise." + type + ".cout-creation", 0.0);
        double distanceMaxCreation = plugin.getConfig().getDouble("creation.distance-max-maire-gerant", 15.0);

        plugin.getLogger().log(Level.INFO, "[DEBUG CREATION] Début proposition pour " + nomEntreprisePropose + " par " + maire.getName() + " pour gérant " + gerantCible.getName());

        // FIX MOYENNE: Valider le nom avant de créer la demande
        if (!isValidEntrepriseName(nomEntreprisePropose, maire)) {
            plugin.getLogger().log(Level.INFO, "[DEBUG CREATION] Échec: Nom d'entreprise invalide.");
            return;
        }

        if (!gerantCible.isOnline()) {
            maire.sendMessage(ChatColor.RED + gerantCible.getName() + " est hors ligne.");
            plugin.getLogger().log(Level.INFO, "[DEBUG CREATION] Échec: Gérant cible hors ligne.");
            return;
        }
        if (!maire.getWorld().equals(gerantCible.getWorld()) || maire.getLocation().distanceSquared(gerantCible.getLocation()) > distanceMaxCreation * distanceMaxCreation) {
            maire.sendMessage(ChatColor.RED + gerantCible.getName() + " est trop loin.");
            plugin.getLogger().log(Level.INFO, "[DEBUG CREATION] Échec: Gérant cible trop loin.");
            return;
        }

        // Vérification 1: Limite du nombre d'entreprises que le gerantCible peut GÉRER
        int maxManagedByGerant = plugin.getConfig().getInt("finance.max-entreprises-par-gerant", 1);
        int currentManagedCount = getEntreprisesGereesPar(gerantCible.getName()).size();
        plugin.getLogger().log(Level.INFO, "[DEBUG CREATION] " + gerantCible.getName() + " gère " + currentManagedCount + " entreprise(s). Limite de gérance: " + maxManagedByGerant);
        if (currentManagedCount >= maxManagedByGerant) {
            maire.sendMessage(ChatColor.RED + gerantCible.getName() + " gère déjà le maximum de " + maxManagedByGerant + " entreprise(s).");
            plugin.getLogger().log(Level.INFO, "[DEBUG CREATION] Échec: " + gerantCible.getName() + " a atteint sa limite de gérance (" + currentManagedCount + "/" + maxManagedByGerant + ").");
            return;
        }

        if (entreprises.containsKey(nomEntreprisePropose)) {
            maire.sendMessage(ChatColor.RED + "Le nom d'entreprise '" + nomEntreprisePropose + "' est déjà pris.");
            plugin.getLogger().log(Level.INFO, "[DEBUG CREATION] Échec: Nom d'entreprise déjà pris.");
            return;
        }

        DemandeCreation demande = new DemandeCreation(maire, gerantCible, type, ville, siret, nomEntreprisePropose, coutCreation, plugin.getConfig().getLong("creation.delai-validation-ms", 60000L));
        demandesEnAttente.put(gerantCible.getUniqueId(), demande);
        maire.sendMessage(ChatColor.GREEN + "Proposition de création d'entreprise envoyée à " + gerantCible.getName() + " (Type: " + type + ", Nom: " + nomEntreprisePropose + "). Délai: " + (demande.getExpirationTimeMillis() - System.currentTimeMillis())/1000 + "s.");
        envoyerInvitationVisuelleContrat(gerantCible, demande);
        plugin.getLogger().log(Level.INFO, "[DEBUG CREATION] Proposition envoyée avec succès pour " + nomEntreprisePropose + " à " + gerantCible.getName());
    }
    private void envoyerInvitationVisuelleContrat(Player gerantCible, DemandeCreation demande) {
        gerantCible.sendMessage(ChatColor.GOLD + "---------------- Contrat de Gérance ----------------");
        gerantCible.sendMessage(ChatColor.AQUA + "Maire: " + ChatColor.WHITE + demande.maire.getName()); gerantCible.sendMessage(ChatColor.AQUA + "Ville: " + ChatColor.WHITE + demande.ville);
        gerantCible.sendMessage(ChatColor.AQUA + "Type: " + ChatColor.WHITE + demande.type); gerantCible.sendMessage(ChatColor.AQUA + "Nom: " + ChatColor.WHITE + demande.nomEntreprise);
        gerantCible.sendMessage(ChatColor.AQUA + "SIRET: " + ChatColor.WHITE + demande.siret); gerantCible.sendMessage(ChatColor.YELLOW + "Coût: " + ChatColor.GREEN + String.format("%,.2f€", demande.cout));
        long remainingSeconds = (demande.getExpirationTimeMillis() - System.currentTimeMillis()) / 1000; gerantCible.sendMessage(ChatColor.YELLOW + "Délai: " + remainingSeconds + "s.");
        TextComponent accepterMsg = new TextComponent("        [VALIDER CONTRAT]"); accepterMsg.setColor(net.md_5.bungee.api.ChatColor.GREEN); accepterMsg.setBold(true); accepterMsg.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/entreprise validercreation")); accepterMsg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Accepter (coût: " + String.format("%,.2f€", demande.cout) + ")").create()));
        TextComponent refuserMsg = new TextComponent("   [REFUSER CONTRAT]"); refuserMsg.setColor(net.md_5.bungee.api.ChatColor.RED); refuserMsg.setBold(true); refuserMsg.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/entreprise annulercreation")); refuserMsg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Refuser").create()));
        TextComponent ligneActions = new TextComponent(""); ligneActions.addExtra(accepterMsg); ligneActions.addExtra(refuserMsg);
        gerantCible.spigot().sendMessage(ligneActions); gerantCible.sendMessage(ChatColor.GOLD + "--------------------------------------------------");
        UUID gerantUUID = gerantCible.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (demandesEnAttente.containsKey(gerantUUID) && demandesEnAttente.get(gerantUUID).equals(demande)) { demandesEnAttente.remove(gerantUUID); Player gOnline = Bukkit.getPlayer(gerantUUID); if (gOnline != null) gOnline.sendMessage(ChatColor.RED + "Offre pour '" + demande.nomEntreprise + "' expirée."); Player mOnline = Bukkit.getPlayer(demande.maire.getUniqueId()); if (mOnline != null) mOnline.sendMessage(ChatColor.RED + "Offre pour '" + demande.nomEntreprise + "' (à " + demande.gerant.getName() + ") expirée."); } }, (demande.getExpirationTimeMillis() - System.currentTimeMillis()) / 50);
    }
    public void validerCreationEntreprise(Player gerantSignataire) {
        DemandeCreation demande = demandesEnAttente.remove(gerantSignataire.getUniqueId());
        if (demande == null) { gerantSignataire.sendMessage(ChatColor.RED + "Aucune demande."); return; }
        if (demande.isExpired()) { gerantSignataire.sendMessage(ChatColor.RED + "Demande expirée."); Player mOnline = Bukkit.getPlayer(demande.maire.getUniqueId()); if (mOnline != null) mOnline.sendMessage(ChatColor.RED + "Demande pour '" + demande.nomEntreprise + "' (à " + gerantSignataire.getName() + ") expirée."); return; }
        if (!RoleplayCity.getEconomy().has(gerantSignataire, demande.cout)) { gerantSignataire.sendMessage(ChatColor.RED + "Fonds insuffisants (" + String.format("%,.2f€", demande.cout) + ")."); Player mOnline = Bukkit.getPlayer(demande.maire.getUniqueId()); if (mOnline != null) mOnline.sendMessage(ChatColor.RED + "Création échouée (fonds " + gerantSignataire.getName() + " insuffisants)."); return; }
        EconomyResponse er = RoleplayCity.getEconomy().withdrawPlayer(gerantSignataire, demande.cout);
        if (!er.transactionSuccess()) { gerantSignataire.sendMessage(ChatColor.RED + "Erreur paiement: " + er.errorMessage); Player mOnline = Bukkit.getPlayer(demande.maire.getUniqueId()); if (mOnline != null) mOnline.sendMessage(ChatColor.RED + "Erreur paiement par " + gerantSignataire.getName() + "."); return; }
        if (entreprises.containsKey(demande.nomEntreprise)) { gerantSignataire.sendMessage(ChatColor.RED + "Nom '" + demande.nomEntreprise + "' pris. Annulé."); Player mOnline = Bukkit.getPlayer(demande.maire.getUniqueId()); if (mOnline != null) mOnline.sendMessage(ChatColor.RED + "Nom '" + demande.nomEntreprise + "' pris. Annulé."); RoleplayCity.getEconomy().depositPlayer(gerantSignataire, demande.cout); gerantSignataire.sendMessage(ChatColor.YELLOW + "Frais remboursés."); return; }
        declareEntreprise(demande.maire, demande.ville, demande.nomEntreprise, demande.type, gerantSignataire, demande.siret, demande.cout);
        gerantSignataire.sendMessage(ChatColor.GREEN + "Contrat accepté! Frais (" + String.format("%,.2f€", demande.cout) + ") payés. '" + demande.nomEntreprise + "' créée !");
        Player mOnline = Bukkit.getPlayer(demande.maire.getUniqueId()); if (mOnline != null) mOnline.sendMessage(ChatColor.GREEN + gerantSignataire.getName() + " a validé. '" + demande.nomEntreprise + "' créée.");
    }
    public void refuserCreationEntreprise(Player gerantSignataire) {
        DemandeCreation demande = demandesEnAttente.remove(gerantSignataire.getUniqueId());
        if (demande == null) { gerantSignataire.sendMessage(ChatColor.RED + "Aucune demande."); return; }
        gerantSignataire.sendMessage(ChatColor.YELLOW + "Contrat pour '" + demande.nomEntreprise + "' refusé.");
        Player maireOnline = Bukkit.getPlayer(demande.maire.getUniqueId()); if (maireOnline != null) maireOnline.sendMessage(ChatColor.RED + gerantSignataire.getName() + " a refusé le contrat.");
    }
    // --- Fin Création / Contrat ---

    public void renameEntreprise(Player gerant, String ancienNom, String nouveauNom) {
        Entreprise entreprise = getEntreprise(ancienNom);
        if (entreprise == null) {
            gerant.sendMessage(ChatColor.RED + "L'entreprise '" + ancienNom + "' n'a pas été trouvée.");
            return;
        }

        boolean isAdminAgissantSurAutrui = gerant.hasPermission("entreprisemanager.admin.renameany") && !entreprise.getGerant().equalsIgnoreCase(gerant.getName());
        boolean isGerantProprietaire = entreprise.getGerant().equalsIgnoreCase(gerant.getName());

        if (!isGerantProprietaire && !isAdminAgissantSurAutrui) {
            gerant.sendMessage(ChatColor.RED + "Vous n'avez pas la permission de renommer cette entreprise.");
            return;
        }

        if (ancienNom.equalsIgnoreCase(nouveauNom)) {
            gerant.sendMessage(ChatColor.RED + "Le nouveau nom est identique à l'ancien.");
            return;
        }
        if (entreprises.containsKey(nouveauNom)) {
            gerant.sendMessage(ChatColor.RED + "Le nom d'entreprise '" + nouveauNom + "' est déjà pris.");
            return;
        }
        if (!nouveauNom.matches("^[a-zA-Z0-9_\\-]+$") || nouveauNom.length() < 3 || nouveauNom.length() > 30) {
            gerant.sendMessage(ChatColor.RED + "Nom invalide. Utilisez des lettres (a-z, A-Z), chiffres (0-9), tirets (_) ou traits d'union (-). Longueur: 3-30 caractères.");
            return;
        }

        double coutRenommage = plugin.getConfig().getDouble("rename-cost", 2500.0);

        if (coutRenommage > 0) {
            if (entreprise.getSolde() < coutRenommage) {
                gerant.sendMessage(ChatColor.RED + "Le solde de l'entreprise (" + String.format("%,.2f€", entreprise.getSolde()) + ") est insuffisant pour couvrir les frais de renommage (" + String.format("%,.2f€", coutRenommage) + ").");
                gerant.sendMessage(ChatColor.RED + "Veuillez déposer des fonds dans l'entreprise via le menu de gestion ('Déposer Argent') pour pouvoir la renommer."); // Indication ajoutée
                return;
            }
            entreprise.setSolde(entreprise.getSolde() - coutRenommage);
            entreprise.addTransaction(new Transaction(TransactionType.RENAME_COST, coutRenommage, "Renommage: " + ancienNom + " -> " + nouveauNom, gerant.getName()));
            gerant.sendMessage(ChatColor.YELLOW + "Frais de renommage (" + String.format("%,.2f€", coutRenommage) + ") déduits du solde de l'entreprise. Nouveau solde : " + String.format("%,.2f€", entreprise.getSolde()) + ".");
        }

        entreprises.remove(ancienNom);
        Double caProductifExistant = activiteProductiveHoraireValeur.remove(ancienNom);
        Double caMagasinExistant = activiteMagasinHoraireValeur.remove(ancienNom);

        entreprise.setNom(nouveauNom);
        entreprises.put(nouveauNom, entreprise);

        activiteProductiveHoraireValeur.put(nouveauNom, caProductifExistant != null ? caProductifExistant : 0.0);
        activiteMagasinHoraireValeur.put(nouveauNom, caMagasinExistant != null ? caMagasinExistant : 0.0);

        saveEntreprises();

        String msgConfirm = ChatColor.GREEN + "L'entreprise '" + ancienNom + "' a été renommée en '" + nouveauNom + "'.";
        if (coutRenommage > 0) {
            msgConfirm += " Les frais de " + String.format("%,.2f€", coutRenommage) + " ont été payés par l'entreprise.";
        }
        gerant.sendMessage(msgConfirm);
    }
    public void definirPrime(String nomEntreprise, String nomEmploye, double montantPrime) {
        Entreprise entreprise = getEntreprise(nomEntreprise);
        if (entreprise == null) { plugin.getLogger().warning("Tentative prime pour ent. inexistante: " + nomEntreprise); return; }
        OfflinePlayer employeOffline = Bukkit.getOfflinePlayer(nomEmploye); UUID empUUID = employeOffline.getUniqueId();
        if (!entreprise.getEmployes().contains(nomEmploye)) { Player p = Bukkit.getPlayerExact(entreprise.getGerant()); if (p != null) p.sendMessage(ChatColor.RED + "'" + nomEmploye + "' n'est pas/plus ici."); return; }
        if (montantPrime < 0) { Player p = Bukkit.getPlayerExact(entreprise.getGerant()); if (p != null) p.sendMessage(ChatColor.RED + "Prime négative invalide."); return; }
        entreprise.setPrimePourEmploye(empUUID.toString(), montantPrime);
        saveEntreprises();
    }
    public String getTownNameFromPlayer(Player player) {
        // Utilise notre système de ville au lieu de Towny
        String townName = plugin.getTownManager().getPlayerTown(player.getUniqueId());
        return townName;
    }
    /**
     * FIX HAUTE: Génération de SIRET avec garantie d'unicité
     */
    public String generateSiret() {
        int longueur = Math.min(plugin.getConfig().getInt("siret.longueur", 14), 32);
        String siret;
        int tentatives = 0;
        int maxTentatives = 100;

        do {
            siret = UUID.randomUUID().toString().replace("-", "").substring(0, longueur);
            tentatives++;

            if (tentatives >= maxTentatives) {
                plugin.getLogger().severe("Impossible de générer un SIRET unique après " + maxTentatives + " tentatives!");
                return null;
            }
        } while (getEntrepriseBySiret(siret) != null);

        plugin.getLogger().fine("SIRET unique généré en " + tentatives + " tentative(s): " + siret);
        return siret;
    }
    public boolean estMaire(Player joueur) {
        // Utilise notre système de ville au lieu de Towny
        return plugin.getTownManager().isPlayerMayor(joueur.getUniqueId());
    }
    public Set<String> getEmployesDeLEntreprise(String nomEntreprise) { Entreprise entreprise = getEntreprise(nomEntreprise); return (entreprise != null) ? entreprise.getEmployes() : Collections.emptySet(); }
    public void retirerArgent(Player player, String nomEntreprise, double montant) {
        Entreprise entreprise = getEntreprise(nomEntreprise);
        if (entreprise == null) { player.sendMessage(ChatColor.RED + "Ent. '" + nomEntreprise + "' non trouvée."); return; }
        if (!entreprise.getGerant().equalsIgnoreCase(player.getName())) { player.sendMessage(ChatColor.RED + "Seul le gérant peut retirer."); return; }
        if (montant <= 0) { player.sendMessage(ChatColor.RED + "Montant doit être positif."); return; }
        if (entreprise.getSolde() < montant) { player.sendMessage(ChatColor.RED + "Solde ent. (" + String.format("%,.2f€", entreprise.getSolde()) + ") insuffisant."); return; }

        // FIX MOYENNE: Système de confirmation pour montants >= 1000€
        if (montant >= 1000.0) {
            WithdrawalRequest requestEnAttente = retraitsEnAttente.get(player.getUniqueId());

            if (requestEnAttente != null &&
                requestEnAttente.entrepriseName.equals(nomEntreprise) &&
                Math.abs(requestEnAttente.amount - montant) < 0.01 &&
                !requestEnAttente.isExpired()) {

                // Confirmation reçue, procéder au retrait
                retraitsEnAttente.remove(player.getUniqueId());
            } else {
                // Première demande, avertir
                retraitsEnAttente.put(player.getUniqueId(), new WithdrawalRequest(nomEntreprise, montant));

                player.sendMessage("");
                player.sendMessage(ChatColor.GOLD + "⚠ CONFIRMATION RETRAIT D'ARGENT ⚠");
                player.sendMessage(ChatColor.YELLOW + "Entreprise: " + ChatColor.WHITE + nomEntreprise);
                player.sendMessage(ChatColor.YELLOW + "Montant: " + ChatColor.GREEN + String.format("%,.2f€", montant));
                player.sendMessage(ChatColor.YELLOW + "Solde restant: " + ChatColor.WHITE + String.format("%,.2f€", entreprise.getSolde() - montant));
                player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Pour confirmer, retapez la même commande dans les 30 secondes.");
                player.sendMessage("");

                // Timeout après 30 secondes
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    WithdrawalRequest req = retraitsEnAttente.remove(player.getUniqueId());
                    if (req != null && req.entrepriseName.equals(nomEntreprise)) {
                        player.sendMessage(ChatColor.GRAY + "[Entreprise] Demande de retrait expirée.");
                    }
                }, CONFIRMATION_TIMEOUT_TICKS);

                return;
            }
        }

        // FIX MOYENNE: Log détaillé transaction Vault
        plugin.getLogger().info("[Vault] DEPOSIT (Retrait entreprise): " + player.getName() + " ← " + String.format("%.2f€", montant) +
            " (Entreprise: " + nomEntreprise + ", Solde avant: " + String.format("%.2f€", entreprise.getSolde()) + ")");

        EconomyResponse response = RoleplayCity.getEconomy().depositPlayer(player, montant);

        if (response.transactionSuccess()) {
            plugin.getLogger().info("[Vault] DEPOSIT SUCCESS (Retrait): " + player.getName() + " ← " + String.format("%.2f€", montant) +
                " (Balance joueur: " + String.format("%.2f€", response.balance) + ")");
            entreprise.setSolde(entreprise.getSolde() - montant);
            entreprise.addTransaction(new Transaction(TransactionType.WITHDRAWAL, montant, "Retrait par gérant " + player.getName(), player.getName()));
            saveEntreprises();
            player.sendMessage(ChatColor.GREEN + String.format("%,.2f€", montant) + " retirés de '" + nomEntreprise + "'. Solde: " + String.format("%,.2f€", entreprise.getSolde()) + ".");
        } else {
            plugin.getLogger().severe("[Vault] DEPOSIT FAILED (Retrait): " + player.getName() + " - " + montant + "€ - Reason: " + response.errorMessage);
            player.sendMessage(ChatColor.RED + "Erreur dépôt compte: " + response.errorMessage);
        }
    }
    public void deposerArgent(Player player, String nomEntreprise, double montant) {
        Entreprise entreprise = getEntreprise(nomEntreprise);
        if (entreprise == null) {
            player.sendMessage(ChatColor.RED + "L'entreprise '" + nomEntreprise + "' n'a pas été trouvée.");
            return;
        }
        if (!entreprise.getGerant().equalsIgnoreCase(player.getName()) && !entreprise.getEmployes().contains(player.getName())) {
            player.sendMessage(ChatColor.RED + "Seuls les membres de l'entreprise peuvent y déposer de l'argent.");
            return;
        }
        if (montant <= 0) {
            player.sendMessage(ChatColor.RED + "Le montant du dépôt doit être positif.");
            return;
        }
        if (!RoleplayCity.getEconomy().has(player, montant)) {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas assez d'argent sur votre compte personnel.");
            return;
        }

        // FIX HAUTE: Meilleur feedback utilisateur sur ajustement du montant
        double montantOriginal = montant;
        double soldeMaxActuel = getLimiteMaxSoldeActuelle(entreprise);

        if (entreprise.getSolde() + montant > soldeMaxActuel) {
            double montantAutorise = soldeMaxActuel - entreprise.getSolde();

            if (montantAutorise <= 0) {
                player.sendMessage(ChatColor.RED + "L'entreprise a atteint son solde maximum actuel (" + String.format("%,.2f", soldeMaxActuel) + "€).");
                player.sendMessage(ChatColor.GRAY + "Solde actuel: " + String.format("%,.2f", entreprise.getSolde()) + "€");
                return;
            }

            // Message clair sur l'ajustement
            player.sendMessage(ChatColor.YELLOW + "⚠ Le montant demandé dépasse le solde maximum !");
            player.sendMessage(ChatColor.GRAY + "Montant demandé: " + String.format("%,.2f", montantOriginal) + "€");
            player.sendMessage(ChatColor.GRAY + "Montant maximum autorisé: " + String.format("%,.2f", montantAutorise) + "€");
            player.sendMessage(ChatColor.GREEN + "→ Seuls " + String.format("%,.2f", montantAutorise) + "€ seront déposés.");

            montant = montantAutorise;

            if (montant <= 0) {
                player.sendMessage(ChatColor.RED + "Aucun dépôt possible sans dépasser le solde maximum.");
                return;
            }
        }

        // FIX MOYENNE: Log détaillé transaction Vault
        plugin.getLogger().info("[Vault] WITHDRAW (Dépôt entreprise): " + player.getName() + " → " + String.format("%.2f€", montant) +
            " (Entreprise: " + nomEntreprise + ", Solde avant: " + String.format("%.2f€", entreprise.getSolde()) + ")");

        EconomyResponse response = RoleplayCity.getEconomy().withdrawPlayer(player, montant);

        if (response.transactionSuccess()) {
            plugin.getLogger().info("[Vault] WITHDRAW SUCCESS (Dépôt): " + player.getName() + " → " + String.format("%.2f€", montant) +
                " (Balance joueur: " + String.format("%.2f€", response.balance) + ")");
            double nouveauSolde = entreprise.getSolde() + montant;
            // FIX MOYENNE: Vérifier cohérence du solde (ne devrait jamais être négatif après un dépôt)
            if (nouveauSolde < 0) {
                plugin.getLogger().severe("[COHERENCE] Solde négatif détecté après dépôt pour '" + nomEntreprise + "': " +
                    entreprise.getSolde() + " + " + montant + " = " + nouveauSolde);
                nouveauSolde = montant; // Corriger à au moins le montant déposé
            }
            entreprise.setSolde(nouveauSolde);
            entreprise.addTransaction(new Transaction(TransactionType.DEPOSIT, montant, "Dépôt par " + player.getName(), player.getName()));
            saveEntreprises();
            player.sendMessage(ChatColor.GREEN + String.format("%,.2f", montant) + "€ déposés dans '" + nomEntreprise + "'. Nouveau solde de l'entreprise : " + String.format("%,.2f", entreprise.getSolde()) + "€.");
        } else {
            plugin.getLogger().severe("[Vault] WITHDRAW FAILED (Dépôt): " + player.getName() + " - " + montant + "€ - Reason: " + response.errorMessage);
            player.sendMessage(ChatColor.RED + "Erreur lors du retrait de votre compte : " + response.errorMessage);
        }
    }
    // --- Fin Autres méthodes ---

    // --- Chargement / Sauvegarde (AVEC HISTORIQUE) ---
    private void loadEntreprises() {
        entreprises.clear();
        activiteProductiveHoraireValeur.clear();
        activiteMagasinHoraireValeur.clear();
        joueurActivitesRestrictions.clear();
        FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(entrepriseFile);
        if (!entrepriseFile.exists()) {
            plugin.getLogger().info("entreprise.yml non trouvé.");
            return;
        }
        ConfigurationSection entreprisesSection = currentConfig.getConfigurationSection("entreprises");
        if (entreprisesSection == null) {
            plugin.getLogger().info("Aucune section 'entreprises' dans entreprise.yml.");
            return;
        }
        int entreprisesChargees = 0;
        for (String nomEnt : entreprisesSection.getKeys(false)) {
            String path = "entreprises." + nomEnt + ".";
            try {
                // FIX BASSE #31: Valider le nom de l'entreprise au chargement
                if (!plugin.getNameValidator().isValidLoadedName(nomEnt, "entreprise")) {
                    plugin.getLogger().warning("⚠ Entreprise avec nom invalide ignorée: " + nomEnt);
                    continue;
                }

                String ville = currentConfig.getString(path + "ville");
                String type = currentConfig.getString(path + "type");
                String gerantNom = currentConfig.getString(path + "gerantNom");
                String gerantUUIDStr = currentConfig.getString(path + "gerantUUID");
                double solde = currentConfig.getDouble(path + "solde", 0.0);
                String siret = currentConfig.getString(path + "siret", generateSiret());
                double caTotal = currentConfig.getDouble(path + "chiffreAffairesTotal", 0.0);
                double caProductifHoraire = currentConfig.getDouble(path + "activiteProductiveHoraireValeur", 0.0);
                double caMagasinHoraire = currentConfig.getDouble(path + "activiteMagasinHoraireValeur", 0.0);
                int niveauMaxEmployes = currentConfig.getInt(path + "niveauMaxEmployes", 0);
                int niveauMaxSolde = currentConfig.getInt(path + "niveauMaxSolde", 0);

                // FIX MOYENNE: Validation robuste des données lors de la désérialisation
                if (gerantNom == null || gerantUUIDStr == null || type == null || ville == null) {
                    plugin.getLogger().severe("Données essentielles manquantes pour l'entreprise '" + nomEnt + "'. Elle ne sera pas chargée.");
                    continue;
                }

                // Valider le solde
                if (solde < 0 || Double.isNaN(solde) || Double.isInfinite(solde)) {
                    plugin.getLogger().warning("Solde invalide pour l'entreprise '" + nomEnt + "': " + solde + ". Réinitialisé à 0.");
                    solde = 0.0;
                }

                // Valider le CA total
                if (caTotal < 0 || Double.isNaN(caTotal) || Double.isInfinite(caTotal)) {
                    plugin.getLogger().warning("CA total invalide pour l'entreprise '" + nomEnt + "': " + caTotal + ". Réinitialisé à 0.");
                    caTotal = 0.0;
                }

                // Valider les CA horaires
                if (caProductifHoraire < 0 || Double.isNaN(caProductifHoraire) || Double.isInfinite(caProductifHoraire)) {
                    plugin.getLogger().warning("CA productif horaire invalide pour l'entreprise '" + nomEnt + "': " + caProductifHoraire + ". Réinitialisé à 0.");
                    caProductifHoraire = 0.0;
                }
                if (caMagasinHoraire < 0 || Double.isNaN(caMagasinHoraire) || Double.isInfinite(caMagasinHoraire)) {
                    plugin.getLogger().warning("CA magasin horaire invalide pour l'entreprise '" + nomEnt + "': " + caMagasinHoraire + ". Réinitialisé à 0.");
                    caMagasinHoraire = 0.0;
                }

                // Valider les niveaux
                if (niveauMaxEmployes < 0) {
                    plugin.getLogger().warning("Niveau max employés invalide pour l'entreprise '" + nomEnt + "': " + niveauMaxEmployes + ". Réinitialisé à 0.");
                    niveauMaxEmployes = 0;
                }
                if (niveauMaxSolde < 0) {
                    plugin.getLogger().warning("Niveau max solde invalide pour l'entreprise '" + nomEnt + "': " + niveauMaxSolde + ". Réinitialisé à 0.");
                    niveauMaxSolde = 0;
                }

                // Valider le SIRET (doit être hexadécimal de longueur configurée)
                int expectedLength = Math.min(plugin.getConfig().getInt("siret.longueur", 14), 32);
                if (siret != null && !siret.matches("[0-9a-f]{" + expectedLength + "}")) {
                    plugin.getLogger().warning("SIRET invalide pour l'entreprise '" + nomEnt + "': " + siret +
                        " (attendu: " + expectedLength + " caractères hexadécimaux). Un nouveau SIRET sera généré.");
                    siret = generateSiret();
                }

                // Valider l'UUID du gérant
                try {
                    UUID.fromString(gerantUUIDStr);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().severe("UUID gérant invalide pour l'entreprise '" + nomEnt + "': " + gerantUUIDStr + ". Elle ne sera pas chargée.");
                    continue;
                }

                Set<String> employesSet = new HashSet<>();
                Map<String, Double> primesMap = new HashMap<>();
                ConfigurationSection employesSect = currentConfig.getConfigurationSection(path + "employes");
                if (employesSect != null) {
                    for (String uuidStr : employesSect.getKeys(false)) {
                        try {
                            UUID empUuid = UUID.fromString(uuidStr);
                            OfflinePlayer p = Bukkit.getOfflinePlayer(empUuid);
                            if (p != null && p.getName() != null && (p.hasPlayedBefore() || p.isOnline())) {
                                employesSet.add(p.getName());
                                // FIX MOYENNE: Valider les primes lors de la désérialisation
                                double prime = employesSect.getDouble(uuidStr + ".prime", 0.0);
                                if (prime < 0 || Double.isNaN(prime) || Double.isInfinite(prime)) {
                                    plugin.getLogger().warning("Prime invalide pour l'employé " + uuidStr + " dans l'entreprise " + nomEnt + ": " + prime + ". Réinitialisée à 0.");
                                    prime = 0.0;
                                }
                                primesMap.put(uuidStr, prime);
                            } else {
                                plugin.getLogger().warning("Employé avec UUID " + uuidStr + " pour l'entreprise " + nomEnt + " n'a pas pu être validé (nom null ou jamais joué).");
                            }
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("UUID invalide pour un employé dans l'entreprise " + nomEnt + ": " + uuidStr);
                        }
                    }
                }

                List<Transaction> transactionLogList = new ArrayList<>();
                if (currentConfig.isList(path + "transactionLog")) {
                    List<Map<?, ?>> rawTxList = currentConfig.getMapList(path + "transactionLog");
                    for (Map<?, ?> rawTxMap : rawTxList) {
                        if (rawTxMap != null) {
                            Map<String, Object> safeTxMap = new HashMap<>();
                            rawTxMap.forEach((key, value) -> {
                                if (key instanceof String) safeTxMap.put((String)key, value);
                            });
                            Transaction tx = Transaction.deserialize(safeTxMap);
                            if (tx != null) transactionLogList.add(tx);
                        }
                    }
                }

                Map<UUID, EmployeeActivityRecord> activitiesMap = new HashMap<>();
                ConfigurationSection activityRecordsSect = currentConfig.getConfigurationSection(path + "employeeActivityRecords");
                if (activityRecordsSect != null) {
                    for (String uuidStr : activityRecordsSect.getKeys(false)) {
                        try {
                            UUID empUuid = UUID.fromString(uuidStr);
                            ConfigurationSection recordSect = activityRecordsSect.getConfigurationSection(uuidStr);
                            if (recordSect != null) {
                                Map<String, Object> recordData = new HashMap<>();
                                for(String key : recordSect.getKeys(true)) {
                                    recordData.put(key, recordSect.get(key));
                                }
                                EmployeeActivityRecord rec = EmployeeActivityRecord.deserialize(recordData);
                                if (rec != null) activitiesMap.put(empUuid, rec);
                            }
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.WARNING, "Erreur lors du chargement du record d'activité pour l'UUID " + uuidStr + " de l'entreprise " + nomEnt + ".", e);
                        }
                    }
                }

                List<ProductionRecord> globalProductionLogList = new ArrayList<>();
                if (currentConfig.isList(path + "productionLog")) {
                    List<Map<?, ?>> rawProdList = currentConfig.getMapList(path + "productionLog");
                    for (Map<?, ?> rawProdMap : rawProdList) {
                        if (rawProdMap != null) {
                            Map<String, Object> safeProdMap = new HashMap<>();
                            rawProdMap.forEach((key, value) -> {
                                if (key instanceof String) safeProdMap.put((String)key, value);
                            });
                            ProductionRecord prodRec = ProductionRecord.deserialize(safeProdMap);
                            if (prodRec != null) globalProductionLogList.add(prodRec);
                        }
                    }
                }

                Entreprise ent = new Entreprise(nomEnt, ville, type, gerantNom, gerantUUIDStr, employesSet, solde, siret);
                ent.setChiffreAffairesTotal(caTotal);
                ent.setPrimes(primesMap);
                ent.setTransactionLog(transactionLogList);
                ent.setEmployeeActivityRecords(activitiesMap);
                ent.setGlobalProductionLog(globalProductionLogList);
                ent.setNiveauMaxEmployes(niveauMaxEmployes);
                ent.setNiveauMaxSolde(niveauMaxSolde);

                // FIX MOYENNE: Valider et nettoyer la cohérence des données après chargement
                validateAndCleanEntrepriseData(ent);

                entreprises.put(nomEnt, ent);
                // FIX PERFORMANCE HAUTE: Maintenir l'index SIRET lors du chargement
                if (ent.getSiret() != null) {
                    entreprisesBySiret.put(ent.getSiret(), ent);
                }
                activiteProductiveHoraireValeur.put(nomEnt, caProductifHoraire);
                activiteMagasinHoraireValeur.put(nomEnt, caMagasinHoraire);
                entreprisesChargees++;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Erreur majeure lors du chargement de l'entreprise '" + nomEnt + "'.", e);
            }
        }
        plugin.getLogger().info(entreprisesChargees + " entreprises chargées depuis entreprise.yml.");

        // FIX PERFORMANCE HAUTE: Charger le cache des restrictions
        loadRestrictionsCache();
    }

    /**
     * FIX MOYENNE: Valide et nettoie les données d'une entreprise après chargement
     * - Valide les tailles de collections (détection corruption/exploits)
     * - Valide les ranges de valeurs numériques
     * - Vérifie cohérence des activity records et primes
     */
    private void validateAndCleanEntrepriseData(Entreprise entreprise) {
        if (entreprise == null) return;

        int issuesFixed = 0;
        String nomEnt = entreprise.getNom();

        // 1. FIX MOYENNE: Valider les tailles de collections (détection corruption/exploits)
        final int MAX_REASONABLE_EMPLOYEES = 100;
        final int MAX_REASONABLE_TRANSACTIONS = 10000;
        final int MAX_REASONABLE_PRODUCTION_RECORDS = 50000;
        final int MAX_REASONABLE_ACTIVITY_RECORDS = 150;

        if (entreprise.getEmployes().size() > MAX_REASONABLE_EMPLOYEES) {
            plugin.getLogger().severe("[INTEGRITY] Entreprise '" + nomEnt + "': Nombre d'employés anormal (" +
                entreprise.getEmployes().size() + "), possible corruption!");
        }

        // Valider et trimmer activity records si trop volumineux
        Map<UUID, EmployeeActivityRecord> activityRecords = entreprise.getEmployeeActivityRecords();
        if (activityRecords != null && activityRecords.size() > MAX_REASONABLE_ACTIVITY_RECORDS) {
            plugin.getLogger().warning("[INTEGRITY] Entreprise '" + nomEnt + "': Trop d'activity records (" +
                activityRecords.size() + "), nettoyage des plus anciens");
            // Garder seulement les plus récents basés sur lastActivityTime
            List<Map.Entry<UUID, EmployeeActivityRecord>> sorted = new ArrayList<>(activityRecords.entrySet());
            sorted.sort((a, b) -> {
                LocalDateTime timeA = a.getValue().lastActivityTime != null ? a.getValue().lastActivityTime : LocalDateTime.MIN;
                LocalDateTime timeB = b.getValue().lastActivityTime != null ? b.getValue().lastActivityTime : LocalDateTime.MIN;
                return timeB.compareTo(timeA);
            });
            activityRecords.clear();
            sorted.stream().limit(MAX_REASONABLE_ACTIVITY_RECORDS).forEach(e -> activityRecords.put(e.getKey(), e.getValue()));
            issuesFixed++;
        }

        List<Transaction> transactions = entreprise.getTransactionLog();
        if (transactions != null && transactions.size() > MAX_REASONABLE_TRANSACTIONS) {
            plugin.getLogger().warning("[INTEGRITY] Entreprise '" + nomEnt + "': Trop de transactions (" +
                transactions.size() + "), trimming à " + MAX_REASONABLE_TRANSACTIONS);
            // Garder seulement les plus récentes
            List<Transaction> trimmed = transactions.stream()
                .sorted((a, b) -> b.timestamp.compareTo(a.timestamp))
                .limit(MAX_REASONABLE_TRANSACTIONS)
                .collect(java.util.stream.Collectors.toList());
            entreprise.setTransactionLog(trimmed);
            issuesFixed++;
        }

        List<ProductionRecord> production = entreprise.getGlobalProductionLog();
        if (production != null && production.size() > MAX_REASONABLE_PRODUCTION_RECORDS) {
            plugin.getLogger().warning("[INTEGRITY] Entreprise '" + nomEnt + "': Trop de records de production (" +
                production.size() + "), trimming à " + MAX_REASONABLE_PRODUCTION_RECORDS);
            // Garder seulement les plus récents
            List<ProductionRecord> trimmed = production.stream()
                .sorted((a, b) -> b.timestamp.compareTo(a.timestamp))
                .limit(MAX_REASONABLE_PRODUCTION_RECORDS)
                .collect(java.util.stream.Collectors.toList());
            entreprise.setGlobalProductionLog(trimmed);
            issuesFixed++;
        }

        // 2. FIX MOYENNE: Valider les ranges de valeurs numériques
        final double MAX_REASONABLE_SOLDE = 100_000_000.0; // 100 millions
        final double MAX_REASONABLE_CA = 1_000_000_000.0; // 1 milliard
        final double MAX_REASONABLE_PRIME = 10_000.0; // 10k par heure

        if (entreprise.getSolde() < 0) {
            plugin.getLogger().severe("[INTEGRITY] Entreprise '" + nomEnt + "': Solde négatif (" +
                entreprise.getSolde() + "), correction à 0");
            entreprise.setSolde(0);
            issuesFixed++;
        } else if (entreprise.getSolde() > MAX_REASONABLE_SOLDE) {
            plugin.getLogger().warning("[INTEGRITY] Entreprise '" + nomEnt + "': Solde anormalement élevé (" +
                String.format("%.2f€", entreprise.getSolde()) + "), possible exploit");
        }

        if (entreprise.getChiffreAffairesTotal() < 0) {
            plugin.getLogger().severe("[INTEGRITY] Entreprise '" + nomEnt + "': CA négatif (" +
                entreprise.getChiffreAffairesTotal() + "), correction à 0");
            entreprise.setChiffreAffairesTotal(0);
            issuesFixed++;
        } else if (entreprise.getChiffreAffairesTotal() > MAX_REASONABLE_CA) {
            plugin.getLogger().warning("[INTEGRITY] Entreprise '" + nomEnt + "': CA anormalement élevé (" +
                String.format("%.2f€", entreprise.getChiffreAffairesTotal()) + ")");
        }

        // 3. FIX MOYENNE: Valider les primes (utilise String comme clé pour UUID.toString())
        Map<String, Double> primes = entreprise.getPrimes();
        if (primes != null && !primes.isEmpty()) {
            List<String> invalidPrimes = new ArrayList<>();
            for (Map.Entry<String, Double> entry : primes.entrySet()) {
                double prime = entry.getValue();
                if (prime < 0 || prime > MAX_REASONABLE_PRIME || Double.isNaN(prime) || Double.isInfinite(prime)) {
                    plugin.getLogger().warning("[INTEGRITY] Entreprise '" + nomEnt + "': Prime invalide pour employé " +
                        entry.getKey() + " (" + prime + "€/h), suppression");
                    invalidPrimes.add(entry.getKey());
                    issuesFixed++;
                }
            }
            invalidPrimes.forEach(primes::remove);
        }

        if (issuesFixed > 0) {
            plugin.getLogger().info("[COHERENCE] Entreprise '" + nomEnt + "': " + issuesFixed + " problème(s) corrigé(s)");
        }
    }

    /**
     * FIX PERFORMANCE HAUTE: Précharge les restrictions d'actions en cache
     * Évite de parcourir tous les types d'entreprise (O(n)) à chaque action du joueur
     * FIX MOYENNE: Le cache expire après 5 minutes pour éviter les données obsolètes
     */
    private void loadRestrictionsCache() {
        restrictionsCache.clear();
        restrictionsCacheLoadTime = System.currentTimeMillis();

        ConfigurationSection typesEntreprisesConfig = plugin.getConfig().getConfigurationSection("types-entreprise");
        if (typesEntreprisesConfig == null) {
            plugin.getLogger().warning("Section 'types-entreprise' introuvable, aucune restriction chargée");
            return;
        }

        int restrictionsChargees = 0;
        for (String typeEntSpecialise : typesEntreprisesConfig.getKeys(false)) {
            ConfigurationSection actionsSection = plugin.getConfig().getConfigurationSection(
                "types-entreprise." + typeEntSpecialise + ".action_restrictions");

            if (actionsSection == null) continue;

            int limiteNonMembre = plugin.getConfig().getInt(
                "types-entreprise." + typeEntSpecialise + ".limite-non-membre-par-heure", -1);

            List<String> messagesErreur = plugin.getConfig().getStringList(
                "types-entreprise." + typeEntSpecialise + ".message-erreur-restriction");
            if (messagesErreur.isEmpty()) {
                messagesErreur = Collections.singletonList("&cAction restreinte. Limite horaire : %limite%");
            }

            // Parcourir toutes les actions restreintes pour ce type
            for (String actionType : actionsSection.getKeys(false)) {
                ConfigurationSection materialsSection = plugin.getConfig().getConfigurationSection(
                    "types-entreprise." + typeEntSpecialise + ".action_restrictions." + actionType);

                if (materialsSection == null) continue;

                for (String material : materialsSection.getKeys(false)) {
                    String cacheKey = actionType.toUpperCase() + ":" + material.toUpperCase();

                    restrictionsCache.computeIfAbsent(cacheKey, k -> new ArrayList<>())
                        .add(new RestrictionInfo(typeEntSpecialise, limiteNonMembre, messagesErreur));

                    restrictionsChargees++;
                }
            }
        }

        plugin.getLogger().info("Cache de restrictions chargé: " + restrictionsChargees + " règles indexées");
    }

    public void saveEntreprises() {
        // FIX BASSE #28: Mesurer performance de la sauvegarde
        com.gravityyfh.roleplaycity.util.PerformanceMetrics.Timer timer = plugin.getPerformanceMetrics().startSave("entreprises");

        if (plugin == null || entrepriseFile == null) {
            System.err.println("ERREUR CRITIQUE: Plugin ou fichier entreprise est null lors de la tentative de sauvegarde !");
            return;
        }
        FileConfiguration tempConfig = new YamlConfiguration();
        ConfigurationSection entreprisesSection = tempConfig.createSection("entreprises");

        for (Map.Entry<String, Entreprise> entry : entreprises.entrySet()) {
            String nomEnt = entry.getKey();
            Entreprise ent = entry.getValue();
            String path = nomEnt + ".";

            entreprisesSection.set(path + "ville", ent.getVille());
            entreprisesSection.set(path + "type", ent.getType());
            entreprisesSection.set(path + "gerantNom", ent.getGerant());
            entreprisesSection.set(path + "gerantUUID", ent.getGerantUUID());
            entreprisesSection.set(path + "solde", ent.getSolde());
            entreprisesSection.set(path + "siret", ent.getSiret());
            entreprisesSection.set(path + "chiffreAffairesTotal", ent.getChiffreAffairesTotal());
            entreprisesSection.set(path + "activiteProductiveHoraireValeur", activiteProductiveHoraireValeur.getOrDefault(nomEnt, 0.0));
            entreprisesSection.set(path + "activiteMagasinHoraireValeur", activiteMagasinHoraireValeur.getOrDefault(nomEnt, 0.0));
            entreprisesSection.set(path + "niveauMaxEmployes", ent.getNiveauMaxEmployes());
            entreprisesSection.set(path + "niveauMaxSolde", ent.getNiveauMaxSolde());

            ConfigurationSection employesSect = entreprisesSection.createSection(path + "employes");
            ent.getPrimes().forEach((uuidStr, primeVal) -> employesSect.set(uuidStr + ".prime", primeVal));

            List<Map<String,Object>> serializedTransactions = ent.getTransactionLog().stream()
                    .map(Transaction::serialize)
                    .collect(Collectors.toList());
            entreprisesSection.set(path + "transactionLog", serializedTransactions);

            ConfigurationSection activityRecordsSect = entreprisesSection.createSection(path + "employeeActivityRecords");
            ent.getEmployeeActivityRecords().forEach((uuid, record) -> {
                Map<String, Object> serializedRecord = record.serialize();
                if (serializedRecord != null) {
                    activityRecordsSect.set(uuid.toString(), serializedRecord);
                } else {
                    plugin.getLogger().warning("Record d'activité sérialisé est null pour l'employé " + uuid + " de l'entreprise " + nomEnt);
                }
            });

            List<Map<String,Object>> serializedProductionLog = ent.getGlobalProductionLog().stream()
                    .map(ProductionRecord::serialize)
                    .collect(Collectors.toList());
            entreprisesSection.set(path + "productionLog", serializedProductionLog);
        }

        // FIX CRITIQUE: Sauvegarde atomique avec backup et validation
        File tempFile = new File(entrepriseFile.getParentFile(), "entreprise.yml.tmp");
        File backupFile = new File(entrepriseFile.getParentFile(), "entreprise.yml.backup");

        try {
            // Étape 1: Écrire dans un fichier temporaire
            tempConfig.save(tempFile);

            // Étape 2: Valider le fichier temporaire
            FileConfiguration testLoad = YamlConfiguration.loadConfiguration(tempFile);
            ConfigurationSection testSection = testLoad.getConfigurationSection("entreprises");
            if ((testSection == null || testSection.getKeys(false).isEmpty()) && !entreprises.isEmpty()) {
                throw new IOException("Validation échouée: le fichier temporaire est vide alors que des entreprises existent");
            }

            // Étape 3: Créer un backup de l'ancien fichier (si existe)
            if (entrepriseFile.exists()) {
                Files.copy(entrepriseFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            // Étape 4: Renommage atomique (remplace l'ancien fichier)
            Files.move(tempFile.toPath(), entrepriseFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            plugin.getLogger().info("Entreprises sauvegardées dans entreprise.yml (atomic write + backup)");
            // FIX BASSE #28: Fin mesure performance (succès)
            timer.stop();

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur critique lors de la sauvegarde de entreprise.yml", e);

            // FIX MOYENNE: Alerter les admins en ligne en cas d'échec de sauvegarde
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player admin : Bukkit.getOnlinePlayers()) {
                    if (admin.hasPermission("entreprisemanager.admin.alerts")) {
                        admin.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "[ALERTE CRITIQUE]");
                        admin.sendMessage(ChatColor.RED + "Échec de la sauvegarde de entreprise.yml !");
                        admin.sendMessage(ChatColor.YELLOW + "Erreur: " + e.getMessage());
                        admin.sendMessage(ChatColor.GOLD + "Vérifiez les logs du serveur pour plus de détails.");
                    }
                }
            });

            // Tentative de restauration depuis backup si disponible
            if (backupFile.exists() && !entrepriseFile.exists()) {
                try {
                    Files.copy(backupFile.toPath(), entrepriseFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().warning("Fichier entreprise.yml restauré depuis le backup");
                } catch (IOException restoreEx) {
                    plugin.getLogger().log(Level.SEVERE, "Impossible de restaurer depuis le backup", restoreEx);
                }
            }
        }
        savePlayerHistory();
    }
    // --- Fin Chargement / Sauvegarde ---

    // --- Reload (AVEC HISTORIQUE) ---
    public void reloadPluginData() { plugin.reloadConfig(); loadEntreprises(); loadPlayerHistory(); planifierTachesHoraires(); planifierVerificationActiviteEmployes(); planifierVerificationInactiviteEmployes(); plugin.getLogger().info("[RoleplayCity] Données, historique et configuration rechargés."); }
    // --- Fin Reload ---

    // --- Cleanup (FIX MOYENNE) ---
    /**
     * FIX MOYENNE: Nettoyer proprement toutes les tasks asynchrones lors du shutdown
     * Prévient les erreurs et fuites de ressources
     */
    public void cleanup() {
        plugin.getLogger().info("[EntrepriseManager] Nettoyage des tasks en cours...");

        if (hourlyTask != null && !hourlyTask.isCancelled()) {
            hourlyTask.cancel();
            plugin.getLogger().fine("[EntrepriseManager] Task horaire annulée");
        }

        if (activityCheckTask != null && !activityCheckTask.isCancelled()) {
            activityCheckTask.cancel();
            plugin.getLogger().fine("[EntrepriseManager] Task de vérification d'activité annulée");
        }

        if (inactivityKickTask != null && !inactivityKickTask.isCancelled()) {
            inactivityKickTask.cancel();
            plugin.getLogger().fine("[EntrepriseManager] Task de kick inactivité annulée");
        }

        plugin.getLogger().info("[EntrepriseManager] Nettoyage terminé");
    }
    // --- Fin Cleanup ---

    // --- Messages Différés ---
    public void ajouterMessageEmployeDifferre(String joueurUUID, String message, String entrepriseNom, double montantPrime) { File messagesFile = new File(plugin.getDataFolder(), "messagesEmployes.yml"); FileConfiguration messagesConfig = YamlConfiguration.loadConfiguration(messagesFile); String listPath = "messages." + joueurUUID + "." + entrepriseNom + ".list"; List<String> messagesActuels = messagesConfig.getStringList(listPath); messagesActuels.add(ChatColor.stripColor(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM HH:mm")) + ": " + message)); messagesConfig.set(listPath, messagesActuels); if (montantPrime > 0) { String totalPrimePath = "messages." + joueurUUID + "." + entrepriseNom + ".totalPrime"; messagesConfig.set(totalPrimePath, messagesConfig.getDouble(totalPrimePath, 0.0) + montantPrime); } try { messagesConfig.save(messagesFile); } catch (IOException e) { plugin.getLogger().severe("Erreur sauvegarde message différé employé " + joueurUUID + ": " + e.getMessage()); } }
    public void envoyerPrimesDifferreesEmployes(Player player) { String playerUUID = player.getUniqueId().toString(); File messagesFile = new File(plugin.getDataFolder(), "messagesEmployes.yml"); if (!messagesFile.exists()) return; FileConfiguration msgsCfg = YamlConfiguration.loadConfiguration(messagesFile); String basePath = "messages." + playerUUID; if (!msgsCfg.contains(basePath)) return; ConfigurationSection entreprisesSect = msgsCfg.getConfigurationSection(basePath); boolean receivedMessage = false; if (entreprisesSect != null) { for (String nomEnt : entreprisesSect.getKeys(false)) { if (entreprisesSect.isConfigurationSection(nomEnt)) { List<String> messages = entreprisesSect.getStringList(nomEnt + ".list"); double totalPrime = entreprisesSect.getDouble(nomEnt + ".totalPrime", 0.0); if (!messages.isEmpty()) { player.sendMessage(ChatColor.GOLD + "--- Primes/Messages de '" + nomEnt + "' (hors-ligne) ---"); messages.forEach(msg -> player.sendMessage(ChatColor.AQUA + "- " + msg)); if (totalPrime > 0) player.sendMessage(ChatColor.GREEN + "Total primes période: " + String.format("%,.2f€", totalPrime)); player.sendMessage(ChatColor.GOLD + "--------------------------------------------------------"); receivedMessage = true; } } } } if (receivedMessage) { msgsCfg.set(basePath, null); try { msgsCfg.save(messagesFile); } catch (IOException e) { plugin.getLogger().severe("Erreur suppression messages différés employé " + playerUUID + ": " + e.getMessage()); } } }
    public void ajouterMessageGerantDifferre(String gerantUUID, String message, String entrepriseNom, double montantConcerne) { File messagesFile = new File(plugin.getDataFolder(), "messagesGerants.yml"); FileConfiguration messagesConfig = YamlConfiguration.loadConfiguration(messagesFile); String listPath = "messages." + gerantUUID + "." + entrepriseNom + ".list"; List<String> messagesActuels = messagesConfig.getStringList(listPath); messagesActuels.add(ChatColor.stripColor(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM HH:mm")) + ": " + message)); messagesConfig.set(listPath, messagesActuels); try { messagesConfig.save(messagesFile); } catch (IOException e) { plugin.getLogger().severe("Erreur sauvegarde message différé gérant " + gerantUUID + ": " + e.getMessage()); } }
    public void envoyerPrimesDifferreesGerants(Player playerGerant) { String gerantUUID = playerGerant.getUniqueId().toString(); File messagesFile = new File(plugin.getDataFolder(), "messagesGerants.yml"); if (!messagesFile.exists()) return; FileConfiguration msgsCfg = YamlConfiguration.loadConfiguration(messagesFile); String basePath = "messages." + gerantUUID; if (!msgsCfg.contains(basePath)) return; ConfigurationSection entreprisesSect = msgsCfg.getConfigurationSection(basePath); boolean receivedMessage = false; if (entreprisesSect != null) { for (String nomEnt : entreprisesSect.getKeys(false)) { if (entreprisesSect.isConfigurationSection(nomEnt)) { List<String> messages = entreprisesSect.getStringList(nomEnt + ".list"); if (!messages.isEmpty()) { playerGerant.sendMessage(ChatColor.BLUE + "--- Notifications Gérance '" + nomEnt + "' (hors-ligne) ---"); messages.forEach(msg -> playerGerant.sendMessage(ChatColor.AQUA + "- " + msg)); playerGerant.sendMessage(ChatColor.BLUE + "----------------------------------------------------------------"); receivedMessage = true; } } } } if (receivedMessage) { msgsCfg.set(basePath, null); try { msgsCfg.save(messagesFile); } catch (IOException e) { plugin.getLogger().severe("Erreur suppression messages différés gérant " + gerantUUID + ": " + e.getMessage()); } } }
    // --- Fin Messages Différés ---

    // --- Getters et Utilitaires ---
    public double getActiviteHoraireValeurPour(String nomEntreprise) {double revenusProductifs = activiteProductiveHoraireValeur.getOrDefault(nomEntreprise, 0.0);double revenusMagasins = activiteMagasinHoraireValeur.getOrDefault(nomEntreprise, 0.0);return revenusProductifs + revenusMagasins;
    }    public List<Transaction> getTransactionsPourEntreprise(String nomEntreprise, int limit) { Entreprise entreprise = getEntreprise(nomEntreprise); if (entreprise == null) return Collections.emptyList(); List<Transaction> log = new ArrayList<>(entreprise.getTransactionLog()); Collections.reverse(log); return (limit <= 0 || limit >= log.size()) ? log : log.subList(0, limit); }
    public Collection<String> getPlayersInMayorTown(Player mayor) {
        // Utilise notre système de ville au lieu de Towny
        if (!estMaire(mayor)) {
            return Collections.emptyList();
        }

        com.gravityyfh.roleplaycity.town.data.Town town = plugin.getTownManager().getPlayerTownObject(mayor.getUniqueId());
        if (town == null) {
            return Collections.emptyList();
        }

        return town.getMembers().values().stream()
                .map(com.gravityyfh.roleplaycity.town.data.TownMember::getPlayerName)
                .collect(Collectors.toList());
    }
    public Collection<String> getAllTownsNames() {
        // Utilise notre système de ville au lieu de Towny
        return plugin.getTownManager().getTownNames();
    }
    public boolean peutCreerEntreprise(Player player) { int max = plugin.getConfig().getInt("finance.max-entreprises-par-gerant", 1); return getEntreprisesGereesPar(player.getName()).size() < max; }
    public Collection<String> getNearbyPlayers(Player centerPlayer, int maxDistance) { return Bukkit.getOnlinePlayers().stream().filter(other -> !other.equals(centerPlayer) && other.getWorld().equals(centerPlayer.getWorld()) && other.getLocation().distanceSquared(centerPlayer.getLocation()) <= (long)maxDistance * maxDistance).map(Player::getName).collect(Collectors.toList()); }
    public void listEntreprises(Player player, String ville) { List<Entreprise> entreprisesDansVille = getEntreprisesByVille(ville); if (entreprisesDansVille.isEmpty()) { player.sendMessage(ChatColor.YELLOW + "Aucune entreprise à " + ville + "."); return; } TextComponent msg = new TextComponent(ChatColor.GOLD + "=== Entreprises à " + ChatColor.AQUA + ville + ChatColor.GOLD + " ===\n"); for (Entreprise e : entreprisesDansVille) { TextComponent ligne = new TextComponent(ChatColor.AQUA + e.getNom() + ChatColor.GRAY + " (Type: " + e.getType() + ", Gérant: " + e.getGerant() + ")"); ligne.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/entreprise info " + e.getNom())); ligne.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Infos '" + e.getNom() + "'").create())); msg.addExtra(ligne); msg.addExtra("\n"); } player.spigot().sendMessage(msg); }
    public Map<Material, Integer> getEmployeeProductionStatsForPeriod(String nomEntreprise, UUID employeeUUID, LocalDateTime start, LocalDateTime end, DetailedActionType actionTypeFilter) { Entreprise ent = getEntreprise(nomEntreprise); if (ent == null) return Collections.emptyMap(); Set<Material> relevant = ent.getTrackedProductionMaterials(); return ent.getEmployeeProductionStatsForPeriod(employeeUUID, start, end, actionTypeFilter, relevant); }
    public Map<Material, Integer> getCompanyProductionStatsForPeriod(String nomEntreprise, LocalDateTime start, LocalDateTime end, DetailedActionType actionTypeFilter) { Entreprise ent = getEntreprise(nomEntreprise); if (ent == null) return Collections.emptyMap(); Set<Material> relevant = ent.getTrackedProductionMaterials(); return ent.getAggregatedProductionStatsForPeriod(start, end, actionTypeFilter, relevant); }
    public boolean canPlayerBreakBlock(Player player, Location location, Material material) {
        // Utilise notre système de Plot au lieu de Towny
        com.gravityyfh.roleplaycity.town.manager.ClaimManager claimManager = plugin.getClaimManager();
        if (claimManager == null) {
            return true; // Si pas de système de claim, autoriser
        }

        com.gravityyfh.roleplaycity.town.data.Plot plot = claimManager.getPlotAt(location);
        if (plot == null) {
            return true; // Pas de plot, autoriser
        }

        // Récupérer la ville associée au plot
        com.gravityyfh.roleplaycity.town.data.Town town = plugin.getTownManager().getTown(plot.getTownName());
        if (town == null) {
            return true;
        }

        // Vérifier les permissions de BUILD (qui incluent break)
        return plot.canBuild(player.getUniqueId(), town);
    }
    public boolean canPlayerPlaceBlock(Player player, Location location, Material material) {
        // Utilise notre système de Plot au lieu de Towny
        com.gravityyfh.roleplaycity.town.manager.ClaimManager claimManager = plugin.getClaimManager();
        if (claimManager == null) {
            return true; // Si pas de système de claim, autoriser
        }

        com.gravityyfh.roleplaycity.town.data.Plot plot = claimManager.getPlotAt(location);
        if (plot == null) {
            return true; // Pas de plot, autoriser
        }

        // Récupérer la ville associée au plot
        com.gravityyfh.roleplaycity.town.data.Town town = plugin.getTownManager().getTown(plot.getTownName());
        if (town == null) {
            return true;
        }

        // Vérifier les permissions de BUILD (qui incluent place)
        return plot.canBuild(player.getUniqueId(), town);
    }
    // --- Fin Getters / Utilitaires ---

    // --- Méthodes spécifiques à l'HISTORIQUE ---
    private void loadPlayerHistory() {
        playerHistoryCache.clear();
        if (!playerHistoryFile.exists()) {
            plugin.getLogger().info(playerHistoryFile.getName() + " non trouvé, création.");
            try { playerHistoryFile.getParentFile().mkdirs(); playerHistoryFile.createNewFile(); }
            catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Impossible de créer " + playerHistoryFile.getName(), e); return; }
        }
        FileConfiguration currentHistoryConfig = YamlConfiguration.loadConfiguration(playerHistoryFile);
        ConfigurationSection historySection = currentHistoryConfig.getConfigurationSection("player-history");
        if (historySection == null) { plugin.getLogger().info("Aucune section 'player-history'."); return; }

        int count = 0;
        for (String uuidStr : historySection.getKeys(false)) {
            try {
                UUID playerUUID = UUID.fromString(uuidStr); List<?> rawList = historySection.getList(uuidStr);
                if (rawList != null) {
                    List<PastExperience> experiences = Collections.synchronizedList(new ArrayList<>());
                    for (Object obj : rawList) {
                        if (obj instanceof Map) { @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>) obj; PastExperience exp = PastExperience.deserialize(map); if (exp != null) { experiences.add(exp); count++; } }
                        else if (obj != null) { plugin.getLogger().warning("Objet inattendu historique " + uuidStr + ": " + obj.getClass().getName()); }
                    }
                    experiences.sort(Comparator.comparing(PastExperience::getDateSortie, Comparator.nullsLast(Comparator.reverseOrder())));
                    playerHistoryCache.put(playerUUID, experiences);
                }
            } catch (IllegalArgumentException e) { plugin.getLogger().warning("UUID invalide historique: " + uuidStr); }
            catch (Exception e) { plugin.getLogger().log(Level.SEVERE, "Erreur chargement historique UUID " + uuidStr + ".", e); }
        }
        plugin.getLogger().info(playerHistoryCache.size() + " joueurs avec historique chargé (" + count + " expériences).");
    }
    private void savePlayerHistory() {
        if (playerHistoryFile == null || plugin == null) { System.err.println("ERREUR SAVE HISTORIQUE: Fichier/Plugin null !"); return; }
        FileConfiguration tempHistoryConfig = new YamlConfiguration();
        ConfigurationSection historySection = tempHistoryConfig.createSection("player-history");
        playerHistoryCache.forEach((uuid, experiences) -> { if (experiences != null) { synchronized (experiences) { List<Map<String, Object>> serialized = experiences.stream().map(PastExperience::serialize).collect(Collectors.toList()); historySection.set(uuid.toString(), serialized); } } });
        try { tempHistoryConfig.save(playerHistoryFile); }
        catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Impossible de sauvegarder " + playerHistoryFile.getName(), e); }
    }
    public List<PastExperience> getPlayerHistory(UUID playerUUID) {
        List<PastExperience> history = playerHistoryCache.get(playerUUID);
        if (history != null) { synchronized (history) { return new ArrayList<>(history); } }
        return Collections.emptyList();
    }
    private void recordPlayerHistoryEntry(UUID playerUUID, Entreprise entrepriseQuittee, String role, LocalDateTime dateSortie) {
        if (playerUUID == null || entrepriseQuittee == null || role == null || dateSortie == null) { plugin.getLogger().warning("Données null enregistrement historique."); return; }
        EmployeeActivityRecord record = entrepriseQuittee.getEmployeeActivityRecord(playerUUID);
        LocalDateTime dateEntree = (record != null) ? record.joinDate : null;
        double caGenere = (record != null) ? record.totalValueGenerated : 0.0;
        PastExperience newEntry = new PastExperience(entrepriseQuittee.getNom(), entrepriseQuittee.getType(), role, dateEntree, dateSortie, caGenere);
        List<PastExperience> history = playerHistoryCache.computeIfAbsent(playerUUID, k -> Collections.synchronizedList(new ArrayList<>()));
        synchronized (history) {
            if (!history.contains(newEntry)) {
                history.add(newEntry);
                history.sort(Comparator.comparing(PastExperience::getDateSortie, Comparator.nullsLast(Comparator.reverseOrder())));
                plugin.getLogger().fine("Historique enregistré pour " + playerUUID + ": " + newEntry);
                int maxHistorySize = plugin.getConfig().getInt("cv.max-history-entries", 20);
                if (history.size() > maxHistorySize) { history.subList(maxHistorySize, history.size()).clear(); }
            } else { plugin.getLogger().fine("Entrée historique déjà existante ignorée pour " + playerUUID + ": " + newEntry); }
        }
    }
    // --- Fin Méthodes Historique ---


    // --- Méthodes pour les Niveaux d'Entreprise (Employés et Solde) ---

    public int getLimiteMaxEmployesActuelle(Entreprise entreprise) {
        if (entreprise == null) return 0;
        return plugin.getConfig().getInt("finance.max-employer-par-entreprise." + entreprise.getNiveauMaxEmployes(), 0);
    }

    public double getLimiteMaxSoldeActuelle(Entreprise entreprise) {
        if (entreprise == null) return 0.0;
        return plugin.getConfig().getDouble("finance.max-solde-par-niveau." + entreprise.getNiveauMaxSolde(), 0.0);
    }

    public int getNiveauMaxPossibleEmployes() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("finance.max-employer-par-entreprise");
        if (section == null) return 0;
        // Les niveaux sont 0, 1, 2, 3, 4. Le plus haut niveau est la taille de la section - 1.
        return section.getKeys(false).size() - 1;
    }

    public int getNiveauMaxPossibleSolde() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("finance.max-solde-par-niveau");
        if (section == null) return 0;
        return section.getKeys(false).size() - 1;
    }

    public double getCoutProchaineAmeliorationEmployes(Entreprise entreprise) {
        if (entreprise == null) return -1; // Coût invalide
        int niveauActuel = entreprise.getNiveauMaxEmployes();
        int niveauMaxPossible = getNiveauMaxPossibleEmployes();
        if (niveauActuel >= niveauMaxPossible) {
            return -1; // Niveau maximum déjà atteint
        }
        // Le coût est pour passer AU niveau suivant (niveauActuel + 1)
        return plugin.getConfig().getDouble("finance.cout-amelioration-niveau-max-employer." + (niveauActuel + 1), -1);
    }

    public double getCoutProchaineAmeliorationSolde(Entreprise entreprise) {
        if (entreprise == null) return -1; // Coût invalide
        int niveauActuel = entreprise.getNiveauMaxSolde();
        int niveauMaxPossible = getNiveauMaxPossibleSolde();
        if (niveauActuel >= niveauMaxPossible) {
            return -1; // Niveau maximum déjà atteint
        }
        // Le coût est pour passer AU niveau suivant (niveauActuel + 1)
        return plugin.getConfig().getDouble("finance.cout-amelioration-niveau-max-solde." + (niveauActuel + 1), -1);
    }

    public String tenterAmeliorationNiveauMaxEmployes(Entreprise entreprise, Player gerant) {
        if (entreprise == null) return ChatColor.RED + "Entreprise non trouvée.";
        if (!entreprise.getGerant().equalsIgnoreCase(gerant.getName())) return ChatColor.RED + "Seul le gérant peut effectuer cette action.";

        int niveauActuel = entreprise.getNiveauMaxEmployes();
        int niveauMaxPossible = getNiveauMaxPossibleEmployes();

        if (niveauActuel >= niveauMaxPossible) {
            return ChatColor.YELLOW + "Votre entreprise a déjà atteint le niveau maximum pour le nombre d'employés ("+ getLimiteMaxEmployesActuelle(entreprise) + ").";
        }

        double cout = getCoutProchaineAmeliorationEmployes(entreprise);
        if (cout < 0) { // Devrait coïncider avec le test de niveau max, mais double sécurité
            return ChatColor.RED + "Impossible de déterminer le coût de l'amélioration ou niveau max atteint.";
        }

        if (entreprise.getSolde() < cout) {
            return ChatColor.RED + "Solde de l'entreprise insuffisant (" + String.format("%,.2f", entreprise.getSolde()) + "€). Requis : " + String.format("%,.2f", cout) + "€.";
        }

        entreprise.setSolde(entreprise.getSolde() - cout);
        entreprise.setNiveauMaxEmployes(niveauActuel + 1);
        entreprise.addTransaction(new Transaction(TransactionType.OTHER_EXPENSE, cout, "Amélioration capacité employés (Niv. " + (niveauActuel + 1) + ")", gerant.getName()));
        saveEntreprises();

        return ChatColor.GREEN + "Capacité d'employés améliorée au niveau " + (niveauActuel + 1) + " ("+ getLimiteMaxEmployesActuelle(entreprise) +" employés max) ! Coût : " + String.format("%,.2f", cout) + "€.";
    }

    public String tenterAmeliorationNiveauMaxSolde(Entreprise entreprise, Player gerant) {
        if (entreprise == null) return ChatColor.RED + "Entreprise non trouvée.";
        if (!entreprise.getGerant().equalsIgnoreCase(gerant.getName())) return ChatColor.RED + "Seul le gérant peut effectuer cette action.";

        int niveauActuel = entreprise.getNiveauMaxSolde();
        int niveauMaxPossible = getNiveauMaxPossibleSolde();

        if (niveauActuel >= niveauMaxPossible) {
            return ChatColor.YELLOW + "Votre entreprise a déjà atteint le niveau maximum pour le solde ("+ String.format("%,.2f", getLimiteMaxSoldeActuelle(entreprise)) +"€).";
        }

        double cout = getCoutProchaineAmeliorationSolde(entreprise);
        if (cout < 0) {
            return ChatColor.RED + "Impossible de déterminer le coût de l'amélioration ou niveau max atteint.";
        }

        if (entreprise.getSolde() < cout) {
            return ChatColor.RED + "Solde de l'entreprise insuffisant (" + String.format("%,.2f", entreprise.getSolde()) + "€). Requis : " + String.format("%,.2f", cout) + "€.";
        }

        entreprise.setSolde(entreprise.getSolde() - cout);
        entreprise.setNiveauMaxSolde(niveauActuel + 1);
        entreprise.addTransaction(new Transaction(TransactionType.OTHER_EXPENSE, cout, "Amélioration solde max (Niv. " + (niveauActuel + 1) + ")", gerant.getName()));
        saveEntreprises();

        return ChatColor.GREEN + "Solde maximum amélioré au niveau " + (niveauActuel + 1) + " ("+ String.format("%,.2f", getLimiteMaxSoldeActuelle(entreprise)) +"€ max) ! Coût : " + String.format("%,.2f", cout) + "€.";
    }
    // --- Fin Méthodes pour les Niveaux ---


    // --- Classe Interne Entreprise ---
    public static class Entreprise {
        private String nom; private final String ville; private final String type;
        private final String gerantNom; private final String gerantUUID;
        private final Set<String> employesNoms; private final Map<String, Double> primes;
        private double solde; private final String siret; private double chiffreAffairesTotal;
        private List<Transaction> transactionLog; private Map<UUID, EmployeeActivityRecord> employeeActivityRecords;
        private List<ProductionRecord> globalProductionLog;
        private int niveauMaxEmployes; // Nouveau champ
        private int niveauMaxSolde;    // Nouveau champ

        public Entreprise(String nom, String ville, String type, String gerantNom, String gerantUUID, Set<String> employesNoms, double solde, String siret) {
            this.nom = nom; this.ville = ville; this.type = type; this.gerantNom = gerantNom; this.gerantUUID = gerantUUID;
            this.employesNoms = (employesNoms != null) ? ConcurrentHashMap.newKeySet() : ConcurrentHashMap.newKeySet();
            if (employesNoms != null) this.employesNoms.addAll(employesNoms);
            this.solde = solde; this.siret = siret; this.chiffreAffairesTotal = 0.0;
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
            this.niveauMaxEmployes = 0; // Valeur par défaut à la création
            this.niveauMaxSolde = 0;    // Valeur par défaut à la création
        }

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

        public int getNiveauMaxEmployes() { return niveauMaxEmployes; } // Nouveau getter
        public int getNiveauMaxSolde() { return niveauMaxSolde; }       // Nouveau getter

        public void setNom(String nom) { this.nom = nom; }
        protected Set<String> getEmployesInternal() { return this.employesNoms; }
        public synchronized void setSolde(double solde) { this.solde = solde; }
        // FIX BASSE #16: Renamed parameters for clarity
        public synchronized void setChiffreAffairesTotal(double totalRevenue) { this.chiffreAffairesTotal = totalRevenue; }
        public void setPrimes(Map<String, Double> newPrimes) { this.primes.clear(); if (newPrimes != null) this.primes.putAll(newPrimes); }
        public void setTransactionLog(List<Transaction> transactions) { synchronized(transactionLog) { this.transactionLog.clear(); if (transactions != null) this.transactionLog.addAll(transactions); } }
        public void setEmployeeActivityRecords(Map<UUID, EmployeeActivityRecord> records) { this.employeeActivityRecords.clear(); if (records != null) this.employeeActivityRecords.putAll(records); }
        public void setGlobalProductionLog(List<ProductionRecord> productionRecords) { synchronized(globalProductionLog) { this.globalProductionLog.clear(); if (productionRecords != null) this.globalProductionLog.addAll(productionRecords); } }
        public void addTransaction(Transaction tx) { synchronized(transactionLog) { this.transactionLog.add(tx); int maxLogSize = plugin.getConfig().getInt("entreprise.max-transaction-log-size", 200); if(transactionLog.size() > maxLogSize) transactionLog.subList(0, transactionLog.size() - maxLogSize).clear(); } }
        public void addGlobalProductionRecord(LocalDateTime timestamp, Material material, int quantity, String employeeUUIDPerformingAction, DetailedActionType actionType) {
            synchronized(globalProductionLog) {
                this.globalProductionLog.add(new ProductionRecord(timestamp, material, quantity, employeeUUIDPerformingAction, actionType));
                // FIX CRITIQUE: Rotation des logs pour éviter fuite mémoire (millions d'enregistrements après quelques semaines)
                int maxLogSize = plugin.getConfig().getInt("entreprise.max-production-log-size", 500);
                if(globalProductionLog.size() > maxLogSize)
                    globalProductionLog.subList(0, globalProductionLog.size() - maxLogSize).clear();
            }
        }
        public EmployeeActivityRecord getEmployeeActivityRecord(UUID employeeId) { return employeeActivityRecords.get(employeeId); }
        public EmployeeActivityRecord getOrCreateEmployeeActivityRecord(UUID employeeId, String employeeName) { return employeeActivityRecords.computeIfAbsent(employeeId, k -> new EmployeeActivityRecord(k, employeeName)); }
        public double getPrimePourEmploye(String employeeUUID) { return this.primes.getOrDefault(employeeUUID, 0.0); }
        public void setPrimePourEmploye(String employeeUUID, double prime) { this.primes.put(employeeUUID, Math.max(0, prime)); }
        public void retirerPrimeEmploye(String employeeUUID) { this.primes.remove(employeeUUID); }

        public void setNiveauMaxEmployes(int niveau) { this.niveauMaxEmployes = niveau; } // Nouveau setter
        public void setNiveauMaxSolde(int niveau) { this.niveauMaxSolde = niveau; }       // Nouveau setter

        public double calculateProfitLoss(LocalDateTime start, LocalDateTime end) { synchronized(transactionLog) { double income = 0; double expense = 0; for (Transaction tx : transactionLog) { if (!tx.timestamp.isBefore(start) && tx.timestamp.isBefore(end)) { if (tx.type.isOperationalIncome()) income += tx.amount; else if (tx.type.isOperationalExpense()) expense += Math.abs(tx.amount); } } return income - expense; } }
        public Map<Material, Integer> getEmployeeProductionStatsForPeriod(UUID employeeUUID, LocalDateTime start, LocalDateTime end, DetailedActionType actionTypeFilter, Set<Material> relevantMaterials) { EmployeeActivityRecord record = getEmployeeActivityRecord(employeeUUID); if (record == null) return Collections.emptyMap(); return record.getDetailedStatsForPeriod(actionTypeFilter, start, end, relevantMaterials); }
        public Map<Material, Integer> getAggregatedProductionStatsForPeriod(LocalDateTime start, LocalDateTime end, DetailedActionType actionTypeFilter, Set<Material> relevantMaterials) { Map<Material, Integer> aggregatedStats = new HashMap<>(); for (EmployeeActivityRecord record : employeeActivityRecords.values()) { Map<Material, Integer> employeeStats = record.getDetailedStatsForPeriod(actionTypeFilter, start, end, relevantMaterials); employeeStats.forEach((material, quantity) -> aggregatedStats.merge(material, quantity, Integer::sum)); } return aggregatedStats; }
        // FIX BASSE #3: Méthode getGlobalProductionStatsForPeriod() deprecated supprimée - utiliser getAggregatedProductionStatsForPeriod()
        public String getEmployeeSeniorityFormatted(UUID employeeId) { EmployeeActivityRecord record = getEmployeeActivityRecord(employeeId); return (record != null) ? record.getFormattedSeniority() : "N/A"; }
        public Set<Material> getTrackedProductionMaterials() { Set<Material> materials = new HashSet<>(); if (plugin == null || plugin.getConfig() == null) return materials; ConfigurationSection typeConfig = plugin.getConfig().getConfigurationSection("types-entreprise." + this.type); if (typeConfig == null) return materials; ConfigurationSection activitesPayantesConfig = typeConfig.getConfigurationSection("activites-payantes"); if (activitesPayantesConfig != null) { for (String actionTypeKey : activitesPayantesConfig.getKeys(false)) { ConfigurationSection materialsConfig = activitesPayantesConfig.getConfigurationSection(actionTypeKey); if (materialsConfig != null) { for (String materialKey : materialsConfig.getKeys(false)) { Material mat = Material.matchMaterial(materialKey); if (mat != null) materials.add(mat); } } } } return materials; }
        @Override public String toString() { return "Entreprise{nom='" + nom + "', type='" + type + "', gérant='" + gerantNom + "', solde=" + solde + "}"; }
    }
    // --- Fin Classe Entreprise ---

    // --- Classes ActionInfo, DemandeCreation, ProductionRecord ---
    public static class ActionInfo {
        private int nombreActions; private LocalDateTime dernierActionHeure;
        public ActionInfo() { this.nombreActions = 0; this.dernierActionHeure = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS); }
        public int getNombreActions() { return nombreActions; } public LocalDateTime getDernierActionHeure() { return dernierActionHeure; }
        public void incrementerActions(int quantite) { this.nombreActions += quantite; }
        public void reinitialiserActions(LocalDateTime maintenant) { this.nombreActions = 0; this.dernierActionHeure = maintenant.truncatedTo(ChronoUnit.HOURS); }
        @Override public String toString() { return "ActionInfo{nActions=" + nombreActions + ", heure=" + dernierActionHeure.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "}"; }
    }
    public static class DemandeCreation {
        public final Player maire; public final Player gerant;
        public final String type; public final String ville; public final String siret;
        public final String nomEntreprise; public final double cout; private final long expirationTimeMillis;
        public DemandeCreation(Player maire, Player gerant, String type, String ville, String siret, String nomEntreprise, double cout, long dureeValiditeMillis) { this.maire = maire; this.gerant = gerant; this.type = type; this.ville = ville; this.siret = siret; this.nomEntreprise = nomEntreprise; this.cout = cout; this.expirationTimeMillis = System.currentTimeMillis() + dureeValiditeMillis; }
        public boolean isExpired() { return System.currentTimeMillis() > expirationTimeMillis; }
        public long getExpirationTimeMillis() { return expirationTimeMillis; }
    }
    public static class ProductionRecord {
        public final LocalDateTime timestamp; public final Material material; public final int quantity;
        public final String recordedByEmployeeUUID; public final DetailedActionType actionType;
        public ProductionRecord(LocalDateTime timestamp, Material material, int quantity, String recordedByEmployeeUUID, DetailedActionType actionType) { this.timestamp = timestamp; this.material = material; this.quantity = quantity; this.recordedByEmployeeUUID = recordedByEmployeeUUID; this.actionType = actionType; }
        public Map<String, Object> serialize() { Map<String, Object> map = new HashMap<>(); map.put("timestamp", timestamp.toString()); map.put("material", material.name()); map.put("quantity", quantity); map.put("recordedByEmployeeUUID", recordedByEmployeeUUID); map.put("actionType", actionType.name()); return map; }
        public static ProductionRecord deserialize(Map<String, Object> map) { try { LocalDateTime ts = LocalDateTime.parse((String) map.get("timestamp")); Material mat = Material.matchMaterial((String) map.get("material")); int qty = ((Number) map.get("quantity")).intValue(); String uuid = (String) map.get("recordedByEmployeeUUID"); DetailedActionType at = DetailedActionType.valueOf((String) map.getOrDefault("actionType", DetailedActionType.BLOCK_BROKEN.name())); if (mat == null) { if (plugin != null) plugin.getLogger().warning("Material null désérialisation PR (global): " + map.get("material")); return null; } return new ProductionRecord(ts, mat, qty, uuid, at); } catch (Exception e) { if (plugin != null) plugin.getLogger().warning("Erreur désérialisation PR (global): " + e.getMessage() + " pour map: " + map); return null; } }
    }
    // --- Fin Classes ---

} // Fin de la classe EntrepriseManagerLogic