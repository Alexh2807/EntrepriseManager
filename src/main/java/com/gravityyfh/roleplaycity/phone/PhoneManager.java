package com.gravityyfh.roleplaycity.phone;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.phone.model.*;
import dev.lone.itemsadder.api.CustomStack;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;

/**
 * Manager principal du systeme de telephonie.
 * Gere la configuration, les items, et les recettes de craft.
 */
public class PhoneManager {

    private final RoleplayCity plugin;

    // Configuration
    private YamlConfiguration phoneConfig;
    private final Map<String, PhoneType> phoneTypes = new HashMap<>();
    private final Map<String, PlanType> planTypes = new HashMap<>();
    private final List<MusicTrack> musicTracks = new ArrayList<>();

    // Couts
    private int callCostPerMinute = 50;
    private int smsCost = 10;

    // Settings
    private int callTimeout = 30;
    private int smsMaxLength = 160;
    private int maxContacts = 50;

    // Messages
    private final Map<String, String> messages = new HashMap<>();

    // Sons
    private String ringtoneSource;
    private String smsNotificationSource;
    private String callEndSource;

    // PDC Keys
    private final NamespacedKey phoneKey;
    private final NamespacedKey phoneTypeKey;
    private final NamespacedKey phoneUuidKey;
    private final NamespacedKey phoneCreditsKey;
    private final NamespacedKey planKey;
    private final NamespacedKey planTypeKey;

    // ItemsAdder
    private boolean itemsAdderAvailable = false;

    public PhoneManager(RoleplayCity plugin) {
        this.plugin = plugin;

        // Initialiser les cles PDC
        this.phoneKey = new NamespacedKey(plugin, "phone");
        this.phoneTypeKey = new NamespacedKey(plugin, "phone_type");
        this.phoneUuidKey = new NamespacedKey(plugin, "phone_uuid");
        this.phoneCreditsKey = new NamespacedKey(plugin, "phone_credits");
        this.planKey = new NamespacedKey(plugin, "phone_plan");
        this.planTypeKey = new NamespacedKey(plugin, "plan_type");
    }

    /**
     * Initialise le manager et charge la configuration
     */
    public void initialize() {
        // Verifier ItemsAdder
        itemsAdderAvailable = Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;

        // Charger la configuration
        loadConfiguration();

        // Enregistrer les recettes
        registerRecipes();

        plugin.getLogger().info("[Phone] Systeme de telephonie initialise - " +
                phoneTypes.size() + " telephones, " + planTypes.size() + " forfaits");
    }

    /**
     * Charge la configuration depuis phones.yml
     */
    public void loadConfiguration() {
        phoneTypes.clear();
        planTypes.clear();
        musicTracks.clear();
        messages.clear();

        // Creer le fichier s'il n'existe pas
        File configFile = new File(plugin.getDataFolder(), "phones.yml");
        if (!configFile.exists()) {
            plugin.saveResource("phones.yml", false);
        }

        phoneConfig = YamlConfiguration.loadConfiguration(configFile);

        // Charger les telephones
        ConfigurationSection phonesSection = phoneConfig.getConfigurationSection("phones");
        if (phonesSection != null) {
            for (String phoneId : phonesSection.getKeys(false)) {
                ConfigurationSection phoneSection = phonesSection.getConfigurationSection(phoneId);
                if (phoneSection != null) {
                    PhoneType type = PhoneType.fromConfig(phoneId, phoneSection);
                    if (type.isEnabled()) {
                        phoneTypes.put(phoneId, type);
                    }
                }
            }
        }

        // Charger les forfaits
        ConfigurationSection plansSection = phoneConfig.getConfigurationSection("plans");
        if (plansSection != null) {
            for (String planId : plansSection.getKeys(false)) {
                ConfigurationSection planSection = plansSection.getConfigurationSection(planId);
                if (planSection != null) {
                    PlanType type = PlanType.fromConfig(planId, planSection);
                    if (type.isEnabled()) {
                        planTypes.put(planId, type);
                    }
                }
            }
        }

        // Charger les couts
        ConfigurationSection costsSection = phoneConfig.getConfigurationSection("costs");
        if (costsSection != null) {
            callCostPerMinute = costsSection.getInt("call_per_minute", 50);
            smsCost = costsSection.getInt("sms_per_message", 10);
        }

        // Charger les pistes musicales
        ConfigurationSection musicSection = phoneConfig.getConfigurationSection("music");
        if (musicSection != null) {
            List<Map<?, ?>> tracks = musicSection.getMapList("tracks");
            for (Map<?, ?> trackMap : tracks) {
                String id = String.valueOf(trackMap.get("id"));
                String name = String.valueOf(trackMap.get("name"));
                String source = String.valueOf(trackMap.get("source"));
                musicTracks.add(new MusicTrack(id, name, source));
            }
        }

        // Charger les sons
        ConfigurationSection soundsSection = phoneConfig.getConfigurationSection("sounds");
        if (soundsSection != null) {
            ringtoneSource = soundsSection.getString("ringtone", "files:sounds/ringtone.mp3");
            smsNotificationSource = soundsSection.getString("notification_sms", "files:sounds/sms.mp3");
            callEndSource = soundsSection.getString("call_end", "files:sounds/call_end.mp3");
        }

        // Charger les messages
        ConfigurationSection messagesSection = phoneConfig.getConfigurationSection("messages");
        if (messagesSection != null) {
            for (String key : messagesSection.getKeys(false)) {
                messages.put(key, messagesSection.getString(key, ""));
            }
        }

        // Charger les settings
        ConfigurationSection settingsSection = phoneConfig.getConfigurationSection("settings");
        if (settingsSection != null) {
            callTimeout = settingsSection.getInt("call_timeout", 30);
            smsMaxLength = settingsSection.getInt("sms_max_length", 160);
            maxContacts = settingsSection.getInt("max_contacts", 50);
        }
    }

