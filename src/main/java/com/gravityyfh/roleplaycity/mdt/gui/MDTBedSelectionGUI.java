package com.gravityyfh.roleplaycity.mdt.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.mdt.config.MDTConfig;
import com.gravityyfh.roleplaycity.mdt.data.MDTTeam;
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
 * GUI qui s'ouvre quand on fait clic droit sur un lit pour le définir
 */
public class MDTBedSelectionGUI implements Listener {
    private final RoleplayCity plugin;
    private final MDTConfig config;

    private static final String TITLE = "§8Définir ce Lit";

    // Stocke la location du lit cliqué par joueur
    private final Map<UUID, Location> pendingBedLocations = new HashMap<>();

    // Stocke le type de lit en attente (RED, BLUE, ou NEUTRAL) - STATIC pour accès global
    private static final Map<UUID, String> pendingBedType = new HashMap<>();

    public MDTBedSelectionGUI(RoleplayCity plugin, MDTConfig config) {
        this.plugin = plugin;
        this.config = config;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Définit le type de lit en attente (appelé depuis le GUI principal)
     * @param player Le joueur
     * @param type "RED", "BLUE", ou "NEUTRAL"
     */
    public static void setPendingType(Player player, String type) {
        pendingBedType.put(player.getUniqueId(), type);
    }

    /**
     * Vérifie si le joueur a un type de lit en attente
     */
    public static boolean hasPendingType(UUID playerUuid) {
        return pendingBedType.containsKey(playerUuid);
    }

    /**
     * Récupère et supprime le type en attente
     */
    public static String getPendingType(UUID playerUuid) {
        return pendingBedType.remove(playerUuid);
    }

    // Méthodes d'instance pour compatibilité
    public void setPendingBedType(Player player, String type) {
        setPendingType(player, type);
    }

    public boolean hasPendingBedType(UUID playerUuid) {
        return hasPendingType(playerUuid);
    }

    /**
     * Applique directement le type de lit en attente sur la location donnée
     * @return true si appliqué, false si pas de type en attente
     */
    public boolean applyPendingBedType(Player player, Location bedLoc) {
        String type = getPendingType(player.getUniqueId());
        if (type == null) return false;

        switch (type) {
            case "RED" -> {
                config.setBedLocation(MDTTeam.RED, bedLoc);
                config.setTeamSpawn(MDTTeam.RED, bedLoc.clone().add(0.5, 0.1, 0.5));
                player.sendMessage("");
                player.sendMessage(ChatColor.RED + "✓ Lit de l'équipe ROUGE défini !");
                player.sendMessage(ChatColor.RED + "✓ Spawn de l'équipe ROUGE défini !");
                player.sendMessage(ChatColor.GRAY + "  Position: " + bedLoc.getBlockX() + ", " + bedLoc.getBlockY() + ", " + bedLoc.getBlockZ());
                player.sendMessage("");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            }
            case "BLUE" -> {
                config.setBedLocation(MDTTeam.BLUE, bedLoc);
                config.setTeamSpawn(MDTTeam.BLUE, bedLoc.clone().add(0.5, 0.1, 0.5));
                player.sendMessage("");
                player.sendMessage(ChatColor.BLUE + "✓ Lit de l'équipe BLEUE défini !");
                player.sendMessage(ChatColor.BLUE + "✓ Spawn de l'équipe BLEUE défini !");
                player.sendMessage(ChatColor.GRAY + "  Position: " + bedLoc.getBlockX() + ", " + bedLoc.getBlockY() + ", " + bedLoc.getBlockZ());
                player.sendMessage("");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            }
            case "NEUTRAL" -> {
                config.addNeutralBedLocation(bedLoc);
                player.sendMessage("");
                player.sendMessage(ChatColor.WHITE + "✓ Lit Neutre ajouté !");
                player.sendMessage(ChatColor.GRAY + "  Bonus: " + ChatColor.RED + "+2 cœurs");
                player.sendMessage(ChatColor.GRAY + "  Position: " + bedLoc.getBlockX() + ", " + bedLoc.getBlockY() + ", " + bedLoc.getBlockZ());
                player.sendMessage("");
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    public void open(Player player, Location bedLocation) {
        pendingBedLocations.put(player.getUniqueId(), bedLocation);

        Inventory inv = Bukkit.createInventory(null, 27, TITLE);

        // Fond
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, glass);
        }

        // Lit Équipe Rouge
        inv.setItem(10, createItem(Material.RED_BED,
            ChatColor.RED + "" + ChatColor.BOLD + "Lit Équipe Rouge",
            "",
            ChatColor.GRAY + "Définit ce lit comme le lit",
            ChatColor.GRAY + "de respawn de l'équipe " + ChatColor.RED + "ROUGE",
            "",
            ChatColor.YELLOW + "Clic pour sélectionner"));

        // Lit Neutre (bonus cœurs)
        inv.setItem(13, createItem(Material.WHITE_BED,
            ChatColor.WHITE + "" + ChatColor.BOLD + "Lit Neutre (+2❤)",
            "",
            ChatColor.GRAY + "Définit ce lit comme un lit",
            ChatColor.GRAY + "neutre donnant " + ChatColor.RED + "+2 cœurs",
            "",
            ChatColor.YELLOW + "Clic pour sélectionner"));

        // Lit Équipe Bleue
        inv.setItem(16, createItem(Material.BLUE_BED,
            ChatColor.BLUE + "" + ChatColor.BOLD + "Lit Équipe Bleue",
            "",
            ChatColor.GRAY + "Définit ce lit comme le lit",
            ChatColor.GRAY + "de respawn de l'équipe " + ChatColor.BLUE + "BLEUE",
            "",
            ChatColor.YELLOW + "Clic pour sélectionner"));

        // Annuler
        inv.setItem(22, createItem(Material.BARRIER,
            ChatColor.RED + "Annuler",
            "",
            ChatColor.GRAY + "Fermer sans modifier"));

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(TITLE)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        Location bedLoc = pendingBedLocations.get(player.getUniqueId());
        if (bedLoc == null) {
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();

        switch (slot) {
            case 10 -> { // Lit Rouge
                // Définir le lit ET le spawn de l'équipe rouge
                config.setBedLocation(MDTTeam.RED, bedLoc);
                config.setTeamSpawn(MDTTeam.RED, bedLoc.clone().add(0.5, 0.1, 0.5));
                player.sendMessage("");
                player.sendMessage(ChatColor.RED + "✓ Lit de l'équipe ROUGE défini !");
                player.sendMessage(ChatColor.RED + "✓ Spawn de l'équipe ROUGE défini !");
                player.sendMessage(ChatColor.GRAY + "  Position: " + bedLoc.getBlockX() + ", " + bedLoc.getBlockY() + ", " + bedLoc.getBlockZ());
                player.sendMessage("");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                player.closeInventory();
            }
            case 13 -> { // Lit Neutre
                config.addNeutralBedLocation(bedLoc);
                player.sendMessage("");
                player.sendMessage(ChatColor.WHITE + "✓ Lit Neutre ajouté !");
                player.sendMessage(ChatColor.GRAY + "  Bonus: " + ChatColor.RED + "+2 cœurs");
                player.sendMessage(ChatColor.GRAY + "  Position: " + bedLoc.getBlockX() + ", " + bedLoc.getBlockY() + ", " + bedLoc.getBlockZ());
                player.sendMessage("");
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                player.closeInventory();
            }
            case 16 -> { // Lit Bleu
                // Définir le lit ET le spawn de l'équipe bleue
                config.setBedLocation(MDTTeam.BLUE, bedLoc);
                config.setTeamSpawn(MDTTeam.BLUE, bedLoc.clone().add(0.5, 0.1, 0.5));
                player.sendMessage("");
                player.sendMessage(ChatColor.BLUE + "✓ Lit de l'équipe BLEUE défini !");
                player.sendMessage(ChatColor.BLUE + "✓ Spawn de l'équipe BLEUE défini !");
                player.sendMessage(ChatColor.GRAY + "  Position: " + bedLoc.getBlockX() + ", " + bedLoc.getBlockY() + ", " + bedLoc.getBlockZ());
                player.sendMessage("");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                player.closeInventory();
            }
            case 22 -> { // Annuler
                player.closeInventory();
            }
        }

        pendingBedLocations.remove(player.getUniqueId());
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
