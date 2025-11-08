package com.gravityyfh.roleplaycity.medical.data;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class InjuredPlayer {
    private final UUID playerUuid;
    private final Player player;
    private final Location injuryLocation;
    private final String injuryCause;
    private final LocalDateTime injuryTime;
    private final List<ArmorStand> armorStands;
    private final boolean canAffordCare;

    private UUID assignedMedicUuid;
    private LocalDateTime medicAssignedTime;
    private int remainingSeconds;
    private int taskId = -1;

    public InjuredPlayer(Player player, String injuryCause, boolean canAffordCare) {
        this.playerUuid = player.getUniqueId();
        this.player = player;
        this.injuryLocation = player.getLocation().clone();
        this.injuryCause = injuryCause;
        this.injuryTime = LocalDateTime.now();
        this.armorStands = new ArrayList<>();
        this.canAffordCare = canAffordCare;
        this.remainingSeconds = 300; // 5 minutes
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public Player getPlayer() {
        return player;
    }

    public Location getInjuryLocation() {
        return injuryLocation;
    }

    public String getInjuryCause() {
        return injuryCause;
    }

    public LocalDateTime getInjuryTime() {
        return injuryTime;
    }

    public List<ArmorStand> getArmorStands() {
        return armorStands;
    }

    public void addArmorStand(ArmorStand stand) {
        this.armorStands.add(stand);
    }

    public void clearArmorStands() {
        for (ArmorStand stand : armorStands) {
            stand.remove();
        }
        armorStands.clear();
    }

    public boolean canAffordCare() {
        return canAffordCare;
    }

    public UUID getAssignedMedicUuid() {
        return assignedMedicUuid;
    }

    public void setAssignedMedic(UUID medicUuid) {
        this.assignedMedicUuid = medicUuid;
        this.medicAssignedTime = LocalDateTime.now();
    }

    public boolean hasMedic() {
        return assignedMedicUuid != null;
    }

    public LocalDateTime getMedicAssignedTime() {
        return medicAssignedTime;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public void decrementTime() {
        if (remainingSeconds > 0) {
            remainingSeconds--;
        }
    }

    public int getTaskId() {
        return taskId;
    }

    public void setTaskId(int taskId) {
        this.taskId = taskId;
    }

    public String getFormattedTimeLeft() {
        int minutes = remainingSeconds / 60;
        int seconds = remainingSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
}
