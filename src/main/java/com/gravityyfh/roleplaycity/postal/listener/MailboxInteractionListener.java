package com.gravityyfh.roleplaycity.postal.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.postal.data.Mailbox;
import com.gravityyfh.roleplaycity.postal.manager.MailboxManager;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.TownRole;
import com.gravityyfh.roleplaycity.town.manager.ClaimManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;

/**
 * Gère les interactions avec les boîtes aux lettres
 * REFONTE: Utilise le nouveau système intégré dans Plot
 * - Clic droit pour ouvrir l'inventaire
 * - Protection contre la destruction
 * - Accès pour: propriétaire, locataire, MAIRE/ADJOINT
 * - Sauvegarde automatique lors de la fermeture
 */
public class MailboxInteractionListener implements Listener {
    private final RoleplayCity plugin;
    private final MailboxManager mailboxManager;
    private final ClaimManager claimManager;

    public MailboxInteractionListener(RoleplayCity plugin, MailboxManager mailboxManager,
                                      ClaimManager claimManager) {
        this.plugin = plugin;
        this.mailboxManager = mailboxManager;
        this.claimManager = claimManager;
    }

    /**
     * Gère le clic droit sur une boîte aux lettres pour ouvrir l'inventaire
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        // Ignorer l'événement de la main secondaire pour éviter les doublons
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;

        Player player = event.getPlayer();

        // Récupérer le plot via le nouveau système
        Plot plot = mailboxManager.getPlotByMailboxLocation(clicked.getLocation());
        if (plot == null || !plot.hasMailbox()) {
            return; // Ce n'est pas une boîte aux lettres
        }

        event.setCancelled(true);

        // Vérifier les permissions: propriétaire, locataire, ou MAIRE/ADJOINT
        boolean canAccess = canAccessMailbox(player, plot);

        if (!canAccess) {
            player.sendMessage(ChatColor.RED + "Cette boîte aux lettres ne vous appartient pas.");
            player.sendMessage(ChatColor.GRAY + "Seuls le propriétaire, le locataire ou le maire peuvent l'ouvrir.");
            return;
        }

        // Ouvrir l'inventaire via le MailboxManager
        mailboxManager.openMailbox(player, plot);
    }

    /**
     * Vérifie si un joueur peut accéder à une mailbox
     * Accès autorisé pour:
     * - Propriétaire du plot
     * - Locataire du plot
     * - Maire ou Adjoint de la ville (gestion, pas lecture du courrier personnel)
     */
    private boolean canAccessMailbox(Player player, Plot plot) {
        // Propriétaire
        if (plot.getOwnerUuid() != null && plot.getOwnerUuid().equals(player.getUniqueId())) {
            return true;
        }

        // Locataire
        if (plot.getRenterUuid() != null && plot.getRenterUuid().equals(player.getUniqueId())) {
            return true;
        }

        // Maire ou Adjoint
        var town = plugin.getTownManager().getTown(plot.getTownName());
        if (town != null) {
            var member = town.getMember(player.getUniqueId());
            if (member != null) {
                return member.hasRole(TownRole.MAIRE) || member.hasRole(TownRole.ADJOINT);
            }
        }

        return false;
    }

    /**
     * Sauvegarde immédiate quand on ferme une boîte aux lettres
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        Inventory inventory = event.getInventory();
        String title = event.getView().getTitle();

        // Vérifier si c'est une boîte aux lettres
        if (title.equals(ChatColor.GOLD + "📬 Boîte aux Lettres")) {
            // Récupérer le plot associé via metadata
            if (player.hasMetadata("viewing_mailbox_plot")) {
                Plot plot = (Plot) player.getMetadata("viewing_mailbox_plot").get(0).value();
                player.removeMetadata("viewing_mailbox_plot", plugin);

                if (plot != null) {
                    // Sauvegarder l'inventaire dans le plot
                    mailboxManager.saveMailboxInventory(plot, inventory);
                }
            }
        }
    }

    /**
     * Protège les boîtes aux lettres contre la destruction
     * Seul le propriétaire/locataire/maire peut les retirer via le menu
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        // Vérifier si c'est la tête de la mailbox
        Plot plot = mailboxManager.getPlotByMailboxLocation(block.getLocation());
        if (plot != null && plot.hasMailbox()) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Vous ne pouvez pas détruire une boîte aux lettres directement.");
            player.sendMessage(ChatColor.YELLOW + "Utilisez " + ChatColor.WHITE + "/ville " +
                ChatColor.YELLOW + "-> Mes Propriétés pour la remplacer.");
        }
    }
}
