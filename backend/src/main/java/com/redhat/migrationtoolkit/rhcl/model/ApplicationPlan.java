package com.redhat.migrationtoolkit.rhcl.model;

import java.util.List;
import java.util.Map;

/**
 * 3scale Application Plan with optional usage limits.
 */
public class ApplicationPlan {
    public String id;
    public String name;
    public String systemName;
    /** Normalized limit maps: period (minute/hour/...), value, optional metric_system_name. */
    public List<Map<String, Object>> limits;
}
