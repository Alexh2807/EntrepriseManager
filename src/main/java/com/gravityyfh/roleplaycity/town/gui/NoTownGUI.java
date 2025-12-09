package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.TownTeleportCooldown;
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
 * GUI affiché aux joueurs qui n'ont pas encore de ville
 * Permet de créer une ville ou de voir la liste des villes existantes
 */
public class NoTownGUI implements Listener {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final TownListGUI townListGUI;

    public NoTownGUI(RoleplayCity plugin, TownManager townManager, TownListGUI townListGUI) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.townListGUI = townListGUI;
    }

    /**
     * Ouvre le menu pour joueurs sans ville
     */
    public void openNoTownMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "RoleplayCity - Bienvenue");

        // Récupérer le coût de création depuis la config
        double creationCost = plugin.getConfig().getDouble("town.creation-cost", 5000.0);

        // Bouton: Créer une ville
        ItemStack createItem = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta createMeta = createItem.getItemMeta();
        createMeta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "➕ Créer une Ville");

        List<String> createLore = new ArrayList<>();
        createLore.add(ChatColor.GRAY + "─────────────────");
        createLore.add(ChatColor.YELLOW + "Fondez votre propre ville");
        createLore.add(ChatColor.YELLOW + "et devenez maire !");
        createLore.add("");
        createLore.add(ChatColor.GOLD + "Coût: " + ChatColor.WHITE + String.format("%.0f€", creationCost));
        createLore.add(ChatColor.GOLD + "Niveau initial: " + ChatColor.WHITE + "Campement");
        createLore.add("");
        createLore.add(ChatColor.GREEN + "▶ Cliquez pour créer votre ville");
        createLore.add(ChatColor.GRAY + "─────────────────");
        createMeta.setLore(createLore);
        createItem.setItemMeta(createMeta);

        // Bouton: Voir les villes
        ItemStack listItem = new ItemStack(Material.COMPASS);
        ItemMeta listMeta = listItem.getItemMeta();
        listMeta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "🌍 Voir les Villes");

        List<String> listLore = new ArrayList<>();
        listLore.add(ChatColor.GRAY + "─────────────────");
        listLore.add(ChatColor.YELLOW + "Explorez les villes existantes");
        listLore.add(ChatColor.YELLOW + "et téléportez-vous !");
        listLore.add("");
        listLore.add(ChatColor.GRAY + "Vous pourrez:");
        listLore.add(ChatColor.WHITE + "• Voir les informations");
        listLore.add(ChatColor.WHITE + "• Vous téléporter (clic droit)");
        listLore.add("");
        listLore.add(ChatColor.AQUA + "▶ Cliquez pour voir la liste");
        listLore.add(ChatColor.GRAY + "─────────────────");
        listMeta.setLore(listLore);
        listItem.setItemMeta(listMeta);

        // Placer les items
        inv.setItem(11, createItem);  // Gauche
        inv.setItem(15, listItem);    // Droite

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals("RoleplayCity - Bienvenue")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        player.closeInventory();

        // Créer une ville
        if (clicked.getType() == Material.GRASS_BLOCK) {
            handleCreateTown(player);
            return;
        }

        // Voir les villes
        if (clicked.getType() == Material.COMPASS) {
            townListGUI.openTownList(player, false); // Pas de bouton retour
        }
    }

    /**
     * Gère la création d'une ville
     */
    private void handleCreateTown(Player player) {
        // Récupérer le coût de création depuis la config
        double creationCost = plugin.getConfig().getDouble("town.creation-cost", 5000.0);

        // Vérifier si le joueur a assez d'argent AVANT de demander le nom
        if (!RoleplayCity.getEconomy().has(player, creationCost)) {
            double balance = RoleplayCity.getEconomy().getBalance(player);
            player.sendMessage("");
            player.sendMessage(ChatColor.RED + "❌ Argent insuffisant pour créer une ville !");
            player.sendMessage(ChatColor.RED + "Coût requis: " + ChatColor.GOLD + String.format("%.0f€", creationCost));
            player.sendMessage(ChatColor.RED + "Votre solde: " + ChatColor.GOLD + String.format("%.0f€", balance));
            player.sendMessage(ChatColor.RED + "Il vous manque: " + ChatColor.GOLD + String.format("%.0f€", (creationCost - balance)));
            player.sendMessage("");
            return;
        }

        // Demander le nom de la ville via le chat
        plugin.getChatInputListener().requestInput(
            player,
            ChatColor.GOLD + "📝 Entrez le nom de votre ville:",
            input -> {
                // Re-vérifier l'argent au cas où le joueur l'aurait dépensé entre-temps
                if (!RoleplayCity.getEconomy().has(player, creationCost)) {
                    player.sendMessage(ChatColor.RED + "❌ Vous n'avez plus assez d'argent !");
                    return;
                }

                // Créer la ville
                boolean success = townManager.createTown(input, player, creationCost);

                if (success) {
                    player.sendMessage("");
                    player.sendMessage(ChatColor.GREEN + "✓ Ville créée avec succès !");
                    player.sendMessage(ChatColor.YELLOW + "Nom: " + ChatColor.WHITE + input);
                    player.sendMessage(ChatColor.YELLOW + "Coût: " + ChatColor.GOLD + String.format("%.0f€", creationCost));
                    player.sendMessage("");

                    // Ouvrir le GUI de la ville
                    plugin.getTownMainGUI().openMainMenu(player);
                }
                // Les messages d'erreur sont gérés par createTown()
            },
            input -> input.length() >= 3 && input.length() <= 32,
            ChatColor.RED + "Le nom doit contenir entre 3 et 32 caractères",
            60
        );
    }
}
