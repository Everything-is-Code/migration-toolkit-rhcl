package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.AnonymousSecretContributor;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.ApiKeySecretContributor;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.AppIdKeySecretContributor;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.DefaultCredentialsSecretContributor;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.SecretContributor;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.TokenIntrospectionSecretContributor;

import java.util.ArrayList;
import java.util.List;

final class ManualSecretContributorFactory {

    private ManualSecretContributorFactory() {
    }

    static List<SecretContributor> create() {
        List<SecretContributor> contributors = new ArrayList<>();
        contributors.add(new AnonymousSecretContributor());
        contributors.add(new TokenIntrospectionSecretContributor());
        contributors.add(new AppIdKeySecretContributor());
        contributors.add(new ApiKeySecretContributor());
        contributors.add(new DefaultCredentialsSecretContributor());
        return contributors;
    }
}
