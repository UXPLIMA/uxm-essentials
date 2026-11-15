package com.uxplima.uxmessentials.migration.adapter;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import com.uxplima.uxmessentials.migration.BalancePolicy;
import com.uxplima.uxmessentials.migration.ConflictPolicy;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * The two collision knobs of an import, read from the migration module config. A value the importer does not know
 * falls back to the safe default and says so in the log, because a typo in {@code on-conflict = overwrite} that
 * silently became {@code skip} would look to the operator like an import that ignored their setting.
 *
 * @param onConflict how a record that already exists is handled
 * @param balancePolicy whether an imported balance replaces one a wallet already holds
 */
@NullMarked
record ImportPolicies(ConflictPolicy onConflict, BalancePolicy balancePolicy) {

    private static final String FILE = "modules/migration/config.conf";

    ImportPolicies {
        Objects.requireNonNull(onConflict, "onConflict");
        Objects.requireNonNull(balancePolicy, "balancePolicy");
    }

    /** Read both policies from the {@code modules.migration} subtree, warning about each value it cannot read. */
    static ImportPolicies read(ConfigStore config, Logger log) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(log, "log");
        ConflictPolicy onConflict = policy(
                config,
                log,
                "on-conflict",
                "skip, overwrite, merge",
                "skip",
                ConflictPolicy::parse,
                ConflictPolicy.SKIP);
        BalancePolicy balancePolicy = policy(
                config,
                log,
                "balance-policy",
                "skip-if-present, overwrite",
                "skip-if-present",
                BalancePolicy::parse,
                BalancePolicy.SKIP_IF_PRESENT);
        return new ImportPolicies(onConflict, balancePolicy);
    }

    private static <T> T policy(
            ConfigStore config,
            Logger log,
            String key,
            String accepted,
            String fallbackKey,
            Function<String, Optional<T>> parser,
            T fallback) {
        String raw = config.getString(key, fallbackKey);
        Optional<T> parsed = parser.apply(raw);
        if (parsed.isPresent()) {
            return parsed.get();
        }
        log.warn("{}: {} = '{}' is not one of {}; using {}", FILE, key, raw, accepted, fallbackKey);
        return fallback;
    }
}
