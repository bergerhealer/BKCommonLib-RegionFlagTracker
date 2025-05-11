package com.bergerkiller.bukkit.common.regionflagtracker;

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
import com.sk89q.worldguard.session.MoveType;
import com.sk89q.worldguard.session.Session;
import com.sk89q.worldguard.session.SessionManager;
import com.sk89q.worldguard.session.handler.FlagValueChangeHandler;
import com.sk89q.worldguard.session.handler.Handler;
import org.bukkit.plugin.Plugin;

import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Makes use of WorldGuard's session flag tracking logic to refresh the region-flag values.
 */
class RegionFlagRegistryWorldGuard extends RegionFlagRegistryBaseImpl {
    private final Map<RegionFlag.Type, FlagMapper<?, ?>> flagMappers = new EnumMap<>(RegionFlag.Type.class);

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

    private static class ValueTrackerHandler<T, R> extends FlagValueChangeHandler<R> {
        private final RegisteredWorldGuardRegionFlag<T, R> flag;
        private RegionFlagTracker<T> tracker = null; // Initialized on first callback

        protected ValueTrackerHandler(Session session, RegisteredWorldGuardRegionFlag<T, R> flag) {
            super(session, flag.worldguardFlag);
            this.flag = flag;
        }

        private RegionFlagTracker<T> getTracker(LocalPlayer localPlayer) {
            RegionFlagTracker<T> tracker;
            if ((tracker = this.tracker) == null) {
                this.tracker = tracker = flag.registry.track(BukkitAdapter.adapt(localPlayer), flag.flag);
            }
            return tracker;
        }

        @Override
        protected void onInitialValue(LocalPlayer localPlayer, ApplicableRegionSet applicableRegionSet, R t) {
            if (t == null) {
                getTracker(localPlayer).updateValue(null);
            } else {
                getTracker(localPlayer).updateValue(flag.mapper.marshalValue(t));
            }
        }

        @Override
        protected boolean onSetValue(LocalPlayer localPlayer, Location location, Location location1, ApplicableRegionSet applicableRegionSet, R t, R t1, MoveType moveType) {
            getTracker(localPlayer).updateValue(flag.mapper.marshalValue(t));
            return true;
        }

        @Override
        protected boolean onAbsentValue(LocalPlayer localPlayer, Location location, Location location1, ApplicableRegionSet applicableRegionSet, R t, MoveType moveType) {
            getTracker(localPlayer).updateValue(null);
            return true;
        }
    }
}
