package com.redhat.migrationtoolkit.rhcl.model;

import java.util.List;
import java.util.Map;

/**
 * 3scale Application Plan with optional usage limits (populated in PR3).
 */
public class ApplicationPlan {
    public String id;
    public String name;
    public String systemName;
    /** Metric system name → limit values (e.g. minute/hour). Stubbed for PR3. */
    public List<Map<String, Object>> limits;
}
