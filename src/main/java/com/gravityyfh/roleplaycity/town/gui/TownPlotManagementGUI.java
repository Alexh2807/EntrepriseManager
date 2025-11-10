package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.gui.NavigationManager;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.PlotType;
import com.gravityyfh.roleplaycity.town.data.MunicipalSubType;
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
    private static final String SUBTYPE_MENU_TITLE = ChatColor.DARK_PURPLE + "⚙ Sous-Type Municipal";

    // Système de saisie de prix
    private final Map<UUID, PlotActionContext> pendingActions;

    // FIX UX P2.7: Stockage du Plot actuellement affiché dans le menu
    private final Map<UUID, Plot> currentMenuPlots;

    public TownPlotManagementGUI(RoleplayCity plugin, TownManager townManager,
                                 ClaimManager claimManager, TownEconomyManager economyManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.claimManager = claimManager;
        this.economyManager = economyManager;
        this.pendingActions = new HashMap<>();
        this.currentMenuPlots = new HashMap<>();
    }

    /**
     * Ouvre le menu de gestion pour le chunk actuel du joueur
     */
    public void openPlotMenu(Player player) {
        Chunk currentChunk = player.getLocation().getChunk();
        Plot plot = claimManager.getPlotAt(currentChunk);

        if (plot == null) {
            player.sendMessage(ChatColor.RED + "Cette parcelle n'appartient à aucune ville.");
            return;
        }

        openPlotMenu(player, plot);
    }

    /**
     * FIX UX P2.1: Ouvre le menu de gestion pour un plot spécifique (accès distant depuis "Mes Propriétés")
     * Permet de gérer un terrain sans être physiquement dessus
     */
    public void openPlotMenu(Player player, Plot plot) {
        String townName = plot.getTownName();
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
            // Si terrain PROFESSIONNEL avec entreprise : afficher entreprise
            if (plot.getType() == PlotType.PROFESSIONNEL && plot.getCompanySiret() != null) {
                com.gravityyfh.roleplaycity.EntrepriseManagerLogic.Entreprise ownerCompany = plugin.getCompanyPlotManager()
                    .getCompanyBySiret(plot.getCompanySiret());
                if (ownerCompany != null) {
                    infoLore.add(ChatColor.GRAY + "Entreprise: " + ChatColor.YELLOW + ownerCompany.getNom() + ChatColor.GRAY + " (" + ownerCompany.getType() + ")");
                } else {
                    infoLore.add(ChatColor.GRAY + "Propriétaire: " + ChatColor.YELLOW + plot.getOwnerName());
                }
            } else {
                // Terrain PARTICULIER
                infoLore.add(ChatColor.GRAY + "Propriétaire: " + ChatColor.YELLOW + plot.getOwnerName());
            }
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
            inv.setItem(14, typeItem);
        }

        // Changer le sous-type municipal (uniquement si MUNICIPAL)
        if ((role == TownRole.MAIRE || role == TownRole.ADJOINT) && plot.getType() == PlotType.MUNICIPAL) {
            ItemStack subtypeItem = new ItemStack(plot.getMunicipalSubType().getIcon());
            ItemMeta subtypeMeta = subtypeItem.getItemMeta();
            subtypeMeta.setDisplayName(ChatColor.DARK_PURPLE + "Sous-Type Municipal");
            List<String> subtypeLore = new ArrayList<>();
            subtypeLore.add(ChatColor.GRAY + "Actuel: " + ChatColor.WHITE + plot.getMunicipalSubType().getDisplayName());
            subtypeLore.add("");
            subtypeLore.add(ChatColor.GRAY + "Définit la fonction de");
            subtypeLore.add(ChatColor.GRAY + "ce bâtiment municipal");
            subtypeLore.add("");
            subtypeLore.add(ChatColor.YELLOW + "Cliquez pour changer");
            subtypeMeta.setLore(subtypeLore);
            subtypeItem.setItemMeta(subtypeMeta);
            inv.setItem(16, subtypeItem);
        }

        // DÉGROUPER - Séparer un groupe de terrains (slot 18)
        if ((role == TownRole.MAIRE || role == TownRole.ADJOINT) && plot.isGrouped()) {
            ItemStack ungroupItem = new ItemStack(Material.SHEARS);
            ItemMeta ungroupMeta = ungroupItem.getItemMeta();
            ungroupMeta.setDisplayName(ChatColor.RED + "✂ Dégrouper ce Terrain");
            List<String> ungroupLore = new ArrayList<>();
            ungroupLore.add(ChatColor.GRAY + "Séparer ce groupe en");
            ungroupLore.add(ChatColor.GRAY + "parcelles individuelles");
            ungroupLore.add("");
            ungroupLore.add(ChatColor.LIGHT_PURPLE + "Groupe: " + ChatColor.WHITE + plot.getGroupName());
            ungroupLore.add(ChatColor.YELLOW + "Parcelles: " + ChatColor.WHITE + plot.getChunks().size());
            ungroupLore.add("");
            ungroupLore.add(ChatColor.RED + "Cliquez pour dégrouper");
            ungroupMeta.setLore(ungroupLore);
            ungroupItem.setItemMeta(ungroupMeta);
            inv.setItem(18, ungroupItem);
        }

        // UNCLAIM - Retourner la parcelle à la ville (slot 22)
        // Conditions : propriétaire, pas loué, type PARTICULIER ou PROFESSIONNEL
        if (plot.getOwnerUuid() != null &&
            plot.getOwnerUuid().equals(player.getUniqueId()) &&
            plot.getRenterUuid() == null &&
            (plot.getType() == PlotType.PARTICULIER || plot.getType() == PlotType.PROFESSIONNEL)) {

            ItemStack unclaimItem = new ItemStack(Material.BARRIER);
            ItemMeta unclaimMeta = unclaimItem.getItemMeta();
            unclaimMeta.setDisplayName(ChatColor.DARK_RED + "Retourner à la Ville");
            List<String> unclaimLore = new ArrayList<>();
            unclaimLore.add(ChatColor.GRAY + "Rendre cette parcelle à la ville");
            unclaimLore.add("");
            unclaimLore.add(ChatColor.RED + "Attention: Aucun remboursement");
            unclaimLore.add("");
            unclaimLore.add(ChatColor.YELLOW + "Cliquez pour UNCLAIM");
            unclaimMeta.setLore(unclaimLore);
            unclaimItem.setItemMeta(unclaimMeta);
            inv.setItem(22, unclaimItem);
        }

        // Retour à Mes Propriétés
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "← Retour à Mes Propriétés");
        List<String> backLore = new ArrayList<>();
        backLore.add(ChatColor.GRAY + "Voir tous vos terrains");
        backMeta.setLore(backLore);
        backItem.setItemMeta(backMeta);
        inv.setItem(26, backItem);

        // NOUVEAU : Bouton "Placer une boîte aux lettres" (slot 20)
        // Disponible uniquement pour le propriétaire ou locataire
        if (plot.getOwnerUuid() != null &&
            (plot.getOwnerUuid().equals(player.getUniqueId()) || plot.isRentedBy(player.getUniqueId()))) {

            ItemStack mailboxItem = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta mailboxMeta = mailboxItem.getItemMeta();
            mailboxMeta.setDisplayName(ChatColor.GOLD + "📮 Placer une Boîte aux Lettres");
            List<String> mailboxLore = new ArrayList<>();
            mailboxLore.add(ChatColor.GRAY + "Placez une boîte aux lettres");
            mailboxLore.add(ChatColor.GRAY + "pour recevoir du courrier");
            mailboxLore.add("");
            mailboxLore.add(ChatColor.YELLOW + "Cliquez pour choisir votre");
            mailboxLore.add(ChatColor.YELLOW + "modèle de boîte aux lettres");
            mailboxMeta.setLore(mailboxLore);
            mailboxItem.setItemMeta(mailboxMeta);
            inv.setItem(20, mailboxItem);
        }

        // FIX UX P2.7: Stocker le Plot affiché pour référence ultérieure
        currentMenuPlots.put(player.getUniqueId(), plot);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.equals(PLOT_MENU_TITLE) && !title.contains("Changer le type") && !title.equals(SUBTYPE_MENU_TITLE)) {
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
                // FIX UX P2.7: Utiliser le Plot stocké
                Plot plot = currentMenuPlots.get(player.getUniqueId());
                if (plot != null) {
                    openPlotMenu(player, plot);
                }
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
                    // FIX UX P2.7: Utiliser le Plot stocké
                    Plot plot = currentMenuPlots.get(player.getUniqueId());
                    String townName = player.getMetadata("plot_type_change_town").get(0).asString();
                    player.removeMetadata("plot_type_change_town", plugin);

                    if (plot != null) {
                        PlotType oldType = plot.getType();

                        // VALIDATION : Un terrain avec propriétaire ne peut pas devenir MUNICIPAL ou PUBLIC
                        // VÉRIFICATION 1 : Parcelle avec propriétaire ne peut pas devenir MUNICIPAL/PUBLIC
                        if (plot.getOwnerUuid() != null &&
                            (selectedType == PlotType.MUNICIPAL || selectedType == PlotType.PUBLIC)) {
                            player.sendMessage(ChatColor.RED + "✗ Impossible de changer ce terrain en " + selectedType.getDisplayName() + " !");
                            player.sendMessage(ChatColor.YELLOW + "Raison : Cette parcelle a un propriétaire (" + plot.getOwnerName() + ")");
                            player.sendMessage(ChatColor.GRAY + "Vous devez d'abord retirer le propriétaire ou racheter le terrain à la ville.");
                            player.closeInventory();
                            return;
                        }

                        // VÉRIFICATION 2 : Parcelle groupée ne peut pas changer vers MUNICIPAL/PUBLIC
                        if (plot.isGrouped() &&
                            (selectedType == PlotType.MUNICIPAL || selectedType == PlotType.PUBLIC)) {
                            player.sendMessage(ChatColor.RED + "✗ Impossible de changer ce terrain en " + selectedType.getDisplayName() + " !");
                            player.sendMessage(ChatColor.YELLOW + "Raison : Cette parcelle fait partie du groupe \"" + plot.getGroupName() + "\"");
                            player.sendMessage(ChatColor.GRAY + "Seuls les terrains PARTICULIER et PROFESSIONNEL peuvent être groupés.");
                            player.sendMessage(ChatColor.AQUA + "→ Vous devez d'abord dégrouper ce terrain avant de changer son type.");
                            player.closeInventory();
                            return;
                        }

                        // Gérer le numéro de terrain lors du changement de type
                        plot.setType(selectedType);

                        // Réinitialiser le sous-type municipal si on quitte le type MUNICIPAL
                        if (selectedType != PlotType.MUNICIPAL && plot.getMunicipalSubType() != MunicipalSubType.NONE) {
                            plot.setMunicipalSubType(MunicipalSubType.NONE);
                        }

                        // Si on passe à PARTICULIER, PROFESSIONNEL ou MUNICIPAL depuis PUBLIC, attribuer un numéro
                        boolean needsNumber = (selectedType == PlotType.PARTICULIER ||
                                             selectedType == PlotType.PROFESSIONNEL ||
                                             selectedType == PlotType.MUNICIPAL);
                        boolean hadNoNumber = (oldType == PlotType.PUBLIC || plot.getPlotNumber() == null);

                        if (needsNumber && hadNoNumber) {
                            // Récupérer la ville et générer un numéro unique
                            Town town = plugin.getTownManager().getTown(plot.getTownName());
                            if (town != null) {
                                String plotNumber = town.generateUniquePlotNumber();
                                if (plotNumber != null) {
                                    plot.setPlotNumber(plotNumber);
                                    player.sendMessage(ChatColor.GREEN + "Type de parcelle changé en " + selectedType.getDisplayName());
                                    player.sendMessage(ChatColor.GOLD + "→ Numéro de terrain attribué : " + ChatColor.BOLD + plotNumber);
                                } else {
                                    player.sendMessage(ChatColor.RED + "✗ Impossible d'attribuer un numéro de terrain !");
                                    player.sendMessage(ChatColor.YELLOW + "Tous les numéros (001-999) sont déjà utilisés dans cette ville.");
                                    // Revenir à l'ancien type
                                    plot.setType(oldType);
                                    player.closeInventory();
                                    return;
                                }
                            }
                        } else if (!needsNumber && plot.getPlotNumber() != null) {
                            // Si on passe à PUBLIC, retirer le numéro
                            plot.setPlotNumber(null);
                            player.sendMessage(ChatColor.GREEN + "Type de parcelle changé en " + selectedType.getDisplayName());
                            player.sendMessage(ChatColor.GRAY + "Le numéro de terrain a été retiré.");
                        } else {
                            player.sendMessage(ChatColor.GREEN + "Type de parcelle changé en " + selectedType.getDisplayName());
                        }

                        // Sauvegarder immédiatement
                        plugin.getTownManager().saveTownsNow();

                        player.closeInventory();
                        // FIX UX P2.7: Utiliser le plot stocké
                        openPlotMenu(player, plot);
                    }
                }
            }
            return;
        }

        // Gestion du menu de changement de sous-type municipal
        if (title.equals(SUBTYPE_MENU_TITLE)) {
            if (displayName.contains("Retour")) {
                player.closeInventory();
                Plot plot = currentMenuPlots.get(player.getUniqueId());
                if (plot != null) {
                    openPlotMenu(player, plot);
                }
            } else if (!displayName.contains("Terrain Municipal")) {
                // Chercher le sous-type sélectionné
                MunicipalSubType selectedSubtype = null;
                for (MunicipalSubType subtype : MunicipalSubType.values()) {
                    if (displayName.contains(ChatColor.stripColor(subtype.getDisplayName()))) {
                        selectedSubtype = subtype;
                        break;
                    }
                }

                if (selectedSubtype != null && player.hasMetadata("plot_subtype_change_town")) {
                    Plot plot = currentMenuPlots.get(player.getUniqueId());
                    String townName = player.getMetadata("plot_subtype_change_town").get(0).asString();
                    player.removeMetadata("plot_subtype_change_town", plugin);

                    if (plot != null) {
                        MunicipalSubType oldSubtype = plot.getMunicipalSubType();

                        // Appliquer le changement
                        plot.setMunicipalSubType(selectedSubtype);

                        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                        player.sendMessage("§a✔ §lSOUS-TYPE MODIFIÉ");
                        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                        player.sendMessage("§7Ancien: §f" + oldSubtype.getDisplayName());
                        player.sendMessage("§7Nouveau: §d" + selectedSubtype.getDisplayName());
                        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

                        plugin.getTownManager().saveTownsNow();

                        player.closeInventory();
                        openPlotMenu(player, plot);
                    }
                }
            }
            return;
        }

        // FIX UX P2.7: Utiliser le Plot stocké au lieu de player.getLocation()
        Plot plot = currentMenuPlots.get(player.getUniqueId());

        if (plot == null) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "Erreur: Contexte du menu perdu. Réouvrez le menu.");
            return;
        }

        String townName = plot.getTownName();

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
        } else if (displayName.contains("Sous-Type Municipal")) {
            handleChangeMunicipalSubtype(player, plot, townName);
        } else if (displayName.contains("Dégrouper ce Terrain")) {
            handleUngroupPlot(player, plot, townName);
        } else if (displayName.contains("Retourner à la Ville")) {
            handleUnclaimPlot(player, plot, townName);
        } else if (displayName.contains("Retour à Mes Propriétés")) {
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + "Utilisez " + ChatColor.WHITE + "/ville" +
                ChatColor.YELLOW + " pour accéder à vos propriétés");
        } else if (displayName.contains("Placer une Boîte aux Lettres")) {
            handlePlaceMailbox(player, plot);
        }
    }

    private void handlePlaceMailbox(Player player, Plot plot) {
        player.closeInventory();

        // Vérifier que le joueur est bien propriétaire ou locataire
        if (plot.getOwnerUuid() == null ||
            (!plot.getOwnerUuid().equals(player.getUniqueId()) && !plot.isRentedBy(player.getUniqueId()))) {
            player.sendMessage(ChatColor.RED + "Vous devez être propriétaire ou locataire pour placer une boîte aux lettres.");
            return;
        }

        // Ouvrir le GUI de sélection de mailbox
        plugin.getMailboxPlacementGUI().openMailboxSelectionMenu(player, plot);
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

        // Sauvegarder immédiatement
        plugin.getTownManager().saveTownsNow();
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

        // Sauvegarder immédiatement
        plugin.getTownManager().saveTownsNow();
    }

    private void handleChangePlotType(Player player, Plot plot, String townName) {
        player.closeInventory();
        openPlotTypeSelectionMenu(player, plot, townName);
    }

    private void handleUngroupPlot(Player player, Plot plot, String townName) {
        player.closeInventory();
        plugin.getPlotGroupManagementGUI().ungroupPlot(player, plot, townName);
    }

    private void handleUnclaimPlot(Player player, Plot plot, String townName) {
        player.closeInventory();

        // SÉCURITÉ : Vérifier que la parcelle n'est pas louée
        if (plot.getRenterUuid() != null) {
            NavigationManager.sendError(player, "Impossible de retourner cette parcelle à la ville : elle est actuellement louée !");
            return;
        }

        // SÉCURITÉ : Vérifier que le joueur est bien le propriétaire
        if (plot.getOwnerUuid() == null || !plot.getOwnerUuid().equals(player.getUniqueId())) {
            NavigationManager.sendError(player, "Vous n'êtes pas le propriétaire de cette parcelle !");
            return;
        }

        // SÉCURITÉ : Vérifier que c'est bien un terrain PARTICULIER ou PROFESSIONNEL
        if (plot.getType() != PlotType.PARTICULIER && plot.getType() != PlotType.PROFESSIONNEL) {
            NavigationManager.sendError(player, "Seules les parcelles de type Particulier ou Professionnel peuvent être retournées à la ville !");
            return;
        }

        // FIX CRITIQUE: Nettoyer TOUTES les données du terrain (propriétaire, entreprise, dettes)
        townManager.clearPlotOwnership(plot);

        // Nettoyer les paramètres de vente/location
        plot.setForRent(false);
        plot.clearRenter();

        // Retirer la parcelle de la vente (elle appartient maintenant à la ville mais n'est pas en vente)
        plot.setForSale(false);
        plot.setSalePrice(0.0);

        // Sauvegarder
        townManager.saveTownsNow();

        // Message de confirmation
        NavigationManager.sendStyledMessage(player, "PARCELLE RETOURNÉE À LA VILLE", Arrays.asList(
            "+La parcelle a été retournée à la ville",
            "",
            "Type: " + plot.getType().getDisplayName(),
            "Position: " + plot.getCoordinates(),
            "",
            "*La parcelle appartient maintenant à la ville",
            "*Elle n'est pas mise en vente automatiquement"
        ));
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

        // FIX UX P2.7: Stocker le Plot affiché pour référence ultérieure
        currentMenuPlots.put(player.getUniqueId(), plot);

        player.openInventory(inv);

        // Stocker le plot dans les métadonnées pour le récupérer au clic
        player.setMetadata("plot_type_change_town", new org.bukkit.metadata.FixedMetadataValue(plugin, townName));
    }

    private void handleChangeMunicipalSubtype(Player player, Plot plot, String townName) {
        player.closeInventory();
        openMunicipalSubtypeSelectionMenu(player, plot, townName);
    }

    private void openMunicipalSubtypeSelectionMenu(Player player, Plot plot, String townName) {
        Inventory inv = Bukkit.createInventory(null, 27, SUBTYPE_MENU_TITLE);

        // Informations du terrain
        ItemStack infoItem = new ItemStack(Material.MAP);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(ChatColor.GOLD + "Terrain Municipal");
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.GRAY + "Position: " + ChatColor.WHITE + plot.getCoordinates());
        infoLore.add(ChatColor.GRAY + "Actuel: " + ChatColor.AQUA + plot.getMunicipalSubType().getDisplayName());
        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);
        inv.setItem(4, infoItem);

        // Afficher tous les sous-types municipaux
        int slot = 10;
        for (MunicipalSubType subtype : MunicipalSubType.values()) {
            ItemStack subtypeItem = new ItemStack(subtype.getIcon());
            ItemMeta subtypeMeta = subtypeItem.getItemMeta();
            subtypeMeta.setDisplayName(ChatColor.LIGHT_PURPLE + subtype.getDisplayName());

            List<String> subtypeLore = new ArrayList<>();
            subtypeLore.add("");

            // Description selon le type
            switch (subtype) {
                case NONE:
                    subtypeLore.add(ChatColor.GRAY + "Bâtiment sans fonction");
                    subtypeLore.add(ChatColor.GRAY + "particulière");
                    break;
                case MAIRIE:
                    subtypeLore.add(ChatColor.GRAY + "Bâtiment administratif");
                    subtypeLore.add(ChatColor.GRAY + "Accès: Maire + Adjoint");
                    break;
                case COMMISSARIAT:
                    subtypeLore.add(ChatColor.GRAY + "Poste de police");
                    subtypeLore.add(ChatColor.GRAY + "Accès: Policiers");
                    break;
                case TRIBUNAL:
                    subtypeLore.add(ChatColor.GRAY + "Palais de justice");
                    subtypeLore.add(ChatColor.GRAY + "Accès: Juges");
                    subtypeLore.add(ChatColor.YELLOW + "Requis pour juger!");
                    break;
            }

            subtypeLore.add("");
            if (plot.getMunicipalSubType() == subtype) {
                subtypeLore.add(ChatColor.GREEN + "✔ Actuellement sélectionné");
            } else {
                subtypeLore.add(ChatColor.YELLOW + "Cliquez pour sélectionner");
            }

            subtypeMeta.setLore(subtypeLore);
            subtypeItem.setItemMeta(subtypeMeta);
            inv.setItem(slot++, subtypeItem);
        }

        // Bouton retour
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(22, backItem);

        // Stocker le Plot affiché
        currentMenuPlots.put(player.getUniqueId(), plot);

        player.openInventory(inv);
        player.setMetadata("plot_subtype_change_town", new org.bukkit.metadata.FixedMetadataValue(plugin, townName));
    }

    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        String title = event.getView().getTitle();
        if (title.equals(PLOT_MENU_TITLE) || title.contains("Changer le type") || title.equals(SUBTYPE_MENU_TITLE)) {
            // FIX UX P2.7: Nettoyer le Plot stocké quand le menu est fermé
            currentMenuPlots.remove(player.getUniqueId());
        }
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

                // Mettre en location directement avec le prix par jour
                if (economyManager.putPlotForRent(context.townName, context.plot, pricePerDay, player)) {
                    player.sendMessage(ChatColor.GREEN + "Parcelle mise en location !");
                    player.sendMessage(ChatColor.YELLOW + "Prix: " + ChatColor.GOLD + String.format("%.2f€/jour", pricePerDay));
                    player.sendMessage(ChatColor.GRAY + "Les locataires pourront louer jusqu'à 30 jours");
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
