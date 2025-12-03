package com.gravityyfh.roleplaycity.shop.components;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.shop.model.Shop;
import com.gravityyfh.roleplaycity.shop.validation.ShopValidator;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Gestionnaire des hologrammes de boutique
 * Crée et met à jour les hologrammes avec ItemDisplay et TextDisplay
 */
public class ShopHologramManager {
    private final RoleplayCity plugin;
    private final ShopValidator validator;
    private final DecimalFormat priceFormat = new DecimalFormat("#,##0.00");

    public ShopHologramManager(RoleplayCity plugin, ShopValidator validator) {
        this.plugin = plugin;
        this.validator = validator;
    }

    // Méthodes pour récupérer les valeurs de config à la volée (rechargeable)
    private boolean isHologramEnabled() {
        return plugin.getConfig().getBoolean("shop-system.hologram-enabled", true);
    }

    private float getHologramViewRange() {
        return (float) plugin.getConfig().getDouble("shop-system.hologram-view-range", 32.0);
    }

    private boolean isRotationEnabled() {
        return plugin.getConfig().getBoolean("shop-system.hologram-rotation-enabled", true);
    }

    private double getBaseHeight() {
        return plugin.getConfig().getDouble("shop-holograms.base-height", 2.2);
    }

    private double getLineSpacing() {
        return plugin.getConfig().getDouble("shop-holograms.line-spacing", 0.25);
    }

    private double getItemDisplayScale() {
        return plugin.getConfig().getDouble("shop-holograms.item-display.scale", 0.8);
    }

    private double getItemDisplayOffset() {
        return plugin.getConfig().getDouble("shop-holograms.item-display.offset-below", 0.5);
    }

    private float getTextScale() {
        return (float) plugin.getConfig().getDouble("shop-system.hologram-text-scale", 0.6);
    }

