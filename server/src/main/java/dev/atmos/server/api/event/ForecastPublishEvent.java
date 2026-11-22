package dev.atmos.server.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** 预报发布事件（可取消）。 */
public final class ForecastPublishEvent extends Event {
    private static final HandlerList H = new HandlerList();
    private final int days;
    public ForecastPublishEvent(int days) { this.days = days; }
    public int days() { return days; }
    public HandlerList getHandlers() { return H; }
    public static HandlerList getHandlerList() { return H; }
}
