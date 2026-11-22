package dev.atmos.server.forecast;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** 预报 vs 实况命中率统计，持久化到 forecasts/score.csv。 */
public final class Scoreboard {
    private final Path file;
    private int total, hits;

    public Scoreboard(Path dataFolder) {
        this.file = dataFolder.resolve("score.csv");
        load();
    }

    /** 记录一次预报与实际天气类型是否命中 */
    public void record(int predictedKind, int actualKind) {
        total++;
        if (predictedKind == actualKind) hits++;
        append(predictedKind, actualKind);
    }

    public double hitRate() { return total == 0 ? 0 : (double) hits / total; }
    public int total() { return total; }
    public int hits() { return hits; }

    private void load() {
        if (!Files.exists(file)) return;
        try (BufferedReader r = Files.newBufferedReader(file)) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split(",");
                if (p.length >= 2) {
                    total++;
                    if (p[0].equals(p[1])) hits++;
                }
            }
        } catch (IOException ignored) {}
    }

    private void append(int pred, int actual) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, pred + "," + actual + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {}
    }
}
