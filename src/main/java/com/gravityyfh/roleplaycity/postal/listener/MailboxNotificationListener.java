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
            // NOTIFICATION : Absence de boîte aux lettres (TOUJOURS affiché)
            if (!mailboxManager.hasMailbox(plot)) {
                player.sendMessage(ChatColor.YELLOW + "⚠ Votre terrain ne possède aucune boîte aux lettres.");
                player.sendMessage(ChatColor.GRAY + "Vous devez en placer une : " +
                    ChatColor.WHITE + "/ville " + ChatColor.GRAY + "-> \"Mes propriétés\"");
            } else {
                // NOTIFICATION : Courrier en attente (TOUJOURS affiché)
                if (plot.hasMailbox()) {
                    Mailbox mailbox = plot.getMailbox();
                    if (mailbox != null && mailbox.hasMail()) {
                        player.sendMessage(ChatColor.GREEN + "📬 Vous avez du courrier !");
                        player.sendMessage(ChatColor.YELLOW + "Faites un clic droit sur votre boîte aux lettres " +
                            "pour récupérer votre courrier.");
                    }
                }
            }
        } else {
            // NOTIFICATION : Visiteur sur un terrain sans mailbox (TOUJOURS affiché)
            if (!mailboxManager.hasMailbox(plot)) {
                player.sendMessage(ChatColor.GRAY + "Ce terrain ne possède aucune boîte aux lettres.");
            }
        }
    }
}
