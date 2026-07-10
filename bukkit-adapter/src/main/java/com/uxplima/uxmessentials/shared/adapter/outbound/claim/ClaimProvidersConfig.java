package com.uxplima.uxmessentials.shared.adapter.outbound.claim;

import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * The operator's cross-cutting claim-provider choices, read once from the root {@code config.conf}
 * {@code claims} block at wiring time. Which claim plugins to consult and how to fold their answers is a
 * server-wide decision, not a per-module one — homes, teleport and poses all read the same block — so it lives
 * in the globals file rather than under any single module's config subtree, alongside {@code network} and
 * {@code update-check}.
 *
 * <p>A present claim plugin is consulted unless the operator turns it off, so a provider is enabled by
 * default and only the keys set to {@code false} under {@code claims.providers} are recorded here as disabled.
 *
 * @param disabledProviders the provider keys the operator switched off (lower-cased); an unlisted provider is on
 * @param combine how {@link CompositeClaimProvider} folds several overlapping claims' answers into one
 */
@NullMarked
public record ClaimProvidersConfig(Set<String> disabledProviders, CombineMode combine) {

    private static final String PROVIDERS_PATH = "claims.providers";
    private static final String COMBINE_PATH = "claims.combine";

    public ClaimProvidersConfig {
        Objects.requireNonNull(disabledProviders, "disabledProviders");
        Objects.requireNonNull(combine, "combine");
        disabledProviders = Set.copyOf(disabledProviders);
    }

    /** Every provider on, folded with {@link CombineMode#ANY_LAND} — the behaviour when no {@code claims} block is set. */
    public static ClaimProvidersConfig defaults() {
        return new ClaimProvidersConfig(Set.of(), CombineMode.ANY_LAND);
    }

    /** Read the {@code claims} block from {@code config}, defaulting each provider on and the combine to any-land. */
    public static ClaimProvidersConfig from(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        Set<String> disabled = new HashSet<>();
        for (String key : config.getKeys(PROVIDERS_PATH)) {
            if (!config.getBoolean(PROVIDERS_PATH + "." + key, true)) {
                disabled.add(normalize(key));
            }
        }
        CombineMode combine = CombineMode.fromConfig(config.getString(COMBINE_PATH, CombineMode.ANY_LAND.configName()));
        return new ClaimProvidersConfig(disabled, combine);
    }

    /** Whether the provider registered under {@code key} may be consulted; one absent from config is on by default. */
    public boolean enabled(String key) {
        Objects.requireNonNull(key, "key");
        return !disabledProviders.contains(normalize(key));
    }

    private static String normalize(String key) {
        return key.toLowerCase(Locale.ROOT);
    }

    /** How overlapping claims from several providers are folded into a single trust/ownership answer. */
    public enum CombineMode {

        /** Trusted or owner if <em>any</em> covering claim says so — the most permissive reading. */
        ANY_LAND("any-land"),

        /** Trusted or owner only if <em>every</em> covering claim says so — the strictest reading. */
        ALL_LAND("all-land");

        private final String configName;

        CombineMode(String configName) {
            this.configName = configName;
        }

        /** The lower-cased token an operator writes under {@code claims.combine}. */
        public String configName() {
            return configName;
        }

        /** Parse a {@code claims.combine} token, falling back to {@link #ANY_LAND} for anything unrecognised. */
        static CombineMode fromConfig(String raw) {
            Objects.requireNonNull(raw, "raw");
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            return ALL_LAND.configName.equals(normalized) ? ALL_LAND : ANY_LAND;
        }
    }
}
