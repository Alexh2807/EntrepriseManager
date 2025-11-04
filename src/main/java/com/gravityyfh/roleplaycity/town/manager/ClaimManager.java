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

    public ClaimManager(RoleplayCity plugin, TownManager townManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.chunkOwners = new ConcurrentHashMap<>();
    }

    /**
     * Initialise le cache à partir des villes existantes
     */
    public void rebuildCache() {
        chunkOwners.clear();
        for (Town town : townManager.getAllTowns()) {
            for (Plot plot : town.getPlots().values()) {
                ChunkCoordinate coord = new ChunkCoordinate(
                    plot.getWorldName(),
                    plot.getChunkX(),
                    plot.getChunkZ()
                );
                chunkOwners.put(coord, town.getName());
            }
        }
        plugin.getLogger().info("Cache de claims reconstruit: " + chunkOwners.size() + " chunks.");
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
     */
    public Plot getPlotAt(ChunkCoordinate coord) {
        String townName = getClaimOwner(coord);
        if (townName == null) {
            return null;
        }

        Town town = townManager.getTown(townName);
        if (town == null) {
            return null;
        }

        return town.getPlot(coord.getWorldName(), coord.getX(), coord.getZ());
    }

    public Plot getPlotAt(Chunk chunk) {
        return getPlotAt(new ChunkCoordinate(chunk));
    }

    public Plot getPlotAt(Location location) {
        return getPlotAt(ChunkCoordinate.fromLocation(location));
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

        // Vérifier le solde de la ville
        if (town.getBankBalance() < cost) {
            return false;
        }

        // Prélever le coût
        town.withdraw(cost);

        // Créer la parcelle
        Plot plot = new Plot(townName, chunk);
        town.addPlot(plot);

        // Mettre à jour le cache
        chunkOwners.put(coord, townName);

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

        // Récupérer la parcelle
        Plot plot = town.getPlot(coord.getWorldName(), coord.getX(), coord.getZ());
        if (plot == null) {
            return 0.0;
        }

        // Calculer le remboursement
        double claimCost = plugin.getConfig().getDouble("town.claim-cost-per-chunk", 500.0);
        double refund = claimCost * (refundPercentage / 100.0);

        // Supprimer la parcelle
        town.removePlot(coord.getWorldName(), coord.getX(), coord.getZ());

        // Mettre à jour le cache
        chunkOwners.remove(coord);

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
