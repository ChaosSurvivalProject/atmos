package dev.atmos.protocol;

import java.util.ArrayList;
import java.util.List;

/** 分片与重组：把大 body 切成 ≤ 上限的片，按 (seq,total) 聚合并发 ack 补片。 */
public final class Fragmenter {

    /** 把一个 body 切成若干片，返回 (index,total,chunk)。 */
    public static List<Part> split(byte[] body) {
        int max = AtmosProtocol.MAX_PAYLOAD;
        int total = Math.max(1, (body.length + max - 1) / max);
        List<Part> parts = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            int from = i * max;
            int to = Math.min(body.length, from + max);
            byte[] chunk = new byte[to - from];
            System.arraycopy(body, from, chunk, 0, to - from);
            parts.add(new Part(i, total, chunk));
        }
        return parts;
    }

    public static final class Part {
        public final int index, total;
        public final byte[] chunk;
        public Part(int index, int total, byte[] chunk) {
            this.index = index; this.total = total; this.chunk = chunk;
        }
    }

    /** 接收侧聚合缓冲。 */
    public static final class Reassembly {
        private final byte[][] chunks;
        private final boolean[] got;
        private int count;

        public Reassembly(int total) {
            if (total < 1 || total > 1024) throw new IllegalArgumentException("bad total " + total);
            chunks = new byte[total][];
            got = new boolean[total];
        }

        public void add(int index, byte[] chunk) {
            if (index < 0 || index >= chunks.length) return;
            if (!got[index]) { got[index] = true; chunks[index] = chunk; count++; }
        }

        public boolean complete() { return count == chunks.length; }

        /** 缺失片位图（每片 1 bit，MSB 先）。 */
        public byte[] missingBitmap() {
            int bytes = (chunks.length + 7) / 8;
            byte[] bm = new byte[bytes];
            for (int i = 0; i < chunks.length; i++) {
                if (!got[i]) bm[i / 8] |= (byte) (1 << (7 - (i % 8)));
            }
            return bm;
        }

        public byte[] join() {
            int len = 0;
            for (byte[] c : chunks) if (c != null) len += c.length;
            byte[] out = new byte[len];
            int pos = 0;
            for (byte[] c : chunks) if (c != null) { System.arraycopy(c, 0, out, pos, c.length); pos += c.length; }
            return out;
        }
    }

    private Fragmenter() {}
}
