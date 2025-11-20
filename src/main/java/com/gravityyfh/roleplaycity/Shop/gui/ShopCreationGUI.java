package com.gravityyfh.roleplaycity.shop.gui;

import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.gravityyfh.roleplaycity.entreprise.model.*;
import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.shop.manager.ShopManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * GUI pour créer une nouvelle boutique
 * Utilise le chat pour guider le joueur étape par étape
 */
public class ShopCreationGUI implements Listener {
    private final RoleplayCity plugin;
    private final ShopManager shopManager;

    // Contextes de création
    private final Map<UUID, ShopCreationContext> creationContexts = new HashMap<>();

    public ShopCreationGUI(RoleplayCity plugin, ShopManager shopManager) {
        this.plugin = plugin;
        this.shopManager = shopManager;
    }

    /**
     * Démarre le processus de création de boutique
     */
    public void openCreationMenu(Player player, Entreprise entreprise) {
        // Vérifier le nombre de boutiques
        int currentShops = shopManager.getShopsBySiret(entreprise.getSiret()).size();
        int maxShops = plugin.getConfig().getInt("shop-system.max-shops-per-entreprise", 10);

        if (currentShops >= maxShops) {
            player.sendMessage(ChatColor.RED + "✗ Limite atteinte!");
            player.sendMessage(ChatColor.GRAY + "Votre entreprise a déjà " + currentShops + "/" + maxShops + " boutiques.");
            return;
        }

        // Vérifier les fonds
        double cost = plugin.getConfig().getDouble("shop-system.creation-cost", 5000.0);
        if (entreprise.getSolde() < cost) {
            player.sendMessage(ChatColor.RED + "✗ Fonds insuffisants!");
            player.sendMessage(ChatColor.GRAY + "Coût de création: " + ChatColor.GOLD + cost + "€");
            player.sendMessage(ChatColor.GRAY + "Solde actuel: " + ChatColor.GOLD + entreprise.getSolde() + "€");
            return;
        }

        // VALIDATION 1: Vérifier le terrain IMMÉDIATEMENT
        String terrainError = validateTerrainAtStart(player, entreprise);
        if (terrainError != null) {
            player.sendMessage(ChatColor.RED + "✗ " + terrainError);
            player.sendMessage(ChatColor.YELLOW + "→ Déplacez-vous sur votre terrain PROFESSIONNEL puis réessayez.");
            return;
        }

        player.sendMessage(ChatColor.DARK_AQUA + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "CRÉATION DE BOUTIQUE");
        player.sendMessage(ChatColor.DARK_AQUA + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "📦 Étape 1/3: Sélection de l'item");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Tenez dans votre main l'item que vous");
        player.sendMessage(ChatColor.GRAY + "souhaitez vendre, puis tapez " + ChatColor.WHITE + "ok");
        player.sendMessage("");
        player.sendMessage(ChatColor.DARK_GRAY + "Tapez " + ChatColor.RED + "annuler" + ChatColor.DARK_GRAY + " pour abandonner");

        // Créer le contexte
        ShopCreationContext context = new ShopCreationContext(entreprise);
        context.step = CreationStep.ITEM_SELECTION;
        creationContexts.put(player.getUniqueId(), context);
    }

    /**
     * Gère les messages du chat pendant la création
     */
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        ShopCreationContext context = creationContexts.get(player.getUniqueId());

        if (context == null) return;

        event.setCancelled(true);
        String message = event.getMessage().trim();

