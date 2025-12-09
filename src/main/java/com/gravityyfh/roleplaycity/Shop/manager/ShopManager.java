package com.gravityyfh.roleplaycity.shop.manager;

import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.gravityyfh.roleplaycity.entreprise.model.*;
import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.shop.*;
import com.gravityyfh.roleplaycity.shop.components.ShopComponents;
import com.gravityyfh.roleplaycity.shop.model.Shop;
import com.gravityyfh.roleplaycity.shop.persistence.ShopPersistence;
import com.gravityyfh.roleplaycity.shop.util.LocationKey;
import com.gravityyfh.roleplaycity.shop.validation.ShopValidator;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Gestionnaire central du système de boutiques
 * Point d'entrée unique pour toutes les opérations sur les boutiques
 */
public class ShopManager {
    private final RoleplayCity plugin;
    private final ShopValidator validator;
    private final ShopPersistence persistence;
    private final ShopComponents components;
    private final EntrepriseManagerLogic entrepriseLogic;
    private final Economy economy;

    // Stockage en mémoire des boutiques
    private final Map<UUID, Shop> shopsById;
    private final Map<LocationKey, UUID> shopsByChestLocation;
    private final Map<LocationKey, UUID> shopsBySignLocation;

    // Locks pour les achats (éviter la duplication)
    private final Map<UUID, Lock> shopLocks;

    // Anti-duplication pour les achats
    private final Map<UUID, Long> lastPurchaseTime;
    private static final long PURCHASE_COOLDOWN_MS = 500;

    // Configuration
    private final int maxShopsPerEntreprise;
    private final boolean autoRepairEnabled;
    private final boolean autoDeleteBrokenShops;

    private final com.gravityyfh.roleplaycity.shop.service.ShopPersistenceService sqlitePersistence;

    public ShopManager(RoleplayCity plugin, EntrepriseManagerLogic entrepriseLogic, Economy economy, 
                       com.gravityyfh.roleplaycity.shop.service.ShopPersistenceService sqlitePersistence) {
        this.plugin = plugin;
        this.entrepriseLogic = entrepriseLogic;
        this.economy = economy;
        this.sqlitePersistence = sqlitePersistence;

        // Initialiser les composants
        this.validator = new ShopValidator(plugin);
        this.persistence = new ShopPersistence(plugin, validator);
        this.components = new ShopComponents(plugin, validator);

        // Initialiser le stockage
        this.shopsById = new ConcurrentHashMap<>();
        this.shopsByChestLocation = new ConcurrentHashMap<>();
        this.shopsBySignLocation = new ConcurrentHashMap<>();
        this.shopLocks = new ConcurrentHashMap<>();
        this.lastPurchaseTime = new ConcurrentHashMap<>();

        // Configuration
        this.maxShopsPerEntreprise = plugin.getConfig().getInt("shop-system.max-shops-per-entreprise", 10);
        this.autoRepairEnabled = plugin.getConfig().getBoolean("shop-system.auto-repair-enabled", true);
        this.autoDeleteBrokenShops = plugin.getConfig().getBoolean("shop-system.auto-delete-broken-shops", true);
    }

    // ===== INITIALISATION =====

