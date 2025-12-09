package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownTeleportCooldown;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI affichant la liste de toutes les villes
 * Permet de se téléporter aux villes en clic droit
 */
public class TownListGUI implements Listener {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final TownTeleportCooldown cooldownManager;

    public TownListGUI(RoleplayCity plugin, TownManager townManager, TownTeleportCooldown cooldownManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.cooldownManager = cooldownManager;
    }

    /**
     * Ouvre le GUI de liste des villes
     * @param player Le joueur
     * @param showBackButton Afficher le bouton retour (si ouvert depuis TownMainGUI)
     */
    public void openTownList(Player player, boolean showBackButton) {
        Inventory inv = Bukkit.createInventory(null, 54, "🌍 Villes du Serveur");

        List<String> townNames = new ArrayList<>(townManager.getTownNames());

        int slot = 0;
        for (String townName : townNames) {
            if (slot >= 45) break; // Limite à 45 villes (5 lignes)

            Town town = townManager.getTown(townName);
            if (town == null) continue;

            // Créer l'item de la ville
            Material icon = switch (town.getLevel()) {
                case CAMPEMENT -> Material.GRASS_BLOCK;
                case VILLAGE -> Material.OAK_LOG;
                case VILLE -> Material.BEACON;
            };

            ItemStack townItem = new ItemStack(icon);
            ItemMeta meta = townItem.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + townName);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "─────────────────");
            lore.add(ChatColor.YELLOW + "Niveau: " + ChatColor.WHITE + town.getLevel().getDisplayName());
            lore.add(ChatColor.YELLOW + "Membres: " + ChatColor.WHITE + town.getMembers().size());
            lore.add(ChatColor.YELLOW + "Parcelles: " + ChatColor.WHITE + town.getPlots().size());
            lore.add("");

            // Indiquer si le spawn est disponible
            if (town.hasSpawnLocation()) {
                lore.add(ChatColor.GREEN + "✓ Spawn disponible");
                lore.add("");
                lore.add(ChatColor.GREEN + "▶ Clic GAUCHE: " + ChatColor.WHITE + "Rejoindre la ville");
                lore.add(ChatColor.AQUA + "▶ Clic DROIT: " + ChatColor.WHITE + "Se téléporter");
                lore.add(ChatColor.GRAY + "  (Cooldown: 5 secondes)");
            } else {
                lore.add(ChatColor.RED + "✗ Pas de spawn");
                lore.add(ChatColor.GREEN + "▶ Clic GAUCHE: " + ChatColor.WHITE + "Rejoindre la ville");
                lore.add(ChatColor.GRAY + "Téléportation indisponible");
            }

            lore.add(ChatColor.GRAY + "─────────────────");
            meta.setLore(lore);
            townItem.setItemMeta(meta);

            inv.setItem(slot++, townItem);
        }

        // Bouton retour si demandé
        if (showBackButton) {
            ItemStack backItem = new ItemStack(Material.ARROW);
            ItemMeta backMeta = backItem.getItemMeta();
            backMeta.setDisplayName(ChatColor.YELLOW + "← Retour à Ma Ville");
            backItem.setItemMeta(backMeta);
            inv.setItem(49, backItem);
        }

        // Sauvegarder dans metadata si bouton retour affiché
        player.setMetadata("townListShowBack", new org.bukkit.metadata.FixedMetadataValue(plugin, showBackButton));

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals("🌍 Villes du Serveur")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Bouton Retour
        if (clicked.getType() == Material.ARROW) {
            player.closeInventory();
            player.removeMetadata("townListShowBack", plugin);
            plugin.getTownMainGUI().openMainMenu(player);
            return;
        }

        // Clic sur une ville
        if (event.getClick() == ClickType.RIGHT) {
            handleTeleport(player, clicked);
        } else if (event.getClick() == ClickType.LEFT) {
            handleJoinRequest(player, clicked);
        }
    }

    /**
     * Gère la demande de rejoindre une ville
     */
    private void handleJoinRequest(Player player, ItemStack townItem) {
        if (!townItem.hasItemMeta()) return;
        String displayName = townItem.getItemMeta().getDisplayName();
        String townName = ChatColor.stripColor(displayName).trim();

        // Fermer l'inventaire
        player.closeInventory();

        // Exécuter la commande de join
        player.performCommand("ville rejoindre " + townName);
    }

    /**
     * Gère la téléportation vers une ville
     */
    private void handleTeleport(Player player, ItemStack townItem) {
        if (!townItem.hasItemMeta()) return;

        String displayName = townItem.getItemMeta().getDisplayName();
        String townName = ChatColor.stripColor(displayName).trim();

        Town town = townManager.getTown(townName);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Ville introuvable.");
            return;
        }

        // Vérifier que le spawn existe
        if (!town.hasSpawnLocation()) {
            player.sendMessage(ChatColor.RED + "Cette ville n'a pas de spawn configuré.");
            return;
        }

        // Vérifier si déjà en warm-up
        if (cooldownManager != null && cooldownManager.isWarmingUp(player)) {
            player.sendMessage(ChatColor.RED + "Téléportation déjà en cours !");
            return;
        }

        // Fermer l'inventaire
        player.closeInventory();

        // Démarrer le warm-up de 5 secondes
        Location spawn = town.getSpawnLocation();
        if (cooldownManager != null) {
            cooldownManager.startTeleportWarmup(player, spawn, townName);
        } else {
            // Fallback si cooldownManager null (ne devrait jamais arriver)
            plugin.getLogger().warning("[WARNING] cooldownManager est NULL ! Téléportation directe sans warm-up.");
            player.teleport(spawn);
            player.sendMessage(ChatColor.GREEN + "✓ Téléportation vers " + ChatColor.GOLD + townName);
        }
    }
}
