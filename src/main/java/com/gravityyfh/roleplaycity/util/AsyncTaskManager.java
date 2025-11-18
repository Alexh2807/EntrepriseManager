package com.gravityyfh.roleplaycity.util;

import org.bukkit.plugin.Plugin;

import java.util.concurrent.*;
import java.util.logging.Level;

/**
 * FIX BASSE #10: Gestionnaire de pool de threads pour les tâches asynchrones
 *
 * Avantages:
 * - Réutilisation des threads au lieu de créer un nouveau thread à chaque tâche
 * - Limite le nombre de threads concurrents pour éviter la surcharge
 * - Meilleure gestion des exceptions et du shutdown
 * - Statistiques et monitoring des tâches
 */
public class AsyncTaskManager {

    private final Plugin plugin;
    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutor;

    // Statistiques
    private final java.util.concurrent.atomic.AtomicLong tasksSubmitted = new java.util.concurrent.atomic.AtomicLong(0);
    private final java.util.concurrent.atomic.AtomicLong tasksCompleted = new java.util.concurrent.atomic.AtomicLong(0);
    private final java.util.concurrent.atomic.AtomicLong tasksFailed = new java.util.concurrent.atomic.AtomicLong(0);

    // Configuration
    private static final int CORE_POOL_SIZE = 2;
    private static final int MAX_POOL_SIZE = 4;
    private static final long KEEP_ALIVE_TIME = 60L;
    private static final TimeUnit KEEP_ALIVE_UNIT = TimeUnit.SECONDS;

    public AsyncTaskManager(Plugin plugin) {
        this.plugin = plugin;

        // Thread pool principal avec taille dynamique
        ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
            CORE_POOL_SIZE,
            MAX_POOL_SIZE,
            KEEP_ALIVE_TIME,
            KEEP_ALIVE_UNIT,
            new LinkedBlockingQueue<>(),
            new NamedThreadFactory("RoleplayCity-Async"),
            new ThreadPoolExecutor.CallerRunsPolicy() // Si saturé, exécuter dans le thread appelant
        );

        // Permettre aux threads core de timeout
        threadPool.allowCoreThreadTimeOut(true);

        this.executorService = threadPool;

        // Executor pour les tâches planifiées
        this.scheduledExecutor = Executors.newScheduledThreadPool(
            1,
            new NamedThreadFactory("RoleplayCity-Scheduled")
        );

