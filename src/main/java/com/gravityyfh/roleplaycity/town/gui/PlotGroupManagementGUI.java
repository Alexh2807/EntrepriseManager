package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.listener.PlotGroupingListener;
import com.gravityyfh.roleplaycity.town.manager.ClaimManager;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI pour gérer les groupes de parcelles
 */
public class PlotGroupManagementGUI implements Listener {
    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final ClaimManager claimManager;

    public PlotGroupManagementGUI(RoleplayCity plugin, TownManager townManager, ClaimManager claimManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.claimManager = claimManager;
    }

    /**
     * Ouvrir le menu principal de gestion des groupes
     */
    public void openMainMenu(Player player, String townName) {
        Town town = townManager.getTown(townName);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Ville introuvable.");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_PURPLE + "⚡ Gestion des Groupes");

        // Compter les terrains groupés
        long groupCount = town.getPlots().values().stream()
            .filter(Plot::isGrouped)
            .count();

        // Item: Créer un nouveau groupe
        ItemStack createItem = new ItemStack(Material.EMERALD);
        ItemMeta createMeta = createItem.getItemMeta();
        createMeta.setDisplayName(ChatColor.GREEN + "➕ Créer un Groupe");
        List<String> createLore = new ArrayList<>();
        createLore.add(ChatColor.GRAY + "Groupez plusieurs parcelles");
        createLore.add(ChatColor.GRAY + "adjacentes ensemble");
        createLore.add("");
        createLore.add(ChatColor.YELLOW + "Cliquez pour obtenir l'outil");
        createItem.setItemMeta(createMeta);
        inv.setItem(11, createItem);

        // Item: Voir les groupes existants
        ItemStack viewItem = new ItemStack(Material.BOOK);
        ItemMeta viewMeta = viewItem.getItemMeta();
        viewMeta.setDisplayName(ChatColor.AQUA + "📋 Groupes Existants");
        List<String> viewLore = new ArrayList<>();
        viewLore.add(ChatColor.GRAY + "Voir tous vos groupes");
        viewLore.add("");
        viewLore.add(ChatColor.YELLOW + "Groupes actuels: " + ChatColor.WHITE + groupCount);
        viewItem.setItemMeta(viewMeta);
        inv.setItem(13, viewItem);

        // Item: Dégrouper
        ItemStack ungroupItem = new ItemStack(Material.SHEARS);
        ItemMeta ungroupMeta = ungroupItem.getItemMeta();
        ungroupMeta.setDisplayName(ChatColor.RED + "✂ Dégrouper un Terrain");
        List<String> ungroupLore = new ArrayList<>();
        ungroupLore.add(ChatColor.GRAY + "Séparer un groupe de terrains");
        ungroupLore.add(ChatColor.GRAY + "en parcelles individuelles");
        ungroupLore.add("");
        ungroupLore.add(ChatColor.YELLOW + "Cliquez pour voir vos groupes");
        ungroupItem.setItemMeta(ungroupMeta);
        inv.setItem(15, ungroupItem);

        // Item: Retour
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.GRAY + "← Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(22, backItem);

        player.openInventory(inv);
    }

    /**
     * Ouvrir la liste des groupes existants
     */
    public void openGroupsList(Player player, String townName) {
        Town town = townManager.getTown(townName);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Ville introuvable.");
            return;
        }

        // Récupérer tous les terrains groupés
        List<Plot> groupedPlots = town.getPlots().values().stream()
            .filter(Plot::isGrouped)
            .toList();

