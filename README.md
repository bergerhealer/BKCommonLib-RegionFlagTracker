# BKCommonLib Region Flag Tracker
Module included with BKCommonLib that allows tracking flags set in regions in WorldGuard.
Passively turns the API into a no-op if WorldGuard is not installed.

## Maven Dependency
```xml
<repositories>
    <repository>
        <id>mg-dev repo</id>
        <url>https://ci.mg-dev.eu/plugin/repository/everything</url>
    </repository>
</repositories>
<dependencies>
    <dependency>
        <groupId>com.bergerkiller.bukkit.regionflagtracker</groupId>
        <artifactId>BKCommonLib-RegionFlagTracker-Core</artifactId>
        <version>1.0</version>
        <optional>true</optional>
    </dependency>
</dependencies>
```

Include _com.bergerkiller.bukkit.regionflagtracker_ in the maven shade plugin to shade it in

## Soft Depend
```yml
softdepend: [WorldGuard]
```

## Example Plugin
```java
public class MyPlugin extends JavaPlugin {
    public static final RegionFlag<Integer> MY_NUMBER = RegionFlag.ofInteger("my_number");

    @Override
    public void onLoad() {
        RegionFlagRegistry.instance().register(this, MY_NUMBER);
    }

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPlayerJoin(PlayerJoinEvent event) {
                RegionFlagTracker<Integer> tracker = RegionFlagTracker.track(event.getPlayer(), MY_NUMBER);

                tracker.getPlayer().sendMessage("Initial Value: " + tracker.getValue());
                tracker.addListener(t -> {
                    t.getPlayer().sendMessage("Initial Value: " + t.getValue());
                });
            }
        }, this);
    }

    @Override
    public void onDisable() {
    }
}
```
