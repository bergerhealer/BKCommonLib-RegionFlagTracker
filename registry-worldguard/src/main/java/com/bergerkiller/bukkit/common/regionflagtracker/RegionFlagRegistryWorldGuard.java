package com.bergerkiller.bukkit.common.regionflagtracker;

import com.bergerkiller.bukkit.common.regionflagtracker.worldguard.WGRegionFlagsChangeTracker;
import com.bergerkiller.bukkit.common.regionflagtracker.worldguard.WGRegionFlagsChangeTrackerFallback;
import com.bergerkiller.bukkit.common.regionflagtracker.worldguard.WGRegionFlagsChangeTrackerFieldHack;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.BooleanFlag;
import com.sk89q.worldguard.protection.flags.DoubleFlag;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.IntegerFlag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.session.MoveType;
import com.sk89q.worldguard.session.Session;
import com.sk89q.worldguard.session.SessionManager;
import com.sk89q.worldguard.session.handler.FlagValueChangeHandler;
import com.sk89q.worldguard.session.handler.Handler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;

/**
 * Makes use of WorldGuard's session flag tracking logic to refresh the region-flag values.
 * Does a lot of extra logic, like keeping track of when the flags of regions (that players are in)
 * change and if so to refresh the flag query. It's all quite complicated because WorldGuard
 * sucks.
 */
class RegionFlagRegistryWorldGuard extends RegionFlagRegistryBaseImpl {
    private Plugin libraryPlugin = null;
    private final Map<RegionFlag.Type, FlagMapper<?, ?>> flagMappers = new EnumMap<>(RegionFlag.Type.class);
    private final Map<ProtectedRegion, TrackedProtectedRegion> trackedRegions = new IdentityHashMap<>();

    public RegionFlagRegistryWorldGuard() {
        flagMappers.put(RegionFlag.Type.BOOLEAN, new UnaryFlagMapper<Boolean>() {
            @Override
            public Flag<Boolean> adapt(Flag<?> flag) {
                return flag instanceof BooleanFlag ? (BooleanFlag) flag : null;
            }

            @Override
            public Flag<Boolean> create(String name) {
                return new BooleanFlag(name);
            }
        });
        flagMappers.put(RegionFlag.Type.STATE, new FlagMapper<StateFlag.State, RegionFlag.State>() {
            @Override
            public Flag<StateFlag.State> adapt(Flag<?> flag) {
                return flag instanceof StateFlag ? (StateFlag) flag : null;
            }

            @Override
            public Flag<StateFlag.State> create(String name) {
                return new StateFlag(name, false);
            }

            @Override
            public RegionFlag.State marshalValue(StateFlag.State value) {
                return (value == StateFlag.State.ALLOW)
                        ? RegionFlag.State.ALLOW : RegionFlag.State.DENY;
            }
        });
        flagMappers.put(RegionFlag.Type.INTEGER, new UnaryFlagMapper<Integer>() {
            @Override
            public Flag<Integer> adapt(Flag<?> flag) {
                return flag instanceof IntegerFlag ? (IntegerFlag) flag : null;
            }

            @Override
            public Flag<Integer> create(String name) {
                return new IntegerFlag(name);
            }
        });
        flagMappers.put(RegionFlag.Type.DOUBLE, new UnaryFlagMapper<Double>() {
            @Override
            public Flag<Double> adapt(Flag<?> flag) {
                return flag instanceof DoubleFlag ? (DoubleFlag) flag : null;
            }

            @Override
            public Flag<Double> create(String name) {
                return new DoubleFlag(name);
            }
        });
        flagMappers.put(RegionFlag.Type.STRING, new UnaryFlagMapper<String>() {
            @Override
            public Flag<String> adapt(Flag<?> flag) {
                return flag instanceof StringFlag ? (StringFlag) flag : null;
            }

            @Override
            public Flag<String> create(String name) {
                return new StringFlag(name);
            }
        });
    }