        if (groupedPlots.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "Aucun groupe de terrains dans " + townName);
            player.sendMessage(ChatColor.GRAY + "Utilisez le menu pour créer un groupe!");
            return;
        }

        int size = Math.min(54, ((groupedPlots.size() + 8) / 9) * 9);
        Inventory inv = Bukkit.createInventory(null, size, ChatColor.DARK_PURPLE + "📋 Groupes de " + townName);

        int slot = 0;
        for (Plot plot : groupedPlots) {
            if (slot >= size - 9) break; // Garder la dernière ligne pour navigation

            ItemStack item = createGroupItem(plot);
            inv.setItem(slot, item);
            slot++;
        }

        // Item: Retour
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.GRAY + "← Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(size - 5, backItem);

        player.openInventory(inv);
    }

    /**
     * Créer un ItemStack représentant un groupe
     */
    private ItemStack createGroupItem(Plot plot) {
        ItemStack item = new ItemStack(Material.MAP);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "📦 " + plot.getGroupName());

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "═══════════════════");
        lore.add(ChatColor.YELLOW + "Parcelles: " + ChatColor.WHITE + plot.getChunks().size());
        lore.add(ChatColor.YELLOW + "Surface: " + ChatColor.WHITE + (plot.getChunks().size() * 256) + "m²");
        lore.add(ChatColor.YELLOW + "Type: " + ChatColor.WHITE + plot.getType().getDisplayName());

        if (plot.getOwnerName() != null) {
            lore.add(ChatColor.YELLOW + "Propriétaire: " + ChatColor.WHITE + plot.getOwnerName());
        }

        if (plot.getCompanyName() != null) {
            lore.add(ChatColor.YELLOW + "Entreprise: " + ChatColor.WHITE + plot.getCompanyName());
        }

        if (plot.isForSale()) {
            lore.add(ChatColor.GREEN + "💰 À vendre: " + plot.getSalePrice() + "€");
        }

        if (plot.isForRent()) {
            lore.add(ChatColor.AQUA + "🏠 À louer: " + plot.getRentPricePerDay() + "€/jour");
        }

        lore.add(ChatColor.GRAY + "═══════════════════");
        lore.add(ChatColor.RED + "Clic droit: Dégrouper");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Démarrer une session de groupement
     */
    public void startGroupingSession(Player player, String townName) {
        PlotGroupingListener listener = plugin.getPlotGroupingListener();

        // Vérifier si le joueur a déjà une session en cours
        if (listener.hasActiveSession(player)) {
            player.sendMessage(ChatColor.YELLOW + "Vous avez déjà une session de groupement active!");
            player.sendMessage(ChatColor.YELLOW + "Parcelles sélectionnées: " + listener.getSelectionCount(player));
            player.sendMessage(ChatColor.GRAY + "Utilisez /ville cancelgrouping pour annuler");
            player.closeInventory();
            return;
        }

        // Démarrer la session avec particules
        listener.startGroupingSession(player, townName);
        player.closeInventory();
    }

    /**
     * Dégrouper un terrain
     */
    public void ungroupPlot(Player player, Plot plot, String townName) {
        if (!plot.isGrouped()) {
            player.sendMessage(ChatColor.RED + "Ce terrain n'est pas groupé!");
            return;
        }

        Town town = townManager.getTown(townName);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Ville introuvable!");
            return;
        }

        String groupName = plot.getGroupName();
        int chunkCount = plot.getChunks().size();

        // NOUVEAU : Supprimer la mailbox du terrain groupé AVANT le dégroupement
        if (plugin.getMailboxManager() != null) {
            plugin.getMailboxManager().removeMailboxByPlot(plot);
        }

        // Sauvegarder la liste des chunks AVANT modification (getChunks() retourne une copie)
        List<String> chunks = new ArrayList<>(plot.getChunks());

        // ÉTAPE 1 : Dégrouper le plot principal IMMÉDIATEMENT (garder uniquement le premier chunk)
        // Utiliser removeChunk() car getChunks() retourne une copie, pas la liste originale
        String firstChunk = chunks.get(0);
        for (int i = 1; i < chunks.size(); i++) {
            plot.removeChunk(chunks.get(i));
        }
        // Maintenant le plot n'a plus qu'1 chunk
        plot.setGrouped(false);
        plot.setGroupName(null);

        // ÉTAPE 2 : Créer des plots individuels pour les chunks restants (à partir de l'index 1)
        for (int i = 1; i < chunks.size(); i++) {
            String chunkKey = chunks.get(i);
            String[] parts = chunkKey.split(":");
            if (parts.length == 3) {
                String world = parts[0];
                int x = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);

                // Créer un nouveau plot individuel
                org.bukkit.World bukkitWorld = plugin.getServer().getWorld(world);
                if (bukkitWorld != null) {
                    Plot newPlot = new Plot(townName, bukkitWorld.getChunkAt(x, z));
                    newPlot.setType(plot.getType());
                    newPlot.setMunicipalSubType(plot.getMunicipalSubType());
                    // Garder le même propriétaire si existant
                    if (plot.getOwnerUuid() != null) {
                        newPlot.setOwner(plot.getOwnerUuid(), plot.getOwnerName());
                    }
                    // Attribuer un numéro unique si le type nécessite un numéro
                    com.gravityyfh.roleplaycity.town.data.PlotType plotType = newPlot.getType();
                    if (plotType == com.gravityyfh.roleplaycity.town.data.PlotType.PARTICULIER ||
                        plotType == com.gravityyfh.roleplaycity.town.data.PlotType.PROFESSIONNEL ||
                        plotType == com.gravityyfh.roleplaycity.town.data.PlotType.MUNICIPAL) {
                        String plotNumber = town.generateUniquePlotNumber();
                        if (plotNumber != null) {
                            newPlot.setPlotNumber(plotNumber);
                        }
                    }
                    town.addPlot(newPlot);
                }
            }
        }

        // Sauvegarder
        townManager.saveTownsNow();

        // Reconstruire le cache
        claimManager.rebuildCache();

        player.sendMessage(ChatColor.GREEN + "✓ Groupe dégroupé avec succès!");
        player.sendMessage(ChatColor.YELLOW + "Ancien nom: " + ChatColor.WHITE + groupName);
        player.sendMessage(ChatColor.YELLOW + "Parcelles séparées: " + ChatColor.WHITE + chunkCount);
        player.closeInventory();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        String title = event.getView().getTitle();

        // Menu principal
        if (title.equals(ChatColor.DARK_PURPLE + "⚡ Gestion des Groupes")) {
            event.setCancelled(true);

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            String townName = extractTownName(player);
            if (townName == null) return;

            String displayName = clicked.getItemMeta().getDisplayName();

            if (displayName.contains("Créer un Groupe")) {
                startGroupingSession(player, townName);
            } else if (displayName.contains("Groupes Existants")) {
                openGroupsList(player, townName);
            } else if (displayName.contains("Dégrouper")) {
                openGroupsList(player, townName);
            } else if (displayName.contains("Retour")) {
                player.closeInventory();
                plugin.getTownMainGUI().openMainMenu(player);
            }
        }
        // Liste des groupes
        else if (title.contains("Groupes de ")) {
            event.setCancelled(true);

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            String townName = title.replace(ChatColor.DARK_PURPLE + "📋 Groupes de ", "");

            if (clicked.getType() == Material.ARROW) {
                openMainMenu(player, townName);
                return;
            }

            // Dégrouper avec clic droit
            if (clicked.getType() == Material.MAP && event.isRightClick()) {
                String groupName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName())
                    .replace("📦 ", "");

                // Trouver le plot
                Town town = townManager.getTown(townName);
                if (town != null) {
                    Plot foundPlot = town.getPlots().values().stream()
                        .filter(p -> p.isGrouped() && groupName.equals(p.getGroupName()))
                        .findFirst()
                        .orElse(null);

                    if (foundPlot != null) {
                        ungroupPlot(player, foundPlot, townName);
                    }
                }
            }
        }
    }

    /**
     * Extraire le nom de la ville du joueur (cherche dans ses villes)
     */
    private String extractTownName(Player player) {
        for (Town town : townManager.getAllTowns()) {
            if (town.isMember(player.getUniqueId())) {
                return town.getName();
            }
        }
        return null;
    }
}
