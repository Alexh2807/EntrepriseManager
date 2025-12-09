package com.gravityyfh.roleplaycity.entreprise.service;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.entreprise.model.Entreprise;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Wrapper asynchrone pour EntrepriseService avec cache en mémoire.
 *
 * Responsabilités:
 * - Exécuter les opérations en arrière-plan
 * - Maintenir un cache en mémoire (write-through)
 * - Éviter le blocking du thread principal
 * - Fournir des callbacks pour les résultats
 *
 * Pattern: Async-Cache-Repository
 */
public class AsyncEntrepriseService {

    private final RoleplayCity plugin;
    private final EntrepriseService service;

    // Cache en mémoire (write-through)
    private final Map<String, Entreprise> cacheByNom = new ConcurrentHashMap<>();
    private final Map<String, Entreprise> cacheBySiret = new ConcurrentHashMap<>();

    // Dirty flag pour sauvegarde périodique
    private final Set<String> dirtyEntreprises = ConcurrentHashMap.newKeySet();

    public AsyncEntrepriseService(RoleplayCity plugin, EntrepriseService service) {
        this.plugin = plugin;
        this.service = service;
    }

    /**
     * Charge toutes les entreprises dans le cache.
     * À appeler au démarrage du plugin.
     */
    public CompletableFuture<Integer> loadAllToCache() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Entreprise> all = service.loadAll();
            cacheByNom.putAll(all);

            // Indexer par SIRET
            all.values().forEach(e -> cacheBySiret.put(e.getSiret(), e));

