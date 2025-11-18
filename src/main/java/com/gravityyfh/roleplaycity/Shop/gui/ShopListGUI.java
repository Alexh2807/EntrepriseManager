package com.gravityyfh.roleplaycity.shop.gui;

import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.shop.manager.ShopManager;
import com.gravityyfh.roleplaycity.shop.model.Shop;
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

import java.text.DecimalFormat;
import java.util.*;

/**
 * GUI pour afficher la liste des boutiques d'une entreprise
 */
public class ShopListGUI implements Listener {
    private final RoleplayCity plugin;
    private final ShopManager shopManager;
    private final DecimalFormat priceFormat = new DecimalFormat("#,##0.00");

    private static final String TITLE_PREFIX = ChatColor.DARK_BLUE + "Boutiques: ";
    private static final int ITEMS_PER_PAGE = 45; // 5 lignes de 9 items

    // Tracking des contextes joueurs
    private final Map<UUID, ShopListContext> playerContexts = new HashMap<>();

    public ShopListGUI(RoleplayCity plugin, ShopManager shopManager) {
        this.plugin = plugin;
        this.shopManager = shopManager;
    }

    /**
     * Ouvre le menu de liste des boutiques pour une entreprise
     */
    public void openShopList(Player player, EntrepriseManagerLogic.Entreprise entreprise, int page) {
        List<Shop> shops = shopManager.getShopsBySiret(entreprise.getSiret());

        int totalPages = (int) Math.ceil(shops.size() / (double) ITEMS_PER_PAGE);
        if (page < 0) page = 0;
        if (page >= totalPages && totalPages > 0) page = totalPages - 1;

        String title = TITLE_PREFIX + entreprise.getNom() + " §8[" + (page + 1) + "/" + Math.max(1, totalPages) + "]";
        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Stocker le contexte
        playerContexts.put(player.getUniqueId(), new ShopListContext(entreprise, page));

        // Ajouter les boutiques
        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, shops.size());

        for (int i = start; i < end; i++) {
            Shop shop = shops.get(i);
            inv.addItem(createShopIcon(shop));
        }

        // Boutons de navigation (ligne 6)
        if (page > 0) {
            inv.setItem(45, createNavigationButton(Material.ARROW, ChatColor.YELLOW + "← Page Précédente"));
        }

        // Bouton créer une boutique
        inv.setItem(49, createActionButton(Material.EMERALD,
            ChatColor.GREEN + "" + ChatColor.BOLD + "Créer une Boutique",
            ChatColor.GRAY + "Cliquez pour créer une nouvelle boutique",
            ChatColor.YELLOW + "Max: " + plugin.getConfig().getInt("shop-system.max-shops-per-entreprise", 10) + " boutiques"
        ));

        if (page < totalPages - 1) {
            inv.setItem(53, createNavigationButton(Material.ARROW, ChatColor.YELLOW + "Page Suivante →"));
        }

        // Bouton retour
        inv.setItem(48, createActionButton(Material.BARRIER,
            ChatColor.RED + "Retour",
            ChatColor.GRAY + "Retour au menu de l'entreprise"
        ));

        player.openInventory(inv);
    }

    /**
     * Crée l'icône représentant une boutique
     */
    private ItemStack createShopIcon(Shop shop) {
        ItemStack item = shop.getItemTemplate().clone();
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Nom de l'item vendu
            String itemName = meta.hasDisplayName() ? meta.getDisplayName() :
                formatMaterialName(shop.getItemTemplate().getType());

            meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + itemName);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "▪ " + ChatColor.WHITE + "Quantité: " + ChatColor.YELLOW + shop.getQuantityPerSale());
            lore.add(ChatColor.GRAY + "▪ " + ChatColor.WHITE + "Prix: " + ChatColor.GOLD + priceFormat.format(shop.getPricePerSale()) + "€");
            lore.add("");

            // Statut
            switch (shop.getStatus()) {
                case ACTIVE:
                    lore.add(ChatColor.GREEN + "✓ OUVERT");
                    break;
                case OUT_OF_STOCK:
                    lore.add(ChatColor.RED + "✗ RUPTURE DE STOCK");
                    break;
                case DISABLED:
                    lore.add(ChatColor.GRAY + "○ FERMÉ");
                    break;
                case BROKEN:
                    lore.add(ChatColor.DARK_RED + "⚠ ENDOMMAGÉ");
                    break;
            }

            lore.add("");
            lore.add(ChatColor.YELLOW + "📊 Statistiques:");
            lore.add(ChatColor.GRAY + "  Ventes: " + ChatColor.WHITE + shop.getTotalSales());
            lore.add(ChatColor.GRAY + "  Revenu: " + ChatColor.GOLD + priceFormat.format(shop.getTotalRevenue()) + "€");
            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "ID: " + shop.getShopId().toString().substring(0, 8));
            lore.add("");
            lore.add(ChatColor.YELLOW + "⌘ Clic gauche " + ChatColor.GRAY + "pour gérer");

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Crée un bouton de navigation
     */
    private ItemStack createNavigationButton(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
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
     * Formate le nom d'un matériau
     */
    private String formatMaterialName(Material material) {
        String name = material.name().replace("_", " ");
        String[] words = name.split(" ");
        StringBuilder formatted = new StringBuilder();

        for (String word : words) {
            if (formatted.length() > 0) {
                formatted.append(" ");
            }
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

        ShopListContext context = playerContexts.get(player.getUniqueId());
        if (context == null) return;

        int slot = event.getRawSlot();

        // Navigation
        if (slot == 45 && context.page > 0) {
            openShopList(player, context.entreprise, context.page - 1);
        } else if (slot == 53) {
            openShopList(player, context.entreprise, context.page + 1);
        } else if (slot == 48) {
            // Retour au menu entreprise
            player.closeInventory();
            plugin.getEntrepriseGUI().openManageSpecificEntrepriseMenu(player, context.entreprise);
        } else if (slot == 49) {
            // Créer une boutique
            player.closeInventory();
            ShopCreationGUI creationGUI = plugin.getShopCreationGUI();
            if (creationGUI != null) {
                creationGUI.openCreationMenu(player, context.entreprise);
            } else {
                player.sendMessage(ChatColor.RED + "Erreur: GUI de création non disponible.");
            }
        } else if (slot < 45) {
            // Clic sur une boutique
            List<Shop> shops = shopManager.getShopsBySiret(context.entreprise.getSiret());
            int index = (context.page * ITEMS_PER_PAGE) + slot;

            if (index < shops.size()) {
                Shop shop = shops.get(index);
                player.closeInventory();
                ShopManagementGUI managementGUI = plugin.getShopManagementGUI();
                if (managementGUI != null) {
                    managementGUI.openManagementMenu(player, shop, context.entreprise);
                } else {
                    player.sendMessage(ChatColor.RED + "Erreur: GUI de gestion non disponible.");
                }
            }
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
    public boolean isShopListMenu(String title) {
        return title != null && title.startsWith(TITLE_PREFIX);
    }

    /**
         * Contexte de navigation pour un joueur
         */
        private record ShopListContext(EntrepriseManagerLogic.Entreprise entreprise, int page) {
    }
}
