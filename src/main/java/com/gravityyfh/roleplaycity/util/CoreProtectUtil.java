package com.gravityyfh.roleplaycity.util;

import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class CoreProtectUtil {

    private static final int MIN_API_VERSION = 9;
    private static CoreProtectAPI cachedAPI;

    private CoreProtectUtil() {
        // Utility class
    }

    public static CoreProtectAPI getAPI() {
        if (cachedAPI != null && cachedAPI.isEnabled()) {
            return cachedAPI;
        }

        Plugin plugin = Bukkit.getPluginManager().getPlugin("CoreProtect");
        if (!(plugin instanceof CoreProtect)) {
            return null;
        }

        CoreProtectAPI api = ((CoreProtect) plugin).getAPI();
        if (!api.isEnabled() || api.APIVersion() < MIN_API_VERSION) {
            return null;
        }

        cachedAPI = api;
        return api;
    }

    public static boolean isAvailable() {
        return getAPI() != null;
    }

    public static void clearCache() {
        cachedAPI = null;
    }
}
