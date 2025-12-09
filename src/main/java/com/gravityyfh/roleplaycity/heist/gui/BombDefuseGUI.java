package com.gravityyfh.roleplaycity.heist.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.heist.config.HeistConfig;
import com.gravityyfh.roleplaycity.heist.data.Heist;
import com.gravityyfh.roleplaycity.heist.data.HeistPhase;
import com.gravityyfh.roleplaycity.heist.manager.HeistManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * GUI pour le mini-jeu de désamorçage de bombe
 * La police doit couper le bon fil parmi 5 options
 */
public class BombDefuseGUI implements Listener {

    private static final int GUI_SIZE = 27; // 3 rangées
    private static final String GUI_TITLE = ChatColor.DARK_RED + "💣 Désamorçage de Bombe";

    // Slots pour les fils (rangée du milieu)
    private static final int[] WIRE_SLOTS = {10, 11, 12, 13, 14};

    // Couleurs des fils
    private static final Material[] WIRE_MATERIALS = {
        Material.RED_WOOL,      // 0 - Rouge
        Material.BLUE_WOOL,     // 1 - Bleu
        Material.LIME_WOOL,     // 2 - Vert
        Material.YELLOW_WOOL,   // 3 - Jaune
        Material.WHITE_WOOL     // 4 - Blanc
    };

    private static final String[] WIRE_COLORS = {
        "&cRouge", "&9Bleu", "&aVert", "&eJaune", "&fBlanc"
    };

    private final RoleplayCity plugin;
    private final HeistManager heistManager;
    private final HeistConfig config;
    private final Heist heist;
    private final Player player;
    private final Inventory inventory;

    private boolean isOpen;

    public BombDefuseGUI(RoleplayCity plugin, HeistManager heistManager, Heist heist, Player player) {
        this.plugin = plugin;
        this.heistManager = heistManager;
        this.config = heistManager.getConfig();
        this.heist = heist;
        this.player = player;
        this.isOpen = false;

        // Créer l'inventaire
        this.inventory = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE);

