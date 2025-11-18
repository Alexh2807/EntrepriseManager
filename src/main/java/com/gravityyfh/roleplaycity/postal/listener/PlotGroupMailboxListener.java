package com.gravityyfh.roleplaycity.postal.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.postal.manager.MailboxManager;
import com.gravityyfh.roleplaycity.town.data.Plot;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Écoute les événements de groupement/dégroupement de terrains
 * pour supprimer automatiquement les boîtes aux lettres
 */
public class PlotGroupMailboxListener implements Listener {
    private final RoleplayCity plugin;
    private final MailboxManager mailboxManager;

    public PlotGroupMailboxListener(RoleplayCity plugin, MailboxManager mailboxManager) {
        this.plugin = plugin;
        this.mailboxManager = mailboxManager;
    }

    /**
     * Lorsqu'un terrain est groupé ou dégroupé, supprimer sa boîte aux lettres
     * Cet événement sera déclenché depuis PlotGroupManagementGUI
     */
    public void onPlotGrouped(Plot plot) {
        if (mailboxManager.hasMailbox(plot)) {
            mailboxManager.removeMailbox(plot);
        }
    }

    public void onPlotUngrouped(Plot plot) {
        if (mailboxManager.hasMailbox(plot)) {
            mailboxManager.removeMailbox(plot);
        }
    }
}
