package com.gravityyfh.entreprisemanager.Services;

import com.gravityyfh.entreprisemanager.EntrepriseManager;
import com.gravityyfh.entreprisemanager.EntrepriseManagerLogic;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TaskService {

    private final EntrepriseManager plugin;
    private final FinanceService financeService;
    private final ActivityService activityService;

    private BukkitTask hourlyTask;
    private BukkitTask activityCheckTask;
    private LocalDateTime nextPaymentTime;

    private static final long ACTIVITY_CHECK_INTERVAL_TICKS = 20L * 10L;

    public TaskService(EntrepriseManagerLogic logic, FinanceService financeService, ActivityService activityService) {
        this.plugin = logic.plugin;
        this.financeService = financeService;
        this.activityService = activityService;
    }

    public void scheduleAllTasks() {
        scheduleHourlyTasks();
        scheduleActivityCheckTask();
    }

    public void cancelAllTasks() {
        cancelTask(hourlyTask);
        cancelTask(activityCheckTask);
    }

    public LocalDateTime getNextPaymentTime() {
        return nextPaymentTime;
    }

    private void scheduleHourlyTasks() {
        cancelTask(hourlyTask);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextFullHour = now.withMinute(0).withSecond(0).withNano(0).plusHours(1);
        long initialDelayTicks = Duration.between(now, nextFullHour).toSeconds() * 20L;

        if (initialDelayTicks <= 0) {
            initialDelayTicks = 20L * 3600L; // 1 heure en ticks
            nextFullHour = nextFullHour.plusHours(1);
        }
        this.nextPaymentTime = nextFullHour;
        long periodTicks = 20L * 3600L;

        hourlyTask = new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getLogger().info("Exécution des tâches horaires automatiques...");
                financeService.processHourlyRevenue();
                financeService.payHourlyWages();
                financeService.payPayrollTaxes();
                financeService.payUnemploymentBenefits();
                activityService.resetHourlyLimitsForAllPlayers();
                plugin.getLogger().info("Tâches horaires terminées.");
                nextPaymentTime = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0).plusHours(1);
            }
        }.runTaskTimer(plugin, initialDelayTicks, periodTicks);

        plugin.getLogger().info("Tâches horaires planifiées. Prochaine exécution vers : " + nextPaymentTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }

    private void scheduleActivityCheckTask() {
        cancelTask(activityCheckTask);
        activityCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                activityService.checkEmployeeActivity();
            }
        }.runTaskTimerAsynchronously(plugin, ACTIVITY_CHECK_INTERVAL_TICKS, ACTIVITY_CHECK_INTERVAL_TICKS);
    }

    private void cancelTask(BukkitTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }
}