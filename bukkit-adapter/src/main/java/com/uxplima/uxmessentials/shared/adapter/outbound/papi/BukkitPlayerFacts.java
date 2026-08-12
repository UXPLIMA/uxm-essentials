package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@link PlayerFactsPlaceholders} over the live {@link Server}.
 *
 * <p>Every read is a plain accessor on the player or their profile. They are taken on the PlaceholderAPI thread
 * rather than marshalled onto a region tick, for the same reason the server metrics are: no region owns the
 * caller, and a torn read of a ping or a crouch flag is at worst one refresh stale and corrects itself on the
 * next one. Nothing here mutates.
 *
 * <p>Item text is flattened to plain text before it leaves this class: a display name or a lore line is operator
 * or player authored, and it must never reach a MiniMessage parser as markup.
 */
@NullMarked
public final class BukkitPlayerFacts implements PlayerFactsPlaceholders {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    /** The vanilla play statistic counts ticks despite its name, so a tick is 50ms. */
    private static final long MILLIS_PER_TICK = 50L;

    private final Server server;

    public BukkitPlayerFacts(Server server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public Optional<Session> session(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = server.getPlayer(who.uuid());
        if (player == null) {
            return Optional.empty();
        }
        World world = player.getWorld();
        return Optional.of(new Session(
                player.getPing(),
                player.isSneaking(),
                player.isSprinting(),
                player.isOp(),
                world.getName(),
                world.getTime(),
                world.hasStorm(),
                world.isThundering(),
                player.getLevel(),
                player.getTotalExperience(),
                player.getExpToLevel(),
                player.getExp()));
    }

    @Override
    public Optional<Account> account(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        OfflinePlayer account = server.getOfflinePlayer(who.uuid());
        if (!account.hasPlayedBefore() && !account.isOnline()) {
            return Optional.empty();
        }
        return Optional.of(new Account(
                instantOf(account.getFirstPlayed()),
                account.isOnline() ? Optional.empty() : instantOf(account.getLastSeen()),
                playtime(account),
                account.isBanned()));
    }

    @Override
    public Optional<HeldItem> held(PlayerRef who, Hand hand) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(hand, "hand");
        Player player = server.getPlayer(who.uuid());
        if (player == null) {
            return Optional.empty();
        }
        ItemStack item = hand == Hand.MAIN
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
        if (item.getType().isAir()) {
            return Optional.empty();
        }
        return Optional.of(describe(item));
    }

