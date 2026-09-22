package com.uxplima.uxmessentials.bootstrap.health;

import java.util.Objects;
import java.util.function.BooleanSupplier;

import com.uxplima.uxmessentials.shared.application.health.HealthCheck;
import com.uxplima.uxmessentials.shared.application.health.HealthResult;
import org.jspecify.annotations.NullMarked;

/**
 * The placeholders line of {@code /uxmess doctor}: whether the expansion this plugin publishes is registered.
 *
 * <p>Our placeholders in somebody else's scoreboard read as nothing when the plugin behind them is not there,
 * and nothing else on the server shows it: the line simply renders empty, and the operator concludes that the
 * placeholder is wrong rather than absent.
 *
 * <p>A warning and never a failure. Every other part of this plugin works without PlaceholderAPI, and an
 * operator who has not installed it has not made a mistake. The second warning is the one worth reading: the
 * plugin is there and nothing was published, which happens when no placeholder group is enabled, and that is
 * ours to explain rather than theirs to guess.
 */
@NullMarked
public final class PlaceholderExpansionHealthCheck implements HealthCheck {

    private final BooleanSupplier published;
    private final BooleanSupplier placeholderApiPresent;

    public PlaceholderExpansionHealthCheck(boolean published, BooleanSupplier placeholderApiPresent) {
        this(() -> published, placeholderApiPresent);
    }

    /**
     * As above, with whether the expansion published asked when the doctor runs. The expansion is registered on
     * {@code ServerLoadEvent}, after this check is built, because this plugin enables before PlaceholderAPI does.
     */
    public PlaceholderExpansionHealthCheck(BooleanSupplier published, BooleanSupplier placeholderApiPresent) {
        this.published = Objects.requireNonNull(published, "published");
        this.placeholderApiPresent = Objects.requireNonNull(placeholderApiPresent, "placeholderApiPresent");
    }

    @Override
    public String name() {
        return "placeholders";
    }

    @Override
    public HealthResult check() {
        if (published.getAsBoolean()) {
            return HealthResult.ok("published");
        }
        if (!placeholderApiPresent.getAsBoolean()) {
            return HealthResult.warn("PlaceholderAPI is not on this server, so our placeholders publish nothing:"
                    + " every other part of the plugin works");
        }
        return HealthResult.warn("PlaceholderAPI is here and no placeholder group is enabled, so nothing was"
                + " published and every placeholder of ours reads as empty");
    }
}
