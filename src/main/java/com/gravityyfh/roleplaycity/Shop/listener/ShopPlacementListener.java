package com.gravityyfh.roleplaycity.shop.listener;

import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.shop.ShopCreationResult;
import com.gravityyfh.roleplaycity.shop.manager.ShopManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listener pour gérer le placement des boutiques
 */
public class ShopPlacementListener implements Listener {
    private final RoleplayCity plugin;
    private final ShopManager shopManager;

    // Contextes de placement en cours
    private final Map<UUID, PlacementContext> pendingPlacements = new HashMap<>();

    public ShopPlacementListener(RoleplayCity plugin, ShopManager shopManager) {
        this.plugin = plugin;
        this.shopManager = shopManager;
    }

    /**
     * Démarre le processus de placement pour un joueur
     */
    public void startPlacement(Player player, EntrepriseManagerLogic.Entreprise entreprise,
                              ItemStack itemTemplate, double price, int quantity) {
        PlacementContext context = new PlacementContext(entreprise, itemTemplate, price, quantity);
        pendingPlacements.put(player.getUniqueId(), context);

        player.sendMessage("");
        player.sendMessage(ChatColor.DARK_AQUA + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "PLACEMENT DE LA BOUTIQUE");
        player.sendMessage(ChatColor.DARK_AQUA + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Étape 1/2: Placez un " + ChatColor.GOLD + "COFFRE");
        player.sendMessage(ChatColor.GRAY + "Ce coffre contiendra le stock de votre boutique");
        player.sendMessage("");
        player.sendMessage(ChatColor.DARK_GRAY + "Tapez " + ChatColor.RED + "/entreprise shop cancel" + ChatColor.DARK_GRAY + " pour annuler");
    }

    /**
     * Gère le placement de blocs
     */
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        PlacementContext context = pendingPlacements.get(player.getUniqueId());

        if (context == null) return;

        Block block = event.getBlock();
        Material type = block.getType();

        // Étape 1: Placement du coffre
        if (context.chestLocation == null) {
            if (type != Material.CHEST) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "✗ Vous devez placer un " + ChatColor.GOLD + "COFFRE" + ChatColor.RED + "!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            context.chestLocation = block.getLocation();
            player.sendMessage("");
            player.sendMessage(ChatColor.GREEN + "✓ Coffre placé!");
            player.sendMessage("");
            player.sendMessage(ChatColor.YELLOW + "Étape 2/2: Placez un " + ChatColor.GOLD + "PANNEAU");
            player.sendMessage(ChatColor.GRAY + "Le panneau doit être placé à côté du coffre");
            player.sendMessage(ChatColor.GRAY + "(Distance maximale: " +
                plugin.getConfig().getInt("shop-system.max-distance-chest-sign", 2) + " blocs)");
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
            return;
        }

        // Étape 2: Placement du panneau
        if (context.signLocation == null) {
            if (!isSign(type)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "✗ Vous devez placer un " + ChatColor.GOLD + "PANNEAU" + ChatColor.RED + "!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            // Vérifier la distance
            double distance = block.getLocation().distance(context.chestLocation);
            int maxDistance = plugin.getConfig().getInt("shop-system.max-distance-chest-sign", 2);

            if (distance > maxDistance) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "✗ Le panneau est trop éloigné du coffre!");
                player.sendMessage(ChatColor.GRAY + "Distance actuelle: " + ChatColor.YELLOW +
                    String.format("%.1f", distance) + " blocs");
                player.sendMessage(ChatColor.GRAY + "Distance maximale: " + ChatColor.YELLOW + maxDistance + " blocs");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            context.signLocation = block.getLocation();

            // Créer la boutique!
            player.sendMessage("");
            player.sendMessage(ChatColor.GREEN + "✓ Panneau placé!");
            player.sendMessage("");
            player.sendMessage(ChatColor.AQUA + "Création de la boutique en cours...");

            // Finaliser la création
            finalizePlacement(player, context);
        }
    }

