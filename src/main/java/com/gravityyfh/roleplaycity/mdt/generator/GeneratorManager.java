package com.gravityyfh.roleplaycity.mdt.generator;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.mdt.config.MDTConfig;
import com.gravityyfh.roleplaycity.mdt.data.MDTGame;
import com.gravityyfh.roleplaycity.mdt.data.MDTGenerator;
import com.gravityyfh.roleplaycity.mdt.data.ResourceSpawner;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class GeneratorManager {
    private final RoleplayCity plugin;
    private final MDTConfig config;
    private final List<MDTGenerator> activeGenerators = new ArrayList<>();
    private BukkitTask generatorTask;

    public GeneratorManager(RoleplayCity plugin, MDTConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void startGenerators(MDTGame game) {
        stopGenerators(); // Sécurité

        // Charger les générateurs depuis la config (basé sur les locations sauvegardées)
        activeGenerators.addAll(config.loadGeneratorsFromLocations());

        if (activeGenerators.isEmpty()) {
            plugin.getLogger().warning("[MDT] Aucun générateur configuré! Utilisez /mdt setup.");
            return;
        }

        // Démarrer la boucle (1 tick = 1/20 seconde)
        generatorTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (game == null || game.isEnded()) {
                stopGenerators();
                return;
            }

            for (MDTGenerator generator : activeGenerators) {
                tickGenerator(generator);
            }
        }, 1L, 1L);
    }

    private void tickGenerator(MDTGenerator generator) {
        Location loc = generator.getLocation();
        
        for (ResourceSpawner resource : generator.getResources()) {
            resource.tick();
            
            if (resource.shouldSpawn()) {
                dropItem(loc, resource);
                resource.resetTimer();
            }
        }
    }

    private void dropItem(Location location, ResourceSpawner resource) {
        // Vérifier s'il y a déjà trop d'items au sol (anti-lag)
        long nearbyItems = location.getWorld().getNearbyEntities(location, 1, 1, 1).stream()
                .filter(e -> e instanceof Item)
                .filter(e -> ((Item) e).getItemStack().getType() == resource.getMaterial())
                .count();

        if (nearbyItems >= resource.getMaxStack()) {
            return; // Trop d'items, on ne spawn pas
        }

        ItemStack itemStack = resource.createItemStack();
        Item item = location.getWorld().dropItem(location.clone().add(0, 0.5, 0), itemStack);
        item.setVelocity(new Vector(0, 0.2, 0)); // Petit saut
        
        // Hologramme temporaire (optionnel, simpliste ici)
        // ArmorStand... 
    }

    public void stopGenerators() {
        if (generatorTask != null) {
            generatorTask.cancel();
            generatorTask = null;
        }
        activeGenerators.clear();
    }
}
