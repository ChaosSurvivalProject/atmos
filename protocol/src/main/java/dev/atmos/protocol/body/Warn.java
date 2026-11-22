package dev.atmos.protocol.body;

import dev.atmos.protocol.util.Bytes;

/** S→C 0x07：预警（即时）。warnLevel 0..3 蓝黄橙红 */
public final class Warn {
    public final int warnLevel;
    public final int cellId;
    public final long leadTicks;
    public final String messageKey;

    public Warn(int warnLevel, int cellId, long leadTicks, String messageKey) {
        this.warnLevel = warnLevel; this.cellId = cellId;
        this.leadTicks = leadTicks; this.messageKey = messageKey;
    }

    public byte[] encode() {
        return new Bytes.Writer().u8(warnLevel).i32(cellId).i64(leadTicks).str(messageKey).toByteArray();
    }

    public static Warn decode(byte[] b) {
        Bytes r = new Bytes(b);
        return new Warn(r.u8(), r.i32(), r.i64(), r.str());
    }
}
