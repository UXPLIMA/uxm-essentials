package com.uxplima.uxmessentials.npc.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.npc.application.CreateNpc;
import com.uxplima.uxmessentials.npc.application.DeleteNpc;
import com.uxplima.uxmessentials.npc.application.MoveNpcTo;
import com.uxplima.uxmessentials.npc.application.NpcQuota;
import com.uxplima.uxmessentials.npc.application.SetNpcClickCommand;
import com.uxplima.uxmessentials.npc.application.SetNpcDisplayName;
import com.uxplima.uxmessentials.npc.application.SetNpcSkin;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.shared.action.ActionDoubles;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published NPC writes: they run the same use cases {@code /npc} runs, so the creation limit still bites, the
 * actor still owns what they put up, and the renderer is still told about every change.
 */
class NpcActionsTest {

    private static final PlayerRef ACTOR = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final UxmLocation SOMEWHERE = new UxmLocation("world", 10, 64, -20, 90f, 0f);

    private NpcApiSupport.FakeRepository repository;
    private NpcApiSupport.RecordingView view;
    private ActionDoubles.InlineScheduler scheduler;
    private NpcActions actions;

    @BeforeEach
    void setUp() {
        repository = new NpcApiSupport.FakeRepository();
        view = new NpcApiSupport.RecordingView();
        scheduler = new ActionDoubles.InlineScheduler();
        actions = actions(2);
    }

    @Test
    void createStoresTheNpcOffTheCallingThreadAndShowsIt() {
        UxmOutcome outcome =
                actions.create(ACTOR.uuid(), "shopkeeper", SOMEWHERE).join();

        assertThat(outcome.succeeded()).isTrue();
        assertThat(stored("shopkeeper").owner()).isEqualTo(ACTOR.uuid());
        assertThat(stored("shopkeeper").location().yaw()).isEqualTo(90f);
        assertThat(view.renders()).isOne();
        assertThat(scheduler.asyncCalls()).isOne();
        assertThat(scheduler.entityCalls()).isZero();
    }

    @Test
    void aWorldNobodyLoadedIsAnsweredNotScheduled() {
        UxmOutcome outcome = actions.create(ACTOR.uuid(), "shopkeeper", new UxmLocation("nether", 0, 64, 0))
                .join();

        assertThat(outcome.failureOrThrow().code()).isEqualTo(UxmFailure.NOT_FOUND);
        assertThat(scheduler.asyncCalls()).isZero();
    }

    @Test
    void theSameNameTwiceIsAlreadyExists() {
        actions.create(ACTOR.uuid(), "shopkeeper", SOMEWHERE).join();

        UxmOutcome again = actions.create(ACTOR.uuid(), "shopkeeper", SOMEWHERE).join();

        assertThat(again.failureOrThrow().code()).isEqualTo(UxmFailure.ALREADY_EXISTS);
    }

    @Test
    void theCreationLimitStillBitesOverTheApi() {
        actions.create(ACTOR.uuid(), "one", SOMEWHERE).join();
        actions.create(ACTOR.uuid(), "two", SOMEWHERE).join();

        UxmOutcome third = actions.create(ACTOR.uuid(), "three", SOMEWHERE).join();

        assertThat(third.failureOrThrow().code()).isEqualTo(UxmFailure.REFUSED);
        assertThat(repository.all()).hasSize(2);
    }

    @Test
    void deleteRemovesTheRowAndTakesTheFakePlayerAway() {
        actions.create(ACTOR.uuid(), "shopkeeper", SOMEWHERE).join();

        assertThat(actions.delete(ACTOR.uuid(), "shopkeeper").join().succeeded())
                .isTrue();
        assertThat(repository.all()).isEmpty();
        assertThat(view.despawns()).isOne();
    }

    @Test
    void moveWithinTheirOwnWorldIsAppliedAndAcrossWorldsIsRefused() {
        actions.create(ACTOR.uuid(), "shopkeeper", SOMEWHERE).join();

        assertThat(actions.move(ACTOR.uuid(), "shopkeeper", new UxmLocation("world", 1, 2, 3))
                        .join()
                        .succeeded())
                .isTrue();
        assertThat(stored("shopkeeper").location().x()).isEqualTo(1);

        UxmOutcome elsewhere = actions.move(ACTOR.uuid(), "shopkeeper", new UxmLocation("nether", 0, 64, 0))
                .join();
        assertThat(elsewhere.failureOrThrow().code()).isEqualTo(UxmFailure.REFUSED);
        assertThat(stored("shopkeeper").location().x()).isEqualTo(1);
    }

