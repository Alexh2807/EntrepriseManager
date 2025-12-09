package com.gravityyfh.roleplaycity.shop.model;

import com.gravityyfh.roleplaycity.shop.ShopStatus;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;

/**
 * Modèle de données d'une boutique
 * Représente une boutique avec tous ses composants et ses métadonnées
 */
public class Shop {
    // === IDENTIFIANTS UNIQUES ===
    private final UUID shopId;
    private final String entrepriseSiret;
    private final String entrepriseName;
    private final UUID ownerUUID;
    private final String ownerName;

    // === EMPLACEMENT (Immutable après création) ===
    private final Location chestLocation;
    private final Location signLocation;
    private final Location hologramLocation;

    // === DONNÉES DE VENTE (Mutable) ===
    private ItemStack itemTemplate;
    private int quantityPerSale;
    private double pricePerSale;

    // === MÉTADONNÉES ===
    private final LocalDateTime creationDate;
    private LocalDateTime lastStockCheck;
    private LocalDateTime lastPurchase;
    private int totalSales;        // Nombre de transactions (lots vendus)
    private int totalItemsSold;    // Nombre total d'items vendus
    private double totalRevenue;

    // === STATUT ===
    private ShopStatus status;
    private int cachedStock;

    // === COMPOSANTS (IDs pour traçabilité) ===
    private UUID displayItemEntityId;
    private List<UUID> hologramTextEntityIds;

    // === TOP ACHETEURS ===
    private final Map<String, Integer> topBuyers;

    /**
     * Constructeur pour créer un nouveau shop
     */
    public Shop(String entrepriseName, String entrepriseSiret, UUID ownerUUID, String ownerName,
                Location chestLocation, Location signLocation, ItemStack itemToSell,
                int quantityPerSale, double pricePerSale) {
        this.shopId = UUID.randomUUID();
        this.entrepriseName = Objects.requireNonNull(entrepriseName, "entrepriseName cannot be null");
        this.entrepriseSiret = Objects.requireNonNull(entrepriseSiret, "entrepriseSiret cannot be null");
        this.ownerUUID = Objects.requireNonNull(ownerUUID, "ownerUUID cannot be null");
        this.ownerName = Objects.requireNonNull(ownerName, "ownerName cannot be null");
        this.chestLocation = Objects.requireNonNull(chestLocation, "chestLocation cannot be null");
        this.signLocation = Objects.requireNonNull(signLocation, "signLocation cannot be null");

        // Hologramme au-dessus du coffre (centré sur X et Z, élevé sur Y)
        this.hologramLocation = chestLocation.clone().add(0.5, 1.8, 0.5);

        this.itemTemplate = itemToSell.clone();
        this.itemTemplate.setAmount(1); // Template = 1 item
        this.quantityPerSale = quantityPerSale;
        this.pricePerSale = pricePerSale;

        this.creationDate = LocalDateTime.now();
        this.lastStockCheck = LocalDateTime.now();
        this.status = ShopStatus.ACTIVE;
        this.cachedStock = 0;

        this.totalSales = 0;
        this.totalItemsSold = 0;
        this.totalRevenue = 0.0;
        this.hologramTextEntityIds = new ArrayList<>();
        this.topBuyers = new HashMap<>();
    }