    @Override
    protected boolean isStateReady() {
        return findPlugin("WorldGuard", Plugin::isEnabled) != null;
    }

    @Override
    protected void onStateIsReady(final Plugin libraryPlugin) {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(libraryPlugin, this::updateTrackedRegions, 1L, 1L);
        libraryPlugin.getLogger().info("[RegionFlagTracker] Region flags will be tracked from WorldGuard");
    }

    @Override
    public synchronized void enable(Plugin libraryPlugin) {
        this.libraryPlugin = libraryPlugin;
        super.enable(libraryPlugin);
    }

    @Override
    public synchronized void disable() {
        super.disable();
        trackedRegions.clear();
    }

    private void updateTrackedRegions() {
        Set<ValueTrackerHandler<?, ?>> changedHandlers = Collections.emptySet();
        final Iterator<TrackedProtectedRegion> iter = this.trackedRegions.values().iterator();
        while (iter.hasNext()) {
            final TrackedProtectedRegion.UpdateResult result = iter.next().update();
            if (result.cleanupRegion) {
                iter.remove();
            }
            else {
                if (result.handlersToRefresh.isEmpty()) {
                    continue;
                }
                if (changedHandlers.isEmpty()) {
                    changedHandlers = new HashSet<ValueTrackerHandler<?, ?>>();
                }
                changedHandlers.addAll(result.handlersToRefresh);
            }
        }
        changedHandlers.forEach(ValueTrackerHandler::refresh);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <T> RegisteredRegionFlag<T> createNewFlag(Plugin plugin, RegionFlag<T> flag) {
        FlagMapper<?, T> mapper = (FlagMapper<?, T>) flagMappers.get(flag.type());

        try {
            return createNewFlagUnsafe(plugin, mapper, flag);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Region Flag '" + flag.name() + " of type " + flag.type().name() + " could not be registered", t);
            return new RegisteredRegionFlag<T>(plugin, flag);
        }
    }

    private <T, R> RegisteredRegionFlag<T> createNewFlagUnsafe(Plugin plugin, FlagMapper<R, T> mapper, RegionFlag<T> flag) {
        Flag<R> worldguardFlag = findOrRegisterFlag(plugin, mapper, flag);
        return new RegisteredWorldGuardRegionFlag<T, R>(this, plugin, flag, worldguardFlag, mapper);
    }

    private <T, R> Flag<R> findOrRegisterFlag(Plugin plugin, FlagMapper<R, T> mapper, RegionFlag<T> flag) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        for (Flag<?> worldguardFlag: registry.getAll()) {
            if (!worldguardFlag.getName().equals(flag.name())) {
                continue;
            }

            Flag<R> adapted = mapper.adapt(worldguardFlag);
            if (adapted != null) {
                return adapted;
            }

            // Incorrect type / already in use
            throw new IllegalStateException("Region Flag " + flag.name() + " by plugin " + plugin.getName() +
                    " is already in use by another plugin with a conflicting type");
        }

        // Register it
        Flag<R> worldguardFlag = mapper.create(flag.name());
        registry.register(worldguardFlag); // Can throw!
        return worldguardFlag;
    }

    private Optional<TrackedProtectedRegion> trackRegionIfExists(final ProtectedRegion region) {
        return Optional.ofNullable(this.trackedRegions.get(region));
    }

    private TrackedProtectedRegion trackRegion(final ProtectedRegion region) {
        if (libraryPlugin == null) {
            throw new IllegalStateException("Region tracking begun before enable()");
        }
        return this.trackedRegions.computeIfAbsent(region, r -> new TrackedProtectedRegion(libraryPlugin, region));
    }

    /**
     * Maps between WorldGuard types and our known types
     *
     * @param <R> Raw Type (WorldGuard type)
     * @param <T> Exposed Type
     */
    private interface FlagMapper<R, T> {
        Flag<R> adapt(Flag<?> flag);
        Flag<R> create(String name);
        T marshalValue(R value);
    }

