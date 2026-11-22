package dev.atmos.server.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** 天气单体生成事件。 */
public final class WeatherCellSpawnEvent extends Event {
    private static final HandlerList H = new HandlerList();
    private final int id;
    public WeatherCellSpawnEvent(int id) { this.id = id; }
    public int id() { return id; }
    public HandlerList getHandlers() { return H; }
    public static HandlerList getHandlerList() { return H; }
}
