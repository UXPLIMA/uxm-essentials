package com.uxplima.uxmessentials.shared.application;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.NameCase;

/**
 * Whether names that differ only in case are one account or two on this server, as it stands right now.
 *
 * <p>The setting ({@code lookup.name-case}) can force either answer. On {@link Setting#AUTO}, the default, the
 * answer starts from what the server reports about its logins and is corrected by evidence. A server behind a proxy
 * reports the proxy's online mode from its own config, and the proxy settings Paper reads default to online, so a
 * network whose proxy runs in offline mode reports authenticated logins it does not have. Its joins give it away:
 * an offline-mode login carries a name-derived version 3 uuid, which an authenticated Java account never has, and a
 * Bedrock player through Floodgate carries an Xbox id (version 0), so it proves nothing either way. The first such
 * join switches the answer to {@link NameCase#SENSITIVE} for good, is logged as a warning, and is kept in
 * {@link Memory} so the next start begins case-sensitive before anybody joins.
 *
 * <p>Only a completed join counts, never a login that was refused. A server that authenticates its own logins
 * (online mode on the server itself) never switches: a version 3 uuid there is a fake player or a forged handshake,
 * not evidence about the network.
 *
 * <p>Everything that tells accounts apart by name reads this on every question rather than once at wiring, so the
 * switch reaches the lookups, the login check and the operator list at once. Reads and the switch are lock-free.
 */
public final class LiveNameCase implements Supplier<NameCase> {

    /** The {@code lookup.name-case} setting. */
    public enum Setting {
        /** Start from the server's report, and switch to case-sensitive on the first offline-mode join. */
        AUTO,
        /** Always case-sensitive: every spelling is its own account. */
        SENSITIVE,
        /** Always case-insensitive: a name has one owner. */
        INSENSITIVE;

        /** The setting {@code raw} names, or {@link #AUTO} with a warning for anything else. */
        public static Setting parse(String raw, Logger log) {
            Objects.requireNonNull(raw, "raw");
            Objects.requireNonNull(log, "log");
            String wanted = raw.strip().toUpperCase(Locale.ROOT);
            for (Setting setting : values()) {
                if (setting.name().equals(wanted)) {
                    return setting;
                }
            }
            log.warn("event=name_case_unknown value={} using=auto expected=auto|sensitive|insensitive", raw);
            return AUTO;
        }
    }

    /** Where a detected case-sensitive answer is kept from one start to the next. */
    public interface Memory {

        /** Nothing is kept: every start begins from the server's report. */
        Memory NONE = new Memory() {
            @Override
            public boolean detected() {
                return false;
            }

            @Override
            public void remember() {
                // Nothing is kept.
            }

            @Override
            public void forget() {
                // Nothing is kept.
            }
        };

        /** Whether an earlier start found this server case-sensitive. */
        boolean detected();

        /** Keep that this server was found case-sensitive. It must not block the caller's thread. */
        void remember();

        /** Drop what was kept: the operator has said the server is case-insensitive. */
        void forget();
    }

    private static final int OFFLINE_UUID_VERSION = 3;

    private final Setting setting;
    private final boolean authenticatedHere;
    private final Memory memory;
    private final Logger log;
    private volatile NameCase current;

    /**
     * @param setting the configured setting
     * @param reported what the server says about its logins, the starting answer on {@link Setting#AUTO}
     * @param authenticatedHere whether the server itself runs in online mode, where nothing switches the answer
     * @param memory where a detected case-sensitive answer is kept across restarts
     */
    public LiveNameCase(Setting setting, NameCase reported, boolean authenticatedHere, Memory memory, Logger log) {
        this.setting = Objects.requireNonNull(setting, "setting");
        Objects.requireNonNull(reported, "reported");
        this.authenticatedHere = authenticatedHere;
        this.memory = Objects.requireNonNull(memory, "memory");
        this.log = Objects.requireNonNull(log, "log");
        this.current = switch (setting) {
            case SENSITIVE -> NameCase.SENSITIVE;
            case INSENSITIVE -> {
                memory.forget();
                yield NameCase.INSENSITIVE;
            }
            case AUTO -> start(reported);
        };
    }

    private NameCase start(NameCase reported) {
        if (authenticatedHere || reported == NameCase.SENSITIVE) {
            return reported;
        }
        if (memory.detected()) {
            log.info("event=name_case_restored value=sensitive reason=offline_mode_join_seen_before");
            return NameCase.SENSITIVE;
        }
        return reported;
    }

    @Override
    public NameCase get() {
        return current;
    }

    /** A player completed a join with {@code account} under {@code name}; an offline-mode uuid settles the question. */
    public void observe(UUID account, String name) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(name, "name");
        if (setting != Setting.AUTO
                || authenticatedHere
                || current == NameCase.SENSITIVE
                || account.version() != OFFLINE_UUID_VERSION) {
            return;
        }
        synchronized (this) {
            if (current == NameCase.SENSITIVE) {
                return;
            }
            current = NameCase.SENSITIVE;
        }
        memory.remember();
        log.warn(
                "event=name_case_detected value=sensitive player={} uuid={} reason=offline_mode_uuid hint=set"
                        + " lookup.name-case to say so up front",
                name,
                account);
    }
}
