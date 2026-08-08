package com.uxplima.uxmessentials.holograms.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.holograms.application.HologramTestSupport.CapturingSink;
import com.uxplima.uxmessentials.holograms.application.HologramTestSupport.FakeHologramRepository;
import com.uxplima.uxmessentials.holograms.application.HologramTestSupport.RecordingView;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramError;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ManageHologramBlacklistTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private FakeHologramRepository repository;
    private RecordingView view;
    private CapturingSink sink;
    private ManageHologramBlacklist blacklist;
    private PlayerRef actor;
    private PlayerRef target;

    @BeforeEach
    void setUp() {
        repository = new FakeHologramRepository();
        view = new RecordingView();
        sink = new CapturingSink();
        blacklist = new ManageHologramBlacklist(
                repository, view, new Notifier(new HologramTestSupport.KeyMessages(), sink));
        actor = new PlayerRef(UUID.randomUUID(), "Operator");
        target = new PlayerRef(UUID.randomUUID(), "Banned");
    }

    @Test
    void blacklistingHidesPersistsAndReRenders() {
        repository.save(hologram("spawn"));

        Result<Unit, HologramError> result = blacklist.blacklist(actor, HologramName.of("spawn"), target);

        assertThat(result.isOk()).isTrue();
        assertThat(repository.blacklisted(HologramName.of("spawn"))).containsExactly(target.uuid());
        assertThat(view.rendered).hasSize(1);
        assertThat(sink.textFor(actor)).contains(HologramsMessageKey.HOLOGRAM_BLACKLISTED.key());
    }

    @Test
    void unblacklistingRemovesAndReRenders() {
        repository.save(hologram("spawn"));
        repository.addToBlacklist(HologramName.of("spawn"), target.uuid());

        Result<Unit, HologramError> result = blacklist.unblacklist(actor, HologramName.of("spawn"), target);

        assertThat(result.isOk()).isTrue();
        assertThat(repository.blacklisted(HologramName.of("spawn"))).isEmpty();
        assertThat(view.rendered).hasSize(1);
        assertThat(sink.textFor(actor)).contains(HologramsMessageKey.HOLOGRAM_UNBLACKLISTED.key());
    }

    @Test
    void blacklistingAnAlreadyListedPlayerIsANoOp() {
        repository.save(hologram("spawn"));
        repository.addToBlacklist(HologramName.of("spawn"), target.uuid());

        Result<Unit, HologramError> result = blacklist.blacklist(actor, HologramName.of("spawn"), target);

        assertThat(result.isOk()).isTrue();
        assertThat(view.rendered).isEmpty();
        assertThat(sink.textFor(actor)).contains(HologramsMessageKey.HOLOGRAM_ALREADY_BLACKLISTED.key());
    }

    @Test
    void unblacklistingAPlayerNotListedIsANoOp() {
        repository.save(hologram("spawn"));

        Result<Unit, HologramError> result = blacklist.unblacklist(actor, HologramName.of("spawn"), target);

        assertThat(result.isOk()).isTrue();
        assertThat(view.rendered).isEmpty();
        assertThat(sink.textFor(actor)).contains(HologramsMessageKey.HOLOGRAM_NOT_BLACKLISTED.key());
    }

    @Test
    void rejectsAnUnknownName() {
        Result<Unit, HologramError> result = blacklist.blacklist(actor, HologramName.of("ghost"), target);

        assertThat(result.errorOrThrow()).isEqualTo(HologramError.NOT_FOUND);
        assertThat(view.rendered).isEmpty();
        assertThat(sink.textFor(actor)).contains(HologramsMessageKey.HOLOGRAM_NOT_FOUND.key());
    }

    private Hologram hologram(String name) {
        return Hologram.create(
                HologramName.of(name), Position.of(WORLD, 0, 64, 0), List.of(new HologramLine("line")), Instant.EPOCH);
    }
}
