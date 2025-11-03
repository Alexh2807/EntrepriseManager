// Fichier : /src/main/java/com/gravityyfh/entreprisemanager/Shop/ShopDestructionListener.java

package com.gravityyfh.roleplaycity.Shop;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.palmergames.bukkit.towny.event.PlotClearEvent;
import com.palmergames.bukkit.towny.event.town.TownUnclaimEvent;
import com.palmergames.bukkit.towny.event.plot.PlayerChangePlotTypeEvent;
import com.palmergames.bukkit.towny.event.plot.changeowner.PlotChangeOwnerEvent;
import com.palmergames.bukkit.towny.event.plot.changeowner.PlotUnclaimEvent;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.TownBlockType;
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

public class ShopDestructionListener implements Listener {
   private final RoleplayCity plugin;
   private final ShopManager shopManager;

   public ShopDestructionListener(RoleplayCity plugin) {
      this.plugin = plugin;
      this.shopManager = plugin.getShopManager();
   }

   @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
   public void onBlockBreak(BlockBreakEvent event) {
      try {
         Block block = event.getBlock();
         Shop shop = null;
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
               this.shopManager.deleteShop(shop); // Utilise la méthode sécurisée
               player.sendMessage(ChatColor.GREEN + "Boutique supprimée avec succès.");
            }
         }
      } catch (Exception e) {
         this.plugin.getLogger().log(Level.SEVERE, "Erreur critique dans onBlockBreak:", e);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onPlotChangeOwner(PlotChangeOwnerEvent event) {
      shopManager.deleteShopsAt(event.getTownBlock().getWorldCoord(), "Changement de Propriétaire");
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onPlotUnclaim(PlotUnclaimEvent event) {
      shopManager.deleteShopsAt(event.getTownBlock().getWorldCoord(), "Plot Unclaim");
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onTownUnclaim(TownUnclaimEvent event) {
      shopManager.deleteShopsAt(event.getWorldCoord(), "Town Unclaim");
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onPlotClear(PlotClearEvent event) {
      shopManager.deleteShopsAt(event.getTownBlock().getWorldCoord(), "Plot Clear");
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onPlotTypeChange(PlayerChangePlotTypeEvent event) {
      TownBlock townBlock = event.getTownBlock();
      if (townBlock != null && event.getNewType() != TownBlockType.COMMERCIAL) {
         shopManager.deleteShopsAt(townBlock.getWorldCoord(), "Changement de type de parcelle (non-commercial)");
      }
   }
}