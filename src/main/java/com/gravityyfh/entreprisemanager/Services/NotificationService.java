package com.gravityyfh.entreprisemanager.Services;

import com.gravityyfh.entreprisemanager.EntrepriseManager;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Level;

public class NotificationService {

    private final EntrepriseManager plugin;
    private final File messagesEmployesFile;
    private final File messagesGerantsFile;

    public NotificationService(EntrepriseManager plugin) {
        this.plugin = plugin;
        this.messagesEmployesFile = new File(plugin.getDataFolder(), "messagesEmployes.yml");
        this.messagesGerantsFile = new File(plugin.getDataFolder(), "messagesGerants.yml");
    }

    public void ajouterMessageEmployeDifferre(String joueurUUID, String message, String entrepriseNom, double montantPrime) {
        FileConfiguration config = YamlConfiguration.loadConfiguration(messagesEmployesFile);
        String listPath = "messages." + joueurUUID + "." + entrepriseNom + ".list";
        List<String> messages = config.getStringList(listPath);
        messages.add(ChatColor.stripColor(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM HH:mm")) + ": " + message));
        config.set(listPath, messages);

        if (montantPrime > 0) {
            String totalPrimePath = "messages." + joueurUUID + "." + entrepriseNom + ".totalPrime";
            config.set(totalPrimePath, config.getDouble(totalPrimePath, 0.0) + montantPrime);
        }
        try {
            config.save(messagesEmployesFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur sauvegarde message différé employé " + joueurUUID, e);
        }
    }

    public void envoyerPrimesDifferreesEmployes(Player player) {
        String playerUUID = player.getUniqueId().toString();
        if (!messagesEmployesFile.exists()) return;

        FileConfiguration config = YamlConfiguration.loadConfiguration(messagesEmployesFile);
        String basePath = "messages." + playerUUID;
        if (!config.contains(basePath)) return;

        ConfigurationSection entreprisesSect = config.getConfigurationSection(basePath);
        if (entreprisesSect == null) return;

        boolean receivedMessage = false;
        for (String nomEnt : entreprisesSect.getKeys(false)) {
            List<String> messages = entreprisesSect.getStringList(nomEnt + ".list");
            double totalPrime = entreprisesSect.getDouble(nomEnt + ".totalPrime", 0.0);
            if (!messages.isEmpty()) {
                player.sendMessage(ChatColor.GOLD + "--- Primes/Messages de '" + nomEnt + "' (hors-ligne) ---");
                messages.forEach(msg -> player.sendMessage(ChatColor.AQUA + "- " + msg));
                if (totalPrime > 0) {
                    player.sendMessage(ChatColor.GREEN + "Total primes période: " + String.format("%,.2f€", totalPrime));
                }
                player.sendMessage(ChatColor.GOLD + "--------------------------------------------------------");
                receivedMessage = true;
            }
        }

        if (receivedMessage) {
            config.set(basePath, null);
            try {
                config.save(messagesEmployesFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Erreur suppression messages différés employé " + playerUUID, e);
            }
        }
    }

    public void ajouterMessageGerantDifferre(String gerantUUID, String message) {
        FileConfiguration config = YamlConfiguration.loadConfiguration(messagesGerantsFile);
        String listPath = "messages." + gerantUUID + ".list";
        List<String> messages = config.getStringList(listPath);
        messages.add(ChatColor.stripColor(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM HH:mm")) + ": " + message));
        config.set(listPath, messages);
        try {
            config.save(messagesGerantsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur sauvegarde message différé gérant " + gerantUUID, e);
        }
    }

    public void envoyerMessagesDifferresGerants(Player player) {
        String gerantUUID = player.getUniqueId().toString();
        if (!messagesGerantsFile.exists()) return;

        FileConfiguration config = YamlConfiguration.loadConfiguration(messagesGerantsFile);
        String listPath = "messages." + gerantUUID + ".list";
        if (!config.contains(listPath)) return;

        List<String> messages = config.getStringList(listPath);
        if (!messages.isEmpty()) {
            player.sendMessage(ChatColor.BLUE + "--- Notifications de Gérance (hors-ligne) ---");
            messages.forEach(msg -> player.sendMessage(ChatColor.AQUA + "- " + msg));
            player.sendMessage(ChatColor.BLUE + "-------------------------------------------------");

            config.set("messages." + gerantUUID, null);
            try {
                config.save(messagesGerantsFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Erreur suppression messages différés gérant " + gerantUUID, e);
            }
        }
    }
}