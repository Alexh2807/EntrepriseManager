package com.gravityyfh.roleplaycity.town.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.PlotType;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.manager.ClaimManager;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Listener pour la sélection interactive de parcelles à grouper
 * Avec système de particules colorées et clic droit direct
 */
public class PlotGroupingListener implements Listener {
    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final ClaimManager claimManager;

    // Sessions actives de groupement
    private final Map<UUID, GroupingSession> activeSessions = new HashMap<>();

    // Protection double-clic (UUID -> dernière interaction en ms)
    private final Map<UUID, Long> lastClickTime = new HashMap<>();
    private static final long DOUBLE_CLICK_THRESHOLD = 300; // 300ms

    /**
     * Session de groupement avec particules
     */
    private static class GroupingSession {
        final String townName;
        final Set<String> selectedChunkKeys; // "world:x:z"
        final long startTime;
        int particleTaskId = -1;

        GroupingSession(String townName) {
            this.townName = townName;
            this.selectedChunkKeys = new HashSet<>();
            this.startTime = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - startTime > 300000; // 5 minutes
        }
    }

    public PlotGroupingListener(RoleplayCity plugin, TownManager townManager, ClaimManager claimManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.claimManager = claimManager;
    }

    /**
     * Démarre une session de groupement
     */
    public void startGroupingSession(Player player, String townName) {
        Town town = townManager.getTown(townName);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Ville introuvable.");
            return;
        }

        // Annuler session précédente si existante
        if (activeSessions.containsKey(player.getUniqueId())) {
            cancelSession(player);
        }

