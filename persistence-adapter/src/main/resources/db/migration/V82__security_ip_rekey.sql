-- Re-key the security context's IP tokens (see V77 and V78). Both tables store a
-- one-way token instead of the address itself, but that token used to be a plain
-- SHA-256 of the address text. The whole IPv4 space is only about four billion
-- values, so anyone holding a copy of the database could hash their way through
-- it and read every address back out: the token was reversible in practice and
-- the "raw IP is never stored" promise did not hold. The adapter now keys the
-- token with the server's own secret.key, which is not in the database.
--
-- Tokens written under the old scheme cannot match one written under the new one,
-- and leaving them in place would keep exactly the recoverable data this change
-- exists to remove, so they go. The cost is one-time and visible: a player's
-- remembered device is forgotten (they verify once more on their next join) and
-- staff alt lookups start from this upgrade rather than carrying older links.
--
-- Same portability contract as V1-V81: plain DELETE statements every supported
-- dialect accepts, and no schema change, so the generated jOOQ classes are
-- unaffected.
DELETE FROM security_trusted;
DELETE FROM security_ip;
