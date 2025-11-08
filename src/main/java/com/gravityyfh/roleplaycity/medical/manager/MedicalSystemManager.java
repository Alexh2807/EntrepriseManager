package com.gravityyfh.roleplaycity.medical.manager;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.medical.data.InjuredPlayer;
import com.gravityyfh.roleplaycity.medical.data.MedicalMission;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownMember;
import com.gravityyfh.roleplaycity.town.data.TownRole;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import net.md_5.bungee.api.ChatMessageType;
import net.milkbowl.vault.economy.Economy;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MedicalSystemManager {
    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final Economy economy;

    // Stockage des joueurs blessés et missions
    private final Map<UUID, InjuredPlayer> injuredPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, MedicalMission> activeMissions = new ConcurrentHashMap<>();
    private final Map<UUID, Scoreboard> medicScoreboards = new ConcurrentHashMap<>();
    private final Map<UUID, Scoreboard> previousScoreboards = new ConcurrentHashMap<>();

    // Configuration
    private int medicalCost = 250;
    private double medicShare = 0.80; // 80% pour le médecin
    private double townShare = 0.20;  // 20% pour la ville
    private int acceptanceTimeout = 30; // 30 secondes pour accepter
    private int interventionTimeout = 300; // 5 minutes pour intervenir

    public MedicalSystemManager(RoleplayCity plugin) {
        this.plugin = plugin;
        this.townManager = plugin.getTownManager();
        this.economy = RoleplayCity.getEconomy();
    }

    /**
     * Appelé quand un joueur tombe inconscient
     */
    public void onPlayerDowned(Player player, String cause) {
        if (injuredPlayers.containsKey(player.getUniqueId())) {
            return; // Déjà blessé
        }

        // Vérifier si le joueur peut payer
        boolean canAfford = economy.getBalance(player) >= medicalCost;

        // Créer le joueur blessé
        InjuredPlayer injured = new InjuredPlayer(player, cause, canAfford);
        injuredPlayers.put(player.getUniqueId(), injured);

        // Mettre le joueur au sol
        makePlayerDowned(injured);

        // Créer et envoyer la mission médicale
        MedicalMission mission = new MedicalMission(injured);
        activeMissions.put(player.getUniqueId(), mission);

        // Notifier tous les médecins
        notifyMedics(mission);

        // Démarrer le timer d'acceptation (30 secondes)
        startAcceptanceTimer(mission);
    }

    /**
     * Met le joueur au sol avec effets visuels
     */
    private void makePlayerDowned(InjuredPlayer injured) {
        Player player = injured.getPlayer();

        // ArmorStand invisible pour immobiliser le joueur
        ArmorStand mainStand = (ArmorStand) player.getWorld().spawnEntity(
                player.getLocation().subtract(0, 1.7, 0),
                EntityType.ARMOR_STAND
        );
        mainStand.setInvisible(true);
        mainStand.setGravity(false);
        mainStand.setInvulnerable(true);
        mainStand.addPassenger(player);
        injured.addArmorStand(mainStand);

        // Texte au-dessus du joueur
        String[] texts = {
                ChatColor.AQUA + injured.getFormattedTimeLeft(),
                ChatColor.RED + "❤ " + ChatColor.YELLOW + "Blessé",
                ChatColor.GRAY + "En attente de secours..."
        };

        // Démarrer plus haut pour éviter la superposition avec le nametag
        Location textLoc = player.getLocation().clone().add(0, 1.5, 0);
        for (String text : texts) {
            ArmorStand textStand = (ArmorStand) player.getWorld().spawnEntity(
                    textLoc.subtract(0, 0.3, 0),
                    EntityType.ARMOR_STAND
            );
            textStand.setInvisible(true);
            textStand.setGravity(false);
            textStand.setInvulnerable(true);
            textStand.setCustomNameVisible(true);
            textStand.setCustomName(text);
            injured.addArmorStand(textStand);
        }

        // Effets
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, interventionTimeout * 20, 1));
        player.setHealth(1.0);

        // Message au joueur
        player.sendTitle(
                ChatColor.RED + "" + ChatColor.BOLD + "BLESSÉ",
                ChatColor.YELLOW + "Attendez les secours...",
                10, 70, 20
        );

        // Démarrer le countdown
        startInjuredCountdown(injured);
    }

    /**
     * Notifie tous les médecins en ligne d'une urgence
     */
    private void notifyMedics(MedicalMission mission) {
        InjuredPlayer injured = mission.getInjuredPlayer();
        Player patient = injured.getPlayer();

        List<Player> medics = getOnlineMedics();

        if (medics.isEmpty()) {
            // Aucun médecin en ligne
            handleNoMedicAvailable(mission);
            return;
        }

        String paymentStatus = injured.canAffordCare()
                ? ChatColor.GREEN + "✓ Le joueur peut payer"
                : ChatColor.RED + "✗ Le joueur n'a pas l'argent";

        for (Player medic : medics) {
            medic.sendMessage("");
            medic.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            medic.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "   🚑 URGENCE MÉDICALE !");
            medic.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            medic.sendMessage(ChatColor.YELLOW + "📍 Victime: " + ChatColor.WHITE + patient.getName());
            medic.sendMessage(ChatColor.YELLOW + "⚠ Cause: " + ChatColor.WHITE + injured.getInjuryCause());
            medic.sendMessage(ChatColor.YELLOW + "💰 Paiement: " + paymentStatus);
            medic.sendMessage(ChatColor.YELLOW + "🕒 Réponse requise sous " + ChatColor.RED + acceptanceTimeout + " secondes");
            medic.sendMessage("");

            // Bouton cliquable pour accepter
            TextComponent acceptButton = new TextComponent("  [✅ ACCEPTER LA MISSION]  ");
            acceptButton.setColor(net.md_5.bungee.api.ChatColor.GREEN);
            acceptButton.setBold(true);
            acceptButton.setClickEvent(new ClickEvent(
                    ClickEvent.Action.RUN_COMMAND,
                    "/medical accept " + mission.getMissionId()
            ));
            acceptButton.setHoverEvent(new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    new Text("Cliquez pour accepter cette urgence médicale")
            ));

            medic.spigot().sendMessage(acceptButton);
            medic.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            medic.sendMessage("");

            // Son d'alerte
            medic.playSound(medic.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
        }
    }

    /**
     * Récupère tous les médecins en ligne
     */
    private List<Player> getOnlineMedics() {
        List<Player> medics = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            Town town = townManager.getPlayerTownObject(player.getUniqueId());
            if (town != null) {
                TownMember member = town.getMember(player.getUniqueId());
                if (member != null && member.hasRole(TownRole.MEDECIN)) {
                    medics.add(player);
                }
            }
        }

        return medics;
    }

    /**
     * Démarre le timer d'acceptation de 30 secondes
     */
    private void startAcceptanceTimer(MedicalMission mission) {
        int taskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (mission.getStatus() == MedicalMission.MissionStatus.WAITING_FOR_MEDIC) {
                    handleNoMedicAvailable(mission);
                }
            }
        }.runTaskLater(plugin, acceptanceTimeout * 20L).getTaskId();

        mission.setAcceptanceTaskId(taskId);
    }

    /**
     * Gère le cas où aucun médecin n'est disponible ou n'accepte
     */
    private void handleNoMedicAvailable(MedicalMission mission) {
        mission.setStatus(MedicalMission.MissionStatus.EXPIRED);
        Player patient = mission.getInjuredPlayer().getPlayer();

        patient.sendMessage("");
        patient.sendMessage(ChatColor.RED + "❌ Aucun médecin disponible...");
        patient.sendMessage(ChatColor.GRAY + "Vous allez succomber à vos blessures dans " +
                ChatColor.RED + "5 minutes" + ChatColor.GRAY + ".");
        patient.sendMessage(ChatColor.YELLOW + "Ou utilisez " + ChatColor.RED + "/mourir " +
                ChatColor.YELLOW + "pour abandonner vos chances.");
        patient.sendMessage("");
    }

    /**
     * Appelé quand un médecin accepte la mission
     */
    public boolean acceptMission(Player medic, UUID missionId) {
        MedicalMission mission = activeMissions.values().stream()
                .filter(m -> m.getMissionId().equals(missionId))
                .findFirst()
                .orElse(null);

        if (mission == null || mission.getStatus() != MedicalMission.MissionStatus.WAITING_FOR_MEDIC) {
            medic.sendMessage(ChatColor.RED + "Cette mission n'est plus disponible.");
            return false;
        }

        // Annuler le timer d'acceptation
        if (mission.getAcceptanceTaskId() != -1) {
            Bukkit.getScheduler().cancelTask(mission.getAcceptanceTaskId());
        }

        // Assigner le médecin
        mission.assignMedic(medic);

        // Notifier le médecin
        medic.sendMessage("");
        medic.sendMessage(ChatColor.GREEN + "✅ Mission acceptée !");
        medic.sendMessage(ChatColor.YELLOW + "Vous avez " + ChatColor.RED + "5 minutes" +
                ChatColor.YELLOW + " pour sauver " + ChatColor.WHITE + mission.getInjuredPlayer().getPlayer().getName());
        medic.sendMessage("");

        // Notifier le patient
        Player patient = mission.getInjuredPlayer().getPlayer();
        patient.sendMessage("");
        patient.sendMessage(ChatColor.GREEN + "✅ Un médecin vient vers vous !");
        patient.sendMessage(ChatColor.YELLOW + "Médecin: " + ChatColor.WHITE + medic.getName());
        patient.sendMessage("");

        // Notifier les autres médecins
        for (Player otherMedic : getOnlineMedics()) {
            if (!otherMedic.equals(medic)) {
                otherMedic.sendMessage(ChatColor.GRAY + "La mission pour " + patient.getName() +
                        " a été acceptée par " + medic.getName());
            }
        }

        // Créer le scoreboard pour le médecin
        createMedicScoreboard(medic, mission);

        // Démarrer le suivi en temps réel
        startMissionTracking(mission);

        return true;
    }

    /**
     * Crée le scoreboard d'urgence pour le médecin
     */
    private void createMedicScoreboard(Player medic, MedicalMission mission) {
        // Sauvegarder le scoreboard actuel
        if (medic.getScoreboard() != null && medic.getScoreboard() != Bukkit.getScoreboardManager().getMainScoreboard()) {
            previousScoreboards.put(medic.getUniqueId(), medic.getScoreboard());
        }

        // Créer nouveau scoreboard
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective("medical", "dummy",
                ChatColor.RED + "" + ChatColor.BOLD + "🚑 URGENCE");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        medicScoreboards.put(medic.getUniqueId(), board);
        medic.setScoreboard(board);

        updateMedicScoreboard(medic, mission);
    }

    /**
     * Met à jour le scoreboard du médecin
     */
    private void updateMedicScoreboard(Player medic, MedicalMission mission) {
        Scoreboard board = medicScoreboards.get(medic.getUniqueId());
        if (board == null) return;

        Objective objective = board.getObjective("medical");
        if (objective == null) return;

        // Clear existing scores
        for (String entry : board.getEntries()) {
            board.resetScores(entry);
        }

        InjuredPlayer injured = mission.getInjuredPlayer();
        Player patient = injured.getPlayer();

        int line = 15;

        // Informations patient
        objective.getScore(ChatColor.YELLOW + "Patient:").setScore(line--);
        objective.getScore(ChatColor.WHITE + patient.getName()).setScore(line--);
        objective.getScore("").setScore(line--);

        // Cause
        objective.getScore(ChatColor.YELLOW + "Cause:").setScore(line--);
        objective.getScore(ChatColor.GRAY + injured.getInjuryCause()).setScore(line--);
        objective.getScore(" ").setScore(line--);

        // Distance
        double distance = medic.getLocation().distance(patient.getLocation());
        objective.getScore(ChatColor.YELLOW + "Distance:").setScore(line--);
        objective.getScore(ChatColor.WHITE + String.format("%.1f", distance) + "m").setScore(line--);
        objective.getScore("  ").setScore(line--);

        // Direction (flèche)
        String direction = getDirectionArrow(medic, patient);
        objective.getScore(ChatColor.YELLOW + "Direction:").setScore(line--);
        objective.getScore(direction).setScore(line--);
        objective.getScore("   ").setScore(line--);

        // Timer
        objective.getScore(ChatColor.YELLOW + "Temps restant:").setScore(line--);
        objective.getScore(ChatColor.RED + injured.getFormattedTimeLeft()).setScore(line--);
    }

    /**
     * Calcule la flèche directionnelle 360° vers le patient
     */
    private String getDirectionArrow(Player medic, Player target) {
        Location medicLoc = medic.getLocation();
        Location targetLoc = target.getLocation();

        // Calculer l'angle entre le médecin et le patient
        double dx = targetLoc.getX() - medicLoc.getX();
        double dz = targetLoc.getZ() - medicLoc.getZ();
        double angle = Math.atan2(dz, dx);

        // Convertir l'angle du joueur (yaw) en radians
        float yaw = medicLoc.getYaw();
        double playerAngle = Math.toRadians(yaw + 90); // +90 pour aligner avec l'axe X

        // Calculer la différence d'angle
        double diff = angle - playerAngle;

        // Normaliser entre -PI et PI
        while (diff > Math.PI) diff -= 2 * Math.PI;
        while (diff < -Math.PI) diff += 2 * Math.PI;

        // Convertir en degrés
        double degrees = Math.toDegrees(diff);

        // Distance pour la couleur
        double distance = medicLoc.distance(targetLoc);
        ChatColor color;
        if (distance < 10) {
            color = ChatColor.GREEN;
        } else if (distance < 50) {
            color = ChatColor.YELLOW;
        } else {
            color = ChatColor.RED;
        }

        // Choisir la flèche selon l'angle
        String arrow;
        if (degrees >= -22.5 && degrees < 22.5) {
            arrow = "↑"; // Devant
        } else if (degrees >= 22.5 && degrees < 67.5) {
            arrow = "↗"; // Avant-droite
        } else if (degrees >= 67.5 && degrees < 112.5) {
            arrow = "→"; // Droite
        } else if (degrees >= 112.5 && degrees < 157.5) {
            arrow = "↘"; // Arrière-droite
        } else if (degrees >= 157.5 || degrees < -157.5) {
            arrow = "↓"; // Derrière
        } else if (degrees >= -157.5 && degrees < -112.5) {
            arrow = "↙"; // Arrière-gauche
        } else if (degrees >= -112.5 && degrees < -67.5) {
            arrow = "←"; // Gauche
        } else {
            arrow = "↖"; // Avant-gauche
        }

        return color + "" + ChatColor.BOLD + arrow;
    }

    /**
     * Démarre le suivi en temps réel de la mission
     */
    private void startMissionTracking(MedicalMission mission) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (mission.isExpired() || mission.isCompleted()) {
                    cancel();
                    return;
                }

                Player medic = mission.getMedic();
                Player patient = mission.getInjuredPlayer().getPlayer();

                if (medic == null || !medic.isOnline() || patient == null || !patient.isOnline()) {
                    handleMissionFailed(mission);
                    cancel();
                    return;
                }

                // Mettre à jour le scoreboard du médecin
                updateMedicScoreboard(medic, mission);

                // Mettre à jour le patient sur la distance
                double distance = medic.getLocation().distance(patient.getLocation());
                String distanceMsg = ChatColor.YELLOW + "📍 Médecin à " + ChatColor.WHITE +
                        String.format("%.1fm", distance);

                patient.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(distanceMsg));

                // Vérifier si le médecin est proche pour soigner
                if (distance <= 3.0) {
                    completeMission(mission);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Toutes les secondes
    }

    /**
     * Démarre le countdown pour un joueur blessé
     */
    private void startInjuredCountdown(InjuredPlayer injured) {
        int taskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (!injuredPlayers.containsKey(injured.getPlayerUuid())) {
                    cancel();
                    return;
                }

                injured.decrementTime();

                // Mettre à jour l'armor stand avec le timer
                if (!injured.getArmorStands().isEmpty() && injured.getArmorStands().size() > 1) {
                    ArmorStand timerStand = injured.getArmorStands().get(1);
                    if (timerStand != null && timerStand.isValid()) {
                        timerStand.setCustomName(ChatColor.AQUA + injured.getFormattedTimeLeft());
                    }
                }

                // Message action bar
                Player player = injured.getPlayer();
                if (player != null && player.isOnline()) {
                    String msg = ChatColor.RED + "⏱ Temps restant: " + ChatColor.WHITE +
                            injured.getFormattedTimeLeft();

                    if (injured.hasMedic()) {
                        msg += ChatColor.GREEN + " | Médecin en route";
                    }

                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            TextComponent.fromLegacyText(msg));
                }

                // Si le temps est écoulé
                if (injured.getRemainingSeconds() <= 0) {
                    killInjuredPlayer(injured);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L).getTaskId();

        injured.setTaskId(taskId);
    }

    /**
     * Complète la mission avec succès
     */
    private void completeMission(MedicalMission mission) {
        mission.setStatus(MedicalMission.MissionStatus.COMPLETED);

        Player medic = mission.getMedic();
        Player patient = mission.getInjuredPlayer().getPlayer();
        InjuredPlayer injured = mission.getInjuredPlayer();

        // Soigner le patient
        revivePlayer(injured);

        // Facturation
        handlePayment(medic, patient);

        // Messages
        medic.sendMessage("");
        medic.sendMessage(ChatColor.GREEN + "✅ Patient sauvé avec succès !");
        medic.sendMessage(ChatColor.GOLD + "Vous avez reçu " + ChatColor.GREEN +
                (int)(medicalCost * medicShare) + "€");
        medic.sendMessage("");

        patient.sendMessage("");
        patient.sendMessage(ChatColor.GREEN + "✅ Vous avez été soigné par " + medic.getName());
        patient.sendMessage(ChatColor.YELLOW + "Coût des soins: " + ChatColor.RED + medicalCost + "€");
        patient.sendMessage("");

        // Restaurer le scoreboard du médecin
        restoreMedicScoreboard(medic);

        // Nettoyer
        cleanup(mission);
    }

    /**
     * Gère le paiement des soins
     */
    private void handlePayment(Player medic, Player patient) {
        // Retirer au patient
        economy.withdrawPlayer(patient, medicalCost);

        // Payer le médecin
        int medicPayment = (int) (medicalCost * medicShare);
        economy.depositPlayer(medic, medicPayment);

        // Payer la ville
        Town town = townManager.getPlayerTownObject(patient.getUniqueId());
        if (town != null) {
            int townPayment = medicalCost - medicPayment;
            town.deposit(townPayment);
            townManager.saveTownsNow();
        }
    }

    /**
     * Relève un joueur blessé
     */
    private void revivePlayer(InjuredPlayer injured) {
        Player player = injured.getPlayer();

        // Nettoyer
        cleanup(injured);

        // Téléporter légèrement au-dessus
        player.teleport(player.getLocation().add(0, 1, 0));

        // Restaurer la santé
        player.setHealth(4.0);

        // Retirer les effets
        player.removePotionEffect(PotionEffectType.BLINDNESS);

        // Particules
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1, 0), 20);

        // Son
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    /**
     * Tue un joueur blessé dont le temps est écoulé
     */
    private void killInjuredPlayer(InjuredPlayer injured) {
        Player player = injured.getPlayer();

        player.sendMessage("");
        player.sendMessage(ChatColor.DARK_RED + "☠ Vous avez succombé à vos blessures...");
        player.sendMessage("");

        Bukkit.broadcastMessage(ChatColor.RED + "Le joueur " + ChatColor.GRAY + player.getName() +
                ChatColor.RED + " est mort après avoir été blessé.");

        cleanup(injured);
        player.setHealth(0.0);
    }

    /**
     * Gère l'échec d'une mission
     */
    private void handleMissionFailed(MedicalMission mission) {
        mission.setStatus(MedicalMission.MissionStatus.FAILED);

        Player medic = mission.getMedic();
        if (medic != null && medic.isOnline()) {
            medic.sendMessage(ChatColor.RED + "❌ Mission échouée.");
            restoreMedicScoreboard(medic);
        }

        Player patient = mission.getInjuredPlayer().getPlayer();
        if (patient != null && patient.isOnline()) {
            patient.sendMessage(ChatColor.RED + "Le médecin n'a pas pu arriver à temps...");
        }

        cleanup(mission);
    }

    /**
     * Restaure le scoreboard précédent du médecin
     */
    private void restoreMedicScoreboard(Player medic) {
        medicScoreboards.remove(medic.getUniqueId());

        Scoreboard previousBoard = previousScoreboards.remove(medic.getUniqueId());
        if (previousBoard != null) {
            medic.setScoreboard(previousBoard);
        } else {
            medic.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    /**
     * Nettoie les données d'un joueur blessé
     */
    private void cleanup(InjuredPlayer injured) {
        // Annuler les tâches
        if (injured.getTaskId() != -1) {
            Bukkit.getScheduler().cancelTask(injured.getTaskId());
        }

        // Retirer les armor stands
        injured.clearArmorStands();

        // Retirer de la map
        injuredPlayers.remove(injured.getPlayerUuid());
    }

    /**
     * Nettoie les données d'une mission
     */
    private void cleanup(MedicalMission mission) {
        cleanup(mission.getInjuredPlayer());
        activeMissions.remove(mission.getInjuredPlayer().getPlayerUuid());
    }

    /**
     * Vérifie si un joueur est blessé
     */
    public boolean isInjured(Player player) {
        return injuredPlayers.containsKey(player.getUniqueId());
    }

    /**
     * Récupère un joueur blessé
     */
    public InjuredPlayer getInjuredPlayer(Player player) {
        return injuredPlayers.get(player.getUniqueId());
    }

    /**
     * Permet au joueur de choisir de mourir immédiatement
     */
    public void playerChooseDeath(Player player) {
        InjuredPlayer injured = injuredPlayers.get(player.getUniqueId());
        if (injured == null) {
            player.sendMessage(ChatColor.RED + "Vous n'êtes pas blessé.");
            return;
        }

        player.sendMessage(ChatColor.DARK_RED + "Vous avez choisi d'abandonner...");
        killInjuredPlayer(injured);
    }

    /**
     * Nettoie toutes les données (pour reload)
     */
    public void cleanup() {
        // Nettoyer tous les joueurs blessés
        for (InjuredPlayer injured : new ArrayList<>(injuredPlayers.values())) {
            cleanup(injured);
        }

        // Nettoyer toutes les missions
        for (MedicalMission mission : new ArrayList<>(activeMissions.values())) {
            if (mission.getAcceptanceTaskId() != -1) {
                Bukkit.getScheduler().cancelTask(mission.getAcceptanceTaskId());
            }
        }
        activeMissions.clear();

        // Restaurer tous les scoreboards
        for (UUID medicUuid : new ArrayList<>(medicScoreboards.keySet())) {
            Player medic = Bukkit.getPlayer(medicUuid);
            if (medic != null && medic.isOnline()) {
                restoreMedicScoreboard(medic);
            }
        }
        medicScoreboards.clear();
        previousScoreboards.clear();
    }

    // Getters pour la configuration
    public void setMedicalCost(int cost) {
        this.medicalCost = cost;
    }

    public void setMedicShare(double share) {
        this.medicShare = share;
    }

    public void setTownShare(double share) {
        this.townShare = share;
    }

    public void setAcceptanceTimeout(int seconds) {
        this.acceptanceTimeout = seconds;
    }

    public void setInterventionTimeout(int seconds) {
        this.interventionTimeout = seconds;
    }

    /**
     * Vérifie si un joueur a un scoreboard médical actif
     */
    public boolean hasMedicalScoreboard(Player player) {
        return medicScoreboards.containsKey(player.getUniqueId());
    }
}
