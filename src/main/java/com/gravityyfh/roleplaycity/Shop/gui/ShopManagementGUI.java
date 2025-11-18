package com.gravityyfh.roleplaycity.shop.gui;

import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.shop.ShopDeletionResult;
import com.gravityyfh.roleplaycity.shop.ShopStatus;
import com.gravityyfh.roleplaycity.shop.manager.ShopManager;
import com.gravityyfh.roleplaycity.shop.model.Shop;
import com.gravityyfh.roleplaycity.shop.util.TeleportManager;
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

import java.text.DecimalFormat;
import java.util.*;

/**
 * GUI pour gérer une boutique spécifique
 */
public class ShopManagementGUI implements Listener {
    private final RoleplayCity plugin;
    private final ShopManager shopManager;
    private final TeleportManager teleportManager;
    private final DecimalFormat priceFormat = new DecimalFormat("#,##0.00");

    private static final String TITLE_PREFIX = ChatColor.DARK_PURPLE + "Gestion Boutique ";

    // Tracking des contextes joueurs
    private final Map<UUID, ShopManagementContext> playerContexts = new HashMap<>();

    public ShopManagementGUI(RoleplayCity plugin, ShopManager shopManager) {
        this.plugin = plugin;
        this.shopManager = shopManager;
        this.teleportManager = new TeleportManager(plugin);
    }

    /**
     * Ouvre le menu de gestion d'une boutique
     */
    public void openManagementMenu(Player player, Shop shop, EntrepriseManagerLogic.Entreprise entreprise) {
        String title = TITLE_PREFIX + shop.getShopId().toString().substring(0, 8);
        Inventory inv = Bukkit.createInventory(null, 45, title);

        // Stocker le contexte
        playerContexts.put(player.getUniqueId(), new ShopManagementContext(shop, entreprise));

        // Item vendu (centre haut)
        inv.setItem(4, createShopDisplayItem(shop));

        // Informations
        inv.setItem(10, createInfoButton(Material.BOOK,
            ChatColor.YELLOW + "Informations",
            ChatColor.GRAY + "Entreprise: " + ChatColor.WHITE + entreprise.getNom(),
            ChatColor.GRAY + "Prix unitaire: " + ChatColor.GOLD + priceFormat.format(shop.getPricePerSale()) + "€",
            ChatColor.GRAY + "Quantité par vente: " + ChatColor.YELLOW + shop.getQuantityPerSale(),
            ChatColor.GRAY + "Statut: " + getStatusColor(shop.getStatus()) + shop.getStatus().name()
        ));

        // Statistiques
        inv.setItem(13, createInfoButton(Material.PAPER,
            ChatColor.AQUA + "Statistiques",
            ChatColor.GRAY + "Ventes totales: " + ChatColor.WHITE + shop.getTotalSales(),
            ChatColor.GRAY + "Revenu total: " + ChatColor.GOLD + priceFormat.format(shop.getTotalRevenue()) + "€",
            "",
            ChatColor.GRAY + "Top acheteurs:",
            getTopBuyersLore(shop)
        ));

        // Localisation
        inv.setItem(16, createInfoButton(Material.COMPASS,
            ChatColor.GREEN + "Localisation",
            ChatColor.GRAY + "Coffre: " + formatLocation(shop.getChestLocation()),
            ChatColor.GRAY + "Panneau: " + formatLocation(shop.getSignLocation()),
            ChatColor.GRAY + "Hologramme: " + formatLocation(shop.getHologramLocation()),
            "",
            ChatColor.YELLOW + "⌘ Clic " + ChatColor.GRAY + "pour téléporter au coffre"
        ));

        // Actions
        inv.setItem(28, createActionButton(Material.GOLD_INGOT,
            ChatColor.YELLOW + "Modifier le Prix",
            ChatColor.GRAY + "Prix actuel: " + ChatColor.GOLD + priceFormat.format(shop.getPricePerSale()) + "€",
            "",
            ChatColor.YELLOW + "⌘ Clic " + ChatColor.GRAY + "pour changer"
        ));

        inv.setItem(30, createActionButton(Material.CHEST,
            ChatColor.AQUA + "Modifier la Quantité",
            ChatColor.GRAY + "Quantité actuelle: " + ChatColor.YELLOW + shop.getQuantityPerSale(),
            "",
            ChatColor.YELLOW + "⌘ Clic " + ChatColor.GRAY + "pour changer"
        ));

        // Toggle statut
        if (shop.getStatus() == ShopStatus.ACTIVE || shop.getStatus() == ShopStatus.OUT_OF_STOCK) {
            inv.setItem(32, createActionButton(Material.REDSTONE_BLOCK,
                ChatColor.RED + "Fermer la Boutique",
                ChatColor.GRAY + "Désactive temporairement la boutique",
                ChatColor.GRAY + "sans la supprimer"
            ));
        } else if (shop.getStatus() == ShopStatus.DISABLED) {
            inv.setItem(32, createActionButton(Material.EMERALD_BLOCK,
                ChatColor.GREEN + "Ouvrir la Boutique",
                ChatColor.GRAY + "Réactive la boutique"
            ));
        }

        // Réparer
        if (shop.getStatus() == ShopStatus.BROKEN) {
            inv.setItem(34, createActionButton(Material.ANVIL,
                ChatColor.GOLD + "Réparer",
                ChatColor.RED + "La boutique est endommagée!",
                "",
                ChatColor.YELLOW + "⌘ Clic " + ChatColor.GRAY + "pour réparer"
            ));
        }

        // Supprimer
        inv.setItem(39, createActionButton(Material.BARRIER,
            ChatColor.RED + "" + ChatColor.BOLD + "Supprimer la Boutique",
            ChatColor.GRAY + "Suppression définitive",
            ChatColor.DARK_RED + "⚠ Cette action est irréversible!",
            "",
            ChatColor.YELLOW + "⌘ Clic " + ChatColor.GRAY + "pour confirmer"
        ));

        // Retour
        inv.setItem(40, createActionButton(Material.ARROW,
            ChatColor.YELLOW + "Retour",
            ChatColor.GRAY + "Retour à la liste des boutiques"
        ));

        player.openInventory(inv);
    }

