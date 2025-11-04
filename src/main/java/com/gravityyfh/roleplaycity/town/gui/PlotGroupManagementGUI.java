package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.*;
import com.gravityyfh.roleplaycity.town.manager.ClaimManager;
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

/**
 * GUI pour gérer l'assemblage et le désassemblage de groupes de parcelles
 */
public class PlotGroupManagementGUI implements Listener {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final ClaimManager claimManager;

    // Sessions de sélection de parcelles
    private final Map<UUID, GroupCreationSession> groupSessions = new HashMap<>();

    // Sessions d'input chat
    private final Map<UUID, ChatInputContext> chatInputSessions = new HashMap<>();
    private final Map<UUID, SalePriceInputContext> salePriceInputSessions = new HashMap<>();

    public PlotGroupManagementGUI(RoleplayCity plugin, TownManager townManager, ClaimManager claimManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.claimManager = claimManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Ouvre le menu principal de gestion des groupes
     */
    public void openMainMenu(Player player, String townName) {
        Town town = townManager.getTown(townName);
        if (town == null) return;

        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_PURPLE + "Groupes de Parcelles");

        // Créer un nouveau groupe
        ItemStack createItem = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta createMeta = createItem.getItemMeta();
        createMeta.setDisplayName(ChatColor.GREEN + "Créer un Nouveau Groupe");
        List<String> createLore = new ArrayList<>();
        createLore.add(ChatColor.GRAY + "Assembler plusieurs parcelles");
        createLore.add(ChatColor.GRAY + "adjacentes en un seul terrain");
        createLore.add("");
        createLore.add(ChatColor.YELLOW + "Cliquez pour commencer");
        createMeta.setLore(createLore);
        createItem.setItemMeta(createMeta);
        inv.setItem(11, createItem);

        // Gérer les groupes existants
        ItemStack manageItem = new ItemStack(Material.CHEST);
        ItemMeta manageMeta = manageItem.getItemMeta();
        manageMeta.setDisplayName(ChatColor.AQUA + "Gérer les Groupes Existants");
        List<String> manageLore = new ArrayList<>();
        manageLore.add(ChatColor.GRAY + "Voir et gérer vos groupes");
        manageLore.add(ChatColor.GRAY + "de parcelles existants");
        manageLore.add("");
        manageLore.add(ChatColor.GOLD + "Groupes: " + town.getPlotGroups().size());
        manageLore.add("");
        manageLore.add(ChatColor.YELLOW + "Cliquez pour voir");
        manageMeta.setLore(manageLore);
        manageItem.setItemMeta(manageMeta);
        inv.setItem(15, manageItem);

        player.openInventory(inv);
    }

    /**
     * Ouvre le menu de sélection de parcelles pour créer un groupe
     */
    public void startGroupCreation(Player player, String townName) {
        Town town = townManager.getTown(townName);
        if (town == null) return;

        // Récupérer les parcelles du joueur
        List<Plot> playerPlots = town.getPlotsByOwner(player.getUniqueId());

        if (playerPlots.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Vous ne possédez aucune parcelle dans cette ville.");
            return;
        }

        // Filtrer les parcelles déjà dans un groupe
        List<Plot> availablePlots = playerPlots.stream()
                .filter(plot -> !town.isPlotInAnyGroup(plot))
                .toList();

        if (availablePlots.size() < 2) {
            player.sendMessage(ChatColor.RED + "Vous devez avoir au moins 2 parcelles disponibles pour créer un groupe.");
            player.sendMessage(ChatColor.YELLOW + "Parcelles disponibles: " + availablePlots.size());
            return;
        }

        // Créer une session
        GroupCreationSession session = new GroupCreationSession(townName);
        groupSessions.put(player.getUniqueId(), session);

        openPlotSelectionMenu(player, townName, 0);
    }

    /**
     * Menu de sélection des parcelles
     */
    private void openPlotSelectionMenu(Player player, String townName, int page) {
        Town town = townManager.getTown(townName);
        if (town == null) return;

        GroupCreationSession session = groupSessions.get(player.getUniqueId());
        if (session == null) return;

        List<Plot> playerPlots = town.getPlotsByOwner(player.getUniqueId()).stream()
                .filter(plot -> !town.isPlotInAnyGroup(plot))
                .toList();

        int maxPage = (playerPlots.size() - 1) / 21;
        page = Math.max(0, Math.min(page, maxPage));

        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_PURPLE + "Sélection de Parcelles (" + (page + 1) + "/" + (maxPage + 1) + ")");

