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

    // Cache: ChunkCoordinate -> Plot pour récupération directe du plot (groupés ou
    // non)
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
                                parts[0], // world
                                Integer.parseInt(parts[1]), // chunkX
                                Integer.parseInt(parts[2]) // chunkZ
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
     * 
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
                { 0, -1 }, // Nord (Z-)
                { 0, 1 }, // Sud (Z+)
                { -1, 0 }, // Ouest (X-)
                { 1, 0 } // Est (X+)
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
     * 
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

        // Vérifier la limite de claims selon le niveau de la ville
        if (!plugin.getTownLevelManager().canClaimMore(town)) {
            return false; // Limite atteinte
        }

        // Vérifier le solde de la ville
        if (town.getBankBalance() < cost) {
            return false;
        }

        // Prélever le coût
        town.withdraw(cost);

        // Vérifier si c'est le premier claim de la ville
        boolean isFirstClaim = town.getPlots().isEmpty();

        // Créer la parcelle
        Plot plot = new Plot(townName, chunk);
        town.addPlot(plot);

        // Mettre à jour le cache (les deux maps pour cohérence)
        chunkOwners.put(coord, townName);
        chunkToPlot.put(coord, plot);

        plugin.getLogger().info("Chunk " + coord + " claimé par " + townName);

        // NOUVEAU : Si c'est le premier claim, créer automatiquement le spawn
        if (isFirstClaim) {
            createDefaultSpawn(town, chunk);
        }

        // Sauvegarder immédiatement
        townManager.saveTownsNow();
        return true;
    }

    /**
     * Crée automatiquement le spawn de la ville au centre du chunk
     * Appelé lors du premier claim
     */
    private void createDefaultSpawn(Town town, Chunk chunk) {
        // Calculer le centre du chunk
        int centerX = (chunk.getX() << 4) + 8; // chunk.getX() * 16 + 8
        int centerZ = (chunk.getZ() << 4) + 8;

        // Trouver le Y le plus haut qui est safe (bloc solide avec 2 blocs d'air au-dessus)
        org.bukkit.World world = chunk.getWorld();
        int highestY = world.getHighestBlockYAt(centerX, centerZ);

        // Créer la location du spawn (légèrement au-dessus du sol)
        Location spawnLocation = new Location(world, centerX + 0.5, highestY + 1, centerZ + 0.5);

        town.setSpawnLocation(spawnLocation);

        // Notifier le maire et les adjoints
        notifyTownAdmins(town, "§a§l✓ SPAWN CRÉÉ §r§a- Le point de spawn de la ville a été défini automatiquement au centre du premier terrain claimé.");

        plugin.getLogger().info("Spawn automatique créé pour " + town.getName() + " à " +
            spawnLocation.getBlockX() + ", " + spawnLocation.getBlockY() + ", " + spawnLocation.getBlockZ());
    }

    /**
     * Notifie le maire et les adjoints d'un message important concernant la ville
     */
    private void notifyTownAdmins(Town town, String message) {
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            for (com.gravityyfh.roleplaycity.town.data.TownMember member : town.getMembers().values()) {
                if (member.getRole() == com.gravityyfh.roleplaycity.town.data.TownRole.MAIRE ||
                    member.getRole() == com.gravityyfh.roleplaycity.town.data.TownRole.ADJOINT) {
                    org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(member.getPlayerUuid());
                    if (player != null && player.isOnline()) {
                        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                        player.sendMessage("§6§l🏛 " + town.getName().toUpperCase());
                        player.sendMessage(message);
                        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                    }
                }
            }
        });
    }

    /**
     * Unclaim un chunk
     * 
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
        Plot plot = town.getPlot(coord.worldName(), coord.x(), coord.z());
        if (plot == null) {
            return 0.0;
        }

        // Empêcher unclaim si le terrain est groupé (multi-chunks)
        if (plot.isGrouped()) {
            plugin.getLogger().warning(String.format(
                    "Tentative d'unclaim d'un chunk faisant partie du terrain groupé '%s' (ville: %s)",
                    plot.getGroupName(), townName));
            return -1.0; // Code d'erreur spécial pour indiquer "chunk dans un groupe"
        }

        // Calculer le remboursement
        double claimCost = plugin.getConfig().getDouble("town.claim-cost-per-chunk", 500.0);
        double refund = claimCost * (refundPercentage / 100.0);

        // Nettoyer la mailbox si nécessaire avant de supprimer le plot
        if (plot.hasMailbox() && plugin.getMailboxManager() != null) {
            plugin.getMailboxManager().removeMailbox(plot);
        }

        // Fire event AVANT de supprimer la parcelle
        com.gravityyfh.roleplaycity.town.event.TownUnclaimPlotEvent event = new com.gravityyfh.roleplaycity.town.event.TownUnclaimPlotEvent(
                townName, plot, coord.worldName(), coord.x(), coord.z());
        org.bukkit.Bukkit.getPluginManager().callEvent(event);

        // Supprimer la parcelle
        town.removePlot(coord.worldName(), coord.x(), coord.z());

        // Mettre à jour le cache (les deux maps pour cohérence)
        chunkOwners.remove(coord);
        chunkToPlot.remove(coord);

        // Rembourser
        town.deposit(refund);

        plugin.getLogger().info("Chunk " + coord + " unclaimed par " + townName + " (remboursement: " + refund + "€)");

        // NOUVEAU : Vérifier si le spawn était sur ce chunk et le déplacer si nécessaire
        validateAndRelocateSpawn(town);

        // Sauvegarder immédiatement
        townManager.saveTownsNow();
        return refund;
    }

    /**
     * Vérifie si le spawn de la ville est toujours sur un terrain valide
     * Si non, le déplace automatiquement vers un autre terrain
     */
    public void validateAndRelocateSpawn(Town town) {
        if (!town.hasSpawnLocation()) {
            return; // Pas de spawn, rien à faire
        }

        Location spawnLoc = town.getSpawnLocation();

        // Vérifier si le spawn est toujours sur un terrain de la ville
        if (isSpawnLocationValid(town, spawnLoc)) {
            return; // Spawn toujours valide
        }

        // Le spawn n'est plus valide, chercher un nouveau terrain
        Plot newSpawnPlot = findBestPlotForSpawn(town);

        if (newSpawnPlot == null) {
            // Plus aucun terrain ! Supprimer le spawn
            town.setSpawnLocation(null);
            notifyTownAdmins(town, "§c§l⚠ SPAWN SUPPRIMÉ §r§c- La ville n'a plus de terrain, le point de spawn a été supprimé.");
            plugin.getLogger().warning("Spawn de " + town.getName() + " supprimé car plus aucun terrain disponible");
            return;
        }

        // Calculer le nouveau spawn au centre du nouveau terrain
        Location newSpawn = calculateSpawnLocation(newSpawnPlot);
        town.setSpawnLocation(newSpawn);

        notifyTownAdmins(town, "§e§l⚠ SPAWN DÉPLACÉ §r§e- Le terrain contenant le spawn a été supprimé. Le spawn a été automatiquement déplacé vers un autre terrain.");

        plugin.getLogger().info("Spawn de " + town.getName() + " déplacé vers " +
            newSpawn.getBlockX() + ", " + newSpawn.getBlockY() + ", " + newSpawn.getBlockZ());
    }

    /**
     * Vérifie si la location du spawn est sur un terrain appartenant à la ville
     */
    public boolean isSpawnLocationValid(Town town, Location location) {
        if (location == null) {
            return false;
        }

        ChunkCoordinate spawnCoord = ChunkCoordinate.fromLocation(location);
        String owner = getClaimOwner(spawnCoord);

        return town.getName().equals(owner);
    }

    /**
     * Trouve le meilleur terrain pour placer le spawn
     * Priorité : MUNICIPAL (Mairie) > MUNICIPAL (autre) > PUBLIC > autres
     */
    private Plot findBestPlotForSpawn(Town town) {
        Plot bestPlot = null;
        int bestPriority = -1;

        for (Plot plot : town.getPlots().values()) {
            int priority = getPlotSpawnPriority(plot);
            if (priority > bestPriority) {
                bestPriority = priority;
                bestPlot = plot;
            }
        }

        return bestPlot;
    }

    /**
     * Calcule la priorité d'un terrain pour y placer le spawn
     * Plus le nombre est élevé, plus le terrain est prioritaire
     */
    private int getPlotSpawnPriority(Plot plot) {
        com.gravityyfh.roleplaycity.town.data.PlotType type = plot.getType();

        // Municipal avec sous-type MAIRIE = priorité maximale
        if (type == com.gravityyfh.roleplaycity.town.data.PlotType.MUNICIPAL) {
            if (plot.getMunicipalSubType() == com.gravityyfh.roleplaycity.town.data.MunicipalSubType.MAIRIE) {
                return 100;
            }
            return 80; // Autre municipal
        }

        // Public = bonne priorité
        if (type == com.gravityyfh.roleplaycity.town.data.PlotType.PUBLIC) {
            return 60;
        }

        // Particulier/Professionnel = priorité basse
        return 20;
    }

    /**
     * Calcule la location du spawn au centre d'un terrain
     */
    private Location calculateSpawnLocation(Plot plot) {
        // Récupérer le premier chunk du plot
        String firstChunkKey = plot.getChunks().iterator().next();
        String[] parts = firstChunkKey.split(":");

        String worldName = parts[0];
        int chunkX = Integer.parseInt(parts[1]);
        int chunkZ = Integer.parseInt(parts[2]);

        org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }

        // Centre du chunk
        int centerX = (chunkX << 4) + 8;
        int centerZ = (chunkZ << 4) + 8;
        int highestY = world.getHighestBlockYAt(centerX, centerZ);

        return new Location(world, centerX + 0.5, highestY + 1, centerZ + 0.5);
    }

    /**
     * Supprime tous les claims d'une ville (utilisé lors de la suppression d'une
     * ville)
     */
    /**
     * Supprime tous les claims d'une ville (utilisé lors de la suppression d'une
     * ville)
     */
    public void removeAllClaims(String townName) {
        // Identifier les chunks à supprimer pour éviter ConcurrentModificationException
        java.util.List<ChunkCoordinate> toRemove = new java.util.ArrayList<>();

        for (Map.Entry<ChunkCoordinate, String> entry : chunkOwners.entrySet()) {
            if (entry.getValue().equals(townName)) {
                toRemove.add(entry.getKey());
            }
        }

        // Supprimer des deux caches
        for (ChunkCoordinate coord : toRemove) {
            chunkOwners.remove(coord);
            chunkToPlot.remove(coord);
        }

        plugin.getLogger().info(
                "Tous les claims de " + townName + " ont été supprimés du cache (" + toRemove.size() + " chunks).");
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
