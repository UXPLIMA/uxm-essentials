package com.uxplima.uxmessentials.holograms.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.holograms.adapter.inbound.listener.HologramClickHandler.Primary;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.holograms.domain.HologramPage;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.domain.action.ClickAction;
import com.uxplima.uxmessentials.shared.domain.action.ClickActionType;
import com.uxplima.uxmessentials.shared.domain.action.ClickTrigger;
import org.junit.jupiter.api.Test;

/**
 * Pins what a click on a hologram's box does. {@link HologramClickHandler#primaryBehaviour} is the single-click
 * decision that runs before the action chain: the command, the page, or nothing, and the command is right-click
 * only. The chain itself is engine-tested in the shared module; here it is only seen to run, after the primary.
 */
class HologramClickHandlerTest {

    @Test
    void aRightClickCommandTakesPrecedence() {
        assertThat(HologramClickHandler.primaryBehaviour("warp spawn", true, false))
                .isEqualTo(Primary.RUN_COMMAND);
        assertThat(HologramClickHandler.primaryBehaviour("warp spawn", false, false))
                .isEqualTo(Primary.RUN_COMMAND);
    }

    @Test
    void aLeftClickNeverRunsTheLegacyCommand() {
        // The single click command is the legacy right-click binding (as it is for an NPC); a left-click falls
        // through to the page cycle when paged, otherwise to nothing: the chain's left actions run regardless.
        assertThat(HologramClickHandler.primaryBehaviour("warp spawn", true, true))
                .isEqualTo(Primary.CYCLE_PAGE);
        assertThat(HologramClickHandler.primaryBehaviour("warp spawn", false, true))
                .isEqualTo(Primary.NONE);
    }

    @Test
    void aPagedHologramWithNoCommandCyclesOnEitherClick() {
        assertThat(HologramClickHandler.primaryBehaviour(null, true, false)).isEqualTo(Primary.CYCLE_PAGE);
        assertThat(HologramClickHandler.primaryBehaviour("   ", true, true)).isEqualTo(Primary.CYCLE_PAGE);
    }

    @Test
    void aBlankCommandAndNoPagesIsNone() {
        assertThat(HologramClickHandler.primaryBehaviour(null, false, false)).isEqualTo(Primary.NONE);
        assertThat(HologramClickHandler.primaryBehaviour("", false, true)).isEqualTo(Primary.NONE);
    }

    // -- a click ------------------------------------------------------------------------------------------------

    private final List<String> did = new ArrayList<>();
    private final AtomicLong now = new AtomicLong(10_000);
    private final Map<String, Hologram> stored = new java.util.HashMap<>();
    private final HologramClickHandler handler = new HologramClickHandler(
            name -> Optional.ofNullable(stored.get(name.value())),
            (viewer, name) -> did.add("page " + name.value()),
            (viewer, actions, attack) -> did.add("actions " + actions.size() + (attack ? " left" : " right")),
            now::get);

    @Test
    void aRightClickRunsTheCommandAsTheClickingPlayerThenTheActions() {
        stored.put("portal", text("portal").withClickCommand("warp spawn").withActionAdded(message()));

        handler.clicked(player(), HologramName.of("portal"), false);

        assertThat(did).containsExactly("command warp spawn", "actions 1 right");
    }

    @Test
    void aClickOnAPagedHologramTurnsThePage() {
        stored.put("guide", text("guide").withPageAppended(HologramPage.of(List.of(new HologramLine("two")))));

        handler.clicked(player(), HologramName.of("guide"), true);

        assertThat(did).containsExactly("page guide");
    }

    @Test
    void aSecondClickInsideTheCooldownDoesNothing() {
        stored.put("shop", text("shop").withActionAdded(message()));
        Player player = player();

        handler.clicked(player, HologramName.of("shop"), false);
        now.addAndGet(100);
        handler.clicked(player, HologramName.of("shop"), false);
        now.addAndGet(300);
        handler.clicked(player, HologramName.of("shop"), false);

        assertThat(did).containsExactly("actions 1 right", "actions 1 right");
    }

    /** The box may outlive its hologram by a frame; a click on a hologram that is gone does nothing. */
    @Test
    void aClickOnAHologramThatIsGoneDoesNothing() {
        handler.clicked(player(), HologramName.of("deleted"), false);

        assertThat(did).isEmpty();
    }

    private static Hologram text(String name) {
        return Hologram.create(
                HologramName.of(name),
                new Position(new WorldRef(UUID.randomUUID(), "world"), 0, 64, 0, 0f, 0f),
                List.of(new HologramLine("hello")),
                Instant.EPOCH);
    }

    private static ClickAction message() {
        return new ClickAction(ClickTrigger.ANY, ClickActionType.MESSAGE, "hi");
    }

    private Player player() {
        UUID id = UUID.randomUUID();
        return (Player) java.lang.reflect.Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class<?>[] {Player.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getUniqueId" -> {
                            return id;
                        }
                        case "performCommand" -> {
                            did.add("command " + args[0]);
                            return true;
                        }
                        case "hashCode" -> {
                            return id.hashCode();
                        }
                        case "equals" -> {
                            return proxy == args[0];
                        }
                        default -> {
                            return null;
                        }
                    }
                });
    }
}
