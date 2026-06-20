package com.uxplima.uxmessentials.shared.adapter.inbound.gui.property;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import org.bukkit.Material;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * A property whose click steps an integer value within bounds and hands the new value to a setter. A
 * left-click adds {@code step}, a right-click subtracts it, and a shift-click multiplies the step by
 * {@code shiftMultiplier} for coarse jumps; the result is clamped to {@code [min, max]}. The new value is
 * written through the caller's setter off the tick thread via the shared {@link Scheduler}, then the editor is
 * redrawn so the value lore shows it. A step that does not change the value (already at a bound) skips the
 * write and just redraws.
 *
 * <p>The step sizes, bounds, and multiplier come from the editor layout conf (the caller passes them in), so
 * nothing numeric is hardcoded. The setter is the module's existing application use case wrapped as a
 * {@link Consumer}; this property holds no domain logic.
 */
@NullMarked
public final class NumberProperty implements EditableProperty {

    private final MessageKey label;
    private final Material icon;
    private final LongSupplier current;
    private final long step;
    private final long shiftMultiplier;
    private final long min;
    private final long max;
    private final Consumer<Long> setter;
    private final Scheduler scheduler;

    public NumberProperty(
            MessageKey label,
            Material icon,
            LongSupplier current,
            long step,
            long shiftMultiplier,
            long min,
            long max,
            Consumer<Long> setter,
            Scheduler scheduler) {
        this.label = Objects.requireNonNull(label, "label");
        this.icon = Objects.requireNonNull(icon, "icon");
        this.current = Objects.requireNonNull(current, "current");
        if (step <= 0) {
            throw new IllegalArgumentException("step must be > 0, was " + step);
        }
        if (shiftMultiplier < 1) {
            throw new IllegalArgumentException("shiftMultiplier must be >= 1, was " + shiftMultiplier);
        }
        if (min > max) {
            throw new IllegalArgumentException("min " + min + " must be <= max " + max);
        }
        this.step = step;
        this.shiftMultiplier = shiftMultiplier;
        this.min = min;
        this.max = max;
        this.setter = Objects.requireNonNull(setter, "setter");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public MessageKey label() {
        return label;
    }

    @Override
    public Material icon() {
        return icon;
    }

    @Override
    public String valueLore(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        return Long.toString(current.getAsLong());
    }

    @Override
    public void onClick(ClickContext context) {
        Objects.requireNonNull(context, "context");
        long delta = (context.rightClick() ? -step : step) * (context.shiftClick() ? shiftMultiplier : 1);
        long next = clamp(current.getAsLong() + delta);
        if (next == current.getAsLong()) {
            context.reopen().run();
            return;
        }
        scheduler.async(() -> {
            setter.accept(next);
            scheduler.onEntity(context.viewer(), context.reopen());
        });
    }

    private long clamp(long value) {
        return Math.max(min, Math.min(max, value));
    }
}
