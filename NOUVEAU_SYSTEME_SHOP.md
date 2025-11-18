# 🏪 Système de Boutiques - Architecture Moderne et Fiable

## 📋 Vue d'ensemble

Un système de boutiques complet, robuste et performant pour RoleplayCity qui garantit la **persistance des données**, la **cohérence** et une **expérience utilisateur fluide**.

---

## 🎯 Principes fondamentaux

### 1. Source de Vérité Unique
**LE COFFRE EST LA RÉFÉRENCE ABSOLUE**
- Toutes les informations de stock proviennent du coffre
- Si le coffre est détruit/déplacé → Shop supprimé automatiquement
- Si le panneau/hologramme est perdu → Recréation automatique OU suppression du shop

### 2. Système de Validation Continue
- Vérification de l'intégrité à chaque interaction
- Auto-nettoyage des shops orphelins
- Logs détaillés de tous les changements

### 3. Architecture Modulaire
```
ShopManager (Gestionnaire central)
    ↓
ShopValidator (Validation d'intégrité)
    ↓
ShopPersistence (Sauvegarde/Chargement)
    ↓
ShopComponents (Coffre/Panneau/Hologramme)
    ↓
ShopInteraction (Achat/Gestion)
```

---

## 🏗️ Architecture Détaillée

### 📦 1. Classe `Shop` (Modèle de données)

```java
public class Shop {
    // === IDENTIFIANTS UNIQUES ===
    private final UUID shopId;                    // ID unique du shop
    private final String entrepriseSiret;         // SIRET de l'entreprise propriétaire
    private final String entrepriseName;          // Nom de l'entreprise
    private final UUID ownerUUID;                 // UUID du créateur
    private final String ownerName;               // Nom du créateur

    // === EMPLACEMENT (Immutable après création) ===
    private final Location chestLocation;         // Position du coffre (référence)
    private final Location signLocation;          // Position du panneau
    private final Location hologramLocation;      // Position de l'hologramme (au-dessus du panneau)

    // === DONNÉES DE VENTE (Mutable) ===
    private ItemStack itemTemplate;               // Item vendu (amount=1 pour template)
    private int quantityPerSale;                  // Quantité vendue par transaction
    private double pricePerSale;                  // Prix par transaction

    // === MÉTADONNÉES ===
    private final LocalDateTime creationDate;     // Date de création
    private LocalDateTime lastStockCheck;         // Dernière vérification du stock
    private LocalDateTime lastPurchase;           // Dernier achat effectué
    private int totalSales;                       // Nombre total de ventes
    private double totalRevenue;                  // Revenu total généré

    // === STATUT ===
    private ShopStatus status;                    // ACTIVE, OUT_OF_STOCK, BROKEN, DISABLED

    // === COMPOSANTS (IDs pour traçabilité) ===
    private UUID displayItemEntityId;             // ID de l'ArmorStand hologramme
    private List<UUID> hologramTextEntityIds;     // IDs des lignes de texte
}

enum ShopStatus {
    ACTIVE,           // Fonctionnel avec stock
    OUT_OF_STOCK,     // Fonctionnel mais sans stock
    BROKEN,           // Composant manquant (coffre/panneau)
    DISABLED          // Désactivé manuellement par le propriétaire
}
```

---

### 🔧 2. Classe `ShopManager` (Gestionnaire central)

**Responsabilités:**
- Création/Suppression de shops
- Gestion du cycle de vie
- Coordination entre les composants
- Point d'entrée unique pour toutes les opérations

**Méthodes principales:**

```java
// === CRÉATION ===
public CompletableFuture<ShopCreationResult> createShop(
    Player creator,
    Entreprise entreprise,
    Location chestLocation,
    Location signLocation,
    ItemStack itemToSell,
    int quantity,
    double price
)

// === RÉCUPÉRATION ===
public Optional<Shop> getShopById(UUID shopId)
public Optional<Shop> getShopByChestLocation(Location location)
public Optional<Shop> getShopBySignLocation(Location location)
public List<Shop> getShopsBySiret(String siret)
public List<Shop> getShopsByOwner(UUID ownerUUID)
public List<Shop> getActiveShops()

// === MODIFICATION ===
public ShopUpdateResult updateItemForSale(Shop shop, ItemStack newItem)
public ShopUpdateResult updatePrice(Shop shop, double newPrice)
public ShopUpdateResult updateQuantity(Shop shop, int newQuantity)
public ShopUpdateResult toggleShopStatus(Shop shop, Player admin)

// === SUPPRESSION ===
public ShopDeletionResult deleteShop(Shop shop, String reason, Player initiator)
public int deleteShopsBySiret(String siret, String reason)
public int deleteShopsByOwner(UUID ownerUUID, String reason)
public int deleteOrphanedShops() // Nettoyage automatique

// === INTERACTION ===
public PurchaseResult processPurchase(Player buyer, Shop shop)

// === VALIDATION ===
public ValidationResult validateShopIntegrity(Shop shop)
public void startIntegrityCheckTask() // Tâche périodique (toutes les 5 minutes)
```

---

### ✅ 3. Classe `ShopValidator` (Validation d'intégrité)

**Responsabilités:**
- Vérifier que tous les composants existent
- Vérifier la cohérence des données
- Détecter les anomalies

**Validations effectuées:**

