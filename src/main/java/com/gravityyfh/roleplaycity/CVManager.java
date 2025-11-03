package com.gravityyfh.roleplaycity;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class CVManager {

    private final RoleplayCity plugin;
    private final EntrepriseManagerLogic entrepriseLogic;
    private PlayerCVGUI playerCVGUI; // Injecté via setPlayerCVGUI pour éviter dépendance circulaire directe au constructeur

    // Clé: UUID du joueur qui DOIT accepter/refuser la demande
    // Valeur: Informations sur la demande en attente
    private final Map<UUID, PendingCVRequest> pendingRequests = new HashMap<>();

    // Classe interne pour stocker les détails d'une demande en attente
    private static class PendingCVRequest {
        final UUID concernedPlayerUUID; // UUID du joueur dont le CV est concerné OU qui a initié une demande de voir
        final RequestType type;         // Type de la demande (voir le CV de qqn OU montrer son propre CV)
        final long expirationTimeMillis; // Timestamp d'expiration de la demande
        final int bukkitTaskID;         // ID de la tâche Bukkit pour pouvoir l'annuler

        PendingCVRequest(UUID concernedPlayerUUID, RequestType type, long expirationTimeMillis, int bukkitTaskID) {
            this.concernedPlayerUUID = concernedPlayerUUID;
            this.type = type;
            this.expirationTimeMillis = expirationTimeMillis;
            this.bukkitTaskID = bukkitTaskID;
        }
    }

    // Énumération pour distinguer les types de demandes
    private enum RequestType {
        /** Un joueur demande à VOIR le CV de 'concernedPlayerUUID' (le propriétaire du CV) */
        PLAYER_WANTS_TO_VIEW_OTHER_CV,
        /** 'concernedPlayerUUID' (le propriétaire du CV) propose de MONTRER son CV */
        PLAYER_OFFERS_TO_SHOW_OWN_CV
    }

    public CVManager(RoleplayCity plugin, EntrepriseManagerLogic entrepriseLogic) {
        this.plugin = plugin;
        this.entrepriseLogic = entrepriseLogic;
    }

    // Méthode pour l'injection de dépendance (appelée depuis RoleplayCity après initialisation de PlayerCVGUI)
    public void setPlayerCVGUI(PlayerCVGUI playerCVGUI) {
        this.playerCVGUI = playerCVGUI;
    }

    /**
     * Gère la demande d'un joueur (requester) de VOIR le CV d'un autre joueur (cvOwner).
     * @param requester Le joueur qui fait la demande.
     * @param cvOwner Le joueur dont le CV est demandé.
     */
    public void requestShareCV(Player requester, Player cvOwner) {
        if (requester.getUniqueId().equals(cvOwner.getUniqueId())) {
            requester.sendMessage(ChatColor.RED + "Utilisez l'option 'Consulter mon CV' pour voir votre propre CV.");
            return;
        }

        double maxDistance = plugin.getConfig().getDouble("invitation.distance-max", 15.0);
        if (!isPlayerInRange(requester, cvOwner, maxDistance)) {
            requester.sendMessage(ChatColor.RED + cvOwner.getName() + " n'est pas assez proche.");
            return;
        }

        // cvOwner est celui qui doit accepter de montrer son CV.
        // requester est celui qui veut voir (et est donc le "concernedPlayer" du point de vue de cvOwner).
        if (hasPendingRequest(cvOwner.getUniqueId(), requester.getUniqueId()) || hasPendingRequest(requester.getUniqueId(),null)) {
            requester.sendMessage(ChatColor.YELLOW + "Une demande de CV est déjà en cours impliquant vous ou " + cvOwner.getName() + ".");
            return;
        }

        long expirationDelayTicks = getExpirationDelayTicks();
        final UUID requesterUUID = requester.getUniqueId();
        final UUID cvOwnerUUID = cvOwner.getUniqueId();

        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PendingCVRequest expiredRequest = pendingRequests.remove(cvOwnerUUID);
            if (expiredRequest != null && expiredRequest.concernedPlayerUUID.equals(requesterUUID) && expiredRequest.type == RequestType.PLAYER_WANTS_TO_VIEW_OTHER_CV) {
                sendMessageIfOnline(cvOwnerUUID, ChatColor.RED + "La demande de CV de " + requester.getName() + " a expiré.");
                sendMessageIfOnline(requesterUUID, ChatColor.RED + "Votre demande de CV à " + cvOwner.getName() + " a expiré.");
            }
        }, expirationDelayTicks).getTaskId();

        pendingRequests.put(cvOwnerUUID, new PendingCVRequest(requesterUUID, RequestType.PLAYER_WANTS_TO_VIEW_OTHER_CV, System.currentTimeMillis() + (expirationDelayTicks / 20 * 1000), taskId));

        requester.sendMessage(ChatColor.GREEN + "Demande d'affichage du CV de " + ChatColor.YELLOW + cvOwner.getName() + ChatColor.GREEN + " envoyée.");
        TextComponent message = new TextComponent(ChatColor.YELLOW + requester.getName() + ChatColor.AQUA + " souhaite consulter votre CV. ");
        addAcceptDenyOptions(message, "/entreprise cv accepter", "/entreprise cv refuser", "Accepter de montrer votre CV", "Refuser de montrer votre CV");
        cvOwner.spigot().sendMessage(message);
    }

    /**
     * Gère la proposition d'un joueur (sharer) de MONTRER son propre CV à un autre joueur (targetPlayer).
     * @param sharer Le joueur qui veut montrer son CV.
     * @param targetPlayer Le joueur à qui le CV est proposé.
     */
    public void requestShareOwnCV(Player sharer, Player targetPlayer) {
        if (sharer.getUniqueId().equals(targetPlayer.getUniqueId())) {
            sharer.sendMessage(ChatColor.RED + "Utilisez 'Consulter mon CV' pour voir votre propre CV.");
            return;
        }

        double maxDistance = plugin.getConfig().getDouble("invitation.distance-max", 15.0);
        if (!isPlayerInRange(sharer, targetPlayer, maxDistance)) {
            sharer.sendMessage(ChatColor.RED + targetPlayer.getName() + " n'est pas assez proche.");
            return;
        }

        // targetPlayer est celui qui doit accepter de voir le CV de sharer.
        // sharer est le "concernedPlayer" (propriétaire du CV).
        if (hasPendingRequest(targetPlayer.getUniqueId(), sharer.getUniqueId()) || hasPendingRequest(sharer.getUniqueId(),null)) {
            sharer.sendMessage(ChatColor.YELLOW + "Une demande de CV est déjà en cours impliquant vous ou " + targetPlayer.getName() + ".");
            return;
        }

        long expirationDelayTicks = getExpirationDelayTicks();
        final UUID sharerUUID = sharer.getUniqueId();
        final UUID targetUUID = targetPlayer.getUniqueId();

        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PendingCVRequest expiredRequest = pendingRequests.remove(targetUUID);
            if (expiredRequest != null && expiredRequest.concernedPlayerUUID.equals(sharerUUID) && expiredRequest.type == RequestType.PLAYER_OFFERS_TO_SHOW_OWN_CV) {
                sendMessageIfOnline(targetUUID, ChatColor.RED + "La proposition de " + sharer.getName() + " pour voir son CV a expiré.");
                sendMessageIfOnline(sharerUUID, ChatColor.RED + "Votre proposition de montrer votre CV à " + targetPlayer.getName() + " a expiré.");
            }
        }, expirationDelayTicks).getTaskId();

        pendingRequests.put(targetUUID, new PendingCVRequest(sharerUUID, RequestType.PLAYER_OFFERS_TO_SHOW_OWN_CV, System.currentTimeMillis() + (expirationDelayTicks / 20 * 1000), taskId));

        sharer.sendMessage(ChatColor.GREEN + "Proposition d'afficher votre CV envoyée à " + ChatColor.YELLOW + targetPlayer.getName() + ChatColor.GREEN + ".");
        TextComponent message = new TextComponent(ChatColor.YELLOW + sharer.getName() + ChatColor.AQUA + " souhaite vous montrer son CV. ");
        addAcceptDenyOptions(message, "/entreprise cv accepter", "/entreprise cv refuser", "Accepter de voir le CV de " + sharer.getName(), "Refuser de voir son CV");
        targetPlayer.spigot().sendMessage(message);
    }

    /**
     * Gère la commande d'acceptation d'une demande de CV.
     * @param acceptor Le joueur qui tape la commande pour accepter.
     */
    public void handleAcceptCV(Player acceptor) {
        PendingCVRequest requestData = pendingRequests.remove(acceptor.getUniqueId());

        if (requestData == null || System.currentTimeMillis() > requestData.expirationTimeMillis) {
            if (requestData != null) Bukkit.getScheduler().cancelTask(requestData.bukkitTaskID);
            acceptor.sendMessage(ChatColor.RED + "Aucune demande de CV valide à accepter ou la demande a expiré.");
            return;
        }
        Bukkit.getScheduler().cancelTask(requestData.bukkitTaskID); // Annuler la tâche d'expiration

        Player concernedPlayer = Bukkit.getPlayer(requestData.concernedPlayerUUID);
        if (concernedPlayer == null || !concernedPlayer.isOnline()) {
            acceptor.sendMessage(ChatColor.RED + "Le joueur initialement concerné par cette demande n'est plus en ligne.");
            return;
        }

        if (playerCVGUI == null) {
            plugin.getLogger().log(Level.SEVERE, "PlayerCVGUI n'est pas initialisé dans CVManager ! Impossible d'ouvrir le CV.");
            acceptor.sendMessage(ChatColor.RED + "Erreur interne du plugin, impossible d'ouvrir le CV.");
            return;
        }

        Player viewer;
        Player cvOwner;

        if (requestData.type == RequestType.PLAYER_WANTS_TO_VIEW_OTHER_CV) {
            // Cas: 'concernedPlayer' (demandeur) veut voir le CV de 'acceptor' (propriétaire du CV).
            // 'acceptor' a accepté de montrer son CV.
            viewer = concernedPlayer;
            cvOwner = acceptor;
            acceptor.sendMessage(ChatColor.GREEN + "Vous avez accepté de montrer votre CV à " + ChatColor.YELLOW + viewer.getName() + ChatColor.GREEN + ".");
            viewer.sendMessage(ChatColor.GREEN + acceptor.getName() + " a accepté votre demande. Affichage de son CV...");
        } else { // RequestType.PLAYER_OFFERS_TO_SHOW_OWN_CV
            // Cas: 'concernedPlayer' (propriétaire du CV) a proposé de montrer son CV à 'acceptor'.
            // 'acceptor' a accepté de voir le CV.
            viewer = acceptor;
            cvOwner = concernedPlayer;
            acceptor.sendMessage(ChatColor.GREEN + "Vous avez accepté de voir le CV de " + ChatColor.YELLOW + cvOwner.getName() + ChatColor.GREEN + ". Affichage...");
            cvOwner.sendMessage(ChatColor.GREEN + acceptor.getName() + " consulte maintenant votre CV.");
        }
        playerCVGUI.openCV(viewer, cvOwner, entrepriseLogic);
    }

    /**
     * Gère la commande de refus d'une demande de CV.
     * @param refuser Le joueur qui tape la commande pour refuser.
     */
    public void handleRefuseCV(Player refuser) {
        PendingCVRequest requestData = pendingRequests.remove(refuser.getUniqueId());

        if (requestData == null || System.currentTimeMillis() > requestData.expirationTimeMillis) {
            if (requestData != null) Bukkit.getScheduler().cancelTask(requestData.bukkitTaskID);
            refuser.sendMessage(ChatColor.RED + "Aucune demande de CV valide à refuser ou la demande a expiré.");
            return;
        }
        Bukkit.getScheduler().cancelTask(requestData.bukkitTaskID);

        Player concernedPlayer = Bukkit.getPlayer(requestData.concernedPlayerUUID);

        if (requestData.type == RequestType.PLAYER_WANTS_TO_VIEW_OTHER_CV) {
            // Cas: 'concernedPlayer' (demandeur) voulait voir le CV de 'refuser' (propriétaire du CV).
            // 'refuser' a refusé de montrer son CV.
            refuser.sendMessage(ChatColor.YELLOW + "Vous avez refusé la demande de CV de " + ChatColor.YELLOW + (concernedPlayer != null ? concernedPlayer.getName() : "un joueur") + ChatColor.YELLOW + ".");
            sendMessageIfOnline(requestData.concernedPlayerUUID, ChatColor.RED + refuser.getName() + " a refusé votre demande de voir son CV.");
        } else { // RequestType.PLAYER_OFFERS_TO_SHOW_OWN_CV
            // Cas: 'concernedPlayer' (propriétaire du CV) avait proposé son CV à 'refuser'.
            // 'refuser' a refusé de voir le CV.
            refuser.sendMessage(ChatColor.YELLOW + "Vous avez refusé de voir le CV de " + ChatColor.YELLOW + (concernedPlayer != null ? concernedPlayer.getName() : "un joueur") + ChatColor.YELLOW + ".");
            sendMessageIfOnline(requestData.concernedPlayerUUID, ChatColor.RED + refuser.getName() + " a refusé votre proposition de voir votre CV.");
        }
    }

    // --- Méthodes utilitaires ---
    private boolean isPlayerInRange(Player p1, Player p2, double maxDistance) {
        return p1.getWorld().equals(p2.getWorld()) && p1.getLocation().distanceSquared(p2.getLocation()) <= (maxDistance * maxDistance);
    }

    private boolean hasPendingRequest(UUID acceptorOrTargetUUID, UUID specificConcernedUUID) {
        PendingCVRequest request = pendingRequests.get(acceptorOrTargetUUID);
        if (request == null) return false;
        if (System.currentTimeMillis() > request.expirationTimeMillis) { // La demande a expiré
            Bukkit.getScheduler().cancelTask(request.bukkitTaskID);
            pendingRequests.remove(acceptorOrTargetUUID);
            return false;
        }
        // Si specificConcernedUUID est null, on vérifie juste si une demande existe pour acceptorOrTargetUUID
        // Sinon, on vérifie si la demande existante concerne bien specificConcernedUUID
        return specificConcernedUUID == null || request.concernedPlayerUUID.equals(specificConcernedUUID);
    }


    private long getExpirationDelayTicks() {
        return plugin.getConfig().getLong("cv.request-expiration-seconds", 60L) * 20L;
    }

    private void sendMessageIfOnline(UUID playerUUID, String message) {
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null && player.isOnline()) {
            player.sendMessage(message);
        }
    }

    private void addAcceptDenyOptions(TextComponent message, String acceptCommand, String refuseCommand, String acceptHover, String refuseHover) {
        TextComponent accepterComp = new TextComponent("[ACCEPTER]");
        accepterComp.setColor(net.md_5.bungee.api.ChatColor.GREEN);
        accepterComp.setBold(true);
        accepterComp.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, acceptCommand));
        accepterComp.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(acceptHover).create()));

        TextComponent refuserComp = new TextComponent(" [REFUSER]");
        refuserComp.setColor(net.md_5.bungee.api.ChatColor.RED);
        refuserComp.setBold(true);
        refuserComp.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, refuseCommand));
        refuserComp.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(refuseHover).create()));

        message.addExtra(accepterComp);
        message.addExtra(refuserComp);
    }
}