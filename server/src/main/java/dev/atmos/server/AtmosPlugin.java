package dev.atmos.server;

import dev.atmos.protocol.Channel;
import dev.atmos.protocol.body.*;
import dev.atmos.server.api.AtmosAPI;
import dev.atmos.server.climate.*;
import dev.atmos.server.cmd.WxCommand;
import dev.atmos.server.core.Settings;
import dev.atmos.server.effects.EffectEngine;
import dev.atmos.server.forecast.*;
import dev.atmos.server.integration.VanillaWeather;
import dev.atmos.server.netbridge.*;
import dev.atmos.server.persist.SnapshotStore;
import dev.atmos.server.registry.WeatherRegistry;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;

/** Atmos 插件主类：唯一权威。异步演化 → 主线程 apply；所有通信复用 25565。 */
public final class AtmosPlugin extends JavaPlugin {
    private Settings settings;
    private ClimateField field;
    private Cyclone cyclone;
    private ForecastEngine forecast;
    private Scoreboard scoreboard;
    private WeatherRegistry registry;
    private EffectEngine effects;
    private SnapshotStore snapshot;
    private ChannelBridge bridge;
    private Budget budget;
    private VanillaWeather vanilla;
    private BukkitTask climateTask, networkTask, effectTask;
    private long tickCounter;
    private final Map<UUID, Long> lastWarn = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfigs();
        settings = new Settings(getConfig());
        File data = getDataFolder();
        snapshot = new SnapshotStore(data.toPath());
        registry = new WeatherRegistry(new File(data, "weathers.yml"));
        field = new ClimateField(settings.gridSize, 20261122L);
        cyclone = new Cyclone(settings.gridSize / 2.0, settings.gridSize / 2.0, 40, 0.4, 12345L);
        forecast = new ForecastEngine(7, 4);
        scoreboard = new Scoreboard(data.toPath());
        effects = new EffectEngine(field, registry, settings.effectsEnabled);
        budget = new Budget(settings.byteBudgetPerSec, settings.msptSoft, settings.msptHard);
        vanilla = new VanillaWeather(this, true);

        bridge = new ChannelBridge(this);
        bridge.setMaxPerSec(20);
        bridge.addListener(new ChannelBridge.HandshakeListener() {
            @Override public void onHello(Player p, Hello h) { /* welcome 已发 */ }
            @Override public void onAck(Player p, Ack a) { /* 补片由客户端重组，服务端按需忽略 */ }
            @Override public void onProbe(Player p, Probe pr) {
                // 仅统计，不判定
                int actual = field.weatherKind(field.sample(pr.x, pr.z));
                scoreboard.record(actual, actual);
            }
        });

        AtmosAPI.init(field, forecast);
        getCommand("wx").setExecutor(new WxCommand(this));

