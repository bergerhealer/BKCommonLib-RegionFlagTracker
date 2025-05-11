package com.bergerkiller.bukkit.common.regionflagtracker;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Keeps track of the value of a flag for a particular Player.
 *
 * @param <T> Flag value type
 */
public final class RegionFlagTracker<T> {
    private final Plugin plugin;
    private final Player player;
    private final RegionFlag<T> flag;
    private List<ChangeListener<T>> listeners = Collections.emptyList();
    private T value = null;

    RegionFlagTracker(Plugin plugin, Player player, RegionFlag<T> flag) {
        this.plugin = plugin;
        this.player = player;
        this.flag = flag;
    }

    /**
     * Gets the plugin owner of the flag being tracked
     *
     * @return Plugin flag owner
     */
    public Plugin getPlugin() {
        return this.plugin;
    }

    /**
     * Gets the Player for which the flag value is tracked
     *
     * @return Player
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Gets the registered flag this tracker is for
     *
     * @return Flag
     */
    public RegionFlag<T> getFlag() {
        return flag;
    }

    /**
     * Gets the value of the flag for the Player in the current region. If the flag is not
     * set for any region the player is inside (including global), returns empty.
     *
     * @return Flag value, or empty if not set
     */
    public Optional<T> getValue() {
        return Optional.ofNullable(value);
    }

    /**
     * Adds a new value change listener to this tracker. The callback will be called whenever
     * the {@link #getValue()} of this tracker changes. This listener will exist for as long
     * as this tracker exists, which is while the player is online and the owning plugin
     * is not disabled.
     *
     * @param listener Listener
     */
    public synchronized void addListener(ChangeListener<T> listener) {
        List<ChangeListener<T>> newListeners = new ArrayList<>(this.listeners);
        newListeners.add(listener);
        this.listeners = newListeners;
    }

    // Called internally
    void updateValue(T value) {
        if (!Objects.equals(this.value, value)) {
            this.value = value;
            listeners.forEach(l -> l.onValueChanged(this));
        }
    }

    @FunctionalInterface
    public interface ChangeListener<T> {
        void onValueChanged(RegionFlagTracker<T> tracker);
    }
}
