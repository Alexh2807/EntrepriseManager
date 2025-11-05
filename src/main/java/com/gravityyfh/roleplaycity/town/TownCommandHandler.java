package com.gravityyfh.roleplaycity.town;

import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.ChunkCoordinate;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.gui.TownMainGUI;
import com.gravityyfh.roleplaycity.town.manager.ClaimManager;
import com.gravityyfh.roleplaycity.town.manager.CompanyPlotManager;
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
    private final TownMainGUI townGUI;

    // Cache de sélection d'entreprise pour achats de terrains PROFESSIONNEL
    // UUID joueur → SIRET entreprise sélectionnée
    private final Map<UUID, String> selectedCompanyCache = new HashMap<>();

    public TownCommandHandler(RoleplayCity plugin, TownManager townManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.townGUI = new TownMainGUI(plugin, townManager);
    }

    /**
     * Définit l'entreprise sélectionnée par un joueur pour un achat
     */
    public void setSelectedCompany(UUID playerUuid, String siret) {
        selectedCompanyCache.put(playerUuid, siret);
    }

    /**
     * Récupère l'entreprise sélectionnée par un joueur, puis la supprime du cache
     */
    public String getAndClearSelectedCompany(UUID playerUuid) {
        return selectedCompanyCache.remove(playerUuid);
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
            if (plot == null) {
                player.sendMessage(ChatColor.RED + "Parcelle introuvable.");
                return;
            }

            // NOUVEAU : Si terrain PROFESSIONNEL, gérer sélection d'entreprise
            if (plot.getType() == com.gravityyfh.roleplaycity.town.data.PlotType.PROFESSIONNEL) {
                CompanyPlotManager companyManager = plugin.getCompanyPlotManager();

                // Compter les entreprises du joueur
                List<EntrepriseManagerLogic.Entreprise> playerCompanies = new ArrayList<>();
                for (EntrepriseManagerLogic.Entreprise entreprise : plugin.getEntrepriseManagerLogic().getEntreprises()) {
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

                if (playerCompanies.isEmpty()) {
                    player.sendMessage(ChatColor.RED + "Vous devez posséder une entreprise pour acheter un terrain PROFESSIONNEL !");
                    player.sendMessage(ChatColor.YELLOW + "→ Discutez de votre projet avec le Maire pour obtenir un contrat d'entreprise");
                    return;
                }

                if (playerCompanies.size() > 1) {
                    // 2+ entreprises : ouvrir GUI de sélection
                    plugin.getCompanySelectionGUI().open(player, chunkX, chunkZ, worldName, false);
                    return; // Stopper ici, la confirmation viendra après sélection
                } else {
                    // 1 seule entreprise : stocker automatiquement
                    setSelectedCompany(player.getUniqueId(), playerCompanies.get(0).getSiret());
                }
            }

            // Vérifier si la parcelle est dans un groupe
            com.gravityyfh.roleplaycity.town.data.PlotGroup plotGroup = town.findPlotGroupByPlot(plot);
            boolean isInGroup = (plotGroup != null);

            // Vérifier la vente (groupe ou parcelle individuelle)
            boolean isForSale = isInGroup ? plotGroup.isForSale() : plot.isForSale();
            if (!isForSale) {
                player.sendMessage(ChatColor.RED + (isInGroup ? "Ce groupe n'est pas en vente." : "Cette parcelle n'est pas en vente."));
                return;
            }

            // Récupérer les informations
            double salePrice = isInGroup ? plotGroup.getSalePrice() : plot.getSalePrice();
            int surface = isInGroup ? (plotGroup.getPlotCount() * 256) : 256;

            // Message de confirmation
            player.sendMessage("");
            player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
            player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "CONFIRMATION D'ACHAT");
            player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
            if (isInGroup) {
                player.sendMessage(ChatColor.YELLOW + "Groupe: " + ChatColor.WHITE + plotGroup.getGroupName() + " (" + plotGroup.getPlotCount() + " parcelles)");
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
            if (plot == null) {
                player.sendMessage(ChatColor.RED + "Parcelle introuvable.");
                return;
            }

            // Vérifier si la parcelle est dans un groupe
            com.gravityyfh.roleplaycity.town.data.PlotGroup plotGroup = town.findPlotGroupByPlot(plot);
            boolean isInGroup = (plotGroup != null);

            // Vérifier la location
            boolean isForRent = isInGroup ? plotGroup.isForRent() : plot.isForRent();
            if (!isForRent) {
                player.sendMessage(ChatColor.RED + (isInGroup ? "Ce groupe n'est pas en location." : "Cette parcelle n'est pas en location."));
                return;
            }

            // Récupérer les informations
            double rentPrice = isInGroup ? plotGroup.getRentPricePerDay() : plot.getRentPricePerDay();
            UUID currentRenter = isInGroup ? plotGroup.getRenterUuid() : plot.getRenterUuid();
            int currentDays = isInGroup ? plotGroup.getRentDaysRemaining() : plot.getRentDaysRemaining();
            int surface = isInGroup ? (plotGroup.getPlotCount() * 256) : 256;

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
            player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
            player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + (isRecharge ? "RECHARGE DE LOCATION" : "LOCATION"));
            player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
            if (isInGroup) {
                player.sendMessage(ChatColor.YELLOW + "Groupe: " + ChatColor.WHITE + plotGroup.getGroupName() + " (" + plotGroup.getPlotCount() + " parcelles)");
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
                "/ville:confirmrent " + chunkX + " " + chunkZ + " " + worldName + " " + daysToRent));
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
            cancelButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ville"));
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
            if (plot == null) {
                player.sendMessage(ChatColor.RED + "Parcelle introuvable.");
                return;
            }

            // Vérifier si la parcelle est dans un groupe
            com.gravityyfh.roleplaycity.town.data.PlotGroup plotGroup = town.findPlotGroupByPlot(plot);
            boolean isInGroup = (plotGroup != null);

            // Vérifier la vente
            boolean isForSale = isInGroup ? plotGroup.isForSale() : plot.isForSale();
            if (!isForSale) {
                player.sendMessage(ChatColor.RED + (isInGroup ? "Ce groupe n'est plus en vente." : "Cette parcelle n'est plus en vente."));
                return;
            }

            // Exécuter l'achat (groupe ou parcelle)
            boolean success;
            int surface;
            if (isInGroup) {
                success = economyManager.buyPlotGroup(townName, plotGroup, player);
                surface = plotGroup.getPlotCount() * 256;
            } else {
                success = economyManager.buyPlot(townName, plot, player);
                surface = 256;
            }

            if (success) {
                player.sendMessage("");
                player.sendMessage(ChatColor.GREEN + "═══════════════════════════════════════");
                player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "✓ ACHAT CONFIRMÉ");
                player.sendMessage(ChatColor.GREEN + "═══════════════════════════════════════");
                if (isInGroup) {
                    player.sendMessage(ChatColor.YELLOW + "Félicitations ! Vous êtes maintenant propriétaire de ce groupe de parcelles.");
                    player.sendMessage(ChatColor.GRAY + "Groupe: " + ChatColor.WHITE + plotGroup.getGroupName());
                    player.sendMessage(ChatColor.GRAY + "Parcelles: " + ChatColor.WHITE + plotGroup.getPlotCount());
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Félicitations ! Vous êtes maintenant propriétaire de cette parcelle.");
                    player.sendMessage(ChatColor.GRAY + "Position: " + ChatColor.WHITE + "X: " + (chunkX * 16) + " Z: " + (chunkZ * 16));
                }
                player.sendMessage(ChatColor.GRAY + "Surface: " + ChatColor.WHITE + surface + "m²");
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
            if (plot == null) {
                player.sendMessage(ChatColor.RED + "Parcelle introuvable.");
                return;
            }

            // Vérifier si la parcelle est dans un groupe
            com.gravityyfh.roleplaycity.town.data.PlotGroup plotGroup = town.findPlotGroupByPlot(plot);
            boolean isInGroup = (plotGroup != null);

            // Vérifier la location
            boolean isForRent = isInGroup ? plotGroup.isForRent() : plot.isForRent();
            if (!isForRent) {
                player.sendMessage(ChatColor.RED + (isInGroup ? "Ce groupe n'est plus en location." : "Cette parcelle n'est plus en location."));
                return;
            }

            // Exécuter la location (groupe ou parcelle)
            boolean success;
            int surface;
            double rentPrice;
            int finalDays;

            if (isInGroup) {
                success = economyManager.rentPlotGroup(townName, plotGroup, player, days);
                surface = plotGroup.getPlotCount() * 256;
                rentPrice = plotGroup.getRentPricePerDay();
                finalDays = plotGroup.getRentDaysRemaining();
            } else {
                success = economyManager.rentPlot(townName, plot, player, days);
                surface = 256;
                rentPrice = plot.getRentPricePerDay();
                finalDays = plot.getRentDaysRemaining();
            }

            if (success) {
                double totalPrice = rentPrice * days;
                player.sendMessage("");
                player.sendMessage(ChatColor.AQUA + "═══════════════════════════════════════");
                player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "✓ LOCATION CONFIRMÉE");
                player.sendMessage(ChatColor.AQUA + "═══════════════════════════════════════");
                if (isInGroup) {
                    player.sendMessage(ChatColor.YELLOW + "Vous louez maintenant ce groupe pour " + days + " jour(s) !");
                    player.sendMessage(ChatColor.GRAY + "Groupe: " + ChatColor.WHITE + plotGroup.getGroupName());
                    player.sendMessage(ChatColor.GRAY + "Parcelles: " + ChatColor.WHITE + plotGroup.getPlotCount());
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Vous louez maintenant cette parcelle pour " + days + " jour(s) !");
                    player.sendMessage(ChatColor.GRAY + "Position: " + ChatColor.WHITE + "X: " + (chunkX * 16) + " Z: " + (chunkZ * 16));
                }
                player.sendMessage(ChatColor.GRAY + "Surface: " + ChatColor.WHITE + surface + "m²");
                player.sendMessage(ChatColor.GRAY + "Coût: " + ChatColor.GOLD + String.format("%.2f€", totalPrice));
                player.sendMessage(ChatColor.GRAY + "Jours restants: " + ChatColor.WHITE + finalDays);
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
