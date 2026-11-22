package dev.atmos.server.netbridge;

/** 全局出站字节预算 + MSPT 三级自适应降级。 */
public final class Budget {
    private final int byteBudget;
    private final int msptSoft, msptHard;
    private int bytesThisSec;
    private long sec;
    private int level = 0;  // 0 全量 1 降分辨率 2 降频率 3 只保预警

    public Budget(int byteBudget, int msptSoft, int msptHard) {
        this.byteBudget = byteBudget; this.msptSoft = msptSoft; this.msptHard = msptHard;
    }

    /** 每 tick 调用，返回当前降级级别 */
    public int update(double mspt) {
        long now = System.currentTimeMillis() / 1000;
        if (now != sec) { sec = now; bytesThisSec = 0; }
        if (mspt > msptHard) level = 3;
        else if (mspt > (msptSoft + msptHard) / 2.0) level = 2;
        else if (mspt > msptSoft) level = 1;
        else level = Math.max(0, level - 1); // 恢复时逐级回升
        return level;
    }

    /** 是否允许发送 n 字节（预算内） */
    public boolean allow(int n) {
        if (bytesThisSec + n > byteBudget) return false;
        bytesThisSec += n;
        return true;
    }

    /** 当前有效网格边长（降级降分辨率） */
    public int effectiveGrid(int base) {
        return switch (level) {
            case 1 -> Math.max(64, base / 2);
            case 2 -> Math.max(64, base / 2);
            default -> base;
        };
    }

    /** 当前同步周期（降级降频率） */
    public int effectivePeriod(int base) {
        return switch (level) {
            case 2 -> base * 2;
            case 3 -> base * 4;
            default -> base;
        };
    }

    public boolean onlyWarnings() { return level >= 3; }
    public int level() { return level; }
}
