package dev.atmos.protocol;

import dev.atmos.protocol.body.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 字节级一致性：编码→解码→再编码，golden vector 固定 hex 锁死两端行为。 */
class ProtocolTest {

    @Test
    void frameRoundTripAllChannels() {
        long tick = 123456789L;
        int seq = 4242;
        Frame[] frames = {
            new Frame((byte)1, Channel.HELLO, seq, tick, new Hello("0.1.0", (byte)1, 0xF, 2).encode()),
            new Frame((byte)1, Channel.WELCOME, seq, tick, new Welcome(256, 20, 0, 0, (byte)1).encode()),
            new Frame((byte)1, Channel.CLOCK, seq, tick, new Clock(tick, 987654321L).encode()),
            new Frame((byte)1, Channel.WARN, seq, tick, new Warn(2, 7, 600L, "warn.storm").encode()),
            new Frame((byte)1, Channel.EFFECT, seq, tick, new Effect(1, 1.5f, 64f, -2.5f, 99L).encode()),
            new Frame((byte)1, Channel.ACK, seq, tick, new Ack(seq, new byte[]{(byte)0b10100000}).encode()),
            new Frame((byte)1, Channel.PROBE, seq, tick, new Probe(10, -20, 12.5f, 0.3f).encode()),
        };
        for (Frame f : frames) {
            byte[] enc = FrameCodec.encode(f);
            Frame dec = FrameCodec.decode(enc);
            assertEquals(f.type, dec.type);
            assertEquals(f.seq, dec.seq);
            assertEquals(f.tick, dec.tick);
            assertArrayEquals(enc, FrameCodec.encode(dec), "re-encode must match: " + f.type);
        }
    }

    @Test
    void goldenVectorMagicHeader() {
        Frame f = new Frame((byte)1, Channel.CLOCK, 0x0102, 0x0102030405060708L, new byte[]{(byte)0x2A});
        byte[] e = FrameCodec.encode(f);
        assertEquals('A', e[0]); assertEquals('T', e[1]);
        assertEquals('M', e[2]); assertEquals('S', e[3]);
        assertEquals(1, e[4]);
        assertEquals(0x03, e[5]); // CLOCK type
        assertEquals(0x01, e[6]); assertEquals(0x02, e[7]); // seq BE
        for (int i = 8; i < 16; i++) assertEquals((0x01_02_03_04_05_06_07_08L >>> ((15 - i) * 8)) & 0xFF, e[i] & 0xFF);
        assertEquals(1, e[16]);   // body len VarInt
        assertEquals(0x2A, e[17]);  // body
    }

    @Test
    void fixedPoint() {
        assertEquals((short) 1250, Fixed.pack(12.5f));
        assertEquals(12.5f, Fixed.unpack((short) 1250));
        assertEquals((short) -500, Fixed.pack(-5f));
    }

    @Test
    void lz4RoundTrip() {
        byte[] src = new byte[512];
        for (int i = 0; i < src.length; i++) src[i] = (byte) (i % 7);
        byte[] c = Lz4.compress(src);
        assertArrayEquals(src, Lz4.decompress(c));
        byte[] small = {1, 2, 3};
        assertArrayEquals(small, Lz4.decompress(Lz4.compress(small)));
    }

    @Test
    void fieldRoundTrip() {
        Cell[] cells = new Cell[4];
        for (int i = 0; i < 4; i++) cells[i] = Cell.of(20f + i, 0.5f, 1013f, -1f, 10f, 3f, -2f, 0.4f, 1000 + i);
        Field field = new Field(0, 1, -3, 5, 2, 2, cells);
        byte[] enc = field.encode();
        Field dec = Field.decode(enc);
        assertEquals(4, dec.cellCount);
        assertEquals(2, dec.width);
        assertEquals(Fixed.pack(21f), dec.cells[1].temp);
    }

    @Test
    void forecastRoundTrip() {
        Forecast.Seg[] segs = new Forecast.Seg[28];
        for (int i = 0; i < segs.length; i++) segs[i] = new Forecast.Seg(i % 10, 10f, 25f, 60, 4, 8000, 90 - i, i % 4);
        Forecast fc = new Forecast(7, 4, segs);
        Forecast dec = Forecast.decode(fc.encode());
        assertEquals(7, dec.days);
        assertEquals(28, dec.segments.length);
        assertEquals(3, dec.segments[3].warnLevel);
    }

    @Test
    void fragmentReassembly() {
        byte[] big = new byte[AtmosProtocol.MAX_PAYLOAD * 2 + 123];
        for (int i = 0; i < big.length; i++) big[i] = (byte) i;
        List<Fragmenter.Part> parts = Fragmenter.split(big);
        assertEquals(3, parts.size());
        Fragmenter.Reassembly ra = new Fragmenter.Reassembly(3);
        ra.add(2, parts.get(2).chunk);
        ra.add(0, parts.get(0).chunk);
        assertFalse(ra.complete());
        assertEquals(0b01000000, ra.missingBitmap()[0]); // bit 1 missing (MSB first)
        ra.add(1, parts.get(1).chunk);
        assertTrue(ra.complete());
        assertArrayEquals(big, ra.join());
    }

    @Test
    void badFrameRejected() {
        byte[] bad = FrameCodec.encode(new Frame((byte)1, Channel.CLOCK, 1, 1L, new byte[0]));
        bad[0] = 'X';
        assertThrows(IllegalArgumentException.class, () -> FrameCodec.decode(bad));
        byte[] badVer = FrameCodec.encode(new Frame((byte)1, Channel.CLOCK, 1, 1L, new byte[0]));
        badVer[4] = 99;
        assertThrows(IllegalArgumentException.class, () -> FrameCodec.decode(badVer));
    }
}
