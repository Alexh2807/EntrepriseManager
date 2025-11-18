package com.gravityyfh.roleplaycity.town.manager;

import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.gravityyfh.roleplaycity.RoleplayCity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service centralisé pour gérer le contexte d'entreprise lors des opérations.
 *
 * Responsabilités:
 * - Récupérer les entreprises éligibles d'un joueur
 * - Gérer le cache de sélection d'entreprise de manière robuste
 * - Valider qu'une entreprise peut être utilisée pour une opération
 * - Unifier la logique d'achat, location, et création de shops
 */
public class EnterpriseContextManager {

    private final RoleplayCity plugin;
    private final EntrepriseManagerLogic entrepriseLogic;

    // Cache de sélection: UUID joueur → SelectionContext
    private final Map<UUID, SelectionContext> selectionCache = new HashMap<>();

    // Durée de validité du cache (5 minutes)
    private static final long CACHE_TIMEOUT_MS = 5 * 60 * 1000;

    public EnterpriseContextManager(RoleplayCity plugin, EntrepriseManagerLogic entrepriseLogic) {
        this.plugin = plugin;
        this.entrepriseLogic = entrepriseLogic;
    }

    /**
     * Types d'opérations nécessitant une sélection d'entreprise
     */
    public enum OperationType {
        PURCHASE,       // Achat de terrain PROFESSIONNEL
        RENTAL,         // Location de terrain PROFESSIONNEL
        SHOP_CREATION   // Création de boutique (future utilisation)
    }

    /**
     * Contexte de sélection d'entreprise (cache avec timeout)
     */
    private static class SelectionContext {
        final String siret;
        final OperationType operationType;
        final long timestamp;

        SelectionContext(String siret, OperationType operationType) {
            this.siret = siret;
            this.operationType = operationType;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > CACHE_TIMEOUT_MS;
        }
    }

