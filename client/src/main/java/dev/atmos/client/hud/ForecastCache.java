package dev.atmos.client.hud;

import dev.atmos.protocol.body.Forecast;

/** 客户端预报/预警缓存（只用于 HUD 显示）。 */
public final class ForecastCache {
    private static volatile Forecast forecast;
    private static volatile int warnLevel;
    private static volatile String warnKey = "";
    private static volatile long warnAt;

    public static void set(Forecast f) { forecast = f; }
    public static Forecast get() { return forecast; }

    public static void warn(int level, String key) {
        warnLevel = level; warnKey = key; warnAt = System.currentTimeMillis();
    }
    public static int warnLevel() { return warnLevel; }
    public static String warnKey() { return warnKey; }
    public static boolean warnActive() { return System.currentTimeMillis() - warnAt < 15000; }
}
