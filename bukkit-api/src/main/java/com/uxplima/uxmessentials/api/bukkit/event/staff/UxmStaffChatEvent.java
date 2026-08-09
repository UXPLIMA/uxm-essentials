package com.uxplima.uxmessentials.api.bukkit.event.staff;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import org.jspecify.annotations.NullMarked;

/** Something was said on staff chat. It has already been delivered to everyone who can read it. */
@NullMarked
public final class UxmStaffChatEvent extends UxmPlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String message;

    public UxmStaffChatEvent(UUID senderId, String senderName, String message) {
        super(senderId, senderName);
        this.message = Objects.requireNonNull(message, "message");
    }

    /** What was said, as typed. */
    public String getMessage() {
        return message;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
