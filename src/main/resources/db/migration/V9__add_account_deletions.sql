-- Deletion ledger. A row is written in the same transaction as the library purge, so
-- "this account asked to be erased" survives whatever happens to the identity delete
-- that follows it in the gateway. identity_deleted_at stays NULL until the gateway
-- reports that the Keycloak user is gone; the retry job works off the NULL rows.
-- The row outlives the purge on purpose: it is the erasure record, and after the
-- identity is gone the UUID links to nothing. Same VARCHAR stand-in for the Keycloak
-- UUID as every other table here, no FK.

CREATE TABLE account_deletions (
    user_id VARCHAR(255) NOT NULL,
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    identity_deleted_at TIMESTAMP,
    CONSTRAINT account_deletions_pkey PRIMARY KEY (user_id)
);

-- The retry job only ever asks for unfinished rows, so index those alone.
CREATE INDEX idx_account_deletions_pending
    ON account_deletions (requested_at)
    WHERE identity_deleted_at IS NULL;
