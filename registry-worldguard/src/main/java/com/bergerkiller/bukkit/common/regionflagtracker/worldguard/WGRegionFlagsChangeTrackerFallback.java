package com.bergerkiller.bukkit.common.regionflagtracker.worldguard;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.flags.Flag;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Fallback implementation that just clones the flags map and checks for equality.
 * It's a lot slower to call equals() every tick...
 */
public class WGRegionFlagsChangeTrackerFallback implements WGRegionFlagsChangeTracker {
    private Map<Flag<?>, ?> lastFlags;

    public WGRegionFlagsChangeTrackerFallback(ProtectedRegion region) {
        this.lastFlags = new HashMap<>(region.getFlags());
    }

    @Override
    public void cleanup(ProtectedRegion region) {
        lastFlags = Collections.emptyMap();
    }

    @Override
    public boolean update(ProtectedRegion region) {
        if (region.getFlags().equals(lastFlags)) {
            return false;
        } else {
            lastFlags = new HashMap<>(region.getFlags());
            return true;
        }
    }
}
