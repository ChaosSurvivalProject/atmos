package dev.atmos.protocol.body;

import dev.atmos.protocol.util.Bytes;

/** S→C 0x02：网格尺寸、同步频率、模式 A/B、是否强制 mod、采纳协议版本 */
public final class Welcome {
    public final int gridSize;
    public final int syncPeriodTicks;
    public final int baseMode;     // 0=A / 1=B
    public final int forceMod;     // 0/1
    public final byte acceptedProto;

    public Welcome(int gridSize, int syncPeriodTicks, int baseMode, int forceMod, byte acceptedProto) {
        this.gridSize = gridSize;
        this.syncPeriodTicks = syncPeriodTicks;
        this.baseMode = baseMode;
        this.forceMod = forceMod;
        this.acceptedProto = acceptedProto;
    }

    public byte[] encode() {
        return new Bytes.Writer()
                .u16(gridSize)
                .u16(syncPeriodTicks)
                .u8(baseMode)
                .u8(forceMod)
                .u8(acceptedProto & 0xFF)
                .toByteArray();
    }

    public static Welcome decode(byte[] b) {
        Bytes r = new Bytes(b);
        return new Welcome(r.u16(), r.u16(), r.u8(), r.u8(), (byte) r.u8());
    }
}
