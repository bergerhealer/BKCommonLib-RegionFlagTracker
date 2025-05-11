package com.bergerkiller.bukkit.common.regionflagtracker;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Base implementation of the registry. Has the enable/disable methods to be used
 * by the plugin containing this library (BKCommonLib).
 */
public abstract class RegionFlagRegistryBaseImpl extends RegionFlagRegistry {
    private boolean enabled = false;

    /**
     * Same as {@link RegionFlagRegistry#getInstance()} but as the implementation base type
     * for calling enable/disable on. To be used by the library-owning plugin.
     *
     * @return RegionFlagRegistryBaseImpl
     */
    public static RegionFlagRegistryBaseImpl getInstance() {
        return (RegionFlagRegistryBaseImpl) RegionFlagRegistry.getInstance();
    }

    /**
     * Starts tracking value changes using all flags registered so far.
     * Flag registration must occur during load, after enabling this is
     * no longer possible unless the flag was already registered before. (reload)
     *
     * @param libraryPlugin Plugin that owns this library (BKCommonLib plugin instance).
     *                      Event listeners are registered with this plugin.
     */
    public synchronized void enable(Plugin libraryPlugin) {
        enabled = true;

        Bukkit.getPluginManager().registerEvents(new Listener() {
            // Fires BEFORE a plugin is disabled
            @EventHandler(priority = EventPriority.MONITOR)
            public void onPluginBeforeDisable(PluginDisableEvent event) {
                // Unregister registered flags owned by this plugin
                // The WorldGuard registered flags stay around (in case of a hot reload)
                Plugin disabledPlugin = event.getPlugin();
                synchronized (RegionFlagRegistryBaseImpl.this) {
                    List<RegisteredRegionFlag<?>> handlersToUnregister = Collections.emptyList();
                    Set<RegionFlag<?>> unregisteredFlags = Collections.emptySet();
                    for (Iterator<RegisteredRegionFlag<?>> iter = registeredFlags.iterator(); iter.hasNext();) {
                        RegisteredRegionFlag<?> registeredFlag = iter.next();
                        if (registeredFlag.plugin == disabledPlugin) {
                            iter.remove();

                            {
                                if (handlersToUnregister.isEmpty()) {
                                    handlersToUnregister = new ArrayList<>();
                                }
                                handlersToUnregister.add(registeredFlag);
                            }
                            {
                                if (unregisteredFlags.isEmpty()) {
                                    unregisteredFlags = new HashSet<>();
                                }
                                unregisteredFlags.add(registeredFlag.flag);
                            }
                        }
                    }

                    // Remove player-tied trackers for this unregistered flag
                    if (!unregisteredFlags.isEmpty()) {
                        for (Iterator<RegionFlagTracker<?>> trackerIter = trackers.values().iterator(); trackerIter.hasNext();) {
                            if (unregisteredFlags.contains(trackerIter.next().getFlag())) {
                                trackerIter.remove();
                            }
                        }
                    }

                    // Disable any change handler we had registered for it
                    handlersToUnregister.forEach(RegisteredRegionFlag::unregisterHandler);
                }
            }

            @EventHandler(priority = EventPriority.MONITOR)
            public void onPlayerQuit(PlayerQuitEvent event) {
                // Cleanup trackers of this player
                removeTrackersOfPlayer(event.getPlayer());
            }
        }, libraryPlugin);

        // Register handler for all previously created flags (during onLoad())
        for (RegisteredRegionFlag<?> registeredFlag : registeredFlags) {
            registeredFlag.registerHandler();
        }
    }

    private synchronized void removeTrackersOfPlayer(Player player) {
        for (RegisteredRegionFlag<?> registeredFlag : registeredFlags) {
            trackers.remove(new PlayerFlagKey(player, registeredFlag.flag));
        }
    }

    /**
     * Shuts down all previously registered value change trackers
     */
    public synchronized void disable() {
        enabled = false;
        for (RegisteredRegionFlag<?> registeredFlag : registeredFlags) {
            registeredFlag.unregisterHandler();
        }
        registeredFlags.clear();
        trackers.clear();
    }

    @Override
    protected void onFlagRegistered(RegisteredRegionFlag<?> registeredFlag) {
        if (enabled) {
            registeredFlag.registerHandler();
        }
    }
}