```java
public class ValidationResult {
    boolean isValid;
    List<ValidationIssue> issues;
    RepairAction suggestedAction; // REPAIR, DELETE, NOTIFY
}

public ValidationResult validateShop(Shop shop) {
    // 1. VÉRIFIER LE COFFRE (Priorité absolue)
    if (!isChestPresent(shop.getChestLocation())) {
        return ValidationResult.broken("Coffre manquant", RepairAction.DELETE);
    }

    // 2. VÉRIFIER LE PANNEAU
    if (!isSignPresent(shop.getSignLocation())) {
        issues.add("Panneau manquant");
        suggestedAction = RepairAction.REPAIR; // Recréer le panneau
    }

    // 3. VÉRIFIER L'HOLOGRAMME
    if (!isHologramPresent(shop)) {
        issues.add("Hologramme manquant");
        suggestedAction = RepairAction.REPAIR; // Recréer l'hologramme
    }

    // 4. VÉRIFIER LE STOCK
    int actualStock = countItemsInChest(shop);
    if (actualStock == 0 && shop.getStatus() == ShopStatus.ACTIVE) {
        issues.add("Stock épuisé");
        suggestedAction = RepairAction.UPDATE_STATUS;
    }

    // 5. VÉRIFIER LA COHÉRENCE DES DONNÉES
    if (!isItemMatchingTemplate(shop)) {
        issues.add("Item dans le coffre ne correspond pas au template");
        suggestedAction = RepairAction.NOTIFY;
    }

    return new ValidationResult(issues.isEmpty(), issues, suggestedAction);
}
```

---

### 💾 4. Classe `ShopPersistence` (Sauvegarde/Chargement)

**Responsabilités:**
- Sérialisation/Désérialisation
- Sauvegarde atomique
- Backup automatique
- Migration de versions

**Caractéristiques:**

```java
// === SAUVEGARDE ATOMIQUE ===
public void saveShops(Collection<Shop> shops) {
    // 1. Créer fichier temporaire
    File tempFile = new File(dataFolder, "shops.yml.tmp");

    // 2. Sérialiser et écrire
    YamlConfiguration config = new YamlConfiguration();
    config.set("version", CURRENT_VERSION);
    config.set("last-save", LocalDateTime.now().toString());
    config.set("shops", serializeShops(shops));
    config.save(tempFile);

    // 3. Créer backup de l'ancien fichier
    if (shopsFile.exists()) {
        Files.copy(shopsFile.toPath(),
                   new File(dataFolder, "shops.yml.backup").toPath(),
                   StandardCopyOption.REPLACE_EXISTING);
    }

    // 4. Déplacement atomique
    Files.move(tempFile.toPath(), shopsFile.toPath(),
               StandardCopyOption.REPLACE_EXISTING,
               StandardCopyOption.ATOMIC_MOVE);
}

// === CHARGEMENT AVEC VALIDATION ===
public LoadResult loadShops() {
    List<Shop> loadedShops = new ArrayList<>();
    List<LoadError> errors = new ArrayList<>();

    YamlConfiguration config = YamlConfiguration.loadConfiguration(shopsFile);

    // Vérifier la version
    String version = config.getString("version");
    if (needsMigration(version)) {
        migrateData(config, version, CURRENT_VERSION);
    }

    // Charger et valider chaque shop
    for (Map<?, ?> shopData : config.getMapList("shops")) {
        try {
            Shop shop = Shop.deserialize(shopData);

            // Validation immédiate
            ValidationResult validation = validator.validateShop(shop);
            if (validation.isValid()) {
                loadedShops.add(shop);
            } else {
                errors.add(new LoadError(shop.getShopId(), validation.getIssues()));
                if (validation.getSuggestedAction() == RepairAction.DELETE) {
                    logger.warning("Shop " + shop.getShopId() + " est cassé et sera ignoré");
                }
            }
        } catch (Exception e) {
            errors.add(new LoadError(null, "Erreur de désérialisation: " + e.getMessage()));
        }
    }

    return new LoadResult(loadedShops, errors);
}

// === AUTO-SAVE PÉRIODIQUE ===
public void startAutoSaveTask() {
    Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
        () -> saveShops(shopManager.getAllShops()),
        20L * 60 * 5, // Première sauvegarde après 5 minutes
        20L * 60 * 10  // Puis toutes les 10 minutes
    );
}
```

---

### 🎨 5. Classe `ShopComponents` (Gestion des composants visuels)

**Responsabilités:**
- Création/Mise à jour des panneaux
- Création/Mise à jour des hologrammes
- Synchronisation visuelle avec l'état du shop

#### 5.1 Gestion du Panneau

```java
public class ShopSignManager {

    public void createOrUpdateSign(Shop shop) {
        Block block = shop.getSignLocation().getBlock();

        // Vérifier que c'est bien un panneau
        if (!(block.getState() instanceof Sign)) {
            logger.severe("Bloc à " + shop.getSignLocation() + " n'est pas un panneau!");
            return;
        }

        Sign sign = (Sign) block.getState();

        // Récupérer le stock actuel
        int stock = countStock(shop);
        boolean hasStock = stock > 0;

        // Ligne 1: Statut (OUVERT/FERMÉ)
        if (shop.getStatus() == ShopStatus.ACTIVE && hasStock) {
            sign.setLine(0, ChatColor.GREEN + ChatColor.BOLD.toString() + "OUVERT");
        } else if (shop.getStatus() == ShopStatus.DISABLED) {
            sign.setLine(0, ChatColor.GRAY + ChatColor.BOLD.toString() + "FERMÉ");
        } else {
            sign.setLine(0, ChatColor.RED + ChatColor.BOLD.toString() + "FERMÉ");
        }

        // Ligne 2: Nom de l'entreprise
        sign.setLine(1, ChatColor.DARK_BLUE + truncate(shop.getEntrepriseName(), 15));

        // Ligne 3: Prix
        sign.setLine(2, ChatColor.GOLD + formatPrice(shop.getPricePerSale()) + "€");

        // Ligne 4: Stock disponible
        if (hasStock) {
            sign.setLine(3, ChatColor.GRAY + "Stock: " + ChatColor.WHITE + stock);
        } else {
            sign.setLine(3, ChatColor.RED + "Rupture");
        }

        sign.update(true); // Force update

        // Colorer le panneau si possible (1.17+)
        if (hasStock) {
            sign.setColor(DyeColor.GREEN);
        } else {
            sign.setColor(DyeColor.RED);
        }
        sign.update(true);
    }

    private int countStock(Shop shop) {
        Block chestBlock = shop.getChestLocation().getBlock();
        if (!(chestBlock.getState() instanceof Chest)) {
            return 0;
        }

        Chest chest = (Chest) chestBlock.getState();
        Inventory inv = chest.getInventory();

        int count = 0;
        ItemStack template = shop.getItemTemplate();

        for (ItemStack item : inv.getContents()) {
            if (item != null && item.isSimilar(template)) {
                count += item.getAmount();
            }
        }

        return count / shop.getQuantityPerSale(); // Nombre de ventes possibles
    }
}
```

