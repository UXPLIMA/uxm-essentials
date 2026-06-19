package com.uxplima.uxmessentials.vote.adapter.inbound.gui;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

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
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.PaginatedGui;
import com.uxplima.uxmlib.gui.item.ItemPopulator;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * Paginated GUI listing every configured vote site with its current cooldown state. One icon per
 * site: green ({@code votable-material}) when the player can vote now, amber ({@code cooldown-material})
 * when the cooldown has not yet elapsed. Clicking a votable site with a URL sends the player an
 * Adventure {@link ClickEvent#openUrl} chat component so their client can open the link; clicking a
 * site with no URL is a no-op.
 *
 * <p>The view fetches cooldown data off the tick thread via {@link Scheduler#async}, then hops back to
 * the player's entity thread to build and open the inventory — matching the {@code BaltopGuiView} pattern.
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
                Material m = Material.valueOf(name.strip().toUpperCase(java.util.Locale.ROOT));
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
    private final GuiConfig guiConfig;

    public VoteSitesGuiView(
            VoteSiteCatalog catalog,
            VoteRepository repository,
            Scheduler scheduler,
            Messages messages,
            GuiConfig guiConfig) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
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
     * Open the vote-sites GUI for {@code viewer}. Fetches per-site cooldown data asynchronously,
     * then opens the inventory on the player's entity thread.
     */
    public void open(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        PlayerRef viewerRef = new PlayerRef(viewer.getUniqueId(), viewer.getName());
        Instant now = Instant.now();

        scheduler.async(() -> {
            List<SiteEntry> entries = buildEntries(viewerRef, now);
            scheduler.onEntity(viewerRef, () -> buildAndOpen(viewer, viewerRef, entries));
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

    private void buildAndOpen(Player viewer, PlayerRef viewerRef, List<SiteEntry> entries) {
        int rows = Math.max(1, Math.min(6, computeRows(entries.size())));

        Component title = text(viewerRef, VoteMessageKey.VOTE_GUI_TITLE, Map.of());

        int contentSlotCount = rows * 9;
        List<Integer> contentSlots = new ArrayList<>(contentSlotCount);
        for (int i = 0; i < contentSlotCount; i++) {
            contentSlots.add(i);
        }

        PaginatedGui gui = Guis.paginated()
                .title(title)
                .rows(rows)
                .contentSlots(contentSlots)
                .build();

        gui.populate(
                entries,
                ItemPopulator.of(
                        entry -> buildSiteIcon(viewerRef, entry),
                        (entry, event) -> handleClick(viewer, viewerRef, entry, gui)));

        gui.open(viewer);
    }

    private void handleClick(Player viewer, PlayerRef viewerRef, SiteEntry entry, PaginatedGui gui) {
        Optional<String> url = entry.spec().url();
        if (url.isEmpty()) {
            return;
        }
        // Close the GUI, then send the clickable URL link.
        scheduler.onEntity(viewerRef, () -> {
            gui.close(viewer);
            Component link = text(viewerRef, VoteMessageKey.VOTE_GUI_CLICK, Map.of("url", url.get()))
                    .clickEvent(ClickEvent.openUrl(url.get()));
            viewer.sendMessage(link);
        });
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

    /** Determine a row count large enough for all entries, capped at {@link GuiConfig#rows()}. */
    private int computeRows(int entryCount) {
        if (entryCount == 0) {
            return 1;
        }
        int needed = (entryCount + 8) / 9;
        return Math.min(needed, guiConfig.rows());
    }

    private Component text(PlayerRef viewer, VoteMessageKey key, Map<String, String> placeholders) {
        return StyledText.render(messages.resolve(viewer, key, placeholders));
    }

    /** Per-site snapshot held while the GUI is being built. */
    private record SiteEntry(VoteSiteSpec spec, boolean votable, Duration remaining) {}
}
