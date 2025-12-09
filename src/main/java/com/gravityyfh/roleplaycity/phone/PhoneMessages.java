package com.gravityyfh.roleplaycity.phone;

import org.bukkit.ChatColor;

/**
 * Messages du systeme telephonique codes en dur.
 * Centralise tous les messages pour faciliter la maintenance.
 */
public final class PhoneMessages {

    private PhoneMessages() {}

    // Prefixe
    public static final String PREFIX = ChatColor.DARK_GREEN + "[" + ChatColor.GREEN + "Tel" + ChatColor.DARK_GREEN + "] " + ChatColor.WHITE;
    public static final String PREFIX_SMS = ChatColor.GOLD + "[SMS] " + ChatColor.WHITE;

    // === ERREURS ===
    public static final String NO_PHONE = PREFIX + ChatColor.RED + "Vous n'avez pas de telephone.";
    public static final String PHONE_NOT_IN_HAND = PREFIX + ChatColor.RED + "Tenez votre telephone en main.";
    public static final String NO_CREDITS = PREFIX + ChatColor.RED + "Credits insuffisants!";
    public static final String INVALID_NUMBER = PREFIX + ChatColor.RED + "Ce numero n'existe pas.";
    public static final String SELF_CALL = PREFIX + ChatColor.RED + "Vous ne pouvez pas vous appeler.";
    public static final String SELF_SMS = PREFIX + ChatColor.RED + "Vous ne pouvez pas vous envoyer un SMS.";
    public static final String TARGET_OFFLINE = PREFIX + ChatColor.YELLOW + "Ce correspondant n'est pas joignable.";
    public static final String TARGET_BUSY = PREFIX + ChatColor.YELLOW + "Ce correspondant est deja en ligne.";
    public static final String ALREADY_IN_CALL = PREFIX + ChatColor.RED + "Vous etes deja en appel.";
    public static final String NUMBER_BLOCKED = PREFIX + ChatColor.YELLOW + "Ce correspondant n'est pas joignable.";
    public static final String MESSAGE_TOO_LONG = PREFIX + ChatColor.RED + "Message trop long (max 200 caracteres).";
    public static final String MESSAGE_EMPTY = PREFIX + ChatColor.RED + "Message vide.";
    public static final String ENTER_7_DIGITS = PREFIX + ChatColor.RED + "Entrez un numero complet (7 chiffres).";
    public static final String CONTACT_EXISTS = PREFIX + ChatColor.RED + "Ce contact existe deja.";
    public static final String CONTACT_NAME_TOO_LONG = PREFIX + ChatColor.RED + "Nom trop long (max 20 caracteres).";
    public static final String CONTACT_NAME_EMPTY = PREFIX + ChatColor.RED + "Nom vide.";
    public static final String MAX_CONTACTS_REACHED = PREFIX + ChatColor.RED + "Nombre maximum de contacts atteint.";

    // === OPENAUDIOMC ===
    public static final String NOT_CONNECTED_AUDIO = PREFIX + ChatColor.RED + "Connectez-vous au chat vocal avec " + ChatColor.YELLOW + "/audio" + ChatColor.RED + " pour appeler!";
    public static final String TARGET_NOT_CONNECTED_AUDIO = PREFIX + ChatColor.RED + "Ce joueur n'est pas connecte au chat vocal.";
    public static final String VOICE_CONNECTED = PREFIX + ChatColor.GREEN + "Chat vocal connecte!";
    public static final String VOICE_UNAVAILABLE = PREFIX + ChatColor.GRAY + "Mode texte (chat vocal indisponible).";

    // === APPELS ===
    public static final String CALLING = PREFIX + ChatColor.YELLOW + "Appel en cours vers ";
    public static final String INCOMING_CALL = PREFIX + ChatColor.YELLOW + "Appel entrant de ";
    public static final String CALL_CONNECTED = PREFIX + ChatColor.GREEN + "Appel connecte!";
    public static final String CALL_ENDED = PREFIX + ChatColor.GRAY + "Appel termine.";
    public static final String CALL_MISSED = PREFIX + ChatColor.GRAY + "Appel manque.";
    public static final String CALL_REJECTED = PREFIX + ChatColor.GRAY + "Appel rejete.";
    public static final String CALL_NO_ANSWER = PREFIX + ChatColor.GRAY + "Pas de reponse.";
    public static final String CALL_DISCONNECTED_DEATH = PREFIX + ChatColor.RED + "Appel coupe - vous etes mort.";
    public static final String CALL_DISCONNECTED_NO_PHONE = PREFIX + ChatColor.RED + "Appel coupe - telephone retire.";
    public static final String CREDITS_DEPLETED = PREFIX + ChatColor.RED + "Credits epuises - appel termine.";

