package dev.atmos.server.api;

import dev.atmos.server.api.event.*;
import dev.atmos.server.climate.ClimateField;
import dev.atmos.server.forecast.ForecastEngine;
import org.bukkit.Bukkit;

/** 公共 API：供其他插件读取当前场与预报。 */
public final class AtmosAPI {
    private static ClimateField field;
    private static ForecastEngine forecast;

    public static void init(ClimateField f, ForecastEngine fc) {
        field = f; forecast = fc;
    }

    /** 按网格坐标取当前场样本 */
    public static ClimateField.Sample current(double gridX, double gridZ) {
        return field == null ? null : field.sample(gridX, gridZ);
    }

    /** 取第 day 天（0-based）的分段预报 */
    public static ForecastEngine forecast(int day) {
        return forecast;
    }

    public static void publishForecast(int days) {
        Bukkit.getPluginManager().callEvent(new ForecastPublishEvent(days));
    }

    public static void warn(int level, String key) {
        Bukkit.getPluginManager().callEvent(new ExtremeWarningEvent(level, key));
    }

    private AtmosAPI() {}
}
