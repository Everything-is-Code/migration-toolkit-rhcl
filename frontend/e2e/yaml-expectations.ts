/**
 * Expected YAML fragments per 3scale seed product (live lab E2E).
 * Update when adding a new rhcl_seed_* conversion case.
 */
export type YamlFileExpectations = {
  mustContain: string[];
  mustNotContain?: string[];
};

export type ProductYamlExpectations = {
  /** Visible name in API list (regex or string) */
  productLabel: RegExp | string;
  files: Record<string, YamlFileExpectations>;
};

export const SEED_YAML_EXPECTATIONS: Record<string, ProductYamlExpectations> = {
  rhcl_seed_claim_role_chain: {
    productLabel: /RHCL Seed Claim Role Chain/,
    files: {
      'httproute.yaml': {
        mustContain: ['rhcl-seed-claim-role-chain-route', 'kind: HTTPRoute'],
        mustNotContain: ['rhcl-seed-claim-cache-chain'],
      },
      'policy.yaml': {
        mustContain: [
          'rhcl-seed-claim-role-chain-auth',
          'jwt-claim-check',
          'keycloak-role-check',
          'auth.identity.role',
          'rhcl-demo-user',
        ],
      },
      'secret.yaml': { mustContain: ['kind: Secret'] },
      'configmap.yaml': { mustContain: ['kind: ConfigMap'] },
      'apiproduct.yaml': { mustContain: ['kind: APIProduct'] },
    },
  },
  rhcl_seed_claim_cache_chain: {
    productLabel: /RHCL Seed Claim Cache Chain/,
    files: {
      'httproute.yaml': {
        mustContain: ['rhcl-seed-claim-cache-chain-route'],
        mustNotContain: ['rhcl-seed-claim-role-chain'],
      },
      'policy.yaml': {
        mustContain: [
          'rhcl-seed-claim-cache-chain-auth',
          'jwt-claim-check',
          'cache',
        ],
      },
      'secret.yaml': { mustContain: ['kind: Secret'] },
      'configmap.yaml': { mustContain: ['kind: ConfigMap'] },
      'apiproduct.yaml': { mustContain: ['kind: APIProduct'] },
    },
  },
  rhcl_seed_keycloak_roles: {
    productLabel: /RHCL Seed Keycloak Role Check/,
    files: {
      'httproute.yaml': { mustContain: ['rhcl-seed-keycloak-roles-route'] },
      'policy.yaml': {
        mustContain: [
          'rhcl-seed-keycloak-roles-auth',
          'keycloak-role-check',
          'auth.identity.realm_access.roles',
          'rhcl-demo-user',
        ],
        mustNotContain: ['jwt-claim-check'],
      },
    },
  },
  rhcl_seed_auth_chain: {
    productLabel: /RHCL Seed Auth Chain/,
    files: {
      'httproute.yaml': { mustContain: ['rhcl-seed-auth-chain-route'] },
      'policy.yaml': {
        mustContain: [
          'rhcl-seed-auth-chain-auth',
          'jwt-claim-check',
          'auth.identity.role',
        ],
      },
      'authorizationpolicy.yaml': {
        mustContain: [
          'rhcl-seed-auth-chain-ip-check',
          'remoteIpBlocks:',
          '10.0.0.0/8',
          '192.168.0.0/16',
        ],
        mustNotContain: ['remoteIpBlocks:\n        - "'],
      },
    },
  },
  rhcl_seed_anonymous: {
    productLabel: /RHCL Seed Anonymous/,
    files: {
      'httproute.yaml': { mustContain: ['rhcl-seed-anonymous-route'] },
      'policy.yaml': {
        mustContain: ['rhcl-seed-anonymous-auth', 'anonymous'],
        mustNotContain: ['rhcl-seed-auth-chain', 'rhcl-seed-claim-role-chain'],
      },
    },
  },
};
