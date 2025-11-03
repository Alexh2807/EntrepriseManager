package com.gravityyfh.roleplaycity.Shop;

import com.gravityyfh.roleplaycity.RoleplayCity;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import java.util.logging.Level;

/**
 * Listener pour la protection et destruction des boutiques
 * Version adaptée sans dépendance Towny
 */
public class ShopDestructionListener implements Listener {
   private final RoleplayCity plugin;
   private final ShopManager shopManager;

   public ShopDestructionListener(RoleplayCity plugin) {
      this.plugin = plugin;
      this.shopManager = plugin.getShopManager();
   }

   /**
    * Empêche la destruction de coffres/panneaux de boutique par des joueurs non autorisés
    */
   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onBlockBreak(BlockBreakEvent event) {
      try {
         Block block = event.getBlock();
         Shop shop = null;

         // Vérifier si c'est un coffre ou un panneau de boutique
         if (block.getState() instanceof Chest) {
            shop = this.shopManager.getShopByChestLocation(block.getLocation());
         } else if (block.getState() instanceof Sign) {
            shop = this.shopManager.getShopBySignLocation(block.getLocation());
         }

         if (shop != null) {
            Player player = event.getPlayer();
            boolean isOwner = player.getUniqueId().equals(shop.getOwnerUUID());
            boolean isAdmin = player.hasPermission("entreprisemanager.admin.breakshops");

            if (!isOwner && !isAdmin) {
               player.sendMessage(ChatColor.RED + "Vous ne pouvez pas détruire une boutique qui ne vous appartient pas.");
               event.setCancelled(true);
            } else {
               this.shopManager.deleteShop(shop);
               player.sendMessage(ChatColor.GREEN + "Boutique supprimée avec succès.");
            }
         }
      } catch (Exception e) {
         this.plugin.getLogger().log(Level.SEVERE, "Erreur critique dans onBlockBreak:", e);
      }
   }

   // Note: Les événements de changement de propriétaire de plot, unclaim, etc.
   // seront gérés automatiquement via TownDeleteEvent et TownMemberLeaveEvent
   // qui suppriment les entreprises (et donc leurs boutiques)
}
