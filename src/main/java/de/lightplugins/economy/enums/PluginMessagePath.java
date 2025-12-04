/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 */
package de.lightplugins.economy.enums;

public enum PluginMessagePath {
    PAY("lighteconomy:pay");

    private String type;

    private PluginMessagePath(String type) {
        this.type = type;
    }

    public String getType() {
        return this.type;
    }
}

