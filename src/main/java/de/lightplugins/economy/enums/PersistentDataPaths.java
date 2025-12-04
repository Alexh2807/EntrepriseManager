/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 */
package de.lightplugins.economy.enums;

public enum PersistentDataPaths {
    MONEY_VALUE("money_value");

    private String type;

    private PersistentDataPaths(String type) {
        this.type = type;
    }

    public String getType() {
        return this.type;
    }
}

