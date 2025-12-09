package com.gravityyfh.roleplaycity.lotto;

import com.gravityyfh.roleplaycity.RoleplayCity;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Gestionnaire du système de Loto Horaire.
 * Gère les phases (Achat -> Attente -> Tirage).
 */
public class LottoManager {

    private final RoleplayCity plugin;
    
    // Config
        private double ticketPrice;
        private int startMinute;
        private int endMinute;
        private int drawMinute;
        
        private final List<UUID> tickets = new ArrayList<>();
        private LottoState currentState = LottoState.WAITING;
        private final Random random = new Random();
        private final LottoPersistenceService persistence;
    
        public LottoManager(RoleplayCity plugin) {
            this.plugin = plugin;
            this.persistence = new LottoPersistenceService(plugin);
            
            // Charger l'état persistant
            this.currentState = persistence.loadState();
            this.tickets.addAll(persistence.loadTickets());
            
            reloadConfig();
            startScheduler();
        }
    
        public void reloadConfig() {
            this.ticketPrice = plugin.getConfig().getDouble("lotto-system.ticket-price", 20.0);
            this.startMinute = plugin.getConfig().getInt("lotto-system.schedule.start-minute", 15);
            this.endMinute = plugin.getConfig().getInt("lotto-system.schedule.end-minute", 30);
            this.drawMinute = plugin.getConfig().getInt("lotto-system.schedule.draw-minute", 45);
        }
    
        public enum LottoState {
            WAITING, // Avant start
            OPEN,    // Entre start et end
            CLOSED   // Entre end et draw
        }
    
