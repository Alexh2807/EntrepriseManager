package com.gravityyfh.roleplaycity.shop;

import com.gravityyfh.roleplaycity.shop.model.Shop;

/**
 * Résultat de création d'une boutique
 */
public class ShopCreationResult {
    private final boolean success;
    private final Shop shop;
    private final String errorMessage;

    private ShopCreationResult(boolean success, Shop shop, String errorMessage) {
        this.success = success;
        this.shop = shop;
        this.errorMessage = errorMessage;
    }

    public static ShopCreationResult success(Shop shop) {
        return new ShopCreationResult(true, shop, null);
    }

    public static ShopCreationResult failure(String errorMessage) {
        return new ShopCreationResult(false, null, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public Shop getShop() {
        return shop;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
