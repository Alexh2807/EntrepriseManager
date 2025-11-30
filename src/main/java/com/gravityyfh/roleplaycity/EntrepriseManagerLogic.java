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

// Imports des classes du package model
import com.gravityyfh.roleplaycity.entreprise.model.*;

public class EntrepriseManagerLogic {
    // FIX CRITIQUE: Retirer 'static' pour éviter race conditions et problèmes de synchronisation
    // Le plugin reste statique pour être accessible aux classes internes
    public static RoleplayCity plugin;
    private final Map<String, Entreprise> entreprises;
    // FIX PERFORMANCE HAUTE: Index SIRET pour recherche O(1) au lieu de O(n)
    private final Map<String, Entreprise> entreprisesBySiret;

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

    // BossBars temporaires pour afficher le quota aux joueurs hors service
    private final Map<UUID, org.bukkit.boss.BossBar> temporaryQuotaBossBars = new ConcurrentHashMap<>();

    // FIX HAUTE: Système de confirmation pour suppression d'entreprise
    // Map: UUID joueur -> Nom entreprise à supprimer
    private final Map<UUID, String> suppressionsEnAttente = new ConcurrentHashMap<>();

    // SUPPRESSION: WithdrawalRequest et KickRequest extraites vers package model (~30 lignes supprimées)
    private final Map<UUID, WithdrawalRequest> retraitsEnAttente = new ConcurrentHashMap<>();
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

    // SUPPRESSION: RestrictionInfo extraite vers package model


    // SUPPRESSION: Toutes les classes internes extraites vers com.gravityyfh.roleplaycity.entreprise.model
    // Classes supprimées (~120 lignes):
    //   - TransactionType (enum)
    //   - Transaction
    //   - DetailedActionType (enum)
    //   - DetailedProductionRecord (record)
    //   - EmployeeActivityRecord

    // --- Fin Enumérations et Classes Internes (classes extraites vers package model) ---

