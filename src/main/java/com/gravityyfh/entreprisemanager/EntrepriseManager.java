package com.gravityyfh.entreprisemanager;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class EntrepriseManager extends JavaPlugin {

    private static EntrepriseManager instance; // Singleton instance pour faciliter l'accès depuis d'autres classes
    private EntrepriseManagerLogic entrepriseLogic;
    private ChatListener chatListener;
    private EntrepriseGUI entrepriseGUI;
    private static Economy econ = null; // Instance d'économie


    @Override
    public void onEnable() {
        instance = this; // Initialisation de l'instance singleton
        getLogger().info("EntrepriseManager activé !");

        // Assurez-vous que Vault est présent
        if (!setupEconomy()) {
            getLogger().severe("Vault est nécessaire pour ce plugin, mais il n'a pas été trouvé !");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Assurez-vous que le dossier de configuration existe et sauvegardez la configuration par défaut
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }
        saveDefaultConfig();

        // Initialisation de la logique principale du plugin et des écouteurs d'événements
        entrepriseLogic = new EntrepriseManagerLogic(this);
        entrepriseGUI = new EntrepriseGUI(this, entrepriseLogic); // Initialiser l'interface GUI
        chatListener = new ChatListener(this, entrepriseGUI); // Passez 'entrepriseGUI' pour accéder à EntrepriseManager depuis ChatListener
        EventListener eventListener = new EventListener(this, entrepriseLogic);
        getServer().getPluginManager().registerEvents(chatListener, this);
        getServer().getPluginManager().registerEvents(eventListener, this);
        getServer().getPluginManager().registerEvents(entrepriseGUI, this);
        entrepriseLogic.chargerBlocsAutorises();
        getServer().getPluginManager().registerEvents(new TreeCutListener(new EntrepriseManagerLogic(this)), this);
        // Configuration des commandes
        setupCommands();

        // Charger les entreprises et leurs coffres virtuels
        entrepriseLogic.reloadEntreprises(); // Charger les entreprises depuis le fichier

        // Charger les coffres virtuels pour chaque entreprise
        File entrepriseFile = new File(getDataFolder(), "entreprise.yml");
        YamlConfiguration entrepriseConfig = YamlConfiguration.loadConfiguration(entrepriseFile);

        // Parcours de chaque entreprise pour charger son coffre virtuel
        for (EntrepriseManagerLogic.Entreprise entreprise : entrepriseLogic.getEntreprises()) {
            EntrepriseVirtualChest virtualChest = entreprise.getVirtualChest();
            String path = "entreprises." + entreprise.getNom(); // Définit le chemin pour le coffre dans la configuration
            virtualChest.loadFromConfig(entrepriseConfig, path); // Charger le coffre virtuel depuis le fichier
        }

        // Planifier les paiements journaliers selon la configuration
        entrepriseLogic.planifierPaiements();

        getLogger().info("EntrepriseManager est maintenant actif.");
    }

    @Override
    public void onDisable() {
        // Sauvegarde des entreprises et autres données si nécessaire
        entrepriseLogic.saveEntreprises();

        // Sauvegarder les coffres virtuels dans le fichier de configuration entreprise.yml
        File entrepriseFile = new File(getDataFolder(), "entreprise.yml");
        YamlConfiguration entrepriseConfig = YamlConfiguration.loadConfiguration(entrepriseFile);

        // Parcours de chaque entreprise pour sauvegarder son coffre virtuel
        for (EntrepriseManagerLogic.Entreprise entreprise : entrepriseLogic.getEntreprises()) {
            EntrepriseVirtualChest virtualChest = entreprise.getVirtualChest();
            String path = "entreprises." + entreprise.getNom(); // Définit le chemin pour le coffre dans la configuration
            virtualChest.saveToConfig(entrepriseConfig, path); // Sauvegarder le coffre virtuel dans la configuration
        }

        try {
            entrepriseConfig.save(entrepriseFile); // Sauvegarder le fichier après avoir mis à jour les coffres virtuels
        } catch (Exception e) {
            getLogger().severe("Erreur lors de la sauvegarde des coffres virtuels : " + e.getMessage());
        }

        getLogger().info("EntrepriseManager désactivé. Données sauvegardées.");
    }

    private void setupCommands() {
        // Initialisation et enregistrement du gestionnaire de commandes
        EntrepriseCommandHandler commandHandler = new EntrepriseCommandHandler(entrepriseLogic, entrepriseGUI);
        getCommand("entreprise").setExecutor(commandHandler);

        // Configuration du compléteur d'onglet pour la commande
        EntrepriseTabCompleter tabCompleter = new EntrepriseTabCompleter(entrepriseLogic);
        getCommand("entreprise").setTabCompleter(tabCompleter);
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    public static Economy getEconomy() {
        return econ;
    }

    public void reloadPlugin() {
        reloadConfig(); // Recharge le fichier config.yml
        entrepriseLogic.reloadEntreprises(); // Recharger les entreprises depuis le fichier
        getLogger().info("Le plugin EntrepriseManager a été rechargé.");
    }

    // Getters pour accéder aux instances depuis d'autres parties du plugin
    public static EntrepriseManager getInstance() {
        return instance;
    }

    public EntrepriseManagerLogic getEntrepriseLogic() {
        return entrepriseLogic;
    }

    public ChatListener getChatListener() {
        return chatListener;
    }
}
