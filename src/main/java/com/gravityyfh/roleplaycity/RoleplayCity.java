package com.gravityyfh.roleplaycity;

import com.gravityyfh.roleplaycity.Listener.*;
import com.gravityyfh.roleplaycity.Shop.*;
import com.gravityyfh.roleplaycity.town.TownCommandHandler;
import com.gravityyfh.roleplaycity.town.gui.TownMainGUI;
import com.gravityyfh.roleplaycity.town.manager.TownDataManager;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class RoleplayCity extends JavaPlugin implements Listener {
    private static RoleplayCity instance;
    private EntrepriseManagerLogic entrepriseLogic;
    private ChatListener chatListener;
    private EntrepriseGUI entrepriseGUI;
    private PlayerCVGUI playerCVGUI;
    private CVManager cvManager;
    private ShopManager shopManager;
    private ShopGUI shopGUI;
    private static Economy econ = null;
    private ShopInteractionListener shopInteractionListener;
    private ShopDestructionListener shopDestructionListener;
    private ShopDisplayItemListener shopDisplayItemListener;
    private BlockPlaceListener blockPlaceListener;
    private CraftItemListener craftItemListener;
    private SmithItemListener smithItemListener;
    private EntityDamageListener entityDamageListener;
    private EntityDeathListener entityDeathListener;
    private TreeCutListener treeCutListener;
    private TownEventListener townEventListener;

    // Système de Ville
    private TownManager townManager;
    private TownDataManager townDataManager;
    private TownMainGUI townMainGUI;
    private com.gravityyfh.roleplaycity.town.manager.ClaimManager claimManager;
    private com.gravityyfh.roleplaycity.town.manager.TownLevelManager townLevelManager;
    private com.gravityyfh.roleplaycity.town.manager.TownEconomyManager townEconomyManager;
    private com.gravityyfh.roleplaycity.town.manager.CompanyPlotManager companyPlotManager;
    private com.gravityyfh.roleplaycity.town.manager.TownPoliceManager townPoliceManager;
    private com.gravityyfh.roleplaycity.town.manager.TownJusticeManager townJusticeManager;
    private com.gravityyfh.roleplaycity.town.manager.TownFinesDataManager townFinesDataManager;
    private com.gravityyfh.roleplaycity.town.manager.FineExpirationManager fineExpirationManager;
    private com.gravityyfh.roleplaycity.town.gui.TownClaimsGUI townClaimsGUI;
    private com.gravityyfh.roleplaycity.town.gui.TownBankGUI townBankGUI;
    private com.gravityyfh.roleplaycity.town.gui.TownPlotManagementGUI townPlotManagementGUI;
    private com.gravityyfh.roleplaycity.town.gui.PlotOwnerGUI plotOwnerGUI;
    private com.gravityyfh.roleplaycity.town.gui.TownPoliceGUI townPoliceGUI;
    private com.gravityyfh.roleplaycity.town.gui.TownJusticeGUI townJusticeGUI;
    private com.gravityyfh.roleplaycity.town.gui.TownCitizenFinesGUI townCitizenFinesGUI;
    private com.gravityyfh.roleplaycity.town.gui.TownMembersGUI townMembersGUI;
    private com.gravityyfh.roleplaycity.town.gui.TownUpgradeGUI townUpgradeGUI;
    private com.gravityyfh.roleplaycity.town.gui.MyPropertyGUI myPropertyGUI;
    private com.gravityyfh.roleplaycity.town.gui.RentedPropertyGUI rentedPropertyGUI;
    private com.gravityyfh.roleplaycity.town.gui.MyCompaniesGUI myCompaniesGUI;
    private com.gravityyfh.roleplaycity.town.gui.CompanySelectionGUI companySelectionGUI;
    private com.gravityyfh.roleplaycity.town.gui.DebtManagementGUI debtManagementGUI;
    private com.gravityyfh.roleplaycity.town.gui.PlotGroupManagementGUI plotGroupManagementGUI;
    private com.gravityyfh.roleplaycity.town.listener.TownProtectionListener townProtectionListener;
    private com.gravityyfh.roleplaycity.town.listener.PlotGroupingListener plotGroupingListener;
    private com.gravityyfh.roleplaycity.town.task.TownEconomyTask townEconomyTask;
    private com.gravityyfh.roleplaycity.town.manager.NotificationDataManager notificationDataManager;
    private com.gravityyfh.roleplaycity.town.manager.NotificationManager notificationManager;
    private com.gravityyfh.roleplaycity.town.manager.DebtNotificationService debtNotificationService;

    // Système Médical
    private com.gravityyfh.roleplaycity.medical.manager.MedicalSystemManager medicalSystemManager;
    private com.gravityyfh.roleplaycity.medical.listener.MedicalListener medicalListener;
    private com.gravityyfh.roleplaycity.medical.listener.HealingMiniGameListener healingMiniGameListener;

    public void onEnable() {
        instance = this;
        getLogger().info("============================================");
        getLogger().info("-> Activation de RoleplayCity");
        getLogger().info("============================================");

        if (!setupEconomy()) {
            getLogger().severe("### ERREUR CRITIQUE : Vault non trouvé ou pas de fournisseur d'économie. ###");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        initializeComponents();
        shopManager.loadShops();
        registerAllListeners();
        setupCommands();

        getLogger().info("============================================");
        getLogger().info("-> RoleplayCity activé avec succès !");
        getLogger().info("============================================");
    }

    private void initializeComponents() {
        // Système d'entreprises
        entrepriseLogic = new EntrepriseManagerLogic(this);
        shopManager = new ShopManager(this);
        shopGUI = new ShopGUI(this);
        entrepriseGUI = new EntrepriseGUI(this, entrepriseLogic);
        chatListener = new ChatListener(this, entrepriseGUI);
        cvManager = new CVManager(this, entrepriseLogic);
        playerCVGUI = new PlayerCVGUI(this, entrepriseLogic, cvManager);
        cvManager.setPlayerCVGUI(playerCVGUI);

        // Système de ville
        townDataManager = new TownDataManager(this);
        townManager = new TownManager(this);
        townLevelManager = new com.gravityyfh.roleplaycity.town.manager.TownLevelManager(this);
        claimManager = new com.gravityyfh.roleplaycity.town.manager.ClaimManager(this, townManager);

        // Système de notifications (DOIT être créé AVANT TownEconomyManager)
        notificationDataManager = new com.gravityyfh.roleplaycity.town.manager.NotificationDataManager(this);
        notificationManager = new com.gravityyfh.roleplaycity.town.manager.NotificationManager(this, notificationDataManager, townManager);
        notificationManager.loadNotifications();
        notificationManager.scheduleAutomaticNotifications();

        debtNotificationService = new com.gravityyfh.roleplaycity.town.manager.DebtNotificationService(this, townManager);
        debtNotificationService.start();

        // Managers qui dépendent du NotificationManager
        townEconomyManager = new com.gravityyfh.roleplaycity.town.manager.TownEconomyManager(this, townManager, claimManager);
        companyPlotManager = new com.gravityyfh.roleplaycity.town.manager.CompanyPlotManager(this, townManager, entrepriseLogic, debtNotificationService);

        // Police et Justice
        townFinesDataManager = new com.gravityyfh.roleplaycity.town.manager.TownFinesDataManager(this);
        townPoliceManager = new com.gravityyfh.roleplaycity.town.manager.TownPoliceManager(this, townManager);
        townJusticeManager = new com.gravityyfh.roleplaycity.town.manager.TownJusticeManager(this, townManager, townPoliceManager);
        fineExpirationManager = new com.gravityyfh.roleplaycity.town.manager.FineExpirationManager(this, townPoliceManager, townManager);

        // GUIs
        townMainGUI = new TownMainGUI(this, townManager);
        townMembersGUI = new com.gravityyfh.roleplaycity.town.gui.TownMembersGUI(this, townManager);
        townUpgradeGUI = new com.gravityyfh.roleplaycity.town.gui.TownUpgradeGUI(this, townManager);
        townClaimsGUI = new com.gravityyfh.roleplaycity.town.gui.TownClaimsGUI(this, townManager, claimManager);
        townBankGUI = new com.gravityyfh.roleplaycity.town.gui.TownBankGUI(this, townManager, townEconomyManager);
        townPlotManagementGUI = new com.gravityyfh.roleplaycity.town.gui.TownPlotManagementGUI(this, townManager, claimManager, townEconomyManager);
        plotOwnerGUI = new com.gravityyfh.roleplaycity.town.gui.PlotOwnerGUI(this, townManager, claimManager);
        townPoliceGUI = new com.gravityyfh.roleplaycity.town.gui.TownPoliceGUI(this, townManager, townPoliceManager);
        townJusticeGUI = new com.gravityyfh.roleplaycity.town.gui.TownJusticeGUI(this, townManager, townPoliceManager, townJusticeManager);
        townCitizenFinesGUI = new com.gravityyfh.roleplaycity.town.gui.TownCitizenFinesGUI(this, townPoliceManager);
        myPropertyGUI = new com.gravityyfh.roleplaycity.town.gui.MyPropertyGUI(this, townManager, townMainGUI);
        rentedPropertyGUI = new com.gravityyfh.roleplaycity.town.gui.RentedPropertyGUI(this, townManager, myPropertyGUI);
        myCompaniesGUI = new com.gravityyfh.roleplaycity.town.gui.MyCompaniesGUI(this, townManager, townMainGUI);
        companySelectionGUI = new com.gravityyfh.roleplaycity.town.gui.CompanySelectionGUI(this);
        debtManagementGUI = new com.gravityyfh.roleplaycity.town.gui.DebtManagementGUI(this, townManager, townMainGUI, debtNotificationService);
        plotGroupManagementGUI = new com.gravityyfh.roleplaycity.town.gui.PlotGroupManagementGUI(this, townManager, claimManager);

        // Listeners
        townProtectionListener = new com.gravityyfh.roleplaycity.town.listener.TownProtectionListener(this, townManager, claimManager);
        plotGroupingListener = new com.gravityyfh.roleplaycity.town.listener.PlotGroupingListener(this, townManager, claimManager);

        // Charger les villes
        townManager.loadTowns(townDataManager.loadTowns());

        // Charger les amendes
        townPoliceManager.loadFines(townFinesDataManager.loadFines());

        // Démarrer la vérification automatique des amendes expirées
        fineExpirationManager.startAutomaticChecks();

        // Reconstruire le cache de claims
        claimManager.rebuildCache();

        // Lier les GUIs
        townMainGUI.setClaimsGUI(townClaimsGUI);
        townMainGUI.setBankGUI(townBankGUI);
        townMainGUI.setPlotManagementGUI(townPlotManagementGUI);
        townMainGUI.setPoliceGUI(townPoliceGUI);
        townMainGUI.setJusticeGUI(townJusticeGUI);
        townMainGUI.setCitizenFinesGUI(townCitizenFinesGUI);
        townMainGUI.setMembersGUI(townMembersGUI);
        townMainGUI.setUpgradeGUI(townUpgradeGUI);
        townMainGUI.setMyPropertyGUI(myPropertyGUI);
        townMainGUI.setMyCompaniesGUI(myCompaniesGUI);
        townMainGUI.setDebtManagementGUI(debtManagementGUI);
        townMembersGUI.setMainGUI(townMainGUI);
        townUpgradeGUI.setMainGUI(townMainGUI);
        townClaimsGUI.setPlotManagementGUI(townPlotManagementGUI);

        // Démarrer la tâche économique récurrente
        townEconomyTask = new com.gravityyfh.roleplaycity.town.task.TownEconomyTask(this, townManager, townEconomyManager);
        townEconomyTask.start();

        // Système Médical
        medicalSystemManager = new com.gravityyfh.roleplaycity.medical.manager.MedicalSystemManager(this);
        medicalListener = new com.gravityyfh.roleplaycity.medical.listener.MedicalListener(this);
        healingMiniGameListener = new com.gravityyfh.roleplaycity.medical.listener.HealingMiniGameListener(this);

        // Shop Listeners
        shopInteractionListener = new ShopInteractionListener(this);
        shopDestructionListener = new ShopDestructionListener(this);
        shopDisplayItemListener = new ShopDisplayItemListener();

        // Activity Listeners
        blockPlaceListener = new BlockPlaceListener(this, entrepriseLogic);
        craftItemListener = new CraftItemListener(this, entrepriseLogic);
        smithItemListener = new SmithItemListener(entrepriseLogic);
        entityDamageListener = new EntityDamageListener(this, entrepriseLogic);
        entityDeathListener = new EntityDeathListener(this, entrepriseLogic);
        treeCutListener = new TreeCutListener(entrepriseLogic, this);

        // Town Event Listener (remplace TownyListener)
        townEventListener = new TownEventListener(this, entrepriseLogic);

        // PlaceholderAPI integration
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new com.gravityyfh.roleplaycity.integration.RoleplayCityPlaceholders(this).register();
            getLogger().info("PlaceholderAPI détecté - Placeholders enregistrés avec succès.");
        } else {
            getLogger().warning("PlaceholderAPI non trouvé - Les placeholders ne seront pas disponibles.");
        }

        getLogger().info("Tous les composants ont été initialisés avec succès.");
    }

    public void onDisable() {
        // Arrêter la tâche économique
        if (townEconomyTask != null) {
            townEconomyTask.cancel();
        }

        // Nettoyer le système médical
        if (medicalSystemManager != null) {
            medicalSystemManager.cleanup();
        }

        if (shopManager != null) {
            shopManager.saveShops();
        }
        if (entrepriseLogic != null) {
            entrepriseLogic.saveEntreprises();
        }
        if (townManager != null) {
            // Utiliser saveTownsSync() pour une sauvegarde synchrone lors de l'arrêt
            townManager.saveTownsSync();
        }
        if (notificationManager != null) {
            // Sauvegarder les notifications de manière synchrone
            notificationManager.saveNotificationsSync();
        }
        if (townPoliceManager != null && townFinesDataManager != null) {
            townFinesDataManager.saveFines(townPoliceManager.getFinesForSave());
        }
        getLogger().info("RoleplayCity désactivé. Données sauvegardées.");
    }

    private void registerAllListeners() {
        var pm = getServer().getPluginManager();
        var listeners = new Listener[] {
            this, chatListener, entrepriseGUI, playerCVGUI, shopGUI,
            shopInteractionListener, shopDestructionListener, shopDisplayItemListener,
            blockPlaceListener, craftItemListener, smithItemListener,
            entityDamageListener, entityDeathListener, treeCutListener,
            townMainGUI, townMembersGUI, townUpgradeGUI, townClaimsGUI, townBankGUI, townPlotManagementGUI, plotOwnerGUI, // GUI du système de ville
            myPropertyGUI, rentedPropertyGUI, myCompaniesGUI, debtManagementGUI, // GUI Mes Propriétés, Mes Entreprises, et Dettes
            companySelectionGUI, // GUI Sélection Entreprise
            townPoliceGUI, townJusticeGUI, townCitizenFinesGUI, // GUI Police et Justice
            plotGroupManagementGUI, plotGroupingListener, // GUI et Listener pour groupement de terrains
            townProtectionListener, // Protection des territoires de ville
            new com.gravityyfh.roleplaycity.town.listener.TownHUDListener(this, townManager, claimManager), // HUD pour afficher les infos de territoire
            townEventListener, // Événements de ville (suppression, départ membres)
            medicalListener, // Système médical (Revive intégré)
            healingMiniGameListener, // Mini-jeu de suture pour les soins médicaux
            new EventListener(this, entrepriseLogic),
            new PlayerConnectionListener(this, entrepriseLogic),
            new com.gravityyfh.roleplaycity.town.listener.PlayerConnectionListener(this) // Listener pour les notifications
        };

        for (Listener listener : listeners) {
            if (listener != null) {
                pm.registerEvents(listener, this);
            }
        }

        getLogger().info("Tous les listeners ont été enregistrés avec succès.");
    }

    private void setupCommands() {
        var entrepriseCmd = getCommand("entreprise");
        if (entrepriseCmd != null) {
            entrepriseCmd.setExecutor(new EntrepriseCommandHandler(this, entrepriseLogic, entrepriseGUI, cvManager));
            getLogger().info("Gestionnaire de commandes pour /entreprise enregistré.");
        } else {
            getLogger().severe("ERREUR: La commande 'entreprise' n'est pas définie dans plugin.yml !");
        }

        var villeCmd = getCommand("ville");
        if (villeCmd != null) {
            villeCmd.setExecutor(new TownCommandHandler(this, townManager));
            getLogger().info("Gestionnaire de commandes pour /ville enregistré.");
        } else {
            getLogger().severe("ERREUR: La commande 'ville' n'est pas définie dans plugin.yml !");
        }

        var medicalCmd = getCommand("medical");
        if (medicalCmd != null) {
            medicalCmd.setExecutor(new com.gravityyfh.roleplaycity.medical.command.MedicalCommand(this));
            getLogger().info("Gestionnaire de commandes pour /medical enregistré.");
        } else {
            getLogger().severe("ERREUR: La commande 'medical' n'est pas définie dans plugin.yml !");
        }

        var mourirCmd = getCommand("mourir");
        if (mourirCmd != null) {
            mourirCmd.setExecutor(new com.gravityyfh.roleplaycity.medical.command.DieCommand(this));
            getLogger().info("Gestionnaire de commandes pour /mourir enregistré.");
        } else {
            getLogger().severe("ERREUR: La commande 'mourir' n'est pas définie dans plugin.yml !");
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
        reloadConfig();
        if (entrepriseLogic != null) {
            entrepriseLogic.reloadPluginData();
        }
        if (shopManager != null) {
            shopManager.loadShops();
        }
        getLogger().info("Plugin RoleplayCity et ses données ont été rechargés.");
    }

    /**
     * Intercepte les commandes avec le préfixe "ville:" pour les rediriger vers "/ville"
     * Permet aux boutons cliquables de fonctionner correctement
     */
    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();

        // Intercepter les commandes ville:xxx et les transformer en /ville xxx
        if (message.startsWith("/ville:")) {
            String subCommand = message.substring(7); // Enlever "/ville:"
            event.setMessage("/ville " + subCommand);
        }
    }

    // Getters
    public static RoleplayCity getInstance() { return instance; }
    public EntrepriseManagerLogic getEntrepriseManagerLogic() { return entrepriseLogic; }
    public ChatListener getChatListener() { return chatListener; }
    public EntrepriseGUI getEntrepriseGUI() { return entrepriseGUI; }
    public PlayerCVGUI getPlayerCVGUI() { return playerCVGUI; }
    public static Economy getEconomy() { return econ; }
    public ShopManager getShopManager() { return shopManager; }
    public ShopGUI getShopGUI() { return shopGUI; }
    public TownManager getTownManager() { return townManager; }
    public com.gravityyfh.roleplaycity.town.manager.TownDataManager getTownDataManager() { return townDataManager; }
    public com.gravityyfh.roleplaycity.town.manager.ClaimManager getClaimManager() { return claimManager; }
    public com.gravityyfh.roleplaycity.town.manager.TownLevelManager getTownLevelManager() { return townLevelManager; }
    public com.gravityyfh.roleplaycity.town.manager.TownEconomyManager getTownEconomyManager() { return townEconomyManager; }
    public com.gravityyfh.roleplaycity.town.manager.CompanyPlotManager getCompanyPlotManager() { return companyPlotManager; }
    public com.gravityyfh.roleplaycity.town.manager.TownPoliceManager getTownPoliceManager() { return townPoliceManager; }
    public com.gravityyfh.roleplaycity.town.manager.TownFinesDataManager getTownFinesDataManager() { return townFinesDataManager; }
    public com.gravityyfh.roleplaycity.town.manager.FineExpirationManager getFineExpirationManager() { return fineExpirationManager; }
    public com.gravityyfh.roleplaycity.town.gui.TownPlotManagementGUI getTownPlotManagementGUI() { return townPlotManagementGUI; }
    public com.gravityyfh.roleplaycity.town.gui.PlotOwnerGUI getPlotOwnerGUI() { return plotOwnerGUI; }
    public com.gravityyfh.roleplaycity.town.gui.MyPropertyGUI getMyPropertyGUI() { return myPropertyGUI; }
    public com.gravityyfh.roleplaycity.town.gui.RentedPropertyGUI getRentedPropertyGUI() { return rentedPropertyGUI; }
    public com.gravityyfh.roleplaycity.town.gui.CompanySelectionGUI getCompanySelectionGUI() { return companySelectionGUI; }
    public com.gravityyfh.roleplaycity.town.manager.NotificationManager getNotificationManager() { return notificationManager; }
    public com.gravityyfh.roleplaycity.town.manager.DebtNotificationService getDebtNotificationService() { return debtNotificationService; }
    public com.gravityyfh.roleplaycity.town.gui.PlotGroupManagementGUI getPlotGroupManagementGUI() { return plotGroupManagementGUI; }
    public com.gravityyfh.roleplaycity.town.listener.PlotGroupingListener getPlotGroupingListener() { return plotGroupingListener; }
    public TownCommandHandler getTownCommandHandler() {
        return (TownCommandHandler) getCommand("ville").getExecutor();
    }
    public com.gravityyfh.roleplaycity.town.gui.TownMainGUI getTownMainGUI() { return townMainGUI; }
    public com.gravityyfh.roleplaycity.medical.manager.MedicalSystemManager getMedicalSystemManager() { return medicalSystemManager; }
}
