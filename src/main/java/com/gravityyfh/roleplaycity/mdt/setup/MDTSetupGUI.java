package com.gravityyfh.roleplaycity.mdt.setup;

import com.gravityyfh.roleplaycity.RoleplayCity;
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
 * GUI pour sélectionner le mode de configuration
 */
public class MDTSetupGUI implements Listener {
    private final RoleplayCity plugin;
    private final MDTSetupManager setupManager;

    private static final String TITLE = ChatColor.GOLD + "⚙ MDT Setup - Sélection";
    private static final int SIZE = 45; // 5 lignes

    private final Set<UUID> openGUIs = new HashSet<>();

    public MDTSetupGUI(RoleplayCity plugin, MDTSetupManager setupManager) {
        this.plugin = plugin;
        this.setupManager = setupManager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        if (!setupManager.isInSetupMode(player.getUniqueId())) {
            player.sendMessage(setupManager.getPrefix() + ChatColor.RED + "Tu n'es pas en mode setup!");
            return;
        }

        MDTSetupSession session = setupManager.getSession(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, SIZE, TITLE);

        // Fond gris
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, glass);
        }

        // === Ligne 1: Spawns ===
        inv.setItem(10, createModeItem(MDTSetupMode.LOBBY, session));
        inv.setItem(11, createModeItem(MDTSetupMode.RED_SPAWN, session));
        inv.setItem(12, createModeItem(MDTSetupMode.BLUE_SPAWN, session));

        // === Ligne 2: Lits ===
        inv.setItem(19, createModeItem(MDTSetupMode.RED_BED, session));
        inv.setItem(20, createModeItem(MDTSetupMode.BLUE_BED, session));
        inv.setItem(21, createModeItem(MDTSetupMode.NEUTRAL_BED, session));

        // === Ligne 1-2: Générateurs (droite) ===
        inv.setItem(14, createModeItem(MDTSetupMode.GENERATOR_BRICK, session));
        inv.setItem(15, createModeItem(MDTSetupMode.GENERATOR_IRON, session));
        inv.setItem(23, createModeItem(MDTSetupMode.GENERATOR_GOLD, session));
        inv.setItem(24, createModeItem(MDTSetupMode.GENERATOR_DIAMOND, session));

        // === Ligne 3: Marchand et Supprimer ===
        inv.setItem(29, createModeItem(MDTSetupMode.MERCHANT, session));
        inv.setItem(33, createModeItem(MDTSetupMode.REMOVE, session));

        // === Ligne 4: Actions ===
        // Statut
        inv.setItem(36, createItem(Material.BOOK, ChatColor.YELLOW + "Voir le Statut",
            "",
            ChatColor.GRAY + "Affiche tous les éléments",
            ChatColor.GRAY + "actuellement configurés"));

        // Sauvegarder
        inv.setItem(40, createItem(Material.LIME_CONCRETE, ChatColor.GREEN + "✓ Sauvegarder",
            "",
            ChatColor.GRAY + "Sauvegarde la configuration",
            ChatColor.GRAY + "et quitte le mode setup",
            "",
            session.isMinimalConfigComplete() ?
                ChatColor.GREEN + "Configuration complète!" :
                ChatColor.RED + "Configuration incomplète!"));

        // Annuler
        inv.setItem(44, createItem(Material.RED_CONCRETE, ChatColor.RED + "✗ Annuler",
            "",
            ChatColor.GRAY + "Annule toutes les",
            ChatColor.GRAY + "modifications non sauvegardées"));

        player.openInventory(inv);
        openGUIs.add(player.getUniqueId());
    }

    private ItemStack createModeItem(MDTSetupMode mode, MDTSetupSession session) {
        boolean isCurrentMode = session.getCurrentMode() == mode;
        String status = getStatusForMode(mode, session);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + mode.getInstruction());
        lore.add("");
        lore.add(ChatColor.GRAY + "Statut: " + status);
        lore.add("");

        if (isCurrentMode) {
            lore.add(ChatColor.GREEN + "► Mode actuel");
        } else {
            lore.add(ChatColor.YELLOW + "Clic pour sélectionner");
        }

        ItemStack item = new ItemStack(mode.getIcon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = (isCurrentMode ? ChatColor.GREEN + "► " : "") + mode.getDisplayName();
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private String getStatusForMode(MDTSetupMode mode, MDTSetupSession session) {
        return switch (mode) {
            case LOBBY -> session.getLobbySpawn() != null ? ChatColor.GREEN + "✓ Défini" : ChatColor.RED + "✗ Non défini";
            case RED_SPAWN -> session.getRedTeamSpawn() != null ? ChatColor.GREEN + "✓ Défini" : ChatColor.RED + "✗ Non défini";
            case BLUE_SPAWN -> session.getBlueTeamSpawn() != null ? ChatColor.GREEN + "✓ Défini" : ChatColor.RED + "✗ Non défini";
            case RED_BED -> session.getRedBedLocation() != null ? ChatColor.GREEN + "✓ Défini" : ChatColor.RED + "✗ Non défini";
            case BLUE_BED -> session.getBlueBedLocation() != null ? ChatColor.GREEN + "✓ Défini" : ChatColor.RED + "✗ Non défini";
            case NEUTRAL_BED -> ChatColor.WHITE + String.valueOf(session.getNeutralBedLocations().size()) + "/4";
            case GENERATOR_BRICK -> ChatColor.WHITE + String.valueOf(session.getGeneratorLocations().get("brick").size());
            case GENERATOR_IRON -> ChatColor.WHITE + String.valueOf(session.getGeneratorLocations().get("iron").size());
            case GENERATOR_GOLD -> ChatColor.WHITE + String.valueOf(session.getGeneratorLocations().get("gold").size());
            case GENERATOR_DIAMOND -> ChatColor.WHITE + String.valueOf(session.getGeneratorLocations().get("diamond").size());
            case MERCHANT -> ChatColor.WHITE + String.valueOf(session.getMerchantLocations().size()) + "/4";
            case REMOVE -> ChatColor.GRAY + "Outil";
        };
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(TITLE)) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR ||
            clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        int slot = event.getRawSlot();

        // Vérifier les modes
        MDTSetupMode clickedMode = getModeForSlot(slot);
        if (clickedMode != null) {
            setupManager.setMode(player, clickedMode);
            player.closeInventory();
            return;
        }

        // Actions spéciales
        switch (slot) {
            case 36 -> { // Statut
                player.closeInventory();
                setupManager.showStatus(player);
            }
            case 40 -> { // Sauvegarder
                player.closeInventory();
                setupManager.saveSetup(player);
            }
            case 44 -> { // Annuler
                player.closeInventory();
                setupManager.cancelSetup(player);
            }
        }
    }

    private MDTSetupMode getModeForSlot(int slot) {
        return switch (slot) {
            case 10 -> MDTSetupMode.LOBBY;
            case 11 -> MDTSetupMode.RED_SPAWN;
            case 12 -> MDTSetupMode.BLUE_SPAWN;
            case 19 -> MDTSetupMode.RED_BED;
            case 20 -> MDTSetupMode.BLUE_BED;
            case 21 -> MDTSetupMode.NEUTRAL_BED;
            case 14 -> MDTSetupMode.GENERATOR_BRICK;
            case 15 -> MDTSetupMode.GENERATOR_IRON;
            case 23 -> MDTSetupMode.GENERATOR_GOLD;
            case 24 -> MDTSetupMode.GENERATOR_DIAMOND;
            case 29 -> MDTSetupMode.MERCHANT;
            case 33 -> MDTSetupMode.REMOVE;
            default -> null;
        };
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            openGUIs.remove(player.getUniqueId());
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
