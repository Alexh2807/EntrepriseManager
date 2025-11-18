package com.gravityyfh.roleplaycity.util;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gestionnaire de confirmations avec système de timeout et callbacks
 * Remplace les commandes à taper par des boutons cliquables
 *
 * Exemple d'utilisation:
 * confirmationManager.requestConfirmation(
 *     player,
 *     "Supprimer l'entreprise " + name + " ?",
 *     () -> deleteEntreprise(name),
 *     () -> player.sendMessage("Suppression annulée"),
 *     30
 * );
 */
public class ConfirmationManager {

    private final Plugin plugin;
    private final Map<UUID, PendingConfirmation> pendingConfirmations;

    public ConfirmationManager(Plugin plugin) {
        this.plugin = plugin;
        this.pendingConfirmations = new HashMap<>();

        // Nettoyage automatique toutes les 5 secondes
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupExpired, 100L, 100L);
    }

    /**
     * Demande une confirmation au joueur avec boutons cliquables
     *
     * @param player Le joueur
     * @param question La question à afficher
     * @param onConfirm Action à exécuter si confirmé
     * @param onCancel Action à exécuter si annulé
     * @param timeoutSeconds Temps avant expiration (par défaut 30)
     */
    public void requestConfirmation(Player player, String question,
                                   Runnable onConfirm, Runnable onCancel,
                                   int timeoutSeconds) {
        UUID playerId = player.getUniqueId();

        // Annuler toute confirmation en attente
        if (hasPendingConfirmation(playerId)) {
            cancelConfirmation(playerId);
        }

        // Générer un ID unique pour cette confirmation
        String confirmId = generateConfirmationId(playerId);

        // Créer la confirmation
        PendingConfirmation confirmation = new PendingConfirmation(
            confirmId,
            question,
            onConfirm,
            onCancel,
            System.currentTimeMillis() + (timeoutSeconds * 1000L)
        );

        pendingConfirmations.put(playerId, confirmation);

        // Envoyer le message interactif
        sendConfirmationMessage(player, question, timeoutSeconds);
    }

    /**
     * Demande une confirmation avec timeout par défaut (30 secondes)
     */
    public void requestConfirmation(Player player, String question,
                                   Runnable onConfirm, Runnable onCancel) {
        requestConfirmation(player, question, onConfirm, onCancel, 30);
    }

    /**
     * Confirme l'action en attente pour un joueur
     */
    public boolean confirm(UUID playerId) {
        PendingConfirmation confirmation = pendingConfirmations.remove(playerId);

        if (confirmation == null) {
            return false;
        }

        if (confirmation.isExpired()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                InteractiveMessage.error("La confirmation a expiré. Veuillez réessayer.")
                    .send(player);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            return false;
        }

        // Exécuter l'action de confirmation
        confirmation.onConfirm.run();
        return true;
    }

    /**
     * Annule l'action en attente pour un joueur
     */
    public boolean cancel(UUID playerId) {
        PendingConfirmation confirmation = pendingConfirmations.remove(playerId);

        if (confirmation == null) {
            return false;
        }

        // Exécuter l'action d'annulation
        confirmation.onCancel.run();
        return true;
    }

    /**
     * Vérifie si un joueur a une confirmation en attente
     */
    public boolean hasPendingConfirmation(UUID playerId) {
        return pendingConfirmations.containsKey(playerId);
    }

    /**
     * Annule une confirmation sans exécuter l'action d'annulation
     */
    public void cancelConfirmation(UUID playerId) {
        pendingConfirmations.remove(playerId);
    }

    /**
     * Nettoie les confirmations expirées
     */
    private void cleanupExpired() {

        pendingConfirmations.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired()) {
                UUID playerId = entry.getKey();
                Player player = Bukkit.getPlayer(playerId);

                if (player != null && player.isOnline()) {
                    InteractiveMessage.warning("Votre confirmation a expiré.")
                        .send(player);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
                }

                // Exécuter l'action d'annulation
                entry.getValue().onCancel.run();
                return true;
            }
            return false;
        });
    }

    /**
     * Génère un ID unique pour une confirmation
     */
    private String generateConfirmationId(UUID playerId) {
        return playerId.toString().substring(0, 8) + "-" + System.currentTimeMillis();
    }

    /**
     * Envoie le message de confirmation interactif
     */
    private void sendConfirmationMessage(Player player, String question,
                                         int timeoutSeconds) {
        // Créer un message interactif avec boutons
        new InteractiveMessage()
            .emptyLine()
            .separator(ChatColor.GOLD)
            .text("⚠ CONFIRMATION REQUISE", ChatColor.YELLOW, true, false)
            .newLine()
            .separator(ChatColor.GOLD)
            .emptyLine()
            .text(question, ChatColor.WHITE)
            .emptyLine()
            .button("✓ CONFIRMER", "/rc:confirm", "Cliquez pour confirmer l'action", ChatColor.GREEN)
            .spaces(3)
            .button("✗ ANNULER", "/rc:cancel", "Cliquez pour annuler", ChatColor.RED)
            .emptyLine()
            .text("Cette confirmation expire dans " + timeoutSeconds + " secondes", ChatColor.GRAY, false, true)
            .newLine()
            .separator(ChatColor.GOLD)
            .send(player);

        // Son d'attention
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
    }

    /**
     * Nettoie toutes les confirmations (lors du reload/disable)
     */
    public void shutdown() {
        // Annuler toutes les confirmations en attente
        pendingConfirmations.forEach((playerId, confirmation) -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                InteractiveMessage.warning("Toutes les confirmations ont été annulées (reload du plugin).")
                    .send(player);
            }
            confirmation.onCancel.run();
        });

        pendingConfirmations.clear();
    }

    /**
         * Classe interne pour stocker une confirmation en attente
         */
        private record PendingConfirmation(String id, String question, Runnable onConfirm, Runnable onCancel,
                                           long expiresAt) {

        public boolean isExpired() {
                return System.currentTimeMillis() > expiresAt;
            }
        }
}
