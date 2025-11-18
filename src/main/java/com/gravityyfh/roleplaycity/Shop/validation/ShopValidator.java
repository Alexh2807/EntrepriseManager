package com.gravityyfh.roleplaycity.shop.validation;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.shop.RepairAction;
import com.gravityyfh.roleplaycity.shop.ShopStatus;
import com.gravityyfh.roleplaycity.shop.ValidationResult;
import com.gravityyfh.roleplaycity.shop.model.Shop;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Validateur d'intégrité des boutiques
 * Vérifie que tous les composants existent et sont cohérents
 */
public class ShopValidator {
    private final RoleplayCity plugin;

    public ShopValidator(RoleplayCity plugin) {
        this.plugin = plugin;
    }

    /**
     * Valide l'intégrité complète d'une boutique
     * @param shop La boutique à valider
     * @return Le résultat de la validation
     */
    public ValidationResult validateShop(Shop shop) {
        List<String> issues = new ArrayList<>();
        RepairAction suggestedAction = RepairAction.NONE;

        // 1. VÉRIFIER LE COFFRE (Priorité absolue)
        if (!isChestPresent(shop.getChestLocation())) {
            issues.add("Coffre manquant");
            return ValidationResult.broken("Coffre manquant", RepairAction.DELETE);
        }

        // 2. VÉRIFIER LE PANNEAU
        if (!isSignPresent(shop.getSignLocation())) {
            issues.add("Panneau manquant");
            suggestedAction = RepairAction.REPAIR;
        }

        // 3. VÉRIFIER L'HOLOGRAMME
        if (!isHologramPresent(shop)) {
            issues.add("Hologramme manquant");
            if (suggestedAction == RepairAction.NONE) {
                suggestedAction = RepairAction.REPAIR;
            }
        }

        // 4. VÉRIFIER LE STOCK
        int actualStock = countItemsInChest(shop);
        if (actualStock == 0 && shop.getStatus() == ShopStatus.ACTIVE) {
            issues.add("Stock épuisé");
            if (suggestedAction == RepairAction.NONE) {
                suggestedAction = RepairAction.UPDATE_STATUS;
            }
        } else if (actualStock > 0 && shop.getStatus() == ShopStatus.OUT_OF_STOCK) {
            issues.add("Stock disponible mais statut OUT_OF_STOCK");
            if (suggestedAction == RepairAction.NONE) {
                suggestedAction = RepairAction.UPDATE_STATUS;
            }
        }

        // 5. VÉRIFIER LA COHÉRENCE DES DONNÉES
        if (!isItemMatchingTemplate(shop)) {
            issues.add("Item dans le coffre ne correspond pas au template");
            if (suggestedAction == RepairAction.NONE) {
                suggestedAction = RepairAction.NOTIFY;
            }
        }

        boolean isValid = issues.isEmpty();
        return new ValidationResult(isValid, issues, suggestedAction);
    }

    /**
     * Vérifie si un coffre est présent à la location donnée
     */
    public boolean isChestPresent(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        Block block = location.getBlock();
        return block.getState() instanceof Chest;
    }

    /**
     * Vérifie si un panneau est présent à la location donnée
     */
    public boolean isSignPresent(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        Block block = location.getBlock();
        return block.getState() instanceof Sign;
    }

    /**
     * Vérifie si l'hologramme est présent
     */
    public boolean isHologramPresent(Shop shop) {
        // Vérifier le display item
        if (shop.getDisplayItemEntityId() != null) {
            Entity entity = Bukkit.getEntity(shop.getDisplayItemEntityId());
            if (entity == null || !entity.isValid()) {
                return false;
            }
        } else {
            return false;
        }

        // Vérifier au moins un texte
        if (shop.getHologramTextEntityIds().isEmpty()) {
            return false;
        }

        boolean atLeastOneValid = false;
        for (UUID entityId : shop.getHologramTextEntityIds()) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null && entity.isValid()) {
                atLeastOneValid = true;
                break;
            }
        }

        return atLeastOneValid;
    }

    /**
     * Compte le nombre d'items dans le coffre correspondant au template
     * @return Le nombre d'unités vendables (stock total / quantité par vente)
     */
    public int countItemsInChest(Shop shop) {
        if (!isChestPresent(shop.getChestLocation())) {
            return 0;
        }

        Chest chest = (Chest) shop.getChestLocation().getBlock().getState();
        Inventory inv = chest.getInventory();

        int count = 0;
        ItemStack template = shop.getItemTemplate();

        for (ItemStack item : inv.getContents()) {
            if (item != null && item.isSimilar(template)) {
                count += item.getAmount();
            }
        }

        // Retourner le nombre de ventes possibles
        return count / shop.getQuantityPerSale();
    }

    /**
     * Compte le stock brut (nombre total d'items)
     */
    public int countRawItemsInChest(Shop shop) {
        if (!isChestPresent(shop.getChestLocation())) {
            return 0;
        }

        Chest chest = (Chest) shop.getChestLocation().getBlock().getState();
        Inventory inv = chest.getInventory();

        int count = 0;
        ItemStack template = shop.getItemTemplate();

        for (ItemStack item : inv.getContents()) {
            if (item != null && item.isSimilar(template)) {
                count += item.getAmount();
            }
        }

        return count;
    }

    /**
     * Vérifie si l'item dans le coffre correspond au template
     */
    public boolean isItemMatchingTemplate(Shop shop) {
        if (!isChestPresent(shop.getChestLocation())) {
            return false;
        }

        Chest chest = (Chest) shop.getChestLocation().getBlock().getState();
        Inventory inv = chest.getInventory();
        ItemStack template = shop.getItemTemplate();

        // Vérifier qu'il y a au moins un item correspondant
        for (ItemStack item : inv.getContents()) {
            if (item != null && !item.isSimilar(template) && item.getType() == template.getType()) {
                // Il y a des items du même type mais avec des métadonnées différentes
                return false;
            }
        }

        return true;
    }

    /**
     * Vérifie si le joueur a assez d'espace dans son inventaire
     */
    public boolean hasInventorySpace(org.bukkit.entity.Player player, ItemStack item, int quantity) {
        Inventory inv = player.getInventory();

        // Compter combien on peut ajouter
        int canFit = 0;
        ItemStack testItem = item.clone();
        int maxStackSize = testItem.getMaxStackSize();

        for (ItemStack slot : inv.getStorageContents()) {
            if (slot == null || slot.getType().isAir()) {
                // Slot vide
                canFit += maxStackSize;
            } else if (slot.isSimilar(testItem)) {
                // Slot avec le même item
                int remaining = maxStackSize - slot.getAmount();
                canFit += remaining;
            }

            if (canFit >= quantity) {
                return true;
            }
        }

        return canFit >= quantity;
    }

    /**
     * Retire des items du coffre
     * @return Le nombre d'items effectivement retirés
     */
    public int removeItemsFromChest(Shop shop, int quantity) {
        if (!isChestPresent(shop.getChestLocation())) {
            return 0;
        }

        Chest chest = (Chest) shop.getChestLocation().getBlock().getState();
        Inventory inv = chest.getInventory();
        ItemStack template = shop.getItemTemplate();

        int remaining = quantity;

        for (int i = 0; i < inv.getSize() && remaining > 0; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.isSimilar(template)) {
                int amount = item.getAmount();
                if (amount <= remaining) {
                    // Retirer tout le stack
                    inv.setItem(i, null);
                    remaining -= amount;
                } else {
                    // Retirer partiellement
                    item.setAmount(amount - remaining);
                    remaining = 0;
                }
            }
        }

        return quantity - remaining;
    }
}
