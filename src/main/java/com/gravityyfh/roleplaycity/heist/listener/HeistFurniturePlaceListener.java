package com.gravityyfh.roleplaycity.heist.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.heist.data.PlacedBomb;
import com.gravityyfh.roleplaycity.heist.manager.HeistManager;
import dev.lone.itemsadder.api.CustomFurniture;
import dev.lone.itemsadder.api.Events.FurniturePlaceSuccessEvent;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Listener pour intercepter le placement de la bombe via ItemsAdder.
 * Enregistre la bombe comme "posée mais non activée" dans HeistManager.
 */
public class HeistFurniturePlaceListener implements Listener {

    private static final String BOMB_NAMESPACED_ID = "my_items:bomb";

    private final RoleplayCity plugin;
    private final HeistManager heistManager;

    public HeistFurniturePlaceListener(RoleplayCity plugin, HeistManager heistManager) {
        this.plugin = plugin;
        this.heistManager = heistManager;
    }

    /**
     * Intercepte le placement réussi d'un furniture ItemsAdder.
     * Si c'est une bombe, l'enregistre pour la confirmation ultérieure.
     *
     * NOTE: Cet événement est déclenché quand le joueur pose la bombe manuellement
     * via /iaget my_items:bomb. Si la bombe est posée via notre système
     * (PLACE_HEIST_BOMB), elle est déjà enregistrée - on vérifie donc d'abord.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurniturePlaced(FurniturePlaceSuccessEvent event) {
        CustomFurniture furniture = event.getFurniture();
        if (furniture == null) return;

        String namespacedId = furniture.getNamespacedID();
        if (!BOMB_NAMESPACED_ID.equals(namespacedId)) return;

        Player player = event.getPlayer();
        if (player == null) return;

        // Récupérer l'UUID de l'ArmorStand
        java.util.UUID armorStandId;
        Location location;
        try {
            armorStandId = furniture.getArmorstand().getUniqueId();
            location = furniture.getArmorstand().getLocation();
        } catch (Exception e) {
            plugin.getLogger().warning("[HeistFurniturePlaceListener] Impossible de récupérer l'ArmorStand");
            return;
        }

        // Vérifier si cette bombe est déjà enregistrée (cas où elle a été posée via PLACE_HEIST_BOMB)
        if (heistManager.getPlacedBombByArmorStand(armorStandId) != null) {
            // Bombe déjà enregistrée par notre système, ne pas re-enregistrer
            plugin.getLogger().fine("[Heist] Bombe déjà enregistrée (via PLACE_HEIST_BOMB), skip FurniturePlaceListener");
            return;
        }

        // Enregistrer la bombe comme "posée mais pas activée"
        PlacedBomb bomb = heistManager.registerPlacedBomb(
            player,
            location,
            armorStandId
        );

        if (bomb == null) {
            player.sendMessage(ChatColor.RED + "Erreur lors du placement de la bombe.");
            return;
        }

        // Message au joueur selon le contexte
        if (bomb.isOnClaimedPlot()) {
            player.sendMessage(ChatColor.YELLOW + "Bombe posée sur le terrain " +
                ChatColor.WHITE + bomb.getPlotId() +
                ChatColor.YELLOW + " de " + ChatColor.WHITE + bomb.getTownName());
        } else {
            player.sendMessage(ChatColor.YELLOW + "Bombe posée (hors zone protégée)");
        }
        player.sendMessage(ChatColor.GREEN + "➤ Clic droit sur la bombe pour confirmer le déclenchement.");

        plugin.getLogger().info("[Heist] Bombe posée (manuellement) par " + player.getName() +
            " à " + location.getWorld().getName() + " " +
            location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ());
    }
}
