package com.gravityyfh.roleplaycity.shop.listener;

import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.gravityyfh.roleplaycity.EntrepriseManagerLogic.Entreprise;
import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.shop.ComponentType;
import com.gravityyfh.roleplaycity.shop.PurchaseResult;
import com.gravityyfh.roleplaycity.shop.ShopStatus;
import com.gravityyfh.roleplaycity.shop.manager.ShopManager;
import com.gravityyfh.roleplaycity.shop.model.Shop;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Gestionnaire d'événements pour les interactions avec les boutiques
 */
public class ShopInteractionHandler implements Listener {
    private final RoleplayCity plugin;
    private final ShopManager shopManager;
    private final EntrepriseManagerLogic entrepriseLogic;
    private final DecimalFormat priceFormat = new DecimalFormat("#,##0.00");

    // Système de confirmation pour la destruction de coffres
    private final Map<UUID, ConfirmationData> pendingConfirmations = new HashMap<>();
    private static final long CONFIRMATION_TIMEOUT_MS = 30000; // 30 secondes

    public ShopInteractionHandler(RoleplayCity plugin, ShopManager shopManager,
                                   EntrepriseManagerLogic entrepriseLogic) {
        this.plugin = plugin;
        this.shopManager = shopManager;
        this.entrepriseLogic = entrepriseLogic;
    }

    // ===== ACHAT D'ITEMS =====

