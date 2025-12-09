package com.gravityyfh.roleplaycity.customitems.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.customitems.action.ActionExecutor;
import com.gravityyfh.roleplaycity.customitems.manager.CustomItemManager;
import com.gravityyfh.roleplaycity.customitems.model.CustomItem;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownRole;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.List;
import java.util.Map;

/**
 * Listener pour gérer les interactions avec les items custom (Vanilla & ItemsAdder)
 * 
 * Ce listener est sécurisé et ne dépend PAS des classes ItemsAdder.
 * Il utilise CustomItemManager.getItemByItemStack() qui gère à la fois
 * les items vanilla (via PDC) et les items ItemsAdder (via CustomStack si dispo).
 */
public class CustomItemListener implements Listener {

    private final RoleplayCity plugin;
    private final CustomItemManager manager;
    private final ActionExecutor executor;

    public CustomItemListener(RoleplayCity plugin, CustomItemManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.executor = new ActionExecutor(plugin);
    }

    /**
     * Gère les interactions avec les ITEMS EN MAIN uniquement
     *
     * Ne traite PAS les interactions avec les blocs/meubles posés
     * (ceux-ci sont gérés par ItemsAdderListener si ItemsAdder est présent)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractWithItem(PlayerInteractEvent event) {
        // Empêcher la double exécution (Main Hand uniquement)
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        Action action = event.getAction();

        // Ne gérer que les clics droits
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Vérifier si le joueur a un item en main
        if (event.getItem() == null) return;

        // Trouver le CustomItem correspondant (Méthode universelle Vanilla/ItemsAdder)
        CustomItem customItem = manager.getItemByItemStack(event.getItem());
        if (customItem == null) return;

        // Déterminer le trigger à utiliser
        String triggerKey = action == Action.RIGHT_CLICK_AIR ? "RIGHT_CLICK_AIR" : "RIGHT_CLICK_BLOCK";

        // Vérifier si on a ce trigger
        if (!customItem.getTriggers().containsKey(triggerKey)) {
            return;
        }

        // Exécuter le trigger
        handleTrigger(customItem, triggerKey, player, null);

        // Annuler l'événement pour empêcher l'action par défaut
        event.setCancelled(true);
    }

    /**
     * Exécute un trigger avec ses conditions et actions
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
