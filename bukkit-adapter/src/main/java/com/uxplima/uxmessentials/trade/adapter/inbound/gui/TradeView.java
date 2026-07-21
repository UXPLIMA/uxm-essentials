package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.application.TradeConfig;
import com.uxplima.uxmessentials.trade.application.TradeMessageKey;
import com.uxplima.uxmessentials.trade.application.TradeReceipt;
import com.uxplima.uxmessentials.trade.application.TradeSettlement;
import com.uxplima.uxmessentials.trade.application.port.TradeAudit;
import com.uxplima.uxmessentials.trade.application.port.TradeExperience;
import com.uxplima.uxmessentials.trade.domain.TradeId;
import com.uxplima.uxmessentials.trade.domain.TradeSession;
import com.uxplima.uxmessentials.trade.domain.TradeSide;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Builds and drives the two live inventory views of one same-server trade over the shared {@link TradeExchange}. Each
 * participant opens their own six-row window ({@link TradeLayout}): their editable offer on the left, the counterpart's
 * offer mirrored read-only on the right, and a confirm button. Placing or removing an item re-reads that side's offer
 * into the domain {@link TradeSession} (which clears both confirmations — the anti-scam invariant) and re-renders both
 * views live, so the other player sees the change and both confirms reset. When both sides confirm with no pending
 * change the swap runs: each player receives the other's stacks, with any overflow dropped at their feet rather than
 * deleted.
 *
 * <p>Item safety is absolute. An offered stack physically sits in the placing player's window, out of their inventory;
 * every terminal path — a commit, a window close, a disconnect, a world change, or a plugin stop — either swaps or
 * returns those stacks exactly once, gated by the exchange's single-winner settle flag. Every live-player touch hops to
 * that player's region thread through the injected {@link Scheduler}, so the flow is Folia-safe.
 */
@NullMarked
public final class TradeView {

    private final Messages messages;
    private final MessageSink messageSink;
    private final Scheduler scheduler;
    private final TradeLayout layout;
    private final TradeSessions sessions;
    private final TradeMoneyPrompt moneyPrompt;
    private final TradeExperiencePrompt experiencePrompt;

    /** The all-or-nothing mover for staked money and experience; always present because experience is always tradeable. */
    private final TradeSettlement settlement;

    /** The experience seam, read for the amount prompt's affordability check; also settled through {@link #settlement}. */
    private final TradeExperience experience;

    /** Whether the money button is shown, true only when an economy provider is wired. */
    private final boolean moneyEnabled;

    /** The completed-trade audit sink; consulted only when {@link #auditEnabled} is set. */
    private final TradeAudit audit;

    /** Whether a completed trade emits an audit line — the module's {@code audit} config knob, resolved once. */
    private final boolean auditEnabled;

    /** The materials refused into the window, resolved once from {@code item-blacklist}. */
    private final Set<Material> blacklist;

    /**
     * Viewers whose window closed only to show the money prompt — their next {@link #onClose} is a re-open, not a
     * cancel. Concurrent because the two participants' region threads touch it independently on Folia.
     */
    private final Set<UUID> promptingViewers = ConcurrentHashMap.newKeySet();

