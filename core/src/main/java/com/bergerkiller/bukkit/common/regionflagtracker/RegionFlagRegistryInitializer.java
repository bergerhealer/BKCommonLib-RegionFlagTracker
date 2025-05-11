package com.bergerkiller.bukkit.common.regionflagtracker;

import org.bukkit.plugin.Plugin;

/**
 * Helper class that does at-load initialization of the right Registry implementation
 */
class RegionFlagRegistryInitializer {

    public static RegionFlagRegistryBaseImpl initialize() {
        Plugin worldguardPlugin = RegionFlagRegistryBaseImpl.findPlugin("WorldGuard", p -> true);
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
}
