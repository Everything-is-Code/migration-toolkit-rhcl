UPDATE app_settings SET value = '["3scale APIcast","Upstream Connection","Logging","Header Modification"]',
    updated_at = NOW() WHERE settings_key = 'supportedPolicies';
