package com.gravityyfh.roleplaycity.postal.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.postal.data.MailboxType;
import com.gravityyfh.roleplaycity.postal.manager.MailboxManager;
import com.gravityyfh.roleplaycity.town.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * GUI pour choisir et placer une boîte aux lettres
 */
public class MailboxPlacementGUI implements Listener {
    private static final String MENU_TITLE = ChatColor.GOLD + "Choisir une Boîte aux Lettres";

    private final RoleplayCity plugin;
    private final MailboxManager mailboxManager;

    // Stockage temporaire : joueur -> plot sélectionné
    private final Map<UUID, Plot> selectedPlots;

    // Stockage temporaire : joueur -> type de mailbox choisi (en attente de placement)
    private final Map<UUID, MailboxType> pendingPlacements;

    public MailboxPlacementGUI(RoleplayCity plugin, MailboxManager mailboxManager) {
        this.plugin = plugin;
        this.mailboxManager = mailboxManager;
        this.selectedPlots = new HashMap<>();
        this.pendingPlacements = new HashMap<>();
    }

    /**
     * Ouvre le menu de sélection de boîte aux lettres pour un terrain
     */
    public void openMailboxSelectionMenu(Player player, Plot plot) {
        Inventory inv = Bukkit.createInventory(null, 27, MENU_TITLE);

        // Stocker le plot sélectionné
        selectedPlots.put(player.getUniqueId(), plot);

        // Informations
        ItemStack infoItem = new ItemStack(Material.PAPER);
        infoItem.setItemMeta(createMeta(infoItem, ChatColor.AQUA + "Choisissez votre boîte aux lettres",
            Arrays.asList(
                ChatColor.GRAY + "Terrain: " + ChatColor.WHITE + plot.getDisplayInfo(),
                ChatColor.GRAY + "Ville: " + ChatColor.YELLOW + plot.getTownName(),
                "",
                ChatColor.YELLOW + "Cliquez sur une boîte aux lettres",
                ChatColor.GRAY + "puis placez-la sur votre terrain"
            )));
        inv.setItem(4, infoItem);

        // Les 3 types de boîtes aux lettres
        int slot = 11;
        for (MailboxType type : MailboxType.values()) {
            ItemStack mailboxItem = createMailboxHead(type);
            inv.setItem(slot, mailboxItem);
            slot += 2; // Espacement entre les items
        }

        // Flèche de retour
        ItemStack backItem = new ItemStack(Material.ARROW);
        backItem.setItemMeta(createMeta(backItem, ChatColor.YELLOW + "← Retour",
            Collections.singletonList(ChatColor.GRAY + "Retourner au menu précédent")));
        inv.setItem(26, backItem);

        player.openInventory(inv);
    }

    /**
     * Crée un item représentant une tête de boîte aux lettres
     */
    private ItemStack createMailboxHead(MailboxType type) {
        // Utiliser CHEST au lieu de PLAYER_HEAD pour éviter les problèmes de textures
        Material material = switch (type) {
            case LIGHT_GRAY -> Material.LIGHT_GRAY_CONCRETE;
            case LIGHT_BLUE -> Material.LIGHT_BLUE_CONCRETE;
            case ORANGE -> Material.ORANGE_CONCRETE;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.GOLD + type.getDisplayName());
        meta.setLore(Arrays.asList(
            "",
            ChatColor.GRAY + "Une boîte aux lettres élégante",
            ChatColor.GRAY + "pour recevoir votre courrier",
            "",
            ChatColor.YELLOW + "Cliquez pour sélectionner"
        ));

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Utilitaire pour créer des ItemMeta
     */
    private org.bukkit.inventory.meta.ItemMeta createMeta(ItemStack item, String displayName, List<String> lore) {
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(displayName);
        meta.setLore(lore);
        return meta;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(MENU_TITLE)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        // Retour
        if (displayName.contains("Retour")) {
            player.closeInventory();
            selectedPlots.remove(player.getUniqueId());
            return;
        }

        // Sélection d'une boîte aux lettres
        for (MailboxType type : MailboxType.values()) {
            if (displayName.equals(type.getDisplayName())) {
                Plot plot = selectedPlots.get(player.getUniqueId());
                if (plot == null) {
                    player.sendMessage(ChatColor.RED + "Erreur: Terrain introuvable.");
                    player.closeInventory();
                    return;
                }

                // Stocker le type choisi en attente de placement
                pendingPlacements.put(player.getUniqueId(), type);

                player.closeInventory();
                player.sendMessage(ChatColor.GREEN + "Boîte aux lettres sélectionnée !");
                player.sendMessage(ChatColor.YELLOW + "Placez la tête de la boîte aux lettres " +
                    ChatColor.WHITE + "dans les limites de votre terrain.");
                player.sendMessage(ChatColor.GRAY + "Un barrel sera automatiquement placé en dessous.");

                return;
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Vérifier si le joueur a une mailbox en attente de placement
        if (!pendingPlacements.containsKey(playerId)) return;

        MailboxType type = pendingPlacements.get(playerId);
        Plot plot = selectedPlots.get(playerId);

        if (plot == null) {
            pendingPlacements.remove(playerId);
            return;
        }

        // Vérifier que le joueur fait un clic droit sur un bloc
        if (!event.getAction().name().contains("RIGHT_CLICK_BLOCK")) return;

        event.setCancelled(true);

        // Récupérer la position du bloc cliqué
        org.bukkit.block.Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        // Position où la tête sera placée (au-dessus du bloc cliqué)
        org.bukkit.Location headLocation = clickedBlock.getLocation().add(0, 1, 0);

        // Tenter de placer la mailbox
        boolean success = mailboxManager.placeMailbox(plot, headLocation, type);

        if (success) {
            player.sendMessage(ChatColor.GREEN + "✓ Boîte aux lettres placée avec succès !");
            player.sendMessage(ChatColor.GRAY + "Vous pouvez maintenant recevoir du courrier.");
        } else {
            player.sendMessage(ChatColor.RED + "✗ Impossible de placer la boîte aux lettres !");
            player.sendMessage(ChatColor.YELLOW + "Assurez-vous de la placer dans les limites de votre terrain.");
            return;
        }

        // Nettoyer
        pendingPlacements.remove(playerId);
        selectedPlots.remove(playerId);
    }

    /**
     * Annuler le placement en attente
     */
    public void cancelPlacement(Player player) {
        pendingPlacements.remove(player.getUniqueId());
        selectedPlots.remove(player.getUniqueId());
    }
}
