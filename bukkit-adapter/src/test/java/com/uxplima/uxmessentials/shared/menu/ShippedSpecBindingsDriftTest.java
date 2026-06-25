package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import org.junit.jupiter.api.Test;

/**
 * A shipped menu spec may only name binding ids that production wiring actually registers. Without this guard a
 * typo in a {@code .conf} — or a spec that outlives the wiring that fed it — becomes a broken menu a player meets
 * at runtime instead of a failure a maintainer meets at build time. The test walks the bundled specs, parses each
 * (a parse failure is itself a drift the test surfaces), and asserts every ref resolves against an allowlist that
 * mirrors the ids the feature wiring registers.
 *
 * <p>The four {@code EXPECTED_*} sets are the contract: keep them in sync with the ids registered through
 * {@link MenuBindings} in the production wiring. Each migrated menu lands its ids here in the same change that adds
 * the spec and the wiring (warp-sounds and the vault selector are wired today).
 */
class ShippedSpecBindingsDriftTest {

    /** Action ids any shipped spec may reference. Keep in sync with the ids registered in production wiring. */
    private static final Set<String> EXPECTED_ACTIONS = Set.of(
            "warp:set-sound",
            "warp:custom-sound",
            "warp:remove-sound",
            "warp:edit-back",
            "vault:open-slot",
            "communication:chat-lock",
            "communication:clearchat",
            "communication:broadcast",
            "communication:open-announcer",
            "communication:close",
            "messaging:unignore",
            "messaging:ignore-add",
            "messaging:read-mail",
            "messaging:clear-mail",
            "messaging:mail-back",
            "close",
            "itemworld:open-workbench",
            "itemworld:open-anvil",
            "itemworld:open-cartography",
            "itemworld:open-grindstone",
            "itemworld:open-loom",
            "itemworld:open-smithingtable",
            "itemworld:open-stonecutter",
            "itemworld:open-enderchest",
            "itemworld:time-day",
            "itemworld:time-night",
            "itemworld:weather-clear",
            "itemworld:weather-rain",
            "itemworld:clear-drops",
            "itemworld:clear-mobs",
            "staff:teleport-to",
            "staff:examine",
            "holograms:edit",
            "holograms:create",
            "playerwarps:edit",
            "playerwarps:create",
            "npc:edit",
            "npc:create",
            "worlds:edit",
            "worlds:teleport",
            "worlds:create",
            "homes:set-icon",
            "homes:reset-icon",
            "homes:icon-back",
            "homes:uninvite",
            "homes:invite-add",
            "homes:invites-back",
            "homes:teleport",
            "homes:delete",
            "homes:relocate",
            "homes:rename",
            "homes:open-icons",
            "homes:toggle-visibility",
            "homes:open-invites",
            "homes:action-back",
            "homes:open-slot",
            "moderation:open-detail",
            "moderation:release");

    /** Condition ids any shipped spec may reference. Keep in sync with the ids registered in production wiring. */
    private static final Set<String> EXPECTED_CONDITIONS =
            Set.of("perm", "has-prev", "has-next", "homes:home-public", "homes:home-private");

