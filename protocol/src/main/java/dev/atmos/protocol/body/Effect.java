package dev.atmos.protocol.body;

import dev.atmos.protocol.util.Bytes;

/** S→C 0x08：一次性特效 / 方位雷声（即时） */
public final class Effect {
    public final int effectId;
    public final float x, y, z;
    public final long param;

    public Effect(int effectId, float x, float y, float z, long param) {
        this.effectId = effectId; this.x = x; this.y = y; this.z = z; this.param = param;
    }

    public byte[] encode() {
        return new Bytes.Writer().u8(effectId).f32(x).f32(y).f32(z).i64(param).toByteArray();
    }

    public static Effect decode(byte[] b) {
        Bytes r = new Bytes(b);
        return new Effect(r.u8(), r.f32(), r.f32(), r.f32(), r.i64());
    }
}
