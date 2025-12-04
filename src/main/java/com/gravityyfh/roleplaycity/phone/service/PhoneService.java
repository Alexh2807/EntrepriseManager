package com.gravityyfh.roleplaycity.phone.service;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.phone.PhoneManager;
import com.gravityyfh.roleplaycity.phone.model.*;
import com.gravityyfh.roleplaycity.phone.repository.PhoneRepository;
import com.gravityyfh.roleplaycity.phone.PhoneMessages;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service de gestion des telephones.
 * Gere la logique metier: SMS, appels, credits, etc.
 */
public class PhoneService {

    private final RoleplayCity plugin;
    private final PhoneManager phoneManager;
    private final PhoneRepository phoneRepository;

    // Cache des comptes pour eviter les requetes repetees
    private final Map<UUID, PhoneAccount> accountCache = new ConcurrentHashMap<>();

    // Appels actifs (callerUUID -> ActiveCall)
    private final Map<UUID, ActiveCall> activeCalls = new ConcurrentHashMap<>();

    // Mapping numero -> UUID pour les appels entrants
    private final Map<String, UUID> numberToUuid = new ConcurrentHashMap<>();

    public PhoneService(RoleplayCity plugin, PhoneManager phoneManager, PhoneRepository phoneRepository) {
        this.plugin = plugin;
        this.phoneManager = phoneManager;
        this.phoneRepository = phoneRepository;
    }

    // ==================== COMPTE TELEPHONE ====================

    /**
     * Recupere ou cree le compte telephone d'un joueur.
     */
    public PhoneAccount getOrCreateAccount(Player player) {
        UUID uuid = player.getUniqueId();

        // Verifier le cache
        PhoneAccount cached = accountCache.get(uuid);
        if (cached != null) {
            return cached;
        }

        // Recuperer depuis la BDD
        PhoneAccount account = phoneRepository.getAccountByUuid(uuid);

        if (account == null) {
            // Creer un nouveau compte
            account = phoneRepository.createAccount(uuid, player.getName());
            if (account != null) {
                plugin.getLogger().info("Nouveau compte telephone cree pour " + player.getName() + ": " + account.getPhoneNumber());
            }
        } else {
            // Mettre a jour le nom si necessaire
            if (!account.getOwnerName().equals(player.getName())) {
                phoneRepository.updateOwnerName(uuid, player.getName());
                account = new PhoneAccount(account.getId(), uuid, player.getName(),
                    account.getPhoneNumber(), account.getCreatedAt(), System.currentTimeMillis());
            }
        }

        if (account != null) {
            accountCache.put(uuid, account);
            numberToUuid.put(account.getPhoneNumber(), uuid);
        }

        return account;
    }

    /**
     * Recupere un compte par numero de telephone.
     */
    public PhoneAccount getAccountByNumber(String phoneNumber) {
        // Chercher dans le cache d'abord
        UUID uuid = numberToUuid.get(phoneNumber);
        if (uuid != null) {
            PhoneAccount cached = accountCache.get(uuid);
            if (cached != null) {
                return cached;
            }
        }

        // Sinon chercher en BDD
        return phoneRepository.getAccountByPhoneNumber(phoneNumber);
    }

    /**
     * Recupere le numero de telephone d'un joueur.
     */
    public String getPhoneNumber(Player player) {
        PhoneAccount account = getOrCreateAccount(player);
        return account != null ? account.getPhoneNumber() : null;
    }

    /**
     * Vide le cache d'un joueur (deconnexion).
     */
    public void clearCache(UUID uuid) {
        PhoneAccount account = accountCache.remove(uuid);
        if (account != null) {
            numberToUuid.remove(account.getPhoneNumber());
        }
    }

    /**
     * Verifie si un numero de telephone existe.
     */
    public boolean phoneNumberExists(String number) {
        return getAccountByNumber(number) != null;
    }

    // ==================== SMS ====================

