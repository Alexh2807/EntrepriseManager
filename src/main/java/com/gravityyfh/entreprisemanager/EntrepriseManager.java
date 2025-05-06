package com.gravityyfh.entreprisemanager;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration; // Pas utilisé directement ici, mais bon à garder si besoin futur
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent; // Ajout pour les messages différés
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File; // Pas utilisé directement ici

public class EntrepriseManager extends JavaPlugin implements Listener {

    private static EntrepriseManager instance;
    private EntrepriseManagerLogic entrepriseLogic;
    private ChatListener chatListener;
    private EntrepriseGUI entrepriseGUI;
    private static Economy econ = null;

    // Nouveaux Listeners (à créer si vous les séparez)
    private CraftItemListener craftItemListener;
    private BlockPlaceListener blockPlaceListener;


    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("✅ Chargement du plugin EntrepriseManager V2...");

        if (!setupEconomy()) {
            getLogger().severe("❌ Vault est requis pour EntrepriseManager ! Désactivation...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig(); // Assure que config.yml existe

        // Initialisation des composants principaux
        entrepriseLogic = new EntrepriseManagerLogic(this); // Contient maintenant planifierTachesHoraires()
        entrepriseGUI = new EntrepriseGUI(this, entrepriseLogic);
        chatListener = new ChatListener(this, entrepriseGUI); // Pour les saisies de montant

        // Enregistrement des listeners d'événements
        registerEvents();
        getServer().getPluginManager().registerEvents(this, this); // Pour PlayerCommandPreprocessEvent et PlayerJoinEvent

        getLogger().info("Listener pour Towny et autres événements enregistrés.");

        // Enregistrement des commandes
        setupCommands();

        // Le chargement des entreprises et la planification des tâches sont maintenant gérés
        // dans le constructeur de EntrepriseManagerLogic et sa méthode reloadPluginData.
        // entrepriseLogic.reloadEntreprises(); // Déjà fait dans le constructeur et reloadPluginData
        // entrepriseLogic.planifierPaiements(); // Remplacé par planifierTachesHoraires dans EntrepriseManagerLogic

        getLogger().info("✅ Plugin EntrepriseManager V2 activé avec succès !");
    }

    @Override
    public void onDisable() {
        if (entrepriseLogic != null) {
            entrepriseLogic.saveEntreprises(); // Sauvegarder les données avant la désactivation
        }
        getLogger().info("🛑 Plugin EntrepriseManager désactivé. Données sauvegardées.");
    }

    private void registerEvents() {
        getServer().getPluginManager().registerEvents(chatListener, this);
        getServer().getPluginManager().registerEvents(new EventListener(this, entrepriseLogic), this); // Pour BlockBreak
        getServer().getPluginManager().registerEvents(entrepriseGUI, this);
        getServer().getPluginManager().registerEvents(new TreeCutListener(entrepriseLogic, this), this); // Ajout du plugin pour TreeCutListener
        getServer().getPluginManager().registerEvents(new TownyListener(this, entrepriseLogic), this); // Assurez-vous que TownyListener est bien enregistré

        // Enregistrement des nouveaux listeners s'ils existent
        // Si vous les intégrez dans EventListener.java, ces lignes ne sont pas nécessaires.
        this.craftItemListener = new CraftItemListener(this, entrepriseLogic);
        getServer().getPluginManager().registerEvents(this.craftItemListener, this);
        getLogger().info("CraftItemListener enregistré.");

        this.blockPlaceListener = new BlockPlaceListener(this, entrepriseLogic);
        getServer().getPluginManager().registerEvents(this.blockPlaceListener, this);
        getLogger().info("BlockPlaceListener enregistré.");

        // Listener pour la connexion du joueur (pour les messages différés)
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(entrepriseLogic), this);
        getLogger().info("PlayerConnectionListener enregistré pour les messages différés.");

    }

    private void setupCommands() {
        EntrepriseCommandHandler commandHandler = new EntrepriseCommandHandler(entrepriseLogic, entrepriseGUI);
        getCommand("entreprise").setExecutor(commandHandler);
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().severe("Vault non trouvé !");
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().severe("Aucun fournisseur d'économie Vault trouvé !");
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    public void reloadPlugin() {
        // D'abord, recharger la configuration Bukkit standard (config.yml)
        super.reloadConfig(); // Important pour que plugin.getConfig() ait les nouvelles valeurs

        // Ensuite, appeler la méthode de logique qui gère le rechargement des données spécifiques au plugin
        if (entrepriseLogic != null) {
            entrepriseLogic.reloadPluginData(); // Cette méthode devrait recharger entreprise.yml, players.yml et redémarrer les tâches
        }
        getLogger().info("🔄 Plugin EntrepriseManager V2 et ses données ont été rechargés.");
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage().toLowerCase();
        Player player = event.getPlayer();

        // Intercepter la création de shop QuickShop
        if (message.startsWith("/qs create") || message.startsWith("/quickshop create")) {
            // Vérifier si le joueur est gérant d'AU MOINS UNE entreprise
            // La logique getEntreprisesGereesPar retourne une liste. Si elle n'est pas vide, il est gérant.
            if (entrepriseLogic != null && entrepriseLogic.getEntreprisesGereesPar(player.getName()).isEmpty()) {
                player.sendMessage(ChatColor.RED + "❌ Seuls les gérants d'une entreprise peuvent créer un shop QuickShop.");
                event.setCancelled(true);
            }
        }
    }


    // Listener interne pour la connexion des joueurs (primes différées)
    // Alternativement, créer une classe PlayerConnectionListener séparée.
    public static class PlayerConnectionListener implements Listener {
        private final EntrepriseManagerLogic logic;

        public PlayerConnectionListener(EntrepriseManagerLogic logic) {
            this.logic = logic;
        }

        @EventHandler
        public void onPlayerJoin(PlayerJoinEvent event) {
            Player player = event.getPlayer();
            // Envoyer les messages de primes différées pour les employés et les gérants
            Bukkit.getScheduler().runTaskLater(EntrepriseManager.getInstance(), () -> {
                logic.envoyerPrimesDifferreesEmployes(player);
                logic.envoyerPrimesDifferreesGerants(player);
            }, 20L * 5); // Délai de 5 secondes pour laisser le temps au joueur de charger
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