package com.gravityyfh.entreprisemanager.Shop;

import com.gravityyfh.entreprisemanager.EntrepriseManager;
import java.util.logging.Level;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class ShopInteractionListener implements Listener {
   private final ShopManager shopManager;
   private final EntrepriseManager plugin;

   public ShopInteractionListener(EntrepriseManager plugin) {
      this.shopManager = plugin.getShopManager();
      this.plugin = plugin;
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onPlayerInteract(PlayerInteractEvent event) {
      if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
         Block clickedBlock = event.getClickedBlock();
         if (clickedBlock != null) {
            if (clickedBlock.getState() instanceof Sign) {
               Sign sign = (Sign)clickedBlock.getState();
               String firstLine = sign.getLine(0);
               String strippedFirstLine = ChatColor.stripColor(firstLine);
               if (strippedFirstLine.equalsIgnoreCase("[Boutique]")) {
                  event.setCancelled(true);
                  Player player = event.getPlayer();
                  Shop shop = this.shopManager.getShopBySignLocation(clickedBlock.getLocation());
                  if (shop == null) {
                     this.plugin.getLogger().log(Level.WARNING, "[Shop-Debug] ERREUR : getShopBySignLocation a retourné NULL pour le panneau à " + clickedBlock.getLocation().toVector());
                     player.sendMessage(ChatColor.RED + "Cette boutique semble être cassée ou invalide (code: L-NULL).");
                     this.shopManager.cleanupOrphanedShopDisplay(clickedBlock.getLocation());
                     clickedBlock.breakNaturally();
                  } else {
                     this.shopManager.purchaseItem(player, shop);
                  }
               }
            } else {
               if (clickedBlock.getState() instanceof Chest) {
                  Shop shop = this.shopManager.getShopByChestLocation(clickedBlock.getLocation());
                  if (shop == null) {
                     return;
                  }

                  Player player = event.getPlayer();
                  boolean isOwner = player.getUniqueId().equals(shop.getOwnerUUID());
                  boolean isAdmin = player.hasPermission("entreprisemanager.admin.bypasschest");
                  if (!isOwner && !isAdmin) {
                     event.setCancelled(true);
                     player.sendMessage(ChatColor.RED + "Vous ne pouvez pas accéder au stock de cette boutique.");
                  }
               }

            }
         }
      }
   }
}
