package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.service.conversion.ContributorOrdering;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ConversionContext;
import com.redhat.migrationtoolkit.rhcl.service.conversion.ManifestSerializer;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.SecretBuilder;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.SecretContributor;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ApplicationScoped
@Priority(400)
public class SecretGenerator implements ResourceGenerator {

    @Inject
    Instance<SecretContributor> contributors;

    @Inject
    ManifestSerializer manifestSerializer;

    private List<SecretContributor> manualContributors;

    void bindManualContributors(List<SecretContributor> list) {
        this.manualContributors = list;
    }

    void bindManual(ManifestSerializer serializer) {
        this.manifestSerializer = serializer;
    }

    @Override
    public String outputKey() {
        return "secret.yaml";
    }

    @Override
    public boolean applies(ConversionContext ctx) {
        return true;
    }

    @Override
    public String generate(ConversionContext ctx) {
        SecretBuilder builder = new SecretBuilder(ctx);
        for (SecretContributor contributor : orderedContributors()) {
            contributor.contribute(builder, ctx);
        }
        return builder.build(serializer());
    }

    private ManifestSerializer serializer() {
        return manifestSerializer != null ? manifestSerializer : new ManifestSerializer();
    }

    private List<SecretContributor> orderedContributors() {
        List<SecretContributor> list = manualContributors != null
                ? new ArrayList<>(manualContributors)
                : new ArrayList<>();
        if (manualContributors == null && contributors != null) {
            contributors.forEach(list::add);
        }
        list.sort(Comparator.comparingInt(ContributorOrdering::priorityOf));
        return list;
    }
}
