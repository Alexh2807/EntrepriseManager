package com.gravityyfh.roleplaycity.entreprise.service;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.entreprise.model.Entreprise;
import com.gravityyfh.roleplaycity.entreprise.persistence.EntrepriseRepository;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Level;

/**
 * Service de gestion des entreprises.
 * Centralise toutes les opérations CRUD et logique métier liée aux entreprises.
 *
 * Responsabilités:
 * - Création, modification, suppression d'entreprises
 * - Validation des données
 * - Gestion des employés
 * - Recherches et requêtes
 */
public class EntrepriseService {

    private final RoleplayCity plugin;
    private final EntrepriseRepository repository;

    public EntrepriseService(RoleplayCity plugin, EntrepriseRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    /**
     * Crée une nouvelle entreprise.
     *
     * @param nom Le nom unique
     * @param ville La ville
     * @param type Le type d'entreprise
     * @param gerant Le joueur gérant
     * @param siret Le SIRET unique
     * @param coutCreation Le coût de création (déjà payé)
     * @return L'entreprise créée ou null si erreur
     */
    public Entreprise createEntreprise(
        String nom,
        String ville,
        String type,
        Player gerant,
        String siret,
        double coutCreation
    ) {
        // Validation
        if (!validateNom(nom)) {
            plugin.getLogger().warning("Nom invalide: " + nom);
            return null;
        }

        if (repository.nomExists(nom)) {
            plugin.getLogger().warning("Nom déjà existant: " + nom);
            return null;
        }

        if (repository.exists(siret)) {
            plugin.getLogger().warning("SIRET déjà existant: " + siret);
            return null;
        }

        // Créer l'entreprise
        Entreprise entreprise = new Entreprise(
            nom,
            ville,
            type,
            gerant.getName(),
            gerant.getUniqueId().toString(),
            new HashSet<>(),
            coutCreation, // Solde initial = coût création
            siret
        );

        // Sauvegarder
        if (repository.save(entreprise)) {
            plugin.getLogger().info("Entreprise créée: " + nom + " (SIRET: " + siret + ")");
            return entreprise;
        } else {
            plugin.getLogger().severe("Erreur sauvegarde entreprise: " + nom);
            return null;
        }
    }

    /**
     * Récupère une entreprise par son nom.
     */
    public Entreprise getEntreprise(String nom) {
        return repository.findByNom(nom).orElse(null);
    }

    /**
     * Récupère une entreprise par son SIRET.
     */
    public Entreprise getEntrepriseBySiret(String siret) {
        return repository.findBySiret(siret).orElse(null);
    }

    /**
     * Récupère toutes les entreprises d'un joueur (en tant que gérant).
     */
    public List<Entreprise> getEntreprisesGereesPar(String playerName) {
        Player player = plugin.getServer().getPlayerExact(playerName);
        if (player == null) {
            return Collections.emptyList();
        }
        return repository.findByGerant(player.getUniqueId());
    }

    /**
     * Récupère toutes les entreprises où un joueur est membre (gérant ou employé).
     */
    public List<Entreprise> getEntreprisesDuJoueur(Player player) {
        return repository.findByMember(player.getName());
    }

    /**
     * Récupère la première entreprise d'un joueur (pour compatibilité).
     */
    public Entreprise getEntrepriseDuJoueur(Player player) {
        List<Entreprise> entreprises = getEntreprisesDuJoueur(player);
        return entreprises.isEmpty() ? null : entreprises.get(0);
    }

    /**
     * Récupère les noms de toutes les entreprises où le joueur est membre.
     */
    public List<String> getNomsEntreprisesDuMembre(String nomJoueur) {
        return repository.findByMember(nomJoueur).stream()
            .map(Entreprise::getNom)
            .toList();
    }

    /**
     * Récupère le nom de la première entreprise d'un joueur.
     */
    public String getNomEntrepriseDuMembre(String nomJoueur) {
        List<String> noms = getNomsEntreprisesDuMembre(nomJoueur);
        return noms.isEmpty() ? null : noms.get(0);
    }

    /**
     * Récupère toutes les entreprises.
     */
    public Collection<Entreprise> getEntreprises() {
        return repository.findAll();
    }

    /**
     * Récupère toutes les entreprises d'une ville.
     */
    public List<Entreprise> getEntreprisesParVille(String ville) {
        return repository.findByVille(ville);
    }

    /**
     * Récupère toutes les entreprises d'un type.
     */
    public List<Entreprise> getEntreprisesParType(String type) {
        return repository.findByType(type);
    }

    /**
     * Sauvegarde une entreprise.
     */
    public boolean saveEntreprise(Entreprise entreprise) {
        return repository.save(entreprise);
    }

    /**
     * Renomme une entreprise.
     */
    public boolean renameEntreprise(Entreprise entreprise, String nouveauNom) {
        if (!validateNom(nouveauNom)) {
            return false;
        }

        if (repository.nomExists(nouveauNom)) {
            return false;
        }

        entreprise.setNom(nouveauNom);
        return repository.save(entreprise);
    }

    /**
     * Supprime une entreprise.
     */
    public boolean deleteEntreprise(String siret) {
        if (repository.delete(siret)) {
            plugin.getLogger().info("Entreprise supprimée: SIRET " + siret);
            return true;
        }
        return false;
    }

    /**
     * Ajoute un employé à une entreprise.
     */
    public boolean addEmploye(Entreprise entreprise, String nomJoueur) {
        if (entreprise == null || nomJoueur == null) {
            return false;
        }

        // Vérifier la limite d'employés
        int maxEmployes = getMaxEmployesPourNiveau(entreprise.getNiveauMaxEmployes());
        if (entreprise.getEmployes().size() >= maxEmployes) {
            plugin.getLogger().warning("Limite d'employés atteinte pour: " + entreprise.getNom());
            return false;
        }

        // Ajouter l'employé
        if (entreprise.getEmployesInternal().add(nomJoueur)) {
            // Initialiser la prime à 0
            Player player = plugin.getServer().getPlayerExact(nomJoueur);
            if (player != null) {
                entreprise.setPrimePourEmploye(player.getUniqueId().toString(), 0.0);
            }

            // Créer l'enregistrement d'activité
            if (player != null) {
                entreprise.getOrCreateEmployeeActivityRecord(player.getUniqueId(), nomJoueur);
            }

            return repository.save(entreprise);
        }

        return false;
    }

    /**
     * Retire un employé d'une entreprise.
     */
    public boolean removeEmploye(Entreprise entreprise, String nomJoueur) {
        if (entreprise == null || nomJoueur == null) {
            return false;
        }

        if (entreprise.getEmployesInternal().remove(nomJoueur)) {
            // Retirer la prime
            Player player = plugin.getServer().getPlayerExact(nomJoueur);
            if (player != null) {
                entreprise.retirerPrimeEmploye(player.getUniqueId().toString());
            }

            return repository.save(entreprise);
        }

        return false;
    }

    /**
     * Vérifie si un joueur est membre d'une entreprise.
     */
    public boolean estMembre(Entreprise entreprise, String nomJoueur) {
        if (entreprise == null || nomJoueur == null) {
            return false;
        }

        return entreprise.getGerant().equalsIgnoreCase(nomJoueur) ||
               entreprise.getEmployes().contains(nomJoueur);
    }

    /**
     * Vérifie si un joueur est gérant d'une entreprise.
     */
    public boolean estGerant(Entreprise entreprise, Player player) {
        if (entreprise == null || player == null) {
            return false;
        }
        return entreprise.getGerantUUID().equals(player.getUniqueId().toString());
    }

    /**
     * Compte le nombre d'entreprises.
     */
    public int countEntreprises() {
        return repository.count();
    }

    /**
     * Compte le nombre d'entreprises d'un gérant.
     */
    public int countEntreprisesGerant(UUID gerantUuid) {
        return repository.countByGerant(gerantUuid);
    }

    /**
     * Valide un nom d'entreprise.
     * Caractères autorisés: lettres (a-z, A-Z), chiffres (0-9), tirets (-) et underscores (_)
     * PAS D'ESPACES pour éviter les problèmes avec les commandes
     */
    private boolean validateNom(String nom) {
        if (nom == null || nom.trim().isEmpty()) {
            return false;
        }

        // Longueur
        if (nom.length() < 3 || nom.length() > 32) {
            return false;
        }

        // Caractères autorisés: lettres, chiffres, tirets, underscores (PAS d'espaces)
        return nom.matches("^[a-zA-Z0-9_\\-]+$");
    }

    /**
     * Calcule le nombre maximum d'employés selon le niveau.
     */
    public int getMaxEmployesPourNiveau(int niveau) {
        // Configuration depuis config.yml
        return plugin.getConfig().getInt("entreprise.niveaux.employes.niveau-" + niveau + ".max", 5);
    }

    /**
     * Calcule le solde maximum selon le niveau.
     */
    public double getMaxSoldePourNiveau(int niveau) {
        // Configuration depuis config.yml
        return plugin.getConfig().getDouble("entreprise.niveaux.solde.niveau-" + niveau + ".max", 100000.0);
    }

    /**
     * Vérifie si une entreprise existe.
     */
    public boolean exists(String nom) {
        return repository.nomExists(nom);
    }

    /**
     * Vérifie si un SIRET existe.
     */
    public boolean siretExists(String siret) {
        return repository.exists(siret);
    }

    /**
     * Génère un SIRET unique.
     */
    public String generateSiret() {
        String siret;
        do {
            siret = String.format("%014d", new Random().nextLong(100000000000000L));
        } while (repository.exists(siret));
        return siret;
    }

    /**
     * Sauvegarde toutes les entreprises (bulk save).
     */
    public int saveAll(Collection<Entreprise> entreprises) {
        int saved = 0;
        for (Entreprise entreprise : entreprises) {
            if (repository.save(entreprise)) {
                saved++;
            }
        }
        return saved;
    }

    /**
     * Charge toutes les entreprises en mémoire (pour initialisation).
     */
    public Map<String, Entreprise> loadAll() {
        Map<String, Entreprise> map = new HashMap<>();
        List<Entreprise> all = repository.findAll();

        for (Entreprise entreprise : all) {
            map.put(entreprise.getNom(), entreprise);
        }

        plugin.getLogger().info("Chargé " + all.size() + " entreprises depuis SQLite");
        return map;
    }
}
