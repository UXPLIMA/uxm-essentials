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
    private final BooleanSupplier currentForm;
    private final BooleanSupplier groupsEnabled;
    private final BooleanSupplier placeholderApiPresent;

    /**
     * Every state is asked when the doctor runs: the placeholders are published on {@code ServerLoadEvent}, after
     * this check is built, because this plugin enables before PlaceholderAPI does, and another plugin can take an
     * identifier from it at any time after.
     *
     * @param published whether any expansion of ours answers right now
     * @param currentForm whether the {@code %uxm_essentials_<key>%} form answers through the shared {@code uxm}
     *     expansion, rather than only the older one
     * @param groupsEnabled whether at least one placeholder group is enabled, so there was something to publish
     * @param placeholderApiPresent whether PlaceholderAPI is on the server
     */
    public PlaceholderExpansionHealthCheck(
            BooleanSupplier published,
            BooleanSupplier currentForm,
            BooleanSupplier groupsEnabled,
            BooleanSupplier placeholderApiPresent) {
        this.published = Objects.requireNonNull(published, "published");
        this.currentForm = Objects.requireNonNull(currentForm, "currentForm");
        this.groupsEnabled = Objects.requireNonNull(groupsEnabled, "groupsEnabled");
        this.placeholderApiPresent = Objects.requireNonNull(placeholderApiPresent, "placeholderApiPresent");
    }

    @Override
    public String name() {
        return "placeholders";
    }

    @Override
    public HealthResult check() {
        if (published.getAsBoolean()) {
            if (!currentForm.getAsBoolean()) {
                return HealthResult.warn("only the older %uxmessentials_<key>% form answers: the uxm identifier is"
                        + " held by an expansion that is not the one UXM plugins share, or another plugin holds the"
                        + " essentials prefix in it, so %uxm_essentials_<key>% reads as written; the log names which"
                        + " (papi_expansion_not_joined)");
            }
            return HealthResult.ok("published");
        }
        if (!placeholderApiPresent.getAsBoolean()) {
            return HealthResult.warn("PlaceholderAPI is not on this server, so our placeholders publish nothing:"
                    + " every other part of the plugin works");
        }
        if (!groupsEnabled.getAsBoolean()) {
            return HealthResult.warn("PlaceholderAPI is here and no placeholder group is enabled, so nothing was"
                    + " published and every placeholder of ours reads as empty");
        }
        return HealthResult.warn("our placeholders are not held: we could not join the shared uxm expansion and"
                + " another plugin took the uxmessentials identifier, at start or since, so every placeholder of ours"
                + " reads as written; the log names the holder (papi_expansion_not_joined,"
                + " papi_expansion_identifier_taken or papi_expansion_evicted)");
    }
}
