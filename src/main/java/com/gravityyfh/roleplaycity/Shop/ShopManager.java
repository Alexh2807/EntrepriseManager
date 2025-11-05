package com.gravityyfh.roleplaycity.Shop;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.PlotType;
import com.gravityyfh.roleplaycity.town.manager.ClaimManager;
import java.io.File;
import java.io.IOException;
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

   public ShopManager(RoleplayCity plugin) {
      this.plugin = plugin;
      this.entrepriseLogic = plugin.getEntrepriseManagerLogic();
      this.shopsFile = new File(plugin.getDataFolder(), "shops.yml");
   }

   public void purchaseItem(Player buyer, Shop shop) {
      EntrepriseManagerLogic.Entreprise entreprise = this.entrepriseLogic.getEntrepriseBySiret(shop.getEntrepriseSiret());
      if (entreprise == null) {
         buyer.sendMessage(ChatColor.RED + "L'entreprise propriétaire de cette boutique n'existe plus.");
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
                  owner.sendMessage(var10001 + "[Boutique] " + buyer.getName() + " a tenté d'acheter '" + this.formatMaterialName(itemToSell.getType()) + "' mais une de vos boutiques est en rupture de stock !");
               }

            } else {
               double price = shop.getPrice();
               if (!RoleplayCity.getEconomy().has(buyer, price)) {
                  var10001 = ChatColor.RED;
                  buyer.sendMessage(var10001 + "Vous n'avez pas assez d'argent. Il vous faut " + String.format("%,.2f€", price) + ".");
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

                     chest.getInventory().removeItem(new ItemStack[]{itemToSell.clone()});
                     buyer.getInventory().addItem(new ItemStack[]{itemToSell.clone()});
                     var10001 = ChatColor.GREEN;
                     buyer.sendMessage(var10001 + "Vous avez acheté " + itemToSell.getAmount() + "x " + this.formatMaterialName(itemToSell.getType()) + " pour " + String.format("%,.2f€", price) + ".");
                     Player owner = Bukkit.getPlayer(shop.getOwnerUUID());
                     if (owner != null && owner.isOnline()) {
                        // The message to the owner is simplified as the balance is no longer updated instantly.
                        var10001 = ChatColor.GREEN;
                        owner.sendMessage(var10001 + "[Boutique] Vente de " + itemToSell.getAmount() + "x " + this.formatMaterialName(itemToSell.getType()) + " à " + buyer.getName() + " pour " + String.format("%,.2f€", price) + ".");
                        owner.sendMessage(ChatColor.AQUA + "Les revenus de la vente seront traités dans le prochain rapport horaire de l'entreprise '" + entreprise.getNom() + "'.");
                     }
                  }
               }
            }
         }
      }
   }

   public void startShopCreationProcess(Player player, EntrepriseManagerLogic.Entreprise entreprise) {
      if (!UUID.fromString(entreprise.getGerantUUID()).equals(player.getUniqueId())) {
         player.sendMessage(ChatColor.RED + "Seul le gérant peut créer des boutiques pour cette entreprise.");
      } else {
         int maxShops = this.plugin.getConfig().getInt("shop.max-shops-per-enterprise", 10);
         if (this.getShopsBySiret(entreprise.getSiret()).size() >= maxShops) {
            player.sendMessage(ChatColor.RED + "Cette entreprise a déjà atteint le nombre maximum de boutiques (" + maxShops + ").");
         } else {
            Block targetBlock = this.getTargetChest(player);
            if (targetBlock == null) {
               player.sendMessage(ChatColor.RED + "Vous devez regarder un coffre pour créer une boutique.");
            } else {
               Location chestLocation = targetBlock.getLocation();
               if (this.getShopByChestLocation(chestLocation) != null) {
                  player.sendMessage(ChatColor.RED + "Il y a déjà une boutique sur ce coffre.");
               } else {
                  // Vérifier avec notre système de ville
                  ClaimManager claimManager = plugin.getClaimManager();
                  if (claimManager == null) {
                     player.sendMessage(ChatColor.RED + "Système de ville non disponible.");
                     return;
                  }

                  Plot plot = claimManager.getPlotAt(chestLocation);
                  if (plot == null) {
                     player.sendMessage(ChatColor.RED + "Vous devez être sur une parcelle de ville pour créer une boutique.");
                     return;
                  }

                  // Vérifier que c'est une parcelle professionnelle
                  if (plot.getType() != PlotType.PROFESSIONNEL) {
                     player.sendMessage(ChatColor.RED + "La parcelle doit être de type PROFESSIONNEL pour placer une boutique.");
                     return;
                  }

                  // NOUVEAU : Déterminer quel SIRET est autorisé sur ce terrain
                  String authorizedSiret = null;
                  boolean isRented = (plot.getRenterUuid() != null);

                  if (isRented) {
                     // Terrain loué → seul le locataire peut créer des shops (avec SON entreprise)
                     if (!plot.isRentedBy(player.getUniqueId())) {
                        player.sendMessage(ChatColor.RED + "Ce terrain est loué par quelqu'un d'autre.");
                        player.sendMessage(ChatColor.YELLOW + "Seul le locataire actuel peut créer des boutiques ici.");
                        return;
                     }
                     // Récupérer le SIRET de l'entreprise du locataire
                     authorizedSiret = plot.getRenterCompanySiret();

                     if (authorizedSiret == null) {
                        player.sendMessage(ChatColor.RED + "Erreur: Aucune entreprise associée à votre location.");
                        return;
                     }
                  } else {
                     // Terrain PAS loué → seul le propriétaire peut créer des shops
                     if (!plot.isOwnedBy(player.getUniqueId())) {
                        player.sendMessage(ChatColor.RED + "Vous devez être propriétaire de cette parcelle.");
                        return;
                     }
                     // Récupérer le SIRET de l'entreprise propriétaire
                     authorizedSiret = plot.getCompanySiret();

                     if (authorizedSiret == null) {
                        player.sendMessage(ChatColor.RED + "Erreur: Aucune entreprise associée à ce terrain.");
                        return;
                     }
                  }

                  // NOUVEAU : Vérifier que l'entreprise du shop correspond au terrain
                  if (!entreprise.getSiret().equals(authorizedSiret)) {
                     player.sendMessage(ChatColor.RED + "✗ Cette entreprise n'est pas autorisée sur ce terrain !");
                     player.sendMessage(ChatColor.YELLOW + "Seule l'entreprise liée au terrain peut créer des boutiques ici.");

                     // Afficher quelle entreprise est autorisée
                     EntrepriseManagerLogic.Entreprise authorizedCompany = this.entrepriseLogic.getEntrepriseBySiret(authorizedSiret);
                     if (authorizedCompany != null) {
                        player.sendMessage(ChatColor.GRAY + "Entreprise autorisée: " + ChatColor.WHITE + authorizedCompany.getNom());
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

         // Utiliser notre système de ville
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
         gerant.sendMessage(var10001 + "Nouvelle boutique pour '" + entreprise.getNom() + "' créée avec succès !");
      } else {
         gerant.sendMessage(ChatColor.RED + "Le prix et la quantité doivent être positifs.");
      }
   }

// Dans /src/main/java/com/gravityyfh/entreprisemanager/Shop/ShopManager.java

   /**
    * Supprime complètement une boutique de manière sécurisée (thread-safe).
    * <p>
    * Cette méthode retire immédiatement la boutique du registre de données,
    * puis planifie la suppression des éléments visuels (panneau, item flottant)
    * sur le thread principal du serveur pour éviter les erreurs asynchrones.
    *
    * @param shop La boutique à supprimer.
    */
   public void deleteShop(Shop shop) {
      if (shop == null) {
         return;
      }

      // 1. Retire la boutique du registre interne. C'est une action sûre depuis n'importe quel thread.
      if (this.shops.remove(shop.getShopId()) == null) {
         // La boutique n'était pas dans la liste, elle a peut-être déjà été supprimée.
         this.plugin.getLogger().log(Level.WARNING, "[Suppression] Tentative de suppression d'une boutique (" + shop.getShopId() + ") qui n'était pas dans le registre.");
         return;
      }

      this.plugin.getLogger().log(Level.INFO, "[Suppression] Boutique " + shop.getShopId() + " retirée du registre. Planification du nettoyage visuel sur le thread principal.");

      // 2. Planifie toutes les interactions avec le monde du jeu pour qu'elles s'exécutent sur le thread principal.
      //    Ceci empêche l'erreur "Asynchronous chunk load!".
      Bukkit.getScheduler().runTask(this.plugin, () -> {
         // --- Début du code exécuté en toute sécurité sur le thread principal ---

         // A. Logique de suppression de l'item flottant (Display Item)
         if (shop.getDisplayItemID() != null) {
            Entity entity = Bukkit.getEntity(shop.getDisplayItemID());
            if (entity instanceof Item) {
               entity.remove();
               this.plugin.getLogger().log(Level.FINE, "[Suppression-Sync] Item flottant " + shop.getDisplayItemID() + " supprimé.");
            }
         }

         // Sécurité supplémentaire : nettoyer les items flottants orphelins à proximité au cas où l'UUID serait perdu.
         Location floatingItemLocation = getFloatingItemLocation(shop.getLocation());
         if (floatingItemLocation != null && floatingItemLocation.isWorldLoaded()) {
            for (Entity nearby : floatingItemLocation.getWorld().getNearbyEntities(floatingItemLocation, 0.3D, 0.3D, 0.3D)) {
               if (nearby instanceof Item && ((Item) nearby).getPickupDelay() == Integer.MAX_VALUE) {
                  nearby.remove();
                  this.plugin.getLogger().log(Level.FINE, "[Suppression-Sync] Item flottant orphelin à proximité de " + floatingItemLocation.toVector() + " supprimé.");
                  break;
               }
            }
         }

         // B. Logique de suppression du panneau (Sign)
         Block chestBlock = shop.getLocation().getBlock();
         Block signBlock = findSignAttachedToChest(chestBlock);
         if (signBlock != null) {
            signBlock.setType(Material.AIR, false); // Modifie le monde
            this.plugin.getLogger().log(Level.FINE, "[Suppression-Sync] Panneau à " + signBlock.getLocation().toVector() + " supprimé.");
         }

         // --- Fin du code sécurisé ---
      });

      // 3. Sauvegarde la liste des boutiques mise à jour.
      //    Cette méthode est déjà asynchrone et donc non-bloquante.
      this.saveShops();
   }

   /**
    * Supprime toutes les boutiques associées à un SIRET d'entreprise spécifique.
    * À appeler lorsque l'entreprise elle-même est dissoute.
    *
    * @param siret Le SIRET de l'entreprise dont les boutiques doivent être supprimées.
    */
   public void deleteAllShopsForEnterprise(String siret) {
      if (siret == null || siret.isEmpty()) {
         return;
      }
      // On crée une copie de la liste pour éviter les modifications concurrentes
      List<Shop> shopsToDelete = new ArrayList<>(getShopsBySiret(siret));
      if (!shopsToDelete.isEmpty()) {
         plugin.getLogger().log(Level.INFO, "Suppression de " + shopsToDelete.size() + " boutique(s) pour l'entreprise SIRET: " + siret);
         shopsToDelete.forEach(this::deleteShop); // Appelle la méthode de suppression sécurisée pour chaque boutique
      }
   }

   /**
    * Supprime toutes les boutiques situées dans une ville spécifique.
    * À appeler lorsque la ville est supprimée.
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
         plugin.getLogger().log(Level.INFO, "Suppression de " + shopsToDelete.size() + " boutique(s) suite à la suppression de la ville : " + townName);
         // On itère sur une copie pour être sûr
         new ArrayList<>(shopsToDelete).forEach(this::deleteShop);
      }
   }

   /**
    * Supprime toutes les boutiques situées dans les coordonnées d'une parcelle donnée.
    * C'est la méthode à appeler depuis les listeners de parcelle (plot).
    *
    * @param coord  Les coordonnées de la parcelle.
    * @param reason La raison de la suppression (pour les logs).
    * @return Le nombre de boutiques supprimées.
    */
   // Méthode supprimée - N'utilise plus Towny
   // La suppression de boutiques est gérée via TownEventListener

   /**
    * Supprime tous les shops d'une entreprise sur un terrain spécifique
    * Utilisé lors de délocation ou perte d'entreprise
    *
    * @param siret SIRET de l'entreprise dont on supprime les shops
    * @param plot Terrain concerné
    * @param notifyOwner Si true, notifie le gérant
    * @param reason Raison de la suppression
    * @return Nombre de shops supprimés
    */
   public int deleteShopsByCompanyOnPlot(String siret, Plot plot, boolean notifyOwner, String reason) {
      if (siret == null || plot == null) return 0;

      List<Shop> shopsToDelete = new ArrayList<>();

      // Trouver tous les shops de cette entreprise sur ce terrain
      for (Shop shop : shops.values()) {
         if (siret.equals(shop.getEntrepriseSiret())) {
            // Vérifier si le shop est sur ce terrain
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

      // Supprimer les shops trouvés
      for (Shop shop : shopsToDelete) {
         deleteShop(shop);
      }

      // Notifier le gérant si demandé
      if (notifyOwner && !shopsToDelete.isEmpty()) {
         Player owner = Bukkit.getPlayer(shopsToDelete.get(0).getOwnerUUID());
         if (owner != null && owner.isOnline()) {
            owner.sendMessage("");
            owner.sendMessage(ChatColor.RED + "⚠ SUPPRESSION DE BOUTIQUES");
            owner.sendMessage(ChatColor.YELLOW + "Raison: " + reason);
            owner.sendMessage(ChatColor.GRAY + "Nombre de boutiques supprimées: " + ChatColor.WHITE + shopsToDelete.size());
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
    * Utilisé lors de dissolution d'entreprise
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
            owner.sendMessage(ChatColor.RED + "⚠ SUPPRESSION DE TOUTES VOS BOUTIQUES");
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

   // Méthode supprimée - N'utilise plus Towny
   // La suppression de boutiques est gérée via TownEventListener

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

   // Méthode supprimée - N'utilise plus Towny WorldCoord
   // Les boutiques sont maintenant gérées via notre système de Plot

   private String formatMaterialName(Material material) {
      return material == null ? "Inconnu" : (String) Arrays.stream(material.name().split("_")).map((s) -> {
         String var10000 = s.substring(0, 1).toUpperCase();
         return var10000 + s.substring(1).toLowerCase();
      }).collect(Collectors.joining(" "));
   }

   /**
    * Place ou met à jour le panneau d'une boutique.
    * Tente de placer le panneau en face du joueur s'il est fourni.
    *
    * @param shop       La boutique concernée.
    * @param entreprise L'entreprise propriétaire.
    * @param creator    Le joueur qui crée la boutique (peut être null lors d'une simple mise à jour).
    */

   /**
    * Place ou met à jour le panneau d'une boutique.
    * Tente de placer le panneau sur la face du coffre que le joueur regarde.
    *
    * @param shop       La boutique concernée.
    * @param entreprise L'entreprise propriétaire.
    * @param creator    Le joueur qui crée la boutique (peut être null lors d'une simple mise à jour).
    */
// Dans /src/main/java/com/gravityyfh/entreprisemanager/Shop/ShopManager.java

   /**
    * Place ou met à jour le panneau d'une boutique.
    * Tente de placer le panneau sur la face du coffre que le joueur regarde.
    *
    * @param shop       La boutique concernée.
    * @param entreprise L'entreprise propriétaire.
    * @param creator    Le joueur qui crée la boutique (peut être null lors d'une simple mise à jour).
    */
   public void placeAndUpdateShopSign(Shop shop, EntrepriseManagerLogic.Entreprise entreprise, Player creator) {
      Block chestBlock = shop.getLocation().getBlock();
      if (!(chestBlock.getState() instanceof Chest)) {
         return; // Le coffre n'existe plus, on ne peut rien faire.
      }

      Block signBlock = this.findSignAttachedToChest(chestBlock);

      // Si aucun panneau n'existe, il faut en créer un au bon endroit.
      if (signBlock == null) {
         // La face du coffre sur laquelle le panneau sera attaché.
         BlockFace attachFace = null;

         // Cas idéal : le créateur est connu, on se base sur sa position.
         if (creator != null) {
            BlockFace desiredAttachFace = creator.getFacing().getOppositeFace();

            // --- MODIFICATION ICI ---
            // Remplacement de isCardinal() par une vérification manuelle compatible partout.
            if (desiredAttachFace == BlockFace.NORTH || desiredAttachFace == BlockFace.SOUTH || desiredAttachFace == BlockFace.EAST || desiredAttachFace == BlockFace.WEST) {
               // On vérifie que l'espace est libre.
               if (chestBlock.getRelative(desiredAttachFace).getType().isAir()) {
                  attachFace = desiredAttachFace;
               }
            }
         }

         // Fallback : Si la place idéale est prise ou si la méthode est appelée sans créateur.
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
               creator.sendMessage(ChatColor.RED + "Impossible de placer le panneau, tous les côtés du coffre sont bloqués !");
            }
            return;
         }

         // On place le nouveau panneau à l'endroit déterminé.
         signBlock = chestBlock.getRelative(attachFace);
         signBlock.setType(Material.OAK_WALL_SIGN, false);
         BlockData data = signBlock.getBlockData();
         if (data instanceof WallSign) {
            WallSign signData = (WallSign) data;
            // L'orientation du texte du panneau doit correspondre à la face sur laquelle il est attaché.
            signData.setFacing(attachFace);
            signBlock.setBlockData(signData, true);
         }
      }

      // Mise à jour du texte du panneau (qu'il soit nouveau ou existant).
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
         this.plugin.getLogger().log(Level.FINE, "Aucun item flottant à supprimer pour la boutique " + shop.getShopId());
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
         // Si le coffre n'existe plus, on estime simplement la position basée sur l'ancienne localisation
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
         try {
            config.save(this.shopsFile);
         } catch (IOException var3) {
            this.plugin.getLogger().log(Level.SEVERE, "Impossible de sauvegarder shops.yml", var3);
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
                     this.plugin.getLogger().warning("Item flottant pour la boutique " + shop.getShopId() + " introuvable. Il sera recréé si nécessaire.");
                  }
               }
            } catch (Exception var7) {
               this.plugin.getLogger().log(Level.SEVERE, "Erreur lors du chargement d'une boutique depuis shops.yml.", var7);
            }
         }

         this.plugin.getLogger().info(this.shops.size() + " boutique(s) chargée(s) depuis shops.yml.");
      }
   }

   // Méthode supprimée - N'utilise plus Towny TownBlock
   // Les boutiques sont maintenant gérées via notre système de Plot
   // La suppression de boutiques lors de changements de parcelles est gérée par TownEventListener

   public boolean removeTargetedDisplayItem(Player player) {
      // 1. On cherche une entité de type 'Item', et non plus 'Display'.
      RayTraceResult entityTrace = player.getWorld().rayTraceEntities(
              player.getEyeLocation(),
              player.getEyeLocation().getDirection(),
              10.0D, // La distance du rayon
              entity -> entity instanceof Item // On cible les entités Item
      );

      if (entityTrace != null && entityTrace.getHitEntity() != null) {
         Item item = (Item) entityTrace.getHitEntity();

         // 2. Pour la sécurité, on vérifie que l'item a les bonnes propriétés
         //    (celles définies dans votre ancienne méthode de création).
         //    Cela évite de supprimer n'importe quel objet jeté par terre.
         boolean isShopDisplayItem = !item.hasGravity() && item.getPickupDelay() > 30000;

         if (isShopDisplayItem) {
            item.remove(); // On supprime l'entité
            return true;   // On retourne un succès
         }
      }

      // Si on n'a rien trouvé, ou si ce n'était pas un item de boutique, on retourne un échec.
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
                           this.plugin.getLogger().log(Level.INFO, "Nettoyage d'un item flottant orphelin à la position: " + expectedItemLocation.toVector());
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