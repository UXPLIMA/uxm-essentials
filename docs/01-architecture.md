# 1. Architecture

The layers, the modules that sit in them, and the invariants that cost something when they
are broken. It is written from the code that cites it. `10-feature-modules.md` holds what a
feature module is and how to add one; this document holds the shape they all sit in.

## 1.1 Hexagonal, with the domain in the middle

Four layers, and the dependency arrow only ever points inward.

| Layer | Package | May name |
|---|---|---|
| Domain | `<context>/domain` | plain Java, and the other domain types of its own context |
| Application | `<context>/application`, ports in `application/port` | the domain, and its own ports |
| Adapter | `<context>/adapter/inbound`, `adapter/outbound` | the application, and whatever the outside thing is |
| Bootstrap | `bootstrap/` | everything, because it builds the graph |

The domain and the application name no `org.bukkit`, no `io.papermc`, no `net.kyori` and no
logging framework. `ArchitectureTest` fails the build over each of those, and over
`JavaPlugin` outside `bootstrap`, over any dependency on the Bukkit scheduler, and over an
API module reaching into internals.

Wiring is by hand, through constructors, in the composition root. There is no container and
no static holder: `JavaPlugin.getInstance()` does not exist here, which is what lets every
class be built in a test with a fake.

## 1.2 The modules of the build

Ten Gradle modules, and the graph is as flat as the layering.

| Module | What it is |
|---|---|
| `:api` | what another plugin may ask uxmEssentials to do, one interface per context |
| `:bukkit-api` | the front door, the events other plugins listen to, and the menu registration surface |
| `:core` | the domain and the application of every context, plus the kernel they stand on |
| `:bukkit-adapter` | the Paper plugin: inbound and outbound adapters, the composition root |
| `:persistence-adapter` | jOOQ, the row mappings and the migrations |
| `:migration` | importing from the plugins a server is leaving |
| `:velocity-adapter` | the proxy jar, the bus broker and proxy command control |
| `:discord-adapter` | the Discord bridge jar |
| `:redis-adapter` | the Redis carrier for the bus |
| `:rest-adapter` | the published API over HTTP |

`:core` is pure Java. Everything a context needs from the outside is a port it declares, and
the adapter that implements it lives in another module entirely. The four companion jars are
real plugins rather than libraries, and each is optional.

## 1.3 A context is the unit, not a layer

The packages are cut by context first and by layer second: `homes/domain`,
`homes/application`, `homes/adapter`. A class that would have to live in two contexts belongs
in neither, and `shared` is where the kernel lives: the permission port, the scheduler port,
the message catalogue, the lookup of a player by name, the config store.

Two contexts talk through a port, never by reaching into each other's application. Teleport
does not read jail state; it asks a `JailGate` and takes `NEVER` when moderation is off.
Messaging does the same with `MutePolicy`. Degrading to "nobody is jailed" when the other
module is off is the pattern, and it is why a server can run any subset of the modules.

## 1.4 The published API is a layer too

`:api` is what a third-party plugin compiles against, and it is deliberately narrow. A surface
is obtained with the calling plugin, because every write is attributable: what the audit log
records is the caller's name, so an operator asking who moved the money gets an answer rather
than "the API". An action whose module is switched off answers with an empty optional rather
than throwing.

`:rest-adapter` compiles against `:api` and nothing else. An endpoint that cannot be written
without reaching into internals is one a third-party plugin could not write either, so the
missing piece is added to the published API rather than worked around in the add-on.

## 1.5 The anti-corruption layer at each edge

A type that belongs to something outside stops at the edge. `ItemStack` is the clearest case:
the vaults domain holds `VaultContents`, an opaque payload, and `VaultItemCodec` is the one
class in that context that touches an `ItemStack` at all. `:core` never sees one.

The same rule runs the other way at the database. `VaultRows` and `StaffLoadoutRows` are the
only places a row becomes a domain object, so the row shape is defined once and stays
identical on every backend.

## 1.6 Optional integrations

Every optional plugin is reached the same way: a present guard first, then the SDK strictly
past it. The guard is the load-bearing half, because a class that names an absent SDK fails to
link the moment it is loaded, whatever the config says.

So the seam is a class of its own. Nothing outside it names the other plugin's types, the
catalogue of integrations says which plugins exist, and `SoftDependSeamDriftTest` fails the
build when an SDK type appears on the wrong side of the guard. `/uxmess doctor` reports the
integrations that are installed and enabled, and warns about the one case that costs an
evening: a dependency the operator configured and the server cannot reach.

## 1.7 Where a decision about threads lives

Nothing in this document decides a thread. `02-concurrency.md` holds the four lanes, the
scheduler port and the enumerate-then-hop rule, and the persistence rules below assume it.

## 1.8 Persistence invariants

Five, and each is cheap to state and expensive to discover.

**Backend parity.** SQLite, MySQL and PostgreSQL run the same migrations and the same
generated jOOQ DSL. No backend gets a statement of its own, so a server that moves from the
embedded file to a network database gets the same behaviour rather than a second code path
nobody tested. SQLite is the default because a single server should never need a database
server; it marks itself single writer, so the pool is sized to one connection and WAL is on.

**Queryable facts are columns, opaque payload is a blob.** What a query has to filter, sort or
join on is a first-class column: the owner UUID as canonical 36 character text, the index, the
size, the last touched epoch millis. What only the server can interpret is payload, stored as
base64 of the adapter's already serialized bytes. A vault holding nothing writes no blob at
all: a null cell round-trips to empty contents and back to null.

**One place per translation.** A row becomes a domain object in exactly one class per table.
That is what keeps the shape identical across backends and what makes a schema change one
edit.

**A parent and its dependent write in one transaction.** The offline row upsert inserts the
user and the dependent row inside a single jOOQ transaction, so a foreign key can never
observe a missing parent. `Transactions` is the seam: the work commits when it returns and
rolls back when it throws, and jOOQ's `DataAccessException` is translated at the boundary so
no caller ever catches a jOOQ type.

**A transaction is short and holds no server call.** Transactions are millisecond scale and
never wrap a Bukkit API call. A transaction that waits on the main thread is a lock held for
as long as the server takes to answer.

## 1.9 What keeps this true

`ArchitectureTest` for the layering, because those are questions about class identity and
ArchUnit answers them from the bytecode. The drift guards in the same package for the rules
the bytecode cannot see. `05-testing.md` §16 says which kind a new rule needs.
