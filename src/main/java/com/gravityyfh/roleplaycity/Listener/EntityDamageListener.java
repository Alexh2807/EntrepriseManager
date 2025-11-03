package com.gravityyfh.roleplaycity.Listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import java.util.logging.Level;

public class EntityDamageListener implements Listener {

    private final EntrepriseManagerLogic entrepriseLogic;
    private final RoleplayCity plugin;

    public EntityDamageListener(RoleplayCity plugin, EntrepriseManagerLogic entrepriseLogic) {
        this.plugin = plugin;
        this.entrepriseLogic = entrepriseLogic;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPotentialFatalDamage(EntityDamageByEntityEvent event) {
        // On ne vérifie que si l'attaquant est un joueur
        if (!(event.getDamager() instanceof Player)) {
            return;
        }
        Player attacker = (Player) event.getDamager();

        // Ignorer le mode créatif
        if (attacker.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        // On ne vérifie que les entités vivantes
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        LivingEntity livingVictim = (LivingEntity) event.getEntity();

        // On vérifie si ce coup est fatal
        if (event.getFinalDamage() >= livingVictim.getHealth()) {
            String entityTypeName = livingVictim.getType().name();

            // On appelle TOUJOURS la logique de restriction.
            // C'est la méthode centrale qui déterminera, en lisant la config, si cette entité est restreinte.
            plugin.getLogger().log(Level.INFO, "[DEBUG Damage] Coup fatal détecté par " + attacker.getName() + " sur " + entityTypeName + ". Vérification de la restriction...");

            boolean isBlocked = entrepriseLogic.verifierEtGererRestrictionAction(attacker, "ENTITY_KILL", entityTypeName, 1);

            if (isBlocked) {
                // La limite horaire est atteinte ! Annuler le coup fatal.
                plugin.getLogger().log(Level.INFO, "[DEBUG Damage] Restriction active. Dégâts annulés pour " + attacker.getName() + " sur " + entityTypeName);
                event.setCancelled(true);
                // Le message d'erreur est déjà envoyé par verifierEtGererRestrictionAction
            } else {
                // La limite n'est pas atteinte (ou le joueur est membre autorisé)
                plugin.getLogger().log(Level.INFO, "[DEBUG Damage] Restriction non active ou limite OK. Dégâts autorisés pour " + attacker.getName() + " sur " + entityTypeName);
                // L'événement continue, l'entité va mourir, et EntityDeathListener enregistrera l'action productive.
            }
        }
    }
}