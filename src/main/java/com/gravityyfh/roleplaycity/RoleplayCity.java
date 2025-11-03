package com.gravityyfh.roleplaycity;

import com.gravityyfh.roleplaycity.Listener.ChatListener;
import com.gravityyfh.roleplaycity.Listener.PlayerConnectionListener;
import com.gravityyfh.roleplaycity.Listener.TownyListener;
import com.gravityyfh.roleplaycity.Listener.BlockPlaceListener;
import com.gravityyfh.roleplaycity.Listener.CraftItemListener;
import com.gravityyfh.roleplaycity.Listener.EntityDamageListener;
import com.gravityyfh.roleplaycity.Listener.EntityDeathListener;
// AJOUT : Import pour le nouveau listener
import com.gravityyfh.roleplaycity.Listener.SmithItemListener;
import com.gravityyfh.roleplaycity.Listener.TreeCutListener;
import com.gravityyfh.roleplaycity.Shop.ShopDestructionListener;
import com.gravityyfh.roleplaycity.Shop.ShopDisplayItemListener;
import com.gravityyfh.roleplaycity.Shop.ShopGUI;
import com.gravityyfh.roleplaycity.Shop.ShopInteractionListener;
import com.gravityyfh.roleplaycity.Shop.ShopManager;
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
    private EventListener mainEventListener;
    private TownyListener townyListener;
    private ShopInteractionListener shopInteractionListener;
    private ShopDestructionListener shopDestructionListener;
    private ShopDisplayItemListener shopDisplayItemListener;
    private BlockPlaceListener blockPlaceListener;
    private CraftItemListener craftItemListener;
    // AJOUT : Déclaration du nouveau listener
    private SmithItemListener smithItemListener;
    private EntityDamageListener entityDamageListener;
    private EntityDeathListener entityDeathListener;
    private TreeCutListener treeCutListener;

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
        entrepriseLogic = new EntrepriseManagerLogic(this);
        shopManager = new ShopManager(this);
        shopGUI = new ShopGUI(this);
        entrepriseGUI = new EntrepriseGUI(this, entrepriseLogic);
        chatListener = new ChatListener(this, entrepriseGUI);
        cvManager = new CVManager(this, entrepriseLogic);
        playerCVGUI = new PlayerCVGUI(this, entrepriseLogic, cvManager);
        cvManager.setPlayerCVGUI(playerCVGUI);

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

        getLogger().info("Tous les composants ont été initialisés avec succès.");
    }

    public void onDisable() {
        if (shopManager != null) {
            shopManager.saveShops();
        }
        if (entrepriseLogic != null) {
            entrepriseLogic.saveEntreprises();
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
            new EventListener(this, entrepriseLogic),
            new PlayerConnectionListener(this, entrepriseLogic)
        };

        for (Listener listener : listeners) {
            if (listener != null) {
                pm.registerEvents(listener, this);
            }
        }

        // Towny integration (optional dependency)
        if (pm.getPlugin("Towny") != null) {
            pm.registerEvents(new TownyListener(this, entrepriseLogic), this);
            getLogger().info("TownyListener enregistré (intégration Towny active).");
        }

        getLogger().info("Tous les listeners ont été enregistrés avec succès.");
    }

    private void setupCommands() {
        var command = getCommand("entreprise");
        if (command != null) {
            command.setExecutor(new EntrepriseCommandHandler(this, entrepriseLogic, entrepriseGUI, cvManager));
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
        reloadConfig();
        if (entrepriseLogic != null) {
            entrepriseLogic.reloadPluginData();
        }
        if (shopManager != null) {
            shopManager.loadShops();
        }
        getLogger().info("Plugin RoleplayCity et ses données ont été rechargés.");
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
}