    /**
     * Crée ou met à jour l'hologramme d'une boutique
     */
    public void createOrUpdateHologram(Shop shop) {
        if (!isHologramEnabled()) {
            return;
        }

        // Supprimer l'ancien hologramme si existe
        removeHologram(shop);

        Location hologramLoc = shop.getHologramLocation();
        World world = hologramLoc.getWorld();

        if (world == null) {
            plugin.getLogger().warning("[ShopSystem] World null pour hologramme du shop " + shop.getShopId());
            return;
        }

        try {
            List<UUID> textEntityIds = new ArrayList<>();

            // Calculer la position de base en utilisant baseHeight du config
            double currentHeight = getBaseHeight();

            // === LIGNE 1: Display Item (ItemDisplay) ===
            // Position de l'item = baseHeight + itemDisplayOffset
            ItemDisplay displayItem = (ItemDisplay) world.spawnEntity(
                hologramLoc.clone().add(0, currentHeight + getItemDisplayOffset(), 0),
                EntityType.ITEM_DISPLAY
            );
            displayItem.setItemStack(shop.getItemTemplate());
            displayItem.setBillboard(Display.Billboard.VERTICAL);
            displayItem.setViewRange(getHologramViewRange());
            displayItem.setPersistent(false); // Ne pas sauvegarder dans le monde
            displayItem.setInvulnerable(true);
            displayItem.setGravity(false);

            // Appliquer la taille configurée
            double itemScale = getItemDisplayScale();
            if (itemScale != 1.0) {
                try {
                    Transformation transform = displayItem.getTransformation();
                    float scale = (float) itemScale;
                    org.joml.Vector3f scaleVec = new org.joml.Vector3f(scale, scale, scale);
                    Transformation newTransform = new Transformation(
                        transform.getTranslation(),
                        transform.getLeftRotation(),
                        scaleVec,
                        transform.getRightRotation()
                    );
                    displayItem.setTransformation(newTransform);
                } catch (Exception e) {
                    plugin.getLogger().fine("[ShopSystem] Impossible d'appliquer la taille de l'hologramme");
                }
            }

            // Rotation initiale si activée
            if (isRotationEnabled()) {
                try {
                    Transformation transform = displayItem.getTransformation();
                    // Rotation minimale pour initialiser l'animation
                    displayItem.setTransformation(transform);
                } catch (Exception e) {
                    plugin.getLogger().fine("[ShopSystem] Impossible d'initialiser la rotation de l'hologramme");
                }
            }

            // Descendre pour les lignes de texte
            double spacing = getLineSpacing();
            currentHeight -= spacing;

            // === LIGNE 1: Séparateur supérieur ===
            TextDisplay topSeparator = createTextDisplay(
                world,
                hologramLoc.clone().add(0, currentHeight, 0),
                ChatColor.DARK_GRAY + "━━━━━━━━━━━━━"
            );
            textEntityIds.add(topSeparator.getUniqueId());
            currentHeight -= spacing;

            // === LIGNE 2: Nom de l'item avec style moderne ===
            TextDisplay itemName = createTextDisplay(
                world,
                hologramLoc.clone().add(0, currentHeight, 0),
                ChatColor.WHITE + "▸ " + formatItemName(shop.getItemTemplate()) + ChatColor.WHITE + " ◂"
            );
            textEntityIds.add(itemName.getUniqueId());
            currentHeight -= spacing;

            // === LIGNE 3: Séparateur fin ===
            TextDisplay midSeparator = createTextDisplay(
                world,
                hologramLoc.clone().add(0, currentHeight, 0),
                ChatColor.DARK_GRAY + "─────────────"
            );
            textEntityIds.add(midSeparator.getUniqueId());
            currentHeight -= spacing;

            // === LIGNE 4: Prix + Quantité sur la même ligne ===
            String priceAndQuantity = ChatColor.GOLD + "💰 " + ChatColor.YELLOW + ChatColor.BOLD +
                formatPrice(shop.getPricePerSale()) + "€" +
                ChatColor.DARK_GRAY + " • " +
                ChatColor.AQUA + "×" + shop.getQuantityPerSale();
            TextDisplay priceQuantityText = createTextDisplay(
                world,
                hologramLoc.clone().add(0, currentHeight, 0),
                priceAndQuantity
            );
            textEntityIds.add(priceQuantityText.getUniqueId());
            currentHeight -= spacing;

            // === LIGNE 5: Stock avec indicateur visuel ===
            int rawStock = validator.countRawItemsInChest(shop); // Nombre total d'items
            int availableLots = validator.countItemsInChest(shop); // Nombre de lots achetables
            String stockText;
            if (availableLots > 0) {
                // Afficher le nombre total d'items en stock
                String formattedStock = formatStock(rawStock);
                stockText = ChatColor.GREEN + "📦 " + ChatColor.WHITE + formattedStock + ChatColor.GRAY + " en stock";
            } else if (rawStock > 0) {
                // Il reste des items mais pas assez pour un lot
                stockText = ChatColor.RED + "⚠ " + ChatColor.DARK_RED + "Stock insuffisant " +
                    ChatColor.GRAY + "(" + rawStock + "/" + shop.getQuantityPerSale() + ")";
            } else {
                stockText = ChatColor.RED + "✗ " + ChatColor.DARK_RED + ChatColor.BOLD + "RUPTURE DE STOCK";
            }
            TextDisplay stockDisplay = createTextDisplay(
                world,
                hologramLoc.clone().add(0, currentHeight, 0),
                stockText
            );
            textEntityIds.add(stockDisplay.getUniqueId());
            currentHeight -= spacing;

            // === LIGNE 6: Call-to-action ===
            TextDisplay ctaText = createTextDisplay(
                world,
                hologramLoc.clone().add(0, currentHeight, 0),
                ChatColor.GRAY + "🛒 " + ChatColor.WHITE + "Clic droit » acheter "
            );
            textEntityIds.add(ctaText.getUniqueId());

            // Sauvegarder les IDs
            shop.setDisplayItemEntityId(displayItem.getUniqueId());
            shop.setHologramTextEntityIds(textEntityIds);

        } catch (Exception e) {
            plugin.getLogger().warning("[ShopSystem] Erreur lors de la création de l'hologramme: " + e.getMessage());
        }
    }

    /**
     * Supprime l'hologramme d'une boutique
     */
    public void removeHologram(Shop shop) {
        // Méthode 1: Supprimer par UUID (si les entités existent encore)
        int removedByUuid = 0;

        if (shop.getDisplayItemEntityId() != null) {
            Entity entity = Bukkit.getEntity(shop.getDisplayItemEntityId());
            if (entity != null) {
                entity.remove();
                removedByUuid++;
            }
        }

        if (shop.getHologramTextEntityIds() != null) {
            for (UUID entityId : shop.getHologramTextEntityIds()) {
                Entity entity = Bukkit.getEntity(entityId);
                if (entity != null) {
                    entity.remove();
                    removedByUuid++;
                }
            }
        }

        // Méthode 2: Nettoyage par position (pour les entités orphelines après reload/restart)
        // Scanner la zone autour de l'hologramme et supprimer tous les ItemDisplay/TextDisplay
        Location hologramLoc = shop.getHologramLocation();
        if (hologramLoc.getWorld() != null && hologramLoc.getChunk().isLoaded()) {
            int removedByLocation = cleanupHologramArea(hologramLoc);

            if (removedByLocation > 0) {
                plugin.getLogger().fine("[ShopSystem] Nettoyage zone hologramme shop " +
                    shop.getShopId() + ": " + removedByLocation + " entité(s) orpheline(s) supprimée(s)");
            }
        }

        // Réinitialiser les IDs stockés
        shop.setDisplayItemEntityId(null);
        shop.setHologramTextEntityIds(new java.util.ArrayList<>());
    }

