package com.gravityyfh.roleplaycity.util;

import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * FIX BASSE #28: Système de métriques de performance
 *
 * Cette classe permet de mesurer et logger le temps d'exécution des opérations critiques.
 * Logs automatiques si une opération dépasse un seuil configurable.
 */
public class PerformanceMetrics {

    private final Plugin plugin;
    private final Map<String, MetricStats> metrics;
    private boolean enabled;

    // Seuils de temps en millisecondes
    private static final long DEFAULT_WARNING_THRESHOLD_MS = 100;
    private static final long SAVE_WARNING_THRESHOLD_MS = 500;
    private static final long LOAD_WARNING_THRESHOLD_MS = 1000;

    public PerformanceMetrics(Plugin plugin) {
        this.plugin = plugin;
        this.metrics = new ConcurrentHashMap<>();
        this.enabled = plugin.getConfig().getBoolean("system.performance-metrics", true);
    }

    /**
     * Statistiques d'une métrique
     */
    private static class MetricStats {
        long totalCalls = 0;
        long totalTime = 0;
        long minTime = Long.MAX_VALUE;
        long maxTime = 0;
        long warningCount = 0;

        void record(long timeMs, boolean warning) {
            totalCalls++;
            totalTime += timeMs;
            minTime = Math.min(minTime, timeMs);
            maxTime = Math.max(maxTime, timeMs);
            if (warning) warningCount++;
        }

        double getAverageMs() {
            return totalCalls > 0 ? (double) totalTime / totalCalls : 0;
        }
    }

    /**
     * Contexte de mesure de performance
     */
    public static class Timer {
        private final PerformanceMetrics metrics;
        private final String operationName;
        private final long startTime;
        private final long warningThreshold;

        private Timer(PerformanceMetrics metrics, String operationName, long warningThreshold) {
            this.metrics = metrics;
            this.operationName = operationName;
            this.startTime = System.currentTimeMillis();
            this.warningThreshold = warningThreshold;
        }

        /**
         * Termine la mesure et enregistre le résultat
         */
        public void stop() {
            long elapsed = System.currentTimeMillis() - startTime;
            metrics.record(operationName, elapsed, warningThreshold);
        }

        /**
         * Termine la mesure et retourne le temps écoulé
         * @return Temps écoulé en millisecondes
         */
        public long stopAndGet() {
            long elapsed = System.currentTimeMillis() - startTime;
            metrics.record(operationName, elapsed, warningThreshold);
            return elapsed;
        }
    }

    /**
     * Démarre la mesure d'une opération avec seuil par défaut
     * @param operationName Nom de l'opération
     * @return Timer à stopper à la fin de l'opération
     */
    public Timer start(String operationName) {
        return start(operationName, DEFAULT_WARNING_THRESHOLD_MS);
    }

    /**
     * Démarre la mesure d'une opération avec seuil personnalisé
     * @param operationName Nom de l'opération
     * @param warningThresholdMs Seuil en ms au-delà duquel logger un warning
     * @return Timer à stopper à la fin de l'opération
     */
    public Timer start(String operationName, long warningThresholdMs) {
        if (!enabled) {
            // Timer no-op si désactivé
            return new Timer(this, operationName, Long.MAX_VALUE);
        }
        return new Timer(this, operationName, warningThresholdMs);
    }

    /**
     * Démarre la mesure d'une sauvegarde
     */
    public Timer startSave(String saveType) {
        return start("save_" + saveType, SAVE_WARNING_THRESHOLD_MS);
    }

    /**
     * Démarre la mesure d'un chargement
     */
    public Timer startLoad(String loadType) {
        return start("load_" + loadType, LOAD_WARNING_THRESHOLD_MS);
    }

    /**
     * Enregistre un temps mesuré
     */
    private void record(String operationName, long elapsedMs, long warningThreshold) {
        if (!enabled) return;

        boolean isWarning = elapsedMs > warningThreshold;

        // Mise à jour des statistiques
        metrics.computeIfAbsent(operationName, k -> new MetricStats())
               .record(elapsedMs, isWarning);

        // Log si dépasse le seuil
        if (isWarning) {
            plugin.getLogger().warning(String.format(
                "[Performance] Operation '%s' took %dms (threshold: %dms)",
                operationName, elapsedMs, warningThreshold
            ));
        } else {
            plugin.getLogger().fine(String.format(
                "[Performance] Operation '%s' completed in %dms",
                operationName, elapsedMs
            ));
        }
    }

    /**
     * Affiche un rapport des métriques
     */
    public void printReport() {
        if (metrics.isEmpty()) {
            plugin.getLogger().info("[Performance] Aucune métrique enregistrée");
            return;
        }

        plugin.getLogger().info("=== Rapport de Performance ===");

        for (Map.Entry<String, MetricStats> entry : metrics.entrySet()) {
            String name = entry.getKey();
            MetricStats stats = entry.getValue();

            plugin.getLogger().info(String.format(
                "  %s: calls=%d avg=%.1fms min=%dms max=%dms warnings=%d",
                name, stats.totalCalls, stats.getAverageMs(),
                stats.minTime, stats.maxTime, stats.warningCount
            ));
        }

        plugin.getLogger().info("==============================");
    }

    /**
     * Réinitialise toutes les métriques
     */
    public void reset() {
        metrics.clear();
        plugin.getLogger().info("[Performance] Métriques réinitialisées");
    }

    /**
     * Retourne les statistiques d'une opération
     */
    public Map<String, Object> getStats(String operationName) {
        MetricStats stats = metrics.get(operationName);
        if (stats == null) return null;

        Map<String, Object> result = new HashMap<>();
        result.put("calls", stats.totalCalls);
        result.put("avgMs", stats.getAverageMs());
        result.put("minMs", stats.minTime);
        result.put("maxMs", stats.maxTime);
        result.put("warnings", stats.warningCount);
        return result;
    }

    /**
     * Active ou désactive les métriques
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        plugin.getLogger().info("[Performance] Métriques " + (enabled ? "activées" : "désactivées"));
    }

    public boolean isEnabled() {
        return enabled;
    }
}
