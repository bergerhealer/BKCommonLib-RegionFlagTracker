package com.bergerkiller.bukkit.common.regionflagtracker;

/**
 * Implementation used when WorldGuard is not installed on the server (during onLoad())
 */
class RegionFlagRegistryDisabled extends RegionFlagRegistryBaseImpl {
    @Override
    protected boolean isStateReady() {
        return true;
    }
}