        plugin.getLogger().info("[AsyncTaskManager] Pool de threads initialisé (core=" + CORE_POOL_SIZE + ", max=" + MAX_POOL_SIZE + ")");
    }

    /**
     * Exécute une tâche de manière asynchrone
     *
     * @param task La tâche à exécuter
     * @return Future représentant la tâche en cours
     */
    public Future<?> runAsync(Runnable task) {
        tasksSubmitted.incrementAndGet();

        return executorService.submit(() -> {
            try {
                task.run();
                tasksCompleted.incrementAndGet();
            } catch (Exception e) {
                tasksFailed.incrementAndGet();
                plugin.getLogger().log(Level.SEVERE, "Erreur lors de l'exécution d'une tâche asynchrone", e);
            }
        });
    }

    /**
     * Exécute une tâche de manière asynchrone avec un résultat
     *
     * @param task La tâche à exécuter
     * @param <T> Type du résultat
     * @return Future contenant le résultat
     */
    public <T> Future<T> submitAsync(Callable<T> task) {
        tasksSubmitted.incrementAndGet();

        return executorService.submit(() -> {
            try {
                T result = task.call();
                tasksCompleted.incrementAndGet();
                return result;
            } catch (Exception e) {
                tasksFailed.incrementAndGet();
                plugin.getLogger().log(Level.SEVERE, "Erreur lors de l'exécution d'une tâche asynchrone", e);
                throw e;
            }
        });
    }

    /**
     * Planifie une tâche à exécuter après un délai
     *
     * @param task La tâche à exécuter
     * @param delay Le délai avant exécution
     * @param unit L'unité de temps du délai
     * @return ScheduledFuture représentant la tâche planifiée
     */
    public ScheduledFuture<?> runDelayed(Runnable task, long delay, TimeUnit unit) {
        tasksSubmitted.incrementAndGet();

        return scheduledExecutor.schedule(() -> {
            try {
                task.run();
                tasksCompleted.incrementAndGet();
            } catch (Exception e) {
                tasksFailed.incrementAndGet();
                plugin.getLogger().log(Level.SEVERE, "Erreur lors de l'exécution d'une tâche planifiée", e);
            }
        }, delay, unit);
    }

    /**
     * Planifie une tâche à exécuter de manière répétée
     *
     * @param task La tâche à exécuter
     * @param initialDelay Le délai avant la première exécution
     * @param period L'intervalle entre les exécutions
     * @param unit L'unité de temps
     * @return ScheduledFuture représentant la tâche répétée
     */
    public ScheduledFuture<?> runRepeating(Runnable task, long initialDelay, long period, TimeUnit unit) {
        return scheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                task.run();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Erreur lors de l'exécution d'une tâche répétée", e);
            }
        }, initialDelay, period, unit);
    }

    /**
     * Arrête proprement le gestionnaire de tâches
     */
    public void shutdown() {
        plugin.getLogger().info("[AsyncTaskManager] Arrêt du pool de threads...");

        // Arrêter d'accepter de nouvelles tâches
        executorService.shutdown();
        scheduledExecutor.shutdown();

        try {
            // Attendre la fin des tâches en cours (max 10 secondes)
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("[AsyncTaskManager] Timeout - arrêt forcé des tâches restantes");
                executorService.shutdownNow();
            }

            if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }

            plugin.getLogger().info("[AsyncTaskManager] Pool de threads arrêté (complétées: " +
                tasksCompleted.get() + ", échouées: " + tasksFailed.get() + ")");

        } catch (InterruptedException e) {
            plugin.getLogger().warning("[AsyncTaskManager] Interruption lors de l'arrêt");
            executorService.shutdownNow();
            scheduledExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Retourne les statistiques du gestionnaire
     */
    public String getStats() {
        ThreadPoolExecutor tpe = (ThreadPoolExecutor) executorService;

        return String.format(
            "AsyncTaskManager Stats: soumises=%d, complétées=%d, échouées=%d, active=%d, en_queue=%d, pool_size=%d/%d",
            tasksSubmitted.get(),
            tasksCompleted.get(),
            tasksFailed.get(),
            tpe.getActiveCount(),
            tpe.getQueue().size(),
            tpe.getPoolSize(),
            tpe.getMaximumPoolSize()
        );
    }

    /**
     * Nombre de tâches soumises
     */
    public long getTasksSubmitted() {
        return tasksSubmitted.get();
    }

    /**
     * Nombre de tâches complétées
     */
    public long getTasksCompleted() {
        return tasksCompleted.get();
    }

    /**
     * Nombre de tâches échouées
     */
    public long getTasksFailed() {
        return tasksFailed.get();
    }

    /**
     * Nombre de tâches actuellement en cours d'exécution
     */
    public int getActiveTasks() {
        return ((ThreadPoolExecutor) executorService).getActiveCount();
    }

    /**
     * Nombre de tâches en attente dans la queue
     */
    public int getQueuedTasks() {
        return ((ThreadPoolExecutor) executorService).getQueue().size();
    }

    /**
     * Factory pour créer des threads avec des noms descriptifs
     */
    private static class NamedThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private final java.util.concurrent.atomic.AtomicInteger threadNumber = new java.util.concurrent.atomic.AtomicInteger(1);

        public NamedThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, namePrefix + "-" + threadNumber.getAndIncrement());
            thread.setDaemon(true); // Daemon thread pour éviter de bloquer le shutdown
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        }
    }
}
