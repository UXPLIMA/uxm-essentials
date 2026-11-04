package com.uxplima.uxmessentials.guard.adapter.inbound.listener;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import com.uxplima.uxmessentials.guard.adapter.GuardRuntime;
import com.uxplima.uxmessentials.guard.adapter.Selections;
import com.uxplima.uxmessentials.guard.application.GuardMessageKey;
import com.uxplima.uxmessentials.guard.domain.Bounds;
import com.uxplima.uxmessentials.guard.domain.Region;

/**
 * Keeps WorldEdit off locked regions (H02): an editing command whose selection touches a locked region, or whose
 * paste lands at a player standing in one, is refused unless the player holds the lock permission. Registered
 * only while WorldEdit or FAWE is installed and some region is locked.
 */
public final class LockListener implements Listener {

    /** WorldEdit commands that change blocks, without their slashes. */
    private static final Set<String> EDITING = Set.of(
            "set",
            "replace",
            "rep",
            "overlay",
            "walls",
            "outline",
            "faces",
            "center",
            "naturalize",
            "smooth",
            "move",
            "stack",
            "regen",
            "deform",
            "hollow",
            "fill",
            "fillr",
            "drain",
            "fixwater",
            "fixlava",
            "removeabove",
            "removebelow",
            "removenear",
            "replacenear",
            "snow",
            "thaw",
            "green",
            "extinguish",
            "cut",
            "paste",
            "rotate",
            "flip",
            "sphere",
            "hsphere",
            "cyl",
            "hcyl",
            "pyramid",
            "hpyramid",
            "forest",
            "forestgen",
            "flora",
            "generate",
            "gen",
            "g",
            "line",
            "curve",
            "brush",
            "br",
            "undo",
            "redo",
            "drawsel");

    private final GuardRuntime guard;
    private final Selections selections;

    public LockListener(GuardRuntime guard, Selections selections) {
        this.guard = Objects.requireNonNull(guard, "guard");
        this.selections = Objects.requireNonNull(selections, "selections");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(GuardRuntime.LOCK_PERMISSION)) {
            return;
        }
        String message = event.getMessage();
        if (!message.startsWith("//") && !message.startsWith("/worldedit:")) {
            return;
        }
        String word =
                message.replaceFirst("^/(worldedit:)?/?", "").split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (!EDITING.contains(word)) {
            return;
        }
        Optional<Bounds> area = selections.worldEditBounds(player);
        String world = player.getWorld().getName();
        boolean touches = area.isPresent() && touchesLocked(world, area.get());
        Location at = player.getLocation();
        if (!touches && (word.equals("paste") || word.equals("undo") || word.equals("redo")) && at != null) {
            touches = touchesLocked(
                    world,
                    new Bounds(
                            at.getBlockX() - 64,
                            at.getBlockY() - 64,
                            at.getBlockZ() - 64,
                            at.getBlockX() + 64,
                            at.getBlockY() + 64,
                            at.getBlockZ() + 64));
        }
        if (touches) {
            event.setCancelled(true);
            guard.tell(player, GuardMessageKey.ERROR_REGION_LOCKED, Map.of());
        }
    }

    private boolean touchesLocked(String world, Bounds area) {
        for (Region region : guard.service().regions(world)) {
            if (region.locked() && region.shape().bounds().intersects(area)) {
                return true;
            }
        }
        return false;
    }
}
