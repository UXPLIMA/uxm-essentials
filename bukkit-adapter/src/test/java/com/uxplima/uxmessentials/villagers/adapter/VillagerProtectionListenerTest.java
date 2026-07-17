package com.uxplima.uxmessentials.villagers.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.EntityTransformEvent.TransformReason;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.uxplima.uxmessentials.villagers.adapter.inbound.listener.VillagerProtectionListener;
import com.uxplima.uxmessentials.villagers.adapter.outbound.PdcVillagerFlags;
import com.uxplima.uxmessentials.villagers.domain.VillagerProtectionPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * MockBukkit coverage of the villager saver: a protected villager survives a zombie infection, a lightning strike, a
 * suffocation blow, and is kept loaded so it never despawns; an unprotected villager suffers all of them untouched; and
 * each {@code protect.from-*} gate turned off is a no-op for its own threat.
 */
class VillagerProtectionListenerTest {

    private static final VillagerProtectionPolicy ALL_ON =
            new VillagerProtectionPolicy(true, false, true, true, true, true);

    private ServerMock server;
    private WorldMock world;
    private Villager villager;
    private PdcVillagerFlags flags;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        villager = (Villager) world.spawnEntity(new Location(world, 0, 64, 0), EntityType.VILLAGER);
        flags = new PdcVillagerFlags();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aProtectedVillagerSurvivesAZombieInfection() {
        flags.setProtected(villager, true);
        EntityTransformEvent event = infection();

        listener(ALL_ON).onTransform(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void anUnprotectedVillagerIsStillInfected() {
        EntityTransformEvent event = infection();

        listener(ALL_ON).onTransform(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void theFromZombiesGateOffIsANoOp() {
        flags.setProtected(villager, true);
        EntityTransformEvent event = infection();

        listener(new VillagerProtectionPolicy(true, false, false, true, true, true))
                .onTransform(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void aCureIsNeverCancelled() {
        flags.setProtected(villager, true);
        EntityTransformEvent event = new EntityTransformEvent(villager, List.of(villager), TransformReason.CURED);

        listener(ALL_ON).onTransform(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void aProtectedVillagerSurvivesLightning() {
        flags.setProtected(villager, true);
        EntityDamageEvent event = damage(DamageCause.LIGHTNING, DamageType.LIGHTNING_BOLT);

        listener(ALL_ON).onDamage(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void theFromLightningGateOffIsANoOp() {
        flags.setProtected(villager, true);
        EntityDamageEvent event = damage(DamageCause.LIGHTNING, DamageType.LIGHTNING_BOLT);

        listener(new VillagerProtectionPolicy(true, false, true, false, true, true))
                .onDamage(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void aProtectedVillagerSurvivesSuffocation() {
        flags.setProtected(villager, true);
        EntityDamageEvent event = damage(DamageCause.SUFFOCATION, DamageType.IN_WALL);

        listener(ALL_ON).onDamage(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void anUnprotectedVillagerTakesDamage() {
        EntityDamageEvent event = damage(DamageCause.SUFFOCATION, DamageType.IN_WALL);

        listener(ALL_ON).onDamage(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void theFromDamageGateOffLeavesGeneralDamageThrough() {
        flags.setProtected(villager, true);
        EntityDamageEvent event = damage(DamageCause.SUFFOCATION, DamageType.IN_WALL);

        listener(new VillagerProtectionPolicy(true, false, true, true, false, true))
                .onDamage(event);

        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void aProtectedVillagerIsKeptLoadedSoItNeverDespawns() {
        flags.setProtected(villager, true);
        villager.setPersistent(false);

        listener(ALL_ON).onEntityAdd(new EntityAddToWorldEvent(villager, world));

        assertThat(villager.isPersistent()).isTrue();
    }

    @Test
    void anUnprotectedVillagerIsLeftToDespawn() {
        villager.setPersistent(false);

        listener(ALL_ON).onEntityAdd(new EntityAddToWorldEvent(villager, world));

        assertThat(villager.isPersistent()).isFalse();
    }

    @Test
    void theNoDespawnGateOffLeavesPersistenceAlone() {
        flags.setProtected(villager, true);
        villager.setPersistent(false);

        listener(new VillagerProtectionPolicy(true, false, true, true, true, false))
                .onEntityAdd(new EntityAddToWorldEvent(villager, world));

        assertThat(villager.isPersistent()).isFalse();
    }

    @Test
    void aDisabledFeatureShieldsNothing() {
        flags.setProtected(villager, true);
        EntityDamageEvent event = damage(DamageCause.LIGHTNING, DamageType.LIGHTNING_BOLT);

        listener(new VillagerProtectionPolicy(false, true, true, true, true, true))
                .onDamage(event);

        assertThat(event.isCancelled()).isFalse();
    }

    private VillagerProtectionListener listener(VillagerProtectionPolicy policy) {
        return new VillagerProtectionListener(policy, flags);
    }

    private EntityTransformEvent infection() {
        Entity zombie = world.spawnEntity(new Location(world, 0, 64, 0), EntityType.ZOMBIE_VILLAGER);
        return new EntityTransformEvent(villager, List.of(zombie), TransformReason.INFECTION);
    }

    private EntityDamageEvent damage(DamageCause cause, DamageType type) {
        return new EntityDamageEvent(villager, cause, DamageSource.builder(type).build(), 100.0);
    }
}
