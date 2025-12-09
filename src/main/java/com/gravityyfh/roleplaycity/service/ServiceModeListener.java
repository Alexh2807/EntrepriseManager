package com.gravityyfh.roleplaycity.service;

import org.bukkit.event.Listener;

/**
 * Listener pour gérer les drops d'items en mode service.
 *
 * IMPORTANT: Ce listener est maintenant VIDE car toute la logique est gérée par
 * ServiceDropListener qui vérifie correctement les action_restrictions.
 *
 * HISTORIQUE DES BUGS CORRIGES:
 *
 * 1. onBlockDropItem annulait TOUS les drops sans vérifier les restrictions
 *    - Un agriculteur qui cassait du gravier perdait le silex
 *    - Maintenant géré par ServiceDropListener.onBlockBreak avec isActionAllowedForStorage()
 *
 * 2. onEntityDeath vidait TOUS les drops sans vérifier les restrictions
 *    - Un agriculteur qui tuait un zombie perdait la chair pourrie
 *    - Maintenant géré par ServiceDropListener.onEntityDeath avec isActionAllowedForStorage()
 *
 * 3. onCraftItem retirait TOUS les items du curseur sans vérifier les restrictions
 *    - Un agriculteur qui craftait une epee la perdait
 *    - Maintenant géré par CraftItemListener avec isActionAllowedForStorage()
 *
 * REGLE: En mode service, seuls les items qui font partie des action_restrictions
 * de l'entreprise vont au coffre. Les autres items vont dans l'inventaire normalement.
 */
public class ServiceModeListener implements Listener {

    /**
     * Constructeur conservé pour compatibilité avec RoleplayCity.java
     * Les paramètres ne sont plus utilisés car la logique est dans ServiceDropListener
     */
    public ServiceModeListener(
            com.gravityyfh.roleplaycity.RoleplayCity plugin,
            ServiceModeManager serviceModeManager,
            com.gravityyfh.roleplaycity.EntrepriseManagerLogic entrepriseLogic) {
        // Paramètres ignorés - classe vide
    }

    // Toute la logique est dans ServiceDropListener et CraftItemListener
}