        startTasks();
        getLogger().info("Atmos 启用：网格=" + settings.gridSize + " 模式=" + (settings.baseMode==0?"A":"B")
                + " 端口仅 25565（Custom Payload），无额外监听。");
    }

    private void startTasks() {
        // 异步气候演化（纯计算，不碰 Bukkit 对象）
        climateTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            cyclone.step(tickCounter, field.size);
            field.step(tickCounter, cyclone);
            tickCounter++;
        }, 1L, 1L);

        // 主线程：效果 + 原版基底 + 降级预算
        effectTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            double mspt = avgMspt();
            int lvl = budget.update(mspt);
            if (tickCounter % 20 == 0) {
                effects.apply(tickCounter);
                applyVanillaBase(lvl);
                checkWarnings();
            }
            if (tickCounter % (20 * 60 * 20) == 0) republishForecast(); // 每 MC 日
        }, 1L, 1L);

        // 主线程发包（field/clock/cells/forecast），受握手闸门与预算约束
        networkTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            int period = budget.effectivePeriod(settings.syncPeriodTicks);
            if (tickCounter % period != 0) return;
            for (Player p : Bukkit.getOnlinePlayers()) {
                var s = bridge.sessionOrNull(p);
                if (s == null || !s.handshaked) continue; // 闸门：未握手不发
                sendClock(p);
                if (budget.onlyWarnings()) continue;
                sendField(p, s);
                if (tickCounter % (period * 3) == 0) sendCells(p);
                if (tickCounter % (period * 20) == 0) sendForecast(p);
            }
        }, 1L, 1L);
    }

    private void sendClock(Player p) {
        long mono = System.nanoTime() / 1_000_000L;
        byte[] body = new Clock(Bukkit.getCurrentTick(), mono).encode();
        bridge.send(p, Channel.CLOCK, body);
    }

    private void sendField(Player p, Session s) {
        int w = Math.min(16, budget.effectiveGrid(field.size));
        int h = w;
        int gx = (int) (p.getLocation().getX() / 8.0 + field.size / 2.0);
        int gz = (int) (p.getLocation().getZ() / 8.0 + field.size / 2.0);
        int ox = gx - w / 2, oz = gz - h / 2;
        dev.atmos.protocol.body.Cell[] cells = new dev.atmos.protocol.body.Cell[w * h];
        int idx = 0;
        for (int dz = 0; dz < h; dz++) {
            short[] rowT = field.cells(ox, oz, w, h).tempRow(dz, 0);
            for (int dx = 0; dx < w; dx++) {
                int fx = ox + dx, fz = oz + dz;
                float t = field.getTemp(fx, fz), hum = field.getHum(fx, fz), pres = field.getPres(fx, fz);
                ClimateField.Sample sm = field.sample(fx, fz);
                cells[idx++] = dev.atmos.protocol.body.Cell.of(t, hum, pres, sm.trend, sm.temp - (100-hum*100)/5f,
                        sm.windU, sm.windV, sm.cloud, (int) sm.vis);
            }
        }
        Field body = new Field(0, 1, ox, oz, w, h, cells);
        byte[] encoded = body.encode();
        if (budget.allow(encoded.length)) bridge.send(p, Channel.FIELD, encoded);
    }

    private void sendCells(Player p) {
        if (!cyclone.active) return;
        Cells.Cell item = new Cells.Cell(1, cyclone.intensity > 0.6 ? 0 : 1,
                (float) cyclone.x, (float) cyclone.z, (float) cyclone.radius,
                (float) cyclone.intensity, (float) cyclone.vx, (float) cyclone.vz, cyclone.phase());
        bridge.send(p, Channel.CELLS, new Cells(new Cells.Cell[]{item}).encode());
    }

    private void sendForecast(Player p) {
        Forecast fc = forecast.current();
        if (fc == null) fc = forecast.recompute(field, cyclone, snapshot.year());
        bridge.send(p, Channel.FORECAST, fc.encode());
    }

    private void applyVanillaBase(int level) {
        // 模式 A：保留全局基底，按"玩家平均所在地"的场决定是否 storm/thunder
        if (settings.baseMode != 0) return; // 模式 B 不设基底
        ClimateField.Sample s = field.sample(field.size/2.0, field.size/2.0);
        int kind = field.weatherKind(s);
        boolean storm = kind >= 5 && kind <= 16;
        boolean thunder = kind == 9 || kind == 16;
        for (World w : Bukkit.getWorlds()) vanilla.applyBase(w, storm, thunder);
    }

    private void checkWarnings() {
        if (!cyclone.active || cyclone.intensity < 0.7) return;
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Long last = lastWarn.get(p.getUniqueId());
            if (last != null && now - last < 30000) continue;
            lastWarn.put(p.getUniqueId(), now);
            int level = cyclone.intensity > 0.9 ? 3 : 2;
            Warn w = new Warn(level, 1, 600, "warn.storm");
            bridge.send(p, Channel.WARN, w.encode());
            AtmosAPI.warn(level, "warn.storm");
        }
    }

    private void republishForecast() {
        Forecast fc = forecast.recompute(field, cyclone, snapshot.year());
        AtmosAPI.publishForecast(settings.baseMode);
        // 记录命中率：上一天的预报 vs 现在实况
        var segs = forecast.segments();
        if (segs != null && segs.length > 0) scoreboard.record(segs[0].kind, field.weatherKind(field.sample(field.size/2.0, field.size/2.0)));
    }

    private double avgMspt() {
        // Paper API 提供近平均 tick 时间（毫秒）
        try {
            double mspt = Bukkit.getServer().getAverageTickTime();
            return mspt > 0 ? mspt : 10;
        } catch (Throwable t) {
            return 10; // 极端兼容：降级默认值
        }
    }

    private void saveDefaultConfigs() {
        saveDefaultConfig();
        saveResourceIfAbsent("weathers.yml");
        saveResourceIfAbsent("biomes.yml");
        saveResourceIfAbsent("forecast.yml");
        saveResourceIfAbsent("messages.yml");
    }

    private void saveResourceIfAbsent(String name) {
        if (!new File(getDataFolder(), name).exists()) saveResource(name, false);
    }

    @Override
    public void onDisable() {
        if (climateTask != null) climateTask.cancel();
        if (networkTask != null) networkTask.cancel();
        if (effectTask != null) effectTask.cancel();
        snapshot.setYear(tickCounter / (20L * 60 * 20));
        snapshot.setDroughtDays(0);
        snapshot.setSnowDepth(0);
        snapshot.setSoilMoisture(0.5f);
        snapshot.save();
        getLogger().info("Atmos 停用：快照已保存。");
    }

    // --- 供命令与 API 访问 ---
    public ClimateField field() { return field; }
    public Cyclone cyclone() { return cyclone; }
    public ForecastEngine forecast() { return forecast; }
    public Scoreboard scoreboard() { return scoreboard; }
    public WeatherRegistry registry() { return registry; }
    public Settings settings() { return settings; }
    public ChannelBridge bridge() { return bridge; }
    public Budget budget() { return budget; }
    public long tick() { return tickCounter; }

    /** 生成一份当前实况文本（命令用） */
    public String nowText() {
        ClimateField.Sample s = field.sample(field.size/2.0, field.size/2.0);
        int kind = field.weatherKind(s);
        WeatherRegistry.Def d = registry.get(kind);
        return String.format("当前：%s | 温度 %.1f°C | 湿度 %.0f%% | 气压 %.0f hPa (3h %+.1f) | 风 %.1f m/s | 能见度 %.0f m",
                d.name, s.temp, s.humidity*100, s.pressure, s.trend, Math.hypot(s.windU, s.windV), s.vis);
    }
}
