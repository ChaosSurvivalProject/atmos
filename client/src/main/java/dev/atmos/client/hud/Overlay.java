package dev.atmos.client.hud;

import dev.atmos.client.AtmosClient;
import dev.atmos.protocol.Fixed;
import dev.atmos.protocol.body.Cell;
import dev.atmos.protocol.body.Forecast;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** HUD：预警横幅 + 预报摘要 + 温度风力条。只显示服务端下发的数据，不参与判定。 */
public final class Overlay implements HudRenderCallback {

    public static void register() {
        HudRenderCallback.EVENT.register(new Overlay());
    }

    @Override
    public void onHudRender(GuiGraphics g, net.minecraft.client.DeltaTracker tickCounter) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        int y = 10;
        // 预警横幅
        if (ForecastCache.warnActive()) {
            int lvl = ForecastCache.warnLevel();
            int color = switch (lvl) {
                case 3 -> 0xFFFF3333; case 2 -> 0xFFFFAA00; case 1 -> 0xFF3388FF; default -> 0xFFAAAAAA;
            };
            String label = switch (lvl) {
                case 3 -> "[红] 极端天气！寻找室内庇护"; case 2 -> "[黄] 强天气逼近";
                case 1 -> "[蓝] 注意天气变化"; default -> "气象预警";
            };
            draw(g, Component.literal(label), mc, 10, y, color);
            y += 14;
        }

        // 连接状态（L1/L2）
        draw(g, Component.literal("Atmos " + AtmosClient.STATE.level()), mc, 10, y, 0xFF55FF55);
        y += 14;

        // 当前局地温度/风（仅渲染缓存）
        Cell c = AtmosClient.FIELD.current() == null ? null
                : AtmosClient.FIELD.current().cells.length > 0 ? AtmosClient.FIELD.current().cells[0] : null;
        if (c != null) {
            float temp = Fixed.unpack(c.temp);
            float wind = (float) Math.hypot(Fixed.unpack(c.windU), Fixed.unpack(c.windV));
            draw(g, Component.literal(String.format("局地 %.1f°C  风 %.1f m/s", temp, wind)), mc, 10, y, 0xFFAAAAAA);
            y += 14;
        }

        // 预报首日摘要
        Forecast fc = ForecastCache.get();
        if (fc != null && fc.segments.length > 0) {
            Forecast.Seg s0 = fc.segments[0];
            draw(g, Component.literal(String.format("预报今日 %.1f~%.1f°C 置信%d%%",
                    Fixed.unpack(s0.tempLo), Fixed.unpack(s0.tempHi), s0.confidence)), mc, 10, y, 0xFFFFDD55);
        }
    }

    private static void draw(GuiGraphics g, Component text, Minecraft mc, int x, int y, int color) {
        // 1.21 的 drawString 需要 Font / TextRenderer；用 GuiGraphics 便捷重载
        g.drawString(mc.font, text, x, y, color, false);
    }
}
