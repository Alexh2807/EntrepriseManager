package com.gravityyfh.roleplaycity.mdt.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.mdt.config.MDTConfig;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
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
 * GUI pour voir et gérer tous les lits neutres configurés
 */
public class MDTNeutralBedsGUI implements Listener {
    private final RoleplayCity plugin;
    private final MDTConfig config;

    private static final String TITLE = "§8Lits Neutres MDT";

    public MDTNeutralBedsGUI(RoleplayCity plugin, MDTConfig config) {
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

        // Charger les lits neutres depuis la config
        List<String> neutralBeds = config.getFileConfig().getStringList("locations.beds.neutral");

        // Afficher les lits (max 28 pour laisser de la place)
        int slot = 10;
        int bedIndex = 0;
        for (String entry : neutralBeds) {
            if (slot > 43 || bedIndex >= 28) break; // Limiter

            try {
                String[] parts = entry.split(",");
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);

                ItemStack bedItem = createItem(Material.WHITE_BED,
                    ChatColor.WHITE + "Lit Neutre #" + (bedIndex + 1),
                    "",
                    ChatColor.GRAY + "Position: " + ChatColor.YELLOW + x + ", " + y + ", " + z,
                    ChatColor.GRAY + "Bonus: " + ChatColor.RED + "+2 cœurs",
                    "",
                    ChatColor.RED + "Clic pour supprimer");

                // Stocker l'index dans le lore pour identification
                ItemMeta meta = bedItem.getItemMeta();
                if (meta != null) {
                    List<String> lore = meta.getLore();
                    if (lore == null) lore = new ArrayList<>();
                    lore.add(ChatColor.BLACK + "index:" + bedIndex);
                    meta.setLore(lore);
                    bedItem.setItemMeta(meta);
                }

                inv.setItem(slot, bedItem);

                // Passer à la prochaine position (éviter les bords)
                slot++;
                if (slot % 9 == 8) slot += 2; // Sauter les colonnes de bord

                bedIndex++;
            } catch (Exception e) {
                // Ignorer les entrées invalides
            }
        }

        // Info si aucun lit
        if (neutralBeds.isEmpty()) {
            inv.setItem(22, createItem(Material.BARRIER,
                ChatColor.RED + "Aucun lit neutre",
                "",
                ChatColor.GRAY + "Cliquez sur le bouton vert",
                ChatColor.GRAY + "pour ajouter un lit neutre."));
        }

        // Bouton AJOUTER un lit neutre
        inv.setItem(47, createItem(Material.LIME_DYE,
            ChatColor.GREEN + "§l+ Ajouter un lit neutre",
            "",
            ChatColor.GRAY + "Clic pour fermer ce menu,",
            ChatColor.GRAY + "puis fais " + ChatColor.YELLOW + "clic droit sur un lit",
            ChatColor.GRAY + "dans le monde MDT pour",
            ChatColor.GRAY + "le définir comme neutre.",
            "",
            ChatColor.YELLOW + "Bonus: " + ChatColor.RED + "+2 cœurs"));

        // Bouton supprimer tout
        if (!neutralBeds.isEmpty()) {
            inv.setItem(49, createItem(Material.TNT,
                ChatColor.RED + "Supprimer TOUS les lits neutres",
                "",
                ChatColor.GRAY + "Clic pour supprimer tous",
                ChatColor.GRAY + "les lits neutres configurés."));
        }

        // Bouton retour
        inv.setItem(45, createItem(Material.ARROW,
            ChatColor.YELLOW + "← Retour",
            "",
            ChatColor.GRAY + "Fermer ce menu"));

        player.openInventory(inv);
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

        // Bouton ajouter lit neutre
        if (slot == 47 && clicked.getType() == Material.LIME_DYE) {
            MDTBedSelectionGUI.setPendingType(player, "NEUTRAL");
            player.closeInventory();
            player.sendMessage("");
            player.sendMessage(ChatColor.GREEN + "➤ " + ChatColor.WHITE + "Mode Lit NEUTRE activé!");
            player.sendMessage(ChatColor.GRAY + "  Fais " + ChatColor.YELLOW + "clic droit sur un lit" + ChatColor.GRAY + " dans le monde MDT");
            player.sendMessage(ChatColor.GRAY + "  pour le configurer comme lit neutre (" + ChatColor.RED + "+2 cœurs" + ChatColor.GRAY + ").");
            player.sendMessage("");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        // Supprimer tout
        if (slot == 49 && clicked.getType() == Material.TNT) {
            config.getFileConfig().set("locations.beds.neutral", new ArrayList<>());
            config.saveConfig();
            player.sendMessage(ChatColor.RED + "✓ Tous les lits neutres ont été supprimés!");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1f, 1f);
            open(player); // Rafraîchir
            return;
        }

        // Supprimer un lit spécifique
        if (clicked.getType() == Material.WHITE_BED) {
            ItemMeta meta = clicked.getItemMeta();
            if (meta != null && meta.getLore() != null) {
                for (String line : meta.getLore()) {
                    if (line.contains("index:")) {
                        try {
                            int index = Integer.parseInt(ChatColor.stripColor(line).replace("index:", ""));
                            List<String> beds = new ArrayList<>(config.getFileConfig().getStringList("locations.beds.neutral"));
                            if (index >= 0 && index < beds.size()) {
                                beds.remove(index);
                                config.getFileConfig().set("locations.beds.neutral", beds);
                                config.saveConfig();
                                player.sendMessage(ChatColor.YELLOW + "✓ Lit neutre supprimé!");
                                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
                                open(player); // Rafraîchir
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
