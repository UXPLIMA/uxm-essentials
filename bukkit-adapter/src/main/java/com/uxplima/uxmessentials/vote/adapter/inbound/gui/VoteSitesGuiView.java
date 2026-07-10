package com.uxplima.uxmessentials.vote.adapter.inbound.gui;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.EntityListSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DurationText;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.VoteMessageKey;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.SiteCooldown;
import com.uxplima.uxmessentials.vote.domain.VoteSiteCatalog;
import com.uxplima.uxmessentials.vote.domain.VoteSiteSpec;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * Paginated GUI listing every configured vote site with its current cooldown state. One icon per site: green
 * ({@code votable-material}) when the player can vote now, amber ({@code cooldown-material}) when the cooldown has
 * not yet elapsed. Clicking a votable site with a URL sends the player an Adventure {@link ClickEvent#openUrl} chat
 * component so their client can open the link; clicking a site with no URL (or one still on cooldown) is a no-op.
 *
 * <p>The view draws through the menu engine's paginated-list runtime ({@link Menus#openList}) over a
 * {@link EntityListSpec}, so the window is a holder-backed engine list routed and torn down by the one menu listener and
 * one {@code closeMenu}, with paging re-paginating the same holder. The per-site cooldown read is a database hit, so
 * {@link #open} resolves each site's votable/remaining state off the tick thread first and hands the engine the
 * resolved {@link SiteEntry} snapshot; the imperative icon renderer then reads only that snapshot and shows the
 * window on the viewer's entity thread — the same read-off-thread, render-on-entity pattern the other list views
 * follow.
 */
@NullMarked
public final class VoteSitesGuiView {

    /** Immutable snapshot of the operator's {@code gui} sub-block inside the vote module config. */
    public record GuiConfig(boolean enabled, int rows, Material votableMaterial, Material cooldownMaterial) {

        /** Fallback when a material name is absent or unknown in the config. */
        private static final Material DEFAULT_VOTABLE = Material.PAPER;

        private static final Material DEFAULT_COOLDOWN = Material.CLOCK;

        public GuiConfig {
            Objects.requireNonNull(votableMaterial, "votableMaterial");
            Objects.requireNonNull(cooldownMaterial, "cooldownMaterial");
            if (rows < 1 || rows > 6) {
                throw new IllegalArgumentException("rows must be 1..6: " + rows);
            }
        }

        /** Parse a material name, falling back to {@code fallback} when blank or unknown. */
        public static Material parseMaterial(String name, Material fallback) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(fallback, "fallback");
            if (name.isBlank()) {
                return fallback;
            }
            try {
                Material m = Material.valueOf(name.strip().toUpperCase(Locale.ROOT));
                return m == Material.AIR ? fallback : m;
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }

        /** Construct a {@code GuiConfig} with sensible defaults. */
        public static GuiConfig defaults() {
            return new GuiConfig(true, 3, DEFAULT_VOTABLE, DEFAULT_COOLDOWN);
        }
    }

    private final VoteSiteCatalog catalog;
    private final VoteRepository repository;
    private final Scheduler scheduler;
    private final Messages messages;
    private final Menus menus;
    private final GuiText guiText;
    private final GuiConfig guiConfig;

    public VoteSitesGuiView(
            VoteSiteCatalog catalog,
            VoteRepository repository,
            Scheduler scheduler,
            Messages messages,
            Menus menus,
            GuiText guiText,
            GuiConfig guiConfig) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.guiConfig = Objects.requireNonNull(guiConfig, "guiConfig");
    }

    /** Whether the GUI is enabled in config (i.e. list-display = gui). */
    public boolean isEnabled() {
        return guiConfig.enabled();
    }

    /** The configured vote-site catalog this view renders. */
    public VoteSiteCatalog catalog() {
        return catalog;
    }

    /**
     * Open the vote-sites GUI for {@code viewer}. Fetches per-site cooldown data asynchronously, then opens the
     * engine list on the player's entity thread over the resolved snapshot.
     */
    public void open(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        PlayerRef viewerRef = new PlayerRef(viewer.getUniqueId(), viewer.getName());
        Instant now = Instant.now();
        scheduler.async(() -> {
            List<SiteEntry> entries = buildEntries(viewerRef, now);
            menus.openList(viewerRef, spec(viewerRef, entries));
        });
    }

    private List<SiteEntry> buildEntries(PlayerRef viewerRef, Instant now) {
        List<VoteSiteSpec> sites = catalog.sites();
        List<SiteEntry> entries = new ArrayList<>(sites.size());
        for (VoteSiteSpec spec : sites) {
            // Cooldown is keyed on the Votifier service (the write key in HandleVote); display uses name.
            Optional<Instant> lastVote = repository.lastVoteAtSite(viewerRef, spec.service());
            SiteCooldown cooldown = new SiteCooldown(spec.name(), lastVote, spec.cooldown());
            boolean votable = cooldown.isVotable(now);
            Duration remaining = votable ? Duration.ZERO : cooldown.remaining(now);
            entries.add(new SiteEntry(spec, votable, remaining));
        }
        return entries;
    }

    /**
     * Build the engine {@link EntityListSpec} for one viewer over the resolved site entries: one icon per site (votable
     * green / cooldown amber, matching the config materials), and an {@code onSelect} that sends the clickable vote
     * link for a votable site with a URL — a no-op otherwise. The window is sized just large enough for the entries
     * (capped at the configured {@code rows} plus the reserved nav row); the content grid fills every row above the
     * bottom one, and the previous/next arrows sit at the bottom-row corners, the same shape the other engine lists
     * draw.
     */
    private EntityListSpec spec(PlayerRef viewerRef, List<SiteEntry> entries) {
        int rows = windowRows(entries.size());
        return EntityListSpec.builder()
                .title(guiText.text(viewerRef, VoteMessageKey.VOTE_GUI_TITLE))
                .rows(rows)
                .contentSlots(contentSlots(rows))
                .navigation(navPrevSlot(rows), navNextSlot(rows), Material.ARROW)
                .navNames(
                        guiText.text(viewerRef, VoteMessageKey.VOTE_GUI_PREV),
                        guiText.text(viewerRef, VoteMessageKey.VOTE_GUI_NEXT))
                .filler(Material.BLACK_STAINED_GLASS_PANE)
                .entities(() -> List.<Object>copyOf(entries))
                .iconRenderer((v, entity) -> buildSiteIcon(v, (SiteEntry) entity))
                .onSelect((player, entity) -> handleClick(player, (SiteEntry) entity))
                .build();
    }

    /** Send the clickable vote link for a votable site with a URL; a no-op for a URL-less or cooled-down site. */
    private void handleClick(Player player, SiteEntry entry) {
        if (!entry.votable()) {
            return;
        }
        Optional<String> url = entry.spec().url();
        if (url.isEmpty()) {
            return;
        }
        PlayerRef viewerRef = new PlayerRef(player.getUniqueId(), player.getName());
        Component link = text(viewerRef, VoteMessageKey.VOTE_GUI_CLICK, Map.of("url", url.get()))
                .clickEvent(ClickEvent.openUrl(url.get()));
        player.closeInventory();
        player.sendMessage(link);
    }

    private ItemStack buildSiteIcon(PlayerRef viewer, SiteEntry entry) {
        Material material = entry.votable() ? guiConfig.votableMaterial() : guiConfig.cooldownMaterial();

        List<Component> lore = new ArrayList<>(2);
        entry.spec().url().ifPresent(url -> lore.add(StyledText.render("<cta>" + url + "</cta>")));
        if (entry.votable()) {
            lore.add(text(viewer, VoteMessageKey.VOTE_GUI_SITE_VOTABLE, Map.of()));
        } else {
            lore.add(text(
                    viewer,
                    VoteMessageKey.VOTE_GUI_SITE_COOLDOWN,
                    Map.of("time", DurationText.humanize(entry.remaining()))));
        }

        return ItemBuilder.of(material)
                .name(StyledText.render("<value>" + entry.spec().name() + "</value>"))
                .lore(lore)
                .build();
    }

    /**
     * The window's total row count: enough content rows to hold all entries (capped at the configured {@code rows})
     * plus the bottom row reserved for the nav arrows, clamped to a chest's 1..6. A single content row holds nine
     * sites, so a typical handful of sites opens a compact two-row window.
     */
    private int windowRows(int entryCount) {
        int contentRows = entryCount == 0 ? 1 : (entryCount + 8) / 9;
        contentRows = Math.min(contentRows, guiConfig.rows());
        return Math.min(6, contentRows + 1);
    }

    /** The content slots an icon may occupy: every row above the bottom one (which carries the nav arrows). */
    private static List<Integer> contentSlots(int rows) {
        int limit = (rows - 1) * 9;
        List<Integer> slots = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            slots.add(i);
        }
        return List.copyOf(slots);
    }

    /** The previous-page nav slot: the bottom-left corner of the window. */
    private static int navPrevSlot(int rows) {
        return (rows - 1) * 9;
    }

    /** The next-page nav slot: the bottom-right corner of the window. */
    private static int navNextSlot(int rows) {
        return rows * 9 - 1;
    }

    private Component text(PlayerRef viewer, VoteMessageKey key, Map<String, String> placeholders) {
        return StyledText.render(messages.resolve(viewer, key, placeholders));
    }

    /** Per-site snapshot held while the GUI is being built. */
    private record SiteEntry(VoteSiteSpec spec, boolean votable, Duration remaining) {}
}
