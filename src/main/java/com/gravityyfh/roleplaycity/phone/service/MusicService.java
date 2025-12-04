package com.gravityyfh.roleplaycity.phone.service;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.phone.PhoneManager;
import com.gravityyfh.roleplaycity.phone.model.MusicTrack;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Service de gestion de la musique via OpenAudioMc.
 * Gere la lecture de musique sur les telephones.
 */
public class MusicService {

    private final RoleplayCity plugin;
    private final PhoneManager phoneManager;
    private boolean openAudioMcAvailable = false;

    // Etat de lecture par joueur
    private final Map<UUID, MusicTrack> currentTracks = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> playingState = new ConcurrentHashMap<>();
    private final Map<UUID, String> mediaIds = new ConcurrentHashMap<>();

    public MusicService(RoleplayCity plugin, PhoneManager phoneManager) {
        this.plugin = plugin;
        this.phoneManager = phoneManager;

        // Verifier si OpenAudioMc est disponible
        checkOpenAudioMc();
    }

    /**
     * Verifie si OpenAudioMc est disponible et initialise l'API.
     */
    private void checkOpenAudioMc() {
        if (Bukkit.getPluginManager().isPluginEnabled("OpenAudioMc")) {
            try {
                // Verifier que les classes API existent
                Class.forName("com.craftmend.openaudiomc.api.MediaApi");
                Class.forName("com.craftmend.openaudiomc.api.ClientApi");
                openAudioMcAvailable = true;
                plugin.getLogger().info("[Phone] OpenAudioMc detecte et initialise");
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[Phone] Impossible d'initialiser OpenAudioMc API", e);
                openAudioMcAvailable = false;
            }
        } else {
            plugin.getLogger().info("[Phone] OpenAudioMc non detecte - fonctionnalites musicales desactivees");
            openAudioMcAvailable = false;
        }
    }

    /**
     * Verifie si le service est disponible.
     */
    public boolean isAvailable() {
        return openAudioMcAvailable;
    }

