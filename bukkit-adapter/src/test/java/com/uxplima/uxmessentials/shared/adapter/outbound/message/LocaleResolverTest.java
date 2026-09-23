package com.uxplima.uxmessentials.shared.adapter.outbound.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.shared.application.message.LocaleScope;
import com.uxplima.uxmessentials.shared.application.port.LocaleStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which language a viewer is written to, on whichever thread the line is resolved.
 *
 * <p>On 2026-09-23 a player whose client read Turkish typed {@code /balance} and {@code /2fa} and read both answers in
 * English, while {@code /afk} beside them came back in Turkish. The request scope carries the client's language only
 * on the thread of the command, and both answers are written after a database read on another. The scope's own
 * javadoc said that after such a hop the resolver falls back to the viewer's own client locale; it fell back to the
 * server's default instead, because nothing told it the client's language.
 */
class LocaleResolverTest {

    private static final PlayerRef ADA = new PlayerRef(UUID.nameUUIDFromBytes(new byte[] {1}), "Ada");
    private static final Locale TURKISH = Locale.forLanguageTag("tr-TR");

    @Test
    @DisplayName("off the command's thread, a player is written to in the language their client reads")
    void theClientLanguageSurvivesAHop() {
        ClientLocales clients = new ClientLocales();
        clients.remember(ADA.uuid(), TURKISH);

        assertThat(new LocaleResolver(new Overrides(), clients, Locale.ENGLISH).resolve(ADA))
                .isEqualTo(TURKISH);
    }

    @Test
    @DisplayName("a chosen language beats the client, and the request's own beats what was last known")
    void theChainKeepsItsOrder() {
        ClientLocales clients = new ClientLocales();
        clients.remember(ADA.uuid(), TURKISH);
        Overrides overrides = new Overrides();
        LocaleResolver resolver = new LocaleResolver(overrides, clients, Locale.ENGLISH);
        AtomicReference<Locale> inScope = new AtomicReference<>();

        LocaleScope.runWith(Locale.FRENCH, () -> inScope.set(resolver.resolve(ADA)));
        overrides.chosen.put(ADA.uuid(), Locale.GERMAN);

        assertThat(inScope.get()).isEqualTo(Locale.FRENCH);
        assertThat(resolver.resolve(ADA)).isEqualTo(Locale.GERMAN);
    }

    @Test
    @DisplayName("a player the server has not heard from is written to in the server's default")
    void anUnknownClientGetsTheDefault() {
        assertThat(new LocaleResolver(new Overrides(), new ClientLocales(), Locale.ITALIAN).resolve(ADA))
                .isEqualTo(Locale.ITALIAN);
    }

    @Test
    @DisplayName("a player who left is forgotten")
    void aPlayerWhoLeftIsForgotten() {
        ClientLocales clients = new ClientLocales();
        clients.remember(ADA.uuid(), TURKISH);

        clients.forget(ADA.uuid());

        assertThat(clients.of(ADA.uuid())).isEmpty();
    }

    /** The persisted {@code /lang} choices. */
    private static final class Overrides implements LocaleStore {

        private final Map<UUID, Locale> chosen = new ConcurrentHashMap<>();

        @Override
        public Optional<Locale> override(PlayerRef player) {
            return Optional.ofNullable(chosen.get(player.uuid()));
        }

        @Override
        public void setOverride(PlayerRef player, Locale locale) {
            chosen.put(player.uuid(), locale);
        }

        @Override
        public void clearOverride(PlayerRef player) {
            chosen.remove(player.uuid());
        }
    }
}
