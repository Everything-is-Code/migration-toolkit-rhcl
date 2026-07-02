-- Rename the key column to settings_key to avoid reserved word conflicts.
-- Applied as a new migration because V6 was already executed in production.
ALTER TABLE app_settings RENAME COLUMN "key" TO settings_key;