    public EntrepriseManagerLogic(RoleplayCity plugin) {
        EntrepriseManagerLogic.plugin = plugin;

        // Initialiser les instances statiques des classes model
        Entreprise.setPluginInstance(plugin);
        EmployeeActivityRecord.setPluginInstance(plugin);

        entreprises = new ConcurrentHashMap<>();
        entreprisesBySiret = new ConcurrentHashMap<>();
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
                            ajouterMessageGerantDifferre(entreprise.getGerantUUID(), messageGerant, entreprise.getNom());

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
                            ajouterMessageGerantDifferre(entreprise.getGerantUUID(), messageGerant, entreprise.getNom());
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

    /**
     * FIX MULTI-ENTREPRISES: Retourne TOUTES les entreprises où le joueur est membre (gérant ou employé)
     */
    public java.util.List<String> getNomsEntreprisesDuMembre(String nomJoueur) {
        java.util.List<String> nomsEntreprises = new java.util.ArrayList<>();
        if (nomJoueur == null) return nomsEntreprises;
        for (Entreprise entreprise : entreprises.values()) {
            if (entreprise.getGerant() != null && entreprise.getGerant().equalsIgnoreCase(nomJoueur) ||
                entreprise.getEmployes().contains(nomJoueur)) {
                nomsEntreprises.add(entreprise.getNom());
            }
        }
        return nomsEntreprises;
    }

    /**
     * FIX MULTI-ENTREPRISES: Retourne TOUTES les entreprises où le joueur est membre
     */
    public java.util.List<Entreprise> getEntreprisesDuJoueur(Player player) {
        java.util.List<Entreprise> listeEntreprises = new java.util.ArrayList<>();
        if (player == null) return listeEntreprises;
        java.util.List<String> noms = getNomsEntreprisesDuMembre(player.getName());
        for (String nom : noms) {
            Entreprise ent = getEntreprise(nom);
            if (ent != null) {
                listeEntreprises.add(ent);
            }
        }
        return listeEntreprises;
    }
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

    /**
     * Récupère le nombre total d'actions effectuées par un joueur pour un type d'entreprise
     * @param playerUUID UUID du joueur
     * @param typeEntreprise Type de l'entreprise (ex: "Minage", "Styliste")
     * @return Nombre total d'actions effectuées
     */
    public int getQuotaUtilisePourEntreprise(UUID playerUUID, String typeEntreprise) {
        Map<String, ActionInfo> actions = joueurActivitesRestrictions.get(playerUUID);
        if (actions == null) {
            return 0;
        }

        // Quota global: l'actionId est simplement le nom du type d'entreprise
        ActionInfo info = actions.get(typeEntreprise);
        return info != null ? info.getNombreActions() : 0;
    }
    // --- Fin Tâches Horaires ---

    public void enregistrerActionProductive(Player player, String actionTypeString, Material material, int quantite, Block block) {
        if (player == null || material == null || quantite <= 0) {
            plugin.getLogger().warning("Tentative d'enregistrement d'action productive avec des paramètres nuls ou invalides (Joueur: " + player + ", Material: " + material + ", Quantité: " + quantite + ")");
            return;
        }

        // FIX: Si en service, utiliser l'entreprise du service actif (prioritaire)
        Entreprise entreprise = null;
        if (plugin.getServiceModeManager() != null && plugin.getServiceModeManager().isInService(player.getUniqueId())) {
            String serviceEntName = plugin.getServiceModeManager().getActiveEnterprise(player.getUniqueId());
            if (serviceEntName != null) {
                entreprise = getEntreprise(serviceEntName);
            }
        }

        // Fallback: chercher une entreprise du joueur (hors service)
        if (entreprise == null) {
            entreprise = getEntrepriseDuJoueur(player);
        }

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
                if (!canPlayerBreakBlock(player, block.getLocation())) {
                    plugin.getLogger().fine("Action BLOCK_BREAK sur ("+ material.name() +") annulée par protection de Plot pour " + player.getName());
                    return;
                }
            }
        } else if (actionTypeString.equalsIgnoreCase("BLOCK_PLACE")) {
            if (block == null) {
                plugin.getLogger().warning("BLOCK_PLACE enregistré sans référence de bloc pour " + player.getName() + " sur " + material.name());
            } else {
                if (!canPlayerPlaceBlock(player, block.getLocation())) {
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
            // Vérifier si le joueur est en mode service
            boolean isInService = plugin.getServiceModeManager() != null &&
                                 plugin.getServiceModeManager().isInService(player.getUniqueId());

            if (isInService) {
                // Mode service: split 50/50 (joueur accumule 50%, entreprise gagne 50%)
                double companyShare = plugin.getServiceModeManager().processServiceEarnings(player, valeurTotaleAction);
                activiteProductiveHoraireValeur.merge(entreprise.getNom(), companyShare, Double::sum);
                plugin.getLogger().fine("CA Productif Horaire (MODE SERVICE) pour '" + entreprise.getNom() +
                    "' augmenté de " + companyShare + "€ (50% de " + valeurTotaleAction + "€)");
            } else {
                // Hors service: AUCUN gain (le joueur ne travaille pas pour l'entreprise)
                plugin.getLogger().fine("Joueur " + player.getName() + " hors service - Aucun gain enregistré pour '" + entreprise.getNom() + "'");
            }
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

        // FIX: Si en service, utiliser l'entreprise du service actif (prioritaire)
        Entreprise entreprise = null;
        if (plugin.getServiceModeManager() != null && plugin.getServiceModeManager().isInService(player.getUniqueId())) {
            String serviceEntName = plugin.getServiceModeManager().getActiveEnterprise(player.getUniqueId());
            if (serviceEntName != null) {
                entreprise = getEntreprise(serviceEntName);
            }
        }

        // Fallback: chercher une entreprise du joueur (hors service)
        if (entreprise == null) {
            entreprise = getEntrepriseDuJoueur(player);
        }

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
            // Vérifier si le joueur est en mode service
            boolean isInService = plugin.getServiceModeManager() != null &&
                                 plugin.getServiceModeManager().isInService(player.getUniqueId());

            if (isInService) {
                // Mode service: split 50/50 (joueur accumule 50%, entreprise gagne 50%)
                double companyShare = plugin.getServiceModeManager().processServiceEarnings(player, valeurTotaleAction);
                activiteProductiveHoraireValeur.merge(entreprise.getNom(), companyShare, Double::sum);
                plugin.getLogger().fine("CA Productif Horaire (MODE SERVICE) pour '" + entreprise.getNom() +
                    "' augmenté de " + companyShare + "€ (50% de " + valeurTotaleAction + "€)");
            } else {
                // Hors service: AUCUN gain (le joueur ne travaille pas pour l'entreprise)
                plugin.getLogger().fine("Joueur " + player.getName() + " hors service - Aucun gain enregistré pour '" + entreprise.getNom() + "' (Action: " + actionType + ", Cible: " + entityTypeName + ")");
            }
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
    /**
     * Enregistre le craft d'un backpack (version avec quantité par défaut = 1)
     */
    public void enregistrerCraftBackpack(Player player, String backpackType) {
        enregistrerCraftBackpack(player, backpackType, 1);
    }

    /**
     * Enregistre le craft de backpacks avec une quantité spécifique
     */
    public void enregistrerCraftBackpack(Player player, String backpackType, int quantite) {
        if (player == null || backpackType == null || quantite <= 0) {
            plugin.getLogger().warning("enregistrerCraftBackpack appelé avec des paramètres nuls ou invalides");
            return;
        }

        // FIX: Si en service, utiliser l'entreprise du service actif (prioritaire)
        Entreprise entreprise = null;
        if (plugin.getServiceModeManager() != null && plugin.getServiceModeManager().isInService(player.getUniqueId())) {
            String serviceEntName = plugin.getServiceModeManager().getActiveEnterprise(player.getUniqueId());
            if (serviceEntName != null) {
                entreprise = getEntreprise(serviceEntName);
            }
        }

        // Fallback: chercher une entreprise du joueur (hors service)
        if (entreprise == null) {
            entreprise = getEntrepriseDuJoueur(player);
        }

        if (entreprise == null) {
            return; // Pas d'entreprise, pas d'enregistrement
        }

        // Récupérer la valeur unitaire du backpack depuis la config
        String typeEntreprise = entreprise.getType();
        String configPath = "types-entreprise." + typeEntreprise + ".activites-payantes.CRAFT_BACKPACK." + backpackType;
        double valeurUnitaire = plugin.getConfig().getDouble(configPath, 0.0);
        double valeurTotale = valeurUnitaire * quantite;

        if (valeurUnitaire > 0) {
            // Enregistrer l'activité dans le record de l'employé
            EmployeeActivityRecord activityRecord = entreprise.getOrCreateEmployeeActivityRecord(
                player.getUniqueId(),
                player.getName()
            );

            String actionKey = "CRAFT_BACKPACK:" + backpackType;

            // Utiliser LEATHER comme Material de référence pour les backpacks dans les logs
            Material backpackMaterial = Material.LEATHER;

            // Enregistrer l'action détaillée avec la quantité correcte
            activityRecord.recordAction(
                actionKey,
                valeurTotale,
                quantite,
                DetailedActionType.ITEM_CRAFTED,
                backpackMaterial
            );

            // Ajouter au chiffre d'affaires productif horaire
            // Vérifier si le joueur est en mode service
            boolean isInService = plugin.getServiceModeManager() != null &&
                                 plugin.getServiceModeManager().isInService(player.getUniqueId());

            if (isInService) {
                // Mode service: split 50/50 (joueur accumule 50%, entreprise gagne 50%)
                double companyShare = plugin.getServiceModeManager().processServiceEarnings(player, valeurTotale);
                activiteProductiveHoraireValeur.merge(entreprise.getNom(), companyShare, Double::sum);

                // Ajouter au log global de production de l'entreprise
                entreprise.addGlobalProductionRecord(
                    LocalDateTime.now(),
                    backpackMaterial,
                    quantite,
                    player.getUniqueId().toString(),
                    DetailedActionType.ITEM_CRAFTED
                );

                plugin.getLogger().fine("Craft de " + quantite + "x backpack '" + backpackType + "' enregistré pour " + player.getName() +
                        " (MODE SERVICE - Entreprise: " + entreprise.getNom() + ", Type: " + typeEntreprise + ", Valeur totale: " + valeurTotale + "€)");
            } else {
                // Hors service: AUCUN gain (le joueur ne travaille pas pour l'entreprise)
                plugin.getLogger().fine("Joueur " + player.getName() + " hors service - Backpack '" + backpackType + "' x" + quantite + " non enregistré pour '" + entreprise.getNom() + "'");
            }
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

        // Créer une liste incluant les employés ET le gérant
        Set<String> membresAPayer = new HashSet<>(entreprise.getEmployes());
        membresAPayer.add(entreprise.getGerant()); // Ajouter le gérant

        for (String employeNom : membresAPayer) {
            OfflinePlayer employeOffline = Bukkit.getOfflinePlayer(employeNom);
            UUID employeUUID;
            try {
                if(employeOffline != null) employeUUID = employeOffline.getUniqueId(); else continue;
            } catch (Exception ignored) { continue; }

            double primeConfigurée = entreprise.getPrimePourEmploye(employeUUID.toString());

            // Récupérer les gains accumulés en mode service
            double gainsService = 0.0;
            if (plugin.getServiceModeManager() != null) {
                gainsService = plugin.getServiceModeManager().getAndResetAccumulatedEarnings(employeUUID);
            }

            // Ne payer que si actif OU s'il y a des gains service à payer
            EmployeeActivityRecord activity = entreprise.getEmployeeActivityRecord(employeUUID);
            boolean hasActivity = (activity != null && activity.isActive());
            boolean hasServiceEarnings = gainsService > 0;

            // Payer les gains service (générés par le système, PAS par l'entreprise)
            if (hasServiceEarnings) {
                plugin.getLogger().fine("[Vault] DEPOSIT (Gains Service): " + employeNom + " ← " + String.format("%.2f€", gainsService) +
                    " (Entreprise: " + entreprise.getNom() + ")");

                EconomyResponse erService = RoleplayCity.getEconomy().depositPlayer(employeOffline, gainsService);

                if (erService.transactionSuccess()) {
                    plugin.getLogger().fine("[Vault] DEPOSIT SUCCESS (Gains Service): " + employeNom + " ← " + String.format("%.2f€", gainsService));

                    // NE PAS déduire du solde de l'entreprise!
                    // Les gains service sont générés par le système

                    Player onlineEmp = employeOffline.getPlayer();
                    if (onlineEmp != null) {
                        onlineEmp.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            "&aPaiement horaire - Gains service: &e+" + String.format("%.2f€", gainsService)));
                    } else {
                        ajouterMessageEmployeDifferre(employeUUID.toString(),
                            ChatColor.translateAlternateColorCodes('&', "&aPaiement horaire - Gains service: &e+" + String.format("%.2f€", gainsService)),
                            entreprise.getNom(), gainsService);
                    }
                } else {
                    plugin.getLogger().severe("[Vault] DEPOSIT FAILED (Gains Service): " + employeNom + " ← " + gainsService + "€ - Reason: " + erService.errorMessage);
                }
            }

            // Payer la prime (payée par l'entreprise)
            if (!hasActivity || primeConfigurée <= 0) continue;

            if (entreprise.getSolde() >= primeConfigurée) {
                // FIX MOYENNE: Log détaillé transaction Vault
                plugin.getLogger().fine("[Vault] DEPOSIT (Prime): " + employeNom + " ← " + String.format("%.2f€", primeConfigurée) +
                    " (Entreprise: " + entreprise.getNom() + ")");

                EconomyResponse er = RoleplayCity.getEconomy().depositPlayer(employeOffline, primeConfigurée);

                if (er.transactionSuccess()) {
                    plugin.getLogger().fine("[Vault] DEPOSIT SUCCESS (Prime): " + employeNom + " ← " + String.format("%.2f€", primeConfigurée));
                    entreprise.setSolde(entreprise.getSolde() - primeConfigurée); // Seulement la prime est déduite!

                    // Enregistrer la transaction de prime
                    entreprise.addTransaction(new Transaction(TransactionType.PRIMES, primeConfigurée, "Prime horaire: " + employeNom, "System"));

                    totalPrimesPayees += primeConfigurée;

                    // Message pour la prime
                    String msgPrime = String.format("&aPrime horaire reçue: &e+%.2f€&a de '&6%s&a'.", primeConfigurée, entreprise.getNom());
                    Player onlineEmp = employeOffline.getPlayer();
                    if (onlineEmp != null) {
                        onlineEmp.sendMessage(ChatColor.translateAlternateColorCodes('&', msgPrime));
                    } else {
                        ajouterMessageEmployeDifferre(employeUUID.toString(),
                            ChatColor.translateAlternateColorCodes('&', msgPrime),
                            entreprise.getNom(), primeConfigurée);
                    }

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
        report.add(ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        report.add(" " + ChatColor.GOLD + ChatColor.BOLD + "Entreprise : " + ChatColor.YELLOW + entreprise.getNom() + ChatColor.GRAY + " (" + typeEntreprise + ")");
        report.add(" " + ChatColor.GOLD + "Rapport Financier Horaire");
        report.add("");
        report.add(ChatColor.AQUA + "  Chiffre d'Affaires Brut");
        report.add(String.format(ChatColor.GRAY + "    - Revenus d'activité : %s+%,.2f €", ChatColor.GREEN, revActivite));
        report.add(String.format(ChatColor.GRAY + "    - Revenus des boutiques : %s+%,.2f €", ChatColor.GREEN, revMagasins));
        report.add(ChatColor.DARK_AQUA + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        report.add(String.format(ChatColor.AQUA + "      Total Brut : %s+%,.2f €", ChatColor.GREEN, caBrut));
        report.add("");
        report.add(ChatColor.RED + "  Dépenses Opérationnelles");
        report.add(String.format(ChatColor.GRAY + "    - Impôts sur le CA (%.0f%%) : %s-%,.2f €", taxRate, ChatColor.RED, taxes));
        report.add(String.format(ChatColor.GRAY + "    - Primes versées : %s-%,.2f €", ChatColor.RED, primes));
        report.add(String.format(ChatColor.GRAY + "    - Charges salariales : %s-%,.2f €", ChatColor.RED, charges));
        report.add(ChatColor.DARK_RED + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        report.add(String.format(ChatColor.RED + "      Total Dépenses : %s-%,.2f €", ChatColor.RED, totalDepenses));
        report.add("");

        String beneficePrefix = benefice >= 0 ? ChatColor.GREEN + "+" : ChatColor.RED + "";
        report.add(String.format(ChatColor.BOLD + "  Bénéfice/Perte Horaire : %s%,.2f €", beneficePrefix, benefice));
        report.add("");
        report.add(ChatColor.DARK_AQUA + "  Solde de l'entreprise :");
        report.add(String.format(ChatColor.GRAY + "    Ancien solde : " + ChatColor.WHITE + "%,.2f €", ancienSolde));
        report.add(String.format(ChatColor.GRAY + "    Nouveau solde : " + ChatColor.WHITE + ChatColor.BOLD + "%,.2f €", nouveauSolde));

        report.add(ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        Player gerantPlayer = Bukkit.getPlayerExact(entreprise.getGerant());
        String fullReportString = String.join("\n", report);

        if (gerantPlayer != null && gerantPlayer.isOnline()) {
            gerantPlayer.sendMessage(fullReportString);
        } else {
            OfflinePlayer offlineGerant = Bukkit.getOfflinePlayer(UUID.fromString(entreprise.getGerantUUID()));
            if(offlineGerant.hasPlayedBefore() || offlineGerant.isOnline()){
                String resume = String.format("Rapport Horaire '%s': Bénéfice/Perte de %.2f€. Nouveau solde: %.2f€.", entreprise.getNom(), benefice, nouveauSolde);
                ajouterMessageGerantDifferre(entreprise.getGerantUUID(), ChatColor.GREEN + resume, entreprise.getNom());
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
     * NOUVEAU: Support du mode service et des niveaux de restrictions d'entreprise
     * FIX MULTI-ENTREPRISES: Vérifie TOUTES les entreprises du joueur
     */
    public boolean verifierEtGererRestrictionAction(Player player, String actionTypeString, String targetName, int quantite) {
        // 1. Get ALL enterprises of the player first (needed for error messages)
        java.util.List<Entreprise> playerEnterprises = getEntreprisesDuJoueur(player);

        // 2. Check Service Mode first (Override)
        // If in service, ONLY check the service enterprise
        if (plugin.getServiceModeManager() != null && plugin.getServiceModeManager().isInService(player.getUniqueId())) {
            String serviceEntName = plugin.getServiceModeManager().getActiveEnterprise(player.getUniqueId());
            Entreprise serviceEnt = getEntreprise(serviceEntName);
            if (serviceEnt != null) {
                return checkRestrictionForEnterprise(player, serviceEnt, actionTypeString, targetName, quantite, true, playerEnterprises);
            }
        }

        // DEBUG: Log les entreprises du joueur si activé dans la config
        if (plugin.getConfig().getBoolean("debug.restrictions", false)) {
            plugin.getLogger().info("[DEBUG Restrictions] Joueur " + player.getName() +
                                   " possède " + playerEnterprises.size() + " entreprise(s) pour l'action " + actionTypeString);
            for (Entreprise ent : playerEnterprises) {
                plugin.getLogger().info("[DEBUG Restrictions]   - " + ent.getNom() +
                                       " (Type: " + ent.getType() + ", Gérant: " + ent.getGerant() + ")");
            }
        }

        // 3. If no enterprise, check against "non-member" limits (default strict behavior)
        if (playerEnterprises.isEmpty()) {
             return checkRestrictionForEnterprise(player, null, actionTypeString, targetName, quantite, false, playerEnterprises);
        }

        // 4. Iterate and try to find ONE that passes
        for (Entreprise ent : playerEnterprises) {
            // Check if ALLOWED (returns false if NOT blocked)
            // We silence messages during iteration to avoid spam, unless it's the last one?
            // Actually checkRestrictionForEnterprise sends messages on failure.
            // For now, we accept spam if multiple fail, but user stops at first success.
            if (!checkRestrictionForEnterprise(player, ent, actionTypeString, targetName, quantite, false, playerEnterprises)) {
                return false; // Allowed by this enterprise!
            }
        }
        
        return true; // Blocked by all
    }

    /**
     * Helper method to check restriction for a specific enterprise.
     * Returns true if BLOCKED, false if ALLOWED.
     * @param allPlayerEnterprises List of all player's enterprises (for display in error messages)
     */
    private boolean checkRestrictionForEnterprise(Player player, Entreprise entreprise, String actionTypeString, String targetName, int quantite, boolean isInService, List<Entreprise> allPlayerEnterprises) {
        // FIX MOYENNE: Check cache expiry
        long currentTime = System.currentTimeMillis();
        if (currentTime - restrictionsCacheLoadTime > RESTRICTIONS_CACHE_TTL_MS) {
            loadRestrictionsCache();
        }

        String cacheKey = actionTypeString.toUpperCase() + ":" + targetName.toUpperCase();
        List<RestrictionInfo> restrictions = restrictionsCache.get(cacheKey);

        if (restrictions == null || restrictions.isEmpty()) {
            return false; // No restriction
        }

        RestrictionInfo restrictionApplicable = null;
        
        if (entreprise != null) {
            for (RestrictionInfo restriction : restrictions) {
                if (restriction.entrepriseType().equals(entreprise.getType())) {
                    restrictionApplicable = restriction;
                    break;
                }
            }
        }
        
        if (restrictionApplicable == null) {
            if (!restrictions.isEmpty()) {
                restrictionApplicable = restrictions.get(0);
            }
        }
        
        if (restrictionApplicable == null) return false;

        // Determine limit
        int limiteApplicable;
        if (isInService && entreprise != null && entreprise.getType().equals(restrictionApplicable.entrepriseType())) {
            // En service : Utiliser strictement la limite configurée pour le niveau de l'entreprise
            limiteApplicable = getLimiteRestrictionActuelle(entreprise, restrictionApplicable.entrepriseType());
        } else {
             // Hors service ou entreprise différente = limite civile (non-membre)
             limiteApplicable = restrictionApplicable.limiteNonMembre();
        }

        if (limiteApplicable == -1) return false; // Unlimited

        if (limiteApplicable == 0) {
            // Send message to inform user that action is totally blocked
            // This applies both in service mode and normal mode
            // FIX: Pass ALL player enterprises, not just the current one
            List<String> playerEnterpriseNames = new ArrayList<>();
            if (allPlayerEnterprises != null && !allPlayerEnterprises.isEmpty()) {
                for (Entreprise ent : allPlayerEnterprises) {
                    playerEnterpriseNames.add(ent.getNom() + " (" + ent.getType() + ")");
                }
            } else {
                playerEnterpriseNames.add("Aucune");
            }
            com.gravityyfh.roleplaycity.util.RestrictionMessageBuilder.sendTotallyBlocked(
                player, restrictionApplicable.entrepriseType(),
                playerEnterpriseNames
            );
            return true;
        }

        // Quota check
        String actionIdPourRestriction = restrictionApplicable.entrepriseType();
        
        ActionInfo info = joueurActivitesRestrictions
            .computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
            .computeIfAbsent(actionIdPourRestriction, k -> new ActionInfo());

        synchronized(info) {
            if (info.getNombreActions() + quantite > limiteApplicable) {
                // Quota reached - always send message to inform user
                com.gravityyfh.roleplaycity.util.RestrictionMessageBuilder.sendContextualMessage(
                    player,
                    entreprise != null ? entreprise.getType() : null,
                    entreprise != null ? entreprise.getNom() : null,
                    isInService,
                    restrictionApplicable.entrepriseType(),
                    info.getNombreActions(),
                    limiteApplicable,
                    actionTypeString,
                    Collections.emptyList()
                );
                return true; // Blocked
            } else {
                info.incrementerActions(quantite);

                if (!isInService && limiteApplicable > 0) {
                    showTemporaryQuotaBossBar(player, restrictionApplicable.entrepriseType(), info.getNombreActions(), limiteApplicable);
                }
                return false; // Allowed and Consumed
            }
        }
    }

    /**
     * Nettoie la BossBar temporaire d'un joueur (appelé à la déconnexion)
     */
    public void cleanupTemporaryQuotaBossBar(UUID playerUUID) {
        org.bukkit.boss.BossBar bar = temporaryQuotaBossBars.remove(playerUUID);
        if (bar != null) {
            bar.removeAll();
        }
    }

    /**
     * Affiche une BossBar temporaire (5 secondes) montrant le quota utilisé
     * pour les joueurs qui ne sont pas en mode service
     */
    private void showTemporaryQuotaBossBar(Player player, String typeEntreprise, int quotaUtilise, int quotaLimite) {
        UUID uuid = player.getUniqueId();

        // Retirer l'ancienne BossBar si elle existe
        org.bukkit.boss.BossBar oldBar = temporaryQuotaBossBars.remove(uuid);
        if (oldBar != null) {
            oldBar.removePlayer(player);
        }

        // Calculer le pourcentage
        double percentage = (double) quotaUtilise / quotaLimite;
        int percentageInt = (int) (percentage * 100);

        // Choisir la couleur selon le %
        org.bukkit.boss.BarColor color;
        if (percentage >= 0.81) {
            color = org.bukkit.boss.BarColor.RED;
        } else if (percentage >= 0.51) {
            color = org.bukkit.boss.BarColor.YELLOW;
        } else {
            color = org.bukkit.boss.BarColor.GREEN;
        }

        // Créer la BossBar
        String title = ChatColor.GRAY + "⚠ Quota Non-Membre " + ChatColor.WHITE + "| " +
                      ChatColor.GOLD + typeEntreprise + ChatColor.WHITE + " | " +
                      ChatColor.GRAY + "Quota: " + ChatColor.WHITE + quotaUtilise + "/" + quotaLimite +
                      ChatColor.GRAY + " (" + percentageInt + "%)";

        org.bukkit.boss.BossBar bossBar = Bukkit.createBossBar(
            title,
            color,
            org.bukkit.boss.BarStyle.SOLID
        );
        bossBar.addPlayer(player);
        bossBar.setProgress(Math.min(1.0, percentage));

        temporaryQuotaBossBars.put(uuid, bossBar);

        // Supprimer la BossBar après 5 secondes
        new BukkitRunnable() {
            @Override
            public void run() {
                org.bukkit.boss.BossBar bar = temporaryQuotaBossBars.remove(uuid);
                if (bar != null) {
                    bar.removePlayer(player);
                }
            }
        }.runTaskLater(plugin, 100L); // 5 secondes (100 ticks)
    }

    public LocalDateTime getNextPaymentTime() {
        return nextPaymentTime;
    }
    // --- Accès Entreprises ---
    public Entreprise getEntreprise(String nomEntreprise) { return entreprises.get(nomEntreprise); }
    public Collection<Entreprise> getEntreprises() { return Collections.unmodifiableCollection(entreprises.values()); }

    // PHASE 6.5: Accès direct aux maps pour injection depuis SQLite
    protected Map<String, Entreprise> getEntreprisesMap() { return entreprises; }
    protected Map<String, Entreprise> getEntreprisesBySiretMap() { return entreprisesBySiret; }

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
        if (plugin.getServiceModeManager() != null) {
            plugin.getServiceModeManager().forceStopAllForCompany(nomEntreprise);
        }
        entreprise.getEmployeeActivityRecords().values().forEach(EmployeeActivityRecord::endSession);

        // Suppression SQL explicite (CRITIQUE pour la persistance)
        if (plugin.isUsingSQLiteServices() && plugin.getAsyncEntrepriseService() != null) {
            plugin.getAsyncEntrepriseService().deleteEntreprise(entreprise.getSiret());
        }

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
                    // Ne pas afficher le message si l'entreprise n'existe plus (déjà supprimée)
                    if (getEntreprise(nomEntreprise) != null && initiator.isOnline()) {
                        initiator.sendMessage(ChatColor.YELLOW + "Demande de suppression de '" + nomEntreprise + "' annulée (timeout).");
                    }
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
        TextComponent msg = new TextComponent("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"); msg.setColor(net.md_5.bungee.api.ChatColor.GOLD);
        TextComponent invite = new TextComponent(nomGerant + " (Gérant '" + nomEntreprise + "' - " + typeEntreprise + ") vous invite !"); invite.setColor(net.md_5.bungee.api.ChatColor.YELLOW);
        msg.addExtra(invite); msg.addExtra("\n");
        TextComponent accepter = new TextComponent("[ACCEPTER]"); accepter.setColor(net.md_5.bungee.api.ChatColor.GREEN); accepter.setBold(true); accepter.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/entreprise accepter")); accepter.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Rejoindre").create()));
        TextComponent refuser = new TextComponent("   [REFUSER]"); refuser.setColor(net.md_5.bungee.api.ChatColor.RED); refuser.setBold(true); refuser.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/entreprise refuser")); refuser.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Refuser").create()));
        TextComponent actions = new TextComponent("        "); actions.addExtra(accepter); actions.addExtra(refuser);
        msg.addExtra(actions); msg.addExtra("\n▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
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
            if (plugin.getServiceModeManager() != null) {
                plugin.getServiceModeManager().forceStopService(employeUUID);
            }
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
            if (plugin.getServiceModeManager() != null) {
                plugin.getServiceModeManager().forceStopService(joueur.getUniqueId());
            }
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

        if (!validation.valid()) {
            player.sendMessage(ChatColor.RED + "❌ " + validation.error());
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
        maire.sendMessage(ChatColor.GREEN + "Proposition de création d'entreprise envoyée à " + gerantCible.getName() + " (Type: " + type + ", Nom: " + nomEntreprisePropose + "). Délai: " + (demande.expirationTimeMillis() - System.currentTimeMillis())/1000 + "s.");
        envoyerInvitationVisuelleContrat(gerantCible, demande);
        plugin.getLogger().log(Level.INFO, "[DEBUG CREATION] Proposition envoyée avec succès pour " + nomEntreprisePropose + " à " + gerantCible.getName());
    }
    private void envoyerInvitationVisuelleContrat(Player gerantCible, DemandeCreation demande) {
        gerantCible.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬ Contrat de Gérance ▬▬▬▬▬▬▬");
        gerantCible.sendMessage(ChatColor.AQUA + "Maire: " + ChatColor.WHITE + demande.maire().getName()); gerantCible.sendMessage(ChatColor.AQUA + "Ville: " + ChatColor.WHITE + demande.ville());
        gerantCible.sendMessage(ChatColor.AQUA + "Type: " + ChatColor.WHITE + demande.type()); gerantCible.sendMessage(ChatColor.AQUA + "Nom: " + ChatColor.WHITE + demande.nomEntreprise());
        gerantCible.sendMessage(ChatColor.AQUA + "SIRET: " + ChatColor.WHITE + demande.siret()); gerantCible.sendMessage(ChatColor.YELLOW + "Coût: " + ChatColor.GREEN + String.format("%,.2f€", demande.cout()));
        long remainingSeconds = (demande.expirationTimeMillis() - System.currentTimeMillis()) / 1000; gerantCible.sendMessage(ChatColor.YELLOW + "Délai: " + remainingSeconds + "s.");
        TextComponent accepterMsg = new TextComponent("        [VALIDER CONTRAT]"); accepterMsg.setColor(net.md_5.bungee.api.ChatColor.GREEN); accepterMsg.setBold(true); accepterMsg.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/entreprise validercreation")); accepterMsg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Accepter (coût: " + String.format("%,.2f€", demande.cout()) + ")").create()));
        TextComponent refuserMsg = new TextComponent("   [REFUSER CONTRAT]"); refuserMsg.setColor(net.md_5.bungee.api.ChatColor.RED); refuserMsg.setBold(true); refuserMsg.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/entreprise annulercreation")); refuserMsg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Refuser").create()));
        TextComponent ligneActions = new TextComponent(""); ligneActions.addExtra(accepterMsg); ligneActions.addExtra(refuserMsg);
        gerantCible.spigot().sendMessage(ligneActions); gerantCible.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        UUID gerantUUID = gerantCible.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (demandesEnAttente.containsKey(gerantUUID) && demandesEnAttente.get(gerantUUID).equals(demande)) { demandesEnAttente.remove(gerantUUID); Player gOnline = Bukkit.getPlayer(gerantUUID); if (gOnline != null) gOnline.sendMessage(ChatColor.RED + "Offre pour '" + demande.nomEntreprise() + "' expirée."); Player mOnline = Bukkit.getPlayer(demande.maire().getUniqueId()); if (mOnline != null) mOnline.sendMessage(ChatColor.RED + "Offre pour '" + demande.nomEntreprise() + "' (à " + demande.gerant().getName() + ") expirée."); } }, (demande.expirationTimeMillis() - System.currentTimeMillis()) / 50);
    }
    public void validerCreationEntreprise(Player gerantSignataire) {
        DemandeCreation demande = demandesEnAttente.remove(gerantSignataire.getUniqueId());
        if (demande == null) { gerantSignataire.sendMessage(ChatColor.RED + "Aucune demande."); return; }
        if (demande.isExpired()) { gerantSignataire.sendMessage(ChatColor.RED + "Demande expirée."); Player mOnline = Bukkit.getPlayer(demande.maire().getUniqueId()); if (mOnline != null) mOnline.sendMessage(ChatColor.RED + "Demande pour '" + demande.nomEntreprise() + "' (à " + gerantSignataire.getName() + ") expirée."); return; }
        if (!RoleplayCity.getEconomy().has(gerantSignataire, demande.cout())) { gerantSignataire.sendMessage(ChatColor.RED + "Fonds insuffisants (" + String.format("%,.2f€", demande.cout()) + ")."); Player mOnline = Bukkit.getPlayer(demande.maire().getUniqueId()); if (mOnline != null) mOnline.sendMessage(ChatColor.RED + "Création échouée (fonds " + gerantSignataire.getName() + " insuffisants)."); return; }
        EconomyResponse er = RoleplayCity.getEconomy().withdrawPlayer(gerantSignataire, demande.cout());
        if (!er.transactionSuccess()) { gerantSignataire.sendMessage(ChatColor.RED + "Erreur paiement: " + er.errorMessage); Player mOnline = Bukkit.getPlayer(demande.maire().getUniqueId()); if (mOnline != null) mOnline.sendMessage(ChatColor.RED + "Erreur paiement par " + gerantSignataire.getName() + "."); return; }
        if (entreprises.containsKey(demande.nomEntreprise())) { gerantSignataire.sendMessage(ChatColor.RED + "Nom '" + demande.nomEntreprise() + "' pris. Annulé."); Player mOnline = Bukkit.getPlayer(demande.maire().getUniqueId()); if (mOnline != null) mOnline.sendMessage(ChatColor.RED + "Nom '" + demande.nomEntreprise() + "' pris. Annulé."); RoleplayCity.getEconomy().depositPlayer(gerantSignataire, demande.cout()); gerantSignataire.sendMessage(ChatColor.YELLOW + "Frais remboursés."); return; }
        declareEntreprise(demande.maire(), demande.ville(), demande.nomEntreprise(), demande.type(), gerantSignataire, demande.siret(), demande.cout());
        gerantSignataire.sendMessage(ChatColor.GREEN + "Contrat accepté! Frais (" + String.format("%,.2f€", demande.cout()) + ") payés. '" + demande.nomEntreprise() + "' créée !");
        Player mOnline = Bukkit.getPlayer(demande.maire().getUniqueId()); if (mOnline != null) mOnline.sendMessage(ChatColor.GREEN + gerantSignataire.getName() + " a validé. '" + demande.nomEntreprise() + "' créée.");
    }
    public void refuserCreationEntreprise(Player gerantSignataire) {
        DemandeCreation demande = demandesEnAttente.remove(gerantSignataire.getUniqueId());
        if (demande == null) { gerantSignataire.sendMessage(ChatColor.RED + "Aucune demande."); return; }
        gerantSignataire.sendMessage(ChatColor.YELLOW + "Contrat pour '" + demande.nomEntreprise() + "' refusé.");
        Player maireOnline = Bukkit.getPlayer(demande.maire().getUniqueId()); if (maireOnline != null) maireOnline.sendMessage(ChatColor.RED + gerantSignataire.getName() + " a refusé le contrat.");
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

        if (plugin.isUsingSQLiteServices() && plugin.getAsyncEntrepriseService() != null) {
            plugin.getLogger().info("[EntrepriseManager] Chargement des entreprises depuis SQLite...");
            try {
                int count = plugin.getAsyncEntrepriseService().loadAllToCache().join();
                
                Collection<Entreprise> loaded = plugin.getAsyncEntrepriseService().getEntreprises();
                for (Entreprise ent : loaded) {
                    entreprises.put(ent.getNom(), ent);
                    if (ent.getSiret() != null) {
                        entreprisesBySiret.put(ent.getSiret(), ent);
                    }
                    // Initialiser les valeurs horaires à 0 (transitoire)
                    activiteProductiveHoraireValeur.put(ent.getNom(), 0.0);
                    activiteMagasinHoraireValeur.put(ent.getNom(), 0.0);
                }
                
                plugin.getLogger().info("[EntrepriseManager] " + count + " entreprises chargées depuis SQLite.");
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "ERREUR CRITIQUE: Impossible de charger les entreprises depuis SQLite !", e);
            }
        } else {
            plugin.getLogger().severe("ERREUR CRITIQUE: Persistence SQLite pour les entreprises non activée ou service non disponible. Aucune entreprise ne sera chargée.");
        }

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
                .sorted((a, b) -> b.timestamp().compareTo(a.timestamp()))
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
        if (plugin.isUsingSQLiteServices() && plugin.getAsyncEntrepriseService() != null) {
            saveToSQLiteAsync();
        } else {
            plugin.getLogger().severe("ERREUR CRITIQUE: Persistence SQLite pour les entreprises non activée ou service non disponible. Sauvegarde impossible.");
        }
    }

    /**
     * Phase 4 - Migration YAML vers SQLite
     * Exécute la migration complète des données depuis YAML vers SQLite.
     * Cette méthode est appelée par /entreprise admin migrate confirm
     */
    public void executeMigrationToSQLite(org.bukkit.entity.Player admin) {
        try {
            // Créer le repository SQLite
            com.gravityyfh.roleplaycity.database.ConnectionManager connManager = plugin.getConnectionManager();

            com.gravityyfh.roleplaycity.entreprise.persistence.EntrepriseRepository repository =
                new com.gravityyfh.roleplaycity.entreprise.persistence.SQLiteEntrepriseRepository(plugin, connManager);

            // Créer le migrator
            com.gravityyfh.roleplaycity.entreprise.migration.YAMLToSQLiteMigrator migrator =
                new com.gravityyfh.roleplaycity.entreprise.migration.YAMLToSQLiteMigrator(plugin, repository);

            // Informer l'admin
            Bukkit.getScheduler().runTask(plugin, () -> {
                admin.sendMessage(ChatColor.YELLOW + "Phase 1/5: Création du backup YAML...");
            });

            // Exécuter la migration
            com.gravityyfh.roleplaycity.entreprise.migration.YAMLToSQLiteMigrator.MigrationResult result =
                migrator.migrate();

            // Afficher les résultats
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (result.isSuccess()) {
                    admin.sendMessage(ChatColor.GOLD + "========================================");
                    admin.sendMessage(ChatColor.GREEN + ChatColor.BOLD.toString() + "✓ MIGRATION RÉUSSIE");
                    admin.sendMessage(ChatColor.GOLD + "========================================");
                    admin.sendMessage(ChatColor.GREEN + "Entreprises migrées: " + result.getEntreprisesMigrees());
                    admin.sendMessage(ChatColor.GREEN + "Durée: " + result.getDurationMs() + "ms");
                    admin.sendMessage("");
                    admin.sendMessage(ChatColor.YELLOW + result.getMessage());
                    admin.sendMessage("");
                    admin.sendMessage(ChatColor.GOLD + "L'ancien fichier YAML a été renommé.");
                    admin.sendMessage(ChatColor.RED + "⚠ Redémarrez le serveur pour utiliser SQLite!");
                    admin.sendMessage(ChatColor.GOLD + "========================================");
                } else {
                    admin.sendMessage(ChatColor.DARK_RED + "========================================");
                    admin.sendMessage(ChatColor.RED + ChatColor.BOLD.toString() + "✗ ÉCHEC DE LA MIGRATION");
                    admin.sendMessage(ChatColor.DARK_RED + "========================================");
                    admin.sendMessage(ChatColor.RED + "Erreur: " + result.getMessage());
                    admin.sendMessage(ChatColor.YELLOW + "Les données YAML n'ont pas été modifiées.");
                    admin.sendMessage(ChatColor.DARK_RED + "========================================");
                }
            });

        } catch (Exception e) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                admin.sendMessage(ChatColor.DARK_RED + "Erreur critique lors de la migration:");
                admin.sendMessage(ChatColor.RED + e.getMessage());
            });
            plugin.getLogger().severe("Erreur de migration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Phase 4 - Rollback de la migration
     * Restaure les données depuis un fichier de backup YAML.
     */
    public void executeRollbackFromBackup(org.bukkit.entity.Player admin, String backupFileName) {
        try {
            java.io.File backupFile = new java.io.File(plugin.getDataFolder(), backupFileName);

            if (!backupFile.exists()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    admin.sendMessage(ChatColor.RED + "Fichier de backup introuvable: " + backupFileName);
                    admin.sendMessage(ChatColor.YELLOW + "Vérifiez le nom du fichier dans: plugins/RoleplayCity/");
                });
                return;
            }

            // Créer un repository temporaire (non utilisé pour le rollback)
            com.gravityyfh.roleplaycity.database.ConnectionManager connManager = plugin.getConnectionManager();
            com.gravityyfh.roleplaycity.entreprise.persistence.EntrepriseRepository repository =
                new com.gravityyfh.roleplaycity.entreprise.persistence.SQLiteEntrepriseRepository(plugin, connManager);

            // Créer le migrator pour accéder à la méthode rollback
            com.gravityyfh.roleplaycity.entreprise.migration.YAMLToSQLiteMigrator migrator =
                new com.gravityyfh.roleplaycity.entreprise.migration.YAMLToSQLiteMigrator(plugin, repository);

            Bukkit.getScheduler().runTask(plugin, () -> {
                admin.sendMessage(ChatColor.YELLOW + "Restauration depuis: " + backupFileName);
            });

            boolean success = migrator.rollback(backupFile);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    admin.sendMessage(ChatColor.GOLD + "========================================");
                    admin.sendMessage(ChatColor.GREEN + ChatColor.BOLD.toString() + "✓ ROLLBACK RÉUSSI");
                    admin.sendMessage(ChatColor.GOLD + "========================================");
                    admin.sendMessage(ChatColor.GREEN + "Le fichier YAML a été restauré depuis le backup.");
                    admin.sendMessage(ChatColor.YELLOW + "La base SQLite a été conservée pour analyse.");
                    admin.sendMessage("");
                    admin.sendMessage(ChatColor.RED + "⚠ Redémarrez le serveur pour recharger les données YAML!");
                    admin.sendMessage(ChatColor.GOLD + "========================================");
                } else {
                    admin.sendMessage(ChatColor.RED + "Échec du rollback. Vérifiez les logs.");
                }
            });

        } catch (Exception e) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                admin.sendMessage(ChatColor.DARK_RED + "Erreur lors du rollback:");
                admin.sendMessage(ChatColor.RED + e.getMessage());
            });
            plugin.getLogger().severe("Erreur de rollback: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private java.util.concurrent.CompletableFuture<Void> saveToSQLiteAsync() {
        com.gravityyfh.roleplaycity.entreprise.service.AsyncEntrepriseService asyncService =
            plugin.getAsyncEntrepriseService();

        if (asyncService == null) {
            plugin.getLogger().warning("[SQLite] AsyncEntrepriseService non disponible - sauvegarde annulée");
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        // Sauvegarder TOUTES les entreprises connues par Logic (car Logic ne notifie pas les modifs)
        // Utilise saveList pour synchro cache + save DB
        return asyncService.saveList(entreprises.values())
            .thenAccept(saved -> {
                if (saved > 0) {
                    plugin.getLogger().fine("[SQLite] " + saved + " entreprises sauvegardées (via Logic)");
                }
            })
            .exceptionally(ex -> {
                plugin.getLogger().severe("[SQLite] Erreur sauvegarde: " + ex.getMessage());
                ex.printStackTrace();
                return null;
            });
    }

    public void saveEntreprisesSync() {
        if (plugin.isUsingSQLiteServices()) {
            try {
                saveToSQLiteAsync().join();
            } catch (Exception e) {
                plugin.getLogger().severe("Erreur lors de la sauvegarde synchrone des entreprises: " + e.getMessage());
            }
        } else {
            saveEntreprises(); // Legacy YAML (already sync)
        }
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
    public void envoyerPrimesDifferreesEmployes(Player player) { String playerUUID = player.getUniqueId().toString(); File messagesFile = new File(plugin.getDataFolder(), "messagesEmployes.yml"); if (!messagesFile.exists()) return; FileConfiguration msgsCfg = YamlConfiguration.loadConfiguration(messagesFile); String basePath = "messages." + playerUUID; if (!msgsCfg.contains(basePath)) return; ConfigurationSection entreprisesSect = msgsCfg.getConfigurationSection(basePath); boolean receivedMessage = false; if (entreprisesSect != null) { for (String nomEnt : entreprisesSect.getKeys(false)) { if (entreprisesSect.isConfigurationSection(nomEnt)) { List<String> messages = entreprisesSect.getStringList(nomEnt + ".list"); double totalPrime = entreprisesSect.getDouble(nomEnt + ".totalPrime", 0.0); if (!messages.isEmpty()) { player.sendMessage(ChatColor.GOLD + "--- Primes/Messages de '" + nomEnt + "' (hors-ligne) ---"); messages.forEach(msg -> player.sendMessage(ChatColor.AQUA + "- " + msg)); if (totalPrime > 0) player.sendMessage(ChatColor.GREEN + "Total primes période: " + String.format("%,.2f€", totalPrime)); player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"); receivedMessage = true; } } } } if (receivedMessage) { msgsCfg.set(basePath, null); try { msgsCfg.save(messagesFile); } catch (IOException e) { plugin.getLogger().severe("Erreur suppression messages différés employé " + playerUUID + ": " + e.getMessage()); } } }
    public void ajouterMessageGerantDifferre(String gerantUUID, String message, String entrepriseNom) { File messagesFile = new File(plugin.getDataFolder(), "messagesGerants.yml"); FileConfiguration messagesConfig = YamlConfiguration.loadConfiguration(messagesFile); String listPath = "messages." + gerantUUID + "." + entrepriseNom + ".list"; List<String> messagesActuels = messagesConfig.getStringList(listPath); messagesActuels.add(ChatColor.stripColor(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM HH:mm")) + ": " + message)); messagesConfig.set(listPath, messagesActuels); try { messagesConfig.save(messagesFile); } catch (IOException e) { plugin.getLogger().severe("Erreur sauvegarde message différé gérant " + gerantUUID + ": " + e.getMessage()); } }
    public void envoyerPrimesDifferreesGerants(Player playerGerant) { String gerantUUID = playerGerant.getUniqueId().toString(); File messagesFile = new File(plugin.getDataFolder(), "messagesGerants.yml"); if (!messagesFile.exists()) return; FileConfiguration msgsCfg = YamlConfiguration.loadConfiguration(messagesFile); String basePath = "messages." + gerantUUID; if (!msgsCfg.contains(basePath)) return; ConfigurationSection entreprisesSect = msgsCfg.getConfigurationSection(basePath); boolean receivedMessage = false; if (entreprisesSect != null) { for (String nomEnt : entreprisesSect.getKeys(false)) { if (entreprisesSect.isConfigurationSection(nomEnt)) { List<String> messages = entreprisesSect.getStringList(nomEnt + ".list"); if (!messages.isEmpty()) { playerGerant.sendMessage(ChatColor.BLUE + "--- Notifications Gérance '" + nomEnt + "' (hors-ligne) ---"); messages.forEach(msg -> playerGerant.sendMessage(ChatColor.AQUA + "- " + msg)); playerGerant.sendMessage(ChatColor.BLUE + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"); receivedMessage = true; } } } } if (receivedMessage) { msgsCfg.set(basePath, null); try { msgsCfg.save(messagesFile); } catch (IOException e) { plugin.getLogger().severe("Erreur suppression messages différés gérant " + gerantUUID + ": " + e.getMessage()); } } }
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
    public boolean canPlayerBreakBlock(Player player, Location location) {
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
    public boolean canPlayerPlaceBlock(Player player, Location location) {
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
                    experiences.sort(Comparator.comparing(PastExperience::dateSortie, Comparator.nullsLast(Comparator.reverseOrder())));
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
                history.sort(Comparator.comparing(PastExperience::dateSortie, Comparator.nullsLast(Comparator.reverseOrder())));
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

    // --- Méthodes pour les Niveaux de Restrictions ---

    /**
     * Récupère la limite horaire actuelle d'une entreprise pour un type de restriction spécifique
     * basé sur son niveau de restrictions
     */
    public int getLimiteRestrictionActuelle(Entreprise entreprise, String typeEntreprise) {
        if (entreprise == null) return 0;

        int niveau = entreprise.getNiveauRestrictions();
        String configPath = "types-entreprise." + typeEntreprise + ".restriction-levels." + niveau + ".limite-par-heure";

        // Si le chemin n'existe pas, utiliser l'ancienne configuration
        if (!plugin.getConfig().contains(configPath)) {
            return plugin.getConfig().getInt("types-entreprise." + typeEntreprise + ".limite-non-membre-par-heure", 0);
        }

        return plugin.getConfig().getInt(configPath, 0);
    }

    /**
     * Récupère le coût d'amélioration au niveau suivant de restrictions
     */
    public double getCoutAmeliorationRestrictions(Entreprise entreprise) {
        if (entreprise == null) return -1;

        int niveauActuel = entreprise.getNiveauRestrictions();
        if (niveauActuel >= 4) return -1; // Niveau max atteint

        int niveauSuivant = niveauActuel + 1;
        String configPath = "types-entreprise." + entreprise.getType() + ".restriction-levels." + niveauSuivant + ".cout-amelioration";

        return plugin.getConfig().getDouble(configPath, -1);
    }

    /**
     * Tente d'améliorer le niveau de restrictions d'une entreprise
     */
    public String tenterAmeliorationNiveauRestrictions(Entreprise entreprise, Player gerant) {
        if (entreprise == null) return ChatColor.RED + "Entreprise non trouvée.";
        if (!entreprise.getGerant().equalsIgnoreCase(gerant.getName())) {
            return ChatColor.RED + "Seul le gérant peut effectuer cette action.";
        }

        int niveauActuel = entreprise.getNiveauRestrictions();
        if (niveauActuel >= 4) {
            return ChatColor.YELLOW + "Votre entreprise a déjà atteint le niveau maximum de restrictions (Niveau 4).";
        }

        double cout = getCoutAmeliorationRestrictions(entreprise);
        if (cout < 0) {
            return ChatColor.RED + "Impossible de déterminer le coût de l'amélioration ou niveau max atteint.";
        }

        if (entreprise.getSolde() < cout) {
            return ChatColor.RED + "Solde insuffisant (" + String.format("%,.2f", entreprise.getSolde()) +
                "€). Requis : " + String.format("%,.2f", cout) + "€.";
        }

        // Récupérer les quotas avant et après
        int quotaAvant = getLimiteRestrictionActuelle(entreprise, entreprise.getType());

        // Effectuer l'amélioration
        entreprise.setSolde(entreprise.getSolde() - cout);
        entreprise.setNiveauRestrictions(niveauActuel + 1);
        entreprise.addTransaction(new Transaction(TransactionType.OTHER_EXPENSE, cout,
            "Amélioration niveau restrictions (Niv. " + (niveauActuel + 1) + ")", gerant.getName()));
        saveEntreprises();

        int quotaApres = getLimiteRestrictionActuelle(entreprise, entreprise.getType());

        return ChatColor.GREEN + "✓ Niveau de restrictions amélioré au niveau " + (niveauActuel + 1) + " !\n" +
               ChatColor.GRAY + "Quota horaire: " + ChatColor.WHITE + quotaAvant + "/h" +
               ChatColor.GRAY + " → " + ChatColor.GREEN + quotaApres + "/h\n" +
               ChatColor.GRAY + "Coût: " + ChatColor.YELLOW + String.format("%,.2f€", cout);
    }

    // --- Fin Méthodes pour les Niveaux ---

    // SUPPRESSION: Classe Entreprise extraite vers com.gravityyfh.roleplaycity.entreprise.model.Entreprise
    // (~90 lignes supprimées)

    // SUPPRESSION: ActionInfo, DemandeCreation, ProductionRecord extraites vers package model
    // (~60 lignes supprimées)

} // Fin de la classe EntrepriseManagerLogic