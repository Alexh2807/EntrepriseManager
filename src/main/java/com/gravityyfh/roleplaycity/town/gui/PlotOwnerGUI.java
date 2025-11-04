package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.*;
import com.gravityyfh.roleplaycity.town.manager.ClaimManager;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * GUI pour que les propriétaires gèrent leurs plots
 */
public class PlotOwnerGUI implements Listener {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final ClaimManager claimManager;

    private static final String OWNER_MENU_TITLE = ChatColor.GOLD + "⚙️ Mon Plot";
    private static final String PERMISSIONS_TITLE = ChatColor.BLUE + "👥 Gérer Permissions";
    private static final String PLAYER_PERMS_TITLE = ChatColor.AQUA + "📝 Permissions de ";
    private static final String FLAGS_TITLE = ChatColor.DARK_PURPLE + "🚩 Flags de Protection";

    private final Map<UUID, PermissionContext> pendingActions;

    public PlotOwnerGUI(RoleplayCity plugin, TownManager townManager, ClaimManager claimManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.claimManager = claimManager;
        this.pendingActions = new HashMap<>();
    }

    /**
     * Menu principal du propriétaire
     */
    public void openOwnerMenu(Player player) {
        Chunk currentChunk = player.getLocation().getChunk();
        Plot plot = claimManager.getPlotAt(currentChunk);

        if (plot == null) {
            player.sendMessage(ChatColor.RED + "Vous n'êtes pas sur un plot.");
            return;
        }

        // Vérifier si le joueur est propriétaire ou locataire
        if (!plot.isOwnedBy(player.getUniqueId()) && !plot.isRentedBy(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Vous n'êtes pas propriétaire ou locataire de ce plot.");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, OWNER_MENU_TITLE);

        // Informations
        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(ChatColor.GOLD + "Informations du Plot");
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.GRAY + "Position: " + ChatColor.WHITE + plot.getCoordinates());
        infoLore.add(ChatColor.GRAY + "Type: " + ChatColor.AQUA + plot.getType().getDisplayName());
        if (plot.isOwnedBy(player.getUniqueId())) {
            infoLore.add(ChatColor.GREEN + "Vous êtes propriétaire");
        } else {
            infoLore.add(ChatColor.YELLOW + "Vous êtes locataire");
        }
        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);
        inv.setItem(4, infoItem);

        // Gérer les permissions
        ItemStack permsItem = new ItemStack(Material.PAPER);
        ItemMeta permsMeta = permsItem.getItemMeta();
        permsMeta.setDisplayName(ChatColor.BLUE + "Gérer les Permissions");
        List<String> permsLore = new ArrayList<>();
        permsLore.add(ChatColor.GRAY + "Donner accès à d'autres joueurs");
        permsLore.add(ChatColor.GRAY + "Gérer qui peut construire, interagir...");
        permsLore.add("");
        permsLore.add(ChatColor.YELLOW + "Cliquez pour ouvrir");
        permsMeta.setLore(permsLore);
        permsItem.setItemMeta(permsMeta);
        inv.setItem(11, permsItem);

        // Flags de protection
        ItemStack flagsItem = new ItemStack(Material.SHIELD);
        ItemMeta flagsMeta = flagsItem.getItemMeta();
        flagsMeta.setDisplayName(ChatColor.DARK_PURPLE + "Flags de Protection");
        List<String> flagsLore = new ArrayList<>();
        flagsLore.add(ChatColor.GRAY + "Configurer PVP, explosions...");
        flagsLore.add(ChatColor.GRAY + "Protections avancées");
        flagsLore.add("");
        flagsLore.add(ChatColor.YELLOW + "Cliquez pour ouvrir");
        flagsMeta.setLore(flagsLore);
        flagsItem.setItemMeta(flagsMeta);
        inv.setItem(13, flagsItem);

        // Joueurs de confiance
        ItemStack trustItem = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta trustMeta = trustItem.getItemMeta();
        trustMeta.setDisplayName(ChatColor.GREEN + "Joueurs de Confiance");
        List<String> trustLore = new ArrayList<>();
        trustLore.add(ChatColor.GRAY + "Donner accès complet");
        trustLore.add(ChatColor.GRAY + "Total: " + ChatColor.WHITE + plot.getTrustedPlayers().size());
        trustLore.add("");
        trustLore.add(ChatColor.YELLOW + "Cliquez pour gérer");
        trustMeta.setLore(trustLore);
        trustItem.setItemMeta(trustMeta);
        inv.setItem(15, trustItem);

        // Retour à Mes Propriétés
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "← Retour à Mes Propriétés");
        List<String> backLore = new ArrayList<>();
        backLore.add(ChatColor.GRAY + "Voir tous vos terrains");
        backMeta.setLore(backLore);
        backItem.setItemMeta(backMeta);
        inv.setItem(22, backItem);

