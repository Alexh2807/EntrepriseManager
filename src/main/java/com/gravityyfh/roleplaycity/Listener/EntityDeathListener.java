package com.gravityyfh.roleplaycity.Listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
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
    private final RoleplayCity plugin;

    public EntityDeathListener(RoleplayCity plugin, EntrepriseManagerLogic entrepriseLogic) {
        this.plugin = plugin;
        this.entrepriseLogic = entrepriseLogic;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        LivingEntity killerEntity = entity.getKiller();

        // On s'intéresse seulement aux morts causées par un joueur pour l'enregistrement
        if (!(killerEntity instanceof Player killer)) {
            return;
        }

        // Ignorer si le joueur était en créatif au moment du kill
        // Note: Techniquement, le GameMode pourrait changer entre le coup fatal et la mort,
        // mais c'est un cas très rare. On se base sur le GameMode au moment de la mort.
        if (killer.getGameMode() == GameMode.CREATIVE) {
            plugin.getLogger().log(Level.FINER, "EntityDeathEvent ignoré pour enregistrement (Tueur en créatif: " + killer.getName() + ")");
            return;
        }

        EntityType entityType = event.getEntityType();
        String entityTypeName = entityType.name();

        // NOTE: La vérification de quota est faite dans EntityDamageListener AVANT la mort
        // Si le quota était atteint, l'entité ne serait PAS morte (coup fatal annulé)
        // Donc ici, si on arrive dans EntityDeathEvent, c'est que le quota était OK

        // Enregistrer l'action productive (uniquement si elle est valorisée dans la config pour l'entreprise du joueur)
        plugin.getLogger().log(Level.FINE, "[DEBUG Kill] Enregistrement action pour " + killer.getName() + " tuant " + entityTypeName);
        entrepriseLogic.enregistrerActionProductive(killer, "ENTITY_KILL", entityTypeName, 1);
    }
}