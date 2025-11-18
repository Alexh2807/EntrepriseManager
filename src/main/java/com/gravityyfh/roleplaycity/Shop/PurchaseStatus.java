package com.gravityyfh.roleplaycity.shop;

/**
 * Statut d'un achat
 */
public enum PurchaseStatus {
    SUCCESS,
    INSUFFICIENT_FUNDS,
    OUT_OF_STOCK,
    INVENTORY_FULL,
    SHOP_BROKEN,
    INTERNAL_ERROR,
    COOLDOWN_ACTIVE
}
