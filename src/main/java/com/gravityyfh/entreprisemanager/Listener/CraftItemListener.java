package com.gravityyfh.entreprisemanager.Listener;

import com.gravityyfh.entreprisemanager.EntrepriseManager;
import com.gravityyfh.entreprisemanager.EntrepriseManagerLogic;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import java.util.logging.Level;

public class CraftItemListener implements Listener {

    private final EntrepriseManagerLogic entrepriseLogic;
    private final EntrepriseManager plugin;

    public CraftItemListener(EntrepriseManager plugin, EntrepriseManagerLogic entrepriseLogic) {
        this.plugin = plugin;
        this.entrepriseLogic = entrepriseLogic;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();

        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        Recipe recipe = event.getRecipe();
        if (recipe == null) return;

        ItemStack resultItem = recipe.getResult();
        Material itemType = resultItem.getType();
        String itemTypeName = itemType.name();

        // On ne vérifie que la quantité de CET événement de craft (généralement 1 ou plus, selon la recette).
        // La logique complexe pour le shift-click est inutile car la méthode de restriction gère déjà l'accumulation.
        int amountPerCraft = resultItem.getAmount();

        plugin.getLogger().log(Level.INFO, "[DEBUG Craft] Tentative de craft par " + player.getName() + " pour " + amountPerCraft + "x " + itemTypeName);

        // On vérifie la restriction sur la base de la quantité de la fabrication actuelle.
        boolean isBlocked = entrepriseLogic.verifierEtGererRestrictionAction(player, "CRAFT_ITEM", itemTypeName, amountPerCraft);

        plugin.getLogger().log(Level.INFO, "[DEBUG Craft] Résultat vérification restriction : " + (isBlocked ? "BLOQUÉ" : "AUTORISÉ"));

        if (isBlocked) {
            event.setCancelled(true);
            plugin.getLogger().log(Level.INFO, "[DEBUG Craft] Craft de " + itemTypeName + " annulé par restriction pour " + player.getName());
            return; // Important de retourner ici pour ne pas enregistrer l'action productive
        }

        // L'action est autorisée, on enregistre la productivité.
        // La logique centrale s'occupera d'additionner les quantités si le joueur shift-click.
        entrepriseLogic.enregistrerActionProductive(player, "CRAFT_ITEM", itemType, amountPerCraft);
    }
}