package com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.playerwarps.application.DelPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.PlayerwarpsMessageKey;
import com.uxplima.uxmessentials.playerwarps.application.SetPlayerWarpVisibility;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EnumProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.NumberProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.TextProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ToggleProperty;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * The per-warp property editor: a thin consumer of the shared {@link EntityEditorView} that exposes every
 * player-warp property as one button wired to the same write path the {@code /pwarp} subcommands use. A warp is
 * keyed {@code (owner, name)}, so the editor is generic over an {@link OwnedWarp} and every property reads the
 * live row fresh from the {@link PlayerWarpRepository} on each open (the list-click snapshot would otherwise go
 * stale after an edit) and writes back against that same owner — an operator never edits anyone else's warp by
 * accident, and a player only ever reaches their own through the list's owner filter.
 *
 * <p>Most fields are immutable {@code with*} transitions on the {@link PlayerWarp} aggregate persisted through
 * {@code repository.save}; visibility flows through {@link SetPlayerWarpVisibility} (the same use case the
 * {@code /pwarp public|private} subcommands call), move-here re-anchors at the operator's feet, and delete runs
 * {@link DelPlayerWarp} behind the framework's confirm gate.
 */
@NullMarked
public final class PlayerWarpEditorView {

    /** Overrides are edited as integer tenths of a second so the stepper has no float precision drift. */
    private static final long SECONDS_FACTOR = 10L;

    private static final String CLEAR_TOKEN = "-";

    private final GuiText guiText;
    private final Scheduler scheduler;
    private final PlayerWarpRepository repository;
    private final SetPlayerWarpVisibility visibility;
    private final TextInput textInput;
    private final Messages messages;
    private final PlayerWarpEditorSubLayouts sub;
    private final EntityEditorView<OwnedWarp> view;

    public PlayerWarpEditorView(
            GuiText guiText,
            Scheduler scheduler,
            PlayerWarpRepository repository,
            SetPlayerWarpVisibility visibility,
            DelPlayerWarp delPlayerWarp,
            TextInput textInput,
            Messages messages,
            EntityEditorLayout layout,
            PlayerWarpEditorSubLayouts sub,
            BiConsumer<Player, PlayerRef> onBack) {
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(delPlayerWarp, "delPlayerWarp");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sub = Objects.requireNonNull(sub, "sub");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(onBack, "onBack");
        this.view = EntityEditorView.<OwnedWarp>builder()
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(layout)
                .title(this::title)
                .valueLore(PlayerwarpsMessageKey.PWARP_GUI_EDITOR_VALUE_LORE)
                .backName(PlayerwarpsMessageKey.PWARP_GUI_EDITOR_BACK)
                .properties(this::properties)
                .onBack(onBack)
                .onDelete(
                        PlayerwarpsMessageKey.PWARP_GUI_EDITOR_DELETE,
                        PlayerwarpsMessageKey.PWARP_GUI_EDITOR_DELETE_CONFIRM,
                        (player, owned) ->
                                delPlayerWarp.delete(owned.owner(), owned.warp().name()))
                .build();
    }

    /** Open the editor for {@code owned}, scheduled on the viewer's entity thread by the framework. */
    public void open(Player player, PlayerRef viewer, OwnedWarp owned) {
        view.open(player, viewer, owned);
    }

    /** The underlying property grid — exposed for tests to resolve a slot to its property without a live click. */
    EntityEditorView<OwnedWarp> grid() {
        return view;
    }

    private Component title(PlayerRef viewer, OwnedWarp owned) {
        return guiText.text(
                viewer,
                PlayerwarpsMessageKey.PWARP_GUI_EDITOR_TITLE,
                Map.of("name", owned.warp().name().value()));
    }

