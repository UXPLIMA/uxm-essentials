package com.uxplima.uxmessentials.survival.adapter.outbound;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyleTags;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.survival.application.SurvivalConfig.SaleNotice;
import com.uxplima.uxmessentials.survival.application.SurvivalMessageKey;
import com.uxplima.uxmessentials.survival.application.port.SurvivalSales;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The auto-sell receipt: tells a seller what their mined drops fetched. Auto-sell takes a stack out of the drops the
 * moment it is paid for, so without this the item simply never arrives, which reads exactly like losing it: the
 * notice is what makes the sale visible.
 *
 * <h2>Pooling</h2>
 * Mining sells constantly: one notice per broken block would be a wall of text and, on the action bar, an unreadable
 * flicker. So a sale is pooled per seller for {@code autosell.notify.interval-seconds} and reported once, as the sum
 * of what was sold and the money it made ({@code 12x Iron Ingot, 3x Coal for $102}). An interval of 0 turns pooling
 * off and reports each sale as it happens. The window opens on the first sale after a quiet spell and closes on its
 * own, so the last sale of a session is reported like every other one rather than waiting for a break that never
 * comes.
 *
 * <h2>Threading</h2>
 * Sales arrive on whichever region thread owns the broken block. The pool is a {@code ConcurrentHashMap} keyed by
 * seller and merged through {@code compute}, so concurrent breaks in different regions accumulate without a lock. The
 * flush is scheduled through the {@link Scheduler} port: it waits off-tick ({@code asyncAfter}) and then hops to the
 * seller's own entity thread to send, which is where a per-player message is valid under Folia. A seller who has
 * logged off by then is a silent no-op.
 */
@NullMarked
public final class AutoSellNotices {

    private final Server server;
    private final Scheduler scheduler;
    private final Messages messages;
    private final SurvivalSales sales;
    private final SaleNotice mode;
    private final Duration window;
    private final MiniMessage miniMessage;
    private final Map<UUID, Pooled> pooled = new ConcurrentHashMap<>();

    public AutoSellNotices(
            Server server,
            Scheduler scheduler,
            Messages messages,
            SurvivalSales sales,
            SaleNotice mode,
            int intervalSeconds) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sales = Objects.requireNonNull(sales, "sales");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.window = Duration.ofSeconds(Math.max(0, intervalSeconds));
        this.miniMessage = MiniMessage.miniMessage();
    }

    /**
     * Record one paid sale: {@code sold} are the stacks that left the drops and {@code amount} is what they credited.
     * Reports immediately when pooling is off, otherwise folds the sale into the seller's open window (opening one,
     * and the flush that closes it, when this is the first sale).
     */
    public void sold(Player seller, List<ItemStack> sold, BigDecimal amount) {
        Objects.requireNonNull(seller, "seller");
        Objects.requireNonNull(sold, "sold");
        Objects.requireNonNull(amount, "amount");
        if (mode == SaleNotice.OFF || sold.isEmpty() || amount.signum() <= 0) {
            return;
        }
        if (window.isZero()) {
            send(BukkitRefs.toRef(seller), seller, counted(sold), amount);
            return;
        }
        @Nullable Pooled open = pooled.compute(
                seller.getUniqueId(),
                (key, current) ->
                        current == null ? new Pooled(counted(sold), amount, true) : current.plus(sold, amount));
        if (open != null && open.opening()) {
            // The sale that opened the window owns its flush, so exactly one is scheduled per window.
            PlayerRef ref = BukkitRefs.toRef(seller);
            scheduler.asyncAfter(window, () -> scheduler.onEntity(ref, () -> flush(ref)));
        }
    }

    /** Send the seller's pooled sales and close the window; a seller who logged off loses only the notice. */
    private void flush(PlayerRef ref) {
        @Nullable Pooled open = pooled.remove(ref.uuid());
        if (open == null) {
            return;
        }
        @Nullable Player live = server.getPlayer(ref.uuid());
        if (live != null) {
            send(ref, live, open.items(), open.total());
        }
    }

    /** Render the receipt in the seller's locale and deliver it to the configured surface. */
    private void send(PlayerRef viewer, Player live, Map<Material, Integer> items, BigDecimal total) {
        String separator = messages.resolve(viewer, SurvivalMessageKey.SURVIVAL_AUTOSELL_SOLD_SEPARATOR, Map.of());
        StringBuilder list = new StringBuilder();
        for (Map.Entry<Material, Integer> entry : items.entrySet()) {
            if (list.length() > 0) {
                list.append(separator);
            }
            list.append(messages.resolve(
                    viewer,
                    SurvivalMessageKey.SURVIVAL_AUTOSELL_SOLD_ENTRY,
                    Map.of("amount", entry.getValue().toString(), "item", itemName(entry.getKey()))));
        }
        SurvivalMessageKey key = mode == SaleNotice.CHAT
                ? SurvivalMessageKey.SURVIVAL_AUTOSELL_SOLD
                : SurvivalMessageKey.SURVIVAL_AUTOSELL_SOLD_BAR;
        String line = messages.resolve(viewer, key, Map.of("items", list.toString(), "amount", sales.format(total)));
        Component rendered = miniMessage.deserialize(line, StyleTags.resolver());
        if (mode == SaleNotice.CHAT) {
            live.sendMessage(rendered);
        } else {
            live.sendActionBar(rendered);
        }
    }

    /**
     * The item's name as a MiniMessage translation tag, so the receipt names it in the reader's own client language
     * rather than in a hardcoded English word list. The key comes from the material itself and can never carry a
     * quote, so it is safe to inline into the tag.
     */
    private static String itemName(Material material) {
        return "<lang:'" + material.translationKey() + "'>";
    }

    /** The stacks folded into one count per material, insertion-ordered so the receipt reads in mining order. */
    private static Map<Material, Integer> counted(List<ItemStack> sold) {
        Map<Material, Integer> counts = new LinkedHashMap<>();
        for (ItemStack item : sold) {
            counts.merge(item.getType(), item.getAmount(), Integer::sum);
        }
        return counts;
    }

    /**
     * One seller's open window: what has been sold in it and what it has made. Immutable and replaced through
     * {@code compute}, per the shared-state rule for a map keyed by player.
     *
     * @param items the running count per sold material
     * @param total the running proceeds
     * @param opening whether this is the window's first sale, the one that owes the scheduled flush
     */
    private record Pooled(Map<Material, Integer> items, BigDecimal total, boolean opening) {

        Pooled plus(List<ItemStack> sold, BigDecimal amount) {
            Map<Material, Integer> merged = new LinkedHashMap<>(items);
            for (ItemStack item : sold) {
                merged.merge(item.getType(), item.getAmount(), Integer::sum);
            }
            return new Pooled(merged, total.add(amount), false);
        }
    }
}
