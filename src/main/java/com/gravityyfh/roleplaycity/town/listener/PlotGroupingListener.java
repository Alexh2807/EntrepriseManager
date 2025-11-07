package com.gravityyfh.roleplaycity.town.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.PlotFlag;
import com.gravityyfh.roleplaycity.town.data.PlotGroup;
import com.gravityyfh.roleplaycity.town.data.PlotPermission;
import com.gravityyfh.roleplaycity.town.data.PlotType;
import com.gravityyfh.roleplaycity.town.data.TerritoryEntity;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownMember;
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
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Particle;

import java.util.*;

/**
 * Listener pour gérer le système interactif de groupement de parcelles
 */
public class PlotGroupingListener implements Listener {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final ClaimManager claimManager;

    // Sessions actives de groupement
    private final Map<UUID, GroupingSession> activeSessions = new HashMap<>();

    // Anti-spam pour éviter les doubles clics
    private final Map<UUID, Long> lastClickTime = new HashMap<>();
    private static final long CLICK_COOLDOWN = 500; // 500ms entre chaque clic

    // Schedulers pour les indicateurs visuels persistants
    private final Map<UUID, Integer> visualTaskIds = new HashMap<>();

    // Couleurs RGB pour les particules
    private static final Particle.DustOptions COLOR_GREEN = new Particle.DustOptions(Color.fromRGB(0, 255, 0), 1.5f);
    private static final Particle.DustOptions COLOR_BLUE = new Particle.DustOptions(Color.fromRGB(0, 200, 255), 1.5f);
    private static final Particle.DustOptions COLOR_RED = new Particle.DustOptions(Color.fromRGB(255, 0, 0), 1.5f);

    /**
     * Classe pour représenter une session de groupement
     */
    private static class GroupingSession {
        final String townName;
        final Set<String> selectedPlotKeys; // "world:chunkX:chunkZ"
        final long startTime;

        GroupingSession(String townName) {
            this.townName = townName;
            this.selectedPlotKeys = new HashSet<>();
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
     * Démarre une session de groupement pour un joueur
     */
    public void startGroupingSession(Player player, String townName) {
        Town town = townManager.getTown(townName);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Ville introuvable.");
            return;
        }

        // Vérifier que le joueur est membre de la ville
        TownMember member = town.getMember(player.getUniqueId());
        if (member == null) {
            player.sendMessage(ChatColor.RED + "Vous devez être membre de la ville pour grouper des parcelles.");
            return;
        }

        // NOUVELLE RESTRICTION : Seuls le Maire et l'Adjoint peuvent regrouper des parcelles
        boolean isMayorOrAdjoint = town.isMayor(player.getUniqueId()) ||
                                   (member != null && member.hasRole(com.gravityyfh.roleplaycity.town.data.TownRole.ADJOINT));

        if (!isMayorOrAdjoint) {
            player.sendMessage("");
            player.sendMessage(ChatColor.RED + "═══════════════════════════════════════");
            player.sendMessage(ChatColor.RED + "⚠ PERMISSION INSUFFISANTE ⚠");
            player.sendMessage(ChatColor.RED + "═══════════════════════════════════════");
            player.sendMessage(ChatColor.YELLOW + "Seuls le Maire et l'Adjoint peuvent");
            player.sendMessage(ChatColor.YELLOW + "regrouper des parcelles !");
            player.sendMessage("");
            player.sendMessage(ChatColor.GRAY + "Les terrains appartiennent à la ville,");
            player.sendMessage(ChatColor.GRAY + "le regroupement est une décision municipale.");
            player.sendMessage(ChatColor.RED + "═══════════════════════════════════════");
            return;
        }

        GroupingSession session = new GroupingSession(townName);
        activeSessions.put(player.getUniqueId(), session);

        // Démarrer l'affichage persistant des particules
        startVisualTask(player, session);

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "╔══════════════════════════════════════╗");
        player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "MODE GROUPEMENT DE PARCELLES");
        player.sendMessage(ChatColor.GOLD + "╚══════════════════════════════════════╝");
        player.sendMessage(ChatColor.YELLOW + "Instructions:");
        player.sendMessage(ChatColor.WHITE + "→ Faites un " + ChatColor.AQUA + "clic droit" + ChatColor.WHITE + " sur les parcelles à grouper");
        player.sendMessage(ChatColor.WHITE + "→ Seules les parcelles " + ChatColor.GOLD + "privées" + ChatColor.WHITE + " peuvent être groupées");
        player.sendMessage(ChatColor.WHITE + "→ Minimum 2 parcelles requises");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Indicateurs visuels (particules):");
        player.sendMessage(ChatColor.GREEN + "  ● Vert" + ChatColor.GRAY + " = Parcelle sélectionnée");
        player.sendMessage(ChatColor.AQUA + "  ● Bleu" + ChatColor.GRAY + " = Parcelle adjacente disponible");
        player.sendMessage(ChatColor.RED + "  ● Rouge" + ChatColor.GRAY + " = Parcelle non-adjacente (impossible)");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Session active pendant 5 minutes");
        player.sendMessage(ChatColor.GOLD + "╚══════════════════════════════════════╝");
        player.sendMessage("");

