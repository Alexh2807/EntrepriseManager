package com.gravityyfh.roleplaycity.shop.components;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.shop.RepairAction;
import com.gravityyfh.roleplaycity.shop.ValidationResult;
import com.gravityyfh.roleplaycity.shop.model.Shop;
import com.gravityyfh.roleplaycity.shop.validation.ShopValidator;
import org.bukkit.Bukkit;

import java.util.List;

/**
 * Coordinateur des composants visuels d'une boutique
 * Gère la synchronisation entre le panneau et l'hologramme
 */
public class ShopComponents {
    private final RoleplayCity plugin;
    private final ShopSignManager signManager;
    private final ShopHologramManager hologramManager;
    private final ShopValidator validator;

    public ShopComponents(RoleplayCity plugin, ShopValidator validator) {
        this.plugin = plugin;
        this.validator = validator;
        this.signManager = new ShopSignManager(plugin, validator);
        this.hologramManager = new ShopHologramManager(plugin, validator);
    }

    /**
     * Crée tous les composants visuels d'une boutique
     */
    public void createComponents(Shop shop) {
        signManager.createOrUpdateSign(shop);
        hologramManager.createOrUpdateHologram(shop);
    }

    /**
     * Met à jour tous les composants visuels d'une boutique
     */
    public void updateComponents(Shop shop) {
        signManager.createOrUpdateSign(shop);
        hologramManager.createOrUpdateHologram(shop);
    }

    /**
     * Met à jour uniquement le panneau
     */
    public void updateSign(Shop shop) {
        signManager.createOrUpdateSign(shop);
    }

    /**
     * Met à jour uniquement l'hologramme
     */
    public void updateHologram(Shop shop) {
        hologramManager.createOrUpdateHologram(shop);
    }

    /**
     * Supprime tous les composants visuels d'une boutique
     */
    public void removeComponents(Shop shop) {
        hologramManager.removeHologram(shop);
        signManager.clearSign(shop);
    }

    /**
     * Répare les composants manquants d'une boutique
     */
    public void repairShop(Shop shop, List<String> issues) {
        plugin.getLogger().info("[ShopSystem] Réparation du shop " +
            shop.getShopId().toString().substring(0, 8) + ": " + String.join(", ", issues));

        for (String issue : issues) {
            if (issue.contains("Panneau")) {
                // Recréer le panneau
                try {
                    signManager.createOrUpdateSign(shop);
                    plugin.getLogger().info("[ShopSystem] Panneau recréé pour shop " +
                        shop.getShopId().toString().substring(0, 8));
                } catch (Exception e) {
                    plugin.getLogger().warning("[ShopSystem] Impossible de recréer le panneau: " + e.getMessage());
                }
            }

            if (issue.contains("Hologramme")) {
                // Recréer l'hologramme
                try {
                    hologramManager.createOrUpdateHologram(shop);
                    plugin.getLogger().info("[ShopSystem] Hologramme recréé pour shop " +
                        shop.getShopId().toString().substring(0, 8));
                } catch (Exception e) {
                    plugin.getLogger().warning("[ShopSystem] Impossible de recréer l'hologramme: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Démarre la tâche de rotation des hologrammes
     */
    public void startRotationTask(List<Shop> shops) {
        hologramManager.startRotationTask(shops);
    }

    /**
     * Valide et répare automatiquement une boutique
     */
    public boolean validateAndRepair(Shop shop) {
        ValidationResult result = validator.validateShop(shop);

        if (result.isValid()) {
            return true;
        }

        if (result.getSuggestedAction() == RepairAction.REPAIR) {
            repairShop(shop, result.getIssues());
            return true;
        }

        return false;
    }

    /**
     * Recréation complète des composants (en cas de problème)
     */
    public void recreateComponents(Shop shop) {
        plugin.getLogger().info("[ShopSystem] Recréation complète des composants pour shop " +
            shop.getShopId().toString().substring(0, 8));

        // Supprimer les anciens
        hologramManager.removeHologram(shop);

        // Attendre un tick
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Recréer
            signManager.createOrUpdateSign(shop);
            hologramManager.createOrUpdateHologram(shop);
        }, 1L);
    }

    // Getters pour accès aux managers individuels
    public ShopSignManager getSignManager() {
        return signManager;
    }

    public ShopHologramManager getHologramManager() {
        return hologramManager;
    }
}
