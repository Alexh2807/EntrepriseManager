package com.gravityyfh.roleplaycity.phone.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.phone.PhoneManager;
import com.gravityyfh.roleplaycity.phone.gui.*;
import com.gravityyfh.roleplaycity.phone.service.PhoneService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener pour capturer les saisies du chat pour le systeme de telephone.
 * Gere les entrees de numeros, messages SMS, noms de contacts, etc.
 */
public class PhoneChatListener implements Listener {

    private final RoleplayCity plugin;
    private final PhoneManager phoneManager;
    private final PhoneService phoneService;

    /**
     * Types d'input attendus.
     */
    public enum InputType {
        CALL_NUMBER,        // Saisie d'un numero pour appeler
        SMS_NUMBER,         // Saisie d'un numero pour SMS
        SMS_CONTENT,        // Saisie du contenu d'un SMS
        CONTACT_NUMBER,     // Saisie d'un numero pour ajouter un contact
        CONTACT_NAME,       // Saisie d'un nom de contact
        BLOCK_NUMBER        // Saisie d'un numero a bloquer
    }

    /**
     * Classe pour stocker l'etat d'attente d'un joueur.
     */
    private static class PendingInput {
        final InputType type;
        final String data; // Donnees supplementaires (ex: numero pour SMS_CONTENT)
        final long timestamp;

        PendingInput(InputType type, String data) {
            this.type = type;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            // Expire apres 2 minutes
            return System.currentTimeMillis() - timestamp > 120000;
        }
    }

    // Map statique pour stocker les attentes
    private static final Map<UUID, PendingInput> pendingInputs = new ConcurrentHashMap<>();

    public PhoneChatListener(RoleplayCity plugin) {
        this.plugin = plugin;
        this.phoneManager = plugin.getPhoneManager();
        this.phoneService = plugin.getPhoneService();
    }

    /**
     * Demande au joueur de saisir une entree.
     * @param player Le joueur
     * @param type Le type d'entree attendue
     * @param data Donnees supplementaires (peut etre null)
     */
    public static void awaitInput(Player player, InputType type, String data) {
        pendingInputs.put(player.getUniqueId(), new PendingInput(type, data));
    }

    /**
     * Annule l'attente d'entree pour un joueur.
     */
    public static void cancelInput(Player player) {
        pendingInputs.remove(player.getUniqueId());
    }

    /**
     * Verifie si un joueur a une entree en attente.
     */
    public static boolean hasPendingInput(Player player) {
        PendingInput pending = pendingInputs.get(player.getUniqueId());
        if (pending != null && pending.isExpired()) {
            pendingInputs.remove(player.getUniqueId());
            return false;
        }
        return pending != null;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        PendingInput pending = pendingInputs.get(uuid);
        if (pending == null) {
            return;
        }

        // Verifier expiration
        if (pending.isExpired()) {
            pendingInputs.remove(uuid);
            return;
        }

        // Annuler l'event chat
        event.setCancelled(true);

        String message = event.getMessage().trim();

        // Verifier si le joueur veut annuler
        if (message.equalsIgnoreCase("annuler") || message.equalsIgnoreCase("cancel")) {
            pendingInputs.remove(uuid);
            // Executer sur le thread principal
            new BukkitRunnable() {
                @Override
                public void run() {
                    player.sendMessage(ChatColor.YELLOW + "[Telephone] " + ChatColor.WHITE + "Action annulee.");
                    new PhoneMainGUI(plugin).open(player);
                }
            }.runTask(plugin);
            return;
        }

        // Retirer l'attente
        pendingInputs.remove(uuid);

        // Traiter l'entree sur le thread principal
        new BukkitRunnable() {
            @Override
            public void run() {
                processInput(player, pending.type, pending.data, message);
            }
        }.runTask(plugin);
    }

