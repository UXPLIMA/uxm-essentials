package com.uxplima.uxmessentials.custommenus.adapter.inbound.listener;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.shared.adapter.inbound.window.OpenWatch;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmlib.menu.api.event.MenuOpenEvent;
import com.uxplima.uxmlib.menu.runtime.MenuHolder;
import org.jspecify.annotations.NullMarked;

/**
 * Confirms a menu opened for somebody else once it is really in front of them. The menu is built off the tick and
 * opened on the target's thread later, and the open can still be refused there, by the menu's own rules or by another
 * plugin; so the sender is told only once it is showing, and at most once.
 *
 * <p>A chest carries a token of its own request, so its open event is matched to exactly that request however late
 * it comes. A Bedrock player is sent a form instead, which raises no open event and carries nothing of the request:
 * it is matched by the menu's name, from the engine's own open event, which the form follows on the same thread, and
 * only within {@link OpenWatch#WAIT}, so a later open of the same menu by the player themselves is not taken for it.
 */
@NullMarked
public final class MenuOpenedListener implements Listener {

    /** The passthrough key a request's token travels under, into the chest's holder. */
    static final String REQUEST = "uxm-open-request";

    /**
     * How long a chest request is remembered. It is matched by its own token, so this only bounds the memory of a
     * request whose menu never opened.
     */
    private static final Duration CHEST_WAIT = Duration.ofMinutes(5);

    private final OpenWatch<Object> chests = new OpenWatch<>(System::nanoTime, CHEST_WAIT);
    private final OpenWatch<String> forms = new OpenWatch<>(System::nanoTime);
    /** The menu each player's thread is opening right now, between the engine's open event and its form. */
    private final Map<UUID, String> opening = new ConcurrentHashMap<>();

    private final Scheduler scheduler;

    public MenuOpenedListener(Scheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /**
     * Run {@code shown} once the menu {@code menu} opens for {@code target}, as a chest or as a form. Call before
     * asking for the open, and pass what this returns in the open's passthrough, so its chest can be told apart.
     */
    public Map<String, Object> expect(Player target, String menu, Runnable shown) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(shown, "shown");
        Object request = new Object();
        AtomicBoolean told = new AtomicBoolean();
        Runnable once = () -> {
            if (told.compareAndSet(false, true)) {
                shown.run();
            }
        };
        chests.expect(target.getUniqueId(), request::equals, once);
        forms.expect(target.getUniqueId(), menu::equals, once);
        return Map.of(REQUEST, request);
    }

    /** Last, cancelled or not, so the open is already decided. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder(false) instanceof MenuHolder holder) {
            Object request = holder.ctx().passthrough().get(REQUEST);
            if (request != null) {
                chests.decided(event.getPlayer().getUniqueId(), request, event.isCancelled());
            }
        }
    }

    /**
     * The engine is about to draw a menu for a player. Remembered until the next tick, since a form, if one follows,
     * is sent on this same thread before then; a cancelled open forgets what was there.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onMenuOpen(MenuOpenEvent event) {
        Player viewer = event.getViewer();
        UUID id = viewer.getUniqueId();
        if (event.isCancelled()) {
            opening.remove(id);
            return;
        }
        String menu = event.getMenuId();
        opening.put(id, menu);
        scheduler.laterEntity(BukkitRefs.toRef(viewer), Duration.ZERO, () -> opening.remove(id, menu));
    }

    /** A menu form went out to {@code player}: the menu whose open is under way reached them. */
    public void formSent(Player player) {
        UUID id = player.getUniqueId();
        String menu = opening.remove(id);
        if (menu != null) {
            forms.decided(id, menu, false);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        chests.forget(id);
        forms.forget(id);
        opening.remove(id);
    }
}