    private interface UnaryFlagMapper<T> extends FlagMapper<T, T> {
        @Override
        default T marshalValue(T value) {
            return value;
        }
    }

    private static class RegisteredWorldGuardRegionFlag<T, R> extends RegisteredRegionFlag<T> {
        public final RegionFlagRegistryWorldGuard registry;
        public final Flag<R> worldguardFlag;
        public final FlagMapper<R, T> mapper;
        private final ValueTrackerFactory<T, R> sessionFactory;

        public RegisteredWorldGuardRegionFlag(
                final RegionFlagRegistryWorldGuard registry,
                final Plugin plugin,
                final RegionFlag<T> flag,
                final Flag<R> worldguardFlag,
                final FlagMapper<R, T> mapper
        ) {
            super(plugin, flag);
            this.registry = registry;
            this.worldguardFlag = worldguardFlag;
            this.mapper = mapper;
            this.sessionFactory = new ValueTrackerFactory<>(this);
        }

        public void registerHandler() {
            SessionManager sessionManager = WorldGuard.getInstance().getPlatform().getSessionManager();
            sessionManager.registerHandler(this.sessionFactory, null);
        }

        public void unregisterHandler() {
            SessionManager sessionManager = WorldGuard.getInstance().getPlatform().getSessionManager();
            sessionManager.unregisterHandler(this.sessionFactory);
        }
    }

    private static class ValueTrackerFactory<T, R> extends Handler.Factory<ValueTrackerHandler<T, R>> {
        private final RegisteredWorldGuardRegionFlag<T, R> flag;

        public ValueTrackerFactory(RegisteredWorldGuardRegionFlag<T, R> flag) {
            this.flag = flag;
        }

        @Override
        public ValueTrackerHandler<T, R> create(Session session) {
            return new ValueTrackerHandler<>(session, this.flag);
        }
    }

    private static class ValueTrackerHandler<T, R> extends Handler {
        private final RegisteredWorldGuardRegionFlag<T, R> flag;
        private LocalPlayer lastLocalPlayer;
        private RegionFlagTracker<T> tracker;
        private ApplicableRegionSet currentRegionSet;
        private R lastValue;

        protected ValueTrackerHandler(final Session session, final RegisteredWorldGuardRegionFlag<T, R> flag) {
            super(session);
            this.lastLocalPlayer = null;
            this.tracker = null;
            this.currentRegionSet = null;
            this.flag = flag;
        }

        private void updateTracker(final LocalPlayer player) {
            if (this.lastLocalPlayer != player) {
                this.lastLocalPlayer = player;
                this.tracker = this.flag.registry.track(BukkitAdapter.adapt(player), this.flag.flag);
            }
        }

        @Override
        public void initialize(LocalPlayer player, Location current, ApplicableRegionSet set) {
            R currentValue = set.queryValue(player, flag.worldguardFlag);
            this.lastValue = currentValue;
            if (this.currentRegionSet != null) {
                for (final ProtectedRegion region : this.currentRegionSet) {
                    this.flag.registry.trackRegionIfExists(region).ifPresent(tr -> tr.handlers.remove(this));
                }
            }
            this.currentRegionSet = set;
            this.updateTracker(player);
            for (final ProtectedRegion region : set) {
                this.flag.registry.trackRegion(region).handlers.add(this);
            }

            if (currentValue == null) {
                this.tracker.updateValue(null);
            } else {
                this.tracker.updateValue(this.flag.mapper.marshalValue(currentValue));
            }
        }

