package com.gravityyfh.roleplaycity.shop.persistence;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.shop.RepairAction;
import com.gravityyfh.roleplaycity.shop.ValidationResult;
import com.gravityyfh.roleplaycity.shop.model.Shop;
import com.gravityyfh.roleplaycity.shop.validation.ShopValidator;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;

/**
 * Gestionnaire de persistance des boutiques
 * Sauvegarde et chargement avec backups automatiques
 */
public class ShopPersistence {
    private static final String CURRENT_VERSION = "2.0";
    private static final DateTimeFormatter BACKUP_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final RoleplayCity plugin;
    private final File dataFolder;
    private final File shopsFile;
    private final File backupFolder;
    private final ShopValidator validator;

    private final int backupKeepCount;

    public ShopPersistence(RoleplayCity plugin, ShopValidator validator) {
        this.plugin = plugin;
        this.dataFolder = plugin.getDataFolder();
        this.shopsFile = new File(dataFolder, "shops.yml");
        this.backupFolder = new File(dataFolder, "backups");
        this.validator = validator;

        // Configuration
        this.backupKeepCount = plugin.getConfig().getInt("shop-system.backup-keep-count", 10);

        // Créer le dossier de backup
        if (!backupFolder.exists()) {
            backupFolder.mkdirs();
        }
    }

    /**
     * Sauvegarde atomique des boutiques avec backup
     */
    public void saveShops(Collection<Shop> shops) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // 1. Créer backup de l'ancien fichier
                if (shopsFile.exists()) {
                    createBackup();
                }

                // 2. Créer fichier temporaire
                File tempFile = new File(dataFolder, "shops.yml.tmp");

