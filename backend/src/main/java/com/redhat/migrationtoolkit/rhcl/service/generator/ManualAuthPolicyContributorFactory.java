package com.redhat.migrationtoolkit.rhcl.service.generator;

import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.AnonymousContributor;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.ApiKeyAuthenticationContributor;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.AppIdKeyAuthenticationContributor;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.AuthCachingContributor;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.AuthPolicyContributor;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.EmptyAuthenticationContributor;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.IpCheckOpaContributor;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.JwtAuthenticationContributor;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.JwtClaimCheckContributor;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.KeycloakRoleCheckContributor;
import com.redhat.migrationtoolkit.rhcl.service.generator.contributor.Oauth2IntrospectionContributor;

import java.util.ArrayList;
import java.util.List;

final class ManualAuthPolicyContributorFactory {

    private ManualAuthPolicyContributorFactory() {
    }

    static List<AuthPolicyContributor> create() {
        List<AuthPolicyContributor> contributors = new ArrayList<>();
        contributors.add(new AuthCachingContributor());
        contributors.add(new AnonymousContributor());
        contributors.add(new Oauth2IntrospectionContributor());
        contributors.add(new JwtAuthenticationContributor());
        contributors.add(new ApiKeyAuthenticationContributor());
        contributors.add(new AppIdKeyAuthenticationContributor());
        contributors.add(new EmptyAuthenticationContributor());
        contributors.add(new JwtClaimCheckContributor());
        contributors.add(new KeycloakRoleCheckContributor());
        contributors.add(new IpCheckOpaContributor());
        return contributors;
    }
}