            plugin.getLogger().info("[AsyncEntrepriseService] " + all.size() + " entreprises chargées dans le cache");
            return all.size();
        });
    }

    /**
     * Récupère une entreprise (depuis cache si disponible).
     * Exécution synchrone car le cache est en mémoire.
     */
    public Entreprise getEntreprise(String nom) {
        return cacheByNom.get(nom);
    }

    /**
     * Récupère une entreprise par SIRET (depuis cache).
     */
    public Entreprise getEntrepriseBySiret(String siret) {
        return cacheBySiret.get(siret);
    }

    /**
     * Vérifie l'existence d'une entreprise (cache).
     */
    public boolean exists(String nom) {
        return cacheByNom.containsKey(nom);
    }

    /**
     * Vérifie l'existence d'un SIRET (cache).
     */
    public boolean siretExists(String siret) {
        return cacheBySiret.containsKey(siret);
    }

    /**
     * Récupère toutes les entreprises (cache).
     */
    public Collection<Entreprise> getEntreprises() {
        return cacheByNom.values();
    }

    /**
     * Compte le nombre d'entreprises (cache).
     */
    public int countEntreprises() {
        return cacheByNom.size();
    }

    /**
     * Crée une entreprise (async).
     * Met à jour le cache puis sauvegarde en arrière-plan.
     */
    public CompletableFuture<Entreprise> createEntreprise(
            String nom,
            String ville,
            String type,
            Player gerant,
            String siret,
            double coutCreation
    ) {
        return CompletableFuture.supplyAsync(() -> {
            // Créer l'entreprise
            Entreprise entreprise = service.createEntreprise(nom, ville, type, gerant, siret, coutCreation);

            if (entreprise != null) {
                // Ajouter au cache
                cacheByNom.put(nom, entreprise);
                cacheBySiret.put(siret, entreprise);
                dirtyEntreprises.add(siret);

                plugin.getLogger().info("[AsyncEntrepriseService] Entreprise créée: " + nom);
            }

            return entreprise;
        });
    }

    /**
     * Supprime une entreprise (async).
     */
    public CompletableFuture<Boolean> deleteEntreprise(String siret) {
        return CompletableFuture.supplyAsync(() -> {
            Entreprise entreprise = cacheBySiret.get(siret);
            boolean success = service.deleteEntreprise(siret);

            if (success && entreprise != null) {
                // Retirer du cache
                cacheByNom.remove(entreprise.getNom());
                cacheBySiret.remove(siret);
                dirtyEntreprises.remove(siret);

                plugin.getLogger().info("[AsyncEntrepriseService] Entreprise supprimée: " + entreprise.getNom());
            }

            return success;
        });
    }

    /**
     * Sauvegarde une entreprise (async write-through).
     */
    public CompletableFuture<Boolean> saveEntreprise(Entreprise entreprise) {
        return CompletableFuture.supplyAsync(() -> {
            boolean success = service.saveEntreprise(entreprise);

            if (success) {
                // Mettre à jour le cache
                cacheByNom.put(entreprise.getNom(), entreprise);
                cacheBySiret.put(entreprise.getSiret(), entreprise);
                dirtyEntreprises.remove(entreprise.getSiret());
            }

            return success;
        });
    }

    /**
     * Renomme une entreprise (async).
     */
    public CompletableFuture<Boolean> renameEntreprise(Entreprise entreprise, String nouveauNom) {
        return CompletableFuture.supplyAsync(() -> {
            String ancienNom = entreprise.getNom();
            boolean success = service.renameEntreprise(entreprise, nouveauNom);

            if (success) {
                // Mettre à jour le cache
                cacheByNom.remove(ancienNom);
                cacheByNom.put(nouveauNom, entreprise);
                dirtyEntreprises.add(entreprise.getSiret());

                plugin.getLogger().info("[AsyncEntrepriseService] Entreprise renommée: " + ancienNom + " → " + nouveauNom);
            }

            return success;
        });
    }

    /**
     * Ajoute un employé (async).
     */
    public CompletableFuture<Boolean> addEmploye(Entreprise entreprise, String nomJoueur) {
        return CompletableFuture.supplyAsync(() -> {
            boolean success = service.addEmploye(entreprise, nomJoueur);

            if (success) {
                dirtyEntreprises.add(entreprise.getSiret());
            }

            return success;
        });
    }

    /**
     * Retire un employé (async).
     */
    public CompletableFuture<Boolean> removeEmploye(Entreprise entreprise, String nomJoueur) {
        return CompletableFuture.supplyAsync(() -> {
            boolean success = service.removeEmploye(entreprise, nomJoueur);

            if (success) {
                dirtyEntreprises.add(entreprise.getSiret());
            }

            return success;
        });
    }

    /**
     * Recherche par ville (cache).
     */
    public List<Entreprise> getEntreprisesParVille(String ville) {
        return cacheByNom.values().stream()
                .filter(e -> e.getVille().equalsIgnoreCase(ville))
                .toList();
    }

    /**
     * Recherche par type (cache).
     */
    public List<Entreprise> getEntreprisesParType(String type) {
        return cacheByNom.values().stream()
                .filter(e -> e.getType().equalsIgnoreCase(type))
                .toList();
    }

    /**
     * Recherche par gérant (cache).
     */
    public List<Entreprise> getEntreprisesGereesPar(String playerName) {
        return cacheByNom.values().stream()
                .filter(e -> e.getGerant().equalsIgnoreCase(playerName))
                .toList();
    }

    /**
     * Recherche par membre (cache).
     * FIX MULTI-ENTREPRISES: Inclut à la fois le gérant ET les employés
     */
    public List<Entreprise> getEntreprisesDuJoueur(Player player) {
        return cacheByNom.values().stream()
                .filter(e -> e.getGerant().equalsIgnoreCase(player.getName()) ||
                             e.getEmployes().contains(player.getName()))
                .toList();
    }

    /**
     * Sauvegarde toutes les entreprises modifiées (dirty).
     * À appeler périodiquement ou au shutdown.
     */
    public CompletableFuture<Integer> saveDirtyEntreprises() {
        if (dirtyEntreprises.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }

        return CompletableFuture.supplyAsync(() -> {
            int saved = 0;
            List<String> toSave = new ArrayList<>(dirtyEntreprises);

            for (String siret : toSave) {
                Entreprise entreprise = cacheBySiret.get(siret);
                if (entreprise != null && service.saveEntreprise(entreprise)) {
                    dirtyEntreprises.remove(siret);
                    saved++;
                }
            }

            if (saved > 0) {
                plugin.getLogger().info("[AsyncEntrepriseService] " + saved + " entreprises sauvegardées");
            }

            return saved;
        });
    }

    /**
     * Sauvegarde une liste spécifique d'entreprises (Sync cache, Async DB).
     */
    public CompletableFuture<Integer> saveList(Collection<Entreprise> entreprises) {
        return CompletableFuture.supplyAsync(() -> {
            int saved = 0;
            for (Entreprise ent : entreprises) {
                // Update cache first
                cacheByNom.put(ent.getNom(), ent);
                if (ent.getSiret() != null) {
                    cacheBySiret.put(ent.getSiret(), ent);
                }
                
                if (service.saveEntreprise(ent)) {
                    saved++;
                }
            }
            return saved;
        });
    }

    /**
     * Sauvegarde toutes les entreprises (force).
     */
    public CompletableFuture<Integer> saveAll() {
        return CompletableFuture.supplyAsync(() -> {
            int saved = 0;

            for (Entreprise entreprise : cacheByNom.values()) {
                if (service.saveEntreprise(entreprise)) {
                    saved++;
                }
            }

            dirtyEntreprises.clear();
            plugin.getLogger().info("[AsyncEntrepriseService] Toutes les entreprises sauvegardées (" + saved + ")");

            return saved;
        });
    }

    /**
     * Invalide le cache et recharge depuis la base.
     */
    public CompletableFuture<Void> reloadCache() {
        return CompletableFuture.runAsync(() -> {
            cacheByNom.clear();
            cacheBySiret.clear();
            dirtyEntreprises.clear();

            loadAllToCache().join(); // Wait for reload
        });
    }

    /**
     * Génère un SIRET unique (sync car rapide).
     */
    public String generateSiret() {
        return service.generateSiret();
    }

    /**
     * Valide un nom d'entreprise.
     * Caractères autorisés: lettres (a-z, A-Z), chiffres (0-9), tirets (-) et underscores (_)
     * PAS D'ESPACES pour éviter les problèmes avec les commandes
     */
    public boolean validateNom(String nom) {
        // Validation locale (même logique que dans EntrepriseService)
        if (nom == null || nom.trim().isEmpty()) {
            return false;
        }
        if (nom.length() < 3 || nom.length() > 32) {
            return false;
        }
        // Caractères autorisés: lettres, chiffres, tirets, underscores (PAS d'espaces)
        return nom.matches("^[a-zA-Z0-9_\\-]+$");
    }

    /**
     * Statistiques du cache.
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cached_entries", cacheByNom.size());
        stats.put("dirty_entries", dirtyEntreprises.size());
        stats.put("cache_hit_rate", "N/A"); // À implémenter si besoin

        return stats;
    }

    /**
     * Helper pour exécuter un callback sur le thread principal.
     */
    public <T> void onMainThread(CompletableFuture<T> future, Consumer<T> callback) {
        future.thenAccept(result -> {
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
        });
    }
}
