package com.gravityyfh.entreprisemanager.Shop;

import com.gravityyfh.entreprisemanager.EntrepriseManager;
import com.gravityyfh.entreprisemanager.EntrepriseManagerLogic;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.TownBlockType;
import com.palmergames.bukkit.towny.object.WorldCoord;
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
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public class ShopManager {
   private final EntrepriseManager plugin;
   private final EntrepriseManagerLogic entrepriseLogic;
   private final Map<UUID, Shop> shops = new ConcurrentHashMap();
   private final File shopsFile;

   public ShopManager(EntrepriseManager plugin) {
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
               if (!EntrepriseManager.getEconomy().has(buyer, price)) {
                  var10001 = ChatColor.RED;
                  buyer.sendMessage(var10001 + "Vous n'avez pas assez d'argent. Il vous faut " + String.format("%,.2f€", price) + ".");
               } else if (buyer.getInventory().firstEmpty() == -1) {
                  buyer.sendMessage(ChatColor.RED + "Votre inventaire est plein.");
               } else {
                  EconomyResponse buyerResponse = EntrepriseManager.getEconomy().withdrawPlayer(buyer, price);
                  if (!buyerResponse.transactionSuccess()) {
                     buyer.sendMessage(ChatColor.RED + "Une erreur est survenue lors du paiement : " + buyerResponse.errorMessage);
                  } else {
                     entreprise.setSolde(entreprise.getSolde() + price);
                     entreprise.addTransaction(new EntrepriseManagerLogic.Transaction(EntrepriseManagerLogic.TransactionType.REVENUE, price, "Vente boutique: " + itemToSell.getAmount() + "x " + itemToSell.getType().name(), buyer.getName()));
                     chest.getInventory().removeItem(new ItemStack[]{itemToSell.clone()});
                     buyer.getInventory().addItem(new ItemStack[]{itemToSell.clone()});
                     var10001 = ChatColor.GREEN;
                     buyer.sendMessage(var10001 + "Vous avez acheté " + itemToSell.getAmount() + "x " + this.formatMaterialName(itemToSell.getType()) + " pour " + String.format("%,.2f€", price) + ".");
                     Player owner = Bukkit.getPlayer(shop.getOwnerUUID());
                     if (owner != null && owner.isOnline()) {
                        var10001 = ChatColor.GREEN;
                        owner.sendMessage(var10001 + "[Boutique] Vente de " + itemToSell.getAmount() + "x " + this.formatMaterialName(itemToSell.getType()) + " à " + buyer.getName() + " pour " + String.format("%,.2f€", price) + ".");
                        var10001 = ChatColor.AQUA;
                        owner.sendMessage(var10001 + "Nouveau solde de l'entreprise '" + entreprise.getNom() + "' : " + String.format("%,.2f€", entreprise.getSolde()));
                     }

                     this.entrepriseLogic.saveEntreprises();
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
                  try {
                     TownBlock townBlock = TownyAPI.getInstance().getTownBlock(chestLocation);
                     Resident resident = TownyAPI.getInstance().getResident(player);
                     if (townBlock == null || !townBlock.hasResident() || !townBlock.getResident().equals(resident)) {
                        player.sendMessage(ChatColor.RED + "Vous devez être sur une parcelle Towny qui vous appartient.");
                        return;
                     }

                     if (townBlock.getType() != TownBlockType.COMMERCIAL) {
                        player.sendMessage(ChatColor.RED + "La parcelle doit être de type SHOP pour placer une boutique.");
                        return;
                     }
                  } catch (Exception var8) {
                     player.sendMessage(ChatColor.RED + "Erreur de communication avec Towny.");
                     this.plugin.getLogger().log(Level.WARNING, "Erreur Towny lors de la création de boutique", var8);
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
         Shop newShop = new Shop(entreprise.getSiret(), gerant.getUniqueId(), chestLocation, itemToSell, quantity, price);
         this.createDisplayItem(newShop);
         this.placeAndUpdateShopSign(newShop, entreprise);
         this.shops.put(newShop.getShopId(), newShop);
         this.saveShops();
         ChatColor var10001 = ChatColor.GREEN;
         gerant.sendMessage(var10001 + "Nouvelle boutique pour '" + entreprise.getNom() + "' créée avec succès !");
      } else {
         gerant.sendMessage(ChatColor.RED + "Le prix et la quantité doivent être positifs.");
      }
   }

   public void deleteShop(Shop shop) {
      if (shop != null) {
         if (this.shops.remove(shop.getShopId()) != null) {
            this.deleteDisplayItem(shop);
            this.removeShopSign(shop);
            Logger var10000 = this.plugin.getLogger();
            UUID var10001 = shop.getShopId();
            var10000.info("Boutique " + var10001 + " supprimée pour l'entreprise SIRET: " + shop.getEntrepriseSiret());
            this.saveShops();
         }

      }
   }

   public void deleteAllShopsForEnterprise(String siret) {
      new ArrayList<>(this.getShopsBySiret(siret)).forEach(this::deleteShop);
   }

   public void updateAllShopSignsForEnterprise(String siret) {
      List<Shop> shopsToUpdate = this.getShopsBySiret(siret);
      if (!shopsToUpdate.isEmpty()) {
         EntrepriseManagerLogic.Entreprise entreprise = this.entrepriseLogic.getEntrepriseBySiret(siret);
         if (entreprise != null) {
            shopsToUpdate.forEach((shop) -> {
               this.placeAndUpdateShopSign(shop, entreprise);
            });
         }
      }

   }

   public void changeShopItem(Shop shop, ItemStack newItem) {
      if (shop != null && newItem != null && newItem.getType() != Material.AIR) {
         shop.setItemTemplate(newItem);
         this.updateDisplayItem(shop);
         EntrepriseManagerLogic.Entreprise entreprise = this.entrepriseLogic.getEntrepriseBySiret(shop.getEntrepriseSiret());
         if (entreprise != null) {
            this.placeAndUpdateShopSign(shop, entreprise);
         }

         this.saveShops();
      }
   }

   public void changeShopPrice(Shop shop, double newPrice) {
      if (shop != null && !(newPrice <= 0.0D)) {
         shop.setPrice(newPrice);
         EntrepriseManagerLogic.Entreprise entreprise = this.entrepriseLogic.getEntrepriseBySiret(shop.getEntrepriseSiret());
         if (entreprise != null) {
            this.placeAndUpdateShopSign(shop, entreprise);
         }

         this.saveShops();
      }
   }

   public void changeShopQuantity(Shop shop, int newQuantity) {
      if (shop != null && newQuantity > 0) {
         shop.setQuantityPerSale(newQuantity);
         EntrepriseManagerLogic.Entreprise entreprise = this.entrepriseLogic.getEntrepriseBySiret(shop.getEntrepriseSiret());
         if (entreprise != null) {
            this.placeAndUpdateShopSign(shop, entreprise);
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

   public List<Shop> getShopsByPlot(WorldCoord worldCoord) {
      if (worldCoord == null) {
         return Collections.emptyList();
      } else {
         return this.shops.values().stream().filter((shop) -> {
            Location loc = shop.getLocation();
            if (loc == null || loc.getWorld() == null) {
               return false;
            }

            return Objects.equals(loc.getWorld().getName(), worldCoord.getWorldName()) &&
                    loc.getChunk().getX() == worldCoord.getX() &&
                    loc.getChunk().getZ() == worldCoord.getZ();
         }).collect(Collectors.toList());
      }
   }

   private String formatMaterialName(Material material) {
      return material == null ? "Inconnu" : (String) Arrays.stream(material.name().split("_")).map((s) -> {
         String var10000 = s.substring(0, 1).toUpperCase();
         return var10000 + s.substring(1).toLowerCase();
      }).collect(Collectors.joining(" "));
   }

   public void placeAndUpdateShopSign(Shop shop, EntrepriseManagerLogic.Entreprise entreprise) {
      Block chestBlock = shop.getLocation().getBlock();
      if (chestBlock.getState() instanceof Chest) {
         Block signBlock = this.findSignAttachedToChest(chestBlock);
         if (signBlock == null) {
            BlockFace facing = this.getEmptyFaceForSign(chestBlock);
            if (facing == null) {
               return;
            }

            signBlock = chestBlock.getRelative(facing);
            signBlock.setType(Material.OAK_WALL_SIGN, false);
            BlockData data = signBlock.getBlockData();
            if (data instanceof WallSign) {
               WallSign signData = (WallSign) data;
               signData.setFacing(facing);
               signBlock.setBlockData(signData, true);
            }
         }

         Sign signState = (Sign) signBlock.getState();
         signState.setLine(0, ChatColor.DARK_BLUE + "[Boutique]");
         String nomEntreprise = ChatColor.stripColor(entreprise.getNom());
         signState.setLine(1, nomEntreprise.substring(0, Math.min(nomEntreprise.length(), 15)));
         String itemName = this.formatMaterialName(shop.getItemTemplate().getType());
         String quantityString = shop.getQuantityPerSale() + "x ";
         int remainingChars = 15 - quantityString.length();
         signState.setLine(2, quantityString + itemName.substring(0, Math.min(itemName.length(), remainingChars)));
         ChatColor var10002 = ChatColor.GREEN;
         signState.setLine(3, var10002 + String.format("%.2f EUR", shop.getPrice()));
         signState.update(true);
      }
   }

   private void removeShopSign(Shop shop) {
      Block chestBlock = shop.getLocation().getBlock();
      if (chestBlock.getState() instanceof Chest) {
         Block signBlock = this.findSignAttachedToChest(chestBlock);
         if (signBlock != null) {
            signBlock.getChunk().load();
            Bukkit.getScheduler().runTask(this.plugin, () -> {
               signBlock.setType(Material.AIR, false);
            });
         }

      }
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

   private BlockFace getEmptyFaceForSign(Block block) {
      BlockFace[] var2 = new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
      int var3 = var2.length;

      for (int var4 = 0; var4 < var3; ++var4) {
         BlockFace face = var2[var4];
         if (block.getRelative(face).getType().isAir()) {
            return face;
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
      if (shop.getDisplayItemID() != null) {
         Location itemLocation = this.getFloatingItemLocation(shop.getLocation());
         if (itemLocation != null && itemLocation.getWorld() != null) {
            itemLocation.getChunk().load();
            Runnable removeTask = () -> {
               Entity entity = Bukkit.getEntity(shop.getDisplayItemID());
               if (entity instanceof Item) {
                  entity.remove();
               } else {
                  for (Entity nearby : itemLocation.getWorld().getNearbyEntities(itemLocation, 0.3D, 0.3D, 0.3D)) {
                     if (nearby instanceof Item && ((Item) nearby).getPickupDelay() == Integer.MAX_VALUE) {
                        nearby.remove();
                        break;
                     }
                  }
               }
            };
            if (Bukkit.isPrimaryThread()) {
               removeTask.run();
            } else {
               Bukkit.getScheduler().runTask(this.plugin, removeTask);
            }
         }
         shop.setDisplayItemID((UUID) null);
      }

   }

   private Location getFloatingItemLocation(Location chestLocation) {
      Block block = chestLocation.getBlock();
      if (!(block.getState() instanceof Chest)) {
         return null;
      } else {
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

   /**
    * Retourne une collection non modifiable de toutes les boutiques chargées.
    * L'utilisation de "Collections.unmodifiableCollection" est une sécurité pour
    * empêcher d'autres parties du code de modifier la liste des boutiques par accident.
    *
    * @return Une collection de tous les objets Shop.
    */
   public Collection<Shop> getAllShops() {
      return Collections.unmodifiableCollection(shops.values());
   }

   /**
    * Récupère la liste de toutes les boutiques situées sur une parcelle Towny spécifique.
    *
    * @param townBlock La parcelle Towny à vérifier.
    * @return Une liste d'objets Shop trouvés sur cette parcelle. Peut être vide.
    */
   public List<Shop> getShopsOnPlot(TownBlock townBlock) {
      if (townBlock == null) {
         return Collections.emptyList(); // Retourne une liste vide si la parcelle est nulle.
      }

      // On récupère les coordonnées de la parcelle
      com.palmergames.bukkit.towny.object.WorldCoord plotCoord = townBlock.getWorldCoord();

      // On filtre toutes les boutiques du serveur
      return shops.values().stream()
              .filter(shop -> {
                 // Pour chaque boutique, on récupère ses coordonnées
                 com.palmergames.bukkit.towny.object.WorldCoord shopCoord = com.palmergames.bukkit.towny.object.WorldCoord.parseWorldCoord(shop.getLocation());
                 // On compare les coordonnées de la boutique avec celles de la parcelle
                 return plotCoord.equals(shopCoord);
              })
              .collect(Collectors.toList()); // On retourne le résultat sous forme de liste.
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

   public boolean removeTargetedDisplayItem(Player player) {
      Location eye = player.getEyeLocation();
      Vector direction = eye.getDirection().normalize();

      for (double d = 0.0D; d <= 5.0D; d += 0.5D) {
         Location check = eye.clone().add(direction.clone().multiply(d));
         Collection<Entity> entities = check.getWorld().getNearbyEntities(check, 0.5D, 0.5D, 0.5D);
         Iterator<Entity> iterator = entities.iterator();

         while (iterator.hasNext()) {
            Entity ent = iterator.next();
            if (ent instanceof Item) {
               Item item = (Item) ent;
               if (item.getPickupDelay() == Integer.MAX_VALUE) {
                  item.remove();
                  return true;
               }
            }
         }
      }

      return false;
   }

}