    /**
     * Constructeur privé pour la désérialisation
     */
    private Shop(UUID shopId, String entrepriseName, String entrepriseSiret, UUID ownerUUID, String ownerName,
                 Location chestLocation, Location signLocation, Location hologramLocation,
                 ItemStack itemTemplate, int quantityPerSale, double pricePerSale,
                 LocalDateTime creationDate, LocalDateTime lastStockCheck, LocalDateTime lastPurchase,
                 int totalSales, int totalItemsSold, double totalRevenue, ShopStatus status,
                 UUID displayItemEntityId, List<UUID> hologramTextEntityIds,
                 Map<String, Integer> topBuyers) {
        this.shopId = shopId;
        this.entrepriseName = entrepriseName;
        this.entrepriseSiret = entrepriseSiret;
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
        this.chestLocation = chestLocation;
        this.signLocation = signLocation;
        this.hologramLocation = hologramLocation;
        this.itemTemplate = itemTemplate;
        this.quantityPerSale = quantityPerSale;
        this.pricePerSale = pricePerSale;
        this.creationDate = creationDate;
        this.lastStockCheck = lastStockCheck;
        this.lastPurchase = lastPurchase;
        this.totalSales = totalSales;
        this.totalItemsSold = totalItemsSold;
        this.totalRevenue = totalRevenue;
        this.status = status;
        this.displayItemEntityId = displayItemEntityId;
        this.hologramTextEntityIds = hologramTextEntityIds != null ? hologramTextEntityIds : new ArrayList<>();
        this.topBuyers = topBuyers != null ? topBuyers : new HashMap<>();
    }

    // ===== GETTERS =====

    public UUID getShopId() {
        return shopId;
    }

    public String getEntrepriseSiret() {
        return entrepriseSiret;
    }

