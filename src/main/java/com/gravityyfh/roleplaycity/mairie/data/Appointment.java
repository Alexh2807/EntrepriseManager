package com.gravityyfh.roleplaycity.mairie.data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Représente un rendez-vous demandé par un citoyen à la mairie
 */
public class Appointment {

    private final UUID appointmentId;
    private final UUID playerUuid;
    private final String playerName;
    private final String townName;
    private final String subject;
    private final LocalDateTime requestDate;
    private AppointmentStatus status;
    private UUID treatedByUuid;
    private String treatedByName;
    private LocalDateTime treatedDate;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * Constructeur pour un nouveau rendez-vous
     */
    public Appointment(UUID playerUuid, String playerName, String townName, String subject) {
        this.appointmentId = UUID.randomUUID();
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.townName = townName;
        this.subject = subject;
        this.requestDate = LocalDateTime.now();
        this.status = AppointmentStatus.PENDING;
    }

    /**
     * Constructeur complet pour chargement depuis BDD
     */
    public Appointment(UUID appointmentId, UUID playerUuid, String playerName, String townName,
                       String subject, LocalDateTime requestDate, AppointmentStatus status,
                       UUID treatedByUuid, String treatedByName, LocalDateTime treatedDate) {
        this.appointmentId = appointmentId;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.townName = townName;
        this.subject = subject;
        this.requestDate = requestDate;
        this.status = status;
        this.treatedByUuid = treatedByUuid;
        this.treatedByName = treatedByName;
        this.treatedDate = treatedDate;
    }

    // =========================================================================
    // Getters
    // =========================================================================

    public UUID getAppointmentId() {
        return appointmentId;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getTownName() {
        return townName;
    }

    public String getSubject() {
        return subject;
    }

    public LocalDateTime getRequestDate() {
        return requestDate;
    }

    public String getFormattedRequestDate() {
        return requestDate.format(FORMATTER);
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public UUID getTreatedByUuid() {
        return treatedByUuid;
    }

    public String getTreatedByName() {
        return treatedByName;
    }

    public LocalDateTime getTreatedDate() {
        return treatedDate;
    }

    public String getFormattedTreatedDate() {
        return treatedDate != null ? treatedDate.format(FORMATTER) : null;
    }

    // =========================================================================
    // Méthodes
    // =========================================================================

    /**
     * Marque le rendez-vous comme traité
     */
    public void markAsTreated(UUID treatedByUuid, String treatedByName) {
        this.status = AppointmentStatus.TREATED;
        this.treatedByUuid = treatedByUuid;
        this.treatedByName = treatedByName;
        this.treatedDate = LocalDateTime.now();
    }

    /**
     * Vérifie si le rendez-vous est en attente
     */
    public boolean isPending() {
        return status == AppointmentStatus.PENDING;
    }

    /**
     * Vérifie si le rendez-vous a expiré (plus de 15 jours)
     */
    public boolean isExpired() {
        return requestDate.plusDays(15).isBefore(LocalDateTime.now());
    }

    /**
     * Retourne le nombre de jours depuis la demande
     */
    public long getDaysSinceRequest() {
        return java.time.temporal.ChronoUnit.DAYS.between(requestDate, LocalDateTime.now());
    }

    @Override
    public String toString() {
        return "Appointment{" +
                "id=" + appointmentId.toString().substring(0, 8) +
                ", player=" + playerName +
                ", town=" + townName +
                ", status=" + status +
                ", date=" + getFormattedRequestDate() +
                '}';
    }
}
