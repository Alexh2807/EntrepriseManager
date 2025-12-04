package com.gravityyfh.roleplaycity.entreprise.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Statistiques simplifiées d'un employé.
 *
 * Contient UNIQUEMENT les données essentielles pour calculer
 * la rentabilité d'un employé (bénéfice/perte).
 */
public class EmployeeProfit {

    private final UUID employeeUuid;
    private final String employeeName;
    private final LocalDateTime joinDate;

    // Statistiques financières
    private double totalProductionValue;  // Valeur totale générée par production
    private double totalSalaryPaid;       // Total salaires payés
    private double totalBonusPaid;        // Total primes payées

    public EmployeeProfit(UUID employeeUuid, String employeeName, LocalDateTime joinDate) {
        this.employeeUuid = employeeUuid;
        this.employeeName = employeeName;
        this.joinDate = joinDate;
        this.totalProductionValue = 0.0;
        this.totalSalaryPaid = 0.0;
        this.totalBonusPaid = 0.0;
    }

    // Getters
    public UUID getEmployeeUuid() { return employeeUuid; }
    public String getEmployeeName() { return employeeName; }
    public LocalDateTime getJoinDate() { return joinDate; }
    public double getTotalProductionValue() { return totalProductionValue; }
    public double getTotalSalaryPaid() { return totalSalaryPaid; }
    public double getTotalBonusPaid() { return totalBonusPaid; }

    // Setters
    public void setTotalProductionValue(double value) { this.totalProductionValue = value; }
    public void setTotalSalaryPaid(double value) { this.totalSalaryPaid = value; }
    public void setTotalBonusPaid(double value) { this.totalBonusPaid = value; }

    // Méthodes d'incrémentation
    public void addProductionValue(double value) {
        this.totalProductionValue += value;
    }

    public void addSalaryPaid(double salary) {
        this.totalSalaryPaid += salary;
    }

    public void addBonusPaid(double bonus) {
        this.totalBonusPaid += bonus;
    }

    /**
     * Calcule le coût total de l'employé (salaires + primes).
     */
    public double getTotalCost() {
        return totalSalaryPaid + totalBonusPaid;
    }

    /**
     * Calcule le profit net généré par l'employé.
     * Positif = l'employé est rentable
     * Négatif = l'employé coûte plus qu'il ne rapporte
     */
    public double getNetProfit() {
        return totalProductionValue - getTotalCost();
    }

    /**
     * Calcule le ROI (Return on Investment) en pourcentage.
     * 100% = l'employé rapporte autant qu'il coûte
     * 200% = l'employé rapporte 2x ce qu'il coûte
     */
    public double getROI() {
        double cost = getTotalCost();
        if (cost == 0) return 0.0;
        return (totalProductionValue / cost) * 100.0;
    }

    /**
     * Vérifie si l'employé est rentable.
     */
    public boolean isProfitable() {
        return getNetProfit() > 0;
    }

    @Override
    public String toString() {
        return String.format(
            "EmployeeProfit{name='%s', production=%.2f€, cost=%.2f€, profit=%.2f€, roi=%.1f%%}",
            employeeName,
            totalProductionValue,
            getTotalCost(),
            getNetProfit(),
            getROI()
        );
    }
}
