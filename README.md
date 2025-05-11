# BKCommonLib Region Flag Tracker
Module included with [BKCommonLib](https://github.com/bergerhealer/BKCommonLib) that allows tracking flags set in regions in WorldGuard.
Passively turns the API into a no-op if WorldGuard is not installed.

## Example Usage
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
                    t.getPlayer().sendMessage("Updated Value: " + t.getValue());
                });
            }
        }, this);
    }

    @Override
    public void onDisable() {
    }
}
```

## Standalone
Normally, the [BKCommonLib](https://github.com/bergerhealer/BKCommonLib) plugin does this initialization.
If you are shading this library into your own plugin instead of relying on BKCommonLib, you have to include this code too.

### Maven Dependency
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

### Soft Depend
```yml
softdepend: [WorldGuard]
```

### Library initialization code
```java
public class MyLibraryPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        RegionFlagRegistryBaseImpl.instance().enable(this);
    }

    @Override
    public void onDisable() {
        RegionFlagRegistryBaseImpl.instance().disable();
    }
}
```
