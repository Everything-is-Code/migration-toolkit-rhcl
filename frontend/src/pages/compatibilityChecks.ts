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
  errorMessage: string;
};

/**
 * Runs Compatibility checks for selected services.
 * Loads supported policies once, then checks each service with that shared list.
 */
export async function runCompatibilityChecks(
  services: CompatibilityServiceRef[],
  connection: CompatibilityConnection,
  deps: CompatibilityCheckDeps,
): Promise<{ results: CompatibilityResult[]; error: string | null }> {
  const results: CompatibilityResult[] = [];
  try {
    const policies = await deps.loadSupportedPolicies();
    for (const service of services) {
      try {
        const resp = await deps.checkCompatibility(
          service.id,
          connection.url,
          connection.accessToken,
          policies,
        );
        results.push(resp.data);
      } catch {
        results.push({
          serviceId: service.id,
          serviceName: service.name,
          score: 0,
          level: 'LOW',
          items: [{ name: 'Error', status: 'UNSUPPORTED', message: deps.errorMessage }],
        });
      }
    }
    return { results, error: null };
  } catch (e: unknown) {
    const message = e instanceof Error ? e.message : deps.errorMessage;
    return { results, error: message };
  }
}