        // Remplir l'inventaire
        setupInventory();
    }

    /**
     * Configure le contenu de l'inventaire
     */
    private void setupInventory() {
        // Fond noir/gris
        ItemStack background = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < GUI_SIZE; i++) {
            inventory.setItem(i, background);
        }

        // Titre en haut
        inventory.setItem(4, createItem(Material.TNT,
            "&c&l💣 BOMBE ACTIVE",
            "&7Choisissez le bon fil à couper!",
            "&7",
            "&eTemps restant: &c" + heist.getFormattedRemainingTime(),
            "&eTentatives: &c" + (config.getMaxDefuseAttempts() - heist.getDefuseAttempts())
                + "/" + config.getMaxDefuseAttempts()
        ));

        // Les 5 fils
        for (int i = 0; i < 5; i++) {
            inventory.setItem(WIRE_SLOTS[i], createWireItem(i));
        }

        // Indice en bas
        String hint = config.getHintForWire(heist.getCorrectWireIndex());
        // L'indice dit quel fil NE PAS couper, donc on l'affiche différemment
        // Le bon fil est celui qu'il faut couper (le correctWireIndex)
        // L'indice donne la couleur à NE PAS couper
        // On doit donc donner l'indice du mauvais fil, pas du bon
        int hintWireIndex = getHintTargetWire();
        String displayHint = config.getHintForWire(hintWireIndex);

        inventory.setItem(22, createItem(Material.PAPER,
            "&e&lINDICE",
            "&7" + displayHint,
            "&7",
            "&8Réfléchissez bien..."
        ));
    }

    /**
     * Détermine quel fil l'indice désigne (le mauvais fil)
     */
    private int getHintTargetWire() {
        // L'indice dans la config correspond à l'index du fil
        // Donc si correctWireIndex = 2, l'indice dira de ne pas couper le fil à un autre index
        // Pour simplifier, on affiche l'indice du fil qu'il ne faut PAS couper
        // On choisit un fil au hasard parmi ceux qui ne sont pas le bon
        Random random = new Random(heist.getHeistId().hashCode()); // Déterministe par heist
        int badWire;
        do {
            badWire = random.nextInt(5);
        } while (badWire == heist.getCorrectWireIndex());

        return badWire;
    }

    /**
     * Crée l'item représentant un fil
     */
    private ItemStack createWireItem(int wireIndex) {
        Material material = WIRE_MATERIALS[wireIndex];
        String colorName = WIRE_COLORS[wireIndex];

        return createItem(material,
            colorName + " Fil",
            "&7Cliquez pour couper ce fil",
            "&7",
            "&c⚠ Mauvais choix = explosion!"
        );
    }

    /**
     * Crée un item avec nom et lore
     */
    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

            if (lore.length > 0) {
                List<String> loreList = new ArrayList<>();
                for (String line : lore) {
                    loreList.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                meta.setLore(loreList);
            }

            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Ouvre le GUI pour le joueur
     */
    public void open() {
        // Enregistrer le listener
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        isOpen = true;

        // Ouvrir l'inventaire
        player.openInventory(inventory);

        // Son d'ouverture
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 0.5f);
    }

    /**
     * Ferme le GUI
     */
    public void close() {
        if (isOpen) {
            isOpen = false;
            HandlerList.unregisterAll(this);
            player.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory() != inventory) return;
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player clicker = (Player) event.getWhoClicked();
        if (!clicker.getUniqueId().equals(player.getUniqueId())) return;

        event.setCancelled(true);

        // Vérifier que le heist est toujours en phase countdown
        if (heist.getPhase() != HeistPhase.COUNTDOWN) {
            clicker.sendMessage(ChatColor.RED + "La bombe a déjà explosé!");
            close();
            return;
        }

        int slot = event.getRawSlot();

        // Vérifier si c'est un slot de fil
        int wireIndex = getWireIndexFromSlot(slot);
        if (wireIndex == -1) return; // Pas un fil

        // Tenter de couper le fil
        boolean success = heistManager.attemptDefuse(clicker, heist, wireIndex);

        if (success) {
            // Succès! Fermer le GUI
            clicker.playSound(clicker.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            close();
        } else {
            // Échec
            clicker.playSound(clicker.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f);

            // Mettre à jour l'interface si encore des tentatives
            if (!heist.hasExceededDefuseAttempts()) {
                // Marquer le fil coupé comme "mauvais"
                inventory.setItem(slot, createItem(Material.GRAY_WOOL,
                    "&8&m" + WIRE_COLORS[wireIndex].substring(2) + " Fil",
                    "&cMauvais choix!"
                ));

                // Mettre à jour le compteur de tentatives
                inventory.setItem(4, createItem(Material.TNT,
                    "&c&l💣 BOMBE ACTIVE",
                    "&7Choisissez le bon fil à couper!",
                    "&7",
                    "&eTemps restant: &c" + heist.getFormattedRemainingTime(),
                    "&eTentatives: &c" + (config.getMaxDefuseAttempts() - heist.getDefuseAttempts())
                        + "/" + config.getMaxDefuseAttempts()
                ));
            } else {
                // Plus de tentatives, fermer le GUI
                close();
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory() != inventory) return;
        if (!(event.getPlayer() instanceof Player)) return;

        Player closer = (Player) event.getPlayer();
        if (!closer.getUniqueId().equals(player.getUniqueId())) return;

        // Nettoyer
        if (isOpen) {
            isOpen = false;
            HandlerList.unregisterAll(this);
        }
    }

    /**
     * Convertit un slot en index de fil
     * @return -1 si pas un slot de fil
     */
    private int getWireIndexFromSlot(int slot) {
        for (int i = 0; i < WIRE_SLOTS.length; i++) {
            if (WIRE_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Ouvre le GUI de désamorçage pour un policier
     */
    public static void openFor(RoleplayCity plugin, HeistManager heistManager, Heist heist, Player police) {
        BombDefuseGUI gui = new BombDefuseGUI(plugin, heistManager, heist, police);
        gui.open();
    }
}
