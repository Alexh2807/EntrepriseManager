package com.gravityyfh.roleplaycity;

import com.gravityyfh.roleplaycity.Listener.*;
import com.gravityyfh.roleplaycity.town.TownCommandHandler;
import com.gravityyfh.roleplaycity.town.data.TownTeleportCooldown;
import com.gravityyfh.roleplaycity.town.gui.NoTownGUI;
import com.gravityyfh.roleplaycity.town.gui.TownListGUI;
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
    private Economy econ;
    private TownManager townManager;
    private com.gravityyfh.roleplaycity.town.manager.ClaimManager claimManager;
    private TownDataManager townDataManager;
    private com.gravityyfh.roleplaycity.town.manager.TownEconomyManager townEconomyManager;
    private TownMainGUI townMainGUI;
    private com.gravityyfh.roleplaycity.Listener.TownEventListener townEventListener;
    private com.gravityyfh.roleplaycity.town.manager.TownLevelManager townLevelManager;
    // Listeners
    private com.gravityyfh.roleplaycity.Listener.BlockPlaceListener blockPlaceListener;
    private com.gravityyfh.roleplaycity.Listener.CraftItemListener craftItemListener;
    private com.gravityyfh.roleplaycity.Listener.SmithItemListener smithItemListener;
    private com.gravityyfh.roleplaycity.Listener.EntityDamageListener entityDamageListener;
    private com.gravityyfh.roleplaycity.Listener.EntityDeathListener entityDeathListener;
    private com.gravityyfh.roleplaycity.Listener.TreeCutListener treeCutListener;
    private EntrepriseManagerLogic entrepriseLogic;
    private ChatListener chatListener;
    private EntrepriseGUI entrepriseGUI;
    // Système de contrats
    private com.gravityyfh.roleplaycity.contract.service.ContractService contractService;
    private com.gravityyfh.roleplaycity.contract.gui.ContractManagementGUI contractManagementGUI;
    private com.gravityyfh.roleplaycity.contract.gui.ContractCreationGUI contractCreationGUI;
    private com.gravityyfh.roleplaycity.contract.gui.ContractDetailsGUI contractDetailsGUI;
    private com.gravityyfh.roleplaycity.contract.listener.ContractChatListener contractChatListener;
    private PlayerCVGUI playerCVGUI;
    private CVManager cvManager;
    private com.gravityyfh.roleplaycity.shop.manager.ShopManager shopManager; // Nouveau système de boutiques
    private com.gravityyfh.roleplaycity.shop.gui.ShopListGUI shopListGUI;
    private com.gravityyfh.roleplaycity.shop.gui.ShopManagementGUI shopManagementGUI;
    private com.gravityyfh.roleplaycity.shop.gui.ShopCreationGUI shopCreationGUI;
    private com.gravityyfh.roleplaycity.shop.listener.ShopPlacementListener shopPlacementListener;
    private com.gravityyfh.roleplaycity.town.manager.CompanyPlotManager companyPlotManager;
    private com.gravityyfh.roleplaycity.town.manager.EnterpriseContextManager enterpriseContextManager;
    private com.gravityyfh.roleplaycity.town.manager.TownPoliceManager townPoliceManager;
    private com.gravityyfh.roleplaycity.town.manager.TownFinesDataManager townFinesDataManager;
    private com.gravityyfh.roleplaycity.town.manager.FineExpirationManager fineExpirationManager;
    private com.gravityyfh.roleplaycity.town.gui.TownPlotManagementGUI townPlotManagementGUI;
    private com.gravityyfh.roleplaycity.town.gui.TownClaimsGUI townClaimsGUI;
    private com.gravityyfh.roleplaycity.town.gui.TownBankGUI townBankGUI;
    private com.gravityyfh.roleplaycity.town.manager.TownJusticeManager townJusticeManager;
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
    private com.gravityyfh.roleplaycity.town.gui.TownListGUI townListGUI;
    private com.gravityyfh.roleplaycity.town.gui.NoTownGUI noTownGUI;
    private com.gravityyfh.roleplaycity.town.data.TownTeleportCooldown townTeleportCooldown;
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

    // Système Postal (La Poste)
    private com.gravityyfh.roleplaycity.postal.manager.MailboxManager mailboxManager;
    private com.gravityyfh.roleplaycity.postal.gui.MailboxPlacementGUI mailboxPlacementGUI;
    private com.gravityyfh.roleplaycity.postal.gui.MailboxVisualPlacement mailboxVisualPlacement;
    private com.gravityyfh.roleplaycity.postal.gui.LaPosteGUI laPosteGUI;
    private com.gravityyfh.roleplaycity.postal.listener.MailboxInteractionListener mailboxInteractionListener;
    private com.gravityyfh.roleplaycity.postal.listener.MailboxNotificationListener mailboxNotificationListener;
    private com.gravityyfh.roleplaycity.postal.listener.PlotGroupMailboxListener plotGroupMailboxListener;

    // FIX BASSE #28: Système de métriques de performance
    private com.gravityyfh.roleplaycity.util.PerformanceMetrics performanceMetrics;

    // FIX BASSE #29: Système de debug configurable
    private com.gravityyfh.roleplaycity.util.DebugLogger debugLogger;

    // FIX BASSE #20-24: Système de messages avec i18n
    private com.gravityyfh.roleplaycity.util.MessageManager messageManager;

    // FIX BASSE #31-33: Système de validation et sanitisation des noms
    private com.gravityyfh.roleplaycity.util.NameValidator nameValidator;

    // FIX BASSE #8: Cache des joueurs en ligne pour améliorer les performances
    private com.gravityyfh.roleplaycity.util.PlayerCache playerCache;

    // FIX BASSE #10: Gestionnaire de pool de threads pour les tâches asynchrones
    private com.gravityyfh.roleplaycity.util.AsyncTaskManager asyncTaskManager;

    // FIX PERFORMANCES: Cache des blocs placés pour éviter les lookups CoreProtect
    // synchrones
    private com.gravityyfh.roleplaycity.util.PlayerBlockPlaceCache blockPlaceCache;

    // Système de messages interactifs (remplacement des commandes à taper)
    private com.gravityyfh.roleplaycity.util.ConfirmationManager confirmationManager;
    private com.gravityyfh.roleplaycity.util.ChatInputListener chatInputListener;

    // Système de Police (Taser & Menottes)
    private com.gravityyfh.roleplaycity.police.items.PoliceItemManager policeItemManager;
    private com.gravityyfh.roleplaycity.police.data.TasedPlayerData tasedPlayerData;
    private com.gravityyfh.roleplaycity.police.data.HandcuffedPlayerData handcuffedPlayerData;
    private com.gravityyfh.roleplaycity.police.listeners.PoliceCraftListener policeCraftListener;
    private com.gravityyfh.roleplaycity.police.listeners.TaserListener taserListener;
    private com.gravityyfh.roleplaycity.police.listeners.HandcuffsListener handcuffsListener;
    private com.gravityyfh.roleplaycity.police.listeners.HandcuffsBreakListener handcuffsBreakListener;
    private com.gravityyfh.roleplaycity.police.listeners.HandcuffsFollowListener handcuffsFollowListener;

    // Système de Prison
    private com.gravityyfh.roleplaycity.police.manager.PrisonManager prisonManager;
    private com.gravityyfh.roleplaycity.police.gui.TownPrisonManagementGUI townPrisonManagementGUI;
    private com.gravityyfh.roleplaycity.police.gui.ImprisonmentWorkflowGUI imprisonmentWorkflowGUI;
    private com.gravityyfh.roleplaycity.police.listeners.PrisonRestrictionListener prisonRestrictionListener;
    private com.gravityyfh.roleplaycity.police.listeners.PrisonBoundaryListener prisonBoundaryListener;

    // Système de Backpacks
    private com.gravityyfh.roleplaycity.backpack.manager.BackpackItemManager backpackItemManager;
    private com.gravityyfh.roleplaycity.backpack.manager.BackpackManager backpackManager;
    private com.gravityyfh.roleplaycity.backpack.util.BackpackUtil backpackUtil;
    private com.gravityyfh.roleplaycity.backpack.gui.BackpackGUI backpackGUI;
    private com.gravityyfh.roleplaycity.backpack.listener.BackpackInteractionListener backpackInteractionListener;
    private com.gravityyfh.roleplaycity.backpack.listener.BackpackProtectionListener backpackProtectionListener;
    private com.gravityyfh.roleplaycity.backpack.listener.BackpackCraftListener backpackCraftListener;
    private com.gravityyfh.roleplaycity.backpack.command.BackpackCommandHandler backpackCommandHandler;

    // Système de Mode Service
    private com.gravityyfh.roleplaycity.service.ServiceModeManager serviceModeManager;
    private com.gravityyfh.roleplaycity.service.ServiceModeListener serviceModeListener;
    
    // Système de Stockage d'Entreprise
    private com.gravityyfh.roleplaycity.entreprise.storage.CompanyStorageManager companyStorageManager;
    
    // Système de Loto
    private com.gravityyfh.roleplaycity.lotto.LottoManager lottoManager;
    private com.gravityyfh.roleplaycity.lotto.LottoGUI lottoGUI;

    // PHASE 6: Service Layer (SQLite)
    private com.gravityyfh.roleplaycity.entreprise.service.ServiceFactory serviceFactory;
    private com.gravityyfh.roleplaycity.entreprise.service.AsyncEntrepriseService asyncEntrepriseService;
    private boolean usingSQLiteServices = false;

    // PHASE 7: Global SQLite Migration (roleplaycity.db)
    private com.gravityyfh.roleplaycity.database.ConnectionManager connectionManager;

    public com.gravityyfh.roleplaycity.database.ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    private com.gravityyfh.roleplaycity.database.YAMLMigrationManager yamlMigrationManager;
    private com.gravityyfh.roleplaycity.town.service.TownPersistenceService townPersistenceService;
    private com.gravityyfh.roleplaycity.shop.service.ShopPersistenceService shopPersistenceService;
    private com.gravityyfh.roleplaycity.service.ServicePersistenceService servicePersistenceService;
    private com.gravityyfh.roleplaycity.medical.service.MedicalPersistenceService medicalPersistenceService;
    private com.gravityyfh.roleplaycity.town.service.FinesPersistenceService finesPersistenceService;
    private com.gravityyfh.roleplaycity.town.service.NotificationPersistenceService notificationPersistenceService;
    private com.gravityyfh.roleplaycity.backpack.service.BackpackPersistenceService backpackPersistenceService;
    private com.gravityyfh.roleplaycity.police.service.PrisonPersistenceService prisonPersistenceService;

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
        // FIX BASSE #25: Valider la configuration au startup
        validateConfiguration();
        initializeComponents();

        // Charger les boutiques
        if (shopManager != null) {
            try {
                shopManager.loadShops();
            } catch (Exception e) {
                getLogger().severe("[ShopSystem] ERREUR lors du chargement des boutiques: " + e.getMessage());
                e.printStackTrace();
            }
        }

        registerAllListeners();
        setupCommands();

        getLogger().info("============================================");
        getLogger().info("-> RoleplayCity activé avec succès !");
        getLogger().info("============================================");
    }

    private void initializeComponents() {
        // FIX BASSE #28: Initialiser métriques de performance
        performanceMetrics = new com.gravityyfh.roleplaycity.util.PerformanceMetrics(this);

        // FIX BASSE #29: Initialiser système de debug
        debugLogger = new com.gravityyfh.roleplaycity.util.DebugLogger(this);
        debugLogger.debug("STARTUP", "Initialisation des composants...");

        // FIX BASSE #20-24: Initialiser système de messages i18n
        messageManager = new com.gravityyfh.roleplaycity.util.MessageManager(this);

        // FIX BASSE #31-33: Initialiser système de validation des noms
        nameValidator = new com.gravityyfh.roleplaycity.util.NameValidator(this);

        // FIX BASSE #8: Initialiser cache des joueurs en ligne
        playerCache = new com.gravityyfh.roleplaycity.util.PlayerCache(this);
        getServer().getPluginManager().registerEvents(playerCache, this);
        debugLogger.debug("STARTUP", "PlayerCache initialisé avec " + playerCache.getOnlineCount() + " joueurs");

        // FIX BASSE #10: Initialiser gestionnaire de tâches asynchrones
        asyncTaskManager = new com.gravityyfh.roleplaycity.util.AsyncTaskManager(this);
        debugLogger.debug("STARTUP", "AsyncTaskManager initialisé");

        // FIX PERFORMANCES: Initialiser cache de blocs placés (évite lookups
        // CoreProtect)
        blockPlaceCache = new com.gravityyfh.roleplaycity.util.PlayerBlockPlaceCache(this);
        debugLogger.debug("STARTUP", "PlayerBlockPlaceCache initialisé (optimisation CoreProtect)");

        // Initialiser systèmes de messages interactifs
        confirmationManager = new com.gravityyfh.roleplaycity.util.ConfirmationManager(this);
        chatInputListener = new com.gravityyfh.roleplaycity.util.ChatInputListener(this);
        debugLogger.debug("STARTUP", "Systèmes interactifs initialisés (ConfirmationManager, ChatInputListener)");

        // PHASE 7: Initialiser ConnectionManager GLOBAL (roleplaycity.db)
        getLogger().info("========================================");
        getLogger().info("  INITIALISATION SQLite GLOBALE");
        getLogger().info("========================================");
        connectionManager = new com.gravityyfh.roleplaycity.database.ConnectionManager(this);
        connectionManager.initialize();

        // Migrer depuis YAML si nécessaire
        yamlMigrationManager = new com.gravityyfh.roleplaycity.database.YAMLMigrationManager(this, connectionManager);
        boolean migrationPerformed = yamlMigrationManager.checkAndMigrate();
        if (migrationPerformed) {
            yamlMigrationManager.generateMigrationReport();
        }

        // Initialiser TOUS les services de persistance
        initializeAllPersistenceServices();

        // FIX BUG: Initialiser les managers manquants (Town & Police System)
        townDataManager = new com.gravityyfh.roleplaycity.town.manager.TownDataManager(this, townPersistenceService);
        townFinesDataManager = new com.gravityyfh.roleplaycity.town.manager.TownFinesDataManager(this, finesPersistenceService);
        notificationDataManager = new com.gravityyfh.roleplaycity.town.manager.NotificationDataManager(this, notificationPersistenceService);
        
        townLevelManager = new com.gravityyfh.roleplaycity.town.manager.TownLevelManager(this);
        townManager = new com.gravityyfh.roleplaycity.town.manager.TownManager(this);
        claimManager = new com.gravityyfh.roleplaycity.town.manager.ClaimManager(this, townManager);
        
        notificationManager = new com.gravityyfh.roleplaycity.town.manager.NotificationManager(this, notificationDataManager, townManager);
        
        townPoliceManager = new com.gravityyfh.roleplaycity.town.manager.TownPoliceManager(this, townManager);
        townJusticeManager = new com.gravityyfh.roleplaycity.town.manager.TownJusticeManager(this, townManager, townPoliceManager);
        fineExpirationManager = new com.gravityyfh.roleplaycity.town.manager.FineExpirationManager(this, townPoliceManager, townManager);

        // PHASE 6: Initialiser Service Layer (SQLite ou fallback YAML)
        initializeEntrepriseServices();

        // Système d'entreprises
        entrepriseLogic = new EntrepriseManagerLogic(this);
        
        // Initialisation CVManager et PlayerCVGUI (avec injection de dépendance croisée)
        cvManager = new CVManager(this, entrepriseLogic);
        playerCVGUI = new PlayerCVGUI(this, entrepriseLogic, cvManager);
        cvManager.setPlayerCVGUI(playerCVGUI);
        
        // Initialisation EntrepriseGUI
        entrepriseGUI = new EntrepriseGUI(this, entrepriseLogic);
        
        // Initialisation du système de stockage d'entreprise
        companyStorageManager = new com.gravityyfh.roleplaycity.entreprise.storage.CompanyStorageManager(this);
        
        // Initialisation du système de Loto
        lottoManager = new com.gravityyfh.roleplaycity.lotto.LottoManager(this);
        lottoGUI = new com.gravityyfh.roleplaycity.lotto.LottoGUI(this, lottoManager);

        // Initialisation ChatListener pour les saisies via chat
        chatListener = new ChatListener(this, entrepriseGUI);

        // Système de Mode Service
        serviceModeManager = new com.gravityyfh.roleplaycity.service.ServiceModeManager(this, entrepriseLogic, econ,
                servicePersistenceService);
        serviceModeListener = new com.gravityyfh.roleplaycity.service.ServiceModeListener(this, serviceModeManager,
                entrepriseLogic);
        getServer().getPluginManager().registerEvents(serviceModeListener, this);
        debugLogger.debug("STARTUP", "ServiceModeManager et ServiceModeListener initialisés");

        // Système de boutiques moderne
        try {
            shopManager = new com.gravityyfh.roleplaycity.shop.manager.ShopManager(this, entrepriseLogic, econ,
                    shopPersistenceService);
            debugLogger.debug("STARTUP", "ShopManager initialisé");

            // Initialiser les GUIs et listeners de boutiques
            shopListGUI = new com.gravityyfh.roleplaycity.shop.gui.ShopListGUI(this, shopManager);
            shopManagementGUI = new com.gravityyfh.roleplaycity.shop.gui.ShopManagementGUI(this, shopManager);
            shopCreationGUI = new com.gravityyfh.roleplaycity.shop.gui.ShopCreationGUI(this, shopManager);
            shopPlacementListener = new com.gravityyfh.roleplaycity.shop.listener.ShopPlacementListener(this, shopManager);
        } catch (Exception e) {
            getLogger().severe("Erreur lors de l'initialisation du ShopManager: " + e.getMessage());
            e.printStackTrace();
        }

        debtNotificationService = new com.gravityyfh.roleplaycity.town.manager.DebtNotificationService(this,
                townManager);
        debtNotificationService.start();

        // Managers qui dépendent du NotificationManager
        townEconomyManager = new com.gravityyfh.roleplaycity.town.manager.TownEconomyManager(this, townManager,
                claimManager);
        companyPlotManager = new com.gravityyfh.roleplaycity.town.manager.CompanyPlotManager(this, townManager,
                entrepriseLogic, debtNotificationService);

        // EnterpriseContextManager (gestion centralisée de la sélection d'entreprise)
        enterpriseContextManager = new com.gravityyfh.roleplaycity.town.manager.EnterpriseContextManager(this,
                entrepriseLogic);
        // Les GUIs ne seront créés qu'à la première utilisation pour améliorer le temps
        // de démarrage
        debugLogger.debug("STARTUP", "GUIs configurés en lazy loading (seront initialisés à la demande)");
        // Initialise l'ancien ContractManager (deprecated - à supprimer)
        // contractManager = new com.gravityyfh.roleplaycity.manager.ContractManager(this);
        // contractGUI = new com.gravityyfh.roleplaycity.gui.ContractGUI(this);

        // Initialise le nouveau système de contrats
        contractService = new com.gravityyfh.roleplaycity.contract.service.ContractService(this);
        contractManagementGUI = new com.gravityyfh.roleplaycity.contract.gui.ContractManagementGUI(this, contractService);
        contractCreationGUI = new com.gravityyfh.roleplaycity.contract.gui.ContractCreationGUI(this, contractService);
        contractDetailsGUI = new com.gravityyfh.roleplaycity.contract.gui.ContractDetailsGUI(this, contractService);
        contractChatListener = new com.gravityyfh.roleplaycity.contract.listener.ContractChatListener(this, contractService);

        // Listeners
        townProtectionListener = new com.gravityyfh.roleplaycity.town.listener.TownProtectionListener(this, townManager,
                claimManager);
        plotGroupingListener = new com.gravityyfh.roleplaycity.town.listener.PlotGroupingListener(this, townManager,
                claimManager);

        // Charger les villes
        townManager.loadTowns(townDataManager.loadTowns());

        // Charger les amendes
        townPoliceManager.loadFines(townFinesDataManager.loadFines());

        // Charger les notifications
        if (notificationManager != null) {
            notificationManager.loadNotifications();
        }

        // Démarrer la vérification automatique des amendes expirées
        fineExpirationManager.startAutomaticChecks();

        // Reconstruire le cache de claims
        claimManager.rebuildCache();

        // FIX BUG: Liaison des GUIs supprimée car ils sont maintenant en lazy loading
        // Les liaisons entre GUIs se feront automatiquement lors de leur première
        // création
        // (voir les getters getTownMainGUI(), etc.)

        // Démarrer la tâche économique récurrente
        townEconomyTask = new com.gravityyfh.roleplaycity.town.task.TownEconomyTask(this, townManager,
                townEconomyManager);
        townEconomyTask.start();

        // Système Médical
        medicalSystemManager = new com.gravityyfh.roleplaycity.medical.manager.MedicalSystemManager(this,
                medicalPersistenceService);
        medicalListener = new com.gravityyfh.roleplaycity.medical.listener.MedicalListener(this);
        healingMiniGameListener = new com.gravityyfh.roleplaycity.medical.listener.HealingMiniGameListener(this);

        // Système Postal (La Poste)
        mailboxManager = new com.gravityyfh.roleplaycity.postal.manager.MailboxManager(this);
        mailboxPlacementGUI = new com.gravityyfh.roleplaycity.postal.gui.MailboxPlacementGUI(this, mailboxManager);
        mailboxVisualPlacement = new com.gravityyfh.roleplaycity.postal.gui.MailboxVisualPlacement(this,
                mailboxManager);
        laPosteGUI = new com.gravityyfh.roleplaycity.postal.gui.LaPosteGUI(this, townManager, mailboxManager, econ);
        mailboxInteractionListener = new com.gravityyfh.roleplaycity.postal.listener.MailboxInteractionListener(this,
                mailboxManager, claimManager);
        mailboxNotificationListener = new com.gravityyfh.roleplaycity.postal.listener.MailboxNotificationListener(this,
                mailboxManager, claimManager);
        plotGroupMailboxListener = new com.gravityyfh.roleplaycity.postal.listener.PlotGroupMailboxListener(this,
                mailboxManager);

        // Système de Police (Taser & Menottes)
        if (getConfig().getBoolean("police-equipment.taser.enabled", true) ||
                getConfig().getBoolean("police-equipment.handcuffs.enabled", true)) {
            debugLogger.debug("STARTUP", "Initialisation du système de police...");

            policeItemManager = new com.gravityyfh.roleplaycity.police.items.PoliceItemManager(this);
            tasedPlayerData = new com.gravityyfh.roleplaycity.police.data.TasedPlayerData();
            handcuffedPlayerData = new com.gravityyfh.roleplaycity.police.data.HandcuffedPlayerData();

            policeCraftListener = new com.gravityyfh.roleplaycity.police.listeners.PoliceCraftListener(this,
                    policeItemManager);
            taserListener = new com.gravityyfh.roleplaycity.police.listeners.TaserListener(this, policeItemManager,
                    tasedPlayerData);
            handcuffsListener = new com.gravityyfh.roleplaycity.police.listeners.HandcuffsListener(this,
                    policeItemManager, handcuffedPlayerData);
            handcuffsBreakListener = new com.gravityyfh.roleplaycity.police.listeners.HandcuffsBreakListener(this,
                    handcuffedPlayerData);
            handcuffsFollowListener = new com.gravityyfh.roleplaycity.police.listeners.HandcuffsFollowListener(this,
                    handcuffedPlayerData);

            // Charger les joueurs menottés (Persistance)
            if (prisonPersistenceService != null) {
                java.util.Map<java.util.UUID, Double> handcuffed = prisonPersistenceService.loadHandcuffedPlayers();
                handcuffed.forEach((uuid, health) -> handcuffedPlayerData.loadHandcuffed(uuid, health));

                // Charger les joueurs tasés (Persistance)
                java.util.Map<java.util.UUID, Long> tased = prisonPersistenceService.loadTasedPlayers();
                tased.forEach((uuid, expiration) -> tasedPlayerData.loadTased(uuid, expiration));
            }

            debugLogger.debug("STARTUP", "Système de police initialisé (Taser & Menottes)");
        }

        // Initialiser le système de prison (si activé)
        if (getConfig().getBoolean("prison-system.enabled", true)) {
            debugLogger.debug("STARTUP", "Initialisation du système de prison...");

            prisonManager = new com.gravityyfh.roleplaycity.police.manager.PrisonManager(this, townManager,
                    handcuffedPlayerData);
            townPrisonManagementGUI = new com.gravityyfh.roleplaycity.police.gui.TownPrisonManagementGUI(this,
                    townManager, prisonManager);
            imprisonmentWorkflowGUI = new com.gravityyfh.roleplaycity.police.gui.ImprisonmentWorkflowGUI(this,
                    townManager, prisonManager, handcuffedPlayerData);
            prisonRestrictionListener = new com.gravityyfh.roleplaycity.police.listeners.PrisonRestrictionListener(this,
                    prisonManager.getImprisonedData());
            prisonBoundaryListener = new com.gravityyfh.roleplaycity.police.listeners.PrisonBoundaryListener(this,
                    townManager, prisonManager.getImprisonedData());

            // Démarrer le scheduler de vérification des expirations
            prisonManager.startExpirationChecker();

            debugLogger.debug("STARTUP", "Système de prison initialisé avec succès");
            getLogger().info("Système de prison activé");
        }

        // Système de Backpacks
        if (getConfig().getBoolean("backpack.enabled", true)) {
            debugLogger.debug("STARTUP", "Initialisation du système de backpacks...");

            backpackItemManager = new com.gravityyfh.roleplaycity.backpack.manager.BackpackItemManager(this);
            backpackUtil = new com.gravityyfh.roleplaycity.backpack.util.BackpackUtil(this);
            backpackManager = new com.gravityyfh.roleplaycity.backpack.manager.BackpackManager(this,
                    backpackItemManager);
            backpackGUI = new com.gravityyfh.roleplaycity.backpack.gui.BackpackGUI(this, backpackManager,
                    backpackItemManager, backpackUtil);
            backpackInteractionListener = new com.gravityyfh.roleplaycity.backpack.listener.BackpackInteractionListener(
                    this, backpackItemManager, backpackGUI);
            backpackProtectionListener = new com.gravityyfh.roleplaycity.backpack.listener.BackpackProtectionListener(
                    this, backpackItemManager);
            backpackCraftListener = new com.gravityyfh.roleplaycity.backpack.listener.BackpackCraftListener(this,
                    backpackItemManager, entrepriseLogic);
            backpackCommandHandler = new com.gravityyfh.roleplaycity.backpack.command.BackpackCommandHandler(this,
                    backpackItemManager, backpackManager, backpackUtil);

            // Enregistrer les recettes natives Bukkit pour les backpacks
            backpackCraftListener.registerRecipes();

            // ⚡ OPTIMISATION: Activer la compression si configurée
            boolean compressionEnabled = backpackItemManager.getBackpacksConfig()
                    .getBoolean("global_settings.performance.compression_enabled", false);
            com.gravityyfh.roleplaycity.backpack.manager.BackpackSerializer.setCompressionEnabled(compressionEnabled);

            debugLogger.debug("STARTUP", "Système de backpacks initialisé avec succès");
            getLogger().info(
                    "Système de backpacks activé avec " + backpackItemManager.getBackpackTypes().size() + " types");
            if (compressionEnabled) {
                getLogger().info("⚡ Compression GZIP activée (économie mémoire ~50-70%)");
            }
        }

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
        // Nettoyer le système de backpacks
        if (backpackCraftListener != null) {
            backpackCraftListener.unregisterRecipes();
        }

        // Nettoyer le système de boutiques
        if (shopManager != null) {
            getLogger().info("Nettoyage du système de boutiques...");
            shopManager.cleanup();
        }

        // Nettoyer le GUI de gestion des shops (téléportations en attente)
        if (shopManagementGUI != null) {
            shopManagementGUI.cleanup();
        }

        // Nettoyer le système de prison
        if (prisonManager != null) {
            getLogger().info("Arrêt du système de prison...");
            prisonManager.stopExpirationChecker();
            prisonManager.releaseAllPrisoners(true);
            getLogger().info("Système de prison arrêté");
        }

        // Nettoyer le système de suivi des menottes
        if (handcuffsFollowListener != null) {
            handcuffsFollowListener.stopSmoothFollowTask();
            getLogger().info("Système de suivi des menottes arrêté");
        }

        // Nettoyer les systèmes interactifs
        if (confirmationManager != null) {
            confirmationManager.shutdown();
        }
        if (chatInputListener != null) {
            chatInputListener.shutdown();
        }

        // FIX BASSE #10: Arrêter le pool de threads asynchrones
        if (asyncTaskManager != null) {
            asyncTaskManager.shutdown();
        }

        // Arrêter la tâche économique
        if (townEconomyTask != null) {
            townEconomyTask.cancel();
        }

        // Nettoyer le système de téléportation des villes
        if (townTeleportCooldown != null) {
            getLogger().info("Annulation des téléportations en cours...");
            townTeleportCooldown.cancelAll();
            getLogger().info("Système de téléportation des villes arrêté");
        }

        // Nettoyer le système médical
        if (medicalSystemManager != null) {
            medicalSystemManager.cleanup();
        }

        // Nettoyer le système de police
        if (tasedPlayerData != null) {
            tasedPlayerData.clear();
        }
        if (handcuffedPlayerData != null) {
            handcuffedPlayerData.clear();
        }
        if (policeCraftListener != null) {
            policeCraftListener.unregisterRecipes();
        }

        // FIX MOYENNE: Nettoyer les tasks d'EntrepriseManager
        if (entrepriseLogic != null) {
            entrepriseLogic.cleanup();
        }

        // Nettoyer le mode service pour tous les joueurs
        if (serviceModeManager != null) {
            for (org.bukkit.entity.Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (serviceModeManager.isInService(player.getUniqueId())) {
                    serviceModeManager.deactivateService(player);
                }
            }
        }

        // ====================================================================
        // ÉTAPE 1: SAUVEGARDER TOUTES LES DONNÉES AVANT DE FERMER LA BDD
        // ====================================================================

        getLogger().info("============================================");
        getLogger().info("Sauvegarde finale de toutes les données...");
        getLogger().info("============================================");

        java.util.List<java.util.concurrent.CompletableFuture<Void>> futures = new java.util.ArrayList<>();

        // Sauvegarder les entreprises (SQLite)
        if (usingSQLiteServices && asyncEntrepriseService != null) {
            getLogger().info("[Entreprises] Sauvegarde finale des données SQLite...");
            try {
                asyncEntrepriseService.saveAll().join();
                getLogger().info("[Entreprises] Données SQLite sauvegardées");
            } catch (Exception e) {
                getLogger().severe("[Entreprises] Erreur sauvegarde finale: " + e.getMessage());
                e.printStackTrace();
            }
        } else if (entrepriseLogic != null) {
            entrepriseLogic.saveEntreprisesSync();
        }

        // Sauvegarder les villes (SQLite)
        if (townManager != null) {
            getLogger().info("[Towns] Sauvegarde finale...");
            townManager.saveTownsSync();
        }

        // Sauvegarder les notifications (SQLite)
        if (notificationManager != null) {
            getLogger().info("[Notifications] Sauvegarde finale...");
            notificationManager.saveNotificationsSync();
        }

        // Sauvegarder les amendes (SQLite)
        if (townPoliceManager != null && townFinesDataManager != null) {
            getLogger().info("[Fines] Sauvegarde finale...");
            townFinesDataManager.saveFines(townPoliceManager.getFinesForSave());
        }

        // Sauvegarder les menottes (SQLite)
        if (handcuffedPlayerData != null && prisonPersistenceService != null) {
            getLogger().info("[Police] Sauvegarde des menottes...");
            prisonPersistenceService.saveHandcuffedPlayers(handcuffedPlayerData.getAllHandcuffedData());
        }

        // Sauvegarder les joueurs tasés (SQLite)
        if (tasedPlayerData != null && prisonPersistenceService != null) {
            getLogger().info("[Police] Sauvegarde des joueurs tasés...");
            prisonPersistenceService.saveTasedPlayers(tasedPlayerData.getAllTasedData());
        }

        // Sauvegarder les boutiques (async)
        if (shopManager != null) {
            getLogger().info("[Shops] Sauvegarde finale...");
            futures.add(shopManager.saveShops());
        }
        
        // Sauvegarder les coffres d'entreprise
        if (companyStorageManager != null) {
            getLogger().info("[Storage] Sauvegarde des coffres...");
            companyStorageManager.saveAll();
        }

        // Attendre toutes les sauvegardes asynchrones
        if (!futures.isEmpty()) {
            try {
                java.util.concurrent.CompletableFuture
                        .allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
            } catch (Exception e) {
                getLogger().severe("Erreur lors de l'attente des sauvegardes: " + e.getMessage());
            }
        }

        getLogger().info("✓ Toutes les données ont été sauvegardées");

        // ====================================================================
        // ÉTAPE 2: FERMER LES SERVICES ET LA BASE DE DONNÉES
        // ====================================================================

        if (serviceFactory != null) {
            serviceFactory.shutdown();
        }

        // PHASE 7: Fermer le ConnectionManager global (APRÈS toutes les sauvegardes)
        if (connectionManager != null) {
            getLogger().info("[SQLite] Fermeture de la base de données...");
            connectionManager.shutdown();
            getLogger().info("[SQLite] Base de données fermée");
        }
        // Les données de mailbox sont maintenant sauvegardées dans towns.yml via
        // TownDataManager
        // Plus besoin de sauvegarder mailboxes.yml séparément
        getLogger().info("RoleplayCity désactivé. Données sauvegardées.");
    }

    // Getters for Contract System
    public com.gravityyfh.roleplaycity.contract.service.ContractService getContractService() {
        return contractService;
    }

    public com.gravityyfh.roleplaycity.contract.gui.ContractManagementGUI getContractManagementGUI() {
        return contractManagementGUI;
    }

    public com.gravityyfh.roleplaycity.contract.gui.ContractCreationGUI getContractCreationGUI() {
        return contractCreationGUI;
    }

    public com.gravityyfh.roleplaycity.contract.gui.ContractDetailsGUI getContractDetailsGUI() {
        return contractDetailsGUI;
    }

    public com.gravityyfh.roleplaycity.contract.listener.ContractChatListener getContractChatListener() {
        return contractChatListener;
    }

    private void registerAllListeners() {
        var pm = getServer().getPluginManager();
        var listeners = new Listener[] {
                this, chatListener, entrepriseGUI, playerCVGUI,
                shopListGUI, shopManagementGUI, shopCreationGUI, shopPlacementListener, // Système de boutiques
                contractManagementGUI, contractCreationGUI, contractDetailsGUI, contractChatListener, // Système de contrats
                blockPlaceListener, craftItemListener, smithItemListener,
                entityDamageListener, entityDeathListener, treeCutListener,
                // NOTE: Les GUIs en lazy loading (townMainGUI, myPropertyGUI, etc.)
                // s'enregistrent automatiquement lors de leur création
                // Ne PAS les inclure ici pour éviter les doublons de listeners qui causent des
                // bugs de "session expirée"
                plotGroupingListener, // Listener pour groupement de terrains
                townProtectionListener, // Protection des territoires de ville
                new com.gravityyfh.roleplaycity.town.listener.TownHUDListener(this, townManager, claimManager), // HUD
                                                                                                                // pour
                                                                                                                // afficher
                                                                                                                // les
                                                                                                                // infos
                                                                                                                // de
                                                                                                                // territoire
                townEventListener, // Événements de ville (suppression, départ membres)
                medicalListener, // Système médical (Revive intégré)
                healingMiniGameListener, // Mini-jeu de suture pour les soins médicaux
                mailboxPlacementGUI, mailboxVisualPlacement, mailboxInteractionListener, mailboxNotificationListener, // Système
                                                                                                                      // Postal
                                                                                                                      // (La
                                                                                                                      // Poste)
                laPosteGUI, // GUI La Poste
                policeCraftListener, taserListener, handcuffsListener, // Système de Police (Taser & Menottes)
                handcuffsBreakListener, handcuffsFollowListener, // Système de Police (suite)
                townPrisonManagementGUI, imprisonmentWorkflowGUI, // Système de Prison (GUIs)
                prisonRestrictionListener, prisonBoundaryListener, // Système de Prison (Restrictions & Limites)
                backpackInteractionListener, backpackProtectionListener, backpackCraftListener, // Système de Backpacks
                new EventListener(this, entrepriseLogic),
                new PlayerConnectionListener(this, entrepriseLogic),
                new com.gravityyfh.roleplaycity.entreprise.storage.ServiceDropListener(this, companyStorageManager, serviceModeManager, entrepriseLogic),
                new com.gravityyfh.roleplaycity.town.listener.PlayerConnectionListener(this) // Listener pour les
                                                                                             // notifications
        };

        for (Listener listener : listeners) {
            if (listener != null) {
                pm.registerEvents(listener, this);
            }
        }

        // Enregistrer le listener du système de boutiques si disponible
        if (shopManager != null) {
            try {
                Listener shopListener = new com.gravityyfh.roleplaycity.shop.listener.ShopInteractionHandler(
                        this, shopManager, entrepriseLogic);
                pm.registerEvents(shopListener, this);
                getLogger().info("Listener du système de boutiques enregistré.");

                // Enregistrer le listener de chargement automatique des hologrammes
                Listener hologramLoader = new com.gravityyfh.roleplaycity.shop.listener.ShopHologramLoader(
                        this, shopManager);
                pm.registerEvents(hologramLoader, this);
                getLogger().info("Listener de chargement automatique des hologrammes enregistré.");

                // Enregistrer le listener de suppression automatique des shops
                Listener shopDeletionListener = new com.gravityyfh.roleplaycity.shop.listener.ShopDeletionListener(
                        this, shopManager);
                pm.registerEvents(shopDeletionListener, this);
                getLogger().info("Listener de suppression automatique des shops enregistré.");
            } catch (Exception e) {
                getLogger().severe("[ShopSystem] ERREUR lors de l'enregistrement du listener: " + e.getMessage());
                e.printStackTrace();
            }
        }

        getLogger().info("Tous les listeners ont été enregistrés avec succès.");
        getLogger().info(
                "NOTE: Les GUIs en lazy loading s'enregistreront automatiquement lors de leur première utilisation.");
    }

    private void setupCommands() {
        var roleplaycityCmd = getCommand("roleplaycity");
        if (roleplaycityCmd != null) {
            com.gravityyfh.roleplaycity.command.RoleplayCityCommandHandler handler = new com.gravityyfh.roleplaycity.command.RoleplayCityCommandHandler(
                    this);
            roleplaycityCmd.setExecutor(handler);
            roleplaycityCmd.setTabCompleter(handler);
            getLogger().info("Gestionnaire de commandes pour /roleplaycity enregistré.");
        } else {
            getLogger().severe("ERREUR: La commande 'roleplaycity' n'est pas définie dans plugin.yml !");
        }

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

        var laposteCmd = getCommand("laposte");
        if (laposteCmd != null) {
            laposteCmd.setExecutor(
                    new com.gravityyfh.roleplaycity.postal.command.LaPosteCommand(this, claimManager, laPosteGUI));
            getLogger().info("Gestionnaire de commandes pour /laposte enregistré.");
        } else {
            getLogger().severe("ERREUR: La commande 'laposte' n'est pas définie dans plugin.yml !");
        }

        var backpackCmd = getCommand("backpack");
        if (backpackCmd != null && backpackCommandHandler != null) {
            backpackCmd.setExecutor(backpackCommandHandler);
            backpackCmd.setTabCompleter(backpackCommandHandler);
            getLogger().info("Gestionnaire de commandes pour /backpack enregistré.");
        } else if (backpackCmd == null) {
            getLogger().severe("ERREUR: La commande 'backpack' n'est pas définie dans plugin.yml !");
        }
        
        var lotoCmd = getCommand("loto");
        if (lotoCmd != null) {
            lotoCmd.setExecutor(new com.gravityyfh.roleplaycity.lotto.LottoCommand(lottoGUI));
        }
        
        var lotoAdminCmd = getCommand("lotoadmin");
        if (lotoAdminCmd != null) {
            lotoAdminCmd.setExecutor(new com.gravityyfh.roleplaycity.lotto.LottoAdminCommand(lottoManager));
        }
        
        // Ancien système de contrats supprimé - voir nouveau système intégré dans EntrepriseGUI
        // var contractCmd = getCommand("contract");
        // if (contractCmd != null) {
        //     contractCmd.setExecutor(new com.gravityyfh.roleplaycity.command.ContractCommandHandler(this));
        //     getLogger().info("Gestionnaire de commandes pour /contract enregistré.");
        // } else {
        //     getLogger().severe("ERREUR: La commande 'contract' n'est pas définie dans plugin.yml !");
        // }
    }

    /**
     * FIX BASSE #25: Validation complète de config.yml au startup
     * Vérifie toutes les valeurs de configuration et affiche les erreurs/warnings
     */
    private void validateConfiguration() {
        com.gravityyfh.roleplaycity.util.ConfigValidator validator = new com.gravityyfh.roleplaycity.util.ConfigValidator(
                this);

        boolean isValid = validator.validate();

        if (!isValid) {
            getLogger().severe("═══════════════════════════════════════════════════════════════");
            getLogger().severe("⚠ ATTENTION: Des erreurs critiques ont été détectées dans config.yml");
            getLogger().severe("⚠ Le plugin peut ne pas fonctionner correctement !");
            getLogger().severe("⚠ Corrigez les erreurs avant de déployer en production.");
            getLogger().severe("═══════════════════════════════════════════════════════════════");
        } else {
            getLogger().info("✓ Validation de la configuration réussie");
        }
    }

    /**
     * PHASE 6: Initialise les services entreprise (SQLite ou YAML)
     */
    private void initializeEntrepriseServices() {
        serviceFactory = new com.gravityyfh.roleplaycity.entreprise.service.ServiceFactory(this);

        // Utiliser le ConnectionManager global s'il est disponible
        if (connectionManager != null && connectionManager.isInitialized()) {
            try {
                getLogger().info("[Entreprises] Initialisation via ConnectionManager Global...");
                serviceFactory.initializeWithSQLite(connectionManager);

                // Créer le wrapper async avec cache
                asyncEntrepriseService = new com.gravityyfh.roleplaycity.entreprise.service.AsyncEntrepriseService(
                        this,
                        serviceFactory.getEntrepriseService());

                // Scheduler pour sauvegarder les entreprises modifiées toutes les 5 minutes
                org.bukkit.scheduler.BukkitRunnable saveDirtyTask = new org.bukkit.scheduler.BukkitRunnable() {
                    @Override
                    public void run() {
                        if (asyncEntrepriseService != null) {
                            asyncEntrepriseService.saveDirtyEntreprises()
                                    .thenAccept(saved -> {
                                        if (saved > 0) {
                                            debugLogger.debug("AUTOSAVE", saved + " entreprises sauvegardées");
                                        }
                                    });
                        }
                    }
                };
                saveDirtyTask.runTaskTimerAsynchronously(this, 6000L, 6000L); // 5 minutes

                usingSQLiteServices = true;
                getLogger().info("[Entreprises] Service Layer SQLite activé avec succès");

            } catch (Exception e) {
                getLogger().severe("[Entreprises] Erreur initialisation SQLite - Fallback vers YAML:");
                getLogger().severe("[Entreprises] " + e.getMessage());
                e.printStackTrace();

                serviceFactory.initializeWithYAML();
                usingSQLiteServices = false;
                getLogger().warning("[Entreprises] Mode YAML legacy activé (fallback)");
            }
        } else {
            serviceFactory.initializeWithYAML();
            usingSQLiteServices = false;
            getLogger().info("[Entreprises] Mode YAML legacy (ConnectionManager non dispo)");
        }
    }

    /**
     * PHASE 7: Initialise tous les services de persistance SQLite
     */
    private void initializeAllPersistenceServices() {
        getLogger().info("[SQLite] Initialisation des services de persistance...");

        // Towns
        townPersistenceService = new com.gravityyfh.roleplaycity.town.service.TownPersistenceService(
                this, connectionManager);

        // Shops
        shopPersistenceService = new com.gravityyfh.roleplaycity.shop.service.ShopPersistenceService(
                this, connectionManager);

        // Service Mode
        servicePersistenceService = new com.gravityyfh.roleplaycity.service.ServicePersistenceService(
                this, connectionManager);

        // Medical
        medicalPersistenceService = new com.gravityyfh.roleplaycity.medical.service.MedicalPersistenceService(
                this, connectionManager);

        // Fines
        finesPersistenceService = new com.gravityyfh.roleplaycity.town.service.FinesPersistenceService(
                this, connectionManager);

        // Notifications
        notificationPersistenceService = new com.gravityyfh.roleplaycity.town.service.NotificationPersistenceService(
                this, connectionManager);

        // Backpacks
        backpackPersistenceService = new com.gravityyfh.roleplaycity.backpack.service.BackpackPersistenceService(
                this, connectionManager);

        // Prison
        prisonPersistenceService = new com.gravityyfh.roleplaycity.police.service.PrisonPersistenceService(
                this, connectionManager);

        getLogger().info("[SQLite] Tous les services de persistance initialisés avec succès");
    }

    /**
     * FIX BASSE #34-35: Vérification robuste de Vault avec version et graceful
     * degradation
     */
    private boolean setupEconomy() {
        org.bukkit.plugin.Plugin vaultPlugin = getServer().getPluginManager().getPlugin("Vault");

        // FIX BASSE #35: Graceful degradation si Vault absent
        if (vaultPlugin == null) {
            getLogger().severe("════════════════════════════════════════════════════");
            getLogger().severe("⚠ VAULT NON DÉTECTÉ");
            getLogger().severe("════════════════════════════════════════════════════");
            getLogger().severe("");
            getLogger().severe("RoleplayCity nécessite Vault pour fonctionner.");
            getLogger().severe("");
            getLogger().severe("Solutions:");
            getLogger().severe("1. Téléchargez Vault: https://www.spigotmc.org/resources/vault.34315/");
            getLogger().severe("2. Installez un plugin d'économie (EssentialsX, CMI, etc.)");
            getLogger().severe("3. Redémarrez le serveur");
            getLogger().severe("");
            getLogger().severe("════════════════════════════════════════════════════");
            return false;
        }

        // FIX BASSE #34: Vérifier la version de Vault
        String vaultVersion = vaultPlugin.getDescription().getVersion();
        getLogger().info("Vault détecté: version " + vaultVersion);

        // Vérifier la version minimale (1.7.0+)
        try {
            String[] versionParts = vaultVersion.split("\\.");
            if (versionParts.length >= 2) {
                int major = Integer.parseInt(versionParts[0]);
                int minor = Integer.parseInt(versionParts[1]);

                if (major < 1 || (major == 1 && minor < 7)) {
                    getLogger().warning("════════════════════════════════════════════════════");
                    getLogger().warning("⚠ VERSION DE VAULT OBSOLÈTE");
                    getLogger().warning("════════════════════════════════════════════════════");
                    getLogger().warning("");
                    getLogger().warning("Version actuelle: " + vaultVersion);
                    getLogger().warning("Version minimale recommandée: 1.7.0");
                    getLogger().warning("");
                    getLogger().warning("Des problèmes peuvent survenir avec cette version.");
                    getLogger().warning("Téléchargez la dernière version:");
                    getLogger().warning("https://www.spigotmc.org/resources/vault.34315/");
                    getLogger().warning("");
                    getLogger().warning("Tentative de chargement quand même...");
                    getLogger().warning("════════════════════════════════════════════════════");
                }
            }
        } catch (NumberFormatException e) {
            getLogger().warning("Impossible de parser la version de Vault: " + vaultVersion);
        }

        // Vérifier qu'un fournisseur d'économie est disponible
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);

        // FIX BASSE #35: Message détaillé si pas de fournisseur d'économie
        if (rsp == null) {
            getLogger().severe("════════════════════════════════════════════════════");
            getLogger().severe("⚠ AUCUN FOURNISSEUR D'ÉCONOMIE DÉTECTÉ");
            getLogger().severe("════════════════════════════════════════════════════");
            getLogger().severe("");
            getLogger().severe("Vault est installé, mais aucun plugin d'économie");
            getLogger().severe("n'est disponible pour gérer l'argent.");
            getLogger().severe("");
            getLogger().severe("Solutions:");
            getLogger().severe("1. Installez EssentialsX: https://essentialsx.net/downloads.html");
            getLogger().severe("2. OU installez CMI, ou un autre plugin d'économie");
            getLogger().severe("3. Redémarrez le serveur");
            getLogger().severe("");
            getLogger().severe("Plugins d'économie compatibles:");
            getLogger().severe("- EssentialsX (recommandé)");
            getLogger().severe("- CMI");
            getLogger().severe("- CraftConomy");
            getLogger().severe("- iConomy");
            getLogger().severe("");
            getLogger().severe("════════════════════════════════════════════════════");
            return false;
        }

        econ = rsp.getProvider();

        if (econ == null) {
            getLogger().severe("Erreur lors de l'initialisation du fournisseur d'économie");
            return false;
        }

        // Succès !
        String econPluginName = rsp.getProvider().getName();
        getLogger().info("════════════════════════════════════════════════════");
        getLogger().info("✓ Économie configurée avec succès");
        getLogger().info("  Vault: " + vaultVersion);
        getLogger().info("  Fournisseur: " + econPluginName);
        getLogger().info("════════════════════════════════════════════════════");

        return true;
    }

    /**
     * Recharge complètement la configuration et les données du plugin
     * Utilisé par /roleplaycity reload
     */
    public void reloadPluginConfig() {
        getLogger().info("════════════════════════════════════════════════════");
        getLogger().info("Début du rechargement de RoleplayCity...");
        getLogger().info("════════════════════════════════════════════════════");

        try {
            // 1. Recharger config.yml
            reloadConfig();
            getLogger().info("✓ Configuration principale rechargée");

            // 1b. FIX BASSE #20: Recharger les messages i18n
            if (messageManager != null) {
                messageManager.reload();
                getLogger().info("✓ Messages i18n rechargés");
            }

            // 2. Recharger le système de villes
            if (townManager != null && townDataManager != null) {
                // Recharger directement depuis le fichier (sans sauvegarder avant!)
                // Cela permet de récupérer les modifications manuelles du fichier towns.yml
                townManager.loadTowns(townDataManager.loadTowns());
                // Reconstruire le cache de claims
                if (claimManager != null) {
                    claimManager.rebuildCache();
                }
                getLogger().info("✓ Système de villes rechargé (" + townManager.getAllTowns().size() + " villes)");
            }

            // 3. Recharger le système de niveau des villes
            if (townLevelManager != null) {
                townLevelManager.reload();
                getLogger().info("✓ Système d'évolution des villes rechargé");
            }

            // 4. Recharger les entreprises
            if (entrepriseLogic != null) {
                entrepriseLogic.reloadPluginData();
                getLogger().info("✓ Système d'entreprises rechargé");
            }

            // 5. Recharger les boutiques
            if (shopManager != null) {
                shopManager.loadShops();
                getLogger().info("✓ Système de boutiques rechargé");
            }

            // 6. Recharger les amendes
            if (townPoliceManager != null && townFinesDataManager != null) {
                townPoliceManager.loadFines(townFinesDataManager.loadFines());
                getLogger().info("✓ Système d'amendes rechargé");
            }

            // 7. Recharger les notifications
            if (notificationManager != null && notificationDataManager != null) {
                notificationManager.loadNotifications();
                getLogger().info("✓ Système de notifications rechargé");
            }

            // 8. Recharger le système médical
            if (medicalSystemManager != null) {
                medicalSystemManager.reloadConfig();
                getLogger().info("✓ Système médical rechargé");
            }

            // 9. Recharger le système de police (Taser & Menottes)
            if (policeCraftListener != null) {
                policeCraftListener.reloadRecipes();
                getLogger().info("✓ Système de police rechargé (Taser & Menottes)");
            }

            getLogger().info("════════════════════════════════════════════════════");
            getLogger().info("RoleplayCity rechargé avec succès !");
            getLogger().info("════════════════════════════════════════════════════");

        } catch (Exception e) {
            getLogger().severe("════════════════════════════════════════════════════");
            // FIX BASSE: Utiliser logging approprié au lieu de printStackTrace
            getLogger().log(java.util.logging.Level.SEVERE, "ERREUR lors du rechargement du plugin", e);
            getLogger().severe("════════════════════════════════════════════════════");
        }
    }

    // FIX BASSE #4: Méthode deprecated supprimée - utiliser reloadPluginConfig()
    // directement

    /**
     * Intercepte les commandes avec le préfixe "ville:" pour les rediriger vers
     * "/ville"
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
    public static RoleplayCity getInstance() {
        return instance;
    }

    public EntrepriseManagerLogic getEntrepriseManagerLogic() {
        return entrepriseLogic;
    }

    // PHASE 6: Getters pour les nouveaux services
    public com.gravityyfh.roleplaycity.entreprise.service.ServiceFactory getServiceFactory() {
        return serviceFactory;
    }

    public com.gravityyfh.roleplaycity.entreprise.service.AsyncEntrepriseService getAsyncEntrepriseService() {
        return asyncEntrepriseService;
    }

    public boolean isUsingSQLiteServices() {
        return usingSQLiteServices;
    }

    public ChatListener getChatListener() {
        return chatListener;
    }

    // Ancien système de contrats (deprecated) - utiliser getContractService() instead
    // public com.gravityyfh.roleplaycity.manager.ContractManager getContractManager() {
    //     return contractManager;
    // }

    public com.gravityyfh.roleplaycity.service.ServiceModeManager getServiceModeManager() {
        return serviceModeManager;
    }
    
    public com.gravityyfh.roleplaycity.entreprise.storage.CompanyStorageManager getCompanyStorageManager() {
        return companyStorageManager;
    }

    public EntrepriseGUI getEntrepriseGUI() {
        return entrepriseGUI;
    }

    public PlayerCVGUI getPlayerCVGUI() {
        return playerCVGUI;
    }

    public static Economy getEconomy() {
        return instance.econ;
    }

    public com.gravityyfh.roleplaycity.shop.manager.ShopManager getShopManager() {
        return shopManager;
    }

    public com.gravityyfh.roleplaycity.shop.gui.ShopListGUI getShopListGUI() {
        return shopListGUI;
    }

    public com.gravityyfh.roleplaycity.shop.gui.ShopManagementGUI getShopManagementGUI() {
        return shopManagementGUI;
    }

    public com.gravityyfh.roleplaycity.shop.gui.ShopCreationGUI getShopCreationGUI() {
        return shopCreationGUI;
    }

    public com.gravityyfh.roleplaycity.shop.listener.ShopPlacementListener getShopPlacementListener() {
        return shopPlacementListener;
    }

    public TownManager getTownManager() {
        return townManager;
    }

    public com.gravityyfh.roleplaycity.town.manager.TownDataManager getTownDataManager() {
        return townDataManager;
    }

    public com.gravityyfh.roleplaycity.town.manager.ClaimManager getClaimManager() {
        return claimManager;
    }

    public com.gravityyfh.roleplaycity.town.manager.TownLevelManager getTownLevelManager() {
        return townLevelManager;
    }

    public com.gravityyfh.roleplaycity.town.manager.TownEconomyManager getTownEconomyManager() {
        return townEconomyManager;
    }

    public com.gravityyfh.roleplaycity.town.manager.CompanyPlotManager getCompanyPlotManager() {
        return companyPlotManager;
    }

    public com.gravityyfh.roleplaycity.town.manager.EnterpriseContextManager getEnterpriseContextManager() {
        return enterpriseContextManager;
    }

    public com.gravityyfh.roleplaycity.town.manager.TownPoliceManager getTownPoliceManager() {
        return townPoliceManager;
    }

    public com.gravityyfh.roleplaycity.town.manager.TownFinesDataManager getTownFinesDataManager() {
        return townFinesDataManager;
    }

    public com.gravityyfh.roleplaycity.town.manager.FineExpirationManager getFineExpirationManager() {
        return fineExpirationManager;
    }

    // FIX BASSE #9: Lazy loading pour GUIs
    public com.gravityyfh.roleplaycity.town.gui.TownPlotManagementGUI getTownPlotManagementGUI() {
        if (townPlotManagementGUI == null) {
            // IMPORTANT: Toujours créer via getTownMainGUI() pour éviter les doublons
            // d'instances
            // getTownMainGUI() créera et enregistrera TownPlotManagementGUI correctement
            getTownMainGUI();
        }
        return townPlotManagementGUI;
    }

    public com.gravityyfh.roleplaycity.town.gui.PlotOwnerGUI getPlotOwnerGUI() {
        if (plotOwnerGUI == null) {
            synchronized (this) {
                if (plotOwnerGUI == null) {
                    plotOwnerGUI = new com.gravityyfh.roleplaycity.town.gui.PlotOwnerGUI(this, townManager,
                            claimManager);
                    getServer().getPluginManager().registerEvents(plotOwnerGUI, this);
                    debugLogger.debug("LAZY_LOAD", "PlotOwnerGUI initialisé et enregistré");
                }
            }
        }
        return plotOwnerGUI;
    }

    public com.gravityyfh.roleplaycity.town.gui.MyPropertyGUI getMyPropertyGUI() {
        if (myPropertyGUI == null) {
            // IMPORTANT: Toujours créer via getTownMainGUI() pour éviter les doublons
            // d'instances
            // getTownMainGUI() créera et enregistrera MyPropertyGUI correctement
            getTownMainGUI();
        }
        return myPropertyGUI;
    }

    public com.gravityyfh.roleplaycity.town.gui.RentedPropertyGUI getRentedPropertyGUI() {
        if (rentedPropertyGUI == null) {
            synchronized (this) {
                if (rentedPropertyGUI == null) {
                    rentedPropertyGUI = new com.gravityyfh.roleplaycity.town.gui.RentedPropertyGUI(this, townManager,
                            getMyPropertyGUI());
                    getServer().getPluginManager().registerEvents(rentedPropertyGUI, this);
                    debugLogger.debug("LAZY_LOAD", "RentedPropertyGUI initialisé et enregistré");
                }
            }
        }
        return rentedPropertyGUI;
    }

    public com.gravityyfh.roleplaycity.town.gui.CompanySelectionGUI getCompanySelectionGUI() {
        if (companySelectionGUI == null) {
            synchronized (this) {
                if (companySelectionGUI == null) {
                    companySelectionGUI = new com.gravityyfh.roleplaycity.town.gui.CompanySelectionGUI(this);
                    // Injecter EnterpriseContextManager
                    if (enterpriseContextManager != null) {
                        companySelectionGUI.setEnterpriseContextManager(enterpriseContextManager);
                    }
                    getServer().getPluginManager().registerEvents(companySelectionGUI, this);
                    debugLogger.debug("LAZY_LOAD", "CompanySelectionGUI initialisé et enregistré");
                }
            }
        }
        return companySelectionGUI;
    }

    public com.gravityyfh.roleplaycity.town.manager.NotificationManager getNotificationManager() {
        return notificationManager;
    }

    public com.gravityyfh.roleplaycity.town.manager.DebtNotificationService getDebtNotificationService() {
        return debtNotificationService;
    }

    public com.gravityyfh.roleplaycity.town.gui.PlotGroupManagementGUI getPlotGroupManagementGUI() {
        if (plotGroupManagementGUI == null) {
            synchronized (this) {
                if (plotGroupManagementGUI == null) {
                    plotGroupManagementGUI = new com.gravityyfh.roleplaycity.town.gui.PlotGroupManagementGUI(this,
                            townManager, claimManager);
                    getServer().getPluginManager().registerEvents(plotGroupManagementGUI, this);
                    debugLogger.debug("LAZY_LOAD", "PlotGroupManagementGUI initialisé et enregistré");
                }
            }
        }
        return plotGroupManagementGUI;
    }

    public com.gravityyfh.roleplaycity.town.listener.PlotGroupingListener getPlotGroupingListener() {
        return plotGroupingListener;
    }

    // FIX BASSE #28: Getter pour métriques de performance
    public com.gravityyfh.roleplaycity.util.PerformanceMetrics getPerformanceMetrics() {
        return performanceMetrics;
    }

    // FIX BASSE #29: Getter pour debug logger
    public com.gravityyfh.roleplaycity.util.DebugLogger getDebugLogger() {
        return debugLogger;
    }

    // FIX BASSE #20-24: Getter pour message manager
    public com.gravityyfh.roleplaycity.util.MessageManager getMessageManager() {
        return messageManager;
    }

    // FIX BASSE #31-33: Getter pour name validator
    public com.gravityyfh.roleplaycity.util.NameValidator getNameValidator() {
        return nameValidator;
    }

    // FIX BASSE #8: Getter pour player cache
    public com.gravityyfh.roleplaycity.util.PlayerCache getPlayerCache() {
        return playerCache;
    }

    // FIX BASSE #10: Getter pour async task manager
    public com.gravityyfh.roleplaycity.util.AsyncTaskManager getAsyncTaskManager() {
        return asyncTaskManager;
    }

    // FIX PERFORMANCES: Getter pour block place cache
    public com.gravityyfh.roleplaycity.util.PlayerBlockPlaceCache getBlockPlaceCache() {
        return blockPlaceCache;
    }

    public TownCommandHandler getTownCommandHandler() {
        return (TownCommandHandler) getCommand("ville").getExecutor();
    }

    // FIX BASSE #9: Lazy loading pour TownMainGUI
    public com.gravityyfh.roleplaycity.town.gui.TownMainGUI getTownMainGUI() {
        if (townMainGUI == null) {
            synchronized (this) {
                if (townMainGUI == null) {
                    // Étape 1: Créer TownMainGUI d'abord (sans liaisons)
                    townMainGUI = new com.gravityyfh.roleplaycity.town.gui.TownMainGUI(this, townManager);

                    // Étape 2: Créer les GUIs qui en ont besoin (certains dépendent de townMainGUI)
                    // Note: Ne pas enregistrer ici, l'enregistrement se fait groupé à la fin
                    if (townPlotManagementGUI == null) {
                        townPlotManagementGUI = new com.gravityyfh.roleplaycity.town.gui.TownPlotManagementGUI(this,
                                townManager, claimManager, townEconomyManager);
                    }
                    if (myPropertyGUI == null) {
                        myPropertyGUI = new com.gravityyfh.roleplaycity.town.gui.MyPropertyGUI(this, townManager,
                                townMainGUI);
                    }

                    townClaimsGUI = new com.gravityyfh.roleplaycity.town.gui.TownClaimsGUI(this, townManager,
                            claimManager);
                    townBankGUI = new com.gravityyfh.roleplaycity.town.gui.TownBankGUI(this, townManager,
                            townEconomyManager);
                    townPoliceGUI = new com.gravityyfh.roleplaycity.town.gui.TownPoliceGUI(this, townManager,
                            townPoliceManager);
                    townJusticeGUI = new com.gravityyfh.roleplaycity.town.gui.TownJusticeGUI(this, townManager,
                            townPoliceManager, townJusticeManager);
                    townCitizenFinesGUI = new com.gravityyfh.roleplaycity.town.gui.TownCitizenFinesGUI(this,
                            townPoliceManager);
                    townMembersGUI = new com.gravityyfh.roleplaycity.town.gui.TownMembersGUI(this, townManager);
                    townUpgradeGUI = new com.gravityyfh.roleplaycity.town.gui.TownUpgradeGUI(this, townManager);
                    myCompaniesGUI = new com.gravityyfh.roleplaycity.town.gui.MyCompaniesGUI(this, townManager,
                            townMainGUI);
                    debtManagementGUI = new com.gravityyfh.roleplaycity.town.gui.DebtManagementGUI(this, townManager,
                            townMainGUI, debtNotificationService);

                    // Système de téléportation et liste des villes
                    townTeleportCooldown = new com.gravityyfh.roleplaycity.town.data.TownTeleportCooldown(this);
                    townListGUI = new com.gravityyfh.roleplaycity.town.gui.TownListGUI(this, townManager,
                            townTeleportCooldown);
                    noTownGUI = new com.gravityyfh.roleplaycity.town.gui.NoTownGUI(this, townManager, townListGUI);

                    debugLogger.debug("LAZY_LOAD", "TownMainGUI et GUIs liés initialisés");

                    // Étape 3: Lier les GUIs
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
                    townMainGUI.setTownListGUI(townListGUI);
                    townMainGUI.setNoTownGUI(noTownGUI);
                    townMainGUI.setCooldownManager(townTeleportCooldown);

                    // Liaisons réciproques
                    townMembersGUI.setMainGUI(townMainGUI);
                    townUpgradeGUI.setMainGUI(townMainGUI);
                    townClaimsGUI.setPlotManagementGUI(townPlotManagementGUI);

                    // FIX BUG: Enregistrer tous les GUIs comme listeners
                    getServer().getPluginManager().registerEvents(townMainGUI, this);
                    getServer().getPluginManager().registerEvents(townClaimsGUI, this);
                    getServer().getPluginManager().registerEvents(townBankGUI, this);
                    getServer().getPluginManager().registerEvents(townPlotManagementGUI, this);
                    getServer().getPluginManager().registerEvents(townPoliceGUI, this);
                    getServer().getPluginManager().registerEvents(townJusticeGUI, this);
                    getServer().getPluginManager().registerEvents(townCitizenFinesGUI, this);
                    getServer().getPluginManager().registerEvents(townMembersGUI, this);
                    getServer().getPluginManager().registerEvents(townUpgradeGUI, this);
                    getServer().getPluginManager().registerEvents(myPropertyGUI, this);
                    getServer().getPluginManager().registerEvents(myCompaniesGUI, this);
                    getServer().getPluginManager().registerEvents(debtManagementGUI, this);
                    getServer().getPluginManager().registerEvents(townListGUI, this);
                    getServer().getPluginManager().registerEvents(noTownGUI, this);

                    debugLogger.debug("LAZY_LOAD", "Tous les listeners GUI enregistrés");
                }
            }
        }
        return townMainGUI;
    }

    public com.gravityyfh.roleplaycity.medical.manager.MedicalSystemManager getMedicalSystemManager() {
        return medicalSystemManager;
    }

    // Système Postal (La Poste)
    public com.gravityyfh.roleplaycity.postal.manager.MailboxManager getMailboxManager() {
        return mailboxManager;
    }

    public com.gravityyfh.roleplaycity.postal.gui.MailboxPlacementGUI getMailboxPlacementGUI() {
        return mailboxPlacementGUI;
    }

    public com.gravityyfh.roleplaycity.postal.gui.MailboxVisualPlacement getMailboxVisualPlacement() {
        return mailboxVisualPlacement;
    }

    public com.gravityyfh.roleplaycity.postal.gui.LaPosteGUI getLaPosteGUI() {
        return laPosteGUI;
    }

    // Système de Police (Taser & Menottes)
    public com.gravityyfh.roleplaycity.police.items.PoliceItemManager getPoliceItemManager() {
        return policeItemManager;
    }

    public com.gravityyfh.roleplaycity.police.data.TasedPlayerData getTasedPlayerData() {
        return tasedPlayerData;
    }

    public com.gravityyfh.roleplaycity.police.data.HandcuffedPlayerData getHandcuffedPlayerData() {
        return handcuffedPlayerData;
    }

    // Système de Prison
    public com.gravityyfh.roleplaycity.police.manager.PrisonManager getPrisonManager() {
        return prisonManager;
    }

    public com.gravityyfh.roleplaycity.police.gui.TownPrisonManagementGUI getTownPrisonManagementGUI() {
        return townPrisonManagementGUI;
    }

    public com.gravityyfh.roleplaycity.police.gui.ImprisonmentWorkflowGUI getImprisonmentWorkflowGUI() {
        return imprisonmentWorkflowGUI;
    }

    // Système de Backpacks
    public com.gravityyfh.roleplaycity.backpack.manager.BackpackItemManager getBackpackItemManager() {
        return backpackItemManager;
    }

    public com.gravityyfh.roleplaycity.backpack.manager.BackpackManager getBackpackManager() {
        return backpackManager;
    }

    public com.gravityyfh.roleplaycity.backpack.util.BackpackUtil getBackpackUtil() {
        return backpackUtil;
    }

    public com.gravityyfh.roleplaycity.backpack.gui.BackpackGUI getBackpackGUI() {
        return backpackGUI;
    }

    // Systèmes de messages interactifs
    public com.gravityyfh.roleplaycity.util.ConfirmationManager getConfirmationManager() {
        return confirmationManager;
    }

    public com.gravityyfh.roleplaycity.util.ChatInputListener getChatInputListener() {
        return chatInputListener;
    }

    /**
     * Intercepte les commandes internes utilisées par les messages interactifs
     * /rc:confirm, /rc:cancel, /rc:cancelinput
     */
    @EventHandler
    public void onInternalCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage().toLowerCase();
        org.bukkit.entity.Player player = event.getPlayer();

        if (message.equals("/rc:confirm")) {
            event.setCancelled(true);
            if (confirmationManager != null) {
                boolean success = confirmationManager.confirm(player.getUniqueId());
                if (!success) {
                    com.gravityyfh.roleplaycity.util.InteractiveMessage.error(
                            "Aucune confirmation en attente.").send(player);
                }
            }
        } else if (message.equals("/rc:cancel")) {
            event.setCancelled(true);
            if (confirmationManager != null) {
                boolean success = confirmationManager.cancel(player.getUniqueId());
                if (!success) {
                    com.gravityyfh.roleplaycity.util.InteractiveMessage.error(
                            "Aucune confirmation en attente.").send(player);
                }
            }
        } else if (message.equals("/rc:cancelinput")) {
            event.setCancelled(true);
            if (chatInputListener != null) {
                if (chatInputListener.hasPendingInput(player.getUniqueId())) {
                    chatInputListener.cancelInput(player.getUniqueId());
                } else {
                    com.gravityyfh.roleplaycity.util.InteractiveMessage.error(
                            "Aucune saisie en attente.").send(player);
                }
            }
        }
    }
}
