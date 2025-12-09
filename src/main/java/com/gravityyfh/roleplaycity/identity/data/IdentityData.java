package com.gravityyfh.roleplaycity.identity.data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Représente une carte d'identité virtuelle
 */
public class IdentityData {
    private final UUID playerUuid;
    private String realName;
    private String birthDate;
    private String gender;
    private String jobTitle;
    private final LocalDateTime creationDate;
    private boolean valid;

    public IdentityData(UUID playerUuid, String realName, String birthDate, String gender, String jobTitle, LocalDateTime creationDate, boolean valid) {
        this.playerUuid = playerUuid;
        this.realName = realName;
        this.birthDate = birthDate;
        this.gender = gender;
        this.jobTitle = jobTitle;
        this.creationDate = creationDate;
        this.valid = valid;
    }

    // Constructeur pour nouvelle carte
    public IdentityData(UUID playerUuid, String realName, String birthDate, String gender) {
        this(playerUuid, realName, birthDate, gender, "Chômeur", LocalDateTime.now(), true);
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getRealName() { return realName; }
    public String getBirthDate() { return birthDate; }
    public String getGender() { return gender; }
    public String getJobTitle() { return jobTitle; }
    public LocalDateTime getCreationDate() { return creationDate; }
    public boolean isValid() { return valid; }

    public void setRealName(String realName) { this.realName = realName; }
    public void setBirthDate(String birthDate) { this.birthDate = birthDate; }
    public void setGender(String gender) { this.gender = gender; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }
    public void setValid(boolean valid) { this.valid = valid; }
}
