-- Migration script to add cost_currency column to warps table.
ALTER TABLE warps ADD COLUMN cost_currency VARCHAR(36) DEFAULT 'default' NOT NULL;
