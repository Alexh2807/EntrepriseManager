package com.gravityyfh.roleplaycity.Listener;

import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.inventory.ItemStack;

public class SmithItemListener implements Listener {

    private final EntrepriseManagerLogic entrepriseLogic;

    public SmithItemListener(EntrepriseManagerLogic entrepriseLogic) {
        this.entrepriseLogic = entrepriseLogic;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSmithItem(SmithItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();

        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        ItemStack resultItem = event.getCurrentItem(); // L'item résultant de la forge
        if (resultItem == null || resultItem.getType() == Material.AIR) {
            return;
        }

        Material itemType = resultItem.getType();
        String itemTypeName = itemType.name();

        // La table de forgeron ne produit qu'un seul item à la fois. La quantité est donc toujours 1.
        int quantity = 1;

        // On vérifie les restrictions (basé sur votre config qui utilise CRAFT_ITEM pour l'armurerie)
        boolean isBlocked = entrepriseLogic.verifierEtGererRestrictionAction(player, "CRAFT_ITEM", itemTypeName, quantity);

        if (isBlocked) {
            event.setCancelled(true);
            return;
        }

        // Si l'action est autorisée, on enregistre la productivité
        entrepriseLogic.enregistrerActionProductive(player, "CRAFT_ITEM", itemType, quantity);
    }
}