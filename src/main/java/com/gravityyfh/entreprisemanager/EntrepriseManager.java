package com.gravityyfh.entreprisemanager;

import com.gravityyfh.entreprisemanager.Listener.ChatListener;
import com.gravityyfh.entreprisemanager.Listener.PlayerConnectionListener;
import com.gravityyfh.entreprisemanager.Listener.TownyListener;
import com.gravityyfh.entreprisemanager.Listener.BlockPlaceListener;
import com.gravityyfh.entreprisemanager.Listener.CraftItemListener;
import com.gravityyfh.entreprisemanager.Listener.EntityDamageListener;
import com.gravityyfh.entreprisemanager.Listener.EntityDeathListener;
import com.gravityyfh.entreprisemanager.Listener.TreeCutListener;
import com.gravityyfh.entreprisemanager.Shop.ShopDestructionListener;
import com.gravityyfh.entreprisemanager.Shop.ShopGUI;
import com.gravityyfh.entreprisemanager.Shop.ShopInteractionListener;
import com.gravityyfh.entreprisemanager.Shop.ShopManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class EntrepriseManager extends JavaPlugin implements Listener {
    private static EntrepriseManager instance;
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
    private BlockPlaceListener blockPlaceListener;
    private CraftItemListener craftItemListener;
    private EntityDamageListener entityDamageListener;
    private EntityDeathListener entityDeathListener;
    private TreeCutListener treeCutListener;

    public void onEnable() {
        instance = this;
        this.getLogger().info("============================================");
        this.getLogger().info("-> Activation d'EntrepriseManager V2");
        this.getLogger().info("============================================");
        if (!this.setupEconomy()) {
            this.getLogger().severe("### ERREUR CRITIQUE : Vault non trouvé ou pas de fournisseur d'économie. ###");
            this.getServer().getPluginManager().disablePlugin(this);
        } else {
            this.getLogger().info("Vault et le fournisseur d'économie ont été trouvés et initialisés.");
            this.saveDefaultConfig();
            this.getLogger().info("Configuration chargée.");
            this.entrepriseLogic = new EntrepriseManagerLogic(this);
            this.getLogger().info("EntrepriseManagerLogic initialisé.");
            this.shopManager = new ShopManager(this);
            this.getLogger().info("ShopManager initialisé.");
            this.shopGUI = new ShopGUI(this);
            this.getLogger().info("ShopGUI initialisé.");
            this.entrepriseGUI = new EntrepriseGUI(this, this.entrepriseLogic);
            this.getLogger().info("EntrepriseGUI initialisé.");
            this.chatListener = new ChatListener(this, this.entrepriseGUI);
            this.getLogger().info("ChatListener initialisé.");
            this.cvManager = new CVManager(this, this.entrepriseLogic);
            this.getLogger().info("CVManager initialisé.");
            this.playerCVGUI = new PlayerCVGUI(this, this.entrepriseLogic, this.cvManager);
            this.getLogger().info("PlayerCVGUI initialisé.");
            this.cvManager.setPlayerCVGUI(this.playerCVGUI);
            this.getLogger().info("Dépendance PlayerCVGUI injectée dans CVManager.");
            this.shopInteractionListener = new ShopInteractionListener(this);
            this.getLogger().info("ShopInteractionListener initialisé.");
            this.shopDestructionListener = new ShopDestructionListener(this);
            this.getLogger().info("ShopDestructionListener initialisé.");
            this.blockPlaceListener = new BlockPlaceListener(this, this.entrepriseLogic);
            this.getLogger().info("BlockPlaceListener initialisé.");
            this.craftItemListener = new CraftItemListener(this, this.entrepriseLogic);
            this.getLogger().info("CraftItemListener initialisé.");
            this.entityDamageListener = new EntityDamageListener(this, this.entrepriseLogic);
            this.getLogger().info("EntityDamageListener initialisé.");
            this.entityDeathListener = new EntityDeathListener(this, this.entrepriseLogic);
            this.getLogger().info("EntityDeathListener initialisé.");
            this.treeCutListener = new TreeCutListener(this.entrepriseLogic, this);
            this.getLogger().info("TreeCutListener initialisé.");
            this.shopManager.loadShops();
            this.registerAllListeners();
            this.setupCommands();
            this.getLogger().info("============================================");
            this.getLogger().info("-> EntrepriseManager V2 activé avec succès !");
            this.getLogger().info("============================================");
        }
    }

    public void onDisable() {
        if (this.shopManager != null) {
            this.shopManager.saveShops();
        }

        if (this.entrepriseLogic != null) {
            this.entrepriseLogic.saveEntreprises();
        }

        this.getLogger().info("EntrepriseManager désactivé. Données sauvegardées.");
    }

    private void registerAllListeners() {
        this.getLogger().info("Enregistrement des listeners...");
        this.getServer().getPluginManager().registerEvents(this, this);
        if (this.chatListener != null) {
            this.getServer().getPluginManager().registerEvents(this.chatListener, this);
        }

        if (this.entrepriseGUI != null) {
            this.getServer().getPluginManager().registerEvents(this.entrepriseGUI, this);
        }

        if (this.playerCVGUI != null) {
            this.getServer().getPluginManager().registerEvents(this.playerCVGUI, this);
        }

        if (this.shopGUI != null) {
            this.getServer().getPluginManager().registerEvents(this.shopGUI, this);
        }

        if (this.shopInteractionListener != null) {
            this.getServer().getPluginManager().registerEvents(this.shopInteractionListener, this);
        }

        if (this.shopDestructionListener != null) {
            this.getServer().getPluginManager().registerEvents(this.shopDestructionListener, this);
        }

        if (this.blockPlaceListener != null) {
            this.getServer().getPluginManager().registerEvents(this.blockPlaceListener, this);
        }

        if (this.craftItemListener != null) {
            this.getServer().getPluginManager().registerEvents(this.craftItemListener, this);
        }

        if (this.entityDamageListener != null) {
            this.getServer().getPluginManager().registerEvents(this.entityDamageListener, this);
        }

        if (this.entityDeathListener != null) {
            this.getServer().getPluginManager().registerEvents(this.entityDeathListener, this);
        }

        if (this.treeCutListener != null) {
            this.getServer().getPluginManager().registerEvents(this.treeCutListener, this);
        }

        if (this.mainEventListener == null) {
            this.mainEventListener = new EventListener(this, this.entrepriseLogic);
        }

        this.getServer().getPluginManager().registerEvents(this.mainEventListener, this);
        if (this.getServer().getPluginManager().getPlugin("Towny") != null) {
            if (this.townyListener == null) {
                this.townyListener = new TownyListener(this, this.entrepriseLogic);
            }

            this.getServer().getPluginManager().registerEvents(this.townyListener, this);
            this.getLogger().info("TownyListener enregistré (intégration Towny active).");
        } else {
            this.getLogger().info("Plugin Towny non trouvé, TownyListener non enregistré.");
        }

        this.getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this, this.entrepriseLogic), this);
        this.getLogger().info("PlayerConnectionListener enregistré.");
        this.getLogger().info("Tous les listeners applicables ont été enregistrés.");
    }

    private void setupCommands() {
        EntrepriseCommandHandler commandHandler = new EntrepriseCommandHandler(this, this.entrepriseLogic, this.entrepriseGUI, this.cvManager);
        if (this.getCommand("entreprise") != null) {
            this.getCommand("entreprise").setExecutor(commandHandler);
            this.getLogger().info("Gestionnaire de commandes pour /entreprise enregistré.");
        } else {
            this.getLogger().severe("ERREUR: La commande 'entreprise' n'est pas définie dans plugin.yml !");
        }

    }

    private boolean setupEconomy() {
        if (this.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        } else {
            RegisteredServiceProvider<Economy> rsp = this.getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp == null) {
                return false;
            } else {
                econ = (Economy)rsp.getProvider();
                return econ != null;
            }
        }
    }

    public void reloadPluginData() {
        super.reloadConfig();
        if (this.entrepriseLogic != null) {
            this.entrepriseLogic.reloadPluginData();
        }

        if (this.shopManager != null) {
            this.shopManager.loadShops();
        }

        this.getLogger().info("Plugin EntrepriseManager V2 et ses données ont été rechargés.");
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
    }

    public static EntrepriseManager getInstance() {
        return instance;
    }

    public EntrepriseManagerLogic getEntrepriseManagerLogic() {
        return this.entrepriseLogic;
    }

    public ChatListener getChatListener() {
        return this.chatListener;
    }

    public EntrepriseGUI getEntrepriseGUI() {
        return this.entrepriseGUI;
    }

    public PlayerCVGUI getPlayerCVGUI() {
        return this.playerCVGUI;
    }

    public CVManager getCvManager() {
        return this.cvManager;
    }

    public static Economy getEconomy() {
        return econ;
    }

    public ShopManager getShopManager() {
        return this.shopManager;
    }

    public ShopGUI getShopGUI() {
        return this.shopGUI;
    }
}
