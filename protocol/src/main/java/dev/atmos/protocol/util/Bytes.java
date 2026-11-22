package dev.atmos.protocol.util;

import java.nio.charset.StandardCharsets;

/** big-endian 字节读写游标，带边界校验（防止越界与超长分配）。 */
public final class Bytes {
    private final byte[] data;
    private int pos;

    public Bytes(byte[] data) { this.data = data; this.pos = 0; }

    public int pos() { return pos; }
    public int remaining() { return data.length - pos; }

    private void need(int n) {
        if (n < 0 || pos + n > data.length)
            throw new IndexOutOfBoundsException("read " + n + " @ " + pos + " of " + data.length);
    }

    public int u8() { need(1); return data[pos++] & 0xFF; }
    public int u16() { need(2); int v = ((data[pos]&0xFF)<<8) | (data[pos+1]&0xFF); pos+=2; return v; }
    public short i16() { need(2); int v = ((data[pos]&0xFF)<<8) | (data[pos+1]&0xFF); pos+=2; return (short)v; }
    public int i32() { need(4); int v = ((data[pos]&0xFF)<<24)|((data[pos+1]&0xFF)<<16)|((data[pos+2]&0xFF)<<8)|(data[pos+3]&0xFF); pos+=4; return v; }
    public long i64() {
        need(8);
        long v = 0;
        for (int i=0;i<8;i++) v = (v<<8) | (data[pos+i]&0xFFL);
        pos+=8; return v;
    }
    public float f32() { return Float.intBitsToFloat(i32()); }

    public byte[] bytes(int n) {
        need(n);
        byte[] out = new byte[n];
        System.arraycopy(data, pos, out, 0, n);
        pos += n;
        return out;
    }

    public String str() {
        int len = VarInt.read(this);
        if (len < 0 || len > 1 << 20) throw new IllegalArgumentException("bad str len " + len);
        return new String(bytes(len), StandardCharsets.UTF_8);
    }

    /* ---------- writer ---------- */
    public static final class Writer {
        private final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        public Writer u8(int v) { out.write(v & 0xFF); return this; }
        public Writer u16(int v) { out.write((v>>>8)&0xFF); out.write(v&0xFF); return this; }
        public Writer i16(int v) { return u16(v & 0xFFFF); }
        public Writer i32(int v) { out.write((v>>>24)&0xFF); out.write((v>>>16)&0xFF); out.write((v>>>8)&0xFF); out.write(v&0xFF); return this; }
        public Writer i64(long v) { for (int i=7;i>=0;i--) out.write((int)(v>>>(i*8))&0xFF); return this; }
        public Writer f32(float v) { return i32(Float.floatToRawIntBits(v)); }
        public Writer bytes(byte[] b) { out.writeBytes(b); return this; }
        public Writer str(String s) {
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            VarInt.write(out, b.length);
            out.writeBytes(b);
            return this;
        }
        public byte[] toByteArray() { return out.toByteArray(); }
        public int size() { return out.size(); }
    }
}
