package com.gravityyfh.entreprisemanager;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level; // Pour un logging plus fin

public class EntrepriseManager extends JavaPlugin implements Listener {

    private static EntrepriseManager instance;
    private EntrepriseManagerLogic entrepriseLogic;
    private ChatListener chatListener;
    private EntrepriseGUI entrepriseGUI;
    private PlayerCVGUI playerCVGUI; // Pour l'interface graphique des CV
    private CVManager cvManager;     // Pour la logique de gestion des CV

    private static Economy econ = null;

    // Listeners spécifiques aux actions pour éviter de les recréer inutilement
    private CraftItemListener craftItemListener;
    private BlockPlaceListener blockPlaceListener;
    private EntityDeathListener entityDeathListener;
    private EntityDamageListener entityDamageListener;
    private EventListener mainEventListener; // Pour BlockBreak
    private TreeCutListener treeCutListener;
    private TownyListener townyListener;
    private PlayerConnectionListener playerConnectionListener;


    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("============================================");
        getLogger().info("-> Activation d'EntrepriseManager V2");
        getLogger().info("============================================");


        if (!setupEconomy()) {
            getLogger().severe("### ERREUR CRITIQUE : Vault non trouvé ou pas de fournisseur d'économie. ###");
            getLogger().severe("### EntrepriseManager ne peut pas fonctionner sans Vault et une économie. ###");
            getLogger().severe("### DÉSACTIVATION DU PLUGIN. ###");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Vault et le fournisseur d'économie ont été trouvés et initialisés.");

        saveDefaultConfig(); // Assure que config.yml existe et charge les valeurs par défaut si nécessaire
        getLogger().info("Configuration chargée (ou créée par défaut).");

        // Initialisation des composants principaux dans un ordre logique
        entrepriseLogic = new EntrepriseManagerLogic(this);
        getLogger().info("EntrepriseManagerLogic initialisé.");

        entrepriseGUI = new EntrepriseGUI(this, entrepriseLogic);
        getLogger().info("EntrepriseGUI initialisé.");

        chatListener = new ChatListener(this, entrepriseGUI); // ChatListener peut avoir besoin d'EntrepriseGUI
        getLogger().info("ChatListener initialisé.");

        // Initialisation des composants du système de CV
        // CVManager a besoin d'EntrepriseManagerLogic. PlayerCVGUI a besoin de CVManager et EntrepriseManagerLogic.
        cvManager = new CVManager(this, entrepriseLogic);
        getLogger().info("CVManager initialisé (sans PlayerCVGUI pour l'instant).");

        playerCVGUI = new PlayerCVGUI(this, entrepriseLogic, cvManager);
        getLogger().info("PlayerCVGUI initialisé (avec dépendances).");

        // Injection de dépendance circulaire pour CVManager après l'initialisation de PlayerCVGUI
        cvManager.setPlayerCVGUI(playerCVGUI);
        getLogger().info("Dépendance PlayerCVGUI injectée dans CVManager.");


        // Enregistrement des listeners d'événements
        registerAllListeners();

        // Enregistrement des commandes
        setupCommands();

        getLogger().info("============================================");
        getLogger().info("-> EntrepriseManager V2 activé avec succès !");
        getLogger().info("============================================");
    }

    @Override
    public void onDisable() {
        if (entrepriseLogic != null) {
            entrepriseLogic.saveEntreprises(); // S'assurer que tout est sauvegardé
        }
        getLogger().info("EntrepriseManager désactivé. Données sauvegardées.");
    }

