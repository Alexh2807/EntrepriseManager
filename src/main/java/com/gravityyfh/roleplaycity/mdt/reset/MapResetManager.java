package com.gravityyfh.roleplaycity.mdt.reset;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.mdt.config.MDTConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;

import java.util.Map;
import java.util.Set;

/**
 * Gestionnaire de reset de la map MDT après chaque partie
 */
public class MapResetManager {
    private final RoleplayCity plugin;
    private final MDTConfig config;
    private final BlockTracker blockTracker;

    public MapResetManager(RoleplayCity plugin, MDTConfig config, BlockTracker blockTracker) {
        this.plugin = plugin;
        this.config = config;
        this.blockTracker = blockTracker;
    }

    /**
     * Réinitialise la map à son état original
     */
    public void resetMap() {
        plugin.getLogger().info("[MDT] Début du reset de la map...");

        World world = config.getWorld();
        if (world == null) {
            plugin.getLogger().warning("[MDT] Impossible de reset: monde '" + config.getWorldName() + "' introuvable!");
            return;
        }

        int blocksReset = 0;
        int blocksSkipped = 0;

        // 1. Retirer tous les blocs placés par les joueurs
        plugin.getLogger().info("[MDT] Blocs placés trackés: " + blockTracker.getPlayerPlacedBlockCount());
        Set<Location> playerBlocks = blockTracker.getPlayerPlacedBlocks();
        plugin.getLogger().info("[MDT] Blocs à supprimer (après conversion): " + playerBlocks.size());

        for (Location loc : playerBlocks) {
            if (loc.getWorld() != null && loc.getWorld().getName().equals(world.getName())) {
                loc.getBlock().setType(Material.AIR, false);
                blocksReset++;
            } else {
                blocksSkipped++;
                plugin.getLogger().warning("[MDT] Bloc ignoré (monde différent): " + loc);
            }
        }

        if (blocksSkipped > 0) {
            plugin.getLogger().warning("[MDT] " + blocksSkipped + " blocs ignorés (monde différent)");
        }

        // 2. Restaurer les blocs originaux qui ont été cassés
        Map<Location, BlockData> originalBlocks = blockTracker.getOriginalBlocks();
        for (Map.Entry<Location, BlockData> entry : originalBlocks.entrySet()) {
            Location loc = entry.getKey();
            BlockData originalData = entry.getValue();

            if (loc.getWorld() == null || !loc.getWorld().equals(world)) {
                continue;
            }

            // Si l'original était null, c'était de l'air avant (bloc placé par joueur)
            if (originalData == null) {
                // Déjà traité ci-dessus
                continue;
            }

            // Restaurer le bloc original
            if (!loc.getBlock().getBlockData().equals(originalData)) {
                loc.getBlock().setBlockData(originalData, false);
                blocksReset++;
            }
        }

        // 3. Nettoyer les items au sol
        int itemsRemoved = 0;
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Item || entity instanceof ExperienceOrb) {
                entity.remove();
                itemsRemoved++;
            }
        }

        // 4. Nettoyer le tracker
        blockTracker.clear();

        plugin.getLogger().info("[MDT] Reset terminé: " + blocksReset + " blocs restaurés, " +
                itemsRemoved + " items retirés");
    }

    /**
     * Régénère les lits à leur emplacement original
     * (appelé séparément si les lits ont été détruits)
     */
    public void regenerateBeds() {
        // Les lits sont définis dans la config et doivent être placés manuellement
        // lors de la création de la map. Cette méthode pourrait être étendue
        // pour placer automatiquement les lits si nécessaire.
        plugin.getLogger().info("[MDT] Régénération des lits non implémentée - " +
                "les lits doivent être présents sur la map de base");
    }
}
