package dev.atmos.client.net;

/** 客户端连接/降级状态。 */
public final class ConnectionState {
    public volatile boolean handshaked = false;
    public volatile int gridSize = 128;
    public volatile int syncPeriod = 20;
    public volatile int baseMode = 0;
    public volatile boolean forceMod = false;
    public volatile byte protocolVersion = 0;
    public volatile long serverTick = 0;
    public volatile long clientRecvNanos = 0;

    public boolean isFull() { return handshaked && protocolVersion == 1; }

    public String level() {
        if (!handshaked) return "L1（未握手，原版天气）";
        if (protocolVersion != 1) return "L1（协议不符）";
        return "L2（局地气象场）";
    }
}
