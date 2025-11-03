package com.gravityyfh.roleplaycity.town;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.ChunkCoordinate;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.gui.TownMainGUI;
import com.gravityyfh.roleplaycity.town.manager.ClaimManager;
import com.gravityyfh.roleplaycity.town.manager.TownEconomyManager;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TownCommandHandler implements CommandExecutor {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final TownMainGUI townGUI;

    public TownCommandHandler(RoleplayCity plugin, TownManager townManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.townGUI = new TownMainGUI(plugin, townManager);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Seuls les joueurs peuvent exécuter cette commande.");
            return true;
        }

        // Sans arguments : ouvrir le GUI principal
        if (args.length == 0) {
            townGUI.openMainMenu(player);
            return true;
        }

        // Sous-commandes (pour debug ou futures fonctionnalités)
        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "info" -> {
                String townName = townManager.getPlayerTown(player.getUniqueId());
                if (townName == null) {
                    player.sendMessage(ChatColor.RED + "Vous n'êtes dans aucune ville.");
                    return true;
                }
                player.sendMessage(ChatColor.GREEN + "Vous êtes dans la ville: " + ChatColor.GOLD + townName);
                return true;
            }
            case "list" -> {
                player.sendMessage(ChatColor.GOLD + "=== Villes du serveur ===");
                for (String town : townManager.getTownNames()) {
                    player.sendMessage(ChatColor.YELLOW + "- " + town);
                }
                return true;
            }
            case "gui" -> {
                townGUI.openMainMenu(player);
                return true;
            }
            case "accept" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /ville accept <nom_ville>");
                    return true;
                }
                String townName = args[1];
                if (townManager.acceptInvitation(player, townName)) {
                    player.sendMessage(ChatColor.GREEN + "Vous avez rejoint la ville " + ChatColor.GOLD + townName + ChatColor.GREEN + "!");
                } else {
                    player.sendMessage(ChatColor.RED + "Impossible d'accepter l'invitation.");
                    player.sendMessage(ChatColor.GRAY + "- Vérifiez que vous avez bien été invité");
                    player.sendMessage(ChatColor.GRAY + "- Vérifiez que vous n'êtes pas déjà dans une ville");
                }
                return true;
            }
            case "refuse" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /ville refuse <nom_ville>");
                    return true;
                }
                String townName = args[1];
                if (townManager.refuseInvitation(player, townName)) {
                    player.sendMessage(ChatColor.YELLOW + "Vous avez refusé l'invitation de " + ChatColor.GOLD + townName + ChatColor.YELLOW + ".");
                } else {
                    player.sendMessage(ChatColor.RED + "Impossible de refuser l'invitation.");
                }
                return true;
            }
            case "groupes", "groups" -> {
                String townName = townManager.getPlayerTown(player.getUniqueId());
                if (townName == null) {
                    player.sendMessage(ChatColor.RED + "Vous n'êtes dans aucune ville.");
                    return true;
                }
                plugin.getPlotGroupManagementGUI().openMainMenu(player, townName);
                return true;
            }
            case "finishgrouping" -> {
                // Commande interne pour terminer le groupement
                if (plugin.getPlotGroupingListener() != null) {
                    plugin.getPlotGroupingListener().finishGrouping(player);
                }
                return true;
            }
            case "cancelgrouping" -> {
                // Commande interne pour annuler le groupement
                if (plugin.getPlotGroupingListener() != null) {
                    plugin.getPlotGroupingListener().cancelSession(player);
                }
                return true;
            }
            case "buyplot" -> {
                // Commande interne pour acheter une parcelle
                if (args.length < 4) {
                    player.sendMessage(ChatColor.RED + "Erreur interne: paramètres manquants.");
                    return true;
                }
                handleBuyPlotCommand(player, args[1], args[2], args[3]);
                return true;
            }
            case "rentplot" -> {
                // Commande interne pour louer une parcelle
                if (args.length < 4) {
                    player.sendMessage(ChatColor.RED + "Erreur interne: paramètres manquants.");
                    return true;
                }
                handleRentPlotCommand(player, args[1], args[2], args[3]);
                return true;
            }
            case "confirmbuy" -> {
                // Confirmation d'achat
                if (args.length < 4) {
                    player.sendMessage(ChatColor.RED + "Erreur interne: paramètres manquants.");
                    return true;
                }
                handleConfirmBuy(player, args[1], args[2], args[3]);
                return true;
            }
            case "confirmrent" -> {
                // Confirmation de location
                if (args.length < 5) {
                    player.sendMessage(ChatColor.RED + "Erreur interne: paramètres manquants.");
                    return true;
                }
                handleConfirmRent(player, args[1], args[2], args[3], args[4]);
                return true;
            }
            default -> {
                player.sendMessage(ChatColor.RED + "Sous-commande inconnue.");
                townGUI.openMainMenu(player);
                return true;
            }
        }
    }

    /**
     * Gère l'achat d'une parcelle avec confirmation
     */
    private void handleBuyPlotCommand(Player player, String chunkXStr, String chunkZStr, String worldName) {
        try {
            int chunkX = Integer.parseInt(chunkXStr);
            int chunkZ = Integer.parseInt(chunkZStr);

            ClaimManager claimManager = plugin.getClaimManager();
            TownEconomyManager economyManager = plugin.getTownEconomyManager();

            ChunkCoordinate coord = new ChunkCoordinate(worldName, chunkX, chunkZ);
            String townName = claimManager.getClaimOwner(coord);
            if (townName == null) {
                player.sendMessage(ChatColor.RED + "Cette parcelle n'appartient à aucune ville.");
                return;
            }

            Town town = townManager.getTown(townName);
            if (town == null) {
                player.sendMessage(ChatColor.RED + "Ville introuvable.");
                return;
            }

            Plot plot = town.getPlot(worldName, chunkX, chunkZ);
            if (plot == null || !plot.isForSale()) {
                player.sendMessage(ChatColor.RED + "Cette parcelle n'est pas en vente.");
                return;
            }

            // Message de confirmation
            player.sendMessage("");
            player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
            player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "CONFIRMATION D'ACHAT");
            player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
            player.sendMessage(ChatColor.YELLOW + "Parcelle: " + ChatColor.WHITE + "256m² (" + (chunkX * 16) + ", " + (chunkZ * 16) + ")");
            player.sendMessage(ChatColor.YELLOW + "Prix: " + ChatColor.GOLD + String.format("%.2f€", plot.getSalePrice()));
            player.sendMessage(ChatColor.YELLOW + "Ville: " + ChatColor.WHITE + townName);
            player.sendMessage("");

            // Bouton CONFIRMER
            TextComponent confirmButton = new TextComponent("  [✓ CONFIRMER L'ACHAT]");
            confirmButton.setColor(net.md_5.bungee.api.ChatColor.GREEN);
            confirmButton.setBold(true);
            confirmButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/ville:confirmbuy " + chunkX + " " + chunkZ + " " + worldName));
            confirmButton.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("Cliquez pour confirmer l'achat")
                    .color(net.md_5.bungee.api.ChatColor.YELLOW)
                    .create()
            ));

            // Bouton ANNULER
            TextComponent cancelButton = new TextComponent(" [✗ ANNULER]");
            cancelButton.setColor(net.md_5.bungee.api.ChatColor.RED);
            cancelButton.setBold(true);
            cancelButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/ville"));
            cancelButton.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("Annuler l'achat")
                    .color(net.md_5.bungee.api.ChatColor.RED)
                    .create()
            ));

            TextComponent message = new TextComponent("");
            message.addExtra(confirmButton);
            message.addExtra(cancelButton);
            player.spigot().sendMessage(message);

            player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
            player.sendMessage("");

        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Erreur: coordonnées invalides.");
        }
    }

    /**
     * Gère la location d'une parcelle avec choix du nombre de jours
     */
    private void handleRentPlotCommand(Player player, String chunkXStr, String chunkZStr, String worldName) {
        try {
            int chunkX = Integer.parseInt(chunkXStr);
            int chunkZ = Integer.parseInt(chunkZStr);

            ClaimManager claimManager = plugin.getClaimManager();

            ChunkCoordinate coord = new ChunkCoordinate(worldName, chunkX, chunkZ);
            String townName = claimManager.getClaimOwner(coord);
            if (townName == null) {
                player.sendMessage(ChatColor.RED + "Cette parcelle n'appartient à aucune ville.");
                return;
            }

            Town town = townManager.getTown(townName);
            if (town == null) {
                player.sendMessage(ChatColor.RED + "Ville introuvable.");
                return;
            }

            Plot plot = town.getPlot(worldName, chunkX, chunkZ);
            if (plot == null || !plot.isForRent()) {
                player.sendMessage(ChatColor.RED + "Cette parcelle n'est pas en location.");
                return;
            }

            // Message de sélection du nombre de jours
            player.sendMessage("");
            player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
            player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "LOCATION DE PARCELLE");
            player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
            player.sendMessage(ChatColor.YELLOW + "Parcelle: " + ChatColor.WHITE + "256m² (" + (chunkX * 16) + ", " + (chunkZ * 16) + ")");
            player.sendMessage(ChatColor.YELLOW + "Prix: " + ChatColor.GOLD + String.format("%.2f€/jour", plot.getRentPricePerDay()));
            player.sendMessage(ChatColor.YELLOW + "Ville: " + ChatColor.WHITE + townName);
            player.sendMessage("");
            player.sendMessage(ChatColor.GRAY + "Choisissez la durée de location:");
            player.sendMessage("");

            // Boutons pour différentes durées
            TextComponent message = new TextComponent("  ");

            for (int days : new int[]{7, 15, 30}) {
                double totalPrice = plot.getRentPricePerDay() * days;
                TextComponent dayButton = new TextComponent("[" + days + " jours]");
                dayButton.setColor(net.md_5.bungee.api.ChatColor.AQUA);
                dayButton.setBold(true);
                dayButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                    "/ville:confirmrent " + chunkX + " " + chunkZ + " " + worldName + " " + days));
                dayButton.setHoverEvent(new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder("Louer pour " + days + " jours\nTotal: " + String.format("%.2f€", totalPrice))
                        .color(net.md_5.bungee.api.ChatColor.YELLOW)
                        .create()
                ));
                message.addExtra(dayButton);
                message.addExtra(new TextComponent(" "));
            }

            player.spigot().sendMessage(message);
            player.sendMessage("");
            player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
            player.sendMessage("");

        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Erreur: coordonnées invalides.");
        }
    }

    /**
     * Confirme et exécute l'achat
     */
    private void handleConfirmBuy(Player player, String chunkXStr, String chunkZStr, String worldName) {
        try {
            int chunkX = Integer.parseInt(chunkXStr);
            int chunkZ = Integer.parseInt(chunkZStr);

            ClaimManager claimManager = plugin.getClaimManager();
            TownEconomyManager economyManager = plugin.getTownEconomyManager();

            ChunkCoordinate coord = new ChunkCoordinate(worldName, chunkX, chunkZ);
            String townName = claimManager.getClaimOwner(coord);
            if (townName == null) {
                player.sendMessage(ChatColor.RED + "Cette parcelle n'appartient à aucune ville.");
                return;
            }

            Town town = townManager.getTown(townName);
            if (town == null) {
                player.sendMessage(ChatColor.RED + "Ville introuvable.");
                return;
            }

            Plot plot = town.getPlot(worldName, chunkX, chunkZ);
            if (plot == null || !plot.isForSale()) {
                player.sendMessage(ChatColor.RED + "Cette parcelle n'est plus en vente.");
                return;
            }

            // Exécuter l'achat
            if (economyManager.buyPlot(townName, plot, player)) {
                player.sendMessage("");
                player.sendMessage(ChatColor.GREEN + "═══════════════════════════════════════");
                player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "✓ ACHAT CONFIRMÉ");
                player.sendMessage(ChatColor.GREEN + "═══════════════════════════════════════");
                player.sendMessage(ChatColor.YELLOW + "Félicitations ! Vous êtes maintenant propriétaire de cette parcelle.");
                player.sendMessage(ChatColor.GRAY + "Surface: " + ChatColor.WHITE + "256m²");
                player.sendMessage(ChatColor.GRAY + "Position: " + ChatColor.WHITE + "X: " + (chunkX * 16) + " Z: " + (chunkZ * 16));
                player.sendMessage(ChatColor.GREEN + "═══════════════════════════════════════");
                player.sendMessage("");
            } else {
                player.sendMessage(ChatColor.RED + "✗ Achat impossible (fonds insuffisants ou erreur).");
            }

        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Erreur: coordonnées invalides.");
        }
    }

    /**
     * Confirme et exécute la location
     */
    private void handleConfirmRent(Player player, String chunkXStr, String chunkZStr, String worldName, String daysStr) {
        try {
            int chunkX = Integer.parseInt(chunkXStr);
            int chunkZ = Integer.parseInt(chunkZStr);
            int days = Integer.parseInt(daysStr);

            ClaimManager claimManager = plugin.getClaimManager();
            TownEconomyManager economyManager = plugin.getTownEconomyManager();

            ChunkCoordinate coord = new ChunkCoordinate(worldName, chunkX, chunkZ);
            String townName = claimManager.getClaimOwner(coord);
            if (townName == null) {
                player.sendMessage(ChatColor.RED + "Cette parcelle n'appartient à aucune ville.");
                return;
            }

            Town town = townManager.getTown(townName);
            if (town == null) {
                player.sendMessage(ChatColor.RED + "Ville introuvable.");
                return;
            }

            Plot plot = town.getPlot(worldName, chunkX, chunkZ);
            if (plot == null || !plot.isForRent()) {
                player.sendMessage(ChatColor.RED + "Cette parcelle n'est plus en location.");
                return;
            }

            // Exécuter la location
            if (economyManager.rentPlot(townName, plot, player, days)) {
                double totalPrice = plot.getRentPricePerDay() * days;
                player.sendMessage("");
                player.sendMessage(ChatColor.AQUA + "═══════════════════════════════════════");
                player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "✓ LOCATION CONFIRMÉE");
                player.sendMessage(ChatColor.AQUA + "═══════════════════════════════════════");
                player.sendMessage(ChatColor.YELLOW + "Vous louez maintenant cette parcelle pour " + days + " jours !");
                player.sendMessage(ChatColor.GRAY + "Surface: " + ChatColor.WHITE + "256m²");
                player.sendMessage(ChatColor.GRAY + "Position: " + ChatColor.WHITE + "X: " + (chunkX * 16) + " Z: " + (chunkZ * 16));
                player.sendMessage(ChatColor.GRAY + "Coût total: " + ChatColor.GOLD + String.format("%.2f€", totalPrice));
                player.sendMessage(ChatColor.GRAY + "Jours restants: " + ChatColor.WHITE + days);
                player.sendMessage(ChatColor.AQUA + "═══════════════════════════════════════");
                player.sendMessage("");
            } else {
                player.sendMessage(ChatColor.RED + "✗ Location impossible (fonds insuffisants ou erreur).");
            }

        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Erreur: paramètres invalides.");
        }
    }
}
