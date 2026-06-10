package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.fakes.FakeModerationRepository;
import com.uxplima.uxmessentials.moderation.fakes.FakeSanctions;
import com.uxplima.uxmessentials.moderation.fakes.ModerationFakes;
import com.uxplima.uxmessentials.moderation.fakes.RecordingModerationAudit;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code /togglejail} flips a target's jail in a single command: a free target is confined (to the named jail,
 * or the first configured jail when omitted) and a jailed target is released, each by delegating to the real
 * {@link Jail} / {@link Unjail} use cases rather than duplicating jail logic.
 */
class ToggleJailTest {

    private static final Instant T0 = Instant.parse("2026-05-31T00:00:00Z");
    private static final PlayerRef ADMIN = new PlayerRef(UUID.randomUUID(), "admin");
    private static final PlayerRef TARGET = new PlayerRef(UUID.randomUUID(), "griefer");

    private FakeModerationRepository repository;
    private FakeSanctions sanctions;
    private ToggleJail toggleJail;

    @BeforeEach
    void setUp() {
        repository = new FakeModerationRepository();
        RecordingModerationAudit audit = new RecordingModerationAudit();
        ModerationFakes.RecordingEvents events = new ModerationFakes.RecordingEvents();
        Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
        ModerationGuard guard = new ModerationGuard(ModerationFakes.exempt());
        sanctions = new FakeSanctions(TARGET);
        var jails = ModerationFakes.jails(Set.of("cells", "block-a"), Set.of());
        Jail jail = new Jail(repository, jails, sanctions, guard, ModerationFakes.notifier(), audit, events, clock);
        Unjail unjail = new Unjail(repository, sanctions, ModerationFakes.notifier(), audit, events, clock);
        toggleJail = new ToggleJail(repository, jails, jail, unjail);
    }

    @Test
    void aFreeTargetIsJailedInTheNamedJail() {
        toggleJail.toggle(ADMIN, TARGET, "cells", Optional.of("grief"));

        assertThat(repository.loadJail(TARGET)).isInstanceOf(JailState.Active.class);
        assertThat(((JailState.Active) repository.loadJail(TARGET)).jail()).isEqualTo("cells");
        assertThat(sanctions.jailedInto).containsExactly("cells");
    }

    @Test
    void aFreeTargetWithNoJailNameLandsInTheFirstConfiguredJail() {
        toggleJail.toggle(ADMIN, TARGET, "", Optional.empty());

        assertThat(repository.loadJail(TARGET)).isInstanceOf(JailState.Active.class);
        // ModerationFakes.jails sorts the names, so block-a precedes cells.
        assertThat(((JailState.Active) repository.loadJail(TARGET)).jail()).isEqualTo("block-a");
    }

    @Test
    void aJailedTargetIsReleased() {
        repository.saveJail(TARGET, JailState.permanent("cells", Issuer.of(ADMIN), Optional.of("grief"), T0));

        toggleJail.toggle(ADMIN, TARGET, "", Optional.empty());

        assertThat(repository.loadJail(TARGET)).isInstanceOf(JailState.None.class);
        assertThat(sanctions.released).containsExactly(TARGET);
    }
}
