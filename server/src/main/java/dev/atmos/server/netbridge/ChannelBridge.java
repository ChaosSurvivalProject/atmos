package dev.atmos.server.netbridge;

import dev.atmos.protocol.*;
import dev.atmos.protocol.body.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端 Custom Payload 网桥：只复用 25565 的 Plugin Message 通道，不监听任何端口。
 * 以 hello 为发送闸门；异常只降级不踢人。
 */
public final class ChannelBridge implements PluginMessageListener {
    private final Plugin plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final List<HandshakeListener> listeners = new ArrayList<>();
    private int maxPerSec = 20;

    public interface HandshakeListener {
        void onHello(Player player, Hello hello);
        void onAck(Player player, Ack ack);
        void onProbe(Player player, Probe probe);
    }

    public ChannelBridge(Plugin plugin) {
        this.plugin = plugin;
        for (Channel c : Channel.values()) {
            plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, c.fullName(), this);
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, c.fullName());
        }
    }

    public void addListener(HandshakeListener l) { listeners.add(l); }
    public Session session(Player p) { return sessions.computeIfAbsent(p.getUniqueId(), Session::new); }
    public Session sessionOrNull(Player p) { return sessions.get(p.getUniqueId()); }
    public void remove(Player p) { sessions.remove(p.getUniqueId()); }
    public void setMaxPerSec(int n) { this.maxPerSec = n; }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        Channel ch = Channel.byName(channel.substring(channel.indexOf(':') + 1));
        if (ch == null || ch.dir != Channel.Dir.C2S) return;
        Session s = session(player);
        long nowSec = System.currentTimeMillis() / 1000;
        if (!s.allow(maxPerSec, nowSec)) return;         // 限流，静默丢弃
        try {
            Frame f = FrameCodec.decode(message);
            switch (f.type) {
                case HELLO -> {
                    Hello h = Hello.decode(f.body);
                    if (h.protocolVersion != AtmosProtocol.PROTO_VERSION) {
                        sendWelcome(player, false);        // 版本不符 → L1
                        return;
                    }
                    s.handshaked = true;
                    s.protocolVersion = h.protocolVersion;
                    s.capabilityBits = h.capabilityBits;
                    s.renderTier = h.renderTier;
                    sendWelcome(player, true);
                    for (HandshakeListener l : listeners) l.onHello(player, h);
                }
                case ACK -> {
                    if (!s.handshaked) return;
                    for (HandshakeListener l : listeners) l.onAck(player, Ack.decode(f.body));
                }
                case PROBE -> {
                    if (!s.handshaked) return;
                    for (HandshakeListener l : listeners) l.onProbe(player, Probe.decode(f.body));
                }
                default -> { /* 忽略其它 C→S 类型 */ }
            }
        } catch (Exception e) {
            // 魔数/长度/版本异常：只降级，不踢人
            s.handshaked = false;
        }
    }

    public void sendWelcome(Player player, boolean accepted) {
        Session s = session(player);
        send(player, Channel.WELCOME, new Welcome(128, 20, 0, accepted ? 0 : 1,
                accepted ? AtmosProtocol.PROTO_VERSION : 0).encode());
    }

    /** 发送一帧（自动分片）；未握手玩家不发 field 类数据。 */
    public void send(Player player, Channel type, byte[] body) {
        if (!player.isOnline()) return;
        Session s = sessionOrNull(player);
        if (s == null && type != Channel.WELCOME) return; // 闸门
        int seq = s == null ? 0 : s.nextSeq();
        long tick = Bukkit.getCurrentTick();
        for (Fragmenter.Part p : Fragmenter.split(body)) {
            // 分片信息编码进 body 头：idx(u16) total(u16) + chunk
            byte[] payload = new byte[p.chunk.length + 4];
            payload[0] = (byte)(p.index >>> 8); payload[1] = (byte)p.index;
            payload[2] = (byte)(p.total >>> 8); payload[3] = (byte)p.total;
            System.arraycopy(p.chunk, 0, payload, 4, p.chunk.length);
            try {
                player.sendPluginMessage(plugin, type.fullName(), FrameCodec.encode(
                        new Frame(AtmosProtocol.PROTO_VERSION, type, seq, tick, payload)));
            } catch (Exception ignored) { /* 断链：静默 */ }
        }
    }
}
