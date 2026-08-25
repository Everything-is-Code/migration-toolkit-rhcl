export interface YamlFile { name: string; content: string; }
export type EditMap = Record<string, string>;
export interface ApplyResult { fileName: string; success: boolean; message: string; }

export interface RouteInfo { path: string; method: string; }
export interface AuthInfo {
  type: 'apiKey' | 'jwt' | 'none';
  prefix?: string;
  headerName?: string;
}
export interface TestInfo {
  gatewayName: string;
  routes: RouteInfo[];
  auth: AuthInfo;
  apiKey?: string;
}

export function parseTestInfo(edits: EditMap): TestInfo {
  const gatewayYaml  = edits['gateway.yaml']  ?? '';
  const routeYaml    = edits['httproute.yaml'] ?? '';
  const policyYaml   = edits['policy.yaml']   ?? '';

  const gwNameMatch = gatewayYaml.match(/^  name:\s*(.+)$/m);
  const gatewayName = gwNameMatch ? gwNameMatch[1].trim() : '';

  const routes: RouteInfo[] = [];
  const pathMatches   = Array.from(routeYaml.matchAll(/value:\s*"([^"]+)"/g));
  const methodMatches = Array.from(routeYaml.matchAll(/method:\s*(\w+)/g));
  pathMatches.forEach((pm, i) => {
    routes.push({
      path: pm[1],
      method: methodMatches[i]?.[1] ?? 'GET',
    });
  });
  if (routes.length === 0) routes.push({ path: '/', method: 'GET' });

  let auth: AuthInfo = { type: 'none' };
  if (policyYaml.includes('jwt:') || policyYaml.includes('jwt-auth:')) {
    auth = { type: 'jwt', headerName: 'Authorization' };
  } else if (policyYaml.includes('apiKey:') || policyYaml.includes('api-key-auth:')) {
    const prefixMatch = policyYaml.match(/prefix:\s*(\w+)/);
    auth = {
      type: 'apiKey',
      prefix: prefixMatch ? prefixMatch[1] : 'APIKEY',
      headerName: 'Authorization',
    };
  }

  const secretYaml = edits['secret.yaml'] ?? '';
  const apiKeyMatch = secretYaml.match(/api_key:\s*"?([a-zA-Z0-9+/=_-]{8,})"?/);
  const apiKey = apiKeyMatch ? apiKeyMatch[1] : undefined;

  return { gatewayName, routes, auth, apiKey };
}

export type DiffLine = { type: 'same' | 'add' | 'remove'; text: string };

export function detectExternalBackend(editMap: EditMap): boolean {
  if ('serviceentry.yaml' in editMap) return true;
  return Object.values(editMap).some(
    yaml => /type:\s*ExternalName/i.test(yaml) || /kind:\s*ServiceEntry/i.test(yaml)
  );
}

export function normalizeApiVersions(yaml: string): string {
  return yaml
    .replace(/apiVersion: kuadrant\.io\/v1beta2/g, 'apiVersion: kuadrant.io/v1')
    .replace(/apiVersion: kuadrant\.io\/v1beta1/g, 'apiVersion: kuadrant.io/v1')
    .replace(/apiVersion: gateway\.networking\.k8s\.io\/v1beta1/g, 'apiVersion: gateway.networking.k8s.io/v1');
}

export function applyPkgToYaml(yaml: string, pkg: string): string {
  if (!pkg) return yaml;
  return yaml
    .replace(/^(\s+name:\s+)api(-|(?=\s*$))/gm, `$1${pkg}$2`)
    .replace(/^(\s+app:\s+)api(-|(?=\s*$))/gm, `$1${pkg}$2`)
    .replace(/^(\s+service-name:\s*)["']?API["']?/gm, `$1"${pkg.toUpperCase()}"`);
}

export function deriveEdits(base: EditMap, pkg: string, ns: string): EditMap {
  const updated: EditMap = {};
  for (const [name, content] of Object.entries(base)) {
    let yaml = normalizeApiVersions(content);
    yaml = yaml.replace(/^(\s*namespace:\s*).+$/gm, `$1${ns}`);
    yaml = applyPkgToYaml(yaml, pkg);
    updated[name] = yaml;
  }
  return updated;
}

export function computeDiff(original: string, current: string): DiffLine[] {
  const oldLines = original.split('\n');
  const newLines = current.split('\n');

  const m = oldLines.length;
  const n = newLines.length;
  const dp: number[][] = Array.from({ length: m + 1 }, () => new Array(n + 1).fill(0));
  for (let i = m - 1; i >= 0; i--) {
    for (let j = n - 1; j >= 0; j--) {
      if (oldLines[i] === newLines[j]) {
        dp[i][j] = dp[i + 1][j + 1] + 1;
      } else {
        dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
      }
    }
  }

  const result: DiffLine[] = [];
  let i = 0, j = 0;
  while (i < m || j < n) {
    if (i < m && j < n && oldLines[i] === newLines[j]) {
      result.push({ type: 'same', text: oldLines[i] });
      i++; j++;
    } else if (j < n && (i >= m || dp[i][j + 1] >= dp[i + 1][j])) {
      result.push({ type: 'add', text: newLines[j] });
      j++;
    } else {
      result.push({ type: 'remove', text: oldLines[i] });
      i++;
    }
  }
  return result;
}
