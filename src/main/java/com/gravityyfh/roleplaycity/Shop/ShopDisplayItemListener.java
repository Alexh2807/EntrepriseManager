package com.gravityyfh.roleplaycity.Shop;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.entity.Item;

/**
 * Listener empêchant la fusion des items utilisés comme affichage de boutique.
 */
public class ShopDisplayItemListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemMerge(ItemMergeEvent event) {
        Item entity = event.getEntity();
        Item target = event.getTarget();
        // Les items de vitrine ont un délai de ramassage MAX_VALUE
        if (entity.getPickupDelay() == Integer.MAX_VALUE || target.getPickupDelay() == Integer.MAX_VALUE) {
            event.setCancelled(true);
        }
    }
}
