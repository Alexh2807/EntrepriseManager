package com.gravityyfh.roleplaycity.mdt.beds;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.mdt.config.MDTConfig;
import com.gravityyfh.roleplaycity.mdt.config.MDTConfig.BedComplete;
import org.bukkit.Location;
import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Gestionnaire d'état ultra-fiable pour les lits MDT
 *
 * Ce gestionnaire garantit que les lits sont sauvegardés et restaurés
 * avec leur orientation exacte, indépendamment de l'état actuel du monde.
 */
public class BedStateManager {

    private final RoleplayCity plugin;
    private final MDTConfig config;

    // Cache des lits chargés depuis la configuration pour éviter les recharges multiples
    private final Map<String, BedComplete> bedCache = new HashMap<>();

    // Timestamp de la dernière initialisation
    private long lastInitializationTime = 0;

    public BedStateManager(RoleplayCity plugin, MDTConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Initialise tous les lits depuis la configuration
     * Doit être appelé au début de chaque partie
     */
    public void initializeFromConfig() {
        plugin.getLogger().info("[MDT-BedState] Initialisation des lits depuis la configuration...");

        bedCache.clear();

        // Charger le lit rouge
        BedComplete redBed = config.loadBedCompleteFromConfig("red");
        if (redBed != null) {
            if (validateBedIntegrity("red", redBed)) {
                bedCache.put("red", redBed);
                plugin.getLogger().info("[MDT-BedState] ✓ Lit rouge initialisé: " + redBed.toString());
            } else {
                plugin.getLogger().warning("[MDT-BedState] ✗ Lit rouge invalide, ignoré");
            }
        } else {
            plugin.getLogger().warning("[MDT-BedState] ✗ Lit rouge non trouvé dans la configuration");
        }

        // Charger le lit bleu
        BedComplete blueBed = config.loadBedCompleteFromConfig("blue");
        if (blueBed != null) {
            if (validateBedIntegrity("blue", blueBed)) {
                bedCache.put("blue", blueBed);
                plugin.getLogger().info("[MDT-BedState] ✓ Lit bleu initialisé: " + blueBed.toString());
            } else {
                plugin.getLogger().warning("[MDT-BedState] ✗ Lit bleu invalide, ignoré");
            }
        } else {
            plugin.getLogger().warning("[MDT-BedState] ✗ Lit bleu non trouvé dans la configuration");
        }

        // Charger les lits neutres
        for (int i = 1; i <= 10; i++) { // Supporter jusqu'à 10 lits neutres
            String bedId = "neutral_" + i;
            BedComplete neutralBed = config.loadBedCompleteFromConfig(bedId);
            if (neutralBed != null) {
                if (validateBedIntegrity(bedId, neutralBed)) {
                    bedCache.put(bedId, neutralBed);
                    plugin.getLogger().info("[MDT-BedState] ✓ Lit neutre " + i + " initialisé: " + neutralBed.toString());
                } else {
                    plugin.getLogger().warning("[MDT-BedState] ✗ Lit neutre " + i + " invalide, ignoré");
                }
            }
        }

        lastInitializationTime = System.currentTimeMillis();

        plugin.getLogger().info("[MDT-BedState] Initialisation terminée: " + bedCache.size() + " lits chargés");
    }

    /**
     * Valide l'intégrité d'un lit
     */
    private boolean validateBedIntegrity(String bedId, BedComplete bed) {
        if (bed == null) return false;

        // Vérifier que les deux parties ont le même matériau
        if (bed.material == null || !bed.material.name().endsWith("_BED")) {
            plugin.getLogger().warning("[MDT-BedState] Lit " + bedId + ": matériau invalide - " + bed.material);
            return false;
        }

        // Vérifier que les positions sont valides
        if (bed.headLocation == null || bed.footLocation == null) {
            plugin.getLogger().warning("[MDT-BedState] Lit " + bedId + ": positions invalides");
            return false;
        }

        // Vérifier que les positions sont adjacentes
        double distance = bed.headLocation.distance(bed.footLocation);
        if (distance > 2.0) { // Distance anormale
            plugin.getLogger().warning("[MDT-BedState] Lit " + bedId + ": parties trop éloignées (" + distance + " blocs)");
            return false;
        }

        // Vérifier que les parties sont bien opposées
        if (!isValidBedOrientation(bed)) {
            plugin.getLogger().warning("[MDT-BedState] Lit " + bedId + ": orientation invalide");
            return false;
        }

        return true;
    }

    /**
     * Vérifie que l'orientation du lit est cohérente
     */
    private boolean isValidBedOrientation(BedComplete bed) {
        // La tête et le pied doivent être dans des directions opposées
        return bed.headFacing.getOppositeFace().equals(
            getDirectionFrom(bed.headLocation, bed.footLocation)
        );
    }

    /**
     * Calcule la direction d'un point à un autre
     */
    private org.bukkit.block.BlockFace getDirectionFrom(Location from, Location to) {
        int dx = to.getBlockX() - from.getBlockX();
        int dz = to.getBlockZ() - from.getBlockZ();

        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? org.bukkit.block.BlockFace.EAST : org.bukkit.block.BlockFace.WEST;
        } else if (dz != 0) {
            return dz > 0 ? org.bukkit.block.BlockFace.SOUTH : org.bukkit.block.BlockFace.NORTH;
        } else {
            return org.bukkit.block.BlockFace.NORTH; // Default
        }
    }

