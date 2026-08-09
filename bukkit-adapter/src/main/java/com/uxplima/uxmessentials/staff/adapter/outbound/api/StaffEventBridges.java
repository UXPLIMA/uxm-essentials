package com.uxplima.uxmessentials.staff.adapter.outbound.api;

import java.util.Objects;

import com.uxplima.uxmessentials.api.bukkit.event.staff.UxmStaffChatEvent;
import com.uxplima.uxmessentials.api.bukkit.event.staff.UxmStaffModeEvent;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridgeRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.Region;
import com.uxplima.uxmessentials.staff.domain.event.StaffChatSent;
import com.uxplima.uxmessentials.staff.domain.event.StaffModeEntered;
import com.uxplima.uxmessentials.staff.domain.event.StaffModeExited;
import org.jspecify.annotations.NullMarked;

/**
 * Which Bukkit event each staff fact becomes.
 *
 * <p>Entering and leaving staff mode share one event with a flag rather than getting one class each: a listener that
 * cares about one invariably cares about the other, and a single class is what stops it having to register twice.
 */
@NullMarked
public final class StaffEventBridges {

    private StaffEventBridges() {}

    public static void register(EventBridgeRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(
                StaffModeEntered.class,
                UxmStaffModeEvent.getHandlerList(),
                fact -> new UxmStaffModeEvent(fact.staff().uuid(), fact.staff().name(), true),
                fact -> Region.entity(fact.staff()));
        registry.register(
                StaffModeExited.class,
                UxmStaffModeEvent.getHandlerList(),
                fact -> new UxmStaffModeEvent(fact.staff().uuid(), fact.staff().name(), false),
                fact -> Region.entity(fact.staff()));
        registry.register(
                StaffChatSent.class,
                UxmStaffChatEvent.getHandlerList(),
                fact -> new UxmStaffChatEvent(fact.from().uuid(), fact.from().name(), fact.message()),
                fact -> Region.entity(fact.from()));
    }
}
