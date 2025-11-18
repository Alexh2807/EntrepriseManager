package com.gravityyfh.roleplaycity.shop;

/**
 * Résultat d'un achat
 */
public class PurchaseResult {
    private final PurchaseStatus status;
    private final double price;
    private final int quantity;
    private final double playerBalance;
    private final double requiredBalance;

    private PurchaseResult(PurchaseStatus status, double price, int quantity,
                          double playerBalance, double requiredBalance) {
        this.status = status;
        this.price = price;
        this.quantity = quantity;
        this.playerBalance = playerBalance;
        this.requiredBalance = requiredBalance;
    }

    public static PurchaseResult success(double price, int quantity) {
        return new PurchaseResult(PurchaseStatus.SUCCESS, price, quantity, 0, 0);
    }

    public static PurchaseResult insufficientFunds(double playerBalance, double requiredBalance) {
        return new PurchaseResult(PurchaseStatus.INSUFFICIENT_FUNDS, 0, 0, playerBalance, requiredBalance);
    }

    public static PurchaseResult outOfStock() {
        return new PurchaseResult(PurchaseStatus.OUT_OF_STOCK, 0, 0, 0, 0);
    }

    public static PurchaseResult inventoryFull() {
        return new PurchaseResult(PurchaseStatus.INVENTORY_FULL, 0, 0, 0, 0);
    }

    public static PurchaseResult shopBroken() {
        return new PurchaseResult(PurchaseStatus.SHOP_BROKEN, 0, 0, 0, 0);
    }

    public static PurchaseResult internalError() {
        return new PurchaseResult(PurchaseStatus.INTERNAL_ERROR, 0, 0, 0, 0);
    }

    public static PurchaseResult cooldownActive() {
        return new PurchaseResult(PurchaseStatus.COOLDOWN_ACTIVE, 0, 0, 0, 0);
    }

    public PurchaseStatus getStatus() {
        return status;
    }

    public double getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getPlayerBalance() {
        return playerBalance;
    }

    public double getRequiredBalance() {
        return requiredBalance;
    }

    public boolean isSuccess() {
        return status == PurchaseStatus.SUCCESS;
    }
}