    private List<EditableProperty> properties(OwnedWarp owned) {
        PlayerRef owner = owned.owner();
        PlayerWarpName name = owned.warp().name();
        List<EditableProperty> props = new ArrayList<>();
        props.add(nameProperty(owner, name));
        props.add(moveProperty(owner, name));
        props.add(iconProperty(owner, name));
        props.add(visibilityProperty(owner, name));
        props.add(lockProperty(owner, name));
        props.add(passwordProperty(owner, name));
        props.add(soundProperty(
                owner,
                name,
                PlayerwarpsMessageKey.PWARP_GUI_PROP_DEPARTURE_SOUND,
                PlayerwarpsMessageKey.PWARP_GUI_PROP_DEPARTURE_SOUND_PROMPT,
                Material.NOTE_BLOCK,
                PlayerWarp::departureSound,
                PlayerWarp::withDepartureSound));
        props.add(soundProperty(
                owner,
                name,
                PlayerwarpsMessageKey.PWARP_GUI_PROP_ARRIVAL_SOUND,
                PlayerwarpsMessageKey.PWARP_GUI_PROP_ARRIVAL_SOUND_PROMPT,
                Material.JUKEBOX,
                PlayerWarp::arrivalSound,
                PlayerWarp::withArrivalSound));
        props.add(soundProperty(
                owner,
                name,
                PlayerwarpsMessageKey.PWARP_GUI_PROP_DEPARTURE_PARTICLE,
                PlayerwarpsMessageKey.PWARP_GUI_PROP_DEPARTURE_PARTICLE_PROMPT,
                Material.BLAZE_POWDER,
                PlayerWarp::departureParticle,
                PlayerWarp::withDepartureParticle));
        props.add(soundProperty(
                owner,
                name,
                PlayerwarpsMessageKey.PWARP_GUI_PROP_ARRIVAL_PARTICLE,
                PlayerwarpsMessageKey.PWARP_GUI_PROP_ARRIVAL_PARTICLE_PROMPT,
                Material.GLOWSTONE_DUST,
                PlayerWarp::arrivalParticle,
                PlayerWarp::withArrivalParticle));
        props.add(secondsProperty(
                owner,
                name,
                PlayerwarpsMessageKey.PWARP_GUI_PROP_WARMUP,
                PlayerWarp::warmupOverrideSeconds,
                PlayerWarp::withWarmupOverride));
        props.add(secondsProperty(
                owner,
                name,
                PlayerwarpsMessageKey.PWARP_GUI_PROP_COOLDOWN,
                PlayerWarp::cooldownOverrideSeconds,
                PlayerWarp::withCooldownOverride));
        return props;
    }

    // --- identity / position ---

    private EditableProperty nameProperty(PlayerRef owner, PlayerWarpName name) {
        return new TextProperty(
                "editor.text-field",
                PlayerwarpsMessageKey.PWARP_GUI_PROP_NAME,
                PlayerwarpsMessageKey.PWARP_GUI_PROP_NAME_PROMPT,
                Material.NAME_TAG,
                name::value,
                raw -> raw.isBlank() ? Optional.empty() : Optional.of(raw.trim()),
                value -> rename(owner, name, value),
                textInput,
                scheduler);
    }

    /**
     * Rename a warp by re-saving the live row under the new name and deleting the original — there is no rename
     * use case (the same copy+delete an operator would run by hand), so this composes the existing save/delete
     * paths. A no-op when the new name equals the old or no such warp exists, and a name the owner already uses is
     * left to the repository's {@code (owner, name)} upsert just as {@code /setpwarp} is.
     */
    private void rename(PlayerRef owner, PlayerWarpName from, String rawTo) {
        PlayerWarpName to = PlayerWarpName.of(rawTo);
        if (from.equals(to)) {
            return;
        }
        Optional<PlayerWarp> existing = repository.find(owner, from);
        if (existing.isEmpty()) {
            return;
        }
        repository.save(withName(existing.get(), to));
        repository.delete(owner, from);
    }

    /** A copy of {@code warp} under {@code name}, keeping every other field (the record has no name setter). */
    private static PlayerWarp withName(PlayerWarp warp, PlayerWarpName name) {
        return new PlayerWarp(
                warp.owner(),
                name,
                warp.location(),
                warp.isPublic(),
                warp.createdAt(),
                warp.visitors(),
                warp.password(),
                warp.isLocked(),
                warp.welcomeMessages(),
                warp.departureSound(),
                warp.arrivalSound(),
                warp.departureParticle(),
                warp.arrivalParticle(),
                warp.warmupOverrideSeconds(),
                warp.cooldownOverrideSeconds(),
                warp.iconMaterial());
    }