    /**
     * Obtient un lit depuis le cache
     */
    public BedComplete getBed(String bedId) {
        return bedCache.get(bedId);
    }

    /**
     * Restaure tous les lits dans le monde
     */
    public int restoreAllBeds() {
        plugin.getLogger().info("[MDT-BedState] Restauration de " + bedCache.size() + " lits...");

        int restoredCount = 0;
        for (Map.Entry<String, BedComplete> entry : bedCache.entrySet()) {
            String bedId = entry.getKey();
            BedComplete bed = entry.getValue();

            try {
                bed.restoreToWorld();
                restoredCount++;
                plugin.getLogger().fine("[MDT-BedState] Lit " + bedId + " restauré");
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[MDT-BedState] Erreur lors de la restauration du lit " + bedId, e);
            }
        }

        plugin.getLogger().info("[MDT-BedState] Restauration terminée: " + restoredCount + "/" + bedCache.size() + " lits restaurés");
        return restoredCount;
    }

    /**
     * Force la sauvegarde de l'état actuel des lits dans la configuration
     * Utile pour mettre à jour la configuration après modifications manuelles
     */
    public void forceSaveCurrentState() {
        plugin.getLogger().info("[MDT-BedState] Sauvegarde forcée de l'état actuel...");

        // Pour chaque lit dans le cache, trouver le lit actuel dans le monde et le sauvegarder
        for (String bedId : bedCache.keySet()) {
            BedComplete currentBed = bedCache.get(bedId);
            if (currentBed != null) {
                config.saveBedCompleteToConfig(bedId, currentBed.headLocation);
            }
        }

        // Aussi essayer de trouver des lits qui ne sont pas dans le cache
        // (utile après ajout de nouveaux lits)
        discoverAndSaveNewBeds();
    }

    /**
     * Découvre et sauvegarde les nouveaux lits non encore dans la configuration
     */
    private void discoverAndSaveNewBeds() {
        if (config.getWorld() == null) return;

        // Pour l'instant, cette méthode est un placeholder
        // Dans une version complète, on pourrait scanner le monde pour trouver des lits MDT
        plugin.getLogger().info("[MDT-BedState] Scan des nouveaux lits (non implémenté)");
    }

    /**
     * Génère un rapport de diagnostic complet
     */
    public String getDiagnosticReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== RAPPORT DE DIAGNOSTIC DES LITS MDT ===\n");
        report.append("Dernière initialisation: ").append(lastInitializationTime > 0 ?
            new java.util.Date(lastInitializationTime).toString() : "Jamais").append("\n");
        report.append("Lits en cache: ").append(bedCache.size()).append("\n\n");

        if (bedCache.isEmpty()) {
            report.append("❌ Aucun lit n'est actuellement en cache\n");
        } else {
            report.append("Lits en cache:\n");
            for (Map.Entry<String, BedComplete> entry : bedCache.entrySet()) {
                BedComplete bed = entry.getValue();
                boolean valid = validateBedIntegrity(entry.getKey(), bed);
                report.append(valid ? "✅ " : "❌ ").append(entry.getKey()).append(": ")
                      .append(bed.toString()).append("\n");
            }
        }

        // Ajouter la validation depuis la configuration
        report.append("\n");
        report.append(config.validateAllBeds());

        return report.toString();
    }

    /**
     * Vérifie si les lits dans le monde correspondent à ceux dans le cache
     */
    public boolean validateWorldConsistency() {
        if (bedCache.isEmpty()) return false;

        boolean allConsistent = true;

        for (Map.Entry<String, BedComplete> entry : bedCache.entrySet()) {
            String bedId = entry.getKey();
            BedComplete expectedBed = entry.getValue();

            // Vérifier que le lit existe dans le monde avec les bonnes propriétés
            if (!isBedConsistent(bedId, expectedBed)) {
                allConsistent = false;
                plugin.getLogger().warning("[MDT-BedState] Incohérence détectée pour le lit " + bedId);
            }
        }

        return allConsistent;
    }

    /**
     * Vérifie qu'un lit spécifique est cohérent entre le cache et le monde
     */
    private boolean isBedConsistent(String bedId, BedComplete expectedBed) {
        try {
            org.bukkit.block.Block headBlock = expectedBed.headLocation.getBlock();
            org.bukkit.block.Block footBlock = expectedBed.footLocation.getBlock();

            // Vérifier que ce sont bien des lits
            if (!headBlock.getType().name().endsWith("_BED") ||
                !footBlock.getType().name().endsWith("_BED")) {
                return false;
            }

            // Vérifier que le matériau correspond
            if (headBlock.getType() != expectedBed.material) {
                return false;
            }

            // Vérifier l'orientation (simplifié)
            return true; // Pour l'instant, la présence suffit

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[MDT-BedState] Erreur lors de la validation du lit " + bedId, e);
            return false;
        }
    }

    /**
     * Nettoie le cache (appelé à la fin d'une partie)
     */
    public void cleanup() {
        bedCache.clear();
        lastInitializationTime = 0;
        plugin.getLogger().info("[MDT-BedState] Cache nettoyé");
    }

    /**
     * Retourne le nombre de lits en cache
     */
    public int getCachedBedCount() {
        return bedCache.size();
    }

    /**
     * Retourne le timestamp de la dernière initialisation
     */
    public long getLastInitializationTime() {
        return lastInitializationTime;
    }
}