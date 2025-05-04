package com.gravityyfh.entreprisemanager;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class EntrepriseManager extends JavaPlugin implements Listener {

    private static EntrepriseManager instance;
    private EntrepriseManagerLogic entrepriseLogic;
    private ChatListener chatListener;
    private EntrepriseGUI entrepriseGUI;
    private static Economy econ = null;

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("✅ Chargement du plugin EntrepriseManager...");

        // Vérification de Vault
        if (!setupEconomy()) {
            getLogger().severe("❌ Vault est requis pour EntrepriseManager !");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Sauvegarde de la configuration par défaut
        saveDefaultConfig();

        // Initialisation logique + GUI + Events
        entrepriseLogic = new EntrepriseManagerLogic(this);
        entrepriseGUI = new EntrepriseGUI(this, entrepriseLogic);
        chatListener = new ChatListener(this, entrepriseGUI);

        // Enregistrement des listeners
        registerEvents();
        getServer().getPluginManager().registerEvents(this, this); // Pour capter PlayerCommandPreprocessEvent

        // Enregistrement des commandes
        setupCommands();

        // Chargement des entreprises
        entrepriseLogic.reloadEntreprises();

        // Planification des paiements journaliers
        entrepriseLogic.planifierPaiements();

        getLogger().info("✅ Plugin EntrepriseManager activé avec succès !");
    }

    @Override
    public void onDisable() {
        entrepriseLogic.saveEntreprises();
        saveVirtualChests();
        getLogger().info("🛑 Plugin EntrepriseManager désactivé et les données ont été sauvegardées.");
    }

    private void registerEvents() {
        getServer().getPluginManager().registerEvents(chatListener, this);
        getServer().getPluginManager().registerEvents(new EventListener(this, entrepriseLogic), this);
        getServer().getPluginManager().registerEvents(entrepriseGUI, this);
        getServer().getPluginManager().registerEvents(new TreeCutListener(entrepriseLogic), this);
    }

    private void setupCommands() {
        EntrepriseCommandHandler commandHandler = new EntrepriseCommandHandler(entrepriseLogic, entrepriseGUI);
        getCommand("entreprise").setExecutor(commandHandler);
        getCommand("entreprise").setTabCompleter(new EntrepriseTabCompleter(entrepriseLogic));
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
    }

    private void saveVirtualChests() {
        File entrepriseFile = new File(getDataFolder(), "entreprise.yml");
        YamlConfiguration entrepriseConfig = YamlConfiguration.loadConfiguration(entrepriseFile);

        for (EntrepriseManagerLogic.Entreprise entreprise : entrepriseLogic.getEntreprises()) {
            String path = "entreprises." + entreprise.getNom();
            // TODO: sauvegarder les données du coffre ici
        }

        try {
            entrepriseConfig.save(entrepriseFile);
        } catch (Exception e) {
            getLogger().severe("❌ Erreur lors de la sauvegarde des coffres virtuels : " + e.getMessage());
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        entrepriseLogic.reloadEntreprises();
        getLogger().info("🔄 Plugin EntrepriseManager rechargé.");
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage().toLowerCase();
        Player player = event.getPlayer();

        if (message.startsWith("/qs create") || message.startsWith("/quickshop create")) {
            boolean estGerant = entrepriseLogic.getEntrepriseDuGerant(player.getName()).size() > 0;

            if (!estGerant) {
                player.sendMessage(ChatColor.RED + "❌ Seuls les gérants d'une entreprise peuvent créer un shop.");
                event.setCancelled(true);
            }
        }
    }

    public static EntrepriseManager getInstance() {
        return instance;
    }

    public EntrepriseManagerLogic getEntrepriseLogic() {
        return entrepriseLogic;
    }

    public ChatListener getChatListener() {
        return chatListener;
    }

    public static Economy getEconomy() {
        return econ;
    }
}
