package com.redhat.migrationtoolkit.rhcl.model;

import java.util.List;

public class ApiService {
    public String id;
    public String name;
    public String description;
    public String state;
    public String systemName;
    public String backendVersion;
    public String deploymentOption;

    public List<Backend> backends;
    public List<MappingRule> mappingRules;
    public List<Metric> metrics;
    public List<Policy> policies;
    public Authentication authentication;
    public String proxyEndpoint;
    public String apicastProductionEndpoint;
    public String apicastStagingEndpoint;

    /** Applications + keys from Admin API (App ID/App Key auth). */
    public List<Application> applications;
    /** Application plans + limits (used by RateLimit in PR3; may be stubbed empty in PR2). */
    public List<ApplicationPlan> applicationPlans;
}
