package com.uxplima.uxmessentials.vote.adapter.inbound.gui;

import java.nio.file.Path;
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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.DurationText;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.VoteMessageKey;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.SiteCooldown;
import com.uxplima.uxmessentials.vote.domain.VoteSiteCatalog;
import com.uxplima.uxmessentials.vote.domain.VoteSiteSpec;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the vote-site board with the menu engine and opens it. One icon per configured site: the votable
 * material when the player may vote there now, the cooldown material while the site is still cooling down. Clicking
 * a votable site that has a URL sends the player an Adventure {@link ClickEvent#openUrl} chat component so their
 * client can open the link; clicking a URL-less or cooled-down site is a no-op.
 *
 * <p>The icons are the {@code vote:sites} list source. Every site's cooldown is a database read, and the engine
 * resolves list sources off the tick thread before it hops to the viewer to render, so the read happens there and
 * the resolved {@link SiteEntry} snapshot is what the placeholders draw from. The window itself, its backdrop and
 * its arrows live in {@code modules/vote/gui/vote-sites.conf} and are an operator's to change; only the pair of
 * state materials stays in the module config, since it is the site's state rather than a slot that picks between
 * them.
 */
@NullMarked
public final class VoteSitesMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "vote-sites";

    private static final String SPEC_RESOURCE = "modules/vote/gui/vote-sites.conf";

    /** The operator's {@code gui} sub-block inside the vote module config, minus what the spec file now owns. */
    public record GuiConfig(boolean enabled, Material votableMaterial, Material cooldownMaterial) {

        /** Fallback when a material name is absent or unknown in the config. */
        private static final Material DEFAULT_VOTABLE = Material.PAPER;

        private static final Material DEFAULT_COOLDOWN = Material.CLOCK;

        public GuiConfig {
            Objects.requireNonNull(votableMaterial, "votableMaterial");
            Objects.requireNonNull(cooldownMaterial, "cooldownMaterial");
        }

        /** Parse a material name, falling back to {@code fallback} when blank or unknown. */
        public static Material parseMaterial(String name, Material fallback) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(fallback, "fallback");
            if (name.isBlank()) {
                return fallback;
            }
            try {
                Material parsed = Material.valueOf(name.strip().toUpperCase(Locale.ROOT));
                return parsed == Material.AIR ? fallback : parsed;
            } catch (IllegalArgumentException unknown) {
                return fallback;
            }
        }

        /** The shipped defaults: the board on, a green paper icon for votable, a clock for cooling down. */
        public static GuiConfig defaults() {
            return new GuiConfig(true, DEFAULT_VOTABLE, DEFAULT_COOLDOWN);
        }
    }

    private final Menus menus;
    private final Messages messages;
    private final VoteSiteCatalog catalog;
    private final VoteRepository repository;
    private final GuiConfig guiConfig;

    public VoteSitesMenu(
            Menus menus, Messages messages, VoteSiteCatalog catalog, VoteRepository repository, GuiConfig guiConfig) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.guiConfig = Objects.requireNonNull(guiConfig, "guiConfig");
    }

    /** Register the bindings the spec names and the spec itself; called once at vote wiring time. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list("vote:sites", this::entries);
        bindings.placeholder("vote_site_icon", ctx -> materialOf(entryOf(ctx)).name());
        bindings.placeholder("vote_site_name", ctx -> entryOf(ctx).spec().name());
        bindings.placeholder("vote_site_lore", this::lore);
        bindings.action("vote:site-open", this::siteClicked);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 3, log));
    }

    /** Whether the board is enabled in config (i.e. {@code gui.list-display = gui}). */
    public boolean isEnabled() {
        return guiConfig.enabled();
    }

    /** The configured vote-site catalog this board renders. */
    public VoteSiteCatalog catalog() {
        return catalog;
    }

    /** Open the board for {@code viewer}; the engine reads each site's cooldown off the tick thread before it draws. */
    public void open(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        PlayerRef viewerRef = new PlayerRef(viewer.getUniqueId(), viewer.getName());
        menus.open(viewerRef, SPEC_ID, new Board(Instant.now()));
    }

    /**
     * Resolve one entry per configured site for the viewer: its cooldown row plus whether that cooldown has elapsed
     * as of the moment the board was opened. The engine calls this off the tick thread, which is where the per-site
     * repository read belongs.
     */
    private List<Object> entries(MenuContext ctx) {
        Instant now = ctx.subject(Board.class).openedAt();
        List<VoteSiteSpec> sites = catalog.sites();
        List<Object> entries = new ArrayList<>(sites.size());
        for (VoteSiteSpec site : sites) {
            // Cooldown is keyed on the Votifier service (the write key in HandleVote); display uses the name.
            Optional<Instant> lastVote = repository.lastVoteAtSite(ctx.viewer(), site.service());
            SiteCooldown cooldown = new SiteCooldown(site.name(), lastVote, site.cooldown());
            boolean votable = cooldown.isVotable(now);
            entries.add(new SiteEntry(site, votable, votable ? Duration.ZERO : cooldown.remaining(now)));
        }
        return entries;
    }

    /** The bound site's lore: its URL when it has one, then either the vote-now line or the time-left line. */
    private String lore(MenuContext ctx) {
        SiteEntry entry = entryOf(ctx);
        List<String> lines = new ArrayList<>(2);
        entry.spec().url().ifPresent(url -> lines.add("<cta>" + url + "</cta>"));
        if (entry.votable()) {
            lines.add(messages.resolve(ctx.viewer(), VoteMessageKey.VOTE_GUI_SITE_VOTABLE, Map.of()));
        } else {
            lines.add(messages.resolve(
                    ctx.viewer(),
                    VoteMessageKey.VOTE_GUI_SITE_COOLDOWN,
                    Map.of("time", DurationText.humanize(entry.remaining()))));
        }
        return String.join("\n", lines);
    }

    /** Left-click a site: send its clickable vote link, or do nothing when it is cooling down or has no URL. */
    private void siteClicked(MenuActionContext ctx) {
        SiteEntry entry = ctx.entry(SiteEntry.class);
        if (!entry.votable() || entry.spec().url().isEmpty()) {
            return;
        }
        String url = entry.spec().url().orElseThrow();
        Component link = StyledText.render(
                        messages.resolve(ctx.viewer(), VoteMessageKey.VOTE_GUI_CLICK, Map.of("url", url)))
                .clickEvent(ClickEvent.openUrl(url));
        ctx.player().closeInventory();
        ctx.player().sendMessage(link);
    }

    private Material materialOf(SiteEntry entry) {
        return entry.votable() ? guiConfig.votableMaterial() : guiConfig.cooldownMaterial();
    }

    private static SiteEntry entryOf(MenuContext ctx) {
        return ctx.entry(SiteEntry.class);
    }

    /**
     * The subject of an open board: the instant the viewer opened it, which every site's votable/remaining answer is
     * measured against so one board never mixes two clocks.
     *
     * @param openedAt when the viewer ran the command that opened the board
     */
    public record Board(Instant openedAt) {

        public Board {
            Objects.requireNonNull(openedAt, "openedAt");
        }
    }

    /**
     * One resolved site row: the configured site and the viewer's standing at it as of the open.
     *
     * @param spec the configured site
     * @param votable whether the viewer may vote there now
     * @param remaining how long is left on the cooldown, {@link Duration#ZERO} when votable
     */
    public record SiteEntry(VoteSiteSpec spec, boolean votable, Duration remaining) {

        public SiteEntry {
            Objects.requireNonNull(spec, "spec");
            Objects.requireNonNull(remaining, "remaining");
        }
    }
}
