package com.gravityyfh.roleplaycity.town.data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Représente une amende émise par la police
 */
public class Fine {

    public enum FineStatus {
        PENDING("En attente"),
        PAID("Payée"),
        CONTESTED("Contestée"),
        CANCELLED("Annulée"),
        JUDGED_VALID("Confirmée par juge"),
        JUDGED_INVALID("Annulée par juge");

        private final String displayName;

        FineStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final UUID fineId;
    private final String townName;
    private final UUID offenderUuid;
    private final String offenderName;
    private final UUID policierUuid;
    private final String policierName;
    private final String reason;
    private final double amount;
    private final LocalDateTime issueDate;
    private FineStatus status;
    private LocalDateTime paidDate;
    private UUID judgeUuid;
    private String judgeVerdict;
    private LocalDateTime judgeDate;

    public Fine(String townName, UUID offenderUuid, String offenderName,
                UUID policierUuid, String policierName, String reason, double amount) {
        this.fineId = UUID.randomUUID();
        this.townName = townName;
        this.offenderUuid = offenderUuid;
        this.offenderName = offenderName;
        this.policierUuid = policierUuid;
        this.policierName = policierName;
        this.reason = reason;
        this.amount = amount;
        this.issueDate = LocalDateTime.now();
        this.status = FineStatus.PENDING;
    }

    // Constructor pour le chargement depuis YAML
    public Fine(UUID fineId, String townName, UUID offenderUuid, String offenderName,
                UUID policierUuid, String policierName, String reason, double amount,
                LocalDateTime issueDate, FineStatus status) {
        this.fineId = fineId;
        this.townName = townName;
        this.offenderUuid = offenderUuid;
        this.offenderName = offenderName;
        this.policierUuid = policierUuid;
        this.policierName = policierName;
        this.reason = reason;
        this.amount = amount;
        this.issueDate = issueDate;
        this.status = status;
    }

    public void markAsPaid() {
        this.status = FineStatus.PAID;
        this.paidDate = LocalDateTime.now();
    }

    public void markAsContested() {
        if (this.status == FineStatus.PENDING) {
            this.status = FineStatus.CONTESTED;
        }
    }

    public void setJudgeVerdict(UUID judgeUuid, boolean valid, String verdict) {
        this.judgeUuid = judgeUuid;
        this.judgeVerdict = verdict;
        this.judgeDate = LocalDateTime.now();
        this.status = valid ? FineStatus.JUDGED_VALID : FineStatus.JUDGED_INVALID;
    }

    public void cancel() {
        this.status = FineStatus.CANCELLED;
    }

    public boolean canBeContested() {
        return status == FineStatus.PENDING && issueDate.plusDays(7).isAfter(LocalDateTime.now());
    }

    public boolean isPending() {
        return status == FineStatus.PENDING || status == FineStatus.JUDGED_VALID;
    }

    public boolean isContested() {
        return status == FineStatus.CONTESTED;
    }

    // Getters
    public UUID getFineId() { return fineId; }
    public String getTownName() { return townName; }
    public UUID getOffenderUuid() { return offenderUuid; }
    public String getOffenderName() { return offenderName; }
    public UUID getPolicierUuid() { return policierUuid; }
    public String getPolicierName() { return policierName; }
    public String getReason() { return reason; }
    public double getAmount() { return amount; }
    public LocalDateTime getIssueDate() { return issueDate; }
    public FineStatus getStatus() { return status; }
    public LocalDateTime getPaidDate() { return paidDate; }
    public UUID getJudgeUuid() { return judgeUuid; }
    public String getJudgeVerdict() { return judgeVerdict; }
    public LocalDateTime getJudgeDate() { return judgeDate; }

    public void setStatus(FineStatus status) { this.status = status; }
    public void setPaidDate(LocalDateTime paidDate) { this.paidDate = paidDate; }
    public void setJudgeUuid(UUID judgeUuid) { this.judgeUuid = judgeUuid; }
    public void setJudgeVerdict(String judgeVerdict) { this.judgeVerdict = judgeVerdict; }
    public void setJudgeDate(LocalDateTime judgeDate) { this.judgeDate = judgeDate; }

    @Override
    public String toString() {
        return String.format("Amende #%s: %s - %.2f€ (%s)",
            fineId.toString().substring(0, 8),
            reason,
            amount,
            status.getDisplayName()
        );
    }
}
