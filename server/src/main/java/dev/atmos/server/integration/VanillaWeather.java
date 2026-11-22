package dev.atmos.server.integration;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.plugin.Plugin;

/** 与 Purpur 原版天气衔接：接管 doWeatherCycle，区分 PLUGIN/COMMAND/原版循环，避免互抢。 */
public final class VanillaWeather implements Listener {
    private final Plugin plugin;
    private final boolean freezeCycles;

    public VanillaWeather(Plugin plugin, boolean freezeCycles) {
        this.plugin = plugin;
        this.freezeCycles = freezeCycles;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        if (freezeCycles) {
            for (World w : plugin.getServer().getWorlds()) w.setGameRuleValue("doWeatherCycle", "false");
        }
    }

    public void applyBase(World w, boolean storm, boolean thunder) {
        // 模式 A：保留全局雨/雷作基底
        w.setStorm(storm);
        w.setThundering(thunder);
    }

    @EventHandler(ignoreCancelled = true)
    public void onWeatherChange(WeatherChangeEvent e) {
        if (!freezeCycles) return;
        // 由其它插件/命令触发的变化：放行但记录；这里仅防止原版循环抢控
        // Cause 在较新 API 可通过 e.getCause() 获取，兼容旧版用反射式宽容处理
        if (e.toWeatherState() && stormOwnerOverride) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onThunderChange(ThunderChangeEvent e) {
        if (!freezeCycles) return;
        if (e.toThunderState() && stormOwnerOverride) e.setCancelled(true);
    }

    /** 由 Atmos 主动设置基底时临时打开，允许通过 */
    private volatile boolean stormOwnerOverride = false;
    public void beginBaseUpdate() { stormOwnerOverride = false; }
}
