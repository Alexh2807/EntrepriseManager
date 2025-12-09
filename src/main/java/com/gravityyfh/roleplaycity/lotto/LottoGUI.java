package com.gravityyfh.roleplaycity.lotto;

import com.gravityyfh.roleplaycity.RoleplayCity;
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

public class LottoGUI implements Listener {

    private final LottoManager lottoManager;
    private static final String GUI_TITLE = ChatColor.DARK_GREEN + "🎲 Loto Horaire";

    public LottoGUI(RoleplayCity plugin, LottoManager lottoManager) {
        this.lottoManager = lottoManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);

        // Info Cagnotte
        ItemStack info = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(ChatColor.GOLD + "Cagnotte Actuelle");
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.GRAY + "Montant à gagner : " + ChatColor.GREEN + String.format("%,.2f€", lottoManager.getCurrentPot()));
        infoLore.add(ChatColor.GRAY + "Vos tickets : " + ChatColor.YELLOW + lottoManager.getPlayerTicketCount(player.getUniqueId()));
        infoLore.add("");
        
        if (lottoManager.getState() == LottoManager.LottoState.OPEN) {
            infoLore.add(ChatColor.GREEN + "✔ Vente OUVERTE");
        } else if (lottoManager.getState() == LottoManager.LottoState.CLOSED) {
            infoLore.add(ChatColor.RED + "✖ Vente TERMINÉE (Attente tirage)");
        } else {
            infoLore.add(ChatColor.GRAY + "⏳ En attente du prochain tour (H:15)");
        }
        
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        inv.setItem(4, info);

        if (lottoManager.getState() == LottoManager.LottoState.OPEN) {
            // Acheter 1
            inv.setItem(11, createBuyItem(1, Material.PAPER));
            // Acheter 5
            inv.setItem(13, createBuyItem(5, Material.PAPER));
            // Acheter 10
            inv.setItem(15, createBuyItem(10, Material.PAPER));
        } else {
            // Items grisés
            ItemStack closed = new ItemStack(Material.BARRIER);
            ItemMeta meta = closed.getItemMeta();
            meta.setDisplayName(ChatColor.RED + "Guichet Fermé");
            closed.setItemMeta(meta);
            inv.setItem(13, closed);
        }

        player.openInventory(inv);
    }

    private ItemStack createBuyItem(int amount, Material material) {
        if (material == null) material = Material.PAPER; // Fallback
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GREEN + "Acheter " + amount + " Ticket(s)");
        List<String> lore = new ArrayList<>();
        double price = amount * lottoManager.getTicketPrice();
        lore.add(ChatColor.GRAY + "Prix : " + ChatColor.GOLD + String.format("%,.2f€", price));
        lore.add("");
        lore.add(ChatColor.YELLOW + "Cliquez pour acheter !");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        
        event.setCancelled(true);
        
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        ItemStack current = event.getCurrentItem();
        if (current == null || !current.hasItemMeta()) return;

        String name = ChatColor.stripColor(current.getItemMeta().getDisplayName());

        if (name.startsWith("Acheter ")) {
            // Parse amount from "Acheter X Ticket(s)"
            try {
                String[] parts = name.split(" ");
                int amount = Integer.parseInt(parts[1]);
                
                if (lottoManager.buyTickets(player, amount)) {
                    open(player); // Refresh GUI
                } else {
                    player.closeInventory();
                }
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }
    }
}
