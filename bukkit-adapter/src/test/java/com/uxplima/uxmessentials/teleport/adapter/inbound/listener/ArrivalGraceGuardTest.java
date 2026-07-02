package com.uxplima.uxmessentials.teleport.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.potion.PotionEffectType;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.domain.ArrivalGraceSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the {@link ArrivalGraceGuard}: a successful {@code /rtp} arrival applies the
 * Resistance and Slow-Falling effects and opens a no-fall-damage window that cancels fall damage while it is
 * open and stops once it has elapsed. A disabled grace applies nothing.
 */
class ArrivalGraceGuardTest {

    private static final ArrivalGraceSettings ON = new ArrivalGraceSettings(5, true, true, true);

    private ServerMock server;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        clock = new MutableClock(Instant.EPOCH);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void appliesResistanceAndSlowFallingOnArrival() {
        PlayerMock player = server.addPlayer();
        ArrivalGraceGuard guard = guard(ON);

        guard.applyOnArrival(ref(player));

        assertThat(player.hasPotionEffect(PotionEffectType.RESISTANCE)).isTrue();
        assertThat(player.hasPotionEffect(PotionEffectType.SLOW_FALLING)).isTrue();
        assertThat(guard.shieldsFallDamage(player.getUniqueId())).isTrue();
    }

    @Test
    void cancelsFallDamageDuringTheWindowAndStopsAfterIt() {
        PlayerMock player = server.addPlayer();
        ArrivalGraceGuard guard = guard(ON);
        guard.applyOnArrival(ref(player));

        EntityDamageEvent during = fallDamage(player);
        guard.onDamage(during);
        assertThat(during.isCancelled()).isTrue();

        clock.advance(Duration.ofSeconds(6)); // past the 5-second window
        EntityDamageEvent after = fallDamage(player);
        guard.onDamage(after);
        assertThat(after.isCancelled()).isFalse();
    }

    @Test
    void appliesNothingWhenTheGraceIsDisabled() {
        PlayerMock player = server.addPlayer();
        ArrivalGraceGuard guard = guard(new ArrivalGraceSettings(0, true, true, true));

        guard.applyOnArrival(ref(player));

        assertThat(player.getActivePotionEffects()).isEmpty();
        assertThat(guard.shieldsFallDamage(player.getUniqueId())).isFalse();
    }

    @Test
    void doesNotCancelNonFallDamage() {
        PlayerMock player = server.addPlayer();
        ArrivalGraceGuard guard = guard(ON);
        guard.applyOnArrival(ref(player));

        EntityDamageEvent lava = new EntityDamageEvent(
                player, DamageCause.LAVA, DamageSource.builder(DamageType.LAVA).build(), 4.0);
        guard.onDamage(lava);

        assertThat(lava.isCancelled()).isFalse(); // the no-fall-damage guard covers falls only
    }

    private ArrivalGraceGuard guard(ArrivalGraceSettings settings) {
        return new ArrivalGraceGuard(server, new InlineScheduler(), () -> settings, clock);
    }

    private static EntityDamageEvent fallDamage(PlayerMock player) {
        return new EntityDamageEvent(
                player, DamageCause.FALL, DamageSource.builder(DamageType.FALL).build(), 6.0);
    }

    private static PlayerRef ref(PlayerMock player) {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    /** A clock the test advances to cross the grace window boundary. */
    private static final class MutableClock extends java.time.Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            this.now = now.plus(by);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public java.time.Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final class InlineScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}
