package com.gravityyfh.entreprisemanager;

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
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class EntrepriseGUI implements Listener {

    private final EntrepriseManager plugin;
    private final EntrepriseManagerLogic entrepriseLogic;
    private final Map<Player, String> selectedGerant = new HashMap<>();
    private final Map<Player, String> selectedType = new HashMap<>();
    private final Map<UUID, Long> clickTimestamps = new HashMap<>(); // Horodatages des clics des joueurs

    private static final long CLICK_DELAY = 500; // Délai en millisecondes pour ignorer les clics répétés

    public EntrepriseGUI(EntrepriseManager plugin, EntrepriseManagerLogic entrepriseLogic) {
        this.plugin = plugin;
        this.entrepriseLogic = entrepriseLogic;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_BLUE + "Menu Entreprise");

        inv.setItem(10, createMenuItem(Material.PAPER, ChatColor.GOLD + "Créer une entreprise"));
        inv.setItem(12, createMenuItem(Material.BOOK, ChatColor.GOLD + "Liste des entreprises"));
        inv.setItem(14, createMenuItem(Material.CHEST, ChatColor.GOLD + "Mes entreprises"));
        if (player.hasPermission("entreprisemanager.admin")) {
            inv.setItem(16, createMenuItem(Material.EMERALD, ChatColor.GOLD + "Admin"));
        }

        player.openInventory(inv);
    }

    private ItemStack createMenuItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPlayerHead(String playerName) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerName));
        meta.setDisplayName(ChatColor.GOLD + playerName);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();

        // Vérifier le délai entre les clics pour éviter les exécutions multiples
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        if (clickTimestamps.containsKey(playerId)) {
            long lastClickTime = clickTimestamps.get(playerId);
            if (currentTime - lastClickTime < CLICK_DELAY) {
                return; // Ignorer le clic si le délai est trop court
            }
        }
        clickTimestamps.put(playerId, currentTime);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return;
        }

        String title = event.getView().getTitle();
        Material type = clickedItem.getType();

        if (title.equals(ChatColor.DARK_BLUE + "Menu Entreprise")) {
            event.setCancelled(true);
            handleMainMenuClick(player, type);
        } else if (title.equals(ChatColor.DARK_BLUE + "Créer une entreprise")) {
            event.setCancelled(true);
            handleCreateEntrepriseClick(player, clickedItem);
        } else if (title.equals(ChatColor.DARK_BLUE + "Sélectionnez un gérant")) {
            event.setCancelled(true);
            handleSelectGerantClick(player, clickedItem);
        } else if (title.equals(ChatColor.DARK_BLUE + "Sélectionnez un type")) {
            event.setCancelled(true);
            handleSelectTypeClick(player, clickedItem);
        } else if (title.equals(ChatColor.DARK_BLUE + "Mes entreprises")) {
            event.setCancelled(true);
            handleMyEntreprisesClick(player, clickedItem);
        } else if (title.equals(ChatColor.DARK_BLUE + "Liste des villes")) {
            event.setCancelled(true);
            handleTownSelectionClick(player, clickedItem);
        } else if (title.startsWith(ChatColor.DARK_BLUE + "Liste des entreprises - ")) { // Gestion des clics pour ce menu
            event.setCancelled(true);
            handleListEntreprisesClick(player, clickedItem);
        } else if (title.equals(ChatColor.DARK_BLUE + "Admin")) {
            event.setCancelled(true);
            handleAdminMenuClick(player, type);
        }
    }

    private void handleMainMenuClick(Player player, Material type) {
        switch (type) {
            case PAPER:
                if (entrepriseLogic.estMaire(player)) {
                    openCreateEntrepriseMenu(player);
                } else {
                    player.sendMessage(ChatColor.RED + "Vous devez être le maire de votre ville pour créer une entreprise.");
                }
                break;
            case BOOK:
                openListTownsMenu(player);
                break;
            case CHEST:
                openMyEntreprisesMenu(player);
                break;
            case EMERALD:
                if (player.hasPermission("entreprisemanager.admin")) {
                    openAdminMenu(player);
                } else {
                    player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission d'accéder à cette section.");
                }
                break;
        }
    }

    private void openCreateEntrepriseMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_BLUE + "Créer une entreprise");

        inv.setItem(11, createMenuItem(Material.NAME_TAG, ChatColor.GREEN + "Nom du gérant"));
        inv.setItem(13, createMenuItem(Material.ANVIL, ChatColor.GREEN + "Type d'entreprise"));
        inv.setItem(15, createMenuItem(Material.WRITABLE_BOOK, ChatColor.GREEN + "Créer"));
        inv.setItem(22, createMenuItem(Material.BARRIER, ChatColor.RED + "Retour"));

        player.openInventory(inv);
    }

    private void openSelectGerantMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Sélectionnez un gérant");

        Collection<String> playersInTown = entrepriseLogic.getPlayersInMayorTown(player);
        for (String playerName : playersInTown) {
            inv.addItem(createPlayerHead(playerName));
        }
        inv.setItem(49, createMenuItem(Material.BARRIER, ChatColor.RED + "Retour"));

        player.openInventory(inv);
    }

    private void openSelectTypeMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Sélectionnez un type");

        for (String type : entrepriseLogic.getTypesEntreprise()) {
            inv.addItem(createMenuItem(Material.PAPER, ChatColor.GOLD + type));
        }
        inv.setItem(49, createMenuItem(Material.BARRIER, ChatColor.RED + "Retour"));

        player.openInventory(inv);
    }

    private void openListTownsMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Liste des villes");

        for (String ville : entrepriseLogic.getAllTowns()) {
            inv.addItem(createMenuItem(Material.PAPER, ChatColor.GOLD + ville));
        }
        inv.setItem(49, createMenuItem(Material.BARRIER, ChatColor.RED + "Retour"));

        player.openInventory(inv);
    }

    private void openListEntreprisesMenu(Player player, String ville) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Liste des entreprises - " + ville);

        for (EntrepriseManagerLogic.Entreprise entreprise : entrepriseLogic.getEntreprisesByVille(ville)) {
            ItemStack item = createMenuItem(Material.PAPER, ChatColor.GOLD + entreprise.getNom());
            ItemMeta meta = item.getItemMeta();
            meta.setLore(List.of(
                    ChatColor.YELLOW + "Gérant: " + entreprise.getGerant(),
                    ChatColor.YELLOW + "Type: " + entreprise.getType()
            ));
            item.setItemMeta(meta);
            inv.addItem(item);
        }
        inv.setItem(49, createMenuItem(Material.BARRIER, ChatColor.RED + "Retour"));

        player.openInventory(inv);
    }

    private void openMyEntreprisesMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_BLUE + "Mes entreprises");

        for (EntrepriseManagerLogic.Entreprise entreprise : entrepriseLogic.getEntrepriseDuGerant(player.getName())) {
            ItemStack item = createMenuItem(Material.CHEST, ChatColor.GOLD + entreprise.getNom());
            ItemMeta meta = item.getItemMeta();
            meta.setLore(List.of(
                    ChatColor.YELLOW + "Gérant: " + entreprise.getGerant(),
                    ChatColor.YELLOW + "Type: " + entreprise.getType()
            ));
            item.setItemMeta(meta);
            inv.addItem(item);
        }
        inv.setItem(49, createMenuItem(Material.BARRIER, ChatColor.RED + "Retour"));

        player.openInventory(inv);
    }

    private void openAdminMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_BLUE + "Admin");

        inv.setItem(11, createMenuItem(Material.EMERALD, ChatColor.RED + "Forcer les paiements"));
        inv.setItem(15, createMenuItem(Material.REDSTONE, ChatColor.RED + "Recharger le plugin"));
        inv.setItem(22, createMenuItem(Material.BARRIER, ChatColor.RED + "Retour"));

        player.openInventory(inv);
    }

    private void handleCreateEntrepriseClick(Player player, ItemStack clickedItem) {
        switch (clickedItem.getType()) {
            case NAME_TAG:
                openSelectGerantMenu(player);
                break;
            case ANVIL:
                openSelectTypeMenu(player);
                break;
            case WRITABLE_BOOK:
                String gerant = selectedGerant.get(player);
                String type = selectedType.get(player);
                if (gerant != null && type != null) {
                    player.performCommand("entreprise create " + gerant + " " + type);
                    player.closeInventory();
                } else {
                    player.sendMessage(ChatColor.RED + "Vous devez sélectionner un gérant et un type avant de créer l'entreprise.");
                }
                break;
            case BARRIER:
                openMainMenu(player);
                break;
        }
    }

    private void handleSelectGerantClick(Player player, ItemStack clickedItem) {
        if (clickedItem.getType() == Material.BARRIER) {
            openCreateEntrepriseMenu(player);
            return;
        }

        String gerant = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        selectedGerant.put(player, gerant);
        player.sendMessage(ChatColor.GREEN + "Gérant sélectionné: " + gerant);
        openCreateEntrepriseMenu(player);
    }

    private void handleSelectTypeClick(Player player, ItemStack clickedItem) {
        if (clickedItem.getType() == Material.BARRIER) {
            openCreateEntrepriseMenu(player);
            return;
        }

        String type = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        selectedType.put(player, type);
        player.sendMessage(ChatColor.GREEN + "Type sélectionné: " + type);
        openCreateEntrepriseMenu(player);
    }

    private void handleMyEntreprisesClick(Player player, ItemStack clickedItem) {
        if (clickedItem.getType() == Material.BARRIER) {
            openMainMenu(player);
            return;
        }

        String entrepriseNom = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(entrepriseNom);
        if (entreprise != null) {
            String command = String.format("entreprise info %s %s", entreprise.getGerant(), entreprise.getType());
            player.performCommand(command);
            player.closeInventory();
        } else {
            player.sendMessage(ChatColor.RED + "Entreprise non trouvée.");
        }
    }

    private void handleListEntreprisesClick(Player player, ItemStack clickedItem) {
        if (clickedItem.getType() == Material.BARRIER) {
            openListTownsMenu(player);
            return;
        }

        String entrepriseNom = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(entrepriseNom);
        if (entreprise != null) {
            String command = String.format("entreprise info %s %s", entreprise.getGerant(), entreprise.getType());
            player.performCommand(command);
            player.closeInventory();
        } else {
            player.sendMessage(ChatColor.RED + "Entreprise non trouvée.");
        }
    }

    private void handleTownSelectionClick(Player player, ItemStack clickedItem) {
        if (clickedItem.getType() == Material.BARRIER) {
            openMainMenu(player);
            return;
        }

        String ville = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
        openListEntreprisesMenu(player, ville);
    }

    private void handleAdminMenuClick(Player player, Material type) {
        switch (type) {
            case EMERALD:
                entrepriseLogic.traiterPaiementsJournaliers();
                player.sendMessage(ChatColor.GREEN + "Paiements journaliers forcés.");
                player.closeInventory();
                break;
            case REDSTONE:
                plugin.reloadPlugin();
                player.sendMessage(ChatColor.GREEN + "Plugin rechargé.");
                player.closeInventory();
                break;
            case BARRIER:
                openMainMenu(player);
                break;
        }
    }
}
