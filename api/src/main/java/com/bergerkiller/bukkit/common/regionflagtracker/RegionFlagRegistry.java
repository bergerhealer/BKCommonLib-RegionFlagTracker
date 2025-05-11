package com.bergerkiller.bukkit.common.regionflagtracker;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps track of registered region flags and its per-player trackers.
 */
public abstract class RegionFlagRegistry {
    protected final List<RegisteredRegionFlag<?>> registeredFlags = new ArrayList<>();
    protected final Map<PlayerFlagKey, RegionFlagTracker<?>> trackers = new HashMap<>();

    // Detected during onLoad(), as we need to register flags into that API before enable() occurs
    private static RegionFlagRegistry instance = initRegistryInstance();

    /**
     * Gets the region flag registry singleton instance of the server.
     *
     * @return RegionFlagRegistry
     */
    public static RegionFlagRegistry getInstance() {
        return instance;
    }

    /**
     * Registers a new region flag. Must be done inside {@link Plugin#onLoad()}.<br>
     * <br>
     * When the plugin owner disables, the flag is automatically un-registered.
     * It does stay around in WorldGuard or such to be re-used when it re-enables.
     *
     * @param plugin Plugin owner of the flag. Must not be null. When this plugin disables,
     *               the flag is automatically un-registered. It does stay around in
     *               WorldGuard or such to be re-used when it re-enables.
     * @param flag RegionFlag to register
     */
    public final synchronized void register(Plugin plugin, RegionFlag<?> flag) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin is null");
        }
        if (flag == null) {
            throw new IllegalArgumentException("RegionFlag is null");
        }
        if (plugin.isEnabled()) {
            throw new IllegalStateException("Region flags can only be registered inside onLoad()");
        }

        // Check not already registered
        for (RegisteredRegionFlag<?> registeredFlag : registeredFlags) {
            if (registeredFlag.flag.name().equals(flag.name())) {
                throw new IllegalStateException("Flag is already registered by plugin " + plugin.getName() + ": " + flag);
            }
        }

        // Register a new flag. Callback will set it up in WorldGuard or such, if enabled.
        RegisteredRegionFlag<?> registeredRegionFlag = createNewFlag(plugin, flag);
        registeredFlags.add(registeredRegionFlag);
        onFlagRegistered(registeredRegionFlag);
    }

    /**
     * Retrieves the per-player RegionFlag value tracker. The value is automatically kept
     * up to date when the player moves between regions.<br>
     * <br>
     * If the input player has left the server (is invalid), then this method will return
     * a fallback tracker with value always absent. The tracker will not reflect the true
     * value when the player goes back online.
     *
     * @param player Player to track. Must be of a valid online Player for tracking to work.
     * @param flag RegionFlag to track. Must be registered or an error is thrown.
     * @return RegionFlagTracker object
     * @param <T> Flag value type
     */
    @SuppressWarnings("unchecked")
    public synchronized <T> RegionFlagTracker<T> track(Player player, RegionFlag<T> flag) {
        // If the player instance is invalid, return a detached tracker with an empty value
        // We don't want to cause any weird memory leaks
        if (!player.isValid() && Bukkit.getPlayer(player.getUniqueId()) != player) {
            RegionFlagTracker<T> existing = (RegionFlagTracker<T>) trackers.get(new PlayerFlagKey(player, flag));
            return existing != null ? existing : new RegionFlagTracker<>(getFlagOwnerVerify(flag), player, flag);
        }

        // Get or create trackers of flags per player. These are automatically created and cleaned up
        // when players quit the server.
        return (RegionFlagTracker<T>) trackers.computeIfAbsent(new PlayerFlagKey(player, flag),
                k -> new RegionFlagTracker<>(getFlagOwnerVerify(k.flag), k.player, k.flag));
    }

    private Plugin getFlagOwnerVerify(RegionFlag<?> flag) {
        for (RegisteredRegionFlag<?> registeredFlag : registeredFlags) {
            if (registeredFlag.flag == flag) {
                return registeredFlag.plugin;
            }
        }
        throw new IllegalArgumentException("Flag " + flag + " was not registered");
    }

    /**
     * Called to create a new registered flag. The implementation should talk with WorldGuard's
     * API to initialize the flag. Or if disabled, does nothing special.
     *
     * @param plugin Plugin owner of the region flag
     * @param flag RegionFlag
     * @return RegisteredRegionFlag implementation
     * @param <T> RegionFlag value type
     */
    protected <T> RegisteredRegionFlag<T> createNewFlag(Plugin plugin, RegionFlag<T> flag) {
        return new RegisteredRegionFlag<>(plugin, flag);
    }

    /**
     * Called after a newly created flag is registered. Maintenance logic here.
     *
     * @param registeredFlag RegisteredRegionFlag
     */
    protected abstract void onFlagRegistered(RegisteredRegionFlag<?> registeredFlag);

    // Note: always an instance of RegionFlagRegistryBaseImpl
    private static RegionFlagRegistry initRegistryInstance() {
        try {
            Class<?> initializerHelper = Class.forName(RegionFlagRegistry.class.getName() + "Initializer");
            Method m = initializerHelper.getDeclaredMethod("initialize");
            m.setAccessible(true);
            return (RegionFlagRegistry) m.invoke(null);
        } catch (Throwable t) {
            throw new UnsupportedOperationException("RegionFlagRegistry API could not be initialized", t);
        }
    }

    protected static class RegisteredRegionFlag<T> {
        public final Plugin plugin;
        public final RegionFlag<T> flag;

        public RegisteredRegionFlag(Plugin plugin, RegionFlag<T> flag) {
            this.plugin = plugin;
            this.flag = flag;
        }

        public void registerHandler() {
        }
        public void unregisterHandler() {
        }
    }

    /**
     * Helper class to store a player + flag as key in hashmaps
     */
    protected static final class PlayerFlagKey {
        private final Player player;
        private final RegionFlag<?> flag;

        public PlayerFlagKey(Player player, RegionFlag<?> flag) {
            this.player = player;
            this.flag = flag;
        }

        @Override
        public int hashCode() {
            return player.hashCode() + 31 * flag.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof PlayerFlagKey) {
                PlayerFlagKey other = (PlayerFlagKey) o;
                return this.player == other.player &&
                        this.flag == other.flag;
            } else {
                return false;
            }
        }

        @Override
        public String toString() {
            return "{player=" + player.getName() + ", flag=" + this.flag + "}";
        }
    }
}
