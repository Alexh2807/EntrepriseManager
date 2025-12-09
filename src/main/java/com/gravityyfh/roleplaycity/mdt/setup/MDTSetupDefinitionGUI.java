package com.gravityyfh.roleplaycity.mdt.setup;

import com.gravityyfh.roleplaycity.RoleplayCity;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * GUI qui s'ouvre quand on clique droit sur un élément pour définir ce que c'est
 */
public class MDTSetupDefinitionGUI implements Listener {
    private final RoleplayCity plugin;
    private final MDTSetupManager setupManager;

    private static final String TITLE = ChatColor.GOLD + "⚙ Définir cet élément";
    private static final int SIZE = 54; // 6 lignes

    // Contexte du clic (location et entité cliquée)
    private final Map<UUID, ClickContext> clickContexts = new HashMap<>();

    public MDTSetupDefinitionGUI(RoleplayCity plugin, MDTSetupManager setupManager) {
        this.plugin = plugin;
        this.setupManager = setupManager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Ouvre le GUI de définition après un clic droit
     */
    public void open(Player player, Location location, Entity clickedEntity) {
        if (!setupManager.isInSetupMode(player.getUniqueId())) {
            return;
        }

        // Sauvegarder le contexte du clic
        clickContexts.put(player.getUniqueId(), new ClickContext(location, clickedEntity));

        Inventory inv = Bukkit.createInventory(null, SIZE, TITLE);

        // Fond
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, glass);
        }

        // Séparateurs de catégories
        ItemStack separator = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");

        // === Ligne 0: Info sur ce qui a été cliqué ===
        String clickedInfo = getClickedInfo(location, clickedEntity);
        inv.setItem(4, createItem(Material.PAPER, ChatColor.YELLOW + "Élément cliqué",
            "",
            ChatColor.GRAY + clickedInfo,
            "",
            ChatColor.WHITE + "Choisis ce que cet élément représente"));

        // === Ligne 1: SPAWNS ===
        inv.setItem(9, createItem(Material.COMPASS, ChatColor.WHITE + "▼ SPAWNS", ""));
        inv.setItem(10, createTypeItem(MDTElementType.SPAWN_LOBBY));
        inv.setItem(11, createTypeItem(MDTElementType.SPAWN_RED));
        inv.setItem(12, createTypeItem(MDTElementType.SPAWN_BLUE));

        // === Ligne 1: LITS ===
        inv.setItem(14, createItem(Material.RED_BED, ChatColor.WHITE + "▼ LITS", ""));
        inv.setItem(15, createTypeItem(MDTElementType.BED_RED));
        inv.setItem(16, createTypeItem(MDTElementType.BED_BLUE));
        inv.setItem(17, createTypeItem(MDTElementType.BED_NEUTRAL));

        // === Ligne 2: GÉNÉRATEURS ===
        inv.setItem(18, createItem(Material.SPAWNER, ChatColor.WHITE + "▼ GÉNÉRATEURS", ""));
        inv.setItem(19, createTypeItem(MDTElementType.GEN_BRICK));
        inv.setItem(20, createTypeItem(MDTElementType.GEN_IRON));
        inv.setItem(21, createTypeItem(MDTElementType.GEN_GOLD));
        inv.setItem(22, createTypeItem(MDTElementType.GEN_DIAMOND));

        // === Ligne 3: MARCHANDS ===
        inv.setItem(27, createItem(Material.EMERALD, ChatColor.WHITE + "▼ MARCHANDS (4 types)", ""));
        inv.setItem(28, createTypeItem(MDTElementType.MERCHANT_BLOCKS));
        inv.setItem(29, createTypeItem(MDTElementType.MERCHANT_WEAPONS));
        inv.setItem(30, createTypeItem(MDTElementType.MERCHANT_ARMOR));
        inv.setItem(31, createTypeItem(MDTElementType.MERCHANT_SPECIAL));

        // === Ligne 4: Actions ===
        // Si c'est un élément déjà configuré, montrer l'option de suppression
        MDTSetupSession session = setupManager.getSession(player.getUniqueId());
        MDTElementType existingType = findExistingElement(session, location, clickedEntity);

        if (existingType != null) {
            inv.setItem(36, createItem(Material.BOOK, ChatColor.GREEN + "Élément actuel: " + existingType.getDisplayName(),
                "",
                ChatColor.GRAY + "Cet emplacement est déjà configuré"));
        }

