package dev.atmos.protocol;

/** ×100 定点转换（温度 °C / 气压 hPa 用 short 传输）。 */
public final class Fixed {
    public static short pack(float v) {
        long x = Math.round(v * 100f);
        if (x < Short.MIN_VALUE) return Short.MIN_VALUE;
        if (x > Short.MAX_VALUE) return Short.MAX_VALUE;
        return (short) x;
    }
    public static float unpack(short s) { return s / 100f; }

    private Fixed() {}
}
