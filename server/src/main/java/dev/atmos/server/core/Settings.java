package dev.atmos.server.core;

import org.bukkit.configuration.file.FileConfiguration;

/** 从 config.yml 读取的强类型配置。 */
public final class Settings {
    public final int gridSize;
    public final int syncPeriodTicks;
    public final int baseMode;        // 0=A / 1=B
    public final boolean forceMod;
    public final boolean realWorldSync;   // 默认 false，仅出站 HTTPS
    public final String weatherApiUrl;
    public final int viewDistanceCells;   // 下发视距（格）
    public final int byteBudgetPerSec;
    public final int msptSoft, msptHard;
    public final boolean effectsEnabled;

    public Settings(FileConfiguration c) {
        gridSize = clamp(c.getInt("grid-size", 128), 32, 512);
        syncPeriodTicks = clamp(c.getInt("sync-period-ticks", 20), 5, 200);
        baseMode = c.getString("weather.base-mode", "A").equalsIgnoreCase("B") ? 1 : 0;
        forceMod = c.getBoolean("network.require-mod", false);
        realWorldSync = c.getBoolean("real-world-sync.enabled", false);
        weatherApiUrl = c.getString("real-world-sync.url", "https://api.open-meteo.com/v1/forecast");
        viewDistanceCells = clamp(c.getInt("network.view-distance-cells", 24), 8, 128);
        byteBudgetPerSec = c.getInt("performance.byte-budget-per-sec", 65536);
        msptSoft = c.getInt("performance.mspt-soft", 12);
        msptHard = c.getInt("performance.mspt-hard", 20);
        effectsEnabled = c.getBoolean("effects.enabled", true);
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