    @EventHandler(priority = EventPriority.HIGH)
    public void onSignClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Sign)) {
            return;
        }

        // Récupérer le shop
        Optional<Shop> shopOpt = shopManager.getShopBySignLocation(block.getLocation());
        if (!shopOpt.isPresent()) {
            return;
        }

        Shop shop = shopOpt.get();
        Player buyer = event.getPlayer();
        event.setCancelled(true); // Empêcher l'édition du panneau

        // Vérifier que le shop est actif
        if (shop.getStatus() != ShopStatus.ACTIVE) {
            buyer.sendMessage(ChatColor.RED + "Cette boutique est actuellement fermée.");
            buyer.playSound(buyer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
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

                buyer.playSound(buyer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

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

                // Notifier le propriétaire
                notifyOutOfStock(shop);
                break;

            case INVENTORY_FULL:
                buyer.sendMessage(ChatColor.RED + "✗ Votre inventaire est plein.");
                buyer.playSound(buyer.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                break;

            case SHOP_BROKEN:
                buyer.sendMessage(ChatColor.RED + "✗ Cette boutique est endommagée.");
                plugin.getLogger().warning("[ShopSystem] Shop " + shop.getShopId() +
                    " est cassé lors d'une tentative d'achat");

                shopManager.deleteShop(shop, "Coffre manquant détecté lors d'un achat", null);
                break;

            case COOLDOWN_ACTIVE:
                // Pas de message (anti-spam)
                break;
        }
    }

    // ===== PROTECTION CONTRE LA DESTRUCTION =====

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        Location location = block.getLocation();

        // Vérifier si c'est un composant de shop
        Optional<Shop> shopOpt = findShopByComponent(location);
        if (!shopOpt.isPresent()) {
            return;
        }

        Shop shop = shopOpt.get();
        ComponentType component = getComponentType(shop, location);

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

        // COFFRE: Confirmation requise
        if (component == ComponentType.CHEST) {
            String confirmationKey = "SHOP_DELETE_" + shop.getShopId();

            if (!hasPendingConfirmation(player.getUniqueId(), confirmationKey)) {
                event.setCancelled(true);

                player.sendMessage(ChatColor.YELLOW + "⚠ ATTENTION: Détruire ce coffre supprimera définitivement la boutique!");
                player.sendMessage(ChatColor.YELLOW + "Recliquez pour confirmer la suppression.");

                requestConfirmation(player.getUniqueId(), confirmationKey);
                return;
            }

            // Confirmation validée → Suppression du shop
            shopManager.deleteShop(shop, "Coffre détruit par " + player.getName(), player);

            player.sendMessage(ChatColor.GREEN + "✓ Boutique supprimée.");
            plugin.getLogger().info("[ShopSystem] Shop " + shop.getShopId() +
                " supprimé par " + player.getName() + " (destruction du coffre)");
        }

        // PANNEAU: Laisser détruire mais logger
        else if (component == ComponentType.SIGN) {
            event.setCancelled(false);

            player.sendMessage(ChatColor.YELLOW + "Le panneau a été retiré. La boutique reste active.");
            player.sendMessage(ChatColor.GRAY + "Utilisez /entreprise shop repair pour recréer le panneau.");

            plugin.getLogger().info("[ShopSystem] Panneau du shop " + shop.getShopId() +
                " détruit par " + player.getName());
        }
    }

    // ===== SYNCHRONISATION APRÈS MODIFICATION DU COFFRE =====

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Chest chest)) {
            return;
        }

        Optional<Shop> shopOpt = shopManager.getShopByChestLocation(chest.getLocation());

        if (!shopOpt.isPresent()) {
            return;
        }

        Shop shop = shopOpt.get();

        // Compter le nouveau stock
        int newStock = shopManager.getValidator().countItemsInChest(shop);

        // Mettre à jour le statut
        if (newStock > 0 && shop.getStatus() == ShopStatus.OUT_OF_STOCK) {
            shop.setStatus(ShopStatus.ACTIVE);
            shopManager.getComponents().updateComponents(shop);

            if (event.getPlayer() instanceof Player) {
                event.getPlayer().sendMessage(ChatColor.GREEN +
                    "✓ Stock rechargé, la boutique est à nouveau ouverte.");
            }
        } else if (newStock == 0 && shop.getStatus() == ShopStatus.ACTIVE) {
            shop.setStatus(ShopStatus.OUT_OF_STOCK);
            shopManager.getComponents().updateComponents(shop);

            if (event.getPlayer() instanceof Player) {
                event.getPlayer().sendMessage(ChatColor.RED +
                    "⚠ Stock vide, la boutique est fermée.");
            }
        } else {
            // Mettre à jour l'affichage (panneau ET hologramme)
            shopManager.getComponents().updateSign(shop);
            shopManager.getComponents().updateHologram(shop);
        }

        shop.setLastStockCheck(java.time.LocalDateTime.now());
    }

    // ===== MÉTHODES UTILITAIRES =====

    private Optional<Shop> findShopByComponent(Location location) {
        // Vérifier coffre
        Optional<Shop> shopOpt = shopManager.getShopByChestLocation(location);
        if (shopOpt.isPresent()) {
            return shopOpt;
        }

        // Vérifier panneau
        shopOpt = shopManager.getShopBySignLocation(location);
        if (shopOpt.isPresent()) {
            return shopOpt;
        }

        return Optional.empty();
    }

    private ComponentType getComponentType(Shop shop, Location location) {
        if (shop.getChestLocation().equals(location)) {
            return ComponentType.CHEST;
        } else if (shop.getSignLocation().equals(location)) {
            return ComponentType.SIGN;
        }
        return null;
    }

    private boolean isGerantOfEntreprise(Player player, String siret) {
        Entreprise entreprise = entrepriseLogic.getEntrepriseBySiret(siret);
        if (entreprise == null) {
            return false;
        }

        // Vérifier si le joueur est le gérant (par UUID)
        return entreprise.getGerantUUID() != null &&
               entreprise.getGerantUUID().equals(player.getUniqueId().toString());
    }

    private void notifyOwner(Shop shop, Player buyer, PurchaseResult result) {
        boolean notifyEnabled = plugin.getConfig().getBoolean("shop-system.notify-owner-on-purchase", true);
        if (!notifyEnabled) {
            return;
        }

        Player owner = Bukkit.getPlayer(shop.getOwnerUUID());
        if (owner != null && owner.isOnline() && !owner.equals(buyer)) {
            // Message discret dans l'action bar
            owner.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                new TextComponent(ChatColor.GREEN + "💰 Vente: " +
                    result.getQuantity() + "x → +" +
                    formatPrice(result.getPrice()) + "€")
            );
        }
    }

    private void notifyOutOfStock(Shop shop) {
        boolean notifyEnabled = plugin.getConfig().getBoolean("shop-system.notify-owner-on-out-of-stock", true);
        if (!notifyEnabled) {
            return;
        }

        Player owner = Bukkit.getPlayer(shop.getOwnerUUID());
        if (owner != null && owner.isOnline()) {
            owner.sendMessage(ChatColor.RED + "⚠ Votre boutique est en rupture de stock!");
            owner.sendMessage(ChatColor.GRAY + "Shop: " + shop.getShopId().toString().substring(0, 8));
            owner.playSound(owner.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
        }
    }

    private String formatItemName(ItemStack item) {
        if (item == null) {
            return "?";
        }

        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }

        String materialName = item.getType().name().replace("_", " ");
        String[] words = materialName.split(" ");
        StringBuilder formatted = new StringBuilder();

        for (String word : words) {
            if (formatted.length() > 0) {
                formatted.append(" ");
            }
            formatted.append(word.substring(0, 1).toUpperCase());
            formatted.append(word.substring(1).toLowerCase());
        }

        return formatted.toString();
    }

    private String formatPrice(double price) {
        return priceFormat.format(price);
    }

    // ===== SYSTÈME DE CONFIRMATION =====

    private void requestConfirmation(UUID playerId, String key) {
        pendingConfirmations.put(playerId, new ConfirmationData(key, System.currentTimeMillis()));
    }

    private boolean hasPendingConfirmation(UUID playerId, String key) {
        ConfirmationData data = pendingConfirmations.get(playerId);
        if (data == null) {
            return false;
        }

        // Vérifier le timeout
        if (System.currentTimeMillis() - data.timestamp > CONFIRMATION_TIMEOUT_MS) {
            pendingConfirmations.remove(playerId);
            return false;
        }

        if (data.key.equals(key)) {
            pendingConfirmations.remove(playerId);
            return true;
        }

        return false;
    }

    private record ConfirmationData(String key, long timestamp) {
    }
}
