package com.gravityyfh.roleplaycity.mdt.setup;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.mdt.config.MDTConfig;
import com.gravityyfh.roleplaycity.mdt.gui.MDTBedSelectionGUI;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener pour gérer les interactions pendant le mode setup
 * Clic droit sur n'importe quoi → Ouvre le GUI de définition
 */
public class MDTSetupListener implements Listener {
    private final RoleplayCity plugin;
    private final MDTSetupManager setupManager;
    private final MDTConfig mdtConfig;
    private final MDTSetupDefinitionGUI definitionGUI;
    private final MDTBedSelectionGUI bedSelectionGUI;

    public MDTSetupListener(RoleplayCity plugin, MDTSetupManager setupManager, MDTConfig mdtConfig) {
        this.plugin = plugin;
        this.setupManager = setupManager;
        this.mdtConfig = mdtConfig;
        this.definitionGUI = new MDTSetupDefinitionGUI(plugin, setupManager);
        this.bedSelectionGUI = new MDTBedSelectionGUI(plugin, mdtConfig);
    }

    /**
     * Clic droit sur un bloc → Ouvre le GUI de définition (ou GUI lit si c'est un lit)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // Vérifier si le joueur est en mode setup
        if (!setupManager.isInSetupMode(player.getUniqueId())) {
            return;
        }

        // Seulement clic droit
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) return;

        // Vérifier que c'est dans le bon monde
        String expectedWorld = mdtConfig.getWorldName();
        if (!block.getWorld().getName().equals(expectedWorld)) {
            player.sendMessage(setupManager.getPrefix() + ChatColor.RED +
                "Tu dois être dans le monde '" + expectedWorld + "' pour configurer!");
            return;
        }

        event.setCancelled(true);

        Location blockLoc = block.getLocation();

        // Si c'est un lit
        if (isBedBlock(block.getType())) {
            // Vérifier si le joueur a une action de lit en attente
            if (bedSelectionGUI.hasPendingBedType(player.getUniqueId())) {
                // Appliquer directement le type en attente
                bedSelectionGUI.applyPendingBedType(player, blockLoc);
            } else {
                // Sinon ouvrir le GUI de sélection
                bedSelectionGUI.open(player, blockLoc);
            }
            return;
        }

        // Sinon, ouvrir le GUI de définition standard
        definitionGUI.open(player, blockLoc, null);
    }

    /**
     * Vérifie si un matériau est un lit
     */
    private boolean isBedBlock(Material material) {
        return material.name().endsWith("_BED");
    }

    /**
     * Clic droit sur une entité (villageois, etc.) → Ouvre le GUI de définition
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();

        // Vérifier si le joueur est en mode setup
        if (!setupManager.isInSetupMode(player.getUniqueId())) {
            return;
        }

        Entity entity = event.getRightClicked();

        // Vérifier que c'est dans le bon monde
        String expectedWorld = mdtConfig.getWorldName();
        if (!entity.getWorld().getName().equals(expectedWorld)) {
            player.sendMessage(setupManager.getPrefix() + ChatColor.RED +
                "Tu dois être dans le monde '" + expectedWorld + "' pour configurer!");
            return;
        }

        event.setCancelled(true);

        // Ouvrir le GUI de définition
        Location entityLoc = entity.getLocation();
        definitionGUI.open(player, entityLoc, entity);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Annuler la session si le joueur quitte
        if (setupManager.isInSetupMode(event.getPlayer().getUniqueId())) {
            setupManager.cancelSetup(event.getPlayer());
        }
    }
}