    /** Placeholder ids any shipped spec may reference. Keep in sync with the ids registered in production wiring. */
    private static final Set<String> EXPECTED_PLACEHOLDERS = Set.of(
            "sound",
            "sound_material",
            "vault_icon",
            "vault_name",
            "vault_lore",
            "communication_lock_state",
            "announcement_id",
            "announcement_lines",
            "announcement_channels",
            "ignore_target",
            "mail_icon",
            "mail_sender",
            "mail_time",
            "mail_snippet",
            "mail_message",
            "recipe_slot0_material",
            "recipe_slot0_name",
            "recipe_slot0_lore",
            "recipe_slot1_material",
            "recipe_slot1_name",
            "recipe_slot1_lore",
            "recipe_slot2_material",
            "recipe_slot2_name",
            "recipe_slot2_lore",
            "recipe_slot3_material",
            "recipe_slot3_name",
            "recipe_slot3_lore",
            "recipe_slot4_material",
            "recipe_slot4_name",
            "recipe_slot4_lore",
            "recipe_slot5_material",
            "recipe_slot5_name",
            "recipe_slot5_lore",
            "recipe_slot6_material",
            "recipe_slot6_name",
            "recipe_slot6_lore",
            "recipe_slot7_material",
            "recipe_slot7_name",
            "recipe_slot7_lore",
            "recipe_slot8_material",
            "recipe_slot8_name",
            "recipe_slot8_lore",
            "recipe_result_material",
            "recipe_result_name",
            "recipe_result_lore",
            "entity_icon",
            "entity_type",
            "entity_count",
            "entity_radius",
            "iw_station_workbench",
            "iw_station_anvil",
            "iw_station_cartography",
            "iw_station_grindstone",
            "iw_station_loom",
            "iw_station_smithingtable",
            "iw_station_stonecutter",
            "iw_station_enderchest",
            "staff_player_name",
            "hologram_name",
            "hologram_lines",
            "hologram_world",
            "hologram_x",
            "hologram_y",
            "hologram_z",
            "pwarp_icon",
            "pwarp_name",
            "pwarp_owner",
            "pwarp_world",
            "pwarp_x",
            "pwarp_y",
            "pwarp_z",
            "pwarp_visibility",
            "npc_icon",
            "npc_name",
            "npc_type",
            "npc_world",
            "npc_x",
            "npc_y",
            "npc_z",
            "world_icon",
            "world_name",
            "world_environment",
            "world_loaded",
            "home_icon_name",
            "invited_player",
            "invited_player_lore",
            "home_name",
            "home_world",
            "home_x",
            "home_y",
            "home_z",
            "home_created",
            "home_icon",
            "home_cell_name",
            "home_cell_lore",
            "mod_active_icon",
            "mod_active_player",
            "mod_active_type",
            "mod_active_issuer",
            "mod_active_reason",
            "mod_active_remaining",
            "mod_history_icon",
            "mod_history_action",
            "mod_history_issuer",
            "mod_history_reason",
            "mod_history_at",
            "mod_jailed_player",
            "mod_jailed_jail",
            "mod_jailed_issuer",
            "mod_jailed_reason",
            "mod_jailed_remaining",
            "baltop_currency",
            "baltop_rank",
            "baltop_player",
            "baltop_amount",
            "history_title_key",
            "history_player",
            "history_bank",
            "history_icon",
            "history_type_key",
            "history_id",
            "history_date",
            "history_from",
            "history_to",
            "history_amount",
            "history_reason");

    /** List-source ids any shipped spec may reference. Keep in sync with the ids registered in production wiring. */
    private static final Set<String> EXPECTED_LISTS = Set.of(
            "warp:sound-options",
            "vault:slots",
            "communication:announcements",
            "messaging:ignores",
            "messaging:mail",
            "itemworld:entity-tally",
            "staff:players",
            "holograms:list",
            "playerwarps:list",
            "npc:list",
            "worlds:list",
            "homes:icon-palette",
            "homes:invited-players",
            "home:slots",
            "moderation:active",
            "moderation:history",
            "moderation:jailed",
            "economy:baltop",
            "economy:transactions");

    @Test
    void everyShippedSpecReferencesOnlyKnownBindingIds() {
        Path specsDir = repoRoot().resolve("bukkit-adapter/src/main/resources/modules/menu/specs");
        if (!Files.isDirectory(specsDir)) {
            // No spec ships yet — the pilot (Task 17) creates the directory and its first spec. Nothing to guard.
            return;
        }

        List<MenuSpec> specs = loadAll(specsDir);

        MenuBindings bindings = new MenuBindings();
        EXPECTED_ACTIONS.forEach(id -> bindings.action(id, ctx -> {}));
        EXPECTED_CONDITIONS.forEach(id -> bindings.condition(id, (ctx, args) -> true));
        EXPECTED_PLACEHOLDERS.forEach(id -> bindings.placeholder(id, ctx -> ""));
        EXPECTED_LISTS.forEach(id -> bindings.list(id, ctx -> List.of()));

        assertThat(bindings.validate(specs))
                .as("shipped menu specs reference binding ids not registered in production wiring; register them "
                        + "in the feature wiring (and add them to the matching EXPECTED_* set here), or fix "
                        + "the spec")
                .isEmpty();
    }

    /** Parses every {@code .conf} under {@code specsDir}; a parse failure propagates as the drift it is. */
    private static List<MenuSpec> loadAll(Path specsDir) {
        MenuSpecLoader loader = new MenuSpecLoader();
        List<MenuSpec> specs = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(specsDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".conf"))
                    .sorted()
                    .forEach(p -> specs.add(loader.load(p)));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return specs;
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("settings.gradle.kts"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not locate the repo root (settings.gradle.kts)");
    }
}
