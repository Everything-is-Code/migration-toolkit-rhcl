CREATE TABLE app_settings (
    key VARCHAR(255) PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

INSERT INTO app_settings (key, value) VALUES
    ('supportedPolicies', '["3scale APIcast","Upstream Connection"]');