                // 3. Sérialiser et écrire
                YamlConfiguration config = new YamlConfiguration();
                config.set("version", CURRENT_VERSION);
                config.set("last-save", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                config.set("shop-count", shops.size());

                List<Map<String, Object>> serializedShops = new ArrayList<>();
                for (Shop shop : shops) {
                    serializedShops.add(shop.serialize());
                }
                config.set("shops", serializedShops);

                config.save(tempFile);

                // 4. Déplacement atomique
                Files.move(tempFile.toPath(), shopsFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

                plugin.getLogger().info("[ShopSystem] " + shops.size() + " boutique(s) sauvegardée(s)");

                // 5. Nettoyer les vieux backups
                cleanOldBackups();

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[ShopSystem] Erreur lors de la sauvegarde des boutiques", e);
            }
        });
    }

    /**
     * Chargement des boutiques avec validation
     */
    public LoadResult loadShops() {
        List<Shop> loadedShops = new ArrayList<>();
        List<LoadError> errors = new ArrayList<>();

        if (!shopsFile.exists()) {
            plugin.getLogger().info("[ShopSystem] Aucun fichier shops.yml trouvé, démarrage avec 0 boutique");
            return new LoadResult(loadedShops, errors);
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(shopsFile);

            // Vérifier la version
            String version = config.getString("version", "1.0");
            if (needsMigration(version)) {
                plugin.getLogger().info("[ShopSystem] Migration des données de v" + version + " vers v" + CURRENT_VERSION);
                migrateData(config, version);
            }

            // Charger les shops
            List<Map<?, ?>> shopList = config.getMapList("shops");

            for (Map<?, ?> shopMap : shopList) {
                try {
                    // Convertir la map
                    Map<String, Object> castedMap = new HashMap<>();
                    shopMap.forEach((key, value) -> {
                        if (key instanceof String) {
                            castedMap.put((String) key, value);
                        }
                    });

                    Shop shop = Shop.deserialize(castedMap);

                    if (shop != null) {
                        // Validation immédiate
                        ValidationResult validation = validator.validateShop(shop);

                        if (validation.isValid()) {
                            loadedShops.add(shop);
                        } else {
                            String shopId = shop.getShopId().toString().substring(0, 8);
                            errors.add(new LoadError(shop.getShopId(),
                                "Validation échouée: " + validation.getIssuesAsString()));

                            if (validation.getSuggestedAction() == RepairAction.DELETE) {
                                plugin.getLogger().warning("[ShopSystem] Shop " + shopId +
                                    " est cassé et sera ignoré: " + validation.getIssuesAsString());
                            } else {
                                // Le shop est chargé même s'il a des problèmes mineurs
                                loadedShops.add(shop);
                                plugin.getLogger().warning("[ShopSystem] Shop " + shopId +
                                    " chargé avec des problèmes: " + validation.getIssuesAsString());
                            }
                        }
                    } else {
                        errors.add(new LoadError(null, "Échec de désérialisation (shop null)"));
                    }

                } catch (Exception e) {
                    errors.add(new LoadError(null, "Erreur de chargement: " + e.getMessage()));
                    plugin.getLogger().log(Level.WARNING,
                        "[ShopSystem] Erreur lors du chargement d'une boutique", e);
                }
            }

            plugin.getLogger().info("[ShopSystem] " + loadedShops.size() + " boutique(s) chargée(s)" +
                (errors.isEmpty() ? "" : " (" + errors.size() + " erreur(s))"));

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[ShopSystem] Erreur critique lors du chargement", e);
            errors.add(new LoadError(null, "Erreur critique: " + e.getMessage()));
        }

        return new LoadResult(loadedShops, errors);
    }

    /**
     * Crée un backup du fichier actuel
     */
    private void createBackup() {
        try {
            String timestamp = LocalDateTime.now().format(BACKUP_DATE_FORMAT);
            File backupFile = new File(backupFolder, "shops_" + timestamp + ".yml");

            Files.copy(shopsFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            plugin.getLogger().info("[ShopSystem] Backup créé: " + backupFile.getName());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[ShopSystem] Impossible de créer le backup", e);
        }
    }

    /**
     * Nettoie les vieux backups (garde seulement les N derniers)
     */
    private void cleanOldBackups() {
        File[] backups = backupFolder.listFiles((dir, name) -> name.startsWith("shops_") && name.endsWith(".yml"));

        if (backups == null || backups.length <= backupKeepCount) {
            return;
        }

        // Trier par date (les plus récents en premier)
        Arrays.sort(backups, Comparator.comparingLong(File::lastModified).reversed());

        // Supprimer les anciens
        int deleted = 0;
        for (int i = backupKeepCount; i < backups.length; i++) {
            if (backups[i].delete()) {
                deleted++;
            }
        }

        if (deleted > 0) {
            plugin.getLogger().info("[ShopSystem] " + deleted + " ancien(s) backup(s) supprimé(s)");
        }
    }

    /**
     * Vérifie si une migration est nécessaire
     */
    private boolean needsMigration(String version) {
        return !version.equals(CURRENT_VERSION);
    }

    /**
     * Migre les données depuis une ancienne version
     */
    private void migrateData(YamlConfiguration config, String fromVersion) {
        // Pour l'instant, aucune migration nécessaire
        // Cette méthode sera utilisée pour les futures migrations
        config.set("version", CURRENT_VERSION);

        plugin.getLogger().info("[ShopSystem] Migration effectuée de v" + fromVersion + " vers v" + CURRENT_VERSION);
    }

    /**
     * Restaure depuis un backup
     */
    public boolean restoreFromBackup(String backupFileName) {
        File backupFile = new File(backupFolder, backupFileName);

        if (!backupFile.exists()) {
            plugin.getLogger().warning("[ShopSystem] Backup introuvable: " + backupFileName);
            return false;
        }

        try {
            // Créer un backup du fichier actuel avant de restaurer
            if (shopsFile.exists()) {
                File emergencyBackup = new File(backupFolder, "emergency_backup_" +
                    LocalDateTime.now().format(BACKUP_DATE_FORMAT) + ".yml");
                Files.copy(shopsFile.toPath(), emergencyBackup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            // Restaurer
            Files.copy(backupFile.toPath(), shopsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            plugin.getLogger().info("[ShopSystem] Restauration depuis le backup: " + backupFileName);
            return true;

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[ShopSystem] Erreur lors de la restauration", e);
            return false;
        }
    }

    /**
     * Liste tous les backups disponibles
     */
    public List<String> listBackups() {
        File[] backups = backupFolder.listFiles((dir, name) -> name.startsWith("shops_") && name.endsWith(".yml"));

        if (backups == null) {
            return Collections.emptyList();
        }

        List<String> backupNames = new ArrayList<>();
        for (File backup : backups) {
            backupNames.add(backup.getName());
        }

        backupNames.sort(Collections.reverseOrder()); // Plus récent en premier
        return backupNames;
    }

    /**
         * Résultat du chargement
         */
        public record LoadResult(List<Shop> shops, List<LoadError> errors) {

        public boolean hasErrors() {
                return !errors.isEmpty();
            }
        }

    /**
         * Erreur de chargement
         */
        public record LoadError(UUID shopId, String message) {

        @Override
            public String toString() {
                return (shopId != null ? "Shop " + shopId.toString().substring(0, 8) + ": " : "") + message;
            }
        }
}
