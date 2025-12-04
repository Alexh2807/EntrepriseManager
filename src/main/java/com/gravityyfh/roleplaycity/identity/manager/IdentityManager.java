package com.gravityyfh.roleplaycity.identity.manager;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.database.ConnectionManager;
import com.gravityyfh.roleplaycity.identity.data.Identity;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class IdentityManager {

    private final RoleplayCity plugin;
    private final ConnectionManager connectionManager;
    private final Map<UUID, Identity> identities = new HashMap<>();
    private File identityFile;
    private FileConfiguration identityConfig;

    public IdentityManager(RoleplayCity plugin, ConnectionManager connectionManager) {
        this.plugin = plugin;
        this.connectionManager = connectionManager;
        loadIdentities();
    }

    public void createIdentity(UUID uuid, String firstName, String lastName, String sex, int age, int height) {
        Identity identity = new Identity(uuid, firstName, lastName, sex, age, height, System.currentTimeMillis());
        identities.put(uuid, identity);
        saveIdentity(identity);
    }

    public boolean hasIdentity(UUID uuid) {
        return identities.containsKey(uuid);
    }

    public Identity getIdentity(UUID uuid) {
        return identities.get(uuid);
    }

    /**
     * Met à jour et sauvegarde une identité existante
     */
    public void updateIdentity(Identity identity) {
        if (identity != null && identity.getUuid() != null) {
            identities.put(identity.getUuid(), identity);
            saveIdentity(identity);
        }
    }

    /**
     * Recharge toutes les identités depuis le fichier identities.yml
     * Utilisé par /roleplaycity reload
     * @return Le nombre d'identités rechargées
     */
    public int reloadIdentities() {
        identities.clear();
        loadIdentities();
        plugin.getLogger().info("Identities rechargées: " + identities.size() + " identité(s)");
        return identities.size();
    }

    /**
     * Retourne le nombre d'identités en mémoire
     */
    public int getIdentityCount() {
        return identities.size();
    }

    private void loadIdentities() {
        identityFile = new File(plugin.getDataFolder(), "identities.yml");
        if (!identityFile.exists()) {
            try {
                identityFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create identities.yml!");
                return;
            }
        }
        identityConfig = YamlConfiguration.loadConfiguration(identityFile);

        if (identityConfig.contains("identities")) {
            // FIX: Vérifier que getConfigurationSection ne retourne pas null
            var section = identityConfig.getConfigurationSection("identities");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        String path = "identities." + key + ".";
                        String firstName = identityConfig.getString(path + "first_name");
                        String lastName = identityConfig.getString(path + "last_name");
                        String sex = identityConfig.getString(path + "sex");
                        int age = identityConfig.getInt(path + "age");
                        int height = identityConfig.getInt(path + "height");
                        long creation = identityConfig.getLong(path + "creation_date");

                        String residenceCity = identityConfig.getString(path + "residence_city");
                        Identity identity = new Identity(uuid, firstName, lastName, sex, age, height, creation);
                        identity.setResidenceCity(residenceCity);
                        identities.put(uuid, identity);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to load identity for " + key);
                    }
                }
            }
        }
    }

    private void saveIdentity(Identity identity) {
        if (identityConfig == null || identityFile == null) return;

        String path = "identities." + identity.getUuid().toString() + ".";
        identityConfig.set(path + "first_name", identity.getFirstName());
        identityConfig.set(path + "last_name", identity.getLastName());
        identityConfig.set(path + "sex", identity.getSex());
        identityConfig.set(path + "age", identity.getAge());
        identityConfig.set(path + "height", identity.getHeight());
        identityConfig.set(path + "creation_date", identity.getCreationDate());
        identityConfig.set(path + "residence_city", identity.getResidenceCity());

        try {
            identityConfig.save(identityFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save identities.yml!");
        }
    }
}