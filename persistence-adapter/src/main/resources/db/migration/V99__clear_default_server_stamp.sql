-- Clear the shipped default server name from warps and player warps. A backend with the network off, or still on
-- the default network.server-name, recorded 'server-1' on the warps it set and claimed, so such a row may belong to
-- any backend on the database and points at a server that does not exist once that backend joins a network under its
-- own name. Backends now record no server in that case, and the backend that holds each warp's world claims it
-- again at its next networked start under a real name: warps take it back from 'unscoped' (the V94 sentinel), player
-- warps from NULL, which is all their claim reads. Homes and jail locations never recorded the default name.
-- Plain UPDATE statements run unchanged on SQLite, MySQL/MariaDB and PostgreSQL.

UPDATE ${prefix}player_warps SET server_id = NULL WHERE server_id = 'server-1';
UPDATE ${prefix}warps SET server_id = 'unscoped' WHERE server_id = 'server-1';
