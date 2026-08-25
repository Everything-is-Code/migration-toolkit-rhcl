export type LoggingTarget = 'gateway' | 'workload';
export type AnonymousTarget = 'httproute' | 'gateway';
export type IpCheckMode = 'authorizationPolicy' | 'authPolicyOpa';

export interface ConversionFormOptions {
  isExternal: boolean;
  externalBackendUrl: string;
  loggingTarget: LoggingTarget;
  anonymousTarget: AnonymousTarget;
  ipCheckMode: IpCheckMode;
  includeMigratedFromLabel: boolean;
  includeTlsPolicy: boolean;
  tlsIssuerKind: string;
  tlsIssuerName: string;
  includeDnsPolicy: boolean;
  dnsHostname: string;
  dnsProviderSecretName: string;
}
