package com.gravityyfh.roleplaycity.phone.service;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.phone.PhoneManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Service de gestion des appels vocaux via OpenAudioMc.
 * Gere la connexion vocale entre deux joueurs pendant un appel.
 *
 * Utilise l'API officielle OpenAudioMc:
 * - ClientApi.getInstance().getClient(uuid) pour obtenir un Client
 * - client.isConnected() pour verifier la connexion web
 * - client.hasVoicechatEnabled() pour verifier le voice chat
 * - VoiceApi.getInstance().addStaticPeer(client1, client2, ...) pour connecter
 */
public class CallService {

    private final RoleplayCity plugin;
    private final PhoneManager phoneManager;
    private boolean openAudioMcAvailable = false;

    public CallService(RoleplayCity plugin, PhoneManager phoneManager) {
        this.plugin = plugin;
        this.phoneManager = phoneManager;

        // Verifier si OpenAudioMc est disponible
        checkOpenAudioMc();
    }

    /**
     * Verifie si OpenAudioMc est disponible avec l'API correcte.
     */
    private void checkOpenAudioMc() {
        if (Bukkit.getPluginManager().isPluginEnabled("OpenAudioMc")) {
            try {
                // Verifier si ClientApi existe (API moderne)
                Class.forName("com.craftmend.openaudiomc.api.ClientApi");
                Class.forName("com.craftmend.openaudiomc.api.VoiceApi");
                openAudioMcAvailable = true;
                plugin.getLogger().info("[Phone] OpenAudioMc API disponible");
            } catch (ClassNotFoundException e) {
                // Essayer l'ancienne API
                try {
                    Class.forName("com.craftmend.openaudiomc.api.interfaces.Client");
                    openAudioMcAvailable = true;
                    plugin.getLogger().info("[Phone] OpenAudioMc API (ancienne version) disponible");
                } catch (ClassNotFoundException e2) {
                    plugin.getLogger().warning("[Phone] OpenAudioMc API non disponible");
                    openAudioMcAvailable = false;
                }
            }
        } else {
            plugin.getLogger().info("[Phone] OpenAudioMc non installe - appels en mode texte uniquement");
        }
    }

    /**
     * Verifie si le service vocal est disponible.
     */
    public boolean isAvailable() {
        return openAudioMcAvailable;
    }

