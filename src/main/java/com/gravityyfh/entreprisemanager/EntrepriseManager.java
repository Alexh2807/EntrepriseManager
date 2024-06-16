package com.gravityyfh.entreprisemanager;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class EntrepriseManager extends JavaPlugin {

    private static EntrepriseManager instance; // Singleton instance pour faciliter l'accès depuis d'autres classes
    private EntrepriseManagerLogic entrepriseLogic;
    private ChatListener chatListener;
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
        chatListener = new ChatListener(this); // Passez 'this' pour accéder à EntrepriseManager depuis ChatListener
        EventListener eventListener = new EventListener(this, entrepriseLogic);
        getServer().getPluginManager().registerEvents(chatListener, this);
        getServer().getPluginManager().registerEvents(eventListener, this);
        entrepriseLogic.chargerBlocsAutorises();
        getServer().getPluginManager().registerEvents(new TreeCutListener(entrepriseLogic), this);
        // Configuration des commandes
        setupCommands();

        // Planifier les paiements journaliers selon la configuration
        entrepriseLogic.planifierPaiements();

        getLogger().info("EntrepriseManager est maintenant actif.");
    }

    @Override
    public void onDisable() {
        // Sauvegarde des entreprises et autres données si nécessaire
        entrepriseLogic.saveEntreprises();

        getLogger().info("EntrepriseManager désactivé. Données sauvegardées.");
    }

    private void setupCommands() {
        // Initialisation et enregistrement du gestionnaire de commandes
        EntrepriseCommandHandler commandHandler = new EntrepriseCommandHandler(entrepriseLogic);
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
        entrepriseLogic.reloadEntreprises(); // Méthode hypothétique pour recharger les entreprises depuis le fichier
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
