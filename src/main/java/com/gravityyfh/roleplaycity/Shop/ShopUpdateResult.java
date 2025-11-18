package com.gravityyfh.roleplaycity.shop;

/**
 * Résultat de mise à jour d'une boutique
 */
public class ShopUpdateResult {
    private final boolean success;
    private final String message;

    private ShopUpdateResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static ShopUpdateResult success(String message) {
        return new ShopUpdateResult(true, message);
    }

    public static ShopUpdateResult failure(String message) {
        return new ShopUpdateResult(false, message);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}
