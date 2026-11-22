package dev.atmos.server.persist;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/** 长期量持久化：游戏内年循环/干旱天数/积雪深度/土壤湿度，重启续算。仅本地文件，无端口。 */
public final class SnapshotStore {
    private final Path file;
    private final Properties data = new Properties();

    public SnapshotStore(Path dataFolder) {
        this.file = dataFolder.resolve("snapshot.properties");
        load();
    }

    public long year() { return Long.parseLong(data.getProperty("year", "1")); }
    public int droughtDays() { return Integer.parseInt(data.getProperty("drought-days", "0")); }
    public float snowDepth() { return Float.parseFloat(data.getProperty("snow-depth", "0")); }
    public float soilMoisture() { return Float.parseFloat(data.getProperty("soil-moisture", "0.5")); }

    public void setYear(long v) { data.setProperty("year", String.valueOf(v)); }
    public void setDroughtDays(int v) { data.setProperty("drought-days", String.valueOf(v)); }
    public void setSnowDepth(float v) { data.setProperty("snow-depth", String.valueOf(v)); }
    public void setSoilMoisture(float v) { data.setProperty("soil-moisture", String.valueOf(v)); }

    public void save() {
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream o = Files.newOutputStream(file)) {
                data.store(o, "Atmos climate snapshot");
            }
        } catch (IOException ignored) {}
    }

    private void load() {
        if (!Files.exists(file)) return;
        try (InputStream i = Files.newInputStream(file)) { data.load(i); }
        catch (IOException ignored) {}
    }
}
