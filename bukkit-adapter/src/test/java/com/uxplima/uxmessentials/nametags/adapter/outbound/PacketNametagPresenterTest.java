package com.uxplima.uxmessentials.nametags.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.nametags.domain.NametagAppearance;
import com.uxplima.uxmessentials.nametags.domain.NametagConfig;
import com.uxplima.uxmessentials.nametags.domain.NametagFormat;
import com.uxplima.uxmessentials.nametags.domain.NametagVisibility;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.ReaderText;
import com.uxplima.uxmessentials.shared.adapter.outbound.team.PlayerTeamCoordinator;
import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import com.uxplima.uxmlib.nametag.NametagPackets;
import com.uxplima.uxmlib.nametag.NametagRenderer;
import com.uxplima.uxmlib.scheduler.Scheduler;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class PacketNametagPresenterTest {

    private final Player tracking = player("Tracking", Set.of());
    private final Player hidden = player("Hidden", Set.of());
    private final PacketNametagPresenter presenter = new PacketNametagPresenter(
            () -> new NametagConfig(List.of()),
            new NametagRenderer(unused(NametagPackets.class), unused(Scheduler.class)),
            new AnimationRegistry(List.of()),
            (viewer, wearer) -> !viewer.uuid().equals(hidden.getUniqueId()),
            () -> Duration.ofSeconds(1),
            new PlayerTeamCoordinator(),
            () -> true,
            new ReaderText(
                    line -> false,
                    (reader, line) -> line,
                    player -> ReaderText.Affix.NONE,
                    (reader, about) -> UnaryOperator.identity()));

    @Test
    void onlyClientsThatTrackTheWearerAreSentTheNametag() {
        // A client that does not track the wearer cannot seat the lines on them, so they would stay at its feet.
        Player wearer = player("Wearer", Set.of(tracking));

        assertThat(presenter.eligibleViewers(wearer, format(false))).containsExactly(tracking);
    }

    @Test
    void aTrackingViewerTheWearerIsVanishedFromIsLeftOut() {
        Player wearer = player("Wearer", Set.of(tracking, hidden));

        assertThat(presenter.eligibleViewers(wearer, format(true))).containsExactly(tracking);
    }

    private static NametagFormat format(boolean respectVanish) {
        return new NametagFormat(
                "default",
                DisplayCondition.always(),
                0,
                List.of("{player}"),
                NametagAppearance.defaults(),
                new NametagVisibility(DisplayCondition.always(), false, respectVanish, OptionalDouble.of(0)));
    }

    private static Player player(String name, Set<Player> trackedBy) {
        UUID id = UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return proxy(Player.class, (method, args) -> switch (method) {
            case "getUniqueId" -> id;
            case "getName" -> name;
            case "getTrackedBy" -> trackedBy;
            case "isSneaking" -> false;
            default -> throw new UnsupportedOperationException(method);
        });
    }

    private static <T> T unused(Class<T> type) {
        return proxy(type, (method, args) -> {
            throw new UnsupportedOperationException(method);
        });
    }

    private interface Answer {
        @Nullable Object answer(String method, @Nullable Object[] args);
    }

    private static <T> T proxy(Class<T> type, Answer answer) {
        return type.cast(Proxy.newProxyInstance(
                PacketNametagPresenterTest.class.getClassLoader(), new Class<?>[] {type}, (self, method, args) -> {
                    return switch (method.getName()) {
                        case "hashCode" -> System.identityHashCode(self);
                        case "equals" -> self == args[0];
                        case "toString" -> type.getSimpleName();
                        default -> answer.answer(method.getName(), args == null ? new Object[0] : args);
                    };
                }));
    }
}
