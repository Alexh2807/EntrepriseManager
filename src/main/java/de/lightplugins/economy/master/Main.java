/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  net.milkbowl.vault.economy.Economy
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.entity.Player
 *  org.bukkit.event.Listener
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.PluginManager
 *  org.bukkit.plugin.java.JavaPlugin
 *  org.bukkit.plugin.messaging.PluginMessageListener
 */
package de.lightplugins.economy.master;

import com.zaxxer.hikari.HikariDataSource;
import de.lightplugins.economy.commands.BalanceCommandManager;
import de.lightplugins.economy.commands.BankCommandManager;
import de.lightplugins.economy.commands.ConsoleCommandManager;
import de.lightplugins.economy.commands.MainCommandManager;
import de.lightplugins.economy.commands.MoneyCommandManager;
import de.lightplugins.economy.commands.PayCommandMaster;
import de.lightplugins.economy.commands.tabcompletion.BalanceTabCompletion;
import de.lightplugins.economy.commands.tabcompletion.BankTabCompletion;
import de.lightplugins.economy.commands.tabcompletion.MainTabCompletion;
import de.lightplugins.economy.commands.tabcompletion.MoneyTabCompletion;
import de.lightplugins.economy.database.DatabaseConnection;
import de.lightplugins.economy.database.tables.CreateTable;
import de.lightplugins.economy.enums.PluginMessagePath;
import de.lightplugins.economy.events.ClaimVoucher;
import de.lightplugins.economy.events.NewPlayer;
import de.lightplugins.economy.files.FileManager;
import de.lightplugins.economy.hooks.VaultHook;
import de.lightplugins.economy.implementer.EconomyImplementer;
import de.lightplugins.economy.listener.BankListener;
import de.lightplugins.economy.listener.LoseMoney;
import de.lightplugins.economy.listener.TimeReward;
import de.lightplugins.economy.master.Metrics;
import de.lightplugins.economy.placeholder.PapiRegister;
import de.lightplugins.economy.utils.ColorTranslation;
import de.lightplugins.economy.utils.DebugPrinting;
import de.lightplugins.economy.utils.ProgressionBar;
import de.lightplugins.economy.utils.SignPackets;
import de.lightplugins.economy.utils.Sounds;
import de.lightplugins.economy.utils.TableStatements;
import de.lightplugins.economy.utils.Util;
import fr.minuskube.inv.InventoryManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

public class Main {
    public static Main getInstance;
    public static org.bukkit.plugin.Plugin parentPlugin = null; // Plugin parent (RoleplayCity)
    public static java.io.File customDataFolder = null; // Pour utilisation en tant que sous-module
    public static String currencyName;
    public static final String consolePrefix = "\u00a7r[light\u00a7cEconomy\u00a7r] ";
    public static EconomyImplementer economyImplementer;
    private VaultHook vaultHook;
    public static boolean isCitizens;
    public Economy econ;
    public boolean isBungee;
    public HikariDataSource ds;
    public DatabaseConnection hikari;
    public static ColorTranslation colorTranslation;
    public static ProgressionBar progressionBar;
    public static Util util;
    public static Sounds sounds;
    public static DebugPrinting debugPrinting;
    public static FileManager settings;
    public static FileManager messages;
    public static FileManager titles;
    public static FileManager voucher;
    public static FileManager bankMenu;
    public static FileManager bankLevelMenu;
    public static FileManager lose;
    public static FileManager discord;
    public SignPackets signGui;
    public static List<String> payToggle;
    public List<Player> bankDepositValue = new ArrayList<Player>();
    public List<Player> bankWithdrawValue = new ArrayList<Player>();
    public static InventoryManager bankMenuInventoryManager;

    /**
     * Retourne le bon dataFolder (customDataFolder si défini, sinon celui du plugin parent)
     * Utilisé pour séparer les fichiers LightEconomy dans un sous-dossier
     */
    public java.io.File getLightEconomyDataFolder() {
        if (customDataFolder != null) {
            return customDataFolder;
        }
        if (parentPlugin != null) {
            java.io.File folder = new java.io.File(parentPlugin.getDataFolder(), "LightEconomy");
            if (!folder.exists()) {
                folder.mkdirs();
            }
            return folder;
        }
        throw new IllegalStateException("LightEconomy: Aucun plugin parent défini !");
    }

    /**
     * Méthodes wrapper pour rediriger vers le plugin parent
     */
    public java.io.File getDataFolder() {
        return getLightEconomyDataFolder();
    }