    // === SMS ===
    public static final String SMS_SENT = PREFIX_SMS + ChatColor.GREEN + "SMS envoye a ";
    public static final String SMS_RECEIVED = PREFIX_SMS + "Nouveau message de ";
    public static final String SMS_WRITE_PROMPT = PREFIX_SMS + ChatColor.YELLOW + "Ecrivez votre message:";
    public static final String SMS_CANCEL_HINT = ChatColor.GRAY + "(tapez 'annuler' pour annuler)";
    public static final String ACTION_CANCELLED = PREFIX + ChatColor.YELLOW + "Action annulee.";

    // === CONTACTS ===
    public static final String CONTACT_ADDED = PREFIX + ChatColor.GREEN + "Contact ajoute: ";
    public static final String CONTACT_DELETED = PREFIX + ChatColor.YELLOW + "Contact supprime.";
    public static final String ENTER_CONTACT_NAME = PREFIX + ChatColor.YELLOW + "Entrez le nom du contact:";

    // === FORFAITS ===
    public static final String PLAN_RECHARGED = PREFIX + ChatColor.GREEN + "Forfait applique! +";
    public static final String NO_PHONE_FOR_PLAN = PREFIX + ChatColor.RED + "Vous devez avoir un telephone.";
    public static final String RECHARGE_FAILED = PREFIX + ChatColor.RED + "Erreur lors de la recharge.";

    // === MUSIQUE ===
    public static final String MUSIC_STARTED = PREFIX + ChatColor.GREEN + "Musique lancee: ";
    public static final String MUSIC_STOPPED = PREFIX + ChatColor.GRAY + "Musique arretee.";
    public static final String MUSIC_STOPPED_NO_PHONE = PREFIX + ChatColor.GRAY + "Musique arretee - telephone retire.";

    // === PARAMETRES ===
    public static final String SILENT_MODE_ON = PREFIX + ChatColor.YELLOW + "Mode silencieux active.";
    public static final String SILENT_MODE_OFF = PREFIX + ChatColor.GREEN + "Mode silencieux desactive.";
    public static final String NUMBER_BLOCKED_SUCCESS = PREFIX + ChatColor.YELLOW + "Numero bloque: ";
    public static final String NUMBER_UNBLOCKED = PREFIX + ChatColor.GREEN + "Numero debloque.";
    public static final String NUMBER_ALREADY_BLOCKED = PREFIX + ChatColor.RED + "Ce numero est deja bloque.";

    // === INFO ===
    public static final String YOUR_NUMBER = PREFIX + "Votre numero: " + ChatColor.GREEN;
    public static final String CREDITS_BALANCE = PREFIX + "Credits: " + ChatColor.YELLOW;

    // === METHODES UTILITAIRES ===

    public static String calling(String number) {
        return CALLING + ChatColor.WHITE + number + ChatColor.YELLOW + "...";
    }

    public static String incomingCall(String caller) {
        return INCOMING_CALL + ChatColor.WHITE + caller + ChatColor.YELLOW + "...";
    }

    public static String smsSent(String number) {
        return SMS_SENT + ChatColor.WHITE + number;
    }

    public static String smsReceived(String sender) {
        return SMS_RECEIVED + ChatColor.WHITE + sender;
    }

    public static String contactAdded(String name, String number) {
        return CONTACT_ADDED + ChatColor.WHITE + name + ChatColor.GRAY + " (" + number + ")";
    }

    public static String planRecharged(int credits, String planName) {
        return PLAN_RECHARGED + ChatColor.WHITE + credits + " credits" + ChatColor.GRAY + " (" + planName + ")";
    }

    public static String yourNumber(String number) {
        return YOUR_NUMBER + number;
    }

    public static String creditsBalance(int credits) {
        return CREDITS_BALANCE + credits + " credits";
    }

    public static String creditsDeducted(int amount) {
        return ChatColor.GRAY + "[-" + amount + " credits]";
    }

    public static String musicStarted(String trackName) {
        return MUSIC_STARTED + ChatColor.WHITE + trackName;
    }

    public static String numberBlocked(String number) {
        return NUMBER_BLOCKED_SUCCESS + ChatColor.WHITE + number;
    }
}
