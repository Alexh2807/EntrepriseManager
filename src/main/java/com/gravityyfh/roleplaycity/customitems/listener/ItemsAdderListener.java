package com.gravityyfh.roleplaycity.customitems.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.customitems.action.ActionExecutor;
import com.gravityyfh.roleplaycity.customitems.manager.CustomItemManager;
import com.gravityyfh.roleplaycity.customitems.model.CustomItem;
import com.gravityyfh.roleplaycity.heist.data.Heist;
import com.gravityyfh.roleplaycity.heist.data.HeistPhase;
import com.gravityyfh.roleplaycity.heist.data.PlacedBomb;
import com.gravityyfh.roleplaycity.heist.gui.BombDefuseGUI;
import com.gravityyfh.roleplaycity.heist.manager.HeistManager;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownRole;
import dev.lone.itemsadder.api.CustomFurniture;
import dev.lone.itemsadder.api.Events.CustomBlockInteractEvent;
import dev.lone.itemsadder.api.Events.FurnitureInteractEvent;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Listener spécifique pour les événements ItemsAdder
 * Ne doit être chargé que si ItemsAdder est présent sur le serveur
 */
public class ItemsAdderListener implements Listener {

    private final RoleplayCity plugin;
    private final CustomItemManager manager;
    private final ActionExecutor executor;

    public ItemsAdderListener(RoleplayCity plugin, CustomItemManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.executor = new ActionExecutor(plugin);
    }

    /**
     * Gère les interactions avec les BLOCS custom ItemsAdder placés
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCustomBlockInteract(CustomBlockInteractEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();

        // Ne gérer que les clics droits
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        // Empêcher la double exécution (Main Hand uniquement)
        if (event.getHand() != EquipmentSlot.HAND) return;

        // Récupérer l'ID du bloc custom ItemsAdder
        String namespacedId = event.getNamespacedID();
        if (namespacedId == null) return;

        // Trouver le CustomItem correspondant
        CustomItem customItem = manager.getItemByItemsAdderId(namespacedId);
        if (customItem == null) return;

        // Vérifier si on a un trigger pour RIGHT_CLICK_PLACED_BLOCK
        if (!customItem.getTriggers().containsKey("RIGHT_CLICK_PLACED_BLOCK")) {
            return;
        }

        // Exécuter le trigger
        handleTrigger(customItem, "RIGHT_CLICK_PLACED_BLOCK", player, null);

        // Annuler l'événement pour empêcher l'interaction par défaut
        event.setCancelled(true);
    }

    /**
     * Gère les interactions avec les MEUBLES ItemsAdder (Furniture)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFurnitureInteract(FurnitureInteractEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();

        // Optionnel: autoriser Shift+Clic pour casser
        if (player.isSneaking()) return;

        // Récupérer le meuble
        if (event.getFurniture() == null) return;

        // Récupérer l'ID du meuble
        String namespacedId = event.getFurniture().getNamespacedID();
        if (namespacedId == null) return;

        // CAS SPÉCIAL: Bombe de cambriolage
        if ("my_items:bomb".equals(namespacedId)) {
            handleBombInteraction(event);
            return;
        }

        // Trouver le CustomItem correspondant
        CustomItem customItem = manager.getItemByItemsAdderId(namespacedId);
        if (customItem == null) return;

        // Vérifier si on a un trigger pour RIGHT_CLICK_PLACED_BLOCK
        if (!customItem.getTriggers().containsKey("RIGHT_CLICK_PLACED_BLOCK")) {
            return;
        }

        // Exécuter le trigger
        handleTrigger(customItem, "RIGHT_CLICK_PLACED_BLOCK", player, null);

        // Annuler l'événement pour empêcher l'interaction par défaut
        event.setCancelled(true);
    }

    /**
     * Gère l'interaction avec une bombe de cambriolage posée.
     * Système en 2 étapes: 1er clic = info, 2e clic = confirmation
     */
    private void handleBombInteraction(FurnitureInteractEvent event) {
        Player player = event.getPlayer();
        CustomFurniture furniture = event.getFurniture();

        HeistManager heistManager = plugin.getHeistManager();
        if (heistManager == null) {
            player.sendMessage(ChatColor.RED + "Le système de cambriolage n'est pas activé.");
            return;
        }

        UUID armorStandId;
        try {
            armorStandId = furniture.getArmorstand().getUniqueId();
        } catch (Exception e) {
            plugin.getLogger().warning("[ItemsAdderListener] Impossible de récupérer l'UUID de l'ArmorStand");
            return;
        }

        // 1. Vérifier si c'est une bombe POSÉE
        PlacedBomb placedBomb = heistManager.getPlacedBombByArmorStand(armorStandId);
        if (placedBomb != null) {
            event.setCancelled(true);

            // FIX: Si le timer est démarré → chercher le heist actif pour désamorçage par police
            if (placedBomb.isTimerStarted()) {
                Heist heist = heistManager.getActiveHeist(placedBomb.getPlotKey());
                if (heist == null && placedBomb.getLocation() != null) {
                    heist = heistManager.getActiveHeistAt(placedBomb.getLocation());
                }

                if (heist != null) {
                    // Heist actif trouvé → permettre désamorçage
                    handleHeistBombInteraction(player, heist);
                    return;
                }
                // Fallback: bombe armée mais pas de heist (explosion simple)
                handleArmedBombWithoutHeist(player, placedBomb, furniture);
                return;
            }

            // Timer pas démarré → confirmation par le poseur uniquement
            handleBombConfirmation(player, placedBomb, furniture);
            return;
        }

        // 2. Fallback: vérifier par entity ID du heist (pour compatibilité)
        Heist heist = heistManager.getHeistByBombEntity(armorStandId);
        if (heist != null) {
            event.setCancelled(true);
            handleHeistBombInteraction(player, heist);
            return;
        }

        // Bombe posée mais non enregistrée (cas anormal)
        player.sendMessage(ChatColor.GRAY + "Cette bombe n'est pas active.");
    }