#### 5.2 Gestion de l'Hologramme

```java
public class ShopHologramManager {

    public HologramComponents createOrUpdateHologram(Shop shop) {
        // Supprimer l'ancien hologramme si existe
        removeHologram(shop);

        Location hologramLoc = shop.getSignLocation().clone().add(0.5, 1.5, 0.5);
        World world = hologramLoc.getWorld();

        // === LIGNE 1: Display Item (ItemDisplay) ===
        ItemDisplay displayItem = (ItemDisplay) world.spawnEntity(
            hologramLoc.clone().add(0, 0.5, 0),
            EntityType.ITEM_DISPLAY
        );
        displayItem.setItemStack(shop.getItemTemplate());
        displayItem.setBillboard(Display.Billboard.VERTICAL);
        displayItem.setViewRange(32.0f);
        displayItem.setPersistent(false); // Ne pas sauvegarder dans le monde
        displayItem.setInvulnerable(true);
        displayItem.setGravity(false);

        // Rotation douce
        Transformation transform = displayItem.getTransformation();
        transform.getLeftRotation().set(new AxisAngle4f(0.01f, 0, 1, 0));
        displayItem.setTransformation(transform);

        // === LIGNE 2: Nom de l'item ===
        TextDisplay itemName = (TextDisplay) world.spawnEntity(
            hologramLoc.clone().add(0, 0.2, 0),
            EntityType.TEXT_DISPLAY
        );
        itemName.setText(formatItemName(shop.getItemTemplate()));
        itemName.setBillboard(Display.Billboard.VERTICAL);
        itemName.setAlignment(TextDisplay.TextAlignment.CENTER);
        itemName.setBackgroundColor(Color.fromARGB(0, 0, 0, 0)); // Transparent
        itemName.setShadowed(true);
        itemName.setViewRange(32.0f);
        itemName.setPersistent(false);

        // === LIGNE 3: Prix ===
        TextDisplay priceText = (TextDisplay) world.spawnEntity(
            hologramLoc.clone().add(0, 0.0, 0),
            EntityType.TEXT_DISPLAY
        );
        priceText.setText(
            ChatColor.GOLD + "" + ChatColor.BOLD +
            formatPrice(shop.getPricePerSale()) + "€"
        );
        priceText.setBillboard(Display.Billboard.VERTICAL);
        priceText.setAlignment(TextDisplay.TextAlignment.CENTER);
        priceText.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        priceText.setShadowed(true);
        priceText.setViewRange(32.0f);
        priceText.setPersistent(false);

        // === LIGNE 4: Quantité ===
        TextDisplay quantityText = (TextDisplay) world.spawnEntity(
            hologramLoc.clone().add(0, -0.2, 0),
            EntityType.TEXT_DISPLAY
        );
        quantityText.setText(
            ChatColor.AQUA + "x" + shop.getQuantityPerSale()
        );
        quantityText.setBillboard(Display.Billboard.VERTICAL);
        quantityText.setAlignment(TextDisplay.TextAlignment.CENTER);
        quantityText.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        quantityText.setShadowed(true);
        quantityText.setViewRange(32.0f);
        quantityText.setPersistent(false);

        // === LIGNE 5: Stock ===
        int stock = countStock(shop);
        TextDisplay stockText = (TextDisplay) world.spawnEntity(
            hologramLoc.clone().add(0, -0.4, 0),
            EntityType.TEXT_DISPLAY
        );
        if (stock > 0) {
            stockText.setText(ChatColor.GREEN + "✓ " + ChatColor.GRAY + "En stock");
        } else {
            stockText.setText(ChatColor.RED + "✗ Rupture");
        }
        stockText.setBillboard(Display.Billboard.VERTICAL);
        stockText.setAlignment(TextDisplay.TextAlignment.CENTER);
        stockText.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        stockText.setShadowed(true);
        stockText.setViewRange(32.0f);
        stockText.setPersistent(false);

        // Sauvegarder les IDs
        shop.setDisplayItemEntityId(displayItem.getUniqueId());
        shop.setHologramTextEntityIds(Arrays.asList(
            itemName.getUniqueId(),
            priceText.getUniqueId(),
            quantityText.getUniqueId(),
            stockText.getUniqueId()
        ));

        return new HologramComponents(displayItem,
            Arrays.asList(itemName, priceText, quantityText, stockText));
    }

    public void removeHologram(Shop shop) {
        World world = shop.getSignLocation().getWorld();
        if (world == null) return;

        // Supprimer le display item
        if (shop.getDisplayItemEntityId() != null) {
            Entity entity = Bukkit.getEntity(shop.getDisplayItemEntityId());
            if (entity != null) entity.remove();
        }

        // Supprimer les textes
        if (shop.getHologramTextEntityIds() != null) {
            for (UUID entityId : shop.getHologramTextEntityIds()) {
                Entity entity = Bukkit.getEntity(entityId);
                if (entity != null) entity.remove();
            }
        }
    }

    // Tâche de rotation douce de l'item
    public void startRotationTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Shop shop : shopManager.getActiveShops()) {
                if (shop.getDisplayItemEntityId() == null) continue;

                Entity entity = Bukkit.getEntity(shop.getDisplayItemEntityId());
                if (entity instanceof ItemDisplay display) {
                    Transformation transform = display.getTransformation();

                    // Rotation de 2 degrés par tick
                    float angle = (float) Math.toRadians(2);
                    AxisAngle4f rotation = transform.getLeftRotation();
                    rotation.set(angle, 0, 1, 0);

                    display.setTransformation(transform);
                }
            }
        }, 1L, 1L); // Chaque tick pour une rotation fluide
    }
}
```

