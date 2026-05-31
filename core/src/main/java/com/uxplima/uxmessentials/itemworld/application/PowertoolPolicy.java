package com.uxplima.uxmessentials.itemworld.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.itemworld.domain.ItemQuery;
import com.uxplima.uxmessentials.itemworld.domain.ItemWorldError;
import com.uxplima.uxmessentials.itemworld.domain.PowertoolBinding;
import com.uxplima.uxmessentials.shared.domain.Result;

/**
 * The powertool bind policy: build a {@link PowertoolBinding} for {@code /powertool <command>} from the held
 * item and the command line the actor typed. {@code /powertool} with no command on a bound item clears the
 * binding; the empty hand is rejected with {@link ItemWorldError#NO_ITEM_IN_HAND}.
 *
 * <p>The held item is identified by its normalised {@code namespace:path} id (the adapter resolves the live
 * item to that id and hands it here), so the binding is keyed the same way an {@link ItemQuery} is. The
 * stored binding itself is transient runtime state the adapter holds (a PDC stamp or a per-player map); this
 * policy only validates and shapes the binding value.
 */
public final class PowertoolPolicy {

    /**
     * Build a binding for the held item: {@code rawCommand} present binds it, empty/blank clears it. The
     * {@code heldItemKey} is the adapter-resolved {@code namespace:path} id of the item in hand, empty when
     * the hand is empty (rejected with {@link ItemWorldError#NO_ITEM_IN_HAND}).
     */
    public Result<PowertoolBinding, ItemWorldError> bind(Optional<String> heldItemKey, String rawCommand) {
        Objects.requireNonNull(heldItemKey, "heldItemKey");
        if (heldItemKey.isEmpty() || heldItemKey.get().isBlank()) {
            return Result.err(ItemWorldError.NO_ITEM_IN_HAND);
        }
        String itemKey = heldItemKey.get();
        if (rawCommand == null || rawCommand.isBlank()) {
            return Result.ok(PowertoolBinding.cleared(itemKey));
        }
        return Result.ok(PowertoolBinding.single(itemKey, rawCommand));
    }
}
