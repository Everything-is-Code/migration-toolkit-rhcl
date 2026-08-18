package com.redhat.migrationtoolkit.rhcl.model;

import java.util.List;

public class Backend {
    public String id;
    public String name;
    public String description;
    public String systemName;
    public String privateEndpoint;
    /** Mount path from backend_usage; null/blank normalizes to "/". */
    public String path;
    /** Optional load-balancing weight when co-located backendRefs collide. */
    public Integer weight;
    public List<MappingRule> mappingRules;
    public List<Metric> metrics;
}
