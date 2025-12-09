package com.gravityyfh.roleplaycity.mairie.service;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.mairie.data.Appointment;
import com.gravityyfh.roleplaycity.mairie.data.AppointmentStatus;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Gestionnaire des rendez-vous mairie
 * Limite: 3 RDV max par joueur, expiration apres 15 jours
 */
public class AppointmentManager {
    private final RoleplayCity plugin;
    private final AppointmentPersistenceService persistenceService;

    // Map: townName -> List<Appointment>
    private final Map<String, List<Appointment>> appointmentsByTown = new HashMap<>();

    // Constantes de configuration
    private static final int MAX_APPOINTMENTS_PER_PLAYER = 3;
    private static final int EXPIRATION_DAYS = 15;

    public AppointmentManager(RoleplayCity plugin, AppointmentPersistenceService persistenceService) {
        this.plugin = plugin;
        this.persistenceService = persistenceService;
    }

    /**
     * Charge tous les rendez-vous depuis SQLite
     */
    public void loadAppointments() {
        appointmentsByTown.clear();
        appointmentsByTown.putAll(persistenceService.loadAppointments());

        // Nettoyage des RDV expires au chargement
        cleanExpiredAppointments();
    }

    /**
     * Sauvegarde tous les rendez-vous
     */
    public void saveAll() {
        persistenceService.saveAppointments(appointmentsByTown);
    }

    /**
     * Cree un nouveau rendez-vous
     * @return le RDV cree ou null si limite atteinte
     */
    public Appointment createAppointment(UUID playerUuid, String playerName, String townName, String subject) {
        // Verifier la limite de 3 RDV par joueur
        int currentCount = countPlayerPendingAppointments(playerUuid);
        if (currentCount >= MAX_APPOINTMENTS_PER_PLAYER) {
            return null;
        }

        Appointment appointment = new Appointment(playerUuid, playerName, townName, subject);

        appointmentsByTown.computeIfAbsent(townName, k -> new ArrayList<>()).add(appointment);

        // Sauvegarde immediate
        persistenceService.saveAppointment(appointment);

        plugin.getLogger().info("[Appointments] Nouveau RDV cree: " + playerName + " -> " + townName + " (" + subject + ")");

        return appointment;
    }

    /**
     * Compte les RDV en attente d'un joueur (toutes villes confondues)
     */
    public int countPlayerPendingAppointments(UUID playerUuid) {
        return (int) appointmentsByTown.values().stream()
                .flatMap(List::stream)
                .filter(a -> a.getPlayerUuid().equals(playerUuid))
                .filter(Appointment::isPending)
                .count();
    }

