package com.gravityyfh.roleplaycity.town.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.PlotGroup;
import com.gravityyfh.roleplaycity.town.data.PlotType;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownMember;
import com.gravityyfh.roleplaycity.town.manager.ClaimManager;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;

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

        // Vérifier que le joueur a les droits (Maire ou Adjoint)
        TownMember member = town.getMember(player.getUniqueId());
        if (member == null || (!town.isMayor(player.getUniqueId()) && !member.hasRole(com.gravityyfh.roleplaycity.town.data.TownRole.ADJOINT))) {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas les droits pour grouper des parcelles.");
            return;
        }

        GroupingSession session = new GroupingSession(townName);
        activeSessions.put(player.getUniqueId(), session);

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "MODE GROUPEMENT DE PARCELLES");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        player.sendMessage(ChatColor.YELLOW + "Instructions:");
        player.sendMessage(ChatColor.WHITE + "→ Faites un " + ChatColor.AQUA + "clic droit" + ChatColor.WHITE + " sur les parcelles à grouper");
        player.sendMessage(ChatColor.WHITE + "→ Seules les parcelles " + ChatColor.GOLD + "privées" + ChatColor.WHITE + " peuvent être groupées");
        player.sendMessage(ChatColor.WHITE + "→ Minimum 2 parcelles requises");
        player.sendMessage(ChatColor.GRAY + "Session active pendant 5 minutes");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
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
        GroupingSession session = activeSessions.get(player.getUniqueId());

        if (session == null) return;

        // Vérifier expiration
        if (session.isExpired()) {
            activeSessions.remove(player.getUniqueId());
            player.sendMessage(ChatColor.RED + "Session de groupement expirée.");
            return;
        }

        // Vérifier que c'est un clic droit
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }

        // Annuler l'événement pour éviter d'ouvrir des GUIs ou placer des blocs
        event.setCancelled(true);

        Chunk chunk = player.getLocation().getChunk();
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

        // RESTRICTION: Seulement les parcelles PARTICULIER (privées)
        if (plot.getType() != PlotType.PARTICULIER) {
            player.sendMessage(ChatColor.RED + "Seules les parcelles privées peuvent être groupées.");
            player.sendMessage(ChatColor.GRAY + "Type actuel: " + plot.getType().getDisplayName());
            return;
        }

        // Vérifier que toutes les parcelles appartiennent au même propriétaire
        if (plot.getOwnerUuid() == null) {
            player.sendMessage(ChatColor.RED + "Cette parcelle n'a pas de propriétaire.");
            return;
        }

        // Si c'est la première parcelle, enregistrer le propriétaire
        String plotKey = chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();

        if (session.selectedPlotKeys.isEmpty()) {
            session.selectedPlotKeys.add(plotKey);
            player.sendMessage(ChatColor.GREEN + "✓ Parcelle ajoutée au groupe (" + session.selectedPlotKeys.size() + ")");
            player.sendMessage(ChatColor.GRAY + "Propriétaire: " + plot.getOwnerName());
            return;
        }

        // Vérifier que le propriétaire est le même
        String firstPlotKey = session.selectedPlotKeys.iterator().next();
        String[] parts = firstPlotKey.split(":");
        Plot firstPlot = town.getPlot(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));

        if (firstPlot == null || !firstPlot.getOwnerUuid().equals(plot.getOwnerUuid())) {
            player.sendMessage(ChatColor.RED + "Toutes les parcelles doivent appartenir au même propriétaire.");
            return;
        }

        // Vérifier si déjà sélectionnée
        if (session.selectedPlotKeys.contains(plotKey)) {
            session.selectedPlotKeys.remove(plotKey);
            player.sendMessage(ChatColor.YELLOW + "✗ Parcelle retirée du groupe (" + session.selectedPlotKeys.size() + ")");
        } else {
            session.selectedPlotKeys.add(plotKey);
            player.sendMessage(ChatColor.GREEN + "✓ Parcelle ajoutée au groupe (" + session.selectedPlotKeys.size() + ")");
        }

        // Afficher le statut
        if (session.selectedPlotKeys.size() >= 2) {
            player.sendMessage(ChatColor.GRAY + "Surface totale: " + ChatColor.WHITE + (session.selectedPlotKeys.size() * 256) + "m²");
        } else {
            player.sendMessage(ChatColor.GRAY + "Minimum 2 parcelles requises");
        }
    }

    /**
     * Termine la session de groupement
     */
    public boolean finishGrouping(Player player) {
        GroupingSession session = activeSessions.remove(player.getUniqueId());
        if (session == null) {
            player.sendMessage(ChatColor.RED + "Aucune session de groupement active.");
            return false;
        }

        if (session.selectedPlotKeys.size() < 2) {
            player.sendMessage(ChatColor.RED + "Vous devez sélectionner au moins 2 parcelles.");
            return false;
        }

        Town town = townManager.getTown(session.townName);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Ville introuvable.");
            return false;
        }

        // Créer le groupe
        PlotGroup group = new PlotGroup(UUID.randomUUID().toString(), "Groupe-" + System.currentTimeMillis());
        for (String plotKey : session.selectedPlotKeys) {
            String[] parts = plotKey.split(":");
            if (parts.length == 3) {
                Plot plot = town.getPlot(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                if (plot != null) {
                    group.addPlot(plot);
                }
            }
        }

        // Définir le propriétaire du groupe
        String firstPlotKey = session.selectedPlotKeys.iterator().next();
        String[] parts = firstPlotKey.split(":");
        Plot firstPlot = town.getPlot(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        if (firstPlot != null) {
            group.setOwner(firstPlot.getOwnerUuid(), firstPlot.getOwnerName());
        }

        town.addPlotGroup(group);

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "✓ GROUPE CRÉÉ AVEC SUCCÈS");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        player.sendMessage(ChatColor.YELLOW + "Parcelles groupées: " + ChatColor.WHITE + session.selectedPlotKeys.size());
        player.sendMessage(ChatColor.YELLOW + "Surface totale: " + ChatColor.WHITE + (session.selectedPlotKeys.size() * 256) + "m²");
        player.sendMessage(ChatColor.YELLOW + "ID du groupe: " + ChatColor.GRAY + group.getGroupId());
        player.sendMessage(ChatColor.GRAY + "Utilisez /ville pour gérer ce groupe");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        player.sendMessage("");

        return true;
    }

    /**
     * Annule la session de groupement
     */
    public void cancelSession(Player player) {
        GroupingSession session = activeSessions.remove(player.getUniqueId());
        if (session != null) {
            player.sendMessage(ChatColor.YELLOW + "Session de groupement annulée.");
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
}
