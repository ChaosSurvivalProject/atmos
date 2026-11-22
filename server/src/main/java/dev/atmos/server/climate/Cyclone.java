package dev.atmos.server.climate;

/** 低压吸引子：从气候场识别低压区生成，之后自行演化移动。纯计算。 */
public final class Cyclone {
    public double x, z;
    public double radius;
    public double intensity;
    public double vx, vz;
    public boolean active;
    private final Noise drift;

    public Cyclone(double x, double z, double radius, double intensity, long seed) {
        this.x = x; this.z = z; this.radius = radius;
        this.intensity = intensity;
        this.vx = 0.15; this.vz = 0.08;
        this.active = true;
        this.drift = new Noise(seed ^ 0xABCDEF);
    }

    /** 每个演化步推进位置与生命周期 */
    public void step(long tick, int gridSize) {
        if (!active) return;
        // 引导气流漂移 + 噪声扰动
        vx += drift.valueNoise(tick * 0.0003, 0) * 0.005;
        vz += drift.valueNoise(0, tick * 0.0003) * 0.005;
        vx = Math.max(-0.6, Math.min(0.6, vx));
        vz = Math.max(-0.6, Math.min(0.6, vz));
        x += vx; z += vz;
        if (x < 0) x += gridSize; if (x >= gridSize) x -= gridSize;
        if (z < 0) z += gridSize; if (z >= gridSize) z -= gridSize;
        // 生命衰减：强度缓慢减弱
        intensity *= 0.9995;
        radius *= 0.9998;
        if (intensity < 0.05 || radius < 2) active = false;
    }

    public int phase() {
        if (!active) return 3;
        if (intensity < 0.3) return 0;
        if (intensity < 0.7) return 1;
        return 2;
    }
}