    /**
     * Recupere les RDV d'un joueur
     */
    public List<Appointment> getPlayerAppointments(UUID playerUuid) {
        return appointmentsByTown.values().stream()
                .flatMap(List::stream)
                .filter(a -> a.getPlayerUuid().equals(playerUuid))
                .sorted(Comparator.comparing(Appointment::getRequestDate).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Recupere les RDV en attente d'un joueur
     */
    public List<Appointment> getPlayerPendingAppointments(UUID playerUuid) {
        return getPlayerAppointments(playerUuid).stream()
                .filter(Appointment::isPending)
                .collect(Collectors.toList());
    }

    /**
     * Recupere tous les RDV d'une ville
     */
    public List<Appointment> getTownAppointments(String townName) {
        return appointmentsByTown.getOrDefault(townName, new ArrayList<>()).stream()
                .sorted(Comparator.comparing(Appointment::getRequestDate))
                .collect(Collectors.toList());
    }

    /**
     * Recupere les RDV en attente d'une ville
     */
    public List<Appointment> getTownPendingAppointments(String townName) {
        return getTownAppointments(townName).stream()
                .filter(Appointment::isPending)
                .collect(Collectors.toList());
    }

    /**
     * Compte les RDV en attente d'une ville
     */
    public int countTownPendingAppointments(String townName) {
        return (int) appointmentsByTown.getOrDefault(townName, new ArrayList<>()).stream()
                .filter(Appointment::isPending)
                .count();
    }

    /**
     * Marque un RDV comme traite
     */
    public boolean markAsTreated(UUID appointmentId, UUID treatedByUuid, String treatedByName) {
        for (List<Appointment> appointments : appointmentsByTown.values()) {
            for (Appointment appointment : appointments) {
                if (appointment.getAppointmentId().equals(appointmentId)) {
                    appointment.markAsTreated(treatedByUuid, treatedByName);
                    persistenceService.saveAppointment(appointment);

                    plugin.getLogger().info("[Appointments] RDV traite: " + appointment.getPlayerName() +
                            " par " + treatedByName);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Trouve un RDV par son ID
     */
    public Appointment findAppointment(UUID appointmentId) {
        return appointmentsByTown.values().stream()
                .flatMap(List::stream)
                .filter(a -> a.getAppointmentId().equals(appointmentId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Supprime un RDV
     */
    public boolean deleteAppointment(UUID appointmentId) {
        for (Map.Entry<String, List<Appointment>> entry : appointmentsByTown.entrySet()) {
            List<Appointment> appointments = entry.getValue();
            boolean removed = appointments.removeIf(a -> a.getAppointmentId().equals(appointmentId));
            if (removed) {
                persistenceService.deleteAppointment(appointmentId);
                plugin.getLogger().info("[Appointments] RDV supprime: " + appointmentId);
                return true;
            }
        }
        return false;
    }

    /**
     * Annule un RDV (par le joueur lui-meme)
     */
    public boolean cancelAppointment(UUID appointmentId, UUID playerUuid) {
        Appointment appointment = findAppointment(appointmentId);
        if (appointment != null && appointment.getPlayerUuid().equals(playerUuid) && appointment.isPending()) {
            return deleteAppointment(appointmentId);
        }
        return false;
    }

    /**
     * Nettoie les RDV expires (plus de 15 jours)
     */
    public int cleanExpiredAppointments() {
        int totalRemoved = 0;

        for (List<Appointment> appointments : appointmentsByTown.values()) {
            int before = appointments.size();
            appointments.removeIf(a -> a.isPending() && a.isExpired());
            totalRemoved += before - appointments.size();
        }

        // Suppression en base
        int dbDeleted = persistenceService.deleteExpiredAppointments(EXPIRATION_DAYS);

        if (totalRemoved > 0) {
            plugin.getLogger().info("[Appointments] " + totalRemoved + " RDV expires supprimes");
        }

        return totalRemoved;
    }

    /**
     * Verifie si un joueur peut creer un nouveau RDV
     */
    public boolean canCreateAppointment(UUID playerUuid) {
        return countPlayerPendingAppointments(playerUuid) < MAX_APPOINTMENTS_PER_PLAYER;
    }

    /**
     * Retourne le nombre de RDV restants pour un joueur
     */
    public int getRemainingAppointmentSlots(UUID playerUuid) {
        return MAX_APPOINTMENTS_PER_PLAYER - countPlayerPendingAppointments(playerUuid);
    }

    /**
     * Recupere les RDV traites d'une ville (historique)
     */
    public List<Appointment> getTownTreatedAppointments(String townName) {
        return getTownAppointments(townName).stream()
                .filter(a -> a.getStatus() == AppointmentStatus.TREATED)
                .sorted(Comparator.comparing(Appointment::getTreatedDate).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Statistiques pour une ville
     */
    public Map<String, Integer> getTownStatistics(String townName) {
        List<Appointment> townAppts = getTownAppointments(townName);

        Map<String, Integer> stats = new HashMap<>();
        stats.put("total", townAppts.size());
        stats.put("pending", (int) townAppts.stream().filter(Appointment::isPending).count());
        stats.put("treated", (int) townAppts.stream().filter(a -> a.getStatus() == AppointmentStatus.TREATED).count());

        return stats;
    }

    // Getters pour configuration
    public static int getMaxAppointmentsPerPlayer() {
        return MAX_APPOINTMENTS_PER_PLAYER;
    }

    public static int getExpirationDays() {
        return EXPIRATION_DAYS;
    }
}
