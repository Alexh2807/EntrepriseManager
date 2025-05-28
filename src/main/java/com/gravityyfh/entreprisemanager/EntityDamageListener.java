package com.gravityyfh.entreprisemanager;

import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.EnumSet; // Pour stocker les types restreints
import java.util.logging.Level;

public class EntityDamageListener implements Listener {

    private final EntrepriseManagerLogic entrepriseLogic;
    private final EntrepriseManager plugin;
    // Garder une liste des types restreints pour éviter de vérifier la config à chaque coup
    // NOTE : Cette liste devrait être mise à jour si la config est rechargée via /entreprise admin reload
    // Pour simplifier, on ne le fait pas ici, mais c'est une optimisation possible.
    private final EnumSet<EntityType> restrictedKillTypes = EnumSet.of(
            EntityType.COW, EntityType.PIG, EntityType.SHEEP,
            EntityType.CHICKEN, EntityType.RABBIT // Ajoutez ZOMBIE, SKELETON etc. si vous avez Chasseur
    );

    public EntityDamageListener(EntrepriseManager plugin, EntrepriseManagerLogic entrepriseLogic) {
        this.plugin = plugin;
        this.entrepriseLogic = entrepriseLogic;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true) // HIGH pour vérifier avant d'autres plugins potentiels
    public void onPotentialFatalDamage(EntityDamageByEntityEvent event) {
        // Vérifier si l'attaquant est un joueur
        if (!(event.getDamager() instanceof Player)) {
            return;
        }
        Player attacker = (Player) event.getDamager();

        // Ignorer si en créatif
        if (attacker.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        // Vérifier si la victime est une LivingEntity et si son type est restreint
        Entity victim = event.getEntity();
        if (!(victim instanceof LivingEntity)) {
            return;
        }
        LivingEntity livingVictim = (LivingEntity) victim;
        EntityType victimType = livingVictim.getType();

        // Vérifier si ce type d'entité fait partie de ceux dont le kill est restreint
        // On pourrait lire la config ici, mais utiliser une liste pré-chargée est plus performant.
        // ATTENTION : Cette liste NE contient QUE les types dont le KILL peut être restreint.
        // Il faut s'assurer qu'elle correspond aux clés sous action_restrictions.ENTITY_KILL dans TOUS les types d'entreprise.
        boolean isKillPotentiallyRestricted = restrictedKillTypes.contains(victimType);
        // Alternative (moins performante, mais dynamique):
        // boolean isKillPotentiallyRestricted = entrepriseLogic.isEntityTypeKillRestrictedInAnyConfig(victimType.name());
        // Il faudrait créer la méthode isEntityTypeKillRestrictedInAnyConfig dans EntrepriseManagerLogic

        if (!isKillPotentiallyRestricted) {
            return; // Ce type d'entité n'est jamais restreint, on ne fait rien.
        }

        // Vérifier si ce coup est fatal
        if (event.getFinalDamage() >= livingVictim.getHealth()) {
            // Ce coup VA tuer l'entité. Vérifions la restriction horaire.
            String entityTypeName = victimType.name();
            plugin.getLogger().log(Level.INFO, "[DEBUG Damage] Coup fatal détecté par " + attacker.getName() + " sur " + entityTypeName + ". Vérification restriction kill...");

            boolean isBlocked = entrepriseLogic.verifierEtGererRestrictionAction(attacker, "ENTITY_KILL", entityTypeName, 1);

            if (isBlocked) {
                // La limite horaire est atteinte ! Annuler le coup fatal.
                plugin.getLogger().log(Level.INFO, "[DEBUG Damage] Restriction kill active. Annulation des dégâts pour " + attacker.getName() + " sur " + entityTypeName);
                event.setCancelled(true);
                // Le message d'erreur est déjà envoyé par verifierEtGererRestrictionAction
            } else {
                // La limite n'est pas atteinte (ou le joueur est membre autorisé)
                plugin.getLogger().log(Level.INFO, "[DEBUG Damage] Restriction kill non active ou limite OK. Dégâts autorisés pour " + attacker.getName() + " sur " + entityTypeName);
                // L'événement continue, l'entité va mourir, et EntityDeathListener enregistrera l'action.
            }
        }
    }
}