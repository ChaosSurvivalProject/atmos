package dev.atmos.protocol;

/** 通道与消息类型枚举。type 码两端必须一致。 */
public enum Channel {
    HELLO    (0x01, "hello",     Dir.C2S),
    WELCOME  (0x02, "welcome",   Dir.S2C),
    CLOCK    (0x03, "clock",     Dir.S2C),
    FIELD    (0x04, "field",     Dir.S2C),
    CELLS    (0x05, "cells",     Dir.S2C),
    FORECAST (0x06, "forecast",  Dir.S2C),
    WARN     (0x07, "warn",      Dir.S2C),
    EFFECT   (0x08, "effect",    Dir.S2C),
    ACK      (0x09, "ack",       Dir.C2S),
    PROBE    (0x0A, "probe",     Dir.C2S);

    public enum Dir { C2S, S2C }

    public final int code;
    public final String name;
    public final Dir dir;

    Channel(int code, String name, Dir dir) {
        this.code = code;
        this.name = name;
        this.dir = dir;
    }

    public String fullName() {
        return AtmosProtocol.channel(name);
    }

    public static Channel byCode(int code) {
        for (Channel c : values()) if (c.code == (code & 0xFF)) return c;
        return null;
    }

    public static Channel byName(String name) {
        for (Channel c : values()) if (c.name.equals(name)) return c;
        return null;
    }
}
