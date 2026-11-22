package dev.atmos.server.forecast;

import dev.atmos.protocol.body.Forecast;
import dev.atmos.server.climate.Cyclone;
import dev.atmos.server.climate.ClimateField;

/** 7 日分段预报：由当前场 + 气旋趋势推演，临近时段逐次订正，置信度随距离衰减。 */
public final class ForecastEngine {
    private final int days;
    private final int segmentsPerDay;
    private Forecast current;

    public ForecastEngine(int days, int segmentsPerDay) {
        this.days = days;
        this.segmentsPerDay = segmentsPerDay;
    }

    /** 重新推演（每 MC 日或订正时调用） */
    public Forecast recompute(ClimateField field, Cyclone cyclone, long day) {
        int n = days * segmentsPerDay;
        Forecast.Seg[] segs = new Forecast.Seg[n];
        // 采样当前全局均值作为基准
        float baseT = 0, baseH = 0, baseP = 0;
        int s = field.size, samples = 0;
        for (int z = 0; z < s; z += Math.max(1, s / 16))
            for (int x = 0; x < s; x += Math.max(1, s / 16)) {
                baseT += field.getTemp(x, z); baseH += field.getHum(x, z);
                baseP += field.getPres(x, z); samples++;
            }
        baseT /= samples; baseH /= samples; baseP /= samples;

        for (int i = 0; i < n; i++) {
            int dayIdx = i / segmentsPerDay;
            int segIdx = i % segmentsPerDay;
            double dist = dayIdx + segIdx / (double) segmentsPerDay; // 越远越模糊
            int phase = segIdx; // 0 凌晨 1 上午 2 午后 3 夜间
            double diurnal = Math.sin((phase + 0.5) / segmentsPerDay * Math.PI * 2 - Math.PI / 2) * 5;
            float trend = baseP - 1013;
            // 气旋临近 → 订正为更强天气
            boolean cycloneNear = cyclone != null && cyclone.active && cyclone.intensity > 0.4;
            int kind;
            if (cycloneNear && dayIdx <= 1) kind = baseT > 18 ? 16 : 10;
            else if (trend < -6) kind = 7;
            else if (baseH > 0.65) kind = 3;
            else kind = 0;

            float lo = (float) (baseT + diurnal - 3), hi = (float) (baseT + diurnal + 3);
            int precip = clamp(100 * (kind == 0 ? (int)(baseH*20) : (int)(baseH*100 + (cycloneNear?30:0))), 0, 100);
            int wind = clamp((int) (2 + (cycloneNear ? cyclone.intensity * 10 : Math.abs(trend)/3)), 0, 12);
            int vis = (int) Math.max(500, 30000 - baseH*20000 - (cycloneNear?12000:0));
            int conf = clamp((int) (100 - dist * 12 - (cycloneNear ? 10 : 0)), 5, 100);
            int warn = cycloneNear && dayIdx == 0 && cyclone.intensity > 0.7 ? 3
                     : (cycloneNear && dayIdx <= 1 ? 2 : (trend < -6 ? 1 : 0));
            segs[i] = new Forecast.Seg(kind, lo, hi, precip, wind, vis, conf, warn);
        }
        current = new Forecast(days, segmentsPerDay, segs);
        return current;
    }

    /** 返回完整预报对象 */
    public Forecast current() { return current; }
    /** 返回分段数组 */
    public Forecast.Seg[] segments() { return current == null ? null : current.segments; }
    public int days() { return days; }
    public int segmentsPerDay() { return segmentsPerDay; }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
