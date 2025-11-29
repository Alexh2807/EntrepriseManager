package com.gravityyfh.roleplaycity.postal.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.postal.data.Mailbox;
import com.gravityyfh.roleplaycity.postal.manager.MailboxManager;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.manager.ClaimManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gère les notifications aux joueurs concernant les boîtes aux lettres
 * - Notification d'absence de mailbox
 * - Notification de courrier en attente
 * - Message d'invitation à La Poste
 */
public class MailboxNotificationListener implements Listener {
    private final RoleplayCity plugin;
    private final MailboxManager mailboxManager;
    private final ClaimManager claimManager;

    public MailboxNotificationListener(RoleplayCity plugin, MailboxManager mailboxManager,
                                       ClaimManager claimManager) {
        this.plugin = plugin;
        this.mailboxManager = mailboxManager;
        this.claimManager = claimManager;
    }

    /**
     * Détecte quand un joueur entre dans un chunk et envoie les notifications appropriées
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Optimisation : ignorer les mouvements qui ne changent pas de chunk
        if (event.getFrom().getChunk().equals(event.getTo().getChunk())) {
            return;
        }

        Player player = event.getPlayer();
        Plot plot = claimManager.getPlotAt(event.getTo().getChunk());

        if (plot == null) return;

        UUID playerId = player.getUniqueId();

        // NOTIFICATION LA_POSTE : Message d'invitation (TOUJOURS affiché)
        if (plot.getMunicipalSubType() == com.gravityyfh.roleplaycity.town.data.MunicipalSubType.LA_POSTE) {
            player.sendMessage(ChatColor.GOLD + "📮 Bienvenue à La Poste !");
            player.sendMessage(ChatColor.YELLOW + "Utilisez " + ChatColor.WHITE + "/laposte " +
                ChatColor.YELLOW + "pour envoyer du courrier.");
            return;
        }

        // Vérifier si le joueur est propriétaire ou locataire
        boolean isOwner = plot.getOwnerUuid() != null && plot.getOwnerUuid().equals(playerId);
        boolean isRenter = plot.getRenterUuid() != null && plot.getRenterUuid().equals(playerId);

        if (isOwner || isRenter) {
            // NOTIFICATION : Courrier en attente uniquement (le statut mailbox est dans le scoreboard)
            if (mailboxManager.hasMailbox(plot) && plot.hasMailbox()) {
                Mailbox mailbox = plot.getMailbox();
                if (mailbox != null && mailbox.hasMail()) {
                    player.sendMessage(ChatColor.GREEN + "📬 Vous avez du courrier !");
                    player.sendMessage(ChatColor.YELLOW + "Faites un clic droit sur votre boîte aux lettres " +
                        "pour récupérer votre courrier.");
                }
            }
            // NOTE: Le message "pas de boîte aux lettres" est maintenant affiché dans le scoreboard
        }
        // NOTE: Le message pour les visiteurs est supprimé car l'info est dans le scoreboard
    }
}
