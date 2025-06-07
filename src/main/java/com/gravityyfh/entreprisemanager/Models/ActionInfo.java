package com.gravityyfh.entreprisemanager.Models;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class ActionInfo {
    private int nombreActions;
    private LocalDateTime dernierActionHeure;

    public ActionInfo() {
        this.nombreActions = 0;
        this.dernierActionHeure = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
    }

    public int getNombreActions() {
        return nombreActions;
    }

    public LocalDateTime getDernierActionHeure() {
        return dernierActionHeure;
    }

    public void incrementerActions(int quantite) {
        this.nombreActions += quantite;
    }

    public void reinitialiserActions() {
        this.nombreActions = 0;
        this.dernierActionHeure = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
    }

    @Override
    public String toString() {
        return "ActionInfo{nActions=" + nombreActions + ", heure=" + dernierActionHeure.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "}";
    }
}