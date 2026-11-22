package dev.atmos.server.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** 气候演化 tick 事件。 */
public final class ClimateTickEvent extends Event {
    private static final HandlerList H = new HandlerList();
    private final long tick;
    public ClimateTickEvent(long tick) { this.tick = tick; }
    public long tick() { return tick; }
    public HandlerList getHandlers() { return H; }
    public static HandlerList getHandlerList() { return H; }
}
