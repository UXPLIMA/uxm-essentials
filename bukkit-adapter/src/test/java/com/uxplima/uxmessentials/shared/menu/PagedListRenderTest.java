package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.bedrock.BedrockDetector;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.bedrock.BedrockScreen;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PagedListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PageRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PagedResult;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PinnedEntry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The engine serving a paged list source: {@link Menus} asks the source for one page and the renderer draws that page
 * without re-slicing it, while a static page indicator still reads the whole corpus's page count from the total the
 * source reported. Drives the façade end to end through a synchronous scheduler so the async resolve and the entity
 * render both run inline, then reads back the window the viewer is left looking at.
 */
class PagedListRenderTest {

    // 45 list slots (0-44) with a page indicator below them; no page-size, so the request size derives from the slots.
    private static final String PAGED_HOCON = """
            rows = 6
            items {
              info { slot = 45, material = PAPER, name = "%page%/%max_page%" }
              warps {
                slots = ["0-44"]
                list {
                  source = "pw:browse"
                  sorts  = ["RATING", "VISITS"]
                  template { material = STONE, name = "%v%" }
                }
              }
            }
            """;

    private static final String PLAIN_HOCON = """
            rows = 1
            items {
              things {
                slots = ["0-2"],
                list { source = "pw:plain", template { material = STONE, name = "%v%" } }
              }
            }
            """;

    private ServerMock server;
    private PlayerMock player;
    private MenuRenderer renderer;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer("Viewer");
        GuiText guiText = new GuiText(new KeyMessages());
        PlaceholderRegistry placeholders = new PlaceholderRegistry();
        placeholders.register("v", ctx -> {
            Object entry = ctx.entry().orElseThrow();
            return entry instanceof NamedPin pin ? pin.name() : (String) entry;
        });
        placeholders.register("page", ctx -> String.valueOf(ctx.page() + 1));
        placeholders.register("max_page", ctx -> String.valueOf(ctx.pageCount()));
        ItemRenderer itemRenderer = new ItemRenderer(guiText, placeholders);
        renderer = new MenuRenderer(itemRenderer, new ConditionRegistry());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void asksThePagedSourceOnceForTheDefaultRequest() {
        FakePagedSource source = new FakePagedSource(request -> PagedResult.of(List.<Object>of("a", "b", "c"), 3));
        Menus menus = pagedMenus(source);

        open(menus);

        assertThat(source.calls).isEqualTo(1);
        PageRequest request = source.lastRequest;
        assertThat(request).isNotNull();
        assertThat(request.page()).isZero();
        assertThat(request.sort()).isEqualTo("RATING");
        assertThat(request.size()).isEqualTo(45);
        assertThat(request.filters()).isEmpty();
    }

    @Test
    void reportsTheCorpusPageCountWhileRenderingOnlyOnePage() {
        List<Object> firstPage = rows("r", 45);
        FakePagedSource source = new FakePagedSource(request -> PagedResult.of(firstPage, 46));
        Menus menus = pagedMenus(source);

        Inventory inv = openTop(menus);

        assertThat(plainName(inv, 45)).isEqualTo("1/2");
        assertThat(filledContentSlots(inv)).isEqualTo(45);
    }

    @Test
    void anEmptyCorpusRendersOneEmptyPage() {
        FakePagedSource source = new FakePagedSource(request -> PagedResult.of(List.of(), 0));
        Menus menus = pagedMenus(source);

        Inventory inv = openTop(menus);

        assertThat(plainName(inv, 45)).isEqualTo("1/1");
        assertThat(filledContentSlots(inv)).isZero();
    }

    @Test
    void anOverflowingSourceLogsOnceAndRendersOnlyOnePage() {
        List<Object> overflow = rows("r", 50);
        FakePagedSource source = new FakePagedSource(request -> PagedResult.of(overflow, 50));
        Menus menus = pagedMenus(source);

        List<LogRecord> logged = captureMenusLog(() -> {
            Inventory inv = openTop(menus);
            assertThat(filledContentSlots(inv)).isEqualTo(45);
            assertThat(plainName(inv, 45)).isEqualTo("1/2");
        });

        long overflows = logged.stream()
                .filter(record -> record.getMessage().contains("event=paged_source_overflowed"))
                .count();
        assertThat(overflows).isEqualTo(1);
        assertThat(logged.stream().map(LogRecord::getMessage))
                .anyMatch(message ->
                        message.contains("id=pw:browse") && message.contains("size=45") && message.contains("rows=50"));
    }

