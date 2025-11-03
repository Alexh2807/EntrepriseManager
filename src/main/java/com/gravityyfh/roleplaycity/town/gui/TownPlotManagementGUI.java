package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.PlotType;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownRole;
import com.gravityyfh.roleplaycity.town.manager.ClaimManager;
import com.gravityyfh.roleplaycity.town.manager.TownEconomyManager;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class TownPlotManagementGUI implements Listener {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final ClaimManager claimManager;
    private final TownEconomyManager economyManager;

    private static final String PLOT_MENU_TITLE = ChatColor.DARK_GREEN + "🏘️ Gestion Parcelle";

    // Système de saisie de prix
    private final Map<UUID, PlotActionContext> pendingActions;

    public TownPlotManagementGUI(RoleplayCity plugin, TownManager townManager,
                                 ClaimManager claimManager, TownEconomyManager economyManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.claimManager = claimManager;
        this.economyManager = economyManager;
        this.pendingActions = new HashMap<>();
    }

    public void openPlotMenu(Player player) {
        Chunk currentChunk = player.getLocation().getChunk();
        Plot plot = claimManager.getPlotAt(currentChunk);

        if (plot == null) {
            player.sendMessage(ChatColor.RED + "Cette parcelle n'appartient à aucune ville.");
            return;
        }

        String townName = claimManager.getClaimOwner(currentChunk);
        Town town = townManager.getTown(townName);

        if (town == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Ville introuvable.");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, PLOT_MENU_TITLE);

        // Informations de la parcelle
        ItemStack infoItem = new ItemStack(Material.MAP);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(ChatColor.AQUA + "Informations Parcelle");
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.GRAY + "Position: " + ChatColor.WHITE + plot.getCoordinates());
        infoLore.add(ChatColor.GRAY + "Type: " + ChatColor.AQUA + plot.getType().getDisplayName());
        infoLore.add(ChatColor.GRAY + "Taxe quotidienne: " + ChatColor.GOLD + plot.getDailyTax() + "€");

        if (plot.getOwnerName() != null) {
            infoLore.add(ChatColor.GRAY + "Propriétaire: " + ChatColor.YELLOW + plot.getOwnerName());
        } else {
            infoLore.add(ChatColor.GRAY + "Propriétaire: " + ChatColor.GREEN + "Municipal");
        }

        if (plot.isForSale()) {
            infoLore.add(ChatColor.GREEN + "En vente: " + ChatColor.GOLD + plot.getSalePrice() + "€");
        }

        if (plot.isForRent()) {
            double dailyPrice = plot.getRentPrice() / plot.getRentDurationDays();
            infoLore.add(ChatColor.YELLOW + "En location: " + ChatColor.GOLD +
                String.format("%.2f€/jour", dailyPrice) + ChatColor.GRAY + " (" + plot.getRentDurationDays() + "j total)");
        }

        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);
        inv.setItem(4, infoItem);

        TownRole role = town.getMemberRole(player.getUniqueId());
        boolean canManage = role == TownRole.MAIRE || role == TownRole.ADJOINT ||
            (plot.getOwnerUuid() != null && plot.getOwnerUuid().equals(player.getUniqueId()));

        // Mettre en vente (seulement pour parcelles PARTICULIER ou PROFESSIONNEL)
        if (canManage && !plot.isForSale() &&
            (plot.getType() == PlotType.PARTICULIER || plot.getType() == PlotType.PROFESSIONNEL)) {
            ItemStack saleItem = new ItemStack(Material.GOLD_INGOT);
            ItemMeta saleMeta = saleItem.getItemMeta();
            saleMeta.setDisplayName(ChatColor.GREEN + "Mettre en Vente");
            List<String> saleLore = new ArrayList<>();
            saleLore.add(ChatColor.GRAY + "Proposer cette parcelle");
            saleLore.add(ChatColor.GRAY + "à la vente aux citoyens");
            saleLore.add("");
            saleLore.add(ChatColor.YELLOW + "Cliquez pour définir le prix");
            saleMeta.setLore(saleLore);
            saleItem.setItemMeta(saleMeta);
            inv.setItem(10, saleItem);
        }

        // Acheter
        if (plot.isForSale() && !canManage) {
            ItemStack buyItem = new ItemStack(Material.EMERALD);
            ItemMeta buyMeta = buyItem.getItemMeta();
            buyMeta.setDisplayName(ChatColor.GREEN + "Acheter cette Parcelle");
            List<String> buyLore = new ArrayList<>();
            buyLore.add(ChatColor.GRAY + "Prix: " + ChatColor.GOLD + plot.getSalePrice() + "€");
            buyLore.add("");
            buyLore.add(ChatColor.YELLOW + "Cliquez pour acheter");
            buyMeta.setLore(buyLore);
            buyItem.setItemMeta(buyMeta);
            inv.setItem(11, buyItem);
        }

        // Annuler la vente
        if (canManage && plot.isForSale()) {
            ItemStack cancelSaleItem = new ItemStack(Material.BARRIER);
            ItemMeta cancelMeta = cancelSaleItem.getItemMeta();
            cancelMeta.setDisplayName(ChatColor.RED + "Annuler la Vente");
            cancelSaleItem.setItemMeta(cancelMeta);
            inv.setItem(10, cancelSaleItem);
        }

        // Mettre en location (seulement pour parcelles PARTICULIER ou PROFESSIONNEL)
        if (canManage && !plot.isForRent() && plot.getRenterUuid() == null &&
            (plot.getType() == PlotType.PARTICULIER || plot.getType() == PlotType.PROFESSIONNEL)) {
            ItemStack rentItem = new ItemStack(Material.PAPER);
            ItemMeta rentMeta = rentItem.getItemMeta();
            rentMeta.setDisplayName(ChatColor.YELLOW + "Mettre en Location");
            List<String> rentLore = new ArrayList<>();
            rentLore.add(ChatColor.GRAY + "Proposer cette parcelle");
            rentLore.add(ChatColor.GRAY + "en location temporaire");
            rentLore.add("");
            rentLore.add(ChatColor.YELLOW + "Cliquez pour définir le prix par jour");
            rentMeta.setLore(rentLore);
            rentItem.setItemMeta(rentMeta);
            inv.setItem(12, rentItem);
        }

        // Louer (première fois)
        if (plot.isForRent() && !canManage) {
            ItemStack rentBuyItem = new ItemStack(Material.GOLD_BLOCK);
            ItemMeta rentBuyMeta = rentBuyItem.getItemMeta();
            rentBuyMeta.setDisplayName(ChatColor.YELLOW + "Louer cette Parcelle");
            List<String> rentBuyLore = new ArrayList<>();
            rentBuyLore.add(ChatColor.GRAY + "Prix: " + ChatColor.GOLD + String.format("%.2f€/jour", plot.getRentPricePerDay()));
            rentBuyLore.add(ChatColor.GRAY + "Maximum: " + ChatColor.WHITE + "30 jours");
            rentBuyLore.add("");
            rentBuyLore.add(ChatColor.YELLOW + "Cliquez pour louer");
            rentBuyMeta.setLore(rentBuyLore);
            rentBuyItem.setItemMeta(rentBuyMeta);
            inv.setItem(13, rentBuyItem);
        }

        // Recharger la location (si locataire actuel)
        if (plot.isRentedBy(player.getUniqueId())) {
            ItemStack rechargeItem = new ItemStack(Material.EMERALD);
            ItemMeta rechargeMeta = rechargeItem.getItemMeta();
            rechargeMeta.setDisplayName(ChatColor.GREEN + "Recharger la Location");
            List<String> rechargeLore = new ArrayList<>();
            rechargeLore.add(ChatColor.GRAY + "Solde actuel: " + ChatColor.YELLOW + plot.getRentDaysRemaining() + "/30 jours");
            rechargeLore.add(ChatColor.GRAY + "Prix: " + ChatColor.GOLD + String.format("%.2f€/jour", plot.getRentPricePerDay()));
            rechargeLore.add("");
            int canAdd = 30 - plot.getRentDaysRemaining();
            if (canAdd > 0) {
                rechargeLore.add(ChatColor.GREEN + "Vous pouvez ajouter jusqu'à " + canAdd + " jours");
                rechargeLore.add(ChatColor.YELLOW + "Cliquez pour recharger");
            } else {
                rechargeLore.add(ChatColor.RED + "Solde au maximum");
            }
            rechargeMeta.setLore(rechargeLore);
            rechargeItem.setItemMeta(rechargeMeta);
            inv.setItem(13, rechargeItem);
        }

        // Annuler la location
        if (canManage && plot.isForRent()) {
            ItemStack cancelRentItem = new ItemStack(Material.BARRIER);
            ItemMeta cancelMeta = cancelRentItem.getItemMeta();
            cancelMeta.setDisplayName(ChatColor.RED + "Annuler la Location");
            cancelRentItem.setItemMeta(cancelMeta);
            inv.setItem(12, cancelRentItem);
        }

        // Changer le type de parcelle
        if (role == TownRole.MAIRE || role == TownRole.ADJOINT) {
            ItemStack typeItem = new ItemStack(Material.WRITABLE_BOOK);
            ItemMeta typeMeta = typeItem.getItemMeta();
            typeMeta.setDisplayName(ChatColor.BLUE + "Changer le Type");
            List<String> typeLore = new ArrayList<>();
            typeLore.add(ChatColor.GRAY + "Type actuel: " + ChatColor.AQUA + plot.getType().getDisplayName());
            typeLore.add("");
            typeLore.add(ChatColor.YELLOW + "Cliquez pour changer le type");
            typeMeta.setLore(typeLore);
            typeItem.setItemMeta(typeMeta);
            inv.setItem(16, typeItem);
        }

        // Fermer
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "Fermer");
        closeItem.setItemMeta(closeMeta);
        inv.setItem(26, closeItem);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.equals(PLOT_MENU_TITLE) && !title.contains("Changer le type")) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        // Gestion du menu de changement de type
        if (title.contains("Changer le type")) {
            if (displayName.contains("Retour")) {
                player.closeInventory();
                Chunk currentChunk = player.getLocation().getChunk();
                openPlotMenu(player);
            } else if (!displayName.contains("Actuel")) {
                // Chercher le type sélectionné
                PlotType selectedType = null;
                for (PlotType type : PlotType.values()) {
                    if (displayName.contains(ChatColor.stripColor(type.getDisplayName()))) {
                        selectedType = type;
                        break;
                    }
                }

                if (selectedType != null && player.hasMetadata("plot_type_change_town")) {
                    Chunk currentChunk = player.getLocation().getChunk();
                    Plot plot = claimManager.getPlotAt(currentChunk);
                    String townName = player.getMetadata("plot_type_change_town").get(0).asString();
                    player.removeMetadata("plot_type_change_town", plugin);

                    if (plot != null) {
                        PlotType oldType = plot.getType();

                        // VALIDATION : Un terrain avec propriétaire ne peut pas devenir MUNICIPAL ou PUBLIC
                        if (plot.getOwnerUuid() != null &&
                            (selectedType == PlotType.MUNICIPAL || selectedType == PlotType.PUBLIC)) {
                            player.sendMessage(ChatColor.RED + "✗ Impossible de changer ce terrain en " + selectedType.getDisplayName() + " !");
                            player.sendMessage(ChatColor.YELLOW + "Raison : Cette parcelle a un propriétaire (" + plot.getOwnerName() + ")");
                            player.sendMessage(ChatColor.GRAY + "Vous devez d'abord retirer le propriétaire ou racheter le terrain à la ville.");
                            player.closeInventory();
                            return;
                        }

                        plot.setType(selectedType);

                        // Vérifier si la parcelle faisait partie d'un groupe
                        Town town = townManager.getTown(townName);
                        if (town != null) {
                            boolean wasRemoved = town.removePlotFromGroupIfIncompatible(plot);

                            if (wasRemoved) {
                                player.sendMessage(ChatColor.YELLOW + "⚠ Cette parcelle a été retirée de son groupe de terrain !");
                                player.sendMessage(ChatColor.GRAY + "Raison : Le type " + selectedType.getDisplayName() +
                                    " n'est pas compatible avec les groupements.");
                                player.sendMessage(ChatColor.GRAY + "Seuls les types Particulier et Professionnel peuvent être groupés.");

                                // Si la parcelle était en vente/location via le groupe, annuler
                                plot.setForSale(false);
                                plot.setForRent(false);
                            }
                        }

                        player.sendMessage(ChatColor.GREEN + "Type de parcelle changé en " + selectedType.getDisplayName());
                        player.closeInventory();
                        openPlotMenu(player);
                    }
                }
            }
            return;
        }

        // Gestion du menu principal de parcelle
        Chunk currentChunk = player.getLocation().getChunk();
        Plot plot = claimManager.getPlotAt(currentChunk);

        if (plot == null) {
            player.closeInventory();
            return;
        }

        String townName = claimManager.getClaimOwner(currentChunk);

        if (displayName.contains("Mettre en Vente")) {
            handlePutForSale(player, plot, townName);
        } else if (displayName.contains("Acheter")) {
            handleBuyPlot(player, plot, townName);
        } else if (displayName.contains("Annuler la Vente")) {
            handleCancelSale(player, plot);
        } else if (displayName.contains("Mettre en Location")) {
            handlePutForRent(player, plot, townName);
        } else if (displayName.contains("Louer cette Parcelle")) {
            handleRentPlot(player, plot, townName);
        } else if (displayName.contains("Recharger la Location")) {
            handleRechargePlotRent(player, plot, townName);
        } else if (displayName.contains("Annuler la Location")) {
            handleCancelRent(player, plot);
        } else if (displayName.contains("Changer le Type")) {
            handleChangePlotType(player, plot, townName);
        } else if (displayName.contains("Fermer")) {
            player.closeInventory();
        }
    }

    private void handlePutForSale(Player player, Plot plot, String townName) {
        player.closeInventory();

        // Vérifier que la parcelle est de type PARTICULIER ou PROFESSIONNEL
        if (plot.getType() != PlotType.PARTICULIER && plot.getType() != PlotType.PROFESSIONNEL) {
            player.sendMessage(ChatColor.RED + "Seules les parcelles privées (Particulier/Professionnel) peuvent être mises en vente !");
            player.sendMessage(ChatColor.GRAY + "Type actuel: " + plot.getType().getDisplayName());
            return;
        }

        player.sendMessage(ChatColor.GREEN + "Entrez le prix de vente dans le chat:");
        player.sendMessage(ChatColor.YELLOW + "(Tapez 'annuler' pour abandonner)");

        pendingActions.put(player.getUniqueId(),
            new PlotActionContext(ActionType.SET_SALE_PRICE, plot, townName));
    }

    private void handleBuyPlot(Player player, Plot plot, String townName) {
        player.closeInventory();
        if (economyManager.buyPlot(townName, plot, player)) {
            player.sendMessage(ChatColor.GREEN + "Félicitations ! Vous êtes maintenant propriétaire de cette parcelle.");
        } else {
            player.sendMessage(ChatColor.RED + "Impossible d'acheter cette parcelle.");
        }
    }

    private void handleCancelSale(Player player, Plot plot) {
        player.closeInventory();
        plot.setForSale(false);
        plot.setSalePrice(0);
        player.sendMessage(ChatColor.YELLOW + "La vente de la parcelle a été annulée.");
    }

    private void handlePutForRent(Player player, Plot plot, String townName) {
        player.closeInventory();

        // Vérifier que la parcelle est de type PARTICULIER ou PROFESSIONNEL
        if (plot.getType() != PlotType.PARTICULIER && plot.getType() != PlotType.PROFESSIONNEL) {
            player.sendMessage(ChatColor.RED + "Seules les parcelles privées (Particulier/Professionnel) peuvent être mises en location !");
            player.sendMessage(ChatColor.GRAY + "Type actuel: " + plot.getType().getDisplayName());
            return;
        }

        player.sendMessage(ChatColor.GREEN + "Entrez le prix de location PAR JOUR dans le chat:");
        player.sendMessage(ChatColor.GRAY + "Exemple: 10 pour 10€/jour");
        player.sendMessage(ChatColor.YELLOW + "(Tapez 'annuler' pour abandonner)");

        pendingActions.put(player.getUniqueId(),
            new PlotActionContext(ActionType.SET_RENT_PRICE, plot, townName));
    }

    private void handleRentPlot(Player player, Plot plot, String townName) {
        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "Combien de jours souhaitez-vous louer ?");
        player.sendMessage(ChatColor.GRAY + "Prix: " + String.format("%.2f€/jour", plot.getRentPricePerDay()));
        player.sendMessage(ChatColor.GRAY + "Maximum: 30 jours");
        player.sendMessage(ChatColor.YELLOW + "Entrez le nombre de jours (ou 'annuler'):");

        pendingActions.put(player.getUniqueId(),
            new PlotActionContext(ActionType.RENT_PLOT_DAYS, plot, townName));
    }

    private void handleRechargePlotRent(Player player, Plot plot, String townName) {
        player.closeInventory();
        int currentDays = plot.getRentDaysRemaining();
        int maxCanAdd = 30 - currentDays;

        if (maxCanAdd <= 0) {
            player.sendMessage(ChatColor.YELLOW + "Votre solde est déjà au maximum (30 jours).");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "Combien de jours souhaitez-vous ajouter ?");
        player.sendMessage(ChatColor.GRAY + "Solde actuel: " + ChatColor.YELLOW + currentDays + "/30 jours");
        player.sendMessage(ChatColor.GRAY + "Prix: " + String.format("%.2f€/jour", plot.getRentPricePerDay()));
        player.sendMessage(ChatColor.GRAY + "Maximum: " + maxCanAdd + " jours");
        player.sendMessage(ChatColor.YELLOW + "Entrez le nombre de jours (ou 'annuler'):");

        pendingActions.put(player.getUniqueId(),
            new PlotActionContext(ActionType.RECHARGE_RENT_DAYS, plot, townName));
    }

    private void handleCancelRent(Player player, Plot plot) {
        player.closeInventory();
        plot.setForRent(false);
        plot.setRent(0, 0);
        player.sendMessage(ChatColor.YELLOW + "La location de la parcelle a été annulée.");
    }

    private void handleChangePlotType(Player player, Plot plot, String townName) {
        player.closeInventory();
        openPlotTypeSelectionMenu(player, plot, townName);
    }

    private void openPlotTypeSelectionMenu(Player player, Plot plot, String townName) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.BLUE + "Changer le type");

        int slot = 10;
        for (PlotType type : PlotType.values()) {
            ItemStack typeItem = new ItemStack(type.getIcon());
            ItemMeta meta = typeItem.getItemMeta();

            if (type == plot.getType()) {
                meta.setDisplayName(ChatColor.GREEN + "✓ " + type.getDisplayName() + " (Actuel)");
            } else {
                meta.setDisplayName(ChatColor.YELLOW + type.getDisplayName());
            }

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Taxe journalière: " + ChatColor.GOLD + type.getDailyTax() + "€/jour");

            if (type.canBeSold()) {
                lore.add(ChatColor.GREEN + "✓ Peut être vendu");
            } else {
                lore.add(ChatColor.RED + "✗ Ne peut pas être vendu");
            }

            if (type.requiresCompany()) {
                lore.add(ChatColor.YELLOW + "⚠ Nécessite une entreprise");
            }

            lore.add("");

            if (type == plot.getType()) {
                lore.add(ChatColor.GRAY + "Type actuel");
            } else {
                lore.add(ChatColor.YELLOW + "Cliquez pour changer");
            }

            meta.setLore(lore);
            typeItem.setItemMeta(meta);
            inv.setItem(slot++, typeItem);
        }

        // Bouton retour
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(22, backItem);

        player.openInventory(inv);

        // Stocker le plot dans les métadonnées pour le récupérer au clic
        player.setMetadata("plot_type_change_town", new org.bukkit.metadata.FixedMetadataValue(plugin, townName));
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PlotActionContext context = pendingActions.get(player.getUniqueId());

        if (context == null) {
            return;
        }

        event.setCancelled(true);
        String input = event.getMessage().trim();

        if (input.equalsIgnoreCase("annuler")) {
            pendingActions.remove(player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "Action annulée.");
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            processPlayerInput(player, context, input);
            pendingActions.remove(player.getUniqueId());
        });
    }

    private void processPlayerInput(Player player, PlotActionContext context, String input) {
        try {
            if (context.actionType == ActionType.SET_SALE_PRICE) {
                double price = Double.parseDouble(input);
                if (price <= 0) {
                    player.sendMessage(ChatColor.RED + "Le prix doit être positif.");
                    return;
                }

                if (economyManager.putPlotForSale(context.townName, context.plot, price, player)) {
                    player.sendMessage(ChatColor.GREEN + "Parcelle mise en vente pour " + price + "€ !");
                } else {
                    player.sendMessage(ChatColor.RED + "Impossible de mettre la parcelle en vente.");
                }

            } else if (context.actionType == ActionType.SET_RENT_PRICE) {
                double pricePerDay = Double.parseDouble(input);
                if (pricePerDay <= 0) {
                    player.sendMessage(ChatColor.RED + "Le prix doit être positif.");
                    return;
                }

                player.sendMessage(ChatColor.GREEN + "Prix de location: " + pricePerDay + "€/jour");
                player.sendMessage(ChatColor.GREEN + "Maintenant, entrez la durée en jours:");
                player.sendMessage(ChatColor.GRAY + "Exemple: 7 pour une semaine");

                pendingActions.put(player.getUniqueId(),
                    new PlotActionContext(ActionType.SET_RENT_DURATION, context.plot, context.townName, pricePerDay));

            } else if (context.actionType == ActionType.SET_RENT_DURATION) {
                int days = Integer.parseInt(input);
                if (days <= 0 || days > 365) {
                    player.sendMessage(ChatColor.RED + "La durée doit être entre 1 et 365 jours.");
                    return;
                }

                // context.tempPrice contient le prix PAR JOUR
                double pricePerDay = context.tempPrice;
                double totalPrice = pricePerDay * days;

                if (economyManager.putPlotForRent(context.townName, context.plot,
                    totalPrice, days, player)) {
                    player.sendMessage(ChatColor.GREEN + "Parcelle mise en location !");
                    player.sendMessage(ChatColor.YELLOW + "Prix: " + String.format("%.2f€/jour", pricePerDay) +
                        ChatColor.GRAY + " × " + days + " jours = " + ChatColor.GOLD + String.format("%.2f€ total", totalPrice));
                } else {
                    player.sendMessage(ChatColor.RED + "Impossible de mettre la parcelle en location.");
                }

            } else if (context.actionType == ActionType.RENT_PLOT_DAYS) {
                int days = Integer.parseInt(input);
                if (days <= 0 || days > 30) {
                    player.sendMessage(ChatColor.RED + "Le nombre de jours doit être entre 1 et 30.");
                    return;
                }

                if (economyManager.rentPlot(context.townName, context.plot, player, days)) {
                    player.sendMessage(ChatColor.GREEN + "Location réussie !");
                } else {
                    player.sendMessage(ChatColor.RED + "Impossible de louer cette parcelle.");
                }

            } else if (context.actionType == ActionType.RECHARGE_RENT_DAYS) {
                int days = Integer.parseInt(input);
                if (days <= 0) {
                    player.sendMessage(ChatColor.RED + "Le nombre de jours doit être positif.");
                    return;
                }

                if (economyManager.rechargePlotRent(context.townName, context.plot, player, days)) {
                    player.sendMessage(ChatColor.GREEN + "Recharge effectuée avec succès !");
                } else {
                    player.sendMessage(ChatColor.RED + "Impossible de recharger la location.");
                }
            }
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Veuillez entrer un nombre valide.");
        }
    }

    private enum ActionType {
        SET_SALE_PRICE,
        SET_RENT_PRICE,
        SET_RENT_DURATION,
        RENT_PLOT_DAYS,
        RECHARGE_RENT_DAYS
    }

    private static class PlotActionContext {
        final ActionType actionType;
        final Plot plot;
        final String townName;
        final double tempPrice;

        PlotActionContext(ActionType actionType, Plot plot, String townName) {
            this(actionType, plot, townName, 0);
        }

        PlotActionContext(ActionType actionType, Plot plot, String townName, double tempPrice) {
            this.actionType = actionType;
            this.plot = plot;
            this.townName = townName;
            this.tempPrice = tempPrice;
        }
    }
}
