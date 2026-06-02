package com.uxplima.uxmessentials.playerstate.adapter;

import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.application.Burn;
import com.uxplima.uxmessentials.playerstate.application.ClearInventory;
import com.uxplima.uxmessentials.playerstate.application.Extinguish;
import com.uxplima.uxmessentials.playerstate.application.Feed;
import com.uxplima.uxmessentials.playerstate.application.Heal;
import com.uxplima.uxmessentials.playerstate.application.ListNearby;
import com.uxplima.uxmessentials.playerstate.application.ResetRest;
import com.uxplima.uxmessentials.playerstate.application.SetAir;
import com.uxplima.uxmessentials.playerstate.application.SetExperience;
import com.uxplima.uxmessentials.playerstate.application.SetFoodLevel;
import com.uxplima.uxmessentials.playerstate.application.SetGamemode;
import com.uxplima.uxmessentials.playerstate.application.SetHealth;
import com.uxplima.uxmessentials.playerstate.application.SetPersonalTime;
import com.uxplima.uxmessentials.playerstate.application.SetPersonalWeather;
import com.uxplima.uxmessentials.playerstate.application.SetSpeed;
import com.uxplima.uxmessentials.playerstate.application.ShowPing;
import com.uxplima.uxmessentials.playerstate.application.ShowPosition;
import com.uxplima.uxmessentials.playerstate.application.Suicide;
import com.uxplima.uxmessentials.playerstate.application.ToggleFly;
import com.uxplima.uxmessentials.playerstate.application.ToggleGod;
import com.uxplima.uxmessentials.playerstate.application.ToggleNightVision;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import org.jspecify.annotations.NullMarked;

/**
 * The constructed playerstate use cases the Brigadier commands share, built once per module start by
 * {@code PlayerstateWiring} from the kernel ports and the context's own in-memory store, reconciler, effects
 * bridge, and nearby scan. Held so every command reads the same use cases; the context's only adapter-side
 * runtime state is the in-memory snapshot map (cleared on stop), so there is nothing else to drain.
 *
 * @param toggleGod {@code /god}
 * @param toggleFly {@code /fly}
 * @param heal {@code /heal}
 * @param feed {@code /feed}
 * @param foodLevel {@code /foodlevel}
 * @param health {@code /health}
 * @param setGamemode {@code /gamemode} and its {@code /gmc /gms /gma /gmsp} aliases
 * @param setSpeed {@code /speed}, {@code /walkspeed}, {@code /flyspeed}
 * @param extinguish {@code /ext} ({@code /extinguish})
 * @param clearInventory {@code /clearinventory} ({@code /ci}, {@code /clear})
 * @param suicide {@code /suicide}
 * @param listNearby {@code /near}
 * @param toggleNightVision {@code /nightvision} ({@code /nv})
 * @param personalTime {@code /ptime}
 * @param personalWeather {@code /pweather}
 * @param experience {@code /exp} ({@code /xp})
 * @param air {@code /air}
 * @param burn {@code /burn}
 * @param showPosition {@code /getpos} ({@code /coords}, {@code /whereami})
 * @param showPing {@code /ping}
 * @param resetRest {@code /rest}
 * @param players name → ref resolution for the {@code .others} targets
 */
@NullMarked
public record PlayerStateServices(
        ToggleGod toggleGod,
        ToggleFly toggleFly,
        Heal heal,
        Feed feed,
        SetFoodLevel foodLevel,
        SetHealth health,
        SetGamemode setGamemode,
        SetSpeed setSpeed,
        Extinguish extinguish,
        ClearInventory clearInventory,
        Suicide suicide,
        ListNearby listNearby,
        ToggleNightVision toggleNightVision,
        SetPersonalTime personalTime,
        SetPersonalWeather personalWeather,
        SetExperience experience,
        SetAir air,
        Burn burn,
        ShowPosition showPosition,
        ShowPing showPing,
        ResetRest resetRest,
        PlayerLookup players) {

    public PlayerStateServices {
        Objects.requireNonNull(toggleGod, "toggleGod");
        Objects.requireNonNull(toggleFly, "toggleFly");
        Objects.requireNonNull(heal, "heal");
        Objects.requireNonNull(feed, "feed");
        Objects.requireNonNull(foodLevel, "foodLevel");
        Objects.requireNonNull(health, "health");
        Objects.requireNonNull(setGamemode, "setGamemode");
        Objects.requireNonNull(setSpeed, "setSpeed");
        Objects.requireNonNull(extinguish, "extinguish");
        Objects.requireNonNull(clearInventory, "clearInventory");
        Objects.requireNonNull(suicide, "suicide");
        Objects.requireNonNull(listNearby, "listNearby");
        Objects.requireNonNull(toggleNightVision, "toggleNightVision");
        Objects.requireNonNull(personalTime, "personalTime");
        Objects.requireNonNull(personalWeather, "personalWeather");
        Objects.requireNonNull(experience, "experience");
        Objects.requireNonNull(air, "air");
        Objects.requireNonNull(burn, "burn");
        Objects.requireNonNull(showPosition, "showPosition");
        Objects.requireNonNull(showPing, "showPing");
        Objects.requireNonNull(resetRest, "resetRest");
        Objects.requireNonNull(players, "players");
    }
}