    /**
     * Traite l'entree du joueur selon le type.
     */
    private void processInput(Player player, InputType type, String data, String input) {
        switch (type) {
            case CALL_NUMBER:
                processCallNumber(player, input);
                break;

            case SMS_NUMBER:
                processSmsNumber(player, input);
                break;

            case SMS_CONTENT:
                processSmsContent(player, data, input);
                break;

            case CONTACT_NUMBER:
                processContactNumber(player, input);
                break;

            case CONTACT_NAME:
                processContactName(player, data, input);
                break;

            case BLOCK_NUMBER:
                processBlockNumber(player, input);
                break;
        }
    }

    /**
     * Traite la saisie d'un numero pour appeler.
     */
    private void processCallNumber(Player player, String number) {
        // Valider le format du numero
        if (!isValidPhoneNumber(number)) {
            player.sendMessage(ChatColor.RED + "[Telephone] Format invalide. Utilisez XXX-XXXX");
            player.sendMessage("");
            player.sendMessage(ChatColor.YELLOW + "Reessayez ou tapez 'annuler':");
            awaitInput(player, InputType.CALL_NUMBER, null);
            return;
        }

        // Normaliser le numero
        String normalizedNumber = normalizePhoneNumber(number);

        // Verifier si le joueur a un telephone
        if (!phoneManager.hasPhoneInHand(player)) {
            player.sendMessage(ChatColor.RED + "[Telephone] Vous devez tenir votre telephone en main pour appeler.");
            return;
        }

        // Initier l'appel
        phoneService.initiateCall(player, normalizedNumber);
    }

    /**
     * Traite la saisie d'un numero pour SMS.
     */
    private void processSmsNumber(Player player, String number) {
        // Valider le format du numero
        if (!isValidPhoneNumber(number)) {
            player.sendMessage(ChatColor.RED + "[Telephone] Format invalide. Utilisez XXX-XXXX");
            player.sendMessage("");
            player.sendMessage(ChatColor.YELLOW + "Reessayez ou tapez 'annuler':");
            awaitInput(player, InputType.SMS_NUMBER, null);
            return;
        }

        // Normaliser le numero
        String normalizedNumber = normalizePhoneNumber(number);

        // Demander le contenu du message
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "[Telephone] " + ChatColor.WHITE + "Ecrivez votre message:");
        player.sendMessage(ChatColor.GRAY + "(ou tapez 'annuler')");
        player.sendMessage("");

