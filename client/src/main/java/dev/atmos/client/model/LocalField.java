package dev.atmos.client.model;

import dev.atmos.protocol.body.Cell;

import java.util.concurrent.atomic.AtomicReference;

/** 客户端局地场快照缓存（只用于渲染插值，不参与任何判定）。 */
public final class LocalField {
    public static final class Snapshot {
        public final int originX, originZ, width, height;
        public final Cell[] cells;
        public final long recvNanos;
        Snapshot(int ox, int oz, int w, int h, Cell[] cells) {
            this.originX = ox; this.originZ = oz; this.width = w; this.height = h;
            this.cells = cells; this.recvNanos = System.nanoTime();
        }
    }

    private final AtomicReference<Snapshot> current = new AtomicReference<>();
    private final AtomicReference<Snapshot> previous = new AtomicReference<>();

    public void update(int ox, int oz, int w, int h, Cell[] cells) {
        Snapshot prev = current.get();
        Snapshot next = new Snapshot(ox, oz, w, h, cells);
        previous.set(prev == null ? next : prev);
        current.set(next);
    }

    public Snapshot current() { return current.get(); }
    public Snapshot previous() { return previous.get(); }

    /** 两帧间的插值 alpha（0..1） */
    public float alpha(long nowNanos, long periodNanos) {
        Snapshot s = current.get();
        if (s == null || periodNanos <= 0) return 1f;
        long elapsed = nowNanos - s.recvNanos;
        return (float) Math.max(0, Math.min(1, elapsed / (double) periodNanos));
    }

    /** 按网格坐标取插值后的单元（渲染用） */
    public Cell sample(float gx, float gz, float alpha) {
        Snapshot a = previous.get(), b = current.get();
        if (b == null) return null;
        if (a == null) a = b;
        Cell ca = pick(a, gx, gz), cb = pick(b, gx, gz);
        if (ca == null) return cb;
        if (cb == null) return ca;
        return lerp(ca, cb, alpha);
    }

    private Cell pick(Snapshot s, float gx, float gz) {
        int lx = (int) (gx - s.originX), lz = (int) (gz - s.originZ);
        if (lx < 0 || lz < 0 || lx >= s.width || lz >= s.height) return null;
        return s.cells[lz * s.width + lx];
    }

    private static Cell lerp(Cell a, Cell b, float t) {
        return new Cell(
            (short) (a.temp + (b.temp - a.temp) * t),
            (short) (a.humidity + (b.humidity - a.humidity) * t),
            (short) (a.pressure + (b.pressure - a.pressure) * t),
            (short) (a.trend + (b.trend - a.trend) * t),
            (short) (a.dew + (b.dew - a.dew) * t),
            (short) (a.windU + (b.windU - a.windU) * t),
            (short) (a.windV + (b.windV - a.windV) * t),
            (short) (a.cloud + (b.cloud - a.cloud) * t),
            (short) (a.vis + (b.vis - a.vis) * t)
        );
    }
}
