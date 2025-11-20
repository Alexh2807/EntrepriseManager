package com.gravityyfh.roleplaycity.town;

import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.gravityyfh.roleplaycity.entreprise.model.*;
import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.ChunkCoordinate;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.PlotType;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TownCommandHandler implements CommandExecutor {

    private final RoleplayCity plugin;
    private final TownManager townManager;

    public TownCommandHandler(RoleplayCity plugin, TownManager townManager) {
        this.plugin = plugin;
        this.townManager = townManager;
    }

    // FIX: Utiliser le lazy getter au lieu de créer une nouvelle instance
    private TownMainGUI getTownGUI() {
        return plugin.getTownMainGUI();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Seuls les joueurs peuvent exécuter cette commande.");
            return true;
        }

        // Sans arguments : ouvrir le GUI principal
        if (args.length == 0) {
            getTownGUI().openMainMenu(player);
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
                getTownGUI().openMainMenu(player);
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
                // Ouvrir le GUI de gestion des groupes
                String townName = townManager.getPlayerTown(player.getUniqueId());
                if (townName == null) {
                    player.sendMessage(ChatColor.RED + "Vous devez être dans une ville pour gérer les groupes!");
                    return true;
                }
                plugin.getPlotGroupManagementGUI().openMainMenu(player, townName);
                return true;
            }
            case "finishgrouping" -> {
                // Finaliser le groupement de parcelles
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /ville finishgrouping <nom_du_groupe>");
                    return true;
                }
                String groupName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                plugin.getPlotGroupingListener().finishGrouping(player, groupName);
                return true;
            }
            case "cancelgrouping" -> {
                // Annuler la sélection en cours
                plugin.getPlotGroupingListener().cancelSession(player);
                return true;
            }
            case "degroup", "ungroup" -> {
                // Dégrouper le terrain sur lequel se trouve le joueur
                String townName = townManager.getPlayerTown(player.getUniqueId());
                if (townName == null) {
                    player.sendMessage(ChatColor.RED + "Vous devez être dans une ville!");
                    return true;
                }

                Town town = townManager.getTown(townName);
                if (town == null) {
                    player.sendMessage(ChatColor.RED + "Ville introuvable!");
                    return true;
                }

                org.bukkit.Chunk chunk = player.getLocation().getChunk();
                Plot plot = plugin.getClaimManager().getPlotAt(chunk);

                if (plot == null) {
                    player.sendMessage(ChatColor.RED + "Vous devez être sur un terrain de votre ville!");
                    return true;
                }

                if (!plot.getTownName().equals(townName)) {
                    player.sendMessage(ChatColor.RED + "Ce terrain n'appartient pas à votre ville!");
                    return true;
                }

                if (!plot.isGrouped()) {
                    player.sendMessage(ChatColor.RED + "Ce terrain n'est pas groupé!");
                    return true;
                }

                // Vérifier les permissions
                if (!town.isMayor(player.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + "Seul le maire peut dégrouper des terrains!");
                    return true;
                }

                plugin.getPlotGroupManagementGUI().ungroupPlot(player, plot, townName);
                return true;
            }
            case "buyplot" -> {
                // Commande interne pour acheter une parcelle
                // Vérifier si c'est une annulation
                if (args.length >= 2 && args[1].equalsIgnoreCase("cancel")) {
                    // Nettoyer le cache et afficher message d'annulation
                    if (plugin.getEnterpriseContextManager() != null) plugin.getEnterpriseContextManager().clearPlayerCache(player.getUniqueId());
                    player.sendMessage("");
                    player.sendMessage(ChatColor.YELLOW + "✗ Achat annulé.");
                    player.sendMessage("");
                    return true;
                }

                if (args.length < 4) {
                    player.sendMessage(ChatColor.RED + "Erreur interne: paramètres manquants.");
                    return true;
                }
                handleBuyPlotCommand(player, args[1], args[2], args[3]);
                return true;
            }
            case "rentplot" -> {
                // Commande interne pour louer une parcelle
                // Vérifier si c'est une annulation
                if (args.length >= 2 && args[1].equalsIgnoreCase("cancel")) {
                    // Nettoyer le cache et afficher message d'annulation
                    if (plugin.getEnterpriseContextManager() != null) plugin.getEnterpriseContextManager().clearPlayerCache(player.getUniqueId());
                    player.sendMessage("");
                    player.sendMessage(ChatColor.YELLOW + "✗ Location annulée.");
                    player.sendMessage("");
                    return true;
                }

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
            case "admin" -> {
                return handleAdminCommand(player, args);
            }
            default -> {
                player.sendMessage(ChatColor.RED + "Sous-commande inconnue.");
                getTownGUI().openMainMenu(player);
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
            if (plot == null) {
                player.sendMessage(ChatColor.RED + "Terrain introuvable.");
                return;
            }

            PlotType terrainType = plot.getType();

            // NOUVEAU : Si terrain PROFESSIONNEL, gérer sélection d'entreprise
            if (terrainType == PlotType.PROFESSIONNEL) {
                // FIX: Toujours nettoyer le cache et vérifier s'il contenait un SIRET
                String cachedSiret = null;
                com.gravityyfh.roleplaycity.town.manager.EnterpriseContextManager enterpriseContextManager = plugin.getEnterpriseContextManager();
                
                if (enterpriseContextManager != null) {
                    cachedSiret = enterpriseContextManager.getAndClearSelectedEnterprise(
                        player.getUniqueId(), 
                        com.gravityyfh.roleplaycity.town.manager.EnterpriseContextManager.OperationType.PURCHASE
                    );
                }

                if (cachedSiret == null) {
                    // Pas de cache → Première fois, ouvrir le GUI
                    List<Entreprise> playerCompanies;
                    if (enterpriseContextManager != null) {
                        playerCompanies = enterpriseContextManager.getPlayerEnterprises(player);
                    } else {
                        // Fallback si pas encore initialisé
                        playerCompanies = new ArrayList<>();
                        for (Entreprise entreprise : plugin.getEntrepriseManagerLogic().getEntreprises()) {
                            String gerantUuidStr = entreprise.getGerantUUID();
                            if (gerantUuidStr != null) {
                                try {
                                    UUID gerantUuid = UUID.fromString(gerantUuidStr);
                                    if (gerantUuid.equals(player.getUniqueId())) {
                                        playerCompanies.add(entreprise);
                                    }
                                } catch (IllegalArgumentException e) {
                                    // UUID invalide, ignorer
                                }
                            }
                        }
                    }

                    if (playerCompanies.isEmpty()) {
                        player.sendMessage(ChatColor.RED + "✗ Vous devez posséder une entreprise pour acheter un terrain PROFESSIONNEL.");
                        player.sendMessage(ChatColor.YELLOW + "→ Parlez avec le maire de votre ville afin de créer le projet");
                        return;
                    }

                    // TOUJOURS ouvrir le GUI de sélection, même avec 1 entreprise
                    plugin.getCompanySelectionGUI().open(player, chunkX, chunkZ, worldName, false,
                        com.gravityyfh.roleplaycity.town.manager.EnterpriseContextManager.OperationType.PURCHASE);
                    return; // Stopper ici, la confirmation viendra après sélection
                }
                // Sinon: cache existe, continuer normalement vers le message de confirmation
            }

            // Vérifier la vente
            if (!plot.isForSale()) {
                player.sendMessage(ChatColor.RED + "Cette parcelle n'est pas en vente.");
                return;
            }

            // Récupérer les informations
            double salePrice = plot.getSalePrice();
            int totalChunks = plot.getChunks().size();
            int surface = totalChunks * 256;

            // Message de confirmation
            player.sendMessage("");
            player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "CONFIRMATION D'ACHAT");
            player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            if (plot.isGrouped()) {
                player.sendMessage(ChatColor.YELLOW + "Terrain: " + ChatColor.WHITE + "Terrain groupé (" + totalChunks + " chunks)");
                player.sendMessage(ChatColor.YELLOW + "Surface totale: " + ChatColor.WHITE + surface + "m²");
            } else {
                player.sendMessage(ChatColor.YELLOW + "Parcelle: " + ChatColor.WHITE + "256m² (" + (chunkX * 16) + ", " + (chunkZ * 16) + ")");
            }
            player.sendMessage(ChatColor.YELLOW + "Prix: " + ChatColor.GOLD + String.format("%.2f€", salePrice));
            player.sendMessage(ChatColor.YELLOW + "Ville: " + ChatColor.WHITE + townName);
            player.sendMessage("");

            // Bouton CONFIRMER
            TextComponent confirmButton = new TextComponent("  [✓ CONFIRMER L'ACHAT]");
            confirmButton.setColor(net.md_5.bungee.api.ChatColor.GREEN);
            confirmButton.setBold(true);
            confirmButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/ville confirmbuy " + chunkX + " " + chunkZ + " " + worldName));
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
                "/ville buyplot cancel"));
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

            player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
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
            if (plot == null) {
                player.sendMessage(ChatColor.RED + "Parcelle introuvable.");
                return;
            }

            // Vérifier la location
            if (!plot.isForRent()) {
                player.sendMessage(ChatColor.RED + "Cette parcelle n'est pas en location.");
                return;
            }

            // AJOUT BUG FIX: Si terrain PROFESSIONNEL, gérer sélection d'entreprise
            PlotType terrainType = plot.getType();
            if (terrainType == PlotType.PROFESSIONNEL) {
                // Vérifier si c'est une recharge (déjà locataire)
                UUID currentRenter = plot.getRenterUuid();
                boolean isRecharge = (currentRenter != null && currentRenter.equals(player.getUniqueId()));

                // Seulement demander entreprise pour NOUVELLE location, pas recharge
                if (!isRecharge) {
                    // FIX: Toujours nettoyer le cache et vérifier s'il contenait un SIRET
                    String cachedSiret = null;
                    com.gravityyfh.roleplaycity.town.manager.EnterpriseContextManager enterpriseContextManager = plugin.getEnterpriseContextManager();
                    
                    if (enterpriseContextManager != null) {
                        cachedSiret = enterpriseContextManager.getAndClearSelectedEnterprise(
                            player.getUniqueId(),
                            com.gravityyfh.roleplaycity.town.manager.EnterpriseContextManager.OperationType.RENTAL
                        );
                    }

                    if (cachedSiret == null) {
                        // Pas de cache → Première fois, ouvrir le GUI
                        List<Entreprise> playerCompanies;
                        if (enterpriseContextManager != null) {
                            playerCompanies = enterpriseContextManager.getPlayerEnterprises(player);
                        } else {
                            // Fallback si pas encore initialisé
                            playerCompanies = new ArrayList<>();
                            for (Entreprise entreprise : plugin.getEntrepriseManagerLogic().getEntreprises()) {
                                String gerantUuidStr = entreprise.getGerantUUID();
                                if (gerantUuidStr != null) {
                                    try {
                                        UUID gerantUuid = UUID.fromString(gerantUuidStr);
                                        if (gerantUuid.equals(player.getUniqueId())) {
                                            playerCompanies.add(entreprise);
                                        }
                                    } catch (IllegalArgumentException e) {
                                        // UUID invalide, ignorer
                                    }
                                }
                            }
                        }

                        if (playerCompanies.isEmpty()) {
                            player.sendMessage(ChatColor.RED + "✗ Vous devez posséder une entreprise pour louer un terrain PROFESSIONNEL.");
                            player.sendMessage(ChatColor.YELLOW + "→ Parlez avec le maire de votre ville afin de créer le projet");
                            return;
                        }

                        // TOUJOURS ouvrir le GUI de sélection, même avec 1 entreprise
                        plugin.getCompanySelectionGUI().open(player, chunkX, chunkZ, worldName, false,
                            com.gravityyfh.roleplaycity.town.manager.EnterpriseContextManager.OperationType.RENTAL);
                        return; // Stopper ici, la confirmation viendra après sélection
                    }
                    // Sinon: cache existe, continuer normalement vers le message de confirmation
                }
            }

            // Récupérer les informations
            double rentPrice = plot.getRentPricePerDay();
            UUID currentRenter = plot.getRenterUuid();
            int currentDays = plot.getRentDaysRemaining();
            int totalChunks = plot.getChunks().size();
            int surface = totalChunks * 256;

            // NOUVEAU SYSTÈME: Location initiale de 1 jour, rechargeable jusqu'à 30 jours max
            int daysToRent = 1;

            // Si déjà loué par ce joueur, recharger au lieu de louer initialement
            boolean isRecharge = (currentRenter != null && currentRenter.equals(player.getUniqueId()));

            if (isRecharge) {
                // C'est une recharge
                int maxDays = 30 - currentDays;
                if (maxDays <= 0) {
                    player.sendMessage(ChatColor.RED + "Vous avez déjà atteint le maximum de 30 jours !");
                    return;
                }
                // Proposer de recharger 1 jour
                daysToRent = 1;
            }

            // Message de confirmation
            player.sendMessage("");
            player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + (isRecharge ? "RECHARGE DE LOCATION" : "LOCATION"));
            player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            if (plot.isGrouped()) {
                player.sendMessage(ChatColor.YELLOW + "Terrain: " + ChatColor.WHITE + "Terrain groupé (" + totalChunks + " chunks)");
                player.sendMessage(ChatColor.YELLOW + "Surface totale: " + ChatColor.WHITE + surface + "m²");
            } else {
                player.sendMessage(ChatColor.YELLOW + "Parcelle: " + ChatColor.WHITE + "256m² (" + (chunkX * 16) + ", " + (chunkZ * 16) + ")");
            }
            player.sendMessage(ChatColor.YELLOW + "Prix: " + ChatColor.GOLD + String.format("%.2f€/jour", rentPrice));
            player.sendMessage(ChatColor.YELLOW + "Ville: " + ChatColor.WHITE + townName);

            if (isRecharge) {
                player.sendMessage("");
                player.sendMessage(ChatColor.GRAY + "Jours actuels: " + ChatColor.WHITE + currentDays + " jours");
                player.sendMessage(ChatColor.GRAY + "Après recharge: " + ChatColor.WHITE + (currentDays + daysToRent) + " jours");
            }

            player.sendMessage("");
            player.sendMessage(ChatColor.GRAY + "Coût pour " + daysToRent + " jour: " + ChatColor.GOLD + String.format("%.2f€", rentPrice * daysToRent));
            player.sendMessage(ChatColor.GRAY + "Maximum rechargeable: 30 jours");
            player.sendMessage("");

            // Bouton CONFIRMER
            TextComponent confirmButton = new TextComponent("  [✓ CONFIRMER]");
            confirmButton.setColor(net.md_5.bungee.api.ChatColor.AQUA);
            confirmButton.setBold(true);
            confirmButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/ville confirmrent " + chunkX + " " + chunkZ + " " + worldName + " " + daysToRent));
            confirmButton.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder((isRecharge ? "Recharger" : "Louer") + " pour " + daysToRent + " jour\nTotal: " + String.format("%.2f€", rentPrice * daysToRent))
                    .color(net.md_5.bungee.api.ChatColor.YELLOW)
                    .create()
            ));

            // Bouton ANNULER
            TextComponent cancelButton = new TextComponent(" [✗ ANNULER]");
            cancelButton.setColor(net.md_5.bungee.api.ChatColor.RED);
            cancelButton.setBold(true);
            cancelButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ville rentplot cancel"));
            cancelButton.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("Annuler")
                    .color(net.md_5.bungee.api.ChatColor.RED)
                    .create()
            ));

            TextComponent message = new TextComponent("");
            message.addExtra(confirmButton);
            message.addExtra(cancelButton);
            player.spigot().sendMessage(message);

            player.sendMessage("");
            player.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
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

            // FIX: Nettoyer le cache de sélection d'entreprise après confirmation
            // Cela garantit qu'une nouvelle transaction nécessitera une nouvelle sélection
            if (plugin.getEnterpriseContextManager() != null) plugin.getEnterpriseContextManager().clearPlayerCache(player.getUniqueId());

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
            if (plot == null) {
                player.sendMessage(ChatColor.RED + "Parcelle introuvable.");
                return;
            }

            // Vérifier la vente
            if (!plot.isForSale()) {
                player.sendMessage(ChatColor.RED + "Cette parcelle n'est plus en vente.");
                return;
            }

            // Exécuter l'achat
            boolean success = economyManager.buyPlot(townName, plot, player);
            int totalChunks = plot.getChunks().size();
            int surface = totalChunks * 256;

            if (success) {
                player.sendMessage("");
                player.sendMessage(ChatColor.GREEN + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "✓ ACHAT CONFIRMÉ");
                player.sendMessage(ChatColor.GREEN + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                if (plot.isGrouped()) {
                    player.sendMessage(ChatColor.YELLOW + "Félicitations ! Vous êtes maintenant propriétaire de ce terrain.");
                    player.sendMessage(ChatColor.GRAY + "Terrain: " + ChatColor.WHITE + "Terrain groupé");
                    player.sendMessage(ChatColor.GRAY + "Chunks: " + ChatColor.WHITE + totalChunks);
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Félicitations ! Vous êtes maintenant propriétaire de cette parcelle.");
                    player.sendMessage(ChatColor.GRAY + "Position: " + ChatColor.WHITE + "X: " + (chunkX * 16) + " Z: " + (chunkZ * 16));
                }
                player.sendMessage(ChatColor.GRAY + "Surface: " + ChatColor.WHITE + surface + "m²");
                player.sendMessage(ChatColor.GREEN + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
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

            // FIX: Nettoyer le cache de sélection d'entreprise après confirmation
            // Cela garantit qu'une nouvelle transaction nécessitera une nouvelle sélection
            if (plugin.getEnterpriseContextManager() != null) plugin.getEnterpriseContextManager().clearPlayerCache(player.getUniqueId());

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
            if (plot == null) {
                player.sendMessage(ChatColor.RED + "Parcelle introuvable.");
                return;
            }

            // Vérifier la location
            if (!plot.isForRent()) {
                player.sendMessage(ChatColor.RED + "Cette parcelle n'est plus en location.");
                return;
            }

            // Exécuter la location
            boolean success = economyManager.rentPlot(townName, plot, player, days);
            int totalChunks = plot.getChunks().size();
            int surface = totalChunks * 256;
            double rentPrice = plot.getRentPricePerDay();
            int finalDays = plot.getRentDaysRemaining();

            if (success) {
                double totalPrice = rentPrice * days;
                player.sendMessage("");
                player.sendMessage(ChatColor.AQUA + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "✓ LOCATION CONFIRMÉE");
                player.sendMessage(ChatColor.AQUA + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                if (plot.isGrouped()) {
                    player.sendMessage(ChatColor.YELLOW + "Vous louez maintenant ce terrain pour " + days + " jour(s) !");
                    player.sendMessage(ChatColor.GRAY + "Terrain: " + ChatColor.WHITE + "Terrain groupé");
                    player.sendMessage(ChatColor.GRAY + "Chunks: " + ChatColor.WHITE + totalChunks);
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Vous louez maintenant cette parcelle pour " + days + " jour(s) !");
                    player.sendMessage(ChatColor.GRAY + "Position: " + ChatColor.WHITE + "X: " + (chunkX * 16) + " Z: " + (chunkZ * 16));
                }
                player.sendMessage(ChatColor.GRAY + "Surface: " + ChatColor.WHITE + surface + "m²");
                player.sendMessage(ChatColor.GRAY + "Coût: " + ChatColor.GOLD + String.format("%.2f€", totalPrice));
                player.sendMessage(ChatColor.GRAY + "Jours restants: " + ChatColor.WHITE + finalDays);
                player.sendMessage(ChatColor.AQUA + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                player.sendMessage("");
            } else {
                player.sendMessage(ChatColor.RED + "✗ Location impossible (fonds insuffisants ou erreur).");
            }

        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Erreur: paramètres invalides.");
        }
    }

    /**
     * NOUVEAU: Gère les commandes admin
     * /ville admin collecttaxes - Force la collecte horaire pour toutes les villes
     * /ville admin forcepay <joueur> - Force un joueur à payer ses taxes immédiatement
     * /ville admin cleardebt <joueur> - Efface toutes les dettes d'un joueur
     */
    private boolean handleAdminCommand(Player player, String[] args) {
        // Vérifier les permissions
        if (!player.hasPermission("roleplaycity.admin") && !player.isOp()) {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission d'utiliser cette commande.");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.GOLD + "=== Commandes Admin - Taxes ===");
            player.sendMessage(ChatColor.YELLOW + "/ville admin collecttaxes" + ChatColor.GRAY + " - Force la collecte horaire");
            player.sendMessage(ChatColor.YELLOW + "/ville admin forcepay <joueur>" + ChatColor.GRAY + " - Force le paiement d'un joueur");
            player.sendMessage(ChatColor.YELLOW + "/ville admin cleardebt <joueur>" + ChatColor.GRAY + " - Efface les dettes d'un joueur");
            return true;
        }

        String adminSubCommand = args[1].toLowerCase();

        switch (adminSubCommand) {
            case "collecttaxes" -> {
                return handleAdminCollectTaxes(player);
            }
            case "forcepay" -> {
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /ville admin forcepay <joueur>");
                    return true;
                }
                return handleAdminForcePay(player, args[2]);
            }
            case "cleardebt" -> {
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /ville admin cleardebt <joueur>");
                    return true;
                }
                return handleAdminClearDebt(player, args[2]);
            }
            default -> {
                player.sendMessage(ChatColor.RED + "Sous-commande admin inconnue.");
                player.sendMessage(ChatColor.YELLOW + "Utilisez /ville admin pour voir les commandes disponibles.");
                return true;
            }
        }
    }

    /**
     * Force la collecte horaire des taxes pour toutes les villes
     */
    private boolean handleAdminCollectTaxes(Player player) {
        player.sendMessage(ChatColor.YELLOW + "⏳ Collecte horaire des taxes en cours...");

        TownEconomyManager economyManager = plugin.getTownEconomyManager();
        if (economyManager == null) {
            player.sendMessage(ChatColor.RED + "❌ Erreur: Système économique non disponible.");
            return true;
        }

        // Lancer la collecte horaire
        economyManager.collectAllTaxesHourly();

        player.sendMessage(ChatColor.GREEN + "✓ Collecte horaire des taxes terminée!");
        player.sendMessage(ChatColor.GRAY + "Consultez la console pour les détails.");

        return true;
    }

    /**
     * Force un joueur à payer ses taxes immédiatement
     */
    private boolean handleAdminForcePay(Player player, String targetName) {
        org.bukkit.OfflinePlayer target = plugin.getServer().getOfflinePlayer(targetName);

        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            player.sendMessage(ChatColor.RED + "❌ Joueur introuvable: " + targetName);
            return true;
        }

        // Trouver la ville du joueur
        String townName = townManager.getPlayerTown(target.getUniqueId());
        if (townName == null) {
            player.sendMessage(ChatColor.RED + "❌ Le joueur " + targetName + " n'est dans aucune ville.");
            return true;
        }

        Town town = townManager.getTown(townName);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "❌ Ville introuvable.");
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "⏳ Collecte des taxes de " + targetName + " en cours...");

        TownEconomyManager economyManager = plugin.getTownEconomyManager();
        if (economyManager == null) {
            player.sendMessage(ChatColor.RED + "❌ Erreur: Système économique non disponible.");
            return true;
        }

        // Lancer la collecte uniquement pour cette ville
        // Cela va créer des dettes si le joueur ne peut pas payer
        economyManager.collectAllTaxesHourly();

        player.sendMessage(ChatColor.GREEN + "✓ Collecte terminée pour " + targetName + "!");
        player.sendMessage(ChatColor.GRAY + "Si le joueur n'a pas pu payer, une dette a été créée.");

        // Afficher les dettes du joueur
        if (town.hasPlayerDebts(target.getUniqueId())) {
            double totalDebt = town.getTotalPlayerDebt(target.getUniqueId());
            player.sendMessage(ChatColor.RED + "⚠ Dette actuelle: " + ChatColor.GOLD + String.format("%.2f€", totalDebt));
        } else {
            player.sendMessage(ChatColor.GREEN + "✓ Aucune dette pour ce joueur.");
        }

        return true;
    }

    /**
     * Efface toutes les dettes d'un joueur
     */
    private boolean handleAdminClearDebt(Player player, String targetName) {
        org.bukkit.OfflinePlayer target = plugin.getServer().getOfflinePlayer(targetName);

        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            player.sendMessage(ChatColor.RED + "❌ Joueur introuvable: " + targetName);
            return true;
        }

        // Trouver la ville du joueur
        String townName = townManager.getPlayerTown(target.getUniqueId());
        if (townName == null) {
            player.sendMessage(ChatColor.RED + "❌ Le joueur " + targetName + " n'est dans aucune ville.");
            return true;
        }

        Town town = townManager.getTown(townName);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "❌ Ville introuvable.");
            return true;
        }

        // Vérifier s'il y a des dettes
        if (!town.hasPlayerDebts(target.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "ℹ Le joueur " + targetName + " n'a aucune dette.");
            return true;
        }

        double totalDebt = town.getTotalPlayerDebt(target.getUniqueId());
        int debtCount = town.getPlayerDebts(target.getUniqueId()).size();

        // Effacer toutes les dettes
        for (Town.PlayerDebt debt : town.getPlayerDebts(target.getUniqueId())) {
            Plot plot = debt.plot();

            // Réinitialiser les dettes selon le type
            if (plot.getParticularDebtAmount() > 0) {
                plot.resetParticularDebt();
            }
            if (plot.getCompanyDebtAmount() > 0) {
                plot.resetDebt();
            }
        }

        // Sauvegarder
        townManager.saveTownsNow();

        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "✓ DETTES EFFACÉES");
        player.sendMessage(ChatColor.GREEN + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage(ChatColor.YELLOW + "Joueur: " + ChatColor.WHITE + targetName);
        player.sendMessage(ChatColor.YELLOW + "Ville: " + ChatColor.WHITE + townName);
        player.sendMessage(ChatColor.YELLOW + "Dettes effacées: " + ChatColor.WHITE + debtCount);
        player.sendMessage(ChatColor.YELLOW + "Montant total: " + ChatColor.GOLD + String.format("%.2f€", totalDebt));
        player.sendMessage(ChatColor.GREEN + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("");

        // Notifier le joueur s'il est en ligne
        if (target.isOnline() && target.getPlayer() != null) {
            Player targetPlayer = target.getPlayer();
            targetPlayer.sendMessage("");
            targetPlayer.sendMessage(ChatColor.GREEN + "✓ Toutes vos dettes ont été effacées par un administrateur!");
            targetPlayer.sendMessage(ChatColor.YELLOW + "Montant: " + ChatColor.GOLD + String.format("%.2f€", totalDebt));
            targetPlayer.sendMessage("");
        }

        return true;
    }
}
