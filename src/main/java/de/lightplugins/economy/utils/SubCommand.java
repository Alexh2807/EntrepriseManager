/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.entity.Player
 */
package de.lightplugins.economy.utils;

import java.util.concurrent.ExecutionException;
import org.bukkit.entity.Player;

public abstract class SubCommand {
    public abstract String getName();

    public abstract String getDescription();

    public abstract String getSyntax();

    public abstract boolean perform(Player var1, String[] var2) throws ExecutionException, InterruptedException;
}

