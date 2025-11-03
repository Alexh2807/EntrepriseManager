package com.gravityyfh.roleplaycity.Shop;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

public class Shop {
   private final UUID shopId;
   private final String entrepriseSiret;
   private final String entrepriseName;
   private final UUID ownerUUID;
   private final String ownerName;
   private final Location location;
   private final String townName;
   private final String townBlockWorld;
   private final int townBlockX;
   private final int townBlockZ;
   private final LocalDateTime creationDate;
   private ItemStack itemTemplate;
   private int quantityPerSale;
   private double price;
   private UUID displayItemID;

   public Shop(String entrepriseName, String entrepriseSiret, UUID ownerUUID, String ownerName,
               Location location, String townName, String townBlockWorld, int townBlockX, int townBlockZ,
               ItemStack itemForSale, int quantityPerSale, double price) {
      this.shopId = UUID.randomUUID();
      this.entrepriseName = Objects.requireNonNull(entrepriseName, "entrepriseName cannot be null");
      this.entrepriseSiret = Objects.requireNonNull(entrepriseSiret, "entrepriseSiret cannot be null");
      this.ownerUUID = Objects.requireNonNull(ownerUUID, "ownerUUID cannot be null");
      this.ownerName = Objects.requireNonNull(ownerName, "ownerName cannot be null");
      this.location = Objects.requireNonNull(location, "location cannot be null");
      this.townName = townName;
      this.townBlockWorld = townBlockWorld;
      this.townBlockX = townBlockX;
      this.townBlockZ = townBlockZ;
      this.itemTemplate = itemForSale.clone();
      this.itemTemplate.setAmount(1);
      this.quantityPerSale = quantityPerSale;
      this.price = price;
      this.creationDate = LocalDateTime.now();
   }

   private Shop(UUID shopId, String entrepriseName, String entrepriseSiret, UUID ownerUUID, String ownerName,
                Location location, String townName, String townBlockWorld, int townBlockX, int townBlockZ,
                LocalDateTime creationDate, ItemStack itemTemplate, int quantityPerSale, double price, UUID displayItemID) {
      this.shopId = shopId;
      this.entrepriseName = entrepriseName;
      this.entrepriseSiret = entrepriseSiret;
      this.ownerUUID = ownerUUID;
      this.ownerName = ownerName;
      this.location = location;
      this.townName = townName;
      this.townBlockWorld = townBlockWorld;
      this.townBlockX = townBlockX;
      this.townBlockZ = townBlockZ;
      this.creationDate = creationDate;
      this.itemTemplate = itemTemplate;
      this.quantityPerSale = quantityPerSale;
      this.price = price;
      this.displayItemID = displayItemID;
   }

   public UUID getShopId() {
      return this.shopId;
   }

   public String getEntrepriseSiret() {
      return this.entrepriseSiret;
   }

   public String getEntrepriseName() {
      return this.entrepriseName;
   }

   public String getOwnerName() {
      return this.ownerName;
   }

   public String getTownName() {
      return this.townName;
   }

   public String getTownBlockWorld() {
      return this.townBlockWorld;
   }

   public int getTownBlockX() {
      return this.townBlockX;
   }

   public int getTownBlockZ() {
      return this.townBlockZ;
   }

   public UUID getOwnerUUID() {
      return this.ownerUUID;
   }

   public Location getLocation() {
      return this.location;
   }

   public ItemStack getItemTemplate() {
      return this.itemTemplate;
   }

   public int getQuantityPerSale() {
      return this.quantityPerSale;
   }

   public double getPrice() {
      return this.price;
   }

   public LocalDateTime getCreationDate() {
      return this.creationDate;
   }

   public UUID getDisplayItemID() {
      return this.displayItemID;
   }

   public void setItemTemplate(ItemStack itemTemplate) {
      this.itemTemplate = itemTemplate.clone();
      this.itemTemplate.setAmount(1);
   }

   public void setQuantityPerSale(int quantityPerSale) {
      this.quantityPerSale = quantityPerSale;
   }

   public void setPrice(double price) {
      this.price = price;
   }

   public void setDisplayItemID(UUID displayItemID) {
      this.displayItemID = displayItemID;
   }

   public ItemStack getSaleBundle() {
      ItemStack bundle = this.itemTemplate.clone();
      bundle.setAmount(this.quantityPerSale);
      return bundle;
   }

