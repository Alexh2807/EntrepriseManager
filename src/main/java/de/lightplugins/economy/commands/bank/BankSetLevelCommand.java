/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.entity.Player
 */
package de.lightplugins.economy.commands.bank;

import de.lightplugins.economy.database.querys.BankTableAsync;
import de.lightplugins.economy.enums.MessagePath;
import de.lightplugins.economy.enums.PermissionPath;
import de.lightplugins.economy.master.Main;
import de.lightplugins.economy.utils.Sounds;
import de.lightplugins.economy.utils.SubCommand;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class BankSetLevelCommand
extends SubCommand {
    @Override
    public String getName() {
        return "level";
    }

    @Override
    public String getDescription() {
        return "Set the players bank level to a fix number from the config";
    }

    @Override
    public String getSyntax() {
        return "/bank level set " + Main.util.languageTarget() + " " + Main.util.languageAmount();
    }

    @Override
    public boolean perform(Player player, String[] args) throws ExecutionException, InterruptedException {
        if (args.length == 4) {
            Sounds sounds = new Sounds();
            BankTableAsync bankTable = new BankTableAsync(Main.getInstance);
            try {
                String targetName = args[2];
                Player target = Bukkit.getPlayer((String)targetName);
                // Accepter si le joueur a la permission OU s'il est OP
                if (!player.hasPermission(PermissionPath.BankSetLevel.getPerm()) && !player.isOp()) {
                    Main.util.sendMessage(player, MessagePath.NoPermission.getPath());
                    sounds.soundOnFailure(player);
                    return false;
                }
                if (target == null) {
                    Main.util.sendMessage(player, MessagePath.PlayerNotExists.getPath());
                    sounds.soundOnFailure(player);
                    return false;
                }
                CompletableFuture<Integer> currentLevel = bankTable.playerCurrentBankLevel(target.getName());
                int currentBankLevel = currentLevel.get();
                int levelValue = Integer.parseInt(args[3]);
                if (levelValue <= 0) {
                    Main.util.sendMessage(player, MessagePath.BankSetLevelPlayerToLow.getPath());
                    sounds.soundOnFailure(player);
                    return false;
                }
                int maxLevelViaConfig = 0;
                FileConfiguration config = Main.bankLevelMenu.getConfig();
                for (int i = 0; i < config.getConfigurationSection("levels").getKeys(false).size(); ++i) {
                    ++maxLevelViaConfig;
                }
                if (levelValue > maxLevelViaConfig) {
                    Main.util.sendMessage(player, MessagePath.BankSetLevelPlayerMax.getPath());
                    sounds.soundOnFailure(player);
                    return false;
                }
                CompletableFuture<Boolean> completableFuture = bankTable.setBankLevel(target.getName(), levelValue);
                try {
                    if (completableFuture.get().booleanValue()) {
                        Main.util.sendMessage(player, MessagePath.BankSetLevelPlayer.getPath().replace("#old-level#", String.valueOf(currentBankLevel)).replace("#new-level#", String.valueOf(levelValue)));
                        sounds.soundOnSuccess(player);
                        return true;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } catch (NumberFormatException e) {
                Main.util.sendMessage(player, MessagePath.NotANumber.getPath());
                sounds.soundOnFailure(player);
                return false;
            }
        }
        Main.util.sendMessage(player, MessagePath.WrongCommand.getPath().replace("#command#", this.getSyntax()));
        return false;
    }
}

