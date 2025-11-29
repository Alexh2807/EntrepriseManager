package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownLevel;
import com.gravityyfh.roleplaycity.town.data.TownLevelConfig;
import com.gravityyfh.roleplaycity.town.manager.TownLevelManager;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
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
 * GUI pour l'upgrade de ville (Campement → Village → Ville)
 */
public class TownUpgradeGUI implements Listener {
    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final TownLevelManager levelManager;
    private TownMainGUI mainGUI;

    public TownUpgradeGUI(RoleplayCity plugin, TownManager townManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.levelManager = plugin.getTownLevelManager();
    }

    public void setMainGUI(TownMainGUI mainGUI) {
        this.mainGUI = mainGUI;
    }

    public void openUpgradeMenu(Player player) {
        String townName = townManager.getPlayerTown(player.getUniqueId());
        if (townName == null) {
            player.sendMessage(ChatColor.RED + "Vous n'êtes dans aucune ville.");
            return;
        }

        Town town = townManager.getTown(townName);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Ville introuvable.");
            return;
        }

        // Vérifier que le joueur est maire
        if (!town.isMayor(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Seul le maire peut améliorer la ville.");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.GOLD + "⭐ Évolution de votre Ville");

        TownLevel currentLevel = town.getLevel();
        TownLevelConfig currentConfig = levelManager.getConfig(currentLevel);
        TownLevel nextLevel = currentLevel.getNextLevel();

        // Afficher le niveau actuel (slot 11)
        inv.setItem(11, createCurrentLevelItem(town, currentLevel, currentConfig));

        // Afficher le niveau suivant si disponible (slot 15)
        if (nextLevel != null) {
            TownLevelConfig nextConfig = levelManager.getConfig(nextLevel);
            inv.setItem(15, createNextLevelItem(town, nextLevel, nextConfig));

            // Bouton d'upgrade (slot 13)
            TownLevelManager.UpgradeResult result = levelManager.canUpgrade(town);
            inv.setItem(13, createUpgradeButton(result, nextConfig));
        } else {
            // Ville au niveau maximum
            inv.setItem(15, createMaxLevelItem());
        }

        // Bouton retour (haut gauche)
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "← Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(0, backItem);

        // Bouton fermer (haut droite)
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "✖ Fermer");
        closeItem.setItemMeta(closeMeta);
        inv.setItem(8, closeItem);

        player.openInventory(inv);
    }

