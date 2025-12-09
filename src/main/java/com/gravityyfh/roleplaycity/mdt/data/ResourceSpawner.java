package com.gravityyfh.roleplaycity.mdt.data;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class ResourceSpawner {
    private final Material material;
    private final String displayName;
    private final int intervalSeconds;
    private final int maxStack;
    
    private int currentTick = 0;

    public ResourceSpawner(Material material, String displayName, int intervalSeconds, int maxStack) {
        this.material = material;
        this.displayName = displayName;
        this.intervalSeconds = intervalSeconds;
        this.maxStack = maxStack;
    }

    public Material getMaterial() { return material; }
    public String getDisplayName() { return displayName; }
    public int getIntervalSeconds() { return intervalSeconds; }
    public int getMaxStack() { return maxStack; }
    
    public void tick() {
        currentTick++;
    }
    
    public boolean shouldSpawn() {
        return currentTick >= (intervalSeconds * 20); // 20 ticks = 1 seconde
    }
    
    public void resetTimer() {
        currentTick = 0;
    }
    
    public ItemStack createItemStack() {
        return new ItemStack(material, 1);
    }
    
    // Clone pour avoir des compteurs indépendants par générateur
    public ResourceSpawner clone() {
        return new ResourceSpawner(material, displayName, intervalSeconds, maxStack);
    }
}
