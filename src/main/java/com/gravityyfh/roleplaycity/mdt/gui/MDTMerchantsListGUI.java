package com.gravityyfh.roleplaycity.mdt.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.mdt.config.MDTConfig;
import com.gravityyfh.roleplaycity.mdt.config.MDTConfig.MerchantType;
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

import java.util.*;

/**
 * GUI pour voir et gérer tous les marchands configurés
 */
public class MDTMerchantsListGUI implements Listener {
    private final RoleplayCity plugin;
    private final MDTConfig config;

    private static final String TITLE = "§8Marchands MDT";

    public MDTMerchantsListGUI(RoleplayCity plugin, MDTConfig config) {
        this.plugin = plugin;
        this.config = config;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);

        // Fond
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, glass);
        }

        // Charger les marchands depuis la config
        List<String> merchants = config.getFileConfig().getStringList("locations.merchants");

        // Afficher les marchands
        int slot = 10;
        int merchantIndex = 0;
        for (String entry : merchants) {
            if (slot > 43 || merchantIndex >= 28) break;

            try {
                String[] parts = entry.split(",");
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                MerchantType type = MerchantType.valueOf(parts[3]);

                Material icon = getMerchantIcon(type);
                String typeName = getMerchantTypeName(type);
                ChatColor typeColor = getMerchantColor(type);

                ItemStack merchantItem = createItem(icon,
                    typeColor + typeName,
                    "",
                    ChatColor.GRAY + "Position: " + ChatColor.YELLOW + x + ", " + y + ", " + z,
                    ChatColor.GRAY + "Type: " + typeColor + type.name(),
                    "",
                    ChatColor.RED + "Clic pour supprimer");

                // Stocker l'index
                ItemMeta meta = merchantItem.getItemMeta();
                if (meta != null) {
                    List<String> lore = meta.getLore();
                    if (lore == null) lore = new ArrayList<>();
                    lore.add(ChatColor.BLACK + "index:" + merchantIndex);
                    meta.setLore(lore);
                    merchantItem.setItemMeta(meta);
                }

                inv.setItem(slot, merchantItem);

                slot++;
                if (slot % 9 == 8) slot += 2;

                merchantIndex++;
            } catch (Exception e) {
                // Ignorer les entrées invalides
            }
        }

        // Info si aucun marchand
        if (merchants.isEmpty()) {
            inv.setItem(22, createItem(Material.BARRIER,
                ChatColor.RED + "Aucun marchand",
                "",
                ChatColor.GRAY + "Utilisez " + ChatColor.YELLOW + "/mdt setup",
                ChatColor.GRAY + "puis cliquez sur un bloc pour",
                ChatColor.GRAY + "placer un marchand."));
        }

        // Légende des types
        inv.setItem(46, createItem(Material.VILLAGER_SPAWN_EGG,
            ChatColor.YELLOW + "Petit Vendeur (Global)",
            ChatColor.GRAY + "Vend un peu de tout"));

        inv.setItem(47, createItem(Material.BRICK,
            ChatColor.GRAY + "Grand Marchand (Blocs)",
            ChatColor.GRAY + "Blocs et outils"));

        inv.setItem(48, createItem(Material.IRON_SWORD,
            ChatColor.RED + "Grand Marchand (Armes)",
            ChatColor.GRAY + "Épées et arcs"));

        // Bouton supprimer tout
        if (!merchants.isEmpty()) {
            inv.setItem(49, createItem(Material.TNT,
                ChatColor.RED + "Supprimer TOUS",
                "",
                ChatColor.GRAY + "Clic pour supprimer tous",
                ChatColor.GRAY + "les marchands configurés."));
        }

        inv.setItem(50, createItem(Material.IRON_CHESTPLATE,
            ChatColor.BLUE + "Grand Marchand (Armures)",
            ChatColor.GRAY + "Armures et protections"));

        inv.setItem(51, createItem(Material.TNT,
            ChatColor.LIGHT_PURPLE + "Grand Marchand (Spécial)",
            ChatColor.GRAY + "Potions et TNT"));

        // Bouton retour
        inv.setItem(45, createItem(Material.ARROW,
            ChatColor.YELLOW + "← Retour",
            "",
            ChatColor.GRAY + "Fermer ce menu"));

        player.openInventory(inv);
    }

    private Material getMerchantIcon(MerchantType type) {
        return switch (type) {
            case GLOBAL -> Material.VILLAGER_SPAWN_EGG;
            case BLOCKS -> Material.BRICK;
            case WEAPONS -> Material.IRON_SWORD;
            case ARMOR -> Material.IRON_CHESTPLATE;
            case SPECIAL -> Material.TNT;
        };
    }

    private String getMerchantTypeName(MerchantType type) {
        return switch (type) {
            case GLOBAL -> "Petit Vendeur";
            case BLOCKS -> "Blocs & Outils";
            case WEAPONS -> "Armes";
            case ARMOR -> "Armures";
            case SPECIAL -> "Spécial";
        };
    }

    private ChatColor getMerchantColor(MerchantType type) {
        return switch (type) {
            case GLOBAL -> ChatColor.YELLOW;
            case BLOCKS -> ChatColor.GRAY;
            case WEAPONS -> ChatColor.RED;
            case ARMOR -> ChatColor.BLUE;
            case SPECIAL -> ChatColor.LIGHT_PURPLE;
        };
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(TITLE)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR ||
            clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        int slot = event.getRawSlot();

        // Bouton retour
        if (slot == 45) {
            player.closeInventory();
            return;
        }

        // Supprimer tout (slot 49 avec TNT qui n'est pas dans les légendes)
        if (slot == 49 && clicked.getType() == Material.TNT) {
            ItemMeta meta = clicked.getItemMeta();
            if (meta != null && meta.getDisplayName().contains("Supprimer")) {
                config.clearMerchants();
                player.sendMessage(ChatColor.RED + "✓ Tous les marchands ont été supprimés!");
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1f, 1f);
                open(player);
                return;
            }
        }

        // Supprimer un marchand spécifique (les icônes de marchands)
        if (isMerchantIcon(clicked.getType())) {
            ItemMeta meta = clicked.getItemMeta();
            if (meta != null && meta.getLore() != null) {
                for (String line : meta.getLore()) {
                    if (line.contains("index:")) {
                        try {
                            int index = Integer.parseInt(ChatColor.stripColor(line).replace("index:", ""));
                            List<String> merchantsList = new ArrayList<>(config.getFileConfig().getStringList("locations.merchants"));
                            if (index >= 0 && index < merchantsList.size()) {
                                merchantsList.remove(index);
                                config.getFileConfig().set("locations.merchants", merchantsList);
                                config.saveConfig();
                                player.sendMessage(ChatColor.YELLOW + "✓ Marchand supprimé!");
                                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
                                open(player);
                            }
                        } catch (Exception e) {
                            // Ignorer
                        }
                        break;
                    }
                }
            }
        }
    }

    private boolean isMerchantIcon(Material material) {
        return material == Material.VILLAGER_SPAWN_EGG ||
               material == Material.BRICK ||
               material == Material.IRON_SWORD ||
               material == Material.IRON_CHESTPLATE ||
               material == Material.TNT;
    }

    private ItemStack createItem(Material material, String name, String... lore) {
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
}
