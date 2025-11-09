package com.gravityyfh.roleplaycity.town.manager;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.ChunkCoordinate;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.Town;
import org.bukkit.Chunk;
import org.bukkit.Location;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire des claims de chunks par les villes
 * Permet une recherche rapide des chunks claimés
 */
public class ClaimManager {
    private final RoleplayCity plugin;
    private final TownManager townManager;

    // Cache: ChunkCoordinate -> TownName pour recherche ultra-rapide
    private final Map<ChunkCoordinate, String> chunkOwners;

    // Cache: ChunkCoordinate -> Plot pour récupération directe du plot (groupés ou non)
    private final Map<ChunkCoordinate, Plot> chunkToPlot;

    public ClaimManager(RoleplayCity plugin, TownManager townManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.chunkOwners = new ConcurrentHashMap<>();
        this.chunkToPlot = new ConcurrentHashMap<>();
    }

    /**
     * Initialise le cache à partir des villes existantes
     * ⚠️ SYSTÈME UNIFIÉ : Chaque Plot peut avoir 1 ou plusieurs chunks
     */
    public void rebuildCache() {
        chunkOwners.clear();
        chunkToPlot.clear();
        int plotCount = 0;
        int totalChunkCount = 0;

        for (Town town : townManager.getAllTowns()) {
            // Parcourir tous les plots (qu'ils soient simples ou groupés)
            for (Plot plot : town.getPlots().values()) {
                // Chaque plot a une liste de chunks (1 ou plusieurs)
                for (String chunkKey : plot.getChunks()) {
                    String[] parts = chunkKey.split(":");
                    if (parts.length == 3) {
                        ChunkCoordinate coord = new ChunkCoordinate(
                            parts[0],  // world
                            Integer.parseInt(parts[1]),  // chunkX
                            Integer.parseInt(parts[2])   // chunkZ
                        );
                        chunkOwners.put(coord, town.getName());
                        chunkToPlot.put(coord, plot); // Cache direct vers le plot !
                        totalChunkCount++;
                    }
                }
                plotCount++;
            }
        }

        plugin.getLogger().info("Cache de claims reconstruit: " + totalChunkCount + " chunks total dans " +
                               plotCount + " parcelles (simples et groupées).");
    }

    /**
     * Vérifie si un chunk est claimé
     */
    public boolean isClaimed(ChunkCoordinate coord) {
        return chunkOwners.containsKey(coord);
    }

    public boolean isClaimed(Chunk chunk) {
        return isClaimed(new ChunkCoordinate(chunk));
    }

    public boolean isClaimed(Location location) {
        return isClaimed(ChunkCoordinate.fromLocation(location));
    }

    /**
     * Récupère le nom de la ville qui possède ce chunk
     * @return Le nom de la ville, ou null si non claimé
     */
    public String getClaimOwner(ChunkCoordinate coord) {
        return chunkOwners.get(coord);
    }

    public String getClaimOwner(Chunk chunk) {
        return getClaimOwner(new ChunkCoordinate(chunk));
    }

    public String getClaimOwner(Location location) {
        return getClaimOwner(ChunkCoordinate.fromLocation(location));
    }

    /**
     * Récupère la parcelle à cette position
     * Utilise le cache direct pour une recherche O(1) ultra-rapide
     */
    public Plot getPlotAt(ChunkCoordinate coord) {
        // Recherche directe dans le cache (O(1) - ultra-rapide)
        return chunkToPlot.get(coord);
    }

    public Plot getPlotAt(Chunk chunk) {
        return getPlotAt(new ChunkCoordinate(chunk));
    }

    public Plot getPlotAt(Location location) {
        return getPlotAt(ChunkCoordinate.fromLocation(location));
    }

    /**
     * Vérifie si un chunk est adjacent à au moins un chunk de la ville
     * Un chunk est adjacent s'il partage un côté (pas diagonal)
     */
    public boolean isAdjacentToTownClaim(String townName, Chunk chunk) {
        Town town = townManager.getTown(townName);
        if (town == null || town.getPlots().isEmpty()) {
            return true; // Premier claim, toujours autorisé
        }

        String worldName = chunk.getWorld().getName();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();

        // Vérifier les 4 directions (Nord, Sud, Est, Ouest)
        int[][] directions = {
            {0, -1},  // Nord (Z-)
            {0, 1},   // Sud (Z+)
            {-1, 0},  // Ouest (X-)
            {1, 0}    // Est (X+)
        };

        for (int[] dir : directions) {
            int adjacentX = chunkX + dir[0];
            int adjacentZ = chunkZ + dir[1];

            // Vérifier si ce chunk adjacent appartient à la ville
            Plot adjacentPlot = town.getPlot(worldName, adjacentX, adjacentZ);
            if (adjacentPlot != null) {
                return true; // Trouvé un chunk adjacent de la ville
            }
        }

        return false; // Aucun chunk adjacent trouvé
    }

