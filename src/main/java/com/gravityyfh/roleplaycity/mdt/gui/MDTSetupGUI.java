package com.gravityyfh.roleplaycity.mdt.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

public class MDTSetupGUI implements InventoryHolder {
    private final Inventory inventory;

    public MDTSetupGUI() {
        this.inventory = Bukkit.createInventory(this, 54, "§8Configuration MDT Rush"); // 54 slots (6 lignes) pour plus d'espace
        initializeItems();
    }

    private void initializeItems() {
        // Vitres de décoration
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, glass);
        }

        // === LIGNE DE SÉPARATION SNAPSHOT ===
        inventory.setItem(9, createItem(Material.YELLOW_STAINED_GLASS_PANE, "§e§lSNAPSHOT & PROTECTION"));
        inventory.setItem(17, createItem(Material.YELLOW_STAINED_GLASS_PANE, "§e§lSNAPSHOT & PROTECTION"));

        // === OUTILS SNAPSHOT ===
        inventory.setItem(10, createItem(Material.WOODEN_AXE, "§a§lOutils Sélection FAWE",
                "§7Donne les outils de sélection WorldEdit",
                "§7§eHache§7 = Point 1 | §ePioche§7 = Point 2",
                "",
                "§eClic pour recevoir"));

        inventory.setItem(11, createItem(Material.PAPER, "§b§lSauvegarder Snapshot",
                "§7Sauvegarde la sélection actuelle",
                "§7en format .schematic",
                "§7Maximum: 1M de blocs",
                "",
                "§eClic pour sauvegarder"));

        inventory.setItem(12, createItem(Material.REPEATER, "§c§lRestaurer Snapshot",
                "§7Restaure le dernier snapshot",
                "§7à ta position actuelle",
                "",
                "§eClic pour restaurer"));

        inventory.setItem(13, createItem(Material.SHIELD, "§d§lProtéger la Zone",
                "§7Active la protection de la région",
                "§7Pendant les parties MDT",
                "",
                "§eClic pour activer/désactiver"));

        inventory.setItem(14, createItem(Material.BOOK, "§e§lLister Snapshots",
                "§7Affiche tous les snapshots",
                "§7disponibles",
                "",
                "§eClic pour voir la liste"));

        // === LIGNE DE SÉPARATION ===
        inventory.setItem(18, createItem(Material.GRAY_STAINED_GLASS_PANE, "§7§lCONFIGURATION CLASSIQUE"));
        inventory.setItem(26, createItem(Material.GRAY_STAINED_GLASS_PANE, "§7§lCONFIGURATION CLASSIQUE"));
        inventory.setItem(27, createItem(Material.GRAY_STAINED_GLASS_PANE, "§7§lCONFIGURATION CLASSIQUE"));
        inventory.setItem(35, createItem(Material.GRAY_STAINED_GLASS_PANE, "§7§lCONFIGURATION CLASSIQUE"));

        // === LITS D'ÉQUIPE (clic droit sur lit dans le monde) ===
        inventory.setItem(28, createItem(Material.RED_BED, "§c§lLit Équipe Rouge",
                "§7Pour définir: ferme ce menu",
                "§7et fais §eclic droit sur un lit§7",
                "§7dans le monde MDT."));

        inventory.setItem(29, createItem(Material.BLUE_BED, "§9§lLit Équipe Bleue",
                "§7Pour définir: ferme ce menu",
                "§7et fais §eclic droit sur un lit§7",
                "§7dans le monde MDT."));

        // === LITS NEUTRES ===
        inventory.setItem(30, createItem(Material.WHITE_BED, "§f§lGérer Lits Neutres",
                "§7Voir et supprimer les lits",
                "§7neutres configurés.",
                "",
                "§eClic pour ouvrir"));

        // === SPAWNS ===
        inventory.setItem(32, createItem(Material.RED_WOOL, "§cSpawn Équipe Rouge",
                "§7Clic pour définir le spawn", "§7de l'équipe rouge à ta position."));

        inventory.setItem(33, createItem(Material.BEACON, "§eSpawn Lobby",
                "§7Clic pour définir le spawn", "§7d'attente (Lobby)."));

        inventory.setItem(34, createItem(Material.BLUE_WOOL, "§9Spawn Équipe Bleue",
                "§7Clic pour définir le spawn", "§7de l'équipe bleue à ta position."));

        // === GÉNÉRATEURS ===
        inventory.setItem(40, createItem(Material.SPAWNER, "§6Ajouter Générateur",
                "§7Clic pour placer un générateur", "§7(Brique/Fer/Or/Diamant).", "§e(Place-en 5 par île !)"));

        // === MARCHANDS ===
        inventory.setItem(45, createItem(Material.VILLAGER_SPAWN_EGG, "§ePetit Vendeur (Global)",
                "§7Clic pour placer le petit", "§7marchand fourre-tout."));

        inventory.setItem(46, createItem(Material.BRICK, "§7Grand Marchand (Blocs)",
                "§7Clic pour placer le", "§7marchand de blocs/outils."));

        inventory.setItem(47, createItem(Material.IRON_SWORD, "§cGrand Marchand (Armes)",
                "§7Clic pour placer le", "§7marchand d'armes."));

        inventory.setItem(48, createItem(Material.IRON_CHESTPLATE, "§9Grand Marchand (Armures)",
                "§7Clic pour placer le", "§7marchand d'armures."));

        inventory.setItem(49, createItem(Material.TNT, "§dGrand Marchand (Spécial)",
                "§7Clic pour placer le", "§7marchand de potions/TNT."));

        // === GESTION MARCHANDS ===
        inventory.setItem(50, createItem(Material.EMERALD, "§a§lGérer Marchands",
                "§7Voir et supprimer les",
                "§7marchands configurés.",
                "",
                "§eClic pour ouvrir"));

        inventory.setItem(53, createItem(Material.BARRIER, "§cSupprimer TOUT",
                "§7Clic pour supprimer TOUS", "§7les générateurs et marchands."));
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
