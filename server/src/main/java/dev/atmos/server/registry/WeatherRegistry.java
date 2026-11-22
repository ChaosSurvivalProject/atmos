package dev.atmos.server.registry;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

/** 配置驱动天气注册表：从 weathers.yml 加载，每类天气的阈值/效果/预警提前量。 */
public final class WeatherRegistry {
    public static final class Def {
        public final int id;
        public final String key, name;
        public final int tier;            // 0 普通 1 强 2 极端
        public final double tempMin, tempMax;
        public final double humMin;
        public final boolean pushEntities, damageCrops, breakGlass, ignite, hailDamage, shelterSafe;
        public final int warnLeadTicks;

        public Def(int id, String key, String name, int tier, double tmin, double tmax, double hmin,
                   boolean push, boolean crops, boolean glass, boolean ignite, boolean hail,
                   boolean shelter, int warnLead) {
            this.id = id; this.key = key; this.name = name; this.tier = tier;
            this.tempMin = tmin; this.tempMax = tmax; this.humMin = hmin;
            this.pushEntities = push; this.damageCrops = crops; this.breakGlass = glass;
            this.ignite = ignite; this.hailDamage = hail; this.shelterSafe = shelter;
            this.warnLeadTicks = warnLead;
        }
    }

    private final List<Def> defs = new ArrayList<>();
    private final Map<Integer, Def> byId = new HashMap<>();

    public WeatherRegistry(File file) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        var section = y.getConfigurationSection("weathers");
        int id = 0;
        if (section != null) {
            for (String key : section.getKeys(false)) {
                var w = section.getConfigurationSection(key);
                if (w == null) continue;
                String name = w.getString("name", key);
                int tier = w.getInt("tier", 0);
                double tmin = w.getDouble("temp-min", -100);
                double tmax = w.getDouble("temp-max", 100);
                double hmin = w.getDouble("hum-min", 0);
                boolean push = w.getBoolean("effects.push-entities", false);
                boolean crops = w.getBoolean("effects.damage-crops", false);
                boolean glass = w.getBoolean("effects.break-glass", false);
                boolean ignite = w.getBoolean("effects.ignite", false);
                boolean hail = w.getBoolean("effects.hail-damage", false);
                boolean shelter = w.getBoolean("effects.shelter-safe", true);
                int lead = w.getInt("warn-lead-ticks", tier == 2 ? 600 : tier == 1 ? 300 : 0);
                Def d = new Def(id++, key, name, tier, tmin, tmax, hmin,
                        push, crops, glass, ignite, hail, shelter, lead);
                defs.add(d); byId.put(d.id, d);
            }
        }
        if (defs.isEmpty()) loadDefaults();
    }

    private void loadDefaults() {
        String[] keys = {"clear","partly_cloudy","cloudy","fog","drizzle","rain","shower","sleet","snow",
            "thunderstorm","heavy_rain","blizzard","hail","freezing_rain","sandstorm","gale","dense_fog",
            "heatwave","drought","superstorm","typhoon","tornado","whiteout","extreme_heat"};
        String[] names = {"晴","多云","阴","雾","毛毛雨","小雨","阵雨","雨夹雪","雪","雷阵雨","暴雨","暴雪",
            "冰雹","冻雨","沙尘暴","大风","浓雾","热浪","干旱","超级雷暴","台风","龙卷风","白毛风","极端高温"};
        for (int i = 0; i < keys.length; i++) {
            int tier = i >= 19 ? 2 : (i >= 10 ? 1 : 0);
            boolean extreme = tier == 2;
            Def d = new Def(i, keys[i], names[i], tier, -100, 100, extreme?0.7:0,
                    extreme, extreme, extreme, extreme, i==12||extreme, true,
                    extreme?600:(tier==1?300:0));
            defs.add(d); byId.put(i, d);
        }
    }

    public Def get(int id) { return byId.getOrDefault(id, defs.get(0)); }
    public List<Def> all() { return Collections.unmodifiableList(defs); }
    public int size() { return defs.size(); }
}