    /**
     * Claim un chunk pour une ville
     * @return true si le claim a réussi, false sinon
     */
    public boolean claimChunk(String townName, Chunk chunk, double cost) {
        ChunkCoordinate coord = new ChunkCoordinate(chunk);

        // Vérifier si déjà claimé
        if (isClaimed(coord)) {
            return false;
        }

        Town town = townManager.getTown(townName);
        if (town == null) {
            return false;
        }

        // NOUVEAU : Vérifier que le chunk est adjacent (sauf premier claim)
        if (!isAdjacentToTownClaim(townName, chunk)) {
            return false;
        }

        // Vérifier le solde de la ville
        if (town.getBankBalance() < cost) {
            return false;
        }

        // Prélever le coût
        town.withdraw(cost);

        // Créer la parcelle
        Plot plot = new Plot(townName, chunk);
        town.addPlot(plot);

        // Mettre à jour le cache (les deux maps pour cohérence)
        chunkOwners.put(coord, townName);
        chunkToPlot.put(coord, plot);

        plugin.getLogger().info("Chunk " + coord + " claimé par " + townName);

        // Sauvegarder immédiatement
        townManager.saveTownsNow();
        return true;
    }

    /**
     * Unclaim un chunk
     * @return Le montant remboursé, ou 0 si échec
     */
    public double unclaimChunk(String townName, Chunk chunk, double refundPercentage) {
        ChunkCoordinate coord = new ChunkCoordinate(chunk);

        // Vérifier que ce chunk appartient bien à cette ville
        String owner = getClaimOwner(coord);
        if (owner == null || !owner.equals(townName)) {
            return 0.0;
        }

        Town town = townManager.getTown(townName);
        if (town == null) {
            return 0.0;
        }

        // ⚠️ SYSTÈME UNIFIÉ : Empêcher unclaim si chunk fait partie d'un terrain groupé
        // Récupérer la parcelle
        Plot plot = town.getPlot(coord.getWorldName(), coord.getX(), coord.getZ());
        if (plot == null) {
            return 0.0;
        }

        // Empêcher unclaim si le terrain est groupé (multi-chunks)
        if (plot.isGrouped()) {
            plugin.getLogger().warning(String.format(
                "Tentative d'unclaim d'un chunk faisant partie du terrain groupé '%s' (ville: %s)",
                plot.getGroupName(), townName
            ));
            return -1.0; // Code d'erreur spécial pour indiquer "chunk dans un groupe"
        }

        // Calculer le remboursement
        double claimCost = plugin.getConfig().getDouble("town.claim-cost-per-chunk", 500.0);
        double refund = claimCost * (refundPercentage / 100.0);

        // Supprimer la parcelle
        town.removePlot(coord.getWorldName(), coord.getX(), coord.getZ());

        // Mettre à jour le cache (les deux maps pour cohérence)
        chunkOwners.remove(coord);
        chunkToPlot.remove(coord);

        // Rembourser
        town.deposit(refund);

        plugin.getLogger().info("Chunk " + coord + " unclaimed par " + townName + " (remboursement: " + refund + "€)");

        // Sauvegarder immédiatement
        townManager.saveTownsNow();
        return refund;
    }

    /**
     * Supprime tous les claims d'une ville (utilisé lors de la suppression d'une ville)
     */
    public void removeAllClaims(String townName) {
        chunkOwners.entrySet().removeIf(entry -> entry.getValue().equals(townName));
        plugin.getLogger().info("Tous les claims de " + townName + " ont été supprimés.");
    }

    /**
     * Récupère le nombre total de chunks claimés
     */
    public int getTotalClaims() {
        return chunkOwners.size();
    }

    /**
     * Récupère le nombre de chunks claimés par une ville
     */
    public int getClaimCount(String townName) {
        return (int) chunkOwners.values().stream()
            .filter(name -> name.equals(townName))
            .count();
    }
}
