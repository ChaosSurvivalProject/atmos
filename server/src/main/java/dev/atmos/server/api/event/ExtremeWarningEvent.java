package dev.atmos.server.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** 极端预警事件。 */
public final class ExtremeWarningEvent extends Event {
    private static final HandlerList H = new HandlerList();
    private final int level;
    private final String key;
    public ExtremeWarningEvent(int level, String key) { this.level = level; this.key = key; }
    public int level() { return level; }
    public String key() { return key; }
    public HandlerList getHandlers() { return H; }
    public static HandlerList getHandlerList() { return H; }
}