    /**
     * Resultat de l'envoi d'un SMS.
     */
    public enum SmsResult {
        SUCCESS,
        NO_PHONE,
        INSUFFICIENT_CREDITS,
        INVALID_NUMBER,
        SELF_MESSAGE,
        MESSAGE_TOO_LONG
    }

    /**
     * Envoie un SMS (avec messages d'erreur).
     */
    public boolean sendSms(Player sender, String recipientNumber, String content) {
        // Verifier que le joueur a un telephone
        ItemStack phone = phoneManager.findPhoneInInventory(sender);
        if (phone == null) {
            sender.sendMessage(ChatColor.RED + "[Telephone] Vous n'avez pas de telephone.");
            return false;
        }

        // Verifier le numero destinataire
        PhoneAccount recipientAccount = getAccountByNumber(recipientNumber);
        if (recipientAccount == null) {
            sender.sendMessage(ChatColor.RED + "[Telephone] Ce numero n'existe pas.");
            return false;
        }

        // Verifier que ce n'est pas soi-meme
        PhoneAccount senderAccount = getOrCreateAccount(sender);
        if (senderAccount.getPhoneNumber().equals(recipientNumber)) {
            sender.sendMessage(ChatColor.RED + "[Telephone] Vous ne pouvez pas vous envoyer un SMS.");
            return false;
        }

        // Verifier la longueur du message
        int maxLength = phoneManager.getMaxSmsLength();
        if (content.length() > maxLength) {
            sender.sendMessage(ChatColor.RED + "[Telephone] Message trop long (max " + maxLength + " caracteres).");
            return false;
        }

        // Verifier les credits
        int smsCost = phoneManager.getSmsCost();
        int currentCredits = phoneManager.getCredits(phone);
        if (currentCredits < smsCost) {
            sender.sendMessage(ChatColor.RED + "[Telephone] Credits insuffisants pour envoyer ce SMS.");
            return false;
        }

        // Deduire les credits
        phoneManager.deductCredits(phone, smsCost);

        // Incrementer les stats
        senderAccount.incrementTotalSms();

        // Enregistrer le message
        Message message = phoneRepository.sendMessage(senderAccount.getPhoneNumber(), recipientNumber, content);

        if (message != null) {
            // Notifier le destinataire s'il est en ligne
            notifySmsReceived(recipientAccount.getOwnerUuid(), senderAccount);

            // Feedback a l'expediteur
            sender.playSound(sender.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
        }

        return true;
    }

    /**
     * Notifie un joueur qu'il a recu un SMS.
     */
    private void notifySmsReceived(UUID recipientUuid, PhoneAccount senderAccount) {
        Player recipient = Bukkit.getPlayer(recipientUuid);
        if (recipient != null && recipient.isOnline()) {
            // Verifier le mode silencieux
            PhoneAccount recipientAccount = accountCache.get(recipientUuid);
            if (recipientAccount != null && recipientAccount.isSilentMode()) {
                // Mode silencieux actif - pas de son
                recipient.sendMessage(phoneManager.getMessage("sms_received")
                    .replace("{sender}", getContactDisplayName(recipientUuid, senderAccount.getPhoneNumber())));
                return;
            }

            // Chercher le nom du contact ou utiliser le numero
            String displayName = getContactDisplayName(recipientUuid, senderAccount.getPhoneNumber());
            if (displayName == null) {
                displayName = senderAccount.getPhoneNumber();
            }

            recipient.playSound(recipient.getLocation(),
                Sound.valueOf(phoneManager.getSmsNotificationSound()), 1.0f, 1.0f);
            recipient.sendMessage(phoneManager.getMessage("sms_received")
                .replace("{sender}", displayName));
        }
    }

    /**
     * Recupere les messages recus d'un joueur.
     */
    public List<Message> getReceivedMessages(Player player) {
        PhoneAccount account = getOrCreateAccount(player);
        if (account == null) return Collections.emptyList();
        return phoneRepository.getReceivedMessages(account.getPhoneNumber());
    }

    /**
     * Recupere les messages envoyes d'un joueur.
     */
    public List<Message> getSentMessages(Player player) {
        PhoneAccount account = getOrCreateAccount(player);
        if (account == null) return Collections.emptyList();
        return phoneRepository.getSentMessages(account.getPhoneNumber());
    }

    /**
     * Recupere la conversation avec un numero.
     */
    public List<Message> getConversation(Player player, String otherNumber) {
        PhoneAccount account = getOrCreateAccount(player);
        if (account == null) return Collections.emptyList();
        return phoneRepository.getConversation(account.getPhoneNumber(), otherNumber);
    }

    /**
     * Compte les messages non lus.
     */
    public int countUnreadMessages(Player player) {
        PhoneAccount account = getOrCreateAccount(player);
        if (account == null) return 0;
        return phoneRepository.countUnreadMessages(account.getPhoneNumber());
    }

    /**
     * Marque une conversation comme lue.
     */
    public void markConversationAsRead(Player player, String senderNumber) {
        PhoneAccount account = getOrCreateAccount(player);
        if (account != null) {
            phoneRepository.markConversationAsRead(account.getPhoneNumber(), senderNumber);
        }
    }

    /**
     * Recupere toutes les conversations groupees par interlocuteur.
     * Chaque element represente le dernier message d'une conversation.
     */
    public List<Message> getAllConversationsGrouped(Player player) {
        PhoneAccount account = getOrCreateAccount(player);
        if (account == null) return Collections.emptyList();
        return phoneRepository.getAllConversationsGrouped(account.getPhoneNumber());
    }

    /**
     * Compte les messages non lus dans une conversation specifique.
     */
    public int countUnreadInConversation(Player player, String otherNumber) {
        PhoneAccount account = getOrCreateAccount(player);
        if (account == null) return 0;
        return phoneRepository.countUnreadInConversation(account.getPhoneNumber(), otherNumber);
    }

    // ==================== CONTACTS ====================

    /**
     * Ajoute un contact.
     */
    public boolean addContact(Player player, String number, String name) {
        // Verifier que le numero existe
        if (getAccountByNumber(number) == null) {
            return false;
        }

        // Verifier que le contact n'existe pas deja
        if (phoneRepository.contactExists(player.getUniqueId(), number)) {
            return false;
        }

        Contact contact = phoneRepository.addContact(player.getUniqueId(), name, number);
        return contact != null;
    }

    /**
     * Verifie si un joueur a deja ce numero en contact.
     */
    public boolean hasContact(Player player, String number) {
        return phoneRepository.contactExists(player.getUniqueId(), number);
    }

    /**
     * Recupere les contacts d'un joueur.
     */
    public List<Contact> getContacts(Player player) {
        return phoneRepository.getContacts(player.getUniqueId());
    }

    /**
     * Supprime un contact par ID.
     */
    public void deleteContact(Player player, int contactId) {
        phoneRepository.deleteContact(contactId);
    }

    /**
     * Recupere le nom d'affichage d'un numero (contact ou numero brut).
     */
    public String getContactDisplayName(UUID ownerUuid, String phoneNumber) {
        Contact contact = phoneRepository.getContactByNumber(ownerUuid, phoneNumber);
        if (contact != null) {
            return contact.getContactName();
        }
        // Chercher le nom du proprietaire du numero
        PhoneAccount account = getAccountByNumber(phoneNumber);
        if (account != null) {
            return account.getOwnerName();
        }
        return null;
    }

    // ==================== APPELS ====================

    /**
     * Resultat d'un appel.
     */
    public enum CallResult {
        SUCCESS,
        NO_PHONE,
        PHONE_NOT_IN_HAND,
        INSUFFICIENT_CREDITS,
        INVALID_NUMBER,
        SELF_CALL,
        TARGET_OFFLINE,
        TARGET_BUSY,
        CALLER_BUSY,
        NO_OPENAUDIOMC,
        TARGET_NO_OPENAUDIOMC
    }

    /**
     * Initie un appel (avec messages d'erreur).
     */
    public void initiateCall(Player caller, String targetNumber) {
        // Verifier que le joueur a un telephone EN MAIN
        if (!phoneManager.hasPhoneInHand(caller)) {
            caller.sendMessage(PhoneMessages.PHONE_NOT_IN_HAND);
            return;
        }

        ItemStack phone = caller.getInventory().getItemInMainHand();

        // Verifier le numero cible
        PhoneAccount targetAccount = getAccountByNumber(targetNumber);
        if (targetAccount == null) {
            caller.sendMessage(PhoneMessages.INVALID_NUMBER);
            return;
        }

        // Verifier que ce n'est pas soi-meme
        PhoneAccount callerAccount = getOrCreateAccount(caller);
        if (callerAccount.getPhoneNumber().equals(targetNumber)) {
            caller.sendMessage(PhoneMessages.SELF_CALL);
            return;
        }

        // Verifier que l'appelant n'est pas deja en appel
        if (activeCalls.containsKey(caller.getUniqueId())) {
            caller.sendMessage(PhoneMessages.ALREADY_IN_CALL);
            return;
        }

        // Verifier que le destinataire est en ligne
        Player target = Bukkit.getPlayer(targetAccount.getOwnerUuid());
        if (target == null || !target.isOnline()) {
            phoneRepository.recordCall(callerAccount.getPhoneNumber(), targetNumber,
                CallRecord.CallStatus.MISSED, null);
            caller.sendMessage(PhoneMessages.TARGET_OFFLINE);
            return;
        }

        // Verifier que le destinataire n'est pas deja en appel
        if (isInCall(target.getUniqueId())) {
            caller.sendMessage(PhoneMessages.TARGET_BUSY);
            return;
        }

        // Verifier OpenAudioMc pour les deux joueurs (si disponible)
        CallService callService = plugin.getCallService();
        if (callService != null && callService.isAvailable()) {
            // L'appelant doit etre connecte au voice chat
            if (!callService.isPlayerInVoiceChat(caller)) {
                caller.sendMessage(PhoneMessages.NOT_CONNECTED_AUDIO);
                return;
            }
            // Le destinataire doit aussi etre connecte au voice chat
            if (!callService.isPlayerInVoiceChat(target)) {
                caller.sendMessage(PhoneMessages.TARGET_NOT_CONNECTED_AUDIO);
                return;
            }
        }

        // Verifier si le numero est bloque
        PhoneAccount targetPlayerAccount = getOrCreateAccount(target);
        if (targetPlayerAccount.isBlocked(callerAccount.getPhoneNumber())) {
            phoneRepository.recordCall(callerAccount.getPhoneNumber(), targetNumber,
                CallRecord.CallStatus.MISSED, null);
            caller.sendMessage(PhoneMessages.NUMBER_BLOCKED);
            return;
        }

        // Verifier les credits
        int callCostPerMinute = phoneManager.getCallCostPerMinute();
        int currentCredits = phoneManager.getCredits(phone);
        if (currentCredits < callCostPerMinute) {
            caller.sendMessage(PhoneMessages.NO_CREDITS);
            return;
        }

        // Incrementer les stats
        callerAccount.incrementTotalCalls();

        // Creer l'appel actif
        ActiveCall call = new ActiveCall(
            caller.getUniqueId(),
            target.getUniqueId(),
            callerAccount.getPhoneNumber(),
            targetNumber
        );
        call.setState(ActiveCall.CallState.RINGING);

        // Stocker l'appel pour les deux joueurs
        activeCalls.put(caller.getUniqueId(), call);
        activeCalls.put(target.getUniqueId(), call);

        // Notifier le destinataire (sauf si mode silencieux)
        if (!targetPlayerAccount.isSilentMode()) {
            target.playSound(target.getLocation(),
                Sound.valueOf(phoneManager.getRingtoneSound()), 1.0f, 1.0f);
        }

        String callerDisplayName = getContactDisplayName(target.getUniqueId(), callerAccount.getPhoneNumber());
        if (callerDisplayName == null) {
            callerDisplayName = callerAccount.getPhoneNumber();
        }

        target.sendMessage(PhoneMessages.incomingCall(callerDisplayName));

        // Feedback a l'appelant
        caller.sendMessage(PhoneMessages.calling(targetNumber));

        // Timer pour l'expiration de la sonnerie
        int ringTimeout = phoneManager.getRingTimeout();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ActiveCall activeCall = activeCalls.get(caller.getUniqueId());
            if (activeCall != null && activeCall.getState() == ActiveCall.CallState.RINGING) {
                endCall(caller.getUniqueId(), CallRecord.CallStatus.MISSED);
            }
        }, ringTimeout * 20L);
    }

    /**
     * Accepte un appel entrant.
     */
    public boolean acceptCall(Player player) {
        ActiveCall call = activeCalls.get(player.getUniqueId());
        if (call == null || call.getState() != ActiveCall.CallState.RINGING) {
            return false;
        }

        // Verifier que le joueur a un telephone en main
        if (!phoneManager.hasPhoneInHand(player)) {
            player.sendMessage(PhoneMessages.PHONE_NOT_IN_HAND);
            return false;
        }

        // Recuperer l'appelant
        Player caller = Bukkit.getPlayer(call.getCallerUuid());
        if (caller == null || !caller.isOnline()) {
            // L'appelant s'est deconnecte
            endCall(player.getUniqueId(), CallRecord.CallStatus.MISSED);
            return false;
        }

        // Demarrer l'appel - utiliser connect() pour definir connectedTime correctement
        call.connect();

        // Connecter les joueurs via OpenAudioMc si disponible
        CallService callService = plugin.getCallService();
        if (callService != null && callService.isAvailable()) {
            boolean voiceConnected = callService.connectCall(caller, player);
            if (voiceConnected) {
                caller.sendMessage(PhoneMessages.VOICE_CONNECTED);
                player.sendMessage(PhoneMessages.VOICE_CONNECTED);
            } else {
                // Si la connexion vocale echoue, l'appel continue en mode texte
                caller.sendMessage(PhoneMessages.VOICE_UNAVAILABLE);
                player.sendMessage(PhoneMessages.VOICE_UNAVAILABLE);
            }
        }

        // Notifier les deux joueurs
        caller.sendMessage(PhoneMessages.CALL_CONNECTED);
        caller.playSound(caller.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);

        player.sendMessage(PhoneMessages.CALL_CONNECTED);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);

        // Demarrer la facturation periodique
        startBillingTask(call);

        return true;
    }

    /**
     * Refuse un appel entrant.
     */
    public boolean rejectCall(Player player) {
        ActiveCall call = activeCalls.get(player.getUniqueId());
        if (call == null || call.getState() != ActiveCall.CallState.RINGING) {
            return false;
        }

        endCall(player.getUniqueId(), CallRecord.CallStatus.REJECTED);
        return true;
    }

    /**
     * Termine un appel en cours.
     */
    public void hangUp(Player player) {
        ActiveCall call = activeCalls.get(player.getUniqueId());
        if (call == null) {
            return;
        }

        CallRecord.CallStatus status = call.getState() == ActiveCall.CallState.CONNECTED
            ? CallRecord.CallStatus.COMPLETED
            : CallRecord.CallStatus.MISSED;

        endCall(player.getUniqueId(), status);
    }

    /**
     * Termine un appel avec un statut specifique.
     */
    private void endCall(UUID playerUuid, CallRecord.CallStatus status) {
        ActiveCall call = activeCalls.remove(playerUuid);
        if (call == null) return;

        // Retirer l'appel pour l'autre participant
        UUID otherUuid = call.getCallerUuid().equals(playerUuid)
            ? call.getCalleeUuid() : call.getCallerUuid();
        activeCalls.remove(otherUuid);

        // Annuler la tache de facturation
        call.cancelBillingTask();

        // Deconnecter OpenAudioMc si l'appel etait connecte
        if (call.getConnectedTime() > 0) {
            CallService callService = plugin.getCallService();
            if (callService != null && callService.isAvailable()) {
                callService.disconnectCall(call.getCallerUuid(), call.getCalleeUuid());
            }
        }

        // Calculer la duree
        Long duration = null;
        if (call.getConnectedTime() > 0) {
            duration = (System.currentTimeMillis() - call.getConnectedTime()) / 1000;
        }

        // Enregistrer l'appel
        phoneRepository.recordCall(call.getCallerNumber(), call.getCalleeNumber(), status, duration);

        // Notifier les joueurs
        Player caller = Bukkit.getPlayer(call.getCallerUuid());
        Player callee = Bukkit.getPlayer(call.getCalleeUuid());

        String endMessage;
        switch (status) {
            case COMPLETED:
                endMessage = PhoneMessages.CALL_ENDED;
                break;
            case MISSED:
                endMessage = PhoneMessages.CALL_MISSED;
                break;
            case REJECTED:
                endMessage = PhoneMessages.CALL_REJECTED;
                break;
            default:
                endMessage = PhoneMessages.CALL_ENDED;
        }

        if (caller != null) {
            caller.sendMessage(endMessage);
            caller.playSound(caller.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
        }
        if (callee != null) {
            callee.sendMessage(endMessage);
            callee.playSound(callee.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
        }
    }

    /**
     * Demarre la tache de facturation periodique.
     */
    private void startBillingTask(ActiveCall call) {
        int callCostPerMinute = phoneManager.getCallCostPerMinute();

        call.setBillingTaskId(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Verifier que l'appel est toujours actif
            if (!activeCalls.containsKey(call.getCallerUuid()) ||
                activeCalls.get(call.getCallerUuid()).getState() != ActiveCall.CallState.CONNECTED) {
                call.cancelBillingTask();
                return;
            }

            Player caller = Bukkit.getPlayer(call.getCallerUuid());
            if (caller == null) {
                endCall(call.getCallerUuid(), CallRecord.CallStatus.COMPLETED);
                return;
            }

            ItemStack phone = phoneManager.findPhoneInInventory(caller);
            if (phone == null) {
                endCall(call.getCallerUuid(), CallRecord.CallStatus.COMPLETED);
                return;
            }

            int currentCredits = phoneManager.getCredits(phone);
            if (currentCredits < callCostPerMinute) {
                caller.sendMessage(PhoneMessages.CREDITS_DEPLETED);
                endCall(call.getCallerUuid(), CallRecord.CallStatus.COMPLETED);
                return;
            }

            phoneManager.deductCredits(phone, callCostPerMinute);
            caller.sendMessage(PhoneMessages.creditsDeducted(callCostPerMinute));

        }, 20L * 60, 20L * 60).getTaskId());
    }

    /**
     * Verifie si un joueur est en appel.
     */
    public boolean isInCall(UUID playerUuid) {
        return activeCalls.containsKey(playerUuid);
    }

    /**
     * Recupere l'appel actif d'un joueur.
     */
    public ActiveCall getActiveCall(UUID playerUuid) {
        return activeCalls.get(playerUuid);
    }

    /**
     * Recupere l'historique des appels.
     */
    public List<CallRecord> getCallHistory(Player player, int limit) {
        PhoneAccount account = getOrCreateAccount(player);
        if (account == null) return Collections.emptyList();
        return phoneRepository.getCallHistory(account.getPhoneNumber(), limit);
    }

    // ==================== PARAMETRES ====================

    /**
     * Active/desactive le mode silencieux.
     */
    public void toggleSilentMode(Player player) {
        PhoneAccount account = getOrCreateAccount(player);
        if (account != null) {
            account.toggleSilentMode();
            String status = account.isSilentMode() ? "active" : "desactive";
            player.sendMessage(ChatColor.YELLOW + "[Telephone] " + ChatColor.WHITE + "Mode silencieux " + status + ".");
        }
    }

    /**
     * Bloque un numero.
     */
    public boolean blockNumber(Player player, String number) {
        PhoneAccount account = getOrCreateAccount(player);
        if (account != null && !account.isBlocked(number)) {
            account.blockNumber(number);
            return true;
        }
        return false;
    }

    /**
     * Debloque un numero.
     */
    public void unblockNumber(Player player, String number) {
        PhoneAccount account = getOrCreateAccount(player);
        if (account != null) {
            account.unblockNumber(number);
        }
    }

    /**
     * Change le numero de telephone d'un joueur.
     */
    public boolean changePhoneNumber(Player player, int cost) {
        ItemStack phone = phoneManager.findPhoneInInventory(player);
        if (phone == null) {
            player.sendMessage(ChatColor.RED + "[Telephone] Aucun telephone trouve.");
            return false;
        }

        int credits = phoneManager.getCredits(phone);
        if (credits < cost) {
            player.sendMessage(ChatColor.RED + "[Telephone] Credits insuffisants.");
            return false;
        }

        // Generer un nouveau numero via le repository
        PhoneAccount account = getOrCreateAccount(player);
        String oldNumber = account.getPhoneNumber();

        // Generer et mettre a jour
        String newNumber = phoneRepository.generateNewPhoneNumber();
        if (newNumber == null) {
            player.sendMessage(ChatColor.RED + "[Telephone] Erreur lors de la generation du numero.");
            return false;
        }

        if (phoneRepository.updateAccountPhoneNumber(player.getUniqueId(), newNumber)) {
            // Mettre a jour le cache
            numberToUuid.remove(oldNumber);
            numberToUuid.put(newNumber, player.getUniqueId());
            account.setPhoneNumber(newNumber);

            // Mettre a jour l'item telephone via PDC
            phoneManager.updatePhoneNumber(phone, newNumber);

            // Deduire les credits
            phoneManager.deductCredits(phone, cost);

            return true;
        }

        return false;
    }

    // ==================== CREDITS ====================

    /**
     * Recharge un telephone avec un forfait.
     */
    public boolean rechargePlan(Player player, ItemStack phone, ItemStack plan) {
        if (!phoneManager.isPhone(phone) || !phoneManager.isPlan(plan)) {
            return false;
        }

        PlanType planType = phoneManager.getPlanType(plan);
        if (planType == null) {
            return false;
        }

        // Ajouter les credits
        phoneManager.addCredits(phone, planType.getCredits());

        // Consommer le forfait
        plan.setAmount(plan.getAmount() - 1);

        // Feedback
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        player.sendMessage(PhoneMessages.planRecharged(planType.getCredits(), planType.getDisplayName()));

        return true;
    }

    /**
     * Recupere les credits d'un telephone.
     */
    public int getCredits(ItemStack phone) {
        return phoneManager.getCredits(phone);
    }

    // ==================== UTILITAIRES ====================

    /**
     * Verifie si OpenAudioMc est disponible.
     */
    public boolean isOpenAudioMcAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("OpenAudioMc");
    }

    /**
     * Termine tous les appels d'un joueur (deconnexion).
     */
    public void handlePlayerDisconnect(UUID playerUuid) {
        ActiveCall call = activeCalls.get(playerUuid);
        if (call != null) {
            endCall(playerUuid, CallRecord.CallStatus.MISSED);
        }
        clearCache(playerUuid);
    }
}
