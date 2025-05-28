package com.gravityyfh.entreprisemanager; // Assurez-vous que le package est correct

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class PastExperience {
    // Déclarer les champs en private final pour l'encapsulation
    private final String entrepriseNom;
    private final String entrepriseType;
    private final String role;
    private final LocalDateTime dateEntree; // Peut être null si non trouvé
    private final LocalDateTime dateSortie;
    private final double caGenere;

    public PastExperience(String entrepriseNom, String entrepriseType, String role, LocalDateTime dateEntree, LocalDateTime dateSortie, double caGenere) {
        this.entrepriseNom = Objects.requireNonNull(entrepriseNom, "Nom entreprise ne peut être null");
        this.entrepriseType = Objects.requireNonNull(entrepriseType, "Type entreprise ne peut être null");
        this.role = Objects.requireNonNull(role, "Rôle ne peut être null");
        this.dateEntree = dateEntree;
        this.dateSortie = Objects.requireNonNull(dateSortie, "Date de sortie ne peut être null");
        this.caGenere = caGenere;
    }

    // --- Getters pour accéder aux champs privés ---
    public String getEntrepriseNom() { return entrepriseNom; }
    public String getEntrepriseType() { return entrepriseType; }
    public String getRole() { return role; }
    public LocalDateTime getDateEntree() { return dateEntree; }
    public LocalDateTime getDateSortie() { return dateSortie; } // <-- GETTER AJOUTÉ/VÉRIFIÉ
    public double getCaGenere() { return caGenere; }
    // --- Fin des Getters ---


    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("entrepriseNom", entrepriseNom);
        map.put("entrepriseType", entrepriseType);
        map.put("role", role);
        if (dateEntree != null) map.put("dateEntree", dateEntree.toString());
        if (dateSortie != null) map.put("dateSortie", dateSortie.toString());
        map.put("caGenere", caGenere);
        return map;
    }

    public static PastExperience deserialize(Map<String, Object> map) {
        if (map == null) { System.err.println("Erreur deserialize PastExperience: Map null."); return null; }
        try {
            String nom = (String) map.get("entrepriseNom");
            String type = (String) map.get("entrepriseType");
            String role = (String) map.get("role");
            LocalDateTime entree = null;
            if (map.containsKey("dateEntree") && map.get("dateEntree") instanceof String) {
                try { entree = LocalDateTime.parse((String) map.get("dateEntree")); }
                catch (Exception e) { System.err.println("Format dateEntree invalide: " + map.get("dateEntree")); }
            }
            LocalDateTime sortie = null;
            if (map.containsKey("dateSortie") && map.get("dateSortie") instanceof String) {
                try { sortie = LocalDateTime.parse((String) map.get("dateSortie")); }
                catch (Exception e) { System.err.println("Format dateSortie invalide: " + map.get("dateSortie")); }
            } else { System.err.println("dateSortie manquante: " + map); return null; }
            if (nom == null || type == null || role == null || sortie == null) { System.err.println("Données requises manquantes/invalides: " + map); return null; }
            double ca = ((Number) map.getOrDefault("caGenere", 0.0)).doubleValue();
            return new PastExperience(nom, type, role, entree, sortie, ca);
        } catch (Exception e) { System.err.println("Erreur générale deserialize PastExperience: " + e.getMessage() + " pour map: " + map); e.printStackTrace(); return null; }
    }

    @Override
    public String toString() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yy");
        String entreeStr = dateEntree != null ? dateEntree.format(formatter) : "???";
        String sortieStr = dateSortie != null ? dateSortie.format(formatter) : "???";
        return String.format("%s (%s) chez '%s' [%s - %s] (CA: %.2f€)", role, entrepriseType, entrepriseNom, entreeStr, sortieStr, caGenere);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PastExperience that = (PastExperience) o;
        return Double.compare(that.caGenere, caGenere) == 0 && Objects.equals(entrepriseNom, that.entrepriseNom) && Objects.equals(entrepriseType, that.entrepriseType) && Objects.equals(role, that.role) && Objects.equals(dateEntree, that.dateEntree) && Objects.equals(dateSortie, that.dateSortie);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entrepriseNom, entrepriseType, role, dateEntree, dateSortie, caGenere);
    }
}