    public java.io.InputStream getResource(String filename) {
        if (parentPlugin != null) {
            java.io.InputStream in = parentPlugin.getResource("LightEconomy/" + filename);
            if (in != null) {
                return in;
            }
            return parentPlugin.getResource(filename);
        }
        return null;
    }

    public void saveResource(String resourcePath, boolean replace) {
        if (resourcePath == null || resourcePath.equals("")) {
            throw new IllegalArgumentException("ResourcePath cannot be null or empty");
        }

        resourcePath = resourcePath.replace('\\', '/');
        java.io.InputStream in = getResource(resourcePath);
        if (in == null) {
            throw new IllegalArgumentException("The embedded resource '" + resourcePath + "' cannot be found in " + (parentPlugin != null ? parentPlugin.getName() : "null"));
        }

        java.io.File outFile = new java.io.File(getLightEconomyDataFolder(), resourcePath);
        int lastIndex = resourcePath.lastIndexOf('/');
        java.io.File outDir = new java.io.File(getLightEconomyDataFolder(), lastIndex >= 0 ? resourcePath.substring(0, lastIndex) : "");

        if (!outDir.exists()) {
            outDir.mkdirs();
        }

        try {
            if (!outFile.exists() || replace) {
                java.io.OutputStream out = new java.io.FileOutputStream(outFile);
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                out.close();
                in.close();
            }
        } catch (java.io.IOException ex) {
            java.util.logging.Logger.getLogger("LightEconomy").log(java.util.logging.Level.SEVERE, "Could not save " + outFile.getName() + " to " + outFile, ex);
        }
    }

    public void reloadConfig() {
        if (parentPlugin != null) {
            parentPlugin.reloadConfig();
        }
    }

    public org.bukkit.configuration.file.FileConfiguration getConfig() {
        if (parentPlugin != null) {
            return parentPlugin.getConfig();
        }
        return null;
    }

    public java.util.logging.Logger getLogger() {
        if (parentPlugin != null) {
            return parentPlugin.getLogger();
        }
        return java.util.logging.Logger.getLogger("LightEconomy");
    }

    public String getName() {
        return "LightEconomy";
    }

    public org.bukkit.Server getServer() {
        return org.bukkit.Bukkit.getServer();
    }

    /**
     * Retourne le plugin parent pour les casts (Plugin)
     */
    public org.bukkit.plugin.Plugin asPlugin() {
        if (parentPlugin != null) {
            return parentPlugin;
        }
        throw new IllegalStateException("LightEconomy: Aucun plugin parent défini !");
    }

    /**
     * Retourne une commande du plugin parent
     */
    public org.bukkit.command.PluginCommand getCommand(String name) {
        if (parentPlugin instanceof JavaPlugin) {
            return ((JavaPlugin)parentPlugin).getCommand(name);
        }
        return null;
    }

    /**
     * Retourne la description du plugin
     */
    public org.bukkit.plugin.PluginDescriptionFile getDescription() {
        if (parentPlugin != null) {
            return parentPlugin.getDescription();
        }
        return null;
    }

    public void onLoad() {
        getInstance = this;
        economyImplementer = new EconomyImplementer();
        this.vaultHook = new VaultHook();
        this.vaultHook.hook();
        colorTranslation = new ColorTranslation();
        util = new Util();
        debugPrinting = new DebugPrinting();
        settings = new FileManager(this, "settings.yml");
        messages = new FileManager(this, "messages.yml");
        titles = new FileManager(this, "titles.yml");
        voucher = new FileManager(this, "voucher.yml");
        bankMenu = new FileManager(this, "bank-menu.yml");
        bankLevelMenu = new FileManager(this, "bank-level.yml");
        lose = new FileManager(this, "lose.yml");
        currencyName = settings.getConfig().getString("settings.currency-name");
        sounds = new Sounds();
        Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Successfully loaded " + this.getName());
    }

