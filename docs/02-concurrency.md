# 2. Concurrency

Thirty one references in this repository's javadoc point here, and three guards cite the two
section numbers this document has to keep: §2.6 and §6.10. It is written from the code that
cites it: the `Scheduler` port in `:core`, `FeatureModule.stop()`, `BoundedAwait`, and the
three guards that fail the build when one of these rules is broken.

## 2.1 There is no main thread

On Folia a server tick belongs to the region that is ticking it. There is no single thread
that owns every entity, every world and every player, and every assumption that there is one
is a defect that compiles, passes its tests and throws on a real server.

Four threads answer for four different things:

| Lane | Owns |
|---|---|
| global | The roster, the time of day, the weather, anything server wide |
| region | A block, a location, a chunk, the entities standing in it |
| entity | One player or one mob: where they are and what they carry |
| async | Nothing of the server. A database, a file, a socket |

## 2.2 Everything is scheduled through the port

`Scheduler` in `:core` is the only way this plugin schedules anything. `BukkitScheduler` and
`BukkitRunnable` are forbidden, and so are `CompletableFuture.supplyAsync` and
`new Thread(...)`: `ForbiddenConcurrencyApiDriftTest` fails the build on any of them.

Each one picks a thread the plugin does not own. `supplyAsync` lands on the common
ForkJoinPool, which is sized for CPU work and shared with the whole JVM, so one blocking
database call parks a thread the rest of the server is using. `new Thread` creates something
nothing will ever shut down, so a reload leaks it and the next reload leaks another.
`BukkitRunnable` is worse than either here, because there is no single main thread for it to
return to.

Blocking work goes to an injected `Executor` that the module shuts down in `stop()`. Nothing
above the adapter names a thread at all.

## 2.3 The port is fire and forget

Every method returns `void` and there is no cancellation handle. That is deliberate and it was
found rather than designed: production callers either need no cancellation or model it in
their own state, the way the teleport warmup re-checks a cancelled flag each tick instead of
holding a handle.

It is also what lets a `FeatureModule.stop()` drain its own in flight work: there is no global
scheduler holding orphaned tasks on its behalf. A module that needs to bound its outstanding
work tracks it itself, with a `Phaser` or an `AtomicInteger`.

## 2.4 A wait always ends

`Future.get()` with no timeout is forbidden. `BoundedAwait.get(future, timeout, context)` is
the one way to wait: it cancels the future on a stall and throws with the context in the
message, so a stuck integration is a line an operator can read rather than a server that
stopped answering.

## 2.5 A module stops quiescent

`stop()` returns only once the module's own work is finished: it drains in flight async work
within a bounded wait and cancels every handle it opened. A clean stop leaves zero orphaned
tasks and zero in memory state, and touches no persisted row. This is what makes a reload
safe, because a reload is a stop and a start.

## 2.6 Enumerate on the global thread, then hop

**The roster and a whole world's entities may only be read on the global region thread.**
`Bukkit.getOnlinePlayers()` is coherent there and nowhere else, and `world.getEntities()`
hands back entities that each belong to their own region thread. Reading either on an
arbitrary thread and then mutating what comes back is the exact assumption Folia breaks.

`FoliaThreadingDriftTest` walks the production source and fails when a class enumerates
either without being on its allowlist. Four shapes are safe, and a new site must match one of
them:

- **global**, the read runs inside `scheduler.onGlobal(...)`, a globally scheduled task, or a
  `FeatureModule.stop()`, which Folia dispatches on the global region thread;
- **command**, the read runs in a Brigadier handler, which Paper and Folia dispatch on the
  global region thread;
- **off tick**, a PlaceholderAPI resolver or a Brigadier suggestion provider, which run off
  any tick and read only a count or a list of names, never a foreign player's live state;
- **render scan**, the enumerate then hop pattern of §6.10.

Anything that fits none of them is a Folia bug rather than an allowlist entry: marshal it
through `scheduler.onGlobal`, or hop per entity.

**The known hole.** The roster can travel through a `Supplier<Collection<Player>>`. The class
that creates the supplier is caught by the scan; the class that calls `get()` on it carries no
text for the scan to see. The two known consumers are listed in the guard, and a reviewer who
injects a new supplier of the roster must hand check the consuming class for the shape below.

## 6.10 The render scan

A loop that draws something for every player enumerates the roster on its own thread and then
**hops to each player's entity or region thread before touching that player**. No foreign
`Player` is mutated on the scanning thread, which is what §2.6 forbids; a shared display
entity anchored to one region is read on that region.

This is the pattern behind the tablist, the scoreboard and the holograms, and it is the reason
those classes appear on the allowlist rather than being rewritten: they already do the right
thing, and the guard's job is to make sure the next one does too.

## 2.7 Storage is never on a tick

A query belongs on the async lane. A read that a command needs answered now is a read that was
already in memory: the pattern is a cache filled at `AsyncPlayerPreLoginEvent`, which the
server itself fires off the main thread, and written behind. Where a write must be seen by the
next read, the write is the truth and the memory follows it, not the other way round.
