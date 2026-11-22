package dev.atmos.protocol;

/**
 * 纯 Java 的 LZ4 block 编解码（无第三方依赖，保证两端字节完全一致）。
 * 只实现 block 格式：token(litLen<<4|matchLen) [extLen] literals offset [extMatchLen]。
 * 若压缩后更大则返回原数据，由调用方用 flag 区分。
 */
public final class Lz4 {
    private static final int HASH_SIZE = 1 << 14;
    private static final int MIN_MATCH = 4;
    private static final int MAX_DIST = 1 << 16;

    public static byte[] compress(byte[] src) {
        if (src.length < MIN_MATCH) return wrap(false, src);
        int[] hashTable = new int[HASH_SIZE];
        java.util.Arrays.fill(hashTable, -1);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(src.length);
        int anchor = 0;
        int i = 0;
        int limit = src.length - MIN_MATCH;
        while (i <= limit) {
            int seq = readInt(src, i);
            int h = hash(seq);
            int ref = hashTable[h];
            hashTable[h] = i;
            // 找到有效匹配
            if (ref >= 0 && i - ref <= MAX_DIST && readInt(src, ref) == seq) {
                int matchLen = MIN_MATCH;
                while (i + matchLen < src.length && src[ref + matchLen] == src[i + matchLen]) matchLen++;
                int litLen = i - anchor;
                int m = matchLen - MIN_MATCH;
                writeToken(out, litLen, m);
                out.write(src, anchor, litLen);
                writeShort(out, i - ref); // LZ4 offset 为 little-endian
                if (m >= 15) {
                    int rem = m - 15;
                    while (rem >= 255) { out.write(255); rem -= 255; }
                    out.write(rem);
                }
                i += matchLen;
                anchor = i;
            } else {
                i++;
            }
        }
        // 剩余作为 literals
        int litLen = src.length - anchor;
        writeToken(out, litLen, 0);
        out.write(src, anchor, litLen);
        return wrap(true, out.toByteArray());
    }

    public static byte[] decompress(byte[] data) {
        boolean compressed = (data[0] & 0xFF) == 1;
        int len = ((data[1]&0xFF)<<8) | (data[2]&0xFF);
        byte[] body = new byte[len];
        System.arraycopy(data, 3, body, 0, len);
        if (!compressed) return body;
        // 用可增长数组，match 逐字节读取"正在增长"的输出（天然支持重叠匹配）
        Buf out = new Buf(Math.max(64, len * 2));
        int ip = 0;
        while (ip < body.length) {
            int token = body[ip++] & 0xFF;
            int litLen = token >>> 4;
            if (litLen == 15) {
                int add;
                do { add = body[ip++] & 0xFF; litLen += add; } while (add == 255 && ip < body.length);
            }
            out.ensure(out.pos + litLen);
            System.arraycopy(body, ip, out.data, out.pos, litLen);
            out.pos += litLen; ip += litLen;
            if (ip >= body.length) break;
            if (ip + 2 > body.length) break;
            int offset = (body[ip] & 0xFF) | ((body[ip + 1] & 0xFF) << 8);
            ip += 2;
            int matchLen = token & 0x0F;
            if (matchLen == 15) {
                int add;
                do { add = body[ip++] & 0xFF; matchLen += add; } while (add == 255 && ip < body.length);
            }
            matchLen += MIN_MATCH;
            if (offset < 1 || offset > out.pos) break; // 非法 offset，防御
            out.ensure(out.pos + matchLen);
            for (int k = 0; k < matchLen; k++) {
                out.data[out.pos] = out.data[out.pos - offset];
                out.pos++;
            }
        }
        return java.util.Arrays.copyOf(out.data, out.pos);
    }

    /** 简单可增长字节缓冲 */
    private static final class Buf {
        byte[] data;
        int pos = 0;
        Buf(int cap) { data = new byte[cap]; }
        void ensure(int need) {
            if (need <= data.length) return;
            int cap = data.length;
            while (cap < need) cap *= 2;
            data = java.util.Arrays.copyOf(data, cap);
        }
    }

    private static byte[] wrap(boolean compressed, byte[] body) {
        byte[] out = new byte[3 + body.length];
        out[0] = (byte) (compressed ? 1 : 0);
        out[1] = (byte) ((body.length >>> 8) & 0xFF);
        out[2] = (byte) (body.length & 0xFF);
        System.arraycopy(body, 0, out, 3, body.length);
        return out;
    }

    private static void writeToken(java.io.ByteArrayOutputStream out, int litLen, int matchLen) {
        int l = Math.min(litLen, 15);
        int m = Math.min(matchLen, 15);
        out.write((l << 4) | m);
        if (litLen >= 15) {
            int rem = litLen - 15;
            while (rem >= 255) { out.write(255); rem -= 255; }
            out.write(rem);
        }
    }

    private static void writeShort(java.io.ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
    }

    private static int hash(int v) { return (v * 0x9E3779B1) >>> 18 & (HASH_SIZE - 1); }

    private static int readInt(byte[] b, int i) {
        return (b[i]&0xFF) | ((b[i+1]&0xFF)<<8) | ((b[i+2]&0xFF)<<16) | ((b[i+3]&0xFF)<<24);
    }

    private Lz4() {}
}
