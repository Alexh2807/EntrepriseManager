package com.gravityyfh.roleplaycity.mdt.merchant;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.mdt.config.MDTConfig;
import com.gravityyfh.roleplaycity.mdt.config.MDTConfig.MerchantType;
import com.gravityyfh.roleplaycity.mdt.config.MDTConfig.TradeConfig;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MDTMerchantManager {
    private final RoleplayCity plugin;
    private final MDTConfig config;

    private final List<Villager> activeMerchants = new ArrayList<>();
    private BukkitTask tradeResetTask = null;

    public MDTMerchantManager(RoleplayCity plugin, MDTConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void spawnMerchants() {
        despawnMerchants();

        // IMPORTANT: Nettoyer TOUS les villageois existants aux positions des marchands
        // Cela résout le problème des villageois qui persistent après un restart/reload
        cleanupOldMerchants();

        List<MDTConfig.MerchantData> merchants = config.loadMerchants();
        if (merchants.isEmpty()) {
            plugin.getLogger().warning("[MDT] Aucun marchand configuré. Utilisez /mdt setup.");
            return;
        }

        for (MDTConfig.MerchantData data : merchants) {
            spawnMerchant(data.location, data.type);
        }

        // Démarrer le reset périodique des trades
        startTradeResetTask();
    }

    /**
     * Nettoie TOUS les villageois du monde MDT
     * Solution robuste: supprime tout villageois dans le monde MDT car il ne devrait pas y en avoir d'autres
     */
    private void cleanupOldMerchants() {
        World world = config.getWorld();
        if (world == null) return;

        int count = 0;
        // Supprimer TOUS les villageois du monde MDT (il ne devrait pas y en avoir d'autres)
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Villager) {
                entity.remove();
                count++;
            }
        }

        if (count > 0) {
            plugin.getLogger().info("[MDT] " + count + " villageois nettoyé(s) du monde MDT");
        }
    }

    private void spawnMerchant(Location loc, MerchantType type) {
        if (loc == null || loc.getWorld() == null) return;

        Villager villager = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);

        // Appliquer la rotation (yaw) si définie
        if (loc.getYaw() != 0) {
            villager.setRotation(loc.getYaw(), 0);
        }

        // Configuration visuelle - GARDER AI ACTIVE pour le trading!
        villager.setAI(true); // AI doit être activée pour le trading
        villager.setSilent(true);
        villager.setInvulnerable(true);
        villager.setCollidable(false);
        villager.setAware(false); // Désactive l'IA sans bloquer les interactions
        villager.setVillagerType(Villager.Type.PLAINS);
        villager.setVillagerLevel(5);

        // Réduire la vitesse de mouvement à 0 via attribut (empêche le mouvement)
        if (villager.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
            villager.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0);
        }
        
        switch (type) {
            case GLOBAL: // Le Petit "Fourre-tout"
                villager.setBaby();
                villager.setProfession(Villager.Profession.NITWIT);
                villager.setCustomName(ChatColor.YELLOW + "Petit Vendeur");
                break;
            case BLOCKS:
                villager.setAdult();
                villager.setProfession(Villager.Profession.MASON);
                villager.setCustomName(ChatColor.GRAY + "Blocs & Outils");
                break;
            case WEAPONS:
                villager.setAdult();
                villager.setProfession(Villager.Profession.WEAPONSMITH);
                villager.setCustomName(ChatColor.RED + "Armes");
                break;
            case ARMOR:
                villager.setAdult();
                villager.setProfession(Villager.Profession.ARMORER);
                villager.setCustomName(ChatColor.BLUE + "Armures");
                break;
            case SPECIAL:
                villager.setAdult();
                villager.setProfession(Villager.Profession.CLERIC);
                villager.setCustomName(ChatColor.LIGHT_PURPLE + "Spécial");
                break;
        }
        villager.setCustomNameVisible(true);

        // Création des trades
        List<MerchantRecipe> recipes = new ArrayList<>();
        List<TradeConfig> trades = config.loadTrades(type);

        for (TradeConfig trade : trades) {
            ItemStack result;

            // Support items custom (dynamite, etc.)
            if (trade.isCustomItem && trade.customItemId != null) {
                // Creer l'item via CustomItemManager
                result = plugin.getCustomItemManager().createItemStack(trade.customItemId);
                if (result == null) {
                    plugin.getLogger().warning("[MDT] Item custom introuvable: " + trade.customItemId);
                    continue;
                }
                result.setAmount(trade.resultAmount);
                // Appliquer le nom personnalise si defini
                if (trade.displayName != null) {
                    ItemMeta meta = result.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(trade.displayName.replace("&", "§"));
                        result.setItemMeta(meta);
                    }
                }
            } else {
                // Item vanilla standard
                result = new ItemStack(trade.resultMaterial, trade.resultAmount);
                ItemMeta meta = result.getItemMeta();

                if (meta != null) {
                    if (trade.displayName != null) meta.setDisplayName(trade.displayName.replace("&", "§"));

                    if (trade.enchantments != null) {
                        for (String ench : trade.enchantments) {
                            try {
                                String[] parts = ench.split(":");
                                Enchantment e = Enchantment.getByName(parts[0]);
                                int lvl = Integer.parseInt(parts[1]);
                                if (e != null) meta.addEnchant(e, lvl, true);
                            } catch (Exception e) { }
                        }
                    }
                    result.setItemMeta(meta);
                }
            }

            // Trades VRAIMENT illimités - désactiver tous les systèmes de blocage
            MerchantRecipe recipe = new MerchantRecipe(result, Integer.MAX_VALUE);
            recipe.setMaxUses(Integer.MAX_VALUE);
            recipe.setUses(0);                    // Reset le compteur d'utilisations
            recipe.setExperienceReward(false);    // Pas d'XP requis
            recipe.setPriceMultiplier(0f);        // Pas de multiplicateur de prix
            recipe.setDemand(0);                  // Pas de demande (prix fixe)
            recipe.setSpecialPrice(0);            // Pas de prix spécial
            recipe.addIngredient(new ItemStack(trade.costMaterial, trade.costAmount));
            recipes.add(recipe);
        }

        villager.setRecipes(recipes);
        activeMerchants.add(villager);
    }

    /**
     * Démarre la tâche de reset des trades (appelé après spawn)
     */
    public void startTradeResetTask() {
        stopTradeResetTask();

        // Reset les trades toutes les 20 ticks (1 seconde)
        tradeResetTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Villager villager : activeMerchants) {
                if (villager != null && !villager.isDead()) {
                    resetVillagerTrades(villager);
                }
            }
        }, 20L, 20L);
    }

    /**
     * Arrête la tâche de reset des trades
     */
    public void stopTradeResetTask() {
        if (tradeResetTask != null && !tradeResetTask.isCancelled()) {
            tradeResetTask.cancel();
            tradeResetTask = null;
        }
    }

    /**
     * Reset les trades d'un villageois pour qu'ils soient toujours disponibles
     */
    private void resetVillagerTrades(Villager villager) {
        List<MerchantRecipe> currentRecipes = villager.getRecipes();
        List<MerchantRecipe> newRecipes = new ArrayList<>();

        for (MerchantRecipe oldRecipe : currentRecipes) {
            // Recréer la recette avec uses = 0
            MerchantRecipe newRecipe = new MerchantRecipe(
                    oldRecipe.getResult().clone(),
                    Integer.MAX_VALUE
            );
            newRecipe.setMaxUses(Integer.MAX_VALUE);
            newRecipe.setUses(0);
            newRecipe.setExperienceReward(false);
            newRecipe.setPriceMultiplier(0f);
            newRecipe.setDemand(0);
            newRecipe.setSpecialPrice(0);
            newRecipe.setIngredients(oldRecipe.getIngredients());
            newRecipes.add(newRecipe);
        }

        villager.setRecipes(newRecipes);
    }

    public void despawnMerchants() {
        stopTradeResetTask();
        for (Villager v : activeMerchants) {
            if (v != null && !v.isDead()) v.remove();
        }
        activeMerchants.clear();
    }
    
    // Permet de savoir si on clique sur un marchand du MDT pour annuler d'autres events si besoin
    public boolean isMDTMerchant(Entity entity) {
        return activeMerchants.contains(entity);
    }
    
    // Méthode de compatibilité (spawn unique) - Redirige vers spawnMerchants
    public void spawnMerchant() {
        spawnMerchants();
    }
    
    public void despawnMerchant() {
        despawnMerchants();
    }
}