---

### 🛒 6. Classe `ShopInteractionHandler` (Listeners)

**Responsabilités:**
- Gérer les clics sur les panneaux
- Traiter les achats
- Gérer la destruction de composants
- Synchroniser le GUI

#### 6.1 Achat d'items

```java
@EventHandler(priority = EventPriority.HIGH)
public void onSignClick(PlayerInteractEvent event) {
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

    Block block = event.getClickedBlock();
    if (!(block.getState() instanceof Sign)) return;

    // Récupérer le shop
    Optional<Shop> shopOpt = shopManager.getShopBySignLocation(block.getLocation());
    if (!shopOpt.isPresent()) return;

    Shop shop = shopOpt.get();
    Player buyer = event.getPlayer();
    event.setCancelled(true); // Empêcher l'édition du panneau

    // Vérifier que le shop est actif
    if (shop.getStatus() != ShopStatus.ACTIVE) {
        buyer.sendMessage(ChatColor.RED + "Cette boutique est actuellement fermée.");
        return;
    }

    // Traiter l'achat
    PurchaseResult result = shopManager.processPurchase(buyer, shop);

    switch (result.getStatus()) {
        case SUCCESS:
            buyer.sendMessage(ChatColor.GREEN + "✓ Achat effectué: " +
                shop.getQuantityPerSale() + "x " +
                formatItemName(shop.getItemTemplate()) +
                " pour " + formatPrice(shop.getPricePerSale()) + "€");

            // Effet sonore
            buyer.playSound(buyer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

            // Mettre à jour le panneau et l'hologramme
            shopComponents.updateSign(shop);
            shopComponents.updateHologram(shop);

            // Notifier le propriétaire
            notifyOwner(shop, buyer, result);
            break;

        case INSUFFICIENT_FUNDS:
            buyer.sendMessage(ChatColor.RED + "✗ Fonds insuffisants. Prix: " +
                formatPrice(shop.getPricePerSale()) + "€");
            buyer.playSound(buyer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            break;

        case OUT_OF_STOCK:
            buyer.sendMessage(ChatColor.RED + "✗ Rupture de stock.");
            buyer.playSound(buyer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);

            // Mettre à jour le statut
            shop.setStatus(ShopStatus.OUT_OF_STOCK);
            shopComponents.updateSign(shop);
            shopComponents.updateHologram(shop);
            break;

        case INVENTORY_FULL:
            buyer.sendMessage(ChatColor.RED + "✗ Votre inventaire est plein.");
            buyer.playSound(buyer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            break;

        case SHOP_BROKEN:
            buyer.sendMessage(ChatColor.RED + "✗ Cette boutique est endommagée.");
            logger.warning("Shop " + shop.getShopId() + " est cassé lors d'une tentative d'achat");

            // Auto-suppression
            shopManager.deleteShop(shop, "Coffre manquant détecté lors d'un achat", null);
            break;
    }
}

public PurchaseResult processPurchase(Player buyer, Shop shop) {
    // 1. VALIDER L'INTÉGRITÉ DU SHOP
    ValidationResult validation = validator.validateShop(shop);
    if (!validation.isValid()) {
        return PurchaseResult.shopBroken();
    }

    // 2. VÉRIFIER LE STOCK
    Chest chest = (Chest) shop.getChestLocation().getBlock().getState();
    int availableStock = countMatchingItems(chest.getInventory(), shop.getItemTemplate());

    if (availableStock < shop.getQuantityPerSale()) {
        shop.setStatus(ShopStatus.OUT_OF_STOCK);
        return PurchaseResult.outOfStock();
    }

    // 3. VÉRIFIER L'INVENTAIRE DE L'ACHETEUR
    if (!hasSpace(buyer.getInventory(), shop.getItemTemplate(), shop.getQuantityPerSale())) {
        return PurchaseResult.inventoryFull();
    }

    // 4. VÉRIFIER LES FONDS
    double balance = economy.getBalance(buyer);
    if (balance < shop.getPricePerSale()) {
        return PurchaseResult.insufficientFunds(balance, shop.getPricePerSale());
    }

    // 5. RETIRER LES ITEMS DU COFFRE
    int removed = removeItems(chest.getInventory(), shop.getItemTemplate(), shop.getQuantityPerSale());
    if (removed < shop.getQuantityPerSale()) {
        logger.severe("Erreur: pas assez d'items retirés du coffre pour shop " + shop.getShopId());
        return PurchaseResult.internalError();
    }

    // 6. EFFECTUER LA TRANSACTION
    economy.withdrawPlayer(buyer, shop.getPricePerSale());

    // 7. CRÉDITER L'ENTREPRISE
    Entreprise entreprise = entrepriseLogic.getEntrepriseBySiret(shop.getEntrepriseSiret());
    if (entreprise != null) {
        entreprise.ajouterSolde(shop.getPricePerSale());
        entrepriseLogic.saveEntreprises();
    } else {
        logger.warning("Entreprise introuvable pour le shop " + shop.getShopId() +
            " - L'argent est perdu!");
    }

    // 8. DONNER LES ITEMS À L'ACHETEUR
    ItemStack toGive = shop.getItemTemplate().clone();
    toGive.setAmount(shop.getQuantityPerSale());
    buyer.getInventory().addItem(toGive);

    // 9. METTRE À JOUR LES STATISTIQUES
    shop.incrementSales();
    shop.addRevenue(shop.getPricePerSale());
    shop.setLastPurchase(LocalDateTime.now());

    // 10. LOGGER LA TRANSACTION
    logger.info(String.format(
        "[SHOP] Achat effectué: %s a acheté %dx %s pour %.2f€ au shop %s (SIRET: %s)",
        buyer.getName(),
        shop.getQuantityPerSale(),
        shop.getItemTemplate().getType(),
        shop.getPricePerSale(),
        shop.getShopId(),
        shop.getEntrepriseSiret()
    ));

    return PurchaseResult.success(shop.getPricePerSale(), shop.getQuantityPerSale());
}
```

