package com.gravityyfh.roleplaycity.heist.data;

import com.gravityyfh.roleplaycity.town.data.PlotType;
import org.bukkit.Location;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Représente un cambriolage en cours ou terminé
 */
public class Heist {

    private final UUID heistId;
    private final String townName;
    private final String plotKey; // Format: "world:chunkX:chunkZ"
    private final PlotType targetType; // PARTICULIER ou PROFESSIONNEL

    // Joueurs impliqués
    private final UUID initiatorUuid; // Celui qui a posé la bombe
    private final Map<UUID, HeistParticipant> participants;

    // Localisation
    private final Location bombLocation;
    private UUID bombEntityId; // Entité de la bombe (ArmorStand ou autre)
    private List<UUID> hologramEntityIds; // Lignes d'hologramme

    // État du cambriolage
    private HeistPhase phase;
    private HeistResult result;
    private final LocalDateTime startTime;
    private LocalDateTime explosionTime; // null si pas encore explosé
    private LocalDateTime endTime;

    // Configuration (copiée depuis cambriolage.yml au moment de la création)
    private final int countdownSeconds;
    private final int robberySeconds;
    private final float explosionPower;
    private final boolean breakBlocks;
    private final boolean damageEntities;

    // Statistiques
    private final List<UUID> arrestedPlayers;
    private final List<UUID> killedPlayers;
    private int totalChestsOpened;
    private int totalItemsStolen;
    private double totalValueStolen;

    // Flag pour indiquer que l'explosion initiale est en cours
    // Permet au listener de savoir qu'il doit autoriser la casse des blocs
    private boolean isExploding = false;

    // Puzzle désamorçage
    private int correctWireIndex; // Index du bon fil (0-4)
    private int defuseAttempts;
    private int maxDefuseAttempts;

    public Heist(UUID initiatorUuid, String townName, String plotKey, PlotType targetType,
                 Location bombLocation, int countdownSeconds, int robberySeconds,
                 float explosionPower, boolean breakBlocks, boolean damageEntities,
                 int maxDefuseAttempts) {
        this.heistId = UUID.randomUUID();
        this.initiatorUuid = initiatorUuid;
        this.townName = townName;
        this.plotKey = plotKey;
        this.targetType = targetType;
        this.bombLocation = bombLocation.clone();

        this.participants = new HashMap<>();
        this.phase = HeistPhase.COUNTDOWN;
        this.result = null;
        this.startTime = LocalDateTime.now();
        this.explosionTime = null;
        this.endTime = null;

        this.countdownSeconds = countdownSeconds;
        this.robberySeconds = robberySeconds;
        this.explosionPower = explosionPower;
        this.breakBlocks = breakBlocks;
        this.damageEntities = damageEntities;

        this.arrestedPlayers = new ArrayList<>();
        this.killedPlayers = new ArrayList<>();
        this.totalChestsOpened = 0;
        this.totalItemsStolen = 0;
        this.totalValueStolen = 0.0;

        // Générer le bon fil aléatoirement (0-4 pour 5 fils)
        this.correctWireIndex = new Random().nextInt(5);
        this.defuseAttempts = 0;
        this.maxDefuseAttempts = maxDefuseAttempts;

        this.hologramEntityIds = new ArrayList<>();
    }

    // === GESTION DES PARTICIPANTS ===

    /**
     * Ajoute un participant au cambriolage
     */
    public void addParticipant(UUID playerUuid, String playerName, boolean isInitiator) {
        if (!participants.containsKey(playerUuid)) {
            participants.put(playerUuid, new HeistParticipant(playerUuid, playerName, isInitiator));
        }
    }

    /**
     * Vérifie si un joueur est participant
     */
    public boolean isParticipant(UUID playerUuid) {
        return participants.containsKey(playerUuid);
    }

    /**
     * Récupère un participant
     */
    public HeistParticipant getParticipant(UUID playerUuid) {
        return participants.get(playerUuid);
    }

    /**
     * @return Nombre de participants actifs (peuvent encore agir)
     */
    public int getActiveParticipantCount() {
        return (int) participants.values().stream()
            .filter(HeistParticipant::canAct)
            .count();
    }

    /**
     * @return true si tous les participants sont hors-jeu
     */
    public boolean areAllParticipantsOutOfGame() {
        return participants.values().stream().allMatch(HeistParticipant::isOutOfGame);
    }

    // === GESTION DU TEMPS ===

    /**
     * @return Secondes restantes avant explosion (phase COUNTDOWN)
     */
    public long getCountdownRemainingSeconds() {
        if (phase != HeistPhase.COUNTDOWN) return 0;

        LocalDateTime explosionDeadline = startTime.plusSeconds(countdownSeconds);
        long seconds = Duration.between(LocalDateTime.now(), explosionDeadline).getSeconds();
        return Math.max(0, seconds);
    }

    /**
     * @return Secondes restantes pour le vol (phase ROBBERY)
     */
    public long getRobberyRemainingSeconds() {
        if (phase != HeistPhase.ROBBERY || explosionTime == null) return 0;

        LocalDateTime robberyDeadline = explosionTime.plusSeconds(robberySeconds);
        long seconds = Duration.between(LocalDateTime.now(), robberyDeadline).getSeconds();
        return Math.max(0, seconds);
    }

