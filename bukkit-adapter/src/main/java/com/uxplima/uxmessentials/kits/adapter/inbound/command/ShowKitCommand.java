package com.uxplima.uxmessentials.kits.adapter.inbound.command;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.kits.adapter.KitServices;
import com.uxplima.uxmessentials.kits.adapter.outbound.KitItemCodec;
import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.kits.domain.KitItem;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /showkit <name>}: preview a kit's contents without claiming it ({@code uxmessentials.kit.preview}).
 * The {@link com.uxplima.uxmessentials.kits.application.ShowKit} use case resolves the kit (and answers a
 * missing id), then returns the definition; the header and one entry per stack are rendered here because the
 * per-item line needs the stack's display or material name, which is decoded from the opaque
 * {@link KitItem} payload with the same {@link KitItemCodec} the claim path uses — the kernel never parses it.
 */
@NullMarked
public final class ShowKitCommand extends KitCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.kit.preview";
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    public ShowKitCommand(KitServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("showkit")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(kitNameArgument("name").executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return "Preview a kit's contents without claiming it.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        KitId id = KitId.of(ctx.getArgument("name", String.class));
        services.showKit().show(ref(sender), id).asValue().ifPresent(definition -> renderPreview(sender, definition));
        return Command.SINGLE_SUCCESS;
    }

    private void renderPreview(Player sender, KitDefinition definition) {
        feedback.send(
                sender,
                KitsMessageKey.KIT_PREVIEW_HEADER,
                Map.of("kit", definition.id().value()));
        List<KitItem> items = definition.items();
        for (int i = 0; i < items.size(); i++) {
            KitItem item = items.get(i);
            feedback.send(
                    sender,
                    KitsMessageKey.KIT_PREVIEW_ENTRY,
                    Map.of(
                            "slot", Integer.toString(i + 1),
                            "amount", Integer.toString(item.amount()),
                            "item", itemName(item)));
        }
    }

    /** A readable name for a kit item: its custom display name when set, else the prettified material name. */
    private static String itemName(KitItem item) {
        ItemStack stack = KitItemCodec.decode(item);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            Component name = meta.displayName();
            if (name != null) {
                return PLAIN.serialize(name);
            }
        }
        return prettyMaterial(stack);
    }

    /** Title-cased material name, e.g. {@code DIAMOND_SWORD} reads "Diamond Sword". */
    private static String prettyMaterial(ItemStack stack) {
        String key = stack.getType().getKey().getKey().toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(key.length());
        boolean wordStart = true;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '_') {
                out.append(' ');
                wordStart = true;
            } else {
                out.append(wordStart ? Character.toUpperCase(c) : c);
                wordStart = false;
            }
        }
        return out.toString();
    }
}
