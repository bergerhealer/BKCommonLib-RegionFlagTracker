package com.bergerkiller.bukkit.common.regionflagtracker;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Helper class that does at-load initialization of the right Registry implementation
 */
class RegionFlagRegistryInitializer {

    public static RegionFlagRegistryBaseImpl initialize() {
        Plugin worldguardPlugin = findPluginEnabledOrProvided("WorldGuard");
        if (worldguardPlugin != null) {
            boolean available = false;
            try {
                Class.forName("com.sk89q.worldguard.WorldGuard");
                Class.forName("com.sk89q.worldguard.protection.flags.registry.FlagRegistry");
                Class.forName("com.sk89q.worldguard.session.handler.FlagValueChangeHandler");
                available = true;
            } catch (Throwable t) {
                /* Not available ... */
            }

            if (available) {
                return new RegionFlagRegistryWorldGuard();
            }
        }

        return new RegionFlagRegistryDisabled();
    }

    private static Plugin findPluginEnabledOrProvided(String pluginName) {
        // The plugin itself
        {
            Plugin p = Bukkit.getPluginManager().getPlugin(pluginName);
            if (p != null) {
                return p;
            }
        }

        // Plugins that substitute the plugin (provides)
        try {
            for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                for (String provide : p.getDescription().getProvides()) {
                    if (pluginName.equalsIgnoreCase(provide)) {
                        return p;
                    }
                }
            }
        } catch (Throwable t) {
            /* Ignore - probably missing provides api */
        }
        return null;
    }
}