        GroupingSession session = new GroupingSession(townName);
        activeSessions.put(player.getUniqueId(), session);

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "MODE GROUPEMENT DE PARCELLES");
        player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage(ChatColor.YELLOW + "Instructions:");
        player.sendMessage(ChatColor.WHITE + "→ Faites " + ChatColor.AQUA + "clic droit" + ChatColor.WHITE + " sur les parcelles à grouper");
        player.sendMessage(ChatColor.WHITE + "→ Minimum 2 parcelles adjacentes requises");
        player.sendMessage("");
        player.sendMessage(ChatColor.AQUA + "🔵 Bleu" + ChatColor.GRAY + " = Peut être sélectionné");
        player.sendMessage(ChatColor.GREEN + "🟢 Vert" + ChatColor.GRAY + " = Déjà sélectionné");
        player.sendMessage(ChatColor.RED + "🔴 Rouge" + ChatColor.GRAY + " = Impossible à sélectionner");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Session active pendant 5 minutes");
        player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("");

        // Démarrer l'affichage des particules
        startParticleTask(player, session);

        // Envoyer les boutons
        sendActionButtons(player);
    }

    /**
     * Démarre la tâche d'affichage des particules
     */
    private void startParticleTask(Player player, GroupingSession session) {
        int taskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (!activeSessions.containsKey(player.getUniqueId()) || !player.isOnline()) {
                    cancel();
                    return;
                }

                displayParticlesForAllPlots(player, session);
            }
        }.runTaskTimer(plugin, 0L, 10L).getTaskId(); // Toutes les 0.5 secondes

        session.particleTaskId = taskId;
    }

    /**
     * Affiche les particules pour tous les terrains autour du joueur
     */
    private void displayParticlesForAllPlots(Player player, GroupingSession session) {
        Town town = townManager.getTown(session.townName);
        if (town == null) return;

        // Rayon de chunks autour du joueur (5 chunks = 80 blocs)
        Chunk playerChunk = player.getLocation().getChunk();
        int radius = 5;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int chunkX = playerChunk.getX() + dx;
                int chunkZ = playerChunk.getZ() + dz;

                Plot plot = town.getPlot(player.getWorld().getName(), chunkX, chunkZ);
                if (plot == null) continue;

                // NOUVEAU : Ignorer les terrains MUNICIPAL et PUBLIC (ne pas afficher de particules)
                if (plot.getType() != PlotType.PARTICULIER && plot.getType() != PlotType.PROFESSIONNEL) {
                    continue;
                }

                String chunkKey = player.getWorld().getName() + ":" + chunkX + ":" + chunkZ;

                // Déterminer la couleur
                Particle.DustOptions color;
                if (session.selectedChunkKeys.contains(chunkKey)) {
                    // VERT = Sélectionné
                    color = new Particle.DustOptions(Color.fromRGB(0, 255, 0), 1.5f);
                } else if (canSelectPlot(plot, chunkKey, session, town)) {
                    // BLEU = Peut être sélectionné
                    color = new Particle.DustOptions(Color.fromRGB(0, 191, 255), 1.5f);
                } else {
                    // ROUGE = Impossible (déjà groupé ou mauvais propriétaire)
                    color = new Particle.DustOptions(Color.fromRGB(255, 0, 0), 1.5f);
                }

                // Afficher les particules autour du chunk
                displayChunkBorder(player, chunkX, chunkZ, color);
            }
        }
    }

    /**
     * Affiche les particules autour d'un chunk (style bas et dense comme les terrains à vendre)
     */
    private void displayChunkBorder(Player player, int chunkX, int chunkZ, Particle.DustOptions color) {
        World world = player.getWorld();
        int startX = chunkX * 16;
        int startZ = chunkZ * 16;

        // Particules sur les 4 côtés
        // Côté Nord (Z min)
        for (int x = startX; x < startX + 16; x++) {
            spawnParticleColumn(world, x + 0.5, startZ + 0.5, color);
        }

        // Côté Est (X max)
        for (int z = startZ; z < startZ + 16; z++) {
            spawnParticleColumn(world, startX + 16.0, z + 0.5, color);
        }

        // Côté Sud (Z max)
        for (int x = startX; x < startX + 16; x++) {
            spawnParticleColumn(world, x + 0.5, startZ + 16.0, color);
        }

        // Côté Ouest (X min)
        for (int z = startZ; z < startZ + 16; z++) {
            spawnParticleColumn(world, startX + 0.0, z + 0.5, color);
        }
    }

    /**
     * Spawn une colonne de particules dense et basse (comme pour les terrains à vendre)
     */
    private void spawnParticleColumn(World world, double x, double z, Particle.DustOptions color) {
        int groundY = world.getHighestBlockYAt((int) x, (int) z);

        // Spawn 5 particules espacées verticalement (environ 2 blocs de hauteur)
        for (int i = 0; i < 5; i++) {
            double y = groundY + 0.2 + (i * 0.4);

            // Spawn 3 particules pour plus de densité
            for (int j = 0; j < 3; j++) {
                world.spawnParticle(
                    Particle.REDSTONE,
                    x + (Math.random() * 0.2 - 0.1),
                    y,
                    z + (Math.random() * 0.2 - 0.1),
                    1,
                    0, 0, 0, 0,
                    color
                );
            }
        }
    }

    /**
     * Vérifie si un chunk est adjacent à au moins un chunk sélectionné
     */
    private boolean isAdjacentToSelection(String chunkKey, Set<String> selectedChunks) {
        if (selectedChunks.isEmpty()) return true; // Première sélection toujours OK

        String[] parts = chunkKey.split(":");
        if (parts.length != 3) return false;

        int chunkX = Integer.parseInt(parts[1]);
        int chunkZ = Integer.parseInt(parts[2]);
        String world = parts[0];

        // Vérifier les 4 directions (Nord, Sud, Est, Ouest)
        String[] neighbors = {
            world + ":" + (chunkX + 1) + ":" + chunkZ,  // Est
            world + ":" + (chunkX - 1) + ":" + chunkZ,  // Ouest
            world + ":" + chunkX + ":" + (chunkZ + 1),  // Sud
            world + ":" + chunkX + ":" + (chunkZ - 1)   // Nord
        };

        for (String neighbor : neighbors) {
            if (selectedChunks.contains(neighbor)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Vérifie si un plot peut être sélectionné
     */
    private boolean canSelectPlot(Plot plot, String chunkKey, GroupingSession session, Town town) {
        // Déjà sélectionné
        if (session.selectedChunkKeys.contains(chunkKey)) {
            return false;
        }

        // Déjà groupé
        if (plot.isGrouped()) {
            return false;
        }

        // Type incompatible
        if (plot.getType() != PlotType.PARTICULIER && plot.getType() != PlotType.PROFESSIONNEL) {
            return false;
        }

        // Si aucune sélection, OK
        if (session.selectedChunkKeys.isEmpty()) {
            return true;
        }

        // Vérifier même propriétaire et même type
        String firstKey = session.selectedChunkKeys.iterator().next();
        String[] parts = firstKey.split(":");
        Plot firstPlot = town.getPlot(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));

        if (firstPlot == null) return false;

        // Même type
        if (plot.getType() != firstPlot.getType()) {
            return false;
        }

        // Même propriétaire (ou les deux sans proprio)
        if (firstPlot.getOwnerUuid() == null && plot.getOwnerUuid() == null) {
            // OK, continuer à vérifier l'adjacence
        } else if (firstPlot.getOwnerUuid() == null || plot.getOwnerUuid() == null) {
            return false;
        } else if (!firstPlot.getOwnerUuid().equals(plot.getOwnerUuid())) {
            return false;
        }

        // VÉRIFICATION CRITIQUE : Le terrain doit être adjacent à la sélection
        return isAdjacentToSelection(chunkKey, session.selectedChunkKeys);
    }

    /**
     * Envoie les boutons cliquables
     */
    private void sendActionButtons(Player player) {
        TextComponent message = new TextComponent("  ");

        // Bouton TERMINER (génère automatiquement un nom)
        TextComponent finishButton = new TextComponent("[✓ TERMINER]");
        finishButton.setColor(net.md_5.bungee.api.ChatColor.GREEN);
        finishButton.setBold(true);
        finishButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ville:finishgrouping auto"));
        finishButton.setHoverEvent(new HoverEvent(
            HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder("Cliquez pour terminer le groupement")
                .color(net.md_5.bungee.api.ChatColor.YELLOW)
                .append("\n")
                .color(net.md_5.bungee.api.ChatColor.GRAY)
                .append("Un nom sera généré automatiquement")
                .color(net.md_5.bungee.api.ChatColor.GRAY)
                .create()
        ));
        message.addExtra(finishButton);

        // Bouton ANNULER
        TextComponent cancelButton = new TextComponent(" [✗ ANNULER]");
        cancelButton.setColor(net.md_5.bungee.api.ChatColor.RED);
        cancelButton.setBold(true);
        cancelButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ville:cancelgrouping"));
        cancelButton.setHoverEvent(new HoverEvent(
            HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder("Cliquez pour annuler")
                .color(net.md_5.bungee.api.ChatColor.RED)
                .create()
        ));
        message.addExtra(cancelButton);

        player.spigot().sendMessage(message);
        player.sendMessage("");
    }

    /**
     * Gestion du clic droit
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        GroupingSession session = activeSessions.get(player.getUniqueId());

        if (session == null) return;

        // Vérifier expiration
        if (session.isExpired()) {
            cancelSession(player);
            player.sendMessage(ChatColor.RED + "Session de groupement expirée.");
            return;
        }

        // Seulement clic droit
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }

        // Protection double-clic
        long currentTime = System.currentTimeMillis();
        Long lastClick = lastClickTime.get(player.getUniqueId());
        if (lastClick != null && (currentTime - lastClick) < DOUBLE_CLICK_THRESHOLD) {
            // Double-clic détecté, ignorer silencieusement
            return;
        }
        lastClickTime.put(player.getUniqueId(), currentTime);

        // Annuler l'événement pour éviter interactions
        event.setCancelled(true);

        Chunk chunk = player.getLocation().getChunk();
        String chunkKey = chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();

        // Vérifier que c'est un terrain de la ville
        String claimOwner = claimManager.getClaimOwner(chunk);
        if (claimOwner == null || !claimOwner.equals(session.townName)) {
            player.sendMessage(ChatColor.RED + "Cette parcelle n'appartient pas à " + session.townName);
            return;
        }

        Town town = townManager.getTown(session.townName);
        if (town == null) {
            cancelSession(player);
            return;
        }

        Plot plot = claimManager.getPlotAt(chunk);
        if (plot == null) {
            player.sendMessage(ChatColor.RED + "Parcelle introuvable.");
            return;
        }

        // Vérifier si déjà sélectionné -> Désélectionner
        if (session.selectedChunkKeys.contains(chunkKey)) {
            session.selectedChunkKeys.remove(chunkKey);
            player.sendMessage(ChatColor.YELLOW + "✗ Parcelle retirée (" + session.selectedChunkKeys.size() + " sélectionnées)");

            // Ré-afficher les boutons
            player.sendMessage("");
            sendActionButtons(player);
            return;
        }

        // Vérifier si peut être sélectionné
        if (!canSelectPlot(plot, chunkKey, session, town)) {
            if (plot.isGrouped()) {
                player.sendMessage(ChatColor.RED + "Cette parcelle fait déjà partie du groupe: " + plot.getGroupName());
            } else if (plot.getType() != PlotType.PARTICULIER && plot.getType() != PlotType.PROFESSIONNEL) {
                player.sendMessage(ChatColor.RED + "Seules les parcelles PARTICULIER et PROFESSIONNEL peuvent être groupées.");
            } else if (!session.selectedChunkKeys.isEmpty()) {
                String firstKey = session.selectedChunkKeys.iterator().next();
                String[] parts = firstKey.split(":");
                Plot firstPlot = town.getPlot(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));

                if (firstPlot != null && plot.getType() != firstPlot.getType()) {
                    player.sendMessage(ChatColor.RED + "Toutes les parcelles doivent être du même type!");
                } else if (firstPlot != null && !Objects.equals(plot.getOwnerUuid(), firstPlot.getOwnerUuid())) {
                    player.sendMessage(ChatColor.RED + "Toutes les parcelles doivent avoir le même propriétaire!");
                } else if (!isAdjacentToSelection(chunkKey, session.selectedChunkKeys)) {
                    // Message spécifique pour non-adjacence
                    player.sendMessage(ChatColor.RED + "Cette parcelle n'est pas adjacente à votre sélection!");
                    player.sendMessage(ChatColor.YELLOW + "Seules les parcelles côte à côte (bord à bord) peuvent être groupées.");
                    player.sendMessage(ChatColor.GRAY + "Les particules BLEUES indiquent les terrains adjacents sélectionnables.");
                }
            }
            return;
        }

        // Ajouter à la sélection
        session.selectedChunkKeys.add(chunkKey);
        player.sendMessage(ChatColor.GREEN + "✓ Parcelle ajoutée (" + session.selectedChunkKeys.size() + " sélectionnées)");

        if (session.selectedChunkKeys.size() >= 2) {
            player.sendMessage(ChatColor.GRAY + "Surface totale: " + ChatColor.WHITE + (session.selectedChunkKeys.size() * 256) + "m²");
        }

        // Ré-afficher les boutons pour un accès facile
        player.sendMessage("");
        sendActionButtons(player);
    }

    /**
     * Finaliser le groupement
     */
    public boolean finishGrouping(Player player, String groupName) {
        GroupingSession session = activeSessions.get(player.getUniqueId());

        if (session == null) {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas de session de groupement active!");
            return false;
        }

        if (session.selectedChunkKeys.size() < 2) {
            player.sendMessage(ChatColor.RED + "Vous devez sélectionner au moins 2 parcelles!");
            return false;
        }

        Town town = townManager.getTown(session.townName);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Ville introuvable!");
            cancelSession(player);
            return false;
        }

        // Générer automatiquement un nom si "auto" est passé
        if (groupName == null || groupName.equalsIgnoreCase("auto")) {
            groupName = generateGroupName(town);
        }

        // Vérifier l'adjacence
        if (!areAllPlotsAdjacent(session.selectedChunkKeys)) {
            player.sendMessage(ChatColor.RED + "Les parcelles doivent être adjacentes (côte à côte)!");
            player.sendMessage(ChatColor.YELLOW + "Les parcelles doivent former un terrain continu.");
            return false;
        }

        // Trouver le premier plot pour le transformer en groupe
        String firstKey = session.selectedChunkKeys.iterator().next();
        String[] parts = firstKey.split(":");
        Plot mainPlot = town.getPlot(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));

        if (mainPlot == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Parcelle principale introuvable.");
            return false;
        }

        // Ajouter tous les chunks au plot principal
        List<String> allChunks = new ArrayList<>(session.selectedChunkKeys);

        // NOUVEAU : Supprimer les mailboxes de tous les terrains qui vont être groupés
        if (plugin.getMailboxManager() != null) {
            for (String chunkKey : allChunks) {
                String[] chunkParts = chunkKey.split(":");
                if (chunkParts.length == 3) {
                    Plot plotToGroup = town.getPlot(chunkParts[0], Integer.parseInt(chunkParts[1]), Integer.parseInt(chunkParts[2]));
                    if (plotToGroup != null) {
                        plugin.getMailboxManager().removeMailbox(plotToGroup);
                    }
                }
            }
        }

        // Ajouter les chunks au plot principal
        for (String chunkKey : allChunks) {
            if (!chunkKey.equals(firstKey)) {
                String[] chunkParts = chunkKey.split(":");
                if (chunkParts.length == 3) {
                    // Supprimer le plot individuel
                    town.removePlot(chunkParts[0], Integer.parseInt(chunkParts[1]), Integer.parseInt(chunkParts[2]));

                    // Ajouter le chunk au plot principal
                    mainPlot.addChunk(chunkKey);
                }
            }
        }

        // Définir le nom du groupe
        mainPlot.setGroupName(groupName);

        // Sauvegarder
        townManager.saveTownsNow();
        claimManager.rebuildCache();

        // Message de succès
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "✓ GROUPE CRÉÉ AVEC SUCCÈS");
        player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage(ChatColor.YELLOW + "Nom: " + ChatColor.WHITE + groupName);
        player.sendMessage(ChatColor.YELLOW + "Parcelles: " + ChatColor.WHITE + session.selectedChunkKeys.size());
        player.sendMessage(ChatColor.YELLOW + "Surface: " + ChatColor.WHITE + (session.selectedChunkKeys.size() * 256) + "m²");
        player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("");

        // Nettoyer la session
        cancelSession(player);

        return true;
    }

    /**
     * Vérifie que tous les plots sont adjacents
     */
    private boolean areAllPlotsAdjacent(Set<String> chunkKeys) {
        if (chunkKeys.size() < 2) return true;

        // Convertir en coordonnées
        Set<ChunkCoord> coords = new HashSet<>();
        for (String key : chunkKeys) {
            String[] parts = key.split(":");
            if (parts.length == 3) {
                coords.add(new ChunkCoord(Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
            }
        }

        // BFS pour vérifier la connectivité
        Set<ChunkCoord> visited = new HashSet<>();
        Queue<ChunkCoord> queue = new LinkedList<>();

        ChunkCoord start = coords.iterator().next();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            ChunkCoord current = queue.poll();

            // Vérifier les 4 voisins
            ChunkCoord[] neighbors = {
                new ChunkCoord(current.x + 1, current.z),
                new ChunkCoord(current.x - 1, current.z),
                new ChunkCoord(current.x, current.z + 1),
                new ChunkCoord(current.x, current.z - 1)
            };

            for (ChunkCoord neighbor : neighbors) {
                if (coords.contains(neighbor) && !visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        return visited.size() == coords.size();
    }

    /**
     * Classe helper pour coordonnées de chunk
     */
    private static class ChunkCoord {
        final int x, z;

        ChunkCoord(int x, int z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ChunkCoord)) return false;
            ChunkCoord that = (ChunkCoord) o;
            return x == that.x && z == that.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, z);
        }
    }

    /**
     * Annuler la session
     */
    public void cancelSession(Player player) {
        GroupingSession session = activeSessions.remove(player.getUniqueId());
        lastClickTime.remove(player.getUniqueId());

        if (session != null) {
            // Arrêter la tâche de particules
            if (session.particleTaskId != -1) {
                Bukkit.getScheduler().cancelTask(session.particleTaskId);
            }

            player.sendMessage(ChatColor.YELLOW + "Session de groupement annulée.");
        }
    }

    /**
     * Vérifier si session active
     */
    public boolean hasActiveSession(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    /**
     * Obtenir le nombre de parcelles sélectionnées
     */
    public int getSelectionCount(Player player) {
        GroupingSession session = activeSessions.get(player.getUniqueId());
        return session != null ? session.selectedChunkKeys.size() : 0;
    }

    /**
     * Vérifier si une sélection existe
     */
    public boolean hasSelection(Player player) {
        return hasActiveSession(player) && getSelectionCount(player) > 0;
    }

    /**
     * Nettoyer un joueur déconnecté
     */
    public void cleanupPlayer(UUID playerUuid) {
        GroupingSession session = activeSessions.remove(playerUuid);
        lastClickTime.remove(playerUuid);

        if (session != null && session.particleTaskId != -1) {
            Bukkit.getScheduler().cancelTask(session.particleTaskId);
        }
    }

    /**
     * Génère automatiquement un nom de groupe unique
     */
    private String generateGroupName(Town town) {
        String baseName = "Groupe";
        int counter = 1;

        // Chercher tous les noms de groupes existants
        Set<String> existingNames = new HashSet<>();
        for (Plot plot : town.getPlots().values()) {
            if (plot.isGrouped() && plot.getGroupName() != null) {
                existingNames.add(plot.getGroupName().toLowerCase());
            }
        }

        // Trouver un nom disponible
        String generatedName;
        do {
            generatedName = baseName + "-" + counter;
            counter++;
        } while (existingNames.contains(generatedName.toLowerCase()));

        return generatedName;
    }
}
