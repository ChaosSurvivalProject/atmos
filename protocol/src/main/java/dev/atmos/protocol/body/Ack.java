package dev.atmos.protocol.body;

import dev.atmos.protocol.util.Bytes;

/** C→S 0x09：分片确认。sessionSeq + 收到的片位图 */
public final class Ack {
    public final int sessionSeq;
    public final byte[] receivedBitmap;

    public Ack(int sessionSeq, byte[] receivedBitmap) {
        this.sessionSeq = sessionSeq; this.receivedBitmap = receivedBitmap;
    }

    public byte[] encode() {
        return new Bytes.Writer().u16(sessionSeq).u8(receivedBitmap.length).bytes(receivedBitmap).toByteArray();
    }

    public static Ack decode(byte[] b) {
        Bytes r = new Bytes(b);
        int seq = r.u16();
        int len = r.u8();
        return new Ack(seq, r.bytes(len));
    }
}
