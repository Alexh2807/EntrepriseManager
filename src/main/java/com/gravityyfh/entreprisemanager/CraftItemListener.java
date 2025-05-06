package com.gravityyfh.entreprisemanager;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

public class CraftItemListener implements Listener {

    private final EntrepriseManagerLogic entrepriseLogic;
    private final EntrepriseManager plugin;

    public CraftItemListener(EntrepriseManager plugin, EntrepriseManagerLogic entrepriseLogic) {
        this.plugin = plugin;
        this.entrepriseLogic = entrepriseLogic;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return; // Action non initiée par un joueur
        }
        Player player = (Player) event.getWhoClicked();

        if (player.getGameMode() == GameMode.CREATIVE) {
            // En mode créatif, aucune restriction ni enregistrement de productivité.
            return;
        }

        ItemStack itemCrafted = event.getRecipe().getResult();
        Material itemType = itemCrafted.getType();
        int amountCrafted = itemCrafted.getAmount(); // Quantité produite par UNE opération de craft

        // Gérer le cas du shift-click (craft de plusieurs items à la fois)
        // La quantité exacte craftée avec shift-click est complexe à déterminer parfaitement sans
        // inspecter l'inventaire et les ingrédients.
        // Pour la logique de CA, on va se baser sur la quantité PAR opération de craft.
        // Si un joueur shift-click, l'événement est appelé plusieurs fois.
        // Alternative: multiplier 'amountCrafted' par une estimation si event.isShiftClick() est vrai.
        // Pour l'instant, on traite chaque événement comme une opération de craft.

        // Idée 3: Vérifier les restrictions pour l'item crafté
        if (entrepriseLogic.verifierEtGererRestrictionAction(player, "CRAFT_ITEM", itemType, amountCrafted)) {
            event.setCancelled(true); // Action bloquée par la restriction (limite non-membre atteinte)
            // Le message d'erreur est déjà envoyé par verifierEtGererRestrictionAction
            // player.sendMessage(ChatColor.DARK_RED + "[DEBUG] Craft de " + itemType.name() + " annulé par restriction."); // Debug
            return;
        }

        // Si l'action n'est pas bloquée, alors Idées 1 & 4: Enregistrer l'action productive
        // Cette méthode ne fera rien si le joueur n'est pas dans une entreprise
        // ou si l'action de craft n'est pas valorisée pour son type d'entreprise.
        entrepriseLogic.enregistrerActionProductive(player, "CRAFT_ITEM", itemType, amountCrafted);
        // player.sendMessage(ChatColor.DARK_AQUA + "[DEBUG] Action CRAFT_ITEM enregistrée pour " + amountCrafted + "x " + itemType.name()); // Debug
    }
}