package com.gravityyfh.roleplaycity.entreprise.persistence;

import com.gravityyfh.roleplaycity.entreprise.model.Entreprise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository pour la persistence des entreprises.
 * Abstraction de la couche de stockage (SQLite).
 */
public interface EntrepriseRepository {

    /**
     * Sauvegarde ou met à jour une entreprise.
     * @param entreprise L'entreprise à sauvegarder
     * @return true si succès, false sinon
     */
    boolean save(Entreprise entreprise);

    /**
     * Récupère une entreprise par son SIRET.
     * @param siret Le SIRET unique
     * @return Optional contenant l'entreprise si trouvée
     */
    Optional<Entreprise> findBySiret(String siret);

    /**
     * Récupère une entreprise par son nom.
     * @param nom Le nom unique
     * @return Optional contenant l'entreprise si trouvée
     */
    Optional<Entreprise> findByNom(String nom);

    /**
     * Récupère toutes les entreprises d'une ville.
     * @param ville Le nom de la ville
     * @return Liste des entreprises
     */
    List<Entreprise> findByVille(String ville);

    /**
     * Récupère toutes les entreprises d'un type.
     * @param type Le type d'entreprise
     * @return Liste des entreprises
     */
    List<Entreprise> findByType(String type);

    /**
     * Récupère toutes les entreprises gérées par un joueur.
     * @param gerantUuid UUID du gérant
     * @return Liste des entreprises
     */
    List<Entreprise> findByGerant(UUID gerantUuid);

    /**
     * Récupère toutes les entreprises où un joueur est membre (gérant ou employé).
     * @param playerName Nom du joueur
     * @return Liste des entreprises
     */
    List<Entreprise> findByMember(String playerName);

    /**
     * Récupère toutes les entreprises.
     * @return Liste de toutes les entreprises
     */
    List<Entreprise> findAll();

    /**
     * Supprime une entreprise.
     * @param siret Le SIRET de l'entreprise
     * @return true si supprimée, false si non trouvée
     */
    boolean delete(String siret);

    /**
     * Vérifie si une entreprise existe.
     * @param siret Le SIRET
     * @return true si existe
     */
    boolean exists(String siret);

    /**
     * Vérifie si un nom est déjà pris.
     * @param nom Le nom
     * @return true si déjà pris
     */
    boolean nomExists(String nom);

    /**
     * Compte le nombre d'entreprises.
     * @return Nombre total d'entreprises
     */
    int count();

    /**
     * Compte le nombre d'entreprises d'un gérant.
     * @param gerantUuid UUID du gérant
     * @return Nombre d'entreprises
     */
    int countByGerant(UUID gerantUuid);
}
