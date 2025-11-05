package com.gravityyfh.roleplaycity.Shop;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.PlotType;
import com.gravityyfh.roleplaycity.town.manager.ClaimManager;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Display;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public class ShopManager {
   private final RoleplayCity plugin;
   private final EntrepriseManagerLogic entrepriseLogic;
   private final Map<UUID, Shop> shops = new ConcurrentHashMap();
   private final File shopsFile;

   // FIX CRITIQUE P1.2: Lock pour éviter corruption lors de sauvegardes parallèles
   private final ReentrantLock saveLock = new ReentrantLock();

   public ShopManager(RoleplayCity plugin) {
      this.plugin = plugin;
      this.entrepriseLogic = plugin.getEntrepriseManagerLogic();
      this.shopsFile = new File(plugin.getDataFolder(), "shops.yml");
   }

   public void purchaseItem(Player buyer, Shop shop) {
      EntrepriseManagerLogic.Entreprise entreprise = this.entrepriseLogic.getEntrepriseBySiret(shop.getEntrepriseSiret());
      if (entreprise == null) {
         buyer.sendMessage(ChatColor.RED + "L'entreprise propri├®taire de cette boutique n'existe plus.");
      } else if (UUID.fromString(entreprise.getGerantUUID()).equals(buyer.getUniqueId())) {
         buyer.sendMessage(ChatColor.YELLOW + "Vous ne pouvez pas acheter dans la boutique de votre propre entreprise.");
      } else {
         Block chestBlock = shop.getLocation().getBlock();
         if (!(chestBlock.getState() instanceof Chest)) {
            buyer.sendMessage(ChatColor.RED + "Erreur : Le coffre de la boutique est manquant.");
         } else {
            Chest chest = (Chest) chestBlock.getState();
            ItemStack itemToSell = shop.getSaleBundle();
            ChatColor var10001;
            if (!chest.getInventory().containsAtLeast(itemToSell, itemToSell.getAmount())) {
               buyer.sendMessage(ChatColor.RED + "Cette boutique est en rupture de stock.");
               Player owner = Bukkit.getPlayer(shop.getOwnerUUID());
               if (owner != null && owner.isOnline()) {
                  var10001 = ChatColor.YELLOW;
                  owner.sendMessage(var10001 + "[Boutique] " + buyer.getName() + " a tent├® d'acheter '" + this.formatMaterialName(itemToSell.getType()) + "' mais une de vos boutiques est en rupture de stock !");
               }

            } else {
               double price = shop.getPrice();
               if (!RoleplayCity.getEconomy().has(buyer, price)) {
                  var10001 = ChatColor.RED;
                  buyer.sendMessage(var10001 + "Vous n'avez pas assez d'argent. Il vous faut " + String.format("%,.2fÔé¼", price) + ".");
               } else if (buyer.getInventory().firstEmpty() == -1) {
                  buyer.sendMessage(ChatColor.RED + "Votre inventaire est plein.");
               } else {
                  EconomyResponse buyerResponse = RoleplayCity.getEconomy().withdrawPlayer(buyer, price);
                  if (!buyerResponse.transactionSuccess()) {
                     buyer.sendMessage(ChatColor.RED + "Une erreur est survenue lors du paiement : " + buyerResponse.errorMessage);
                  } else {
                     // --- MODIFICATION START ---
                     // The sale amount is now added to the hourly turnover pool instead of the direct balance.
                     // The transaction logging is handled by the central hourly task.
                     this.entrepriseLogic.enregistrerRevenuMagasin(entreprise.getNom(), price);
                     // --- MODIFICATION END ---

                     // FIX CRITIQUE: Vérifier que removeItem() réussit avant de donner l'item
                     java.util.HashMap<Integer, ItemStack> leftover = chest.getInventory().removeItem(itemToSell.clone());
                     if (!leftover.isEmpty()) {
                        // Échec du retrait → Le stock a été vidé entre-temps
                        // Rembourser l'acheteur
                        RoleplayCity.getEconomy().depositPlayer(buyer, price);
                        this.entrepriseLogic.annulerRevenuMagasin(entreprise.getNom(), price);

                        buyer.sendMessage(ChatColor.RED + "Stock insuffisant (le coffre a été vidé entre-temps).");
                        buyer.sendMessage(ChatColor.YELLOW + "Vous avez été remboursé automatiquement.");

                        // Notifier le propriétaire
                        Player owner = Bukkit.getPlayer(shop.getOwnerUUID());
                        if (owner != null && owner.isOnline()) {
                           owner.sendMessage(ChatColor.RED + "[Boutique] Vente échouée à " + buyer.getName() + " (stock insuffisant).");
                           owner.sendMessage(ChatColor.YELLOW + "Veuillez réapprovisionner votre boutique.");
                        }
                        return;
                     }

                     // Succès du retrait → Donner l'item à l'acheteur
                     buyer.getInventory().addItem(itemToSell.clone());
                     var10001 = ChatColor.GREEN;
                     buyer.sendMessage(var10001 + "Vous avez achet├® " + itemToSell.getAmount() + "x " + this.formatMaterialName(itemToSell.getType()) + " pour " + String.format("%,.2fÔé¼", price) + ".");
                     Player owner = Bukkit.getPlayer(shop.getOwnerUUID());
                     if (owner != null && owner.isOnline()) {
                        // The message to the owner is simplified as the balance is no longer updated instantly.
                        var10001 = ChatColor.GREEN;
                        owner.sendMessage(var10001 + "[Boutique] Vente de " + itemToSell.getAmount() + "x " + this.formatMaterialName(itemToSell.getType()) + " ├á " + buyer.getName() + " pour " + String.format("%,.2fÔé¼", price) + ".");
                        owner.sendMessage(ChatColor.AQUA + "Les revenus de la vente seront trait├®s dans le prochain rapport horaire de l'entreprise '" + entreprise.getNom() + "'.");
                     }
                  }
               }
            }
         }
      }
   }

   public void startShopCreationProcess(Player player, EntrepriseManagerLogic.Entreprise entreprise) {
      if (!UUID.fromString(entreprise.getGerantUUID()).equals(player.getUniqueId())) {
         player.sendMessage(ChatColor.RED + "Seul le g├®rant peut cr├®er des boutiques pour cette entreprise.");
      } else {
         int maxShops = this.plugin.getConfig().getInt("shop.max-shops-per-enterprise", 10);
         if (this.getShopsBySiret(entreprise.getSiret()).size() >= maxShops) {
            player.sendMessage(ChatColor.RED + "Cette entreprise a d├®j├á atteint le nombre maximum de boutiques (" + maxShops + ").");
         } else {
            Block targetBlock = this.getTargetChest(player);
            if (targetBlock == null) {
               player.sendMessage(ChatColor.RED + "Vous devez regarder un coffre pour cr├®er une boutique.");
            } else {
               Location chestLocation = targetBlock.getLocation();
               if (this.getShopByChestLocation(chestLocation) != null) {
                  player.sendMessage(ChatColor.RED + "Il y a d├®j├á une boutique sur ce coffre.");
               } else {
                  // V├®rifier avec notre syst├¿me de ville
                  ClaimManager claimManager = plugin.getClaimManager();
                  if (claimManager == null) {
                     player.sendMessage(ChatColor.RED + "Syst├¿me de ville non disponible.");
                     return;
                  }

                  Plot plot = claimManager.getPlotAt(chestLocation);
                  if (plot == null) {
                     player.sendMessage(ChatColor.RED + "Vous devez ├¬tre sur une parcelle de ville pour cr├®er une boutique.");
                     return;
                  }

                  // V├®rifier que c'est une parcelle professionnelle
                  if (plot.getType() != PlotType.PROFESSIONNEL) {
                     player.sendMessage(ChatColor.RED + "La parcelle doit ├¬tre de type PROFESSIONNEL pour placer une boutique.");
                     return;
                  }

                  // NOUVEAU : D├®terminer quel SIRET est autoris├® sur ce terrain
                  String authorizedSiret = null;
                  boolean isRented = (plot.getRenterUuid() != null);

                  if (isRented) {
                     // Terrain lou├® ÔåÆ seul le locataire peut cr├®er des shops (avec SON entreprise)
                     if (!plot.isRentedBy(player.getUniqueId())) {
                        player.sendMessage(ChatColor.RED + "Ce terrain est lou├® par quelqu'un d'autre.");
                        player.sendMessage(ChatColor.YELLOW + "Seul le locataire actuel peut cr├®er des boutiques ici.");
                        return;
                     }
                     // R├®cup├®rer le SIRET de l'entreprise du locataire
                     authorizedSiret = plot.getRenterCompanySiret();

                     if (authorizedSiret == null) {
                        player.sendMessage(ChatColor.RED + "Erreur: Aucune entreprise associ├®e ├á votre location.");
                        return;
                     }
                  } else {
                     // Terrain PAS lou├® ÔåÆ seul le propri├®taire peut cr├®er des shops
                     if (!plot.isOwnedBy(player.getUniqueId())) {
                        player.sendMessage(ChatColor.RED + "Vous devez ├¬tre propri├®taire de cette parcelle.");
                        return;
                     }
                     // R├®cup├®rer le SIRET de l'entreprise propri├®taire
                     authorizedSiret = plot.getCompanySiret();

                     if (authorizedSiret == null) {
                        player.sendMessage(ChatColor.RED + "Erreur: Aucune entreprise associ├®e ├á ce terrain.");
                        return;
                     }
                  }

                  // NOUVEAU : V├®rifier que l'entreprise du shop correspond au terrain
                  if (!entreprise.getSiret().equals(authorizedSiret)) {
                     player.sendMessage(ChatColor.RED + "Ô£ù Cette entreprise n'est pas autoris├®e sur ce terrain !");
                     player.sendMessage(ChatColor.YELLOW + "Seule l'entreprise li├®e au terrain peut cr├®er des boutiques ici.");

                     // Afficher quelle entreprise est autoris├®e
                     EntrepriseManagerLogic.Entreprise authorizedCompany = this.entrepriseLogic.getEntrepriseBySiret(authorizedSiret);
                     if (authorizedCompany != null) {
                        player.sendMessage(ChatColor.GRAY + "Entreprise autoris├®e: " + ChatColor.WHITE + authorizedCompany.getNom());
                     }
                     return;
                  }

                  ItemStack itemInHand = player.getInventory().getItemInMainHand();
                  if (itemInHand.getType() == Material.AIR) {
                     player.sendMessage(ChatColor.RED + "Vous devez tenir dans votre main l'objet que vous souhaitez vendre.");
                  } else {
                     this.plugin.getChatListener().requestShopCreationDetails(player, entreprise, chestLocation, itemInHand.clone());
                  }
               }
            }
         }
      }
   }

   public void finalizeShopCreation(Player gerant, EntrepriseManagerLogic.Entreprise entreprise, Location chestLocation, ItemStack itemToSell, int quantity, double price) {
      if (!(price <= 0.0D) && quantity > 0) {
         String townName = null;
         String tbWorld = null;
         int tbX = 0;
         int tbZ = 0;

         // Utiliser notre syst├¿me de ville
         ClaimManager claimManager = plugin.getClaimManager();
         if (claimManager != null) {
            Plot plot = claimManager.getPlotAt(chestLocation);
            if (plot != null) {
               townName = plot.getTownName();
               tbWorld = plot.getWorldName();
               tbX = plot.getChunkX();
               tbZ = plot.getChunkZ();
            }
         }

         Shop newShop = new Shop(entreprise.getNom(), entreprise.getSiret(), gerant.getUniqueId(), gerant.getName(),
                 chestLocation, townName, tbWorld, tbX, tbZ, itemToSell, quantity, price);
         this.createDisplayItem(newShop);
         this.placeAndUpdateShopSign(newShop, entreprise, gerant);
         this.shops.put(newShop.getShopId(), newShop);
         this.saveShops();
         ChatColor var10001 = ChatColor.GREEN;
         gerant.sendMessage(var10001 + "Nouvelle boutique pour '" + entreprise.getNom() + "' cr├®├®e avec succ├¿s !");
      } else {
         gerant.sendMessage(ChatColor.RED + "Le prix et la quantit├® doivent ├¬tre positifs.");
      }
   }

// Dans /src/main/java/com/gravityyfh/entreprisemanager/Shop/ShopManager.java

   /**
    * Supprime compl├¿tement une boutique de mani├¿re s├®curis├®e (thread-safe).
    * <p>
    * Cette m├®thode retire imm├®diatement la boutique du registre de donn├®es,
    * puis planifie la suppression des ├®l├®ments visuels (panneau, item flottant)
    * sur le thread principal du serveur pour ├®viter les erreurs asynchrones.
    *
    * @param shop La boutique ├á supprimer.
    */
   public void deleteShop(Shop shop) {
      if (shop == null) {
         return;
      }

      // 1. Retire la boutique du registre interne. C'est une action s├╗re depuis n'importe quel thread.
      if (this.shops.remove(shop.getShopId()) == null) {
         // La boutique n'├®tait pas dans la liste, elle a peut-├¬tre d├®j├á ├®t├® supprim├®e.
         this.plugin.getLogger().log(Level.WARNING, "[Suppression] Tentative de suppression d'une boutique (" + shop.getShopId() + ") qui n'├®tait pas dans le registre.");
         return;
      }

      this.plugin.getLogger().log(Level.INFO, "[Suppression] Boutique " + shop.getShopId() + " retir├®e du registre. Planification du nettoyage visuel sur le thread principal.");

      // 2. Planifie toutes les interactions avec le monde du jeu pour qu'elles s'ex├®cutent sur le thread principal.
      //    Ceci emp├¬che l'erreur "Asynchronous chunk load!".
      Bukkit.getScheduler().runTask(this.plugin, () -> {
         // --- D├®but du code ex├®cut├® en toute s├®curit├® sur le thread principal ---

         // A. Logique de suppression de l'item flottant (Display Item)
         if (shop.getDisplayItemID() != null) {
            Entity entity = Bukkit.getEntity(shop.getDisplayItemID());
            if (entity instanceof Item) {
               entity.remove();
               this.plugin.getLogger().log(Level.FINE, "[Suppression-Sync] Item flottant " + shop.getDisplayItemID() + " supprim├®.");
            }
         }

         // S├®curit├® suppl├®mentaire : nettoyer les items flottants orphelins ├á proximit├® au cas o├╣ l'UUID serait perdu.
         Location floatingItemLocation = getFloatingItemLocation(shop.getLocation());
         if (floatingItemLocation != null && floatingItemLocation.isWorldLoaded()) {
            for (Entity nearby : floatingItemLocation.getWorld().getNearbyEntities(floatingItemLocation, 0.3D, 0.3D, 0.3D)) {
               if (nearby instanceof Item && ((Item) nearby).getPickupDelay() == Integer.MAX_VALUE) {
                  nearby.remove();
                  this.plugin.getLogger().log(Level.FINE, "[Suppression-Sync] Item flottant orphelin ├á proximit├® de " + floatingItemLocation.toVector() + " supprim├®.");
                  break;
               }
            }
         }

         // B. Logique de suppression du panneau (Sign)
         Block chestBlock = shop.getLocation().getBlock();
         Block signBlock = findSignAttachedToChest(chestBlock);
         if (signBlock != null) {
            signBlock.setType(Material.AIR, false); // Modifie le monde
            this.plugin.getLogger().log(Level.FINE, "[Suppression-Sync] Panneau ├á " + signBlock.getLocation().toVector() + " supprim├®.");
         }

         // --- Fin du code s├®curis├® ---
      });

      // 3. Sauvegarde la liste des boutiques mise ├á jour.
      //    Cette m├®thode est d├®j├á asynchrone et donc non-bloquante.
      this.saveShops();
   }

   /**
    * Supprime toutes les boutiques associ├®es ├á un SIRET d'entreprise sp├®cifique.
    * ├Ç appeler lorsque l'entreprise elle-m├¬me est dissoute.
    *
    * @param siret Le SIRET de l'entreprise dont les boutiques doivent ├¬tre supprim├®es.
    */
   public void deleteAllShopsForEnterprise(String siret) {
      if (siret == null || siret.isEmpty()) {
         return;
      }
      // On cr├®e une copie de la liste pour ├®viter les modifications concurrentes
      List<Shop> shopsToDelete = new ArrayList<>(getShopsBySiret(siret));
      if (!shopsToDelete.isEmpty()) {
         plugin.getLogger().log(Level.INFO, "Suppression de " + shopsToDelete.size() + " boutique(s) pour l'entreprise SIRET: " + siret);
         shopsToDelete.forEach(this::deleteShop); // Appelle la m├®thode de suppression s├®curis├®e pour chaque boutique
      }
   }

   /**
    * Supprime toutes les boutiques situ├®es dans une ville sp├®cifique.
    * ├Ç appeler lorsque la ville est supprim├®e.
    *
    * @param townName Le nom de la ville.
    */
   public void deleteShopsInTown(String townName) {
      if (townName == null || townName.isEmpty()) {
         return;
      }
      List<Shop> shopsToDelete = this.shops.values().stream()
              .filter(shop -> townName.equalsIgnoreCase(shop.getTownName()))
              .collect(Collectors.toList());

      if (!shopsToDelete.isEmpty()) {
         plugin.getLogger().log(Level.INFO, "Suppression de " + shopsToDelete.size() + " boutique(s) suite ├á la suppression de la ville : " + townName);
         // On it├¿re sur une copie pour ├¬tre s├╗r
         new ArrayList<>(shopsToDelete).forEach(this::deleteShop);
      }
   }

   /**
    * Supprime toutes les boutiques situ├®es dans les coordonn├®es d'une parcelle donn├®e.
    * C'est la m├®thode ├á appeler depuis les listeners de parcelle (plot).
    *
    * @param coord  Les coordonn├®es de la parcelle.
    * @param reason La raison de la suppression (pour les logs).
    * @return Le nombre de boutiques supprim├®es.
    */
   // M├®thode supprim├®e - N'utilise plus Towny
   // La suppression de boutiques est g├®r├®e via TownEventListener

   /**
    * Supprime tous les shops d'une entreprise sur un terrain sp├®cifique
    * Utilis├® lors de d├®location ou perte d'entreprise
    *
    * @param siret SIRET de l'entreprise dont on supprime les shops
    * @param plot Terrain concern├®
    * @param notifyOwner Si true, notifie le g├®rant
    * @param reason Raison de la suppression
    * @return Nombre de shops supprim├®s
    */
   public int deleteShopsByCompanyOnPlot(String siret, Plot plot, boolean notifyOwner, String reason) {
      if (siret == null || plot == null) return 0;

      List<Shop> shopsToDelete = new ArrayList<>();

      // Trouver tous les shops de cette entreprise sur ce terrain
      for (Shop shop : shops.values()) {
         if (siret.equals(shop.getEntrepriseSiret())) {
            // V├®rifier si le shop est sur ce terrain
            Location shopLoc = shop.getLocation();
            if (shopLoc.getWorld().getName().equals(plot.getWorldName())) {
               int shopChunkX = shopLoc.getBlockX() >> 4;
               int shopChunkZ = shopLoc.getBlockZ() >> 4;

               if (shopChunkX == plot.getChunkX() && shopChunkZ == plot.getChunkZ()) {
                  shopsToDelete.add(shop);
               }
            }
         }
      }

      // Supprimer les shops trouv├®s
      for (Shop shop : shopsToDelete) {
         deleteShop(shop);
      }

      // Notifier le g├®rant si demand├®
      if (notifyOwner && !shopsToDelete.isEmpty()) {
         Player owner = Bukkit.getPlayer(shopsToDelete.get(0).getOwnerUUID());
         if (owner != null && owner.isOnline()) {
            owner.sendMessage("");
            owner.sendMessage(ChatColor.RED + "ÔÜá SUPPRESSION DE BOUTIQUES");
            owner.sendMessage(ChatColor.YELLOW + "Raison: " + reason);
            owner.sendMessage(ChatColor.GRAY + "Nombre de boutiques supprim├®es: " + ChatColor.WHITE + shopsToDelete.size());
            owner.sendMessage(ChatColor.GRAY + "Terrain: " + plot.getCoordinates());
            owner.sendMessage("");
         }
      }

      plugin.getLogger().info(String.format(
         "[ShopManager] Suppression de %d shop(s) de l'entreprise %s sur terrain %s (Raison: %s)",
         shopsToDelete.size(), siret, plot.getCoordinates(), reason
      ));

      return shopsToDelete.size();
   }

   /**
    * Supprime TOUS les shops d'une entreprise (toutes parcelles confondues)
    * Utilis├® lors de dissolution d'entreprise
    */
   public int deleteAllShopsByCompany(String siret, boolean notifyOwner, String reason) {
      if (siret == null) return 0;

      List<Shop> shopsToDelete = shops.values().stream()
         .filter(shop -> siret.equals(shop.getEntrepriseSiret()))
         .collect(Collectors.toList());

      for (Shop shop : shopsToDelete) {
         deleteShop(shop);
      }

      if (notifyOwner && !shopsToDelete.isEmpty()) {
         Player owner = Bukkit.getPlayer(shopsToDelete.get(0).getOwnerUUID());
         if (owner != null && owner.isOnline()) {
            owner.sendMessage("");
            owner.sendMessage(ChatColor.RED + "ÔÜá SUPPRESSION DE TOUTES VOS BOUTIQUES");
            owner.sendMessage(ChatColor.YELLOW + "Raison: " + reason);
            owner.sendMessage(ChatColor.GRAY + "Nombre total: " + ChatColor.WHITE + shopsToDelete.size());
            owner.sendMessage("");
         }
      }

      plugin.getLogger().info(String.format(
         "[ShopManager] Suppression de %d shop(s) de l'entreprise %s (Raison: %s)",
         shopsToDelete.size(), siret, reason
      ));

      return shopsToDelete.size();
   }


   public void changeShopItem(Shop shop, ItemStack newItem) {
      if (shop != null && newItem != null && newItem.getType() != Material.AIR) {
         shop.setItemTemplate(newItem);
         this.updateDisplayItem(shop);
         EntrepriseManagerLogic.Entreprise entreprise = this.entrepriseLogic.getEntrepriseBySiret(shop.getEntrepriseSiret());
         if (entreprise != null) {
            this.placeAndUpdateShopSign(shop, entreprise, null);
         }

         this.saveShops();
      }
   }

   public void changeShopPrice(Shop shop, double newPrice) {
      if (shop != null && !(newPrice <= 0.0D)) {
         shop.setPrice(newPrice);
         EntrepriseManagerLogic.Entreprise entreprise = this.entrepriseLogic.getEntrepriseBySiret(shop.getEntrepriseSiret());
         if (entreprise != null) {
            this.placeAndUpdateShopSign(shop, entreprise, null);
         }

         this.saveShops();
      }
   }

   // M├®thode supprim├®e - N'utilise plus Towny
   // La suppression de boutiques est g├®r├®e via TownEventListener

   public void changeShopQuantity(Shop shop, int newQuantity) {
      if (shop != null && newQuantity > 0) {
         shop.setQuantityPerSale(newQuantity);
         EntrepriseManagerLogic.Entreprise entreprise = this.entrepriseLogic.getEntrepriseBySiret(shop.getEntrepriseSiret());
         if (entreprise != null) {
            this.placeAndUpdateShopSign(shop, entreprise, null);
         }

         this.saveShops();
      }
   }

   public Shop getShopById(UUID shopId) {
      return (Shop) this.shops.get(shopId);
   }

   public Shop getShopBySignLocation(Location signLocation) {
      if (signLocation == null) {
         return null;
      } else {
         Iterator var2 = this.shops.values().iterator();

         while (var2.hasNext()) {
            Shop shop = (Shop) var2.next();
            Block chestBlock = shop.getLocation().getBlock();
            BlockFace[] var5 = new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
            int var6 = var5.length;

            for (int var7 = 0; var7 < var6; ++var7) {
               BlockFace face = var5[var7];
               Block potentialSignBlock = chestBlock.getRelative(face);
               if (potentialSignBlock.getLocation().equals(signLocation) && potentialSignBlock.getState() instanceof Sign) {
                  return shop;
               }
            }
         }

         return null;
      }
   }

   public Shop getShopByChestLocation(Location location) {
      if (location == null) {
         return null;
      } else {
         Block block = location.getBlock();
         if (block.getState() instanceof Chest) {
            Chest chest = (Chest) block.getState();
            InventoryHolder holder = chest.getInventory().getHolder();
            if (holder instanceof DoubleChest) {
               DoubleChest doubleChest = (DoubleChest) holder;
               Location leftLoc = ((Chest) doubleChest.getLeftSide()).getLocation();
               Location rightLoc = ((Chest) doubleChest.getRightSide()).getLocation();
               return this.shops.values().stream()
                       .filter(s -> s.getLocation().equals(leftLoc) || s.getLocation().equals(rightLoc))
                       .findFirst()
                       .orElse(null);
            }
         }

         return this.shops.values().stream()
                 .filter(shop -> shop.getLocation().equals(location))
                 .findFirst()
                 .orElse(null);
      }
   }

   public List<Shop> getShopsBySiret(String siret) {
      return (List) this.shops.values().stream().filter((shop) -> {
         return shop.getEntrepriseSiret().equals(siret);
      }).sorted(Comparator.comparing(Shop::getCreationDate)).collect(Collectors.toList());
   }

   // M├®thode supprim├®e - N'utilise plus Towny WorldCoord
   // Les boutiques sont maintenant g├®r├®es via notre syst├¿me de Plot

   private String formatMaterialName(Material material) {
      return material == null ? "Inconnu" : (String) Arrays.stream(material.name().split("_")).map((s) -> {
         String var10000 = s.substring(0, 1).toUpperCase();
         return var10000 + s.substring(1).toLowerCase();
      }).collect(Collectors.joining(" "));
   }

   /**
    * Place ou met ├á jour le panneau d'une boutique.
    * Tente de placer le panneau en face du joueur s'il est fourni.
    *
    * @param shop       La boutique concern├®e.
    * @param entreprise L'entreprise propri├®taire.
    * @param creator    Le joueur qui cr├®e la boutique (peut ├¬tre null lors d'une simple mise ├á jour).
    */

   /**
    * Place ou met ├á jour le panneau d'une boutique.
    * Tente de placer le panneau sur la face du coffre que le joueur regarde.
    *
    * @param shop       La boutique concern├®e.
    * @param entreprise L'entreprise propri├®taire.
    * @param creator    Le joueur qui cr├®e la boutique (peut ├¬tre null lors d'une simple mise ├á jour).
    */
// Dans /src/main/java/com/gravityyfh/entreprisemanager/Shop/ShopManager.java

   /**
    * Place ou met ├á jour le panneau d'une boutique.
    * Tente de placer le panneau sur la face du coffre que le joueur regarde.
    *
    * @param shop       La boutique concern├®e.
    * @param entreprise L'entreprise propri├®taire.
    * @param creator    Le joueur qui cr├®e la boutique (peut ├¬tre null lors d'une simple mise ├á jour).
    */
   public void placeAndUpdateShopSign(Shop shop, EntrepriseManagerLogic.Entreprise entreprise, Player creator) {
      Block chestBlock = shop.getLocation().getBlock();
      if (!(chestBlock.getState() instanceof Chest)) {
         return; // Le coffre n'existe plus, on ne peut rien faire.
      }

      Block signBlock = this.findSignAttachedToChest(chestBlock);

      // Si aucun panneau n'existe, il faut en cr├®er un au bon endroit.
      if (signBlock == null) {
         // La face du coffre sur laquelle le panneau sera attach├®.
         BlockFace attachFace = null;

         // Cas id├®al : le cr├®ateur est connu, on se base sur sa position.
         if (creator != null) {
            BlockFace desiredAttachFace = creator.getFacing().getOppositeFace();

            // --- MODIFICATION ICI ---
            // Remplacement de isCardinal() par une v├®rification manuelle compatible partout.
            if (desiredAttachFace == BlockFace.NORTH || desiredAttachFace == BlockFace.SOUTH || desiredAttachFace == BlockFace.EAST || desiredAttachFace == BlockFace.WEST) {
               // On v├®rifie que l'espace est libre.
               if (chestBlock.getRelative(desiredAttachFace).getType().isAir()) {
                  attachFace = desiredAttachFace;
               }
            }
         }

         // Fallback : Si la place id├®ale est prise ou si la m├®thode est appel├®e sans cr├®ateur.
         if (attachFace == null) {
            for (BlockFace cardinalFace : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
               if (chestBlock.getRelative(cardinalFace).getType().isAir()) {
                  attachFace = cardinalFace;
                  break;
               }
            }
         }

         // Si aucune face n'est libre, on abandonne.
         if (attachFace == null) {
            plugin.getLogger().warning("Impossible de placer un panneau pour la boutique " + shop.getShopId() + " : pas d'espace libre autour du coffre.");
            if (creator != null) {
               creator.sendMessage(ChatColor.RED + "Impossible de placer le panneau, tous les c├┤t├®s du coffre sont bloqu├®s !");
            }
            return;
         }

         // On place le nouveau panneau ├á l'endroit d├®termin├®.
         signBlock = chestBlock.getRelative(attachFace);
         signBlock.setType(Material.OAK_WALL_SIGN, false);
         BlockData data = signBlock.getBlockData();
         if (data instanceof WallSign) {
            WallSign signData = (WallSign) data;
            // L'orientation du texte du panneau doit correspondre ├á la face sur laquelle il est attach├®.
            signData.setFacing(attachFace);
            signBlock.setBlockData(signData, true);
         }
      }

      // Mise ├á jour du texte du panneau (qu'il soit nouveau ou existant).
      Sign signState = (Sign) signBlock.getState();
      signState.setLine(0, ChatColor.DARK_BLUE + "[Boutique]");
      String nomEntreprise = ChatColor.stripColor(entreprise.getNom());
      signState.setLine(1, nomEntreprise.substring(0, Math.min(nomEntreprise.length(), 15)));
      String itemName = this.formatMaterialName(shop.getItemTemplate().getType());
      String quantityString = shop.getQuantityPerSale() + "x ";
      int remainingChars = 15 - quantityString.length();
      if (remainingChars < 0) remainingChars = 0;
      signState.setLine(2, quantityString + itemName.substring(0, Math.min(itemName.length(), remainingChars)));
      signState.setLine(3, ChatColor.GREEN + String.format("%.2f EUR", shop.getPrice()));
      signState.update(true);
   }


   private Block getTargetChest(Player player) {
      RayTraceResult rayTrace = player.rayTraceBlocks(5.0D);
      return rayTrace != null && rayTrace.getHitBlock() != null && rayTrace.getHitBlock().getState() instanceof Chest ? rayTrace.getHitBlock() : null;
   }

   private Block findSignAttachedToChest(Block chestBlock) {
      BlockFace[] faces = new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

      for (BlockFace face : faces) {
         Block relative = chestBlock.getRelative(face);
         if (relative.getBlockData() instanceof WallSign) {
            WallSign signData = (WallSign) relative.getBlockData();
            if (signData.getFacing() == face) {
               return relative;
            }
         }
      }

      return null;
   }

   private void createDisplayItem(Shop shop) {
      Location itemLocation = this.getFloatingItemLocation(shop.getLocation());
      if (itemLocation != null && itemLocation.getWorld() != null) {
         itemLocation.getChunk().load();
         Runnable spawnTask = () -> {
            ItemStack displayStack = shop.getItemTemplate().clone();
            displayStack.setAmount(1);
            Item item = itemLocation.getWorld().dropItem(itemLocation, displayStack);
            item.setPickupDelay(Integer.MAX_VALUE);
            item.setInvulnerable(true);
            item.setUnlimitedLifetime(true);
            item.setGravity(false);
            item.setVelocity(item.getVelocity().zero());
            item.setTicksLived(1);
            shop.setDisplayItemID(item.getUniqueId());
            this.saveShops();
         };
         if (Bukkit.isPrimaryThread()) {
            spawnTask.run();
         } else {
            Bukkit.getScheduler().runTask(this.plugin, spawnTask);
         }
      }
   }

   private void updateDisplayItem(Shop shop) {
      this.deleteDisplayItem(shop);
      this.createDisplayItem(shop);
   }

   private void deleteDisplayItem(Shop shop) {
      if (shop.getDisplayItemID() == null) {
         this.plugin.getLogger().log(Level.FINE, "Aucun item flottant ├á supprimer pour la boutique " + shop.getShopId());
         return;
      }

      this.plugin.getLogger().log(Level.FINE, "Suppression de l'item flottant de la boutique " + shop.getShopId());
      Location itemLocation = this.getFloatingItemLocation(shop.getLocation());
      Runnable removeTask = () -> {
         Entity entity = Bukkit.getEntity(shop.getDisplayItemID());
         if (entity instanceof Item) {
            entity.remove();
         }

         if (itemLocation != null && itemLocation.getWorld() != null) {
            for (Entity nearby : itemLocation.getWorld().getNearbyEntities(itemLocation, 0.3D, 0.3D, 0.3D)) {
               if (nearby instanceof Item && ((Item) nearby).getPickupDelay() == Integer.MAX_VALUE) {
                  nearby.remove();
                  break;
               }
            }
         }
      };

      if (itemLocation != null && itemLocation.getWorld() != null) {
         itemLocation.getChunk().load();
      }

      if (Bukkit.isPrimaryThread()) {
         removeTask.run();
      } else {
         Bukkit.getScheduler().runTask(this.plugin, removeTask);
      }

      shop.setDisplayItemID((UUID) null);

   }

   private Location getFloatingItemLocation(Location chestLocation) {
      Block block = chestLocation.getBlock();
      if (!(block.getState() instanceof Chest)) {
         // Si le coffre n'existe plus, on estime simplement la position bas├®e sur l'ancienne localisation
         return chestLocation.clone().add(0.5D, 1.2D, 0.5D);
      }

      Chest chest = (Chest) block.getState();
      InventoryHolder holder = chest.getInventory().getHolder();
      if (holder instanceof DoubleChest) {
         DoubleChest doubleChest = (DoubleChest) holder;
         Location left = ((Chest) doubleChest.getLeftSide()).getLocation();
         Location right = ((Chest) doubleChest.getRightSide()).getLocation();
         Location center = new Location(left.getWorld(), (left.getX() + right.getX()) / 2.0D, left.getY(), (left.getZ() + right.getZ()) / 2.0D);
         return center.add(0.5D, 1.2D, 0.5D);
      } else {
         return chestLocation.clone().add(0.5D, 1.2D, 0.5D);
      }
   }

   public void saveShops() {
      FileConfiguration config = new YamlConfiguration();
      List<Map<String, Object>> serializedShops = new ArrayList();
      this.shops.values().forEach((shop) -> {
         serializedShops.add(shop.serialize());
      });
      config.set("shops", serializedShops);
      Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
         // FIX CRITIQUE P1.2: Sauvegardes atomiques avec lock
         saveLock.lock();
         try {
            // Étape 1: Écrire dans un fichier temporaire
            File tempFile = new File(this.shopsFile.getParentFile(), "shops.yml.tmp");
            config.save(tempFile);

            // Étape 2: Renommage atomique (remplace l'ancien fichier)
            Files.move(tempFile.toPath(), this.shopsFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            this.plugin.getLogger().info("Boutiques sauvegardées dans shops.yml (atomic write)");
         } catch (IOException var3) {
            this.plugin.getLogger().log(Level.SEVERE, "Impossible de sauvegarder shops.yml", var3);
         } finally {
            saveLock.unlock();
         }

      });
   }

   public void loadShops() {
      if (this.shopsFile.exists()) {
         FileConfiguration config = YamlConfiguration.loadConfiguration(this.shopsFile);
         List<Map<?, ?>> shopList = config.getMapList("shops");
         this.shops.clear();
         Iterator var3 = shopList.iterator();

         while (var3.hasNext()) {
            Map shopMap = (Map) var3.next();

            try {
               Map<String, Object> castedMap = new HashMap();
               shopMap.forEach((key, value) -> {
                  if (key instanceof String) {
                     castedMap.put((String) key, value);
                  }

               });
               Shop shop = Shop.deserialize(castedMap);
               if (shop != null) {
                  this.shops.put(shop.getShopId(), shop);
                  if (shop.getDisplayItemID() != null && Bukkit.getEntity(shop.getDisplayItemID()) == null) {
                     this.plugin.getLogger().warning("Item flottant pour la boutique " + shop.getShopId() + " introuvable. Il sera recr├®├® si n├®cessaire.");
                  }
               }
            } catch (Exception var7) {
               this.plugin.getLogger().log(Level.SEVERE, "Erreur lors du chargement d'une boutique depuis shops.yml.", var7);
            }
         }

         this.plugin.getLogger().info(this.shops.size() + " boutique(s) charg├®e(s) depuis shops.yml.");
      }
   }

   // M├®thode supprim├®e - N'utilise plus Towny TownBlock
   // Les boutiques sont maintenant g├®r├®es via notre syst├¿me de Plot
   // La suppression de boutiques lors de changements de parcelles est g├®r├®e par TownEventListener

   public boolean removeTargetedDisplayItem(Player player) {
      // 1. On cherche une entit├® de type 'Item', et non plus 'Display'.
      RayTraceResult entityTrace = player.getWorld().rayTraceEntities(
              player.getEyeLocation(),
              player.getEyeLocation().getDirection(),
              10.0D, // La distance du rayon
              entity -> entity instanceof Item // On cible les entit├®s Item
      );

      if (entityTrace != null && entityTrace.getHitEntity() != null) {
         Item item = (Item) entityTrace.getHitEntity();

         // 2. Pour la s├®curit├®, on v├®rifie que l'item a les bonnes propri├®t├®s
         //    (celles d├®finies dans votre ancienne m├®thode de cr├®ation).
         //    Cela ├®vite de supprimer n'importe quel objet jet├® par terre.
         boolean isShopDisplayItem = !item.hasGravity() && item.getPickupDelay() > 30000;

         if (isShopDisplayItem) {
            item.remove(); // On supprime l'entit├®
            return true;   // On retourne un succ├¿s
         }
      }

      // Si on n'a rien trouv├®, ou si ce n'├®tait pas un item de boutique, on retourne un ├®chec.
      return false;
   }

   public void cleanupOrphanedShopDisplay(Location signLocation) {
      if (signLocation.getBlock().getBlockData() instanceof WallSign) {
         WallSign signData = (WallSign) signLocation.getBlock().getBlockData();
         BlockFace attachedFace = signData.getFacing().getOppositeFace();
         Block chestBlock = signLocation.getBlock().getRelative(attachedFace);
         if (chestBlock.getState() instanceof Chest) {
            Location expectedItemLocation = this.getFloatingItemLocation(chestBlock.getLocation());
            if (expectedItemLocation != null && expectedItemLocation.getWorld() != null) {
               Bukkit.getScheduler().runTask(this.plugin, () -> {
                  Collection<Entity> nearbyEntities = expectedItemLocation.getWorld().getNearbyEntities(expectedItemLocation, 0.2D, 0.2D, 0.2D);
                  Iterator var3 = nearbyEntities.iterator();

                  while (var3.hasNext()) {
                     Entity entity = (Entity) var3.next();
                     if (entity instanceof Item) {
                        Item item = (Item) entity;
                        if (item.getPickupDelay() == Integer.MAX_VALUE) {
                           item.remove();
                           this.plugin.getLogger().log(Level.INFO, "Nettoyage d'un item flottant orphelin ├á la position: " + expectedItemLocation.toVector());
                           break;
                        }
                     }
                  }

               });
            }
         }
      }
   }
}
