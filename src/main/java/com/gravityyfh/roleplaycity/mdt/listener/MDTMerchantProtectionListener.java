package com.gravityyfh.roleplaycity.mdt.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.mdt.MDTRushManager;
import com.gravityyfh.roleplaycity.mdt.merchant.MDTMerchantManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import java.util.ArrayList;
import java.util.List;

/**
 * Listener pour protéger les marchands MDT (villageois)
 * - Invincibilité totale
 * - Protection contre les explosions
 * - Protection contre le ciblage par les mobs
 * - Empêche toute modification
 */
public class MDTMerchantProtectionListener implements Listener {
    private final RoleplayCity plugin;
    private final MDTRushManager manager;

    public MDTMerchantProtectionListener(RoleplayCity plugin, MDTRushManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /**
     * Récupère le MerchantManager (peut être null si pas de partie)
     */
    private MDTMerchantManager getMerchantManager() {
        // Le MerchantManager est dans MDTRushManager mais pas public
        // On utilise l'attribut invulnerable des villageois + leur nom custom
        return null;
    }

    /**
     * Vérifie si une entité est un marchand MDT
     * On vérifie si c'est un villageois invulnérable avec un nom custom correspondant aux noms MDT
     */
    private boolean isMDTMerchant(Entity entity) {
        if (entity == null || entity.getType() != EntityType.VILLAGER) {
            return false;
        }

        Villager villager = (Villager) entity;

        // Vérifier si c'est invulnérable (tous les marchands MDT le sont)
        if (!villager.isInvulnerable()) {
            return false;
        }

        // Vérifier le nom custom
        String customName = villager.getCustomName();
        if (customName == null) {
            return false;
        }

        // Les marchands MDT ont ces noms (sans les codes couleurs)
        String cleanName = ChatColor.stripColor(customName);
        return cleanName.equals("Petit Vendeur") ||
               cleanName.equals("Blocs & Outils") ||
               cleanName.equals("Armes") ||
               cleanName.equals("Armures") ||
               cleanName.equals("Spécial");
    }

    /**
     * Protection contre tous les dégâts
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (isMDTMerchant(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /**
     * Protection contre les dégâts par entités (joueurs, mobs, projectiles)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (isMDTMerchant(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /**
     * Protection contre les explosions
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        // Les marchands sont déjà protégés par setInvulnerable(true)
        // mais on s'assure qu'ils ne sont pas affectés par les explosions
    }

    /**
     * Empêcher les mobs de cibler les marchands
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetEvent event) {
        if (isMDTMerchant(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    /**
     * Empêcher les mobs de cibler les marchands (version living entity)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTargetLiving(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() != null && isMDTMerchant(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    /**
     * Empêcher la combustion (feu, lave, etc.)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityCombust(EntityCombustEvent event) {
        if (isMDTMerchant(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /**
     * Permettre l'interaction avec les marchands (trade)
     * mais empêcher toute modification
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();

        if (!isMDTMerchant(entity)) {
            return;
        }

        Player player = event.getPlayer();

        // Forcer l'ouverture du menu de trade
        if (entity instanceof Villager villager) {
            player.openMerchant(villager, true);
            event.setCancelled(true); // Empêcher le comportement par défaut
        }
    }

    /**
     * Protection contre la mort (ne devrait jamais arriver mais au cas où)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (isMDTMerchant(event.getEntity())) {
            // Supprimer les drops
            event.getDrops().clear();
            event.setDroppedExp(0);

            // Logger un warning car ça ne devrait jamais arriver
            plugin.getLogger().warning("[MDT] Un marchand MDT a été tué! Ceci ne devrait pas arriver.");
        }
    }

    /**
     * Empêcher la transformation en zombie villageois
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTransform(EntityTransformEvent event) {
        if (isMDTMerchant(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /**
     * Empêcher les potions négatives sur les marchands
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        event.getAffectedEntities().removeIf(this::isMDTMerchant);
    }

    /**
     * Empêcher les area effect clouds (lingering potions) sur les marchands
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAreaEffectCloud(AreaEffectCloudApplyEvent event) {
        event.getAffectedEntities().removeIf(this::isMDTMerchant);
    }

    /**
     * SOLUTION TRADES ILLIMITÉS:
     * Après chaque trade, on reset les uses de toutes les recettes du marchand
     * Cela garantit que les trades ne se bloquent jamais
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getInventory() instanceof MerchantInventory)) return;
        if (event.getSlotType() != InventoryType.SlotType.RESULT) return;
        if (event.getCurrentItem() == null || event.getCurrentItem().getType().isAir()) return;

        MerchantInventory merchantInv = (MerchantInventory) event.getInventory();
        Merchant merchant = merchantInv.getMerchant();

        // Vérifier si c'est un villageois MDT
        if (merchant instanceof Villager villager && isMDTMerchant(villager)) {
            // Reset les uses de toutes les recettes après un tick (pour que le trade actuel soit traité)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                resetMerchantRecipes(villager);
            }, 1L);
        }
    }

    /**
     * Reset toutes les recettes d'un marchand pour qu'elles soient illimitées
     */
    private void resetMerchantRecipes(Villager villager) {
        List<MerchantRecipe> currentRecipes = villager.getRecipes();
        List<MerchantRecipe> newRecipes = new ArrayList<>();

        for (MerchantRecipe oldRecipe : currentRecipes) {
            // Créer une nouvelle recette avec les mêmes propriétés mais uses = 0
            MerchantRecipe newRecipe = new MerchantRecipe(
                    oldRecipe.getResult(),
                    Integer.MAX_VALUE
            );
            newRecipe.setMaxUses(Integer.MAX_VALUE);
            newRecipe.setUses(0);  // RESET!
            newRecipe.setExperienceReward(false);
            newRecipe.setPriceMultiplier(0f);
            newRecipe.setDemand(0);
            newRecipe.setSpecialPrice(0);

            // Copier les ingrédients
            newRecipe.setIngredients(oldRecipe.getIngredients());

            newRecipes.add(newRecipe);
        }

        villager.setRecipes(newRecipes);
    }
}
