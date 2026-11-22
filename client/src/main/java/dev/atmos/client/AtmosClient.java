package dev.atmos.client;

import dev.atmos.client.model.LocalField;
import dev.atmos.client.net.ClientBridge;
import dev.atmos.client.net.ConnectionState;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fabric 纯客户端入口：只渲染/插值/HUD/上报，不判定天气。 */
public final class AtmosClient implements ClientModInitializer {
    public static final String MOD_ID = "atmos";
    public static final Logger LOG = LoggerFactory.getLogger("Atmos");

    /** 客户端本地场缓存（只用于渲染插值） */
    public static final LocalField FIELD = new LocalField();
    /** 连接状态 */
    public static final ConnectionState STATE = new ConnectionState();

    @Override
    public void onInitializeClient() {
        LOG.info("Atmos 客户端初始化（纯客户端，无判定逻辑）");

        // PLAY 阶段注册接收通道。Custom Payload 裸 byte[] 通过注册为 byte-array 类型接收。
        // 每条通道都注册为 PlayPayloadHandler，直接从 PacketByteBuf 读字节数组交给协议解码。
        // C→S 通道（hello/ack/probe）注册发送 codec
        ClientBridge.registerClientSending();

        // S→C 通道注册接收
        for (String name : new String[]{"welcome", "clock", "field", "cells", "forecast", "warn", "effect"}) {
            ClientBridge.registerReceiver(Identifier.fromNamespaceAndPath(MOD_ID, name));
        }

        // 握手：进入世界后发 hello（PLAY 阶段），以 hello 为发送闸门
        ClientBridge.registerJoin();

        // HUD 覆盖层
        dev.atmos.client.hud.Overlay.register();
    }

    /** 便捷：构造 Identifier */
    public static Identifier id(String name) { return Identifier.fromNamespaceAndPath(MOD_ID, name); }
}
