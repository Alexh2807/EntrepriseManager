package com.gravityyfh.roleplaycity.Shop;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class ShopGUI implements Listener {
   private final RoleplayCity plugin;
   private final ShopManager shopManager;
   private final EntrepriseManagerLogic entrepriseLogic;
   private static final String TITLE_SHOP_LIST_PREFIX;
   private static final String TITLE_MANAGE_SHOP_PREFIX;
   private final Map<UUID, ShopGUI.PlayerGUIContext> playerContexts = new HashMap();

   public ShopGUI(RoleplayCity plugin) {
      this.plugin = plugin;
      this.shopManager = plugin.getShopManager();
      this.entrepriseLogic = plugin.getEntrepriseManagerLogic();
   }

   public boolean isShopMenu(String title) {
      if (title == null) {
         return false;
      } else {
         return title.startsWith(TITLE_SHOP_LIST_PREFIX) || title.startsWith(TITLE_MANAGE_SHOP_PREFIX);
      }
   }

   private ShopGUI.PlayerGUIContext getPlayerContext(Player player) {
      return (ShopGUI.PlayerGUIContext)this.playerContexts.computeIfAbsent(player.getUniqueId(), (k) -> {
         return new ShopGUI.PlayerGUIContext(TITLE_SHOP_LIST_PREFIX);
      });
   }

   public void openShopListMenu(Player player, EntrepriseManagerLogic.Entreprise entreprise, int page) {
      ShopGUI.PlayerGUIContext context = this.getPlayerContext(player);
      List<Shop> shops = this.shopManager.getShopsBySiret(entreprise.getSiret());
      int itemsPerPage = 45;
      int totalPages = Math.max(1, (int)Math.ceil((double)shops.size() / (double)itemsPerPage));
      page = Math.max(0, Math.min(page, totalPages - 1));
      context.currentPage = page;
      context.siret = entreprise.getSiret();
      String var10000 = TITLE_SHOP_LIST_PREFIX;
      String title = var10000 + ChatColor.GOLD + entreprise.getNom() + " (" + (page + 1) + "/" + totalPages + ")";
      context.navigateTo(title);
      Inventory inv = Bukkit.createInventory((InventoryHolder)null, 54, title);
      int startIndex = page * itemsPerPage;

      for(int i = 0; i < itemsPerPage && startIndex + i < shops.size(); ++i) {
         inv.setItem(i, this.createShopMenuItem((Shop)shops.get(startIndex + i)));
      }

      inv.setItem(45, this.createMenuItem(Material.EMERALD_BLOCK, ChatColor.GREEN + "Créer une nouvelle boutique"));
      if (page > 0) {
         inv.setItem(48, this.createMenuItem(Material.ARROW, ChatColor.YELLOW + "Page Précédente"));
      }

      this.addBackButton(inv, 49, "(Gestion Entreprise)");
      if (page + 1 < totalPages) {
         inv.setItem(50, this.createMenuItem(Material.ARROW, ChatColor.YELLOW + "Page Suivante"));
      }

      player.openInventory(inv);
   }

   public void openManageShopMenu(Player player, Shop shop) {
      ShopGUI.PlayerGUIContext context = this.getPlayerContext(player);
      context.selectedShopId = shop.getShopId();
      context.siret = shop.getEntrepriseSiret();
      String var10000 = TITLE_MANAGE_SHOP_PREFIX;
      String title = var10000 + shop.getShopId().toString().substring(0, 8);
      context.navigateTo(title);
      Inventory inv = Bukkit.createInventory((InventoryHolder)null, 27, title);
      inv.setItem(10, this.createMenuItem(shop.getItemTemplate().getType(), ChatColor.AQUA + "Changer l'objet en vente"));
      inv.setItem(12, this.createMenuItem(Material.GOLD_NUGGET, ChatColor.GOLD + "Changer le prix"));
      inv.setItem(14, this.createMenuItem(Material.DIAMOND, ChatColor.BLUE + "Changer la quantité"));
      inv.setItem(16, this.createMenuItem(Material.TNT, ChatColor.RED + "Supprimer cette boutique"));
      this.addBackButton(inv, 22, "(Liste des boutiques)");
      player.openInventory(inv);
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onInventoryClick(InventoryClickEvent event) {
      String title = event.getView().getTitle();
      if (this.isShopMenu(title)) {
         event.setCancelled(true);
         Player player = (Player)event.getWhoClicked();
         ItemStack clickedItem = event.getCurrentItem();
         if (clickedItem != null && clickedItem.hasItemMeta()) {
            ShopGUI.PlayerGUIContext context = this.getPlayerContext(player);
            String itemName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
            if (itemName.startsWith("Retour")) {
               this.handleGoBack(player, context);
            } else {
               this.handleMenuClick(player, context, itemName, clickedItem);
            }
         }
      }
   }

   private void handleGoBack(Player player, ShopGUI.PlayerGUIContext context) {
      context.goBack();
      String previousMenu = context.currentMenuTitle;
      EntrepriseManagerLogic.Entreprise entreprise = this.entrepriseLogic.getEntrepriseBySiret(context.siret);
      if (entreprise == null) {
         player.closeInventory();
      } else {
         if (previousMenu.startsWith(TITLE_SHOP_LIST_PREFIX)) {
            this.openShopListMenu(player, entreprise, context.currentPage);
         } else {
            this.plugin.getEntrepriseGUI().openManageSpecificEntrepriseMenu(player, entreprise);
         }

      }
   }

   private void handleMenuClick(Player player, ShopGUI.PlayerGUIContext context, String itemName, ItemStack clickedItem) {
      String currentTitle = context.currentMenuTitle;
      EntrepriseManagerLogic.Entreprise entreprise = this.entrepriseLogic.getEntrepriseBySiret(context.siret);
      if (entreprise != null) {
         if (currentTitle.startsWith(TITLE_SHOP_LIST_PREFIX)) {
            byte var8 = -1;
            switch(itemName.hashCode()) {
            case -1544691435:
               if (itemName.equals("Créer une nouvelle boutique")) {
                  var8 = 0;
               }
               break;
            case -639553186:
               if (itemName.equals("Page Suivante")) {
                  var8 = 2;
               }
               break;
            case 934298434:
               if (itemName.equals("Page Précédente")) {
                  var8 = 1;
               }
            }

            switch(var8) {
            case 0:
               player.closeInventory();
               this.shopManager.startShopCreationProcess(player, entreprise);
               break;
            case 1:
               this.openShopListMenu(player, entreprise, context.currentPage - 1);
               break;
            case 2:
               this.openShopListMenu(player, entreprise, context.currentPage + 1);
               break;
            default:
               UUID shopId = this.getShopIdFromLore(clickedItem.getItemMeta().getLore());
               if (shopId != null) {
                  Shop shop = this.shopManager.getShopById(shopId);
                  if (shop != null) {
                     this.openManageShopMenu(player, shop);
                  }
               }
            }
         } else if (currentTitle.startsWith(TITLE_MANAGE_SHOP_PREFIX)) {
            Shop shop = this.shopManager.getShopById(context.selectedShopId);
            if (shop == null) {
               player.sendMessage(ChatColor.RED + "Erreur: Boutique introuvable.");
               this.openShopListMenu(player, entreprise, 0);
               return;
            }

            byte var11 = -1;
            switch(itemName.hashCode()) {
            case -2123309792:
               if (itemName.equals("Supprimer cette boutique")) {
                  var11 = 3;
               }
               break;
            case -1569613108:
               if (itemName.equals("Changer l'objet en vente")) {
                  var11 = 0;
               }
               break;
            case 947998408:
               if (itemName.equals("Changer la quantité")) {
                  var11 = 2;
               }
               break;
            case 1066392954:
               if (itemName.equals("Changer le prix")) {
                  var11 = 1;
               }
            }

            switch(var11) {
            case 0:
               ItemStack newItem = player.getInventory().getItemInMainHand();
               if (newItem.getType() != Material.AIR) {
                  this.shopManager.changeShopItem(shop, newItem.clone());
                  player.sendMessage(ChatColor.GREEN + "Objet mis à jour !");
                  this.openManageShopMenu(player, shop);
               } else {
                  player.sendMessage(ChatColor.RED + "Vous devez tenir un objet en main !");
               }
               break;
            case 1:
               this.plugin.getChatListener().requestNewPriceForShop(player, shop);
               player.closeInventory();
               break;
            case 2:
               this.plugin.getChatListener().requestNewQuantityForShop(player, shop);
               player.closeInventory();
               break;
            case 3:
               this.shopManager.deleteShop(shop);
               player.sendMessage(ChatColor.GREEN + "Boutique supprimée.");
               this.openShopListMenu(player, entreprise, context.currentPage);
            }
         }

      }
   }

   @EventHandler
   public void onInventoryClose(InventoryCloseEvent event) {
      Player player = (Player)event.getPlayer();
      if (this.playerContexts.containsKey(player.getUniqueId())) {
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (!player.isOnline()) {
               this.playerContexts.remove(player.getUniqueId());
            } else {
               String currentOpenInventoryTitle = player.getOpenInventory().getTitle();
               if (!this.plugin.getChatListener().isPlayerWaitingForInput(player.getUniqueId()) && !this.isShopMenu(currentOpenInventoryTitle)) {
                  this.playerContexts.remove(player.getUniqueId());
               }

            }
         }, 1L);
      }

   }

   private ItemStack createMenuItem(Material material, String name, List<String> lore) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(name);
         if (lore != null) {
            meta.setLore(lore);
         }

         item.setItemMeta(meta);
      }

      return item;
   }

   private ItemStack createMenuItem(Material material, String name) {
      return this.createMenuItem(material, name, (List)null);
   }

   private void addBackButton(Inventory inv, int slot, String hint) {
      inv.setItem(slot, this.createMenuItem(Material.OAK_DOOR, ChatColor.RED + "Retour " + ChatColor.GRAY + hint));
   }

   private ItemStack createShopMenuItem(Shop shop) {
      String locationString = String.format("X:%d Y:%d Z:%d", shop.getLocation().getBlockX(), shop.getLocation().getBlockY(), shop.getLocation().getBlockZ());
      Material var10001 = shop.getItemTemplate().getType();
      String var10002 = ChatColor.GREEN + "Boutique " + shop.getShopId().toString().substring(0, 8);
      String[] var10003 = new String[]{ChatColor.GRAY + locationString, null, null, null, null, null};
      ChatColor var10006 = ChatColor.AQUA;
      var10003[1] = var10006 + "Vente: " + ChatColor.WHITE + shop.getQuantityPerSale() + "x " + this.formatMaterialName(shop.getItemTemplate().getType());
      var10006 = ChatColor.GOLD;
      var10003[2] = var10006 + "Prix: " + ChatColor.WHITE + String.format("%,.2f", shop.getPrice()) + " €";
      var10003[3] = "";
      var10003[4] = ChatColor.YELLOW + "Cliquez pour gérer.";
      var10006 = ChatColor.DARK_GRAY;
      var10003[5] = var10006 + "ID:" + shop.getShopId().toString();
      return this.createMenuItem(var10001, var10002, Arrays.asList(var10003));
   }

   private UUID getShopIdFromLore(List<String> lore) {
      if (lore == null) {
         return null;
      } else {
         Iterator var2 = lore.iterator();

         String stripped;
         do {
            if (!var2.hasNext()) {
               return null;
            }

            String line = (String)var2.next();
            stripped = ChatColor.stripColor(line);
         } while(!stripped.startsWith("ID:"));

         try {
            return UUID.fromString(stripped.substring(3));
         } catch (IllegalArgumentException var6) {
            return null;
         }
      }
   }

   private String formatMaterialName(Material material) {
      return material == null ? "Inconnu" : (String)Arrays.stream(material.name().split("_")).map((s) -> {
         String var10000 = s.substring(0, 1).toUpperCase();
         return var10000 + s.substring(1).toLowerCase();
      }).collect(Collectors.joining(" "));
   }

   static {
      TITLE_SHOP_LIST_PREFIX = ChatColor.DARK_BLUE + "Boutiques: ";
      TITLE_MANAGE_SHOP_PREFIX = ChatColor.DARK_BLUE + "Gérer Boutique: ";
   }

   private static class PlayerGUIContext {
      final Stack<String> menuHistory = new Stack();
      String currentMenuTitle;
      String siret;
      UUID selectedShopId;
      int currentPage;

      PlayerGUIContext(String initialMenu) {
         this.navigateTo(initialMenu);
      }

      void navigateTo(String newMenuTitle) {
         if (this.menuHistory.isEmpty() || !((String)this.menuHistory.peek()).equals(newMenuTitle)) {
            this.menuHistory.push(newMenuTitle);
         }

         this.currentMenuTitle = newMenuTitle;
         this.currentPage = 0;
      }

      void goBack() {
         if (this.menuHistory.size() > 1) {
            this.menuHistory.pop();
         }

         this.currentMenuTitle = (String)this.menuHistory.peek();
         this.currentPage = 0;
      }
   }
}
