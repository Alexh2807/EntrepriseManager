package com.gravityyfh.roleplaycity.service;

import com.gravityyfh.roleplaycity.RoleplayCity;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire de requêtes d'interaction entre joueurs
 * Gère les demandes de fouille, d'identité, etc. avec consentement
 */
public class InteractionRequestManager {

    private final RoleplayCity plugin;

    // Requêtes en attente par ID de requête
    private final Map<UUID, InteractionRequest> pendingRequests = new ConcurrentHashMap<>();

    // Requêtes par joueur cible (pour retrouver facilement)
    private final Map<UUID, List<UUID>> requestsByTarget = new ConcurrentHashMap<>();

    // Distance maximale pour les interactions (5 blocs)
    public static final double MAX_INTERACTION_DISTANCE = 5.0;

    public InteractionRequestManager(RoleplayCity plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    /**
     * Crée une requête de fouille
     */
    public InteractionRequest createFriskRequest(Player police, Player target) {
        return createRequest(police, target, InteractionRequest.RequestType.FRISK);
    }

    /**
     * Crée une requête de demande d'identité (police -> joueur)
     */
    public InteractionRequest createIdRequest(Player police, Player target) {
        return createRequest(police, target, InteractionRequest.RequestType.REQUEST_ID);
    }

    /**
     * Crée une requête pour montrer son ID (joueur -> autre joueur)
     */
    public InteractionRequest createShowIdRequest(Player player, Player target) {
        return createRequest(player, target, InteractionRequest.RequestType.SHOW_ID);
    }

    private InteractionRequest createRequest(Player requester, Player target, InteractionRequest.RequestType type) {
        // Vérifier la distance
        if (!isWithinDistance(requester, target)) {
            requester.sendMessage(ChatColor.RED + "Ce joueur est trop loin (max " + (int)MAX_INTERACTION_DISTANCE + " blocs).");
            return null;
        }

        // Créer la requête
        InteractionRequest request = new InteractionRequest(
            requester.getUniqueId(),
            requester.getName(),
            target.getUniqueId(),
            target.getName(),
            type
        );

        // Enregistrer
        pendingRequests.put(request.getRequestId(), request);
        requestsByTarget.computeIfAbsent(target.getUniqueId(), k -> new ArrayList<>()).add(request.getRequestId());

        // Envoyer le message au joueur cible avec les boutons
        sendRequestMessage(target, request);

        return request;
    }

    /**
     * Envoie le message cliquable au joueur cible
     */
    private void sendRequestMessage(Player target, InteractionRequest request) {
        String message = "";
        String hoverAccept = "";
        String hoverRefuse = "";

        switch (request.getType()) {
            case FRISK:
                message = ChatColor.GOLD + "👮 " + ChatColor.YELLOW + request.getRequesterName() +
                          ChatColor.GOLD + " veut vous fouiller.";
                hoverAccept = "Accepter la fouille";
                hoverRefuse = "Refuser la fouille";
                break;
            case REQUEST_ID:
                message = ChatColor.GOLD + "👮 " + ChatColor.YELLOW + request.getRequesterName() +
                          ChatColor.GOLD + " demande à voir votre carte d'identité.";
                hoverAccept = "Montrer votre carte d'identité";
                hoverRefuse = "Refuser de montrer votre ID";
                break;
            case SHOW_ID:
                message = ChatColor.GREEN + "📋 " + ChatColor.YELLOW + request.getRequesterName() +
                          ChatColor.GREEN + " veut vous montrer sa carte d'identité.";
                hoverAccept = "Voir sa carte d'identité";
                hoverRefuse = "Refuser de voir";
                break;
        }

        target.sendMessage("");
        target.sendMessage(message);

        // Boutons Accept/Refuse
        TextComponent acceptBtn = new TextComponent(ChatColor.GREEN + "" + ChatColor.BOLD + "[ACCEPTER]");
        acceptBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/rpc_internal_accept " + request.getRequestId()));
        acceptBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.GREEN + hoverAccept)));

        TextComponent space = new TextComponent("  ");

        TextComponent refuseBtn = new TextComponent(ChatColor.RED + "" + ChatColor.BOLD + "[REFUSER]");
        refuseBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/rpc_internal_refuse " + request.getRequestId()));
        refuseBtn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(ChatColor.RED + hoverRefuse)));

        TextComponent expireInfo = new TextComponent(ChatColor.GRAY + " (30s)");

        target.spigot().sendMessage(acceptBtn, space, refuseBtn, expireInfo);
        target.sendMessage("");
    }

    /**
     * Accepte une requête
     */
    public boolean acceptRequest(UUID requestId, Player accepter) {
        InteractionRequest request = pendingRequests.remove(requestId);
        if (request == null) {
            accepter.sendMessage(ChatColor.RED + "Cette demande n'existe plus ou a expiré.");
            return false;
        }

        if (request.isExpired()) {
            accepter.sendMessage(ChatColor.RED + "Cette demande a expiré.");
            cleanupRequest(request);
            return false;
        }

        // Vérifier que c'est bien la cible qui accepte
        if (!request.getTargetId().equals(accepter.getUniqueId())) {
            accepter.sendMessage(ChatColor.RED + "Cette demande ne vous concerne pas.");
            pendingRequests.put(requestId, request); // Remettre
            return false;
        }

        Player requester = Bukkit.getPlayer(request.getRequesterId());
        if (requester == null || !requester.isOnline()) {
            accepter.sendMessage(ChatColor.RED + "Le demandeur s'est déconnecté.");
            cleanupRequest(request);
            return false;
        }

        // Vérifier la distance
        if (!isWithinDistance(requester, accepter)) {
            accepter.sendMessage(ChatColor.RED + "Vous êtes trop loin du demandeur.");
            requester.sendMessage(ChatColor.RED + accepter.getName() + " est trop loin pour l'interaction.");
            cleanupRequest(request);
            return false;
        }

        cleanupRequest(request);

        // Exécuter l'action
        switch (request.getType()) {
            case FRISK:
                handleFriskAccepted(requester, accepter);
                break;
            case REQUEST_ID:
                handleIdRequestAccepted(requester, accepter);
                break;
            case SHOW_ID:
                handleShowIdAccepted(requester, accepter);
                break;
        }

        return true;
    }

    /**
     * Refuse une requête
     */
    public boolean refuseRequest(UUID requestId, Player refuser) {
        InteractionRequest request = pendingRequests.remove(requestId);
        if (request == null) {
            refuser.sendMessage(ChatColor.RED + "Cette demande n'existe plus ou a expiré.");
            return false;
        }

        // Vérifier que c'est bien la cible qui refuse
        if (!request.getTargetId().equals(refuser.getUniqueId())) {
            refuser.sendMessage(ChatColor.RED + "Cette demande ne vous concerne pas.");
            pendingRequests.put(requestId, request);
            return false;
        }

        cleanupRequest(request);

        Player requester = Bukkit.getPlayer(request.getRequesterId());

        String typeMsg = switch (request.getType()) {
            case FRISK -> "la fouille";
            case REQUEST_ID -> "de montrer sa carte d'identité";
            case SHOW_ID -> "de voir votre carte d'identité";
        };

        refuser.sendMessage(ChatColor.YELLOW + "Vous avez refusé " + typeMsg + ".");

        if (requester != null && requester.isOnline()) {
            requester.sendMessage(ChatColor.RED + refuser.getName() + " a refusé " + typeMsg + ".");
        }

        return true;
    }

    // =========================================================================
    // Actions après acceptation
    // =========================================================================

    private void handleFriskAccepted(Player police, Player target) {
        police.sendMessage(ChatColor.GREEN + target.getName() + " a accepté la fouille.");
        target.sendMessage(ChatColor.YELLOW + "Vous avez accepté d'être fouillé par " + police.getName() + ".");

        // Ouvrir le GUI de fouille
        if (plugin.getFriskGUI() != null) {
            plugin.getFriskGUI().openFriskInventory(police, target);
        }
    }

    private void handleIdRequestAccepted(Player police, Player target) {
        police.sendMessage(ChatColor.GREEN + target.getName() + " vous montre sa carte d'identité.");
        target.sendMessage(ChatColor.YELLOW + "Vous montrez votre carte d'identité à " + police.getName() + ".");

        // Afficher l'identité dans le chat du policier
        showIdentityTo(target, police);
    }

    private void handleShowIdAccepted(Player player, Player viewer) {
        player.sendMessage(ChatColor.GREEN + viewer.getName() + " accepte de voir votre carte d'identité.");
        viewer.sendMessage(ChatColor.GREEN + "Vous regardez la carte d'identité de " + player.getName() + ".");

        // Afficher l'identité
        showIdentityTo(player, viewer);
    }

    /**
     * Affiche l'identité d'un joueur à un autre
     */
    private void showIdentityTo(Player owner, Player viewer) {
        var identityManager = plugin.getIdentityManager();
        if (identityManager == null) {
            viewer.sendMessage(ChatColor.RED + "Système d'identité non disponible.");
            return;
        }

        var identity = identityManager.getIdentity(owner.getUniqueId());
        if (identity == null) {
            viewer.sendMessage(ChatColor.GRAY + "Cette personne n'a pas de carte d'identité enregistrée.");
            return;
        }

        // Utiliser le nom Minecraft du joueur
        String nom = owner.getName();
        String sexe = identity.getSex() != null ? identity.getSex() : "Non défini";
        String age = identity.getAge() > 0 ? String.valueOf(identity.getAge()) : "Non défini";
        String taille = identity.getHeight() > 0 ? identity.getHeight() + " cm" : "Non défini";
        String ville = identity.hasResidenceCity() ? identity.getResidenceCity() : "Aucune";

        viewer.sendMessage("");
        viewer.sendMessage(ChatColor.GOLD + "╔══════════════════════════════════╗");
        viewer.sendMessage(ChatColor.GOLD + "║    " + ChatColor.WHITE + ChatColor.BOLD + "CARTE D'IDENTITÉ" + ChatColor.GOLD + "             ║");
        viewer.sendMessage(ChatColor.GOLD + "╠══════════════════════════════════╣");
        viewer.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Nom: " + ChatColor.WHITE + nom);
        viewer.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Sexe: " + ChatColor.WHITE + sexe);
        viewer.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Âge: " + ChatColor.WHITE + age + " ans");
        viewer.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Taille: " + ChatColor.WHITE + taille);
        viewer.sendMessage(ChatColor.GOLD + "║ " + ChatColor.YELLOW + "Ville: " + ChatColor.WHITE + ville);
        viewer.sendMessage(ChatColor.GOLD + "╚══════════════════════════════════╝");
        viewer.sendMessage("");
    }

    // =========================================================================
    // Utilitaires
    // =========================================================================

    public boolean isWithinDistance(Player p1, Player p2) {
        if (!p1.getWorld().equals(p2.getWorld())) return false;
        return p1.getLocation().distance(p2.getLocation()) <= MAX_INTERACTION_DISTANCE;
    }

    private void cleanupRequest(InteractionRequest request) {
        List<UUID> targetRequests = requestsByTarget.get(request.getTargetId());
        if (targetRequests != null) {
            targetRequests.remove(request.getRequestId());
            if (targetRequests.isEmpty()) {
                requestsByTarget.remove(request.getTargetId());
            }
        }
    }

    /**
     * Vérifie si le joueur a déjà une requête en attente du même type
     */
    public boolean hasPendingRequest(UUID requesterId, UUID targetId, InteractionRequest.RequestType type) {
        for (InteractionRequest req : pendingRequests.values()) {
            if (req.getRequesterId().equals(requesterId) &&
                req.getTargetId().equals(targetId) &&
                req.getType() == type &&
                !req.isExpired()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Nettoie les requêtes expirées
     */
    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                Iterator<Map.Entry<UUID, InteractionRequest>> it = pendingRequests.entrySet().iterator();
                while (it.hasNext()) {
                    InteractionRequest req = it.next().getValue();
                    if (req.isExpired()) {
                        it.remove();
                        cleanupRequest(req);

                        // Notifier le demandeur
                        Player requester = Bukkit.getPlayer(req.getRequesterId());
                        if (requester != null && requester.isOnline()) {
                            String typeMsg = switch (req.getType()) {
                                case FRISK -> "de fouille";
                                case REQUEST_ID -> "de carte d'identité";
                                case SHOW_ID -> "d'affichage d'identité";
                            };
                            requester.sendMessage(ChatColor.GRAY + "Votre demande " + typeMsg + " à " + req.getTargetName() + " a expiré.");
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L * 5, 20L * 5); // Toutes les 5 secondes
    }
}