    private EditableProperty moveProperty(PlayerRef owner, PlayerWarpName name) {
        return new PlayerWarpActionButton(
                PlayerwarpsMessageKey.PWARP_GUI_PROP_MOVE,
                Material.COMPASS,
                "",
                (player, reopen) -> {
                    Position at = BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "location"));
                    scheduler.async(() -> {
                        mutate(owner, name, warp -> warp.movedTo(at));
                        scheduler.onEntity(BukkitRefs.toRef(player), reopen);
                    });
                },
                scheduler);
    }

    // --- appearance / access ---

    private EditableProperty iconProperty(PlayerRef owner, PlayerWarpName name) {
        return new TextProperty(
                "editor.text-field",
                PlayerwarpsMessageKey.PWARP_GUI_PROP_ICON,
                PlayerwarpsMessageKey.PWARP_GUI_PROP_ICON_PROMPT,
                iconButtonMaterial(owner, name),
                () -> current(owner, name).flatMap(PlayerWarp::iconMaterial).orElseGet(this::none),
                raw -> raw.isBlank() ? Optional.empty() : Optional.of(raw.trim()),
                value -> mutate(owner, name, warp -> warp.withIconMaterial(optional(value))),
                textInput,
                scheduler);
    }

    /**
     * The icon button's own material: the warp's configured icon, so the button shows what it sets rather than a
     * fixed stand-in. The property list is rebuilt from the live row on each open, so resolving here keeps the
     * button in step with the value. An unset or unparseable icon falls back to {@code ITEM_FRAME}.
     */
    private Material iconButtonMaterial(PlayerRef owner, PlayerWarpName name) {
        return current(owner, name)
                .flatMap(PlayerWarp::iconMaterial)
                .map(Material::matchMaterial)
                .filter(material -> material != Material.AIR)
                .orElse(Material.ITEM_FRAME);
    }

    private EditableProperty visibilityProperty(PlayerRef owner, PlayerWarpName name) {
        return new EnumProperty<>(
                PlayerwarpsMessageKey.PWARP_GUI_PROP_VISIBILITY,
                PlayerwarpsMessageKey.PWARP_GUI_SELECT_VISIBILITY,
                Material.ENDER_EYE,
                guiText,
                List.of(Boolean.TRUE, Boolean.FALSE),
                () -> current(owner, name).map(PlayerWarp::isPublic).orElse(false),
                (viewer, isPublic) -> visibilityWord(viewer, isPublic),
                isPublic -> applyVisibility(owner, name, isPublic),
                sub.selectorOptionIcon(),
                sub.selectorFiller(),
                sub.selectorSlots(),
                sub.selectorRows(),
                scheduler);
    }

    private void applyVisibility(PlayerRef owner, PlayerWarpName name, boolean isPublic) {
        if (isPublic) {
            visibility.setPublic(owner, name);
        } else {
            visibility.setPrivate(owner, name);
        }
    }

    private EditableProperty lockProperty(PlayerRef owner, PlayerWarpName name) {
        return ToggleProperty.ofBoolean(
                PlayerwarpsMessageKey.PWARP_GUI_PROP_LOCK,
                Material.TRIPWIRE_HOOK,
                () -> current(owner, name).map(PlayerWarp::isLocked).orElse(false),
                this::lockWord,
                locked -> mutate(owner, name, warp -> warp.withLocked(locked)),
                scheduler);
    }

    private EditableProperty passwordProperty(PlayerRef owner, PlayerWarpName name) {
        return new TextProperty(
                "editor.text-field",
                PlayerwarpsMessageKey.PWARP_GUI_PROP_PASSWORD,
                PlayerwarpsMessageKey.PWARP_GUI_PROP_PASSWORD_PROMPT,
                Material.IRON_DOOR,
                () -> current(owner, name).flatMap(PlayerWarp::password).isPresent() ? set() : none(),
                raw -> raw.isBlank() ? Optional.empty() : Optional.of(raw.trim()),
                value -> mutate(owner, name, warp -> warp.withPassword(optional(value))),
                textInput,
                scheduler);
    }

    // --- effects (sounds / particles) ---

    private EditableProperty soundProperty(
            PlayerRef owner,
            PlayerWarpName name,
            MessageKey label,
            MessageKey prompt,
            Material icon,
            java.util.function.Function<PlayerWarp, Optional<String>> getter,
            java.util.function.BiFunction<PlayerWarp, Optional<String>, PlayerWarp> setter) {
        return new TextProperty(
                "editor.text-field",
                label,
                prompt,
                icon,
                () -> current(owner, name).flatMap(getter).orElseGet(this::none),
                raw -> raw.isBlank() ? Optional.empty() : Optional.of(raw.trim()),
                value -> mutate(owner, name, warp -> setter.apply(warp, optional(value))),
                textInput,
                scheduler);
    }

    // --- warmup / cooldown overrides ---

    private EditableProperty secondsProperty(
            PlayerRef owner,
            PlayerWarpName name,
            MessageKey label,
            java.util.function.Function<PlayerWarp, Optional<Double>> getter,
            java.util.function.BiFunction<PlayerWarp, Optional<Double>, PlayerWarp> setter) {
        return new NumberProperty(
                label,
                Material.CLOCK,
                () -> Math.round(current(owner, name).flatMap(getter).orElse(0.0) * SECONDS_FACTOR),
                SECONDS_FACTOR, // a click steps one whole second
                5,
                0,
                Math.round(3600.0 * SECONDS_FACTOR),
                value -> mutate(owner, name, warp -> setter.apply(warp, secondsOverride(value))),
                scheduler);
    }

    /** Zero clears the override (matching "no override"); anything positive is the override in seconds. */
    private static Optional<Double> secondsOverride(long tenths) {
        return tenths <= 0 ? Optional.empty() : Optional.of(tenths / (double) SECONDS_FACTOR);
    }

    // --- write helper: read the live row, apply the transition, save it owner-scoped ---

    private void mutate(PlayerRef owner, PlayerWarpName name, java.util.function.UnaryOperator<PlayerWarp> change) {
        repository.find(owner, name).map(change).ifPresent(repository::save);
    }

    private Optional<PlayerWarp> current(PlayerRef owner, PlayerWarpName name) {
        return repository.find(owner, name);
    }

    private static Optional<String> optional(String value) {
        String trimmed = value.strip();
        return trimmed.equals(CLEAR_TOKEN) || trimmed.equalsIgnoreCase("none") || trimmed.equalsIgnoreCase("clear")
                ? Optional.empty()
                : Optional.of(trimmed);
    }

    // --- localised value words ---

    private String visibilityWord(PlayerRef viewer, boolean isPublic) {
        return messages.resolve(
                viewer,
                isPublic ? PlayerwarpsMessageKey.PWARP_GUI_VALUE_PUBLIC : PlayerwarpsMessageKey.PWARP_GUI_VALUE_PRIVATE,
                Map.of());
    }

    private String lockWord(PlayerRef viewer, boolean locked) {
        return messages.resolve(
                viewer,
                locked ? PlayerwarpsMessageKey.PWARP_GUI_VALUE_LOCKED : PlayerwarpsMessageKey.PWARP_GUI_VALUE_UNLOCKED,
                Map.of());
    }

    private String none() {
        return word(PlayerwarpsMessageKey.PWARP_GUI_VALUE_NONE);
    }

    private String set() {
        return word(PlayerwarpsMessageKey.PWARP_GUI_VALUE_SET);
    }

    private String word(MessageKey key) {
        return messages.resolve(GUI_ACTOR, key, Map.of());
    }

    /**
     * The stable synthetic actor a fixed-text value word is resolved for. A button's value word has no live
     * viewer when the editor builds the property list off the click thread; the word is the same in every locale
     * the catalog ships, so a fixed ref keeps it consistent without binding it to whoever holds the menu.
     */
    private static final PlayerRef GUI_ACTOR = new PlayerRef(new java.util.UUID(0L, 0L), "pwarp-gui");
}
