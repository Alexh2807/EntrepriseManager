package com.gravityyfh.roleplaycity.phone.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.phone.PhoneManager;
import com.gravityyfh.roleplaycity.phone.gui.PhoneMainGUI;
import com.gravityyfh.roleplaycity.phone.service.PhoneService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Listener pour l'interaction avec les telephones.
 * Gere l'ouverture du GUI quand le joueur fait clic droit avec un telephone.
 */
public class PhoneInteractionListener implements Listener {

    private final RoleplayCity plugin;
    private final PhoneManager phoneManager;
    private final PhoneService phoneService;

    public PhoneInteractionListener(RoleplayCity plugin) {
        this.plugin = plugin;
        this.phoneManager = plugin.getPhoneManager();
        this.phoneService = plugin.getPhoneService();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Ignorer les interactions de la main secondaire
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        // Verifier si c'est un clic droit (air ou bloc)
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Verifier si c'est un telephone
        if (item == null || !phoneManager.isPhone(item)) {
            return;
        }

        // Annuler l'event pour ne pas placer de bloc ou autre
        event.setCancelled(true);

        // Ouvrir le GUI du telephone
        new PhoneMainGUI(plugin).open(player);
    }
}
