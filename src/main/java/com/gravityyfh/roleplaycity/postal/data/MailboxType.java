package com.gravityyfh.roleplaycity.postal.data;

/**
 * Types de boîtes aux lettres disponibles (couleurs)
 */
public enum MailboxType {
    LIGHT_GRAY("Mailbox (light gray)",
        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzBhZTNkNTA2NDIzNzllOThhNjRkNGEwNmJiNGVmOTRhMzRiNDc4NmRhMzc4NGE5MzBkOTM0NmVjNjExM2QyIn19fQ==",
        "1a78c18a-1d06-4ec9-a27f-c5138cc56852"),

    LIGHT_BLUE("Mailbox (light blue)",
        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTRiMzQ5ZTc0Y2Y5YTU1YWYwMzFlMzkyYWE4MDhjNWMxNjFlMTU3YTRlYmI3ODQxNDQ4MWNhMGNiYmEyZmFlMyJ9fX0=",
        "32af295c-18d6-430b-a0d1-ece7fc0708f5"),

    ORANGE("Mailbox (orange)",
        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDk2MDEyNjI5ODgzOTI5MjdmOWU1ZWUxMmJlZGQ1YmE5ZjRjMDE3OTc2ODFmMTUzNTI5Y2Q0M2UyMzQ4OGU0In19fQ==",
        "ac9d486b-bc19-4897-bb4b-967cb24513a0");

    private final String displayName;
    private final String textureValue;
    private final String textureId;

    MailboxType(String displayName, String textureValue, String textureId) {
        this.displayName = displayName;
        this.textureValue = textureValue;
        this.textureId = textureId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTextureValue() {
        return textureValue;
    }

    public String getTextureId() {
        return textureId;
    }
}