    /**
     * Enregistre les recettes de craft pour les telephones et forfaits
     */
    private void registerRecipes() {
        // Enregistrer les recettes de telephones (seulement ceux avec un pattern valide ET pas de coloration)
        for (PhoneType type : phoneTypes.values()) {
            if (type.isCraftEnabled() && !type.isCraftColoration()) {
                // Les recettes avec coloration: true sont gérées par DyeCraftListener
                String[] pattern = type.getCraftPattern();
                if (pattern != null && pattern.length > 0) {
                    registerPhoneRecipe(type);
                }
            }
        }

        // Enregistrer les recettes de forfaits
        for (PlanType type : planTypes.values()) {
            if (type.isCraftEnabled()) {
                String[] pattern = type.getCraftPattern();
                if (pattern != null && pattern.length > 0) {
                    registerPlanRecipe(type);
                }
            }
        }
    }

    private void registerPhoneRecipe(PhoneType type) {
        try {
            // Vérifier que le pattern existe et est valide
            String[] pattern = type.getCraftPattern();
            if (pattern == null || pattern.length == 0) {
                // Pas de pattern = craftable via teinture uniquement, pas d'erreur
                plugin.getLogger().fine("[Phone] " + type.getId() + " n'a pas de recette de craft (teinture uniquement)");
                return;
            }

            ItemStack result = createPhoneItem(type.getId(), 0);
            if (result == null) return;

            NamespacedKey recipeKey = new NamespacedKey(plugin, "phone_" + type.getId());

            // Supprimer l'ancienne recette si elle existe
            Bukkit.removeRecipe(recipeKey);

            ShapedRecipe recipe = new ShapedRecipe(recipeKey, result);
            recipe.shape(pattern);

            Map<Character, Material> ingredients = type.getCraftIngredients();
            if (ingredients != null) {
                for (Map.Entry<Character, Material> entry : ingredients.entrySet()) {
                    recipe.setIngredient(entry.getKey(), entry.getValue());
                }
            }

            Bukkit.addRecipe(recipe);
            plugin.getLogger().fine("[Phone] Recette enregistree pour: " + type.getId());

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Phone] Erreur enregistrement recette " + type.getId(), e);
        }
    }

    private void registerPlanRecipe(PlanType type) {
        try {
            ItemStack result = createPlanItem(type.getId());
            if (result == null) return;

            NamespacedKey recipeKey = new NamespacedKey(plugin, "plan_" + type.getId());

            Bukkit.removeRecipe(recipeKey);

            ShapedRecipe recipe = new ShapedRecipe(recipeKey, result);

            String[] pattern = type.getCraftPattern();
            if (pattern.length >= 1) recipe.shape(pattern);

            Map<Character, Material> ingredients = type.getCraftIngredients();
            for (Map.Entry<Character, Material> entry : ingredients.entrySet()) {
                recipe.setIngredient(entry.getKey(), entry.getValue());
            }

            Bukkit.addRecipe(recipe);
            plugin.getLogger().fine("[Phone] Recette enregistree pour forfait: " + type.getId());

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Phone] Erreur enregistrement recette forfait " + type.getId(), e);
        }
    }

    /**
     * Recharge la configuration du systeme de telephonie.
     * Utilisé par /roleplaycity reload pour recharger phones.yml
     */
    public void reload() {
        // Supprimer les anciennes recettes
        unregisterRecipes();

        // Recharger la configuration
        loadConfiguration();

        // Ré-enregistrer les recettes
        registerRecipes();

        plugin.getLogger().info("[Phone] Configuration rechargée - " +
            phoneTypes.size() + " telephones, " + planTypes.size() + " forfaits, " +
            musicTracks.size() + " pistes musicales");
    }

    /**
     * Desenregistre toutes les recettes de telephones et forfaits
     */
    public void unregisterRecipes() {
        // Desenregistrer les recettes de telephones
        for (PhoneType type : phoneTypes.values()) {
            try {
                NamespacedKey recipeKey = new NamespacedKey(plugin, "phone_" + type.getId());
                Bukkit.removeRecipe(recipeKey);
            } catch (Exception ignored) {}
        }

        // Desenregistrer les recettes de forfaits
        for (PlanType type : planTypes.values()) {
            try {
                NamespacedKey recipeKey = new NamespacedKey(plugin, "plan_" + type.getId());
                Bukkit.removeRecipe(recipeKey);
            } catch (Exception ignored) {}
        }

        plugin.getLogger().info("[Phone] Recettes de telephonie desenregistrees");
    }

    // ==================== CREATION D'ITEMS ====================

    /**
     * Cree un item telephone avec les credits specifies
     */
    public ItemStack createPhoneItem(String typeId, int credits) {
        PhoneType type = phoneTypes.get(typeId);
        if (type == null) return null;

        ItemStack item;

        // Creer l'item de base
        if (type.isItemsAdderItem() && itemsAdderAvailable && type.getItemsAdderId() != null) {
            try {
                CustomStack customStack = CustomStack.getInstance(type.getItemsAdderId());
                if (customStack != null) {
                    item = customStack.getItemStack().clone();
                } else {
                    item = new ItemStack(Material.PAPER);
                }
            } catch (Exception e) {
                item = new ItemStack(Material.PAPER);
            }
        } else {
            Material mat = type.getVanillaMaterial() != null ? type.getVanillaMaterial() : Material.PAPER;
            item = new ItemStack(mat);
        }

        // Configurer les meta
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Display name
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', type.getDisplayName()));

            // Lore avec credits
            List<String> lore = new ArrayList<>();
            for (String line : type.getLore()) {
                String processed = line.replace("{credits}", String.valueOf(credits));
                lore.add(ChatColor.translateAlternateColorCodes('&', processed));
            }
            meta.setLore(lore);

            // PDC
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(phoneKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(phoneTypeKey, PersistentDataType.STRING, typeId);
            pdc.set(phoneUuidKey, PersistentDataType.STRING, UUID.randomUUID().toString());
            pdc.set(phoneCreditsKey, PersistentDataType.INTEGER, credits);

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Cree un item forfait
     */
    public ItemStack createPlanItem(String typeId) {
        PlanType type = planTypes.get(typeId);
        if (type == null) return null;

        ItemStack item;

        if (type.isItemsAdderItem() && itemsAdderAvailable && type.getItemsAdderId() != null) {
            try {
                CustomStack customStack = CustomStack.getInstance(type.getItemsAdderId());
                if (customStack != null) {
                    item = customStack.getItemStack().clone();
                } else {
                    item = new ItemStack(Material.PAPER);
                }
            } catch (Exception e) {
                item = new ItemStack(Material.PAPER);
            }
        } else {
            Material mat = type.getVanillaMaterial() != null ? type.getVanillaMaterial() : Material.PAPER;
            item = new ItemStack(mat);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', type.getDisplayName()));

            List<String> lore = new ArrayList<>();
            for (String line : type.getLore()) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(lore);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(planKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(planTypeKey, PersistentDataType.STRING, typeId);

            item.setItemMeta(meta);
        }

        return item;
    }

    // ==================== DETECTION D'ITEMS ====================

    /**
     * Verifie si un item est un telephone
     */
    public boolean isPhone(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();

        // Verifier PDC d'abord (nos items craftes)
        if (pdc.has(phoneKey, PersistentDataType.BYTE)) {
            return true;
        }

        // Verifier ItemsAdder
        if (itemsAdderAvailable) {
            try {
                CustomStack customStack = CustomStack.byItemStack(item);
                if (customStack != null) {
                    String itemId = customStack.getNamespacedID();
                    for (PhoneType type : phoneTypes.values()) {
                        if (type.getItemsAdderId() != null && type.getItemsAdderId().equals(itemId)) {
                            return true;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return false;
    }

    /**
     * Verifie si un item est un forfait
     */
    public boolean isPlan(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();

        if (pdc.has(planKey, PersistentDataType.BYTE)) {
            return true;
        }

        if (itemsAdderAvailable) {
            try {
                CustomStack customStack = CustomStack.byItemStack(item);
                if (customStack != null) {
                    String itemId = customStack.getNamespacedID();
                    for (PlanType type : planTypes.values()) {
                        if (type.getItemsAdderId() != null && type.getItemsAdderId().equals(itemId)) {
                            return true;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return false;
    }

    /**
     * Recupere le type de telephone d'un item
     */
    public PhoneType getPhoneType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();

        // Verifier PDC
        String typeId = pdc.get(phoneTypeKey, PersistentDataType.STRING);
        if (typeId != null) {
            return phoneTypes.get(typeId);
        }

        // Verifier ItemsAdder
        if (itemsAdderAvailable) {
            try {
                CustomStack customStack = CustomStack.byItemStack(item);
                if (customStack != null) {
                    String itemId = customStack.getNamespacedID();
                    for (PhoneType type : phoneTypes.values()) {
                        if (type.getItemsAdderId() != null && type.getItemsAdderId().equals(itemId)) {
                            return type;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    /**
     * Recupere le type de forfait d'un item
     */
    public PlanType getPlanType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();

        String typeId = pdc.get(planTypeKey, PersistentDataType.STRING);
        if (typeId != null) {
            return planTypes.get(typeId);
        }

        if (itemsAdderAvailable) {
            try {
                CustomStack customStack = CustomStack.byItemStack(item);
                if (customStack != null) {
                    String itemId = customStack.getNamespacedID();
                    for (PlanType type : planTypes.values()) {
                        if (type.getItemsAdderId() != null && type.getItemsAdderId().equals(itemId)) {
                            return type;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    // ==================== GESTION DES CREDITS ====================

    /**
     * Recupere les credits d'un telephone
     */
    public int getPhoneCredits(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Integer credits = pdc.get(phoneCreditsKey, PersistentDataType.INTEGER);
        return credits != null ? credits : 0;
    }

    /**
     * Definit les credits d'un telephone et met a jour le lore
     */
    public void setPhoneCredits(ItemStack item, int credits) {
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(phoneCreditsKey, PersistentDataType.INTEGER, credits);

        // Mettre a jour le lore
        PhoneType type = getPhoneType(item);
        if (type != null) {
            List<String> lore = new ArrayList<>();
            for (String line : type.getLore()) {
                String processed = line.replace("{credits}", String.valueOf(credits));
                lore.add(ChatColor.translateAlternateColorCodes('&', processed));
            }
            meta.setLore(lore);
        }

        item.setItemMeta(meta);
    }

    /**
     * Ajoute des credits a un telephone
     */
    public void addPhoneCredits(ItemStack item, int amount) {
        int current = getPhoneCredits(item);
        setPhoneCredits(item, current + amount);
    }

    /**
     * Deduit des credits d'un telephone
     * @return true si suffisant, false sinon
     */
    public boolean deductCredits(ItemStack item, int amount) {
        int current = getPhoneCredits(item);
        if (current < amount) return false;
        setPhoneCredits(item, current - amount);
        return true;
    }

    // Alias pour compatibilite avec les GUIs/Services
    public int getCredits(ItemStack item) {
        return getPhoneCredits(item);
    }

    public void addCredits(ItemStack item, int amount) {
        addPhoneCredits(item, amount);
    }

    /**
     * Recupere l'UUID unique d'un telephone
     */
    public String getPhoneUuid(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.get(phoneUuidKey, PersistentDataType.STRING);
    }

    // ==================== UTILITAIRES ====================

    /**
     * Trouve un telephone dans l'inventaire du joueur
     */
    public ItemStack findPhoneInInventory(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isPhone(item)) {
                return item;
            }
        }
        return null;
    }

    /**
     * Verifie si le joueur a un telephone en main (principale ou secondaire)
     */
    public boolean hasPhoneInHand(Player player) {
        return isPhone(player.getInventory().getItemInMainHand()) ||
                isPhone(player.getInventory().getItemInOffHand());
    }

    /**
     * Recupere le telephone en main du joueur
     */
    public ItemStack getPhoneInHand(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (isPhone(mainHand)) return mainHand;

        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (isPhone(offHand)) return offHand;

        return null;
    }

    /**
     * Recupere un message configure
     */
    public String getMessage(String key) {
        String msg = messages.get(key);
        return msg != null ? ChatColor.translateAlternateColorCodes('&', msg) : "";
    }

    /**
     * Recupere un message avec placeholders
     */
    public String getMessage(String key, Map<String, String> placeholders) {
        String msg = getMessage(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            msg = msg.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return msg;
    }

    // ==================== GETTERS ====================

    public Map<String, PhoneType> getPhoneTypes() {
        return Collections.unmodifiableMap(phoneTypes);
    }

    public Map<String, PlanType> getPlanTypes() {
        return Collections.unmodifiableMap(planTypes);
    }

    public List<MusicTrack> getMusicTracks() {
        return Collections.unmodifiableList(musicTracks);
    }

    public int getCallCostPerMinute() {
        return callCostPerMinute;
    }

    public int getSmsCost() {
        return smsCost;
    }

    public int getCallTimeout() {
        return callTimeout;
    }

    public int getSmsMaxLength() {
        return smsMaxLength;
    }

    // Alias pour compatibilite
    public int getMaxSmsLength() {
        return smsMaxLength;
    }

    public String getSmsNotificationSound() {
        // Retourne un son Bukkit standard si OpenAudioMc n'est pas utilise
        return "ENTITY_EXPERIENCE_ORB_PICKUP";
    }

    public String getRingtoneSound() {
        // Retourne un son Bukkit standard si OpenAudioMc n'est pas utilise
        return "BLOCK_NOTE_BLOCK_BELL";
    }

    public int getRingTimeout() {
        return callTimeout;
    }

    public int getMaxContacts() {
        return maxContacts;
    }

    public String getRingtoneSource() {
        return ringtoneSource;
    }

    public String getSmsNotificationSource() {
        return smsNotificationSource;
    }

    public String getCallEndSource() {
        return callEndSource;
    }

    public boolean isItemsAdderAvailable() {
        return itemsAdderAvailable;
    }

    public NamespacedKey getPhoneKey() {
        return phoneKey;
    }

    public NamespacedKey getPlanKey() {
        return planKey;
    }

    // ==================== NUMERO DE TELEPHONE ====================

    // Cle PDC pour le numero de telephone
    private final NamespacedKey phoneNumberKey = new NamespacedKey(
        Bukkit.getPluginManager().getPlugin("RoleplayCity"), "phone_number");

    /**
     * Recupere le numero de telephone d'un item telephone.
     */
    public String getPhoneNumber(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.get(phoneNumberKey, PersistentDataType.STRING);
    }

    /**
     * Met a jour le numero de telephone d'un item.
     */
    public void updatePhoneNumber(ItemStack item, String newNumber) {
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(phoneNumberKey, PersistentDataType.STRING, newNumber);
        item.setItemMeta(meta);
    }

    /**
     * Definit le numero de telephone sur un item (pour creation).
     */
    public void setPhoneNumber(ItemStack item, String phoneNumber) {
        updatePhoneNumber(item, phoneNumber);
    }

    /**
     * Recupere le type de telephone par son ID.
     */
    public String getPhoneType(String typeId) {
        PhoneType type = phoneTypes.get(typeId);
        return type != null ? type.getId() : null;
    }

    /**
     * Cout pour changer de numero.
     */
    public int getChangeNumberCost() {
        return phoneConfig != null ? phoneConfig.getInt("costs.change_number", 500) : 500;
    }
}