#### 6.2 Protection contre la destruction

```java
@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
public void onBlockBreak(BlockBreakEvent event) {
    Block block = event.getBlock();
    Player player = event.getPlayer();

    // Vérifier si c'est un composant de shop
    Optional<Shop> shopOpt = findShopByComponent(block.getLocation());
    if (!shopOpt.isPresent()) return;

    Shop shop = shopOpt.get();

    // Déterminer quel composant
    ComponentType component = getComponentType(shop, block.getLocation());

    // Vérifier les permissions
    boolean isOwner = shop.getOwnerUUID().equals(player.getUniqueId());
    boolean isGerant = isGerantOfEntreprise(player, shop.getEntrepriseSiret());
    boolean isAdmin = player.hasPermission("roleplaycity.admin.shop.break");

    if (!isOwner && !isGerant && !isAdmin) {
        event.setCancelled(true);
        player.sendMessage(ChatColor.RED + "Vous ne pouvez pas détruire ce composant de boutique.");
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        return;
    }

    // Confirmation requise pour la destruction du COFFRE
    if (component == ComponentType.CHEST) {
        if (!confirmationManager.hasPendingConfirmation(player.getUniqueId(), "SHOP_DELETE_" + shop.getShopId())) {
            event.setCancelled(true);

            player.sendMessage(ChatColor.YELLOW + "⚠ ATTENTION: Détruire ce coffre supprimera définitivement la boutique!");
            player.sendMessage(ChatColor.YELLOW + "Recliquez pour confirmer la suppression.");

            confirmationManager.requestConfirmation(
                player.getUniqueId(),
                "SHOP_DELETE_" + shop.getShopId(),
                30000L // 30 secondes
            );
            return;
        }

        // Confirmation validée → Suppression du shop
        shopManager.deleteShop(shop, "Coffre détruit par " + player.getName(), player);

        player.sendMessage(ChatColor.GREEN + "✓ Boutique supprimée.");
        logger.info("[SHOP] Shop " + shop.getShopId() + " supprimé par " + player.getName() + " (destruction du coffre)");
    }

    // Destruction du PANNEAU → Recréation automatique ou suppression
    else if (component == ComponentType.SIGN) {
        event.setCancelled(false); // Autoriser la destruction

        // Planifier la recréation après 1 tick
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Vérifier que le coffre existe toujours
            ValidationResult validation = validator.validateShop(shop);
            if (!validation.isValid() && validation.getSuggestedAction() == RepairAction.DELETE) {
                // Coffre manquant → Supprimer le shop
                shopManager.deleteShop(shop, "Coffre manquant après destruction du panneau", player);
            } else {
                // Coffre OK → On laisse le shop sans panneau (sera recréé au prochain restart ou manuellement)
                player.sendMessage(ChatColor.YELLOW + "Le panneau a été retiré. La boutique reste active.");
                player.sendMessage(ChatColor.GRAY + "Utilisez /entreprise shop repair pour recréer le panneau.");
            }
        }, 1L);
    }
}

private Optional<Shop> findShopByComponent(Location location) {
    // Vérifier coffre
    Optional<Shop> shopOpt = shopManager.getShopByChestLocation(location);
    if (shopOpt.isPresent()) return shopOpt;

    // Vérifier panneau
    shopOpt = shopManager.getShopBySignLocation(location);
    if (shopOpt.isPresent()) return shopOpt;

    return Optional.empty();
}
```

#### 6.3 Synchronisation après modification du coffre

```java
@EventHandler
public void onInventoryClose(InventoryCloseEvent event) {
    if (!(event.getInventory().getHolder() instanceof Chest)) return;

    Chest chest = (Chest) event.getInventory().getHolder();
    Optional<Shop> shopOpt = shopManager.getShopByChestLocation(chest.getLocation());

    if (!shopOpt.isPresent()) return;

    Shop shop = shopOpt.get();

    // Compter le nouveau stock
    int newStock = countMatchingItems(chest.getInventory(), shop.getItemTemplate());

    // Mettre à jour le statut
    if (newStock > 0 && shop.getStatus() == ShopStatus.OUT_OF_STOCK) {
        shop.setStatus(ShopStatus.ACTIVE);
    } else if (newStock == 0 && shop.getStatus() == ShopStatus.ACTIVE) {
        shop.setStatus(ShopStatus.OUT_OF_STOCK);
    }

    // Mettre à jour l'affichage
    shopComponents.updateSign(shop);
    shopComponents.updateHologram(shop);

    shop.setLastStockCheck(LocalDateTime.now());
}
```

---

## 🔄 Cycle de vie d'un Shop

### 1️⃣ Création

