package dev.atmos.client.net;

import dev.atmos.client.AtmosClient;
import dev.atmos.protocol.Channel;
import dev.atmos.protocol.Frame;
import dev.atmos.protocol.FrameCodec;
import dev.atmos.protocol.body.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * 客户端 Custom Payload 网桥：复用 25565 的 Playing 阶段通道，不监听任何端口。
 * 每条 atmos:* 通道注册一个 Type + 裸 byte[] codec。
 */
public final class ClientBridge {

    /** 裸字节负载 */
    public static final class RawPayload implements CustomPacketPayload {
        public final CustomPacketPayload.Type<RawPayload> type;
        public final byte[] data;

        public RawPayload(CustomPacketPayload.Type<RawPayload> type, byte[] data) {
            this.type = type; this.data = data;
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return type; }
    }

    /** 每个 Type 一个 codec（从 FriendlyByteBuf 读写 byte[]） */
    private static final Map<Identifier, CustomPacketPayload.Type<RawPayload>> TYPES = new HashMap<>();

    /** 注册所有 S→C 通道的 Type + 接收器 */
    public static void registerReceiver(Identifier id) {
        CustomPacketPayload.Type<RawPayload> type = new CustomPacketPayload.Type<>(id);
        TYPES.put(id, type);

        // 注册客户端接收 payload 的编解码
        StreamCodec<FriendlyByteBuf, RawPayload> codec = StreamCodec.of(
                (buf, p) -> buf.writeByteArray(p.data),
                buf -> new RawPayload(type, buf.readByteArray(32767))
        );
        PayloadTypeRegistry.playS2C().register(type, codec);

        ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> {
            // 只在客户端线程处理
            context.client().execute(() -> handle(payload.data));
        });
    }

    /** 注册加入世界后发 hello（PLAY 阶段握手闸门） */
    public static void registerJoin() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            // 进入世界后发 atmos:hello（PLAY 阶段，配置阶段会超时断开）
            byte[] body = new Hello("0.1.0", (byte) 1, capabilityBits(), 2).encode();
            byte[] frame = FrameCodec.encode(new Frame((byte) 1, Channel.HELLO, 1, 0L, body));
            sender.sendPacket(new RawPayload(typeOf(AtmosClient.id("hello")), frame));
        });
    }

    private static int capabilityBits() {
        // 粒子=1 雷声=2 HUD=4 雷达=8 着色器=16
        return 1 | 2 | 4 | 8 | 16;
    }

    /** C→S hello 也属于 playC2S 注册（否则发送被拒） */
    public static void registerClientSending() {
        CustomPacketPayload.Type<RawPayload> hello = typeOf(AtmosClient.id("hello"));
        StreamCodec<FriendlyByteBuf, RawPayload> codec = StreamCodec.of(
                (buf, p) -> buf.writeByteArray(p.data),
                buf -> new RawPayload(hello, buf.readByteArray(32767))
        );
        PayloadTypeRegistry.playC2S().register(hello, codec);
        // ack / probe 同理
        for (String n : new String[]{"ack", "probe"}) {
            CustomPacketPayload.Type<RawPayload> t = typeOf(AtmosClient.id(n));
            PayloadTypeRegistry.playC2S().register(t, StreamCodec.of(
                    (buf, p) -> buf.writeByteArray(p.data),
                    buf -> new RawPayload(t, buf.readByteArray(32767))));
        }
    }

    private static CustomPacketPayload.Type<RawPayload> typeOf(Identifier id) {
        return TYPES.computeIfAbsent(id, k -> new CustomPacketPayload.Type<>(k));
    }

    /** 解码收到的帧并按通道分发（只更新渲染缓存，不判定） */
    private static void handle(byte[] message) {
        Frame f;
        try {
            f = FrameCodec.decode(message);
        } catch (Exception e) {
            AtmosClient.STATE.handshaked = false; // 异常降级 L1
            return;
        }
        // 分片头：idx(u16) total(u16) + chunk
        byte[] payload = f.body;
        if (payload.length < 4) return;
        int idx = ((payload[0]&0xFF)<<8) | (payload[1]&0xFF);
        int total = ((payload[2]&0xFF)<<8) | (payload[3]&0xFF);
        byte[] body = new byte[payload.length - 4];
        System.arraycopy(payload, 4, body, 0, body.length);

        try {
            switch (f.type) {
                case WELCOME -> {
                    Welcome w = Welcome.decode(body);
                    AtmosClient.STATE.handshaked = w.acceptedProto == 1;
                    AtmosClient.STATE.protocolVersion = w.acceptedProto;
                    AtmosClient.STATE.gridSize = w.gridSize;
                    AtmosClient.STATE.syncPeriod = w.syncPeriodTicks;
                    AtmosClient.STATE.baseMode = w.baseMode;
                    AtmosClient.STATE.forceMod = w.forceMod == 1;
                    AtmosClient.LOG.info("Atmos 握手：{}", AtmosClient.STATE.level());
                }
                case CLOCK -> {
                    Clock c = Clock.decode(body);
                    AtmosClient.STATE.serverTick = c.gameTick;
                    AtmosClient.STATE.clientRecvNanos = System.nanoTime();
                }
                case FIELD -> {
                    Field field = Field.decode(body);
                    // 客户端重组：这里简化为单片渲染缓存更新
                    AtmosClient.FIELD.update(field.originX, field.originZ, field.width, field.height, field.cells);
                }
                case FORECAST -> {
                    Forecast fc = Forecast.decode(body);
                    dev.atmos.client.hud.ForecastCache.set(fc);
                }
                case WARN -> {
                    Warn w = Warn.decode(body);
                    dev.atmos.client.hud.ForecastCache.warn(w.warnLevel, w.messageKey);
                }
                case CELLS, EFFECT -> { /* 渲染层读取，占位 */ }
                default -> { }
            }
        } catch (Exception e) {
            AtmosClient.LOG.warn("解析 atmos:{} 失败：{}", f.type, e.getMessage());
        }
    }

    private ClientBridge() {}
}