        // Annulation
        if (message.equalsIgnoreCase("annuler") || message.equalsIgnoreCase("cancel")) {
            creationContexts.remove(player.getUniqueId());
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.sendMessage(ChatColor.RED + "✗ Création de boutique annulée.");
            });
            return;
        }

        switch (context.step) {
            case ITEM_SELECTION:
                handleItemSelection(player, context, message);
                break;

            case PRICE_INPUT:
                handlePriceInput(player, context, message);
                break;

            case QUANTITY_INPUT:
                handleQuantityInput(player, context, message);
                break;
        }
    }

    /**
     * Étape 1: Sélection de l'item
     */
    private void handleItemSelection(Player player, ShopCreationContext context, String message) {
        if (!message.equalsIgnoreCase("ok")) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.sendMessage(ChatColor.RED + "Tapez 'ok' pour confirmer l'item.");
            });
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ItemStack handItem = player.getInventory().getItemInMainHand();

            if (handItem == null || handItem.getType() == Material.AIR) {
                player.sendMessage(ChatColor.RED + "✗ Vous devez tenir un item dans votre main!");
                player.sendMessage(ChatColor.GRAY + "Tapez " + ChatColor.WHITE + "ok" + ChatColor.GRAY + " une fois prêt.");
                return;
            }

            context.itemTemplate = handItem.clone();
            context.itemTemplate.setAmount(1);

            // VALIDATION 2: Vérifier que l'item est autorisé pour cette entreprise
            String itemError = validateItemAllowed(context.entreprise, context.itemTemplate);
            if (itemError != null) {
                player.sendMessage("");
                player.sendMessage(ChatColor.RED + "✗ " + itemError);
                player.sendMessage("");
                player.sendMessage(ChatColor.YELLOW + "→ Tenez un autre item puis tapez " + ChatColor.WHITE + "ok");
                return;
            }

            context.step = CreationStep.PRICE_INPUT;

            String itemName = handItem.hasItemMeta() && handItem.getItemMeta().hasDisplayName()
                ? handItem.getItemMeta().getDisplayName()
                : formatMaterialName(handItem.getType());

            player.sendMessage("");
            player.sendMessage(ChatColor.GREEN + "✓ Item sélectionné: " + itemName);
            player.sendMessage("");
            player.sendMessage(ChatColor.YELLOW + "💰 Étape 2/3: Prix de vente");
            player.sendMessage("");
            player.sendMessage(ChatColor.GRAY + "Entrez le prix de vente unitaire");
            player.sendMessage(ChatColor.GRAY + "Exemple: " + ChatColor.WHITE + "100" + ChatColor.GRAY + " pour 100€");
            player.sendMessage("");
            player.sendMessage(ChatColor.DARK_GRAY + "Tapez " + ChatColor.RED + "annuler" + ChatColor.DARK_GRAY + " pour abandonner");
        });
    }

    /**
     * Étape 2: Prix
     */
    private void handlePriceInput(Player player, ShopCreationContext context, String message) {
        try {
            double price = Double.parseDouble(message);

            if (price <= 0) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatColor.RED + "✗ Le prix doit être supérieur à 0!");
                });
                return;
            }

            if (price > 1000000) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatColor.RED + "✗ Le prix ne peut pas dépasser 1,000,000€!");
                });
                return;
            }

            context.price = price;
            context.step = CreationStep.QUANTITY_INPUT;

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.sendMessage("");
                player.sendMessage(ChatColor.GREEN + "✓ Prix défini: " + ChatColor.GOLD + price + "€");
                player.sendMessage("");
                player.sendMessage(ChatColor.YELLOW + "📊 Étape 3/3: Quantité par vente");
                player.sendMessage("");
                player.sendMessage(ChatColor.GRAY + "Combien d'items seront vendus par achat?");
                player.sendMessage(ChatColor.GRAY + "Exemple: " + ChatColor.WHITE + "64" + ChatColor.GRAY + " pour une stack complète");
                player.sendMessage("");
                player.sendMessage(ChatColor.DARK_GRAY + "Tapez " + ChatColor.RED + "annuler" + ChatColor.DARK_GRAY + " pour abandonner");
            });

        } catch (NumberFormatException e) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.sendMessage(ChatColor.RED + "✗ Veuillez entrer un nombre valide!");
            });
        }
    }

    /**
     * Étape 3: Quantité et finalisation
     */
    private void handleQuantityInput(Player player, ShopCreationContext context, String message) {
        try {
            int quantity = Integer.parseInt(message);

            if (quantity <= 0) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatColor.RED + "✗ La quantité doit être supérieure à 0!");
                });
                return;
            }

            if (quantity > 2304) { // 36 stacks de 64
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatColor.RED + "✗ La quantité ne peut pas dépasser 2304!");
                });
                return;
            }

            context.quantity = quantity;

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                // VALIDATION 3: Re-vérifier terrain et limites avant placement
                String revalidationError = revalidateBeforePlacement(player, context.entreprise);
                if (revalidationError != null) {
                    player.sendMessage("");
                    player.sendMessage(ChatColor.RED + "✗ " + revalidationError);
                    String advice = revalidationError.contains("terrain")
                        ? "Retournez sur votre terrain PROFESSIONNEL puis recommencez."
                        : "Vérifiez vos boutiques existantes.";
                    player.sendMessage(ChatColor.YELLOW + "→ " + advice);
                    creationContexts.remove(player.getUniqueId());
                    return;
                }

                player.sendMessage("");
                player.sendMessage(ChatColor.GREEN + "✓ Quantité définie: " + ChatColor.YELLOW + quantity);
                player.sendMessage("");
                player.sendMessage(ChatColor.YELLOW + "📋 Récapitulatif:");
                player.sendMessage(ChatColor.GRAY + "  • Item: " + formatItemName(context.itemTemplate));
                player.sendMessage(ChatColor.GRAY + "  • Prix: " + ChatColor.GOLD + context.price + "€");
                player.sendMessage(ChatColor.GRAY + "  • Quantité: " + ChatColor.YELLOW + context.quantity);
                player.sendMessage("");

                // Démarrer le placement via le PlacementListener
                if (plugin.getShopPlacementListener() != null) {
                    plugin.getShopPlacementListener().startPlacement(
                        player,
                        context.entreprise,
                        context.itemTemplate,
                        context.price,
                        context.quantity
                    );
                } else {
                    player.sendMessage(ChatColor.RED + "✗ Erreur: Système de placement non disponible.");
                }

                // Nettoyer le contexte
                creationContexts.remove(player.getUniqueId());
            });

        } catch (NumberFormatException e) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.sendMessage(ChatColor.RED + "✗ Veuillez entrer un nombre entier valide!");
            });
        }
    }

    /**
     * Formate le nom d'un item
     */
    private String formatItemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return formatMaterialName(item.getType());
    }

    /**
     * Formate le nom d'un matériau
     */
    private String formatMaterialName(Material material) {
        String name = material.name().replace("_", " ");
        String[] words = name.split(" ");
        StringBuilder formatted = new StringBuilder();

        for (String word : words) {
            if (formatted.length() > 0) formatted.append(" ");
            formatted.append(word.substring(0, 1).toUpperCase());
            formatted.append(word.substring(1).toLowerCase());
        }

        return formatted.toString();
    }

    /**
     * Nettoie le contexte d'un joueur
     */
    public void cleanupPlayerContext(UUID playerId) {
        creationContexts.remove(playerId);
    }

    /**
     * Contexte de création de boutique
     */
    private static class ShopCreationContext {
        final Entreprise entreprise;
        CreationStep step;
        ItemStack itemTemplate;
        double price;
        int quantity;

        ShopCreationContext(Entreprise entreprise) {
            this.entreprise = entreprise;
        }
    }

    /**
     * VALIDATION 1: Vérifier le terrain au démarrage
     * Retourne un message d'erreur ou null si valide
     */
    private String validateTerrainAtStart(Player player, Entreprise entreprise) {
        Location location = player.getLocation();

        // Vérifier le ClaimManager
        if (plugin.getClaimManager() == null) {
            return "Système de terrains non disponible";
        }

        // Récupérer le plot
        com.gravityyfh.roleplaycity.town.data.Plot plot = plugin.getClaimManager().getPlotAt(location);
        if (plot == null) {
            return "Vous n'êtes pas dans une ville. Déplacez-vous sur un terrain PROFESSIONNEL de votre entreprise.";
        }

        // Vérifier le type de terrain
        if (plot.getType() != com.gravityyfh.roleplaycity.town.data.PlotType.PROFESSIONNEL) {
            return "Ce terrain est de type " + plot.getType().getDisplayName() + ". Vous devez être sur un terrain PROFESSIONNEL.";
        }

        // Vérifier que le terrain appartient à l'entreprise
        String plotSiret = plot.getCompanySiret();
        if (plotSiret == null || !plotSiret.equals(entreprise.getSiret())) {
            return "Ce terrain n'appartient pas à votre entreprise.";
        }

        // Vérifier que le joueur est propriétaire ou locataire
        UUID playerUuid = player.getUniqueId();
        if (!plot.isOwnedBy(playerUuid) && !plot.isRentedBy(playerUuid)) {
            return "Vous devez être propriétaire ou locataire de ce terrain.";
        }

        // Vérifier les permissions de construction
        com.gravityyfh.roleplaycity.town.data.Town town = plugin.getTownManager().getTown(plot.getTownName());
        if (town == null) {
            return "Ville introuvable";
        }

        if (!plot.canBuild(playerUuid, town)) {
            return "Vous n'avez pas la permission de construire sur ce terrain.";
        }

        return null; // Tout est OK
    }

    /**
     * VALIDATION 2: Vérifier que l'item est autorisé pour cette entreprise
     * Retourne un message d'erreur ou null si valide
     */
    private String validateItemAllowed(Entreprise entreprise, ItemStack itemToSell) {
        if (itemToSell == null) {
            return "Item invalide";
        }

        // Vérifier si c'est un backpack
        if (plugin.getBackpackItemManager() != null && plugin.getBackpackItemManager().isBackpack(itemToSell)) {
            com.gravityyfh.roleplaycity.backpack.model.BackpackType backpackType =
                plugin.getBackpackItemManager().getBackpackType(itemToSell);

            if (backpackType == null) {
                return "Type de backpack invalide";
            }

            // Vérifier via EntrepriseManagerLogic
            if (!plugin.getEntrepriseManagerLogic().isBackpackAllowedInShop(entreprise.getType(), backpackType.getId())) {
                return "Votre entreprise (" + entreprise.getType() + ") n'est pas autorisée à vendre ce backpack.";
            }
        } else {
            // Item standard
            if (!plugin.getEntrepriseManagerLogic().isItemAllowedInShop(entreprise.getType(), itemToSell.getType())) {
                return "Votre entreprise (" + entreprise.getType() + ") n'est pas autorisée à vendre cet item.";
            }
        }

        return null; // Item autorisé
    }

    /**
     * VALIDATION 3: Re-vérifier tout avant le placement final
     * Retourne un message d'erreur ou null si valide
     */
    private String revalidateBeforePlacement(Player player, Entreprise entreprise) {
        // Re-vérifier le terrain (le joueur a pu se déplacer)
        String terrainError = validateTerrainAtStart(player, entreprise);
        if (terrainError != null) {
            return terrainError;
        }

        // Re-vérifier la limite de boutiques
        int currentShops = shopManager.getShopsBySiret(entreprise.getSiret()).size();
        int maxShops = plugin.getConfig().getInt("shop-system.max-shops-per-entreprise", 10);

        if (currentShops >= maxShops) {
            return "Limite de boutiques atteinte (" + currentShops + "/" + maxShops + ")";
        }

        return null; // Tout est OK
    }

    /**
     * Étapes de création
     */
    private enum CreationStep {
        ITEM_SELECTION,
        PRICE_INPUT,
        QUANTITY_INPUT
    }
}
