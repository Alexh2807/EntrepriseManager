package com.gravityyfh.entreprisemanager.Services;

import com.gravityyfh.entreprisemanager.EntrepriseManager;
import com.gravityyfh.entreprisemanager.EntrepriseManagerLogic;
import com.gravityyfh.entreprisemanager.Models.ActionInfo;
import com.gravityyfh.entreprisemanager.Models.DetailedActionType;
import com.gravityyfh.entreprisemanager.Models.EmployeeActivityRecord;
import com.gravityyfh.entreprisemanager.Models.Entreprise;
import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.TownyPermission;
import com.palmergames.bukkit.towny.utils.PlayerCacheUtil;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ActivityService {

    private final EntrepriseManagerLogic logic;
    private final EntrepriseManager plugin;
    private static final long INACTIVITY_THRESHOLD_SECONDS = 15;

    public ActivityService(EntrepriseManagerLogic logic) {
        this.logic = logic;
        this.plugin = logic.plugin;
    }

    public void recordProductiveAction(Player player, String actionTypeString, Material material, int quantite, Block block) {
        EntrepriseManagerLogic.Entreprise entreprise = logic.getEntrepriseDuJoueur(player);
        if (entreprise == null) return;

        String typeEntreprise = entreprise.getType();
        String materialPathConfig = "types-entreprise." + typeEntreprise + ".activites-payantes." + actionTypeString.toUpperCase() + "." + material.name();
        boolean estActionValorisee = plugin.getConfig().contains(materialPathConfig);

        if (actionTypeString.equalsIgnoreCase("BLOCK_BREAK") && block != null && !canPlayerInteract(player, block.getLocation(), TownyPermission.ActionType.DESTROY)) {
            return;
        } else if (actionTypeString.equalsIgnoreCase("BLOCK_PLACE") && block != null && !canPlayerInteract(player, block.getLocation(), TownyPermission.ActionType.BUILD)) {
            return;
        }

        EntrepriseManagerLogic.EmployeeActivityRecord activityRecord = entreprise.getOrCreateEmployeeActivityRecord(player.getUniqueId(), player.getName());
        String genericActionKey = actionTypeString.toUpperCase() + ":" + material.name();
        double valeurUnitaire = estActionValorisee ? plugin.getConfig().getDouble(materialPathConfig, 0.0) : 0.0;
        double valeurTotaleAction = valeurUnitaire * quantite;

        DetailedActionType detailedActionType;
        try {
            detailedActionType = DetailedActionType.valueOf(actionTypeString.toUpperCase().replace("CRAFT_ITEM", "ITEM_CRAFTED").replace("BLOCK_BREAK", "BLOCK_BROKEN").replace("BLOCK_PLACE", "BLOCK_PLACED"));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().severe("Type d'action détaillé INCONNU pour enregistrement productif : '" + actionTypeString + "'.");
            detailedActionType = null;
        }

        if (detailedActionType != null) {
            activityRecord.recordAction(genericActionKey, valeurTotaleAction, quantite, detailedActionType, material);
            entreprise.addGlobalProductionRecord(LocalDateTime.now(), material, quantite, player.getUniqueId().toString(), detailedActionType);
        }

        if (valeurTotaleAction > 0) {
            logic.getActiviteHoraireValeur().merge(entreprise.getNom(), valeurTotaleAction, Double::sum);
        }
    }

    // Surcharge pour les actions sans bloc (ex: craft)
    public void recordProductiveAction(Player player, String actionTypeString, Material material, int quantite) {
        recordProductiveAction(player, actionTypeString, material, quantite, null);
    }

    // Surcharge pour les entités
    public void recordProductiveActionForEntity(Player player, String entityTypeName, int quantite) {
        EntrepriseManagerLogic.Entreprise entreprise = logic.getEntrepriseDuJoueur(player);
        if (entreprise == null) return;

        String configPathValeur = "types-entreprise." + entreprise.getType() + ".activites-payantes.ENTITY_KILL." + entityTypeName.toUpperCase();
        double valeurUnitaire = plugin.getConfig().getDouble(configPathValeur, 0.0);
        double valeurTotaleAction = valeurUnitaire * quantite;

        if (valeurTotaleAction > 0) {
            logic.getActiviteHoraireValeur().merge(entreprise.getNom(), valeurTotaleAction, Double::sum);
        }

        // Note: La logique de log détaillé pour les entités peut être ajoutée ici si nécessaire
    }

    public boolean checkAndManageActionRestriction(Player player, String actionTypeString, String targetName, int quantite) {
        EntrepriseManagerLogic.Entreprise entrepriseJoueurObj = logic.getEntrepriseDuJoueur(player);
        ConfigurationSection typesEntreprisesConfig = plugin.getConfig().getConfigurationSection("types-entreprise");
        if (typesEntreprisesConfig == null) return false;

        for (String typeEntSpecialise : typesEntreprisesConfig.getKeys(false)) {
            String restrictionPath = "types-entreprise." + typeEntSpecialise + ".action_restrictions." + actionTypeString.toUpperCase() + "." + targetName.toUpperCase();
            if (plugin.getConfig().contains(restrictionPath)) {
                boolean estMembreDeCeTypeSpecialise = (entrepriseJoueurObj != null && entrepriseJoueurObj.getType().equals(typeEntSpecialise));
                if (estMembreDeCeTypeSpecialise) return false; // Autorisé

                int limiteNonMembre = plugin.getConfig().getInt("types-entreprise." + typeEntSpecialise + ".limite-non-membre-par-heure", -1);
                if (limiteNonMembre == -1) continue;

                List<String> messagesErreur = plugin.getConfig().getStringList("types-entreprise." + typeEntSpecialise + ".message-erreur-restriction");
                if (limiteNonMembre == 0) {
                    messagesErreur.forEach(msg -> player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg.replace("%limite%", "0"))));
                    return true; // Bloqué
                }

                String actionIdPourRestriction = typeEntSpecialise + "_" + actionTypeString.toUpperCase() + "_" + targetName.toUpperCase();
                ActionInfo info = logic.getJoueurActivitesRestrictions()
                        .computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                        .computeIfAbsent(actionIdPourRestriction, k -> new ActionInfo());

                synchronized(info) {
                    if (info.getNombreActions() + quantite > limiteNonMembre) {
                        messagesErreur.forEach(msg -> player.sendMessage(ChatColor.translateAlternateColorCodes('&', msg.replace("%limite%", String.valueOf(limiteNonMembre)))));
                        player.sendMessage(ChatColor.GRAY + "(Limite atteinte: " + info.getNombreActions() + "/" + limiteNonMembre + ")");
                        return true; // Bloqué
                    } else {
                        info.incrementerActions(quantite);
                    }
                }
            }
        }
        return false; // Autorisé
    }

    public void checkEmployeeActivity() {
        LocalDateTime now = LocalDateTime.now();
        for (Entreprise entreprise : logic.getEntreprisesMap().values()) {
            for (EmployeeActivityRecord record : entreprise.getEmployeeActivityRecords().values()) {
                if (!record.isActive()) continue;
                if (record.lastActivityTime != null && Duration.between(record.lastActivityTime, now).toSeconds() >= INACTIVITY_THRESHOLD_SECONDS) {
                    record.endSession();
                }
            }
        }
    }

    public void resetHourlyLimitsForAllPlayers() {
        logic.getJoueurActivitesRestrictions().clear();
        plugin.getLogger().info("Les limites d'actions horaires pour les non-membres ont été réinitialisées.");
    }

    private boolean canPlayerInteract(Player player, Location location, TownyPermission.ActionType actionType) {
        if (plugin.getServer().getPluginManager().getPlugin("Towny") == null) return true;
        try {
            return PlayerCacheUtil.getCachePermission(player, location, location.getBlock().getType(), actionType);
        } catch (Exception e) {
            return false;
        }
    }
}