    @Override
    public OptionalInt itemCount(PlayerRef who, String material) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(material, "material");
        Material type = Material.matchMaterial(material);
        Player player = server.getPlayer(who.uuid());
        if (type == null || player == null) {
            return OptionalInt.empty();
        }
        int held = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == type) {
                held += stack.getAmount();
            }
        }
        return OptionalInt.of(held);
    }

    @Override
    public Optional<Position> position(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = server.getPlayer(who.uuid());
        if (player == null) {
            return Optional.empty();
        }
        // Read straight off the entity rather than through a Location, which allocates a copy per call and
        // carries a nullable world reference of its own.
        return Optional.of(new Position(player.getWorld().getName(), player.getX(), player.getY(), player.getZ()));
    }

    @Override
    public Optional<Identity> identity(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = server.getPlayer(who.uuid());
        if (player == null) {
            return Optional.of(new Identity(
                    who.name(),
                    who.name(),
                    who.uuid().toString(),
                    Optional.empty(),
                    "",
                    "",
                    false,
                    false,
                    0f,
                    0f,
                    Optional.empty(),
                    Optional.empty()));
        }
        InetSocketAddress address = player.getAddress();
        return Optional.of(new Identity(
                player.getName(),
                PLAIN.serialize(player.displayName()),
                player.getUniqueId().toString(),
                Optional.ofNullable(address).map(InetSocketAddress::getHostString),
                player.locale().toString().toLowerCase(Locale.ROOT),
                player.getGameMode().name().toLowerCase(Locale.ROOT),
                player.isFlying(),
                player.getAllowFlight(),
                player.getFlySpeed(),
                player.getWalkSpeed(),
                at(player.getRespawnLocation()),
                at(player.getCompassTarget())));
    }

    @Override
    public Optional<Vitals> vitals(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = server.getPlayer(who.uuid());
        if (player == null) {
            return Optional.empty();
        }
        return Optional.of(new Vitals(
                player.getHealth(),
                attribute(player, Attribute.MAX_HEALTH, player.getHealth()),
                player.getFoodLevel(),
                player.getSaturation(),
                player.getRemainingAir(),
                player.getMaximumAir(),
                attribute(player, Attribute.ARMOR, 0d),
                player.getAbsorptionAmount(),
                player.getFireTicks() > 0));
    }

    @Override
    public Optional<Where> where(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = server.getPlayer(who.uuid());
        if (player == null) {
            return Optional.empty();
        }
        World world = player.getWorld();
        int x = (int) Math.floor(player.getX());
        int y = (int) Math.floor(player.getY());
        int z = (int) Math.floor(player.getZ());
        Block below = world.getBlockAt(x, y - 1, z);
        return Optional.of(new Where(
                world.getName(),
                world.getEnvironment().name().toLowerCase(Locale.ROOT),
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYaw(),
                player.getPitch(),
                world.getBiome(x, y, z).getKey().getKey(),
                below.getType().getKey().getKey(),
                world.getBlockAt(x, y, z).getLightLevel()));
    }

    @Override
    public OptionalLong statistic(PlayerRef who, String statistic, String qualifier) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(statistic, "statistic");
        Objects.requireNonNull(qualifier, "qualifier");
        Statistic stat = named(statistic);
        if (stat == null) {
            return OptionalLong.empty();
        }
        OfflinePlayer account = server.getOfflinePlayer(who.uuid());
        try {
            return OptionalLong.of(read(account, stat, qualifier));
        } catch (IllegalArgumentException | UnsupportedOperationException mismatch) {
            // A statistic asked for with the wrong kind of qualifier, or an account with no statistics file:
            // an unanswerable key rather than a failed line.
            return OptionalLong.empty();
        }
    }

    /** Read one statistic, completing the block/item/entity kinds with the qualifier the key carried. */
    private static long read(OfflinePlayer account, Statistic stat, String qualifier) {
        return switch (stat.getType()) {
            case UNTYPED -> account.getStatistic(stat);
            case ENTITY -> account.getStatistic(stat, entity(qualifier));
            case BLOCK, ITEM -> account.getStatistic(stat, material(qualifier));
        };
    }

    private static @Nullable Statistic named(String statistic) {
        try {
            return Statistic.valueOf(statistic.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static EntityType entity(String qualifier) {
        return EntityType.valueOf(qualifier.toUpperCase(Locale.ROOT));
    }

    private static Material material(String qualifier) {
        Material material = Material.matchMaterial(qualifier);
        if (material == null) {
            throw new IllegalArgumentException("no such material: " + qualifier);
        }
        return material;
    }

    /** One attribute's value, or {@code fallback} on a mob attribute this entity does not carry. */
    private static double attribute(Player player, Attribute attribute, double fallback) {
        AttributeInstance instance = player.getAttribute(attribute);
        return instance == null ? fallback : instance.getValue();
    }

    private static Optional<Position> at(@Nullable Location location) {
        if (location == null) {
            return Optional.empty();
        }
        World world = location.getWorld();
        return Optional.of(
                new Position(world == null ? "" : world.getName(), location.getX(), location.getY(), location.getZ()));
    }

    private static HeldItem describe(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        String type = item.getType().getKey().getKey();
        return new HeldItem(
                type,
                displayName(meta, type),
                item.getAmount(),
                meta instanceof Damageable damageable ? damageable.getDamage() : 0,
                item.getType().getMaxDurability(),
                enchantments(item),
                lore(meta),
                model(meta));
    }

    private static String displayName(@Nullable ItemMeta meta, String fallback) {
        if (meta == null) {
            return fallback;
        }
        Component name = meta.displayName();
        return name == null ? fallback : PLAIN.serialize(name);
    }

    private static List<String> enchantments(ItemStack item) {
        List<String> named = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
            named.add(entry.getKey().getKey().getKey() + " " + entry.getValue());
        }
        return List.copyOf(named);
    }

    private static List<String> lore(@Nullable ItemMeta meta) {
        if (meta == null) {
            return List.of();
        }
        List<Component> lines = meta.lore();
        if (lines == null) {
            return List.of();
        }
        List<String> plain = new ArrayList<>(lines.size());
        for (Component line : lines) {
            plain.add(PLAIN.serialize(line));
        }
        return List.copyOf(plain);
    }

    /** The component API carries a list of floats; a placeholder wants the first one, the way a resource pack reads it. */
    private static OptionalInt model(@Nullable ItemMeta meta) {
        if (meta == null) {
            return OptionalInt.empty();
        }
        List<Float> floats = meta.getCustomModelDataComponent().getFloats();
        return floats.isEmpty()
                ? OptionalInt.empty()
                : OptionalInt.of(floats.get(0).intValue());
    }

    private static Duration playtime(OfflinePlayer account) {
        try {
            return Duration.ofMillis(account.getStatistic(Statistic.PLAY_ONE_MINUTE) * MILLIS_PER_TICK);
        } catch (IllegalArgumentException | UnsupportedOperationException unavailable) {
            // A profile the server holds no statistics file for reports nothing rather than failing the whole line.
            return Duration.ZERO;
        }
    }

    /** A zero timestamp is Bukkit's "never", which is an absent value rather than the epoch. */
    private static Optional<Instant> instantOf(long millis) {
        return millis <= 0 ? Optional.empty() : Optional.of(Instant.ofEpochMilli(millis));
    }
}
