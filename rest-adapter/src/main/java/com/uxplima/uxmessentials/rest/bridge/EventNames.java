package com.uxplima.uxmessentials.rest.bridge;

import java.util.Locale;

import com.uxplima.uxmessentials.api.bukkit.event.UxmEvent;

/**
 * What an event is called on the wire.
 *
 * <p>Derived from the class rather than written out beside it, because a name kept in a second list is a name that
 * eventually disagrees with the class it belongs to. The rule is short enough to hold in your head: the context is
 * the package the event lives in, and the rest is the class name with {@code Uxm} and {@code Event} taken off, the
 * context taken off the front when it is repeated there, and the words joined with hyphens.
 *
 * <p>{@code UxmWalletCreditEvent} in {@code event.economy} is {@code economy.wallet-credit}.
 * {@code UxmHomeCreateEvent} in {@code event.home} is {@code home.create}. Every name the rule produces is pinned in
 * a golden file, so a rename that changes one is a diff somebody has to agree to rather than a surprise for
 * whoever was subscribed to it.
 */
public final class EventNames {

    private static final String PREFIX = "Uxm";
    private static final String SUFFIX = "Event";

    private EventNames() {}

    /** The wire name of an event class. */
    public static String of(Class<? extends UxmEvent> type) {
        String context = contextOf(type);
        return context + "." + kebab(withoutContext(stemOf(type.getSimpleName()), context));
    }

    /** The last segment of the package, which is the bounded context the event belongs to. */
    private static String contextOf(Class<? extends UxmEvent> type) {
        String packageName = type.getPackageName();
        return packageName.substring(packageName.lastIndexOf('.') + 1);
    }

    /** The class name with the prefix and suffix every one of them carries taken off. */
    private static String stemOf(String simpleName) {
        String stem = simpleName;
        if (stem.startsWith(PREFIX)) {
            stem = stem.substring(PREFIX.length());
        }
        if (stem.endsWith(SUFFIX)) {
            stem = stem.substring(0, stem.length() - SUFFIX.length());
        }
        return stem;
    }

    /**
     * Drop the context from the front of the stem when it is there, so a name does not say it twice.
     *
     * <p>Unless dropping it would leave nothing: {@code UxmPoseEvent} in {@code event.pose} keeps its stem and is
     * {@code pose.pose}, which is odd to read but is at least a name.
     */
    private static String withoutContext(String stem, String context) {
        if (stem.length() <= context.length()) {
            return stem;
        }
        String start = stem.substring(0, context.length()).toLowerCase(Locale.ROOT);
        return start.equals(context) ? stem.substring(context.length()) : stem;
    }

    /** {@code WalletCredit} becomes {@code wallet-credit}. */
    static String kebab(String camel) {
        StringBuilder name = new StringBuilder();
        for (int at = 0; at < camel.length(); at++) {
            char letter = camel.charAt(at);
            if (Character.isUpperCase(letter) && at > 0) {
                name.append('-');
            }
            name.append(Character.toLowerCase(letter));
        }
        return name.toString();
    }
}
