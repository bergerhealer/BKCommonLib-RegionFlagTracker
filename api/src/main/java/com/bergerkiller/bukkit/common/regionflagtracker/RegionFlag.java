package com.bergerkiller.bukkit.common.regionflagtracker;

import org.bukkit.plugin.Plugin;

/**
 * A singular flag that can be set on a region. When players move in and out of the
 * region the tracker notifies the plugin of the region flag changing.
 * In addition, the {@link RegionFlagTracker} can be obtained for a Player for this
 * flag to quickly get the current flag value without relying on events.<br>
 * <br>
 * Before the region flag is used, it must be registered with
 * {@link RegionFlagRegistry#register(Plugin, RegionFlag)} inside your plugin
 * {@link Plugin#onLoad()}. The registry has a static instance getter.<br>
 * <br>
 * Please note that in internal logic, it uses identity equality to check whether
 * flags are the same. Not flag name or type. In normal use you would create a static
 * RegionFlag constant somewhere in your plugin and refer to that in your own code.
 *
 * @param <T> Value type of the region flag
 */
public final class RegionFlag<T> {
    private final String name;
    private final Type type;

    /**
     * Creates a new RegionFlag for a state flag of the given name.
     * The state value is either ALLOW or DENY, or not present (empty)
     * where a default behavior can be chosen.
     *
     * @param name Flag name
     * @return RegionFlag
     */
    public static RegionFlag<State> ofState(String name) {
        return create(name, Type.STATE);
    }

    /**
     * Creates a new RegionFlag for an Boolean flag of the given name
     *
     * @param name Flag name
     * @return RegionFlag
     */
    public static RegionFlag<Boolean> ofBoolean(String name) {
        return create(name, Type.BOOLEAN);
    }

    /**
     * Creates a new RegionFlag for an Integer flag of the given name
     *
     * @param name Flag name
     * @return RegionFlag
     */
    public static RegionFlag<Integer> ofInteger(String name) {
        return create(name, Type.INTEGER);
    }

    /**
     * Creates a new RegionFlag for a Double flag of the given name
     *
     * @param name Flag name
     * @return RegionFlag
     */
    public static RegionFlag<Double> ofDouble(String name) {
        return create(name, Type.DOUBLE);
    }

    /**
     * Creates a new RegionFlag for a String flag of the given name
     *
     * @param name Flag name
     * @return RegionFlag
     */
    public static RegionFlag<String> ofString(String name) {
        return create(name, Type.STRING);
    }

    private RegionFlag(String name, Type type) {
        this.name = name;
        this.type = type;
    }

    /**
     * Gets the name of this flag
     *
     * @return Flag name
     */
    public String name() {
        return name;
    }

    /**
     * Gets the type of value stored by this flag
     *
     * @return Type
     */
    public Type type() {
        return type;
    }

    @Override
    public String toString() {
        return "RegionFlag{name=" + name + ", type=" + type.name() + "}";
    }

    // Helper
    private static <T> RegionFlag<T> create(String name, Type type) {
        return new RegionFlag<>(name, type);
    }

    /**
     * A permission or behavior toggle. Either ALLOW or DENY.
     */
    public enum State {
        ALLOW,
        DENY
    }

    /**
     * Supported flag types
     */
    public enum Type {
        /** Flag is either ALLOW or DENY, or not present (empty) where a default behavior can be chosen */
        STATE,
        /** Flag value can be true, false or not present (empty) */
        BOOLEAN,
        /** Flag value is an integer, or not present (empty) */
        INTEGER,
        /** Flag value is a double number, or not present (empty) */
        DOUBLE,
        /** Flag value is String text value, or not present (empty) */
        STRING
    }
}
