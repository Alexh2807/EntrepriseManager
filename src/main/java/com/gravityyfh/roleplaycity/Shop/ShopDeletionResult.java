package com.gravityyfh.roleplaycity.shop;

/**
 * Résultat de suppression d'une boutique
 */
public class ShopDeletionResult {
    private final boolean success;
    private final String message;

    private ShopDeletionResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static ShopDeletionResult success() {
        return new ShopDeletionResult(true, "Boutique supprimée avec succès");
    }

    public static ShopDeletionResult failure(String message) {
        return new ShopDeletionResult(false, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