        // Supprimer
        inv.setItem(44, createTypeItem(MDTElementType.REMOVE));

        // Annuler
        inv.setItem(49, createItem(Material.ARROW, ChatColor.YELLOW + "← Annuler",
            "",
            ChatColor.GRAY + "Fermer sans modifier"));

        player.openInventory(inv);
    }

    private String getClickedInfo(Location location, Entity entity) {
        if (entity != null) {
            if (entity instanceof Villager) {
                return "Villageois à " + formatLocation(location);
            }
            return entity.getType().name() + " à " + formatLocation(location);
        }
        return "Bloc à " + formatLocation(location);
    }

    private String formatLocation(Location loc) {
        return String.format("(%.0f, %.0f, %.0f)", loc.getX(), loc.getY(), loc.getZ());
    }

    private MDTElementType findExistingElement(MDTSetupSession session, Location location, Entity entity) {
        // Vérifier les spawns
        if (isSameLocation(session.getLobbySpawn(), location)) return MDTElementType.SPAWN_LOBBY;
        if (isSameLocation(session.getRedTeamSpawn(), location)) return MDTElementType.SPAWN_RED;
        if (isSameLocation(session.getBlueTeamSpawn(), location)) return MDTElementType.SPAWN_BLUE;

        // Vérifier les lits
        if (isSameBlock(session.getRedBedLocation(), location)) return MDTElementType.BED_RED;
        if (isSameBlock(session.getBlueBedLocation(), location)) return MDTElementType.BED_BLUE;
        for (Location bed : session.getNeutralBedLocations()) {
            if (isSameBlock(bed, location)) return MDTElementType.BED_NEUTRAL;
        }

        // Vérifier les générateurs
        for (Map.Entry<String, List<Location>> entry : session.getGeneratorLocations().entrySet()) {
            for (Location gen : entry.getValue()) {
                if (isSameBlock(gen, location)) {
                    return switch (entry.getKey()) {
                        case "brick" -> MDTElementType.GEN_BRICK;
                        case "iron" -> MDTElementType.GEN_IRON;
                        case "gold" -> MDTElementType.GEN_GOLD;
                        case "diamond" -> MDTElementType.GEN_DIAMOND;
                        default -> null;
                    };
                }
            }
        }

        // Vérifier les marchands
        for (Map.Entry<String, Location> entry : session.getMerchantLocations().entrySet()) {
            if (isSameBlock(entry.getValue(), location)) {
                return switch (entry.getKey()) {
                    case "blocks" -> MDTElementType.MERCHANT_BLOCKS;
                    case "weapons" -> MDTElementType.MERCHANT_WEAPONS;
                    case "armor" -> MDTElementType.MERCHANT_ARMOR;
                    case "special" -> MDTElementType.MERCHANT_SPECIAL;
                    default -> null;
                };
            }
        }

        return null;
    }

    private boolean isSameLocation(Location a, Location b) {
        if (a == null || b == null) return false;
        return a.getWorld().equals(b.getWorld()) &&
               Math.abs(a.getX() - b.getX()) < 1 &&
               Math.abs(a.getY() - b.getY()) < 1 &&
               Math.abs(a.getZ() - b.getZ()) < 1;
    }

    private boolean isSameBlock(Location a, Location b) {
        if (a == null || b == null) return false;
        return a.getBlockX() == b.getBlockX() &&
               a.getBlockY() == b.getBlockY() &&
               a.getBlockZ() == b.getBlockZ();
    }

    private ItemStack createTypeItem(MDTElementType type) {
        return createItem(type.getIcon(), type.getDisplayName(),
            "",
            ChatColor.GRAY + type.getDescription(),
            "",
            ChatColor.YELLOW + "Clic pour sélectionner");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(TITLE)) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR ||
            clicked.getType() == Material.GRAY_STAINED_GLASS_PANE ||
            clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) return;

        int slot = event.getRawSlot();

        // Annuler
        if (slot == 49) {
            player.closeInventory();
            clickContexts.remove(player.getUniqueId());
            return;
        }

        // Trouver le type sélectionné
        MDTElementType selectedType = getTypeForSlot(slot);
        if (selectedType == null) return;

        // Récupérer le contexte du clic
        ClickContext context = clickContexts.get(player.getUniqueId());
        if (context == null) {
            player.sendMessage(ChatColor.RED + "Erreur: contexte perdu. Réessaye.");
            player.closeInventory();
            return;
        }

        // Appliquer la sélection
        boolean success = applySelection(player, selectedType, context);

        player.closeInventory();
        clickContexts.remove(player.getUniqueId());

        if (success) {
            player.sendMessage(setupManager.getPrefix() + ChatColor.GREEN +
                selectedType.getRawName() + " défini avec succès!");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
        }
    }

    private MDTElementType getTypeForSlot(int slot) {
        return switch (slot) {
            case 10 -> MDTElementType.SPAWN_LOBBY;
            case 11 -> MDTElementType.SPAWN_RED;
            case 12 -> MDTElementType.SPAWN_BLUE;
            case 15 -> MDTElementType.BED_RED;
            case 16 -> MDTElementType.BED_BLUE;
            case 17 -> MDTElementType.BED_NEUTRAL;
            case 19 -> MDTElementType.GEN_BRICK;
            case 20 -> MDTElementType.GEN_IRON;
            case 21 -> MDTElementType.GEN_GOLD;
            case 22 -> MDTElementType.GEN_DIAMOND;
            case 28 -> MDTElementType.MERCHANT_BLOCKS;
            case 29 -> MDTElementType.MERCHANT_WEAPONS;
            case 30 -> MDTElementType.MERCHANT_ARMOR;
            case 31 -> MDTElementType.MERCHANT_SPECIAL;
            case 44 -> MDTElementType.REMOVE;
            default -> null;
        };
    }

    private boolean applySelection(Player player, MDTElementType type, ClickContext context) {
        MDTSetupSession session = setupManager.getSession(player.getUniqueId());
        if (session == null) return false;

        Location loc = context.location;
        Location playerLoc = player.getLocation(); // Pour les spawns, utiliser la position du joueur

        switch (type) {
            case SPAWN_LOBBY -> session.setLobbySpawn(playerLoc);
            case SPAWN_RED -> session.setRedTeamSpawn(playerLoc);
            case SPAWN_BLUE -> session.setBlueTeamSpawn(playerLoc);
            case BED_RED -> session.setRedBedLocation(loc);
            case BED_BLUE -> session.setBlueBedLocation(loc);
            case BED_NEUTRAL -> {
                if (session.getNeutralBedLocations().size() >= 4) {
                    player.sendMessage(setupManager.getPrefix() + ChatColor.RED +
                        "Maximum 4 lits neutres! Supprime-en un d'abord.");
                    return false;
                }
                session.addNeutralBed(loc);
            }
            case GEN_BRICK -> session.addGenerator("brick", loc.clone().add(0.5, 1, 0.5));
            case GEN_IRON -> session.addGenerator("iron", loc.clone().add(0.5, 1, 0.5));
            case GEN_GOLD -> session.addGenerator("gold", loc.clone().add(0.5, 1, 0.5));
            case GEN_DIAMOND -> session.addGenerator("diamond", loc.clone().add(0.5, 1, 0.5));
            case MERCHANT_BLOCKS -> session.setMerchantLocation("blocks", loc.clone().add(0.5, 1, 0.5));
            case MERCHANT_WEAPONS -> session.setMerchantLocation("weapons", loc.clone().add(0.5, 1, 0.5));
            case MERCHANT_ARMOR -> session.setMerchantLocation("armor", loc.clone().add(0.5, 1, 0.5));
            case MERCHANT_SPECIAL -> session.setMerchantLocation("special", loc.clone().add(0.5, 1, 0.5));
            case REMOVE -> {
                boolean removed = session.removeElementAt(loc);
                if (!removed) {
                    player.sendMessage(setupManager.getPrefix() + ChatColor.RED +
                        "Aucun élément à supprimer ici.");
                    return false;
                }
                player.sendMessage(setupManager.getPrefix() + ChatColor.YELLOW + "Élément supprimé.");
                return true;
            }
        }

        return true;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            // Ne pas supprimer le contexte immédiatement, au cas où on réouvre
        }
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0 && !lore[0].isEmpty()) {
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Contexte d'un clic (où et sur quoi)
     */
    private static class ClickContext {
        final Location location;
        final Entity entity;

        ClickContext(Location location, Entity entity) {
            this.location = location;
            this.entity = entity;
        }
    }
}