    /**
     * Nettoie toutes les entités d'hologramme (ItemDisplay, TextDisplay) dans une zone
     * @param center Centre de la zone à nettoyer
     * @return Nombre d'entités supprimées
     */
    private int cleanupHologramArea(Location center) {
        int removed = 0;
        World world = center.getWorld();

        if (world == null) {
            return 0;
        }

        // Scanner un rayon de 2 blocs autour de l'hologramme (couvre toute la hauteur)
        double radius = 2.0;

        for (Entity entity : world.getNearbyEntities(center, radius, 4.0, radius)) {
            // Supprimer uniquement les ItemDisplay et TextDisplay
            if (entity instanceof org.bukkit.entity.ItemDisplay ||
                entity instanceof org.bukkit.entity.TextDisplay) {
                entity.remove();
                removed++;
            }
        }

        return removed;
    }

    /**
     * Démarre la tâche de rotation des items
     */
    public void startRotationTask(List<Shop> shops) {
        if (!isRotationEnabled()) {
            return;
        }

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Shop shop : shops) {
                if (shop.getDisplayItemEntityId() == null) {
                    continue;
                }

                Entity entity = Bukkit.getEntity(shop.getDisplayItemEntityId());
                if (entity instanceof ItemDisplay display) {
                    try {
                        Transformation transform = display.getTransformation();

                        // Créer une rotation incrémentielle autour de l'axe Y
                        org.joml.Quaternionf currentRotation = new org.joml.Quaternionf(transform.getLeftRotation());
                        currentRotation.rotateY((float) Math.toRadians(2)); // 2 degrés par tick

                        // Appliquer la nouvelle rotation
                        Transformation newTransform = new Transformation(
                            transform.getTranslation(),
                            currentRotation,
                            transform.getScale(),
                            transform.getRightRotation()
                        );
                        display.setTransformation(newTransform);
                    } catch (Exception e) {
                        // Ignorer les erreurs silencieusement
                    }
                }
            }
        }, 1L, 1L); // Chaque tick pour une rotation fluide
    }

    /**
     * Crée un TextDisplay
     */
    private TextDisplay createTextDisplay(World world, Location location, String text) {
        TextDisplay textDisplay = (TextDisplay) world.spawnEntity(
            location,
            EntityType.TEXT_DISPLAY
        );

        // Utiliser l'API directe setText au lieu de text()
        try {
            // Tenter d'utiliser l'API moderne
            Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
            java.lang.reflect.Method textMethod = componentClass.getMethod("text", String.class);
            Object component = textMethod.invoke(null, text);

            java.lang.reflect.Method setTextMethod = TextDisplay.class.getMethod("text", componentClass);
            setTextMethod.invoke(textDisplay, component);
        } catch (Exception e) {
            // Fallback : utiliser une méthode alternative ou ignorer
            plugin.getLogger().fine("[ShopSystem] Utilisation de l'API TextDisplay alternative");
        }

        textDisplay.setBillboard(Display.Billboard.VERTICAL);
        textDisplay.setAlignment(TextDisplay.TextAlignment.CENTER);
        textDisplay.setBackgroundColor(Color.fromARGB(0, 0, 0, 0)); // Transparent
        textDisplay.setShadowed(true);
        textDisplay.setViewRange(getHologramViewRange());
        textDisplay.setPersistent(false);
        textDisplay.setInvulnerable(true);
        textDisplay.setGravity(false);

        // Appliquer la taille configurée du texte
        float textScaleValue = getTextScale();
        if (textScaleValue != 1.0f) {
            try {
                Transformation transform = textDisplay.getTransformation();
                org.joml.Vector3f scaleVec = new org.joml.Vector3f(textScaleValue, textScaleValue, textScaleValue);
                Transformation newTransform = new Transformation(
                    transform.getTranslation(),
                    transform.getLeftRotation(),
                    scaleVec,
                    transform.getRightRotation()
                );
                textDisplay.setTransformation(newTransform);
            } catch (Exception e) {
                plugin.getLogger().fine("[ShopSystem] Impossible d'appliquer la taille du texte de l'hologramme");
            }
        }

        return textDisplay;
    }

    /**
     * Formate le nom d'un item
     */
    private String formatItemName(ItemStack item) {
        if (item == null) {
            return "?";
        }

        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return ChatColor.YELLOW + item.getItemMeta().getDisplayName();
        }

        // Nom par défaut du matériau
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

        return ChatColor.YELLOW + formatted.toString();
    }

    /**
     * Formate le prix
     */
    private String formatPrice(double price) {
        return priceFormat.format(price);
    }

    /**
     * Formate le nombre d'items en stock avec séparateur de milliers
     */
    private String formatStock(int stock) {
        if (stock < 1000) {
            return String.valueOf(stock);
        }
        // Utiliser le format avec séparateurs de milliers
        DecimalFormat stockFormat = new DecimalFormat("#,###");
        return stockFormat.format(stock);
    }
}