        // Afficher les parcelles
        int startIndex = page * 21;
        int endIndex = Math.min(startIndex + 21, playerPlots.size());

        for (int i = startIndex; i < endIndex; i++) {
            Plot plot = playerPlots.get(i);
            boolean isSelected = session.selectedPlots.contains(plot);

            ItemStack item = new ItemStack(isSelected ? Material.LIME_STAINED_GLASS_PANE : Material.WHITE_STAINED_GLASS_PANE);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName((isSelected ? ChatColor.GREEN + "✓ " : ChatColor.WHITE) +
                    "Parcelle " + plot.getChunkX() + "," + plot.getChunkZ());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Monde: " + plot.getWorldName());
            lore.add(ChatColor.GRAY + "Type: " + plot.getType().getDisplayName());
            lore.add("");
            if (isSelected) {
                lore.add(ChatColor.GREEN + "Sélectionnée");
                lore.add(ChatColor.YELLOW + "Cliquez pour désélectionner");
            } else {
                lore.add(ChatColor.YELLOW + "Cliquez pour sélectionner");
            }
            meta.setLore(lore);
            item.setItemMeta(meta);

            inv.setItem(i - startIndex, item);
        }

        // Boutons de contrôle
        if (page > 0) {
            ItemStack prevItem = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevItem.getItemMeta();
            prevMeta.setDisplayName(ChatColor.YELLOW + "Page Précédente");
            prevItem.setItemMeta(prevMeta);
            inv.setItem(45, prevItem);
        }

        if (page < maxPage) {
            ItemStack nextItem = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextItem.getItemMeta();
            nextMeta.setDisplayName(ChatColor.YELLOW + "Page Suivante");
            nextItem.setItemMeta(nextMeta);
            inv.setItem(53, nextItem);
        }

        // Bouton de confirmation
        if (session.selectedPlots.size() >= 2) {
            ItemStack confirmItem = new ItemStack(Material.EMERALD_BLOCK);
            ItemMeta confirmMeta = confirmItem.getItemMeta();
            confirmMeta.setDisplayName(ChatColor.GREEN + "Créer le Groupe");
            List<String> confirmLore = new ArrayList<>();
            confirmLore.add(ChatColor.GRAY + "Parcelles sélectionnées: " + ChatColor.GOLD + session.selectedPlots.size());
            confirmLore.add("");
            confirmLore.add(ChatColor.YELLOW + "Cliquez pour confirmer");
            confirmMeta.setLore(confirmLore);
            confirmItem.setItemMeta(confirmMeta);
            inv.setItem(49, confirmItem);
        }

        // Bouton d'annulation
        ItemStack cancelItem = new ItemStack(Material.BARRIER);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        cancelMeta.setDisplayName(ChatColor.RED + "Annuler");
        cancelItem.setItemMeta(cancelMeta);
        inv.setItem(48, cancelItem);

