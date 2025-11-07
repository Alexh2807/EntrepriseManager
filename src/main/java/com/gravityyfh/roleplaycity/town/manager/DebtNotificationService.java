package com.gravityyfh.roleplaycity.town.manager;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.Town;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service responsable de l'affichage de la bannière de dettes (format unique).
 * Agrège les dettes personnelles et professionnelles et garantit une seule bannière,
 * quel que soit le moment où la dette est détectée (en ligne ou hors-ligne).
 */
public class DebtNotificationService {

    private static final long GRACE_PERIOD_DAYS = 7L;
    private static final double EPSILON = 0.005d;
    private static final int LOGIN_DELAY_TICKS = 200;         // 10 secondes
    private static final int HOURLY_REFRESH_TICKS = 72000;    // 1 heure
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale.FRANCE);

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final Map<UUID, DebtState> debtStates;

    public DebtNotificationService(RoleplayCity plugin, TownManager townManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.debtStates = new ConcurrentHashMap<>();
    }

    /**
     * Raison de la mise à jour de la bannière.
     */
    public enum DebtUpdateReason {
        ECONOMY_EVENT,
        LOGIN_REFRESH,
        SCHEDULED_REFRESH,
        PAYMENT,
        ADMIN
    }

    private enum DebtTone {
        FIRST,
        UPDATE,
        IMMINENT
    }

    private static final class DebtState {
        private DebtSummary summary;
        private String fingerprint;
        private boolean pendingDisplay;
    }

    private static final class DebtEntry {
        private final String townName;
        private final String label;
        private final double amount;
        private final long hoursRemaining;
        private final boolean enterprise;
        private final String companyName;
        private final String companySiret;

        private DebtEntry(String townName,
                          String label,
                          double amount,
                          long hoursRemaining,
                          boolean enterprise,
                          String companyName,
                          String companySiret) {
            this.townName = townName;
            this.label = label;
            this.amount = amount;
            this.hoursRemaining = hoursRemaining;
            this.enterprise = enterprise;
            this.companyName = companyName;
            this.companySiret = companySiret;
        }

        private long getDaysRemainingFloor() {
            return Math.max(0L, hoursRemaining / 24L);
        }

        private boolean isImminent() {
            return hoursRemaining <= 24;
        }
    }

    private static final class DebtSummary {
        private final List<DebtEntry> enterpriseEntries;
        private final List<DebtEntry> personalEntries;
        private final double totalAmount;

        private DebtSummary(List<DebtEntry> enterpriseEntries,
                            List<DebtEntry> personalEntries,
                            double totalAmount) {
            this.enterpriseEntries = enterpriseEntries;
            this.personalEntries = personalEntries;
            this.totalAmount = totalAmount;
        }

        private boolean isEmpty() {
            return enterpriseEntries.isEmpty() && personalEntries.isEmpty();
        }

        private boolean hasImminentDebt() {
            return enterpriseEntries.stream().anyMatch(DebtEntry::isImminent)
                || personalEntries.stream().anyMatch(DebtEntry::isImminent);
        }

        private String fingerprint() {
            StringBuilder builder = new StringBuilder();
            builder.append(String.format(Locale.ROOT, "%.2f|", totalAmount));
            appendEntries(builder, enterpriseEntries);
            builder.append("|");
            appendEntries(builder, personalEntries);
            return builder.toString();
        }

        private void appendEntries(StringBuilder builder, List<DebtEntry> entries) {
            entries.stream()
                .sorted(Comparator.comparing(e -> e.townName + ":" + e.label))
                .forEach(entry -> builder.append(entry.townName)
                    .append("::")
                    .append(entry.label)
                    .append("::")
                    .append(String.format(Locale.ROOT, "%.2f", entry.amount))
                    .append("::")
                    .append(entry.hoursRemaining)
                    .append("||"));
        }
    }

    /**
     * Démarre les tâches planifiées (rafraîchissement horaire).
     */
    public void start() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            Set<UUID> targets = new LinkedHashSet<>(debtStates.keySet());
            Bukkit.getOnlinePlayers().forEach(player -> targets.add(player.getUniqueId()));
            for (UUID uuid : targets) {
                refresh(uuid, DebtUpdateReason.SCHEDULED_REFRESH);
            }
        }, HOURLY_REFRESH_TICKS, HOURLY_REFRESH_TICKS);
    }

    /**
     * À appeler lors de la connexion d'un joueur.
     */
    public void onPlayerLogin(Player player) {
        Objects.requireNonNull(player, "player");
        UUID uuid = player.getUniqueId();

        DebtState state = debtStates.computeIfAbsent(uuid, id -> new DebtState());
        state.pendingDisplay = true;

        // Rafraîchissement différé pour laisser le temps aux messages de connexion.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                refresh(uuid, DebtUpdateReason.LOGIN_REFRESH);
            }
        }, LOGIN_DELAY_TICKS);
    }

    /**
     * Rafraîchit la bannière de dettes pour un joueur donné.
     */
    public void refresh(UUID playerUuid, DebtUpdateReason reason) {
        if (playerUuid == null) {
            return;
        }

        Runnable task = () -> refreshInternal(playerUuid, reason);
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private void refreshInternal(UUID playerUuid, DebtUpdateReason reason) {
        DebtSummary summary = buildDebtSummary(playerUuid);
        DebtState state = debtStates.computeIfAbsent(playerUuid, id -> new DebtState());
        String previousFingerprint = state.fingerprint;
        boolean hadPrevious = state.summary != null && !state.summary.isEmpty();
        boolean forceDisplay = reason == DebtUpdateReason.LOGIN_REFRESH || state.pendingDisplay;

        if (summary.isEmpty()) {
            debtStates.remove(playerUuid);
            if (hadPrevious) {
                Player player = Bukkit.getPlayer(playerUuid);
                if (player != null && player.isOnline()) {
                    player.sendMessage("");
                    player.sendMessage(ChatColor.GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "✔ Dettes réglées !");
                    player.sendMessage(ChatColor.GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                }
            }
            return;
        }

        String fingerprint = summary.fingerprint();
        boolean changed = !Objects.equals(fingerprint, previousFingerprint);

        if (!forceDisplay && !changed && !summary.hasImminentDebt()) {
            // Rien de nouveau à afficher
            return;
        }

        DebtTone tone;
        if (summary.hasImminentDebt()) {
            tone = DebtTone.IMMINENT;
        } else if (!hadPrevious || previousFingerprint == null || reason == DebtUpdateReason.LOGIN_REFRESH) {
            tone = DebtTone.FIRST;
        } else {
            tone = DebtTone.UPDATE;
        }

        state.summary = summary;
        state.fingerprint = fingerprint;
        state.pendingDisplay = false;

        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && player.isOnline()) {
            sendDebtBanner(player, summary, tone);
        } else {
            state.pendingDisplay = true;
        }
    }

    private DebtSummary buildDebtSummary(UUID playerUuid) {
        List<DebtEntry> enterpriseEntries = new ArrayList<>();
        List<DebtEntry> personalEntries = new ArrayList<>();
        double total = 0.0;
        LocalDateTime now = LocalDateTime.now();

        for (Town town : townManager.getAllTowns()) {
            List<Town.PlayerDebt> debts = town.getPlayerDebts(playerUuid);
            if (debts.isEmpty()) {
                continue;
            }

            for (Town.PlayerDebt debt : debts) {
                Plot plot = debt.getPlot();
                if (plot == null) {
                    continue;
                }

                double amount = roundCurrency(debt.getAmount());
                if (amount <= EPSILON) {
                    continue;
                }

                boolean enterpriseDebt = isEnterpriseDebt(plot, debt);
                LocalDateTime warningDate = debt.getWarningDate();
                if (warningDate == null) {
                    warningDate = enterpriseDebt ? plot.getLastDebtWarningDate() : plot.getParticularLastDebtWarningDate();
                }

                long hoursRemaining = computeRemainingHours(warningDate, now);
                String label = buildDebtLabel(town, debt, plot);

                if (enterpriseDebt) {
                    String companyName = resolveCompanyName(plot);
                    String siret = resolveCompanySiret(plot);
                    enterpriseEntries.add(new DebtEntry(
                        town.getName(),
                        label,
                        amount,
                        hoursRemaining,
                        true,
                        companyName,
                        siret
                    ));
                } else {
                    personalEntries.add(new DebtEntry(
                        town.getName(),
                        label,
                        amount,
                        hoursRemaining,
                        false,
                        null,
                        null
                    ));
                }

                total += amount;
            }
        }

        enterpriseEntries.sort(Comparator.comparing(entry -> entry.townName + ":" + entry.label));
        personalEntries.sort(Comparator.comparing(entry -> entry.townName + ":" + entry.label));

        return new DebtSummary(enterpriseEntries, personalEntries, roundCurrency(total));
    }

    private boolean isEnterpriseDebt(Plot plot, Town.PlayerDebt debt) {
        double companyAmount = plot.getCompanyDebtAmount();
        double amount = debt.getAmount();
        if (companyAmount > EPSILON && Math.abs(companyAmount - amount) <= EPSILON) {
            return true;
        }
        return plot.getCompanySiret() != null
            || plot.getCompanyName() != null
            || plot.getRenterCompanySiret() != null;
    }

    private String resolveCompanyName(Plot plot) {
        String companyName = plot.getCompanyName();
        if (companyName != null && !companyName.isBlank()) {
            return companyName;
        }
        return "Entreprise";
    }

    private String resolveCompanySiret(Plot plot) {
        if (plot.getCompanySiret() != null && !plot.getCompanySiret().isBlank()) {
            return plot.getCompanySiret();
        }
        if (plot.getRenterCompanySiret() != null && !plot.getRenterCompanySiret().isBlank()) {
            return plot.getRenterCompanySiret();
        }
        return null;
    }

    private long computeRemainingHours(LocalDateTime warningDate, LocalDateTime now) {
        if (warningDate == null) {
            return GRACE_PERIOD_DAYS * 24L;
        }
        LocalDateTime deadline = warningDate.plusDays(GRACE_PERIOD_DAYS);
        if (!deadline.isAfter(now)) {
            return 0L;
        }
        return Math.max(0L, ChronoUnit.HOURS.between(now, deadline));
    }

    private double roundCurrency(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }

    private String buildDebtLabel(Town town, Town.PlayerDebt debt, Plot plot) {
        if (plot.isGrouped()) {
            String groupName = plot.getGroupName();
            if (groupName != null) {
                return town.getName() + " • " + groupName;
            }
            return town.getName() + " • Groupe";
        }
        return town.getName() + " • Parcelle " + plot.getCoordinates();
    }

    private void sendDebtBanner(Player player, DebtSummary summary, DebtTone tone) {
        Set<String> towns = new LinkedHashSet<>();
        summary.enterpriseEntries.forEach(entry -> towns.add(entry.townName));
        summary.personalEntries.forEach(entry -> towns.add(entry.townName));

        String headerSuffix;
        if (towns.isEmpty()) {
            headerSuffix = "";
        } else if (towns.size() == 1) {
            headerSuffix = " - " + towns.iterator().next();
        } else {
            headerSuffix = " - Multi-villes";
        }

        player.sendMessage("");
        player.sendMessage(ChatColor.RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "⚠ ALERTE DETTE" + headerSuffix);
        player.sendMessage(ChatColor.RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage(ChatColor.WHITE + "Vous n'avez pas eu assez de fonds pour payer vos taxes.");
        player.sendMessage(ChatColor.WHITE + "Vous êtes maintenant en dette d'un total de "
            + ChatColor.RED + formatCurrency(summary.totalAmount) + ChatColor.WHITE + ".");
        player.sendMessage("");
        player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Dettes en cours :");

        if (!summary.enterpriseEntries.isEmpty()) {
            player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Dettes Entreprises");
            for (DebtEntry entry : summary.enterpriseEntries) {
                String companyName = entry.companyName != null ? entry.companyName : "Entreprise";
                String siretLine = entry.companySiret != null ? ChatColor.GRAY + " (SIRET: " + entry.companySiret + ")" : "";

                player.sendMessage(ChatColor.GOLD + "Entreprise " + ChatColor.YELLOW + companyName + siretLine);
                player.sendMessage(ChatColor.WHITE + "Parcelle/Groupe: " + ChatColor.GRAY + entry.label);
                player.sendMessage(ChatColor.WHITE + "Montant dû: " + ChatColor.GOLD + formatCurrency(entry.amount));
                player.sendMessage(ChatColor.WHITE + "Temps avant saisie: " + formatDeadline(entry));
                player.sendMessage("");
            }
        }

        if (!summary.personalEntries.isEmpty()) {
            player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Dettes Personnelles");
            for (DebtEntry entry : summary.personalEntries) {
                player.sendMessage(ChatColor.AQUA + "Terrain personnel " + ChatColor.GRAY + entry.label);
                player.sendMessage(ChatColor.WHITE + "Montant dû: " + ChatColor.GOLD + formatCurrency(entry.amount));
                player.sendMessage(ChatColor.WHITE + "Temps avant saisie: " + formatDeadline(entry));
                player.sendMessage("");
            }
        }

        player.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "➤ Règlez vos dettes via :");
        player.sendMessage(ChatColor.GRAY + "  /ville → Régler mes dettes !");
        player.sendMessage(ChatColor.RED + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");

        sendDebtActionBar(player, tone);
    }

    private void sendDebtActionBar(Player player, DebtTone tone) {
        String message;
        Sound sound;
        float pitch;

        switch (tone) {
            case IMMINENT -> {
                message = ChatColor.DARK_RED + "" + ChatColor.BOLD + "⚠⚠ SAISIE IMMINENTE ⚠⚠";
                sound = Sound.ENTITY_ENDER_DRAGON_GROWL;
                pitch = 0.8f;
            }
            case UPDATE -> {
                message = ChatColor.YELLOW + "Dette mise à jour";
                sound = Sound.BLOCK_NOTE_BLOCK_BELL;
                pitch = 1.2f;
            }
            case FIRST -> {
                message = ChatColor.RED + "" + ChatColor.BOLD + "⚠ Nouvelle dette !";
                sound = Sound.BLOCK_ANVIL_LAND;
                pitch = 0.6f;
            }
            default -> {
                message = ChatColor.YELLOW + "Dette mise à jour";
                sound = Sound.BLOCK_NOTE_BLOCK_BELL;
                pitch = 1.0f;
            }
        }

        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
        player.playSound(player.getLocation(), sound, 1.0f, pitch);
    }

    private String formatDeadline(DebtEntry entry) {
        if (entry.hoursRemaining <= 0) {
            return ChatColor.DARK_RED + "Saisie imminente";
        }
        if (entry.hoursRemaining <= 24) {
            return ChatColor.RED + "Moins de 24h";
        }
        long days = Math.max(1L, entry.getDaysRemainingFloor());
        return ChatColor.RED + "" + days + " jour(s)";
    }

    private String formatCurrency(double amount) {
        synchronized (CURRENCY_FORMAT) {
            return CURRENCY_FORMAT.format(amount);
        }
    }
}

