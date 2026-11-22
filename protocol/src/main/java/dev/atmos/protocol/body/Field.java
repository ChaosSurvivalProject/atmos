package dev.atmos.protocol.body;

import dev.atmos.protocol.Lz4;
import dev.atmos.protocol.util.Bytes;
import dev.atmos.protocol.util.VarInt;

import java.io.ByteArrayOutputStream;

/**
 * S→C 0x04：场切片（delta）。带分片头 index/total/origin/size，cells 为压缩后的格数据。
 * flag = 0 表示 cells 未压缩，1 表示 LZ4 压缩。
 */
public final class Field {
    public final int fragIndex;
    public final int fragTotal;
    public final int originX;
    public final int originZ;
    public final int width;
    public final int height;
    public final int cellCount;
    public final Cell[] cells;

    public Field(int fragIndex, int fragTotal, int originX, int originZ,
                 int width, int height, Cell[] cells) {
        this.fragIndex = fragIndex; this.fragTotal = fragTotal;
        this.originX = originX; this.originZ = originZ;
        this.width = width; this.height = height;
        this.cells = cells; this.cellCount = cells == null ? 0 : cells.length;
    }

    public byte[] encode() {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        Bytes.Writer w = new Bytes.Writer();
        for (Cell c : cells) c.encode(w);
        byte[] cellBytes = w.toByteArray();
        byte[] packed = Lz4.compress(cellBytes);
        byte[] out = new Bytes.Writer()
                .u16(fragIndex).u16(fragTotal)
                .i16(originX).i16(originZ)
                .u8(width).u8(height)
                .i32(cellCount)
                .u8(packed[0] & 0xFF)   // compressed flag
                .toByteArray();
        // len + packed
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        all.writeBytes(out);
        VarInt.write(all, packed.length - 3);
        all.write(packed, 3, packed.length - 3);
        return all.toByteArray();
    }

    public static Field decode(byte[] b) {
        Bytes r = new Bytes(b);
        int fragIndex = r.u16(), fragTotal = r.u16();
        int originX = r.i16(), originZ = r.i16();
        int width = r.u8(), height = r.u8();
        int cellCount = r.i32();
        int compressed = r.u8();
        int len = VarInt.read(r);
        byte[] packedBody = r.bytes(len);
        byte[] packed = new byte[3 + packedBody.length];
        packed[0] = (byte) compressed;
        packed[1] = (byte) (len >>> 8);
        packed[2] = (byte) len;
        System.arraycopy(packedBody, 0, packed, 3, len);
        byte[] cellBytes = Lz4.decompress(packed);
        Bytes cr = new Bytes(cellBytes);
        Cell[] cells = new Cell[cellCount];
        for (int i = 0; i < cellCount; i++) cells[i] = Cell.decode(cr);
        return new Field(fragIndex, fragTotal, originX, originZ, width, height, cells);
    }
}