    private ItemStack createCurrentLevelItem(Town town, TownLevel level, TownLevelConfig config) {
        Material material = switch (level) {
            case CAMPEMENT -> Material.CAMPFIRE;
            case VILLAGE -> Material.OAK_SIGN;
            case VILLE -> Material.BEACON;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "Niveau Actuel: " + level.getDisplayName());

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + level.getDescription());
        lore.add("");
        lore.add(ChatColor.AQUA + "📊 Statistiques actuelles:");
        lore.add(ChatColor.GRAY + "  • Population: " + ChatColor.WHITE + town.getMemberCount() + " joueur(s)");
        lore.add(ChatColor.GRAY + "  • Claims utilisés: " + ChatColor.WHITE + town.getRealChunkCount() + "/" + config.getMaxClaims());
        lore.add(ChatColor.GRAY + "  • Solde banque: " + ChatColor.GOLD + String.format("%.2f€", town.getBankBalance()) + 
                 ChatColor.GRAY + " / " + ChatColor.YELLOW + String.format("%.2f€", config.getBankLimit()));
        lore.add("");
        lore.add(ChatColor.AQUA + "👮 Personnel municipal:");
        lore.add(ChatColor.GRAY + "  • Policiers: " + ChatColor.WHITE +
            town.getMembersByRole(com.gravityyfh.roleplaycity.town.data.TownRole.POLICIER).size() + "/" + config.getMaxPoliciers());
        lore.add(ChatColor.GRAY + "  • Juges: " + ChatColor.WHITE +
            town.getMembersByRole(com.gravityyfh.roleplaycity.town.data.TownRole.JUGE).size() + "/" + config.getMaxJuges());
        lore.add(ChatColor.GRAY + "  • Médecins: " + ChatColor.WHITE +
            town.getMembersByRole(com.gravityyfh.roleplaycity.town.data.TownRole.MEDECIN).size() + "/" + config.getMaxMedecins());

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNextLevelItem(Town town, TownLevel level, TownLevelConfig config) {
        Material material = switch (level) {
            case CAMPEMENT -> Material.CAMPFIRE;
            case VILLAGE -> Material.OAK_SIGN;
            case VILLE -> Material.BEACON;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Niveau Suivant: " + level.getDisplayName());

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + level.getDescription());
        lore.add("");
        lore.add(ChatColor.AQUA + "📊 Nouveaux avantages:");
        lore.add(ChatColor.GRAY + "  • Population: " + ChatColor.WHITE + config.getMinPopulation() + "-" +
            (config.getMaxPopulation() == Integer.MAX_VALUE ? "∞" : config.getMaxPopulation()) + " joueur(s)");
        lore.add(ChatColor.GRAY + "  • Claims max: " + ChatColor.WHITE + config.getMaxClaims());
        lore.add(ChatColor.GRAY + "  • Banque max: " + ChatColor.WHITE + String.format("%,.0f€", config.getBankLimit()));
        lore.add("");
        lore.add(ChatColor.AQUA + "👮 Personnel autorisé:");
        lore.add(ChatColor.GRAY + "  • Policiers: " + ChatColor.WHITE + config.getMaxPoliciers());
        lore.add(ChatColor.GRAY + "  • Juges: " + ChatColor.WHITE + config.getMaxJuges());
        lore.add(ChatColor.GRAY + "  • Médecins: " + ChatColor.WHITE + config.getMaxMedecins());
        lore.add("");
        lore.add(ChatColor.AQUA + "💰 Conditions requises:");
        lore.add(ChatColor.GRAY + "  • Population minimum: " + ChatColor.WHITE + config.getMinPopulation());
        lore.add(ChatColor.GRAY + "  • Coût d'upgrade: " + ChatColor.GOLD + String.format("%.2f€", config.getUpgradeCost()));

        // Vérifier les conditions
        int currentPop = town.getMemberCount();
        double currentBalance = town.getBankBalance();

        lore.add("");
        if (currentPop >= config.getMinPopulation()) {
            lore.add(ChatColor.GREEN + "✓ Population suffisante");
        } else {
            lore.add(ChatColor.RED + "✗ Population insuffisante (" + (config.getMinPopulation() - currentPop) + " manquant(s))");
        }

        if (currentBalance >= config.getUpgradeCost()) {
            lore.add(ChatColor.GREEN + "✓ Fonds suffisants");
        } else {
            lore.add(ChatColor.RED + "✗ Fonds insuffisants (" + String.format("%.2f€", config.getUpgradeCost() - currentBalance) + " manquant(s))");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createUpgradeButton(TownLevelManager.UpgradeResult result, TownLevelConfig nextConfig) {
        ItemStack item;
        ItemMeta meta;

        if (result.canUpgrade()) {
            item = new ItemStack(Material.EMERALD_BLOCK);
            meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.GREEN + "✓ AMÉLIORER LA VILLE");

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Cliquez pour améliorer votre ville");
            lore.add(ChatColor.GRAY + "au niveau supérieur!");
            lore.add("");
            lore.add(ChatColor.GOLD + "Coût: " + String.format("%.2f€", nextConfig.getUpgradeCost()));
            lore.add(ChatColor.GRAY + "(prélevé de la banque de la ville)");
            meta.setLore(lore);
        } else {
            item = new ItemStack(Material.REDSTONE_BLOCK);
            meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.RED + "✗ Conditions non remplies");

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Vous ne pouvez pas encore");
            lore.add(ChatColor.GRAY + "améliorer votre ville.");
            lore.add("");
            lore.add(ChatColor.YELLOW + "Passez la souris sur le niveau");
            lore.add(ChatColor.YELLOW + "suivant pour voir les conditions.");
            meta.setLore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createMaxLevelItem() {
        ItemStack item = new ItemStack(Material.DIAMOND_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "★ NIVEAU MAXIMUM ATTEINT ★");

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Félicitations !");
        lore.add(ChatColor.GRAY + "Votre ville est au niveau maximum.");
        lore.add("");
        lore.add(ChatColor.YELLOW + "Vous avez débloqué tous les avantages !");
        meta.setLore(lore);

        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();

        // Vérifier si c'est bien notre GUI (avec ou sans codes couleur)
        if (!title.contains("Évolution de votre Ville")) {
            return;
        }

        event.setCancelled(true);

        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) {
            return;
        }

        String townName = townManager.getPlayerTown(player.getUniqueId());
        if (townName == null) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "Erreur: Vous n'êtes pas dans une ville.");
            return;
        }

        Town town = townManager.getTown(townName);
        if (town == null) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "Erreur: Ville introuvable.");
            return;
        }

        Material clickedType = event.getCurrentItem().getType();

        // Bouton d'upgrade (EMERALD_BLOCK)
        if (clickedType == Material.EMERALD_BLOCK) {
            player.closeInventory();

            if (levelManager.upgradeTown(town, player)) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }

        } else if (clickedType == Material.REDSTONE_BLOCK) {
            // Bouton rouge "Conditions non remplies" - juste jouer un son d'erreur
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
            player.sendMessage(ChatColor.RED + "Conditions non remplies pour l'amélioration !");

        } else if (clickedType == Material.ARROW) {
            // Retour au menu principal
            player.closeInventory();
            if (mainGUI != null) {
                mainGUI.openMainMenu(player);
            }

        } else if (clickedType == Material.BARRIER) {
            // Fermer
            player.closeInventory();

        } else {
            // Pour tous les autres items (niveau actuel, niveau suivant, niveau max)
            // On ne fait rien, juste annuler le clic
            // Mais on peut jouer un petit son pour feedback
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
        }
    }
}
