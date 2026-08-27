/**
 * Pure UI helpers for cluster capability surfaces (Connection / Conversion).
 * Kept free of React so vitest can cover WU3 scenarios without a DOM harness.
 */

import type { ClusterProfile, ClusterVersionsResponse } from '../api/types';

export type ClusterConnectionUiState = 'reachable' | 'unreachable' | 'profile';

/** Always show cluster versions on Connection (independent of 3scale connect). */
export function shouldShowClusterVersionsCard(_connected?: boolean): boolean {
  return true;
}

/**
 * Connection page cluster status derived from version resolution.
 */
export function getClusterConnectionUiState(
  versions: ClusterVersionsResponse | null | undefined,
): ClusterConnectionUiState {
  if (!versions) {
    return 'unreachable';
  }
  if (versions.source === 'profile') {
    return 'profile';
  }
  if (versions.capabilities?.clusterReachable === true) {
    return 'reachable';
  }
  return 'unreachable';
}

/** i18n keys used when Kuadrant/RHCL is not shown as a raw detected version string. */
export type KuadrantDisplayI18nKey =
  | 'connection.kuadrantAbsent'
  | 'connection.kuadrantUnreachable'
  | 'connection.kuadrantProfileAssumed';

/**
 * Kuadrant/RHCL cell text for Connection — detected version string or translated state copy.
 */
export function formatKuadrantDisplay(
  versions: ClusterVersionsResponse | null | undefined,
  t: (key: KuadrantDisplayI18nKey) => string,
): string {
  if (!versions) {
    return '—';
  }
  const state = getClusterConnectionUiState(versions);
  if (state === 'profile') {
    return versions.capabilities?.kuadrantPresent
      ? t('connection.kuadrantProfileAssumed')
      : t('connection.kuadrantAbsent');
  }
  if (state === 'unreachable') {
    return t('connection.kuadrantUnreachable');
  }
  const kuadrant = versions.kuadrant?.trim();
  return kuadrant || t('connection.kuadrantAbsent');
}

/** Whether OCP/GAPI values are fallback defaults (not live-detected). */
export function isFallbackVersionSource(versions: ClusterVersionsResponse | null | undefined): boolean {
  return versions?.source === 'default';
}

/**
 * Profile → Connection page i18n key (avoids nested ternaries in FormSelect labels).
 */
export const CLUSTER_PROFILE_I18N_KEYS: Record<ClusterProfile, string> = {
  auto: 'connection.profile.auto',
  'ocp-4.19': 'connection.profile.ocp419',
  'ocp-4.21': 'connection.profile.ocp421',
};

export function clusterProfileI18nKey(profile: ClusterProfile): string {
  return CLUSTER_PROFILE_I18N_KEYS[profile];
}

/**
 * i18n key for the Conversion CORS capability hint, or null when no cors policy.
 */
export function corsConversionHintKey(
  hasCorsPolicy: boolean,
  clusterReachable: boolean,
  corsNative: boolean,
): 'conversion.corsNativeHint' | 'conversion.corsFallbackHint' | null {
  if (!hasCorsPolicy || !clusterReachable) {
    return null;
  }
  return corsNative ? 'conversion.corsNativeHint' : 'conversion.corsFallbackHint';
}
