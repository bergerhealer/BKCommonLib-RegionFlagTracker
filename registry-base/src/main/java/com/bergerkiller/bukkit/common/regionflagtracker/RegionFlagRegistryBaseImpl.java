package com.bergerkiller.bukkit.common.regionflagtracker;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Base implementation of the registry. Has the enable/disable methods to be used
 * by the plugin containing this library (BKCommonLib).
 */
public abstract class RegionFlagRegistryBaseImpl extends RegionFlagRegistry {
    private boolean enabled = false;
    private boolean ready = false;

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
    public synchronized void enable(final Plugin libraryPlugin) {
        enabled = true;

        Bukkit.getPluginManager().registerEvents(new Listener() {
            // Fires AFTER a plugin is enabled
            @EventHandler(priority = EventPriority.MONITOR)
            public void onPluginAfterEnable(PluginEnableEvent event) {
                tryMakeReady(libraryPlugin);
            }

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

        tryMakeReady(libraryPlugin);
    }

    private void tryMakeReady(Plugin libraryPlugin) {
        if (ready || !enabled) {
            return;
        }
        ready = isStateReady();
        if (ready) {
            for (RegisteredRegionFlag<?> registeredFlag : registeredFlags) {
                registeredFlag.registerHandler();
            }
            onStateIsReady(libraryPlugin);
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
        if (ready) {
            ready = false;
            for (RegisteredRegionFlag<?> registeredFlag : registeredFlags) {
                registeredFlag.unregisterHandler();
            }
        }
        registeredFlags.clear();
        trackers.clear();
    }

    @Override
    protected void onFlagRegistered(RegisteredRegionFlag<?> registeredFlag) {
        if (ready) {
            registeredFlag.registerHandler();
        }
    }

    protected abstract boolean isStateReady();

    /**
     * Called when the state goes to ready according to {@link #isStateReady()}
     * for the first time
     *
     * @param libraryPlugin The plugin owner of this library
     */
    protected void onStateIsReady(Plugin libraryPlugin) {
    }

    static Plugin findPlugin(String pluginName, Predicate<Plugin> condition) {
        // The plugin itself
        {
            Plugin p = Bukkit.getPluginManager().getPlugin(pluginName);
            if (p != null && condition.test(p)) {
                return p;
            }
        }

        // Plugins that substitute the plugin (provides)
        try {
            for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
                for (String provide : p.getDescription().getProvides()) {
                    if (pluginName.equalsIgnoreCase(provide) && condition.test(p)) {
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