    /**
     * Verifie si un joueur est connecte a OpenAudioMc.
     */
    public boolean isPlayerConnected(Player player) {
        if (!openAudioMcAvailable) {
            return false;
        }

        try {
            // ClientApi.getInstance().isConnected(uuid)
            Class<?> clientApiClass = Class.forName("com.craftmend.openaudiomc.api.ClientApi");
            Object clientApi = clientApiClass.getMethod("getInstance").invoke(null);
            if (clientApi == null) return false;

            return (boolean) clientApi.getClass().getMethod("isConnected", UUID.class)
                .invoke(clientApi, player.getUniqueId());
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "Erreur verification connexion OpenAudioMc", e);
            return false;
        }
    }

    /**
     * Envoie l'URL de connexion au joueur.
     */
    public void sendConnectUrl(Player player) {
        if (!openAudioMcAvailable) {
            return;
        }

        try {
            // ClientApi.getInstance().getClient(uuid).getAuth().publishSessionUrl()
            Class<?> clientApiClass = Class.forName("com.craftmend.openaudiomc.api.ClientApi");
            Object clientApi = clientApiClass.getMethod("getInstance").invoke(null);
            if (clientApi == null) return;

            Object client = clientApi.getClass().getMethod("getClient", UUID.class)
                .invoke(clientApi, player.getUniqueId());
            if (client != null) {
                // Essayer plusieurs methodes selon la version de l'API
                try {
                    // Nouvelle API: client.getAuth().publishSessionUrl()
                    Object auth = client.getClass().getMethod("getAuth").invoke(client);
                    if (auth != null) {
                        auth.getClass().getMethod("publishSessionUrl").invoke(auth);
                    }
                } catch (NoSuchMethodException e1) {
                    // Ancienne API: client.sendUrl()
                    client.getClass().getMethod("sendUrl").invoke(client);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Erreur envoi URL connexion OpenAudioMc", e);
        }
    }

    /**
     * Joue une piste musicale.
     */
    public void play(Player player, MusicTrack track) {
        if (!openAudioMcAvailable || !isPlayerConnected(player)) {
            return;
        }

        UUID uuid = player.getUniqueId();

        // Arreter la musique actuelle si necessaire
        if (currentTracks.containsKey(uuid)) {
            stop(player);
        }

        try {
            // MediaApi.getInstance()
            Class<?> mediaApiClass = Class.forName("com.craftmend.openaudiomc.api.MediaApi");
            Object mediaApi = mediaApiClass.getMethod("getInstance").invoke(null);
            if (mediaApi == null) return;

            // ClientApi.getInstance().getClient(uuid)
            Class<?> clientApiClass = Class.forName("com.craftmend.openaudiomc.api.ClientApi");
            Object clientApi = clientApiClass.getMethod("getInstance").invoke(null);
            if (clientApi == null) return;

            Object client = clientApi.getClass().getMethod("getClient", UUID.class).invoke(clientApi, uuid);
            if (client == null) return;

            // Creer un Media object avec un ID unique
            String mediaId = "phone_music_" + uuid.toString().substring(0, 8);
            Class<?> mediaClass = Class.forName("com.craftmend.openaudiomc.api.media.Media");
            Object media = mediaClass.getConstructor(String.class).newInstance(track.getSource());

            // Definir l'ID du media pour pouvoir l'arreter plus tard
            mediaClass.getMethod("setMediaId", String.class).invoke(media, mediaId);

            // Jouer: MediaApi.getInstance().playFor(media, client...)
            // Creer un array de Client pour le varargs
            Class<?> clientInterfaceClass = Class.forName("com.craftmend.openaudiomc.api.clients.Client");
            Object clientArray = java.lang.reflect.Array.newInstance(clientInterfaceClass, 1);
            java.lang.reflect.Array.set(clientArray, 0, client);

            mediaApi.getClass().getMethod("playFor", mediaClass, clientArray.getClass())
                .invoke(mediaApi, media, clientArray);

            currentTracks.put(uuid, track);
            playingState.put(uuid, true);
            mediaIds.put(uuid, mediaId);

            plugin.getLogger().fine("[Phone] Musique lancee pour " + player.getName() + ": " + track.getName());
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Erreur lecture musique OpenAudioMc", e);
        }
    }

    /**
     * Arrete la musique.
     */
    public void stop(Player player) {
        UUID uuid = player.getUniqueId();

        if (!openAudioMcAvailable || !currentTracks.containsKey(uuid)) {
            currentTracks.remove(uuid);
            playingState.remove(uuid);
            mediaIds.remove(uuid);
            return;
        }

        try {
            // MediaApi.getInstance()
            Class<?> mediaApiClass = Class.forName("com.craftmend.openaudiomc.api.MediaApi");
            Object mediaApi = mediaApiClass.getMethod("getInstance").invoke(null);
            if (mediaApi == null) return;

            // ClientApi.getInstance().getClient(uuid)
            Class<?> clientApiClass = Class.forName("com.craftmend.openaudiomc.api.ClientApi");
            Object clientApi = clientApiClass.getMethod("getInstance").invoke(null);
            if (clientApi == null) return;

            Object client = clientApi.getClass().getMethod("getClient", UUID.class).invoke(clientApi, uuid);
            if (client == null) return;

            // Creer un array de Client pour le varargs
            Class<?> clientInterfaceClass = Class.forName("com.craftmend.openaudiomc.api.clients.Client");
            Object clientArray = java.lang.reflect.Array.newInstance(clientInterfaceClass, 1);
            java.lang.reflect.Array.set(clientArray, 0, client);

            // Recuperer l'ID du media
            String mediaId = mediaIds.get(uuid);

            // Arreter: MediaApi.getInstance().stopFor(mediaId, client...) ou stopFor(client...)
            if (mediaId != null) {
                // stopFor(String id, Client... clients)
                mediaApi.getClass().getMethod("stopFor", String.class, clientArray.getClass())
                    .invoke(mediaApi, mediaId, clientArray);
            } else {
                // stopFor(Client... clients) - arrete tout
                mediaApi.getClass().getMethod("stopFor", clientArray.getClass())
                    .invoke(mediaApi, clientArray);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Erreur arret musique OpenAudioMc", e);
        }

        currentTracks.remove(uuid);
        playingState.remove(uuid);
        mediaIds.remove(uuid);
    }

    /**
     * Met en pause la musique.
     * Note: OpenAudioMc ne supporte pas directement pause/resume.
     * On simule en arretant mais en gardant la track en memoire.
     */
    public void pause(Player player) {
        UUID uuid = player.getUniqueId();
        if (!currentTracks.containsKey(uuid)) return;

        playingState.put(uuid, false);

        // Arreter le son mais garder la track en memoire
        if (openAudioMcAvailable) {
            try {
                Class<?> mediaApiClass = Class.forName("com.craftmend.openaudiomc.api.MediaApi");
                Object mediaApi = mediaApiClass.getMethod("getInstance").invoke(null);
                if (mediaApi == null) return;

                Class<?> clientApiClass = Class.forName("com.craftmend.openaudiomc.api.ClientApi");
                Object clientApi = clientApiClass.getMethod("getInstance").invoke(null);
                if (clientApi == null) return;

                Object client = clientApi.getClass().getMethod("getClient", UUID.class).invoke(clientApi, uuid);
                if (client == null) return;

                // Creer un array de Client pour le varargs
                Class<?> clientInterfaceClass = Class.forName("com.craftmend.openaudiomc.api.clients.Client");
                Object clientArray = java.lang.reflect.Array.newInstance(clientInterfaceClass, 1);
                java.lang.reflect.Array.set(clientArray, 0, client);

                String mediaId = mediaIds.get(uuid);

                if (mediaId != null) {
                    mediaApi.getClass().getMethod("stopFor", String.class, clientArray.getClass())
                        .invoke(mediaApi, mediaId, clientArray);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINE, "Erreur pause musique", e);
            }
        }
    }

    /**
     * Reprend la musique.
     */
    public void resume(Player player) {
        UUID uuid = player.getUniqueId();
        MusicTrack track = currentTracks.get(uuid);
        if (track == null) return;

        playingState.put(uuid, true);

        if (openAudioMcAvailable && isPlayerConnected(player)) {
            // Relancer la piste
            play(player, track);
        }
    }

    /**
     * Verifie si le joueur ecoute de la musique.
     */
    public boolean isPlaying(Player player) {
        return playingState.getOrDefault(player.getUniqueId(), false);
    }

    /**
     * Recupere la piste en cours.
     */
    public MusicTrack getCurrentTrack(Player player) {
        return currentTracks.get(player.getUniqueId());
    }

    /**
     * Gere la deconnexion d'un joueur.
     */
    public void handlePlayerDisconnect(UUID playerUuid) {
        currentTracks.remove(playerUuid);
        playingState.remove(playerUuid);
        mediaIds.remove(playerUuid);
    }
}