    public String getEntrepriseName() {
        return entrepriseName;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public Location getChestLocation() {
        return chestLocation.clone();
    }

    public Location getSignLocation() {
        return signLocation.clone();
    }

    public Location getHologramLocation() {
        return hologramLocation.clone();
    }

    public ItemStack getItemTemplate() {
        return itemTemplate.clone();
    }

    public int getQuantityPerSale() {
        return quantityPerSale;
    }

    public double getPricePerSale() {
        return pricePerSale;
    }

    public LocalDateTime getCreationDate() {
        return creationDate;
    }

    public LocalDateTime getLastStockCheck() {
        return lastStockCheck;
    }

    public LocalDateTime getLastPurchase() {
        return lastPurchase;
    }

    public int getTotalSales() {
        return totalSales;
    }

    public int getTotalItemsSold() {
        return totalItemsSold;
    }

    public double getTotalRevenue() {
        return totalRevenue;
    }

    public ShopStatus getStatus() {
        return status;
    }

    public UUID getDisplayItemEntityId() {
        return displayItemEntityId;
    }

    public List<UUID> getHologramTextEntityIds() {
        return new ArrayList<>(hologramTextEntityIds);
    }

    public Map<String, Integer> getTopBuyers() {
        return new HashMap<>(topBuyers);
    }

    public int getCachedStock() {
        return cachedStock;
    }

    // ===== SETTERS =====

    public void setItemTemplate(ItemStack itemTemplate) {
        this.itemTemplate = itemTemplate.clone();
        this.itemTemplate.setAmount(1);
    }

    public void setCachedStock(int cachedStock) {
        this.cachedStock = cachedStock;
    }

    public void setQuantityPerSale(int quantityPerSale) {
        this.quantityPerSale = quantityPerSale;
    }

    public void setPricePerSale(double pricePerSale) {
        this.pricePerSale = pricePerSale;
    }

    public void setLastStockCheck(LocalDateTime lastStockCheck) {
        this.lastStockCheck = lastStockCheck;
    }

    public void setLastPurchase(LocalDateTime lastPurchase) {
        this.lastPurchase = lastPurchase;
    }

    public void setStatus(ShopStatus status) {
        this.status = status;
    }

    public void setDisplayItemEntityId(UUID displayItemEntityId) {
        this.displayItemEntityId = displayItemEntityId;
    }

    public void setHologramTextEntityIds(List<UUID> hologramTextEntityIds) {
        this.hologramTextEntityIds = hologramTextEntityIds != null ? new ArrayList<>(hologramTextEntityIds) : new ArrayList<>();
    }

    // ===== MÉTHODES MÉTIER =====

    /**
     * Incrémente le nombre de ventes
     */
    public void incrementSales() {
        this.totalSales++;
        this.totalItemsSold += this.quantityPerSale; // Ajoute le nombre d'items vendus
    }

    /**
     * Ajoute un montant au revenu total
     */
    public void addRevenue(double amount) {
        this.totalRevenue += amount;
    }

    /**
     * Enregistre un acheteur
     */
    public void recordBuyer(String buyerName) {
        topBuyers.put(buyerName, topBuyers.getOrDefault(buyerName, 0) + 1);
    }

    // ===== SÉRIALISATION =====

    /**
     * Sérialise le shop en Map pour sauvegarde YAML
     */
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();

        // Identifiants
        map.put("shopId", shopId.toString());
        map.put("entrepriseSiret", entrepriseSiret);
        map.put("entrepriseName", entrepriseName);
        map.put("ownerUUID", ownerUUID.toString());
        map.put("ownerName", ownerName);

        // Emplacements
        serializeLocation(map, "chest", chestLocation);
        serializeLocation(map, "sign", signLocation);
        serializeLocation(map, "hologram", hologramLocation);

        // Données de vente
        map.put("itemTemplate", itemStackToBase64(itemTemplate));
        map.put("quantityPerSale", quantityPerSale);
        map.put("pricePerSale", pricePerSale);

        // Métadonnées
        map.put("creationDate", creationDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        if (lastStockCheck != null) {
            map.put("lastStockCheck", lastStockCheck.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        if (lastPurchase != null) {
            map.put("lastPurchase", lastPurchase.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        map.put("totalSales", totalSales);
        map.put("totalItemsSold", totalItemsSold);
        map.put("totalRevenue", totalRevenue);

        // Statut
        map.put("status", status.name());
        map.put("cachedStock", cachedStock);

        // Composants
        if (displayItemEntityId != null) {
            map.put("displayItemEntityId", displayItemEntityId.toString());
        }
        if (!hologramTextEntityIds.isEmpty()) {
            List<String> ids = new ArrayList<>();
            for (UUID id : hologramTextEntityIds) {
                ids.add(id.toString());
            }
            map.put("hologramTextEntityIds", ids);
        }

        // Top acheteurs
        if (!topBuyers.isEmpty()) {
            map.put("topBuyers", new HashMap<>(topBuyers));
        }

        return map;
    }

    /**
     * Désérialise un shop depuis une Map YAML
     */
    public static Shop deserialize(Map<String, Object> map) {
        try {
            // Identifiants
            UUID id = UUID.fromString((String) map.get("shopId"));
            String siret = (String) map.get("entrepriseSiret");
            String entrepriseName = (String) map.getOrDefault("entrepriseName", "?");
            UUID ownerId = UUID.fromString((String) map.get("ownerUUID"));
            String ownerName = (String) map.getOrDefault("ownerName", "?");

            // Emplacements
            Location chestLoc = deserializeLocation(map, "chest");
            Location signLoc = deserializeLocation(map, "sign");
            Location hologramLoc = deserializeLocation(map, "hologram");

            // Si hologram location n'existe pas, la calculer depuis le panneau
            if (hologramLoc == null && signLoc != null) {
                hologramLoc = signLoc.clone().add(0.5, 1.5, 0.5);
            }

            // Données de vente
            ItemStack item = itemStackFromBase64((String) map.get("itemTemplate"));
            int quantity = ((Number) map.getOrDefault("quantityPerSale", 1)).intValue();
            double price = ((Number) map.get("pricePerSale")).doubleValue();

            // Métadonnées
            LocalDateTime creationDate = LocalDateTime.parse((String) map.get("creationDate"), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            LocalDateTime lastStockCheck = map.containsKey("lastStockCheck")
                ? LocalDateTime.parse((String) map.get("lastStockCheck"), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : null;
            LocalDateTime lastPurchase = map.containsKey("lastPurchase")
                ? LocalDateTime.parse((String) map.get("lastPurchase"), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : null;
            int totalSales = ((Number) map.getOrDefault("totalSales", 0)).intValue();
            int totalItemsSold = ((Number) map.getOrDefault("totalItemsSold", 0)).intValue();
            double totalRevenue = ((Number) map.getOrDefault("totalRevenue", 0.0)).doubleValue();

            // Statut
            ShopStatus status = ShopStatus.valueOf((String) map.getOrDefault("status", "ACTIVE"));
            int cachedStock = ((Number) map.getOrDefault("cachedStock", 0)).intValue();

            // Composants
            UUID displayItemId = map.containsKey("displayItemEntityId")
                ? UUID.fromString((String) map.get("displayItemEntityId"))
                : null;

            List<UUID> hologramIds = new ArrayList<>();
            if (map.containsKey("hologramTextEntityIds")) {
                @SuppressWarnings("unchecked")
                List<String> ids = (List<String>) map.get("hologramTextEntityIds");
                for (String idStr : ids) {
                    hologramIds.add(UUID.fromString(idStr));
                }
            }

            // Top acheteurs
            Map<String, Integer> topBuyers = new HashMap<>();
            if (map.containsKey("topBuyers")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> buyers = (Map<String, Object>) map.get("topBuyers");
                for (Map.Entry<String, Object> entry : buyers.entrySet()) {
                    topBuyers.put(entry.getKey(), ((Number) entry.getValue()).intValue());
                }
            }

            if (siret != null && chestLoc != null && signLoc != null && item != null) {
                Shop shop = new Shop(id, entrepriseName, siret, ownerId, ownerName,
                    chestLoc, signLoc, hologramLoc, item, quantity, price,
                    creationDate, lastStockCheck, lastPurchase,
                    totalSales, totalItemsSold, totalRevenue, status,
                    displayItemId, hologramIds, topBuyers);
                shop.setCachedStock(cachedStock);
                return shop;
            }

            return null;
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "Erreur lors de la désérialisation d'une boutique.", e);
            return null;
        }
    }

    // ===== MÉTHODES PRIVÉES =====

    private static void serializeLocation(Map<String, Object> map, String prefix, Location location) {
        if (location != null && location.getWorld() != null) {
            map.put(prefix + ".world", location.getWorld().getName());
            map.put(prefix + ".x", location.getX());
            map.put(prefix + ".y", location.getY());
            map.put(prefix + ".z", location.getZ());
            map.put(prefix + ".yaw", location.getYaw());
            map.put(prefix + ".pitch", location.getPitch());
        }
    }

    private static Location deserializeLocation(Map<String, Object> map, String prefix) {
        if (map.containsKey(prefix + ".world")) {
            World world = Bukkit.getWorld((String) map.get(prefix + ".world"));
            if (world != null) {
                double x = ((Number) map.get(prefix + ".x")).doubleValue();
                double y = ((Number) map.get(prefix + ".y")).doubleValue();
                double z = ((Number) map.get(prefix + ".z")).doubleValue();
                float yaw = map.containsKey(prefix + ".yaw") ? ((Number) map.get(prefix + ".yaw")).floatValue() : 0f;
                float pitch = map.containsKey(prefix + ".pitch") ? ((Number) map.get(prefix + ".pitch")).floatValue() : 0f;
                return new Location(world, x, y, z, yaw, pitch);
            }
        }
        return null;
    }

    private static String itemStackToBase64(ItemStack item) {
        if (item == null) {
            return null;
        }
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeObject(item);
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de sauvegarder l'itemstack.", e);
        }
    }

    private static ItemStack itemStackFromBase64(String data) {
        if (data == null) {
            return null;
        }
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            return (ItemStack) dataInput.readObject();
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de décoder l'itemstack.", e);
        }
    }

    @Override
    public String toString() {
        return "Shop{" +
                "id=" + shopId +
                ", entreprise=" + entrepriseName +
                ", siret=" + entrepriseSiret +
                ", status=" + status +
                ", ventes=" + totalSales +
                ", revenu=" + String.format("%.2f", totalRevenue) +
                '}';
    }
}
