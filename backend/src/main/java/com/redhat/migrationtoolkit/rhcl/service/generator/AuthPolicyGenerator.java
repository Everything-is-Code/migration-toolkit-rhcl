package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.model.kuadrant.AuthPolicyManifest;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorOrdering;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.AuthPolicyBuilder;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.AuthPolicyContributor;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ApplicationScoped
@Priority(300)
public class AuthPolicyGenerator implements ResourceGenerator {

    @Inject
    Instance<AuthPolicyContributor> contributors;

    @Inject
    ManifestSerializer manifestSerializer;

    private List<AuthPolicyContributor> manualContributors;

    void bindManualContributors(List<AuthPolicyContributor> list) {
        this.manualContributors = list;
    }

    void bindManual(ManifestSerializer serializer) {
        this.manifestSerializer = serializer;
    }

    @Override
    public String outputKey() {
        return "policy.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        return true;
    }

    @Override
    public String generate(ConversionContext ctx) {
        AuthPolicyBuilder builder = new AuthPolicyBuilder(ctx);
        for (AuthPolicyContributor contributor : orderedContributors()) {
            contributor.contribute(builder, ctx);
        }
        if (!builder.hasBase()) {
            return "";
        }
        AuthPolicyManifest manifest = builder.build();
        return serializer().toYaml(manifest);
    }

    private List<AuthPolicyContributor> orderedContributors() {
        List<AuthPolicyContributor> list = manualContributors != null
                ? new ArrayList<>(manualContributors)
                : new ArrayList<>();
        if (manualContributors == null && contributors != null) {
            contributors.forEach(list::add);
        }
        list.sort(Comparator.comparingInt(ContributorOrdering::priorityOf));
        return list;
    }

    private ManifestSerializer serializer() {
        return manifestSerializer != null ? manifestSerializer : new ManifestSerializer();
    }
}
