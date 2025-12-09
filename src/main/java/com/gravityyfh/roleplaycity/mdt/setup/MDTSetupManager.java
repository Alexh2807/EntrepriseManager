package com.gravityyfh.roleplaycity.mdt.setup;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.mdt.config.MDTConfig;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Gestionnaire des sessions de configuration MDT
 */
public class MDTSetupManager {
    private final RoleplayCity plugin;
    private final MDTConfig mdtConfig;
    private final Map<UUID, MDTSetupSession> activeSessions = new HashMap<>();

    private static final String PREFIX = ChatColor.GRAY + "[" + ChatColor.GOLD + "MDT Setup" + ChatColor.GRAY + "] ";

    public MDTSetupManager(RoleplayCity plugin, MDTConfig mdtConfig) {
        this.plugin = plugin;
        this.mdtConfig = mdtConfig;
    }

    /**
     * Démarre une session de setup pour un joueur
     */
    public boolean startSetup(Player player) {
        if (activeSessions.containsKey(player.getUniqueId())) {
            player.sendMessage(PREFIX + ChatColor.RED + "Tu es déjà en mode setup!");
            return false;
        }

        // Charger les données existantes dans la session
        MDTSetupSession session = new MDTSetupSession(player.getUniqueId());
        loadExistingConfig(session);

        activeSessions.put(player.getUniqueId(), session);

        player.sendMessage("");
        player.sendMessage(PREFIX + ChatColor.GREEN + "Mode Setup activé!");
        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "➤ Utilise " + ChatColor.WHITE + "/mdt setup gui" + ChatColor.YELLOW + " pour ouvrir le menu");
        player.sendMessage(ChatColor.YELLOW + "➤ Ou " + ChatColor.WHITE + "/mdt setup <type>" + ChatColor.YELLOW + " pour changer de mode");
        player.sendMessage(ChatColor.YELLOW + "➤ " + ChatColor.WHITE + "/mdt setup save" + ChatColor.YELLOW + " pour sauvegarder");
        player.sendMessage(ChatColor.YELLOW + "➤ " + ChatColor.WHITE + "/mdt setup cancel" + ChatColor.YELLOW + " pour annuler");
        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Mode actuel: " + session.getCurrentMode().getDisplayName());
        player.sendMessage(session.getCurrentMode().getColoredInstruction());
        player.sendMessage("");

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);

        return true;
    }

    /**
     * Charge la configuration existante dans la session
     */
    private void loadExistingConfig(MDTSetupSession session) {
        // Charger les spawns existants
        Location lobby = mdtConfig.getLobbySpawn();
        if (lobby != null) session.setLobbySpawn(lobby);

        Location redSpawn = mdtConfig.getRedTeamSpawn();
        if (redSpawn != null) session.setRedTeamSpawn(redSpawn);

        Location blueSpawn = mdtConfig.getBlueTeamSpawn();
        if (blueSpawn != null) session.setBlueTeamSpawn(blueSpawn);

        // Les lits et générateurs sont chargés via MDTConfig au démarrage d'une partie
        // Pour le setup, on recharge depuis le fichier brut
        try {
            File configFile = new File(plugin.getDataFolder(), "mdt/mdt.yml");
            if (configFile.exists()) {
                FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

                // Lit rouge
                if (config.contains("beds.red-bed")) {
                    Location redBed = loadLocationFromConfig(config, "beds.red-bed");
                    if (redBed != null) session.setRedBedLocation(redBed);
                }

                // Lit bleu
                if (config.contains("beds.blue-bed")) {
                    Location blueBed = loadLocationFromConfig(config, "beds.blue-bed");
                    if (blueBed != null) session.setBlueBedLocation(blueBed);
                }

                // Lits neutres
                List<Map<?, ?>> neutralBeds = config.getMapList("beds.neutral-beds");
                for (Map<?, ?> bedMap : neutralBeds) {
                    Location loc = loadLocationFromMap(bedMap);
                    if (loc != null) session.addNeutralBed(loc);
                }

                // Générateurs
                for (String type : Arrays.asList("brick", "iron", "gold", "diamond")) {
                    List<Map<?, ?>> positions = config.getMapList("generators." + type + ".positions");
                    for (Map<?, ?> posMap : positions) {
                        Location loc = loadLocationFromMap(posMap);
                        if (loc != null) session.addGenerator(type, loc);
                    }
                }

                // 4 Marchands
                for (String merchantType : Arrays.asList("blocks", "weapons", "armor", "special")) {
                    if (config.contains("merchants." + merchantType + ".location")) {
                        Location merchant = loadLocationFromConfig(config, "merchants." + merchantType + ".location");
                        if (merchant != null) session.setMerchantLocation(merchantType, merchant);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[MDT Setup] Erreur lors du chargement de la config existante: " + e.getMessage());
        }
    }

    private Location loadLocationFromConfig(FileConfiguration config, String path) {
        String worldName = mdtConfig.getWorldName();
        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        double x = config.getDouble(path + ".x", 0);
        double y = config.getDouble(path + ".y", 70);
        double z = config.getDouble(path + ".z", 0);
        float yaw = (float) config.getDouble(path + ".yaw", 0);
        float pitch = (float) config.getDouble(path + ".pitch", 0);

        return new Location(world, x, y, z, yaw, pitch);
    }

    private Location loadLocationFromMap(Map<?, ?> map) {
        String worldName = mdtConfig.getWorldName();
        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        Object xObj = map.get("x");
        Object yObj = map.get("y");
        Object zObj = map.get("z");

        double x = xObj instanceof Number ? ((Number) xObj).doubleValue() : 0;
        double y = yObj instanceof Number ? ((Number) yObj).doubleValue() : 70;
        double z = zObj instanceof Number ? ((Number) zObj).doubleValue() : 0;

        return new Location(world, x, y, z);
    }

    /**
     * Termine et sauvegarde la session de setup
     */
    public boolean saveSetup(Player player) {
        MDTSetupSession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "Tu n'es pas en mode setup!");
            return false;
        }

        // Vérifier la configuration minimale
        if (!session.isMinimalConfigComplete()) {
            player.sendMessage(PREFIX + ChatColor.RED + "Configuration incomplète!");
            player.sendMessage(ChatColor.YELLOW + "Éléments manquants:");
            for (String missing : session.getMissingElements()) {
                player.sendMessage(ChatColor.RED + "  ✗ " + missing);
            }
            return false;
        }

        // Sauvegarder dans le fichier
        try {
            saveSessionToConfig(session);
            activeSessions.remove(player.getUniqueId());

            player.sendMessage("");
            player.sendMessage(PREFIX + ChatColor.GREEN + "Configuration sauvegardée avec succès!");
            player.sendMessage(ChatColor.GRAY + "Total: " + session.getTotalConfiguredElements() + " éléments configurés");
            player.sendMessage(ChatColor.YELLOW + "Utilise " + ChatColor.WHITE + "/mdt reload" + ChatColor.YELLOW + " pour appliquer les changements");
            player.sendMessage("");

            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            return true;

        } catch (Exception e) {
            player.sendMessage(PREFIX + ChatColor.RED + "Erreur lors de la sauvegarde: " + e.getMessage());
            plugin.getLogger().severe("[MDT Setup] Erreur sauvegarde: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Sauvegarde la session dans le fichier de configuration
     */
    private void saveSessionToConfig(MDTSetupSession session) throws IOException {
        File configFile = new File(plugin.getDataFolder(), "mdt/mdt.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

        // Spawns
        saveLocationToConfig(config, "spawns.lobby", session.getLobbySpawn());
        saveLocationToConfig(config, "spawns.red-team", session.getRedTeamSpawn());
        saveLocationToConfig(config, "spawns.blue-team", session.getBlueTeamSpawn());

        // Lits d'équipe
        saveLocationToConfig(config, "beds.red-bed", session.getRedBedLocation());
        saveLocationToConfig(config, "beds.blue-bed", session.getBlueBedLocation());

        // Lits neutres
        List<Map<String, Object>> neutralBeds = new ArrayList<>();
        int neutralIndex = 0;
        for (Location loc : session.getNeutralBedLocations()) {
            Map<String, Object> bedMap = new LinkedHashMap<>();
            bedMap.put("id", "neutral_" + neutralIndex++);
            bedMap.put("x", loc.getX());
            bedMap.put("y", loc.getY());
            bedMap.put("z", loc.getZ());
            bedMap.put("hearts-bonus", 2);
            neutralBeds.add(bedMap);
        }
        config.set("beds.neutral-beds", neutralBeds);

        // Générateurs
        for (Map.Entry<String, List<Location>> entry : session.getGeneratorLocations().entrySet()) {
            String type = entry.getKey();
            List<Location> locations = entry.getValue();

            List<Map<String, Object>> positions = new ArrayList<>();
            for (Location loc : locations) {
                Map<String, Object> posMap = new LinkedHashMap<>();
                posMap.put("x", loc.getX());
                posMap.put("y", loc.getY());
                posMap.put("z", loc.getZ());
                positions.add(posMap);
            }
            config.set("generators." + type + ".positions", positions);
        }

        // 4 Marchands
        for (Map.Entry<String, Location> entry : session.getMerchantLocations().entrySet()) {
            String merchantType = entry.getKey();
            Location loc = entry.getValue();
            saveLocationToConfig(config, "merchants." + merchantType + ".location", loc);
        }

        config.save(configFile);
    }

    private void saveLocationToConfig(FileConfiguration config, String path, Location loc) {
        if (loc == null) return;
        config.set(path + ".x", loc.getX());
        config.set(path + ".y", loc.getY());
        config.set(path + ".z", loc.getZ());
        config.set(path + ".yaw", loc.getYaw());
        config.set(path + ".pitch", loc.getPitch());
    }

    /**
     * Annule la session de setup
     */
    public boolean cancelSetup(Player player) {
        if (!activeSessions.containsKey(player.getUniqueId())) {
            player.sendMessage(PREFIX + ChatColor.RED + "Tu n'es pas en mode setup!");
            return false;
        }

        activeSessions.remove(player.getUniqueId());
        player.sendMessage(PREFIX + ChatColor.YELLOW + "Setup annulé. Aucune modification sauvegardée.");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
        return true;
    }

    /**
     * Change le mode de setup actuel
     */
    public boolean setMode(Player player, MDTSetupMode mode) {
        MDTSetupSession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "Tu n'es pas en mode setup!");
            player.sendMessage(ChatColor.GRAY + "Utilise " + ChatColor.WHITE + "/mdt setup start" + ChatColor.GRAY + " pour commencer");
            return false;
        }

        session.setCurrentMode(mode);
        player.sendMessage("");
        player.sendMessage(PREFIX + "Mode changé: " + mode.getDisplayName());
        player.sendMessage(mode.getColoredInstruction());
        player.sendMessage("");

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
        return true;
    }

    /**
     * Vérifie si un joueur est en mode setup
     */
    public boolean isInSetupMode(UUID playerUuid) {
        return activeSessions.containsKey(playerUuid);
    }

    /**
     * Récupère la session d'un joueur
     */
    public MDTSetupSession getSession(UUID playerUuid) {
        return activeSessions.get(playerUuid);
    }

    /**
     * Affiche le statut actuel du setup
     */
    public void showStatus(Player player) {
        MDTSetupSession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "Tu n'es pas en mode setup!");
            return;
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════ MDT Setup Status ═══════");
        player.sendMessage("");

        // Spawns
        player.sendMessage(ChatColor.YELLOW + "Spawns:");
        sendStatusLine(player, "  Lobby", session.getLobbySpawn());
        sendStatusLine(player, "  Équipe Rouge", session.getRedTeamSpawn());
        sendStatusLine(player, "  Équipe Bleue", session.getBlueTeamSpawn());

        // Lits
        player.sendMessage(ChatColor.YELLOW + "Lits:");
        sendStatusLine(player, "  Lit Rouge", session.getRedBedLocation());
        sendStatusLine(player, "  Lit Bleu", session.getBlueBedLocation());
        player.sendMessage(ChatColor.GRAY + "  Lits Neutres: " + ChatColor.WHITE + session.getNeutralBedLocations().size() + "/4");

        // Générateurs
        player.sendMessage(ChatColor.YELLOW + "Générateurs:");
        Map<String, List<Location>> gens = session.getGeneratorLocations();
        player.sendMessage(ChatColor.GRAY + "  Brique: " + ChatColor.WHITE + gens.get("brick").size());
        player.sendMessage(ChatColor.GRAY + "  Fer: " + ChatColor.WHITE + gens.get("iron").size());
        player.sendMessage(ChatColor.GRAY + "  Or: " + ChatColor.WHITE + gens.get("gold").size());
        player.sendMessage(ChatColor.GRAY + "  Diamant: " + ChatColor.WHITE + gens.get("diamond").size());

        // 4 Marchands
        player.sendMessage(ChatColor.YELLOW + "Marchands (4):");
        Map<String, Location> merchants = session.getMerchantLocations();
        sendStatusLine(player, "  Blocs", merchants.get("blocks"));
        sendStatusLine(player, "  Armes", merchants.get("weapons"));
        sendStatusLine(player, "  Armures", merchants.get("armor"));
        sendStatusLine(player, "  Spécial", merchants.get("special"));

        player.sendMessage("");
        player.sendMessage(ChatColor.GRAY + "Mode actuel: " + session.getCurrentMode().getDisplayName());
        player.sendMessage(ChatColor.GRAY + "Total: " + ChatColor.WHITE + session.getTotalConfiguredElements() + " éléments");
        player.sendMessage("");
    }

    private void sendStatusLine(Player player, String label, Location loc) {
        if (loc != null) {
            player.sendMessage(ChatColor.GREEN + label + ": ✓ " + ChatColor.GRAY +
                String.format("(%.1f, %.1f, %.1f)", loc.getX(), loc.getY(), loc.getZ()));
        } else {
            player.sendMessage(ChatColor.RED + label + ": ✗ Non défini");
        }
    }

    /**
     * Récupère le préfixe pour les messages
     */
    public String getPrefix() {
        return PREFIX;
    }

    /**
     * Nettoie toutes les sessions (appelé au shutdown)
     */
    public void cleanup() {
        activeSessions.clear();
    }
}
