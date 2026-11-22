package dev.atmos.server.climate;

/** 确定性 2D 分层 simplex/value 噪声（纯计算，无 Bukkit 依赖）。 */
public final class Noise {
    private final long seed;
    private final int[] perm = new int[512];

    public Noise(long seed) {
        this.seed = seed;
        java.util.Random r = new java.util.Random(seed);
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        for (int i = 255; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = p[i]; p[i] = p[j]; p[j] = t;
        }
        for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
    }

    /** 值噪声 [-1,1] */
    public double valueNoise(double x, double y) {
        int xi = fastFloor(x), yi = fastFloor(y);
        double xf = x - xi, yf = y - yi;
        double u = fade(xf), v = fade(yf);
        double aa = hash(xi, yi), ba = hash(xi + 1, yi);
        double ab = hash(xi, yi + 1), bb = hash(xi + 1, yi + 1);
        return lerp(lerp(aa, ba, u), lerp(ab, bb, u), v);
    }

    /** 分形叠加（fBm） */
    public double fbm(double x, double y, int octaves, double lacunarity, double gain) {
        double sum = 0, amp = 1, freq = 1, norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += valueNoise(x * freq, y * freq) * amp;
            norm += amp;
            amp *= gain;
            freq *= lacunarity;
        }
        return sum / norm;
    }

    private double hash(int x, int y) {
        int h = perm[(perm[x & 255] + (y & 255)) & 255];
        return (h / 127.5) - 1.0;
    }

    private static int fastFloor(double x) {
        int i = (int) x;
        return x < i ? i - 1 : i;
    }
    private static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    public long seed() { return seed; }
}
