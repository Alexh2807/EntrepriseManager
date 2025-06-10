package com.gravityyfh.entreprisemanager.Shop;

import com.gravityyfh.entreprisemanager.EntrepriseManager;
import com.palmergames.bukkit.towny.event.PlotClearEvent;
import com.palmergames.bukkit.towny.event.plot.PlayerChangePlotTypeEvent;
import com.palmergames.bukkit.towny.event.plot.changeowner.PlotChangeOwnerEvent;
import com.palmergames.bukkit.towny.event.plot.changeowner.PlotUnclaimEvent;
import com.palmergames.bukkit.towny.event.town.TownRuinedEvent;
import com.palmergames.bukkit.towny.event.town.TownUnclaimEvent;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.TownBlockType;
import com.palmergames.bukkit.towny.object.WorldCoord;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class ShopDestructionListener implements Listener {
   private final EntrepriseManager plugin;
   private final ShopManager shopManager;

   public ShopDestructionListener(EntrepriseManager plugin) {
      this.plugin = plugin;
      this.shopManager = plugin.getShopManager();
   }

   @EventHandler(
           priority = EventPriority.HIGHEST,
           ignoreCancelled = true
   )
   public void onBlockBreak(BlockBreakEvent event) {
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
            this.shopManager.deleteShop(shop);
            player.sendMessage(ChatColor.GREEN + "La boutique a été supprimée avec succès.");
            Logger var10000 = this.plugin.getLogger();
            Level var10001 = Level.INFO;
            UUID var10002 = shop.getShopId();
            var10000.log(var10001, "Boutique " + var10002 + " détruite manuellement par " + player.getName());
         }
      }
   }

   private void handlePlotShopsDeletion(WorldCoord worldCoord, String reason) {
      if (worldCoord != null) {
         List<Shop> shopsOnPlot = this.shopManager.getShopsByPlot(worldCoord);
         if (!shopsOnPlot.isEmpty()) {
            this.plugin.getLogger().log(Level.INFO, "La parcelle " + worldCoord.toString() + " a subi l'événement '" + reason + "'. Suppression de " + shopsOnPlot.size() + " boutique(s).");
            ShopManager var10001 = this.shopManager;
            Objects.requireNonNull(var10001);
            shopsOnPlot.forEach(var10001::deleteShop);
         }

      }
   }

   @EventHandler(
           priority = EventPriority.MONITOR,
           ignoreCancelled = true
   )
   public void onPlotChangeOwner(PlotChangeOwnerEvent event) {
      this.handlePlotShopsDeletion(event.getTownBlock().getWorldCoord(), "Changement de Propriétaire");
   }

   @EventHandler(
           priority = EventPriority.MONITOR,
           ignoreCancelled = true
   )
   public void onTownUnclaim(TownUnclaimEvent event) {
      try {
         this.handlePlotShopsDeletion(event.getWorldCoord(), "Unclaim");
      } catch (Exception var3) {
         this.plugin.getLogger().log(Level.WARNING, "Impossible de gérer TownUnclaimEvent pour " + event.getWorldCoord().toString(), var3);
      }

   }

   @EventHandler(
           priority = EventPriority.MONITOR,
           ignoreCancelled = true
   )
   public void onPlotUnclaim(PlotUnclaimEvent event) {
      this.handlePlotShopsDeletion(event.getTownBlock().getWorldCoord(), "Plot Unclaim");
   }

   @EventHandler(
           priority = EventPriority.MONITOR,
           ignoreCancelled = true
   )
   public void onPlotClear(PlotClearEvent event) {
      this.handlePlotShopsDeletion(event.getTownBlock().getWorldCoord(), "Plot Clear");
   }

   @EventHandler(
           priority = EventPriority.MONITOR,
           ignoreCancelled = true
   )
   public void onTownRuin(TownRuinedEvent event) {
      this.plugin.getLogger().log(Level.INFO, "La ville '" + event.getTown().getName() + "' est en ruine. Suppression de toutes les boutiques de la ville.");
      Iterator var2 = event.getTown().getTownBlocks().iterator();

      while(var2.hasNext()) {
         TownBlock townBlock = (TownBlock)var2.next();
         this.handlePlotShopsDeletion(townBlock.getWorldCoord(), "Ville en Ruine");
      }

   }

   @EventHandler(
           priority = EventPriority.MONITOR,
           ignoreCancelled = true
   )
   public void onPlotTypeChange(PlayerChangePlotTypeEvent event) {
      TownBlock townBlock = event.getTownBlock();
      if (townBlock != null && event.getNewType() != TownBlockType.COMMERCIAL) {
         this.handlePlotShopsDeletion(townBlock.getWorldCoord(), "Changement de type de parcelle");
      }
   }
}