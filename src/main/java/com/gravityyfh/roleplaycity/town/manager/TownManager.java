package com.gravityyfh.roleplaycity.town.manager;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownMember;
import com.gravityyfh.roleplaycity.town.data.TownRole;
import com.gravityyfh.roleplaycity.town.event.TownDeleteEvent;
import com.gravityyfh.roleplaycity.town.event.TownMemberLeaveEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class TownManager {
    private final RoleplayCity plugin;
    private final Map<String, Town> towns; // townName -> Town
    private final Map<UUID, String> playerTowns; // playerUUID -> townName

    // Système de sauvegarde asynchrone avec debouncing
    private final AtomicBoolean savePending = new AtomicBoolean(false);
    private BukkitTask saveTask = null;
    private static final long SAVE_DELAY_TICKS = 20L; // 1 seconde de délai

    public TownManager(RoleplayCity plugin) {
        this.plugin = plugin;
        this.towns = new ConcurrentHashMap<>();
        this.playerTowns = new ConcurrentHashMap<>();
    }

    // === CRÉATION ET SUPPRESSION ===

    public boolean createTown(String townName, Player mayor, double creationCost) {
        // Vérifications
        if (townExists(townName)) {
            return false;
        }

        if (getPlayerTown(mayor.getUniqueId()) != null) {
            return false; // Déjà dans une ville
        }

        // Vérifier l'économie
        if (!RoleplayCity.getEconomy().has(mayor, creationCost)) {
            return false;
        }

        // Prélever le coût
        RoleplayCity.getEconomy().withdrawPlayer(mayor, creationCost);

        // Créer la ville
        Town town = new Town(townName, mayor.getUniqueId(), mayor.getName());
        towns.put(townName, town);
        playerTowns.put(mayor.getUniqueId(), townName);

        plugin.getLogger().info("Ville créée: " + townName + " par " + mayor.getName());

        // Sauvegarder immédiatement
        saveTownsNow();

        // Afficher le tutoriel au maire
        showTownCreationTutorial(mayor, townName);

        return true;
    }

    /**
     * Affiche un tutoriel complet au maire après la création de sa ville
     */
    private void showTownCreationTutorial(Player mayor, String townName) {
        double claimCost = plugin.getConfig().getDouble("town.claim-cost-per-chunk", 500.0);

        mayor.sendMessage("");
        mayor.sendMessage(ChatColor.GOLD + "════════════════════════════════════════════════════");
        mayor.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "🏛️ FÉLICITATIONS, MAIRE DE " + townName.toUpperCase() + " !");
        mayor.sendMessage(ChatColor.GOLD + "════════════════════════════════════════════════════");
        mayor.sendMessage("");
        mayor.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "📋 PREMIERS PAS POUR DÉVELOPPER VOTRE VILLE :");
        mayor.sendMessage("");
        mayor.sendMessage(ChatColor.AQUA + "1. ALIMENTER LA BANQUE DE LA VILLE");
        mayor.sendMessage(ChatColor.GRAY + "   → Utilisez " + ChatColor.WHITE + "/ville" + ChatColor.GRAY + " puis " + ChatColor.WHITE + "Banque de la Ville");
        mayor.sendMessage(ChatColor.GRAY + "   → Déposez de l'argent pour financer les claims");
        mayor.sendMessage(ChatColor.GRAY + "   → Sans argent, impossible de revendiquer du terrain !");
        mayor.sendMessage("");
        mayor.sendMessage(ChatColor.AQUA + "2. REVENDIQUER VOS PREMIERS TERRAINS (CLAIMS)");
        mayor.sendMessage(ChatColor.GRAY + "   → Coût : " + ChatColor.GOLD + String.format("%.2f€", claimCost) + ChatColor.GRAY + " par chunk (256m²)");
        mayor.sendMessage(ChatColor.GRAY + "   → Menu : " + ChatColor.WHITE + "/ville" + ChatColor.GRAY + " → " + ChatColor.WHITE + "Gestion des Claims");
        mayor.sendMessage(ChatColor.GRAY + "   → L'argent est prélevé de la banque de ville");
        mayor.sendMessage(ChatColor.YELLOW + "   ⚠ Important: " + ChatColor.GRAY + "Les claims doivent être adjacents (côte à côte)!");
        mayor.sendMessage("");
        mayor.sendMessage(ChatColor.AQUA + "3. RECRUTER DES MÉTIERS MUNICIPAUX");
        mayor.sendMessage(ChatColor.GRAY + "   → Menu : " + ChatColor.WHITE + "/ville" + ChatColor.GRAY + " → " + ChatColor.WHITE + "Gestion des Membres");
        mayor.sendMessage("");
        mayor.sendMessage(ChatColor.YELLOW + "   ⚖️ " + ChatColor.WHITE + "ADJOINT" + ChatColor.GRAY + " - Votre bras droit (gestion ville, claims, économie)");
        mayor.sendMessage(ChatColor.YELLOW + "   👮 " + ChatColor.WHITE + "POLICIER" + ChatColor.GRAY + " - Maintien de l'ordre (amendes, alertes)");
        mayor.sendMessage(ChatColor.YELLOW + "   ⚖️ " + ChatColor.WHITE + "JUGE" + ChatColor.GRAY + " - Justice (jugement des affaires, relaxes)");
        mayor.sendMessage(ChatColor.YELLOW + "   🏗️ " + ChatColor.WHITE + "ARCHITECTE" + ChatColor.GRAY + " - Construction (bâtiments municipaux)");
        mayor.sendMessage("");
        mayor.sendMessage(ChatColor.AQUA + "4. DÉVELOPPER VOTRE ÉCONOMIE");
        mayor.sendMessage(ChatColor.GRAY + "   → Vendez/Louez des terrains aux citoyens");
        mayor.sendMessage(ChatColor.GRAY + "   → Créez des entreprises pour vos citoyens");
        mayor.sendMessage(ChatColor.GRAY + "   → Gérez les taxes et l'économie municipale");
        mayor.sendMessage("");
        mayor.sendMessage(ChatColor.GOLD + "════════════════════════════════════════════════════");
        mayor.sendMessage(ChatColor.GREEN + "💡 Conseil : " + ChatColor.GRAY + "Commencez par déposer au minimum " +
            ChatColor.GOLD + String.format("%.2f€", claimCost * 5) + ChatColor.GRAY + " dans");
        mayor.sendMessage(ChatColor.GRAY + "   la banque pour pouvoir revendiquer 5 chunks de départ.");
        mayor.sendMessage(ChatColor.GOLD + "════════════════════════════════════════════════════");
        mayor.sendMessage("");
    }

    public boolean deleteTown(String townName) {
        Town town = towns.remove(townName);
        if (town == null) {
            return false;
        }

        // Déclencher l'événement de suppression de ville
        TownDeleteEvent event = new TownDeleteEvent(townName);
        Bukkit.getPluginManager().callEvent(event);

        // Retirer tous les membres
        for (UUID memberUuid : town.getMembers().keySet()) {
            playerTowns.remove(memberUuid);
        }

        // NOUVEAU : Supprimer toutes les mailboxes de cette ville
        if (plugin.getMailboxManager() != null) {
            plugin.getMailboxManager().removeAllMailboxesFromTown(townName);
        }

        plugin.getLogger().info("Ville supprimée: " + townName);

        // Sauvegarder immédiatement
        saveTownsNow();

        return true;
    }

    public boolean renameTown(String oldName, String newName, double renameCost) {
        if (!townExists(oldName) || townExists(newName)) {
            return false;
        }

        Town town = towns.get(oldName);

        // Vérifier le solde de la ville
        if (town.getBankBalance() < renameCost) {
            return false;
        }

        // Prélever le coût
        town.withdraw(renameCost);

        // Renommer (créer nouvelle entrée et supprimer l'ancienne)
        towns.remove(oldName);
        towns.put(newName, town);

        // Mettre à jour les références des joueurs
        for (UUID memberUuid : town.getMembers().keySet()) {
            playerTowns.put(memberUuid, newName);
        }

        // NOUVEAU : Mettre à jour le nom de ville dans toutes les mailboxes
        if (plugin.getMailboxManager() != null) {
            plugin.getMailboxManager().renameTownInMailboxes(oldName, newName);
        }

        plugin.getLogger().info("Ville renommée: " + oldName + " -> " + newName);

        // Sauvegarder immédiatement
        saveTownsNow();

        return true;
    }

    // === GESTION DES MEMBRES ===

    public boolean invitePlayer(String townName, Player inviter, Player invited) {
        Town town = getTown(townName);
        if (town == null) {
            return false;
        }

        // Vérifier les permissions
        if (!town.isMember(inviter.getUniqueId())) {
            return false;
        }

        TownRole inviterRole = town.getMemberRole(inviter.getUniqueId());
        if (inviterRole != TownRole.MAIRE && inviterRole != TownRole.ADJOINT) {
            return false;
        }

        // Vérifier que le joueur invité n'est pas déjà dans une ville
        if (getPlayerTown(invited.getUniqueId()) != null) {
            return false;
        }

        // Ajouter l'invitation
        town.invitePlayer(invited.getUniqueId());

        // Envoyer notification au joueur invité
        plugin.getNotificationManager().sendNotification(
            invited.getUniqueId(),
            com.gravityyfh.roleplaycity.town.manager.NotificationManager.NotificationType.SOCIAL,
            "Invitation à rejoindre une ville",
            String.format("%s vous invite à rejoindre la ville de %s. Utilisez /ville join %s pour accepter.",
                inviter.getName(), townName, townName)
        );

        return true;
    }

    public boolean acceptInvitation(Player player, String townName) {
        Town town = getTown(townName);
        if (town == null) {
            return false;
        }

        // Vérifier l'invitation
        if (!town.hasInvitation(player.getUniqueId())) {
            return false;
        }

        // Vérifier que le joueur n'est pas déjà dans une ville
        if (getPlayerTown(player.getUniqueId()) != null) {
            return false;
        }

        // Ajouter le joueur
        town.addMember(player.getUniqueId(), player.getName(), TownRole.CITOYEN);
        town.cancelInvitation(player.getUniqueId());
        playerTowns.put(player.getUniqueId(), townName);

        // Notification au nouveau membre
        plugin.getNotificationManager().sendNotification(
            player.getUniqueId(),
            com.gravityyfh.roleplaycity.town.manager.NotificationManager.NotificationType.INFO,
            "Bienvenue dans la ville !",
            String.format("Vous avez rejoint la ville de %s en tant que Citoyen.", townName)
        );

        // Notification au Maire
        plugin.getNotificationManager().sendNotification(
            town.getMayorUuid(),
            com.gravityyfh.roleplaycity.town.manager.NotificationManager.NotificationType.SOCIAL,
            "Nouveau membre",
            String.format("%s a rejoint votre ville !", player.getName())
        );

        return true;
    }

    public boolean refuseInvitation(Player player, String townName) {
        Town town = getTown(townName);
        if (town == null) {
            return false;
        }

        town.cancelInvitation(player.getUniqueId());
        return true;
    }

    public boolean joinTown(Player player, String townName, double joinCost) {
        Town town = getTown(townName);
        if (town == null) {
            return false;
        }

        // Vérifier que le joueur n'est pas déjà dans une ville
        if (getPlayerTown(player.getUniqueId()) != null) {
            return false;
        }

        // Vérifier l'économie
        if (!RoleplayCity.getEconomy().has(player, joinCost)) {
            return false;
        }

        // Prélever le coût
        RoleplayCity.getEconomy().withdrawPlayer(player, joinCost);

        // Ajouter le joueur
        town.addMember(player.getUniqueId(), player.getName(), TownRole.CITOYEN);
        playerTowns.put(player.getUniqueId(), townName);

        // Déposer le coût dans la banque de la ville
        town.deposit(joinCost);

        // Sauvegarder immédiatement
        saveTownsNow();

        return true;
    }

    public boolean leaveTown(Player player) {
        String townName = getPlayerTown(player.getUniqueId());
        if (townName == null) {
            return false;
        }

        Town town = getTown(townName);
        if (town == null) {
            return false;
        }

        // Le maire ne peut pas quitter (il doit supprimer la ville ou transférer)
        if (town.isMayor(player.getUniqueId())) {
            return false;
        }

        // Déclencher l'événement de départ de membre
        TownMemberLeaveEvent event = new TownMemberLeaveEvent(townName, player.getUniqueId(), player.getName());
        Bukkit.getPluginManager().callEvent(event);

        // Retirer le joueur
        town.removeMember(player.getUniqueId());
        playerTowns.remove(player.getUniqueId());

        // Notification au joueur qui quitte
        plugin.getNotificationManager().sendNotification(
            player.getUniqueId(),
            com.gravityyfh.roleplaycity.town.manager.NotificationManager.NotificationType.INFO,
            "Vous avez quitté la ville",
            String.format("Vous avez quitté la ville de %s.", townName)
        );

        // Notification au Maire
        plugin.getNotificationManager().sendNotification(
            town.getMayorUuid(),
            com.gravityyfh.roleplaycity.town.manager.NotificationManager.NotificationType.INFO,
            "Membre parti",
            String.format("%s a quitté votre ville.", player.getName())
        );

        // Sauvegarder immédiatement
        saveTownsNow();

        return true;
    }

    public boolean kickMember(String townName, Player kicker, UUID kickedUuid) {
        Town town = getTown(townName);
        if (town == null) {
            return false;
        }

        // Vérifier les permissions
        TownRole kickerRole = town.getMemberRole(kicker.getUniqueId());
        if (kickerRole != TownRole.MAIRE && kickerRole != TownRole.ADJOINT) {
            return false;
        }

        // On ne peut pas kick le maire
        if (town.isMayor(kickedUuid)) {
            return false;
        }

        // Vérifier le pouvoir
        TownRole kickedRole = town.getMemberRole(kickedUuid);
        if (kickedRole != null && kickedRole.getPower() >= kickerRole.getPower()) {
            return false; // Ne peut pas kick quelqu'un de rang égal ou supérieur
        }

        // Récupérer le nom du joueur expulsé avant de le retirer
        TownMember kickedMember = town.getMember(kickedUuid);
        String kickedName = kickedMember != null ? kickedMember.getPlayerName() : kickedUuid.toString();

        // Déclencher l'événement de départ de membre
        TownMemberLeaveEvent event = new TownMemberLeaveEvent(townName, kickedUuid, kickedName);
        Bukkit.getPluginManager().callEvent(event);

        // Retirer le joueur
        town.removeMember(kickedUuid);
        playerTowns.remove(kickedUuid);

        // Notification au joueur expulsé
        plugin.getNotificationManager().sendNotification(
            kickedUuid,
            com.gravityyfh.roleplaycity.town.manager.NotificationManager.NotificationType.WARNING,
            "Vous avez été expulsé !",
            String.format("Vous avez été expulsé de la ville de %s par %s.", townName, kicker.getName())
        );

        // Notification au Maire si ce n'est pas lui qui a kick
        if (!town.isMayor(kicker.getUniqueId())) {
            plugin.getNotificationManager().sendNotification(
                town.getMayorUuid(),
                com.gravityyfh.roleplaycity.town.manager.NotificationManager.NotificationType.INFO,
                "Membre expulsé",
                String.format("%s a expulsé %s de la ville.", kicker.getName(), kickedName)
            );
        }

        // Sauvegarder immédiatement
        saveTownsNow();

        return true;
    }

    public boolean setMemberRole(String townName, Player changer, UUID targetUuid, TownRole newRole) {
        Town town = getTown(townName);
        if (town == null) {
            return false;
        }

        // Seul le maire peut changer les rôles
        if (!town.isMayor(changer.getUniqueId())) {
            return false;
        }

        // On ne peut pas changer le rôle du maire
        if (town.isMayor(targetUuid)) {
            return false;
        }

        // Récupérer l'ancien rôle et le nom du membre
        TownMember member = town.getMember(targetUuid);
        if (member == null) {
            return false;
        }
        TownRole oldRole = member.getRole();
        String targetName = member.getPlayerName();

        // Vérifier les restrictions de niveau pour les rôles municipaux (Policier, Juge, Médecin)
        if (newRole == com.gravityyfh.roleplaycity.town.data.TownRole.POLICIER ||
            newRole == com.gravityyfh.roleplaycity.town.data.TownRole.JUGE ||
            newRole == com.gravityyfh.roleplaycity.town.data.TownRole.MEDECIN) {

            com.gravityyfh.roleplaycity.town.manager.TownLevelManager.RoleAssignmentResult result =
                plugin.getTownLevelManager().canAssignRole(town, newRole);

            if (!result.canAssign()) {
                changer.sendMessage(result.getMessage());
                return false;
            }
        }

        // Changer le rôle
        town.setMemberRole(targetUuid, newRole);

        // Notification au membre concerné
        plugin.getNotificationManager().sendNotification(
            targetUuid,
            com.gravityyfh.roleplaycity.town.manager.NotificationManager.NotificationType.IMPORTANT,
            "Changement de rôle",
            String.format("Votre rôle dans %s a été changé de %s à %s par le Maire.",
                townName, oldRole.getDisplayName(), newRole.getDisplayName())
        );

        // Sauvegarder immédiatement
        saveTownsNow();

        return true;
    }

    // === GESTION DES CLAIMS ===

    public boolean claimChunk(String townName, org.bukkit.Chunk chunk, Player claimer) {
        Town town = getTown(townName);
        if (town == null) {
            return false;
        }

        // Vérifier les permissions
        TownRole role = town.getMemberRole(claimer.getUniqueId());
        if (role == null || (!role.canManageClaims() && role != TownRole.MAIRE)) {
            return false;
        }

        // Le coût et la logique de claim sont gérés par ClaimManager
        return true;
    }

    public boolean unclaimChunk(String townName, org.bukkit.Chunk chunk, Player claimer) {
        Town town = getTown(townName);
        if (town == null) {
            return false;
        }

        // Vérifier les permissions
        TownRole role = town.getMemberRole(claimer.getUniqueId());
        if (role == null || (!role.canManageClaims() && role != TownRole.MAIRE)) {
            return false;
        }

        return true;
    }

    // === GETTERS ===

    public Town getTown(String townName) {
        return towns.get(townName);
    }

    public String getPlayerTown(UUID playerUuid) {
        return playerTowns.get(playerUuid);
    }

    public Town getPlayerTownObject(UUID playerUuid) {
        String townName = getPlayerTown(playerUuid);
        return townName != null ? getTown(townName) : null;
    }

    public boolean townExists(String townName) {
        return towns.containsKey(townName);
    }

    public Collection<Town> getAllTowns() {
        return new ArrayList<>(towns.values());
    }

    public List<String> getTownNames() {
        return new ArrayList<>(towns.keySet());
    }

    public int getTownCount() {
        return towns.size();
    }

    // === UTILITAIRES ===

    public boolean isPlayerInTown(UUID playerUuid) {
        return playerTowns.containsKey(playerUuid);
    }

    public boolean isPlayerMayor(UUID playerUuid) {
        Town town = getPlayerTownObject(playerUuid);
        return town != null && town.isMayor(playerUuid);
    }

    public TownRole getPlayerRole(UUID playerUuid) {
        Town town = getPlayerTownObject(playerUuid);
        return town != null ? town.getMemberRole(playerUuid) : null;
    }

    public void cleanupExpiredInvitations() {
        int expirationHours = plugin.getConfig().getInt("town.invitation-expiration-hours", 24);
        for (Town town : towns.values()) {
            town.clearExpiredInvitations(expirationHours);
        }
    }

    // === CHARGEMENT/SAUVEGARDE ===

    public void loadTowns(Map<String, Town> loadedTowns) {
        towns.clear();
        playerTowns.clear();
        towns.putAll(loadedTowns);

        // Reconstruire l'index playerTowns
        for (Map.Entry<String, Town> entry : towns.entrySet()) {
            String townName = entry.getKey();
            Town town = entry.getValue();
            for (UUID memberUuid : town.getMembers().keySet()) {
                playerTowns.put(memberUuid, townName);
            }
        }

        plugin.getLogger().info("Chargé " + towns.size() + " villes avec " + playerTowns.size() + " citoyens.");
    }

    public Map<String, Town> getTownsForSave() {
        return new HashMap<>(towns);
    }

    /**
     * Sauvegarde asynchrone avec debouncing
     * Si plusieurs modifications arrivent rapidement, on attend avant de sauvegarder
     * pour éviter d'écrire 10 fois en 1 seconde
     */
    public void saveTownsNow() {
        // Si une sauvegarde est déjà planifiée, on ne fait rien (debouncing)
        if (savePending.get()) {
            return;
        }

        savePending.set(true);

        // Annuler la tâche précédente si elle existe
        if (saveTask != null && !saveTask.isCancelled()) {
            saveTask.cancel();
        }

        // Planifier la sauvegarde avec un délai de 1 seconde
        // Si d'autres modifications arrivent dans cette seconde, elles seront ignorées
        saveTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Sauvegarder de manière asynchrone pour ne pas bloquer le thread principal
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    if (plugin.getTownDataManager() != null) {
                        plugin.getTownDataManager().saveTowns(getTownsForSave());
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("Erreur lors de la sauvegarde asynchrone des villes: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    // Réinitialiser le flag pour permettre une nouvelle sauvegarde
                    savePending.set(false);
                }
            });
        }, SAVE_DELAY_TICKS);
    }

    /**
     * Sauvegarde synchrone immédiate (utilisée lors de l'arrêt du serveur)
     * Ne pas utiliser en jeu, préférer saveTownsNow()
     */
    public void saveTownsSync() {
        // Annuler toute sauvegarde asynchrone en attente
        if (saveTask != null && !saveTask.isCancelled()) {
            saveTask.cancel();
        }
        savePending.set(false);

        // Sauvegarder immédiatement et de manière synchrone
        if (plugin.getTownDataManager() != null) {
            plugin.getTownDataManager().saveTowns(getTownsForSave());
        }
    }

    // === MÉTHODES UTILITAIRES POUR TERRAINS D'ENTREPRISE ===

    /**
     * Récupère tous les terrains appartenant à une entreprise (via SIRET)
     */
    public List<com.gravityyfh.roleplaycity.town.data.Plot> getPlotsByCompanySiret(String siret, String townName) {
        List<com.gravityyfh.roleplaycity.town.data.Plot> companyPlots = new ArrayList<>();
        Town town = getTown(townName);

        if (town == null || siret == null) {
            return companyPlots;
        }

        for (com.gravityyfh.roleplaycity.town.data.Plot plot : town.getPlots().values()) {
            if (siret.equals(plot.getCompanySiret())) {
                companyPlots.add(plot);
            }
        }

        return companyPlots;
    }

    /**
     * Retourne un terrain à la ville (efface propriétaire et entreprise)
     */
    public void transferPlotToTown(com.gravityyfh.roleplaycity.town.data.Plot plot, String reason) {
        if (plot == null) {
            return;
        }

        // Sauvegarder les informations pour le log
        String previousOwner = plot.getOwnerName();
        String previousCompany = plot.getCompanyName();

        // Effacer la propriété
        clearPlotOwnership(plot);

        // Annuler vente/location
        plot.setForSale(false);
        plot.setForRent(false);
        plot.setSalePrice(0);
        plot.setRentPricePerDay(0);

        // Effacer le locataire
        plot.clearRenter();

        // Note: Avec le système unifié, plus besoin de retirer du groupe
        // Les terrains groupés sont gérés directement via Plot.isGrouped()

        plugin.getLogger().info(String.format(
            "[TownManager] Terrain %s:%d,%d retourné à la ville. Raison: %s. Ancien propriétaire: %s, Entreprise: %s",
            plot.getWorldName(), plot.getChunkX(), plot.getChunkZ(), reason,
            previousOwner != null ? previousOwner : "Aucun",
            previousCompany != null ? previousCompany : "Aucune"
        ));
    }

    /**
     * Efface la propriété d'un terrain (propriétaire, entreprise, dette)
     */
    public void clearPlotOwnership(com.gravityyfh.roleplaycity.town.data.Plot plot) {
        if (plot == null) {
            return;
        }

        plot.setOwner(null, null);
        plot.setCompany(null);
        plot.setCompanySiret(null);
        plot.resetDebt();
    }
}