```
Joueur clique sur "Créer une boutique" dans le GUI
    ↓
Demander de placer un COFFRE
    ↓
Valider:
    - Le coffre est dans un terrain appartenant à l'entreprise
    - Le coffre n'est pas déjà utilisé par un autre shop
    - L'entreprise n'a pas atteint la limite de shops
    ↓
Demander de placer un PANNEAU (adjacent au coffre, max 2 blocs)
    ↓
Valider:
    - Le panneau est bien un panneau (Sign)
    - Le panneau est à portée du coffre
    - Le panneau n'est pas déjà utilisé
    ↓
Demander de mettre un item dans sa main (item à vendre)
    ↓
Demander la quantité par vente (chat)
    ↓
Demander le prix par vente (chat)
    ↓
CRÉER LE SHOP:
    1. Instancier objet Shop
    2. Créer l'hologramme au-dessus du panneau
    3. Mettre à jour le panneau avec les infos
    4. Sauvegarder dans shops.yml
    5. Ajouter à la Map en mémoire
    ↓
Confirmation au joueur avec TP vers le shop
```

**Conditions de création:**
- ✅ Le terrain doit appartenir à l'entreprise (propriétaire ou locataire)
- ✅ Le coffre ne doit pas déjà être utilisé
- ✅ Le panneau ne doit pas déjà être utilisé
- ✅ L'entreprise doit avoir l'autorisation (permissions)
- ✅ Limite: Maximum 10 shops par entreprise (configurable)

### 2️⃣ Utilisation

```
Joueur clique sur le PANNEAU
    ↓
Valider l'intégrité du shop
    ↓
Vérifier le stock dans le coffre
    ↓
Vérifier les fonds du joueur
    ↓
Vérifier l'espace dans l'inventaire
    ↓
TRANSACTION:
    1. Retirer items du coffre
    2. Retirer argent du joueur
    3. Créditer l'entreprise
    4. Donner items au joueur
    5. Mettre à jour statistiques
    6. Logger la transaction
    7. Mettre à jour le panneau/hologramme
```

### 3️⃣ Maintenance

```
Tâche périodique (toutes les 5 minutes)
    ↓
Pour chaque shop:
    ↓
    Valider l'intégrité
        ↓
        COFFRE manquant? → Supprimer le shop
        PANNEAU manquant? → Marquer pour réparation
        HOLOGRAMME manquant? → Recréer
        Stock vide? → Mettre statut OUT_OF_STOCK
```

### 4️⃣ Suppression

**Déclencheurs de suppression:**

1. **Destruction du coffre** → Suppression immédiate
2. **Dissolution de l'entreprise** → Suppression de tous les shops
3. **Perte du terrain** → Suppression des shops sur le terrain
4. **Suppression manuelle** → Via GUI ou commande admin
5. **Shop orphelin** → Nettoyage automatique si composants manquants

**Processus de suppression:**
```java
public ShopDeletionResult deleteShop(Shop shop, String reason, Player initiator) {
    logger.info("[SHOP] Suppression du shop " + shop.getShopId() +
        " (Raison: " + reason + ")");

    // 1. Supprimer les composants visuels
    shopComponents.removeHologram(shop);

    // 2. Nettoyer le panneau (ne pas détruire, juste vider le texte)
    Block signBlock = shop.getSignLocation().getBlock();
    if (signBlock.getState() instanceof Sign) {
        Sign sign = (Sign) signBlock.getState();
        sign.setLine(0, ChatColor.RED + "[SUPPRIMÉ]");
        sign.setLine(1, "");
        sign.setLine(2, "");
        sign.setLine(3, "");
        sign.update(true);
    }

    // 3. NE PAS toucher au coffre (contient peut-être des items)

    // 4. Retirer de la Map en mémoire
    shops.remove(shop.getShopId());

    // 5. Sauvegarder
    persistence.saveShops(shops.values());

    // 6. Notifier le propriétaire
    if (initiator != null) {
        Player owner = Bukkit.getPlayer(shop.getOwnerUUID());
        if (owner != null && owner.isOnline() && !owner.equals(initiator)) {
            owner.sendMessage(ChatColor.YELLOW + "⚠ Votre boutique a été supprimée.");
            owner.sendMessage(ChatColor.GRAY + "Raison: " + reason);
        }
    }

    // 7. Logger dans l'historique de l'entreprise
    Entreprise entreprise = entrepriseLogic.getEntrepriseBySiret(shop.getEntrepriseSiret());
    if (entreprise != null) {
        entreprise.ajouterHistorique(
            "Suppression d'une boutique (" + shop.getShopId().toString().substring(0, 8) + ")",
            reason
        );
    }

    return ShopDeletionResult.success();
}
```

---

## 🛡️ Système de Sécurité et Fiabilité

### 1. Protection contre les exploits

```java
// Anti-duplication d'items
public class AntiDupeChecker {
    private final Map<UUID, Long> lastPurchaseTime = new HashMap<>();
    private static final long COOLDOWN_MS = 500; // 0.5 secondes entre achats

    public boolean canPurchase(Player player) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (lastPurchaseTime.containsKey(playerId)) {
            long lastPurchase = lastPurchaseTime.get(playerId);
            if (now - lastPurchase < COOLDOWN_MS) {
                return false; // Trop rapide
            }
        }

        lastPurchaseTime.put(playerId, now);
        return true;
    }
}

// Protection contre la manipulation du coffre pendant l'achat
public synchronized PurchaseResult processPurchase(Player buyer, Shop shop) {
    // Utiliser un lock pour éviter les achats simultanés
    Lock shopLock = getShopLock(shop.getShopId());
    shopLock.lock();
    try {
        // Traitement de l'achat
        // ...
    } finally {
        shopLock.unlock();
    }
}
```

### 2. Validation continue

