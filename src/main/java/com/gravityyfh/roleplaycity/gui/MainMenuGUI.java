package com.gravityyfh.roleplaycity.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.identity.gui.IdentityGUI;
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
 * Menu Principal (/menu)
 * Hub central pour accéder à toutes les fonctionnalités
 */
public class MainMenuGUI implements Listener {

    private final RoleplayCity plugin;
    private final IdentityGUI identityGUI;
    
    private static final String TITLE = ChatColor.DARK_GRAY + "Menu Principal";

    public MainMenuGUI(RoleplayCity plugin) {
        this.plugin = plugin;
        this.identityGUI = new IdentityGUI(plugin, plugin.getIdentityManager());
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);

        // 1. Ville (/ville)
        ItemStack cityItem = createItem(Material.BEACON, ChatColor.GOLD + "Ma Ville", 
            ChatColor.GRAY + "Gérer votre ville, terrains,", ChatColor.GRAY + "invitations et économie.");
        inv.setItem(10, cityItem);

        // 2. Entreprise (/entreprise)
        ItemStack companyItem = createItem(Material.IRON_PICKAXE, ChatColor.BLUE + "Mon Entreprise", 
            ChatColor.GRAY + "Gérer votre entreprise, employés,", ChatColor.GRAY + "boutiques et finances.");
        inv.setItem(12, companyItem);

        // 3. Identité (Nouveau)
        ItemStack idItem = createItem(Material.NAME_TAG, ChatColor.GREEN + "Mon Identité", 
            ChatColor.GRAY + "Carte d'identité, état civil,", ChatColor.GRAY + "présentation aux autres joueurs.");
        inv.setItem(14, idItem);

        // 4. Paramètres / Aide
        ItemStack helpItem = createItem(Material.BOOK, ChatColor.YELLOW + "Aide & Commandes", 
            ChatColor.GRAY + "Liste des commandes utiles.");
        inv.setItem(16, helpItem);

        // Décoration (Vitre)
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, glass);
        }

        player.openInventory(inv);
    }

    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        List<String> loreList = new ArrayList<>();
        for (String s : lore) loreList.add(s);
        loreList.add("");
        loreList.add(ChatColor.YELLOW + "► Cliquez pour ouvrir");
        meta.setLore(loreList);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(TITLE)) return;
        event.setCancelled(true);
        
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        ItemStack current = event.getCurrentItem();
        if (current == null || !current.hasItemMeta()) return;
        
        String name = ChatColor.stripColor(current.getItemMeta().getDisplayName());
        
        if (name.equals("Ma Ville")) {
            player.performCommand("ville gui");
        } else if (name.equals("Mon Entreprise")) {
            // TODO: Ouvrir un GUI entreprise s'il existe, sinon commande help
            player.performCommand("entreprise"); 
        } else if (name.equals("Mon Identité")) {
            identityGUI.open(player);
        } else if (name.equals("Aide & Commandes")) {
            player.performCommand("help roleplaycity");
            player.closeInventory();
        }
    }
    
    public IdentityGUI getIdentityGUI() {
        return identityGUI;
    }
}
