package com.gravityyfh.roleplaycity.mdt.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.mdt.MDTRushManager;
import com.gravityyfh.roleplaycity.mdt.data.MDTGame;
import com.gravityyfh.roleplaycity.mdt.data.MDTPlayer;
import com.gravityyfh.roleplaycity.mdt.data.MDTTeam;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * GUI pour le lobby MDT Rush - Sélection d'équipe
 */
public class MDTLobbyGUI implements Listener {
    private final RoleplayCity plugin;
    private final MDTRushManager manager;

    private static final String TITLE = ChatColor.DARK_GRAY + "⚔ " + ChatColor.RED + "MDT Rush" + ChatColor.DARK_GRAY + " - Équipes";
    private static final int INVENTORY_SIZE = 27; // 3 lignes

    // Slots
    private static final int RED_TEAM_SLOT = 11;
    private static final int BLUE_TEAM_SLOT = 15;
    private static final int INFO_SLOT = 4;
    private static final int LEAVE_SLOT = 22;

    // Tracking des joueurs avec le GUI ouvert
    private final Set<UUID> openGUIs = new HashSet<>();

    public MDTLobbyGUI(RoleplayCity plugin, MDTRushManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Ouvre le GUI de sélection d'équipe pour un joueur
     */
    public void openTeamSelection(Player player) {
        MDTGame game = manager.getCurrentGame();
        if (game == null) return;

        Inventory inv = Bukkit.createInventory(null, INVENTORY_SIZE, TITLE);

        // Remplir le fond
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            inv.setItem(i, glass);
        }

        // Équipe Rouge
        int redCount = game.getTeamPlayerCount(MDTTeam.RED);
        int maxPerTeam = manager.getConfig().getMaxPlayersPerTeam();
        boolean redFull = redCount >= maxPerTeam;

        ItemStack redWool = createItem(
                redFull ? Material.RED_TERRACOTTA : Material.RED_WOOL,
                ChatColor.RED + "" + ChatColor.BOLD + "Équipe Rouge",
                "",
                ChatColor.GRAY + "Joueurs: " + ChatColor.WHITE + redCount + "/" + maxPerTeam,
                "",
                redFull ? ChatColor.RED + "✗ Équipe pleine" : ChatColor.GREEN + "▶ Cliquez pour rejoindre"
        );
        inv.setItem(RED_TEAM_SLOT, redWool);

        // Équipe Bleue
        int blueCount = game.getTeamPlayerCount(MDTTeam.BLUE);
        boolean blueFull = blueCount >= maxPerTeam;

        ItemStack blueWool = createItem(
                blueFull ? Material.BLUE_TERRACOTTA : Material.BLUE_WOOL,
                ChatColor.BLUE + "" + ChatColor.BOLD + "Équipe Bleue",
                "",
                ChatColor.GRAY + "Joueurs: " + ChatColor.WHITE + blueCount + "/" + maxPerTeam,
                "",
                blueFull ? ChatColor.RED + "✗ Équipe pleine" : ChatColor.GREEN + "▶ Cliquez pour rejoindre"
        );
        inv.setItem(BLUE_TEAM_SLOT, blueWool);

        // Info
        MDTPlayer mdtPlayer = game.getPlayer(player.getUniqueId());
        String currentTeam = mdtPlayer != null && mdtPlayer.hasTeam()
                ? mdtPlayer.getTeam().getColoredName()
                : ChatColor.GRAY + "Aucune";

        ItemStack info = createItem(
                Material.NETHER_STAR,
                ChatColor.GOLD + "" + ChatColor.BOLD + "MDT Rush",
                "",
                ChatColor.GRAY + "Ton équipe: " + currentTeam,
                ChatColor.GRAY + "Total joueurs: " + ChatColor.WHITE + game.getPlayerCount(),
                "",
                ChatColor.YELLOW + "Choisis ton équipe!",
                ChatColor.GRAY + "La partie commence bientôt..."
        );
        inv.setItem(INFO_SLOT, info);

        // Bouton quitter
        ItemStack leave = createItem(
                Material.BARRIER,
                ChatColor.RED + "Quitter le lobby",
                "",
                ChatColor.GRAY + "Retourne au spawn principal"
        );
        inv.setItem(LEAVE_SLOT, leave);

        player.openInventory(inv);
        openGUIs.add(player.getUniqueId());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();
        if (!title.equals(TITLE)) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int slot = event.getRawSlot();

        MDTGame game = manager.getCurrentGame();
        if (game == null) {
            player.closeInventory();
            return;
        }

        switch (slot) {
            case RED_TEAM_SLOT -> {
                if (manager.selectTeam(player, MDTTeam.RED)) {
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    player.closeInventory();
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
            }
            case BLUE_TEAM_SLOT -> {
                if (manager.selectTeam(player, MDTTeam.BLUE)) {
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    player.closeInventory();
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
            }
            case LEAVE_SLOT -> {
                player.closeInventory();
                manager.leaveGame(player);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        openGUIs.remove(player.getUniqueId());
    }

    /**
     * Met à jour tous les GUIs ouverts (appelé quand un joueur change d'équipe)
     */
    public void updateAllOpenGUIs() {
        for (UUID uuid : new HashSet<>(openGUIs)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                openTeamSelection(player);
            }
        }
    }

    /**
     * Crée un ItemStack avec nom et lore
     */
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
