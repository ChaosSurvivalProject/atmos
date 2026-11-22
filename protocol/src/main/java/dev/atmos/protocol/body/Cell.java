package dev.atmos.protocol.body;

import dev.atmos.protocol.Fixed;
import dev.atmos.protocol.util.Bytes;

/** 单个网格点的七要素量化值。单位格按 18 字节传输。 */
public final class Cell {
    public final short temp;      // ×100 °C
    public final short humidity;  // ×100 0..1
    public final short pressure;  // ×100 hPa
    public final short trend;     // ×100 hPa/3h
    public final short dew;       // ×100 °C
    public final short windU;     // ×100 m/s
    public final short windV;     // ×100 m/s
    public final short cloud;     // ×100 0..1
    public final short vis;       // 米，整数

    public Cell(short temp, short humidity, short pressure, short trend, short dew,
                short windU, short windV, short cloud, short vis) {
        this.temp = temp; this.humidity = humidity; this.pressure = pressure;
        this.trend = trend; this.dew = dew; this.windU = windU; this.windV = windV;
        this.cloud = cloud; this.vis = vis;
    }

    public static Cell of(float temp, float hum, float pres, float trend, float dew,
                          float windU, float windV, float cloud, int vis) {
        return new Cell(Fixed.pack(temp), Fixed.pack(hum), Fixed.pack(pres), Fixed.pack(trend),
                Fixed.pack(dew), Fixed.pack(windU), Fixed.pack(windV), Fixed.pack(cloud), (short) vis);
    }

    public void encode(Bytes.Writer w) {
        w.i16(temp).i16(humidity).i16(pressure).i16(trend).i16(dew)
         .i16(windU).i16(windV).i16(cloud).i16(vis);
    }

    public static Cell decode(Bytes r) {
        return new Cell(r.i16(), r.i16(), r.i16(), r.i16(), r.i16(),
                r.i16(), r.i16(), r.i16(), r.i16());
    }
}