    private void registerAllListeners() {
        getLogger().info("Enregistrement des listeners...");
        // Listener principal du plugin (pour PlayerCommandPreprocessEvent, etc.)
        getServer().getPluginManager().registerEvents(this, this);

        // Listeners pour les GUIs et interactions chat
        // Assurez-vous que chatListener, entrepriseGUI, playerCVGUI sont initialisés avant ici
        if (chatListener != null) getServer().getPluginManager().registerEvents(chatListener, this);
        if (entrepriseGUI != null) getServer().getPluginManager().registerEvents(entrepriseGUI, this);
        if (playerCVGUI != null) getServer().getPluginManager().registerEvents(playerCVGUI, this);

        // Listeners pour les actions de jeu
        // Assurez-vous que mainEventListener, craftItemListener, etc. sont initialisés
        if (mainEventListener == null) mainEventListener = new EventListener(this, entrepriseLogic);
        getServer().getPluginManager().registerEvents(mainEventListener, this);

        if (craftItemListener == null) craftItemListener = new CraftItemListener(this, entrepriseLogic);
        getServer().getPluginManager().registerEvents(craftItemListener, this);

        if (blockPlaceListener == null) blockPlaceListener = new BlockPlaceListener(this, entrepriseLogic);
        getServer().getPluginManager().registerEvents(blockPlaceListener, this);

        if (entityDeathListener == null) entityDeathListener = new EntityDeathListener(this, entrepriseLogic);
        getServer().getPluginManager().registerEvents(entityDeathListener, this);

        if (entityDamageListener == null) entityDamageListener = new EntityDamageListener(this, entrepriseLogic);
        getServer().getPluginManager().registerEvents(entityDamageListener, this);


        // Listeners pour intégrations externes et autres
        if (getServer().getPluginManager().getPlugin("TreeCuter") != null) {
            if (treeCutListener == null) treeCutListener = new TreeCutListener(entrepriseLogic, this); // Correction de l'ordre des params si besoin
            getServer().getPluginManager().registerEvents(treeCutListener, this);
            getLogger().info("TreeCutListener enregistré (intégration TreeCuter active).");
        } else {
            getLogger().info("Plugin TreeCuter non trouvé, TreeCutListener non enregistré.");
        }

        if (getServer().getPluginManager().getPlugin("Towny") != null) {
            if (townyListener == null) townyListener = new TownyListener(this, entrepriseLogic);
            getServer().getPluginManager().registerEvents(townyListener, this);
            getLogger().info("TownyListener enregistré (intégration Towny active).");
        } else {
            getLogger().info("Plugin Towny non trouvé, TownyListener non enregistré.");
        }

        // --- CORRECTION ICI pour PlayerConnectionListener ---
        if (entrepriseLogic != null) {
            // Si vous utilisez un champ playerConnectionListener:
            // this.playerConnectionListener = new PlayerConnectionListener(this, entrepriseLogic);
            // getServer().getPluginManager().registerEvents(this.playerConnectionListener, this);

            // Ou si vous l'instanciez directement lors de l'enregistrement (plus simple si pas besoin du champ):
            getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this, entrepriseLogic), this);
            getLogger().info("PlayerConnectionListener enregistré.");
        } else {
            getLogger().severe("ERREUR: entrepriseLogic est null, PlayerConnectionListener ne peut pas être enregistré !");
        }
        // --- FIN CORRECTION ---

        getLogger().info("Tous les listeners applicables ont été enregistrés.");
    }

    private void setupCommands() {
        EntrepriseCommandHandler commandHandler = new EntrepriseCommandHandler(this, entrepriseLogic, entrepriseGUI, cvManager);
        if (getCommand("entreprise") != null) {
            getCommand("entreprise").setExecutor(commandHandler);
            getLogger().info("Gestionnaire de commandes pour /entreprise enregistré.");
        } else {
            getLogger().severe("ERREUR: La commande 'entreprise' n'est pas définie dans plugin.yml !");
        }
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

    public void reloadPluginData() {
        super.reloadConfig(); // Recharge config.yml
        if (entrepriseLogic != null) {
            entrepriseLogic.reloadPluginData(); // Gère le rechargement de entreprise.yml et autres données logiques
        }
        // Si CVManager ou PlayerCVGUI lisent des configurations spécifiques (autre que config.yml),
        // il faudrait ajouter des méthodes de rechargement ici. Pour l'instant, ils utilisent
        // la config principale via plugin.getConfig() ou des valeurs par défaut.
        getLogger().info("Plugin EntrepriseManager V2 et ses données ont été rechargés.");
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage().toLowerCase();
        Player player = event.getPlayer();

        // Intercepter la création de shop QuickShop
        if (message.startsWith("/qs create") || message.startsWith("/quickshop create")) {
            if (entrepriseLogic != null && entrepriseLogic.getEntreprisesGereesPar(player.getName()).isEmpty()) {
                player.sendMessage(ChatColor.RED + "❌ Seuls les gérants d'une entreprise peuvent créer un shop QuickShop.");
                event.setCancelled(true);
            }
        }
    }

    // --- Getters ---
    public static EntrepriseManager getInstance() {
        return instance;
    }

    public EntrepriseManagerLogic getEntrepriseLogic() {
        return entrepriseLogic;
    }

    public ChatListener getChatListener() {
        return chatListener;
    }

    public EntrepriseGUI getEntrepriseGUI() { // Getter pour EntrepriseGUI
        return entrepriseGUI;
    }

    public PlayerCVGUI getPlayerCVGUI() { // Getter pour PlayerCVGUI
        return playerCVGUI;
    }

    public CVManager getCvManager() { // Getter pour CVManager (existait déjà, mais confirmé)
        return cvManager;
    }

    public static Economy getEconomy() {
        return econ;
    }
}