    @Test
    void aPinnedRowLandsInItsFixedSlot() {
        FakePagedSource source = new FakePagedSource(
                request -> new PagedResult<>(List.<Object>of("a", "b"), 2, List.<Object>of(new NamedPin("PIN", 44))));
        Menus menus = pagedMenus(source);

        Inventory inv = openTop(menus);

        assertThat(plainName(inv, 44)).isEqualTo("PIN");
        assertThat(plainName(inv, 0)).isEqualTo("a");
        assertThat(plainName(inv, 1)).isEqualTo("b");
    }

    @Test
    void aPlainSourceRendersThroughTheOldPathWithoutConsultingThePagedRegistry() {
        ListSourceRegistry lists = new ListSourceRegistry();
        lists.register("pw:plain", ctx -> List.of("x", "y", "z"));
        PagedListSourceRegistry paged = mock(PagedListSourceRegistry.class);
        Menus menus = new Menus(
                renderer,
                new SyncScheduler(),
                lists,
                null,
                null,
                null,
                null,
                BedrockDetector.NONE,
                BedrockScreen.NONE,
                paged);
        menus.registerSpec("test", new MenuSpecLoader().parse(PLAIN_HOCON));

        open(menus);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(plainName(inv, 0)).isEqualTo("x");
        assertThat(plainName(inv, 1)).isEqualTo("y");
        assertThat(plainName(inv, 2)).isEqualTo("z");
        verifyNoInteractions(paged);
    }

    private Menus pagedMenus(BiFunction<MenuContext, PageRequest, PagedResult<?>> source) {
        PagedListSourceRegistry paged = new PagedListSourceRegistry();
        paged.register("pw:browse", source);
        Menus menus = new Menus(
                renderer,
                new SyncScheduler(),
                new ListSourceRegistry(),
                null,
                null,
                null,
                null,
                BedrockDetector.NONE,
                BedrockScreen.NONE,
                paged);
        menus.registerSpec("test", new MenuSpecLoader().parse(PAGED_HOCON));
        return menus;
    }

    private void open(Menus menus) {
        menus.open(new PlayerRef(player.getUniqueId(), player.getName()), "test", null);
    }

    private Inventory openTop(Menus menus) {
        open(menus);
        return player.getOpenInventory().getTopInventory();
    }

    /** How many of the 45 list content slots (0-44) hold an item. */
    private static int filledContentSlots(Inventory inv) {
        int filled = 0;
        for (int slot = 0; slot < 45; slot++) {
            if (inv.getItem(slot) != null) {
                filled++;
            }
        }
        return filled;
    }

    private static List<Object> rows(String prefix, int count) {
        List<Object> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(prefix + i);
        }
        return rows;
    }

    private static String plainName(Inventory inv, int slot) {
        ItemStack item = inv.getItem(slot);
        if (item == null) {
            return "";
        }
        return PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());
    }

    private static List<LogRecord> captureMenusLog(Runnable body) {
        Logger logger = Logger.getLogger(Menus.class.getName());
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
        try {
            body.run();
        } finally {
            logger.removeHandler(handler);
        }
        return records;
    }

    /** A list entry that is both named (so the template can label it) and pinned to a fixed content slot. */
    private record NamedPin(String name, int slot) implements PinnedEntry {
        @Override
        public int pinnedSlot() {
            return slot;
        }
    }

    /** A paged source that counts its calls, remembers the last request, and answers from a caller-supplied responder. */
    private static final class FakePagedSource implements BiFunction<MenuContext, PageRequest, PagedResult<?>> {
        private final Function<PageRequest, PagedResult<?>> responder;
        private int calls;
        private PageRequest lastRequest;

        FakePagedSource(Function<PageRequest, PagedResult<?>> responder) {
            this.responder = responder;
        }

        @Override
        public PagedResult<?> apply(MenuContext ctx, PageRequest request) {
            calls++;
            lastRequest = request;
            return responder.apply(request);
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class SyncScheduler implements Scheduler {
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

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            return () -> {};
        }
    }
}