        @Override
        public boolean onCrossBoundary(final LocalPlayer player, final Location from, final Location to, final ApplicableRegionSet toSet, final Set<ProtectedRegion> entered, final Set<ProtectedRegion> exited, final MoveType moveType) {
            if (entered.isEmpty() && exited.isEmpty()
                    && from.getExtent().equals(to.getExtent())) { // sets don't include global regions - check if those changed
                return true; // no changes to flags if regions didn't change
            }

            this.currentRegionSet = toSet;
            this.updateTracker(player);
            for (final ProtectedRegion region : exited) {
                this.flag.registry.trackRegionIfExists(region).ifPresent(tr -> tr.handlers.remove(this));
            }
            for (final ProtectedRegion region : entered) {
                this.flag.registry.trackRegion(region).handlers.add(this);
            }

            updateValue(toSet.queryValue(player, flag.worldguardFlag));
            return true;
        }

        public void refresh() {
            if (this.lastLocalPlayer == null || this.tracker == null || this.currentRegionSet == null) {
                return;
            }

            updateValue(this.currentRegionSet.queryValue(this.lastLocalPlayer, this.flag.worldguardFlag));
        }

        private void updateValue(R currentValue) {
            if (currentValue == null && lastValue != null) {
                this.tracker.updateValue(null);
            } else if (currentValue != null && currentValue != lastValue) {
                this.tracker.updateValue(this.flag.mapper.marshalValue(currentValue));
            }
            lastValue = currentValue;
        }
    }

    private static final class TrackedProtectedRegion {
        private static boolean IS_OPTIMIZED_FLAG_TRACKER_WORKING = true;
        public final ProtectedRegion region;
        public final Set<ValueTrackerHandler<?, ?>> handlers;
        private WGRegionFlagsChangeTracker flagChangeTracker;
        private int checkPlayersQuitCounter = 0;

        public TrackedProtectedRegion(Plugin libraryPlugin, ProtectedRegion region) {
            this.handlers = new HashSet<>();
            this.region = region;
            this.flagChangeTracker = initFlagChangeTracker(libraryPlugin, region);
        }

        private static WGRegionFlagsChangeTracker initFlagChangeTracker(Plugin libraryPlugin, ProtectedRegion region) {
            if (IS_OPTIMIZED_FLAG_TRACKER_WORKING) {
                try {
                    return new WGRegionFlagsChangeTrackerFieldHack(region);
                } catch (WGRegionFlagsChangeTrackerFieldHack.OptimizationNotSupportedException ex) {
                    IS_OPTIMIZED_FLAG_TRACKER_WORKING = false;
                    libraryPlugin.getLogger().log(Level.WARNING, "[RegionFlagTracker] Could not optimize detection of region flag changes", ex);
                }
            }
            return new WGRegionFlagsChangeTrackerFallback(region);
        }

        public UpdateResult update() {
            // Every 40 ticks verify for all the trackers we got whether the player is still online
            // When players log off, this is currently the only way to check for it unfortunately
            if (++checkPlayersQuitCounter >= 40) {
                checkPlayersQuitCounter = 0;
                this.handlers.removeIf(handler -> handler.tracker != null && RegionFlagRegistry.hasPlayerQuit(handler.tracker.getPlayer()));
            }

            if (this.handlers.isEmpty()) {
                return UpdateResult.DEFAULT_CLEANUP;
            } else if (flagChangeTracker.update(region)) {
                return new UpdateResult(false, this.handlers);
            } else {
                return UpdateResult.DEFAULT_KEEP;
            }
        }

        private static class UpdateResult
        {
            public static final UpdateResult DEFAULT_CLEANUP;
            public static final UpdateResult DEFAULT_KEEP;
            public final boolean cleanupRegion;
            public final Set<ValueTrackerHandler<?, ?>> handlersToRefresh;

            public UpdateResult(final boolean cleanupRegion, final Set<ValueTrackerHandler<?, ?>> handlersToRefresh) {
                this.cleanupRegion = cleanupRegion;
                this.handlersToRefresh = handlersToRefresh;
            }

            static {
                DEFAULT_CLEANUP = new UpdateResult(true, Collections.emptySet());
                DEFAULT_KEEP = new UpdateResult(false, Collections.emptySet());
            }
        }
    }
}
