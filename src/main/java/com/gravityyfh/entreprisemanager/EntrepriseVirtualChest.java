package com.gravityyfh.entreprisemanager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EntrepriseVirtualChest {
    private List<ItemStack> items;       // Liste pour stocker les items dans le coffre
    private List<ItemStack> soldItems;   // Liste pour stocker les items vendus
    private String entrepriseNom;
    private Inventory inventory;

    public EntrepriseVirtualChest(String entrepriseNom) {
        this.entrepriseNom = entrepriseNom;
        this.items = new ArrayList<>();
        this.soldItems = new ArrayList<>(); // Initialiser la liste des items vendus
        this.inventory = Bukkit.createInventory(null, 27, ChatColor.DARK_BLUE + "Coffre: " + entrepriseNom);
    }

    // Charger les items et les items vendus depuis la configuration
    public void loadFromConfig(YamlConfiguration config, String path) {
        List<Map<?, ?>> itemList = config.getMapList(path + ".chest-items");
        if (itemList != null) {
            for (Map<?, ?> itemData : itemList) {
                Map<String, Object> serializedItem = new HashMap<>();
                for (Map.Entry<?, ?> entry : itemData.entrySet()) {
                    if (entry.getKey() instanceof String && entry.getValue() != null) {
                        serializedItem.put((String) entry.getKey(), entry.getValue());
                    }
                }
                ItemStack item = ItemStack.deserialize(serializedItem);
                items.add(item);
            }
        }

        List<Map<?, ?>> soldItemList = config.getMapList(path + ".sold-items");
        if (soldItemList != null) {
            for (Map<?, ?> itemData : soldItemList) {
                ItemStack soldItem = ItemStack.deserialize((Map<String, Object>) itemData);
                soldItems.add(soldItem);
            }
        }
    }

    // Sauvegarder les items et les items vendus dans la configuration
    public void saveToConfig(YamlConfiguration config, String path) {
        List<Map<String, Object>> itemList = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null) {
                itemList.add(item.serialize());
            }
        }
        config.set(path + ".chest-items", itemList);

        List<Map<String, Object>> soldItemList = new ArrayList<>();
        for (ItemStack item : soldItems) {
            if (item != null) {
                soldItemList.add(item.serialize());
            }
        }
        config.set(path + ".sold-items", soldItemList);
    }

    // Méthode pour obtenir l'inventaire associé au coffre virtuel
    public Inventory getInventory() {
        return this.inventory;
    }

    // Méthode pour obtenir les items du coffre virtuel
    public List<ItemStack> getItems() {
        return new ArrayList<>(items); // Retourne une copie de la liste pour éviter les modifications directes
    }

    // Méthode pour ajouter un item au coffre virtuel
    public void addItem(ItemStack item) {
        items.add(item);
        this.inventory.addItem(item); // Ajoute l'item dans l'inventaire
    }

    // Méthode pour vider le coffre virtuel
    public void clear() {
        items.clear();
        this.inventory.clear(); // Vide également l'inventaire Bukkit
    }

    // Ajouter des items vendus dans la liste
    public void addSoldItems(List<ItemStack> items) {
        this.soldItems.addAll(items);
    }

    // Récupérer les items vendus
    public List<ItemStack> getSoldItems() {
        return this.soldItems;
    }
}
