package com.bergerkiller.bukkit.common.regionflagtracker.worldguard;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;

public interface WGRegionFlagsChangeTracker {
    void cleanup(ProtectedRegion region);
    boolean update(ProtectedRegion region);
}
