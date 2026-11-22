package dev.atmos.protocol.body;

import dev.atmos.protocol.util.Bytes;

/** S→C 0x03：游戏刻 + 单调毫秒，供客户端插值对齐 */
public final class Clock {
    public final long gameTick;
    public final long monotonicMs;

    public Clock(long gameTick, long monotonicMs) {
        this.gameTick = gameTick;
        this.monotonicMs = monotonicMs;
    }

    public byte[] encode() {
        return new Bytes.Writer().i64(gameTick).i64(monotonicMs).toByteArray();
    }

    public static Clock decode(byte[] b) {
        Bytes r = new Bytes(b);
        return new Clock(r.i64(), r.i64());
    }
}