    public TradeView(
            Messages messages,
            MessageSink messageSink,
            Scheduler scheduler,
            TradeConfig config,
            TradeSessions sessions,
            TradeMoneyPrompt moneyPrompt,
            TradeExperiencePrompt experiencePrompt,
            TradeSettlement settlement,
            TradeExperience experience,
            boolean moneyEnabled,
            TradeAudit audit) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.messageSink = Objects.requireNonNull(messageSink, "messageSink");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(config, "config");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.moneyPrompt = Objects.requireNonNull(moneyPrompt, "moneyPrompt");
        this.experiencePrompt = Objects.requireNonNull(experiencePrompt, "experiencePrompt");
        this.settlement = Objects.requireNonNull(settlement, "settlement");
        this.experience = Objects.requireNonNull(experience, "experience");
        this.moneyEnabled = moneyEnabled;
        this.audit = Objects.requireNonNull(audit, "audit");
        this.auditEnabled = config.audit();
        // The money button appears only when economy is wired; without it the trade moves items and experience only and
        // the layout paints its money slots as plain divider filler. The experience button is always shown, experience
        // is a native resource that needs no provider.
        List<String> currencies = moneyEnabled ? config.currenciesAllowed() : List.of();
        this.layout = new TradeLayout(config.slotsPerSide(), currencies);
        this.blacklist = parseBlacklist(config.itemBlacklist());
    }

    /** The window listener bound to this view, its layout, and the item blacklist — the wiring registers it. */
    public TradeListener newListener() {
        return new TradeListener(this, layout, blacklist);
    }

    /**
     * Schedule an offer re-read on {@code holder}'s owner's region thread. The listener calls this after an allowed
     * click or drag; the hop lands the read on the next tick, once Bukkit has applied the interaction to the window.
     */
    void scheduleSync(TradeHolder holder) {
        Objects.requireNonNull(holder, "holder");
        scheduler.onEntity(holder.viewer(), () -> syncOffer(holder));
    }

    /** Open a fresh trade window between two distinct, online players, unless either is already trading. */
    public void open(Player a, Player b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        if (a.getUniqueId().equals(b.getUniqueId())
                || sessions.isTrading(a.getUniqueId())
                || sessions.isTrading(b.getUniqueId())) {
            return;
        }
        PlayerRef aRef = ref(a);
        PlayerRef bRef = ref(b);
        TradeId id = TradeId.newId();
        TradeExchange exchange = new TradeExchange(
                TradeSession.open(id, aRef, bRef),
                new TradeHolder(id, TradeSide.INITIATOR, aRef),
                new TradeHolder(id, TradeSide.PARTNER, bRef));
        sessions.register(exchange);
        openOne(exchange, TradeSide.INITIATOR);
        openOne(exchange, TradeSide.PARTNER);
    }

    /** Re-read {@code holder}'s side after a placement or removal and re-render both views. */
    void syncOffer(TradeHolder holder) {
        TradeExchange exchange = sessions.find(holder.tradeId());
        if (exchange == null) {
            return;
        }
        Inventory inv = holder.inventoryOrNull();
        if (inv == null) {
            return;
        }
        exchange.applyOffer(holder.side(), layout.readOffer(inv));
        rerender(exchange);
    }

    /** Handle a confirm click: run the swap once both sides agree, otherwise re-render the reset controls. */
    void confirm(TradeHolder holder) {
        TradeExchange exchange = sessions.find(holder.tradeId());
        if (exchange == null) {
            return;
        }
        switch (exchange.confirm(holder.side())) {
            case COMMITTED -> commit(exchange);
            case CHANGED -> rerender(exchange);
            case IGNORED -> {}
        }
    }

    /** A window closed — cancel the trade (if still live) and return both sides' items to their owners. */
    void onClose(TradeHolder holder) {
        if (promptingViewers.remove(holder.viewer().uuid())) {
            // The window closed only so the money prompt could take over the screen; the prompt callback reopens it.
            return;
        }
        TradeExchange exchange = sessions.find(holder.tradeId());
        if (exchange != null) {
            cancel(exchange);
        }
    }

    /** Refuse a blacklisted item back into the window — tell the viewer their placement was rejected. */
    void refuseBlacklisted(TradeHolder holder) {
        PlayerRef viewer = holder.viewer();
        messageSink.deliver(viewer, messages.resolve(viewer, TradeMessageKey.TRADE_ITEM_BLACKLISTED, Map.of()));
    }

    /**
     * A viewer left-clicked their "add money" button, prompt them for an amount of the currently-selected currency. The
     * window closes to show the prompt (suppressed from the cancel path), and the prompt callback re-stakes the money
     * and reopens. Because the money slot is single but the economy is multi-currency, the currency is the one the
     * viewer has cycled to (see {@link #cycleCurrency}); its staked amount is preserved for the currencies not selected.
     */
    void promptMoney(Player player, TradeHolder holder) {
        Objects.requireNonNull(player, "player");
        TradeExchange exchange = sessions.find(holder.tradeId());
        if (!moneyEnabled || exchange == null || exchange.session().state().isTerminal()) {
            return;
        }
        String currencyId = layout.currencyAt(holder.selectedCurrency());
        PlayerRef viewer = holder.viewer();
        promptingViewers.add(viewer.uuid());
        moneyPrompt.prompt(
                player,
                viewer,
                currencyId,
                text -> onMoneySubmitted(holder, currencyId, text, player),
                () -> reopenAfterPrompt(holder, player));
    }

    /** A viewer right-clicked their money button, advance the selected currency and re-render both sides. */
    void cycleCurrency(TradeHolder holder) {
        TradeExchange exchange = sessions.find(holder.tradeId());
        if (!moneyEnabled || exchange == null || exchange.session().state().isTerminal()) {
            return;
        }
        holder.cycleCurrency(layout.currencyCount());
        rerender(exchange);
    }

    private void onMoneySubmitted(TradeHolder holder, String currencyId, String text, Player player) {
        PlayerRef viewer = holder.viewer();
        TradeExchange exchange = sessions.find(holder.tradeId());
        if (exchange != null && !exchange.session().state().isTerminal()) {
            Optional<BigDecimal> amount = parseAmount(text);
            if (amount.isEmpty()) {
                messageSink.deliver(viewer, messages.resolve(viewer, TradeMessageKey.TRADE_MONEY_INVALID, Map.of()));
            } else {
                exchange.setMoney(holder.side(), currencyId, amount.get());
            }
        }
        reopenAfterPrompt(holder, player);
    }

    /**
     * A viewer clicked their "add experience" button, prompt them for an amount of experience points. The window closes
     * to show the prompt (suppressed from the cancel path), and the prompt callback validates the amount against the
     * experience they actually hold, re-stakes it, and reopens, exactly as the money button does.
     */
    void promptExperience(Player player, TradeHolder holder) {
        Objects.requireNonNull(player, "player");
        TradeExchange exchange = sessions.find(holder.tradeId());
        if (exchange == null || exchange.session().state().isTerminal()) {
            return;
        }
        PlayerRef viewer = holder.viewer();
        promptingViewers.add(viewer.uuid());
        experiencePrompt.prompt(
                player,
                viewer,
                text -> onExperienceSubmitted(holder, text, player),
                () -> reopenAfterPrompt(holder, player));
    }

    private void onExperienceSubmitted(TradeHolder holder, String text, Player player) {
        PlayerRef viewer = holder.viewer();
        TradeExchange exchange = sessions.find(holder.tradeId());
        if (exchange != null && !exchange.session().state().isTerminal()) {
            Optional<Long> amount = parseExperience(text);
            if (amount.isEmpty() || amount.get() > experience.available(viewer)) {
                messageSink.deliver(
                        viewer, messages.resolve(viewer, TradeMessageKey.TRADE_EXPERIENCE_INVALID, Map.of()));
            } else {
                exchange.setExperience(holder.side(), amount.get());
            }
        }
        reopenAfterPrompt(holder, player);
    }

    /** Reopen the viewer's window after the prompt resolves and re-render both sides (a money change reset confirms). */
    private void reopenAfterPrompt(TradeHolder holder, Player player) {
        promptingViewers.remove(holder.viewer().uuid());
        TradeExchange exchange = sessions.find(holder.tradeId());
        if (exchange == null || exchange.session().state().isTerminal()) {
            return;
        }
        scheduler.onEntity(holder.viewer(), () -> {
            Inventory inv = holder.inventoryOrNull();
            if (inv != null && player.isOnline()) {
                player.openInventory(inv);
            }
        });
        rerender(exchange);
    }

    /** Parse a typed money amount, a strictly-positive number, else empty (an invalid entry is rejected). */
    private static Optional<BigDecimal> parseAmount(String text) {
        try {
            BigDecimal value = new BigDecimal(text.trim());
            return value.signum() > 0 ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    /** Parse a typed experience amount, a strictly-positive whole number, else empty (an invalid entry is rejected). */
    private static Optional<Long> parseExperience(String text) {
        try {
            long value = Long.parseLong(text.trim());
            return value > 0 ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    private static Set<Material> parseBlacklist(List<String> ids) {
        Set<Material> materials = EnumSet.noneOf(Material.class);
        for (String id : ids) {
            Material material = Material.matchMaterial(id);
            if (material != null) {
                materials.add(material);
            }
        }
        return materials;
    }

    /** A participant changed world — treat it like a close, returning both sides' items. */
    void onLeave(UUID player) {
        TradeExchange exchange = sessions.find(player);
        if (exchange != null) {
            cancel(exchange);
        }
    }

    /** A participant disconnected — return the quitter's items inline (their thread is theirs now) and the other's async. */
    void onQuit(Player quitter) {
        TradeExchange exchange = sessions.find(quitter.getUniqueId());
        if (exchange == null || !exchange.beginCancel()) {
            return;
        }
        exchange.markCancelled();
        sessions.remove(exchange);
        TradeSide side = exchange.participant(TradeSide.INITIATOR).uuid().equals(quitter.getUniqueId())
                ? TradeSide.INITIATOR
                : TradeSide.PARTNER;
        deliver(quitter, TradeItemCodec.stacks(exchange.offer(side)));
        deliverAndClose(exchange, side.other(), exchange.offer(side.other()));
        notifyBoth(exchange, TradeMessageKey.TRADE_CANCELLED);
    }

    /** Drain every live trade on module stop or reload, returning all offered items. */
    public void closeAll() {
        for (TradeExchange exchange : sessions.all()) {
            cancel(exchange);
        }
    }

    private void openOne(TradeExchange exchange, TradeSide side) {
        PlayerRef viewer = exchange.participant(side);
        PlayerRef other = exchange.participant(side.other());
        scheduler.onEntity(viewer, () -> {
            Player live = Bukkit.getPlayer(viewer.uuid());
            if (live == null || !live.isOnline()) {
                return;
            }
            TradeHolder holder = exchange.holder(side);
            Inventory inv = Bukkit.createInventory(holder, TradeLayout.SIZE, title(viewer, other));
            holder.attach(inv);
            layout.seedFrame(inv);
            renderView(
                    inv,
                    viewer,
                    exchange.offer(side.other()),
                    false,
                    false,
                    holder.selectedCurrency(),
                    exchange.money(side),
                    exchange.money(side.other()),
                    exchange.experience(side),
                    exchange.experience(side.other()));
            live.openInventory(inv);
        });
    }

    private void rerender(TradeExchange exchange) {
        for (TradeSide side : TradeSide.values()) {
            renderSide(exchange, side);
        }
    }

    private void renderSide(TradeExchange exchange, TradeSide side) {
        TradeHolder holder = exchange.holder(side);
        PlayerRef viewer = holder.viewer();
        @Nullable ItemStack @Nullable [] otherOffer = exchange.offer(side.other());
        boolean self = exchange.confirmed(side);
        boolean partner = exchange.confirmed(side.other());
        int selected = holder.selectedCurrency();
        Map<String, BigDecimal> ownMoney = exchange.money(side);
        Map<String, BigDecimal> partnerMoney = exchange.money(side.other());
        long ownXp = exchange.experience(side);
        long partnerXp = exchange.experience(side.other());
        scheduler.onEntity(viewer, () -> {
            Inventory inv = holder.inventoryOrNull();
            if (inv != null) {
                renderView(inv, viewer, otherOffer, self, partner, selected, ownMoney, partnerMoney, ownXp, partnerXp);
            }
        });
    }

    private void renderView(
            Inventory inv,
            PlayerRef viewer,
            @Nullable ItemStack @Nullable [] otherOffer,
            boolean self,
            boolean partner,
            int selectedCurrency,
            Map<String, BigDecimal> ownMoney,
            Map<String, BigDecimal> partnerMoney,
            long ownExperience,
            long partnerExperience) {
        layout.renderMirror(inv, otherOffer);
        layout.renderControls(inv, messages, viewer, self, partner);
        layout.renderMoney(inv, messages, viewer, selectedCurrency, ownMoney, partnerMoney);
        layout.renderExperience(inv, messages, viewer, ownExperience, partnerExperience);
    }

    /**
     * Both sides confirmed: settle the trade all-or-nothing. First hop to each side's region to read its LIVE window
     * and freeze it, then off the tick thread move the staked money (guarded, double-spend-safe) and only on success
     * swap the item clones — a money failure leaves every stack with its owner, so nothing moves partway. Reading the
     * live window here (rather than the last snapshot) is the Folia sub-tick fix: a stack a player places in the same
     * tick as the counterpart's confirm, before its deferred re-read runs, is delivered instead of discarded. The
     * settle runs only once both region reads complete, so it never races an un-read window.
     */
    private void commit(TradeExchange exchange) {
        AtomicInteger remaining = new AtomicInteger(TradeSide.values().length);
        for (TradeSide side : TradeSide.values()) {
            freezeAndCapture(exchange, side, remaining);
        }
    }

    /** Read {@code side}'s live window into the item snapshot, clear and close it, and settle once both sides are read. */
    private void freezeAndCapture(TradeExchange exchange, TradeSide side, AtomicInteger remaining) {
        TradeHolder holder = exchange.holder(side);
        PlayerRef viewer = holder.viewer();
        scheduler.onEntity(viewer, () -> {
            Inventory inv = holder.inventoryOrNull();
            if (inv != null) {
                exchange.captureItems(side, layout.readOffer(inv));
                layout.clearOffer(inv);
            }
            Player live = Bukkit.getPlayer(viewer.uuid());
            if (live != null && live.isOnline()) {
                live.closeInventory();
            }
            if (remaining.decrementAndGet() == 0) {
                scheduler.async(() -> settleAndDeliver(exchange));
            }
        });
    }

    private void settleAndDeliver(TradeExchange exchange) {
        sessions.remove(exchange);
        // Settle the staked money and experience all-or-nothing; the items swap only when both moved (or nothing was
        // staked), so items, money, and experience move together or not at all.
        if (settlement.settle(exchange.session())) {
            giveBack(exchange, TradeSide.INITIATOR, exchange.offer(TradeSide.PARTNER));
            giveBack(exchange, TradeSide.PARTNER, exchange.offer(TradeSide.INITIATOR));
            if (auditEnabled) {
                audit.completed(TradeReceipt.of(exchange.session()));
            }
            notifyBoth(exchange, TradeMessageKey.TRADE_COMPLETED);
        } else {
            giveBack(exchange, TradeSide.INITIATOR, exchange.offer(TradeSide.INITIATOR));
            giveBack(exchange, TradeSide.PARTNER, exchange.offer(TradeSide.PARTNER));
            notifyBoth(exchange, TradeMessageKey.TRADE_INSUFFICIENT_FUNDS);
        }
    }

    /** Deliver the (cloned) stacks {@code incoming} to {@code side}'s player, dropping any overflow at their feet. */
    private void giveBack(TradeExchange exchange, TradeSide side, @Nullable ItemStack @Nullable [] incoming) {
        PlayerRef viewer = exchange.holder(side).viewer();
        List<ItemStack> gifts = TradeItemCodec.stacks(incoming);
        scheduler.onEntity(viewer, () -> {
            Player live = Bukkit.getPlayer(viewer.uuid());
            if (live != null && live.isOnline()) {
                deliver(live, gifts);
            }
        });
    }

    private void cancel(TradeExchange exchange) {
        if (!exchange.beginCancel()) {
            return;
        }
        exchange.markCancelled();
        sessions.remove(exchange);
        deliverAndClose(exchange, TradeSide.INITIATOR, exchange.offer(TradeSide.INITIATOR));
        deliverAndClose(exchange, TradeSide.PARTNER, exchange.offer(TradeSide.PARTNER));
        notifyBoth(exchange, TradeMessageKey.TRADE_CANCELLED);
    }

    private void deliverAndClose(TradeExchange exchange, TradeSide side, @Nullable ItemStack @Nullable [] incoming) {
        TradeHolder holder = exchange.holder(side);
        PlayerRef viewer = holder.viewer();
        List<ItemStack> gifts = TradeItemCodec.stacks(incoming);
        scheduler.onEntity(viewer, () -> {
            Inventory inv = holder.inventoryOrNull();
            if (inv != null) {
                layout.clearOffer(inv);
            }
            Player live = Bukkit.getPlayer(viewer.uuid());
            if (live != null && live.isOnline()) {
                deliver(live, gifts);
                live.closeInventory();
            }
        });
    }

    private void deliver(Player recipient, List<ItemStack> gifts) {
        if (gifts.isEmpty()) {
            return;
        }
        Map<Integer, ItemStack> overflow = recipient.getInventory().addItem(gifts.toArray(ItemStack[]::new));
        for (ItemStack extra : overflow.values()) {
            recipient.getWorld().dropItemNaturally(recipient.getLocation(), extra);
        }
    }

    private void notifyBoth(TradeExchange exchange, TradeMessageKey key) {
        for (TradeSide side : TradeSide.values()) {
            PlayerRef viewer = exchange.participant(side);
            messageSink.deliver(viewer, messages.resolve(viewer, key, Map.of()));
        }
    }

    private Component title(PlayerRef viewer, PlayerRef other) {
        return StyledText.render(
                messages.resolve(viewer, TradeMessageKey.TRADE_WINDOW_TITLE, Map.of("player", other.name())));
    }

    private static PlayerRef ref(Player player) {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }
}
