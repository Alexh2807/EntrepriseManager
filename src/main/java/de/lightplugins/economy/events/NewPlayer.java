/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.PlayerJoinEvent
 */
package de.lightplugins.economy.events;

import de.lightplugins.economy.database.querys.BankTableAsync;
import de.lightplugins.economy.database.querys.MoneyTableAsync;
import de.lightplugins.economy.master.Main;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class NewPlayer
implements Listener {
    public Main plugin;

    public NewPlayer(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        MoneyTableAsync moneyTableAsync = new MoneyTableAsync(this.plugin);
        BankTableAsync bankTableAsync = new BankTableAsync(this.plugin);
        moneyTableAsync.playerBalance(player.getName()).thenAccept(balance -> {
            FileConfiguration settings = Main.settings.getConfig();
            if (balance != null) {
                Main.debugPrinting.sendInfo("User already existing in Database. Checking for Name update ... ");
                moneyTableAsync.updatePlayerName(player.getName()).thenAccept(result -> {
                    if (result.booleanValue()) {
                        Main.debugPrinting.sendInfo("Playername updated from PlayerTable via async");
                        return;
                    }
                    Main.debugPrinting.sendInfo("Playername failed from PlayerTable via async");
                });
            } else {
                moneyTableAsync.createNewPlayer(player.getName()).thenAccept(success -> {
                    if (success.booleanValue()) {
                        Main.debugPrinting.sendInfo("New Player was putting in database!");
                        if (!settings.getBoolean("settings.enable-first-join-message")) {
                            return;
                        }
                        FileConfiguration messages = Main.messages.getConfig();
                        double startBalance = settings.getDouble("settings.start-balance");
                        Main.util.sendMessage(player, Objects.requireNonNull(messages.getString("firstJoinMessage")).replace("#startbalance#", String.valueOf(startBalance)).replace("#currency#", Main.util.getCurrency(startBalance)));
                    }
                });
            }
        });
        bankTableAsync.playerBankBalance(player.getName()).thenAccept(balance -> {
            if (balance == null) {
                // Le compte bancaire n'existe pas, le créer
                bankTableAsync.createBankAccount(player.getName()).thenAccept(created -> {
                    if (created.booleanValue()) {
                        Main.debugPrinting.sendInfo("Successfully created bank account for " + player.getName());
                    } else {
                        Main.debugPrinting.sendInfo("Failed to create bank account for " + player.getName());
                    }
                });
            } else {
                // Le compte existe, mettre à jour le nom
                bankTableAsync.updatePlayerBankName(player.getName()).thenAcceptAsync(result -> {
                    if (result.booleanValue()) {
                        Main.debugPrinting.sendInfo("Playername updated from Banktable via async");
                    } else {
                        Main.debugPrinting.sendInfo("Playername failed from Banktable via async");
                    }
                });
            }
        });
    }
}