    /**
     * Récupère toutes les entreprises où le joueur est gérant
     *
     * @param player Le joueur
     * @return Liste des entreprises (peut être vide)
     */
    public List<EntrepriseManagerLogic.Entreprise> getPlayerEnterprises(Player player) {
        List<EntrepriseManagerLogic.Entreprise> playerCompanies = new ArrayList<>();
        UUID playerUuid = player.getUniqueId();

        for (EntrepriseManagerLogic.Entreprise entreprise : entrepriseLogic.getEntreprises()) {
            String gerantUuidStr = entreprise.getGerantUUID(); // CORRECTION: getGerantUUID() au lieu de getGerant()
            if (gerantUuidStr == null) continue;

            try {
                UUID gerantUuid = UUID.fromString(gerantUuidStr);
                if (gerantUuid.equals(playerUuid)) {
                    playerCompanies.add(entreprise);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[EnterpriseContext] UUID gérant invalide: " + gerantUuidStr);
            }
        }

        return playerCompanies;
    }

    /**
     * Détermine si une sélection d'entreprise via GUI est nécessaire
     *
     * Selon la configuration utilisateur: TOUJOURS demander, même avec 1 entreprise
     *
     * @param player Le joueur
     * @param opType Type d'opération
     * @return true si le GUI de sélection doit être ouvert
     */
    public boolean needsEnterpriseSelection(Player player, OperationType opType) {
        List<EntrepriseManagerLogic.Entreprise> companies = getPlayerEnterprises(player);

        // Si aucune entreprise, pas besoin de sélection (opération bloquée)
        return !companies.isEmpty();

        // TOUJOURS demander, même avec 1 seule entreprise (selon choix utilisateur)
    }

    /**
     * Enregistre l'entreprise sélectionnée par le joueur dans le cache
     *
     * @param playerUuid UUID du joueur
     * @param siret SIRET de l'entreprise sélectionnée
     * @param opType Type d'opération
     */
    public void setSelectedEnterprise(UUID playerUuid, String siret, OperationType opType) {
        if (siret == null || playerUuid == null || opType == null) {
            plugin.getLogger().warning("[EnterpriseContext] setSelectedEnterprise avec paramètre null");
            return;
        }

        selectionCache.put(playerUuid, new SelectionContext(siret, opType));
        plugin.getLogger().fine("[EnterpriseContext] SIRET " + siret + " sélectionné pour " +
            playerUuid + " (opération: " + opType + ")");
    }

    /**
     * Récupère et supprime l'entreprise sélectionnée du cache
     *
     * @param playerUuid UUID du joueur
     * @param expectedOpType Type d'opération attendu (sécurité)
     * @return SIRET de l'entreprise, ou null si cache vide/expiré/mauvais type
     */
    public String getAndClearSelectedEnterprise(UUID playerUuid, OperationType expectedOpType) {
        SelectionContext context = selectionCache.remove(playerUuid);

        if (context == null) {
            plugin.getLogger().fine("[EnterpriseContext] Cache vide pour " + playerUuid);
            return null;
        }

        if (context.isExpired()) {
            plugin.getLogger().warning("[EnterpriseContext] Cache expiré pour " + playerUuid +
                " (âge: " + (System.currentTimeMillis() - context.timestamp) / 1000 + "s)");
            return null;
        }

        if (context.operationType != expectedOpType) {
            plugin.getLogger().warning("[EnterpriseContext] Type d'opération incorrect pour " + playerUuid +
                " (attendu: " + expectedOpType + ", trouvé: " + context.operationType + ")");
            return null;
        }

        plugin.getLogger().fine("[EnterpriseContext] Cache récupéré: SIRET " + context.siret + " pour " + playerUuid);
        return context.siret;
    }

    /**
     * Valide qu'une entreprise existe et que le joueur en est le gérant
     *
     * @param player Le joueur
     * @param siret SIRET de l'entreprise
     * @return Résultat de validation avec entreprise ou message d'erreur
     */
    public ValidationResult validateEnterprise(Player player, String siret) {
        if (siret == null) {
            return ValidationResult.failure("SIRET null");
        }

        EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntrepriseBySiret(siret);
        if (entreprise == null) {
            return ValidationResult.failure("Entreprise introuvable (SIRET: " + siret + ")");
        }

        String gerantUuidStr = entreprise.getGerantUUID(); // CORRECTION: getGerantUUID() au lieu de getGerant()
        if (gerantUuidStr == null) {
            return ValidationResult.failure("Entreprise sans gérant");
        }

        try {
            UUID gerantUuid = UUID.fromString(gerantUuidStr);
            if (!gerantUuid.equals(player.getUniqueId())) {
                return ValidationResult.failure("Vous n'êtes pas le gérant de cette entreprise");
            }
        } catch (IllegalArgumentException e) {
            return ValidationResult.failure("UUID gérant invalide");
        }

        return ValidationResult.success(entreprise);
    }

    /**
     * Nettoie le cache d'un joueur (utile lors de déconnexion)
     *
     * @param playerUuid UUID du joueur
     */
    public void clearPlayerCache(UUID playerUuid) {
        selectionCache.remove(playerUuid);
    }

    /**
     * Nettoie les entrées expirées du cache (maintenance périodique)
     */
    public void cleanExpiredCache() {
        selectionCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * Résultat de validation d'entreprise
     */
    public static class ValidationResult {
        private final boolean success;
        private final String errorMessage;
        private final EntrepriseManagerLogic.Entreprise entreprise;

        private ValidationResult(boolean success, String errorMessage, EntrepriseManagerLogic.Entreprise entreprise) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.entreprise = entreprise;
        }

        public static ValidationResult success(EntrepriseManagerLogic.Entreprise entreprise) {
            return new ValidationResult(true, null, entreprise);
        }

        public static ValidationResult failure(String errorMessage) {
            return new ValidationResult(false, errorMessage, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public EntrepriseManagerLogic.Entreprise getEntreprise() {
            return entreprise;
        }
    }
}
