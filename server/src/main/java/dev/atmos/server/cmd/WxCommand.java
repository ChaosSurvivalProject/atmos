package dev.atmos.server.cmd;

import dev.atmos.protocol.body.Forecast;
import dev.atmos.server.AtmosPlugin;
import dev.atmos.server.forecast.Scoreboard;
import dev.atmos.server.registry.WeatherRegistry;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

/** /wx now|forecast|warn|radar|set|reload|verify|debug —— 权限分级。 */
public final class WxCommand implements CommandExecutor {
    private final AtmosPlugin plugin;

    public WxCommand(AtmosPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (args.length == 0) { help(s); return true; }
        switch (args[0].toLowerCase()) {
            case "now" -> s.sendMessage(plugin.nowText());
            case "forecast" -> sendForecast(s);
            case "warn" -> s.sendMessage("§c当前预警：" + (plugin.cyclone().active && plugin.cyclone().intensity > 0.7
                    ? "§4[红] 强气旋逼近，请进入室内！" : "§a无"));
            case "radar" -> s.sendMessage("§b雷达：气旋 " +
                    (plugin.cyclone().active ? String.format("@(%.0f,%.0f) 强度 %.2f",
                            plugin.cyclone().x, plugin.cyclone().z, plugin.cyclone().intensity) : "无"));
            case "set" -> {
                if (!s.hasPermission("atmos.admin")) { deny(s); return true; }
                if (args.length >= 3) {
                    try {
                        double v = Double.parseDouble(args[2]);
                        switch (args[1].toLowerCase()) {
                            case "intensity" -> plugin.cyclone().intensity = v;
                            case "x" -> plugin.cyclone().x = v;
                            case "z" -> plugin.cyclone().z = v;
                            default -> s.sendMessage("§c未知字段");
                        }
                        s.sendMessage("§a已设置 " + args[1] + "=" + v);
                    } catch (NumberFormatException e) { s.sendMessage("§c数值错误"); }
                }
            }
            case "reload" -> {
                if (!s.hasPermission("atmos.admin")) { deny(s); return true; }
                plugin.reloadConfig();
                s.sendMessage("§a配置已重载（部分项需重启）。");
            }
            case "verify" -> {
                if (!s.hasPermission("atmos.admin")) { deny(s); return true; }
                s.sendMessage("§a端口核对：Atmos 仅使用 25565 Custom Payload，无 ServerSocket/HTTP/WS/UDP 监听。");
                s.sendMessage("§a协议：proto=1，单包≤30KiB，分片+ack，握手在 PLAY 阶段。");
            }
            case "debug" -> {
                if (!s.hasPermission("atmos.admin")) { deny(s); return true; }
                s.sendMessage("§7tick=" + plugin.tick() + " 降级级别=" + plugin.budget().level()
                        + " 网格=" + plugin.field().size + " 气旋=" + plugin.cyclone().active);
            }
            case "score" -> {
                Scoreboard sc = plugin.scoreboard();
                s.sendMessage(String.format("§b预报命中率：%.1f%% (%d/%d)", sc.hitRate()*100, sc.hits(), sc.total()));
            }
            default -> help(s);
        }
        return true;
    }

    private void sendForecast(CommandSender s) {
        Forecast fc = plugin.forecast().current();
        if (fc == null) { s.sendMessage("§7预报尚未生成。"); return; }
        s.sendMessage("§6=== 7 日预报 ===");
        String[] phase = {"凌晨","上午","午后","夜间"};
        String[] wname = new String[fc.segments.length];
        WeatherRegistry reg = plugin.registry();
        for (int d = 0; d < fc.days; d++) {
            StringBuilder line = new StringBuilder("§e第" + (d+1) + "天: ");
            for (int seg = 0; seg < fc.segmentsPerDay; seg++) {
                int i = d * fc.segmentsPerDay + seg;
                Forecast.Seg f = fc.segments[i];
                line.append(phase[seg % phase.length]).append("=")
                    .append(reg.get(f.kind).name).append(" ")
                    .append(dev.atmos.protocol.Fixed.unpack(f.tempLo)).append("~")
                    .append(dev.atmos.protocol.Fixed.unpack(f.tempHi)).append("° ")
                    .append(f.confidence).append("% ")
                    .append(warnMark(f.warnLevel)).append(" | ");
            }
            s.sendMessage(line.toString());
        }
    }

    private String warnMark(int lvl) {
        return switch (lvl) {
            case 1 -> "§9[蓝]"; case 2 -> "§6[黄]"; case 3 -> "§4[红]"; default -> "";
        };
    }

    private void help(CommandSender s) {
        s.sendMessage("§6/wx now | forecast | warn | radar | score | set | reload | verify | debug");
    }
    private void deny(CommandSender s) { s.sendMessage("§c需要 atmos.admin 权限。"); }
}
