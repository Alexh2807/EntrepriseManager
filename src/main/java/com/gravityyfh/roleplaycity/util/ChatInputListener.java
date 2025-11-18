package com.gravityyfh.roleplaycity.util;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Système de capture d'input depuis le chat au lieu de commandes
 * Permet aux joueurs d'entrer du texte directement dans le chat
 *
 * Exemple d'utilisation:
 * chatInputListener.requestInput(
 *     player,
 *     "Entrez le nom de votre entreprise:",
 *     input -> createEntreprise(player, input),
 *     input -> input.length() >= 3 && input.length() <= 32,
 *     "Le nom doit contenir entre 3 et 32 caractères",
 *     60
 * );
 */
public class ChatInputListener implements Listener {

    private final Plugin plugin;
    private final Map<UUID, PendingInput> pendingInputs;

    public ChatInputListener(Plugin plugin) {
        this.plugin = plugin;
        this.pendingInputs = new HashMap<>();

        // Enregistrer le listener
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Nettoyage automatique toutes les 5 secondes
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupExpired, 100L, 100L);
    }

    /**
     * Demande une entrée au joueur via le chat
     *
     * @param player Le joueur
     * @param prompt Le message d'invite
     * @param onInput Action à exécuter avec l'input
     * @param validator Validateur d'input (optionnel)
     * @param errorMessage Message d'erreur si validation échoue
     * @param timeoutSeconds Temps avant expiration
     */
    public void requestInput(Player player, String prompt,
                            Consumer<String> onInput,
                            Predicate<String> validator,
                            String errorMessage,
                            int timeoutSeconds) {
        UUID playerId = player.getUniqueId();

        // Annuler tout input en attente
        if (hasPendingInput(playerId)) {
            cancelInput(playerId);
        }

        // Créer l'input en attente
        PendingInput input = new PendingInput(
            prompt,
            onInput,
            validator,
            errorMessage,
            System.currentTimeMillis() + (timeoutSeconds * 1000L)
        );

        pendingInputs.put(playerId, input);

        // Envoyer le message d'invite
        sendInputPrompt(player, prompt, timeoutSeconds);
    }

    /**
     * Demande une entrée avec timeout par défaut (60 secondes)
     */
    public void requestInput(Player player, String prompt, Consumer<String> onInput) {
        requestInput(player, prompt, onInput, null, null, 60);
    }

    /**
     * Demande une entrée avec validateur
     */
    public void requestInput(Player player, String prompt,
                            Consumer<String> onInput,
                            Predicate<String> validator,
                            String errorMessage) {
        requestInput(player, prompt, onInput, validator, errorMessage, 60);
    }

    /**
     * Vérifie si un joueur a un input en attente
     */
    public boolean hasPendingInput(UUID playerId) {
        return pendingInputs.containsKey(playerId);
    }

    /**
     * Annule l'input en attente pour un joueur
     */
    public void cancelInput(UUID playerId) {
        PendingInput input = pendingInputs.remove(playerId);
        if (input != null) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                InteractiveMessage.info("Saisie annulée.")
                    .send(player);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
            }
        }
    }

    /**
     * Écoute les messages du chat pour capturer les inputs
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Vérifier si le joueur a un input en attente
        PendingInput input = pendingInputs.get(playerId);
        if (input == null) {
            return;
        }

        // Annuler l'événement pour éviter que le message soit visible
        event.setCancelled(true);

        String message = event.getMessage().trim();

        // Vérifier si c'est une annulation
        if (message.equalsIgnoreCase("/cancel") || message.equalsIgnoreCase("annuler")) {
            cancelInput(playerId);
            return;
        }

        // Vérifier l'expiration
        if (input.isExpired()) {
            pendingInputs.remove(playerId);
            Bukkit.getScheduler().runTask(plugin, () -> {
                InteractiveMessage.error("La saisie a expiré. Veuillez réessayer.")
                    .send(player);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            });
            return;
        }

        // Valider l'input
        if (input.validator != null && !input.validator.test(message)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                InteractiveMessage.error(input.errorMessage != null ?
                    input.errorMessage : "Entrée invalide. Réessayez.")
                    .send(player);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);

                // Renvoyer le prompt
                sendInputPrompt(player, input.prompt, 60);
            });
            return;
        }

        // Input valide - supprimer et exécuter
        pendingInputs.remove(playerId);

        final String finalMessage = message;
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Exécuter l'action avec l'input
            input.onInput.accept(finalMessage);

            // Son de succès
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        });
    }

    /**
     * Nettoie les inputs quand un joueur se déconnecte
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        pendingInputs.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Envoie le message d'invite pour l'input
     */
    private void sendInputPrompt(Player player, String prompt, int timeoutSeconds) {
        new InteractiveMessage()
            .emptyLine()
            .separator(ChatColor.AQUA)
            .text("✎ SAISIE REQUISE", ChatColor.AQUA, true, false)
            .newLine()
            .separator(ChatColor.AQUA)
            .emptyLine()
            .text(prompt, ChatColor.WHITE, false, false)
            .emptyLine()
            .text("→ ", ChatColor.YELLOW, true, false)
            .text("Entrez votre réponse dans le chat", ChatColor.GRAY, false, true)
            .newLine()
            .emptyLine()
            .button("✗ ANNULER", "/rc:cancelinput", "Cliquez pour annuler la saisie", ChatColor.RED)
            .emptyLine()
            .text("Expire dans " + timeoutSeconds + " secondes", ChatColor.DARK_GRAY, false, true)
            .newLine()
            .separator(ChatColor.AQUA)
            .send(player);

        // Son d'attention
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
    }

    /**
     * Nettoie les inputs expirés
     */
    private void cleanupExpired() {

        pendingInputs.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired()) {
                UUID playerId = entry.getKey();
                Player player = Bukkit.getPlayer(playerId);

                if (player != null && player.isOnline()) {
                    InteractiveMessage.warning("Votre saisie a expiré.")
                        .send(player);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
                }

                return true;
            }
            return false;
        });
    }

    /**
     * Nettoie tous les inputs (lors du reload/disable)
     */
    public void shutdown() {
        pendingInputs.forEach((playerId, input) -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                InteractiveMessage.warning("Toutes les saisies ont été annulées (reload du plugin).")
                    .send(player);
            }
        });

        pendingInputs.clear();
    }

    /**
         * Classe interne pour stocker un input en attente
         */
        private record PendingInput(String prompt, Consumer<String> onInput, Predicate<String> validator,
                                    String errorMessage, long expiresAt) {

        public boolean isExpired() {
                return System.currentTimeMillis() > expiresAt;
            }
        }
}
