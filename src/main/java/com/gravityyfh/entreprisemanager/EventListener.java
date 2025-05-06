package com.gravityyfh.entreprisemanager;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent; // Ajout pour l'événement de placement
import org.bukkit.event.inventory.CraftItemEvent; // Ajout pour l'événement de craft
import org.bukkit.inventory.ItemStack; // Pour CraftItemEvent

import java.util.List;
// UUID n'est plus directement utilisé ici car on passe l'objet Player

public class EventListener implements Listener {

    private final EntrepriseManagerLogic entrepriseLogic;
    private final EntrepriseManager plugin;

    public EventListener(EntrepriseManager plugin, EntrepriseManagerLogic entrepriseLogic) {
        this.plugin = plugin;
        this.entrepriseLogic = entrepriseLogic;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true) // Normal pour laisser d'autres plugins agir avant si besoin
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (player.getGameMode() == GameMode.CREATIVE) {
            // En mode créatif, aucune restriction ni enregistrement de productivité.
            return;
        }

        Material blockType = event.getBlock().getType();

        // Idée 3: Vérifier les restrictions
        if (entrepriseLogic.verifierEtGererRestrictionAction(player, "BLOCK_BREAK", blockType, 1)) {
            event.setCancelled(true); // Action bloquée par la restriction (limite non-membre atteinte)
            // Le message d'erreur est déjà envoyé par verifierEtGererRestrictionAction
            return;
        }

        // Si l'action n'est pas bloquée, alors Idées 1 & 4: Enregistrer l'action productive
        // Cette méthode ne fera rien si le joueur n'est pas dans une entreprise ou si l'action n'est pas valorisée.
        entrepriseLogic.enregistrerActionProductive(player, "BLOCK_BREAK", blockType, 1);
        // player.sendMessage(ChatColor.DARK_AQUA + "[DEBUG] Action BLOCK_BREAK enregistrée pour " + blockType.name()); // Debug
    }

    // Les méthodes onCraftItem et onBlockPlace sont maintenant dans leurs propres listeners
    // Si vous préférez les garder ici, décommentez et adaptez.
    // J'ai fourni des exemples dans les classes dédiées (CraftItemListener.java, BlockPlaceListener.java)

    /*
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        ItemStack itemCrafted = event.getRecipe().getResult();
        Material itemType = itemCrafted.getType();
        int amount = itemCrafted.getAmount(); // Quantité résultant du craft

        // Ajuster la quantité si le clic était un SHIFT_CLICK (craft multiple)
        if (event.isShiftClick()) {
            // Logique complexe pour déterminer la quantité exacte avec SHIFT_CLICK
            // Pour simplifier, on peut prendre la quantité du résultat * une estimation,
            // ou se concentrer sur le CA par item unitaire et laisser le joueur faire plusieurs crafts.
            // Pour cet exemple, nous allons considérer la quantité du résultat de la recette.
            // Une logique plus précise pourrait inspecter l'inventaire du joueur pour voir combien de sets d'ingrédients il avait.
            // Pour un CA par item, la quantité de la recette est ce qu'il faut.
        }


        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        // Idée 3: Vérifier les restrictions
        if (entrepriseLogic.verifierEtGererRestrictionAction(player, "CRAFT_ITEM", itemType, amount)) {
            event.setCancelled(true);
            // Message d'erreur déjà géré
            return;
        }

        // Idées 1 & 4: Enregistrer l'action productive
        entrepriseLogic.enregistrerActionProductive(player, "CRAFT_ITEM", itemType, amount);
         // player.sendMessage(ChatColor.DARK_AQUA + "[DEBUG] Action CRAFT_ITEM enregistrée pour " + itemType.name()); // Debug
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Material blockType = event.getBlockPlaced().getType();

        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        // Idée 3: Vérifier les restrictions
        if (entrepriseLogic.verifierEtGererRestrictionAction(player, "BLOCK_PLACE", blockType, 1)) {
            event.setCancelled(true);
            // Message d'erreur déjà géré
            return;
        }

        // Idées 1 & 4: Enregistrer l'action productive
        entrepriseLogic.enregistrerActionProductive(player, "BLOCK_PLACE", blockType, 1);
        // player.sendMessage(ChatColor.DARK_AQUA + "[DEBUG] Action BLOCK_PLACE enregistrée pour " + blockType.name()); // Debug
    }
    */
}