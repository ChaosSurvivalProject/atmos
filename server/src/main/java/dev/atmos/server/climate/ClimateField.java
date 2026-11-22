package dev.atmos.server.climate;

/** 连续演化的大气场：256²/512² 网格，每格七要素。纯计算，无 Bukkit 依赖。 */
public final class ClimateField {
    public final int size;
    private final float[] temp, humidity, pressure, trend, dew, windU, windV, cloud, vis;
    private final float[] nextTemp, nextPressure, nextHum;

    public ClimateField(int size, long seed) {
        this.size = size;
        int n = size * size;
        temp = new float[n]; humidity = new float[n]; pressure = new float[n];
        trend = new float[n]; dew = new float[n]; windU = new float[n]; windV = new float[n];
        cloud = new float[n]; vis = new float[n];
        nextTemp = new float[n]; nextPressure = new float[n]; nextHum = new float[n];

        Noise base = new Noise(seed);
        Noise detail = new Noise(seed ^ 0x5DEECE66DL);
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int i = z * size + x;
                double nx = x / (double) size, nz = z / (double) size;
                // 纬度温度梯度 + 噪声
                double lat = 1.0 - Math.abs(nz - 0.5) * 2.0; // 0..1 赤道最高
                float t = (float) (28 * lat - 5 + base.fbm(nx * 3, nz * 3, 4, 2.0, 0.5) * 8);
                temp[i] = t;
                double h = 0.5 + detail.fbm(nx * 4 + 10, nz * 4 + 10, 3, 2.0, 0.5) * 0.4;
                humidity[i] = (float) Math.max(0, Math.min(1, h));
                pressure[i] = (float) (1013 + base.fbm(nx * 2 + 50, nz * 2 + 50, 3, 2.0, 0.5) * 20);
                dew[i] = t - (float) (100 - humidity[i] * 100) / 5f;
                cloud[i] = humidity[i] * 0.8f;
                vis[i] = (float) (30000 - humidity[i] * 20000);
                windU[i] = (float) (detail.fbm(nx * 5, nz * 5, 2, 2.0, 0.5) * 6);
                windV[i] = (float) (detail.fbm(nx * 5 + 7, nz * 5 + 7, 2, 2.0, 0.5) * 6);
            }
        }
    }

    /** 推进一个演化步（异步调用，纯计算）。锋面随风传播，格间平滑过渡。 */
    public void step(long tick, Cyclone cyclone) {
        int s = size;
        for (int z = 0; z < s; z++) {
            for (int x = 0; x < s; x++) {
                int i = z * s + x;
                // 上游采样点（风驱动平流）
                int ux = wrap((int) (x - windU[i] * 0.6f), s);
                int uz = wrap((int) (z - windV[i] * 0.6f), s);
                int up = uz * s + ux;
                // 阻尼混合：本格 70% + 上游 30%，形成连续传播无硬边
                float t = temp[i] * 0.7f + temp[up] * 0.3f;
                float h = humidity[i] * 0.7f + humidity[up] * 0.3f;
                float p = pressure[i] * 0.75f + pressure[up] * 0.25f;

                // 气旋吸引子：气压/湿度/风朝向中心缓慢收敛
                if (cyclone != null && cyclone.active) {
                    double dx = x - cyclone.x, dz = z - cyclone.z;
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    if (dist < cyclone.radius) {
                        double infl = (1 - dist / cyclone.radius) * cyclone.intensity * 0.02;
                        p -= (float) (infl * 20);          // 低压
                        h += (float) (infl * 0.5);         // 增湿
                        // 切向风（逆时针）
                        double ang = Math.atan2(dz, dx);
                        windU[i] += (float) (-Math.sin(ang) * infl * 30);
                        windV[i] += (float) (Math.cos(ang) * infl * 30);
                    }
                }
                // 摩擦阻尼，防止风无限累加
                windU[i] *= 0.995f;
                windV[i] *= 0.995f;

                nextTemp[i] = clamp(t, -40, 55);
                nextHum[i] = clamp(h, 0, 1);
                nextPressure[i] = clamp(p, 870, 1085);
            }
        }
        for (int i = 0; i < temp.length; i++) {
            float oldP = pressure[i];
            temp[i] = nextTemp[i];
            humidity[i] = nextHum[i];
            pressure[i] = nextPressure[i];
            trend[i] = (pressure[i] - oldP) * 180f; // 近似 3h 变压
            dew[i] = dewPoint(temp[i], humidity[i]);
            cloud[i] = clamp(humidity[i] * 0.9f + Math.max(0, -trend[i]) / 50f, 0, 1);
            vis[i] = (float) Math.max(50, 30000 - humidity[i] * 22000 - cloud[i] * 6000);
        }
    }

    /** Magnus 公式由温度+相对湿度反算露点 */
    public static float dewPoint(float t, float rh) {
        if (rh <= 0) rh = 0.001f;
        double a = 17.62, b = 243.12;
        double gamma = Math.log(rh) + (a * t) / (b + t);
        return (float) (b * gamma / (a - gamma));
    }

    /** 按 (x,z) 双线性插值取格值（客户端渲染/效果判定用） */
    public Sample sample(double x, double z) {
        int s = size;
        double gx = wrapf(x, s), gz = wrapf(z, s);
        int x0 = (int) gx, z0 = (int) gz;
        int x1 = (x0 + 1) % s, z1 = (z0 + 1) % s;
        double fx = gx - x0, fz = gz - z0;
        int i00 = z0 * s + x0, i10 = z0 * s + x1, i01 = z1 * s + x0, i11 = z1 * s + x1;
        return new Sample(
            (float) bilinear(temp[i00], temp[i10], temp[i01], temp[i11], fx, fz),
            (float) bilinear(humidity[i00], humidity[i10], humidity[i01], humidity[i11], fx, fz),
            (float) bilinear(pressure[i00], pressure[i10], pressure[i01], pressure[i11], fx, fz),
            (float) bilinear(trend[i00], trend[i10], trend[i01], trend[i11], fx, fz),
            (float) bilinear(windU[i00], windU[i10], windU[i01], windU[i11], fx, fz),
            (float) bilinear(windV[i00], windV[i10], windV[i01], windV[i11], fx, fz),
            (float) bilinear(cloud[i00], cloud[i10], cloud[i01], cloud[i11], fx, fz),
            (float) bilinear(vis[i00], vis[i10], vis[i01], vis[i11], fx, fz)
        );
    }

    /** 天气类型判定（气压真降才来风暴，加权状态转移） */
    public int weatherKind(Sample s) {
        boolean stormPressure = s.trend < -1.2f;
        if (s.temp < -2 && s.cloud > 0.6 && s.humidity > 0.6) return stormPressure ? 9 : 8; // 暴雪/雪
        if (s.temp > 32 && s.humidity < 0.3) return 17;                          // 热浪/干旱
        if (stormPressure && s.humidity > 0.8) {
            if (s.temp > 20) return 16;                                          // 超级雷暴
            return 10;                                                           // 暴雨
        }
        if (s.cloud > 0.75 && s.humidity > 0.7) return s.temp < 1 ? 8 : 7;       // 雪/阵雨
        if (s.cloud > 0.5 && s.humidity > 0.55) return 3;                        // 阴
        if (s.cloud > 0.3) return 2;                                             // 多云
        if (s.vis < 2000) return 4;                                              // 雾
        return 0;                                                                // 晴
    }

    /** 降水强度 0..1 */
    public float precipitation(Sample s) {
        int k = weatherKind(s);
        if (k == 0 || k == 1 || k == 2 || k == 3 || k == 4 || k >= 17) return 0;
        return Math.max(0, Math.min(1, (s.humidity - 0.4f) * 2f));
    }

    public CellFetch cells(int ox, int oz, int w, int h) {
        return new CellFetch(this, ox, oz, w, h);
    }

    /** 供 Field body 逐格取量化值 */
    public final class CellFetch {
        final int ox, oz, w, h;
        CellFetch(ClimateField f, int ox, int oz, int w, int h) {
            this.ox = ox; this.oz = oz; this.w = w; this.h = h;
        }
        public short[] tempRow(int z, int x0) {
            short[] r = new short[w];
            for (int c = 0; c < w; c++) r[c] = dev.atmos.protocol.Fixed.pack(temp[(oz+z)*size + wrap(ox+x0+c, size)]);
            return r;
        }
    }

    public float getTemp(int x, int z) { return temp[wrap(z,size)*size + wrap(x,size)]; }
    public float getHum(int x, int z) { return humidity[wrap(z,size)*size + wrap(x,size)]; }
    public float getPres(int x, int z) { return pressure[wrap(z,size)*size + wrap(x,size)]; }

    public static final class Sample {
        public final float temp, humidity, pressure, trend, windU, windV, cloud, vis;
        Sample(float temp, float humidity, float pressure, float trend, float windU,
               float windV, float cloud, float vis) {
            this.temp = temp; this.humidity = humidity; this.pressure = pressure;
            this.trend = trend; this.windU = windU; this.windV = windV;
            this.cloud = cloud; this.vis = vis;
        }
    }

    private static double bilinear(double a, double b, double c, double d, double fx, double fz) {
        return (a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + d * fx) * fz;
    }
    private static int wrap(int v, int s) { return ((v % s) + s) % s; }
    private static double wrapf(double v, double s) { return ((v % s) + s) % s; }
    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
}
