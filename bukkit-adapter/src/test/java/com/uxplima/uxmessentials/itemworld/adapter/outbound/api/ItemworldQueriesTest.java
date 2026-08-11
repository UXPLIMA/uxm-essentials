package com.uxplima.uxmessentials.itemworld.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.api.view.UxmPowertool;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.PdcPowertoolStore;
import com.uxplima.uxmessentials.itemworld.domain.PowertoolBinding;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The published powertool read: it reaches into the live inventory on the owner's own thread, it finds a binding
 * wherever in the inventory it sits, and a player who is not here answers empty rather than hanging.
 */
class ItemworldQueriesTest {

    private ServerMock server;
    private PlayerMock alice;
    private PlayerRef who;
    private PdcPowertoolStore store;
    private QueryDoubles.InlineScheduler scheduler;
    private ItemworldQueries queries;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        alice = server.addPlayer("Alice");
        who = new PlayerRef(alice.getUniqueId(), alice.getName());
        store = new PdcPowertoolStore(MockBukkit.createMockPlugin("uxmEssentials"));
        scheduler = new QueryDoubles.InlineScheduler();
        queries = new ItemworldQueries(store, new QueryDoubles.MapLookup().with(who), scheduler);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void anEmptyHandCarriesNoBinding() {
        assertThat(queries.powertoolInHand(who.uuid()).join()).isEmpty();
        assertThat(scheduler.entityCalls()).isOne();
    }

    @Test
    void theBindingOnTheHeldItemIsPublishedWithItsSlotAndCommands() {
        alice.getInventory().setItem(0, bound(Material.DIAMOND_SWORD, "warp shop", "say hello"));
        alice.getInventory().setHeldItemSlot(0);

        UxmPowertool held = queries.powertoolInHand(who.uuid()).join().orElseThrow();

        assertThat(held.slot()).isZero();
        assertThat(held.item()).isEqualTo("minecraft:diamond_sword");
        assertThat(held.commands()).containsExactly("warp shop", "say hello");
    }

    @Test
    void anItemWithNoBindingIsNotAPowertool() {
        alice.getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD));
        alice.getInventory().setHeldItemSlot(0);

        assertThat(queries.powertoolInHand(who.uuid()).join()).isEmpty();
        assertThat(queries.powertools(who.uuid()).join()).isEmpty();
    }

    @Test
    void everyBoundItemInTheInventoryIsFoundInSlotOrder() {
        alice.getInventory().setItem(0, bound(Material.DIAMOND_SWORD, "warp shop"));
        alice.getInventory().setItem(4, new ItemStack(Material.DIRT));
        alice.getInventory().setItem(9, bound(Material.COMPASS, "spawn"));

        List<UxmPowertool> carried = queries.powertools(who.uuid()).join();

        assertThat(carried).extracting(UxmPowertool::slot).containsExactly(0, 9);
        assertThat(carried)
                .extracting(UxmPowertool::item)
                .containsExactly("minecraft:diamond_sword", "minecraft:compass");
    }

    @Test
    void aPlayerWhoLeftIsAnsweredEmptyRatherThanLeftHanging() {
        scheduler.retire(who);

        assertThat(queries.powertoolInHand(who.uuid()).join()).isEmpty();
        assertThat(queries.powertools(who.uuid()).join()).isEmpty();
    }

    @Test
    void aPlayerNobodyKnowsIsStillAskedAboutRatherThanRejected() {
        // The lookup answers nothing for an unknown uuid, so the read hops for a player who is simply not here.
        assertThat(queries.powertools(UUID.randomUUID()).join()).isEmpty();
    }

    private ItemStack bound(Material material, String... commands) {
        ItemStack item = new ItemStack(material);
        store.apply(item, new PowertoolBinding(material.getKey().toString(), List.of(commands)));
        return item;
    }
}