    public void onEnable() {
        Bukkit.getConsoleSender().sendMessage("\n  \u00a7r_      _____ _____ _    _ _______ \u00a7c______ _____ ____  _   _  ____  __  ____     __\n \u00a7r| |    |_   _/ ____| |  | |__   __\u00a7c|  ____/ ____/ __ \\| \\ | |/ __ \\|  \\/  \\ \\   / /\n \u00a7r| |      | || |  __| |__| |  | |  \u00a7c| |__ | |   | |  | |  \\| | |  | | \\  / |\\ \\_/ / \n \u00a7r| |      | || | |_ |  __  |  | |  \u00a7c|  __|| |   | |  | | . ` | |  | | |\\/| | \\   /  \n \u00a7r| |____ _| || |__| | |  | |  | |  \u00a7c| |___| |___| |__| | |\\  | |__| | |  | |  | |   \n \u00a7r|______|_____\\_____|_|  |_|  |_|  \u00a7c|______\\_____\\____/|_| \\_|\\____/|_|  |_|  |_|\n\n" + ChatColor.RESET + "      Version: \u00a7c5.5.0   \u00a7rAuthor: \u00a7clightPlugins\n      \u00a7rThank you for using lightEconomy. If you came in trouble feel free to join\n      my \u00a7cDiscord \u00a7rserver: https://discord.gg/G2EuzmSW\n");
        this.enableBStats();
        debugPrinting.sendInfo("bStats successfully registered.");
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            Bukkit.getConsoleSender().sendMessage("\n\n    \u00a74ERROR\n\n    \u00a7cCould not find \u00a74PlaceholderAPI \n    \u00a7rLighteconomy will \u00a7cnot run \u00a7rwithout PlaceholderAPI. Please download\n    the latest version of PAPI \n    \u00a7chttps://www.spigotmc.org/resources/placeholderapi.6245/ \n\n\n");
            this.onDisable();
            return;
        }
        Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Successfully hooked into \u00a7cPlaceholderAPI");
        new PapiRegister().register();
        Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Successfully registered lightEconomy placeholders");
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
            if (settings.getConfig().getBoolean("settings.bankInputViaSign.enable")) {
                Bukkit.getConsoleSender().sendMessage("\n\n    \u00a74ERROR\n\n    \u00a7cCould not find \u00a74ProtocolLib\n    \u00a7rYou enabled bankInputViaSign while ProtocolLib is not installed on this Server.\n    \u00a7rDownload the latest version or change \u00a7cbankInputViaSign \u00a77to \u00a7cfalse!\n    \u00a7chttps://ci.dmulloy2.net/job/ProtocolLib/\n\n\n");
                this.onDisable();
                return;
            }
        } else {
            this.signGui = new SignPackets(this);
            Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Successfully hooked into \u00a7cProtocolLib");
        }
        this.hikari = new DatabaseConnection(this);
        if (settings.getConfig().getBoolean("mysql.enable")) {
            this.hikari.connectToDataBaseViaMariaDB();
        } else {
            this.hikari.connectToDatabaseViaSQLite();
        }
        Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Creating Database ...");
        CreateTable createTable = new CreateTable(this);
        createTable.createMoneyTable();
        createTable.createBankTable();
        TableStatements tableStatements = new TableStatements(this);
        // NOTE: Les commandes sont maintenant enregistr\u00e9es par RoleplayCity.setupCommands()
        // Cela \u00e9vite les probl\u00e8mes de timing o\u00f9 LightEconomy tente d'enregistrer
        // les commandes avant que le plugin.yml soit compl\u00e8tement charg\u00e9.
        Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Commands will be registered by parent plugin...");
        PluginManager pluginManager = Bukkit.getPluginManager();
        pluginManager.registerEvents((Listener)new NewPlayer(this), this.asPlugin());
        pluginManager.registerEvents((Listener)new ClaimVoucher(), this.asPlugin());
        pluginManager.registerEvents((Listener)new BankListener(this), this.asPlugin());
        pluginManager.registerEvents((Listener)new LoseMoney(), this.asPlugin());
        this.isBungee = settings.getConfig().getBoolean("settings.bungeecord");
        if (this.isBungee) {
            Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Enable Bungeecord features ...");
            this.getServer().getMessenger().registerOutgoingPluginChannel(this.asPlugin(), "BungeeCord");
            this.getServer().getMessenger().registerIncomingPluginChannel(this.asPlugin(), PluginMessagePath.PAY.getType(), (PluginMessageListener)new de.lightplugins.economy.utils.PluginMessageListener());
        }
        bankMenuInventoryManager = new InventoryManager((JavaPlugin)this.asPlugin());
        bankMenuInventoryManager.init();
        TimeReward timeReward = new TimeReward();
        timeReward.startTimedReward();
        Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Successfully started " + this.getName());
    }

    public void onDisable() {
        this.vaultHook.unhook();
        try {
            if (this.ds != null && !this.ds.isClosed()) {
                Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Status of Database: " + this.ds.getConnection());
                Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Lets try to shutdown the database");
                Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] \u00a7cHint: \u00a74Never 'relaod' the server!");
                this.ds.close();
                Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Successfully disconnected Database!");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Something went wrong on closing database!", e);
        }
        Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Successfully stopped " + this.getName());
    }

    private void enableBStats() {
        int pluginId = 18401;
        Metrics metrics = new Metrics((JavaPlugin)this.asPlugin(), pluginId);
    }

    static {
        isCitizens = false;
        payToggle = new ArrayList<String>();
    }
}