```java
// Tâche périodique de validation
public void startIntegrityCheckTask() {
    Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
        logger.info("[SHOP] Début de la vérification d'intégrité...");

        List<Shop> shopsToCheck = new ArrayList<>(shopManager.getAllShops());
        int repaired = 0;
        int deleted = 0;

        for (Shop shop : shopsToCheck) {
            ValidationResult result = validator.validateShop(shop);

            if (!result.isValid()) {
                switch (result.getSuggestedAction()) {
                    case REPAIR:
                        // Réparer le shop
                        shopComponents.repairShop(shop, result.getIssues());
                        repaired++;
                        break;

                    case DELETE:
                        // Supprimer le shop
                        shopManager.deleteShop(shop, "Auto-suppression: " + result.getIssues(), null);
                        deleted++;
                        break;

                    case NOTIFY:
                        // Notifier le propriétaire
                        Player owner = Bukkit.getPlayer(shop.getOwnerUUID());
                        if (owner != null && owner.isOnline()) {
                            owner.sendMessage(ChatColor.YELLOW +
                                "⚠ Problème détecté sur votre boutique: " + result.getIssues());
                        }
                        break;
                }
            }
        }

        logger.info(String.format(
            "[SHOP] Vérification terminée: %d shops vérifiés, %d réparés, %d supprimés",
            shopsToCheck.size(), repaired, deleted
        ));

    }, 20L * 60 * 5, 20L * 60 * 5); // Toutes les 5 minutes
}
```

### 3. Système de backup

```java
// Backup automatique avant chaque sauvegarde
public void saveWithBackup() {
    // Créer un backup horodaté
    File backupFolder = new File(dataFolder, "backups");
    backupFolder.mkdirs();

    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
    File backupFile = new File(backupFolder, "shops_" + timestamp + ".yml");

    // Copier le fichier actuel
    if (shopsFile.exists()) {
        try {
            Files.copy(shopsFile.toPath(), backupFile.toPath());
        } catch (IOException e) {
            logger.severe("Impossible de créer le backup: " + e.getMessage());
        }
    }

    // Nettoyer les vieux backups (garder seulement les 10 derniers)
    cleanOldBackups(backupFolder, 10);

    // Sauvegarder normalement
    saveShops(shops.values());
}

private void cleanOldBackups(File backupFolder, int keepCount) {
    File[] backups = backupFolder.listFiles((dir, name) -> name.startsWith("shops_"));
    if (backups == null || backups.length <= keepCount) return;

    // Trier par date (les plus récents en premier)
    Arrays.sort(backups, Comparator.comparingLong(File::lastModified).reversed());

    // Supprimer les anciens
    for (int i = keepCount; i < backups.length; i++) {
        backups[i].delete();
    }
}
```

### 4. Logs détaillés

```java
// Logger toutes les opérations importantes
public class ShopAuditLogger {

    public void logCreation(Shop shop, Player creator) {
        log(Level.INFO, String.format(
            "CREATION | Shop: %s | Entreprise: %s (%s) | Créateur: %s | Location: %s",
            shop.getShopId(),
            shop.getEntrepriseName(),
            shop.getEntrepriseSiret(),
            creator.getName(),
            formatLocation(shop.getChestLocation())
        ));
    }

    public void logPurchase(Shop shop, Player buyer, int quantity, double price) {
        log(Level.INFO, String.format(
            "PURCHASE | Shop: %s | Acheteur: %s | Quantité: %d | Prix: %.2f€ | Stock restant: %d",
            shop.getShopId(),
            buyer.getName(),
            quantity,
            price,
            getStockAfterPurchase(shop)
        ));
    }

    public void logDeletion(Shop shop, String reason, Player initiator) {
        log(Level.WARNING, String.format(
            "DELETION | Shop: %s | Raison: %s | Initiateur: %s | Ventes totales: %d | Revenu total: %.2f€",
            shop.getShopId(),
            reason,
            initiator != null ? initiator.getName() : "SYSTEM",
            shop.getTotalSales(),
            shop.getTotalRevenue()
        ));
    }

    public void logValidationFailure(Shop shop, ValidationResult result) {
        log(Level.SEVERE, String.format(
            "VALIDATION_FAILED | Shop: %s | Issues: %s | Action: %s",
            shop.getShopId(),
            String.join(", ", result.getIssues()),
            result.getSuggestedAction()
        ));
    }
}
```

---

## 📊 Statistiques et Monitoring

### Statistiques par Shop

```java
public class ShopStatistics {
    // Inclus dans la classe Shop
    private int totalSales = 0;
    private double totalRevenue = 0.0;
    private LocalDateTime lastPurchase;
    private final Map<String, Integer> topBuyers = new HashMap<>(); // Nom → Nombre d'achats

    public void incrementSales() {
        this.totalSales++;
    }

    public void addRevenue(double amount) {
        this.totalRevenue += amount;
    }

    public void recordBuyer(String buyerName) {
        topBuyers.put(buyerName, topBuyers.getOrDefault(buyerName, 0) + 1);
    }

    // GUI d'affichage des stats
    public void displayStats(Player viewer) {
        viewer.sendMessage(ChatColor.GOLD + "=== Statistiques de la boutique ===");
        viewer.sendMessage(ChatColor.GRAY + "ID: " + shopId.toString().substring(0, 8));
        viewer.sendMessage(ChatColor.AQUA + "Ventes totales: " + ChatColor.WHITE + totalSales);
        viewer.sendMessage(ChatColor.GOLD + "Revenu total: " + ChatColor.WHITE +
            String.format("%.2f€", totalRevenue));

        if (lastPurchase != null) {
            viewer.sendMessage(ChatColor.GRAY + "Dernier achat: " +
                formatTimeAgo(lastPurchase));
        }

        if (!topBuyers.isEmpty()) {
            viewer.sendMessage(ChatColor.YELLOW + "Top acheteurs:");
            topBuyers.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .forEach(entry -> viewer.sendMessage(ChatColor.GRAY + "  - " +
                    entry.getKey() + ": " + entry.getValue() + " achats"));
        }
    }
}
```