        awaitInput(player, InputType.SMS_CONTENT, normalizedNumber);
    }

    /**
     * Traite la saisie du contenu d'un SMS.
     */
    private void processSmsContent(Player player, String recipientNumber, String content) {
        if (content.isEmpty()) {
            player.sendMessage(ChatColor.RED + "[Telephone] Message vide. Reessayez:");
            awaitInput(player, InputType.SMS_CONTENT, recipientNumber);
            return;
        }

        if (content.length() > 200) {
            player.sendMessage(ChatColor.RED + "[Telephone] Message trop long (max 200 caracteres). Reessayez:");
            awaitInput(player, InputType.SMS_CONTENT, recipientNumber);
            return;
        }

        // Envoyer le SMS
        if (phoneService.sendSms(player, recipientNumber, content)) {
            String displayName = phoneService.getContactDisplayName(player.getUniqueId(), recipientNumber);
            if (displayName == null) displayName = recipientNumber;
            player.sendMessage(ChatColor.GREEN + "[Telephone] " + ChatColor.WHITE + "SMS envoye a " + displayName);
        }
        // Les erreurs sont gerees dans sendSms()
    }

    /**
     * Traite la saisie d'un numero pour ajouter un contact.
     */
    private void processContactNumber(Player player, String number) {
        // Valider le format du numero
        if (!isValidPhoneNumber(number)) {
            player.sendMessage(ChatColor.RED + "[Telephone] Format invalide. Utilisez XXX-XXXX");
            player.sendMessage("");
            player.sendMessage(ChatColor.YELLOW + "Reessayez ou tapez 'annuler':");
            awaitInput(player, InputType.CONTACT_NUMBER, null);
            return;
        }

        // Normaliser le numero
        String normalizedNumber = normalizePhoneNumber(number);

        // Verifier si le numero existe
        if (!phoneService.phoneNumberExists(normalizedNumber)) {
            player.sendMessage(ChatColor.RED + "[Telephone] Ce numero n'existe pas.");
            new PhoneContactsGUI(plugin).open(player);
            return;
        }

        // Verifier si deja en contact
        if (phoneService.hasContact(player, normalizedNumber)) {
            player.sendMessage(ChatColor.RED + "[Telephone] Ce numero est deja dans vos contacts.");
            new PhoneContactsGUI(plugin).open(player);
            return;
        }

        // Demander le nom du contact
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "[Telephone] " + ChatColor.WHITE + "Entrez le nom du contact:");
        player.sendMessage(ChatColor.GRAY + "(ou tapez 'annuler')");
        player.sendMessage("");

        awaitInput(player, InputType.CONTACT_NAME, normalizedNumber);
    }

    /**
     * Traite la saisie d'un nom de contact.
     */
    private void processContactName(Player player, String contactNumber, String name) {
        if (name.isEmpty()) {
            player.sendMessage(ChatColor.RED + "[Telephone] Nom vide. Reessayez:");
            awaitInput(player, InputType.CONTACT_NAME, contactNumber);
            return;
        }

        if (name.length() > 20) {
            player.sendMessage(ChatColor.RED + "[Telephone] Nom trop long (max 20 caracteres). Reessayez:");
            awaitInput(player, InputType.CONTACT_NAME, contactNumber);
            return;
        }

        // Nettoyer le nom (supprimer les codes couleur)
        String cleanName = ChatColor.stripColor(name);

        // Ajouter le contact
        if (phoneService.addContact(player, contactNumber, cleanName)) {
            player.sendMessage(ChatColor.GREEN + "[Telephone] " + ChatColor.WHITE + "Contact " + cleanName + " ajoute!");
        } else {
            player.sendMessage(ChatColor.RED + "[Telephone] Erreur lors de l'ajout du contact.");
        }

        new PhoneContactsGUI(plugin).open(player);
    }

    /**
     * Traite la saisie d'un numero a bloquer.
     */
    private void processBlockNumber(Player player, String number) {
        // Valider le format du numero
        if (!isValidPhoneNumber(number)) {
            player.sendMessage(ChatColor.RED + "[Telephone] Format invalide. Utilisez XXX-XXXX");
            player.sendMessage("");
            player.sendMessage(ChatColor.YELLOW + "Reessayez ou tapez 'annuler':");
            awaitInput(player, InputType.BLOCK_NUMBER, null);
            return;
        }

        // Normaliser le numero
        String normalizedNumber = normalizePhoneNumber(number);

        // Bloquer le numero
        if (phoneService.blockNumber(player, normalizedNumber)) {
            player.sendMessage(ChatColor.GREEN + "[Telephone] " + ChatColor.WHITE + "Numero " + normalizedNumber + " bloque.");
        } else {
            player.sendMessage(ChatColor.RED + "[Telephone] Ce numero est deja bloque.");
        }

        new PhoneSettingsGUI(plugin).open(player);
    }

    /**
     * Valide le format d'un numero de telephone.
     * Formats acceptes: XXX-XXXX, XXXXXXX, XXX XXXX
     */
    private boolean isValidPhoneNumber(String number) {
        if (number == null || number.isEmpty()) {
            return false;
        }

        // Supprimer les espaces et tirets pour validation
        String cleaned = number.replaceAll("[\\s-]", "");

        // Doit avoir exactement 7 chiffres
        return cleaned.matches("\\d{7}");
    }

    /**
     * Normalise un numero de telephone au format XXX-XXXX.
     */
    private String normalizePhoneNumber(String number) {
        // Supprimer tout sauf les chiffres
        String cleaned = number.replaceAll("[^\\d]", "");

        if (cleaned.length() != 7) {
            return number; // Retourner tel quel si format invalide
        }

        // Formater en XXX-XXXX
        return cleaned.substring(0, 3) + "-" + cleaned.substring(3);
    }

    /**
     * Nettoie les entrees expiries (appele periodiquement).
     */
    public void cleanupExpiredInputs() {
        pendingInputs.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * Nettoie l'entree quand un joueur se deconnecte.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        pendingInputs.remove(event.getPlayer().getUniqueId());
    }
}