        // Envoyer le bouton cliquable pour terminer
        sendFinishButton(player);
    }

    /**
     * Envoie le bouton cliquable pour terminer le groupement
     */
    private void sendFinishButton(Player player) {
        GroupingSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        TextComponent message = new TextComponent("");

        // Partie fixe
        TextComponent prefix = new TextComponent("  ");
        message.addExtra(prefix);

        // Bouton cliquable
        TextComponent button = new TextComponent("[✓ TERMINER LE GROUPEMENT]");
        button.setColor(net.md_5.bungee.api.ChatColor.GREEN);
        button.setBold(true);
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ville:finishgrouping"));
        button.setHoverEvent(new HoverEvent(
            HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder("Cliquez pour terminer le groupement")
                .color(net.md_5.bungee.api.ChatColor.YELLOW)
                .create()
        ));
        message.addExtra(button);

        // Bouton annuler
        TextComponent cancel = new TextComponent(" [✗ ANNULER]");
        cancel.setColor(net.md_5.bungee.api.ChatColor.RED);
        cancel.setBold(true);
        cancel.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ville:cancelgrouping"));
        cancel.setHoverEvent(new HoverEvent(
            HoverEvent.Action.SHOW_TEXT,
            new ComponentBuilder("Cliquez pour annuler")
                .color(net.md_5.bungee.api.ChatColor.RED)
                .create()
        ));
        message.addExtra(cancel);

        player.spigot().sendMessage(message);
        player.sendMessage("");
    }

    /**
     * Écoute les clics droits pour ajouter des parcelles
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        GroupingSession session = activeSessions.get(playerUuid);

        if (session == null) return;

        // Vérifier expiration
        if (session.isExpired()) {
            cleanupSession(playerUuid);
            activeSessions.remove(playerUuid);
            lastClickTime.remove(playerUuid);
            player.sendMessage(ChatColor.RED + "Session de groupement expirée.");
            return;
        }

        // Vérifier que c'est un clic droit sur un bloc (évite la duplication avec RIGHT_CLICK_AIR)
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // PROTECTION ANTI-DOUBLE-CLIC
        long currentTime = System.currentTimeMillis();
        Long lastClick = lastClickTime.get(playerUuid);
        if (lastClick != null && (currentTime - lastClick) < CLICK_COOLDOWN) {
            event.setCancelled(true);
            return;
        }
        lastClickTime.put(playerUuid, currentTime);

        // Annuler l'événement pour éviter d'ouvrir des GUIs ou placer des blocs
        event.setCancelled(true);

        // Utiliser le chunk du bloc cliqué, pas celui du joueur
        if (event.getClickedBlock() == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Bloc non détecté. Réessayez.");
            return;
        }

        Chunk chunk = event.getClickedBlock().getChunk();
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

        Plot plot = town.getPlot(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        if (plot == null) {
            player.sendMessage(ChatColor.RED + "Parcelle introuvable.");
            return;
        }

        // RESTRICTION: Seulement les parcelles PARTICULIER ou PROFESSIONNEL
        if (plot.getType() != PlotType.PARTICULIER && plot.getType() != PlotType.PROFESSIONNEL) {
            player.sendMessage(ChatColor.RED + "Seules les parcelles PARTICULIER et PROFESSIONNEL peuvent être groupées.");
            player.sendMessage(ChatColor.GRAY + "Type actuel: " + plot.getType().getDisplayName());
            return;
        }

        // === Validation simplifiée (seuls Maire/Adjoint peuvent regrouper) ===
        // Puisque seuls le Maire et l'Adjoint peuvent démarrer une session,
        // on n'a plus besoin de vérifications complexes ici.

        // Vérifier la cohérence : tous les terrains doivent avoir le même statut et propriétaire
        if (!session.selectedPlotKeys.isEmpty()) {
            String firstPlotKey = session.selectedPlotKeys.iterator().next();
            String[] parts = firstPlotKey.split(":");
            Plot firstPlot = town.getPlot(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));

            if (firstPlot != null) {
                boolean firstHasOwner = (firstPlot.getOwnerUuid() != null);
                boolean currentHasOwner = (plot.getOwnerUuid() != null);

                // Vérifier cohérence : tous avec proprio OU tous sans proprio
                if (firstHasOwner != currentHasOwner) {
                    player.sendMessage(ChatColor.RED + "Impossible de mélanger des parcelles avec et sans propriétaire !");
                    if (firstHasOwner) {
                        player.sendMessage(ChatColor.YELLOW + "Les parcelles déjà sélectionnées ont un propriétaire.");
                    } else {
                        player.sendMessage(ChatColor.YELLOW + "Les parcelles déjà sélectionnées sont municipales (sans propriétaire).");
                    }
                    return;
                }

                // Si toutes ont un propriétaire, vérifier que c'est le même
                if (firstHasOwner && currentHasOwner && !firstPlot.getOwnerUuid().equals(plot.getOwnerUuid())) {
                    player.sendMessage(ChatColor.RED + "Toutes les parcelles avec propriétaire doivent avoir le même propriétaire !");

                    // Afficher entreprise ou joueur selon le type
                    String firstOwnerDisplay = firstPlot.getOwnerName();
                    if (firstPlot.getType() == PlotType.PROFESSIONNEL && firstPlot.getCompanySiret() != null) {
                        com.gravityyfh.roleplaycity.EntrepriseManagerLogic.Entreprise firstCompany = plugin.getCompanyPlotManager()
                            .getCompanyBySiret(firstPlot.getCompanySiret());
                        if (firstCompany != null) {
                            firstOwnerDisplay = firstCompany.getNom();
                        }
                    }

                    String currentOwnerDisplay = plot.getOwnerName();
                    if (plot.getType() == PlotType.PROFESSIONNEL && plot.getCompanySiret() != null) {
                        com.gravityyfh.roleplaycity.EntrepriseManagerLogic.Entreprise currentCompany = plugin.getCompanyPlotManager()
                            .getCompanyBySiret(plot.getCompanySiret());
                        if (currentCompany != null) {
                            currentOwnerDisplay = currentCompany.getNom();
                        }
                    }

                    player.sendMessage(ChatColor.YELLOW + "Propriétaire déjà sélectionné: " + ChatColor.WHITE + firstOwnerDisplay);
                    player.sendMessage(ChatColor.YELLOW + "Propriétaire de cette parcelle: " + ChatColor.WHITE + currentOwnerDisplay);
                    return;
                }
            }
        }

        // Si c'est la première parcelle, l'ajouter
        String plotKey = chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();


        if (session.selectedPlotKeys.isEmpty()) {
            session.selectedPlotKeys.add(plotKey);
            player.sendMessage(ChatColor.GREEN + "✓ Parcelle ajoutée au groupe (" + session.selectedPlotKeys.size() + ")");

            // Afficher propriétaire ou entreprise
            if (plot.getType() == PlotType.PROFESSIONNEL && plot.getCompanySiret() != null) {
                com.gravityyfh.roleplaycity.EntrepriseManagerLogic.Entreprise ownerCompany = plugin.getCompanyPlotManager()
                    .getCompanyBySiret(plot.getCompanySiret());
                if (ownerCompany != null) {
                    player.sendMessage(ChatColor.GRAY + "Entreprise: " + ownerCompany.getNom());
                } else {
                    player.sendMessage(ChatColor.GRAY + "Propriétaire: " + plot.getOwnerName());
                }
            } else {
                player.sendMessage(ChatColor.GRAY + "Propriétaire: " + plot.getOwnerName());
            }

            player.sendMessage(ChatColor.GRAY + "Sélectionnez la parcelle suivante (clic droit)");

            // Ne pas afficher le bouton ici, seulement après le premier ajout réel
            return;
        }

        // Vérifier si déjà sélectionnée

        if (session.selectedPlotKeys.contains(plotKey)) {
            session.selectedPlotKeys.remove(plotKey);
            player.sendMessage(ChatColor.YELLOW + "✗ Parcelle retirée du groupe (" + session.selectedPlotKeys.size() + ")");
        } else {

            // Vérifier l'adjacence si ce n'est pas la première parcelle
            if (!session.selectedPlotKeys.isEmpty()) {
                boolean isAdjacent = isAdjacentToAnySelected(chunk.getX(), chunk.getZ(), session.selectedPlotKeys, town);

                if (!isAdjacent) {
                    player.sendMessage(ChatColor.RED + "Cette parcelle doit être adjacente (côte à côte) aux parcelles déjà sélectionnées !");
                    player.sendMessage(ChatColor.YELLOW + "Les parcelles doivent former un terrain continu.");
                    return;
                }
            }

            boolean added = session.selectedPlotKeys.add(plotKey);
            player.sendMessage(ChatColor.GREEN + "✓ Parcelle ajoutée au groupe (" + session.selectedPlotKeys.size() + ")");
        }

        // Afficher le statut
        if (session.selectedPlotKeys.size() >= 2) {
            player.sendMessage(ChatColor.GRAY + "Surface totale: " + ChatColor.WHITE + (session.selectedPlotKeys.size() * 256) + "m²");
            player.sendMessage(ChatColor.GREEN + "Vous pouvez terminer le groupement ou continuer à sélectionner");
        } else {
            player.sendMessage(ChatColor.GRAY + "Minimum 2 parcelles requises");
        }

        // Réafficher les boutons après chaque action
        sendFinishButton(player);
    }

    /**
     * Termine la session de groupement
     */
    public boolean finishGrouping(Player player) {
        UUID playerUuid = player.getUniqueId();

        GroupingSession session = activeSessions.get(playerUuid);
        if (session == null) {
            player.sendMessage(ChatColor.RED + "Aucune session de groupement active.");
            return false;
        }


        if (session.selectedPlotKeys.size() < 2) {
            player.sendMessage(ChatColor.RED + "Vous devez sélectionner au moins 2 parcelles.");
            player.sendMessage(ChatColor.YELLOW + "[DEBUG] Parcelles dans la session: " + session.selectedPlotKeys.size());
            return false;
        }

        // Retirer la session seulement après vérification
        cleanupSession(playerUuid);
        activeSessions.remove(playerUuid);
        lastClickTime.remove(playerUuid);


        Town town = townManager.getTown(session.townName);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Ville introuvable.");
            return false;
        }

        // Créer le groupe avec le townName correct
        PlotGroup group = new PlotGroup(UUID.randomUUID().toString(), session.townName);

        // ⚠️ NOUVEAU SYSTÈME : Copier TOUTES les propriétés du premier plot vers le groupe autonome
        String firstPlotKey = session.selectedPlotKeys.iterator().next();
        String[] parts = firstPlotKey.split(":");
        Plot firstPlot = town.getPlot(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));

        if (firstPlot != null) {
            // Copier propriétaire
            group.setOwner(firstPlot.getOwnerUuid(), firstPlot.getOwnerName());

            // Copier type (PARTICULIER ou PROFESSIONNEL)
            if (firstPlot.getType() != null) {
                group.setType(firstPlot.getType());
            }

            // Copier informations entreprise si applicable
            if (firstPlot.getCompanyName() != null) {
                group.setCompanyName(firstPlot.getCompanyName());
            }
            if (firstPlot.getCompanySiret() != null) {
                group.setCompanySiret(firstPlot.getCompanySiret());
            }

            // Copier permissions individuelles
            Map<UUID, Set<PlotPermission>> plotPermissions = firstPlot.getAllPlayerPermissions();
            if (plotPermissions != null && !plotPermissions.isEmpty()) {
                for (Map.Entry<UUID, Set<PlotPermission>> entry : plotPermissions.entrySet()) {
                    group.setPlayerPermissions(entry.getKey(), entry.getValue());
                }
            }

            // Copier trusted players
            Set<UUID> trustedPlayers = firstPlot.getTrustedPlayers();
            if (trustedPlayers != null && !trustedPlayers.isEmpty()) {
                for (UUID trusted : trustedPlayers) {
                    group.addTrustedPlayer(trusted);
                }
            }

            // Copier flags
            Map<PlotFlag, Boolean> plotFlags = firstPlot.getAllFlags();
            if (plotFlags != null && !plotFlags.isEmpty()) {
                for (Map.Entry<PlotFlag, Boolean> flagEntry : plotFlags.entrySet()) {
                    group.setFlag(flagEntry.getKey(), flagEntry.getValue());
                }
            }
        } else {
            // Si pas de firstPlot, le groupe appartient à la ville
            group.setOwner(null, town.getName() + " (Municipal)");
        }

        // FIX: Vérifier qu'aucune parcelle n'a de dette avant de grouper
        for (String plotKey : session.selectedPlotKeys) {
            String[] keyParts = plotKey.split(":");
            if (keyParts.length == 3) {
                Plot plot = town.getPlot(keyParts[0], Integer.parseInt(keyParts[1]), Integer.parseInt(keyParts[2]));
                if (plot != null) {
                    // Vérifier les dettes
                    if (plot.getParticularDebtAmount() > 0 || plot.getCompanyDebtAmount() > 0) {
                        player.sendMessage("");
                        player.sendMessage(ChatColor.RED + "❌ GROUPEMENT IMPOSSIBLE");
                        player.sendMessage(ChatColor.YELLOW + "Au moins une parcelle a une dette impayée !");
                        player.sendMessage(ChatColor.YELLOW + "Terrain: " + ChatColor.WHITE + plot.getCoordinates());
                        player.sendMessage(ChatColor.YELLOW + "Dette: " + ChatColor.RED +
                            String.format("%.2f€", plot.getParticularDebtAmount() + plot.getCompanyDebtAmount()));
                        player.sendMessage("");
                        player.sendMessage(ChatColor.GRAY + "💡 Réglez toutes les dettes avant de grouper:");
                        player.sendMessage(ChatColor.GRAY + "   /ville → Régler vos Dettes");
                        player.sendMessage("");
                        return false;
                    }
                }
            }
        }

        // ⚠️ NOUVEAU SYSTÈME : Ajouter les chunks au groupe et SUPPRIMER les plots individuels
        for (String plotKey : session.selectedPlotKeys) {
            String[] keyParts = plotKey.split(":");
            if (keyParts.length == 3) {
                Plot plot = town.getPlot(keyParts[0], Integer.parseInt(keyParts[1]), Integer.parseInt(keyParts[2]));
                if (plot != null) {
                    // Ajouter le chunk au groupe autonome
                    group.addChunk(plotKey);

                    // CRITIQUE : Supprimer le plot individuel de town.plots
                    // Dans le système autonome, un chunk est SOIT un Plot SOIT dans un PlotGroup
                    town.removePlot(plot.getWorldName(), plot.getChunkX(), plot.getChunkZ());
                }
            }
        }

        town.addPlotGroup(group);

        plugin.getLogger().info(String.format(
            "[PlotGrouping] Groupe créé: %s (%d chunks, %d plots supprimés)",
            group.getGroupId(),
            group.getChunkKeys().size(),
            session.selectedPlotKeys.size()
        ));

        // Sauvegarder immédiatement le nouveau groupe
        townManager.saveTownsNow();

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "╔══════════════════════════════════════╗");
        player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "✓ GROUPE CRÉÉ AVEC SUCCÈS");
        player.sendMessage(ChatColor.GOLD + "╚══════════════════════════════════════╝");
        player.sendMessage(ChatColor.YELLOW + "Parcelles groupées: " + ChatColor.WHITE + session.selectedPlotKeys.size());
        player.sendMessage(ChatColor.YELLOW + "Surface totale: " + ChatColor.WHITE + (session.selectedPlotKeys.size() * 256) + "m²");
        player.sendMessage(ChatColor.YELLOW + "ID du groupe: " + ChatColor.GRAY + group.getGroupId());
        player.sendMessage(ChatColor.GRAY + "Utilisez /ville pour gérer ce groupe");
        player.sendMessage(ChatColor.GOLD + "╚══════════════════════════════════════╝");
        player.sendMessage("");

        return true;
    }

    /**
     * Annule la session de groupement
     */
    public void cancelSession(Player player) {
        UUID playerUuid = player.getUniqueId();
        cleanupSession(playerUuid);
        GroupingSession session = activeSessions.remove(playerUuid);
        lastClickTime.remove(playerUuid);


        if (session != null) {
            player.sendMessage(ChatColor.YELLOW + "Session de groupement annulée.");
        } else {
        }
    }

    /**
     * Vérifie si un joueur a une session active
     */
    public boolean hasActiveSession(UUID playerUuid) {
        return activeSessions.containsKey(playerUuid);
    }

    /**
     * Nettoie les sessions expirées
     */
    public void cleanExpiredSessions() {
        activeSessions.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * Vérifie si une parcelle est adjacente à au moins une parcelle déjà sélectionnée
     */
    private boolean isAdjacentToAnySelected(int chunkX, int chunkZ, Set<String> selectedPlotKeys, Town town) {
        for (String plotKey : selectedPlotKeys) {
            String[] parts = plotKey.split(":");
            if (parts.length == 3) {
                int selectedX = Integer.parseInt(parts[1]);
                int selectedZ = Integer.parseInt(parts[2]);

                // Vérifier les 4 directions (Nord, Sud, Est, Ouest)
                if ((Math.abs(chunkX - selectedX) == 1 && chunkZ == selectedZ) ||
                    (Math.abs(chunkZ - selectedZ) == 1 && chunkX == selectedX)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Démarre une tâche répétitive pour afficher les particules
     */
    private void startVisualTask(Player player, GroupingSession session) {
        UUID playerUuid = player.getUniqueId();

        // Créer une nouvelle tâche qui s'exécute toutes les 10 ticks (0.5 seconde)
        int taskId = new BukkitRunnable() {
            @Override
            public void run() {
                // Vérifier si la session est toujours active
                GroupingSession currentSession = activeSessions.get(playerUuid);
                if (currentSession == null || !player.isOnline()) {
                    this.cancel();
                    cleanupSession(playerUuid);
                    return;
                }

                // Vérifier expiration
                if (currentSession.isExpired()) {
                    this.cancel();
                    cleanupSession(playerUuid);
                    activeSessions.remove(playerUuid);
                    lastClickTime.remove(playerUuid);
                    if (player.isOnline()) {
                        player.sendMessage(ChatColor.RED + "Session de groupement expirée.");
                    }
                    return;
                }

                // Afficher les particules
                displayVisualIndicators(player, currentSession);
            }
        }.runTaskTimer(plugin, 0L, 10L).getTaskId(); // 0 tick delay, répéter toutes les 10 ticks (0.5 sec)

        visualTaskIds.put(playerUuid, taskId);
    }

    /**
     * Nettoie toutes les ressources d'une session
     */
    private void cleanupSession(UUID playerUuid) {
        // Arrêter la tâche de particules
        Integer taskId = visualTaskIds.remove(playerUuid);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    /**
     * Affiche les indicateurs visuels (particules redstone) pour toutes les parcelles pertinentes
     */
    private void displayVisualIndicators(Player player, GroupingSession session) {
        Town town = townManager.getTown(session.townName);
        if (town == null) return;

        // Afficher particules pour les parcelles sélectionnées (GREEN)
        for (String plotKey : session.selectedPlotKeys) {
            String[] parts = plotKey.split(":");
            if (parts.length == 3) {
                String worldName = parts[0];
                int chunkX = Integer.parseInt(parts[1]);
                int chunkZ = Integer.parseInt(parts[2]);
                spawnParticles(worldName, chunkX, chunkZ, COLOR_GREEN, session);
            }
        }

        // Afficher particules pour les parcelles adjacentes disponibles (BLUE) et impossibles (RED)
        for (Plot plot : town.getPlots().values()) {
            String plotKey = plot.getWorldName() + ":" + plot.getChunkX() + ":" + plot.getChunkZ();

            // Ignorer les parcelles déjà sélectionnées
            if (session.selectedPlotKeys.contains(plotKey)) {
                continue;
            }

            // Vérifier si c'est une parcelle privée (PARTICULIER ou PROFESSIONNEL)
            if (plot.getType() != PlotType.PARTICULIER && plot.getType() != PlotType.PROFESSIONNEL) {
                continue;
            }

            // ⚠️ NOUVEAU SYSTÈME : Vérifier si déjà dans un groupe
            TerritoryEntity territory = town.getTerritoryAt(plot.getWorldName(), plot.getChunkX(), plot.getChunkZ());
            if (territory instanceof PlotGroup) {
                continue;
            }

            // Si c'est la première sélection, toutes les parcelles sont adjacentes (BLUE)
            if (session.selectedPlotKeys.isEmpty()) {
                spawnParticles(plot.getWorldName(), plot.getChunkX(), plot.getChunkZ(), COLOR_BLUE, session);
            } else {
                // Vérifier si adjacent aux parcelles sélectionnées
                boolean isAdjacent = isAdjacentToAnySelected(plot.getChunkX(), plot.getChunkZ(),
                    session.selectedPlotKeys, town);

                if (isAdjacent) {
                    // Parcelle adjacente = BLUE
                    spawnParticles(plot.getWorldName(), plot.getChunkX(), plot.getChunkZ(), COLOR_BLUE, session);
                } else {
                    // Parcelle non-adjacente = RED
                    spawnParticles(plot.getWorldName(), plot.getChunkX(), plot.getChunkZ(), COLOR_RED, session);
                }
            }
        }
    }

    /**
     * Spawn des particules redstone sur tout le contour d'un chunk
     * Optimisé avec "connected borders" : ne dessine pas les bordures internes entre chunks du même groupe
     * @param worldName Nom du monde
     * @param chunkX Coordonnée X du chunk
     * @param chunkZ Coordonnée Z du chunk
     * @param dustOptions Options de couleur pour les particules redstone
     * @param session Session de groupement pour gérer les connected borders
     */
    private void spawnParticles(String worldName, int chunkX, int chunkZ, Particle.DustOptions dustOptions, GroupingSession session) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        int startX = chunkX * 16;
        int startZ = chunkZ * 16;

        // Vérifier quels côtés doivent être affichés (connected borders)
        boolean showNorth = !isChunkInSameGroup(worldName, chunkX, chunkZ - 1, session);
        boolean showEast = !isChunkInSameGroup(worldName, chunkX + 1, chunkZ, session);
        boolean showSouth = !isChunkInSameGroup(worldName, chunkX, chunkZ + 1, session);
        boolean showWest = !isChunkInSameGroup(worldName, chunkX - 1, chunkZ, session);

        // Côté Nord (Z fixe = startZ)
        if (showNorth) {
            for (int x = startX; x < startX + 16; x++) {
                spawnParticleColumn(world, x + 0.5, startZ + 0.5, dustOptions);
            }
        }

        // Côté Est (X fixe = startX + 16)
        if (showEast) {
            for (int z = startZ; z < startZ + 16; z++) {
                spawnParticleColumn(world, startX + 16.0, z + 0.5, dustOptions);
            }
        }

        // Côté Sud (Z fixe = startZ + 16)
        if (showSouth) {
            for (int x = startX; x < startX + 16; x++) {
                spawnParticleColumn(world, x + 0.5, startZ + 16.0, dustOptions);
            }
        }

        // Côté Ouest (X fixe = startX)
        if (showWest) {
            for (int z = startZ; z < startZ + 16; z++) {
                spawnParticleColumn(world, startX + 0.0, z + 0.5, dustOptions);
            }
        }
    }

    /**
     * Spawn une colonne de particules à une position donnée
     * Spawn 5 particules espacées verticalement pour une bonne visibilité
     */
    private void spawnParticleColumn(World world, double x, double z, Particle.DustOptions dustOptions) {
        int groundY = world.getHighestBlockYAt((int) x, (int) z);

        // Spawn 5 particules espacées sur 2 blocs de hauteur
        for (int i = 0; i < 5; i++) {
            double y = groundY + 0.2 + (i * 0.4);

            // Spawn 3 particules au même endroit pour plus de densité
            for (int j = 0; j < 3; j++) {
                world.spawnParticle(
                    Particle.REDSTONE,
                    x + (Math.random() * 0.2 - 0.1), // Légère variation X
                    y,
                    z + (Math.random() * 0.2 - 0.1), // Légère variation Z
                    1,
                    0, 0, 0, 0,
                    dustOptions
                );
            }
        }
    }

    /**
     * Vérifie si un chunk fait partie du même groupe (pour connected borders)
     */
    private boolean isChunkInSameGroup(String worldName, int chunkX, int chunkZ, GroupingSession session) {
        if (session == null) return false;
        String plotKey = worldName + ":" + chunkX + ":" + chunkZ;
        return session.selectedPlotKeys.contains(plotKey);
    }
}
