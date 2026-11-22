package dev.atmos.protocol.body;

import dev.atmos.protocol.util.Bytes;

/** C→S 0x0A：客户端本地观测（仅用于命中率统计，服务端不据此判定）。
 *  单位置样本：世界 X/Z、客户端测得的温度/降水强度。 */
public final class Probe {
    public final int x, z;
    public final float temp;
    public final float precip;

    public Probe(int x, int z, float temp, float precip) {
        this.x = x; this.z = z; this.temp = temp; this.precip = precip;
    }

    public byte[] encode() {
        return new Bytes.Writer().i32(x).i32(z).f32(temp).f32(precip).toByteArray();
    }

    public static Probe decode(byte[] b) {
        Bytes r = new Bytes(b);
        return new Probe(r.i32(), r.i32(), r.f32(), r.f32());
    }
}
