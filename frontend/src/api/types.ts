export interface ConnectionRequest {
  url: string;
  accessToken: string;
  tenant?: string;
}

export interface ApiService {
  id: string;
  name: string;
  description?: string;
  state?: string;
  systemName?: string;
  backendVersion?: string;
  deploymentOption?: string;
  backends?: Backend[];
  mappingRules?: MappingRule[];
  metrics?: Metric[];
  policies?: Policy[];
  authentication?: Authentication;
}

export interface Backend {
  id: string;
  name: string;
  systemName?: string;
  privateEndpoint?: string;
}

export interface MappingRule {
  id: string;
  httpMethod: string;
  pattern: string;
  metricSystemName?: string;
}

export interface Metric {
  id: string;
  name: string;
  systemName: string;
  unit?: string;
}

export interface Policy {
  name: string;
  version?: string;
  enabled: boolean;
  configuration?: Record<string, unknown>;
}

export interface Authentication {
  type: string;
  location?: string;
  paramName?: string;
  oidcIssuerEndpoint?: string;
}

export interface CompatibilityItem {
  name: string;
  status: 'SUPPORTED' | 'WARNING' | 'UNSUPPORTED';
  message: string;
  /** Optional capability flag id (e.g. corsNative, kuadrantPresent). */
  capability?: string;
  /** Optional human-readable version requirement when a capability is missing. */
  requiredVersion?: string;
}

/** Capability flags from GET /api/cluster/versions. */
export interface ClusterCapabilities {
  corsNative: boolean;
  kuadrantPresent: boolean;
  ossmPresent: boolean;
  ossmMatchesOcp: boolean;
  timeoutsSupported: boolean;
}

/** Profile override for cluster version resolution. */
export type ClusterProfile = 'auto' | 'ocp-4.19' | 'ocp-4.21';

/** Version resolution source. */
export type ClusterVersionsSource = 'detected' | 'profile' | 'default';

/** Response for GET /api/cluster/versions. */
export interface ClusterVersionsResponse {
  ocp: string;
  gatewayApi: string;
  kuadrant: string | null;
  ossm: string | null;
  ossmExpectedForOcp: string | null;
  capabilities: ClusterCapabilities;
  source: ClusterVersionsSource;
  profile: ClusterProfile;
  errors?: string[];
}

export interface CompatibilityResult {
  serviceId: string;
  serviceName: string;
  score: number;
  level: 'HIGH' | 'MEDIUM' | 'LOW';
  items: CompatibilityItem[];
}

export interface ConversionRequest {
  threescaleUrl: string;
  accessToken: string;
  tenant?: string;
  namespace: string;
  serviceIds: string[];
  externalBackendUrl?: string;
  supportedPolicies?: string[];
  loggingTarget?: 'gateway' | 'workload';
  anonymousTarget?: 'httproute' | 'gateway';
  includeMigratedFromLabel?: boolean;
  ipCheckMode?: 'authorizationPolicy' | 'authPolicyOpa';
  includeTlsPolicy?: boolean;
  tlsIssuerKind?: string;
  tlsIssuerName?: string;
  includeDnsPolicy?: boolean;
  dnsHostname?: string;
  dnsProviderSecretName?: string;
}

export interface ConversionResultItem {
  serviceId: string;
  serviceName: string;
  packageName: string;
  historyId: number;
  compatibilityScore: number;
  files: string[];
  yamlFiles: Record<string, string>;
  status?: string;
  error?: string;
}

export interface ConversionResponse {
  projectId: number;
  results: ConversionResultItem[];
}

export interface ValidationItem {
  check: string;
  status: 'OK' | 'WARNING' | 'ERROR';
  message: string;
}

export interface ValidationResult {
  valid: boolean;
  items: ValidationItem[];
}

export interface FailureDetail {
  fileName: string;
  kind: string;
  name: string;
  error: string;
}

export interface ConversionHistory {
  id: number;
  serviceId?: string;
  serviceName?: string;
  status: string;
  compatibilityScore?: number;
  source?: string;        // CONVERT | IMPORT
  packageName?: string;
  namespace?: string;
  totalCount?: number;
  successCount?: number;
  failureCount?: number;
  failureDetails?: string; // JSON string of FailureDetail[]
  exportedYaml?: string;
  createdAt: string;
}
