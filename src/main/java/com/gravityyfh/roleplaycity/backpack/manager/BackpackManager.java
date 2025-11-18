package com.gravityyfh.roleplaycity.backpack.manager;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.backpack.model.BackpackData;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Gestionnaire principal pour les backpacks avec système de cache et batch processing
 */
public class BackpackManager {
    private final RoleplayCity plugin;
    private final BackpackItemManager itemManager;
    private final Logger logger;

    // Cache LRU pour les backpacks (100 entrées max)
    private final Map<UUID, CachedBackpack> backpackCache;
    private static final int MAX_CACHE_SIZE = 100;
    private static final long CACHE_TTL = 5 * 60 * 1000; // 5 minutes

    // ⚡ OPTIMISATION: Batch processing pour sauvegardes groupées
    private final Queue<SaveTask> saveQueue;
    private final AtomicInteger saveCounter;
    private static final int BATCH_SIZE = 10; // Nombre de sauvegardes par batch
    private static final long BATCH_INTERVAL = 20L; // 1 seconde (20 ticks)

    // Métriques de performance
    private long totalSaves = 0;
    private long totalSaveTime = 0;
    private long slowSaves = 0;

    public BackpackManager(RoleplayCity plugin, BackpackItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.logger = plugin.getLogger();

        // Initialiser le cache LRU (thread-safe pour async)
        this.backpackCache = Collections.synchronizedMap(
            new LinkedHashMap<UUID, CachedBackpack>(MAX_CACHE_SIZE + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, CachedBackpack> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            }
        );

        // ⚡ Initialiser le système de batch processing
        this.saveQueue = new ConcurrentLinkedQueue<>();
        this.saveCounter = new AtomicInteger(0);

        // Tâche de nettoyage du cache toutes les 5 minutes
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::cleanCache, 6000L, 6000L);

