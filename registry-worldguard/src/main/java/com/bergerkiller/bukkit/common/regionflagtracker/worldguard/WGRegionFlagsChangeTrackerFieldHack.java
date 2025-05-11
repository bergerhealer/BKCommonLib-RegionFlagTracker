package com.bergerkiller.bukkit.common.regionflagtracker.worldguard;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Modifies the private "flags" field using reflection, setting it to a value that includes
 * modification tracking.
 */
public class WGRegionFlagsChangeTrackerFieldHack implements WGRegionFlagsChangeTracker {
    private static final Field FLAGS_FIELD = getFlagsFieldVerify();
    private ChangeTrackedMap expectedFlags;
    private Map<?, ?> lastFlags;

    public WGRegionFlagsChangeTrackerFieldHack(ProtectedRegion region) throws OptimizationNotSupportedException {
        if (FLAGS_FIELD == null) {
            throw new OptimizationNotSupportedException("ProtectedRegion.flags field is not available");
        }

        expectedFlags = hook(region);
        lastFlags = new HashMap<>(expectedFlags);
    }

    @Override
    public void cleanup(ProtectedRegion region) {
        try {
            if (FLAGS_FIELD == null) {
                throw new IllegalStateException("Flags field not supported");
            }
            Object currFieldValue = FLAGS_FIELD.get(region);
            if (currFieldValue instanceof ChangeTrackedMap) {
                FLAGS_FIELD.set(region, ((ChangeTrackedMap) currFieldValue).base);
            }
        } catch (Throwable t) {
            throw new IllegalStateException("Failed to restore ProtectedRegion.flags field", t);
        }
    }

    @Override
    public boolean update(ProtectedRegion region) {
        // See if the field, or the tracked flags field, changed at all
        if ((Object) region.getFlags() != expectedFlags) {
            try {
                expectedFlags = hook(region);
            } catch (Throwable t) {
                throw new IllegalStateException("Failed to update ProtectedRegion.flags field after modification", t);
            }
        } else if (!expectedFlags.changed.getAndSet(false)) {
            return false;
        }

        // If changed, compare the entire flags map with the slow equals()
        if (region.getFlags().equals(lastFlags)) {
            return false;
        }

        lastFlags = new HashMap<>(region.getFlags());
        return true;
    }

    /**
     * Just swaps out the concurrent hashmap for one where we keep track of when it changes.
     * Total hack btw. Why isn't this change tracking built into worldguard?
     */
    private static class ChangeTrackedMap extends AbstractMap<Object, Object> implements ConcurrentMap<Object, Object> {
        public final ConcurrentMap<Object, Object> base;
        public final AtomicBoolean changed = new AtomicBoolean();

        public ChangeTrackedMap(ConcurrentMap<Object, Object> base) {
            this.base = base;
        }

        @Override
        public int size() {
            return base.size();
        }

        @Override
        public boolean isEmpty() {
            return base.isEmpty();
        }

        @Override
        public boolean containsKey(Object key) {
            return base.containsKey(key);
        }

        @Override
        public boolean containsValue(Object value) {
            return base.containsValue(value);
        }

        @Override
        public Object get(Object key) {
            return base.get(key);
        }

        @Override
        public Object getOrDefault(Object key, Object defaultValue) {
            return base.getOrDefault(key, defaultValue);
        }

        @Override
        public Object put(Object key, Object value) {
            Object oldValue = base.put(key, value);
            changed.set(true);
            return oldValue;
        }

        @Override
        public void putAll(Map<?, ?> m) {
            base.putAll(m);
            changed.set(true);
        }

        @Override
        public Object remove(Object key) {
            Object removed = base.remove(key);
            if (removed != null) {
                changed.set(true);
            }
            return removed;
        }

        @Override
        public Object putIfAbsent(Object key, Object value) {
            Object result = base.putIfAbsent(key, value);
            changed.set(true);
            return result;
        }

        @Override
        public boolean remove(Object key, Object value) {
            if (base.remove(key, value)) {
                changed.set(true);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean replace(Object key, Object oldValue, Object newValue) {
            boolean result = base.replace(key, oldValue, newValue);
            if (result) {
                changed.set(true);
            }
            return result;
        }

        @Override
        public Object replace(Object key, Object value) {
            Object result = base.replace(key, value);
            changed.set(true);
            return result;
        }

        @Override
        public void clear() {
            base.clear();
            changed.set(true);
        }

        @Override
        public Set<Entry<Object, Object>> entrySet() {
            return base.entrySet(); // Let's hope nobody modifies getFlags() while iterating...
        }

        @Override
        public Set<Object> keySet() {
            return base.keySet();
        }

        @Override
        public boolean equals(Object o) {
            return base.equals(o);
        }

        @Override
        public int hashCode() {
            return base.hashCode();
        }
    }

    private static ChangeTrackedMap hook(ProtectedRegion region) throws OptimizationNotSupportedException {
        try {
            ConcurrentMap<Object, Object> base = (ConcurrentMap<Object, Object>) FLAGS_FIELD.get(region);
            if (base instanceof ChangeTrackedMap) {
                return (ChangeTrackedMap) base;
            }

            ChangeTrackedMap tracked = new ChangeTrackedMap(base);
            FLAGS_FIELD.set(region, tracked);

            // Verify that property getter still returns the field as-is
            if ((Object) region.getFlags() != tracked) {
                FLAGS_FIELD.set(region, base); // Restore
                throw new IllegalStateException("ProtectedRegion.getFlags() does not return the flags field");
            }

            return tracked;
        } catch (Throwable t) {
            throw new OptimizationNotSupportedException("Failed to hook ProtectedRegion.flags field", t);
        }
    }

    // private ConcurrentMap<Flag<?>, Object> flags = new ConcurrentHashMap<>();
    private static Field getFlagsFieldVerify() {
        try {
            Field f = ProtectedRegion.class.getDeclaredField("flags");
            if (Modifier.isStatic(f.getModifiers())) {
                return null;
            }
            if (!f.getType().isAssignableFrom(ConcurrentMap.class)) {
                return null;
            }

            f.setAccessible(true);
            return f;
        } catch (Throwable t) {
            return null;
        }
    }

    public static class OptimizationNotSupportedException extends Exception {

        public OptimizationNotSupportedException(String message) {
            super(message);
        }

        public OptimizationNotSupportedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
