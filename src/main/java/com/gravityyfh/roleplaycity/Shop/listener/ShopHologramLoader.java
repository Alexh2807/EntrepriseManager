package com.gravityyfh.roleplaycity.shop.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.shop.manager.ShopManager;
import com.gravityyfh.roleplaycity.shop.model.Shop;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Listener qui charge automatiquement les hologrammes des shops
 * lorsqu'un joueur entre dans la zone de visibilité
 */
public class ShopHologramLoader implements Listener {
    private final RoleplayCity plugin;
    private final ShopManager shopManager;
    private final double viewRange;
    private final boolean hologramEnabled;

    // Cache pour éviter de vérifier trop souvent les mêmes joueurs/shops
    private final Set<String> recentlyChecked = new HashSet<>();
    private static final long CACHE_DURATION_MS = 2000; // 2 secondes

    public ShopHologramLoader(RoleplayCity plugin, ShopManager shopManager) {
        this.plugin = plugin;
        this.shopManager = shopManager;
        this.viewRange = plugin.getConfig().getDouble("shop-system.hologram-view-range", 32.0);
        this.hologramEnabled = plugin.getConfig().getBoolean("shop-system.hologram-enabled", true);

        // Nettoyer le cache régulièrement
        startCacheCleanupTask();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!hologramEnabled) {
            return;
        }

        // Optimisation: Ignorer les petits mouvements (changement de rotation uniquement)
        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null || (from.getBlockX() == to.getBlockX() &&
                           from.getBlockY() == to.getBlockY() &&
                           from.getBlockZ() == to.getBlockZ())) {
            return;
        }

        Player player = event.getPlayer();
        checkNearbyShops(player, to);
    }

    /**
     * Vérifie les shops à proximité du joueur et charge les hologrammes manquants
     */
    private void checkNearbyShops(Player player, Location playerLocation) {
        for (Shop shop : shopManager.getAllShops()) {
            // Vérifier si le shop est dans le même monde
            Location hologramLoc = shop.getHologramLocation();
            if (hologramLoc == null || hologramLoc.getWorld() == null) {
                continue;
            }

            if (!hologramLoc.getWorld().equals(playerLocation.getWorld())) {
                continue;
            }

            // Calculer la distance
            double distance = hologramLoc.distance(playerLocation);

            // Si le joueur est dans la zone de visibilité
            if (distance <= viewRange) {
                // Créer une clé unique pour ce check
                String cacheKey = player.getUniqueId() + "_" + shop.getShopId();

                // Vérifier si on a déjà vérifié récemment
                if (recentlyChecked.contains(cacheKey)) {
                    continue;
                }

                // Vérifier si l'hologramme est chargé
                if (!isHologramLoaded(shop)) {
                    // Charger l'hologramme
                    loadHologram(shop);
                }

                // Ajouter au cache
                recentlyChecked.add(cacheKey);
            }
        }
    }

    /**
     * Vérifie si l'hologramme d'un shop est actuellement chargé
     */
    private boolean isHologramLoaded(Shop shop) {
        // Vérifier si l'entity ItemDisplay existe
        if (shop.getDisplayItemEntityId() == null) {
            return false;
        }

        Entity entity = Bukkit.getEntity(shop.getDisplayItemEntityId());
        if (entity == null || !entity.isValid()) {
            return false;
        }

        // Vérifier si au moins une entity de texte existe
        if (shop.getHologramTextEntityIds() == null || shop.getHologramTextEntityIds().isEmpty()) {
            return false;
        }

        // Vérifier qu'au moins une entity de texte est valide
        for (UUID textEntityId : shop.getHologramTextEntityIds()) {
            Entity textEntity = Bukkit.getEntity(textEntityId);
            if (textEntity != null && textEntity.isValid()) {
                return true; // Au moins une entity de texte existe
            }
        }

        return false;
    }

    /**
     * Charge l'hologramme d'un shop
     */
    private void loadHologram(Shop shop) {
        plugin.getLogger().fine("[ShopSystem] Chargement automatique de l'hologramme pour shop " +
                                shop.getShopId().toString().substring(0, 8));

        // Utiliser le système de composants pour mettre à jour l'hologramme
        shopManager.getComponents().updateHologram(shop);
    }

    /**
     * Démarre une tâche qui nettoie le cache régulièrement
     */
    private void startCacheCleanupTask() {
        long cleanupInterval = CACHE_DURATION_MS / 50; // Convertir ms en ticks (20 ticks = 1s)

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            recentlyChecked.clear();
        }, cleanupInterval, cleanupInterval);
    }
}