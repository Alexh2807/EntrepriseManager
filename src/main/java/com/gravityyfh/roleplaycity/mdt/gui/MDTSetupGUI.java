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
        this.inventory = Bukkit.createInventory(this, 36, "§8Configuration MDT Rush");
        initializeItems();
    }

    private void initializeItems() {
        // Vitres de décoration
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 36; i++) {
            inventory.setItem(i, glass);
        }

        // === LITS D'ÉQUIPE (clic droit sur lit dans le monde) ===
        inventory.setItem(10, createItem(Material.RED_BED, "§c§lLit Équipe Rouge",
                "§7Pour définir: ferme ce menu",
                "§7et fais §eclic droit sur un lit§7",
                "§7dans le monde MDT."));

        inventory.setItem(11, createItem(Material.BLUE_BED, "§9§lLit Équipe Bleue",
                "§7Pour définir: ferme ce menu",
                "§7et fais §eclic droit sur un lit§7",
                "§7dans le monde MDT."));

        // === LITS NEUTRES ===
        inventory.setItem(12, createItem(Material.WHITE_BED, "§f§lGérer Lits Neutres",
                "§7Voir et supprimer les lits",
                "§7neutres configurés.",
                "",
                "§eClic pour ouvrir"));

        // === SPAWNS ===
        inventory.setItem(14, createItem(Material.RED_WOOL, "§cSpawn Équipe Rouge",
                "§7Clic pour définir le spawn", "§7de l'équipe rouge à ta position."));

        inventory.setItem(15, createItem(Material.BEACON, "§eSpawn Lobby",
                "§7Clic pour définir le spawn", "§7d'attente (Lobby)."));

        inventory.setItem(16, createItem(Material.BLUE_WOOL, "§9Spawn Équipe Bleue",
                "§7Clic pour définir le spawn", "§7de l'équipe bleue à ta position."));

        // === GÉNÉRATEURS ===
        inventory.setItem(22, createItem(Material.SPAWNER, "§6Ajouter Générateur",
                "§7Clic pour placer un générateur", "§7(Brique/Fer/Or/Diamant).", "§e(Place-en 5 par île !)"));

        // === MARCHANDS ===
        inventory.setItem(27, createItem(Material.VILLAGER_SPAWN_EGG, "§ePetit Vendeur (Global)",
                "§7Clic pour placer le petit", "§7marchand fourre-tout."));

        inventory.setItem(28, createItem(Material.BRICK, "§7Grand Marchand (Blocs)",
                "§7Clic pour placer le", "§7marchand de blocs/outils."));

        inventory.setItem(29, createItem(Material.IRON_SWORD, "§cGrand Marchand (Armes)",
                "§7Clic pour placer le", "§7marchand d'armes."));

        inventory.setItem(30, createItem(Material.IRON_CHESTPLATE, "§9Grand Marchand (Armures)",
                "§7Clic pour placer le", "§7marchand d'armures."));

        inventory.setItem(31, createItem(Material.TNT, "§dGrand Marchand (Spécial)",
                "§7Clic pour placer le", "§7marchand de potions/TNT."));

        // === GESTION MARCHANDS ===
        inventory.setItem(32, createItem(Material.EMERALD, "§a§lGérer Marchands",
                "§7Voir et supprimer les",
                "§7marchands configurés.",
                "",
                "§eClic pour ouvrir"));

        inventory.setItem(35, createItem(Material.BARRIER, "§cSupprimer TOUT",
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