### Commandes d'administration

```java
// /shop info <shopId> - Afficher les infos d'un shop
// /shop list [siret] - Lister les shops (filtre optionnel par SIRET)
// /shop validate <shopId> - Valider l'intégrité d'un shop
// /shop repair <shopId> - Réparer un shop (recréer composants manquants)
// /shop delete <shopId> - Supprimer un shop (admin uniquement)
// /shop stats <shopId> - Afficher les statistiques d'un shop
// /shop cleanup - Nettoyer les shops orphelins
// /shop reload - Recharger la configuration
```

---

## 🎮 Expérience Utilisateur

### GUI de gestion pour le propriétaire

```
┌─────────────────────────────────────────────────────┐
│         🏪 Gestion de la boutique #a3f2            │
├─────────────────────────────────────────────────────┤
│                                                     │
│  [Item]    Changer l'objet vendu                   │
│            Actuellement: 64x DIAMOND               │
│                                                     │
│  [Gold]    Changer le prix                         │
│            Actuellement: 1500.00€                  │
│                                                     │
│  [Chest]   Changer la quantité                     │
│            Actuellement: 64 par vente              │
│                                                     │
│  [Chart]   Voir les statistiques                   │
│            Ventes: 42 | Revenu: 63,000.00€         │
│                                                     │
│  [Map]     Téléportation au shop                   │
│                                                     │
│  [Barrier] Désactiver temporairement               │
│            Statut: ✓ ACTIF                         │
│                                                     │
│  [TNT]     Supprimer définitivement                │
│            ⚠ Action irréversible                   │
│                                                     │
│                   [◄ Retour]                        │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### Notifications intelligentes

```java
// Notification au propriétaire lors d'une vente
public void notifyOwner(Shop shop, Player buyer, PurchaseResult result) {
    Player owner = Bukkit.getPlayer(shop.getOwnerUUID());
    if (owner != null && owner.isOnline()) {
        // Message discret dans l'action bar
        owner.spigot().sendMessage(
            ChatMessageType.ACTION_BAR,
            TextComponent.fromLegacyText(
                ChatColor.GREEN + "💰 Vente: " +
                result.getQuantity() + "x → +" +
                String.format("%.2f€", result.getPrice())
            )
        );
    }
}

// Notification de rupture de stock
public void notifyOutOfStock(Shop shop) {
    Player owner = Bukkit.getPlayer(shop.getOwnerUUID());
    if (owner != null && owner.isOnline()) {
        owner.sendMessage(ChatColor.RED + "⚠ Votre boutique est en rupture de stock!");
        owner.sendMessage(ChatColor.GRAY + "Shop: " +
            shop.getShopId().toString().substring(0, 8));
        owner.sendMessage(ChatColor.GRAY + "Location: " +
            formatLocation(shop.getChestLocation()));

        // Son de notification
        owner.playSound(owner.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
    }
}
```

---

## 🔧 Configuration

### config.yml

```yaml
shop-system:
  # Limites
  max-shops-per-entreprise: 10
  max-distance-chest-sign: 2 # Blocs

  # Économie
  creation-cost: 5000.0 # Coût de création d'un shop

  # Validation
  integrity-check-interval: 300 # Secondes (5 minutes)
  auto-repair-enabled: true
  auto-delete-broken-shops: true

  # Sauvegarde
  auto-save-interval: 600 # Secondes (10 minutes)
  backup-enabled: true
  backup-keep-count: 10

  # Affichage
  hologram-enabled: true
  hologram-view-range: 32.0
  hologram-rotation-enabled: true

  # Sécurité
  purchase-cooldown-ms: 500
  require-confirmation-delete: true

  # Notifications
  notify-owner-on-purchase: true
  notify-owner-on-out-of-stock: true
  notify-owner-on-shop-broken: true
```

---

## 📈 Points d'amélioration futurs

### Phase 2 (Optionnel)

1. **Système de promotions**
   - Réductions temporaires
   - Happy hours
   - Ventes flash

2. **Système de commandes**
   - Précommander des items
   - Paiement à l'avance
   - Notification quand disponible

3. **Système de livraison**
   - Livraison à domicile (via mailbox)
   - Frais de livraison configurables

4. **Intégration avec la ville**
   - Taxes sur les ventes
   - Licences commerciales
   - Zones commerciales spéciales

5. **Système de réputation**
   - Notes et avis des clients
   - Badge "Shop de confiance"
   - Top shops du serveur

---

## ✅ Checklist de fiabilité

- ✅ **Coffre = Source de vérité unique**
- ✅ **Validation d'intégrité périodique**
- ✅ **Auto-nettoyage des shops cassés**
- ✅ **Sauvegarde atomique avec backup**
- ✅ **Logs détaillés de toutes les opérations**
- ✅ **Protection contre la duplication**
- ✅ **Gestion des erreurs robuste**
- ✅ **Notifications intelligentes**
- ✅ **Interface intuitive**
- ✅ **Performance optimisée (async où possible)**
- ✅ **Code modulaire et maintenable**
- ✅ **Tests d'intégrité automatiques**

---

## 🎯 Résumé

Ce système garantit:
1. **Zéro perte de données** grâce aux backups et sauvegardes atomiques
2. **Auto-réparation** des composants manquants
3. **Suppression automatique** des shops cassés
4. **Traçabilité complète** avec logs détaillés
5. **Expérience utilisateur fluide** avec GUI intuitif
6. **Performance optimale** avec tâches asynchrones
7. **Sécurité maximale** contre les exploits

Le coffre reste **toujours** la référence. Si le coffre disparaît, le shop disparaît. Pas de compromis. Simple. Fiable. Robuste. 🚀