    /**
     * Verifie si un joueur est connecte au client web ET a le voice chat actif.
     * Utilise ClientApi.getInstance().getClient(uuid).hasVoicechatEnabled()
     */
    public boolean isPlayerInVoiceChat(Player player) {
        if (!openAudioMcAvailable) {
            return false;
        }

        try {
            // Obtenir le Client via ClientApi
            Class<?> clientApiClass = Class.forName("com.craftmend.openaudiomc.api.ClientApi");
            Object clientApi = clientApiClass.getMethod("getInstance").invoke(null);

            // getClient(UUID) retourne un Client (peut etre null si pas charge)
            Object client = clientApi.getClass().getMethod("getClient", UUID.class)
                .invoke(clientApi, player.getUniqueId());

            if (client == null) {
                // Joueur pas encore charge dans OpenAudioMc
                return false;
            }

            // Verifier d'abord si connecte au client web
            Boolean isConnected = (Boolean) client.getClass().getMethod("isConnected").invoke(client);
            if (!isConnected) {
                return false;
            }

            // Verifier si le voice chat est actif
            Boolean hasVoiceChat = (Boolean) client.getClass().getMethod("hasVoicechatEnabled").invoke(client);
            return hasVoiceChat;

        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "[Phone] Erreur verification voice chat: " + e.getMessage());
            return false;
        }
    }

    /**
     * Verifie si un joueur est au moins connecte au client web (sans forcement voice chat).
     */
    public boolean isPlayerConnectedToWeb(Player player) {
        if (!openAudioMcAvailable) {
            return false;
        }

        try {
            Class<?> clientApiClass = Class.forName("com.craftmend.openaudiomc.api.ClientApi");
            Object clientApi = clientApiClass.getMethod("getInstance").invoke(null);

            Object client = clientApi.getClass().getMethod("getClient", UUID.class)
                .invoke(clientApi, player.getUniqueId());

            if (client == null) {
                return false;
            }

            return (Boolean) client.getClass().getMethod("isConnected").invoke(client);

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Connecte deux joueurs pour un appel vocal.
     * Utilise VoiceApi.addStaticPeer(Client, Client, boolean, boolean)
     * Note: La verification du voice chat est faite en amont dans initiateCall()
     */
    public boolean connectCall(Player caller, Player callee) {
        if (!openAudioMcAvailable) {
            plugin.getLogger().warning("[Phone] Tentative de connexion appel sans OpenAudioMc");
            return false;
        }

        try {
            // Obtenir ClientApi
            Class<?> clientApiClass = Class.forName("com.craftmend.openaudiomc.api.ClientApi");
            Object clientApi = clientApiClass.getMethod("getInstance").invoke(null);

            if (clientApi == null) {
                plugin.getLogger().warning("[Phone] ClientApi.getInstance() retourne null");
                return false;
            }

            // Obtenir les deux Clients
            Object callerClient = clientApi.getClass().getMethod("getClient", UUID.class)
                .invoke(clientApi, caller.getUniqueId());
            Object calleeClient = clientApi.getClass().getMethod("getClient", UUID.class)
                .invoke(clientApi, callee.getUniqueId());

            if (callerClient == null) {
                plugin.getLogger().warning("[Phone] Client null pour caller: " + caller.getName());
                return false;
            }
            if (calleeClient == null) {
                plugin.getLogger().warning("[Phone] Client null pour callee: " + callee.getName());
                return false;
            }

            // Obtenir VoiceApi
            Class<?> voiceApiClass = Class.forName("com.craftmend.openaudiomc.api.VoiceApi");
            Object voiceApi = voiceApiClass.getMethod("getInstance").invoke(null);

            if (voiceApi == null) {
                plugin.getLogger().warning("[Phone] VoiceApi.getInstance() retourne null");
                return false;
            }

            // Obtenir la classe/interface Client pour le type du parametre
            Class<?> clientInterface = Class.forName("com.craftmend.openaudiomc.api.clients.Client");

            // addStaticPeer(Client client, Client peer, boolean visible, boolean mutual)
            // visible = true: affiche le peer dans l'interface
            // mutual = true: ajoute automatiquement le peer dans les deux sens
            voiceApi.getClass().getMethod("addStaticPeer", clientInterface, clientInterface, boolean.class, boolean.class)
                .invoke(voiceApi, callerClient, calleeClient, true, true);

            plugin.getLogger().info("[Phone] Appel vocal connecte: " + caller.getName() + " <-> " + callee.getName());
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Phone] Erreur connexion appel vocal: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Deconnecte deux joueurs d'un appel vocal.
     * Utilise VoiceApi.removeStaticPeer(Client, Client, boolean)
     */
    public void disconnectCall(UUID callerUuid, UUID calleeUuid) {
        if (!openAudioMcAvailable) {
            return;
        }

        try {
            // Obtenir les deux Clients
            Class<?> clientApiClass = Class.forName("com.craftmend.openaudiomc.api.ClientApi");
            Object clientApi = clientApiClass.getMethod("getInstance").invoke(null);

            Object callerClient = clientApi.getClass().getMethod("getClient", UUID.class)
                .invoke(clientApi, callerUuid);
            Object calleeClient = clientApi.getClass().getMethod("getClient", UUID.class)
                .invoke(clientApi, calleeUuid);

            if (callerClient == null || calleeClient == null) {
                // Un des joueurs s'est deconnecte, pas besoin de nettoyer
                return;
            }

            // Obtenir VoiceApi
            Class<?> voiceApiClass = Class.forName("com.craftmend.openaudiomc.api.VoiceApi");
            Object voiceApi = voiceApiClass.getMethod("getInstance").invoke(null);

            Class<?> clientClass = Class.forName("com.craftmend.openaudiomc.api.clients.Client");

            // removeStaticPeer(Client client, Client peer, boolean mutual)
            // mutual = true: retire automatiquement le peer dans les deux sens
            voiceApi.getClass().getMethod("removeStaticPeer", clientClass, clientClass, boolean.class)
                .invoke(voiceApi, callerClient, calleeClient, true);

            plugin.getLogger().fine("[Phone] Appel vocal deconnecte");

        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "[Phone] Erreur deconnexion appel vocal", e);
        }
    }

    /**
     * Deconnecte tous les appels d'un joueur (lors de sa deconnexion).
     * Note: OpenAudioMc gere automatiquement le nettoyage lors de la deconnexion.
     */
    public void disconnectAllCalls(UUID playerUuid) {
        // OpenAudioMc nettoie automatiquement les peers quand un joueur se deconnecte
        plugin.getLogger().fine("[Phone] Nettoyage appels vocaux pour " + playerUuid);
    }
}
