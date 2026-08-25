import type { CompatibilityResult } from '../api/types';

export type CompatibilityServiceRef = {
  id: string;
  name: string;
};

export type CompatibilityConnection = {
  url: string;
  accessToken: string;
};

export type CompatibilityCheckDeps = {
  loadSupportedPolicies: () => Promise<string[]>;
  checkCompatibility: (
    id: string,
    url: string,
    accessToken: string,
    supportedPolicies: string[],
  ) => Promise<{ data: CompatibilityResult }>;
  formatApiError: (e: unknown) => string;
};

/** One retry absorbs a single transient settings hiccup without re-introducing N loads. */
async function loadPoliciesWithRetry(
  load: () => Promise<string[]>,
  retries = 1,
): Promise<string[]> {
  let lastError: unknown;
  for (let attempt = 0; attempt <= retries; attempt++) {
    try {
      return await load();
    } catch (e) {
      lastError = e;
    }
  }
  throw lastError instanceof Error ? lastError : new Error(String(lastError));
}

/**
 * Runs Compatibility checks for selected services.
 * Loads supported policies once (with one retry), then checks each service with that shared list.
 */
export async function runCompatibilityChecks(
  services: CompatibilityServiceRef[],
  connection: CompatibilityConnection,
  deps: CompatibilityCheckDeps,
): Promise<{ results: CompatibilityResult[]; error: string | null }> {
  const results: CompatibilityResult[] = [];
  try {
    const policies = await loadPoliciesWithRetry(deps.loadSupportedPolicies);
    for (const service of services) {
      try {
        const resp = await deps.checkCompatibility(
          service.id,
          connection.url,
          connection.accessToken,
          policies,
        );
        results.push(resp.data);
      } catch (e: unknown) {
        results.push({
          serviceId: service.id,
          serviceName: service.name,
          score: 0,
          level: 'LOW',
          items: [{ name: 'Error', status: 'UNSUPPORTED', message: deps.formatApiError(e) }],
        });
      }
    }
    return { results, error: null };
  } catch (e: unknown) {
    return { results, error: deps.formatApiError(e) };
  }
}
