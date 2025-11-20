package com.gravityyfh.roleplaycity.medical.manager;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.medical.data.HealingProcess;
import com.gravityyfh.roleplaycity.medical.data.InjuredPlayer;
import com.gravityyfh.roleplaycity.medical.data.MedicalMission;
import com.gravityyfh.roleplaycity.medical.gui.HealingMiniGameGUI;
import com.gravityyfh.roleplaycity.medical.service.MedicalPersistenceService;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownMember;
import com.gravityyfh.roleplaycity.town.data.TownRole;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import net.md_5.bungee.api.ChatMessageType;
import net.milkbowl.vault.economy.Economy;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import net.md_5.bungee.api.chat.HoverEvent;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MedicalSystemManager {
    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final Economy economy;
    private final MedicalPersistenceService persistenceService;

    // Stockage des joueurs blessés et missions
    private final Map<UUID, InjuredPlayer> injuredPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, MedicalMission> activeMissions = new ConcurrentHashMap<>();
    private final Map<UUID, Scoreboard> medicScoreboards = new ConcurrentHashMap<>();
    private final Map<UUID, Scoreboard> previousScoreboards = new ConcurrentHashMap<>();
    private final Map<UUID, HealingProcess> activeHealings = new ConcurrentHashMap<>();
    private final Map<UUID, HealingMiniGameGUI> activeMiniGames = new ConcurrentHashMap<>();

    // Configuration
    private int medicalCost = 250;
    private double medicShare = 0.80; 
    private double townShare = 0.20;  
    private int acceptanceTimeout = 30; 
    private int interventionTimeout = 300; 

    public MedicalSystemManager(RoleplayCity plugin, MedicalPersistenceService persistenceService) {
        this.plugin = plugin;
        this.townManager = plugin.getTownManager();
        this.economy = RoleplayCity.getEconomy();
        this.persistenceService = persistenceService;

        // Charger les blessés depuis la DB
        injuredPlayers.putAll(persistenceService.loadInjuredPlayers());

        // Vérifier s'il y a des joueurs à restaurer après redémarrage
        handleServerRestart();
    }

    /**
     * Gère la restauration des joueurs après un redémarrage serveur
     */
    private void handleServerRestart() {
        if (persistenceService.wasServerRestart()) {
            // C'était un redémarrage serveur
            // On va attendre que les joueurs se reconnectent pour les nettoyer
            plugin.getLogger().info("Détection d'un redémarrage serveur - Les joueurs blessés seront relevés automatiquement");

            // Nettoyer le flag après 5 secondes (laisser le temps au serveur de charger)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                persistenceService.clearServerRestartFlag();
            }, 100L); // 5 secondes
        } else {
            // C'était un démarrage normal ou crash
            // Nettoyer toutes les données obsolètes (si ce n'était pas un arrêt propre)
            // Note: loadInjuredPlayers a déjà chargé les "treated=0".
            // Si on veut forcer le nettoyage si crash:
            // persistenceService.clearAllInjured(); // Risqué si crash et joueurs reviennent
        }
    }

    /**
     * Appelé quand un joueur se connecte
     * Gère uniquement la restauration après redémarrage serveur
     */
    public void onPlayerJoin(Player player) {
        UUID playerUuid = player.getUniqueId();

        // Vérifier si le joueur était blessé
        if (injuredPlayers.containsKey(playerUuid)) {
            // Si le serveur a redémarré proprement (flag actif au début, puis clear)
            // Ou si on décide de toujours relever à la connexion:
            
            // Logique: Si le joueur est dans la DB comme blessé, c'est qu'il a quitté blessé ou crash.
            // On le relève pour éviter les bugs de death loop.
            restorePlayerAfterRestart(player);

            // Nettoyer la persistance
            persistenceService.deleteInjuredPlayer(playerUuid);
            injuredPlayers.remove(playerUuid);
        }
    }

    /**
     * Relève un joueur après un redémarrage serveur
     */
    private void restorePlayerAfterRestart(Player player) {
        // Nettoyer tous les effets de potion
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        // Remettre la vie à 20
        player.setHealth(20.0);

        // S'assurer que le joueur peut bouger
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);

        // Message d'information
        player.sendMessage(ChatColor.GREEN + "✓ Vous avez été automatiquement relevé.");

        plugin.getLogger().info("Joueur " + player.getName() + " relevé automatiquement");
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

        // Sauvegarder dans la DB
        persistenceService.saveInjuredPlayer(injured);

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

                // Vérifier si le médecin est proche pour afficher l'instruction
                if (distance <= 3.0 && !activeHealings.containsKey(medic.getUniqueId())) {
                    medic.sendMessage(ChatColor.GREEN + "✋ Maintenez " + ChatColor.YELLOW + ChatColor.BOLD + "SHIFT" +
                            ChatColor.GREEN + " et " + ChatColor.YELLOW + ChatColor.BOLD + "CLIC DROIT" +
                            ChatColor.GREEN + " sur le patient pour commencer les soins !");
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
     * Démarre le processus de soin progressif
     */
    public void startHealingProcess(Player medic, Player patient) {
        // Vérifier que le médecin a une mission active
        MedicalMission mission = activeMissions.get(patient.getUniqueId());
        if (mission == null || !mission.getMedic().equals(medic)) {
            return;
        }

        // Vérifier la distance
        if (medic.getLocation().distance(patient.getLocation()) > 3.0) {
            medic.sendMessage(ChatColor.RED + "❌ Vous êtes trop loin du patient !");
            return;
        }

        // Vérifier si un processus n'est pas déjà en cours
        if (activeHealings.containsKey(medic.getUniqueId())) {
            return;
        }

        // Créer la Boss Bar de progression
        BossBar progressBar = Bukkit.createBossBar(
                ChatColor.GREEN + "🩺 Soins en cours...",
                BarColor.GREEN,
                BarStyle.SOLID
        );
        progressBar.addPlayer(medic);
        progressBar.addPlayer(patient);
        progressBar.setProgress(0.0);

        // Créer le processus de soin
        HealingProcess healing = new HealingProcess(medic, patient, progressBar);
        activeHealings.put(medic.getUniqueId(), healing);

        // Messages de démarrage
        medic.sendMessage(ChatColor.GREEN + "🏥 Vous commencez les soins...");
        patient.sendMessage(ChatColor.GREEN + "🏥 Le médecin commence à vous soigner !");

        // Son de début
        medic.playSound(medic.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        patient.playSound(patient.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);

        // Démarrer le processus progressif
        int taskId = new BukkitRunnable() {
            int particleTick = 0;

            @Override
            public void run() {
                // Vérifier si le processus existe toujours
                if (!activeHealings.containsKey(medic.getUniqueId())) {
                    cancel();
                    return;
                }

                // Vérifier si les joueurs sont toujours en ligne
                if (!medic.isOnline() || !patient.isOnline()) {
                    interruptHealing(healing, "Un joueur s'est déconnecté");
                    cancel();
                    return;
                }

                // Vérifier la distance
                double distance = medic.getLocation().distance(patient.getLocation());
                if (distance > 3.0) {
                    interruptHealing(healing, "Le médecin s'est éloigné");
                    cancel();
                    return;
                }

                // Vérifier si les soins sont en pause (mini-jeu en cours)
                if (healing.isPaused()) {
                    // Ne pas incrémenter, juste attendre
                    return;
                }

                // Incrémenter la progression
                healing.incrementProgress();
                double progress = healing.getProgressPercentage();
                progressBar.setProgress(Math.min(progress, 1.0));

                // Vérifier si un mini-jeu doit être lancé
                int minigameDifficulty = healing.shouldStartMinigame();
                if (minigameDifficulty > 0) {
                    // Mettre en pause les soins
                    healing.setPaused(true);

                    // Créer et lancer le mini-jeu
                    HealingMiniGameGUI minigame = new HealingMiniGameGUI(
                        medic,
                        minigameDifficulty,
                        () -> {
                            // Callback quand le mini-jeu est réussi
                            healing.setPaused(false);
                            medic.sendMessage(ChatColor.GREEN + "✓ Les soins reprennent...");
                        },
                        () -> {
                            // Callback si le mini-jeu échoue
                            interruptHealing(healing, "Mini-jeu échoué");
                        }
                    );

                    // Stocker le mini-jeu actif
                    activeMiniGames.put(medic.getUniqueId(), minigame);

                    // Ouvrir le GUI
                    minigame.open();

                    // Ne pas continuer cette itération
                    return;
                }

                // Message RP
                String rpMessage = healing.getRPMessage();
                progressBar.setTitle(ChatColor.GREEN + rpMessage);

                // Messages d'action spécifiques pour le médecin
                String medicAction = healing.getMedicAction();
                if (medicAction != null) {
                    medic.sendMessage(medicAction);
                    medic.playSound(medic.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
                }

                // Messages pour le patient
                String patientMessage = healing.getPatientMessage();
                if (patientMessage != null) {
                    patient.sendMessage(patientMessage);
                }

                // Action bar pour le médecin
                int percentage = (int) (progress * 100);
                medic.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(ChatColor.GREEN + "Soins: " + ChatColor.YELLOW + percentage + "% " +
                                ChatColor.GRAY + "| Distance: " + ChatColor.WHITE + String.format("%.1fm", distance)));

                // Action bar pour le patient
                patient.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(ChatColor.GREEN + "Le médecin vous soigne... " +
                                ChatColor.YELLOW + percentage + "%"));

                // Effets visuels toutes les 5 ticks (0.25s)
                particleTick++;
                if (particleTick % 5 == 0) {
                    // Particules de cœur
                    patient.getWorld().spawnParticle(
                            Particle.HEART,
                            patient.getLocation().add(0, 1.5, 0),
                            2,
                            0.3, 0.3, 0.3,
                            0.05
                    );

                    // Particules vertes autour du médecin
                    medic.getWorld().spawnParticle(
                            Particle.VILLAGER_HAPPY,
                            medic.getLocation().add(0, 1, 0),
                            3,
                            0.3, 0.5, 0.3,
                            0.05
                    );
                }

                // Son de progression (bip)
                if (healing.getProgressSeconds() % 3 == 0) {
                    float pitch = 1.0f + (healing.getProgressSeconds() * 0.05f); // Le son s'accélère
                    medic.playSound(medic.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, pitch);
                    patient.playSound(patient.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, pitch);
                }

                // Vérifier si le processus est terminé
                if (healing.isCompleted()) {
                    completeHealing(healing, mission);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L).getTaskId(); // Toutes les secondes

        healing.setTaskId(taskId);
    }

    /**
     * Interrompt un processus de soin
     */
    private void interruptHealing(HealingProcess healing, String reason) {
        healing.setInterrupted(true);

        // Annuler la tâche
        if (healing.getTaskId() != -1) {
            Bukkit.getScheduler().cancelTask(healing.getTaskId());
        }

        // Retirer la boss bar
        healing.getProgressBar().removeAll();

        // Messages
        healing.getMedic().sendMessage(ChatColor.RED + "⚠ Soins interrompus ! " + reason);
        healing.getPatient().sendMessage(ChatColor.RED + "⚠ Les soins ont été interrompus...");

        // Son d'échec
        healing.getMedic().playSound(healing.getMedic().getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);

        // Nettoyer
        activeHealings.remove(healing.getMedicUuid());
    }

    /**
     * Complète le processus de soin
     */
    private void completeHealing(HealingProcess healing, MedicalMission mission) {
        healing.setCompleted(true);

        // Retirer la boss bar
        healing.getProgressBar().removeAll();

        // Nettoyer le processus de soin
        activeHealings.remove(healing.getMedicUuid());

        // Son de succès
        healing.getMedic().playSound(healing.getMedic().getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        healing.getPatient().playSound(healing.getPatient().getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

        // Grosses particules de succès
        healing.getPatient().getWorld().spawnParticle(
                Particle.HEART,
                healing.getPatient().getLocation().add(0, 1, 0),
                20,
                0.5, 0.5, 0.5,
                0.1
        );

        healing.getPatient().getWorld().spawnParticle(
                Particle.VILLAGER_HAPPY,
                healing.getPatient().getLocation().add(0, 1, 0),
                30,
                0.5, 0.5, 0.5,
                0.1
        );

        // Compléter la mission
        completeMission(mission);
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

        // Enregistrer le traitement en base
        Town medicTown = townManager.getPlayerTownObject(medic.getUniqueId());
        String townName = medicTown != null ? medicTown.getName() : null;
        persistenceService.recordTreatment(
            patient.getUniqueId(), patient.getName(),
            medic.getUniqueId(), medic.getName(),
            medicalCost, townName
        );
        
        // Marquer comme traité en base (pour ne pas le recharger au redémarrage)
        persistenceService.markTreated(injured.getPlayerUuid(), medic.getUniqueId(), medic.getName());

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

        // Supprimer de la DB
        persistenceService.deleteInjuredPlayer(injured.getPlayerUuid());

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

        // Retirer de la map (Déjà retiré de la DB par l'appelant si nécessaire)
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
     * Récupère le mini-jeu actif d'un médecin
     */
    public HealingMiniGameGUI getActiveMiniGame(Player medic) {
        return activeMiniGames.get(medic.getUniqueId());
    }

    /**
     * Retire le mini-jeu actif d'un médecin
     */
    public void removeActiveMiniGame(Player medic) {
        activeMiniGames.remove(medic.getUniqueId());
    }

    /**
     * Relève un joueur blessé lors d'un kick (arrêt serveur, etc.)
     * Le joueur est relevé complètement au lieu d'être tué
     */
    public void revivePlayerOnKick(Player player) {
        InjuredPlayer injured = injuredPlayers.get(player.getUniqueId());
        if (injured == null) {
            return; // Pas blessé
        }

        // Nettoyer TOUS les effets de potion
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOW);
        player.removePotionEffect(PotionEffectType.SLOW_DIGGING);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        // Restaurer la mobilité
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);

        // Restaurer la santé complètement
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);

        // Nettoyer les armor stands
        injured.clearArmorStands();

        // Annuler les missions et processus de soin
        MedicalMission mission = activeMissions.get(player.getUniqueId());
        if (mission != null) {
            if (mission.getAcceptanceTaskId() != -1) {
                Bukkit.getScheduler().cancelTask(mission.getAcceptanceTaskId());
            }
            activeMissions.remove(player.getUniqueId());
        }

        HealingProcess healing = activeHealings.get(player.getUniqueId());
        if (healing != null) {
            if (healing.getTaskId() != -1) {
                Bukkit.getScheduler().cancelTask(healing.getTaskId());
            }
            healing.getProgressBar().removeAll();
            activeHealings.remove(player.getUniqueId());
        }

        // Annuler les timers
        if (injured.getTaskId() != -1) {
            Bukkit.getScheduler().cancelTask(injured.getTaskId());
        }

        // Nettoyer la persistance et les maps
        persistenceService.deleteInjuredPlayer(injured.getPlayerUuid());
        injuredPlayers.remove(injured.getPlayerUuid());

        // Message au joueur
        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "✓ Vous avez été relevé suite à l'arrêt du serveur.");
        player.sendMessage(ChatColor.GRAY + "Vous pourrez vous reconnecter normalement.");
        player.sendMessage("");

        plugin.getLogger().info("Joueur " + player.getName() + " relevé lors du kick (arrêt serveur)");
    }

    /**
     * Gère la déconnexion d'un joueur blessé
     * Nettoie tout, restaure la mobilité, puis tue le joueur
     * Note: Si le serveur s'arrête, cleanup() relève le joueur AVANT que cette méthode soit appelée
     */
    public void handlePlayerDisconnect(Player player) {
        InjuredPlayer injured = injuredPlayers.get(player.getUniqueId());
        if (injured == null) {
            return; // Pas blessé, rien à faire (ou déjà relevé par cleanup)
        }

        // ÉTAPE 1 : Nettoyer les effets de potion pour qu'il puisse bouger
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOW);
        player.removePotionEffect(PotionEffectType.SLOW_DIGGING);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        // ÉTAPE 2 : Restaurer la vitesse de marche (le joueur peut maintenant bouger)
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);

        // ÉTAPE 3 : Nettoyer les armor stands
        injured.clearArmorStands();

        // ÉTAPE 4 : Nettoyer les missions actives
        MedicalMission mission = activeMissions.get(player.getUniqueId());
        if (mission != null) {
            if (mission.getAcceptanceTaskId() != -1) {
                Bukkit.getScheduler().cancelTask(mission.getAcceptanceTaskId());
            }
            activeMissions.remove(player.getUniqueId());
        }

        // ÉTAPE 5 : Nettoyer le processus de soin si en cours
        HealingProcess healing = activeHealings.get(player.getUniqueId());
        if (healing != null) {
            if (healing.getTaskId() != -1) {
                Bukkit.getScheduler().cancelTask(healing.getTaskId());
            }
            healing.getProgressBar().removeAll();
            activeHealings.remove(player.getUniqueId());
        }

        // ÉTAPE 6 : Annuler les tâches du joueur blessé
        if (injured.getTaskId() != -1) {
            Bukkit.getScheduler().cancelTask(injured.getTaskId());
        }

        // ÉTAPE 7 : Nettoyer de la persistance et des maps
        persistenceService.deleteInjuredPlayer(injured.getPlayerUuid());
        injuredPlayers.remove(injured.getPlayerUuid());

        // ÉTAPE 8 : Maintenant que tout est nettoyé et qu'il peut bouger, le tuer
        player.setHealth(0.0);

        plugin.getLogger().info("Joueur " + player.getName() + " tué après nettoyage (déconnexion en état blessé)");
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
     * Nettoie toutes les données (pour reload ou arrêt serveur)
     * IMPORTANT: Relève automatiquement tous les joueurs blessés avant de nettoyer
     */
    public void cleanup() {
        // Marquer l'arrêt serveur pour restauration au reboot si crash (optionnel car on relève ici)
        persistenceService.markServerShutdown();

        // Nettoyer tous les processus de soin en cours
        for (HealingProcess healing : new ArrayList<>(activeHealings.values())) {
            if (healing.getTaskId() != -1) {
                Bukkit.getScheduler().cancelTask(healing.getTaskId());
            }
            healing.getProgressBar().removeAll();
        }
        activeHealings.clear();

        // RELEVER tous les joueurs blessés AVANT la fermeture du serveur
        for (InjuredPlayer injured : new ArrayList<>(injuredPlayers.values())) {
            Player player = injured.getPlayer();
            if (player != null && player.isOnline()) {
                // Nettoyer les effets
                player.removePotionEffect(PotionEffectType.BLINDNESS);
                player.removePotionEffect(PotionEffectType.SLOW);
                player.removePotionEffect(PotionEffectType.SLOW_DIGGING);
                for (PotionEffect effect : player.getActivePotionEffects()) {
                    player.removePotionEffect(effect.getType());
                }

                // Restaurer la mobilité
                player.setWalkSpeed(0.2f);
                player.setFlySpeed(0.1f);

                // Restaurer la santé complètement
                player.setHealth(20.0);
                player.setFoodLevel(20);
                player.setSaturation(20.0f);

                // Message au joueur
                player.sendMessage("");
                player.sendMessage(ChatColor.GREEN + "✓ Vous avez été relevé suite à l'arrêt du serveur.");
                player.sendMessage(ChatColor.GRAY + "Vous pourrez vous reconnecter normalement.");
                player.sendMessage("");

                plugin.getLogger().info("Joueur " + player.getName() + " relevé avant l'arrêt du serveur");
            }

            // Nettoyer les données
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

    /**
     * Recharge la configuration du système médical depuis config.yml
     * Appelé par /roleplaycity reload
     */
    public void reloadConfig() {
        // Recharger les paramètres de configuration
        FileConfiguration config = plugin.getConfig();

        // Recharger les coûts et délais depuis la config si elle existe
        if (config.contains("medical.cost")) {
            this.medicalCost = config.getInt("medical.cost", 500);
        }
        if (config.contains("medical.medic-share")) {
            this.medicShare = config.getDouble("medical.medic-share", 0.60);
        }
        if (config.contains("medical.town-share")) {
            this.townShare = config.getDouble("medical.town-share", 0.20);
        }
        if (config.contains("medical.acceptance-timeout")) {
            this.acceptanceTimeout = config.getInt("medical.acceptance-timeout", 30);
        }
        if (config.contains("medical.intervention-timeout")) {
            this.interventionTimeout = config.getInt("medical.intervention-timeout", 300);
        }

        plugin.getLogger().info("Configuration du système médical rechargée:");
        plugin.getLogger().info("  - Coût des soins: " + medicalCost + "€");
        plugin.getLogger().info("  - Part médecin: " + (medicShare * 100) + "%");
        plugin.getLogger().info("  - Part ville: " + (townShare * 100) + "%");
        plugin.getLogger().info("  - Timeout acceptation: " + acceptanceTimeout + "s");
        plugin.getLogger().info("  - Timeout intervention: " + interventionTimeout + "s");
    }
}