    /**
     * Charge les boutiques depuis le fichier ou SQLite
     */
    public void loadShops() {
        if (sqlitePersistence != null) {
            // Mode SQLite
            plugin.getLogger().info("[ShopSystem] Chargement depuis SQLite...");
            Map<String, Shop> shops = sqlitePersistence.loadShops();
            
            for (Shop shop : shops.values()) {
                registerShop(shop);
            }
            
            plugin.getLogger().info("[ShopSystem] " + shops.size() + " boutiques chargées (SQLite)");
            
        } else {
            // Mode YAML Legacy
            ShopPersistence.LoadResult result = persistence.loadShops();
    
            for (Shop shop : result.shops()) {
                registerShop(shop);
            }
    
            if (result.hasErrors()) {
                plugin.getLogger().warning("[ShopSystem] " + result.errors().size() +
                    " erreur(s) lors du chargement:");
                for (ShopPersistence.LoadError error : result.errors()) {
                    plugin.getLogger().warning("[ShopSystem] - " + error.toString());
                }
            }
        }

        // Recréer tous les hologrammes après le chargement
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int recreated = 0;

            plugin.getLogger().info("[ShopSystem] Début de la recréation des hologrammes...");

            for (Shop shop : shopsById.values()) {
                Location chestLoc = shop.getChestLocation();
                if (chestLoc.getWorld() != null) {
                    // Forcer le chargement du chunk
                    if (!chestLoc.getChunk().isLoaded()) {
                        chestLoc.getChunk().load();
                        // Attendre un tick que le chunk soit bien chargé
                        try { Thread.sleep(50); } catch (InterruptedException e) {}
                    }

                    // Recréer les composants (updateComponents appelle removeHologram puis createOrUpdateHologram)
                    components.updateComponents(shop);
                    recreated++;
                }
            }

            plugin.getLogger().info("[ShopSystem] " + recreated + " hologramme(s) recréé(s) avec succès");
        }, 200L); // Attendre 2 secondes pour que le serveur soit bien démarré

        // Démarrer les tâches périodiques
        startPeriodicTasks();

        plugin.getLogger().info("[ShopSystem] Système de boutiques initialisé avec " +
            shopsById.size() + " boutique(s)");
    }

    /**
     * Sauvegarde toutes les boutiques
     */
    public java.util.concurrent.CompletableFuture<Void> saveShops() {
        if (sqlitePersistence != null) {
            return sqlitePersistence.saveShops(shopsById.values());
        } else {
            persistence.saveShops(shopsById.values());
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Démarre les tâches périodiques
     */
    private void startPeriodicTasks() {
        // Auto-save toutes les 10 minutes
        int autoSaveInterval = plugin.getConfig().getInt("shop-system.auto-save-interval", 600);
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
            this::saveShops,
            20L * autoSaveInterval,
            20L * autoSaveInterval
        );

        // Vérification d'intégrité toutes les 5 minutes (sur thread principal)
        int integrityCheckInterval = plugin.getConfig().getInt("shop-system.integrity-check-interval", 300);
        Bukkit.getScheduler().runTaskTimer(plugin,
            this::checkIntegrity,
            20L * integrityCheckInterval,
            20L * integrityCheckInterval
        );

        // Rotation des hologrammes
        components.startRotationTask(new ArrayList<>(shopsById.values()));
    }

    // ===== CRÉATION =====

    /**
     * Valide que le shop peut être placé sur le terrain
     * Vérifie : type PROFESSIONNEL, appartenance à l'entreprise, permissions
     */
    private ShopCreationResult validateShopPlacement(Player creator, Entreprise entreprise, Location location) {
        // 1. Récupérer le terrain à cet emplacement
        com.gravityyfh.roleplaycity.town.manager.ClaimManager claimManager = plugin.getClaimManager();
        if (claimManager == null) {
            return ShopCreationResult.failure("Système de terrains non disponible");
        }

        com.gravityyfh.roleplaycity.town.data.Plot plot = claimManager.getPlotAt(location);
        if (plot == null) {
            return ShopCreationResult.failure("Ce terrain n'est pas dans une ville. Vous devez créer votre shop sur un terrain PROFESSIONNEL dans une ville.");
        }

        // 2. Vérifier que c'est un terrain PROFESSIONNEL
        if (plot.getType() != com.gravityyfh.roleplaycity.town.data.PlotType.PROFESSIONNEL) {
            return ShopCreationResult.failure("Vous devez créer ce shop sur un terrain de type PROFESSIONNEL (type actuel: " + plot.getType().getDisplayName() + ")");
        }

        // 3. Vérifier que le terrain appartient à l'entreprise (via SIRET)
        String plotSiret = plot.getCompanySiret();
        if (plotSiret == null || !plotSiret.equals(entreprise.getSiret())) {
            return ShopCreationResult.failure("Ce terrain appartient à une autre entreprise. Vous devez créer votre shop sur un terrain de VOTRE entreprise.");
        }

        // 4. Vérifier que le joueur est propriétaire OU locataire
        UUID playerUuid = creator.getUniqueId();
        boolean isOwner = plot.isOwnedBy(playerUuid);
        boolean isRenter = plot.isRentedBy(playerUuid);

        if (!isOwner && !isRenter) {
            return ShopCreationResult.failure("Vous devez être propriétaire ou locataire de ce terrain pour y créer un shop.");
        }

        // 5. Vérifier les permissions de construction
        com.gravityyfh.roleplaycity.town.data.Town town = plugin.getTownManager().getTown(plot.getTownName());
        if (town == null) {
            return ShopCreationResult.failure("Ville introuvable");
        }

        if (!plot.canBuild(playerUuid, town)) {
            return ShopCreationResult.failure("Vous n'avez pas la permission de construire sur ce terrain.");
        }

        return ShopCreationResult.success(null); // Validation réussie
    }

    /**
     * Vérifie si un item peut être vendu par cette entreprise
     * Gère à la fois les items standards (Material) et les items customs (backpacks ItemsAdder)
     */
    private boolean isItemAllowedForShop(Entreprise entreprise, ItemStack itemToSell) {
        if (itemToSell == null || entreprise == null) {
            return false;
        }

        // Vérifier si c'est un backpack custom (ItemsAdder)
        com.gravityyfh.roleplaycity.backpack.manager.BackpackItemManager backpackManager = plugin.getBackpackItemManager();
        if (backpackManager != null && backpackManager.isBackpack(itemToSell)) {
            // C'est un backpack → vérifier dans action_restrictions.CRAFT_BACKPACK
            com.gravityyfh.roleplaycity.backpack.model.BackpackType backpackType = backpackManager.getBackpackType(itemToSell);
            if (backpackType == null) {
                return false;
            }
            return entrepriseLogic.isBackpackAllowedInShop(entreprise.getType(), backpackType.getId());
        }

        // Item standard → vérifier dans action_restrictions (BLOCK_BREAK, CRAFT_ITEM, etc.)
        return entrepriseLogic.isItemAllowedInShop(entreprise.getType(), itemToSell.getType());
    }

    /**
     * Crée une nouvelle boutique
     */
    public ShopCreationResult createShop(Player creator, Entreprise entreprise,
                                         Location chestLocation, Location signLocation,
                                         ItemStack itemToSell, int quantity, double price) {

        // Vérifications
        if (entreprise == null) {
            return ShopCreationResult.failure("Entreprise introuvable");
        }

        // NOUVELLE VALIDATION 1: Vérifier le terrain (type PROFESSIONNEL, appartenance, permissions)
        ShopCreationResult terrainValidation = validateShopPlacement(creator, entreprise, chestLocation);
        if (!terrainValidation.isSuccess()) {
            return terrainValidation; // Retourner l'erreur de validation
        }

        // NOUVELLE VALIDATION 2: Vérifier que l'item peut être vendu par cette entreprise
        if (!isItemAllowedForShop(entreprise, itemToSell)) {
            String itemName = itemToSell.hasItemMeta() && itemToSell.getItemMeta().hasDisplayName()
                ? itemToSell.getItemMeta().getDisplayName()
                : itemToSell.getType().name();
            return ShopCreationResult.failure("Votre entreprise de type '" + entreprise.getType() + "' ne peut pas vendre: " + itemName + ". Consultez 'action_restrictions' dans votre type d'entreprise.");
        }

        if (!validator.isChestPresent(chestLocation)) {
            return ShopCreationResult.failure("Aucun coffre trouvé à cet emplacement");
        }

        if (!validator.isSignPresent(signLocation)) {
            return ShopCreationResult.failure("Aucun panneau trouvé à cet emplacement");
        }

        // Vérifier que le coffre n'est pas déjà utilisé
        if (shopsByChestLocation.containsKey(chestLocation)) {
            return ShopCreationResult.failure("Ce coffre est déjà utilisé par une autre boutique");
        }

        // Vérifier que le panneau n'est pas déjà utilisé
        if (shopsBySignLocation.containsKey(signLocation)) {
            return ShopCreationResult.failure("Ce panneau est déjà utilisé par une autre boutique");
        }

        // Vérifier la limite de boutiques
        List<Shop> existingShops = getShopsBySiret(entreprise.getSiret());
        if (existingShops.size() >= maxShopsPerEntreprise) {
            return ShopCreationResult.failure("Limite de boutiques atteinte (" +
                maxShopsPerEntreprise + " maximum)");
        }

        // Vérifier la distance entre coffre et panneau
        double distance = chestLocation.distance(signLocation);
        double maxDistance = plugin.getConfig().getDouble("shop-system.max-distance-chest-sign", 2.0);
        if (distance > maxDistance) {
            return ShopCreationResult.failure("Le panneau est trop éloigné du coffre (max " +
                maxDistance + " blocs)");
        }

        try {
            // Créer le shop
            Shop shop = new Shop(
                entreprise.getNom(),
                entreprise.getSiret(),
                creator.getUniqueId(),
                creator.getName(),
                chestLocation,
                signLocation,
                itemToSell,
                quantity,
                price
            );

            // Créer les composants visuels
            components.createComponents(shop);

            // Enregistrer
            registerShop(shop);

            // Sauvegarder
            saveShops();

            plugin.getLogger().info("[ShopSystem] Boutique créée: " + shop.getShopId() +
                " par " + creator.getName() + " pour " + entreprise.getNom());

            return ShopCreationResult.success(shop);

        } catch (Exception e) {
            plugin.getLogger().severe("[ShopSystem] Erreur lors de la création de la boutique: " + e.getMessage());
            e.printStackTrace();
            return ShopCreationResult.failure("Erreur interne: " + e.getMessage());
        }
    }

    // ===== RÉCUPÉRATION =====

    public Optional<Shop> getShopById(UUID shopId) {
        return Optional.ofNullable(shopsById.get(shopId));
    }

    public Optional<Shop> getShopByChestLocation(Location location) {
        LocationKey key = new LocationKey(location);
        UUID shopId = shopsByChestLocation.get(key);
        return shopId != null ? Optional.ofNullable(shopsById.get(shopId)) : Optional.empty();
    }

    public Optional<Shop> getShopBySignLocation(Location location) {
        LocationKey key = new LocationKey(location);
        UUID shopId = shopsBySignLocation.get(key);
        return shopId != null ? Optional.ofNullable(shopsById.get(shopId)) : Optional.empty();
    }

    public List<Shop> getShopsBySiret(String siret) {
        return shopsById.values().stream()
            .filter(shop -> shop.getEntrepriseSiret().equals(siret))
            .sorted(Comparator.comparing(Shop::getCreationDate))
            .collect(Collectors.toList());
    }

    public List<Shop> getShopsByOwner(UUID ownerUUID) {
        return shopsById.values().stream()
            .filter(shop -> shop.getOwnerUUID().equals(ownerUUID))
            .sorted(Comparator.comparing(Shop::getCreationDate))
            .collect(Collectors.toList());
    }

    public List<Shop> getActiveShops() {
        return shopsById.values().stream()
            .filter(shop -> shop.getStatus() == ShopStatus.ACTIVE)
            .collect(Collectors.toList());
    }

    public Collection<Shop> getAllShops() {
        return new ArrayList<>(shopsById.values());
    }

    // ===== MODIFICATION =====

    public ShopUpdateResult updateItemForSale(Shop shop, ItemStack newItem) {
        shop.setItemTemplate(newItem);
        components.updateComponents(shop);
        saveShops();
        return ShopUpdateResult.success("Item mis à jour");
    }

    public ShopUpdateResult updatePrice(Shop shop, double newPrice) {
        shop.setPricePerSale(newPrice);
        components.updateComponents(shop);
        saveShops();
        return ShopUpdateResult.success("Prix mis à jour");
    }

    public ShopUpdateResult updateQuantity(Shop shop, int newQuantity) {
        shop.setQuantityPerSale(newQuantity);
        components.updateComponents(shop);
        saveShops();
        return ShopUpdateResult.success("Quantité mise à jour");
    }

    public ShopUpdateResult toggleShopStatus(Shop shop, Player admin) {
        if (shop.getStatus() == ShopStatus.ACTIVE) {
            shop.setStatus(ShopStatus.DISABLED);
        } else {
            shop.setStatus(ShopStatus.ACTIVE);
        }
        components.updateSign(shop);
        saveShops();
        return ShopUpdateResult.success("Statut mis à jour");
    }

    // ===== SUPPRESSION =====

    public ShopDeletionResult deleteShop(Shop shop, String reason, Player initiator) {
        plugin.getLogger().info("[ShopSystem] Suppression du shop " + shop.getShopId() +
            " (Raison: " + reason + ")");

        // Supprimer les composants visuels
        components.removeComponents(shop);

        // Désenregistrer
        unregisterShop(shop);

        // Sauvegarder
        saveShops();

        // Notifier le propriétaire
        if (initiator != null) {
            Player owner = Bukkit.getPlayer(shop.getOwnerUUID());
            if (owner != null && owner.isOnline() && !owner.equals(initiator)) {
                owner.sendMessage("§e⚠ Votre boutique a été supprimée.");
                owner.sendMessage("§7Raison: " + reason);
            }
        }

        return ShopDeletionResult.success();
    }

    public int deleteShopsBySiret(String siret, String reason) {
        List<Shop> shopsToDelete = getShopsBySiret(siret);
        for (Shop shop : shopsToDelete) {
            deleteShop(shop, reason, null);
        }
        return shopsToDelete.size();
    }

    public int deleteShopsByOwner(UUID ownerUUID, String reason) {
        List<Shop> shopsToDelete = getShopsByOwner(ownerUUID);
        for (Shop shop : shopsToDelete) {
            deleteShop(shop, reason, null);
        }
        return shopsToDelete.size();
    }

    /**
     * Supprime tous les shops dans une ville donnée
     */
    public int deleteShopsInTown(String townName, String reason) {
        List<Shop> shopsToDelete = new ArrayList<>();

        for (Shop shop : shopsById.values()) {
            // Utiliser ClaimManager pour déterminer si le shop est dans cette ville
            com.gravityyfh.roleplaycity.town.data.Plot plot = plugin.getClaimManager().getPlotAt(shop.getChestLocation());
            if (plot != null && plot.getTownName().equals(townName)) {
                shopsToDelete.add(shop);
            }
        }

        for (Shop shop : shopsToDelete) {
            deleteShop(shop, reason, null);
        }

        return shopsToDelete.size();
    }

    /**
     * Récupère tous les shops sur un terrain donné
     */
    public List<Shop> getShopsOnPlot(com.gravityyfh.roleplaycity.town.data.Plot plot) {
        List<Shop> result = new ArrayList<>();

        for (Shop shop : shopsById.values()) {
            com.gravityyfh.roleplaycity.town.data.Plot shopPlot = plugin.getClaimManager().getPlotAt(shop.getChestLocation());
            if (shopPlot != null && shopPlot.equals(plot)) {
                result.add(shop);
            }
        }

        return result;
    }

    /**
     * Supprime tous les shops sur un terrain donné
     */
    public int deleteShopsOnPlot(com.gravityyfh.roleplaycity.town.data.Plot plot, String reason) {
        List<Shop> shopsToDelete = getShopsOnPlot(plot);

        for (Shop shop : shopsToDelete) {
            deleteShop(shop, reason, null);
        }

        return shopsToDelete.size();
    }

    /**
     * Supprime les shops d'une entreprise spécifique sur un terrain donné
     */
    public int deleteShopsByCompanyOnPlot(String siret, com.gravityyfh.roleplaycity.town.data.Plot plot, String reason) {
        List<Shop> shopsToDelete = new ArrayList<>();

        for (Shop shop : getShopsOnPlot(plot)) {
            if (shop.getEntrepriseSiret().equals(siret)) {
                shopsToDelete.add(shop);
            }
        }

        for (Shop shop : shopsToDelete) {
            deleteShop(shop, reason, null);
        }

        return shopsToDelete.size();
    }

    /**
     * Supprime les shops d'un propriétaire spécifique sur un terrain donné
     */
    public int deleteShopsByOwnerOnPlot(UUID ownerUUID, com.gravityyfh.roleplaycity.town.data.Plot plot, String reason) {
        List<Shop> shopsToDelete = new ArrayList<>();

        for (Shop shop : getShopsOnPlot(plot)) {
            if (shop.getOwnerUUID().equals(ownerUUID)) {
                shopsToDelete.add(shop);
            }
        }

        for (Shop shop : shopsToDelete) {
            deleteShop(shop, reason, null);
        }

        return shopsToDelete.size();
    }

    // ===== INTERACTION =====

    public PurchaseResult processPurchase(Player buyer, Shop shop) {
        // Anti-spam
        if (!canPurchase(buyer)) {
            return PurchaseResult.cooldownActive();
        }

        // Lock pour éviter les achats simultanés
        Lock lock = getShopLock(shop.getShopId());
        lock.lock();

        try {
            // 1. Valider l'intégrité - seul le coffre manquant est critique pour un achat
            ValidationResult validation = validator.validateShop(shop);
            if (!validation.isValid() && validation.getSuggestedAction() == RepairAction.DELETE) {
                // Coffre manquant = shop vraiment cassé
                return PurchaseResult.shopBroken();
            }

            // Si autres problèmes (hologramme/panneau manquant), tenter de réparer silencieusement
            if (!validation.isValid() && validation.getSuggestedAction() == RepairAction.REPAIR) {
                components.updateComponents(shop);
            }

            // 2. Vérifier le stock
            int availableStock = validator.countRawItemsInChest(shop);
            if (availableStock < shop.getQuantityPerSale()) {
                shop.setStatus(ShopStatus.OUT_OF_STOCK);
                components.updateComponents(shop); // Mettre à jour TOUS les composants
                return PurchaseResult.outOfStock();
            }

            // 3. Vérifier l'inventaire
            if (!validator.hasInventorySpace(buyer, shop.getItemTemplate(), shop.getQuantityPerSale())) {
                return PurchaseResult.inventoryFull();
            }

            // 4. Vérifier les fonds
            double balance = economy.getBalance(buyer);
            if (balance < shop.getPricePerSale()) {
                return PurchaseResult.insufficientFunds(balance, shop.getPricePerSale());
            }

            // 5. Retirer les items du coffre
            int removed = validator.removeItemsFromChest(shop, shop.getQuantityPerSale());
            if (removed < shop.getQuantityPerSale()) {
                plugin.getLogger().severe("[ShopSystem] Erreur: pas assez d'items retirés pour shop " +
                    shop.getShopId());
                return PurchaseResult.internalError();
            }

            // 6. Transaction économique
            economy.withdrawPlayer(buyer, shop.getPricePerSale());

            // 7. Créditer l'entreprise
            Entreprise entreprise = entrepriseLogic.getEntrepriseBySiret(shop.getEntrepriseSiret());
            if (entreprise != null) {
                double nouveauSolde = entreprise.getSolde() + shop.getPricePerSale();
                entreprise.setSolde(nouveauSolde);
                entrepriseLogic.saveEntreprises();
            } else {
                plugin.getLogger().warning("[ShopSystem] Entreprise introuvable pour shop " +
                    shop.getShopId() + " - L'argent est perdu!");
            }

            // 8. Donner les items
            ItemStack toGive = shop.getItemTemplate().clone();
            toGive.setAmount(shop.getQuantityPerSale());
            buyer.getInventory().addItem(toGive);

            // 9. Statistiques
            shop.incrementSales();
            shop.addRevenue(shop.getPricePerSale());
            shop.setLastPurchase(LocalDateTime.now());
            shop.recordBuyer(buyer.getName());

            // 10. Logger
            plugin.getLogger().info(String.format(
                "[ShopSystem] Achat: %s a acheté %dx %s pour %.2f€ au shop %s",
                buyer.getName(),
                shop.getQuantityPerSale(),
                shop.getItemTemplate().getType(),
                shop.getPricePerSale(),
                shop.getShopId().toString().substring(0, 8)
            ));

            // 11. Vérifier si le stock restant est suffisant pour le prochain achat
            int remainingStock = validator.countRawItemsInChest(shop);
            if (remainingStock < shop.getQuantityPerSale()) {
                // Stock insuffisant pour un autre lot → mettre en rupture
                shop.setStatus(ShopStatus.OUT_OF_STOCK);
                plugin.getLogger().info("[ShopSystem] Shop " + shop.getShopId().toString().substring(0, 8) +
                    " mis en rupture (reste " + remainingStock + " items, lot de " + shop.getQuantityPerSale() + ")");
            }

            // Mettre à jour l'affichage
            components.updateComponents(shop);

            return PurchaseResult.success(shop.getPricePerSale(), shop.getQuantityPerSale());

        } finally {
            lock.unlock();
        }
    }

    // ===== VALIDATION =====

    public ValidationResult validateShopIntegrity(Shop shop) {
        return validator.validateShop(shop);
    }

    public void checkIntegrity() {
        plugin.getLogger().info("[ShopSystem] Début de la vérification d'intégrité...");

        List<Shop> shopsToCheck = new ArrayList<>(shopsById.values());
        int repaired = 0;
        int deleted = 0;
        int statusUpdated = 0;

        for (Shop shop : shopsToCheck) {
            ValidationResult result = validator.validateShop(shop);

            if (!result.isValid()) {
                switch (result.getSuggestedAction()) {
                    case REPAIR:
                        if (autoRepairEnabled) {
                            components.repairShop(shop, result.getIssues());
                            repaired++;
                        }
                        break;

                    case DELETE:
                        if (autoDeleteBrokenShops) {
                            deleteShop(shop, "Auto-suppression: " + result.getIssuesAsString(), null);
                            deleted++;
                        }
                        break;

                    case UPDATE_STATUS:
                        int stock = validator.countItemsInChest(shop);
                        if (stock > 0 && shop.getStatus() == ShopStatus.OUT_OF_STOCK) {
                            shop.setStatus(ShopStatus.ACTIVE);
                            components.updateSign(shop);
                            statusUpdated++;
                        } else if (stock == 0 && shop.getStatus() == ShopStatus.ACTIVE) {
                            shop.setStatus(ShopStatus.OUT_OF_STOCK);
                            components.updateSign(shop);
                            statusUpdated++;
                        }
                        break;

                    case NOTIFY:
                        Player owner = Bukkit.getPlayer(shop.getOwnerUUID());
                        if (owner != null && owner.isOnline()) {
                            owner.sendMessage("§e⚠ Problème détecté sur votre boutique: " +
                                result.getIssuesAsString());
                        }
                        break;
                }
            }
        }

        plugin.getLogger().info(String.format(
            "[ShopSystem] Vérification terminée: %d shops vérifiés, %d réparés, %d supprimés, %d statuts mis à jour",
            shopsToCheck.size(), repaired, deleted, statusUpdated
        ));
    }

    // ===== MÉTHODES PRIVÉES =====

    private void registerShop(Shop shop) {
        shopsById.put(shop.getShopId(), shop);
        shopsByChestLocation.put(new LocationKey(shop.getChestLocation()), shop.getShopId());
        shopsBySignLocation.put(new LocationKey(shop.getSignLocation()), shop.getShopId());
    }

    private void unregisterShop(Shop shop) {
        shopsById.remove(shop.getShopId());
        shopsByChestLocation.remove(new LocationKey(shop.getChestLocation()));
        shopsBySignLocation.remove(new LocationKey(shop.getSignLocation()));
        shopLocks.remove(shop.getShopId());
    }

    private Lock getShopLock(UUID shopId) {
        return shopLocks.computeIfAbsent(shopId, k -> new ReentrantLock());
    }

    private boolean canPurchase(Player player) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (lastPurchaseTime.containsKey(playerId)) {
            long lastPurchase = lastPurchaseTime.get(playerId);
            if (now - lastPurchase < PURCHASE_COOLDOWN_MS) {
                return false;
            }
        }

        lastPurchaseTime.put(playerId, now);
        return true;
    }

    // ===== CLEANUP =====

    public void cleanup() {
        plugin.getLogger().info("[ShopSystem] Nettoyage du système de boutiques...");

        // Supprimer tous les hologrammes
        for (Shop shop : shopsById.values()) {
            components.getHologramManager().removeHologram(shop);
        }

        // Sauvegarde finale synchrone
        if (!shopsById.isEmpty()) {
            saveShops();
            // Attendre que la sauvegarde async se termine
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        plugin.getLogger().info("[ShopSystem] Système de boutiques nettoyé");
    }

    // ===== GETTERS =====

    public ShopComponents getComponents() {
        return components;
    }

    public ShopValidator getValidator() {
        return validator;
    }

    public ShopPersistence getPersistence() {
        return persistence;
    }
}
