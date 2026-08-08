package com.uxplima.uxmessentials.npc.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.npc.application.NpcTestSupport.CapturingSink;
import com.uxplima.uxmessentials.npc.application.NpcTestSupport.FakeNpcRepository;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcError;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.domain.action.ClickAction;
import com.uxplima.uxmessentials.shared.domain.action.ClickActionType;
import com.uxplima.uxmessentials.shared.domain.action.ClickTrigger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NpcActionEditingTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 1, 64, 1);
    private static final NpcName NAME = NpcName.of("guide");

    private FakeNpcRepository repository;
    private CapturingSink sink;
    private InsertNpcAction insert;
    private SetNpcAction set;
    private MoveNpcAction move;
    private PlayerRef actor;

    @BeforeEach
    void setUp() {
        repository = new FakeNpcRepository();
        sink = new CapturingSink();
        Notifier notifier = new Notifier(new NpcTestSupport.KeyMessages(), sink);
        insert = new InsertNpcAction(repository, notifier);
        set = new SetNpcAction(repository, notifier);
        move = new MoveNpcAction(repository, notifier);
        actor = new PlayerRef(UUID.randomUUID(), "Operator");
    }

    private static ClickAction action(String value) {
        return new ClickAction(ClickTrigger.RIGHT_CLICK, ClickActionType.MESSAGE, value);
    }

    private void seed(String... values) {
        Npc npc = Npc.create(NAME, AT, null, Instant.ofEpochMilli(1_000));
        for (String value : values) {
            npc = npc.withActionAdded(action(value));
        }
        repository.save(npc);
    }

    private List<String> values() {
        return repository.find(NAME).orElseThrow().actions().stream()
                .map(ClickAction::value)
                .toList();
    }

    @Test
    void addBeforeShiftsTheTargetDown() {
        seed("a", "b");

        Result<Unit, NpcError> result = insert.insert(actor, NAME, 1, false, action("x"));

        assertThat(result.isOk()).isTrue();
        assertThat(values()).containsExactly("x", "a", "b");
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_ACTION_INSERTED.key());
    }

    @Test
    void addAfterLandsBehindTheTarget() {
        seed("a", "b");

        insert.insert(actor, NAME, 2, true, action("x"));

        assertThat(values()).containsExactly("a", "b", "x");
    }

    @Test
    void insertRejectsAnOutOfRangeIndex() {
        seed("a", "b");

        Result<Unit, NpcError> result = insert.insert(actor, NAME, 3, false, action("x"));

        assertThat(result.errorOrThrow()).isEqualTo(NpcError.ACTION_INDEX_INVALID);
        assertThat(values()).containsExactly("a", "b");
    }

    @Test
    void setReplacesAtThePosition() {
        seed("a", "b");

        set.set(actor, NAME, 1, action("x"));

        assertThat(values()).containsExactly("x", "b");
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_ACTION_SET.key());
    }

    @Test
    void moveUpSwapsWithThePrevious() {
        seed("a", "b", "c");

        move.move(actor, NAME, 3, true);

        assertThat(values()).containsExactly("a", "c", "b");
        assertThat(sink.textFor(actor)).contains(NpcMessageKey.NPC_ACTION_MOVED.key());
    }

    @Test
    void moveDownSwapsWithTheNext() {
        seed("a", "b", "c");

        move.move(actor, NAME, 1, false);

        assertThat(values()).containsExactly("b", "a", "c");
    }

    @Test
    void moveRejectsMovingTheFirstUp() {
        seed("a", "b");

        Result<Unit, NpcError> result = move.move(actor, NAME, 1, true);

        assertThat(result.errorOrThrow()).isEqualTo(NpcError.ACTION_INDEX_INVALID);
        assertThat(values()).containsExactly("a", "b");
    }
}
