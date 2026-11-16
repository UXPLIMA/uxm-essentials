package com.uxplima.uxmessentials.migration;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.messaging.domain.MailId;
import com.uxplima.uxmessentials.messaging.domain.MailItem;
import com.uxplima.uxmessentials.messaging.domain.MailSender;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.migration.convert.map.ImportedMail;
import com.uxplima.uxmessentials.migration.convert.map.ImportedUser;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The shared write path for an imported player: their homes, balance, mail and one-time kit claims, each through the
 * port its own context uses, so an import can never create a state {@code /sethome}, the ledger, {@code /mail send}
 * or a kit claim could not.
 *
 * <ul>
 *   <li><b>Homes</b> upsert by {@code (owner, slot)}. Under {@link ConflictPolicy#SKIP} an occupied slot keeps its
 *       home; the other policies replace it.
 *   <li><b>Balance</b> is set, never added, so a re-run cannot double-credit, and {@link BalancePolicy} decides
 *       whether a wallet that already holds money is touched.
 *   <li><b>Mail</b> has no key of its own: the mailbox is append-only. A message counts as already imported when
 *       the recipient's box holds one with the same sender, the same body and, when the source recorded a send
 *       time, the same time. Each stored message answers for one source message, so two identical lines in the
 *       source still land twice on the first run and never again. This holds under every policy, because
 *       "overwriting" a message would only append its twin. Mail the source had already expired is dropped, and
 *       with the messaging module off no mail is written at all.
 *   <li><b>Retention.</b> The mail sweep deletes mail older than {@code mail-retention-days}. Imported mail keeps
 *       its send time, so mail already past that age is not written: the next sweep would delete it and every
 *       re-run would import it again. It is held back and counted, and an operator who wants it raises the
 *       retention before importing.
 *   <li><b>Expiry.</b> The mailbox has no per-message expiry, so mail the source would still expire later is
 *       imported without one and lives as long as the retention allows.
 *   <li><b>Kit claims</b> are recorded only where none is, under every policy: a claim already recorded here is the
 *       same fact, and replacing it would change nothing a player can see. Like a message already in the box, one
 *       kept is not held back.
 * </ul>
 *
 * <p>The mail text is what a player typed. It reaches readers inside a MiniMessage template, so it goes through
 * {@code escapeText}, the same escaping a player without the colour permission gets on {@code /mail send}, and
 * is never read as markup. The escaping lives with the text renderer, which is why it is handed in.
 *
 * <p>The result reports what really happened: {@link RecordOutcome#SKIPPED} when nothing was written,
 * {@link RecordOutcome#PARTIAL} when some parts were held back, and notes that count each part for the audit
 * line. {@link #preview} runs the same decisions read-only for a dry run.
 */
@NullMarked
public final class UserRecordWriter {

    /** Mirrors the {@code mail.sender_name} column, so the name compared here is the name stored. */
    static final int SENDER_NAME_MAX = 16;

    private final HomeRepository homes;
    private final WalletRepository wallets;
    private final Optional<MailRepository> mail;
    private final KitClaimTarget kitClaims;
    private final Duration mailRetention;
    private final UnaryOperator<String> escapeText;
    private final Currency defaultCurrency;
    private final Clock clock;

    /**
     * Mail ids are the table's highest id plus one, so two writers appending at once could take the same id; the
     * import's writer threads take turns here.
     */
    private final Object appendLock = new Object();

    /**
     * @param homes the homes context's repository
     * @param wallets the economy ledger
     * @param mail the messaging context's mailbox store, empty when the messaging module is off
     * @param kitClaims where the one-time kits a player already claimed are recorded
     * @param mailRetention the age at which the mail sweep deletes mail; zero or negative when it never does
     * @param escapeText turns typed text into text that reads as written inside a MiniMessage template
     * @param defaultCurrency the currency an imported balance is paid in
     * @param clock the send time of mail the source did not date, and the instant expiry is judged at
     */
    public UserRecordWriter(
            HomeRepository homes,
            WalletRepository wallets,
            Optional<MailRepository> mail,
            KitClaimTarget kitClaims,
            Duration mailRetention,
            UnaryOperator<String> escapeText,
            Currency defaultCurrency,
            Clock clock) {
        this.homes = Objects.requireNonNull(homes, "homes");
        this.wallets = Objects.requireNonNull(wallets, "wallets");
        this.mail = Objects.requireNonNull(mail, "mail");
        this.kitClaims = Objects.requireNonNull(kitClaims, "kitClaims");
        this.mailRetention = Objects.requireNonNull(mailRetention, "mailRetention");
        this.escapeText = Objects.requireNonNull(escapeText, "escapeText");
        this.defaultCurrency = Objects.requireNonNull(defaultCurrency, "defaultCurrency");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Write {@code user} under {@code options} and report what landed and what was held back. */
    public RecordResult write(ImportedUser user, ImportOptions options) {
        return apply(user, options, true);
    }

    /** Report what {@link #write} would do, writing nothing: the dry-run path. */
    public RecordResult preview(ImportedUser user, ImportOptions options) {
        return apply(user, options, false);
    }

    private RecordResult apply(ImportedUser user, ImportOptions options, boolean live) {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(options, "options");
        Tally tally = new Tally();
        writeHomes(user, options.onConflict(), live, tally);
        user.balance().ifPresent(balance -> writeBalance(user.owner(), balance, options.balancePolicy(), live, tally));
        writeMail(user, live, tally);
        writeKitClaims(user, live, tally);
        return tally.result();
    }

    private void writeHomes(ImportedUser user, ConflictPolicy policy, boolean live, Tally tally) {
        if (user.homes().isEmpty()) {
            return;
        }
        HomeSet existing = homes.load(user.owner());
        for (Home home : user.homes()) {
            boolean occupied = existing.find(home.slot()).isPresent();
            if (occupied && policy == ConflictPolicy.SKIP) {
                tally.homesKept++;
                continue;
            }
            if (live) {
                homes.save(home);
            }
            tally.homesWritten++;
            if (occupied) {
                tally.replaced++;
            }
        }
    }

    private void writeBalance(PlayerRef owner, BigDecimal raw, BalancePolicy policy, boolean live, Tally tally) {
        boolean present = hasBalance(owner);
        if (present && policy == BalancePolicy.SKIP_IF_PRESENT) {
            tally.balance = "kept";
            return;
        }
        if (live) {
            wallets.ensureOwner(owner);
            wallets.upsertBalance(owner, Money.of(defaultCurrency, raw));
        }
        tally.balance = "written";
        if (present) {
            tally.replaced++;
        }
    }

    private boolean hasBalance(PlayerRef owner) {
        return wallets.findByOwner(owner)
                .map(wallet -> wallet.balanceOf(defaultCurrency).amount().signum() != 0)
                .orElse(false);
    }

    private void writeMail(ImportedUser user, boolean live, Tally tally) {
        if (user.mail().isEmpty()) {
            return;
        }
        if (mail.isEmpty()) {
            tally.mailDisabled = user.mail().size();
            return;
        }
        MailRepository store = mail.get();
        Instant now = clock.instant();
        List<MailItem> unclaimed = new ArrayList<>(store.load(user.owner()).items());
        for (ImportedMail imported : user.mail()) {
            if (imported.expiresAt().filter(expiry -> !expiry.isAfter(now)).isPresent()) {
                tally.mailExpired++;
                continue;
            }
            if (pastRetention(imported, now)) {
                tally.mailPastRetention++;
                continue;
            }
            MailItem item = toItem(user.owner(), imported, now);
            if (claim(unclaimed, item, imported.sentAt().isPresent())) {
                tally.mailKept++;
                continue;
            }
            if (live) {
                synchronized (appendLock) {
                    store.append(item);
                }
            }
            tally.mailWritten++;
        }
    }

    private void writeKitClaims(ImportedUser user, boolean live, Tally tally) {
        if (user.kitClaims().isEmpty()) {
            return;
        }
        tally.kitClaimsKnown = true;
        Set<KitId> recorded = kitClaims.claimed(user.owner());
        for (Map.Entry<KitId, Instant> claim : user.kitClaims().entrySet()) {
            boolean landed = !recorded.contains(claim.getKey())
                    && (!live || kitClaims.record(user.owner(), claim.getKey(), claim.getValue()));
            if (landed) {
                tally.kitClaimsWritten++;
            } else {
                tally.kitClaimsKept++;
            }
        }
    }

    private boolean pastRetention(ImportedMail imported, Instant now) {
        if (mailRetention.isZero() || mailRetention.isNegative()) {
            return false;
        }
        return imported.sentAt()
                .filter(sent -> !sent.plus(mailRetention).isAfter(now))
                .isPresent();
    }

    private MailItem toItem(PlayerRef recipient, ImportedMail imported, Instant now) {
        MailSender sender = new MailSender(imported.senderId(), fitEscaped(imported.senderName(), SENDER_NAME_MAX));
        MessageBody body = MessageBody.of(fitEscaped(imported.text(), MessageBody.MAX_LENGTH));
        return new MailItem(
                MailId.UNASSIGNED, recipient, sender, body, imported.sentAt().orElse(now), imported.read());
    }

    /**
     * {@code text} escaped and shortened until it fits {@code max}. The raw text is cut, never the escaped text, so
     * an escape is never split from the character it guards. Escaping only lengthens text, so the raw text is cut
     * to {@code max} first and then by at least half the overflow per pass, which never cuts more than needed.
     */
    private String fitEscaped(String text, int max) {
        String raw = cut(text, max);
        String escaped = escapeText.apply(raw);
        while (escaped.length() > max) {
            raw = cut(raw, raw.length() - Math.max(1, (escaped.length() - max + 1) / 2));
            escaped = escapeText.apply(raw);
        }
        return escaped;
    }

    /** The first {@code length} chars of {@code text}, one fewer when the cut would split a surrogate pair. */
    private static String cut(String text, int length) {
        if (text.length() <= length) {
            return text;
        }
        int end = Math.max(0, length);
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    /** Take the first stored message that answers for {@code item}, so it cannot answer for another. */
    private static boolean claim(List<MailItem> unclaimed, MailItem item, boolean dated) {
        Iterator<MailItem> candidates = unclaimed.iterator();
        while (candidates.hasNext()) {
            MailItem stored = candidates.next();
            if (stored.sender().equals(item.sender())
                    && stored.body().equals(item.body())
                    && (!dated || stored.sentAt().equals(item.sentAt()))) {
                candidates.remove();
                return true;
            }
        }
        return false;
    }

    /** Counts of each part of one player, folded into the outcome and the audit notes. */
    private static final class Tally {
        private int homesWritten;
        private int homesKept;
        private String balance = "none";
        private int mailWritten;
        private int mailKept;
        private int mailExpired;
        private int mailDisabled;
        private int mailPastRetention;
        private boolean kitClaimsKnown;
        private int kitClaimsWritten;
        private int kitClaimsKept;
        private int replaced;

        RecordResult result() {
            List<String> notes = new ArrayList<>();
            notes.add("homes_written=" + homesWritten);
            notes.add("homes_kept=" + homesKept);
            notes.add("balance=" + balance);
            notes.add("mail_written=" + mailWritten);
            notes.add("mail_kept=" + mailKept);
            if (mailExpired > 0) {
                notes.add("mail_expired=" + mailExpired);
            }
            if (mailPastRetention > 0) {
                notes.add("mail_past_retention=" + mailPastRetention);
            }
            if (mailDisabled > 0) {
                notes.add("mail_skipped=messaging_disabled");
            }
            if (kitClaimsKnown) {
                notes.add("kit_claims_written=" + kitClaimsWritten);
                notes.add("kit_claims_kept=" + kitClaimsKept);
            }
            return new RecordResult(outcome(), notes);
        }

        /**
         * Mail already in the box, mail the source had expired and a kit claim already recorded are not held back:
         * nothing of the source is lost by leaving them. A kept home, a kept balance, mail past the retention and mail the messaging module
         * could not take are.
         */
        private RecordOutcome outcome() {
            int written = homesWritten + mailWritten + kitClaimsWritten + ("written".equals(balance) ? 1 : 0);
            int heldBack = homesKept + mailDisabled + mailPastRetention + ("kept".equals(balance) ? 1 : 0);
            if (written == 0) {
                return RecordOutcome.SKIPPED;
            }
            if (heldBack > 0) {
                return RecordOutcome.PARTIAL;
            }
            return replaced > 0 ? RecordOutcome.OVERWRITTEN : RecordOutcome.WRITTEN;
        }
    }
}