   public Map<String, Object> serialize() {
      Map<String, Object> map = new HashMap();
      map.put("shopId", this.shopId.toString());
      map.put("entrepriseSiret", this.entrepriseSiret);
      map.put("entrepriseName", this.entrepriseName);
      map.put("ownerUUID", this.ownerUUID.toString());
      map.put("ownerName", this.ownerName);
      map.put("creationDate", this.creationDate.toString());
      map.put("price", this.price);
      map.put("quantityPerSale", this.quantityPerSale);
      if (this.townName != null) {
         map.put("townName", this.townName);
      }
      if (this.townBlockWorld != null) {
         map.put("townBlock.world", this.townBlockWorld);
         map.put("townBlock.x", this.townBlockX);
         map.put("townBlock.z", this.townBlockZ);
      }
      if (this.location != null && this.location.getWorld() != null) {
         map.put("location.world", this.location.getWorld().getName());
         map.put("location.x", this.location.getX());
         map.put("location.y", this.location.getY());
         map.put("location.z", this.location.getZ());
      }

      if (this.displayItemID != null) {
         map.put("displayItemID", this.displayItemID.toString());
      }

      map.put("itemTemplate", itemStackToBase64(this.itemTemplate));
      return map;
   }

   public static Shop deserialize(Map<String, Object> map) {
      try {
         UUID id = UUID.fromString((String)map.get("shopId"));
         String siret = (String)map.get("entrepriseSiret");
         String entrepriseName = (String)map.getOrDefault("entrepriseName", "?");
         UUID ownerId = UUID.fromString((String)map.get("ownerUUID"));
         String ownerName = (String)map.getOrDefault("ownerName", "?");
         LocalDateTime date = LocalDateTime.parse((String)map.get("creationDate"));
         double price = (Double)map.get("price");
         int quantity = ((Number)map.getOrDefault("quantityPerSale", 1)).intValue();
         String townName = (String)map.get("townName");
         String tbWorld = (String)map.get("townBlock.world");
         int tbX = map.containsKey("townBlock.x") ? ((Number)map.get("townBlock.x")).intValue() : 0;
         int tbZ = map.containsKey("townBlock.z") ? ((Number)map.get("townBlock.z")).intValue() : 0;
         UUID displayItemID = map.containsKey("displayItemID") ? UUID.fromString((String)map.get("displayItemID")) : null;
         Location loc = null;
         if (map.containsKey("location.world")) {
            World world = Bukkit.getWorld((String)map.get("location.world"));
            if (world != null) {
               loc = new Location(world, (Double)map.get("location.x"), (Double)map.get("location.y"), (Double)map.get("location.z"));
            }
         }

         ItemStack item = itemStackFromBase64((String)map.get("itemTemplate"));
         return siret != null && loc != null && item != null ?
                 new Shop(id, entrepriseName, siret, ownerId, ownerName,
                         loc, townName, tbWorld, tbX, tbZ, date, item, quantity, price, displayItemID) : null;
      } catch (Exception var11) {
         Bukkit.getLogger().log(Level.SEVERE, "Erreur critique lors de la désérialisation d'une boutique.", var11);
         return null;
      }
   }

   public static String itemStackToBase64(ItemStack item) {
      if (item == null) {
         return null;
      } else {
         try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            String var3;
            try {
               BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

               try {
                  dataOutput.writeObject(item);
                  var3 = Base64Coder.encodeLines(outputStream.toByteArray());
               } catch (Throwable var7) {
                  try {
                     dataOutput.close();
                  } catch (Throwable var6) {
                     var7.addSuppressed(var6);
                  }

                  throw var7;
               }

               dataOutput.close();
            } catch (Throwable var8) {
               try {
                  outputStream.close();
               } catch (Throwable var5) {
                  var8.addSuppressed(var5);
               }

               throw var8;
            }

            outputStream.close();
            return var3;
         } catch (Exception var9) {
            throw new IllegalStateException("Impossible de sauvegarder l'itemstack.", var9);
         }
      }
   }

   public static ItemStack itemStackFromBase64(String data) {
      if (data == null) {
         return null;
      } else {
         try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));

            ItemStack var3;
            try {
               BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

               try {
                  var3 = (ItemStack)dataInput.readObject();
               } catch (Throwable var7) {
                  try {
                     dataInput.close();
                  } catch (Throwable var6) {
                     var7.addSuppressed(var6);
                  }

                  throw var7;
               }

               dataInput.close();
            } catch (Throwable var8) {
               try {
                  inputStream.close();
               } catch (Throwable var5) {
                  var8.addSuppressed(var5);
               }

               throw var8;
            }

            inputStream.close();
            return var3;
         } catch (IOException | ClassNotFoundException var9) {
            throw new IllegalStateException("Impossible de décoder l'itemstack.", var9);
         }
      }
   }
}
