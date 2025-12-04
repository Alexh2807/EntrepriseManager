package com.gravityyfh.roleplaycity.service;

import java.util.UUID;

/**
 * Données de session pour un service professionnel actif
 */
public class ProfessionalServiceData {

    private final UUID playerUUID;
    private final String playerName;
    private final ProfessionalServiceType serviceType;
    private final String townName;
    private final String sexe;
    private final String skinApplied;
    private final long startTime;

    // Pour les entreprises uniquement
    private final String enterpriseName;

    // Timestamp de dernière déconnexion (pour timeout automatique)
    private Long lastDisconnectTime;

    /**
     * Constructeur pour services municipaux (Police, Medical, Judge)
     */
    public ProfessionalServiceData(UUID playerUUID, String playerName, ProfessionalServiceType serviceType,
                                    String townName, String sexe, String skinApplied) {
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.serviceType = serviceType;
        this.townName = townName;
        this.sexe = sexe;
        this.skinApplied = skinApplied;
        this.startTime = System.currentTimeMillis();
        this.enterpriseName = null;
    }

    /**
     * Constructeur pour service entreprise
     */
    public ProfessionalServiceData(UUID playerUUID, String playerName, String enterpriseName) {
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.serviceType = ProfessionalServiceType.ENTERPRISE;
        this.townName = null;
        this.sexe = null;
        this.skinApplied = null;
        this.startTime = System.currentTimeMillis();
        this.enterpriseName = enterpriseName;
    }

    /**
     * Constructeur avec startTime personnalisé (pour restauration depuis DB)
     */
    public ProfessionalServiceData(UUID playerUUID, String playerName, ProfessionalServiceType serviceType,
                                    String townName, String sexe, String skinApplied, long startTime,
                                    String enterpriseName) {
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.serviceType = serviceType;
        this.townName = townName;
        this.sexe = sexe;
        this.skinApplied = skinApplied;
        this.startTime = startTime;
        this.enterpriseName = enterpriseName;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public String getPlayerName() {
        return playerName;
    }

    public ProfessionalServiceType getServiceType() {
        return serviceType;
    }

    public String getTownName() {
        return townName;
    }

    public String getSexe() {
        return sexe;
    }

    public String getSkinApplied() {
        return skinApplied;
    }

    public long getStartTime() {
        return startTime;
    }

    public String getEnterpriseName() {
        return enterpriseName;
    }

    /**
     * Retourne la durée du service en millisecondes
     */
    public long getDuration() {
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Retourne le nom d'affichage du lieu de service
     */
    public String getLocationName() {
        if (serviceType == ProfessionalServiceType.ENTERPRISE) {
            return enterpriseName != null ? enterpriseName : "Entreprise";
        }
        return townName != null ? townName : "Inconnu";
    }

    /**
     * Vérifie si ce service est pour une ville spécifique
     */
    public boolean isForTown(String town) {
        return townName != null && townName.equalsIgnoreCase(town);
    }

    /**
     * Vérifie si ce service est pour une entreprise spécifique
     */
    public boolean isForEnterprise(String enterprise) {
        return enterpriseName != null && enterpriseName.equalsIgnoreCase(enterprise);
    }

    /**
     * Retourne le timestamp de dernière déconnexion
     */
    public Long getLastDisconnectTime() {
        return lastDisconnectTime;
    }

    /**
     * Définit le timestamp de dernière déconnexion
     */
    public void setLastDisconnectTime(Long lastDisconnectTime) {
        this.lastDisconnectTime = lastDisconnectTime;
    }

    /**
     * Vérifie si le service a expiré (déconnexion > délai spécifié)
     * @param timeoutMillis délai en millisecondes
     * @return true si expiré
     */
    public boolean isExpired(long timeoutMillis) {
        if (lastDisconnectTime == null) {
            return false;
        }
        return (System.currentTimeMillis() - lastDisconnectTime) > timeoutMillis;
    }
}
