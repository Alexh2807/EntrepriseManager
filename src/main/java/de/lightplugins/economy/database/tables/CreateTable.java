/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.configuration.file.FileConfiguration
 */
package de.lightplugins.economy.database.tables;

import de.lightplugins.economy.master.Main;
import de.lightplugins.economy.utils.TableStatements;
import org.bukkit.configuration.file.FileConfiguration;

public class CreateTable {
    public Main plugin;

    public CreateTable(Main plugin) {
        this.plugin = plugin;
    }

    public void createMoneyTable() {
        String tableName = "MoneyTable";
        TableStatements tableStatements = new TableStatements(this.plugin);
        FileConfiguration settings = Main.settings.getConfig();
        String tableStatement = "CREATE TABLE IF NOT EXISTS " + tableName + " (uuid TEXT,name TEXT,money DOUBLE,isPlayer BOOL,PRIMARY KEY (uuid))";
        if (settings.getBoolean("mysql.enable")) {
            tableStatement = "CREATE TABLE IF NOT EXISTS " + tableName + " (uuid TEXT(200),name TEXT,money DOUBLE,isPlayer BOOL,PRIMARY KEY (uuid(200)))";
        }
        tableStatements.createTableStatement(tableStatement);
    }

    public void createPlayerData() {
        String tableName = "PlayerData";
        TableStatements tableStatements = new TableStatements(this.plugin);
        FileConfiguration settings = Main.settings.getConfig();
        String tableStatement = "CREATE TABLE IF NOT EXISTS " + tableName + " (uuid TEXT,trustedBank TEXT,PRIMARY KEY (uuid))";
        if (settings.getBoolean("mysql.enable")) {
            tableStatement = "CREATE TABLE IF NOT EXISTS " + tableName + " (uuid TEXT,trustedBank TEXT,PRIMARY KEY (uuid(200)))";
        }
        tableStatements.createTableStatement(tableStatement);
    }

    public void createBankTable() {
        String tableName = "BankTable";
        TableStatements tableStatements = new TableStatements(this.plugin);
        FileConfiguration settings = Main.settings.getConfig();
        String tableStatement = "CREATE TABLE IF NOT EXISTS " + tableName + " (uuid TEXT,name TEXT,money DOUBLE,level INTEGER,PRIMARY KEY (uuid))";
        if (settings.getBoolean("mysql.enable")) {
            tableStatement = "CREATE TABLE IF NOT EXISTS " + tableName + " (uuid TEXT(200),name TEXT,money DOUBLE,level INTEGER,PRIMARY KEY (uuid(200)))";
        }
        tableStatements.createTableStatement(tableStatement);
    }
}

