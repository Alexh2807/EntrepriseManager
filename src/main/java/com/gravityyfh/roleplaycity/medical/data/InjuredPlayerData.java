package com.gravityyfh.roleplaycity.medical.data;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Gère la persistance des joueurs blessés
 * Permet de différencier déconnexion volontaire vs redémarrage serveur
 */
public class InjuredPlayerData {
    private final File dataFile;
    private YamlConfiguration config;

    public InjuredPlayerData(File dataFolder) {
        this.dataFile = new File(dataFolder, "injured_players.yml");
        load();
    }

    /**
     * Charge les données depuis le fichier
     */
    private void load() {
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        config = YamlConfiguration.loadConfiguration(dataFile);
    }

    /**
     * Sauvegarde un joueur blessé
     */
    public void saveInjuredPlayer(UUID playerUuid, String cause, long timestamp) {
        config.set("injured." + playerUuid.toString() + ".cause", cause);
        config.set("injured." + playerUuid.toString() + ".timestamp", timestamp);
        config.set("injured." + playerUuid.toString() + ".wasServerRunning", true);
        save();
    }

    /**
     * Retire un joueur blessé de la sauvegarde
     */
    public void removeInjuredPlayer(UUID playerUuid) {
        config.set("injured." + playerUuid.toString(), null);
        save();
    }

    /**
     * Vérifie si un joueur était blessé
     */
    public boolean wasInjured(UUID playerUuid) {
        return config.contains("injured." + playerUuid.toString());
    }

    /**
     * Récupère la cause de la blessure
     */
    public String getInjuryCause(UUID playerUuid) {
        return config.getString("injured." + playerUuid.toString() + ".cause", "Inconnu");
    }

    /**
     * Récupère le timestamp de la blessure
     */
    public long getInjuryTimestamp(UUID playerUuid) {
        return config.getLong("injured." + playerUuid.toString() + ".timestamp", 0);
    }

    /**
     * Obtient la liste de tous les joueurs blessés sauvegardés
     */
    public Set<UUID> getAllInjuredPlayers() {
        Set<UUID> players = new HashSet<>();
        if (config.contains("injured")) {
            for (String key : config.getConfigurationSection("injured").getKeys(false)) {
                try {
                    players.add(UUID.fromString(key));
                } catch (IllegalArgumentException e) {
                    // UUID invalide, ignorer
                }
            }
        }
        return players;
    }

    /**
     * Marque que le serveur s'est arrêté proprement
     * À appeler dans onDisable()
     */
    public void markServerShutdown() {
        config.set("serverShutdown", true);
        config.set("shutdownTime", System.currentTimeMillis());
        save();
    }

    /**
     * Vérifie si c'était un redémarrage serveur
     * (vs déconnexion volontaire du joueur)
     */
    public boolean wasServerRestart() {
        return config.getBoolean("serverShutdown", false);
    }

    /**
     * Nettoie le flag de redémarrage
     * À appeler dans onEnable() après traitement
     */
    public void clearServerRestartFlag() {
        config.set("serverShutdown", false);
        save();
    }

    /**
     * Sauvegarde le fichier
     */
    private void save() {
        try {
            config.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Nettoie toutes les données
     */
    public void clearAll() {
        config.set("injured", null);
        save();
    }
}
