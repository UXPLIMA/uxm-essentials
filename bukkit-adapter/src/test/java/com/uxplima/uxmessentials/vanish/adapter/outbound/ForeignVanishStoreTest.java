package com.uxplima.uxmessentials.vanish.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import com.uxplima.uxmessentials.vanish.domain.VanishState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The overlay that folds SuperVanish and PremiumVanish hidden players into our own vanish authority. The foreign
 * read is reflective and neither plugin is on the test classpath, so the folding is exercised through the
 * {@code ForeignVanish} seam with a fake set of hidden players, and the reflective reader is exercised in the one
 * state a test can reach: the plugin present but its API unreachable, where it must hide nobody rather than throw
 * into a nametag render.
 */
class ForeignVanishStoreTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final VanishLevel FOREIGN_LEVEL = VanishLevel.of(1);

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void present_isFalse_withNeitherVanishPluginInstalled() {
        assertThat(ForeignVanishStore.present(server)).isFalse();
    }

    @Test
    void present_isTrue_withSuperVanishInstalled() {
        MockBukkit.createMockPlugin("SuperVanish");

        assertThat(ForeignVanishStore.present(server)).isTrue();
    }

    @Test
    void present_isTrue_withPremiumVanishInstalled() {
        MockBukkit.createMockPlugin("PremiumVanish");

        assertThat(ForeignVanishStore.present(server)).isTrue();
    }

    @Test
    void withNobodyHiddenElsewhereTheOverlayIsTransparent() {
        InMemoryVanishStore ours = new InMemoryVanishStore();
        ours.vanish(ALICE, VanishLevel.of(2));
        VanishStore overlay = overlay(ours, Set.of());

        assertThat(overlay.snapshot()).isEqualTo(ours.snapshot());
        assertThat(overlay.vanished()).isEqualTo(ours.vanished());
        assertThat(overlay.isVanished(BOB)).isFalse();
        assertThat(overlay.levelOf(ALICE)).contains(VanishLevel.of(2));
    }

    @Test
    void aPlayerHiddenByTheOtherPluginIsVanishedAtTheConfiguredLevel() {
        VanishStore overlay = overlay(new InMemoryVanishStore(), Set.of(BOB));

        assertThat(overlay.isVanished(BOB)).isTrue();
        assertThat(overlay.levelOf(BOB)).contains(FOREIGN_LEVEL);
        assertThat(overlay.vanished()).containsExactly(BOB);
        assertThat(overlay.snapshot().vanished()).containsEntry(BOB, FOREIGN_LEVEL);
    }

    @Test
    void ourOwnStateIsUntouchedByTheFold() {
        InMemoryVanishStore ours = new InMemoryVanishStore();
        ours.vanish(ALICE, VanishLevel.of(4));
        VanishStore overlay = overlay(ours, Set.of(ALICE, BOB));

        // The other plugin hid Alice too, but our level is the one we resolved from her permissions and it wins.
        assertThat(overlay.levelOf(ALICE)).contains(VanishLevel.of(4));
        assertThat(ours.snapshot().vanished()).containsOnlyKeys(ALICE);
    }

    @Test
    void aForeignHiddenPlayerIsHiddenFromAViewerWhoCannotSeeVanishedPlayers() {
        VanishStore overlay = overlay(new InMemoryVanishStore(), Set.of(BOB));
        VanishState state = overlay.snapshot();

        assertThat(state.canSee(ALICE, BOB, 0)).isFalse();
        assertThat(state.canSee(ALICE, BOB, 1)).isTrue();
    }

    @Test
    void everyWriteGoesToOurOwnStore() {
        InMemoryVanishStore ours = new InMemoryVanishStore();
        VanishStore overlay = overlay(ours, Set.of(BOB));

        overlay.vanish(ALICE, VanishLevel.of(3));
        assertThat(ours.levelOf(ALICE)).contains(VanishLevel.of(3));

        overlay.reveal(ALICE);
        assertThat(ours.isVanished(ALICE)).isFalse();
        assertThat(overlay.isVanished(BOB))
                .as("revealing ours does not reveal theirs")
                .isTrue();
    }

    @Test
    void anUnreachableVanishApiHidesNobodyAndWarnsOnce() {
        MockBukkit.createMockPlugin("SuperVanish");
        server.addPlayer("Alice");
        CountingLogger log = new CountingLogger();
        ForeignVanishStore.ForeignVanishPoll poll = new ForeignVanishStore.ForeignVanishPoll(server, log);
        VanishStore overlay = new ForeignVanishStore(new InMemoryVanishStore(), poll, VanishLevel.DEFAULT);

        assertThatCode(() -> {
                    poll.refresh();
                    poll.refresh();
                })
                .doesNotThrowAnyException();
        assertThat(overlay.snapshot().vanished()).isEmpty();
        assertThat(overlay.vanished()).isEmpty();
        assertThat(log.warns())
                .as("an absent API is reported once, not on every poll")
                .isEqualTo(1);
    }

    @Test
    void theOverlayAnswersFromTheLastReadingRatherThanWalkingTheRoster() {
        // The questions come from render paths and from the async messaging resolution, so nothing here may touch
        // the online roster: only the poll does, on the global region thread the wiring runs it on.
        MockBukkit.createMockPlugin("SuperVanish");
        ForeignVanishStore.ForeignVanishPoll poll =
                new ForeignVanishStore.ForeignVanishPoll(server, new CountingLogger());

        assertThat(poll.hidden()).as("nothing is read until the first poll").isEmpty();
        assertThat(new ForeignVanishStore(new InMemoryVanishStore(), poll, VanishLevel.DEFAULT).isVanished(ALICE))
                .isFalse();
    }

    @Test
    void theOverlayDeclaresNoVanishSdkType() {
        // The structural guarantee that the present-guard, not a classload, gates the reflection: the SDK is named
        // only by string class-name, so loading this on a server without either plugin links zero de.myzelyam class.
        assertThat(declaresPackage(ForeignVanishStore.class, "de.myzelyam")).isFalse();
    }

    private static VanishStore overlay(VanishStore delegate, Set<UUID> hidden) {
        return new ForeignVanishStore(delegate, () -> hidden, FOREIGN_LEVEL);
    }

    private static boolean declaresPackage(Class<?> type, String prefix) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getReturnType().getName().startsWith(prefix)) {
                return true;
            }
            for (Class<?> parameter : method.getParameterTypes()) {
                if (parameter.getName().startsWith(prefix)) {
                    return true;
                }
            }
        }
        for (Field field : type.getDeclaredFields()) {
            if (field.getType().getName().startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** A {@link Logger} that counts warnings, so the warn-once contract can be asserted. */
    private static final class CountingLogger implements Logger {
        private final AtomicInteger warns = new AtomicInteger();

        int warns() {
            return warns.get();
        }

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            warns.incrementAndGet();
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
