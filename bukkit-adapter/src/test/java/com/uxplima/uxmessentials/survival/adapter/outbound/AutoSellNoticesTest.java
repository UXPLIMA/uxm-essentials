package com.uxplima.uxmessentials.survival.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.survival.application.SurvivalConfig.SaleNotice;
import com.uxplima.uxmessentials.survival.application.port.SurvivalSales;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the auto-sell receipt: a paid sale reports what it sold and what it paid, sales inside one
 * window are pooled into a single line, the surface follows the configured mode, and {@code off} says nothing.
 */
class AutoSellNoticesTest {

    private ServerMock server;
    private PlayerMock player;
    private RecordingScheduler scheduler;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("Steve");
        scheduler = new RecordingScheduler();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aSaleIsReportedOnTheActionBarWithTheItemAndTheAmount() {
        AutoSellNotices notices = notices(SaleNotice.ACTIONBAR, 0);

        notices.sold(player, List.of(new ItemStack(Material.IRON_INGOT, 2)), new BigDecimal("16"));

        Component bar = player.nextActionBar();
        assertThat(bar).isNotNull();
        assertThat(plain(bar)).contains("2x").contains("for 16");
        // The item is named by its translation key, so each reader sees it in their own client language.
        assertThat(translationKeys(bar)).containsExactly(Material.IRON_INGOT.translationKey());
    }

    @Test
    void salesInsideOneWindowArePooledIntoASingleReceipt() {
        AutoSellNotices notices = notices(SaleNotice.ACTIONBAR, 3);

        notices.sold(player, List.of(new ItemStack(Material.IRON_INGOT, 2)), new BigDecimal("16"));
        notices.sold(player, List.of(new ItemStack(Material.IRON_INGOT, 1)), new BigDecimal("8"));
        notices.sold(player, List.of(new ItemStack(Material.COAL, 4)), new BigDecimal("8"));

        // One flush for the window, however many blocks were broken inside it.
        assertThat(scheduler.delayed).hasSize(1);
        assertThat(player.nextActionBar()).isNull();
        scheduler.runDelayed();

        Component bar = player.nextActionBar();
        assertThat(bar).isNotNull();
        // The three sales are summed per material and paid out as one figure: 3 ingots and 4 coal for 32.
        assertThat(plain(bar)).contains("3x").contains("4x").contains("for 32");
        assertThat(translationKeys(bar))
                .containsExactly(Material.IRON_INGOT.translationKey(), Material.COAL.translationKey());
        assertThat(player.nextActionBar()).isNull();
    }

    @Test
    void aFurtherSaleAfterTheWindowClosedOpensANewOne() {
        AutoSellNotices notices = notices(SaleNotice.ACTIONBAR, 3);

        notices.sold(player, List.of(new ItemStack(Material.COAL, 1)), new BigDecimal("2"));
        scheduler.runDelayed();
        notices.sold(player, List.of(new ItemStack(Material.COAL, 1)), new BigDecimal("2"));

        // The second sale is not folded into the closed window: it schedules its own flush and reports on its own.
        assertThat(scheduler.delayed).hasSize(1);
        scheduler.runDelayed();
        assertThat(player.nextActionBar()).isNotNull();
        assertThat(player.nextActionBar()).isNotNull();
    }

    @Test
    void chatModeSendsTheReceiptToChatInsteadOfTheBar() {
        AutoSellNotices notices = notices(SaleNotice.CHAT, 0);

        notices.sold(player, List.of(new ItemStack(Material.DIAMOND, 1)), new BigDecimal("80"));

        assertThat(player.nextActionBar()).isNull();
        Component message = player.nextComponentMessage();
        assertThat(message).isNotNull();
        assertThat(plain(message)).contains("1x").contains("for 80");
    }

    @Test
    void offModeReportsNothingAtAll() {
        AutoSellNotices notices = notices(SaleNotice.OFF, 0);

        notices.sold(player, List.of(new ItemStack(Material.DIAMOND, 1)), new BigDecimal("80"));

        assertThat(scheduler.delayed).isEmpty();
        assertThat(player.nextActionBar()).isNull();
        assertThat(player.nextComponentMessage()).isNull();
    }

    private AutoSellNotices notices(SaleNotice mode, int intervalSeconds) {
        return new AutoSellNotices(server, scheduler, new CatalogMessages(), new PlainSales(), mode, intervalSeconds);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** Every translation key the rendered receipt carries, in order, so the item naming is asserted, not guessed. */
    private static List<String> translationKeys(Component component) {
        List<String> keys = new ArrayList<>();
        if (component instanceof TranslatableComponent translatable) {
            keys.add(translatable.key());
        }
        component.children().forEach(child -> keys.addAll(translationKeys(child)));
        return keys;
    }

    /** The shipped receipt templates, stripped of styling tags so the assertions read the text alone. */
    private static final class CatalogMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            String template =
                    switch (key.key()) {
                        case "survival.autosell-sold-separator" -> ", ";
                        case "survival.autosell-sold-entry" -> "{amount}x {item}";
                        case "survival.autosell-sold", "survival.autosell-sold-bar" -> "Sold {items} for {amount}";
                        default -> key.key();
                    };
            for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
                template = template.replace("{" + placeholder.getKey() + "}", placeholder.getValue());
            }
            return template;
        }
    }

    /** An economy seam that only formats: the receipt never credits, it reports a credit that already happened. */
    private static final class PlainSales implements SurvivalSales {
        @Override
        public boolean credit(PlayerRef who, BigDecimal amount) {
            return true;
        }
    }

    /** Holds the pooled flush so a test can close the window on purpose instead of waiting out the delay. */
    private static final class RecordingScheduler implements Scheduler {
        private final List<Runnable> delayed = new ArrayList<>();

        void runDelayed() {
            List<Runnable> due = List.copyOf(delayed);
            delayed.clear();
            due.forEach(Runnable::run);
        }

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
            delayed.add(task);
        }
    }
}
