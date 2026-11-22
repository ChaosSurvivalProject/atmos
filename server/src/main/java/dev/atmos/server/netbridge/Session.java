package dev.atmos.server.netbridge;

import java.util.UUID;

/** 每玩家网络会话状态：握手、seq、能力、限流计数。 */
public final class Session {
    public final UUID uuid;
    public boolean handshaked;
    public byte protocolVersion;
    public int capabilityBits;
    public int renderTier;
    public int seq;
    public int window;          // 视距格
    public long lastFieldTick = -1;
    private long rateSecond;
    private int rateCount;

    public Session(UUID uuid) { this.uuid = uuid; }

    /** 每玩家每秒限流，超限返回 false */
    public boolean allow(int maxPerSec, long nowSec) {
        if (nowSec != rateSecond) { rateSecond = nowSec; rateCount = 0; }
        if (rateCount++ >= maxPerSec) return false;
        return true;
    }

    public int nextSeq() { return seq++ & 0xFFFF; }
}