    /**
     * Crée l'item d'affichage de la boutique
     */
    private ItemStack createShopDisplayItem(Shop shop) {
        ItemStack item = shop.getItemTemplate().clone();
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            String itemName = meta.hasDisplayName() ? meta.getDisplayName() :
                formatMaterialName(shop.getItemTemplate().getType());

            meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + itemName);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Article vendu dans cette boutique");
            lore.add("");
            lore.add(ChatColor.YELLOW + "" + shop.getQuantityPerSale() + "x " + ChatColor.GRAY + "pour " +
                ChatColor.GOLD + priceFormat.format(shop.getPricePerSale()) + "€");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Crée un bouton d'information
     */
    private ItemStack createInfoButton(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Crée un bouton d'action
     */
    private ItemStack createActionButton(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Obtient la couleur du statut
     */
    private ChatColor getStatusColor(ShopStatus status) {
        switch (status) {
            case ACTIVE: return ChatColor.GREEN;
            case OUT_OF_STOCK: return ChatColor.RED;
            case DISABLED: return ChatColor.GRAY;
            case BROKEN: return ChatColor.DARK_RED;
            default: return ChatColor.WHITE;
        }
    }

    /**
     * Formate une localisation
     */
    private String formatLocation(org.bukkit.Location loc) {
        return String.format("%d, %d, %d", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    /**
     * Obtient le lore des top acheteurs
     */
    private String getTopBuyersLore(Shop shop) {
        if (shop.getTopBuyers() == null || shop.getTopBuyers().isEmpty()) {
            return ChatColor.GRAY + "  Aucun achat pour le moment";
        }

        StringBuilder sb = new StringBuilder();
        shop.getTopBuyers().entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(3)
            .forEach(entry -> {
                if (sb.length() > 0) sb.append("\n");
                sb.append(ChatColor.GRAY).append("  • ")
                  .append(ChatColor.WHITE).append(entry.getKey())
                  .append(ChatColor.GRAY).append(": ")
                  .append(ChatColor.YELLOW).append(entry.getValue())
                  .append(" achats");
            });

        return sb.toString();
    }

    /**
     * Formate le nom d'un matériau
     */
    private String formatMaterialName(Material material) {
        String name = material.name().replace("_", " ");
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
     * Gère les clics dans le menu
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        if (!title.startsWith(TITLE_PREFIX)) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ShopManagementContext context = playerContexts.get(player.getUniqueId());
        if (context == null) return;

        int slot = event.getRawSlot();

        if (slot == 16) {
            // Téléporter au coffre avec délai et vérification de mouvement
            player.closeInventory();
            String reason = "Boutique " + context.shop.getShopId().toString().substring(0, 8);
            teleportManager.initiateTeleport(player, context.shop.getChestLocation(), reason);

        } else if (slot == 28) {
            // Modifier le prix
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + "Entrez le nouveau prix dans le chat:");
            player.sendMessage(ChatColor.GRAY + "Prix actuel: " + ChatColor.GOLD +
                priceFormat.format(context.shop.getPricePerSale()) + "€");
            plugin.getChatListener().requestNewPriceForShop(player, context.shop);

        } else if (slot == 30) {
            // Modifier la quantité
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + "Entrez la nouvelle quantité par vente dans le chat:");
            player.sendMessage(ChatColor.GRAY + "Quantité actuelle: " + ChatColor.YELLOW + context.shop.getQuantityPerSale());
            plugin.getChatListener().requestNewQuantityForShop(player, context.shop);

        } else if (slot == 32) {
            // Toggle statut
            if (context.shop.getStatus() == ShopStatus.DISABLED) {
                context.shop.setStatus(ShopStatus.ACTIVE);
                shopManager.getComponents().updateComponents(context.shop);
                player.sendMessage(ChatColor.GREEN + "✓ Boutique ouverte!");
            } else {
                context.shop.setStatus(ShopStatus.DISABLED);
                shopManager.getComponents().updateComponents(context.shop);
                player.sendMessage(ChatColor.YELLOW + "Boutique fermée.");
            }
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
            player.closeInventory();

        } else if (slot == 34) {
            // Réparer
            player.sendMessage(ChatColor.RED + "Votre boutique est endommagée!");
            player.sendMessage(ChatColor.YELLOW + "Contactez un administrateur pour la réparer.");
            player.sendMessage(ChatColor.GRAY + "Commande admin: /entreprise shop repair");
            player.closeInventory();

        } else if (slot == 39) {
            // Supprimer
            ShopDeletionResult result = shopManager.deleteShop(context.shop,
                "Suppression par " + player.getName(), player);

            if (result.isSuccess()) {
                player.sendMessage(ChatColor.GREEN + "✓ Boutique supprimée avec succès.");
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                player.closeInventory();

                // Retour à la liste
                plugin.getShopListGUI().openShopList(player, context.entreprise, 0);
            } else {
                player.sendMessage(ChatColor.RED + "✗ Erreur: " + result.getMessage());
                player.closeInventory();
            }

        } else if (slot == 40) {
            // Retour
            player.closeInventory();
            plugin.getShopListGUI().openShopList(player, context.entreprise, 0);
        }
    }

    /**
     * Nettoie le contexte d'un joueur
     */
    public void cleanupPlayerContext(UUID playerId) {
        playerContexts.remove(playerId);
    }

    /**
     * Vérifie si un titre correspond à ce menu
     */
    public boolean isShopManagementMenu(String title) {
        return title != null && title.startsWith(TITLE_PREFIX);
    }

    /**
     * Nettoie toutes les téléportations en attente
     */
    public void cleanup() {
        if (teleportManager != null) {
            teleportManager.cleanup();
        }
    }

    /**
         * Contexte de gestion pour un joueur
         */
        private record ShopManagementContext(Shop shop, EntrepriseManagerLogic.Entreprise entreprise) {
    }
}