        player.openInventory(inv);
    }

    /**
     * Ouvre le menu des groupes existants
     */
    private void openGroupListMenu(Player player, String townName, int page) {
        Town town = townManager.getTown(townName);
        if (town == null) return;

        List<PlotGroup> groups = new ArrayList<>(town.getPlotGroups().values());

        if (groups.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "Aucun groupe de parcelles dans cette ville.");
            return;
        }

        int maxPage = (groups.size() - 1) / 21;
        page = Math.max(0, Math.min(page, maxPage));

        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_PURPLE + "Groupes (" + (page + 1) + "/" + (maxPage + 1) + ")");

        int startIndex = page * 21;
        int endIndex = Math.min(startIndex + 21, groups.size());

        for (int i = startIndex; i < endIndex; i++) {
            PlotGroup group = groups.get(i);

            ItemStack item = new ItemStack(Material.MAP);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + group.getGroupName());
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Parcelles: " + ChatColor.AQUA + group.getPlotCount());
            if (group.getOwnerName() != null) {
                lore.add(ChatColor.GRAY + "Propriétaire: " + ChatColor.YELLOW + group.getOwnerName());
            }
            if (group.isForRent()) {
                lore.add(ChatColor.GREEN + "En location: " + String.format("%.2f€/jour", group.getRentPricePerDay()));
            }
            if (group.getRenterUuid() != null) {
                lore.add(ChatColor.AQUA + "Loué - " + group.getRentDaysRemaining() + " jours restants");
            }
            lore.add("");
            lore.add(ChatColor.YELLOW + "Cliquez pour gérer");
            meta.setLore(lore);
            item.setItemMeta(meta);

            inv.setItem(i - startIndex, item);
        }

        // Navigation
        if (page > 0) {
            ItemStack prevItem = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevItem.getItemMeta();
            prevMeta.setDisplayName(ChatColor.YELLOW + "Page Précédente");
            prevItem.setItemMeta(prevMeta);
            inv.setItem(45, prevItem);
        }

        if (page < maxPage) {
            ItemStack nextItem = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextItem.getItemMeta();
            nextMeta.setDisplayName(ChatColor.YELLOW + "Page Suivante");
            nextItem.setItemMeta(nextMeta);
            inv.setItem(53, nextItem);
        }

        player.openInventory(inv);
    }

    /**
     * Ouvre le menu de gestion d'un groupe spécifique
     */
    private void openGroupManagementMenu(Player player, String townName, PlotGroup group) {
        Town town = townManager.getTown(townName);
        if (town == null) return;

        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_PURPLE + group.getGroupName());

        boolean isOwner = group.isOwnedBy(player.getUniqueId());

        // Si propriétaire : options de vente/location
        if (isOwner) {
            // Mettre en vente
            ItemStack sellItem = new ItemStack(Material.GOLD_INGOT);
            ItemMeta sellMeta = sellItem.getItemMeta();
            sellMeta.setDisplayName(ChatColor.GREEN + "Mettre en Vente");
            List<String> sellLore = new ArrayList<>();
            sellLore.add(ChatColor.GRAY + "Vendre tout le groupe");
            sellLore.add(ChatColor.GRAY + "Parcelles: " + ChatColor.AQUA + group.getPlotCount());
            sellLore.add("");
            if (group.isForSale()) {
                sellLore.add(ChatColor.GREEN + "Prix actuel: " + String.format("%.2f€", group.getSalePrice()));
                sellLore.add(ChatColor.YELLOW + "Cliquez pour modifier");
            } else {
                sellLore.add(ChatColor.YELLOW + "Cliquez pour définir le prix");
            }
            sellMeta.setLore(sellLore);
            sellItem.setItemMeta(sellMeta);
            inv.setItem(11, sellItem);

            // Mettre en location
            ItemStack rentItem = new ItemStack(Material.EMERALD);
            ItemMeta rentMeta = rentItem.getItemMeta();
            rentMeta.setDisplayName(ChatColor.AQUA + "Mettre en Location");
            List<String> rentLore = new ArrayList<>();
            rentLore.add(ChatColor.GRAY + "Louer tout le groupe");
            rentLore.add(ChatColor.GRAY + "Parcelles: " + ChatColor.AQUA + group.getPlotCount());
            rentLore.add("");
            if (group.isForRent()) {
                rentLore.add(ChatColor.AQUA + "Prix actuel: " + String.format("%.2f€/jour", group.getRentPricePerDay()));
                rentLore.add(ChatColor.YELLOW + "Cliquez pour modifier");
            } else {
                rentLore.add(ChatColor.YELLOW + "Cliquez pour définir le prix");
            }
            rentMeta.setLore(rentLore);
            rentItem.setItemMeta(rentMeta);
            inv.setItem(12, rentItem);
        }

        // Si en vente et pas propriétaire : option d'achat
        if (group.isForSale() && !isOwner) {
            ItemStack buyItem = new ItemStack(Material.DIAMOND);
            ItemMeta buyMeta = buyItem.getItemMeta();
            buyMeta.setDisplayName(ChatColor.GREEN + "Acheter ce Groupe");
            List<String> buyLore = new ArrayList<>();
            buyLore.add(ChatColor.GRAY + "Propriétaire: " + ChatColor.YELLOW + group.getOwnerName());
            buyLore.add(ChatColor.GRAY + "Parcelles: " + ChatColor.AQUA + group.getPlotCount());
            buyLore.add(ChatColor.GRAY + "Surface totale: " + ChatColor.WHITE + (group.getPlotCount() * 256) + "m²");
            buyLore.add("");
            buyLore.add(ChatColor.GREEN + "Prix: " + ChatColor.GOLD + String.format("%.2f€", group.getSalePrice()));
            buyLore.add("");
            buyLore.add(ChatColor.YELLOW + "Cliquez pour acheter");
            buyMeta.setLore(buyLore);
            buyItem.setItemMeta(buyMeta);
            inv.setItem(13, buyItem);
        }

        // Désassembler le groupe (seulement propriétaire)
        if (isOwner) {
            ItemStack disbandItem = new ItemStack(Material.TNT);
            ItemMeta disbandMeta = disbandItem.getItemMeta();
            disbandMeta.setDisplayName(ChatColor.RED + "Désassembler le Groupe");
            List<String> disbandLore = new ArrayList<>();
            disbandLore.add(ChatColor.GRAY + "Séparer toutes les parcelles");
            disbandLore.add(ChatColor.GRAY + "Elles redeviendront indépendantes");
            disbandLore.add("");
            disbandLore.add(ChatColor.YELLOW + "Cliquez pour désassembler");
            disbandMeta.setLore(disbandLore);
            disbandItem.setItemMeta(disbandMeta);
            inv.setItem(15, disbandItem);
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.contains("Groupes") && !title.contains("Sélection de Parcelles")) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;

        // Menu principal
        if (title.equals(ChatColor.DARK_PURPLE + "Groupes de Parcelles")) {
            handleMainMenuClick(player, item);
        }
        // Menu de sélection de parcelles
        else if (title.contains("Sélection de Parcelles")) {
            handlePlotSelectionClick(player, item, title);
        }
        // Menu de liste des groupes
        else if (title.startsWith(ChatColor.DARK_PURPLE + "Groupes (")) {
            handleGroupListClick(player, item, title);
        }
        // Menu de gestion d'un groupe
        else {
            handleGroupManagementClick(player, item, title);
        }
    }

    private void handleMainMenuClick(Player player, ItemStack item) {
        String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName());

        if (displayName.contains("Créer un Nouveau Groupe")) {
            String townName = townManager.getPlayerTown(player.getUniqueId());
            if (townName != null) {
                player.closeInventory();
                // Utiliser le nouveau système interactif de groupement par clic droit
                plugin.getPlotGroupingListener().startGroupingSession(player, townName);
            } else {
                player.sendMessage(ChatColor.RED + "Vous devez être membre d'une ville.");
            }
        } else if (displayName.contains("Gérer les Groupes Existants")) {
            Chunk chunk = player.getLocation().getChunk();
            String townName = claimManager.getClaimOwner(player.getLocation());
            if (townName != null) {
                openGroupListMenu(player, townName, 0);
            }
        }
    }

    private void handlePlotSelectionClick(Player player, ItemStack item, String title) {
        GroupCreationSession session = groupSessions.get(player.getUniqueId());
        if (session == null) return;

        String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName());

        if (displayName.equals("Annuler")) {
            groupSessions.remove(player.getUniqueId());
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + "Création de groupe annulée.");
        } else if (displayName.equals("Créer le Groupe")) {
            if (session.selectedPlots.size() >= 2) {
                // Demander le nom du groupe
                player.closeInventory();
                player.sendMessage(ChatColor.GREEN + "Entrez le nom du groupe dans le chat:");
                ChatInputContext context = new ChatInputContext();
                context.townName = session.townName;
                context.selectedPlots = new ArrayList<>(session.selectedPlots);
                chatInputSessions.put(player.getUniqueId(), context);
                groupSessions.remove(player.getUniqueId());
            }
        } else if (displayName.startsWith("Parcelle ")) {
            // Toggle sélection
            String[] parts = displayName.replace("Parcelle ", "").split(",");
            if (parts.length == 2) {
                int chunkX = Integer.parseInt(parts[0]);
                int chunkZ = Integer.parseInt(parts[1]);

                Town town = townManager.getTown(session.townName);
                if (town != null) {
                    Plot plot = town.getPlotsByOwner(player.getUniqueId()).stream()
                            .filter(p -> p.getChunkX() == chunkX && p.getChunkZ() == chunkZ)
                            .findFirst()
                            .orElse(null);

                    if (plot != null) {
                        if (session.selectedPlots.contains(plot)) {
                            session.selectedPlots.remove(plot);
                        } else {
                            session.selectedPlots.add(plot);
                        }
                        openPlotSelectionMenu(player, session.townName, 0);
                    }
                }
            }
        }
    }

    private void handleGroupListClick(Player player, ItemStack item, String title) {
        if (item.getType() == Material.MAP) {
            String groupName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
            String townName = claimManager.getClaimOwner(player.getLocation());
            if (townName != null) {
                Town town = townManager.getTown(townName);
                if (town != null) {
                    PlotGroup group = town.getPlotGroups().values().stream()
                            .filter(g -> g.getGroupName().equals(groupName))
                            .findFirst()
                            .orElse(null);
                    if (group != null) {
                        openGroupManagementMenu(player, townName, group);
                    }
                }
            }
        }
    }

    private void handleGroupManagementClick(Player player, ItemStack item, String title) {
        String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        String groupName = ChatColor.stripColor(title);
        String townName = claimManager.getClaimOwner(player.getLocation());

        if (townName == null) return;
        Town town = townManager.getTown(townName);
        if (town == null) return;

        PlotGroup group = town.getPlotGroups().values().stream()
                .filter(g -> g.getGroupName().equals(groupName))
                .findFirst()
                .orElse(null);

        if (group == null) return;

        if (displayName.contains("Mettre en Vente")) {
            player.closeInventory();
            player.sendMessage(ChatColor.GREEN + "Entrez le prix de vente du groupe dans le chat:");
            player.sendMessage(ChatColor.YELLOW + "Surface totale: " + ChatColor.WHITE + (group.getPlotCount() * 256) + "m²");

            SalePriceInputContext context = new SalePriceInputContext();
            context.townName = townName;
            context.groupId = group.getGroupId();
            salePriceInputSessions.put(player.getUniqueId(), context);

        } else if (displayName.contains("Acheter ce Groupe")) {
            player.closeInventory();

            if (plugin.getTownEconomyManager().buyPlotGroup(townName, group, player)) {
                player.sendMessage(ChatColor.GREEN + "✓ Groupe de parcelles acheté avec succès !");
            } else {
                player.sendMessage(ChatColor.RED + "Impossible d'acheter ce groupe.");
            }

        } else if (displayName.contains("Désassembler le Groupe")) {
            town.removePlotGroup(group.getGroupId());

            // Sauvegarder immédiatement
            townManager.saveTownsNow();

            player.closeInventory();
            player.sendMessage(ChatColor.GREEN + "Groupe désassemblé avec succès !");
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // Gestion de création de groupe
        ChatInputContext context = chatInputSessions.get(player.getUniqueId());
        if (context != null) {
            event.setCancelled(true);
            chatInputSessions.remove(player.getUniqueId());

            String input = event.getMessage();

            Bukkit.getScheduler().runTask(plugin, () -> {
                Town town = townManager.getTown(context.townName);
                if (town == null) return;

                // Créer le groupe
                PlotGroup group = town.createPlotGroup(input);
                group.setOwner(player.getUniqueId(), player.getName());

                for (Plot plot : context.selectedPlots) {
                    group.addPlot(plot);
                }

                player.sendMessage(ChatColor.GREEN + "Groupe créé avec succès !");
                player.sendMessage(ChatColor.YELLOW + "Nom: " + ChatColor.GOLD + input);
                player.sendMessage(ChatColor.YELLOW + "Parcelles: " + ChatColor.GOLD + context.selectedPlots.size());

                // Sauvegarder immédiatement
                townManager.saveTownsNow();
            });
            return;
        }

        // Gestion de prix de vente de groupe
        SalePriceInputContext salePriceContext = salePriceInputSessions.get(player.getUniqueId());
        if (salePriceContext != null) {
            event.setCancelled(true);
            salePriceInputSessions.remove(player.getUniqueId());

            String input = event.getMessage();

            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    double price = Double.parseDouble(input);
                    if (price <= 0) {
                        player.sendMessage(ChatColor.RED + "Le prix doit être supérieur à 0.");
                        return;
                    }

                    Town town = townManager.getTown(salePriceContext.townName);
                    if (town == null) return;

                    PlotGroup group = town.getPlotGroup(salePriceContext.groupId);
                    if (group == null) {
                        player.sendMessage(ChatColor.RED + "Groupe introuvable.");
                        return;
                    }

                    if (plugin.getTownEconomyManager().putPlotGroupForSale(
                            salePriceContext.townName, group, price, player)) {
                        player.sendMessage(ChatColor.GREEN + "✓ Groupe mis en vente !");
                        player.sendMessage(ChatColor.YELLOW + "Prix: " + ChatColor.GOLD + String.format("%.2f€", price));
                        player.sendMessage(ChatColor.GRAY + "Surface: " + (group.getPlotCount() * 256) + "m²");
                    } else {
                        player.sendMessage(ChatColor.RED + "Impossible de mettre le groupe en vente.");
                    }

                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Prix invalide. Veuillez entrer un nombre.");
                }
            });
        }
    }

    private static class GroupCreationSession {
        String townName;
        Set<Plot> selectedPlots = new HashSet<>();

        GroupCreationSession(String townName) {
            this.townName = townName;
        }
    }

    private static class ChatInputContext {
        String townName;
        List<Plot> selectedPlots;
    }

    private static class SalePriceInputContext {
        String townName;
        String groupId;
    }
}
