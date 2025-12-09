package com.gravityyfh.roleplaycity.entreprise.service;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.entreprise.model.*;
import com.gravityyfh.roleplaycity.entreprise.persistence.EntrepriseRepository;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service de suivi de la production et activité des employés.
 *
 * Responsabilités:
 * - Enregistrement des actions productives
 * - Gestion des sessions de travail
 * - Statistiques de production
 * - Calcul de la valeur générée
 */
public class ProductionService {

    private final RoleplayCity plugin;
    private final EntrepriseRepository repository;

    public ProductionService(RoleplayCity plugin, EntrepriseRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    /**
     * Démarre une session de travail pour un employé.
     *
     * @param entreprise L'entreprise
     * @param employee Le joueur employé
     * @return true si succès
     */
    public boolean startSession(Entreprise entreprise, Player employee) {
        if (entreprise == null || employee == null) {
            return false;
        }

        EmployeeActivityRecord record = entreprise.getOrCreateEmployeeActivityRecord(
            employee.getUniqueId(),
            employee.getName()
        );

        record.startSession();
        return repository.save(entreprise);
    }

    /**
     * Termine la session de travail d'un employé.
     *
     * @param entreprise L'entreprise
     * @param employee Le joueur employé
     * @return true si succès
     */
    public boolean endSession(Entreprise entreprise, Player employee) {
        if (entreprise == null || employee == null) {
            return false;
        }

        EmployeeActivityRecord record = entreprise.getEmployeeActivityRecord(employee.getUniqueId());
        if (record != null) {
            record.endSession();
            return repository.save(entreprise);
        }

        return false;
    }

    /**
     * Enregistre une action productive d'un employé.
     *
     * @param entreprise L'entreprise
     * @param employee Le joueur
     * @param material Le matériau concerné
     * @param quantity La quantité
     * @param actionType Le type d'action
     * @param value La valeur économique générée
     * @return true si succès
     */
    public boolean recordAction(
        Entreprise entreprise,
        Player employee,
        Material material,
        int quantity,
        DetailedActionType actionType,
        double value
    ) {
        if (entreprise == null || employee == null) {
            return false;
        }

        // Récupérer ou créer l'enregistrement d'activité
        EmployeeActivityRecord record = entreprise.getOrCreateEmployeeActivityRecord(
            employee.getUniqueId(),
            employee.getName()
        );

        // Clé générique de l'action
        String actionKey = actionType.name() + ":" + material.name();

        // Enregistrer l'action
        record.recordAction(actionKey, value, quantity, actionType, material);

        // Ajouter à la production globale
        entreprise.addGlobalProductionRecord(
            LocalDateTime.now(),
            material,
            quantity,
            employee.getUniqueId().toString(),
            actionType
        );

        return repository.save(entreprise);
    }

    /**
     * Récupère les statistiques de production d'un employé.
     *
     * @param entreprise L'entreprise
     * @param employeeUuid UUID de l'employé
     * @param start Début de la période
     * @param end Fin de la période
     * @param actionType Type d'action à filtrer (null = tous)
     * @return Map Material → Quantité
     */
    public Map<Material, Integer> getEmployeeProductionStats(
        Entreprise entreprise,
        UUID employeeUuid,
        LocalDateTime start,
        LocalDateTime end,
        DetailedActionType actionType
    ) {
        if (entreprise == null || employeeUuid == null) {
            return Collections.emptyMap();
        }

        Set<Material> relevantMaterials = entreprise.getTrackedProductionMaterials();

        return entreprise.getEmployeeProductionStatsForPeriod(
            employeeUuid,
            start,
            end,
            actionType,
            relevantMaterials
        );
    }

    /**
     * Récupère les statistiques de production agrégées de tous les employés.
     *
     * @param entreprise L'entreprise
     * @param start Début de la période
     * @param end Fin de la période
     * @param actionType Type d'action à filtrer (null = tous)
     * @return Map Material → Quantité totale
     */
    public Map<Material, Integer> getAggregatedProductionStats(
        Entreprise entreprise,
        LocalDateTime start,
        LocalDateTime end,
        DetailedActionType actionType
    ) {
        if (entreprise == null) {
            return Collections.emptyMap();
        }

        Set<Material> relevantMaterials = entreprise.getTrackedProductionMaterials();

        return entreprise.getAggregatedProductionStatsForPeriod(
            start,
            end,
            actionType,
            relevantMaterials
        );
    }

    /**
     * Récupère le log de production globale.
     *
     * @param entreprise L'entreprise
     * @return Liste des enregistrements de production
     */
    public List<ProductionRecord> getGlobalProductionLog(Entreprise entreprise) {
        if (entreprise == null) {
            return Collections.emptyList();
        }
        return entreprise.getGlobalProductionLog();
    }

    /**
     * Récupère les N derniers enregistrements de production.
     *
     * @param entreprise L'entreprise
     * @param limit Nombre maximum d'enregistrements
     * @return Liste des enregistrements
     */
    public List<ProductionRecord> getRecentProduction(Entreprise entreprise, int limit) {
        if (entreprise == null) {
            return Collections.emptyList();
        }

        return entreprise.getGlobalProductionLog().stream()
            .sorted((a, b) -> b.timestamp().compareTo(a.timestamp()))
            .limit(limit)
            .toList();
    }

    /**
     * Récupère l'enregistrement d'activité d'un employé.
     *
     * @param entreprise L'entreprise
     * @param employeeUuid UUID de l'employé
     * @return L'enregistrement d'activité ou null
     */
    public EmployeeActivityRecord getEmployeeActivity(Entreprise entreprise, UUID employeeUuid) {
        if (entreprise == null || employeeUuid == null) {
            return null;
        }
        return entreprise.getEmployeeActivityRecord(employeeUuid);
    }

    /**
     * Récupère tous les enregistrements d'activité.
     *
     * @param entreprise L'entreprise
     * @return Map UUID → EmployeeActivityRecord
     */
    public Map<UUID, EmployeeActivityRecord> getAllEmployeeActivities(Entreprise entreprise) {
        if (entreprise == null) {
            return Collections.emptyMap();
        }
        return entreprise.getEmployeeActivityRecords();
    }

    /**
     * Vérifie si un employé est actuellement en session.
     *
     * @param entreprise L'entreprise
     * @param employeeUuid UUID de l'employé
     * @return true si en session active
     */
    public boolean isInSession(Entreprise entreprise, UUID employeeUuid) {
        if (entreprise == null || employeeUuid == null) {
            return false;
        }

        EmployeeActivityRecord record = entreprise.getEmployeeActivityRecord(employeeUuid);
        return record != null && record.isActive();
    }

    /**
     * Récupère la valeur totale générée par un employé.
     *
     * @param entreprise L'entreprise
     * @param employeeUuid UUID de l'employé
     * @return Valeur totale générée
     */
    public double getTotalValueGenerated(Entreprise entreprise, UUID employeeUuid) {
        if (entreprise == null || employeeUuid == null) {
            return 0.0;
        }

        EmployeeActivityRecord record = entreprise.getEmployeeActivityRecord(employeeUuid);
        return record != null ? record.totalValueGenerated : 0.0;
    }

    /**
     * Récupère l'ancienneté formatée d'un employé.
     *
     * @param entreprise L'entreprise
     * @param employeeUuid UUID de l'employé
     * @return Ancienneté formatée
     */
    public String getEmployeeSeniority(Entreprise entreprise, UUID employeeUuid) {
        if (entreprise == null || employeeUuid == null) {
            return "N/A";
        }

        return entreprise.getEmployeeSeniorityFormatted(employeeUuid);
    }

    /**
     * Récupère les compteurs d'actions d'un employé.
     *
     * @param entreprise L'entreprise
     * @param employeeUuid UUID de l'employé
     * @return Map Action → Compteur
     */
    public Map<String, Long> getEmployeeActionCounts(Entreprise entreprise, UUID employeeUuid) {
        if (entreprise == null || employeeUuid == null) {
            return Collections.emptyMap();
        }

        EmployeeActivityRecord record = entreprise.getEmployeeActivityRecord(employeeUuid);
        return record != null ? record.actionsPerformedCount : Collections.emptyMap();
    }

    /**
     * Calcule la production totale d'un matériau sur une période.
     *
     * @param entreprise L'entreprise
     * @param material Le matériau
     * @param start Début de la période
     * @param end Fin de la période
     * @return Quantité totale produite
     */
    public int getTotalProduction(
        Entreprise entreprise,
        Material material,
        LocalDateTime start,
        LocalDateTime end
    ) {
        if (entreprise == null || material == null) {
            return 0;
        }

        return entreprise.getGlobalProductionLog().stream()
            .filter(record -> record.material() == material)
            .filter(record -> !record.timestamp().isBefore(start) && record.timestamp().isBefore(end))
            .mapToInt(ProductionRecord::quantity)
            .sum();
    }

    /**
     * Récupère les employés actuellement actifs (en session).
     *
     * @param entreprise L'entreprise
     * @return Liste des UUID des employés actifs
     */
    public List<UUID> getActiveEmployees(Entreprise entreprise) {
        if (entreprise == null) {
            return Collections.emptyList();
        }

        return entreprise.getEmployeeActivityRecords().values().stream()
            .filter(EmployeeActivityRecord::isActive)
            .map(record -> record.employeeId)
            .toList();
    }

    /**
     * Termine toutes les sessions actives (pour shutdown propre).
     *
     * @param entreprise L'entreprise
     * @return Nombre de sessions terminées
     */
    public int endAllSessions(Entreprise entreprise) {
        if (entreprise == null) {
            return 0;
        }

        int count = 0;
        for (EmployeeActivityRecord record : entreprise.getEmployeeActivityRecords().values()) {
            if (record.isActive()) {
                record.endSession();
                count++;
            }
        }

        if (count > 0) {
            repository.save(entreprise);
        }

        return count;
    }
}