        private void startScheduler() {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!plugin.getConfig().getBoolean("lotto-system.enabled", true)) return;
                    checkTime();
                }
            }.runTaskTimer(plugin, 20L, 1200L); // 60 secondes
        }
    
        private void checkTime() {
            LocalDateTime now = LocalDateTime.now();
            int minute = now.getMinute();
    
            // Cycle normal
            if (minute >= startMinute && minute < endMinute && currentState != LottoState.OPEN) {
                startLotto();
            }
            else if (minute >= endMinute && minute < drawMinute && currentState == LottoState.OPEN) {
                closeSales();
            }
            else if (minute >= drawMinute && currentState == LottoState.CLOSED) {
                drawWinner();
            }
            // Reset après le tirage (si on est passé l'heure de tirage et qu'on attend le prochain tour)
            else if (minute < startMinute && currentState != LottoState.WAITING) {
                currentState = LottoState.WAITING;
                persistence.saveState(currentState); // Sauvegarder le retour à WAITING
                tickets.clear();
                persistence.clearTickets();
            }
        }
    
    public void startLotto() {
        currentState = LottoState.OPEN;
        persistence.saveState(currentState);
        tickets.clear();
        persistence.clearTickets(); // Sécurité

        int currentHour = LocalDateTime.now().getHour();
        String endMinuteStr = String.format("%02d", endMinute);

        List<String> messages = plugin.getConfig().getStringList("lotto-system.messages.broadcast-start");
        for (String msg : messages) {
            if (msg.isEmpty()) {
                Bukkit.broadcastMessage("");
                continue;
            }
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', msg
                .replace("xxH%end_minute%", currentHour + "h" + endMinuteStr)
                .replace("%end_time%", currentHour + "h" + endMinuteStr)
                .replace("%price%", String.format("%.0f", ticketPrice))));
        }
        
        // Message interactif
        TextComponent message = new TextComponent("  ➤ CLIQUEZ ICI POUR JOUER ◀");
        message.setColor(net.md_5.bungee.api.ChatColor.GREEN);
        message.setBold(true);
        message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/loto"));
        message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Ouvrir le menu du Loto").create()));
        
        Bukkit.spigot().broadcast(message);

        playSoundToAll(Sound.BLOCK_NOTE_BLOCK_PLING, 1.5f);
    }
    
        public void closeSales() {
            currentState = LottoState.CLOSED;
            persistence.saveState(currentState);
            
            double currentPot = getCurrentPot();
            
            List<String> messages = plugin.getConfig().getStringList("lotto-system.messages.broadcast-end");
            for (String msg : messages) {
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', msg
                    .replace("%pot%", String.format("%,.2f", currentPot))));
            }
            
            playSoundToAll(Sound.BLOCK_CHEST_CLOSE, 1.0f);
        }
    
        public void drawWinner() {
            if (tickets.isEmpty()) {
                List<String> messages = plugin.getConfig().getStringList("lotto-system.messages.broadcast-no-winner");
                for (String msg : messages) {
                    Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', msg));
                }
                currentState = LottoState.WAITING;
                persistence.saveState(currentState);
                return;
            }
    
            UUID winnerUUID = tickets.get(random.nextInt(tickets.size()));
            OfflinePlayer winner = Bukkit.getOfflinePlayer(winnerUUID);
            double prize = getCurrentPot();
            String winnerName = winner.getName() != null ? winner.getName() : "Inconnu";
    
                    RoleplayCity.getEconomy().depositPlayer(winner, prize);
            
                    int nextHour = LocalDateTime.now().plusHours(1).getHour();
                    String startMinuteStr = String.format("%02d", startMinute);
            
                    List<String> messages = plugin.getConfig().getStringList("lotto-system.messages.broadcast-draw");
                    for (String msg : messages) {
                        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', msg
                            .replace("%winner%", winnerName)
                            .replace("%prize%", String.format("%,.2f", prize))
                            .replace("H+1:%start_minute%", nextHour + "h" + startMinuteStr)
                            .replace("%next_draw%", nextHour + "h" + startMinuteStr)));
                    }
            
                    playSoundToAll(Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f);            
            Player onlineWinner = winner.getPlayer();
            if (onlineWinner != null) {
                onlineWinner.sendTitle(ChatColor.GOLD + "Gagné !", ChatColor.YELLOW + "+" + String.format("%,.2f€", prize), 10, 70, 20);
                onlineWinner.playSound(onlineWinner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
    
            currentState = LottoState.WAITING;
            persistence.saveState(currentState);
            tickets.clear();
            persistence.clearTickets();
        }
    
        // Admin methods
        public void forceStart() {
            if (currentState == LottoState.OPEN) return;
            startLotto();
        }
    
        public void forceStop() {
            if (currentState != LottoState.OPEN) return;
            closeSales();
        }
    
        public void forceDraw() {
            if (currentState == LottoState.WAITING && tickets.isEmpty()) return;
            drawWinner();
        }
    
        public void reset() {
            currentState = LottoState.WAITING;
            persistence.saveState(currentState);
            tickets.clear();
            persistence.clearTickets();
        }
    
        public boolean buyTickets(Player player, int amount) {
            if (currentState != LottoState.OPEN) {
                player.sendMessage(ChatColor.RED + "Le guichet du Loto est fermé !");
                return false;
            }
    
            double totalCost = amount * ticketPrice;
    
            if (!RoleplayCity.getEconomy().has(player, totalCost)) {
                player.sendMessage(ChatColor.RED + "Vous n'avez pas assez d'argent (" + String.format("%,.2f€", totalCost) + " requis).");
                return false;
            }
    
            RoleplayCity.getEconomy().withdrawPlayer(player, totalCost);
    
            for (int i = 0; i < amount; i++) {
                tickets.add(player.getUniqueId());
                persistence.addTicket(player.getUniqueId()); // Sauvegarde immédiate
            }
    
            player.sendMessage(ChatColor.GREEN + "Vous avez acheté " + amount + " ticket(s) pour " + String.format("%,.2f€", totalCost) + ".");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            
            return true;
        }

    public double getCurrentPot() {
        return tickets.size() * ticketPrice;
    }
    
    public int getPlayerTicketCount(UUID uuid) {
        return (int) tickets.stream().filter(id -> id.equals(uuid)).count();
    }

    public LottoState getState() {
        return currentState;
    }

    public double getTicketPrice() {
        return ticketPrice;
    }
    
    private void playSoundToAll(Sound sound, float pitch) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), sound, 0.5f, pitch);
        }
    }
}
