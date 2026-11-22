package dev.atmos.protocol.util;

import java.io.ByteArrayOutputStream;

/** Minecraft 风格 VarInt（与 MC 协议一致，低位在前，最高位续位）。 */
public final class VarInt {
    public static int read(Bytes b) {
        int value = 0;
        for (int i = 0; i < 5; i++) {
            int r = b.u8();
            value |= (r & 0x7F) << (i * 7);
            if ((r & 0x80) == 0) return value;
        }
        throw new IllegalArgumentException("VarInt too long");
    }

    public static void write(ByteArrayOutputStream out, int value) {
        int v = value;
        while ((v & ~0x7F) != 0) {
            out.write((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.write(v);
    }

    public static byte[] encode(int value) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        write(o, value);
        return o.toByteArray();
    }

    private VarInt() {}
}