        // ⚡ Tâche de batch processing toutes les secondes
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::processSaveBatch, BATCH_INTERVAL, BATCH_INTERVAL);

        // Afficher les métriques toutes les 5 minutes
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::logMetrics, 6000L, 6000L);
    }

    /**
     * Charge les données d'un backpack depuis un ItemStack
     *
     * @param item L'item backpack
     * @return Les données du backpack, ou null si invalide
     */
    public BackpackData loadBackpack(ItemStack item) {
        if (!itemManager.isBackpack(item)) {
            return null;
        }

        UUID backpackId = itemManager.getBackpackUUID(item);
        if (backpackId == null) {
            return null;
        }

        // Vérifier le cache
        CachedBackpack cached = backpackCache.get(backpackId);
        if (cached != null && !cached.isExpired()) {
            logger.fine("Backpack " + backpackId + " chargé depuis le cache");
            return cached.data;
        }

        // Charger depuis l'item
        String serializedContent = itemManager.getBackpackContent(item);

        // Obtenir la taille du type de backpack
        var type = itemManager.getBackpackType(item);
        int size = type != null ? type.getSize() : 27; // Défaut: 3 lignes

        ItemStack[] contents = BackpackSerializer.deserialize(serializedContent, size);
        BackpackData data = new BackpackData(backpackId, contents, System.currentTimeMillis(), size);

        // Mettre en cache
        backpackCache.put(backpackId, new CachedBackpack(data));
        logger.fine("Backpack " + backpackId + " chargé et mis en cache");

        return data;
    }

    /**
     * Sauvegarde les données d'un backpack dans un ItemStack
     *
     * @param item L'item backpack
     * @param data Les données à sauvegarder
     * @return true si réussi, false sinon
     */
    public boolean saveBackpack(ItemStack item, BackpackData data) {
        if (!itemManager.isBackpack(item)) {
            return false;
        }

        // Sérialiser le contenu
        String serialized = BackpackSerializer.serialize(data.contents());
        if (serialized == null) {
            logger.warning("Échec de la sérialisation du backpack " + data.uniqueId());
            return false;
        }

        // Sauvegarder dans l'item
        boolean success = itemManager.setBackpackContent(item, serialized);

        if (success) {
            // Mettre à jour le cache
            backpackCache.put(data.uniqueId(), new CachedBackpack(data));
            logger.fine("Backpack " + data.uniqueId() + " sauvegardé avec succès");
        } else {
            logger.warning("Échec de la sauvegarde du backpack " + data.uniqueId());
        }

        return success;
    }

    /**
     * ⚡ OPTIMISATION: Sauvegarde asynchrone avec batch processing
     *
     * @param item L'item backpack
     * @param data Les données à sauvegarder
     * @return true si réussi, false sinon
     */
    public boolean saveBackpackAsync(ItemStack item, BackpackData data) {
        long startTime = System.nanoTime();

        boolean success = saveBackpack(item, data);

        // Métriques de performance
        long duration = (System.nanoTime() - startTime) / 1_000_000; // ms
        totalSaves++;
        totalSaveTime += duration;

        if (duration > 50) {
            slowSaves++;
        }

        saveCounter.incrementAndGet();

        return success;
    }

    /**
     * ⚡ OPTIMISATION: Ajoute une sauvegarde à la queue pour batch processing
     *
     * @param item L'item backpack
     * @param data Les données à sauvegarder
     */
    public void queueSave(ItemStack item, BackpackData data) {
        saveQueue.offer(new SaveTask(item, data));
    }

    /**
     * ⚡ OPTIMISATION: Traite un batch de sauvegardes
     */
    private void processSaveBatch() {
        int processed = 0;
        long startTime = System.nanoTime();

        while (processed < BATCH_SIZE && !saveQueue.isEmpty()) {
            SaveTask task = saveQueue.poll();
            if (task != null) {
                saveBackpack(task.item, task.data);
                processed++;
            }
        }

        if (processed > 0) {
            long duration = (System.nanoTime() - startTime) / 1_000_000;
            logger.fine("[Backpack] Batch processing: " + processed + " sauvegardes en " + duration + "ms");
        }
    }

    /**
     * Invalide l'entrée du cache pour un backpack
     *
     * @param backpackId L'UUID du backpack
     */
    public void invalidateCache(UUID backpackId) {
        backpackCache.remove(backpackId);
        logger.fine("Cache invalidé pour backpack " + backpackId);
    }

    /**
     * Nettoie les entrées expirées du cache
     */
    private void cleanCache() {
        backpackCache.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired()) {
                logger.fine("Entrée de cache expirée supprimée: " + entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Vide complètement le cache
     */
    public void clearCache() {
        backpackCache.clear();
        logger.info("Cache des backpacks vidé");
    }

    /**
     * Obtient le nombre d'entrées dans le cache
     *
     * @return La taille du cache
     */
    public int getCacheSize() {
        return backpackCache.size();
    }

    /**
     * ⚡ OPTIMISATION: Affiche les métriques de performance
     */
    private void logMetrics() {
        if (totalSaves > 0) {
            long avgTime = totalSaveTime / totalSaves;
            double slowPercent = (slowSaves * 100.0) / totalSaves;

            logger.info(String.format(
                "[Backpack] Métriques (5 min): %d sauvegardes | Temps moyen: %dms | Lentes (>50ms): %.1f%%",
                totalSaves, avgTime, slowPercent
            ));

            // Avertissement si beaucoup de sauvegardes lentes
            if (slowPercent > 10) {
                logger.warning("[Backpack] ATTENTION: " + slowPercent + "% de sauvegardes lentes ! Envisager la compression.");
            }

            // Reset des métriques
            totalSaves = 0;
            totalSaveTime = 0;
            slowSaves = 0;
        }

        // Afficher l'état de la queue
        int queueSize = saveQueue.size();
        if (queueSize > 0) {
            logger.info("[Backpack] Queue de sauvegarde: " + queueSize + " en attente");
        }

        // Afficher l'état du cache
        logger.info("[Backpack] Cache: " + backpackCache.size() + "/" + MAX_CACHE_SIZE + " entrées");
    }

    /**
     * ⚡ OPTIMISATION: Obtient les statistiques de performance
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_saves", totalSaves);
        stats.put("avg_save_time_ms", totalSaves > 0 ? totalSaveTime / totalSaves : 0);
        stats.put("slow_saves", slowSaves);
        stats.put("cache_size", backpackCache.size());
        stats.put("queue_size", saveQueue.size());
        stats.put("saves_counter", saveCounter.get());
        return stats;
    }

    /**
     * Classe interne pour stocker les backpacks en cache avec TTL
     */
    private static class CachedBackpack {
        private final BackpackData data;
        private final long cachedAt;

        public CachedBackpack(BackpackData data) {
            this.data = data;
            this.cachedAt = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - cachedAt > CACHE_TTL;
        }
    }

    /**
         * ⚡ OPTIMISATION: Classe pour les tâches de sauvegarde en batch
         */
        private record SaveTask(ItemStack item, BackpackData data) {
    }
}
