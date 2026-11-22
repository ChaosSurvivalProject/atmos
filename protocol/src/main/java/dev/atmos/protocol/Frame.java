package dev.atmos.protocol;

/** 一帧 = 头 + body。frameType 用 Channel.code，seq 16 位，tick 64 位，body 为具体负载字节。 */
public final class Frame {
    public final byte proto;
    public final Channel type;
    public final int seq;
    public final long tick;
    public final byte[] body;

    public Frame(byte proto, Channel type, int seq, long tick, byte[] body) {
        this.proto = proto;
        this.type = type;
        this.seq = seq & 0xFFFF;
        this.tick = tick;
        this.body = body;
    }

    @Override
    public String toString() {
        return "Frame{" + type + " seq=" + seq + " tick=" + tick + " bodyLen=" + body.length + '}';
    }
}
