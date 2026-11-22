package dev.atmos.protocol.body;

import dev.atmos.protocol.util.Bytes;

/** C→S 0x01：mod 版本、协议版本、能力位、渲染档位 */
public final class Hello {
    public final String modVersion;
    public final byte protocolVersion;
    public final int capabilityBits;
    public final int renderTier;

    public Hello(String modVersion, byte protocolVersion, int capabilityBits, int renderTier) {
        this.modVersion = modVersion;
        this.protocolVersion = protocolVersion;
        this.capabilityBits = capabilityBits;
        this.renderTier = renderTier;
    }

    public byte[] encode() {
        return new Bytes.Writer()
                .str(modVersion)
                .u8(protocolVersion & 0xFF)
                .i32(capabilityBits)
                .u8(renderTier)
                .toByteArray();
    }

    public static Hello decode(byte[] b) {
        Bytes r = new Bytes(b);
        String modVersion = r.str();
        byte pv = (byte) r.u8();
        int caps = r.i32();
        int tier = r.u8();
        return new Hello(modVersion, pv, caps, tier);
    }
}
