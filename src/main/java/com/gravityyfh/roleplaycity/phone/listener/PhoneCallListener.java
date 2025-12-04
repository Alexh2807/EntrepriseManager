package com.gravityyfh.roleplaycity.phone.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.phone.PhoneManager;
import com.gravityyfh.roleplaycity.phone.PhoneMessages;
import com.gravityyfh.roleplaycity.phone.gui.PhoneIncomingCallGUI;
import com.gravityyfh.roleplaycity.phone.model.ActiveCall;
import com.gravityyfh.roleplaycity.phone.model.PhoneAccount;
import com.gravityyfh.roleplaycity.phone.service.CallService;
import com.gravityyfh.roleplaycity.phone.service.PhoneService;
import fr.minuskube.inv.SmartInventory;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener pour les evenements d'appels telephoniques.
 * Gere les notifications d'appels entrants et la sonnerie.
 */
public class PhoneCallListener {

    private final RoleplayCity plugin;
    private final PhoneManager phoneManager;
    private final PhoneService phoneService;
    private final CallService callService;

    // Taches de sonnerie par joueur
    private final Map<UUID, BukkitTask> ringingTasks = new ConcurrentHashMap<>();

    public PhoneCallListener(RoleplayCity plugin) {
        this.plugin = plugin;
        this.phoneManager = plugin.getPhoneManager();
        this.phoneService = plugin.getPhoneService();
        this.callService = plugin.getCallService();
    }

    /**
     * Demarre la sonnerie pour un appel entrant.
     */
    public void startRinging(Player recipient, String callerNumber, String callerName) {
        UUID uuid = recipient.getUniqueId();

        // Arreter toute sonnerie existante
        stopRinging(uuid);

        // Ouvrir le GUI d'appel entrant
        PhoneIncomingCallGUI gui = new PhoneIncomingCallGUI(plugin, callerNumber, callerName);
        SmartInventory inv = gui.getInventory();
        if (inv != null) {
            inv.open(recipient);
        }

        // Demarrer la sonnerie periodique
        BukkitTask task = new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                // Verifier si l'appel est toujours actif et en sonnerie
                ActiveCall call = phoneService.getActiveCall(uuid);
                if (call == null || call.getState() != ActiveCall.CallState.RINGING) {
                    cancel();
                    ringingTasks.remove(uuid);
                    return;
                }

                // Jouer le son de sonnerie
                recipient.playSound(recipient.getLocation(),
                    Sound.valueOf(phoneManager.getRingtoneSound()), 1.0f, 1.0f);

                count++;

                // Arreter apres un certain nombre de sonneries
                if (count >= 10) { // 10 sonneries max
                    cancel();
                    ringingTasks.remove(uuid);
                }
            }
        }.runTaskTimer(plugin, 0L, 40L); // Toutes les 2 secondes

        ringingTasks.put(uuid, task);
    }

    /**
     * Arrete la sonnerie pour un joueur.
     */
    public void stopRinging(UUID uuid) {
        BukkitTask task = ringingTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Notifie un joueur d'un appel entrant.
     */
    public void notifyIncomingCall(Player recipient, Player caller) {
        PhoneAccount callerAccount = phoneService.getOrCreateAccount(caller);
        if (callerAccount == null) return;

        String callerNumber = callerAccount.getPhoneNumber();
        String callerDisplayName = phoneService.getContactDisplayName(recipient.getUniqueId(), callerNumber);
        if (callerDisplayName == null) {
            callerDisplayName = callerNumber;
        }

        // Demarrer la sonnerie et afficher le GUI
        startRinging(recipient, callerNumber, callerDisplayName);
    }

    /**
     * Gere l'acceptation d'un appel.
     * Connecte les deux joueurs via OpenAudioMc si disponible.
     */
    public void handleCallAccepted(Player caller, Player callee) {
        // Arreter la sonnerie
        stopRinging(callee.getUniqueId());

        // Connecter les joueurs via OpenAudioMc si disponible
        if (callService != null && callService.isAvailable()) {
            boolean connected = callService.connectCall(caller, callee);
            if (connected) {
                caller.sendMessage(PhoneMessages.VOICE_CONNECTED);
                callee.sendMessage(PhoneMessages.VOICE_CONNECTED);
            } else {
                // Si la connexion vocale echoue, l'appel continue en mode texte
                caller.sendMessage(PhoneMessages.VOICE_UNAVAILABLE);
                callee.sendMessage(PhoneMessages.VOICE_UNAVAILABLE);
            }
        }
    }

    /**
     * Gere la fin d'un appel.
     * Deconnecte les joueurs d'OpenAudioMc.
     */
    public void handleCallEnded(UUID callerUuid, UUID calleeUuid) {
        // Arreter les sonneries
        stopRinging(callerUuid);
        stopRinging(calleeUuid);

        // Deconnecter les joueurs d'OpenAudioMc
        if (callService != null && callService.isAvailable()) {
            callService.disconnectCall(callerUuid, calleeUuid);
        }

        // Fermer les GUIs d'appel entrant si ouverts
        Player caller = Bukkit.getPlayer(callerUuid);
        Player callee = Bukkit.getPlayer(calleeUuid);

        if (caller != null) {
            // Verifier si le joueur a un GUI d'appel ouvert
            closeCallGuiIfOpen(caller);
        }
        if (callee != null) {
            closeCallGuiIfOpen(callee);
        }
    }

    /**
     * Ferme le GUI d'appel si ouvert.
     */
    private void closeCallGuiIfOpen(Player player) {
        String title = player.getOpenInventory().getTitle();
        if (title.contains("Appel entrant") || title.contains("En appel")) {
            player.closeInventory();
        }
    }

    /**
     * Nettoie toutes les sonneries (shutdown du plugin).
     */
    public void cleanup() {
        for (BukkitTask task : ringingTasks.values()) {
            task.cancel();
        }
        ringingTasks.clear();
    }
}
