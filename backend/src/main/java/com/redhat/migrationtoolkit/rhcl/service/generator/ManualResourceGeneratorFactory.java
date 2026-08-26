package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.service.PolicyFinder;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionYamlSupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.PolicyConfigSupport;
import com.redhat.migrationtoolkit.rhcl.service.conversion.RateLimitSupport;

import java.util.ArrayList;
import java.util.List;

final class ManualResourceGeneratorFactory {

    private ManualResourceGeneratorFactory() {
    }

    static List<ResourceGenerator> create() {
        PolicyFinder policyFinder = new PolicyFinder();
        PolicyConfigSupport policyConfigSupport = new PolicyConfigSupport();
        ConversionYamlSupport yamlSupport = new ConversionYamlSupport();
        RateLimitSupport rateLimitSupport = RateLimitSupport.forManual();

        List<ResourceGenerator> generators = new ArrayList<>();
        generators.add(new GatewayGenerator());

        HttpRouteGenerator httpRoute = new HttpRouteGenerator();
        httpRoute.bindManualContributors(ManualHttpRouteContributorFactory.create());
        generators.add(httpRoute);

        AuthPolicyGenerator authPolicy = new AuthPolicyGenerator();
        authPolicy.bindManualContributors(ManualAuthPolicyContributorFactory.create());
        generators.add(authPolicy);

        SecretGenerator secret = new SecretGenerator();
        secret.bindManualContributors(ManualSecretContributorFactory.create());
        generators.add(secret);

        generators.add(new ConfigMapGenerator());
        generators.add(new ApiProductGenerator());

        ApiKeyGenerator apiKey = new ApiKeyGenerator();
        generators.add(apiKey);

        generators.add(new ServiceEntryGenerator());
        generators.add(new DestinationRuleGenerator());

        TelemetryGenerator telemetry = new TelemetryGenerator();
        telemetry.bindManual(policyFinder);
        generators.add(telemetry);

        LoggingEnvoyFilterGenerator logging = new LoggingEnvoyFilterGenerator();
        logging.bindManual(policyFinder, yamlSupport);
        generators.add(logging);

        UrlRewritingEnvoyFilterGenerator urlRewriting = new UrlRewritingEnvoyFilterGenerator();
        urlRewriting.bindManual(policyFinder, yamlSupport);
        generators.add(urlRewriting);

        MaintenanceModeEnvoyFilterGenerator maintenanceMode = new MaintenanceModeEnvoyFilterGenerator();
        maintenanceMode.bindManual(policyFinder);
        generators.add(maintenanceMode);

        ContentLimitsEnvoyFilterGenerator contentLimits = new ContentLimitsEnvoyFilterGenerator();
        contentLimits.bindManual(policyFinder, policyConfigSupport);
        generators.add(contentLimits);

        RetryEnvoyFilterGenerator retry = new RetryEnvoyFilterGenerator();
        retry.bindManual(policyFinder, policyConfigSupport);
        generators.add(retry);

        AuthorizationPolicyGenerator authorizationPolicy = new AuthorizationPolicyGenerator();
        authorizationPolicy.bindManual(policyFinder, policyConfigSupport);
        generators.add(authorizationPolicy);

        RateLimitPolicyGenerator rateLimit = new RateLimitPolicyGenerator();
        rateLimit.bindManual(rateLimitSupport);
        generators.add(rateLimit);

        generators.add(new TlsPolicyGenerator());
        generators.add(new DnsPolicyGenerator());

        ReadmeGenerator readme = new ReadmeGenerator();
        generators.add(readme);

        return generators;
    }
}
