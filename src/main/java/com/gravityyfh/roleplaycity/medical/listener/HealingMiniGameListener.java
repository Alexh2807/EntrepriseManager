package com.gravityyfh.roleplaycity.medical.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.medical.gui.HealingMiniGameGUI;
import com.gravityyfh.roleplaycity.medical.manager.MedicalSystemManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * Listener pour le mini-jeu de suture
 */
public class HealingMiniGameListener implements Listener {

    private final RoleplayCity plugin;
    private final MedicalSystemManager medicalManager;

    public HealingMiniGameListener(RoleplayCity plugin) {
        this.plugin = plugin;
        this.medicalManager = plugin.getMedicalSystemManager();
    }

    /**
     * Gère les clics dans le GUI de suture
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Vérifier si c'est un GUI de mini-jeu actif
        HealingMiniGameGUI minigame = medicalManager.getActiveMiniGame(player);
        if (minigame == null) {
            return;
        }

        // Vérifier si c'est le bon inventaire
        if (!minigame.isThisInventory(event.getClickedInventory())) {
            // Empêcher de prendre des items de son inventaire
            if (event.getClickedInventory() == player.getInventory()) {
                event.setCancelled(true);
            }
            return;
        }

        // Annuler l'événement pour empêcher de prendre l'item
        event.setCancelled(true);

        // Gérer le clic
        int slot = event.getSlot();
        minigame.handleClick(slot);
    }

    /**
     * Gère la fermeture du GUI
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        // Vérifier si c'est un GUI de mini-jeu actif
        HealingMiniGameGUI minigame = medicalManager.getActiveMiniGame(player);
        if (minigame == null) {
            return;
        }

        // Vérifier si c'est le bon inventaire
        if (!minigame.isThisInventory(event.getInventory())) {
            return;
        }

        // Si le mini-jeu n'est pas terminé, c'est un échec
        if (!minigame.isCompleted()) {
            minigame.onPrematureClose();
        }

        // Retirer le mini-jeu actif
        medicalManager.removeActiveMiniGame(player);
    }
}
