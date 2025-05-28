package com.gravityyfh.entreprisemanager;

import org.bukkit.GameMode;
// Supprimez Material si non utilisé
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import java.util.logging.Level;

public class EntityDeathListener implements Listener {

    private final EntrepriseManagerLogic entrepriseLogic;
    private final EntrepriseManager plugin;

    public EntityDeathListener(EntrepriseManager plugin, EntrepriseManagerLogic entrepriseLogic) {
        this.plugin = plugin;
        this.entrepriseLogic = entrepriseLogic;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        LivingEntity killerEntity = entity.getKiller();

        // On s'intéresse seulement aux morts causées par un joueur pour l'enregistrement
        if (!(killerEntity instanceof Player)) {
            return;
        }

        Player killer = (Player) killerEntity;

        // Ignorer si le joueur était en créatif au moment du kill
        // Note: Techniquement, le GameMode pourrait changer entre le coup fatal et la mort,
        // mais c'est un cas très rare. On se base sur le GameMode au moment de la mort.
        if (killer.getGameMode() == GameMode.CREATIVE) {
            plugin.getLogger().log(Level.FINER, "EntityDeathEvent ignoré pour enregistrement (Tueur en créatif: " + killer.getName() + ")");
            return;
        }

        EntityType entityType = event.getEntityType();
        String entityTypeName = entityType.name();

        // --- Suppression de la vérification de restriction ici ---
        // La vérification et le blocage se font maintenant dans EntityDamageListener

        // Enregistrer l'action productive (uniquement si elle est valorisée dans la config pour l'entreprise du joueur)
        // Cette méthode ne fera rien si le joueur n'est pas dans une entreprise pertinente
        // ou si le kill de cette entité n'est pas dans 'activites-payantes'.
        plugin.getLogger().log(Level.INFO, "[DEBUG Kill] Enregistrement action pour " + killer.getName() + " tuant " + entityTypeName);
        entrepriseLogic.enregistrerActionProductive(killer, "ENTITY_KILL", entityTypeName, 1);

        // Note : Les drops et l'XP ne sont plus annulés ici car si l'action
        // devait être bloquée, l'entité n'aurait pas dû mourir grâce à EntityDamageListener.
    }
}