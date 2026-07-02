ALTER TABLE conversion_history
    ADD COLUMN IF NOT EXISTS package_name VARCHAR(255);
