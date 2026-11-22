package dev.atmos.server.effects;

import dev.atmos.server.climate.ClimateField;
import dev.atmos.server.registry.WeatherRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/** 游戏效果引擎：所有判定在服务端，主线程 apply。极端天气室内完全安全。 */
public final class EffectEngine {
    private final ClimateField field;
    private final WeatherRegistry registry;
    private final boolean enabled;

    public EffectEngine(ClimateField field, WeatherRegistry registry, boolean enabled) {
        this.field = field; this.registry = registry; this.enabled = enabled;
    }

    /** 每个效果 tick 调用（必须在主线程） */
    public void apply(long tick) {
        if (!enabled) return;
        for (World w : Bukkit.getWorlds()) {
            for (Player p : w.getPlayers()) {
                Location loc = p.getLocation();
                // 网格坐标：以世界原点为中心，1 格 = 若干方块
                double gx = loc.getX() / 8.0 + field.size / 2.0;
                double gz = loc.getZ() / 8.0 + field.size / 2.0;
                ClimateField.Sample s = field.sample(gx, gz);
                int kind = field.weatherKind(s);
                WeatherRegistry.Def def = registry.get(kind);
                boolean shelter = Shelter.isSheltered(loc);

                // 风力推拉暴露实体
                if (def.pushEntities && !shelter) {
                    float speed = (float) Math.hypot(s.windU, s.windV);
                    if (speed > 6f) {
                        Vector push = new Vector(s.windU * 0.02, 0.02, s.windV * 0.02);
                        p.setVelocity(p.getVelocity().add(push));
                    }
                }
                // 极端高温给予缓慢虚弱（暴露）
                if (def.tier == 2 && s.temp > 40 && !shelter && tick % 40 == 0) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0));
                }
                // 冰雹砸伤（暴露）
                if (def.hailDamage && !shelter && tick % 60 == 0 && p.getHealth() > 2) {
                    p.damage(1.0);
                }
                // 能见度压缩：通过缓慢的失明效果模拟（暴露）
                if (s.vis < 1500 && !shelter && tick % 100 == 0) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0));
                }
            }
        }
    }
}