    /**
     * @return Temps restant formaté (ex: "3m 45s")
     */
    public String getFormattedRemainingTime() {
        long totalSeconds = (phase == HeistPhase.COUNTDOWN)
            ? getCountdownRemainingSeconds()
            : getRobberyRemainingSeconds();

        if (totalSeconds <= 0) return "0s";

        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        }
        return String.format("%ds", seconds);
    }

    /**
     * Vérifie si le countdown est terminé
     */
    public boolean isCountdownExpired() {
        return getCountdownRemainingSeconds() <= 0;
    }

    /**
     * Vérifie si le temps de vol est terminé
     */
    public boolean isRobberyExpired() {
        return getRobberyRemainingSeconds() <= 0;
    }

    // === TRANSITIONS D'ÉTAT ===

    /**
     * Passe en phase explosion/vol
     */
    public void triggerExplosion() {
        this.phase = HeistPhase.ROBBERY;
        this.explosionTime = LocalDateTime.now();
    }

    /**
     * Marque le début de l'explosion (pour autoriser la casse des blocs)
     */
    public void setExploding(boolean exploding) {
        this.isExploding = exploding;
    }

    /**
     * @return true si l'explosion initiale est en cours
     */
    public boolean isExploding() {
        return isExploding;
    }

    /**
     * Termine le cambriolage
     */
    public void end(HeistResult result) {
        this.phase = HeistPhase.ENDED;
        this.result = result;
        this.endTime = LocalDateTime.now();
    }

    /**
     * Marque la bombe comme désamorcée
     */
    public void defuse() {
        end(HeistResult.DEFUSED);
    }

    // === PUZZLE DÉSAMORÇAGE ===

    /**
     * Tente de couper un fil
     * @param wireIndex index du fil coupé (0-4)
     * @return true si c'est le bon fil
     */
    public boolean attemptDefuse(int wireIndex) {
        defuseAttempts++;
        return wireIndex == correctWireIndex;
    }

    /**
     * @return true si le nombre max de tentatives est atteint
     */
    public boolean hasExceededDefuseAttempts() {
        return defuseAttempts >= maxDefuseAttempts;
    }

    // === STATISTIQUES ===

    /**
     * Enregistre un joueur arrêté
     */
    public void recordArrest(UUID playerUuid) {
        if (!arrestedPlayers.contains(playerUuid)) {
            arrestedPlayers.add(playerUuid);
        }
        HeistParticipant participant = participants.get(playerUuid);
        if (participant != null) {
            participant.setArrested();
        }
    }

    /**
     * Enregistre un joueur tué
     */
    public void recordDeath(UUID playerUuid) {
        if (!killedPlayers.contains(playerUuid)) {
            killedPlayers.add(playerUuid);
        }
        HeistParticipant participant = participants.get(playerUuid);
        if (participant != null) {
            participant.setDead();
        }
    }

    /**
     * Ajoute des items volés au total
     */
    public void addStolenItems(UUID playerUuid, int count, double value) {
        this.totalItemsStolen += count;
        this.totalValueStolen += value;
        HeistParticipant participant = participants.get(playerUuid);
        if (participant != null) {
            participant.addStolenItems(count);
        }
    }

    /**
     * Incrémente le compteur de coffres ouverts
     */
    public void recordChestOpened(UUID playerUuid) {
        this.totalChestsOpened++;
        HeistParticipant participant = participants.get(playerUuid);
        if (participant != null) {
            participant.incrementChestsOpened();
        }
    }

    // === GETTERS ===

    public UUID getHeistId() {
        return heistId;
    }

    public String getTownName() {
        return townName;
    }

    public String getPlotKey() {
        return plotKey;
    }

    public PlotType getTargetType() {
        return targetType;
    }

    public UUID getInitiatorUuid() {
        return initiatorUuid;
    }

    public Map<UUID, HeistParticipant> getParticipants() {
        return Collections.unmodifiableMap(participants);
    }

    public Location getBombLocation() {
        return bombLocation.clone();
    }

    public UUID getBombEntityId() {
        return bombEntityId;
    }

    public void setBombEntityId(UUID bombEntityId) {
        this.bombEntityId = bombEntityId;
    }

    public List<UUID> getHologramEntityIds() {
        return hologramEntityIds;
    }

    public void setHologramEntityIds(List<UUID> hologramEntityIds) {
        this.hologramEntityIds = hologramEntityIds;
    }

    public HeistPhase getPhase() {
        return phase;
    }

    public HeistResult getResult() {
        return result;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getExplosionTime() {
        return explosionTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public int getCountdownSeconds() {
        return countdownSeconds;
    }

    public int getRobberySeconds() {
        return robberySeconds;
    }

    public float getExplosionPower() {
        return explosionPower;
    }

    public boolean shouldBreakBlocks() {
        return breakBlocks;
    }

    public boolean shouldDamageEntities() {
        return damageEntities;
    }

    public List<UUID> getArrestedPlayers() {
        return Collections.unmodifiableList(arrestedPlayers);
    }

    public List<UUID> getKilledPlayers() {
        return Collections.unmodifiableList(killedPlayers);
    }

    public int getTotalChestsOpened() {
        return totalChestsOpened;
    }

    public int getTotalItemsStolen() {
        return totalItemsStolen;
    }

    public double getTotalValueStolen() {
        return totalValueStolen;
    }

    public int getCorrectWireIndex() {
        return correctWireIndex;
    }

    public int getDefuseAttempts() {
        return defuseAttempts;
    }

    public int getMaxDefuseAttempts() {
        return maxDefuseAttempts;
    }

    /**
     * @return true si le heist est en cours (pas terminé)
     */
    public boolean isActive() {
        return phase != HeistPhase.ENDED;
    }
}