        player.openInventory(inv);
    }

    /**
     * Menu de gestion des permissions
     */
    public void openPermissionsMenu(Player player) {
        Chunk currentChunk = player.getLocation().getChunk();
        Plot plot = claimManager.getPlotAt(currentChunk);

        if (plot == null || (!plot.isOwnedBy(player.getUniqueId()) && !plot.isRentedBy(player.getUniqueId()))) {
            player.closeInventory();
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 54, PERMISSIONS_TITLE);

        // Ajouter un joueur
        ItemStack addItem = new ItemStack(Material.EMERALD);
        ItemMeta addMeta = addItem.getItemMeta();
        addMeta.setDisplayName(ChatColor.GREEN + "Ajouter un Joueur");
        List<String> addLore = new ArrayList<>();
        addLore.add(ChatColor.GRAY + "Donner des permissions");
        addLore.add(ChatColor.GRAY + "à un joueur spécifique");
        addLore.add("");
        addLore.add(ChatColor.YELLOW + "Cliquez pour ajouter");
        addMeta.setLore(addLore);
        addItem.setItemMeta(addMeta);
        inv.setItem(4, addItem);

        // Liste des joueurs avec permissions
        Map<UUID, Set<PlotPermission>> allPerms = plot.getAllPlayerPermissions();
        int slot = 9;
        for (Map.Entry<UUID, Set<PlotPermission>> entry : allPerms.entrySet()) {
            if (slot >= 45) break;

            UUID playerUuid = entry.getKey();
            Set<PlotPermission> perms = entry.getValue();
            Player target = Bukkit.getPlayer(playerUuid);
            String playerName = target != null ? target.getName() : playerUuid.toString().substring(0, 8);

            ItemStack playerItem = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta playerMeta = playerItem.getItemMeta();
            playerMeta.setDisplayName(ChatColor.YELLOW + playerName);
            List<String> playerLore = new ArrayList<>();
            playerLore.add(ChatColor.GRAY + "Permissions: " + ChatColor.WHITE + perms.size());
            for (PlotPermission perm : perms) {
                playerLore.add(ChatColor.GRAY + "  • " + ChatColor.GREEN + perm.getDisplayName());
            }
            playerLore.add("");
            playerLore.add(ChatColor.YELLOW + "Clic gauche: Modifier");
            playerLore.add(ChatColor.RED + "Clic droit: Retirer");
            playerMeta.setLore(playerLore);
            playerItem.setItemMeta(playerMeta);
            inv.setItem(slot++, playerItem);
        }

        // Retour
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(49, backItem);

        player.openInventory(inv);
    }

    /**
     * Menu de gestion des flags
     */
    public void openFlagsMenu(Player player) {
        Chunk currentChunk = player.getLocation().getChunk();
        Plot plot = claimManager.getPlotAt(currentChunk);

        if (plot == null || (!plot.isOwnedBy(player.getUniqueId()) && !plot.isRentedBy(player.getUniqueId()))) {
            player.closeInventory();
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 54, FLAGS_TITLE);

        int slot = 9;
        for (PlotFlag flag : PlotFlag.values()) {
            boolean enabled = plot.getFlag(flag);

            Material mat = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
            ItemStack flagItem = new ItemStack(mat);
            ItemMeta flagMeta = flagItem.getItemMeta();
            flagMeta.setDisplayName((enabled ? ChatColor.GREEN : ChatColor.RED) + flag.getDisplayName());
            List<String> flagLore = new ArrayList<>();
            flagLore.add(ChatColor.GRAY + flag.getDescription());
            flagLore.add("");
            flagLore.add(ChatColor.GRAY + "Statut: " + (enabled ? ChatColor.GREEN + "Activé" : ChatColor.RED + "Désactivé"));
            flagLore.add("");
            flagLore.add(ChatColor.YELLOW + "Cliquez pour basculer");
            flagMeta.setLore(flagLore);
            flagItem.setItemMeta(flagMeta);
            inv.setItem(slot++, flagItem);
        }

        // Réinitialiser tous les flags
        ItemStack resetItem = new ItemStack(Material.BARRIER);
        ItemMeta resetMeta = resetItem.getItemMeta();
        resetMeta.setDisplayName(ChatColor.RED + "Réinitialiser Tous les Flags");
        List<String> resetLore = new ArrayList<>();
        resetLore.add(ChatColor.GRAY + "Restaurer les valeurs par défaut");
        resetLore.add("");
        resetLore.add(ChatColor.YELLOW + "Cliquez pour réinitialiser");
        resetMeta.setLore(resetLore);
        resetItem.setItemMeta(resetMeta);
        inv.setItem(45, resetItem);

        // Retour
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(49, backItem);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();

        if (title.equals(OWNER_MENU_TITLE)) {
            handleOwnerMenuClick(event);
        } else if (title.equals(PERMISSIONS_TITLE)) {
            handlePermissionsMenuClick(event);
        } else if (title.equals(FLAGS_TITLE)) {
            handleFlagsMenuClick(event);
        }
    }

    private void handleOwnerMenuClick(InventoryClickEvent event) {
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        if (displayName.contains("Gérer les Permissions")) {
            player.closeInventory();
            openPermissionsMenu(player);
        } else if (displayName.contains("Flags de Protection")) {
            player.closeInventory();
            openFlagsMenu(player);
        } else if (displayName.contains("Joueurs de Confiance")) {
            player.sendMessage(ChatColor.YELLOW + "Fonctionnalité à venir: Gérer les joueurs de confiance");
            player.closeInventory();
        } else if (displayName.contains("Retour à Mes Propriétés")) {
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + "Utilisez " + ChatColor.WHITE + "/ville" +
                ChatColor.YELLOW + " pour accéder à vos propriétés");
        }
    }

    private void handlePermissionsMenuClick(InventoryClickEvent event) {
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        if (displayName.contains("Ajouter un Joueur")) {
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + "Fonctionnalité à venir: Ajouter un joueur");
        } else if (displayName.contains("Retour")) {
            player.closeInventory();
            openOwnerMenu(player);
        }
    }

    private void handleFlagsMenuClick(InventoryClickEvent event) {
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        Chunk currentChunk = player.getLocation().getChunk();
        Plot plot = claimManager.getPlotAt(currentChunk);

        if (plot == null) {
            player.closeInventory();
            return;
        }

        String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        if (displayName.contains("Réinitialiser Tous les Flags")) {
            plot.resetAllFlags();
            player.sendMessage(ChatColor.GREEN + "Tous les flags ont été réinitialisés.");

            // Sauvegarder immédiatement
            plugin.getTownManager().saveTownsNow();

            openFlagsMenu(player);
        } else if (displayName.contains("Retour")) {
            player.closeInventory();
            openOwnerMenu(player);
        } else {
            // Basculer un flag spécifique
            for (PlotFlag flag : PlotFlag.values()) {
                if (displayName.contains(flag.getDisplayName())) {
                    boolean newValue = !plot.getFlag(flag);
                    plot.setFlag(flag, newValue);
                    player.sendMessage(ChatColor.GREEN + "Flag '" + flag.getDisplayName() + "' " +
                        (newValue ? "activé" : "désactivé"));

                    // Sauvegarder immédiatement
                    plugin.getTownManager().saveTownsNow();

                    openFlagsMenu(player);
                    break;
                }
            }
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PermissionContext context = pendingActions.get(player.getUniqueId());

        if (context == null) {
            return;
        }

        event.setCancelled(true);
        String input = event.getMessage().trim();

        if (input.equalsIgnoreCase("annuler")) {
            pendingActions.remove(player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "Action annulée.");
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            // Traitement futur
            pendingActions.remove(player.getUniqueId());
        });
    }

    private static class PermissionContext {
        final Plot plot;

        PermissionContext(Plot plot) {
            this.plot = plot;
        }
    }
}
