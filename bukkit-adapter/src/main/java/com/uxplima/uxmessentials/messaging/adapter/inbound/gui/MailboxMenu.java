package com.uxplima.uxmessentials.messaging.adapter.inbound.gui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.messaging.application.ClearMail;
import com.uxplima.uxmessentials.messaging.application.MessagingMessageKey;
import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.messaging.domain.MailBox;
import com.uxplima.uxmessentials.messaging.domain.MailItem;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecException;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RefreshSpec;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.ConfirmMenu;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the mailbox ({@code /mail} with no arguments, or the mailbox entry on the {@code /uxmess gui} hub) with
 * the menu engine and opens it. Two specs cover the two windows: {@code messaging-mailbox} draws the paginated
 * newest-first mail grid and {@code messaging-mail-detail} the read-only per-mail detail — the same shape the
 * communication panel and its announcer list follow.
 *
 * <p>The box is a database read, so the {@code messaging:mail} source loads it off the viewer's region thread,
 * snapshots the (immutable) items, and marks the box read in the same hop — the GUI equivalent of {@code /mail
 * read} — so the icons still render from the pre-mark snapshot and unread mail reads as new on first open. The
 * {@code mail_icon} placeholder picks the read/unread material and the {@code mail_sender} / {@code mail_time} /
 * {@code mail_snippet} / {@code mail_message} placeholders fill each entry and the detail from the bound mail. A
 * left click opens that mail's detail (carrying the {@link MailItem} as the subject); the clear button empties the
 * box behind a confirm through the same {@link ClearMail} use case {@code /mail clear} drives — so the mailbox adds
 * no domain logic of its own.
 */
@NullMarked
public final class MailboxMenu {

    /** The engine spec ids the mailbox list and its per-mail detail register and open under. */
    public static final String LIST_SPEC_ID = "messaging-mailbox";

    public static final String DETAIL_SPEC_ID = "messaging-mail-detail";

    private static final String LIST_RESOURCE = "modules/menu/specs/messaging-mailbox.conf";
    private static final String DETAIL_RESOURCE = "modules/menu/specs/messaging-mail-detail.conf";

    /** How many characters of the body the list lore previews before eliding. */
    private static final int SNIPPET_LIMIT = 40;

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final Menus menus;
    private final GuiText guiText;
    private final Scheduler scheduler;
    private final MailRepository mail;
    private final ClearMail clearMail;

    public MailboxMenu(Menus menus, GuiText guiText, Scheduler scheduler, MailRepository mail, ClearMail clearMail) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.mail = Objects.requireNonNull(mail, "mail");
        this.clearMail = Objects.requireNonNull(clearMail, "clearMail");
    }

    /** Register the bindings the two specs name and the specs themselves; called once at messaging wiring time. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list("messaging:mail", this::items);
        bindings.placeholder("mail_icon", this::icon);
        bindings.placeholder("mail_sender", ctx -> mailOf(ctx).sender().name());
        bindings.placeholder("mail_time", ctx -> TIME.format(mailOf(ctx).sentAt()));
        bindings.placeholder("mail_snippet", ctx -> snippet(mailOf(ctx).body().value()));
        bindings.placeholder("mail_message", ctx -> mailOf(ctx).body().value());
        bindings.action("messaging:read-mail", this::openDetail);
        bindings.action("messaging:clear-mail", this::confirmClear);
        bindings.action("messaging:mail-back", ctx -> open(ctx.viewer()));
        menus.registerSpec(LIST_SPEC_ID, loadSpec(LIST_RESOURCE, dataFolder, log));
        menus.registerSpec(DETAIL_SPEC_ID, loadSpec(DETAIL_RESOURCE, dataFolder, log));
    }

    /** Open the mailbox for {@code viewer} (the live player resolved by the engine). */
    public void open(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        menus.open(viewer, LIST_SPEC_ID, null);
    }

    /**
     * The viewer's mail newest-first, loaded off the region thread (a database read). Opening the mailbox is the
     * GUI's read action, so the box is marked read in the same hop — the icons still render from this pre-mark
     * snapshot (the items are immutable value objects) so unread mail reads as new. Touches no Bukkit API.
     */
    private List<MailItem> items(MenuContext ctx) {
        PlayerRef viewer = ctx.viewer();
        MailBox box = mail.load(viewer);
        List<MailItem> snapshot = box.items();
        mail.markAllRead(viewer);
        return snapshot;
    }

    /** The bound entry's icon material name: an open WRITTEN_BOOK while unread, a plain PAPER once read. */
    private String icon(MenuContext ctx) {
        return mailOf(ctx).read() ? "PAPER" : "WRITTEN_BOOK";
    }

    /** The bound mail: the clicked detail's subject when present, else the per-entry list element. */
    private MailItem mailOf(MenuContext ctx) {
        return ctx.subjectRaw().isPresent() ? ctx.subject(MailItem.class) : ctx.entry(MailItem.class);
    }

    /** Left-click a mail: open its read-only detail, carrying the clicked item as the subject. */
    private void openDetail(MenuActionContext ctx) {
        menus.open(ctx.viewer(), DETAIL_SPEC_ID, ctx.entry(MailItem.class));
    }

    /** Confirm-gate the {@code /mail clear} empty; cancel reopens the mailbox. */
    private void confirmClear(MenuActionContext ctx) {
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        Component title = guiText.text(viewer, MessagingMessageKey.GUI_MAIL_CLEAR_CONFIRM);
        ConfirmMenu.of(title, () -> doClear(viewer), () -> open(viewer)).open(player);
    }

    /** Empty the box through the {@link ClearMail} use case off the click thread, then reopen the mailbox. */
    private void doClear(PlayerRef viewer) {
        scheduler.async(() -> {
            clearMail.clear(viewer);
            open(viewer);
        });
    }

    private static String snippet(String body) {
        String trimmed = body.strip();
        if (trimmed.length() <= SNIPPET_LIMIT) {
            return trimmed;
        }
        return trimmed.substring(0, SNIPPET_LIMIT) + "…";
    }

    /**
     * Load a spec, preferring an operator's edit on disk over the bundled resource and finally a built-in empty
     * fallback, so a typo or a missing file degrades to a closeable empty window rather than aborting messaging
     * wiring. Resolution mirrors {@code GuiLayouts}: disk first, then the classpath default.
     */
    private MenuSpec loadSpec(String resource, Path dataFolder, Logger log) {
        MenuSpecLoader specLoader = new MenuSpecLoader();
        Path onDisk = dataFolder.resolve(resource);
        if (Files.isRegularFile(onDisk)) {
            try {
                return specLoader.load(onDisk);
            } catch (MenuSpecException malformed) {
                log.error("failed to load menu spec " + onDisk + ", using bundled default", malformed);
            }
        }
        return loadBundledSpec(specLoader, resource, log);
    }

    private MenuSpec loadBundledSpec(MenuSpecLoader specLoader, String resource, Logger log) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                log.warn("bundled menu spec {} is missing from the jar", resource);
                return emptySpec();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return specLoader.parse(reader.lines().collect(Collectors.joining("\n")));
            }
        } catch (IOException | MenuSpecException failure) {
            log.error("could not read bundled menu spec " + resource, failure);
            return emptySpec();
        }
    }

    /** A minimal valid spec used only when a real one cannot be read, so messaging still wires cleanly. */
    private static MenuSpec emptySpec() {
        return new MenuSpec("", 6, new RefreshSpec(false, 0), List.of(), List.of(), List.of(), Map.of());
    }
}
