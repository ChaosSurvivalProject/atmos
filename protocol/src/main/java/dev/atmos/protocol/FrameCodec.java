package dev.atmos.protocol;

import dev.atmos.protocol.util.Bytes;
import dev.atmos.protocol.util.VarInt;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * 帧手工编解码：magic(4) | proto(1) | type(1) | seq(2) | tick(8) | len(VarInt) | body。
 * 服务端与 Fabric 端共用此类，禁止各自框架隐式序列化。
 */
public final class FrameCodec {

    public static byte[] encode(Frame f) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(AtmosProtocol.MAGIC);
        out.write(f.proto & 0xFF);
        out.write(f.type.code & 0xFF);
        Bytes.Writer head = new Bytes.Writer().u16(f.seq).i64(f.tick);
        out.writeBytes(head.toByteArray());
        VarInt.write(out, f.body.length);
        out.writeBytes(f.body);
        return out.toByteArray();
    }

    /** 严格解码：魔数不符/版本不支持/长度异常/超长 → 抛异常，由调用方降级。 */
    public static Frame decode(byte[] data) {
        if (data == null || data.length < 16)
            throw new IllegalArgumentException("frame too short");
        for (int i = 0; i < 4; i++)
            if (data[i] != AtmosProtocol.MAGIC[i]) throw new IllegalArgumentException("bad magic");
        int proto = data[4] & 0xFF;
        if (!AtmosProtocol.isSupportedVersion((byte) proto))
            throw new IllegalArgumentException("unsupported proto " + proto);
        int type = data[5] & 0xFF;
        Channel ch = Channel.byCode(type);
        if (ch == null) throw new IllegalArgumentException("unknown type " + type);
        // 跳过 magic(4)+proto(1)+type(1) = 6 字节
        Bytes r = new Bytes(Arrays.copyOfRange(data, 6, data.length));
        int seq = r.u16();
        long tick = r.i64();
        int len = VarInt.read(r);
        if (len < 0 || len > AtmosProtocol.MAX_PAYLOAD || len > r.remaining())
            throw new IllegalArgumentException("bad body len " + len);
        byte[] body = r.bytes(len);
        return new Frame((byte) proto, ch, seq, tick, body);
    }

    private FrameCodec() {}
}
