package com.gravityyfh.entreprisemanager;

// --- Imports ---
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownyPermission;
import com.palmergames.bukkit.towny.utils.PlayerCacheUtil;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class EntrepriseManagerLogic {
    static EntrepriseManager plugin;
    private static Map<String, Entreprise> entreprises;
    private static File entrepriseFile;

    private BukkitTask activityCheckTask;
    private LocalDateTime nextPaymentTime; // <-- CHAMP À AJOUTER
    private static final long INACTIVITY_THRESHOLD_SECONDS = 15;

    // --- Historique ---
    private static File playerHistoryFile;
    private static Map<UUID, List<PastExperience>> playerHistoryCache;
    // --- Fin Historique ---

    private final Map<String, Double> activiteHoraireValeur = new ConcurrentHashMap<>();
    private final Map<String, String> invitations = new ConcurrentHashMap<>();
    private final Map<UUID, DemandeCreation> demandesEnAttente = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, ActionInfo>> joueurActivitesRestrictions = new ConcurrentHashMap<>();
    private BukkitTask hourlyTask;
    private static final long ACTIVITY_CHECK_INTERVAL_TICKS = 20L * 10L;


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
        public void recordAction(String genericActionKey, double value, int quantity, DetailedActionType detailedActionType, Material material) { this.actionsPerformedCount.merge(genericActionKey, (long) quantity, Long::sum); this.totalValueGenerated += value; this.lastActivityTime = LocalDateTime.now(); synchronized(detailedProductionLog) { this.detailedProductionLog.add(new DetailedProductionRecord(detailedActionType, material, quantity)); } if (this.currentSessionStartTime == null) { startSession(); } }
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

    public EntrepriseManagerLogic(EntrepriseManager plugin) {
        EntrepriseManagerLogic.plugin = plugin;
        entreprises = new ConcurrentHashMap<>();
        entrepriseFile = new File(plugin.getDataFolder(), "entreprise.yml");
        // --- Suppression de l'initialisation de playerPlacedBlocksFile et playerPlacedBlocksLocations ---

        playerHistoryFile = new File(plugin.getDataFolder(), "player_history.yml");
        playerHistoryCache = new ConcurrentHashMap<>();

        loadEntreprises();
        loadPlayerHistory();
        planifierTachesHoraires();
        planifierVerificationActiviteEmployes();
    }

    // --- Suppression de toute la section "Blocs Posés" (méthodes serializeLocation, deserializeLocation, marquer..., demarquer..., estBloc..., load..., save...) ---

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

    public void payerChargesSalarialesHoraires() {
        double chargeParEmploye = plugin.getConfig().getDouble("finance.charge-salariale-par-employe-horaire", 0.0);
        boolean actifsSeulement = plugin.getConfig().getBoolean("finance.charges-sur-employes-actifs-seulement", true);

        if (chargeParEmploye <= 0) return;
        boolean modified = false;

        plugin.getLogger().info("Début du calcul des charges salariales horaires...");

        for (Entreprise entreprise : entreprises.values()) {
            long nbEmployesConcernes;
            if (actifsSeulement) {
                nbEmployesConcernes = entreprise.getEmployeeActivityRecords().values().stream()
                        .filter(EmployeeActivityRecord::isActive)
                        .count();
            } else {
                nbEmployesConcernes = entreprise.getEmployes().size();
            }

            if (nbEmployesConcernes == 0) continue;

            double totalCharges = nbEmployesConcernes * chargeParEmploye;
            Player gerantPlayer = Bukkit.getPlayerExact(entreprise.getGerant());
            OfflinePlayer offlineGerant = Bukkit.getOfflinePlayer(UUID.fromString(entreprise.getGerantUUID()));
            String employesType = actifsSeulement ? "actifs" : "total";

            if (entreprise.getSolde() >= totalCharges) {
                double soldeAvant = entreprise.getSolde();
                entreprise.setSolde(soldeAvant - totalCharges);
                entreprise.addTransaction(new Transaction(TransactionType.PAYROLL_TAX, totalCharges, "Charges salariales (" + nbEmployesConcernes + " emp. " + employesType + ")", "System"));
                modified = true;
                String msgSucces = String.format("&aCharges salariales horaires (&b%d&a emp. %s): &e-%.2f€&a. Solde: &e%.2f€ &7-> &e%.2f€",
                        nbEmployesConcernes, employesType, totalCharges, soldeAvant, entreprise.getSolde());

                plugin.getLogger().info("Charges payées pour '" + entreprise.getNom() + "': " + totalCharges + "€ pour " + nbEmployesConcernes + " emp. " + employesType);

                if (gerantPlayer != null && gerantPlayer.isOnline()) gerantPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', msgSucces));
                else if (offlineGerant.hasPlayedBefore() || offlineGerant.isOnline()) ajouterMessageGerantDifferre(entreprise.getGerantUUID(), ChatColor.translateAlternateColorCodes('&',msgSucces), entreprise.getNom(), -totalCharges);

            } else {
                String msgEchec = String.format("&cSolde insuffisant (&e%.2f€&c) pour charges salariales (&e%.2f€&c pour %d emp. %s).",
                        entreprise.getSolde(), totalCharges, nbEmployesConcernes, employesType);

                plugin.getLogger().warning("Solde insuffisant pour charges salariales pour '" + entreprise.getNom() + "'. Requis: " + totalCharges + "€, Solde: " + entreprise.getSolde());

                if (gerantPlayer != null && gerantPlayer.isOnline()) gerantPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', msgEchec));
                else if (offlineGerant.hasPlayedBefore() || offlineGerant.isOnline()) ajouterMessageGerantDifferre(entreprise.getGerantUUID(), ChatColor.translateAlternateColorCodes('&',msgEchec), entreprise.getNom(), 0);
            }
        }
        if (modified) {
            saveEntreprises();
            plugin.getLogger().info("Charges salariales horaires traitées et sauvegardées.");
        } else {
            plugin.getLogger().info("Aucune charge salariale à appliquer cette heure.");
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
                plugin.getLogger().info("Exécution des tâches horaires automatiques...");
                traiterChiffreAffairesHoraire();
                payerPrimesHorairesAuxEmployes();
                payerChargesSalarialesHoraires();
                payerAllocationChomageHoraire();

                // Réinitialise les limites pour tous les joueurs en même temps
                resetHourlyLimitsForAllPlayers();

                plugin.getLogger().info("Tâches horaires automatiques terminées.");

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

        // La vérification estBlocPoseParJoueur a été retirée d'ici.
        // Elle est maintenant gérée en amont par EventListener (pour BlockBreakEvent)
        // et BlockPlaceListener (pour anti-pose-casse-repose) via CoreProtect.

        // Validation Towny (reste pertinente)
        if (actionTypeString.equalsIgnoreCase("BLOCK_BREAK")) {
            if (block == null) {
                plugin.getLogger().warning("BLOCK_BREAK enregistré sans référence de bloc pour " + player.getName() + " sur " + material.name());
            } else {
                if (!canPlayerBreakBlock(player, block.getLocation(), material)) {
                    plugin.getLogger().fine("Action BLOCK_BREAK sur ("+ material.name() +") annulée par protection (ex: Towny) pour " + player.getName());
                    return;
                }
            }
        } else if (actionTypeString.equalsIgnoreCase("BLOCK_PLACE")) {
            if (block == null) {
                plugin.getLogger().warning("BLOCK_PLACE enregistré sans référence de bloc pour " + player.getName() + " sur " + material.name());
            } else {
                if (!canPlayerPlaceBlock(player, block.getLocation(), material)) {
                    plugin.getLogger().fine("Action BLOCK_PLACE de ("+ material.name() +") annulée par protection (ex: Towny) pour " + player.getName());
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
            activiteHoraireValeur.merge(entreprise.getNom(), valeurTotaleAction, Double::sum);
            plugin.getLogger().fine("CA Horaire pour '" + entreprise.getNom() + "' augmenté de " + valeurTotaleAction + " (" + actionTypeString + ": " + material.name() + ")");
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
            activiteHoraireValeur.merge(entreprise.getNom(), valeurTotaleAction, Double::sum);
            plugin.getLogger().fine("CA Horaire pour '" + entreprise.getNom() + "' augmenté de " + valeurTotaleAction + " (Action: " + actionType + ", Cible: " + entityTypeName + ")");
        }

        if (materialEquivalentPourLogDetaille != null) {
            entreprise.addGlobalProductionRecord(LocalDateTime.now(), materialEquivalentPourLogDetaille, quantite, player.getUniqueId().toString(), actionDetailleePourLog);
        }

        plugin.getLogger().fine("Action productive (ENTITY_KILL) enregistrée pour " + player.getName() + ": " + quantite + "x " + entityTypeName +
                (valeurTotaleAction > 0 ? " (Valeur: " + valeurTotaleAction + "€)" : " (Non valorisé)") +
                (materialEquivalentPourLogDetaille != null ? ", log détaillé comme " + materialEquivalentPourLogDetaille.name() : ", pas de log détaillé matériel") );
    }

    public void traiterChiffreAffairesHoraire() {
        if (entreprises.isEmpty()) return;
        double pourcentageTaxes = plugin.getConfig().getDouble("finance.pourcentage-taxes", 15.0);
        boolean modified = false;
        for (Map.Entry<String, Double> entry : new HashMap<>(activiteHoraireValeur).entrySet()) {
            String nomEntreprise = entry.getKey(); double caBrutHoraire = entry.getValue();
            if (caBrutHoraire <= 0) { activiteHoraireValeur.put(nomEntreprise, 0.0); continue; }
            Entreprise entreprise = entreprises.get(nomEntreprise);
            if (entreprise == null) { activiteHoraireValeur.remove(nomEntreprise); continue; }

            modified = true;
            double ancienSolde = entreprise.getSolde(); double taxesCalculees = caBrutHoraire * (pourcentageTaxes / 100.0);
            double caNetHoraire = caBrutHoraire - taxesCalculees;
            entreprise.setSolde(ancienSolde + caNetHoraire); entreprise.setChiffreAffairesTotal(entreprise.getChiffreAffairesTotal() + caBrutHoraire);
            entreprise.addTransaction(new Transaction(TransactionType.REVENUE, caBrutHoraire, "Revenu horaire brut", "System"));
            if (taxesCalculees > 0) entreprise.addTransaction(new Transaction(TransactionType.TAXES, taxesCalculees, "Impôts ("+pourcentageTaxes+"%) sur CA horaire", "System"));

            Player gerantPlayer = Bukkit.getPlayerExact(entreprise.getGerant());
            String messageDetails = String.format("&bSolde: &f%.2f€ &7| &bCA Brut: &f+%.2f€ &7| &cTaxes: &f-%.2f€ &7| &aCA Net: &f+%.2f€ &7| &bNouv. Solde: &f&l%.2f€", ancienSolde, caBrutHoraire, taxesCalculees, caNetHoraire, entreprise.getSolde());

            if (gerantPlayer != null && gerantPlayer.isOnline()) { gerantPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6&l[Rapport Horaire] &e" + entreprise.getNom())); gerantPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', messageDetails)); }
            else {
                OfflinePlayer offlineGerant = Bukkit.getOfflinePlayer(UUID.fromString(entreprise.getGerantUUID()));
                if(offlineGerant.hasPlayedBefore() || offlineGerant.isOnline()){ String resume = String.format("Rapport Horaire '%s': CA Net +%.2f€. Solde: %.2f€.", entreprise.getNom(), caNetHoraire, entreprise.getSolde()); ajouterMessageGerantDifferre(entreprise.getGerantUUID(), ChatColor.GREEN + resume, entreprise.getNom(), caNetHoraire); }
            }
            activiteHoraireValeur.put(nomEntreprise, 0.0);
        }
        if (modified) saveEntreprises();
    }

    public void payerPrimesHorairesAuxEmployes() {
        if (entreprises.isEmpty()) return;
        boolean modified = false;
        for (Entreprise entreprise : entreprises.values()) {
            Player gerantPlayer = Bukkit.getPlayerExact(entreprise.getGerant());
            OfflinePlayer offlineGerant = Bukkit.getOfflinePlayer(UUID.fromString(entreprise.getGerantUUID()));

            for (String employeNom : new HashSet<>(entreprise.getEmployes())) {
                OfflinePlayer employeOffline = Bukkit.getOfflinePlayer(employeNom);
                UUID employeUUID = null; try { if(employeOffline != null) employeUUID = employeOffline.getUniqueId(); } catch (Exception ignored) {}
                if (employeUUID == null) { plugin.getLogger().warning("UUID invalide pour employé: " + employeNom + " (ent: " + entreprise.getNom() + "). Prime ignorée."); continue; }

                double primeConfigurée = entreprise.getPrimePourEmploye(employeUUID.toString());
                if (primeConfigurée <= 0) continue;
                EmployeeActivityRecord activity = entreprise.getEmployeeActivityRecord(employeUUID);
                if (activity == null || !activity.isActive()) continue;

                if (entreprise.getSolde() >= primeConfigurée) {
                    EconomyResponse er = EntrepriseManager.getEconomy().depositPlayer(employeOffline, primeConfigurée);
                    if (er.transactionSuccess()) {
                        double soldeAvant = entreprise.getSolde(); entreprise.setSolde(soldeAvant - primeConfigurée);
                        entreprise.addTransaction(new Transaction(TransactionType.PRIMES, primeConfigurée, "Prime horaire: " + employeNom, "System"));
                        modified = true;
                        String msgEmploye = String.format("&aPrime horaire reçue: &e%.2f€&a de '&6%s&a'.", primeConfigurée, entreprise.getNom());
                        String msgGerant = String.format("&bPrime versée à &3%s&b: &e%.2f€&b. Solde: &e%.2f€ &7-> &e%.2f€", employeNom, primeConfigurée, soldeAvant, entreprise.getSolde());
                        Player onlineEmp = employeOffline.getPlayer();
                        if (onlineEmp != null) onlineEmp.sendMessage(ChatColor.translateAlternateColorCodes('&', msgEmploye));
                        else ajouterMessageEmployeDifferre(employeUUID.toString(), ChatColor.translateAlternateColorCodes('&',msgEmploye), entreprise.getNom(), primeConfigurée);
                        if (gerantPlayer != null && gerantPlayer.isOnline()) gerantPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', msgGerant));
                        else if (offlineGerant.hasPlayedBefore() || offlineGerant.isOnline()) ajouterMessageGerantDifferre(entreprise.getGerantUUID(), ChatColor.translateAlternateColorCodes('&',msgGerant), entreprise.getNom(), primeConfigurée);
                    } else { if (gerantPlayer != null && gerantPlayer.isOnline()) gerantPlayer.sendMessage(ChatColor.RED + "Erreur versement prime à " + employeNom + ": " + er.errorMessage); plugin.getLogger().severe("Erreur Vault prime " + employeNom + ": " + er.errorMessage); }
                } else {
                    String msgEchecEmp = String.format("&cL'entreprise '&6%s&c' n'a pas pu verser votre prime de &e%.2f€&c.", entreprise.getNom(), primeConfigurée);
                    String msgEchecGer = String.format("&cSolde insuffisant (&e%.2f€&c) pour prime de &3%s&c (&e%.2f€&c).", entreprise.getSolde(), employeNom, primeConfigurée);
                    Player onlineEmp = employeOffline.getPlayer();
                    if (onlineEmp != null) onlineEmp.sendMessage(ChatColor.translateAlternateColorCodes('&',msgEchecEmp));
                    else ajouterMessageEmployeDifferre(employeUUID.toString(), ChatColor.translateAlternateColorCodes('&',msgEchecEmp), entreprise.getNom(), 0);
                    if (gerantPlayer != null && gerantPlayer.isOnline()) gerantPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&',msgEchecGer));
                    else if (offlineGerant.hasPlayedBefore() || offlineGerant.isOnline()) ajouterMessageGerantDifferre(entreprise.getGerantUUID(), ChatColor.translateAlternateColorCodes('&',msgEchecGer), entreprise.getNom(), 0);
                }
            }
        }
        if (modified) saveEntreprises();
    }

    public void payerAllocationChomageHoraire() {
        double montantAllocation = plugin.getConfig().getDouble("finance.allocation-chomage-horaire", 0);
        if (montantAllocation <= 0) return;
        int joueursPayes = 0;
        for (Player joueurConnecte : Bukkit.getOnlinePlayers()) {
            if (getNomEntrepriseDuMembre(joueurConnecte.getName()) == null) {
                EconomyResponse er = EntrepriseManager.getEconomy().depositPlayer(joueurConnecte, montantAllocation);
                if (er.transactionSuccess()) { joueurConnecte.sendMessage(ChatColor.translateAlternateColorCodes('&', String.format("&6[Alloc. Chômage] &a+%.2f€", montantAllocation))); joueursPayes++; }
                else { plugin.getLogger().warning("Impossible de verser alloc. chômage à " + joueurConnecte.getName() + ": " + er.errorMessage); }
            }
        }
        if (joueursPayes > 0) plugin.getLogger().fine(joueursPayes + " joueur(s) ont reçu l'allocation chômage.");
    }

    public boolean verifierEtGererRestrictionAction(Player player, String actionTypeString, String targetName, int quantite) {
        Entreprise entrepriseJoueurObj = getEntrepriseDuJoueur(player);
        String typeEntrepriseJoueur = (entrepriseJoueurObj != null) ? entrepriseJoueurObj.getType() : "Aucune";

        ConfigurationSection typesEntreprisesConfig = plugin.getConfig().getConfigurationSection("types-entreprise");
        if (typesEntreprisesConfig == null) {
            plugin.getLogger().severe("[DEBUG Restrict] Section 'types-entreprise' INTROUVABLE dans config.yml!");
            return false;
        }

        for (String typeEntSpecialise : typesEntreprisesConfig.getKeys(false)) {
            String restrictionPath = "types-entreprise." + typeEntSpecialise + ".action_restrictions." + actionTypeString.toUpperCase() + "." + targetName.toUpperCase();
            boolean actionEstRestreinte = plugin.getConfig().contains(restrictionPath);

            if (actionEstRestreinte) {
                boolean estMembreDeCeTypeSpecialise = (entrepriseJoueurObj != null && entrepriseJoueurObj.getType().equals(typeEntSpecialise));

                if (estMembreDeCeTypeSpecialise) {
                    return false; // Les membres ne sont pas restreints, on autorise l'action.
                } else {
                    // Le joueur n'est pas membre, on applique la limite horaire.
                    int limiteNonMembre = plugin.getConfig().getInt("types-entreprise." + typeEntSpecialise + ".limite-non-membre-par-heure", -1);
                    if (limiteNonMembre == -1) {
                        continue; // Pas de limite pour ce type, on passe au suivant.
                    }

                    List<String> messagesErreur = plugin.getConfig().getStringList("types-entreprise." + typeEntSpecialise + ".message-erreur-restriction");
                    if (messagesErreur.isEmpty()) {
                        messagesErreur.add("&cAction restreinte. Limite horaire : %limite%");
                    }

                    if (limiteNonMembre == 0) {
                        messagesErreur.forEach(msg -> player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg.replace("%limite%", "0"))));
                        return true; // Action bloquée.
                    }

                    String actionIdPourRestriction = typeEntSpecialise + "_" + actionTypeString.toUpperCase() + "_" + targetName.toUpperCase();
                    ActionInfo info = joueurActivitesRestrictions
                            .computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                            .computeIfAbsent(actionIdPourRestriction, k -> new ActionInfo());

                    synchronized(info) {
                        // LA LOGIQUE DE RESET INDIVIDUELLE A ÉTÉ SUPPRIMÉE ICI.
                        // La réinitialisation est maintenant globale via resetHourlyLimitsForAllPlayers().

                        if (info.getNombreActions() + quantite > limiteNonMembre) {
                            final int currentCount = info.getNombreActions();
                            messagesErreur.forEach(msg -> player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg.replace("%limite%", String.valueOf(limiteNonMembre)))));
                            player.sendMessage(ChatColor.GRAY + "(Limite atteinte: " + currentCount + "/" + limiteNonMembre + ")");
                            return true; // Action bloquée car la limite est dépassée.
                        } else {
                            info.incrementerActions(quantite);
                        }
                    }
                }
            }
        }

        return false; // Aucune restriction bloquante trouvée, l'action est autorisée.
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
    // --- Fin Accès Entreprises ---

    // --- Suppression et Dissolution (AVEC HISTORIQUE) ---
    public void handleEntrepriseRemoval(Entreprise entreprise, String reason) {
        if (entreprise == null) return;
        String nomEntreprise = entreprise.getNom();
        UUID gerantUUID = null; try { gerantUUID = UUID.fromString(entreprise.getGerantUUID()); } catch (Exception ignored) {}
        plugin.getLogger().info("[EntrepriseManagerLogic] Enregistrement historique avant dissolution de '" + nomEntreprise + "'. Raison: " + reason);
        LocalDateTime dateDissolution = LocalDateTime.now();
        if (gerantUUID != null) { recordPlayerHistoryEntry(gerantUUID, entreprise, "Gérant", dateDissolution); }
        else { plugin.getLogger().warning("UUID gérant invalide pour " + nomEntreprise + ", historique gérant non enregistré."); }
        Set<String> employesAvantDissolution = new HashSet<>(entreprise.getEmployes());
        for (String employeNom : employesAvantDissolution) {
            OfflinePlayer offlineEmp = Bukkit.getOfflinePlayer(employeNom);
            if (offlineEmp != null && (offlineEmp.hasPlayedBefore() || offlineEmp.isOnline())) { recordPlayerHistoryEntry(offlineEmp.getUniqueId(), entreprise, "Employé", dateDissolution); }
            else { plugin.getLogger().warning("UUID invalide pour employé " + employeNom + " (ent: " + nomEntreprise + "), historique non enregistré."); }
        }
        plugin.getLogger().info("[EntrepriseManagerLogic] Suppression effective de '" + nomEntreprise + "'.");
        entreprise.getEmployeeActivityRecords().values().forEach(EmployeeActivityRecord::endSession);
        checkAndRemoveShopsIfNeeded(entreprise.getGerant(), nomEntreprise);
        entreprises.remove(nomEntreprise); activiteHoraireValeur.remove(nomEntreprise);
        saveEntreprises();
        Player gerantPlayer = (gerantUUID != null) ? Bukkit.getPlayer(gerantUUID) : null;
        if (gerantPlayer != null && gerantPlayer.isOnline()) { gerantPlayer.sendMessage(ChatColor.RED + "Votre entreprise '" + nomEntreprise + "' a été dissoute. Raison: " + reason); }
        for (String employeNom : employesAvantDissolution) { OfflinePlayer offlineEmp = Bukkit.getOfflinePlayer(employeNom); Player onlineEmp = (offlineEmp != null) ? offlineEmp.getPlayer() : null; if(onlineEmp != null){ onlineEmp.sendMessage(ChatColor.RED + "L'entreprise '" + nomEntreprise + "' dont vous étiez membre a été dissoute. Raison: " + reason); } }
    }
    public void supprimerEntreprise(Player initiator, String nomEntreprise) {
        Entreprise entrepriseASupprimer = getEntreprise(nomEntreprise);
        if (entrepriseASupprimer == null) { initiator.sendMessage(ChatColor.RED + "Entreprise '" + nomEntreprise + "' non trouvée."); return; }
        boolean isAdmin = initiator.hasPermission("entreprisemanager.admin.deleteany");
        boolean isGerant = entrepriseASupprimer.getGerant().equalsIgnoreCase(initiator.getName());
        if (!isGerant && !isAdmin) { initiator.sendMessage(ChatColor.RED + "Permission refusée."); return; }
        String reason = "Dissolution par " + initiator.getName() + (isAdmin && !isGerant ? " (Admin)" : " (Gérant)");
        handleEntrepriseRemoval(entrepriseASupprimer, reason);
        initiator.sendMessage(ChatColor.GREEN + "L'entreprise '" + nomEntreprise + "' a été dissoute.");
    }
    private void checkAndRemoveShopsIfNeeded(String gerantNom, String entrepriseSupprimeeNom) {
        long autresEntreprisesDuGerant = entreprises.values().stream().filter(e -> e.getGerant().equalsIgnoreCase(gerantNom) && !e.getNom().equalsIgnoreCase(entrepriseSupprimeeNom)).count();
        if (autresEntreprisesDuGerant == 0) { if (Bukkit.getPluginManager().getPlugin("QuickShop") != null) { plugin.getLogger().info("Gérant " + gerantNom + " n'a plus d'entreprises. Exécution 'qs removeall " + gerantNom + "'."); Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "quickshop removeall " + gerantNom)); } }
    }
    // --- Fin Suppression / Dissolution ---

    // --- Création / Invitations ---
    public void declareEntreprise(Player maireCreateur, String ville, String nomEntreprise, String type, Player gerantCible, String siret, double coutCreation) {
        if (entreprises.containsKey(nomEntreprise)) {
            maireCreateur.sendMessage(ChatColor.RED + "Nom entreprise déjà pris.");
            return;
        }
        Entreprise nouvelleEntreprise = new Entreprise(nomEntreprise, ville, type, gerantCible.getName(), gerantCible.getUniqueId().toString(), new HashSet<>(), 0.0, siret);
        nouvelleEntreprise.addTransaction(new Transaction(TransactionType.CREATION_COST, coutCreation, "Frais de création", gerantCible.getName()));
        entreprises.put(nomEntreprise, nouvelleEntreprise);
        activiteHoraireValeur.put(nomEntreprise, 0.0);
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
    public void addEmploye(String nomEntreprise, String nomJoueur, UUID joueurUUID) {
        Entreprise entreprise = entreprises.get(nomEntreprise);
        if (entreprise != null && entreprise.getEmployesInternal().add(nomJoueur)) {
            entreprise.setPrimePourEmploye(joueurUUID.toString(), 0.0);
            EmployeeActivityRecord record = entreprise.getOrCreateEmployeeActivityRecord(joueurUUID, nomJoueur);
            record.joinDate = LocalDateTime.now();
            saveEntreprises();
        }
    }
    // --- Fin Création / Invitations ---

    // --- Départ et Licenciement (AVEC HISTORIQUE) ---
    public void kickEmploye(Player gerant, String nomEntreprise, String nomEmployeAKick) {
        Entreprise entreprise = getEntreprise(nomEntreprise);
        if (entreprise == null || !entreprise.getGerant().equalsIgnoreCase(gerant.getName())) { gerant.sendMessage(ChatColor.RED + "Action impossible."); return; }
        OfflinePlayer employeOffline = Bukkit.getOfflinePlayer(nomEmployeAKick);
        if ((!employeOffline.hasPlayedBefore() && !employeOffline.isOnline()) || !entreprise.getEmployes().contains(nomEmployeAKick)) { gerant.sendMessage(ChatColor.RED + "Employé '" + nomEmployeAKick + "' introuvable ou pas ici."); return; }
        UUID employeUUID = employeOffline.getUniqueId();

        if (entreprise.getEmployesInternal().remove(nomEmployeAKick)) {
            recordPlayerHistoryEntry(employeUUID, entreprise, "Employé", LocalDateTime.now());
            entreprise.retirerPrimeEmploye(employeUUID.toString());
            EmployeeActivityRecord record = entreprise.getEmployeeActivityRecord(employeUUID); if (record != null) record.endSession();
            saveEntreprises();
            gerant.sendMessage(ChatColor.GREEN + nomEmployeAKick + " viré de '" + nomEntreprise + "'.");
            Player onlineEmp = employeOffline.getPlayer(); if (onlineEmp != null) { onlineEmp.sendMessage(ChatColor.RED + "Vous avez été viré de '" + nomEntreprise + "'."); }
        } else { gerant.sendMessage(ChatColor.RED + "Erreur licenciement " + nomEmployeAKick + "."); }
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
        } else { joueur.sendMessage(ChatColor.RED + "Erreur en quittant."); }
    }
    // --- Fin Départ / Licenciement ---

    // --- Création / Contrat ---

    public void proposerCreationEntreprise(Player maire, Player gerantCible, String type, String ville, String nomEntreprisePropose, String siret) {
        double coutCreation = plugin.getConfig().getDouble("types-entreprise." + type + ".cout-creation", 0.0);
        double distanceMaxCreation = plugin.getConfig().getDouble("creation.distance-max-maire-gerant", 15.0);

        plugin.getLogger().log(Level.INFO, "[DEBUG CREATION] Début proposition pour " + nomEntreprisePropose + " par " + maire.getName() + " pour gérant " + gerantCible.getName());

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

        // Vérification 2: (CLARIFICATION) Est-ce que le gerantCible est déjà salarié ailleurs au point de ne plus pouvoir être gérant ?
        // D'après votre définition, max-travail-joueur concerne les emplois salariés.
        // Un joueur peut donc être gérant de N entreprises (selon max-entreprises-par-gerant) ET salarié dans M autres (selon max-travail-joueur).
        // Donc, pour devenir gérant, on ne vérifie PAS sa limite de jobs salariés ici.
        // La seule contrainte est qu'il ne peut pas être DEJA gérant d'une autre entreprise si max-entreprises-par-gerant = 1, etc.

        // L'ancienne vérification `getNomEntrepriseDuMembre(gerantCible.getName()) != null` était trop restrictive
        // si elle empêchait un salarié de devenir gérant de sa première entreprise.
        // Nous devons nous assurer qu'il ne dépasse pas la limite de GÉRANCE.
        // La limite d'emplois SALARIÉS sera vérifiée lors de l'INVITATION à un poste d'employé.

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
        if (!EntrepriseManager.getEconomy().has(gerantSignataire, demande.cout)) { gerantSignataire.sendMessage(ChatColor.RED + "Fonds insuffisants (" + String.format("%,.2f€", demande.cout) + ")."); Player mOnline = Bukkit.getPlayer(demande.maire.getUniqueId()); if (mOnline != null) mOnline.sendMessage(ChatColor.RED + "Création échouée (fonds " + gerantSignataire.getName() + " insuffisants)."); return; }
        EconomyResponse er = EntrepriseManager.getEconomy().withdrawPlayer(gerantSignataire, demande.cout);
        if (!er.transactionSuccess()) { gerantSignataire.sendMessage(ChatColor.RED + "Erreur paiement: " + er.errorMessage); Player mOnline = Bukkit.getPlayer(demande.maire.getUniqueId()); if (mOnline != null) mOnline.sendMessage(ChatColor.RED + "Erreur paiement par " + gerantSignataire.getName() + "."); return; }
        if (entreprises.containsKey(demande.nomEntreprise)) { gerantSignataire.sendMessage(ChatColor.RED + "Nom '" + demande.nomEntreprise + "' pris. Annulé."); Player mOnline = Bukkit.getPlayer(demande.maire.getUniqueId()); if (mOnline != null) mOnline.sendMessage(ChatColor.RED + "Nom '" + demande.nomEntreprise + "' pris. Annulé."); EntrepriseManager.getEconomy().depositPlayer(gerantSignataire, demande.cout); gerantSignataire.sendMessage(ChatColor.YELLOW + "Frais remboursés."); return; }
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

        // La permission 'entreprisemanager.admin.renameany' permettrait toujours à un admin de renommer
        // une entreprise qui ne lui appartient pas, mais le paiement s'appliquera maintenant à tous.
        // La vérification isGerantProprietaire reste pour s'assurer que le joueur est bien le gérant
        // s'il n'est pas un admin agissant sur n'importe quelle entreprise.
        boolean isAdminAgissantSurAutrui = gerant.hasPermission("entreprisemanager.admin.renameany") && !entreprise.getGerant().equalsIgnoreCase(gerant.getName());
        boolean isGerantProprietaire = entreprise.getGerant().equalsIgnoreCase(gerant.getName());

        if (!isGerantProprietaire && !isAdminAgissantSurAutrui) { // Seul le gérant ou un admin (agissant sur une autre entreprise) peut renommer
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

        // ---- LOGIQUE DE PAIEMENT PAR L'ENTREPRISE (S'APPLIQUE À TOUS SI COÛT > 0) ----
        if (coutRenommage > 0) { // Le paiement s'applique si le coût est positif, quel que soit le statut admin.
            if (entreprise.getSolde() < coutRenommage) {
                gerant.sendMessage(ChatColor.RED + "Le solde de l'entreprise (" + String.format("%,.2f€", entreprise.getSolde()) + ") est insuffisant pour couvrir les frais de renommage (" + String.format("%,.2f€", coutRenommage) + ").");
                gerant.sendMessage(ChatColor.RED + "Veuillez déposer des fonds dans l'entreprise via le menu de gestion ('Déposer Argent') pour pouvoir la renommer."); // Indication ajoutée
                return; // Arrêter le processus si fonds insuffisants
            }
            // Déduire les frais du solde de l'entreprise
            entreprise.setSolde(entreprise.getSolde() - coutRenommage);
            entreprise.addTransaction(new Transaction(TransactionType.RENAME_COST, coutRenommage, "Renommage: " + ancienNom + " -> " + nouveauNom, gerant.getName()));
            gerant.sendMessage(ChatColor.YELLOW + "Frais de renommage (" + String.format("%,.2f€", coutRenommage) + ") déduits du solde de l'entreprise. Nouveau solde : " + String.format("%,.2f€", entreprise.getSolde()) + ".");
        }
        // ---- FIN DE LA LOGIQUE DE PAIEMENT ----

        // Processus de renommage effectif
        entreprises.remove(ancienNom);
        Double caPotentielExistant = activiteHoraireValeur.remove(ancienNom);

        entreprise.setNom(nouveauNom);
        entreprises.put(nouveauNom, entreprise);

        if (caPotentielExistant != null) {
            activiteHoraireValeur.put(nouveauNom, caPotentielExistant);
        } else {
            activiteHoraireValeur.put(nouveauNom, 0.0); // S'assurer qu'une entrée existe pour le nouveau nom
        }
        saveEntreprises();

        String msgConfirm = ChatColor.GREEN + "L'entreprise '" + ancienNom + "' a été renommée en '" + nouveauNom + "'.";
        if (coutRenommage > 0) { // Le message sur les frais s'affiche s'il y avait un coût
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
    public String getTownNameFromPlayer(Player player) { if (plugin.getServer().getPluginManager().getPlugin("Towny") == null) return null; try { Resident resident = TownyAPI.getInstance().getResident(player.getName()); if (resident != null && resident.hasTown()) return resident.getTown().getName(); } catch (NotRegisteredException ignored) {} return null; }
    public String generateSiret() { return UUID.randomUUID().toString().replace("-", "").substring(0, Math.min(plugin.getConfig().getInt("siret.longueur", 14), 32)); }
    public boolean estMaire(Player joueur) { if (plugin.getServer().getPluginManager().getPlugin("Towny") == null) return false; Resident resident = TownyAPI.getInstance().getResident(joueur.getName()); return resident != null && resident.isMayor(); }
    public Set<String> getEmployesDeLEntreprise(String nomEntreprise) { Entreprise entreprise = getEntreprise(nomEntreprise); return (entreprise != null) ? entreprise.getEmployes() : Collections.emptySet(); }
    public void retirerArgent(Player player, String nomEntreprise, double montant) {
        Entreprise entreprise = getEntreprise(nomEntreprise);
        if (entreprise == null) { player.sendMessage(ChatColor.RED + "Ent. '" + nomEntreprise + "' non trouvée."); return; }
        if (!entreprise.getGerant().equalsIgnoreCase(player.getName())) { player.sendMessage(ChatColor.RED + "Seul le gérant peut retirer."); return; }
        if (montant <= 0) { player.sendMessage(ChatColor.RED + "Montant doit être positif."); return; }
        if (entreprise.getSolde() < montant) { player.sendMessage(ChatColor.RED + "Solde ent. (" + String.format("%,.2f€", entreprise.getSolde()) + ") insuffisant."); return; }
        EconomyResponse response = EntrepriseManager.getEconomy().depositPlayer(player, montant);
        if (response.transactionSuccess()) { entreprise.setSolde(entreprise.getSolde() - montant); entreprise.addTransaction(new Transaction(TransactionType.WITHDRAWAL, montant, "Retrait par gérant " + player.getName(), player.getName())); saveEntreprises(); player.sendMessage(ChatColor.GREEN + String.format("%,.2f€", montant) + " retirés de '" + nomEntreprise + "'. Solde: " + String.format("%,.2f€", entreprise.getSolde()) + "."); }
        else { player.sendMessage(ChatColor.RED + "Erreur dépôt compte: " + response.errorMessage); }
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
        if (!EntrepriseManager.getEconomy().has(player, montant)) {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas assez d'argent sur votre compte personnel.");
            return;
        }

        double soldeMaxActuel = getLimiteMaxSoldeActuelle(entreprise);
        if (entreprise.getSolde() + montant > soldeMaxActuel) {
            double montantAutorise = soldeMaxActuel - entreprise.getSolde();
            if (montantAutorise <= 0) {
                player.sendMessage(ChatColor.RED + "L'entreprise a atteint son solde maximum actuel (" + String.format("%,.2f", soldeMaxActuel) + "€).");
                return;
            }
            player.sendMessage(ChatColor.YELLOW + "Le montant a été ajusté pour ne pas dépasser le solde maximum de l'entreprise (" + String.format("%,.2f", soldeMaxActuel) + "€).");
            montant = montantAutorise; // Ajuste le montant au maximum possible
            if (montant <= 0) { // Double check si après ajustement c'est 0 ou moins
                player.sendMessage(ChatColor.RED + "Aucun dépôt possible sans dépasser le solde maximum.");
                return;
            }
        }

        EconomyResponse response = EntrepriseManager.getEconomy().withdrawPlayer(player, montant);
        if (response.transactionSuccess()) {
            entreprise.setSolde(entreprise.getSolde() + montant);
            entreprise.addTransaction(new Transaction(TransactionType.DEPOSIT, montant, "Dépôt par " + player.getName(), player.getName()));
            saveEntreprises();
            player.sendMessage(ChatColor.GREEN + String.format("%,.2f", montant) + "€ déposés dans '" + nomEntreprise + "'. Nouveau solde de l'entreprise : " + String.format("%,.2f", entreprise.getSolde()) + "€.");
        } else {
            player.sendMessage(ChatColor.RED + "Erreur lors du retrait de votre compte : " + response.errorMessage);
        }
    }
    // --- Fin Autres méthodes ---

    // --- Chargement / Sauvegarde (AVEC HISTORIQUE) ---
    private void loadEntreprises() {
        entreprises.clear();
        activiteHoraireValeur.clear();
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
                String ville = currentConfig.getString(path + "ville");
                String type = currentConfig.getString(path + "type");
                String gerantNom = currentConfig.getString(path + "gerantNom");
                String gerantUUIDStr = currentConfig.getString(path + "gerantUUID");
                double solde = currentConfig.getDouble(path + "solde", 0.0);
                String siret = currentConfig.getString(path + "siret", generateSiret());
                double caTotal = currentConfig.getDouble(path + "chiffreAffairesTotal", 0.0);
                double caHorairePotentiel = currentConfig.getDouble(path + "activiteHoraireValeur", 0.0);
                int niveauMaxEmployes = currentConfig.getInt(path + "niveauMaxEmployes", 0); // Charger niveau max employés
                int niveauMaxSolde = currentConfig.getInt(path + "niveauMaxSolde", 0);       // Charger niveau max solde

                if (gerantNom == null || gerantUUIDStr == null || type == null || ville == null) {
                    plugin.getLogger().severe("Données essentielles manquantes pour l'entreprise '" + nomEnt + "'. Elle ne sera pas chargée.");
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
                                primesMap.put(uuidStr, employesSect.getDouble(uuidStr + ".prime", 0.0));
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
                                // Conversion explicite pour éviter les problèmes de type avec getValues(true)
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
                ent.setNiveauMaxEmployes(niveauMaxEmployes); // Définir niveau chargé
                ent.setNiveauMaxSolde(niveauMaxSolde);       // Définir niveau chargé

                entreprises.put(nomEnt, ent);
                activiteHoraireValeur.put(nomEnt, caHorairePotentiel);
                entreprisesChargees++;
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Erreur majeure lors du chargement de l'entreprise '" + nomEnt + "'.", e);
            }
        }
        plugin.getLogger().info(entreprisesChargees + " entreprises chargées depuis entreprise.yml.");
    }
    public void saveEntreprises() {
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
            entreprisesSection.set(path + "activiteHoraireValeur", activiteHoraireValeur.getOrDefault(nomEnt, 0.0));

            // --- AJOUTS CRUCIAUX ---
            entreprisesSection.set(path + "niveauMaxEmployes", ent.getNiveauMaxEmployes()); // Sauvegarder niveau max employés
            entreprisesSection.set(path + "niveauMaxSolde", ent.getNiveauMaxSolde());       // Sauvegarder niveau max solde
            // --- FIN AJOUTS ---

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

        try {
            tempConfig.save(entrepriseFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossible de sauvegarder entreprise.yml", e);
        }
        savePlayerHistory();
    }
    // --- Fin Chargement / Sauvegarde ---

    // --- Reload (AVEC HISTORIQUE) ---
    public void reloadPluginData() { plugin.reloadConfig(); loadEntreprises(); loadPlayerHistory(); planifierTachesHoraires(); planifierVerificationActiviteEmployes(); plugin.getLogger().info("[EntrepriseManager] Données, historique et configuration rechargés."); }
    // --- Fin Reload ---

    // --- Messages Différés ---
    public void ajouterMessageEmployeDifferre(String joueurUUID, String message, String entrepriseNom, double montantPrime) { File messagesFile = new File(plugin.getDataFolder(), "messagesEmployes.yml"); FileConfiguration messagesConfig = YamlConfiguration.loadConfiguration(messagesFile); String listPath = "messages." + joueurUUID + "." + entrepriseNom + ".list"; List<String> messagesActuels = messagesConfig.getStringList(listPath); messagesActuels.add(ChatColor.stripColor(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM HH:mm")) + ": " + message)); messagesConfig.set(listPath, messagesActuels); if (montantPrime > 0) { String totalPrimePath = "messages." + joueurUUID + "." + entrepriseNom + ".totalPrime"; messagesConfig.set(totalPrimePath, messagesConfig.getDouble(totalPrimePath, 0.0) + montantPrime); } try { messagesConfig.save(messagesFile); } catch (IOException e) { plugin.getLogger().severe("Erreur sauvegarde message différé employé " + joueurUUID + ": " + e.getMessage()); } }
    public void envoyerPrimesDifferreesEmployes(Player player) { String playerUUID = player.getUniqueId().toString(); File messagesFile = new File(plugin.getDataFolder(), "messagesEmployes.yml"); if (!messagesFile.exists()) return; FileConfiguration msgsCfg = YamlConfiguration.loadConfiguration(messagesFile); String basePath = "messages." + playerUUID; if (!msgsCfg.contains(basePath)) return; ConfigurationSection entreprisesSect = msgsCfg.getConfigurationSection(basePath); boolean receivedMessage = false; if (entreprisesSect != null) { for (String nomEnt : entreprisesSect.getKeys(false)) { if (entreprisesSect.isConfigurationSection(nomEnt)) { List<String> messages = entreprisesSect.getStringList(nomEnt + ".list"); double totalPrime = entreprisesSect.getDouble(nomEnt + ".totalPrime", 0.0); if (!messages.isEmpty()) { player.sendMessage(ChatColor.GOLD + "--- Primes/Messages de '" + nomEnt + "' (hors-ligne) ---"); messages.forEach(msg -> player.sendMessage(ChatColor.AQUA + "- " + msg)); if (totalPrime > 0) player.sendMessage(ChatColor.GREEN + "Total primes période: " + String.format("%,.2f€", totalPrime)); player.sendMessage(ChatColor.GOLD + "--------------------------------------------------------"); receivedMessage = true; } } } } if (receivedMessage) { msgsCfg.set(basePath, null); try { msgsCfg.save(messagesFile); } catch (IOException e) { plugin.getLogger().severe("Erreur suppression messages différés employé " + playerUUID + ": " + e.getMessage()); } } }
    public void ajouterMessageGerantDifferre(String gerantUUID, String message, String entrepriseNom, double montantConcerne) { File messagesFile = new File(plugin.getDataFolder(), "messagesGerants.yml"); FileConfiguration messagesConfig = YamlConfiguration.loadConfiguration(messagesFile); String listPath = "messages." + gerantUUID + "." + entrepriseNom + ".list"; List<String> messagesActuels = messagesConfig.getStringList(listPath); messagesActuels.add(ChatColor.stripColor(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM HH:mm")) + ": " + message)); messagesConfig.set(listPath, messagesActuels); try { messagesConfig.save(messagesFile); } catch (IOException e) { plugin.getLogger().severe("Erreur sauvegarde message différé gérant " + gerantUUID + ": " + e.getMessage()); } }
    public void envoyerPrimesDifferreesGerants(Player playerGerant) { String gerantUUID = playerGerant.getUniqueId().toString(); File messagesFile = new File(plugin.getDataFolder(), "messagesGerants.yml"); if (!messagesFile.exists()) return; FileConfiguration msgsCfg = YamlConfiguration.loadConfiguration(messagesFile); String basePath = "messages." + gerantUUID; if (!msgsCfg.contains(basePath)) return; ConfigurationSection entreprisesSect = msgsCfg.getConfigurationSection(basePath); boolean receivedMessage = false; if (entreprisesSect != null) { for (String nomEnt : entreprisesSect.getKeys(false)) { if (entreprisesSect.isConfigurationSection(nomEnt)) { List<String> messages = entreprisesSect.getStringList(nomEnt + ".list"); if (!messages.isEmpty()) { playerGerant.sendMessage(ChatColor.BLUE + "--- Notifications Gérance '" + nomEnt + "' (hors-ligne) ---"); messages.forEach(msg -> playerGerant.sendMessage(ChatColor.AQUA + "- " + msg)); playerGerant.sendMessage(ChatColor.BLUE + "----------------------------------------------------------------"); receivedMessage = true; } } } } if (receivedMessage) { msgsCfg.set(basePath, null); try { msgsCfg.save(messagesFile); } catch (IOException e) { plugin.getLogger().severe("Erreur suppression messages différés gérant " + gerantUUID + ": " + e.getMessage()); } } }
    // --- Fin Messages Différés ---

    // --- Getters et Utilitaires ---
    public double getActiviteHoraireValeurPour(String nomEntreprise) { return activiteHoraireValeur.getOrDefault(nomEntreprise, 0.0); }
    public List<Transaction> getTransactionsPourEntreprise(String nomEntreprise, int limit) { Entreprise entreprise = getEntreprise(nomEntreprise); if (entreprise == null) return Collections.emptyList(); List<Transaction> log = new ArrayList<>(entreprise.getTransactionLog()); Collections.reverse(log); return (limit <= 0 || limit >= log.size()) ? log : log.subList(0, limit); }
    public Collection<String> getPlayersInMayorTown(Player mayor) { if (!estMaire(mayor) || plugin.getServer().getPluginManager().getPlugin("Towny") == null) return Collections.emptyList(); try { Town town = TownyAPI.getInstance().getResident(mayor).getTown(); return (town != null) ? town.getResidents().stream().map(Resident::getName).collect(Collectors.toList()) : Collections.emptyList(); } catch (NotRegisteredException e) { return Collections.emptyList(); } }
    public Collection<String> getAllTownsNames() { if (plugin.getServer().getPluginManager().getPlugin("Towny") == null) return Collections.emptyList(); return TownyAPI.getInstance().getTowns().stream().map(Town::getName).collect(Collectors.toList()); }
    public boolean peutCreerEntreprise(Player player) { int max = plugin.getConfig().getInt("finance.max-entreprises-par-gerant", 1); return getEntreprisesGereesPar(player.getName()).size() < max; }
    public Collection<String> getNearbyPlayers(Player centerPlayer, int maxDistance) { return Bukkit.getOnlinePlayers().stream().filter(other -> !other.equals(centerPlayer) && other.getWorld().equals(centerPlayer.getWorld()) && other.getLocation().distanceSquared(centerPlayer.getLocation()) <= (long)maxDistance * maxDistance).map(Player::getName).collect(Collectors.toList()); }
    public void listEntreprises(Player player, String ville) { List<Entreprise> entreprisesDansVille = getEntreprisesByVille(ville); if (entreprisesDansVille.isEmpty()) { player.sendMessage(ChatColor.YELLOW + "Aucune entreprise à " + ville + "."); return; } TextComponent msg = new TextComponent(ChatColor.GOLD + "=== Entreprises à " + ChatColor.AQUA + ville + ChatColor.GOLD + " ===\n"); for (Entreprise e : entreprisesDansVille) { TextComponent ligne = new TextComponent(ChatColor.AQUA + e.getNom() + ChatColor.GRAY + " (Type: " + e.getType() + ", Gérant: " + e.getGerant() + ")"); ligne.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/entreprise info " + e.getNom())); ligne.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Infos '" + e.getNom() + "'").create())); msg.addExtra(ligne); msg.addExtra("\n"); } player.spigot().sendMessage(msg); }
    public Map<Material, Integer> getEmployeeProductionStatsForPeriod(String nomEntreprise, UUID employeeUUID, LocalDateTime start, LocalDateTime end, DetailedActionType actionTypeFilter) { Entreprise ent = getEntreprise(nomEntreprise); if (ent == null) return Collections.emptyMap(); Set<Material> relevant = ent.getTrackedProductionMaterials(); return ent.getEmployeeProductionStatsForPeriod(employeeUUID, start, end, actionTypeFilter, relevant); }
    public Map<Material, Integer> getCompanyProductionStatsForPeriod(String nomEntreprise, LocalDateTime start, LocalDateTime end, DetailedActionType actionTypeFilter) { Entreprise ent = getEntreprise(nomEntreprise); if (ent == null) return Collections.emptyMap(); Set<Material> relevant = ent.getTrackedProductionMaterials(); return ent.getAggregatedProductionStatsForPeriod(start, end, actionTypeFilter, relevant); }
    public boolean canPlayerBreakBlock(Player player, Location location, Material material) { if (plugin.getServer().getPluginManager().getPlugin("Towny") == null) return true; try { return PlayerCacheUtil.getCachePermission(player, location, material, TownyPermission.ActionType.DESTROY); } catch (Exception e) { plugin.getLogger().warning("Erreur Towny (destroy) " + player.getName() + " @ " + location + ": " + e.getMessage()); return false; } }
    public boolean canPlayerPlaceBlock(Player player, Location location, Material material) { if (plugin.getServer().getPluginManager().getPlugin("Towny") == null) return true; try { return PlayerCacheUtil.getCachePermission(player, location, material, TownyPermission.ActionType.BUILD); } catch (Exception e) { plugin.getLogger().warning("Erreur Towny (build) " + player.getName() + " @ " + location + ": " + e.getMessage()); return false; } }
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
        public synchronized void setChiffreAffairesTotal(double ca) { this.chiffreAffairesTotal = ca; }
        public void setPrimes(Map<String, Double> p) { this.primes.clear(); if (p != null) this.primes.putAll(p); }
        public void setTransactionLog(List<Transaction> log) { synchronized(transactionLog) { this.transactionLog.clear(); if (log != null) this.transactionLog.addAll(log); } }
        public void setEmployeeActivityRecords(Map<UUID, EmployeeActivityRecord> r) { this.employeeActivityRecords.clear(); if (r != null) this.employeeActivityRecords.putAll(r); }
        public void setGlobalProductionLog(List<ProductionRecord> log) { synchronized(globalProductionLog) { this.globalProductionLog.clear(); if (log != null) this.globalProductionLog.addAll(log); } }
        public void addTransaction(Transaction tx) { synchronized(transactionLog) { this.transactionLog.add(tx); int maxLogSize = plugin.getConfig().getInt("entreprise.max-transaction-log-size", 200); if(transactionLog.size() > maxLogSize) transactionLog.subList(0, transactionLog.size() - maxLogSize).clear(); } }
        public void addGlobalProductionRecord(LocalDateTime ts, Material m, int q, String employeeUUIDPerformingAction, DetailedActionType actionType) { synchronized(globalProductionLog) { this.globalProductionLog.add(new ProductionRecord(ts, m, q, employeeUUIDPerformingAction, actionType)); } }
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
        @Deprecated
        public Map<Material, Integer> getGlobalProductionStatsForPeriod(LocalDateTime start, LocalDateTime end) { synchronized(globalProductionLog) { Map<Material, Integer> stats = new HashMap<>(); for (ProductionRecord r : globalProductionLog) { if (!r.timestamp.isBefore(start) && r.timestamp.isBefore(end)) { stats.merge(r.material, r.quantity, Integer::sum); } } return stats; } }
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