    /**
     * Finalise la création de la boutique
     */
    private void finalizePlacement(Player player, PlacementContext context) {
        ShopCreationResult result = shopManager.createShop(
            player,
            context.entreprise,
            context.chestLocation,
            context.signLocation,
            context.itemTemplate,
            context.quantity,
            context.price
        );

        if (result.isSuccess()) {
            player.sendMessage("");
            player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "✓ BOUTIQUE CRÉÉE AVEC SUCCÈS!");
            player.sendMessage("");
            player.sendMessage(ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage(ChatColor.YELLOW + "Informations:");
            player.sendMessage(ChatColor.GRAY + "  • Item: " + ChatColor.WHITE + formatItemName(context.itemTemplate));
            player.sendMessage(ChatColor.GRAY + "  • Prix: " + ChatColor.GOLD + context.price + "€");
            player.sendMessage(ChatColor.GRAY + "  • Quantité: " + ChatColor.YELLOW + context.quantity);
            player.sendMessage(ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("");
            player.sendMessage(ChatColor.AQUA + "Remplissez le coffre avec l'item à vendre!");
            player.sendMessage(ChatColor.GRAY + "La boutique s'ouvrira automatiquement quand");
            player.sendMessage(ChatColor.GRAY + "le coffre contiendra au moins 1 item.");

            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        } else {
            player.sendMessage("");
            player.sendMessage(ChatColor.RED + "✗ Erreur lors de la création:");
            player.sendMessage(ChatColor.GRAY + result.getErrorMessage());
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);

            // Supprimer les blocs placés
            if (context.chestLocation != null) {
                context.chestLocation.getBlock().setType(Material.AIR);
            }
            if (context.signLocation != null) {
                context.signLocation.getBlock().setType(Material.AIR);
            }
        }

        // Nettoyer le contexte
        pendingPlacements.remove(player.getUniqueId());
    }

    /**
     * Annule le placement en cours pour un joueur
     */
    public void cancelPlacement(Player player) {
        PlacementContext context = pendingPlacements.remove(player.getUniqueId());

        if (context != null) {
            // Supprimer les blocs placés
            if (context.chestLocation != null) {
                context.chestLocation.getBlock().setType(Material.AIR);
            }
            if (context.signLocation != null) {
                context.signLocation.getBlock().setType(Material.AIR);
            }

            player.sendMessage(ChatColor.RED + "✗ Création de boutique annulée.");
            player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.0f);
        } else {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas de création de boutique en cours.");
        }
    }

    /**
     * Vérifie si un joueur a un placement en cours
     */
    public boolean hasPlacement(UUID playerId) {
        return pendingPlacements.containsKey(playerId);
    }

    /**
     * Vérifie si un matériau est un panneau
     */
    private boolean isSign(Material material) {
        String name = material.name();
        return name.contains("SIGN") && !name.contains("HANGING");
    }

    /**
     * Formate le nom d'un item
     */
    private String formatItemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }

        String name = item.getType().name().replace("_", " ");
        String[] words = name.split(" ");
        StringBuilder formatted = new StringBuilder();

        for (String word : words) {
            if (formatted.length() > 0) formatted.append(" ");
            formatted.append(word.substring(0, 1).toUpperCase());
            formatted.append(word.substring(1).toLowerCase());
        }

        return formatted.toString();
    }

    /**
     * Contexte de placement
     */
    private static class PlacementContext {
        final EntrepriseManagerLogic.Entreprise entreprise;
        final ItemStack itemTemplate;
        final double price;
        final int quantity;
        Location chestLocation;
        Location signLocation;

        PlacementContext(EntrepriseManagerLogic.Entreprise entreprise, ItemStack itemTemplate,
                        double price, int quantity) {
            this.entreprise = entreprise;
            this.itemTemplate = itemTemplate;
            this.price = price;
            this.quantity = quantity;
        }
    }
}
