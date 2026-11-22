package dev.atmos.protocol.body;

import dev.atmos.protocol.Fixed;
import dev.atmos.protocol.util.Bytes;

/** S→C 0x06：7 日分段预报 + 置信度 + 预警等级 */
public final class Forecast {
    public final int days;
    public final int segmentsPerDay;
    public final Seg[] segments;

    public Forecast(int days, int segmentsPerDay, Seg[] segments) {
        this.days = days; this.segmentsPerDay = segmentsPerDay; this.segments = segments;
    }

    public static final class Seg {
        public final int kind;        // 天气类型 id（注册表顺序）
        public final short tempLo, tempHi;
        public final int precipProb;  // 0..100
        public final int windLevel;   // 0..12
        public final int vis;         // 米
        public final int confidence;  // 0..100
        public final int warnLevel;   // 0..3 蓝黄橙红

        public Seg(int kind, float tempLo, float tempHi, int precipProb, int windLevel,
                   int vis, int confidence, int warnLevel) {
            this.kind = kind; this.tempLo = Fixed.pack(tempLo); this.tempHi = Fixed.pack(tempHi);
            this.precipProb = precipProb; this.windLevel = windLevel; this.vis = vis;
            this.confidence = confidence; this.warnLevel = warnLevel;
        }

        public Seg(int kind, short tempLo, short tempHi, int precipProb, int windLevel,
                   int vis, int confidence, int warnLevel, boolean raw) {
            this.kind = kind; this.tempLo = tempLo; this.tempHi = tempHi;
            this.precipProb = precipProb; this.windLevel = windLevel; this.vis = vis;
            this.confidence = confidence; this.warnLevel = warnLevel;
        }
    }

    public byte[] encode() {
        Bytes.Writer w = new Bytes.Writer().u8(days).u8(segmentsPerDay);
        for (Seg s : segments) {
            w.u8(s.kind).i16(s.tempLo).i16(s.tempHi).u8(s.precipProb)
             .u8(s.windLevel).u16(s.vis).u8(s.confidence).u8(s.warnLevel);
        }
        return w.toByteArray();
    }

    public static Forecast decode(byte[] b) {
        Bytes r = new Bytes(b);
        int days = r.u8(), spd = r.u8();
        int n = days * spd;
        Seg[] segs = new Seg[n];
        for (int i = 0; i < n; i++) {
            int kind = r.u8(); short lo = r.i16(), hi = r.i16();
            int pp = r.u8(), wl = r.u8(), vis = r.u16(), conf = r.u8(), warn = r.u8();
            segs[i] = new Seg(kind, lo, hi, pp, wl, vis, conf, warn, true);
        }
        return new Forecast(days, spd, segs);
    }
}
