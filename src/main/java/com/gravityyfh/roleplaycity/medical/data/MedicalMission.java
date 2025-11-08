package com.gravityyfh.roleplaycity.medical.data;

import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.util.UUID;

public class MedicalMission {
    private final UUID missionId;
    private final InjuredPlayer injuredPlayer;
    private final LocalDateTime createdTime;

    private UUID medicUuid;
    private Player medic;
    private LocalDateTime acceptedTime;
    private MissionStatus status;
    private int acceptanceTaskId = -1;

    public MedicalMission(InjuredPlayer injuredPlayer) {
        this.missionId = UUID.randomUUID();
        this.injuredPlayer = injuredPlayer;
        this.createdTime = LocalDateTime.now();
        this.status = MissionStatus.WAITING_FOR_MEDIC;
    }

    public UUID getMissionId() {
        return missionId;
    }

    public InjuredPlayer getInjuredPlayer() {
        return injuredPlayer;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public UUID getMedicUuid() {
        return medicUuid;
    }

    public Player getMedic() {
        return medic;
    }

    public void assignMedic(Player medic) {
        this.medic = medic;
        this.medicUuid = medic.getUniqueId();
        this.acceptedTime = LocalDateTime.now();
        this.status = MissionStatus.IN_PROGRESS;
        this.injuredPlayer.setAssignedMedic(medicUuid);
    }

    public LocalDateTime getAcceptedTime() {
        return acceptedTime;
    }

    public MissionStatus getStatus() {
        return status;
    }

    public void setStatus(MissionStatus status) {
        this.status = status;
    }

    public int getAcceptanceTaskId() {
        return acceptanceTaskId;
    }

    public void setAcceptanceTaskId(int taskId) {
        this.acceptanceTaskId = taskId;
    }

    public boolean isExpired() {
        return status == MissionStatus.EXPIRED || status == MissionStatus.FAILED;
    }

    public boolean isCompleted() {
        return status == MissionStatus.COMPLETED;
    }

    public enum MissionStatus {
        WAITING_FOR_MEDIC,    // En attente qu'un médecin accepte
        IN_PROGRESS,          // Médecin en route
        COMPLETED,            // Patient soigné
        FAILED,               // Médecin n'est pas arrivé à temps
        EXPIRED,              // Aucun médecin n'a accepté
        CANCELLED             // Mission annulée (déconnexion, etc.)
    }
}
