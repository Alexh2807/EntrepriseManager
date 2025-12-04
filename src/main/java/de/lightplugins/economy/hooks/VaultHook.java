/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  net.milkbowl.vault.economy.Economy
 *  org.bukkit.Bukkit
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.ServicePriority
 */
package de.lightplugins.economy.hooks;

import de.lightplugins.economy.master.Main;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

public class VaultHook {
    private final Main plugin = Main.getInstance;
    private Economy provider;

    public void hook() {
        this.provider = Main.economyImplementer;
        Bukkit.getServicesManager().register(Economy.class, this.provider, this.plugin.asPlugin(), ServicePriority.Highest);
        Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Vault successfully hooked with highest priority into " + this.plugin.getName());
    }

    public void unhook() {
        Bukkit.getServicesManager().unregister(Economy.class, (Object)this.provider);
        Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Vault successfully unhooked from " + this.plugin.getName());
    }
}

