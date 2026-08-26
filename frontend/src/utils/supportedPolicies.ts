import { settingsApi } from '../api/client';

export const ALL_POLICIES = [
  '3scale APIcast',
  '3scale Auth Caching',
  '3scale Batcher',
  '3scale Referrer',
  'Anonymous Access',
  'Camel Service',
  'Conditional Policy',
  'Content Caching',
  'CORS Request Handling',
  'Custom metrics',
  'Echo',
  'Edge Limiting',
  'Header Modification',
  'IP Check',
  'JWT Claim Check',
  'Liquid Context Debug',
  'Logging',
  'Maintenance Mode',
  'OAuth 2.0 Mutual TLS Client Authentication',
  'OAuth 2.0 Token Introspection',
  'Proxy Service',
  'Rate Limit Headers',
  'Response/Request Content Limits',
  'Retry',
  'RH-SSO/Keycloak Role Check',
  'Routing',
  'SOAP',
  'TLS Client Certificate Validation',
  'TLS Termination',
  'Upstream',
  'Upstream Connection',
  'Upstream Mutual TLS',
  'URL Rewriting',
  'URL Rewriting with Captures',
];

export const DEFAULT_SUPPORTED_POLICIES = [
  '3scale APIcast',
  'Header Modification',
  'Upstream Connection',
  'Logging',
  'Anonymous Access',
  'URL Rewriting',
  '3scale Auth Caching',
  'CORS Request Handling',
  'IP Check',
  'Edge Limiting',
  'OAuth 2.0 Token Introspection',
  'JWT Claim Check',
  'Response/Request Content Limits',
  'Retry',
  'RH-SSO/Keycloak Role Check',
  'Maintenance Mode',
  'Upstream',
];

export const SETTINGS_KEY = 'supportedPolicies';

/** Module-level cache so Convert / Compatibility reuse one GET until save invalidates. */
let supportedPoliciesCache: Promise<string[]> | null = null;

/**
 * Merge newly supported defaults into a saved list so upgrades enable converters
 * that landed after the user last saved Supported Policies.
 */
export function withDefaultSupportedPolicies(saved: string[]): string[] {
  const known = new Set(ALL_POLICIES);
  const kept = saved.filter(p => known.has(p));
  return Array.from(new Set([...DEFAULT_SUPPORTED_POLICIES, ...kept]));
}

/** Clear cached policies (tests + after successful save). */
export function invalidateSupportedPoliciesCache(): void {
  supportedPoliciesCache = null;
}

/** Seed cache without a network round-trip (e.g. after save). */
export function seedSupportedPoliciesCache(policies: string[]): void {
  supportedPoliciesCache = Promise.resolve(policies);
}

export async function loadSupportedPolicies(): Promise<string[]> {
  if (!supportedPoliciesCache) {
    supportedPoliciesCache = (async () => {
      try {
        const resp = await settingsApi.get(SETTINGS_KEY);
        const parsed = JSON.parse(resp.data.value);
        if (!Array.isArray(parsed)) {
          return DEFAULT_SUPPORTED_POLICIES;
        }
        return withDefaultSupportedPolicies(parsed.filter((p): p is string => typeof p === 'string'));
      } catch {
        return DEFAULT_SUPPORTED_POLICIES;
      }
    })();
  }
  return supportedPoliciesCache;
}
