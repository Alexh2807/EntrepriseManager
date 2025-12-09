package com.gravityyfh.roleplaycity.mdt.schematic;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.mdt.config.MDTConfig;

import com.sk89q.worldedit.*;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Gestionnaire complet de schématiques FAWE pour MDT
 * Sauvegarde et restaure la map MDT en utilisant FAWE
 */
public class MDTSchematicManager {

    private final RoleplayCity plugin;
    private final MDTConfig config;
    public final File schematicsDirectory;
    private final boolean hasFAWE;
    private final Logger logger;

    // Cache pour éviter les sauvegardes multiples
    private volatile boolean isSaving = false;
    private volatile boolean isRestoring = false;

    public MDTSchematicManager(RoleplayCity plugin, MDTConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.logger = plugin.getLogger();
        this.schematicsDirectory = new File(plugin.getDataFolder(), "mdt-schematics");
        this.hasFAWE = checkFAWEAvailable();

        // Créer le répertoire des schématiques
        if (!schematicsDirectory.exists()) {
            schematicsDirectory.mkdirs();
        }

        logger.info("[MDT-Schematic] Gestionnaire initialisé - FAWE disponible: " + hasFAWE);
    }

    /**
     * Vérifie si FAWE est disponible
     */
    private boolean checkFAWEAvailable() {
        try {
            Class.forName("com.sk89q.worldedit.world.World");
            return Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") != null;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Vérifie si FAWE est disponible
     */
    public boolean hasFAWE() {
        return hasFAWE;
    }

    /**
     * Donne les outils de sélection FAWE à un joueur
     */
    public void giveSelectionTools(Player player) {
        if (!hasFAWE) {
            player.sendMessage("§cFAWE n'est pas disponible sur ce serveur !");
            return;
        }

        player.sendMessage("§a✅ Outils de sélection FAWE donnés !");
        player.sendMessage("§e- §bHache en bois: §fSélectionner le point 1 (//pos1)");
        player.sendMessage("§e- §bPioche en bois: §fSélectionner le point 2 (//pos2)");
        player.sendMessage("§e- §fUtilisez //wand pour récupérer les outils");
        player.sendMessage("§e- §fUtilisez //size pour voir la taille de la sélection");

        // Commandes FAWE pour donner les outils
        player.performCommand("wand");
    }

    /**
     * Récupère la sélection actuelle du joueur avec FAWE
     */
    public RegionSelection getPlayerSelection(Player player) {
        if (!hasFAWE) {
            return null;
        }

        try {
            com.sk89q.worldedit.bukkit.BukkitPlayer wePlayer = BukkitAdapter.adapt(player);
            com.sk89q.worldedit.LocalSession session = WorldEdit.getInstance().getSessionManager().get(wePlayer);

            Region region = session.getSelection(session.getSelectionWorld());
            if (region instanceof CuboidRegion) {
                CuboidRegion cuboidRegion = (CuboidRegion) region;
                BlockVector3 min = cuboidRegion.getMinimumPoint();
                BlockVector3 max = cuboidRegion.getMaximumPoint();

                World world = player.getWorld();
                Location minLoc = new Location(world, min.getX(), min.getY(), min.getZ());
                Location maxLoc = new Location(world, max.getX(), max.getY(), max.getZ());

                return new RegionSelection(minLoc, maxLoc, (int)region.getVolume(), world.getName());
            }
        } catch (Exception e) {
            logger.warning("[MDT-Snapshot] Erreur lors de la récupération de la sélection: " + e.getMessage());
        }

        return null;
    }

    /**
     * Sauvegarde la région MDT complète en format .schem
     * Utilise la région configurée dans MDTConfig
     */
    public CompletableFuture<Boolean> saveMDTRegion() {
        if (!hasFAWE) {
            logger.warning("[MDT-Schematic] FAWE n'est pas disponible !");
            return CompletableFuture.completedFuture(false);
        }

        if (isSaving) {
            logger.warning("[MDT-Schematic] Sauvegarde déjà en cours !");
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            isSaving = true;
            try {
                logger.info("[MDT-Schematic] Début de la sauvegarde de la région MDT...");

                World world = config.getWorld();
                if (world == null) {
                    logger.severe("[MDT-Schematic] Monde MDT introuvable !");
                    return false;
                }

                // Créer la région depuis la configuration
                Location min = config.getGameRegionMin();
                Location max = config.getGameRegionMax();

                logger.info("[MDT-Schematic] DEBUG - Min: " + min);
                logger.info("[MDT-Schematic] DEBUG - Max: " + max);
                logger.info("[MDT-Schematic] DEBUG - World: " + config.getWorld());

                if (min == null || max == null) {
                    logger.severe("[MDT-Schematic] Région MDT non configurée !");
                    logger.severe("[MDT-Schematic] World: " + config.getWorld());
                    logger.severe("[MDT-Schematic] Min: " + min);
                    logger.severe("[MDT-Schematic] Max: " + max);
                    return false;
                }

                return saveSchematic(world, min, max, "mdt_arena");

            } catch (Exception e) {
                logger.severe("[MDT-Schematic] Erreur lors de la sauvegarde: " + e.getMessage());
                e.printStackTrace();
                return false;
            } finally {
                isSaving = false;
            }
        });
    }

    /**
     * Sauvegarde la région MDT depuis la sélection du joueur et met à jour la config
     */
    public CompletableFuture<Boolean> saveArenaFromSelection(Player player) {
        if (!hasFAWE) {
            player.sendMessage("§cFAWE n'est pas disponible !");
            return CompletableFuture.completedFuture(false);
        }

        RegionSelection selection = getPlayerSelection(player);
        if (selection == null) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            isSaving = true;
            try {
                // 1. Sauvegarder la schématique
                boolean success = saveSchematic(selection.getMin().getWorld(), selection.getMin(), selection.getMax(), "mdt_arena");

                if (success) {
                    // 2. Mettre à jour la configuration
                    // Mettre à jour le monde pour correspondre à celui de la sélection
                    String selectionWorld = selection.getMin().getWorld().getName();
                    if (!selectionWorld.equals(config.getWorldName())) {
                        config.setWorldName(selectionWorld);
                        if (player.isOnline()) {
                            player.sendMessage("§e⚠️ Monde MDT mis à jour dans la config: " + selectionWorld);
                        }
                    }

                    config.setGameRegion(selection.getMin(), selection.getMax());
                    
                    if (player.isOnline()) {
                        player.sendMessage("§a✅ Région MDT définie et sauvegardée !");
                        player.sendMessage("§eVolume: " + selection.getVolume() + " blocs");
                    }
                }

                return success;
            } catch (Exception e) {
                if (player.isOnline()) player.sendMessage("§cErreur: " + e.getMessage());
                e.printStackTrace();
                return false;
            } finally {
                isSaving = false;
            }
        });
    }

    /**
     * Sauvegarde une sélection personnalisée
     */
    public CompletableFuture<Boolean> saveCustomRegion(Player player, String name) {
        if (!hasFAWE) {
            player.sendMessage("§cFAWE n'est pas disponible !");
            return CompletableFuture.completedFuture(false);
        }

        RegionSelection selection = getPlayerSelection(player);
        if (selection == null) {
            player.sendMessage("§cVous n'avez aucune sélection !");
            return CompletableFuture.completedFuture(false);
        }

        // Vérifier la taille maximum (5M de blocs pour éviter les lags)
        if (selection.getVolume() > 5000000) {
            player.sendMessage("§cLa sélection est trop grande ! Maximum: 5M de blocs");
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            isSaving = true;
            try {
                boolean success = saveSchematic(selection.getMin().getWorld(), selection.getMin(), selection.getMax(), name);

                if (success) {
                    player.sendMessage("§a✅ Schématique sauvegardée: " + name + ".schem");
                    player.sendMessage("§eVolume: " + selection.getVolume() + " blocs");
                }

                return success;
            } catch (Exception e) {
                player.sendMessage("§cErreur lors de la sauvegarde: " + e.getMessage());
                logger.severe("[MDT-Schematic] Erreur de sauvegarde: " + e.getMessage());
                return false;
            } finally {
                isSaving = false;
            }
        });
    }

    /**
     * Sauvegarde une région en format .schem
     */
    private boolean saveSchematic(World world, Location min, Location max, String schematicName) {
        try {
            com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);

            // Créer la région
            BlockVector3 minVec = BlockVector3.at(
                Math.min(min.getBlockX(), max.getBlockX()),
                Math.min(min.getBlockY(), max.getBlockY()),
                Math.min(min.getBlockZ(), max.getBlockZ())
            );
            BlockVector3 maxVec = BlockVector3.at(
                Math.max(min.getBlockX(), max.getBlockX()),
                Math.max(min.getBlockY(), max.getBlockY()),
                Math.max(min.getBlockZ(), max.getBlockZ())
            );

            CuboidRegion region = new CuboidRegion(weWorld, minVec, maxVec);

            // Utiliser l'API FAWE directe pour sauvegarder
            com.sk89q.worldedit.EditSession editSession = com.sk89q.worldedit.WorldEdit.getInstance().getEditSessionFactory()
                .getEditSession(weWorld, -1);

            try {
                // Utiliser l'API FAWE simplifiée pour sauvegarder
                // D'abord créer un fichier vide pour la schématique
                File schematicFile = new File(schematicsDirectory, schematicName + ".schem");
                ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
                
                if (format == null) {
                    format = ClipboardFormats.findByAlias("schem");
                }

                if (format == null) {
                    logger.severe("[MDT-Schematic] Format de schématique non supporté !");
                    return false;
                }

                // Créer un clipboard simple
                com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard clipboard =
                    new com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard(region);

                // IMPORTANT: Copier les blocs du monde vers le clipboard
                // Sans ça, le clipboard est vide (ou rempli d'air) !
                ForwardExtentCopy copy = new ForwardExtentCopy(editSession, region, clipboard, region.getMinimumPoint());
                Operations.complete(copy);
                
                logger.info("[MDT-Schematic] Blocs copiés dans le clipboard (Origine: " + region.getMinimumPoint() + ")");

                try (ClipboardWriter writer = format.getWriter(new FileOutputStream(schematicFile))) {
                    writer.write(clipboard);
                }

                logger.info("[MDT-Schematic] ✅ Schématique sauvegardée: " + schematicFile.getName() +
                           " (Volume: " + region.getVolume() + " blocs)");

                // Sauvegarder aussi comme "latest" pour restauration rapide
                if (!schematicName.equals("latest")) {
                    File latestFile = new File(schematicsDirectory, "latest.schem");
                    Files.copy(schematicFile.toPath(), latestFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    logger.info("[MDT-Schematic] ✅ Aussi sauvegardé comme latest.schem");
                }

                return true;

            } finally {
                editSession.close();
            }

        } catch (Exception e) {
            logger.severe("[MDT-Schematic] Erreur lors de la sauvegarde: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Restaure la région MDT depuis le fichier .schem
     */
    public CompletableFuture<Boolean> restoreMDTRegion() {
        return restoreSchematic("latest");
    }

    /**
     * Restaure une schématique spécifique
     */
    public CompletableFuture<Boolean> restoreSchematic(String schematicName) {
        if (!hasFAWE) {
            logger.warning("[MDT-Schematic] FAWE n'est pas disponible !");
            return CompletableFuture.completedFuture(false);
        }

        if (isRestoring) {
            logger.warning("[MDT-Schematic] Restauration déjà en cours !");
            return CompletableFuture.completedFuture(false);
        }

        File schematicFile = new File(schematicsDirectory, schematicName + ".schem");
        if (!schematicFile.exists()) {
            logger.warning("[MDT-Schematic] Fichier de schématique non trouvé: " + schematicName);
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            isRestoring = true;
            try {
                logger.info("[MDT-Schematic] Début de la restauration: " + schematicName);

                // Charger la schématique
                ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
                if (format == null) {
                    format = ClipboardFormats.findByAlias("schem");
                }

                if (format == null) {
                    logger.severe("[MDT-Schematic] Format non supporté !");
                    return false;
                }

                try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
                    Clipboard clipboard = reader.read();

                    // Obtenir le monde et la position
                    World world = config.getWorld();
                    if (world == null) {
                        logger.severe("[MDT-Schematic] Monde MDT introuvable !");
                        return false;
                    }

                    com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);

                    // Utiliser l'origine de la schématique pour la restauration
                    BlockVector3 clipboardOrigin = clipboard.getOrigin();
                    logger.info("[MDT-Schematic] Origine de la schématique (Fichier): " + clipboardOrigin);

                    // Comparer avec la config pour debug
                    Location configMin = config.getGameRegionMin();
                    BlockVector3 pasteTarget;

                    if (configMin != null) {
                        // PRIORITÉ ABSOLUE A LA CONFIG
                        // Si on a une région définie, on colle dedans. C'est le plus fiable.
                        pasteTarget = BlockVector3.at(
                            configMin.getBlockX(),
                            configMin.getBlockY(),
                            configMin.getBlockZ()
                        );
                        logger.info("[MDT-Schematic] 🎯 FORCE PASTE TARGET (Config): " + pasteTarget);
                    } else {
                        // Fallback sur l'origine du fichier si pas de config
                        pasteTarget = clipboardOrigin;
                        logger.severe("[MDT-Schematic] ⛔ CRITIQUE: Pas de région dans la config ! Utilisation de l'origine FICHIER: " + pasteTarget);
                        logger.severe("[MDT-Schematic] ⛔ Cela peut causer un décalage si le fichier .schem est corrompu.");
                    }

                    // Créer une session d'édition
                    com.sk89q.worldedit.EditSession editSession = com.sk89q.worldedit.WorldEdit.getInstance().getEditSessionFactory()
                        .getEditSession(weWorld, -1);

                    try {
                        // Coller à l'origine choisie
                        Operation operation = new com.sk89q.worldedit.session.ClipboardHolder(clipboard)
                            .createPaste(editSession)
                            .to(pasteTarget) // Utilisation de la cible calculée
                            .ignoreAirBlocks(false) // Important: restaurer même l'air (effacer les blocs posés)
                            .build();

                        Operations.complete(operation);
                        
                        // Forcer l'application des changements
                        editSession.flushSession();

                        logger.info("[MDT-Schematic] ✅ Schématique restaurée: " + schematicName +
                                   " (Volume: " + clipboard.getRegion().getVolume() + " blocs) à " + pasteTarget);

                        return true;
                    } finally {
                        editSession.close();
                    }
                }

            } catch (Exception e) {
                logger.severe("[MDT-Schematic] Erreur lors de la restauration: " + e.getMessage());
                e.printStackTrace();
                return false;
            } finally {
                isRestoring = false;
            }
        });
    }

    /**
     * Liste toutes les schématiques disponibles
     */
    public File[] listSchematics() {
        return schematicsDirectory.listFiles((dir, name) ->
            name.endsWith(".schem") || name.endsWith(".schematic"));
    }

    /**
     * Vérifie si une sauvegarde existe
     */
    public boolean hasSavedSchematic() {
        File latestFile = new File(schematicsDirectory, "latest.schem");
        return latestFile.exists();
    }

    /**
     * Obtient la taille du fichier de schématique
     */
    public long getSchematicFileSize() {
        File latestFile = new File(schematicsDirectory, "latest.schem");
        return latestFile.exists() ? latestFile.length() : 0;
    }

    /**
     * Conteneur pour les informations de sélection
     */
    public static class RegionSelection {
        private final Location min;
        private final Location max;
        private final int volume;
        private final String worldName;

        public RegionSelection(Location min, Location max, int volume, String worldName) {
            this.min = min;
            this.max = max;
            this.volume = volume;
            this.worldName = worldName;
        }

        public Location getMin() { return min; }
        public Location getMax() { return max; }
        public int getVolume() { return volume; }
        public String getWorldName() { return worldName; }

        @Override
        public String toString() {
            return String.format("Région %s: (%d,%d,%d) → (%d,%d,%d) | Volume: %,d blocs",
                worldName,
                min.getBlockX(), min.getBlockY(), min.getBlockZ(),
                max.getBlockX(), max.getBlockY(), max.getBlockZ(),
                volume);
        }
    }
}