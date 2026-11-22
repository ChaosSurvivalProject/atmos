package dev.atmos.protocol.body;

import dev.atmos.protocol.util.Bytes;

/** S→C 0x05：气旋/锋面单体：中心、半径、强度、移动矢量、生命周期 */
public final class Cells {
    public final Cell[] items;

    public Cells(Cell[] items) { this.items = items; }

    /** 单体记录 */
    public static final class Cell {
        public final int id;
        public final int kind;      // 0 cyclone / 1 front
        public final float centerX, centerZ, radius, intensity, velX, velZ;
        public final int phase;     // 0 生成 1 增强 2 成熟 3 消散

        public Cell(int id, int kind, float centerX, float centerZ, float radius,
                    float intensity, float velX, float velZ, int phase) {
            this.id = id; this.kind = kind; this.centerX = centerX; this.centerZ = centerZ;
            this.radius = radius; this.intensity = intensity; this.velX = velX; this.velZ = velZ;
            this.phase = phase;
        }
    }

    public byte[] encode() {
        Bytes.Writer w = new Bytes.Writer().u8(items.length);
        for (Cell c : items) {
            w.i32(c.id).u8(c.kind)
             .f32(c.centerX).f32(c.centerZ).f32(c.radius).f32(c.intensity)
             .f32(c.velX).f32(c.velZ).u8(c.phase);
        }
        return w.toByteArray();
    }

    public static Cells decode(byte[] b) {
        Bytes r = new Bytes(b);
        int n = r.u8();
        Cell[] items = new Cell[n];
        for (int i = 0; i < n; i++) {
            int id = r.i32(), kind = r.u8();
            float cx = r.f32(), cz = r.f32(), rad = r.f32(), inten = r.f32();
            float vx = r.f32(), vz = r.f32(), phase = r.u8();
            items[i] = new Cell(id, kind, cx, cz, rad, inten, vx, vz, (int) phase);
        }
        return new Cells(items);
    }
}
