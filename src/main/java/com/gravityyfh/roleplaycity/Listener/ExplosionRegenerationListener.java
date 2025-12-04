package com.gravityyfh.roleplaycity.Listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.manager.ClaimManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Système de régénération des blocs après explosion
 * Régénère progressivement les blocs détruits par des explosions hors des villes
 * La régénération se fait de l'extérieur vers l'intérieur (du plus loin au plus proche du centre)
 */
public class ExplosionRegenerationListener implements Listener {

    private final RoleplayCity plugin;
    private final ClaimManager claimManager;

    // File d'attente des blocs à régénérer (thread-safe)
    private final Queue<BlockSnapshot> blocksToRegenerate = new ConcurrentLinkedQueue<>();

    // Tâche de régénération
    private boolean regenerationTaskRunning = false;

    public ExplosionRegenerationListener(RoleplayCity plugin) {
        this.plugin = plugin;
        this.claimManager = plugin.getClaimManager();
    }

    /**
     * Vérifie si le système est activé
     */
    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("explosion-regeneration.enabled", true);
    }

    /**
     * Délai avant régénération (en secondes)
     */
    private int getRegenerationDelay() {
        return plugin.getConfig().getInt("explosion-regeneration.delay-seconds", 60);
    }

    /**
     * Intervalle entre chaque bloc régénéré (en ticks)
     */
    private int getRegenerationInterval() {
        return plugin.getConfig().getInt("explosion-regeneration.interval-ticks", 2);
    }

    /**
     * Nombre de blocs régénérés par tick
     */
    private int getBlocksPerTick() {
        return plugin.getConfig().getInt("explosion-regeneration.blocks-per-tick", 3);
    }

    /**
     * Liste des mondes où la régénération est active
     */
    private List<String> getEnabledWorlds() {
        return plugin.getConfig().getStringList("explosion-regeneration.worlds");
    }

    /**
     * Vérifie si un monde est activé pour la régénération
     */
    private boolean isWorldEnabled(String worldName) {
        List<String> worlds = getEnabledWorlds();
        if (worlds.isEmpty()) {
            // Si la liste est vide, tous les mondes sont activés par défaut
            return true;
        }
        return worlds.contains(worldName);
    }

    /**
     * Vérifie si un type de bloc doit être ignoré (ne pas régénérer)
     */
    private boolean shouldIgnoreBlock(Material material) {
        List<String> ignoredBlocks = plugin.getConfig().getStringList("explosion-regeneration.ignored-blocks");
        return ignoredBlocks.contains(material.name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!isEnabled()) {
            return;
        }

        Location explosionLoc = event.getLocation();
        String worldName = explosionLoc.getWorld() != null ? explosionLoc.getWorld().getName() : null;

        if (worldName == null || !isWorldEnabled(worldName)) {
            return;
        }

        // Vérifier si l'explosion est dans une zone claim (ville)
        if (claimManager != null && claimManager.isClaimed(explosionLoc)) {
            // Explosion dans une ville = pas de régénération automatique
            // (la protection de ville gère ça différemment)
            return;
        }

        // Sauvegarder les blocs qui vont être détruits
        List<Block> blocks = event.blockList();
        if (blocks.isEmpty()) {
            return;
        }

        // Enregistrer les blocs avec le centre de l'explosion
        registerBlocksWithExplosionCenter(blocks, explosionLoc);

        plugin.getLogger().fine("[ExplosionRegen] Explosion (entité) détectée à " +
            formatLocation(explosionLoc) + " - " + blocks.size() + " blocs en attente de régénération");
    }

    /**
     * Gère les explosions de blocs (dynamite, bombes custom, lits dans le Nether, etc.)
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!isEnabled()) {
            return;
        }

        Location explosionLoc = event.getBlock().getLocation();
        String worldName = explosionLoc.getWorld() != null ? explosionLoc.getWorld().getName() : null;

        if (worldName == null || !isWorldEnabled(worldName)) {
            return;
        }

        // Vérifier si l'explosion est dans une zone claim (ville)
        if (claimManager != null && claimManager.isClaimed(explosionLoc)) {
            return;
        }

        // Sauvegarder les blocs qui vont être détruits
        List<Block> blocks = event.blockList();
        if (blocks.isEmpty()) {
            return;
        }

        // Enregistrer les blocs avec le centre de l'explosion
        registerBlocksWithExplosionCenter(blocks, explosionLoc);

        plugin.getLogger().fine("[ExplosionRegen] Explosion (bloc) détectée à " +
            formatLocation(explosionLoc) + " - " + blocks.size() + " blocs en attente de régénération");
    }

    /**
     * Enregistre une liste de blocs avec leur centre d'explosion
     * Les blocs sont triés par distance décroissante (plus loin en premier)
     */
    private void registerBlocksWithExplosionCenter(List<Block> blocks, Location explosionCenter) {
        long regenerateAt = System.currentTimeMillis() + (getRegenerationDelay() * 1000L);

        // Créer une liste temporaire pour trier par distance
        List<BlockSnapshot> snapshots = new ArrayList<>();

        for (Block block : blocks) {
            Material material = block.getType();

            // Ignorer les blocs non-solides et certains blocs spéciaux
            if (material.isAir() || shouldIgnoreBlock(material)) {
                continue;
            }

            Location blockLoc = block.getLocation();

            // Vérifier si dans une ville (protection supplémentaire)
            if (claimManager != null && claimManager.isClaimed(blockLoc)) {
                continue;
            }

            // Calculer la distance au centre de l'explosion
            double distance = blockLoc.distance(explosionCenter);

            // Créer un snapshot du bloc avec le centre d'explosion
            BlockSnapshot snapshot = new BlockSnapshot(
                blockLoc.clone(),
                material,
                block.getBlockData().clone(),
                regenerateAt,
                explosionCenter.clone(),
                distance
            );

            snapshots.add(snapshot);
        }

        // Trier par distance décroissante (plus loin en premier = régénère en premier)
        snapshots.sort((a, b) -> Double.compare(b.distanceFromCenter, a.distanceFromCenter));

        // Ajouter à la file dans l'ordre trié
        blocksToRegenerate.addAll(snapshots);

        // Démarrer la tâche de régénération si pas déjà en cours
        startRegenerationTask();
    }

    /**
     * Démarre la tâche de régénération si elle n'est pas déjà en cours
     */
    private synchronized void startRegenerationTask() {
        if (regenerationTaskRunning) {
            return;
        }

        regenerationTaskRunning = true;

        new BukkitRunnable() {
            @Override
            public void run() {
                if (blocksToRegenerate.isEmpty()) {
                    regenerationTaskRunning = false;
                    this.cancel();
                    return;
                }

                long now = System.currentTimeMillis();
                int regeneratedThisTick = 0;
                int blocksPerTick = getBlocksPerTick();

                // Régénérer les blocs dont le délai est écoulé
                // Les blocs sont déjà triés par distance (plus loin en premier)
                Iterator<BlockSnapshot> iterator = blocksToRegenerate.iterator();
                while (iterator.hasNext() && regeneratedThisTick < blocksPerTick) {
                    BlockSnapshot snapshot = iterator.next();

                    if (snapshot.regenerateAt <= now) {
                        // Régénérer le bloc
                        if (regenerateBlock(snapshot)) {
                            regeneratedThisTick++;
                        }
                        iterator.remove();
                    }
                }
            }
        }.runTaskTimer(plugin, getRegenerationInterval(), getRegenerationInterval());
    }

    /**
     * Régénère un bloc à partir de son snapshot
     */
    private boolean regenerateBlock(BlockSnapshot snapshot) {
        Location loc = snapshot.location;

        if (loc.getWorld() == null || !loc.getChunk().isLoaded()) {
            // Chunk non chargé, remettre dans la file
            snapshot.regenerateAt = System.currentTimeMillis() + 5000; // Réessayer dans 5 secondes
            blocksToRegenerate.add(snapshot);
            return false;
        }

        Block block = loc.getBlock();

        // Ne régénérer que si le bloc est actuellement de l'air
        if (!block.getType().isAir()) {
            // Un autre bloc a été placé, ne pas écraser
            return true;
        }

        try {
            // Restaurer le bloc
            block.setType(snapshot.material, false);
            block.setBlockData(snapshot.blockData, false);

            // Effet visuel optionnel
            if (plugin.getConfig().getBoolean("explosion-regeneration.particles", true)) {
                try {
                    loc.getWorld().spawnParticle(
                        org.bukkit.Particle.VILLAGER_HAPPY,
                        loc.clone().add(0.5, 0.5, 0.5),
                        3, 0.3, 0.3, 0.3, 0
                    );
                } catch (Exception ignored) {
                    // Particule non disponible dans cette version
                }
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[ExplosionRegen] Erreur lors de la régénération du bloc à " +
                formatLocation(loc) + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Formate une location pour le logging
     */
    private String formatLocation(Location loc) {
        return String.format("%s (%d, %d, %d)",
            loc.getWorld() != null ? loc.getWorld().getName() : "?",
            loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /**
     * Nettoie les ressources (appelé lors du disable du plugin)
     */
    public void cleanup() {
        blocksToRegenerate.clear();
        regenerationTaskRunning = false;
    }

    /**
     * Retourne le nombre de blocs en attente de régénération
     */
    public int getPendingBlocksCount() {
        return blocksToRegenerate.size();
    }

    /**
     * Enregistre un bloc pour régénération avec le centre de l'explosion
     * Utilisé par ModernPhysicsHandler pour les dynamites/bombes custom
     *
     * @param block Le bloc à enregistrer AVANT sa destruction
     * @param explosionCenter Le centre de l'explosion
     */
    public void registerBlockForRegeneration(Block block, Location explosionCenter) {
        if (!isEnabled()) {
            return;
        }

        Location blockLoc = block.getLocation();
        String worldName = blockLoc.getWorld() != null ? blockLoc.getWorld().getName() : null;

        if (worldName == null || !isWorldEnabled(worldName)) {
            return;
        }

        // Vérifier si le bloc est dans une zone claim (ville)
        if (claimManager != null && claimManager.isClaimed(blockLoc)) {
            return;
        }

        Material material = block.getType();

        // Ignorer les blocs non-solides et certains blocs spéciaux
        if (material.isAir() || shouldIgnoreBlock(material)) {
            return;
        }

        long regenerateAt = System.currentTimeMillis() + (getRegenerationDelay() * 1000L);
        double distance = blockLoc.distance(explosionCenter);

        // Créer un snapshot du bloc
        BlockSnapshot snapshot = new BlockSnapshot(
            blockLoc.clone(),
            material,
            block.getBlockData().clone(),
            regenerateAt,
            explosionCenter.clone(),
            distance
        );

        // Insérer dans la file en maintenant l'ordre de distance
        insertSortedByDistance(snapshot);

        // Démarrer la tâche de régénération si pas déjà en cours
        startRegenerationTask();
    }

    /**
     * Enregistre un bloc pour régénération (sans centre d'explosion spécifié)
     * Le bloc lui-même est utilisé comme centre (distance = 0)
     *
     * @param block Le bloc à enregistrer AVANT sa destruction
     * @deprecated Utiliser registerBlockForRegeneration(Block, Location) pour un tri correct
     */
    @Deprecated
    public void registerBlockForRegeneration(Block block) {
        registerBlockForRegeneration(block, block.getLocation());
    }

    /**
     * Enregistre plusieurs blocs pour régénération avec le centre de l'explosion
     *
     * @param blocks Liste des blocs à enregistrer AVANT leur destruction
     * @param explosionCenter Le centre de l'explosion
     */
    public void registerBlocksForRegeneration(List<Block> blocks, Location explosionCenter) {
        if (!isEnabled() || blocks == null || blocks.isEmpty()) {
            return;
        }

        // Créer une liste temporaire pour trier
        List<BlockSnapshot> snapshots = new ArrayList<>();
        long regenerateAt = System.currentTimeMillis() + (getRegenerationDelay() * 1000L);

        for (Block block : blocks) {
            Location blockLoc = block.getLocation();
            String worldName = blockLoc.getWorld() != null ? blockLoc.getWorld().getName() : null;

            if (worldName == null || !isWorldEnabled(worldName)) {
                continue;
            }

            // Vérifier si le bloc est dans une zone claim (ville)
            if (claimManager != null && claimManager.isClaimed(blockLoc)) {
                continue;
            }

            Material material = block.getType();

            if (material.isAir() || shouldIgnoreBlock(material)) {
                continue;
            }

            double distance = blockLoc.distance(explosionCenter);

            BlockSnapshot snapshot = new BlockSnapshot(
                blockLoc.clone(),
                material,
                block.getBlockData().clone(),
                regenerateAt,
                explosionCenter.clone(),
                distance
            );

            snapshots.add(snapshot);
        }

        if (!snapshots.isEmpty()) {
            // Trier par distance décroissante (plus loin en premier)
            snapshots.sort((a, b) -> Double.compare(b.distanceFromCenter, a.distanceFromCenter));

            // Ajouter à la file
            blocksToRegenerate.addAll(snapshots);

            startRegenerationTask();
            plugin.getLogger().fine("[ExplosionRegen] Explosion custom - " + snapshots.size() +
                " blocs enregistrés pour régénération (extérieur vers intérieur)");
        }
    }

    /**
     * Enregistre plusieurs blocs pour régénération (sans centre spécifié)
     * Calcule le centre comme la moyenne des positions
     *
     * @param blocks Liste des blocs à enregistrer AVANT leur destruction
     */
    public void registerBlocksForRegeneration(List<Block> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return;
        }

        // Calculer le centre de l'explosion comme la moyenne des positions
        double sumX = 0, sumY = 0, sumZ = 0;
        for (Block block : blocks) {
            sumX += block.getX();
            sumY += block.getY();
            sumZ += block.getZ();
        }
        int count = blocks.size();
        Location center = blocks.get(0).getLocation().clone();
        center.setX(sumX / count);
        center.setY(sumY / count);
        center.setZ(sumZ / count);

        registerBlocksForRegeneration(blocks, center);
    }

    /**
     * Insère un snapshot dans la file en maintenant l'ordre de distance décroissante
     * (pour les insertions individuelles depuis ModernPhysicsHandler)
     */
    private void insertSortedByDistance(BlockSnapshot newSnapshot) {
        // Pour une ConcurrentLinkedQueue, on ne peut pas insérer au milieu facilement
        // On collecte tous les snapshots avec le même regenerateAt, on les re-trie et on les réinsère

        // Solution simple : on ajoute juste à la fin
        // Les blocs d'une même explosion seront groupés car ils ont le même regenerateAt
        // et le même explosionCenter, donc ils seront traités dans l'ordre d'insertion
        // qui est déjà de l'extérieur vers l'intérieur grâce aux boucles x,y,z dans ModernPhysicsHandler

        // Pour une meilleure solution, on regroupe par explosion et on trie
        List<BlockSnapshot> sameExplosion = new ArrayList<>();
        List<BlockSnapshot> otherExplosions = new ArrayList<>();

        // Vider la file temporairement
        BlockSnapshot existing;
        while ((existing = blocksToRegenerate.poll()) != null) {
            // Même explosion si même centre et même temps de régénération (à 100ms près)
            if (existing.explosionCenter != null && newSnapshot.explosionCenter != null &&
                existing.explosionCenter.distanceSquared(newSnapshot.explosionCenter) < 1 &&
                Math.abs(existing.regenerateAt - newSnapshot.regenerateAt) < 100) {
                sameExplosion.add(existing);
            } else {
                otherExplosions.add(existing);
            }
        }

        // Ajouter le nouveau snapshot à son groupe
        sameExplosion.add(newSnapshot);

        // Trier le groupe de la même explosion par distance décroissante
        sameExplosion.sort((a, b) -> Double.compare(b.distanceFromCenter, a.distanceFromCenter));

        // Réinsérer tout dans la file : d'abord les autres explosions (qui étaient déjà triées)
        blocksToRegenerate.addAll(otherExplosions);
        // Puis le groupe de la même explosion (trié)
        blocksToRegenerate.addAll(sameExplosion);
    }

    /**
     * Classe interne pour stocker l'état d'un bloc avant destruction
     */
    private static class BlockSnapshot {
        final Location location;
        final Material material;
        final BlockData blockData;
        long regenerateAt;
        final Location explosionCenter;
        final double distanceFromCenter;

        BlockSnapshot(Location location, Material material, BlockData blockData, long regenerateAt,
                      Location explosionCenter, double distanceFromCenter) {
            this.location = location;
            this.material = material;
            this.blockData = blockData;
            this.regenerateAt = regenerateAt;
            this.explosionCenter = explosionCenter;
            this.distanceFromCenter = distanceFromCenter;
        }
    }
}