    @Test
    void aSkinIsNamedByItsAccountAndAnUnknownOneIsNotFound() {
        actions.create(ACTOR.uuid(), "shopkeeper", SOMEWHERE).join();

        assertThat(actions.setSkin(ACTOR.uuid(), "shopkeeper", NpcApiSupport.OneKnownSkin.OWNER)
                        .join()
                        .succeeded())
                .isTrue();
        assertThat(stored("shopkeeper").hasSkin()).isTrue();

        UxmOutcome nobody =
                actions.setSkin(ACTOR.uuid(), "shopkeeper", "SomebodyElse").join();
        assertThat(nobody.failureOrThrow().code()).isEqualTo(UxmFailure.NOT_FOUND);

        assertThat(actions.clearSkin(ACTOR.uuid(), "shopkeeper").join().succeeded())
                .isTrue();
        assertThat(stored("shopkeeper").hasSkin()).isFalse();
    }

    @Test
    void theThreeDisplayNameStatesAreThreeDifferentStoredValues() {
        actions.create(ACTOR.uuid(), "shopkeeper", SOMEWHERE).join();

        actions.setDisplayName(ACTOR.uuid(), "shopkeeper", "<gold>Shop").join();
        assertThat(stored("shopkeeper").displayName()).isEqualTo("<gold>Shop");

        // The blank sentinel means "show nothing", which is not the same as going back to showing the id.
        actions.hideDisplayName(ACTOR.uuid(), "shopkeeper").join();
        assertThat(stored("shopkeeper").displayNameHidden()).isTrue();

        actions.clearDisplayName(ACTOR.uuid(), "shopkeeper").join();
        assertThat(stored("shopkeeper").displayName()).isNull();
        assertThat(stored("shopkeeper").displayNameHidden()).isFalse();
    }

    @Test
    void aClickCommandIsStoredWithoutItsSlashAndCanBeUnbound() {
        actions.create(ACTOR.uuid(), "shopkeeper", SOMEWHERE).join();

        actions.setClickCommand(ACTOR.uuid(), "shopkeeper", "/warp shop").join();
        assertThat(stored("shopkeeper").clickCommand()).isEqualTo("warp shop");

        actions.clearClickCommand(ACTOR.uuid(), "shopkeeper").join();
        assertThat(stored("shopkeeper").clickCommand()).isNull();
    }

    @Test
    void aNameNoNpcCouldHaveIsRefusedBeforeAnythingIsRead() {
        UxmOutcome outcome =
                actions.delete(ACTOR.uuid(), "x".repeat(NpcName.MAX_LENGTH + 1)).join();

        assertThat(outcome.failureOrThrow().code()).isEqualTo(UxmFailure.REFUSED);
        assertThat(scheduler.asyncCalls()).isZero();
    }

    @Test
    void aBlankLabelIsAProgrammingErrorRatherThanARefusal() {
        assertThatThrownBy(() -> actions.setDisplayName(ACTOR.uuid(), "shopkeeper", "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Npc stored(String name) {
        return repository.find(NpcName.of(name)).orElseThrow();
    }

    private NpcActions actions(int limit) {
        Clock clock = Clock.fixed(Instant.parse("2026-08-11T12:00:00Z"), ZoneOffset.UTC);
        var notifier = ActionDoubles.silentNotifier();
        var events = new ActionDoubles.RecordingEvents();
        NpcQuota quota = new NpcQuota(new ConfiguredLimit(), limit);
        return new NpcActions(
                repository,
                new CreateNpc(repository, view, notifier, events, clock, quota),
                new DeleteNpc(repository, view, notifier, events),
                new MoveNpcTo(repository, view, notifier, events),
                new SetNpcSkin(repository, view, notifier),
                new SetNpcDisplayName(repository, view, notifier),
                new SetNpcClickCommand(repository, notifier),
                new NpcApiSupport.OneKnownSkin(),
                new QueryDoubles.MapLookup().with(ACTOR),
                new ActionDoubles.NamedWorlds().with(WORLD),
                scheduler);
    }

    /** Nobody holds a numbered node here, so the configured default is the limit that applies. */
    private static final class ConfiguredLimit implements Permissions {

        @Override
        public boolean has(PlayerRef who, String node) {
            return false;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }
}