    /**
     * Gère la confirmation d'une bombe posée (système en 2 étapes)
     */
    private void handleBombConfirmation(Player player, PlacedBomb bomb, CustomFurniture furniture) {
        HeistManager heistManager = plugin.getHeistManager();
        var config = heistManager.getConfig();

        // Vérifier que c'est le poseur
        if (!bomb.getPlacedByUuid().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Seul le poseur peut confirmer cette bombe.");
            return;
        }

        // Si pas encore en attente de confirmation → afficher le message détaillé
        if (!bomb.isAwaitingConfirmation()) {
            int countdownSeconds = config.getCountdownDuration();
            int robberySeconds = config.getRobberyDuration();
            String countdownFormatted = formatTime(countdownSeconds);
            String robberyFormatted = formatTime(robberySeconds);

            if (bomb.isOnClaimedPlot()) {
                player.sendMessage("");
                player.sendMessage(ChatColor.GOLD + "╔══════════════════════════════════════════╗");
                player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.RED + "⚠ CONFIRMATION DE CAMBRIOLAGE ⚠" + ChatColor.GOLD + "        ║");
                player.sendMessage(ChatColor.GOLD + "╠══════════════════════════════════════════╣");
                player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Terrain: " + ChatColor.WHITE + bomb.getPlotId());
                player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Ville: " + ChatColor.WHITE + bomb.getTownName());
                player.sendMessage(ChatColor.GOLD + "╠══════════════════════════════════════════╣");
                player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.AQUA + "⏱ Temps avant explosion: " + ChatColor.WHITE + countdownFormatted);
                player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.AQUA + "⏱ Durée de vol autorisé: " + ChatColor.WHITE + robberyFormatted);
                player.sendMessage(ChatColor.GOLD + "╠══════════════════════════════════════════╣");
                player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.RED + "⚠ La police sera IMMÉDIATEMENT alertée!");
                player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.RED + "⚠ Le maire et adjoints seront prévenus!");
                player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.RED + "⚠ Tous les citoyens seront informés!");
                player.sendMessage(ChatColor.GOLD + "╠══════════════════════════════════════════╣");
                player.sendMessage(ChatColor.GOLD + "║ " + ChatColor.GREEN + "➤ Clic droit à nouveau pour CONFIRMER");
                player.sendMessage(ChatColor.GOLD + "╚══════════════════════════════════════════╝");
                player.sendMessage("");
            } else {
                player.sendMessage("");
                player.sendMessage(ChatColor.YELLOW + "╔════════════════════════════════════╗");
                player.sendMessage(ChatColor.YELLOW + "║ " + ChatColor.WHITE + "Cette bombe est HORS zone protégée");
                player.sendMessage(ChatColor.YELLOW + "╠════════════════════════════════════╣");
                player.sendMessage(ChatColor.YELLOW + "║ " + ChatColor.AQUA + "⏱ Explosion dans: " + ChatColor.WHITE + countdownFormatted);
                player.sendMessage(ChatColor.YELLOW + "║ " + ChatColor.GRAY + "Pas d'alerte police, pas de vol.");
                player.sendMessage(ChatColor.YELLOW + "╠════════════════════════════════════╣");
                player.sendMessage(ChatColor.YELLOW + "║ " + ChatColor.GREEN + "➤ Clic droit pour CONFIRMER");
                player.sendMessage(ChatColor.YELLOW + "╚════════════════════════════════════╝");
                player.sendMessage("");
            }
            bomb.setAwaitingConfirmation(true);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            return;
        }

        // Déjà en attente → confirmer et démarrer
        UUID armorStandId = furniture.getArmorstand().getUniqueId();
        Heist heist = heistManager.confirmBomb(player, bomb, armorStandId);

        if (heist != null) {
            player.playSound(player.getLocation(), Sound.ENTITY_TNT_PRIMED, 1.0f, 0.5f);
        }
    }

    /**
     * Formate les secondes en format lisible (Xmin Xs)
     */
    private String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        if (minutes > 0) {
            return minutes + "min " + seconds + "s";
        }
        return seconds + "s";
    }

    /**
     * Gère l'interaction avec une bombe de heist actif (désamorçage par police)
     */
    private void handleHeistBombInteraction(Player player, Heist heist) {
        // Vérifier que le heist est en phase countdown
        if (heist.getPhase() != HeistPhase.COUNTDOWN) {
            player.sendMessage(ChatColor.RED + "La bombe a déjà explosé!");
            return;
        }

        // Vérifier si le joueur est policier dans cette ville
        Town town = plugin.getTownManager().getTown(heist.getTownName());
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Ville introuvable.");
            return;
        }

        TownRole role = town.getMemberRole(player.getUniqueId());
        boolean isPolice = role == TownRole.POLICIER;

        if (!isPolice) {
            // Vérifier la permission de bypass
            if (!player.hasPermission("roleplaycity.heist.defuse")) {
                player.sendMessage(ChatColor.RED + "Seule la police peut désamorcer cette bombe!");
                return;
            }
        }

        // Ouvrir le GUI de désamorçage
        HeistManager heistManager = plugin.getHeistManager();
        BombDefuseGUI.openFor(plugin, heistManager, heist, player);
    }

    /**
     * Gère une bombe armée sans heist actif (explosion simple en cours)
     * Permet à la police de désamorcer
     */
    private void handleArmedBombWithoutHeist(Player player, PlacedBomb bomb, CustomFurniture furniture) {
        // Vérifier si police
        boolean isPolice = false;
        if (bomb.isOnClaimedPlot()) {
            Town town = plugin.getTownManager().getTown(bomb.getTownName());
            if (town != null) {
                TownRole role = town.getMemberRole(player.getUniqueId());
                isPolice = role == TownRole.POLICIER ||
                           role == TownRole.MAIRE ||
                           role == TownRole.ADJOINT;
            }
        }

        // Permission bypass
        if (!isPolice && player.hasPermission("roleplaycity.heist.defuse")) {
            isPolice = true;
        }

        if (isPolice) {
            // Police peut désamorcer - supprimer la bombe
            HeistManager heistManager = plugin.getHeistManager();
            heistManager.removePlacedBomb(bomb.getArmorStandId());

            // Supprimer le furniture
            try {
                furniture.remove(false);
            } catch (Exception e) {
                plugin.getLogger().warning("[ItemsAdderListener] Erreur suppression furniture: " + e.getMessage());
            }

            player.sendMessage(ChatColor.GREEN + "✓ Bombe désamorcée avec succès!");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        } else {
            player.sendMessage(ChatColor.RED + "Cette bombe est armée et va exploser!");
            player.sendMessage(ChatColor.YELLOW + "Seule la police peut la désamorcer.");
        }
    }

    /**
     * Exécute un trigger avec ses conditions et actions
     * (Dupliqué depuis CustomItemListener pour éviter les dépendances croisées complexes)
     */
    private void handleTrigger(CustomItem item, String triggerType, Player player, Entity target) {
        CustomItem.TriggerData triggerData = item.getTriggers().get(triggerType);
        if (triggerData == null) {
            return;
        }

        // Vérifier les conditions
        if (!checkConditions(triggerData.getConditions(), player, target)) {
            return;
        }

        // Exécuter les actions
        executor.execute(triggerData.getActions(), player, target);
    }

    /**
     * Vérifie si toutes les conditions sont remplies
     */
    private boolean checkConditions(Map<String, Object> conditions, Player player, Entity target) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }

        // Vérification de permission
        String permission = (String) conditions.get("permission");
        if (permission != null && !player.hasPermission(permission)) {
            player.sendMessage("§cVous n'avez pas la permission requise.");
            return false;
        }

        // Vérification du rôle dans la ville
        @SuppressWarnings("unchecked")
        List<String> requiredRoles = (List<String>) conditions.get("town_role");
        if (requiredRoles != null && !requiredRoles.isEmpty()) {
            Town town = plugin.getTownManager().getPlayerTownObject(player.getUniqueId());
            if (town == null) {
                player.sendMessage("§cVous devez faire partie d'une ville.");
                return false;
            }

            TownRole role = town.getMemberRole(player.getUniqueId());
            if (role == null || !requiredRoles.contains(role.name())) {
                player.sendMessage("§cRôle requis: " + String.join(", ", requiredRoles));
                return false;
            }
        }

        // Vérification du type de plot
        @SuppressWarnings("unchecked")
        List<String> requiredPlotTypes = (List<String>) conditions.get("plot_type");
        if (requiredPlotTypes != null && !requiredPlotTypes.isEmpty()) {
            org.bukkit.Chunk chunk = player.getLocation().getChunk();
            com.gravityyfh.roleplaycity.town.data.Plot plot = plugin.getClaimManager().getPlotAt(chunk);

            if (plot == null) {
                player.sendMessage("§cVous devez être dans un plot.");
                return false;
            }

            if (!requiredPlotTypes.contains(plot.getType().name())) {
                player.sendMessage("§cType de plot requis: " + String.join(", ", requiredPlotTypes));
                return false;
            }
        }

        // Vérification du sous-type de plot (Commissariat, Mairie, etc.)
        @SuppressWarnings("unchecked")
        List<String> requiredSubTypes = (List<String>) conditions.get("plot_subtype");
        if (requiredSubTypes != null && !requiredSubTypes.isEmpty()) {
            org.bukkit.Chunk chunk = player.getLocation().getChunk();
            com.gravityyfh.roleplaycity.town.data.Plot plot = plugin.getClaimManager().getPlotAt(chunk);

            if (plot == null) {
                player.sendMessage("§cVous devez être dans un plot.");
                return false;
            }

            if (!requiredSubTypes.contains(plot.getMunicipalSubType().name())) {
                player.sendMessage("§cVous devez être dans: " + String.join(", ", requiredSubTypes));
                return false;
            }
        }

        // Vérification du coût
        if (conditions.containsKey("cost")) {
            double cost = ((Number) conditions.get("cost")).doubleValue();
            if (!RoleplayCity.getEconomy().has(player, cost)) {
                player.sendMessage("§cVous n'avez pas assez d'argent (requis: " + cost + "$).");
                return false;
            }
            // Retirer l'argent
            RoleplayCity.getEconomy().withdrawPlayer(player, cost);
            player.sendMessage("§a-" + cost + "$");
        }

        return true;
    }
}
